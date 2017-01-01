package com.bonstead.pitdroid;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Fragment;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;

import com.androidplot.ui.DynamicTableModel;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYGraphWidget.LineLabelStyle;
import com.androidplot.xy.XYLegendWidget;
import com.androidplot.xy.XYPlot;

import com.bonstead.pitdroid.HeaterMeter.NamedSample;

public class GraphFragment extends Fragment implements HeaterMeter.Listener,
		OnTouchListener
{
	static final String TAG = "GraphFragment";

	private XYPlot mPlot;
	private SampleTimeSeries mFanSpeed;
	private SampleTimeSeries mLidOpen;
	private SampleTimeSeries[] mProbes = new SampleTimeSeries[HeaterMeter.kNumProbes];
	private SampleTimeSeries mSetPoint;

	private HeaterMeter mHeaterMeter;

	private ScaleGestureDetector scaleGestureDetector;

	static final int INVALID_POINTER_ID = -1;
	private PointF mLastTouch;
	private int mActivePointerId;
	private float mLastZooming = 1.0f;
	private float mLastPanning = 0.0f;

	// Default panning window size (seconds)
	public final int DEFAULT_DOMAIN_SPAN = 2 * 60 * 60;
	private int mDomainWindowSpan = DEFAULT_DOMAIN_SPAN;
	private int mDomainWindowMin = 0;
	private int mDomainWindowMax = 0;
	private boolean mIsPanning = false;

	static final String DOMAIN_WINDOW_SPAN = "domainSpan";
	static final String DOMAIN_WINDOW_MIN = "domainMin";
	static final String DOMAIN_WINDOW_MAX = "domainMax";
	static final String IS_PANNING = "isPanning";

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mHeaterMeter = ((PitDroidApplication) this.getActivity().getApplication()).mHeaterMeter;

		View view = inflater.inflate(R.layout.fragment_graph, container, false);

		// initialize our XYPlot reference:
		mPlot = (XYPlot) view.findViewById(R.id.plot);
		mPlot.setOnTouchListener(this);

		// Create a scaleGestureDetector to look for gesture events
		scaleGestureDetector = new ScaleGestureDetector(view.getContext(), new ScaleListener());

		if (savedInstanceState != null)
		{
			mDomainWindowSpan = savedInstanceState.getInt(DOMAIN_WINDOW_SPAN);
			mDomainWindowMin = savedInstanceState.getInt(DOMAIN_WINDOW_MIN);
			mDomainWindowMax = savedInstanceState.getInt(DOMAIN_WINDOW_MAX);
			mIsPanning = savedInstanceState.getBoolean(IS_PANNING);
		}

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
			public StringBuffer format(Object obj, StringBuffer toAppendTo,
									   java.text.FieldPosition pos)
			{
				// Because our timestamps are in seconds and SimpleDateFormat expects
				// milliseconds we multiply our timestamp by 1000:
				long timestamp = ((Number) obj).longValue() * 1000;
				java.util.Date date = new java.util.Date(timestamp);
				return dateFormat.format(date, toAppendTo, pos);
			}

			@Override
			public Object parseObject(String source, java.text.ParsePosition pos)
			{
				return null;
			}
		});

		tempStyle.setFormat(new java.text.Format()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public StringBuffer format(Object obj, StringBuffer toAppendTo,
									   java.text.FieldPosition pos)
			{
				double normalizedTemp = ((Number) obj).doubleValue();
				double temp = mHeaterMeter.getOriginal(normalizedTemp);
				toAppendTo.append((int) temp);
				return toAppendTo.append("°");
			}

			@Override
			public Object parseObject(String source, java.text.ParsePosition pos)
			{
				return null;
			}
		});

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		mHeaterMeter.addListener(this);
	}

	@Override
	public void onPause()
	{
		super.onPause();

		mHeaterMeter.removeListener(this);
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState)
	{
		savedInstanceState.putInt(DOMAIN_WINDOW_SPAN, mDomainWindowSpan);
		savedInstanceState.putInt(DOMAIN_WINDOW_MIN, mDomainWindowMin);
		savedInstanceState.putInt(DOMAIN_WINDOW_MAX, mDomainWindowMax);
		savedInstanceState.putBoolean(IS_PANNING, mIsPanning);

		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public void samplesUpdated(final NamedSample latestSample)
	{
		if (latestSample == null)
		{
			// Use dummy time when no sample data is available
			mDomainWindowMin = 0;
			mDomainWindowMax = DEFAULT_DOMAIN_SPAN;
			mDomainWindowSpan = DEFAULT_DOMAIN_SPAN;
		}
		else
		{
			if (!mIsPanning)
			{
				// If most recent sample is visible in the current panning window,
				// shift the panning window to show the updated sample.
				mDomainWindowMin = latestSample.mTime - mDomainWindowSpan;
				mDomainWindowMax = latestSample.mTime;
			}

		}
		redrawPlot();
	}

	/*
	 * Update plot boundaries and any visual indicators before redrawing
	 */
	private void redrawPlot()
	{
		mPlot.setDomainBoundaries(mDomainWindowMin, mDomainWindowMax, BoundaryMode.FIXED);
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
				mIsPanning = isDomainWindowPanned();
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
		return (float) ((mDomainWindowMax - mDomainWindowMin) / (mHeaterMeter.getMaxTime() - mHeaterMeter.getMinTime()));
	}

	/*
	 * Pan the domain window along the sample set by keeping the domainWindowSpan constant
	 * and recalculating the window bounds.
	 * 
	 * @pan Number of sample values to increase/decrease window
	 */
	private void pan(int pan)
	{
		float step = mDomainWindowSpan / mPlot.getWidth();
		float offset = pan * step;

		int newMin, newMax;

		// Clamp to make sure we don't scroll past our first/last sample, then update the
		// other value to match.
		if (offset < 0)
		{
			newMin = Math.max(mDomainWindowMin + (int) offset, mHeaterMeter.getMinTime());
			newMax = newMin + mDomainWindowSpan;
		}
		else
		{
			newMax = Math.min(mDomainWindowMax + (int) offset, mHeaterMeter.getMaxTime());
			newMin = newMax - mDomainWindowSpan;
		}

		mDomainWindowMin = newMin;
		mDomainWindowMax = newMax;
	}

	/*
	 * Zoom the domain window
	 * 
	 * Right (max) value remains fixed and Left (lower) value is scaled
	 */
	private void zoom(float deltaScaleFactor)
	{
		mDomainWindowSpan = (int) (mDomainWindowSpan / deltaScaleFactor);

		int minTime = mHeaterMeter.getMinTime();
		int maxTime = mHeaterMeter.getMaxTime();

		// Don't let the time range go below 1 minute or above our total range
		final int minTimeRange = 60;
		final int maxTimeRange = maxTime - minTime;

		mDomainWindowSpan = Math.max(mDomainWindowSpan, minTimeRange);
		mDomainWindowSpan = Math.min(mDomainWindowSpan, maxTimeRange);

		// Adjust the minimum value to match our new range
		int newMin = mDomainWindowMax - mDomainWindowSpan;
		newMin = Math.max(newMin, minTime);

		int newMax = newMin + mDomainWindowSpan;

		mDomainWindowMin = newMin;
		mDomainWindowMax = newMax;
	}

	/*
	 * Helper function to check whether panning window is showing the most recent samples
	 * or if the window has been panned over to older (historical) data.
	 */
	private boolean isDomainWindowPanned()
	{
		int latestValue = mHeaterMeter.getMaxTime();
		return (mDomainWindowMax < latestValue);
	}
}
