package com.bonstead.pitdroid;

import com.bonstead.pitdroid.HeaterMeter.NamedSample;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class AlarmService extends Service
{
	static final String TAG = "AlarmService";
    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = 1;
    static final String NAME = "com.bonstead.pitdroid.AlarmService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        PowerManager mgr = (PowerManager)getBaseContext().getSystemService(Context.POWER_SERVICE);
        final WakeLock lock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, NAME);
        lock.acquire();
        
    	if (BuildConfig.DEBUG)
    		Log.v(TAG, "onStartCommand");

        //showNotification(null, null);
        
    	Runnable NameOfRunnable = new Runnable()
    	{
    	    @Override
    	    public void run()
    	    {
    	    	if (BuildConfig.DEBUG)
    	    		Log.v(TAG, "Getting sample from HeaterMeter...");
    	    	
    	    	HeaterMeter heatermeter = ((PitDroidApplication)getApplication()).mHeaterMeter;
    	    	NamedSample sample = heatermeter.getSample();
    	    	
    	    	if (BuildConfig.DEBUG)
    	    	{
    	    		if (sample != null)
    	    			Log.v(TAG, "Got sample");
    	    		else
    	    			Log.v(TAG, "Sample was null");
    	    	}
    	    	
    	        showNotification(sample, heatermeter);
    	        
    	        lock.release();
    	    }
    	};
    	
    	Thread name = new Thread(NameOfRunnable);
        name.start();
        
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

	@Override
    public void onDestroy()
    {
		super.onDestroy();

    	if (BuildConfig.DEBUG)
    		Log.v(TAG, "onDestroy");

        // Cancel the persistent notification.
        stopForeground(true);
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification(final NamedSample latestSample, final HeaterMeter heatermeter)
    {
    	String contentText = null;
    	
    	boolean hasAlarms = false;

    	if (latestSample != null)
    	{
    		for (int p = 0; p < HeaterMeter.kNumProbes; p++)
    		{
    			if (heatermeter.isAlarmed(p, latestSample.mProbes[p]))
    			{
    				hasAlarms = true;
    				
    				if (contentText == null)
    					contentText = new String();
    				
    				contentText += latestSample.mProbeNames[p] + " - " + heatermeter.formatTemperature(latestSample.mProbes[p]) + " ";
    			}
    		}
    	}
    	
        NotificationCompat.Builder builder =
        		new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_status)  
				.setContentTitle(getText(R.string.app_name))  
				.setContentText(contentText != null ? contentText : getString(R.string.alarm_service_info))
				.setOngoing(true);
        
        if (hasAlarms)
        {
        	if (BuildConfig.DEBUG)
        		Log.v(TAG, "Alarm notification:" + contentText);

        	builder.setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS);
        }
        else
        {
        	if (BuildConfig.DEBUG)
        		Log.v(TAG, "Info notification");

			builder.setOnlyAlertOnce(true);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);  
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent,   
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        startForeground(NOTIFICATION, builder.build());
    }
 
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }
}