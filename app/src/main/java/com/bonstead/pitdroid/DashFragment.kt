package com.bonstead.pitdroid

import android.app.Fragment
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.format.Time
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView

import com.bonstead.pitdroid.AlarmSettingsDialog.AlarmDialogListener
import com.bonstead.pitdroid.HeaterMeter.NamedSample

class DashFragment : Fragment(), HeaterMeter.Listener, AlarmDialogListener {
    private lateinit var mFanSpeed: TextView
    private val mProbeNames = arrayOfNulls<TextView>(HeaterMeter.kNumProbes)
    private val mProbeVals = arrayOfNulls<TextView>(HeaterMeter.kNumProbes)
    private val mProbeTimes = arrayOfNulls<TextView>(HeaterMeter.kNumProbes)
    private lateinit var mPitDelta: TextView

    private lateinit var mLastUpdate: TextView
    private var mServerTime = 0
    private val mTime = Time()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_dash, container, false)

        mFanSpeed = view.findViewById<View>(R.id.fanSpeedVal) as TextView

        mProbeNames[0] = view.findViewById<View>(R.id.probe0Name) as TextView
        mProbeNames[1] = view.findViewById<View>(R.id.probe1Name) as TextView
        mProbeNames[2] = view.findViewById<View>(R.id.probe2Name) as TextView
        mProbeNames[3] = view.findViewById<View>(R.id.probe3Name) as TextView

        mProbeVals[0] = view.findViewById<View>(R.id.probe0Val) as TextView
        mProbeVals[1] = view.findViewById<View>(R.id.probe1Val) as TextView
        mProbeVals[2] = view.findViewById<View>(R.id.probe2Val) as TextView
        mProbeVals[3] = view.findViewById<View>(R.id.probe3Val) as TextView

        mProbeTimes[1] = view.findViewById<View>(R.id.probe1Time) as TextView
        mProbeTimes[2] = view.findViewById<View>(R.id.probe2Time) as TextView
        mProbeTimes[3] = view.findViewById<View>(R.id.probe3Time) as TextView

        mPitDelta = view.findViewById<View>(R.id.probe0Delta) as TextView

        mLastUpdate = view.findViewById<View>(R.id.lastUpdate) as TextView

        val probeIds = intArrayOf(R.id.probe0Alarm, R.id.probe1Alarm, R.id.probe2Alarm, R.id.probe3Alarm)
        for (p in 0 until HeaterMeter.kNumProbes) {
            //setAlarmClickListener(view, probeIds[p], p)
            updateAlarmButtonImage(view, probeIds[p], p)
        }

        setDefaults()

        HeaterMeter.addListener(this)

        return view
    }

    private fun setAlarmClickListener(view: View, id: Int, index: Int) {
        val button = view.findViewById<View>(id) as ImageButton
        button.setOnClickListener {
            val dialog = AlarmSettingsDialog()

            val bundle = Bundle()
            bundle.putInt("probeIndex", index)
            dialog.arguments = bundle

            dialog.mListener = this@DashFragment

            dialog.show(fragmentManager, "AlarmDialog")
        }
    }

    override fun onFinishAlarmDialog(probeIndex: Int) {
        var id = 0

        when (probeIndex) {
            0 -> id = R.id.probe0Alarm
            1 -> id = R.id.probe1Alarm
            2 -> id = R.id.probe2Alarm
            3 -> id = R.id.probe3Alarm
        }

        updateAlarmButtonImage(view, id, probeIndex)

        // Since we may have changed alarm settings, tell the HeaterMeter to write them
        // out
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity
                .baseContext)
        HeaterMeter.preferencesChanged(prefs)

        // Update the alarm service, so it gets stopped if there are no alarms any more,
        // or started if there are now.
        val mainActivity = activity as MainActivity
        mainActivity.updateAlarmService()
    }

    private fun updateAlarmButtonImage(view: View, id: Int, index: Int) {
        val button = view.findViewById<View>(id) as ImageButton

        if (HeaterMeter.mProbeLoAlarm[index] > 0 || HeaterMeter.mProbeHiAlarm[index] > 0) {
            button.setImageResource(R.mipmap.ic_alarm_set)
        } else {
            button.setImageResource(R.mipmap.ic_alarm_unset)
        }
    }

    private fun setDefaults() {
        mFanSpeed.text = "-"

        for (p in 0 until HeaterMeter.kNumProbes) {
            mProbeNames[p]!!.setText("-")
            mProbeVals[p]!!.setText("-")
            if (mProbeTimes[p] != null) {
                mProbeTimes[p]!!.setText("")
            }
        }
        mPitDelta.text = ""
    }

    override fun onDestroyView() {
        super.onDestroyView()

        HeaterMeter.removeListener(this)
    }

    override fun samplesUpdated(latestSample: NamedSample?) {
        if (latestSample == null) {
            setDefaults()
        } else {
            mFanSpeed.text = latestSample.mFanSpeed.toInt().toString() + "%"

            for (p in 0 until HeaterMeter.kNumProbes) {
                if (latestSample.mProbeNames[p] == null) {
                    mProbeNames[p]!!.setText("-")
                } else {
                    mProbeNames[p]!!.setText(latestSample.mProbeNames[p] + ": ")
                }

                if (latestSample.mProbes[p] != Double.NaN) {
                    mProbeVals[p]!!.setText("-")
                } else {
                    mProbeVals[p]!!.setText(HeaterMeter.formatTemperature(latestSample.mProbes[p]))
                }

                if (HeaterMeter.formatAlarm(p, latestSample.mProbes[p]).length > 0) {
                    mProbeVals[p]!!.setTextColor(Color.RED)
                } else {
                    mProbeVals[p]!!.setTextColor(Color.BLACK)
                }

                if (mProbeTimes[p] != null) {
                    val timeUntilAlarm = HeaterMeter.getTemperatureChangeText(p)
                    if (timeUntilAlarm != null) {
                        mProbeTimes[p]!!.setText(timeUntilAlarm)
                    } else {
                        mProbeTimes[p]!!.setText("")
                    }
                }
            }

            if (java.lang.Double.isNaN(latestSample.mProbes[0])) {
                mPitDelta.text = ""
            } else {
                val delta = latestSample.mProbes[0] - latestSample.mSetPoint
                if (delta > 0) {
                    mPitDelta.text = HeaterMeter.formatTemperature(delta) + " above set temp"
                } else {
                    mPitDelta.text = HeaterMeter.formatTemperature(-delta) + " below set temp"
                }
            }

            // Update the last update time
            if (mServerTime < latestSample.mTime) {
                mTime.setToNow()
                mLastUpdate.text = mTime.format("%r")
                mServerTime = latestSample.mTime
            }
        }
    }
}