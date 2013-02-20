package com.bonstead.pitdroid;

import java.text.DecimalFormat;

import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
	private DecimalFormat mOneDec = new DecimalFormat("0.0");

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                    Bundle savedInstanceState)
    {
    	View view = inflater.inflate(R.layout.activity_dash, container, false);

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
        
        setDefaults();
		
    	HeaterMeter heaterMeter = ((PitDroidApplication)this.getActivity().getApplication()).mHeaterMeter;
    	heaterMeter.addListener(this);
   
        return view;
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
					mProbeVals[p].setText(mOneDec.format(latestSample.mProbes[p]) + "°");
			}
			
			if (Double.isNaN(latestSample.mProbes[0]))
			{
				mPitDelta.setText(mOneDec.format(-latestSample.mSetPoint) + "°");
			}
			else
			{
				double delta = latestSample.mProbes[0] - latestSample.mSetPoint;
				if (delta > 0)
					mPitDelta.setText("(+" + mOneDec.format(delta) + "°)");
				else
					mPitDelta.setText("(" + mOneDec.format(delta) + "°)");
			}
		}
    }
}