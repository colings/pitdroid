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
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log


class AlarmService : Service() {

    private var mServiceAlarm: PendingIntent? = null
    private var mStatusChannel = ""
    private var mAlarmChannel = ""

    override fun onCreate() {
        super.onCreate()

        // Create a pending intent use to schedule us for wakeups
        val alarmIntent = Intent(this, AlarmService::class.java)
        mServiceAlarm = PendingIntent.getService(this, 0, alarmIntent, 0)

        mStatusChannel = createNotificationChannel("pitdroidstatus", false)
        mAlarmChannel = createNotificationChannel("pitdroidalarm", true)
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

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(kAlarmNotificationId)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "onStartCommand")
        }

        // Acquire a wake lock to ensure the device doesn't go to sleep while we're querying the
        // HeaterMeter.  When the query thread is done it will release this.
        val mgr = baseContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PitDroid:AlarmService")
        lock.acquire(30000)

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
    private fun createNotificationChannel(channelId: String, isAlarm: Boolean): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "PitDroid Background Service"
            var importance = NotificationManager.IMPORTANCE_NONE
            if (isAlarm)
                importance = NotificationManager.IMPORTANCE_HIGH

            var chan = NotificationChannel(channelId, channelName, importance)
            chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC

            if (isAlarm) {
                val alarmTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

                chan.setSound(
                    alarmTone,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(chan)
            return channelId
        }
        else {
            if (isAlarm)
                return "alarm"
            else
                return ""
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createBuilder(icon: Int, intent: PendingIntent, channel: String): Notification.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmBuilder = Notification.Builder(this, channel)
            val statusIcon = Icon.createWithResource("", icon)
            val statusAction = Notification.Action.Builder(statusIcon, "Close", intent).build()
            alarmBuilder.addAction(statusAction)
            alarmBuilder.setSmallIcon(icon)
            return alarmBuilder
        }
        else {
            val alarmBuilder = Notification.Builder(this)
            alarmBuilder.setSmallIcon(icon)

            if (channel == "alarm") {
                val alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                alarmBuilder.setSound(alert, AudioManager.STREAM_ALARM)
            }

            return alarmBuilder
        }
    }
    /**
     * Show a notification while this service is running.
     */
    private fun updateStatusNotification(latestSample: NamedSample?) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Info notification")
        }

        var contentText = ""

        if (latestSample != null) {
            // If we've got a sample, check if any of the alarms are triggered
            for (p in 0 until HeaterMeter.kNumProbes) {
                if (!latestSample.mProbes[p].isNaN()) {
                    if (contentText.isNotEmpty()) {
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

        val builder = createBuilder(R.mipmap.ic_status, closePendingIntent, mStatusChannel)
            .setContentTitle("PitDroid Monitor")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(statusIntent)

        startForeground(kStatusNotificationId, builder.build())
    }

    private fun updateAlarmNotification(latestSample: NamedSample?) {
        var contentText = ""

        var hasAlarms = false

        if (latestSample != null) {
            // If we've got a sample, check if any of the alarms are triggered
            for (p in 0 until HeaterMeter.kNumProbes) {
                val alarmText = HeaterMeter.formatAlarm(p, latestSample.mProbes[p])
                if (alarmText.isNotEmpty()) {
                    hasAlarms = true

                    if (contentText.isNotEmpty()) {
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

            val alarmBuilder = createBuilder(R.mipmap.ic_status, alarmPendingIntent, mAlarmChannel)
            alarmBuilder
                    .setContentTitle("PitDroid Alarm")
                    .setContentText(contentText)
                    .setContentIntent(alarmPendingIntent)

            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val isSilentMode = am.ringerMode != AudioManager.RINGER_MODE_NORMAL

            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Silent mode:" + if (isSilentMode) "on" else "off")
            }

            if (!isSilentMode || HeaterMeter.mAlwaysSoundAlarm) {
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "Using alarm sound")
                }

                //val alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                //alarmBuilder.setSound(alert, AudioManager.STREAM_ALARM)
            } else {
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "Not using alarm sound")
                }
            }

            // Build the notification and issues it with notification manager.
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(kAlarmNotificationId, alarmBuilder.build())
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        internal const val TAG = "AlarmService"

        internal const val kStatusNotificationId = 1
        internal const val kAlarmNotificationId = 2
    }
}