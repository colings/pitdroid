package com.bonstead.pitdroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup

import java.util.ArrayList

// Based on http://mindtherobot.com/blog/272/android-custom-ui-making-a-vintage-thermometer/
class GaugeView : GaugeBaseView {

    // drawing tools
    private var mGaugeRect: RectF = RectF()
    private var mBezelPaint: Paint? = null
    private var mBackgroundPaint: Paint? = null
    private var mRimPaint: Paint? = null

    private var mFaceRect: RectF = RectF()
    private var mRimShadowPaint: Paint? = null

    private var mScalePaint: Paint? = null
    private var mScaleTextPaint: Paint? = null
    private var mScaleRect: RectF = RectF()

    private var mLegendPath: Path? = null
    private var mLegendTextPaint: Paint? = null
    private var mLegendDirty = false

    private var mCachedBackgroundPaint: Paint? = null
    // end drawing tools

    private var mCachedBackground: Bitmap? = null // holds the cached static part

    // scale configuration
    private var mScaleColor = Color.argb(0xd0, 0x09, 0xf0, 0x04)
    var minValue = 0
        private set
    var maxValue = 100
        private set
    private var mTickValue = 5
    private var mSubTicks = 4
    private var mOpenTicks = 2
    private var mTotalTicks: Int = 0
    private var mBezelColor1 = Color.rgb(0xf0, 0xf5, 0xf0)
    private var mBezelColor2 = Color.rgb(0x30, 0x31, 0x30)
    private var mBackgroundColor1 = Color.rgb(0xf0, 0xf5, 0xf0)
    private var mBackgroundColor2 = Color.rgb(0x30, 0x31, 0x30)
    private var mScaleFontSize = 6.0f
    private var mScaleThickness = 0.5f
    private var mScaleOffset = 10f
    private var mLegendOffset = 15f
    private var mRimSize = 2f

    val scaleDiameter: Float
        get() = mScaleRect.width()

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {

        val a = context.obtainStyledAttributes(attrs, R.styleable.GaugeView)
        mScaleColor = a.getColor(R.styleable.GaugeView_scaleColor, mScaleColor)
        minValue = a.getInteger(R.styleable.GaugeView_minValue, minValue)
        maxValue = a.getInteger(R.styleable.GaugeView_maxValue, maxValue)
        mTickValue = a.getInteger(R.styleable.GaugeView_tickValue, mTickValue)
        mOpenTicks = a.getInteger(R.styleable.GaugeView_openTicks, mOpenTicks)
        mSubTicks = a.getInteger(R.styleable.GaugeView_subTicks, mSubTicks)
        mBezelColor1 = a.getColor(R.styleable.GaugeView_bezelColor1, mBezelColor1)
        mBezelColor2 = a.getColor(R.styleable.GaugeView_bezelColor2, mBezelColor2)
        mBackgroundColor1 = a.getColor(R.styleable.GaugeView_backgroundColor1, mBackgroundColor1)
        mBackgroundColor2 = a.getColor(R.styleable.GaugeView_backgroundColor2, mBackgroundColor2)
        mScaleFontSize = a.getFloat(R.styleable.GaugeView_scaleFontSize, mScaleFontSize)
        mScaleThickness = a.getFloat(R.styleable.GaugeView_scaleThickness, mScaleThickness)
        mScaleOffset = a.getFloat(R.styleable.GaugeView_scaleOffset, mScaleOffset)
        mLegendOffset = a.getFloat(R.styleable.GaugeView_legendOffset, mLegendOffset)
        mRimSize = a.getFloat(R.styleable.GaugeView_rimSize, mRimSize)
        a.recycle()

        init()
    }

    private fun init() {
        minValue = roundToTick(minValue)
        maxValue = roundToTick(maxValue)

        if (maxValue <= minValue) {
            maxValue = minValue + mTickValue
        }

        mTotalTicks = (maxValue - minValue) / mTickValue + mOpenTicks
    }

    fun updateRange(minValue: Int, maxValue: Int) {
        this.minValue = minValue
        this.maxValue = maxValue
        init()
    }

    fun nameChanged() {
        mLegendDirty = true
        invalidate()
    }

    private fun roundToTick(value: Int): Int {
        return Math.ceil((value / mTickValue).toDouble()).toInt() * mTickValue
    }

    override fun initDrawingTools() {
        val scale = width.toFloat()
        val relativeScale = scale / 100f

        // The rim has a thickness, so decrease the draw rect by that to ensure it doesn't get clipped
        val rimThickness = mScaleThickness * relativeScale * 0.5f

        mGaugeRect = RectF(rimThickness, rimThickness, scale - rimThickness, scale - rimThickness)

        // the linear gradient is a bit skewed for realism
        mBezelPaint = Paint()
        mBezelPaint!!.isAntiAlias = true
        if (!isInEditMode) {
            mBezelPaint!!.shader = LinearGradient(0.40f * scale, 0.0f * scale, 0.60f * scale, 1.0f * scale,
                    mBezelColor1,
                    mBezelColor2,
                    Shader.TileMode.CLAMP)
        } else {
            mBezelPaint!!.style = Paint.Style.FILL
            mBezelPaint!!.color = mBezelColor1
        }

        mBackgroundPaint = Paint()
        mBackgroundPaint!!.isAntiAlias = true
        if (!isInEditMode) {
            mBackgroundPaint!!.shader = LinearGradient(0.40f * scale, 0.0f * scale, 0.60f * scale, 1.0f * scale,
                    mBackgroundColor1,
                    mBackgroundColor2,
                    Shader.TileMode.CLAMP)
        } else {
            mBackgroundPaint!!.style = Paint.Style.FILL
            mBackgroundPaint!!.color = mBackgroundColor1
        }

        mRimPaint = Paint()
        mRimPaint!!.isAntiAlias = true
        mRimPaint!!.style = Paint.Style.STROKE
        mRimPaint!!.color = Color.argb(0x4f, 0x33, 0x36, 0x33)
        mRimPaint!!.strokeWidth = mScaleThickness * relativeScale

        val rimSize = mRimSize * relativeScale
        mFaceRect.set(mGaugeRect.left + rimSize, mGaugeRect.top + rimSize,
                mGaugeRect.right - rimSize, mGaugeRect.bottom - rimSize)

        mRimShadowPaint = Paint()
        mRimShadowPaint!!.shader = RadialGradient(
                0.5f * scale, 0.5f * scale, mFaceRect.width() / 2.0f,
                intArrayOf(0x00000000, 0x00000500, 0x50000500),
                floatArrayOf(0.96f, 0.96f, 0.99f),
                Shader.TileMode.MIRROR)
        mRimShadowPaint!!.style = Paint.Style.FILL

        mScalePaint = Paint()
        mScalePaint!!.style = Paint.Style.STROKE
        mScalePaint!!.color = mScaleColor
        mScalePaint!!.strokeWidth = mScaleThickness * relativeScale
        mScalePaint!!.isAntiAlias = true

        mScaleTextPaint = Paint()
        mScaleTextPaint!!.isAntiAlias = true
        mScaleTextPaint!!.textSize = mScaleFontSize * relativeScale
        mScaleTextPaint!!.typeface = Typeface.SANS_SERIF
        mScaleTextPaint!!.textScaleX = 0.8f
        mScaleTextPaint!!.textAlign = Paint.Align.CENTER

        val scalePosition = mScaleOffset * relativeScale
        mScaleRect = RectF(mFaceRect.left + scalePosition, mFaceRect.top + scalePosition,
                mFaceRect.right - scalePosition, mFaceRect.bottom - scalePosition)

        val legendPosition = mLegendOffset * relativeScale
        val legendRect = RectF(mFaceRect.left + legendPosition, mFaceRect.top + legendPosition,
                mFaceRect.right - legendPosition, mFaceRect.bottom - legendPosition)
        mLegendPath = Path()
        mLegendPath!!.addArc(legendRect, -180.0f, -180.0f)

        mLegendTextPaint = Paint()
        mLegendTextPaint!!.isAntiAlias = true
        mLegendTextPaint!!.textSize = mScaleFontSize * relativeScale
        mLegendTextPaint!!.typeface = Typeface.SANS_SERIF
        mLegendTextPaint!!.textScaleX = 0.8f

        mCachedBackgroundPaint = Paint()
        mCachedBackgroundPaint!!.isFilterBitmap = true

        regenerateBackground()
    }

    private fun drawScale(canvas: Canvas) {
        val scale = width.toFloat()

        val openDegrees = mOpenTicks / mTotalTicks.toFloat() * 360.0f

        canvas.drawArc(mScaleRect, 90.0f + openDegrees * 0.5f, 360.0f - openDegrees, false, mScalePaint!!)

        canvas.save()

        // We want to start drawing from the bottom center, so first flip the canvas around so
        // that's on top.
        canvas.rotate(180f + openDegrees * 0.5f, 0.5f * scale, 0.5f * scale)

        val tickAngleIncrement = 360f / mTotalTicks
        val subTickAngleIncrement = tickAngleIncrement / (mSubTicks + 1)

        val numSteps = mTotalTicks - mOpenTicks + 1

        for (i in 0 until numSteps) {
            val y1 = mScaleRect.top
            var y2 = y1 - 0.020f * scale

            canvas.drawLine(0.5f * scale, y1, 0.5f * scale, y2, mScalePaint!!)

            val curValue = minValue + i * mTickValue
            val valueString = Integer.toString(curValue)
            canvas.drawText(valueString, 0.5f * scale, y2 - 0.015f * scale, mScaleTextPaint!!)

            if (i < numSteps - 1) {
                for (j in 0 until mSubTicks) {
                    y2 = y1 - 0.010f * scale

                    canvas.rotate(subTickAngleIncrement, 0.5f * scale, 0.5f * scale)
                    canvas.drawLine(0.5f * scale, y1, 0.5f * scale, y2, mScalePaint!!)
                }

                canvas.rotate(subTickAngleIncrement, 0.5f * scale, 0.5f * scale)
            }
        }

        canvas.restore()
    }

    private fun drawLegend(canvas: Canvas) {
        val mHands = ArrayList<GaugeHandView>()

        val row = parent as ViewGroup
        for (i in 0 until row.childCount) {
            val view = row.getChildAt(i)
            if (view is GaugeHandView) {
                mHands.add(view)
            }
        }

        val space = mLegendTextPaint!!.measureText("  ")

        val lengths = FloatArray(mHands.size)
        var totalLength = 0f
        var firstText = true

        for (i in mHands.indices) {
            val hand = mHands[i]

            if (hand.name != null) {
                if (firstText) {
                    firstText = false
                } else {
                    totalLength += space
                }

                lengths[i] = mLegendTextPaint!!.measureText(hand.name)

                totalLength += lengths[i]
            } else {
                lengths[i] = 0f
            }
        }

        val measure = PathMeasure(mLegendPath, false)
        var currentOffset = (measure.length - totalLength) / 2f

        for (i in mHands.indices) {
            val hand = mHands[i]

            if (hand.name != null) {
                mLegendTextPaint!!.color = hand.color
                canvas.drawTextOnPath(hand.name!!, mLegendPath!!, currentOffset, 0f, mLegendTextPaint!!)
                currentOffset += lengths[i] + space
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (mLegendDirty) {
            mLegendDirty = false
            regenerateBackground()
        }

        if (mCachedBackground == null) {
            Log.w(TAG, "Background not created")
        } else {
            canvas.drawBitmap(mCachedBackground!!, 0f, 0f, mCachedBackgroundPaint)
        }
    }

    private fun regenerateBackground() {
        // free the old bitmap
        if (mCachedBackground != null) {
            mCachedBackground!!.recycle()
        }

        mCachedBackground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val backgroundCanvas = Canvas(mCachedBackground!!)

        // first, draw the bezel
        backgroundCanvas.drawOval(mGaugeRect, mBezelPaint!!)
        // now the outer rim circle
        backgroundCanvas.drawOval(mGaugeRect, mRimPaint!!)
        // draw the gauge background
        backgroundCanvas.drawOval(mFaceRect, mBackgroundPaint!!)
        // draw the inner rim circle
        backgroundCanvas.drawOval(mFaceRect, mRimPaint!!)

        // draw the rim shadow inside the face
        if (!isInEditMode)
            backgroundCanvas.drawOval(mFaceRect, mRimShadowPaint!!)

        drawScale(backgroundCanvas)

        drawLegend(backgroundCanvas)
    }

    fun clampValue(value: Float): Float {
        return Math.max(minValue.toFloat(), Math.min(maxValue.toFloat(), value))
    }

    // Converts a gauge value to a 0-360 degree angle value
    // 0 = gauge absolute min value (including open ticks), 360 = gauge absolute max value
    fun valueToAngle(value: Float): Float {
        val clampedVal = clampValue(value)

        val actualMin = minValue - mOpenTicks.toFloat() * mTickValue.toFloat() * 0.5f
        val actualMax = maxValue + mOpenTicks.toFloat() * mTickValue.toFloat() * 0.5f
        val scalar = (clampedVal - actualMin) / (actualMax - actualMin)
        return scalar * 360f
    }

    fun angleToValue(degrees: Float): Float {
        var clampedVal = degrees % 360f
        if (clampedVal < 0f)
            clampedVal = 360f - clampedVal

        val actualMin = minValue - mOpenTicks.toFloat() * mTickValue.toFloat() * 0.5f
        val actualMax = maxValue + mOpenTicks.toFloat() * mTickValue.toFloat() * 0.5f
        val scalar = clampedVal / 360f
        return actualMin + (actualMax - actualMin) * scalar
    }

    companion object {
        private val TAG = GaugeView::class.java.simpleName
    }
}
