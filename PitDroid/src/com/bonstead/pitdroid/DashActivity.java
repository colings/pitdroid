package com.bonstead.pitdroid;

import java.text.DecimalFormat;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.bonstead.pitdroid.R;

public class DashActivity extends SherlockFragment implements HeaterMeter.Listener
{
	private HeaterMeter mHeaterMeter;
	private TextView[] mProbeNames = new TextView[HeaterMeter.kNumProbes];
	private TextView[] mProbeVals = new TextView[HeaterMeter.kNumProbes];
	private DecimalFormat mOneDec = new DecimalFormat("0.0");

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                    Bundle savedInstanceState)
    {
    	View view = inflater.inflate(R.layout.activity_dash, container, false);

        mProbeNames[0] = (TextView)view.findViewById(R.id.probe0name);
        mProbeNames[1] = (TextView)view.findViewById(R.id.probe1name);
        mProbeNames[2] = (TextView)view.findViewById(R.id.probe2name);
        mProbeNames[3] = (TextView)view.findViewById(R.id.probe3name);

        mProbeVals[0] = (TextView)view.findViewById(R.id.probe0val);
        mProbeVals[1] = (TextView)view.findViewById(R.id.probe1val);
        mProbeVals[2] = (TextView)view.findViewById(R.id.probe2val);
        mProbeVals[3] = (TextView)view.findViewById(R.id.probe3val);
        
		for (int p = 0; p < HeaterMeter.kNumProbes; p++)
		{
			mProbeNames[p].setText("-");
			mProbeVals[p].setText("-");
		}
		
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
    public void samplesUpdated()
    {
		HeaterMeter.PackedSample sample = mHeaterMeter.getNewestSample();
		
		for (int p = 0; p < HeaterMeter.kNumProbes; p++)
		{
			mProbeNames[p].setText(mHeaterMeter.mProbes[p].mName + ": ");
			if (Double.isNaN(sample.mProbes[p]))
				mProbeVals[p].setText("-");
			else
				mProbeVals[p].setText(mOneDec.format(sample.mProbes[p]) + "°");
		}
    }
}