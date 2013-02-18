package com.bonstead.pitdroid;

import java.util.Iterator;

import com.androidplot.series.XYSeries;
import com.bonstead.pitdroid.HeaterMeter.Sample;
import com.bonstead.pitdroid.HeaterMeter.Sampler;

public class SampleTimeSeries implements XYSeries
{
    private Sampler mSampler;
    private boolean mIsNormalized;
    
    private Iterator<Sample> mIterator;
    private Sample mCurrentValue;
    private int mCurrentIdx;

    public SampleTimeSeries(Sampler sampler, boolean isNormalized)
    {
    	mSampler = sampler;
    	mIsNormalized = isNormalized;
    }
    
    @Override
    public String getTitle()
    {
        return mSampler.mName;
    }

    @Override
    public int size()
    {
        return mSampler.mHistory.size();
    }

    @Override
    public Number getX(int index)
    {
    	return getSample(index).mTime;
    }

    @Override
    public Number getY(int index)
    {
    	double value = getSample(index).mValue;

    	if (mIsNormalized)
    		return mSampler.getNormalized(value);
    	else
    		return value;
    }

    private Sample getSample(int index)
    {
    	if (index == 0)
    	{
    		mIterator = mSampler.mHistory.iterator();
    		mCurrentValue = mIterator.next();
    		mCurrentIdx = 0;
    		return mCurrentValue;
    	}
    	else if (index == mCurrentIdx)
    	{
    		return mCurrentValue;
    	}
    	else if (index == mCurrentIdx + 1)
    	{
    		mCurrentValue = mIterator.next();
    		mCurrentIdx++;
    		return mCurrentValue;
    	}
    	else
    	{
    		System.out.print("Shouldn't hit this");
    		return mSampler.mHistory.get(index);
    	}
    }
}