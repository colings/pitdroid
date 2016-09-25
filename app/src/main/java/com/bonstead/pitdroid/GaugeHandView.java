package com.bonstead.pitdroid;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class GaugeHandView extends GaugeBaseView
{
	private static final String TAG = GaugeHandView.class.getSimpleName();

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
		else
		{
			final float yTip = (scale - mGauge.getScaleDiameter()) * 0.5f;
			final float length = mHandLength * handLengthScalar;

			mHandPath.moveTo((0.5f - halfWidth * 0.2f) * scale, yTip);
			mHandPath.lineTo((0.5f + halfWidth * 0.2f) * scale, yTip);
			mHandPath.lineTo((0.5f + halfWidth) * scale, yTip + (length * scale));
			mHandPath.lineTo((0.5f - halfWidth) * scale, yTip + (length * scale));
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
			float handAngle = mGauge.valueToAngle(mHandPosition);

			canvas.save(Canvas.MATRIX_SAVE_FLAG);
			canvas.rotate(handAngle, 0.5f * scale, 0.5f * scale);

			canvas.drawPath(mHandPath, mHandPaint);
			canvas.drawCircle(0.5f * scale, 0.5f * scale, 0.01f * scale, mHandScrewPaint);

			canvas.restore();
		}

		if (handNeedsToMove())
		{
			moveHand();
		}
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

	public void setHandTarget(float value)
	{
		if (mGauge != null)
			value = mGauge.clampValue(value);

		mHandTarget = value;
		mHandInitialized = true;
		invalidate();
	}
}
