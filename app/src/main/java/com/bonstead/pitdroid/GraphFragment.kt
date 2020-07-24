package com.bonstead.pitdroid

import java.util.Locale
import java.util.Timer
import java.util.TimerTask

import androidx.fragment.app.Fragment
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import androidx.core.content.ContextCompat

import com.androidplot.ui.DynamicTableModel
import com.androidplot.xy.BoundaryMode
import com.androidplot.xy.LineAndPointFormatter
import com.androidplot.xy.XYGraphWidget
import com.androidplot.xy.XYGraphWidget.LineLabelStyle
import com.androidplot.xy.XYLegendWidget
import com.androidplot.xy.XYPlot

import com.bonstead.pitdroid.HeaterMeter.NamedSample

class GraphFragment : Fragment(), HeaterMeter.Listener, OnTouchListener {

    private var mPlot: XYPlot? = null
    private var mFanSpeed: SampleTimeSeries? = null
    private var mLidOpen: SampleTimeSeries? = null
    private val mProbes = arrayOfNulls<SampleTimeSeries>(HeaterMeter.kNumProbes)
    private var mSetPoint: SampleTimeSeries? = null

    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var mLastTouch: PointF? = null
    private var mActivePointerId: Int = 0
    private var mLastZooming = 1.0f
    private var mLastPanning = 0.0f

    // Default panning window size (seconds)
    val DEFAULT_DOMAIN_SPAN = 2 * 60 * 60
    private var mDomainWindowSpan = DEFAULT_DOMAIN_SPAN
    private var mDomainWindowMin = 0
    private var mDomainWindowMax = 0
    private var mIsPanning = false

    /*
	 * Returns percentage of zoom percentage
	 */
    val scaleFactor: Float
        get() = ((mDomainWindowMax - mDomainWindowMin) / (HeaterMeter.maxTime - HeaterMeter.minTime)).toFloat()

    /*
	 * Helper function to check whether panning window is showing the most recent samples
	 * or if the window has been panned over to older (historical) data.
	 */
    private val isDomainWindowPanned: Boolean
        get() {
            val latestValue = HeaterMeter.maxTime
            return mDomainWindowMax < latestValue
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_graph, container, false)

        // initialize our XYPlot reference:
        mPlot = view.findViewById<View>(R.id.plot) as XYPlot
        mPlot!!.setOnTouchListener(this)

        // Create a scaleGestureDetector to look for gesture events
        scaleGestureDetector = ScaleGestureDetector(view.context, ScaleListener())

        if (savedInstanceState != null) {
            mDomainWindowSpan = savedInstanceState.getInt(DOMAIN_WINDOW_SPAN)
            mDomainWindowMin = savedInstanceState.getInt(DOMAIN_WINDOW_MIN)
            mDomainWindowMax = savedInstanceState.getInt(DOMAIN_WINDOW_MAX)
            mIsPanning = savedInstanceState.getBoolean(IS_PANNING)
        }

        mFanSpeed = SampleTimeSeries(SampleTimeSeries.kFanSpeed)
        mLidOpen = SampleTimeSeries(SampleTimeSeries.kLidOpen)
        mSetPoint = SampleTimeSeries(SampleTimeSeries.kSetPoint)
        mProbes[0] = SampleTimeSeries(0)
        mProbes[1] = SampleTimeSeries(1)
        mProbes[2] = SampleTimeSeries(2)
        mProbes[3] = SampleTimeSeries(3)

        val kFanSpeed = ContextCompat.getColor(requireContext(), R.color.fanSpeed)
        val kLidOpen = ContextCompat.getColor(requireContext(), R.color.lidOpen)
        val kSetPoint = ContextCompat.getColor(requireContext(), R.color.setPoint)
        val kProbes = intArrayOf(
            ContextCompat.getColor(requireContext(), R.color.probe0),
            ContextCompat.getColor(requireContext(), R.color.probe1),
            ContextCompat.getColor(requireContext(), R.color.probe2),
            ContextCompat.getColor(requireContext(), R.color.probe3))
        val kGraphBackground = ContextCompat.getColor(requireContext(), R.color.graphBackground)

        var lpf: LineAndPointFormatter

        lpf = LineAndPointFormatter(kFanSpeed, null, kFanSpeed, null)
        lpf.fillPaint.setAlpha(80)
        mPlot!!.addSeries(mFanSpeed, lpf)

        lpf = LineAndPointFormatter(kLidOpen, null, kLidOpen, null)
        lpf.fillPaint.setAlpha(80)
        mPlot!!.addSeries(mLidOpen, lpf)

        lpf = LineAndPointFormatter(kSetPoint, null, null, null)
        mPlot!!.addSeries(mSetPoint, lpf)

        for (p in 0 until HeaterMeter.kNumProbes) {
            lpf = LineAndPointFormatter(kProbes[p], null, null, null)
            lpf.linePaint.setShadowLayer(2.0f, 1.0f, 1.0f, Color.BLACK)
            mPlot!!.addSeries(mProbes[p], lpf)
        }

        val graphWidget = mPlot!!.getGraph()
        val legendWidget = mPlot!!.getLegend()

        val timeStyle = graphWidget.getLineLabelStyle(XYGraphWidget.Edge.BOTTOM)
        val tempStyle = graphWidget.getLineLabelStyle(XYGraphWidget.Edge.LEFT)

        // Set up the legend as a vertical stack instead of horizontal, since it ends up too wide otherwise
        legendWidget.setTableModel(DynamicTableModel(1, mPlot!!.getRegistry().size()))

        // Set all the background colors to the same value
        mPlot!!.getBackgroundPaint().setColor(kGraphBackground)
        graphWidget.getBackgroundPaint().setColor(kGraphBackground)
        graphWidget.getGridBackgroundPaint().setColor(kGraphBackground)

        // Force the range (temperature) to always be from 0-1, since we normalize them
        // so we can display fan speed on the same graph.
        mPlot!!.setRangeBoundaries(0.0, 1.0, BoundaryMode.FIXED)

        timeStyle.setFormat(object : java.text.Format() {
            private val serialVersionUID = 1L

            private val dateFormat = java.text.SimpleDateFormat("h:mm a", Locale.US)

            override fun format(obj: Any, toAppendTo: StringBuffer,
                                pos: java.text.FieldPosition): StringBuffer {
                // Because our timestamps are in seconds and SimpleDateFormat expects
                // milliseconds we multiply our timestamp by 1000:
                val timestamp = (obj as Number).toLong() * 1000
                val date = java.util.Date(timestamp)
                return dateFormat.format(date, toAppendTo, pos)
            }

            override fun parseObject(source: String, pos: java.text.ParsePosition): Any? {
                return null
            }
        })

        tempStyle.format = object : java.text.Format() {
            private val serialVersionUID = 1L

            override fun format(obj: Any, toAppendTo: StringBuffer,
                                pos: java.text.FieldPosition): StringBuffer {
                val normalizedTemp = (obj as Number).toDouble()
                val temp = HeaterMeter.getOriginal(normalizedTemp)
                toAppendTo.append(temp.toInt())
                return toAppendTo.append("°")
            }

            override fun parseObject(source: String, pos: java.text.ParsePosition): Any? {
                return null
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()

        HeaterMeter.addListener(this)
    }

    override fun onPause() {
        super.onPause()

        HeaterMeter.removeListener(this)
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putInt(DOMAIN_WINDOW_SPAN, mDomainWindowSpan)
        savedInstanceState.putInt(DOMAIN_WINDOW_MIN, mDomainWindowMin)
        savedInstanceState.putInt(DOMAIN_WINDOW_MAX, mDomainWindowMax)
        savedInstanceState.putBoolean(IS_PANNING, mIsPanning)

        super.onSaveInstanceState(savedInstanceState)
    }

    override fun samplesUpdated(latestSample: NamedSample?) {
        if (latestSample == null) {
            // Use dummy time when no sample data is available
            mDomainWindowMin = 0
            mDomainWindowMax = DEFAULT_DOMAIN_SPAN
            mDomainWindowSpan = DEFAULT_DOMAIN_SPAN
        } else {
            if (!mIsPanning) {
                // If most recent sample is visible in the current panning window,
                // shift the panning window to show the updated sample.
                mDomainWindowMin = latestSample.mTime - mDomainWindowSpan
                mDomainWindowMax = latestSample.mTime
            }

        }
        redrawPlot()
    }

    /*
	 * Update plot boundaries and any visual indicators before redrawing
	 */
    private fun redrawPlot() {
        mPlot!!.setDomainBoundaries(mDomainWindowMin, mDomainWindowMax, BoundaryMode.FIXED)
        mPlot!!.redraw()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            mLastZooming = detector.scaleFactor
            zoom(mLastZooming)
            redrawPlot()
            return true
        }
    }

    override fun onTouch(arg0: View, event: MotionEvent): Boolean {
        // Let the ScaleGestureDetector inspect all events
        scaleGestureDetector!!.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mLastTouch = PointF(event.x, event.y)
                mActivePointerId = event.getPointerId(0)
            }

            MotionEvent.ACTION_CANCEL -> {
                mActivePointerId = INVALID_POINTER_ID
            }

            MotionEvent.ACTION_UP -> {
                // Start a timer to add inertia to the panning and zooming
                // redraw() is non-blocking, so let the timer trigger on 200ms cycle to
                val t = Timer()
                t.schedule(object : TimerTask() {
                    override fun run() {
                        if (Math.abs(mLastPanning) > 1f || Math.abs(mLastZooming - 1) > 0.01f) {
                            mLastPanning *= .8f
                            pan(mLastPanning.toInt())
                            mLastZooming += ((1 - mLastZooming) * .1).toFloat()
                            zoom(mLastZooming)
                            redrawPlot()
                        } else {
                            // the thread lives until the scrolling and zooming are
                            // imperceptible
                            cancel()
                        }
                    }
                }, 0, 200)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == mActivePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    mLastTouch!!.set(event.getX(newPointerIndex), event.getY(newPointerIndex))
                    mActivePointerId = event.getPointerId(newPointerIndex)
                }

                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "Action pointer up: $mLastPanning  lastZooming:$mLastZooming")
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(mActivePointerId)
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                // Only move if the ScaleGestureDetector isn't processing a gesture
                if (!scaleGestureDetector!!.isInProgress) {
                    mLastPanning = mLastTouch!!.x - x
                    pan(mLastPanning.toInt())
                    mIsPanning = isDomainWindowPanned
                }
                mLastTouch!!.set(x, y)
                redrawPlot()
            }
        }
        return true
    }

    /*
	 * Pan the domain window along the sample set by keeping the domainWindowSpan constant
	 * and recalculating the window bounds.
	 *
	 * @pan Number of sample values to increase/decrease window
	 */
    private fun pan(pan: Int) {
        val step = mDomainWindowSpan / mPlot!!.getWidth()
        val offset = pan * step

        val newMin: Int
        val newMax: Int

        // Clamp to make sure we don't scroll past our first/last sample, then update the
        // other value to match.
        if (offset < 0) {
            newMin = (mDomainWindowMin + offset.toInt()).coerceAtLeast(HeaterMeter.minTime)
            newMax = newMin + mDomainWindowSpan
        } else {
            newMax = (mDomainWindowMax + offset.toInt()).coerceAtMost(HeaterMeter.maxTime)
            newMin = newMax - mDomainWindowSpan
        }

        mDomainWindowMin = newMin
        mDomainWindowMax = newMax
    }

    /*
	 * Zoom the domain window
	 *
	 * Right (max) value remains fixed and Left (lower) value is scaled
	 */
    private fun zoom(deltaScaleFactor: Float) {
        mDomainWindowSpan = (mDomainWindowSpan / deltaScaleFactor).toInt()

        val minTime = HeaterMeter.minTime
        val maxTime = HeaterMeter.maxTime

        // Don't let the time range go below 1 minute or above our total range
        val minTimeRange = 60
        val maxTimeRange = maxTime - minTime

        mDomainWindowSpan = Math.max(mDomainWindowSpan, minTimeRange)
        mDomainWindowSpan = Math.min(mDomainWindowSpan, maxTimeRange)

        // Adjust the minimum value to match our new range
        var newMin = mDomainWindowMax - mDomainWindowSpan
        newMin = Math.max(newMin, minTime)

        val newMax = newMin + mDomainWindowSpan

        mDomainWindowMin = newMin
        mDomainWindowMax = newMax
    }

    companion object {
        internal const val TAG = "GraphFragment"

        internal const val INVALID_POINTER_ID = -1

        internal const val DOMAIN_WINDOW_SPAN = "domainSpan"
        internal const val DOMAIN_WINDOW_MIN = "domainMin"
        internal const val DOMAIN_WINDOW_MAX = "domainMax"
        internal const val IS_PANNING = "isPanning"
    }
}
