package com.bonstead.pitdroid;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

public class AlarmSettingsDialog extends DialogFragment
{
	public AlarmDialogListener mListener = null;

	public interface AlarmDialogListener
	{
		void onFinishAlarmDialog(int probeIndex);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		Bundle bundle = this.getArguments();
		final int probeIndex = bundle.getInt("probeIndex", 0);

		// Use the Builder class for convenient dialog construction
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		// Get the layout inflater
		LayoutInflater inflater = getActivity().getLayoutInflater();

		// Inflate and set the layout for the dialog
		// Pass null as the parent view because its going in the dialog layout
		final View view = inflater.inflate(R.layout.dialog_alarm, null);

		final HeaterMeter heaterMeter = ((PitDroidApplication) getActivity().getApplication()).mHeaterMeter;

		builder.setView(view).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				// Get the low temperature
				EditText text = (EditText) view.findViewById(R.id.belowTemp);
				int loValue = Integer.parseInt(text.getText().toString());

				// If the alarm isn't enabled, negate the value
				CheckBox check = (CheckBox) view.findViewById(R.id.belowCheck);
				if (!check.isChecked())
					loValue *= -1;

				// Same with the high temperature
				text = (EditText) view.findViewById(R.id.aboveTemp);
				int hiValue = Integer.parseInt(text.getText().toString());

				check = (CheckBox) view.findViewById(R.id.aboveCheck);
				if (!check.isChecked())
					hiValue *= -1;

				// Set the new settings on the HeaterMeter and tell it to save them
				heaterMeter.mProbeLoAlarm[probeIndex] = loValue;
				heaterMeter.mProbeHiAlarm[probeIndex] = hiValue;

				if (mListener != null)
				{
					mListener.onFinishAlarmDialog(probeIndex);
				}
			}
		}).setNegativeButton(R.string.cancel, null);

		int loVal = heaterMeter.mProbeLoAlarm[probeIndex];
		int hiVal = heaterMeter.mProbeHiAlarm[probeIndex];
		boolean loEnabled = true, hiEnabled = true;
		if (loVal < 0)
		{
			loEnabled = false;
			loVal *= -1;
		}
		if (hiVal < 0)
		{
			hiEnabled = false;
			hiVal *= -1;
		}

		CheckBox check = (CheckBox) view.findViewById(R.id.belowCheck);
		check.setChecked(loEnabled);

		EditText text = (EditText) view.findViewById(R.id.belowTemp);
		text.setText(Integer.toString(loVal));

		check = (CheckBox) view.findViewById(R.id.aboveCheck);
		check.setChecked(hiEnabled);

		text = (EditText) view.findViewById(R.id.aboveTemp);
		text.setText(Integer.toString(hiVal));

		// Create the AlertDialog object and return it
		return builder.create();
	}
}
