package com.bonstead.pitdroid

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.text.DecimalFormat
import java.util.ArrayList
import java.util.Locale

import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

import android.content.SharedPreferences
import android.util.Log

object HeaterMeter {
    private const val TAG = "HeaterMeter"

    internal const val kNumProbes = 4
    private const val kHistoryURL = "/luci/lm/hist"
    private const val kStatusURL = "/luci/lm/hmstatus"
    private const val kAuthURL = "/luci/admin/lm"

    // No point in trying to sample faster than this, it's the update rate of the
    // HeaterMeter hardware
    internal const val kMinSampleTime: Long = 1000
    // Wait at least this long between full history refreshes
    private const val kMinHistoryUpdateTime: Long = 5000
    // If we're updating and it's been more than this long since the last update, force a
    // full history refresh, so we don't have too much data missing.
    private const val kMaxUpdateDelta: Long = 5000

    // User settings
    private val mServerAddress = arrayOfNulls<String>(2)
    private var mCurrentServer = 0
    private var mAdminPassword: String? = null
    internal var mBackgroundUpdateTime: Int = 0
    internal var mAlwaysSoundAlarm = true
    internal var mAlarmOnLostConnection = true
    internal var mProbeLoAlarm = IntArray(kNumProbes)
    internal var mProbeHiAlarm = IntArray(kNumProbes)
    internal var mKeepScreenOn: Boolean = false

    internal var mSamples = ArrayList<Sample>()
    internal var mLatestSample: NamedSample? = null
    internal var mProbeNames = arrayOfNulls<String>(kNumProbes)
    private val mDegreesPerHour = DoubleArray(kNumProbes)

    internal var mLastStatusMessage: String? = null

    private var mLastUpdateTime: Long = 0
    private var mLastHistoryTime: Long = 0
    private var mNewestTime = 0
    private var mMinTemperature = Double.MAX_VALUE
    private var mMaxTemperature = Double.MIN_VALUE
    private val mListeners = ArrayList<Listener>()
    private val mOneDec = DecimalFormat("0.0")

    // For authentication, the cookie that's passed, and the URL token
    private var mAuthCookie: String? = null
    private var mAuthToken: String? = null

    private var mSavedHistory: ArrayList<Sample>? = null
    private val mSavedProbeNames = arrayOfNulls<String>(kNumProbes)

    /*
	 * Return the minimum and maximum times from our samples
	 */
    internal val minTime: Int
        get() = if (mSamples.size > 0)
            mSamples[0].mTime
        else
            0

    internal val maxTime: Int
        get() = if (mSamples.size > 0)
            mSamples[mSamples.size - 1].mTime
        else
            0

    internal val sample: NamedSample?
        get() {
            val reader = getUrlReader(kStatusURL)
            return if (reader != null) {
                parseStatus(readerToString(reader))
            } else null
        }

    private val isAuthenticated: Boolean
        get() = mAuthCookie != null

    open class Sample {
        var mTime: Int = 0
        var mFanSpeed: Double = 0.0
        var mLidOpen: Double = 0.0
        var mSetPoint: Double = 0.0
        var mProbes = DoubleArray(kNumProbes)

        constructor() {
            mTime = 0
            mFanSpeed = 0.0
            mLidOpen = 0.0
            mSetPoint = Double.NaN
            for (p in 0 until kNumProbes)
                mProbes[p] = Double.NaN
        }

        constructor(otherSample: Sample) {
            mTime = otherSample.mTime
            mFanSpeed = otherSample.mFanSpeed
            mLidOpen = otherSample.mLidOpen
            mSetPoint = otherSample.mSetPoint
            System.arraycopy(otherSample.mProbes, 0, mProbes, 0, kNumProbes)
        }
    }

    class NamedSample : Sample {
        var mProbeNames = arrayOfNulls<String>(kNumProbes)
        var mDegreesPerHour = DoubleArray(kNumProbes)

        constructor() : super() {

            for (p in 0 until kNumProbes)
                mDegreesPerHour[p] = 0.0
        }

        constructor(otherSample: Sample) : super(otherSample) {

            for (p in 0 until kNumProbes)
                mDegreesPerHour[p] = 0.0
        }
    }

    internal interface Listener {
        fun samplesUpdated(latestSample: NamedSample?)
    }

    internal fun addListener(listener: Listener) {
        mListeners.add(listener)

        // To avoid waiting for the first refresh, grab the latest sample and send it over right away
        if (mLatestSample != null) {
            listener.samplesUpdated(mLatestSample)
        }
    }

    internal fun removeListener(listener: Listener) {
        mListeners.remove(listener)
    }

    internal fun getNormalized(temperature: Double): Double {
        return (temperature - mMinTemperature) / (mMaxTemperature - mMinTemperature)
    }

    internal fun getOriginal(normalized: Double): Double {
        return normalized * (mMaxTemperature - mMinTemperature) + mMinTemperature
    }

    internal fun formatTemperature(temperature: Double): String {
        return mOneDec.format(temperature) + "°"
    }

    internal fun hasAlarms(): Boolean {
        for (p in 0 until kNumProbes) {
            if (mProbeLoAlarm[p] > 0 || mProbeHiAlarm[p] > 0) {
                return true
            }
        }

        return false
    }

    // Returns the text to display for a triggered alarm, or an empty string if the alarm isn't triggered.
    internal fun formatAlarm(probeIndex: Int, temperature: Double): String {
        val hasLo = mProbeLoAlarm[probeIndex] > 0
        val hasHi = mProbeHiAlarm[probeIndex] > 0

        if ((hasLo || hasHi) && temperature.isNaN()) {
            return "off"
        } else if (hasLo && temperature < mProbeLoAlarm[probeIndex]) {
            return formatTemperature(mProbeLoAlarm[probeIndex] - temperature) + " below alarm point"
        } else if (hasHi && temperature > mProbeHiAlarm[probeIndex]) {
            return formatTemperature(temperature - mProbeHiAlarm[probeIndex]) + " above alarm point"
        }

        return ""
    }

    internal fun getTemperatureChangeText(probeIndex: Int): String? {
        val degreesPerHour = mDegreesPerHour[probeIndex]

        // Don't display if there isn't clear increase, prevents wild numbers
        if (degreesPerHour < 1.0) {
            return null
        }

        var timeStr = String.format(Locale.US, "%.1f°/hr", degreesPerHour)

        val lastSample = mSamples[mSamples.size - 1]
        val currentTemp = lastSample.mProbes[probeIndex]

        // If we've got an alarm set and our most recent sample had a reading for this
        // probe, see if we can calculate an estimated time to alarm.
        if (mProbeHiAlarm[probeIndex] > 0 && !currentTemp.isNaN()) {
            var minutesRemaining = ((mProbeHiAlarm[probeIndex] - currentTemp) / degreesPerHour * 60).toInt()
            if (minutesRemaining > 0) {
                val hoursRemaining = minutesRemaining / 60
                minutesRemaining %= 60

                timeStr += String.format(Locale.US, ", %d:%02d to %d°", hoursRemaining,
                        minutesRemaining, mProbeHiAlarm[probeIndex])
            }
        }

        return timeStr
    }

    private fun updateMinMax(temp: Double) {
        // Round our min and max temperatures up/down to a multiple of 10 degrees, and
        // make sure they're increased/decreased by at least 1 degree. This gives us some
        // visual headroom in the graph.
        val roundedUp = Math.ceil((temp + 5.0) / 10.0) * 10.0
        val roundedDown = Math.floor((temp - 5.0) / 10.0) * 10.0

        mMinTemperature = Math.min(mMinTemperature, roundedDown)
        mMaxTemperature = Math.max(mMaxTemperature, roundedUp)
    }

    internal fun initPreferences(prefs: SharedPreferences) {
        mServerAddress[0] = prefs.getString("server", "")
        mServerAddress[1] = prefs.getString("altServer", "")

        for (i in 0..1) {
            if (!(mServerAddress[i]!!).matches("^(https?)://.*$".toRegex())) {
                mServerAddress[i] = "http://" + mServerAddress[i]
            }
        }

        mAdminPassword = prefs.getString("adminPassword", "")

        val updateTime = prefs.getString("backgroundUpdateTime", "15")
        if (updateTime != null) {
            mBackgroundUpdateTime = Integer.valueOf(updateTime)
        }

        mAlwaysSoundAlarm = prefs.getBoolean("alwaysSoundAlarm", true)
        mAlarmOnLostConnection = prefs.getBoolean("alarmOnLostConnection", true)

        mKeepScreenOn = prefs.getBoolean("keepScreenOn", false)

        for (p in 0 until kNumProbes) {
            val loName = "alarm" + p + "Lo"
            mProbeLoAlarm[p] = prefs.getInt(loName, -70)

            val hiName = "alarm" + p + "Hi"
            mProbeHiAlarm[p] = prefs.getInt(hiName, -200)
        }
    }

    internal fun preferencesChanged(prefs: SharedPreferences) {
        val editor = prefs.edit()

        for (p in 0 until kNumProbes) {
            val loName = "alarm" + p + "Lo"
            editor.putInt(loName, mProbeLoAlarm[p])

            val hiName = "alarm" + p + "Hi"
            editor.putInt(hiName, mProbeHiAlarm[p])
        }

        editor.apply()
    }

    internal fun updateThread(): Any? {
        var ret: Any? = null

        val currentTime = System.currentTimeMillis()

        val timeSinceLastUpdate = currentTime - mLastUpdateTime

        // If we don't have any samples, or we have over 500, rebuild the samples from
        // scratch based on the history. The upper end check keeps us from blowing memory
        // on hours and hours of high precision samples that you won't even be able to
        // see.
        // The time check is so that we don't read the history, then immediately read it
        // again because our previous read hadn't been processed by the main thread yet.
        if ((mSamples.size == 0 || mSamples.size > 500 || timeSinceLastUpdate > kMaxUpdateDelta) && currentTime - mLastHistoryTime > kMinHistoryUpdateTime) {
            mLastHistoryTime = currentTime

            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Getting history")
            }

            if (mSavedHistory != null) {
                ret = mSavedHistory
            } else {
                val reader = getUrlReader(kHistoryURL)
                if (reader != null) {
                    ret = parseHistory(reader)
                }
            }
        } else {
            if (mSavedHistory != null) {
                val namedSample = NamedSample(mSavedHistory!![mSavedHistory!!.size - 1])
                namedSample.mProbeNames = mSavedProbeNames
                ret = namedSample
            } else {
                val reader = getUrlReader(kStatusURL)
                if (reader != null) {
                    ret = parseStatus(readerToString(reader))
                }
            }
        }

        if (ret != null) {
            mLastUpdateTime = currentTime

            // We got a valid result, so we assume we connected to the server ok. If we've
            // got a password and we haven't successfully authenticated yet, give it a
            // try.
            if (mAdminPassword != null && mAdminPassword!!.length > 0 && !isAuthenticated) {
                authenticate()
            }
        }

        return ret
    }

    internal fun updateMain(data: Any?) {
        mLatestSample = null

        if (data is NamedSample) {
            mLatestSample = addStatus((data as NamedSample?)!!)
        } else if (data is ArrayList<*>) {
            var sampleData = data as ArrayList<Sample>?
            if (sampleData != null) {
                mLatestSample = addHistory(sampleData)
            }
        }

        for (l in mListeners.indices)
            mListeners[l].samplesUpdated(mLatestSample)
    }

    private fun parseStatus(status: String?): NamedSample? {
        try {
            val tokener = JSONTokener(status)
            val json = JSONObject(tokener)

            val sample = NamedSample()

            sample.mTime = json.getInt("time")
            sample.mSetPoint = json.getDouble("set")

            val fanInfo = json.getJSONObject("fan")
            sample.mFanSpeed = fanInfo.getDouble("c")

            sample.mLidOpen = json.getDouble("lid")

            val temps = json.getJSONArray("temps")
            for (i in 0 until temps.length()) {
                val row = temps.getJSONObject(i)

                sample.mProbeNames[i] = row.getString("n")

                if (!row.isNull("c")) {
                    sample.mProbes[i] = row.getDouble("c")
                } else {
                    sample.mProbes[i] = Double.NaN
                }

                if (!row.isNull("dph")) {
                    sample.mDegreesPerHour[i] = row.getDouble("dph")
                } else {
                    sample.mDegreesPerHour[i] = 0.0
                }
            }

            return sample
        } catch (e: JSONException) {
            e.printStackTrace()
            return null
        }
    }

    private fun addStatus(sample: NamedSample): NamedSample {
        if (mNewestTime != sample.mTime) {
            mNewestTime = sample.mTime

            for (p in 0 until kNumProbes) {
                mProbeNames[p] = sample.mProbeNames[p]
                mDegreesPerHour[p] = sample.mDegreesPerHour[p]
            }

            updateMinMax(sample.mSetPoint)

            for (p in 0 until kNumProbes) {
                if (!sample.mProbes[p].isNaN()) {
                    updateMinMax(sample.mProbes[p])
                }
            }

            val simpleSample = Sample(sample)

            mSamples.add(simpleSample)
        }

        return sample
    }

    internal fun setHistory(reader: Reader) {
        val br = BufferedReader(reader)
        try {
            for (i in 0 until kNumProbes) {
                mSavedProbeNames[i] = br.readLine()
            }

            mSavedHistory = parseHistory(br)
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "IO exception", e)
            }
        }
    }

    private fun parseHistory(reader: Reader): ArrayList<Sample>? {
        try {
            val history = ArrayList<Sample>()

            for (line in reader.readLines()) {
                val tokens = line.split(",")
                if (tokens.size == 7) {
                    // Without a valid set point the graph doesn't work
                    if (!parseDouble(tokens[1]).isNaN()) {
                        val sample = Sample()

                        // First parameter is the time
                        sample.mTime = tokens[0].toInt()

                        // Second is the set point
                        sample.mSetPoint = tokens[1].toDouble()

                        // Third through sixth are the probe temps
                        for (i in 0 until kNumProbes) {
                            sample.mProbes[i] = parseDouble(tokens[i + 2])
                        }

                        // Seventh is the fan speed/lid open
                        sample.mFanSpeed = tokens[6].toDouble()
                        if (sample.mFanSpeed < 0) {
                            sample.mLidOpen = 1.0
                            sample.mFanSpeed = 0.0
                        } else {
                            sample.mLidOpen = 0.0
                        }

                        history.add(sample)
                    }
                }
            }

            return history
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "IO exception", e)
            }
            return null
        }

    }

    private fun addHistory(history: ArrayList<Sample>): NamedSample? {
        mSamples = history

        mMinTemperature = java.lang.Double.MAX_VALUE
        mMaxTemperature = java.lang.Double.MIN_VALUE

        for (sample in mSamples) {
            mNewestTime = Math.max(mNewestTime, sample.mTime)

            updateMinMax(sample.mSetPoint)

            for (p in 0 until kNumProbes) {
                if (!sample.mProbes[p].isNaN()) {
                    updateMinMax(sample.mProbes[p])
                }
            }
        }

        var latestSample: NamedSample? = null
        if (mSamples.size > 0) {
            latestSample = NamedSample(mSamples[mSamples.size - 1])
            System.arraycopy(mProbeNames, 0, latestSample.mProbeNames, 0, kNumProbes)
        }

        return latestSample
    }

    private fun getUrlReader(urlName: String): BufferedReader? {
        var currentServer = mCurrentServer

        for (i in 0..1) {
            try {
                val url = URL(mServerAddress[currentServer] + urlName)

                val connection = url.openConnection()

                // Set a 5 second timeout, otherwise we can end up waiting minutes for an
                // unreachable server to resolve.
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val reader = BufferedReader(InputStreamReader(connection.getInputStream()))

                // If we made it here then the connection must have succeeded, so make sure the
                // current server matches the one we used
                if (mCurrentServer != currentServer) {
                    mCurrentServer = currentServer
                }

                return reader
            } catch (e: MalformedURLException) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Bad server address")
                }
            } catch (e: UnknownHostException) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Unknown host: " + e.localizedMessage)
                }
            } catch (e: IOException) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "IO exception" + e.localizedMessage)
                }
            } catch (e: IllegalArgumentException) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Argument exception (probably bad port)")
                }
            }

            currentServer = (currentServer + 1) % 2

            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Connection failed, switching to server $currentServer")
            }
        }

        return null
    }

    private fun parseDouble(value: String): Double {
        return try {
            value.toDouble()
        } catch (e: NumberFormatException) {
            Double.NaN
        }

    }

    private fun readerToString(reader: BufferedReader): String? {
        try {
            val builder = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                builder.append(line).append("\n")
                line = reader.readLine()
            }

            return builder.toString()
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }

    }

    fun changePitSetTemp(newTemp: Int) {
        if (!isAuthenticated)
            return

        var setAddr = mServerAddress[mCurrentServer] + "/luci"
        if (mAuthToken != null)
            setAddr += "/;stok=" + mAuthToken!!
        setAddr += "/admin/lm/set?sp=$newTemp"

        var urlConnection: HttpURLConnection? = null

        try {
            val url = URL(setAddr)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.doOutput = true
            urlConnection.requestMethod = "POST"
            urlConnection.setRequestProperty("Cookie", "sysauth=" + mAuthCookie!!)

            urlConnection.inputStream
        } catch (e: MalformedURLException) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Bad server address")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        urlConnection?.disconnect()
    }

    private fun authenticate() {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Attempting authentication")
        }

        var urlConnection: HttpURLConnection? = null

        try {
            val url = URL(mServerAddress[mCurrentServer] + kAuthURL)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.doOutput = true

            urlConnection.doInput = true
            urlConnection.requestMethod = "POST"
            urlConnection.instanceFollowRedirects = false

            val out = DataOutputStream(urlConnection.outputStream)
            out.writeBytes("username=root&")
            out.writeBytes("password=" + URLEncoder.encode(mAdminPassword, "UTF-8"))
            out.flush()
            out.close()

            val cookieHeader = urlConnection.getHeaderField("Set-Cookie")

            // The cookieHeader will be null if we used the wrong password
            if (cookieHeader != null) {
                val cookies = cookieHeader.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                for (cookie in cookies) {
                    val cookieChunks = cookie.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val cookieKey = cookieChunks[0]
                    if (cookieKey == "sysauth") {
                        mAuthCookie = cookieChunks[1]
                    } else if (cookieKey == "stok") {
                        mAuthToken = cookieChunks[1]
                    }
                }
            } else {
                // If we fail to authenticate null out the password, so we won't keep
                // trying. It'll automatically get filled in again if the user changes it
                // in the settings.
                mAdminPassword = null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
        }

        urlConnection?.disconnect()

        mLastStatusMessage = if (isAuthenticated) {
            "Authentication succeeded"
        } else {
            "Authentication failed"
        }
    }
}
