package com.bonstead.pitdroid;

import java.util.ArrayList;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.bonstead.pitdroid.R;

public class MainActivity extends SherlockFragmentActivity
{
	ViewPager mViewPager;
	TabsAdapter mTabsAdapter;
	TextView tabCenter;
	TextView tabText;
	private StatusService mBoundService;
	private boolean mIsBound = false;
    public HeaterMeter mHeaterMeter = new HeaterMeter();
    
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		mViewPager = new ViewPager(this);
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
		
		// Get the xml/preferences.xml preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		String serverAddr = prefs.getString("server", "");
		
		mHeaterMeter.mServerAddress = serverAddr;
		
	    Intent intent = new Intent(this, StatusService.class);
//	    intent.putExtra("urlpath", "http://");
	    startService(intent);
	    doBindService();
	}

	Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg)
		{
			mHeaterMeter.updateMain(msg.obj);
		}
	};

	private ServiceConnection mConnection = new ServiceConnection()
	{
	    public void onServiceConnected(ComponentName className, IBinder service)
	    {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        mBoundService = ((StatusService.LocalBinder)service).getService();
	        mBoundService.mMainActivity = MainActivity.this;

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			String serverAddr = prefs.getString("server", "");
			mBoundService.init(serverAddr);
	    }

	    public void onServiceDisconnected(ComponentName className)
	    {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        mBoundService = null;
	    }
	};

	void doBindService()
	{
	    // Establish a connection with the service.  We use an explicit
	    // class name because we want a specific service implementation that
	    // we know will be running in our own process (and thus won't be
	    // supporting component replacement by other applications).
	    bindService(new Intent(this, StatusService.class), mConnection, Context.BIND_AUTO_CREATE);
	    mIsBound = true;
	}

	void doUnbindService()
	{
	    if (mIsBound)
	    {
	        // Detach our existing connection.
	        unbindService(mConnection);
	        mIsBound = false;
	    }
	}
	 @Override
	protected void onDestroy()
	{
		// TODO Auto-generated method stub
		super.onDestroy();
		
		doUnbindService();
	    stopService(new Intent(this, StatusService.class));
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
	}

	@Override
	protected void onPostResume() {
		// TODO Auto-generated method stub
		super.onPostResume();
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