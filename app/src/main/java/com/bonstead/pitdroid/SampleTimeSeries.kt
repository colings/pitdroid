package com.bonstead.pitdroid

import com.androidplot.xy.XYSeries
import com.bonstead.pitdroid.HeaterMeter.Sample

class SampleTimeSeries(private val mIndex: Int) : XYSeries {

    public override fun getTitle(): String {
        if (mIndex < HeaterMeter.kNumProbes) {
            val probeName = HeaterMeter.mProbeNames[mIndex]
            if (probeName != null) {
                return probeName
            }
        } else if (mIndex == kFanSpeed) {
            return "Fan Speed"
        } else if (mIndex == kLidOpen) {
            return "Lid Open"
        } else if (mIndex == kSetPoint) {
            return "Set Point"
        }

        return "Unknown"
    }

    override fun size(): Int {
        return HeaterMeter.mSamples.size
    }

    override fun getX(index: Int): Number {
        return HeaterMeter.mSamples[index].mTime
    }

    override fun getY(index: Int): Number? {
        val sample = HeaterMeter.mSamples.get(index)

        when {
            (mIndex < HeaterMeter.kNumProbes) -> {
                return if (java.lang.Double.isNaN(sample.mProbes[mIndex])) {
                    null
                } else {
                    HeaterMeter.getNormalized(sample.mProbes[mIndex])
                }
            }
            (mIndex == kFanSpeed) -> return sample.mFanSpeed.toInt() / 100.0
            (mIndex == kLidOpen) -> return sample.mLidOpen
            (mIndex == kSetPoint) -> return HeaterMeter.getNormalized(sample.mSetPoint)
        }

        return null
    }

    companion object {
        internal const val kFanSpeed = HeaterMeter.kNumProbes
        internal const val kLidOpen = HeaterMeter.kNumProbes + 1
        internal const val kSetPoint = HeaterMeter.kNumProbes + 2
    }
}