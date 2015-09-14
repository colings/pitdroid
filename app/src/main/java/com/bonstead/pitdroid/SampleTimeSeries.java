package com.bonstead.pitdroid;

import com.androidplot.xy.XYSeries;
import com.bonstead.pitdroid.HeaterMeter.Sample;

public class SampleTimeSeries implements XYSeries
{
	static final int kFanSpeed = HeaterMeter.kNumProbes;
	static final int kLidOpen = HeaterMeter.kNumProbes + 1;
	static final int kSetPoint = HeaterMeter.kNumProbes + 2;

	private HeaterMeter mHeaterMeter;
	private int mIndex;

	public SampleTimeSeries(HeaterMeter heatermeter, int index)
	{
		mHeaterMeter = heatermeter;
		mIndex = index;
	}

	@Override
	public String getTitle()
	{
		if (mIndex < HeaterMeter.kNumProbes)
		{
			return mHeaterMeter.mProbeNames[mIndex];
		}
		else if (mIndex == kFanSpeed)
		{
			return "Fan Speed";
		}
		else if (mIndex == kLidOpen)
		{
			return "Lid Open";
		}
		else if (mIndex == kSetPoint)
		{
			return "Set Point";
		}

		return null;
	}

	@Override
	public int size()
	{
		return mHeaterMeter.mSamples.size();
	}

	@Override
	public Number getX(int index)
	{
		return mHeaterMeter.mSamples.get(index).mTime;
	}

	@Override
	public Number getY(int index)
	{
		Sample sample = mHeaterMeter.mSamples.get(index);

		if (mIndex < HeaterMeter.kNumProbes)
		{
			if (Double.isNaN(sample.mProbes[mIndex]))
				return null;
			else
				return mHeaterMeter.getNormalized(sample.mProbes[mIndex]);
		}
		else if (mIndex == kFanSpeed)
		{
			return (int) sample.mFanSpeed / 100.0;
		}
		else if (mIndex == kLidOpen)
		{
			return sample.mLidOpen;
		}
		else if (mIndex == kSetPoint)
		{
			return mHeaterMeter.getNormalized(sample.mSetPoint);
		}

		return null;
	}
}