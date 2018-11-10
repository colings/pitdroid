package com.bonstead.pitdroid

import com.bonstead.pitdroid.HeaterMeter.NamedSample

import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log

class AlarmService : Service() {

    private var mServiceAlarm: PendingIntent? = null

    override fun onCreate() {
        super.onCreate()

        // Create a pending intent use to schedule us for wakeups
        val alarmIntent = Intent(this, AlarmService::class.java)
        mServiceAlarm = PendingIntent.getService(this, 0, alarmIntent, 0)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "onDestroy")
        }

        cancelAlarm()
        mServiceAlarm = null

        // Cancel the persistent notification.
        stopForeground(true)
    }

    private fun scheduleAlarm() {
        val nextTime = System.currentTimeMillis() + HeaterMeter.mBackgroundUpdateTime * 60 * 1000
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // It's important that even if the user isn't actively using their device the
            // checks will run, so use the version that will force a wakeup on newer
            // versions of Android with Doze mode.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, mServiceAlarm)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, nextTime, mServiceAlarm)
        }
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(mServiceAlarm)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "onStartCommand")
        }

        // Acquire a wake lock to ensure the device doesn't go to sleep while we're querying the
        // HeaterMeter.  When the query thread is done it will release this.
        val mgr = baseContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, NAME)
        lock.acquire()

        // It's possible this is being called due to alarm settings changing and not the alarm going
        // off, so cancel any pending alarms before proceeding.
        cancelAlarm()

        updateStatusNotification(null)

        Thread(Runnable {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Getting sample from HeaterMeter...")
            }

            val sample = HeaterMeter.sample

            if (BuildConfig.DEBUG) {
                if (sample != null) {
                    Log.v(TAG, "Got sample")
                } else {
                    Log.v(TAG, "Sample was null")
                }
            }

            updateStatusNotification(sample)
            updateAlarmNotification(sample)

            scheduleAlarm()

            lock.release()
        }).start()

        // We want this service to continue running until it is explicitly stopped, so return sticky.
        return Service.START_STICKY
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "pitdroid"
        val channelName = "PitDroid Background Service"
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(chan)
        return channelId
    }

    /**
     * Show a notification while this service is running.
     */
    @TargetApi(Build.VERSION_CODES.O)
    private fun updateStatusNotification(latestSample: NamedSample?) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Info notification")
        }

        var contentText = ""

        if (latestSample != null) {
            // If we've got a sample, check if any of the alarms are triggered
            for (p in 0 until HeaterMeter.kNumProbes) {
                if (!java.lang.Double.isNaN(latestSample.mProbes[p])) {
                    if (contentText.length > 0) {
                        contentText += " "
                    }

                    contentText += latestSample.mProbeNames[p] + ": "

                    contentText += HeaterMeter.formatTemperature(latestSample.mProbes[p])
                }
            }
        } else {
            contentText = getString(R.string.alarm_service_info)
        }

        val mainIntent = Intent(this, MainActivity::class.java)
        val statusIntent = PendingIntent.getActivity(this, 0, mainIntent, 0)

        val closeIntent = Intent(this, MainActivity::class.java)
        closeIntent.putExtra("close", true)
        val closePendingIntent = PendingIntent.getActivity(this, 1, closeIntent, 0)

        var channelId = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = createNotificationChannel()
        }

        val builder = Notification.Builder(this, channelId)
                .setSmallIcon(R.mipmap.ic_status)
                .setContentTitle("PitDroid Monitor")
                .setContentText(contentText)
                .setOngoing(true)
                .setContentIntent(statusIntent)
                .addAction(R.mipmap.ic_status, "Close", closePendingIntent)

        startForeground(kStatusNotificationId, builder.build())
    }

    private fun updateAlarmNotification(latestSample: NamedSample?) {
        var contentText = ""

        var hasAlarms = false

        if (latestSample != null) {
            // If we've got a sample, check if any of the alarms are triggered
            for (p in 0 until HeaterMeter.kNumProbes) {
                val alarmText = HeaterMeter.formatAlarm(p, latestSample.mProbes[p])
                if (alarmText.length > 0) {
                    hasAlarms = true

                    if (contentText.length > 0) {
                        contentText += "\n"
                    }

                    contentText += latestSample.mProbeNames[p] + " " + alarmText
                }
            }
        } else {
            // If we didn't get a sample, that's an alarm in itself
            if (HeaterMeter.mAlarmOnLostConnection) {
                hasAlarms = true
            }
            contentText = getText(R.string.no_server).toString()
        }

        if (hasAlarms) {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Alarm notification:$contentText")
            }

            val alarmIntent = Intent(this, MainActivity::class.java)
            val alarmPendingIntent = PendingIntent.getActivity(this, 0, alarmIntent, 0)

            val alarmBuilder = Notification.Builder(this)
                    .setSmallIcon(R.mipmap.ic_status)
                    .setContentTitle("PitDroid Alarm")
                    .setContentText(contentText)
                    .setContentIntent(alarmPendingIntent)
                    .setDefaults(Notification.DEFAULT_VIBRATE or Notification.DEFAULT_LIGHTS)

            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val isSilentMode = am.ringerMode != AudioManager.RINGER_MODE_NORMAL

            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Silent mode:" + if (isSilentMode) "on" else "off")
            }

            if (!isSilentMode || HeaterMeter.mAlwaysSoundAlarm) {
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "Using alarm sound")
                }

                val alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                alarmBuilder.setSound(alert, AudioManager.STREAM_ALARM)
            } else {
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "Not using alarm sound")
                }
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Build the notification and issues it with notification manager.
            notificationManager.notify(kAlarmNotificationId, alarmBuilder.build())
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        internal val TAG = "AlarmService"
        internal val NAME = "com.bonstead.pitdroid.AlarmService"

        internal val kStatusNotificationId = 1
        internal val kAlarmNotificationId = 2
    }
}