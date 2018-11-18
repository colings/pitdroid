package com.bonstead.pitdroid

import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText

class AlarmSettingsDialog : DialogFragment() {
    var mListener: AlarmDialogListener? = null

    interface AlarmDialogListener {
        fun onFinishAlarmDialog(probeIndex: Int)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bundle = this.arguments
        val probeIndex = bundle.getInt("probeIndex", 0)

        // Use the Builder class for convenient dialog construction
        val builder = AlertDialog.Builder(activity)

        // FIXME: The alert dialog is using the wrong theme so the background and text are black,
        // this works around that.
        builder.setInverseBackgroundForced(true)

        // Get the layout inflater
        val inflater = activity.layoutInflater

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        val view = inflater.inflate(R.layout.dialog_alarm, null)

        builder.setView(view).setPositiveButton(R.string.ok) { _, _ ->
            // Get the low temperature
            var text = view.findViewById<View>(R.id.belowTemp) as EditText
            var loValue = Integer.parseInt(text.text.toString())

            // If the alarm isn't enabled, negate the value
            var check = view.findViewById<View>(R.id.belowCheck) as CheckBox
            if (!check.isChecked) {
                loValue *= -1
            }

            // Same with the high temperature
            text = view.findViewById<View>(R.id.aboveTemp) as EditText
            var hiValue = Integer.parseInt(text.text.toString())

            check = view.findViewById<View>(R.id.aboveCheck) as CheckBox
            if (!check.isChecked) {
                hiValue *= -1
            }

            // Set the new settings on the HeaterMeter and tell it to save them
            HeaterMeter.mProbeLoAlarm[probeIndex] = loValue
            HeaterMeter.mProbeHiAlarm[probeIndex] = hiValue

            if (mListener != null) {
                mListener!!.onFinishAlarmDialog(probeIndex)
            }
        }.setNegativeButton(R.string.cancel, null)

        var loVal = HeaterMeter.mProbeLoAlarm[probeIndex]
        var hiVal = HeaterMeter.mProbeHiAlarm[probeIndex]
        var loEnabled = true
        var hiEnabled = true
        if (loVal < 0) {
            loEnabled = false
            loVal *= -1
        }
        if (hiVal < 0) {
            hiEnabled = false
            hiVal *= -1
        }

        var check = view.findViewById<View>(R.id.belowCheck) as CheckBox
        check.isChecked = loEnabled

        var text = view.findViewById<View>(R.id.belowTemp) as EditText
        text.setText(Integer.toString(loVal))

        check = view.findViewById<View>(R.id.aboveCheck) as CheckBox
        check.isChecked = hiEnabled

        text = view.findViewById<View>(R.id.aboveTemp) as EditText
        text.setText(Integer.toString(hiVal))

        // Create the AlertDialog object and return it
        return builder.create()
    }
}
