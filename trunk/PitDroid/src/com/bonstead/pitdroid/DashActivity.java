package com.bonstead.pitdroid;

import java.text.DecimalFormat;
import java.util.LinkedList;

import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.bonstead.pitdroid.R;
import com.bonstead.pitdroid.HeaterMeter.Sample;

public class DashActivity extends SherlockFragment implements HeaterMeter.Listener
{
	private HeaterMeter mHeaterMeter;

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
        
        mFanSpeed.setText("-");

		for (int p = 0; p < HeaterMeter.kNumProbes; p++)
		{
			mProbeNames[p].setText("-");
			mProbeVals[p].setText("-");
		}
		mPitDelta.setText("-");
		
    	MainActivity main = (MainActivity)container.getContext();
    	mHeaterMeter = main.mHeaterMeter;
    	mHeaterMeter.addListener(this);
   
        return view;
    }
    
    @Override
	public void onDestroyView()
    {
		super.onDestroyView();
		
		mHeaterMeter.removeListener(this);
	}

	@Override
    public void samplesUpdated(final LinkedList<Sample> samples, final String[] names)
    {
		Sample sample = samples.getLast();

        mFanSpeed.setText((int)(sample.mFanSpeed * 100) + "%");

		for (int p = 0; p < HeaterMeter.kNumProbes; p++)
		{
			mProbeNames[p].setText(names[p] + ": ");
			if (Double.isNaN(sample.mProbes[p]))
				mProbeVals[p].setText("-");
			else
				mProbeVals[p].setText(mOneDec.format(sample.mProbes[p]) + "°");
		}
		
		if (Double.isNaN(sample.mProbes[0]))
		{
			mPitDelta.setText(mOneDec.format(-sample.mSetPoint) + "°");
		}
		else
		{
			double delta = sample.mProbes[0] - sample.mSetPoint;
			if (delta > 0)
				mPitDelta.setText("(+" + mOneDec.format(delta) + "°)");
			else
				mPitDelta.setText("(" + mOneDec.format(delta) + "°)");
		}
    }
}