package com.bonstead.pitdroid;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener
{
	static final String TAG = "MainActivity";

	private final ScheduledExecutorService mScheduler = Executors.newScheduledThreadPool(1);
	private ScheduledFuture<?> mUpdateTimer = null;
	private HeaterMeter mHeaterMeter = null;
	private boolean mAllowServiceShutdown = false;

	private final Runnable mUpdate = new Runnable()
	{
		public void run()
		{
			Object data = mHeaterMeter.updateThread();
			mHandler.sendMessage(mHandler.obtainMessage(0, data));
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if (BuildConfig.DEBUG)
		{
			Log.v(TAG, "onCreate");
		}

		// If we don't have a fragment (being opened, not recreated), open the gauge fragment by default
		if (getFragmentManager().findFragmentById(android.R.id.content) == null)
		{
			// Display the fragment as the main content.
			getFragmentManager().beginTransaction()
					.add(android.R.id.content, new GaugeFragment())
					.commit();
		}

		mHeaterMeter = ((PitDroidApplication) this.getApplication()).mHeaterMeter;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mHeaterMeter.initPreferences(prefs);
		prefs.registerOnSharedPreferenceChangeListener(this);

		// Uncomment to use saved sample data instead of live, for testing purposes
		//mHeaterMeter.setHistory(new InputStreamReader(getResources().openRawResource(R.raw.sample_data)));

		updateScreenOn();
		updateAlarmService();

		// Sent when the close button is pressed on the alarm service status message
		if (getIntent().hasExtra("close"))
		{
			showCloseMessage();
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();

		if (BuildConfig.DEBUG)
		{
			Log.v(TAG, "onPause");
		}

		if (mUpdateTimer != null)
		{
			if (BuildConfig.DEBUG)
			{
				Log.v(TAG, "Canceling update timer");
			}

			mUpdateTimer.cancel(false);
			mUpdateTimer = null;
		}
	}

	@Override
	protected void onPostResume()
	{
		super.onPostResume();

		if (BuildConfig.DEBUG)
		{
			Log.v(TAG, "onPostResume");
		}

		if (mUpdateTimer == null)
		{
			if (BuildConfig.DEBUG)
			{
				Log.v(TAG, "Starting update timer");
			}

			mUpdateTimer = mScheduler.scheduleAtFixedRate(mUpdate, 0, HeaterMeter.kMinSampleTime, TimeUnit.MILLISECONDS);
		}
		else
		{
			if (BuildConfig.DEBUG)
			{
				Log.v(TAG, "Update timer already set, skipping");
			}
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		if (BuildConfig.DEBUG)
		{
			Log.v(TAG, "onDestroy");
		}

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		prefs.unregisterOnSharedPreferenceChangeListener(this);

		if (mAllowServiceShutdown)
		{
			stopAlarmService();
		}
	}

	public void onSettingsClick(View v)
	{
		getFragmentManager().beginTransaction()
			.replace(android.R.id.content, new SettingsFragment())
			.addToBackStack(null)
			.commit();
	}

	public void onGraphClick(View v)
	{
		getFragmentManager().beginTransaction()
			.replace(android.R.id.content, new GraphFragment())
			.addToBackStack(null)
			.commit();
	}

	public void onDashClick(View v)
	{
		getFragmentManager().beginTransaction()
			.replace(android.R.id.content, new DashFragment())
			.addToBackStack(null)
			.commit();
	}

	static class IncomingHandler extends Handler
	{
		private final WeakReference<MainActivity> mActivity;

		IncomingHandler(MainActivity activity)
		{
			mActivity = new WeakReference<MainActivity>(activity);
		}

		@Override
		public void handleMessage(Message msg)
		{
			MainActivity activity = mActivity.get();
			if (activity != null)
			{
				activity.mHeaterMeter.updateMain(msg.obj);

				if (activity.mHeaterMeter.mLastStatusMessage != null)
				{
					Context context = activity.getApplicationContext();
					CharSequence text = activity.mHeaterMeter.mLastStatusMessage;
					int duration = Toast.LENGTH_SHORT;

					Toast toast = Toast.makeText(context, text, duration);
					toast.show();

					activity.mHeaterMeter.mLastStatusMessage = null;
				}
			}
		}
	}

	private final IncomingHandler mHandler = new IncomingHandler(this);

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		// If any preferences change, have the HeaterMeter re-read them all
		mHeaterMeter.initPreferences(sharedPreferences);
		updateScreenOn();
	}

	private void updateScreenOn()
	{
		if (mHeaterMeter.mKeepScreenOn)
		{
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		else
		{
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
	}

	public void updateAlarmService()
	{
		if (mHeaterMeter.hasAlarms())
		{
			stopAlarmService();
			startAlarmService();
		}
		else
		{
			stopAlarmService();
		}
	}

	@TargetApi(Build.VERSION_CODES.O)
	private void startAlarmService()
	{
		if (BuildConfig.DEBUG)
		{
			Log.v(TAG, "Start alarm service");
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			startForegroundService(new Intent(this, AlarmService.class));
		}
		else
		{
			startService(new Intent(this, AlarmService.class));
		}
		startService(new Intent(this, AlarmService.class));
	}

	private void stopAlarmService()
	{
		if (BuildConfig.DEBUG)
		{
			Log.v(TAG, "Stop alarm service");
		}

		stopService(new Intent(this, AlarmService.class));
	}

	private void showCloseMessage()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);

		builder.setTitle("Confirm");
		builder.setMessage("You have alarms set, are you sure you want to exit?");

		builder.setPositiveButton("Yes", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int which)
			{
				mAllowServiceShutdown = true;
				dialog.dismiss();
				finish();
			}
		});

		builder.setNegativeButton("No", null);

		AlertDialog alert = builder.create();
		alert.show();
	}
}
