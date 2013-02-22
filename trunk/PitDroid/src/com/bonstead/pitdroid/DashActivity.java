package com.bonstead.pitdroid;

import android.graphics.Color;
import android.os.Bundle;

import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.bonstead.pitdroid.HeaterMeter.NamedSample;
import com.bonstead.pitdroid.R;

public class DashActivity extends SherlockFragment implements HeaterMeter.Listener
{
	private TextView mFanSpeed;
	private TextView[] mProbeNames = new TextView[HeaterMeter.kNumProbes];
	private TextView[] mProbeVals = new TextView[HeaterMeter.kNumProbes];
	private TextView mPitDelta;

	private HeaterMeter mHeaterMeter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                    Bundle savedInstanceState)
    {
    	View view = inflater.inflate(R.layout.activity_dash, container, false);

    	mHeaterMeter = ((PitDroidApplication)this.getActivity().getApplication()).mHeaterMeter;

    	mFanSpeed = (TextView)view.findViewById(R.id.fanSpeedVal);

        mProbeNames[0] = (TextView)view.findViewById(R.id.probe0name);
        mProbeNames[1] = (TextView)view.findViewById(R.id.probe1name);
        mProbeNames[2] = (TextView)view.findViewById(R.id.probe2name);
        mProbeNames[3] = (TextView)view.findViewById(R.id.probe3name);

        mProbeVals[0] = (TextView)view.findViewById(R.id.probe0val);
        mProbeVals[1] = (TextView)view.findViewById(R.id.probe1val);
        mProbeVals[2] = (TextView)view.findViewById(R.id.probe2val);
        mProbeVals[3] = (TextView)view.findViewById(R.id.probe3val);
        
        mPitDelta = (TextView)view.findViewById(R.id.probe0delta);
        
        setAlarmClickListener(view, R.id.probe0Alarm, 0);
        setAlarmClickListener(view, R.id.probe1Alarm, 1);
        setAlarmClickListener(view, R.id.probe2Alarm, 2);
        setAlarmClickListener(view, R.id.probe3Alarm, 3);
        
        updateAlarmButtonImage(view, R.id.probe0Alarm, 0);
        updateAlarmButtonImage(view, R.id.probe1Alarm, 1);
        updateAlarmButtonImage(view, R.id.probe2Alarm, 2);
        updateAlarmButtonImage(view, R.id.probe3Alarm, 3);

        setDefaults();
		
    	mHeaterMeter.addListener(this);
   
        return view;
    }
    
    private void setAlarmClickListener(View view, final int id, final int index)
    {
        ImageButton button = (ImageButton)view.findViewById(id);
        button.setOnClickListener(new OnClickListener()
        {          
            public void onClick(View view)
            {
                DialogFragment dialog = new AlarmDialog();
                
                Bundle bundle = new Bundle();
                bundle.putInt("probeIndex", index);
                dialog.setArguments(bundle);
                
                dialog.show(getFragmentManager(), "AlarmDialog");
                
                updateAlarmButtonImage(view, id, index);
            }
        });
    }

    private void updateAlarmButtonImage(View view, final int id, final int index)
    {
    	ImageButton button = (ImageButton)view.findViewById(id);

    	if (mHeaterMeter.mProbeLoAlarm[index] > 0 || mHeaterMeter.mProbeHiAlarm[index] > 0)
    		button.setImageResource(R.drawable.ic_alarm_set);
    	else
    		button.setImageResource(R.drawable.ic_alarm_unset);
    }

    private void setDefaults()
    {
        mFanSpeed.setText("-");

		for (int p = 0; p < HeaterMeter.kNumProbes; p++)
		{
			mProbeNames[p].setText("-");
			mProbeVals[p].setText("-");
		}
		mPitDelta.setText("-");
    }

    @Override
	public void onDestroyView()
    {
		super.onDestroyView();
		
    	HeaterMeter heaterMeter = ((PitDroidApplication)this.getActivity().getApplication()).mHeaterMeter;
		heaterMeter.removeListener(this);
	}

	@Override
    public void samplesUpdated(final NamedSample latestSample)
    {
		if (latestSample == null)
		{
			setDefaults();
		}
		else
		{
	        mFanSpeed.setText((int)latestSample.mFanSpeed + "%");

			for (int p = 0; p < HeaterMeter.kNumProbes; p++)
			{
				if (latestSample.mProbeNames[p] == null)
					mProbeNames[p].setText("-");
				else
					mProbeNames[p].setText(latestSample.mProbeNames[p] + ": ");
				
				if (Double.isNaN(latestSample.mProbes[p]))
					mProbeVals[p].setText("-");
				else
					mProbeVals[p].setText(mHeaterMeter.formatTemperature(latestSample.mProbes[p]));
				
				if (mHeaterMeter.isAlarmed(p, latestSample.mProbes[p]))
				{
					mProbeVals[p].setTextColor(Color.RED);
				}
				else
				{
					mProbeVals[p].setTextColor(Color.WHITE);
				}
			}
			
			if (Double.isNaN(latestSample.mProbes[0]))
			{
				mPitDelta.setText(mHeaterMeter.formatTemperature(-latestSample.mSetPoint));
			}
			else
			{
				double delta = latestSample.mProbes[0] - latestSample.mSetPoint;
				if (delta > 0)
					mPitDelta.setText("(+" + mHeaterMeter.formatTemperature(delta) + ")");
				else
					mPitDelta.setText("(" + mHeaterMeter.formatTemperature(delta) + ")");
			}
		}
    }
}