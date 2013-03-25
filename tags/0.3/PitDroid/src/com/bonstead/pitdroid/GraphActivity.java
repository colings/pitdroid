package com.bonstead.pitdroid;

import android.os.Bundle;

import com.androidplot.Plot.BorderStyle;
import com.androidplot.ui.AnchorPosition;
import com.androidplot.ui.SizeLayoutType;
import com.androidplot.ui.SizeMetrics;
import com.androidplot.xy.*;
import android.graphics.Color;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.actionbarsherlock.app.SherlockFragment;
import com.bonstead.pitdroid.HeaterMeter.NamedSample;
import com.bonstead.pitdroid.R;

public class GraphActivity extends SherlockFragment implements HeaterMeter.Listener
{
	private XYPlot mPlot;
    private SampleTimeSeries mFanSpeed;
    private SampleTimeSeries mLidOpen;
    private SampleTimeSeries[] mProbes = new SampleTimeSeries[HeaterMeter.kNumProbes];
    private SampleTimeSeries mSetPoint;

    private HeaterMeter mHeaterMeter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                    Bundle savedInstanceState)
    {
    	mHeaterMeter = ((PitDroidApplication)this.getActivity().getApplication()).mHeaterMeter;
    	mHeaterMeter.addListener(this);

    	View view = inflater.inflate(R.layout.activity_graph, container, false);

        // initialize our XYPlot reference:
        mPlot = (XYPlot) view.findViewById(R.id.plot);

        mFanSpeed = new SampleTimeSeries(mHeaterMeter, SampleTimeSeries.kFanSpeed);
        mLidOpen = new SampleTimeSeries(mHeaterMeter, SampleTimeSeries.kLidOpen);
        mSetPoint = new SampleTimeSeries(mHeaterMeter, SampleTimeSeries.kSetPoint);
        mProbes[0] = new SampleTimeSeries(mHeaterMeter, 0);
        mProbes[1] = new SampleTimeSeries(mHeaterMeter, 1);
        mProbes[2] = new SampleTimeSeries(mHeaterMeter, 2);
        mProbes[3] = new SampleTimeSeries(mHeaterMeter, 3);
        
        final int kFanSpeed = Color.rgb(102, 204, 255);
        final int kLidOpen = Color.rgb(255, 221, 153);
        final int kSetPoint = Color.rgb(255, 0, 0);
        final int[] kProbes = { Color.rgb(238, 119, 51),
        						Color.rgb(102, 204, 51),
        						Color.rgb(34, 153, 119),
        						Color.rgb(119, 136, 153) };
        final int kGraphBackground = Color.rgb(34, 68, 102);

        PointLabelFormatter plf = null;
        LineAndPointFormatter lpf = null;
        
        lpf = new LineAndPointFormatter(kFanSpeed, null, kFanSpeed, plf);
        lpf.getFillPaint().setAlpha(80);
        mPlot.addSeries(mFanSpeed, lpf);

        lpf = new LineAndPointFormatter(kLidOpen, null, kLidOpen, plf);
        lpf.getFillPaint().setAlpha(80);
        mPlot.addSeries(mLidOpen, lpf);
        
        lpf = new LineAndPointFormatter(kSetPoint, null, null, plf);
        mPlot.addSeries(mSetPoint, lpf);

        for (int p = HeaterMeter.kNumProbes - 1; p >= 0; p--)
        {
        	lpf = new LineAndPointFormatter(kProbes[p], null, null, plf);
        	lpf.getLinePaint().setShadowLayer(2, 1, 1, Color.BLACK);
        	mPlot.addSeries(mProbes[p], lpf);
        }

        // Remove the title, domain, and range labels
        mPlot.getLayoutManager().remove(mPlot.getTitleWidget());
        mPlot.getLayoutManager().remove(mPlot.getDomainLabelWidget());
        mPlot.getLayoutManager().remove(mPlot.getRangeLabelWidget());

        // Adjust the legend so it stretches across the entire screen, since we've got a
        // lot of text to fit there
        mPlot.getLegendWidget().setSize(new SizeMetrics(20, SizeLayoutType.ABSOLUTE, 0, SizeLayoutType.FILL));
        mPlot.position(
                mPlot.getLegendWidget(),
                    0,
                    XLayoutStyle.ABSOLUTE_FROM_LEFT,
                    0,
                    YLayoutStyle.ABSOLUTE_FROM_BOTTOM,
                    AnchorPosition.LEFT_BOTTOM);

        // Turn off the borders
        mPlot.setBorderStyle(BorderStyle.NONE, null, null);

        // Max out the size of the graph widget, so it fills the screen
        mPlot.getGraphWidget().setSize(new SizeMetrics(0, SizeLayoutType.FILL,
                0, SizeLayoutType.FILL));
        mPlot.getLayoutManager().position(mPlot.getGraphWidget(), 0, XLayoutStyle.ABSOLUTE_FROM_LEFT,
                    0, YLayoutStyle.ABSOLUTE_FROM_TOP);
        
        // Set all the background colors to the same value
        mPlot.getBackgroundPaint().setColor(kGraphBackground);
        mPlot.getGraphWidget().getBackgroundPaint().setColor(kGraphBackground);
        mPlot.getGraphWidget().getGridBackgroundPaint().setColor(kGraphBackground);

        // Boost up the top margin a bit, so the text for the highest value doesn't get
        // cut off
        mPlot.getGraphWidget().setMarginTop(10);
        mPlot.getGraphWidget().setMarginRight(10);
        // Add some extra room on the bottom, so the legend doesn't overlap
        mPlot.getGraphWidget().setMarginBottom(20);        

        // Force the range (temperature) to always be from 0-1, since we normalize them
        // so we can display fan speed on the same graph.
        mPlot.setRangeBoundaries(0.0, 1.0, BoundaryMode.FIXED);

        mPlot.setDomainValueFormat( new java.text.Format()
	        {
				private static final long serialVersionUID = 1L;
				// create a simple date format that draws on the year portion of our timestamp.
	            // see http://download.oracle.com/javase/1.4.2/docs/api/java/text/SimpleDateFormat.html
	            // for a full description of SimpleDateFormat.
	            private java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("HH:mm");//"hh:mm a");
	
	            @Override
	            public StringBuffer format(Object obj, StringBuffer toAppendTo, java.text.FieldPosition pos)
	            {
	                // because our timestamps are in seconds and SimpleDateFormat expects milliseconds
	                // we multiply our timestamp by 1000:
	                long timestamp = ((Number) obj).longValue() * 1000;
	                java.util.Date date = new java.util.Date(timestamp);
	                return dateFormat.format(date, toAppendTo, pos);
	            }
	
	            @Override
	            public Object parseObject(String source, java.text.ParsePosition pos)
	            {
	                return null;
	            }
	        } );

        mPlot.setRangeValueFormat( new java.text.Format()
	        {
        		private static final long serialVersionUID = 1L;

	            @Override
	            public StringBuffer format(Object obj, StringBuffer toAppendTo, java.text.FieldPosition pos)
	            {
	                double normalizedTemp = ((Number) obj).doubleValue();
	                double temp = mHeaterMeter.getOriginal(normalizedTemp);
	                toAppendTo.append((int)temp);
	                return toAppendTo.append("°");
	            }
	
	            @Override
	            public Object parseObject(String source, java.text.ParsePosition pos)
	            {
	                return null;
	            }
	        } );

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
    	mPlot.redraw();
	}
}
