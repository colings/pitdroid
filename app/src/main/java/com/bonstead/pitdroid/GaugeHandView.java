package com.bonstead.pitdroid;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class GaugeHandView extends GaugeBaseView
{
	private static final String TAG = GaugeHandView.class.getSimpleName();

	private String mName;

	private GaugeView mGauge;

	private Paint mHandPaint;
	private Path mHandPath;
	private Paint mHandScrewPaint;

	// hand dynamics -- all are angular expressed in gauge values
	private boolean mHandInitialized = false;
	private float mHandPosition = 0.0f;
	private float mHandTarget = 0.0f;
	private float mHandVelocity = 0.0f;
	private float mHandAcceleration = 0.0f;
	private long mLastHandMoveTime = -1L;

	private int mHandColor = Color.rgb(0x39, 0x2f, 0x02c);
	private float mHandWidth = 15.f;
	private float mHandLength = 95.f;
	private int mHandStyle = 0;
	private boolean mInterpolateChanges = true;

	private float mPreviousRotation = 0.f;
	private boolean mDragging = false;

	interface Listener
	{
		void onValueChanged(final float value);
	}

	public Listener mListener;

	public GaugeHandView(Context context)
	{
		super(context);
	}

	public GaugeHandView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GaugeHandView);
		mHandColor = a.getColor(R.styleable.GaugeHandView_handColor, mHandColor);
		mHandLength = a.getFloat(R.styleable.GaugeHandView_handLength, mHandLength);
		mHandWidth = a.getFloat(R.styleable.GaugeHandView_handWidth, mHandWidth);
		mHandStyle = a.getInteger(R.styleable.GaugeHandView_handStyle, mHandStyle);
		mInterpolateChanges = a.getBoolean(R.styleable.GaugeHandView_interpolateChanges, mInterpolateChanges);
		a.recycle();
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state)
	{
		Bundle bundle = (Bundle) state;
		Parcelable superState = bundle.getParcelable("superState");
		super.onRestoreInstanceState(superState);

		mHandInitialized = bundle.getBoolean("handInitialized");
		mHandPosition = bundle.getFloat("handPosition");
		mHandTarget = bundle.getFloat("handTarget");
		mHandVelocity = bundle.getFloat("handVelocity");
		mHandAcceleration = bundle.getFloat("handAcceleration");
		mLastHandMoveTime = bundle.getLong("lastHandMoveTime");
	}

	@Override
	protected Parcelable onSaveInstanceState()
	{
		Parcelable superState = super.onSaveInstanceState();

		Bundle state = new Bundle();
		state.putParcelable("superState", superState);
		state.putBoolean("handInitialized", mHandInitialized);
		state.putFloat("handPosition", mHandPosition);
		state.putFloat("handTarget", mHandTarget);
		state.putFloat("handVelocity", mHandVelocity);
		state.putFloat("handAcceleration", mHandAcceleration);
		state.putLong("lastHandMoveTime", mLastHandMoveTime);
		return state;
	}

	@Override
	protected void onAttachedToWindow()
	{
		super.onAttachedToWindow();

		// Search through the sibling views for the gauge
		ViewGroup row = (ViewGroup) getParent();
		for (int i = 0; i < row.getChildCount(); i++)
		{
			View view = row.getChildAt(i);
			if (view instanceof GaugeView)
			{
				mGauge = (GaugeView) view;
				break;
			}
		}
	}

	@Override
	protected void onDetachedFromWindow()
	{
		super.onDetachedFromWindow();

		mGauge = null;
	}

	@Override
	protected void initDrawingTools()
	{
		final float scale = getWidth();

		mHandPaint = new Paint();
		mHandPaint.setAntiAlias(true);
		mHandPaint.setColor(mHandColor);
		if (!isInEditMode())
		{
			mHandPaint.setShadowLayer(0.01f * scale, -0.005f * scale, -0.005f * scale, 0x7f000000);
		}
		mHandPaint.setStyle(Paint.Style.FILL);

		// Converts a 0-100 value to a 0-1 value where one represents the distance from the hand pivot to the scale
		final float handLengthScalar = (mGauge.getScaleDiameter() / scale) * 0.5f * 0.01f;
		final float halfWidth = mHandWidth * handLengthScalar * 0.5f;

		// Y 0 = top (tip of hand), 1 = bottom (back of hand)
		mHandPath = new Path();
		if (mHandStyle == 0)
		{
			final float frontLength = mHandLength * handLengthScalar;
			final float backLength = frontLength * 0.5f;

			mHandPath.moveTo((0.5f - halfWidth * 0.2f) * scale, (0.5f - frontLength) * scale);
			mHandPath.lineTo((0.5f + halfWidth * 0.2f) * scale, (0.5f - frontLength) * scale);
			mHandPath.lineTo((0.5f + halfWidth) * scale, (0.5f + backLength) * scale);
			mHandPath.lineTo((0.5f - halfWidth) * scale, (0.5f + backLength) * scale);
			mHandPath.close();
		}
		else if (mHandStyle == 1)
		{
			final float yTip = (scale - mGauge.getScaleDiameter()) * 0.5f;
			final float length = mHandLength * handLengthScalar;

			mHandPath.moveTo((0.5f - halfWidth * 0.2f) * scale, yTip);
			mHandPath.lineTo((0.5f + halfWidth * 0.2f) * scale, yTip);
			mHandPath.lineTo((0.5f + halfWidth) * scale, yTip - (length * scale));
			mHandPath.lineTo((0.5f - halfWidth) * scale, yTip - (length * scale));
			mHandPath.close();
		}
		else
		{
			final float yTip = scale * 0.5f;
			final float length = mHandLength * handLengthScalar;

			mHandPath.moveTo((0.5f - halfWidth) * scale, 0);
			mHandPath.lineTo((0.5f + halfWidth) * scale, 0);
			mHandPath.lineTo((0.5f + halfWidth * 0.2f) * scale, (length * scale));
			mHandPath.lineTo((0.5f - halfWidth * 0.2f) * scale, (length * scale));
			mHandPath.close();
		}

		mHandScrewPaint = new Paint();
		mHandScrewPaint.setAntiAlias(true);
		mHandScrewPaint.setColor(0xff493f3c);
		mHandScrewPaint.setStyle(Paint.Style.FILL);

		mHandInitialized = true;
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		if (mHandInitialized)
		{
			float scale = (float) getWidth();
			float handAngle = 180.f + mGauge.valueToAngle(mHandPosition);

			canvas.save(Canvas.MATRIX_SAVE_FLAG);
			canvas.rotate(handAngle, 0.5f * scale, 0.5f * scale);

			canvas.drawPath(mHandPath, mHandPaint);

			if (mHandStyle == 0)
				canvas.drawCircle(0.5f * scale, 0.5f * scale, 0.01f * scale, mHandScrewPaint);

			canvas.restore();
		}

		if (handNeedsToMove())
		{
			moveHand();
		}
	}

	private float positionToRotation(float x, float y)
	{
		double centerX = getWidth() / 2;
		double centerY = getHeight() / 2;

		double adjustedX = x - centerX;
		double adjustedY = y - centerY;

		double rad = Math.atan2(-adjustedY, adjustedX);

		double deg = Math.toDegrees(rad);

		// atan2 returns values in the -180 to 180 range, we want it 0-360
		deg += 180;

		// Polar coordinates are counter-clockwise, switch to clockwise
		deg = 360 - deg;

		// Polar coordinates put 0 at the right side, we want it at the bottom
		deg += 90;

		// Bring the value back into the 0-360 range
		deg = deg % 360;

		return (float) deg;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (mHandStyle != 2)
			return false;

		float curRotation = positionToRotation(event.getX(), event.getY());

		switch (event.getAction())
		{
		case MotionEvent.ACTION_DOWN:
			final float handRotation = mGauge.valueToAngle(mHandPosition);
			final float scale = getWidth();
			final float handLengthScalar = (mGauge.getScaleDiameter() / scale) * 0.5f * 0.01f;
			final float width = mHandWidth * handLengthScalar;
			final float length = mHandLength * handLengthScalar;

			Matrix matrix = new Matrix();
			matrix.preRotate(180.f + handRotation, scale / 2, scale / 2);

			float[] points = new float[] { scale / 2, (length * scale) / 2 };
			matrix.mapPoints(points);

			float dist = PointF.length(event.getX() - points[0], event.getY() - points[1]);

			if (dist <= (width * scale * 2))
			{
				mDragging = true;
			}
			break;

		case MotionEvent.ACTION_MOVE:
			if (mDragging)
			{
				float delta = curRotation - mPreviousRotation;

				float newAngle = mGauge.valueToAngle(mHandPosition) + delta;
				mHandPosition = mGauge.angleToValue(newAngle);
				mHandTarget = mHandPosition;

				invalidate();
			}
			break;

		case MotionEvent.ACTION_UP:
			if (mDragging)
			{
				mDragging = false;

				if (mListener != null)
				{
					mListener.onValueChanged(mHandPosition);
				}
			}
			break;
		}

		mPreviousRotation = curRotation;

		return true;
	}

	private boolean handNeedsToMove()
	{
		return Math.abs(mHandPosition - mHandTarget) > 0.01f;
	}

	private void moveHand()
	{
		if (!handNeedsToMove())
		{
			return;
		}

		if (!mInterpolateChanges)
		{
			mHandPosition = mHandTarget;
			return;
		}

		if (mLastHandMoveTime != -1L)
		{
			long currentTime = System.currentTimeMillis();
			float delta = (currentTime - mLastHandMoveTime) / 1000.0f;

			float direction = Math.signum(mHandVelocity);
			if (Math.abs(mHandVelocity) < 90.0f)
			{
				mHandAcceleration = 5.0f * (mHandTarget - mHandPosition);
			}
			else
			{
				mHandAcceleration = 0.0f;
			}
			mHandPosition += mHandVelocity * delta;
			mHandVelocity += mHandAcceleration * delta;
			if ((mHandTarget - mHandPosition) * direction < 0.01f * direction)
			{
				mHandPosition = mHandTarget;
				mHandVelocity = 0.0f;
				mHandAcceleration = 0.0f;
				mLastHandMoveTime = -1L;
			}
			else
			{
				mLastHandMoveTime = System.currentTimeMillis();
			}
			invalidate();
		}
		else
		{
			mLastHandMoveTime = System.currentTimeMillis();
			moveHand();
		}
	}

	public void setName(String name)
	{
		if (mGauge == null)
			return;

		if (	(mName == null && name != null) ||
				(mName != null && name == null) ||
				(mName != null && name != null && !mName.equals(name)))
		{
			mName = name;
			mGauge.nameChanged(this);
		}
	}

	public String getName()
	{
		return mName;
	}

	public int getColor()
	{
		return mHandColor;
	}

	public void setHandTarget(float value)
	{
		if (mGauge != null)
			value = mGauge.clampValue(value);

		mHandTarget = value;
		mHandInitialized = true;
		invalidate();
	}

	public boolean isDragging()
	{
		return mDragging;
	}
}
