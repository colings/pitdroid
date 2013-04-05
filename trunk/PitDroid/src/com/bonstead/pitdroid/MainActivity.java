package com.bonstead.pitdroid;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.bonstead.pitdroid.R;

public class MainActivity extends SherlockFragmentActivity implements OnSharedPreferenceChangeListener
{
	static final String TAG = "MainActivity";

	NoScrollViewPager mViewPager;
	TabsAdapter mTabsAdapter;
	TextView tabCenter;
	TextView tabText;
    private final ScheduledExecutorService mScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> mUpdateTimer = null;
    private HeaterMeter mHeaterMeter = null;
    private PendingIntent mServiceAlarm = null;
    
    // Custom ViewPager to disable Fragment scrolling on swipe 
    private class NoScrollViewPager extends ViewPager {
        public NoScrollViewPager(Context context) {
        	super(context);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            return false;
        }        
    };
      
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
    		Log.v(TAG, "onCreate");

    	mViewPager = new NoScrollViewPager(this);
		mViewPager.setId(R.id.pager);
		
		setContentView(mViewPager);
		ActionBar bar = getSupportActionBar();
		bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		
		mTabsAdapter = new TabsAdapter(this, mViewPager);
		
		mTabsAdapter.addTab(
		        bar.newTab().setText("Dash"),
		        DashActivity.class, null);
		
		mTabsAdapter.addTab(
		                bar.newTab().setText("Graph"),
		                GraphActivity.class, null);

    	mHeaterMeter = ((PitDroidApplication)this.getApplication()).mHeaterMeter;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    	mHeaterMeter.initPreferences(prefs);
        prefs.registerOnSharedPreferenceChangeListener(this);

   		updateAlarmService();
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
			}
		}
	}
	
	private final IncomingHandler mHandler = new IncomingHandler(this);

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
    	mHeaterMeter.initPreferences(sharedPreferences);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        prefs.unregisterOnSharedPreferenceChangeListener(this);

    	if (BuildConfig.DEBUG)
    		Log.v(TAG, "onDestroy");

    	stopAlarmService();
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
	    		Log.v(TAG, "Start alarm service");
	
	    	Intent alarmIntent = new Intent(this, AlarmService.class);
	    	mServiceAlarm = PendingIntent.getService(this, 0, alarmIntent, 0);
	    	AlarmManager alarm = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
	    	alarm.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), mHeaterMeter.mBackgroundUpdateTime*60*1000, mServiceAlarm);
		}
	}
	
	private void stopAlarmService()
	{
		if (mServiceAlarm != null)
		{
	    	if (BuildConfig.DEBUG)
	    		Log.v(TAG, "Stop alarm service");

			AlarmManager alarm = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
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
    		Log.v(TAG, "onPause");

		if (mUpdateTimer != null)
		{
	    	if (BuildConfig.DEBUG)
	    		Log.v(TAG, "Canceling update timer");

			mUpdateTimer.cancel(false);
			mUpdateTimer = null;
		}
	}

	@Override
	protected void onPostResume()
	{
		super.onPostResume();
		
    	if (BuildConfig.DEBUG)
    		Log.v(TAG, "onPostResume");

		if (mUpdateTimer == null)
		{
	    	if (BuildConfig.DEBUG)
	    		Log.v(TAG, "Starting update timer");

			mUpdateTimer = mScheduler.scheduleAtFixedRate(mUpdate, 0, HeaterMeter.kMinSampleTime, TimeUnit.MILLISECONDS);
		}
		else
		{
			if (BuildConfig.DEBUG)
				Log.v(TAG, "Update timer already set, skipping");
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getSupportMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
      
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle item selection
		switch (item.getItemId())
		{
			case R.id.menu_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

    // From http://bitbucket.org/owentech/abstabsviewpager/
    public static class TabsAdapter extends FragmentPagerAdapter implements
                    ActionBar.TabListener, ViewPager.OnPageChangeListener
    {
	    private final Context mContext;
	    private final ActionBar mActionBar;
	    private final ViewPager mViewPager;
	    private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();
	
	    static final class TabInfo
	    {
            private final Class<?> clss;
            private final Bundle args;

            TabInfo(Class<?> _class, Bundle _args)
            {
                clss = _class;
                args = _args;
            }
	    }
	
	    public TabsAdapter(SherlockFragmentActivity activity, ViewPager pager)
	    {
            super(activity.getSupportFragmentManager());
            mContext = activity;
            mActionBar = activity.getSupportActionBar();
            mViewPager = pager;
            mViewPager.setAdapter(this);
            mViewPager.setOnPageChangeListener(this);
	    }
	
	    public void addTab(ActionBar.Tab tab, Class<?> clss, Bundle args)
	    {
            TabInfo info = new TabInfo(clss, args);
            tab.setTag(info);
            tab.setTabListener(this);
            mTabs.add(info);
            mActionBar.addTab(tab);
            notifyDataSetChanged();
	    }
	
	    @Override
	    public int getCount()
	    {
            return mTabs.size();
	    }
	
	    @Override
	    public Fragment getItem(int position)
	    {
            TabInfo info = mTabs.get(position);
            return Fragment.instantiate(mContext, info.clss.getName(), info.args);
	    }
	
	    @Override
		public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)
	    {
	    }
	
	    @Override
		public void onPageSelected(int position)
	    {
	    	mActionBar.setSelectedNavigationItem(position);
	    }
	
	    @Override
		public void onPageScrollStateChanged(int state)
	    {
	    }
	
	    @Override
		public void onTabSelected(Tab tab, FragmentTransaction ft)
	    {
	        Object tag = tab.getTag();
	        for (int i = 0; i < mTabs.size(); i++)
	        {
                if (mTabs.get(i) == tag)
                {
                	mViewPager.setCurrentItem(i);
                }
	        }
	    }
	
	    @Override
		public void onTabUnselected(Tab tab, FragmentTransaction ft)
	    {
	    }
	
	    @Override
		public void onTabReselected(Tab tab, FragmentTransaction ft)
	    {
	    }
    }
}