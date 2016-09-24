package com.bonstead.pitdroid;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public class MainActivity extends FragmentActivity implements
		OnSharedPreferenceChangeListener
{
	static final String TAG = "MainActivity";

	private final ScheduledExecutorService mScheduler = Executors.newScheduledThreadPool(1);
	private ScheduledFuture<?> mUpdateTimer = null;
	private HeaterMeter mHeaterMeter = null;
	private PendingIntent mServiceAlarm = null;
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

		setContentView(R.layout.activity_main);

		// Display the fragment as the main content.
/*		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		fragmentTransaction.replace(R.id.dash, new GraphActivity());
		fragmentTransaction.addToBackStack(null);
		fragmentTransaction.commit();
*/
		mHeaterMeter = ((PitDroidApplication) this.getApplication()).mHeaterMeter;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		mHeaterMeter.initPreferences(prefs);
		prefs.registerOnSharedPreferenceChangeListener(this);

		changeScreenOn();

		updateAlarmService();
	}

	public void onSettingsClick(View v)
	{
		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		fragmentTransaction.replace(R.id.main, new SettingsActivity());
		fragmentTransaction.addToBackStack(null);
		fragmentTransaction.commit();
	}

	public void onGraphClick(View v)
	{
		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		fragmentTransaction.replace(R.id.main, new GraphActivity());
		fragmentTransaction.addToBackStack(null);
		fragmentTransaction.commit();
	}

	public void onDashClick(View v)
	{
		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		fragmentTransaction.replace(R.id.main, new DashActivity());
		fragmentTransaction.addToBackStack(null);
		fragmentTransaction.commit();
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

	/**
	 * Change screen on setting
	 */
	private void changeScreenOn()
	{
		// keep screen on
		if (mHeaterMeter.mKeepScreenOn)
		{
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		else
		{
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		// If any preferences change, have the HeaterMeter re-read them all
		mHeaterMeter.initPreferences(sharedPreferences);
		changeScreenOn();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		prefs.unregisterOnSharedPreferenceChangeListener(this);

		if (BuildConfig.DEBUG)
		{
			Log.v(TAG, "onDestroy");
		}

		if (mAllowServiceShutdown)
		{
			stopAlarmService();
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

	private void startAlarmService()
	{
		if (mServiceAlarm == null)
		{
			if (BuildConfig.DEBUG)
			{
				Log.v(TAG, "Start alarm service");
			}

			Intent alarmIntent = new Intent(this, AlarmService.class);
			mServiceAlarm = PendingIntent.getService(this, 0, alarmIntent, 0);
			AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			alarm.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
					mHeaterMeter.mBackgroundUpdateTime * 60 * 1000, mServiceAlarm);
		}
	}

	private void stopAlarmService()
	{
		if (mServiceAlarm != null)
		{
			if (BuildConfig.DEBUG)
			{
				Log.v(TAG, "Stop alarm service");
			}

			AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
			alarm.cancel(mServiceAlarm);
			mServiceAlarm = null;
		}

		stopService(new Intent(this, AlarmService.class));
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

			mUpdateTimer = mScheduler.scheduleAtFixedRate(mUpdate, 0, HeaterMeter.kMinSampleTime,
					TimeUnit.MILLISECONDS);
		}
		else
		{
			if (BuildConfig.DEBUG)
			{
				Log.v(TAG, "Update timer already set, skipping");
			}
		}
	}

//	@Override
//	public boolean onCreateOptionsMenu(Menu menu)
//	{
//		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.activity_main, menu);
//		super.onCreateOptionsMenu(menu);
//		return true;
//	}
/*
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle item selection
		switch (item.getItemId())
		{
		case R.id.menu_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		case R.id.menu_exit:
			if (mHeaterMeter.hasAlarms())
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
			else
			{
				finish();
			}
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	*/
}