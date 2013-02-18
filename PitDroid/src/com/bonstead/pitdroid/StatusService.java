package com.bonstead.pitdroid;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class StatusService extends Service {

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = 1;

    private final IBinder mBinder = new LocalBinder();
    public MainActivity mMainActivity;
    private final ScheduledExecutorService mScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> mUpdateTimer;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
    	StatusService getService() {
            return StatusService.this;
        }
    }

    @Override
    public void onCreate() {


    }
/*
    @Override
    public Object onRetainNonConfigurationInstance() {
        final MyDataObject data = collectMyLoadedData();
        return data;
    }
*/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("LocalService", "Received start id " + startId + ": " + intent);
        
        //String urlPath = intent.getStringExtra("urlpath");

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i("LocalService", "onDestroy");
        // Cancel the persistent notification.
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void init(String serverAddr)
    {
        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();

        final Runnable update = new Runnable()
        {
            public void run()
            {
        		if (mMainActivity != null)
        		{
        			Object data = mMainActivity.mHeaterMeter.updateThread();
        			mMainActivity.mHandler.sendMessage(mMainActivity.mHandler.obtainMessage(0, data));
        		}
        	}
        };
        mUpdateTimer = mScheduler.scheduleAtFixedRate(update, 0, HeaterMeter.kMinSampleTime, TimeUnit.MILLISECONDS);
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification()
    {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = "test";//getText(R.string.local_service_started);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_status, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.app_name),
                       text, contentIntent);

        // Send the notification.
        //mNM.notify(NOTIFICATION, notification);
        
        notification.flags|=Notification.FLAG_NO_CLEAR;

        startForeground(NOTIFICATION, notification);
    }
}