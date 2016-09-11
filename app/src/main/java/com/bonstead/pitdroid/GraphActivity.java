package com.bonstead.pitdroid;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;

import com.androidplot.Plot.BorderStyle;
import com.androidplot.ui.Anchor;
import com.androidplot.ui.DynamicTableModel;
import com.androidplot.ui.HorizontalPositioning;
import com.androidplot.ui.Size;
import com.androidplot.ui.SizeMode;
import com.androidplot.ui.VerticalPositioning;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYGraphWidget.LineLabelStyle;
import com.androidplot.xy.XYLegendWidget;
import com.androidplot.xy.XYPlot;

import com.bonstead.pitdroid.HeaterMeter.NamedSample;
import com.bonstead.pitdroid.PanZoomTracker;
import com.bonstead.pitdroid.PanZoomTracker.Range;

public class GraphActivity extends Fragment implements HeaterMeter.Listener,
		OnTouchListener
{
	static final String TAG = "GraphActivity";

	private XYPlot mPlot;
	private SampleTimeSeries mFanSpeed;
	private SampleTimeSeries mLidOpen;
	private SampleTimeSeries[] mProbes = new SampleTimeSeries[HeaterMeter.kNumProbes];
	private SampleTimeSeries mSetPoint;

	private HeaterMeter mHeaterMeter;

	private ScaleGestureDetector scaleGestureDetector;

	static final int INVALID_POINTER_ID = -1;
	private PanZoomTracker mPZT;
	private PointF mLastTouch;
	private int mActivePointerId;
	private float mLastZooming = 1.0f;
	private float mLastPanning = 0.0f;

	// Default panning window size (seconds)
	public final int DEFAULT_DOMAIN_SPAN = 2 * 60 * 60;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mHeaterMeter = ((PitDroidApplication) this.getActivity().getApplication()).mHeaterMeter;
		mHeaterMeter.addListener(this);

		View view = inflater.inflate(R.layout.activity_graph, container, false);

		// initialize our XYPlot reference:
		mPlot = (XYPlot) view.findViewById(R.id.plot);
		mPlot.setOnTouchListener(this);

		// Create a scaleGestureDetector to look for gesture events
		scaleGestureDetector = new ScaleGestureDetector(view.getContext(), new ScaleListener());
		mPZT = ((PitDroidApplication) this.getActivity().getApplication()).mPanZoomTracker;

		mFanSpeed = new SampleTimeSeries(mHeaterMeter, SampleTimeSeries.kFanSpeed);
		mLidOpen = new SampleTimeSeries(mHeaterMeter, SampleTimeSeries.kLidOpen);
		mSetPoint = new SampleTimeSeries(mHeaterMeter, SampleTimeSeries.kSetPoint);
		mProbes[0] = new SampleTimeSeries(mHeaterMeter, 0);
		mProbes[1] = new SampleTimeSeries(mHeaterMeter, 1);
		mProbes[2] = new SampleTimeSeries(mHeaterMeter, 2);
		mProbes[3] = new SampleTimeSeries(mHeaterMeter, 3);

		final int kFanSpeed = getResources().getColor(R.color.fanSpeed);
		final int kLidOpen = getResources().getColor(R.color.lidOpen);
		final int kSetPoint = getResources().getColor(R.color.setPoint);
		final int[] kProbes =
		{
				getResources().getColor(R.color.probe0),
				getResources().getColor(R.color.probe1),
				getResources().getColor(R.color.probe2),
				getResources().getColor(R.color.probe3)
		};
		final int kGraphBackground = getResources().getColor(R.color.graphBackground);

		LineAndPointFormatter lpf;

		lpf = new LineAndPointFormatter(kFanSpeed, null, kFanSpeed, null);
		lpf.getFillPaint().setAlpha(80);
		mPlot.addSeries(mFanSpeed, lpf);

		lpf = new LineAndPointFormatter(kLidOpen, null, kLidOpen, null);
		lpf.getFillPaint().setAlpha(80);
		mPlot.addSeries(mLidOpen, lpf);

		lpf = new LineAndPointFormatter(kSetPoint, null, null, null);
		mPlot.addSeries(mSetPoint, lpf);

		for (int p = 0; p < HeaterMeter.kNumProbes; p++)
		{
			lpf = new LineAndPointFormatter(kProbes[p], null, null, null);
			lpf.getLinePaint().setShadowLayer(2, 1, 1, Color.BLACK);
			mPlot.addSeries(mProbes[p], lpf);
		}

		XYGraphWidget graphWidget = mPlot.getGraph();
		XYLegendWidget legendWidget = mPlot.getLegend();

		LineLabelStyle timeStyle = graphWidget.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM);
		LineLabelStyle tempStyle = graphWidget.getLineLabelStyle(XYGraphWidget.Edge.LEFT);

		// Set up the legend as a vertical stack instead of horizontal, since it ends up too wide otherwise
		legendWidget.setTableModel(new DynamicTableModel(1, mPlot.getSeriesRegistry().size()));

		// Set all the background colors to the same value
		mPlot.getBackgroundPaint().setColor(kGraphBackground);
		graphWidget.getBackgroundPaint().setColor(kGraphBackground);
		graphWidget.getGridBackgroundPaint().setColor(kGraphBackground);

		// Force the range (temperature) to always be from 0-1, since we normalize them
		// so we can display fan speed on the same graph.
		mPlot.setRangeBoundaries(0.0, 1.0, BoundaryMode.FIXED);

		timeStyle.setFormat(new java.text.Format()
		{
			private static final long serialVersionUID = 1L;

			private java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("h:mm a", Locale.US);

			@Override
			public StringBuffer format(Object obj, @NonNull StringBuffer toAppendTo,
									   @NonNull java.text.FieldPosition pos)
			{
				// Because our timestamps are in seconds and SimpleDateFormat expects
				// milliseconds we multiply our timestamp by 1000:
				long timestamp = ((Number) obj).longValue() * 1000;
				java.util.Date date = new java.util.Date(timestamp);
				return dateFormat.format(date, toAppendTo, pos);
			}

			@Override
			public Object parseObject(String source, @NonNull java.text.ParsePosition pos)
			{
				return null;
			}
		});

		tempStyle.setFormat(new java.text.Format()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public StringBuffer format(Object obj, @NonNull StringBuffer toAppendTo,
									   @NonNull java.text.FieldPosition pos)
			{
				double normalizedTemp = ((Number) obj).doubleValue();
				double temp = mHeaterMeter.getOriginal(normalizedTemp);
				toAppendTo.append((int) temp);
				return toAppendTo.append("°");
			}

			@Override
			public Object parseObject(String source, @NonNull java.text.ParsePosition pos)
			{
				return null;
			}
		});

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
		if (latestSample == null)
		{
			// Use dummy time when no sample data is available
			int dummyTime = 0;
			mPZT.domainWindow = new Range<Number>(dummyTime - mPZT.domainWindowSpan, dummyTime);
			mPZT.domainWindowSpan = DEFAULT_DOMAIN_SPAN;
		}
		else
		{
			if (mPZT.domainWindow == null)
			{
				// Initialize panning window if uninitialized or confused
				mPZT.domainWindow = new Range<Number>(latestSample.mTime - mPZT.domainWindowSpan,
						latestSample.mTime);
				mPZT.domainWindowSpan = DEFAULT_DOMAIN_SPAN;
			}
			else if (!mPZT.panning)
			{
				// If most recent sample is visible in the current panning window,
				// shift the panning window to show the updated sample.
				mPZT.domainWindow.setMax(latestSample.mTime);
				mPZT.domainWindow.min = mPZT.domainWindow.max.intValue() - mPZT.domainWindowSpan;
			}

		}
		redrawPlot();
	}

	/*
	 * Update plot boundaries and any visual indicators before redrawing
	 */
	private void redrawPlot()
	{
		// ToDo: Update Panning/Zoom visual indicator
		mPlot.setDomainBoundaries(mPZT.domainWindow.min, mPZT.domainWindow.max, BoundaryMode.FIXED);
		mPlot.redraw();
	}

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener
	{
		@Override
		public boolean onScale(ScaleGestureDetector detector)
		{
			mLastZooming = detector.getScaleFactor();
			zoom(mLastZooming);
			redrawPlot();
			return true;
		}
	}

	@Override
	public boolean onTouch(View arg0, MotionEvent event)
	{
		// Let the ScaleGestureDetector inspect all events
		scaleGestureDetector.onTouchEvent(event);

		switch (event.getAction() & MotionEvent.ACTION_MASK)
		{
		case MotionEvent.ACTION_DOWN:
		{
			mLastTouch = new PointF(event.getX(), event.getY());
			mActivePointerId = event.getPointerId(0);
			break;
		}

		case MotionEvent.ACTION_CANCEL:
		{
			mActivePointerId = INVALID_POINTER_ID;
			break;
		}

		case MotionEvent.ACTION_UP:
		{
			// Start a timer to add inertia to the panning and zooming
			// redraw() is non-blocking, so let the timer trigger on 200ms cycle to
			Timer t = new Timer();
			t.schedule(new TimerTask()
			{
				@Override
				public void run()
				{
					if (Math.abs(mLastPanning) > 1f || Math.abs(mLastZooming - 1) > 0.01f)
					{
						mLastPanning *= .8;
						pan((int) mLastPanning);
						mLastZooming += (1 - mLastZooming) * .1;
						zoom(mLastZooming);
						redrawPlot();
					}
					else
					{
						// the thread lives until the scrolling and zooming are
						// imperceptible
						cancel();
					}
				}
			}, 0, 200);
			break;
		}

		case MotionEvent.ACTION_POINTER_UP:
		{
			final int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
			final int pointerId = event.getPointerId(pointerIndex);
			if (pointerId == mActivePointerId)
			{
				// This was our active pointer going up. Choose a new
				// active pointer and adjust accordingly.
				final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
				mLastTouch.set(event.getX(newPointerIndex), event.getY(newPointerIndex));
				mActivePointerId = event.getPointerId(newPointerIndex);
			}

			if (BuildConfig.DEBUG)
			{
				Log.v(TAG, "Action pointer up: " + mLastPanning + "  lastZooming:" + mLastZooming);
			}

			break;
		}

		case MotionEvent.ACTION_MOVE:
		{
			final int pointerIndex = event.findPointerIndex(mActivePointerId);
			final float x = event.getX(pointerIndex);
			final float y = event.getY(pointerIndex);

			// Only move if the ScaleGestureDetector isn't processing a gesture
			if (!scaleGestureDetector.isInProgress())
			{
				mLastPanning = mLastTouch.x - x;
				pan((int) mLastPanning);
				mPZT.panning = isDomainWindowPanned();
			}
			mLastTouch.set(x, y);
			redrawPlot();
			break;
		}
		}
		return true;
	}

	/*
	 * Returns percentage of zoom percentage
	 */
	public float getScaleFactor()
	{
		return (float) (mPZT.domainWindow.intValue() / mHeaterMeter.getTimeRange().intValue());
	}

	/*
	 * Pan the domain window along the sample set by keeping the domainWindowSpan constant
	 * and recalculating the window bounds.
	 * 
	 * @pan Number of sample values to increase/decrease window
	 */
	private void pan(int pan)
	{
		float step = mPZT.domainWindowSpan / mPlot.getWidth();
		float offset = pan * step;

		Range<Number> timeRange = mHeaterMeter.getTimeRange();

		int newMin, newMax;

		// Clamp to make sure we don't scroll past our first/last sample, then update the
		// other value to match.
		if (offset < 0)
		{
			newMin = Math.max(mPZT.domainWindow.min.intValue() + (int) offset,
					timeRange.min.intValue());
			newMax = newMin + mPZT.domainWindowSpan;
		}
		else
		{
			newMax = Math.min(mPZT.domainWindow.max.intValue() + (int) offset,
					timeRange.max.intValue());
			newMin = newMax - mPZT.domainWindowSpan;
		}

		mPZT.domainWindow.min = newMin;
		mPZT.domainWindow.max = newMax;
	}

	/*
	 * Zoom the domain window
	 * 
	 * Right (max) value remains fixed and Left (lower) value is scaled
	 */
	private void zoom(float deltaScaleFactor)
	{
		mPZT.domainWindowSpan = (int) (mPZT.domainWindowSpan / deltaScaleFactor);

		Range<Number> timeRange = mHeaterMeter.getTimeRange();

		// Don't let the time range go below 1 minute or above our total range
		final int minTimeRange = 60;
		final int maxTimeRange = timeRange.intValue();

		mPZT.domainWindowSpan = Math.max(mPZT.domainWindowSpan, minTimeRange);
		mPZT.domainWindowSpan = Math.min(mPZT.domainWindowSpan, maxTimeRange);

		// Adjust the minimum value to match our new range
		int newMin = mPZT.domainWindow.max.intValue() - mPZT.domainWindowSpan;
		newMin = Math.max(newMin, timeRange.min.intValue());

		int newMax = newMin + mPZT.domainWindowSpan;

		mPZT.domainWindow.min = newMin;
		mPZT.domainWindow.max = newMax;
	}

	/*
	 * Helper function to check whether panning window is showing the most recent samples
	 * or if the window has been panned over to older (historical) data.
	 */
	private boolean isDomainWindowPanned()
	{
		if (mPZT.domainWindow == null)
		{
			return false;
		}
		int latestValue = mHeaterMeter.getTimeRange().max.intValue();
		return (mPZT.domainWindow.max.intValue() < latestValue);
	}
}
