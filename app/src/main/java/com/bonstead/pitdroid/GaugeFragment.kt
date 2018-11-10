package com.bonstead.pitdroid

import android.app.AlertDialog
import android.app.Fragment
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.format.Time
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView

import com.bonstead.pitdroid.HeaterMeter.NamedSample

class GaugeFragment : Fragment(), HeaterMeter.Listener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var mGauge: GaugeView
    private val mProbeHands = arrayOfNulls<GaugeHandView>(HeaterMeter.kNumProbes)
    private lateinit var mSetPoint: GaugeHandView

    private lateinit var mLastUpdate: TextView
    private var mServerTime = 0
    private val mTime = Time()
    private var mSettingPit = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_gauge, container, false)

        mGauge = view.findViewById<View>(R.id.thermometer) as GaugeView
        mProbeHands[0] = view.findViewById<View>(R.id.pitHand) as GaugeHandView
        mProbeHands[1] = view.findViewById<View>(R.id.probe1Hand) as GaugeHandView
        mProbeHands[2] = view.findViewById<View>(R.id.probe2Hand) as GaugeHandView
        mProbeHands[3] = view.findViewById<View>(R.id.probe3Hand) as GaugeHandView

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val masterLayout = view.findViewById<View>(R.id.masterLayout) as LinearLayout
            masterLayout.orientation = LinearLayout.HORIZONTAL

            val gaugeLayout = view.findViewById<View>(R.id.gaugeLayout) as LinearLayout
            gaugeLayout.orientation = LinearLayout.HORIZONTAL
        }

        mSetPoint = view.findViewById<View>(R.id.setPoint) as GaugeHandView
        mSetPoint.mListener = object : GaugeHandView.Listener {
            override fun onValueChanged(value: Float) {
                mSettingPit = true

                val setTempView = inflater.inflate(R.layout.dialog_settemp, null)

                val picker = setTempView.findViewById<View>(R.id.temperature) as NumberPicker
                picker.minValue = mGauge.minValue
                picker.maxValue = mGauge.maxValue
                picker.value = value.toInt()

                val builder = AlertDialog.Builder(activity)
                builder.setView(setTempView)
                builder.setTitle("New pit set temp")
                builder.setPositiveButton("Set") { _dialog, _id ->
                    val newTemp = picker.value

                    val trd = Thread(Runnable {
                        HeaterMeter.changePitSetTemp(newTemp)
                        mSettingPit = false
                    })
                    trd.start()
                }
                .setNegativeButton("Cancel") { _dialog, _id -> mSettingPit = false }
                .create().show()
            }
        }

        mLastUpdate = view.findViewById<View>(R.id.lastUpdate) as TextView

        return view
    }

    override fun onResume() {
        super.onResume()

        HeaterMeter.addListener(this)

        val prefs = PreferenceManager.getDefaultSharedPreferences(activity.application.baseContext)
        updatePrefs(prefs)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()

        val prefs = PreferenceManager.getDefaultSharedPreferences(activity.application.baseContext)
        prefs.unregisterOnSharedPreferenceChangeListener(this)

        HeaterMeter.removeListener(this)
    }

    override fun samplesUpdated(latestSample: NamedSample?) {
        if (latestSample != null) {
            for (p in 0 until HeaterMeter.kNumProbes) {
                if (java.lang.Double.isNaN(latestSample.mProbes[p])) {
                    mProbeHands[p]!!.setVisibility(View.GONE)
                    mProbeHands[p]!!.setHandTarget(0f)
                } else {
                    mProbeHands[p]!!.setVisibility(View.VISIBLE)
                    mProbeHands[p]!!.setHandTarget(latestSample.mProbes[p].toFloat())
                }

                // Don't set the name on the pit temp hand, or any probes that aren't connected, so
                // we wont' show them on the legend
                if (p > 0 && latestSample.mProbes[p] != Double.NaN) {
                    mProbeHands[p]!!.name = latestSample.mProbeNames[p]
                }
            }

            if (latestSample.mSetPoint != Double.NaN && !mSetPoint.isDragging && !mSettingPit)
                mSetPoint.setHandTarget(latestSample.mSetPoint.toFloat())

            // Update the last update time
            if (mServerTime < latestSample.mTime) {
                mTime.setToNow()
                mLastUpdate.text = mTime.format("%r")
                mServerTime = latestSample.mTime
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updatePrefs(sharedPreferences)
    }

    private fun updatePrefs(sharedPreferences: SharedPreferences) {
//        val minTemp = Integer.valueOf(sharedPreferences.getString(SettingsFragment.getKEY_MIN_TEMP(), "50"))
//        val maxTemp = Integer.valueOf(sharedPreferences.getString(SettingsFragment.getKEY_MAX_TEMP(), "350"))

 //       mGauge!!.updateRange(minTemp, maxTemp)
    }
}