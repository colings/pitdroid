package com.bonstead.pitdroid;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

// Based on http://mindtherobot.com/blog/272/android-custom-ui-making-a-vintage-thermometer/
public final class GaugeView extends View
{
	private static final String TAG = GaugeView.class.getSimpleName();

	// drawing tools
	private RectF mGaugeRect;
	private Paint mBackgroundPaint;
	private Paint mRimPaint;

	private RectF mFaceRect;
	private Paint mRimShadowPaint;

	private Paint mScalePaint;
	private Paint mScaleTextPaint;
	private RectF mScaleRect;

	private Paint mHandPaint;
	private Path mHandPath;
	private Paint mHandScrewPaint;

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
	private int mBackgroundColor1 = Color.rgb(0xf0, 0xf5, 0xf0);
	private int mBackgroundColor2 = Color.rgb(0x30, 0x31, 0x30);
	private int mHandColor = Color.rgb(0x39, 0x2f, 0x2c);
	private float mScaleFontSize = 6.0f;
	private float mScaleThickness = 0.5f;
	private float mScaleOffset = 10.f;
	private float mRimSize = 2.f;

	// hand dynamics -- all are angular expressed in gauge values
	private boolean mHandInitialized = false;
	private float mHandPosition = 0.0f;
	private float mHandTarget = 0.0f;
	private float mHandVelocity = 0.0f;
	private float mHandAcceleration = 0.0f;
	private long mLastHandMoveTime = -1L;

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
		mBackgroundColor1 = a.getColor(R.styleable.GaugeView_backgroundColor1, mBackgroundColor1);
		mBackgroundColor2 = a.getColor(R.styleable.GaugeView_backgroundColor2, mBackgroundColor2);
		mHandColor = a.getColor(R.styleable.GaugeView_handColor, mHandColor);
		mScaleFontSize = a.getFloat(R.styleable.GaugeView_scaleFontSize, mScaleFontSize);
		mScaleThickness = a.getFloat(R.styleable.GaugeView_scaleThickness, mScaleThickness);
		mScaleOffset = a.getFloat(R.styleable.GaugeView_scaleOffset, mScaleOffset);
		mRimSize = a.getFloat(R.styleable.GaugeView_rimSize, mRimSize);
		a.recycle();

		init();
	}

	public GaugeView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init();
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

		// TEMP
		setHandTarget(mMaxValue - mMinValue * 0.3f);
	}

	@Override
	protected void onDetachedFromWindow()
	{
		super.onDetachedFromWindow();
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

		mHandTarget = mMinValue;
	}

	private int roundToTick(int value)
	{
		return (int) Math.ceil(value / mTickValue) * mTickValue;
	}

	private void initDrawingTools()
	{
		float scale = getWidth();
		float relativeScale = scale / 100.f;

		mGaugeRect = new RectF(scale * 0.1f, scale * 0.1f, scale * 0.9f, scale * 0.9f);

		// the linear gradient is a bit skewed for realism
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
		mRimPaint.setStrokeWidth(mScaleThickness * relativeScale);//(0.005f);

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
		mScaleRect = new RectF();
		mScaleRect.set(mFaceRect.left + scalePosition, mFaceRect.top + scalePosition,
				mFaceRect.right - scalePosition, mFaceRect.bottom - scalePosition);

		mHandPaint = new Paint();
		mHandPaint.setAntiAlias(true);
		mHandPaint.setColor(mHandColor);
		if (!isInEditMode())
		{
			mHandPaint.setShadowLayer(0.01f, -0.005f, -0.005f, 0x7f000000);
		}
		mHandPaint.setStyle(Paint.Style.FILL);

		mHandPath = new Path();
		mHandPath.moveTo(0.5f * scale, (0.5f + 0.2f) * scale);
		mHandPath.lineTo((0.5f - 0.010f) * scale, (0.5f + 0.2f - 0.007f) * scale);
		mHandPath.lineTo((0.5f - 0.002f) * scale, (0.5f - 0.32f) * scale);
		mHandPath.lineTo((0.5f + 0.002f) * scale, (0.5f - 0.32f) * scale);
		mHandPath.lineTo((0.5f + 0.010f) * scale, (0.5f + 0.2f - 0.007f) * scale);
		mHandPath.lineTo(0.5f * scale, (0.5f + 0.2f) * scale);
		mHandPath.addCircle(0.5f * scale, 0.5f * scale, 0.025f * scale, Path.Direction.CW);

		mHandScrewPaint = new Paint();
		mHandScrewPaint.setAntiAlias(true);
		mHandScrewPaint.setColor(0xff493f3c);
		mHandScrewPaint.setStyle(Paint.Style.FILL);

		mCachedBackgroundPaint = new Paint();
		mCachedBackgroundPaint.setFilterBitmap(true);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		Log.d(TAG, "Width spec: " + MeasureSpec.toString(widthMeasureSpec));
		Log.d(TAG, "Height spec: " + MeasureSpec.toString(heightMeasureSpec));

		int widthMode = MeasureSpec.getMode(widthMeasureSpec);
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);

		int heightMode = MeasureSpec.getMode(heightMeasureSpec);
		int heightSize = MeasureSpec.getSize(heightMeasureSpec);

		int chosenWidth = chooseDimension(widthMode, widthSize);
		int chosenHeight = chooseDimension(heightMode, heightSize);

		int chosenDimension = Math.min(chosenWidth, chosenHeight);

		setMeasuredDimension(chosenDimension, chosenDimension);
	}

	private int chooseDimension(int mode, int size)
	{
		if (mode == MeasureSpec.AT_MOST || mode == MeasureSpec.EXACTLY)
		{
			return size;
		}
		else
		{ // (mode == MeasureSpec.UNSPECIFIED)
			return getPreferredSize();
		}
	}

	// in case there is no size specified
	private int getPreferredSize()
	{
		return 300;
	}

	private void drawRim(Canvas canvas)
	{
		// first, draw the metallic body
		canvas.drawOval(mGaugeRect, mBackgroundPaint);
		// now the outer rim circle
		canvas.drawOval(mGaugeRect, mRimPaint);
	}

	private void drawFace(Canvas canvas)
	{
		// draw the inner rim circle
		canvas.drawOval(mFaceRect, mRimPaint);

		// draw the rim shadow inside the face
		if (!isInEditMode())
			canvas.drawOval(mFaceRect, mRimShadowPaint);
	}

	private void drawScale(Canvas canvas)
	{
		float scale = (float) getWidth();

		float openDegrees = (mOpenTicks / (float) mTotalTicks) * 360.0f;

		canvas.drawArc(mScaleRect, 90.0f + (openDegrees * 0.5f), 360.0f - openDegrees, false, mScalePaint);

		canvas.save(Canvas.MATRIX_SAVE_FLAG);

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

	private float valueToAngle(float value)
	{
		return 180.f + (value / (mTickValue * (mTotalTicks + mOpenTicks))) * 360.f;
	}

	private void drawHand(Canvas canvas)
	{
		float scale = getWidth();

		if (mHandInitialized)
		{
			float handAngle = valueToAngle(mHandPosition);
			canvas.save(Canvas.MATRIX_SAVE_FLAG);
			canvas.rotate(handAngle, 0.5f * scale, 0.5f * scale);
			canvas.drawPath(mHandPath, mHandPaint);
			canvas.restore();

			canvas.drawCircle(0.5f * scale, 0.5f * scale, 0.01f * scale, mHandScrewPaint);
		}
	}

	private void drawBackground(Canvas canvas)
	{
		if (mCachedBackground == null)
		{
			Log.w(TAG, "Background not created");
		}
		else
		{
			canvas.drawBitmap(mCachedBackground, 0, 0, mCachedBackgroundPaint);
		}
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		drawBackground(canvas);

		drawHand(canvas);

		if (handNeedsToMove())
		{
			moveHand();
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		Log.d(TAG, "Size changed to " + w + "x" + h);
		initDrawingTools();
		regenerateBackground();
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

		drawRim(backgroundCanvas);
		drawFace(backgroundCanvas);
		drawScale(backgroundCanvas);
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

	private void setHandTarget(float value)
	{
		if (value < mMinValue)
		{
			value = mMinValue;
		}
		else if (value > mMaxValue)
		{
			value = mMaxValue;
		}
		mHandTarget = value;
		mHandInitialized = true;
		invalidate();
	}
}
