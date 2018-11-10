package com.bonstead.pitdroid

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

class GaugeHandView : GaugeBaseView {

    var name: String? = null
        set(name) {
            if (mGauge == null)
                return

            if (this.name == null && name != null ||
                    this.name != null && name == null ||
                    this.name != null && name != null && this.name != name) {
                field = name
                mGauge!!.nameChanged(this)
            }
        }

    private var mGauge: GaugeView? = null

    private lateinit var mHandPaint: Paint
    private lateinit var mHandPath: Path
    private lateinit var mHandScrewPaint: Paint

    // hand dynamics -- all are angular expressed in gauge values
    private var mHandInitialized = false
    private var mHandPosition = 0.0f
    private var mHandTarget = 0.0f
    private var mHandVelocity = 0.0f
    private var mHandAcceleration = 0.0f
    private var mLastHandMoveTime = -1L

    var color = Color.rgb(0x39, 0x2f, 0x02c)
    private var mHandWidth = 15f
    private var mHandLength = 95f
    private var mHandStyle = 0
    private var mInterpolateChanges = true

    private var mPreviousRotation = 0f
    var isDragging = false
        private set

    var mListener: Listener? = null

    public interface Listener {
        fun onValueChanged(value: Float)
    }

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {

        val a = context.obtainStyledAttributes(attrs, R.styleable.GaugeHandView)
        color = a.getColor(R.styleable.GaugeHandView_handColor, color)
        mHandLength = a.getFloat(R.styleable.GaugeHandView_handLength, mHandLength)
        mHandWidth = a.getFloat(R.styleable.GaugeHandView_handWidth, mHandWidth)
        mHandStyle = a.getInteger(R.styleable.GaugeHandView_handStyle, mHandStyle)
        mInterpolateChanges = a.getBoolean(R.styleable.GaugeHandView_interpolateChanges, mInterpolateChanges)
        a.recycle()
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val bundle = state as Bundle
        val superState = bundle.getParcelable<Parcelable>("superState")
        super.onRestoreInstanceState(superState)

        mHandInitialized = bundle.getBoolean("handInitialized")
        mHandPosition = bundle.getFloat("handPosition")
        mHandTarget = bundle.getFloat("handTarget")
        mHandVelocity = bundle.getFloat("handVelocity")
        mHandAcceleration = bundle.getFloat("handAcceleration")
        mLastHandMoveTime = bundle.getLong("lastHandMoveTime")
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()

        val state = Bundle()
        state.putParcelable("superState", superState)
        state.putBoolean("handInitialized", mHandInitialized)
        state.putFloat("handPosition", mHandPosition)
        state.putFloat("handTarget", mHandTarget)
        state.putFloat("handVelocity", mHandVelocity)
        state.putFloat("handAcceleration", mHandAcceleration)
        state.putLong("lastHandMoveTime", mLastHandMoveTime)
        return state
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // Search through the sibling views for the gauge
        val row = parent as ViewGroup
        for (i in 0 until row.childCount) {
            val view = row.getChildAt(i)
            if (view is GaugeView) {
                mGauge = view
                break
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        mGauge = null
    }

    override fun initDrawingTools() {
        val scale = width.toFloat()

        mHandPaint = Paint()
        mHandPaint.isAntiAlias = true
        mHandPaint.color = color
        if (!isInEditMode) {
            mHandPaint.setShadowLayer(0.01f * scale, -0.005f * scale, -0.005f * scale, 0x7f000000)
        }
        mHandPaint.style = Paint.Style.FILL

        // Converts a 0-100 value to a 0-1 value where one represents the distance from the hand pivot to the scale
        val handLengthScalar = mGauge!!.scaleDiameter / scale * 0.5f * 0.01f
        val halfWidth = mHandWidth * handLengthScalar * 0.5f

        // Y 0 = top (tip of hand), 1 = bottom (back of hand)
        mHandPath = Path()
        if (mHandStyle == 0) {
            val frontLength = mHandLength * handLengthScalar
            val backLength = frontLength * 0.5f

            mHandPath.moveTo((0.5f - halfWidth * 0.2f) * scale, (0.5f - frontLength) * scale)
            mHandPath.lineTo((0.5f + halfWidth * 0.2f) * scale, (0.5f - frontLength) * scale)
            mHandPath.lineTo((0.5f + halfWidth) * scale, (0.5f + backLength) * scale)
            mHandPath.lineTo((0.5f - halfWidth) * scale, (0.5f + backLength) * scale)
            mHandPath.close()
        } else if (mHandStyle == 1) {
            val yTip = (scale - mGauge!!.scaleDiameter) * 0.5f
            val length = mHandLength * handLengthScalar

            mHandPath.moveTo((0.5f - halfWidth * 0.2f) * scale, yTip)
            mHandPath.lineTo((0.5f + halfWidth * 0.2f) * scale, yTip)
            mHandPath.lineTo((0.5f + halfWidth) * scale, yTip - length * scale)
            mHandPath.lineTo((0.5f - halfWidth) * scale, yTip - length * scale)
            mHandPath.close()
        } else {
            val yTip = scale * 0.5f
            val length = mHandLength * handLengthScalar

            mHandPath.moveTo((0.5f - halfWidth) * scale, 0f)
            mHandPath.lineTo((0.5f + halfWidth) * scale, 0f)
            mHandPath.lineTo((0.5f + halfWidth * 0.2f) * scale, length * scale)
            mHandPath.lineTo((0.5f - halfWidth * 0.2f) * scale, length * scale)
            mHandPath.close()
        }

        mHandScrewPaint = Paint()
        mHandScrewPaint.isAntiAlias = true
        mHandScrewPaint.color = -0xb6c0c4
        mHandScrewPaint.style = Paint.Style.FILL

        mHandInitialized = true
    }

    override fun onDraw(canvas: Canvas) {
        if (mHandInitialized) {
            val scale = width.toFloat()
            val handAngle = 180f + mGauge!!.valueToAngle(mHandPosition)

            canvas.save()
            canvas.rotate(handAngle, 0.5f * scale, 0.5f * scale)

            canvas.drawPath(mHandPath, mHandPaint)

            if (mHandStyle == 0)
                canvas.drawCircle(0.5f * scale, 0.5f * scale, 0.01f * scale, mHandScrewPaint)

            canvas.restore()
        }

        if (handNeedsToMove()) {
            moveHand()
        }
    }

    private fun positionToRotation(x: Float, y: Float): Float {
        val centerX = (width / 2).toDouble()
        val centerY = (height / 2).toDouble()

        val adjustedX = x - centerX
        val adjustedY = y - centerY

        val rad = Math.atan2(-adjustedY, adjustedX)

        var deg = Math.toDegrees(rad)

        // atan2 returns values in the -180 to 180 range, we want it 0-360
        deg += 180.0

        // Polar coordinates are counter-clockwise, switch to clockwise
        deg = 360 - deg

        // Polar coordinates put 0 at the right side, we want it at the bottom
        deg += 90.0

        // Bring the value back into the 0-360 range
        deg %= 360

        return deg.toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mHandStyle != 2)
            return false

        val curRotation = positionToRotation(event.x, event.y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val handRotation = mGauge!!.valueToAngle(mHandPosition)
                val scale = width.toFloat()
                val handLengthScalar = mGauge!!.scaleDiameter / scale * 0.5f * 0.01f
                val width = mHandWidth * handLengthScalar
                val length = mHandLength * handLengthScalar

                val matrix = Matrix()
                matrix.preRotate(180f + handRotation, scale / 2, scale / 2)

                val points = floatArrayOf(scale / 2, length * scale / 2)
                matrix.mapPoints(points)

                val dist = PointF.length(event.x - points[0], event.y - points[1])

                if (dist <= width * scale * 2f) {
                    isDragging = true
                }
            }

            MotionEvent.ACTION_MOVE -> if (isDragging) {
                val delta = curRotation - mPreviousRotation

                val newAngle = mGauge!!.valueToAngle(mHandPosition) + delta
                mHandPosition = mGauge!!.angleToValue(newAngle)
                mHandTarget = mHandPosition

                invalidate()
            }

            MotionEvent.ACTION_UP -> if (isDragging) {
                isDragging = false

                if (mListener != null) {
                    mListener!!.onValueChanged(mHandPosition)
                }
            }
        }

        mPreviousRotation = curRotation

        return true
    }

    private fun handNeedsToMove(): Boolean {
        return Math.abs(mHandPosition - mHandTarget) > 0.01f
    }

    private fun moveHand() {
        if (!handNeedsToMove()) {
            return
        }

        if (!mInterpolateChanges) {
            mHandPosition = mHandTarget
            return
        }

        if (mLastHandMoveTime != -1L) {
            val currentTime = System.currentTimeMillis()
            val delta = (currentTime - mLastHandMoveTime) / 1000.0f

            val direction = Math.signum(mHandVelocity)
            if (Math.abs(mHandVelocity) < 90.0f) {
                mHandAcceleration = 5.0f * (mHandTarget - mHandPosition)
            } else {
                mHandAcceleration = 0.0f
            }
            mHandPosition += mHandVelocity * delta
            mHandVelocity += mHandAcceleration * delta
            if ((mHandTarget - mHandPosition) * direction < 0.01f * direction) {
                mHandPosition = mHandTarget
                mHandVelocity = 0.0f
                mHandAcceleration = 0.0f
                mLastHandMoveTime = -1L
            } else {
                mLastHandMoveTime = System.currentTimeMillis()
            }
            invalidate()
        } else {
            mLastHandMoveTime = System.currentTimeMillis()
            moveHand()
        }
    }

    fun setHandTarget(value: Float) {
        var clamped = value
        if (mGauge != null)
            clamped = mGauge!!.clampValue(value)

        mHandTarget = value
        mHandInitialized = true
        invalidate()
    }

    companion object {
        private val TAG = GaugeHandView::class.java.simpleName
    }
}
