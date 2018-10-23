package com.bonstead.pitdroid;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

// Based on http://mindtherobot.com/blog/272/android-custom-ui-making-a-vintage-thermometer/
public final class GaugeView extends GaugeBaseView
{
	private static final String TAG = GaugeView.class.getSimpleName();

	// drawing tools
	private RectF mGaugeRect;
	private Paint mBezelPaint;
	private Paint mBackgroundPaint;
	private Paint mRimPaint;

	private RectF mFaceRect;
	private Paint mRimShadowPaint;

	private Paint mScalePaint;
	private Paint mScaleTextPaint;
	private RectF mScaleRect;

	private Path mLegendPath;
	private Paint mLegendTextPaint;
	private boolean mLegendDirty = false;

	private Paint mCachedBackgroundPaint;
	// end drawing tools

	private Bitmap mCachedBackground; // holds the cached static part

	// scale configuration
	private int mScaleColor = Color.argb(0xd0, 0x09, 0xf0, 0x04);
	private int mMinValue = 0;
	private int mMaxValue = 100;
	private int mTickValue = 5;
	private int mSubTicks = 4;
	private int mOpenTicks = 2;
	private int mTotalTicks;
	private int mBezelColor1 = Color.rgb(0xf0, 0xf5, 0xf0);
	private int mBezelColor2 = Color.rgb(0x30, 0x31, 0x30);
	private int mBackgroundColor1 = Color.rgb(0xf0, 0xf5, 0xf0);
	private int mBackgroundColor2 = Color.rgb(0x30, 0x31, 0x30);
	private float mScaleFontSize = 6.0f;
	private float mScaleThickness = 0.5f;
	private float mScaleOffset = 10.f;
	private float mLegendOffset = 15.f;
	private float mRimSize = 2.f;

	public GaugeView(Context context)
	{
		super(context);
		init();
	}

	public GaugeView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GaugeView);
		mScaleColor = a.getColor(R.styleable.GaugeView_scaleColor, mScaleColor);
		mMinValue = a.getInteger(R.styleable.GaugeView_minValue, mMinValue);
		mMaxValue = a.getInteger(R.styleable.GaugeView_maxValue, mMaxValue);
		mTickValue = a.getInteger(R.styleable.GaugeView_tickValue, mTickValue);
		mOpenTicks = a.getInteger(R.styleable.GaugeView_openTicks, mOpenTicks);
		mSubTicks = a.getInteger(R.styleable.GaugeView_subTicks, mSubTicks);
		mBezelColor1 = a.getColor(R.styleable.GaugeView_bezelColor1, mBezelColor1);
		mBezelColor2 = a.getColor(R.styleable.GaugeView_bezelColor2, mBezelColor2);
		mBackgroundColor1 = a.getColor(R.styleable.GaugeView_backgroundColor1, mBackgroundColor1);
		mBackgroundColor2 = a.getColor(R.styleable.GaugeView_backgroundColor2, mBackgroundColor2);
		mScaleFontSize = a.getFloat(R.styleable.GaugeView_scaleFontSize, mScaleFontSize);
		mScaleThickness = a.getFloat(R.styleable.GaugeView_scaleThickness, mScaleThickness);
		mScaleOffset = a.getFloat(R.styleable.GaugeView_scaleOffset, mScaleOffset);
		mLegendOffset = a.getFloat(R.styleable.GaugeView_legendOffset, mLegendOffset);
		mRimSize = a.getFloat(R.styleable.GaugeView_rimSize, mRimSize);
		a.recycle();

		init();
	}

	private void init()
	{
		mMinValue = roundToTick(mMinValue);
		mMaxValue = roundToTick(mMaxValue);

		if (mMaxValue <= mMinValue)
		{
			mMaxValue = mMinValue + mTickValue;
		}

		mTotalTicks = ((mMaxValue - mMinValue) / mTickValue) + mOpenTicks;
	}

	public void updateRange(int minValue, int maxValue)
	{
		mMinValue = minValue;
		mMaxValue = maxValue;
		init();
	}

	public void nameChanged(GaugeHandView hand)
	{
		mLegendDirty = true;
		invalidate();
	}

	private int roundToTick(int value)
	{
		return (int) Math.ceil(value / mTickValue) * mTickValue;
	}

	@Override
	protected void initDrawingTools()
	{
		float scale = getWidth();
		float relativeScale = scale / 100.f;

		// The rim has a thickness, so decrease the draw rect by that to ensure it doesn't get clipped
		float rimThickness = mScaleThickness * relativeScale * 0.5f;

		mGaugeRect = new RectF(rimThickness, rimThickness, scale - rimThickness, scale - rimThickness);

		// the linear gradient is a bit skewed for realism
		mBezelPaint = new Paint();
		mBezelPaint.setAntiAlias(true);
		if (!isInEditMode())
		{
			mBezelPaint.setShader(new LinearGradient(0.40f * scale, 0.0f * scale, 0.60f * scale, 1.0f * scale,
					mBezelColor1,
					mBezelColor2,
					Shader.TileMode.CLAMP));
		}
		else
		{
			mBezelPaint.setStyle(Paint.Style.FILL);
			mBezelPaint.setColor(mBezelColor1);
		}

		mBackgroundPaint = new Paint();
		mBackgroundPaint.setAntiAlias(true);
		if (!isInEditMode())
		{
			mBackgroundPaint.setShader(new LinearGradient(0.40f * scale, 0.0f * scale, 0.60f * scale, 1.0f * scale,
					mBackgroundColor1,
					mBackgroundColor2,
					Shader.TileMode.CLAMP));
		}
		else
		{
			mBackgroundPaint.setStyle(Paint.Style.FILL);
			mBackgroundPaint.setColor(mBackgroundColor1);
		}

		mRimPaint = new Paint();
		mRimPaint.setAntiAlias(true);
		mRimPaint.setStyle(Paint.Style.STROKE);
		mRimPaint.setColor(Color.argb(0x4f, 0x33, 0x36, 0x33));
		mRimPaint.setStrokeWidth(mScaleThickness * relativeScale);

		float rimSize = (mRimSize * relativeScale);
		mFaceRect = new RectF();
		mFaceRect.set(mGaugeRect.left + rimSize, mGaugeRect.top + rimSize,
				mGaugeRect.right - rimSize, mGaugeRect.bottom - rimSize);

		mRimShadowPaint = new Paint();
		mRimShadowPaint.setShader(new RadialGradient(
					0.5f * scale, 0.5f * scale, mFaceRect.width() / 2.0f,
					new int[]{0x00000000, 0x00000500, 0x50000500},
					new float[]{0.96f, 0.96f, 0.99f},
					Shader.TileMode.MIRROR));
		mRimShadowPaint.setStyle(Paint.Style.FILL);

		mScalePaint = new Paint();
		mScalePaint.setStyle(Paint.Style.STROKE);
		mScalePaint.setColor(mScaleColor);
		mScalePaint.setStrokeWidth(mScaleThickness * relativeScale);
		mScalePaint.setAntiAlias(true);

		mScaleTextPaint = new Paint();
		mScaleTextPaint.setAntiAlias(true);
		mScaleTextPaint.setTextSize(mScaleFontSize * relativeScale);
		mScaleTextPaint.setTypeface(Typeface.SANS_SERIF);
		mScaleTextPaint.setTextScaleX(0.8f);
		mScaleTextPaint.setTextAlign(Paint.Align.CENTER);

		float scalePosition = mScaleOffset * relativeScale;
		mScaleRect = new RectF(mFaceRect.left + scalePosition, mFaceRect.top + scalePosition,
								mFaceRect.right - scalePosition, mFaceRect.bottom - scalePosition);

		float legendPosition = mLegendOffset * relativeScale;
		RectF legendRect = new RectF(mFaceRect.left + legendPosition, mFaceRect.top + legendPosition,
									mFaceRect.right - legendPosition, mFaceRect.bottom - legendPosition);
		mLegendPath = new Path();
		mLegendPath.addArc(legendRect, -180.0f, -180.0f);

		mLegendTextPaint = new Paint();
		mLegendTextPaint.setAntiAlias(true);
		mLegendTextPaint.setTextSize(mScaleFontSize * relativeScale);
		mLegendTextPaint.setTypeface(Typeface.SANS_SERIF);
		mLegendTextPaint.setTextScaleX(0.8f);

		mCachedBackgroundPaint = new Paint();
		mCachedBackgroundPaint.setFilterBitmap(true);

		regenerateBackground();
	}

	private void drawScale(Canvas canvas)
	{
		float scale = (float) getWidth();

		float openDegrees = (mOpenTicks / (float) mTotalTicks) * 360.0f;

		canvas.drawArc(mScaleRect, 90.0f + (openDegrees * 0.5f), 360.0f - openDegrees, false, mScalePaint);

		canvas.save();

		// We want to start drawing from the bottom center, so first flip the canvas around so
		// that's on top.
		canvas.rotate(180.f + (openDegrees * 0.5f), 0.5f * scale, 0.5f * scale);

		final float tickAngleIncrement = 360.f / mTotalTicks;
		final float subTickAngleIncrement = tickAngleIncrement / (mSubTicks + 1);

		final int numSteps = mTotalTicks - mOpenTicks + 1;

		for (int i = 0; i < numSteps; ++i)
		{
			float y1 = mScaleRect.top;
			float y2 = y1 - (0.020f * scale);

			canvas.drawLine(0.5f * scale, y1, 0.5f * scale, y2, mScalePaint);

			int curValue = mMinValue + (i * mTickValue);
			String valueString = Integer.toString(curValue);
			canvas.drawText(valueString, 0.5f * scale, y2 - (0.015f * scale), mScaleTextPaint);

			if (i < numSteps - 1)
			{
				for (int j = 0; j < mSubTicks; j++)
				{
					y2 = y1 - (0.010f * scale);

					canvas.rotate(subTickAngleIncrement, 0.5f * scale, 0.5f * scale);
					canvas.drawLine(0.5f * scale, y1, 0.5f * scale, y2, mScalePaint);
				}

				canvas.rotate(subTickAngleIncrement, 0.5f * scale, 0.5f * scale);
			}
		}

		canvas.restore();
	}

	private void drawLegend(Canvas canvas)
	{
		ArrayList<GaugeHandView> mHands = new ArrayList<>();

		ViewGroup row = (ViewGroup) getParent();
		for (int i = 0; i < row.getChildCount(); i++)
		{
			View view = row.getChildAt(i);
			if (view instanceof GaugeHandView)
			{
				mHands.add((GaugeHandView) view);
			}
		}

		float space = mLegendTextPaint.measureText("  ");

		float[] lengths = new float[mHands.size()];
		float totalLength = 0.f;
		boolean firstText = true;

		for (int i = 0; i < mHands.size(); i++)
		{
			GaugeHandView hand = mHands.get(i);

			if (hand.getName() != null)
			{
				if (firstText)
				{
					firstText = false;
				}
				else
				{
					totalLength += space;
				}

				lengths[i] = mLegendTextPaint.measureText(hand.getName());

				totalLength += lengths[i];
			}
			else
			{
				lengths[i] = 0.f;
			}
		}

		PathMeasure measure = new PathMeasure(mLegendPath, false);
		float currentOffset = (measure.getLength() - totalLength) / 2.f;

		for (int i = 0; i < mHands.size(); i++)
		{
			GaugeHandView hand = mHands.get(i);

			if (hand.getName() != null)
			{
				mLegendTextPaint.setColor(hand.getColor());
				canvas.drawTextOnPath(hand.getName(), mLegendPath, currentOffset, 0.f, mLegendTextPaint);
				currentOffset += lengths[i] + space;
			}
		}
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		if (mLegendDirty)
		{
			mLegendDirty = false;
			regenerateBackground();
		}

		if (mCachedBackground == null)
		{
			Log.w(TAG, "Background not created");
		}
		else
		{
			canvas.drawBitmap(mCachedBackground, 0, 0, mCachedBackgroundPaint);
		}
	}

	private void regenerateBackground()
	{
		// free the old bitmap
		if (mCachedBackground != null)
		{
			mCachedBackground.recycle();
		}

		mCachedBackground = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
		Canvas backgroundCanvas = new Canvas(mCachedBackground);

		// first, draw the bezel
		backgroundCanvas.drawOval(mGaugeRect, mBezelPaint);
		// now the outer rim circle
		backgroundCanvas.drawOval(mGaugeRect, mRimPaint);
		// draw the gauge background
		backgroundCanvas.drawOval(mFaceRect, mBackgroundPaint);
		// draw the inner rim circle
		backgroundCanvas.drawOval(mFaceRect, mRimPaint);

		// draw the rim shadow inside the face
		if (!isInEditMode())
			backgroundCanvas.drawOval(mFaceRect, mRimShadowPaint);

		drawScale(backgroundCanvas);

		drawLegend(backgroundCanvas);
	}

	public float clampValue(float value)
	{
		return Math.max(mMinValue, Math.min(mMaxValue, value));
	}

	// Converts a gauge value to a 0-360 degree angle value
	// 0 = gauge absolute min value (including open ticks), 360 = gauge absolute max value
	public float valueToAngle(float value)
	{
		float clampedVal = clampValue(value);

		float actualMin = mMinValue - (mOpenTicks * mTickValue * 0.5f);
		float actualMax = mMaxValue + (mOpenTicks * mTickValue * 0.5f);
		float scalar = (clampedVal - actualMin) / (actualMax - actualMin);
		return scalar * 360.f;
	}

	public float angleToValue(float degrees)
	{
		float clampedVal = degrees % 360.f;
		if (clampedVal < 0.f)
			clampedVal = 360.f - clampedVal;

		float actualMin = mMinValue - (mOpenTicks * mTickValue * 0.5f);
		float actualMax = mMaxValue + (mOpenTicks * mTickValue * 0.5f);
		float scalar = (clampedVal / 360.f);
		return actualMin + ((actualMax - actualMin) * scalar);
	}

	public float getScaleDiameter()
	{
		return mScaleRect.width();
	}

	public int getMinValue() { return mMinValue; }
	public int getMaxValue() { return mMaxValue; }
}
