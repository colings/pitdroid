package com.bonstead.pitdroid;

import android.app.Fragment;
import android.os.Bundle;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.bonstead.pitdroid.HeaterMeter.NamedSample;

public class GaugeFragment extends Fragment implements HeaterMeter.Listener
{
	private GaugeHandView[] mProbeHands = new GaugeHandView[HeaterMeter.kNumProbes];
	private GaugeHandView mSetPoint;

	private HeaterMeter mHeaterMeter;
	private TextView mLastUpdate;
	private int mServerTime = 0;
	private Time mTime = new Time();
	private boolean mSettingPit = false;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.fragment_gauge, container, false);

		mHeaterMeter = ((PitDroidApplication) this.getActivity().getApplication()).mHeaterMeter;

		mProbeHands[0] = (GaugeHandView) view.findViewById(R.id.pitHand);
		mProbeHands[1] = (GaugeHandView) view.findViewById(R.id.probe1Hand);
		mProbeHands[2] = (GaugeHandView) view.findViewById(R.id.probe2Hand);
		mProbeHands[3] = (GaugeHandView) view.findViewById(R.id.probe3Hand);

		mSetPoint = (GaugeHandView) view.findViewById(R.id.setPoint);
		mSetPoint.mListener = new GaugeHandView.Listener()
		{
			@Override
			public void onValueChanged(final float value)
			{
				mSettingPit = true;

				Thread trd = new Thread(new Runnable()
				{
					@Override
					public void run()
					{
						mHeaterMeter.changePitSetTemp((int)value);
						mSettingPit = false;
					}
				});
				trd.start();
			}
		};

		mLastUpdate = (TextView) view.findViewById(R.id.lastUpdate);

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
	public void samplesUpdated(final NamedSample latestSample)
	{
		if (latestSample != null)
		{
			for (int p = 0; p < HeaterMeter.kNumProbes; p++)
			{
				if (Double.isNaN(latestSample.mProbes[p]))
				{
					mProbeHands[p].setVisibility(View.GONE);
					mProbeHands[p].setHandTarget(0.f);
				}
				else
				{
					mProbeHands[p].setVisibility(View.VISIBLE);
					mProbeHands[p].setHandTarget((float) latestSample.mProbes[p]);
				}

				// Don't set the name on the pit temp hand, we don't want to show it in the legend
				if (p > 0)
				{
					mProbeHands[p].setName(latestSample.mProbeNames[p]);
				}
			}

			if (!Double.isNaN(latestSample.mSetPoint) && !mSetPoint.isDragging() && !mSettingPit)
				mSetPoint.setHandTarget((float)latestSample.mSetPoint);

			// Update the last update time
			if (mServerTime < latestSample.mTime)
			{
				mTime.setToNow();
				mLastUpdate.setText(mTime.format("%r"));
				mServerTime = latestSample.mTime;
			}
		}
	}
}