package com.bonstead.pitdroid

import java.lang.ref.WeakReference
import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.view.WindowManager
import android.widget.Toast
import android.os.Message
import android.annotation.TargetApi
import android.os.Build
import android.content.Intent
import android.app.AlertDialog

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val mScheduler = Executors.newScheduledThreadPool(1)
    private var mUpdateTimer: ScheduledFuture<*>? = null
    private var mAllowServiceShutdown = false
    private val mHandler = IncomingHandler(this)

    private val mUpdate = Runnable {
        val data = HeaterMeter.updateThread()
        mHandler.sendMessage(mHandler.obtainMessage(0, data))
    }

    private val mOnNavigationItemSelectedListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.navigation_dash -> {
                openFragment(DashFragment())
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_graph -> {
                openFragment(GraphFragment())
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_gauge -> {
                openFragment(GaugeFragment())
                return@OnNavigationItemSelectedListener true
            }
            R.id.navigation_settings -> {
                openFragment(SettingsFragment())
                return@OnNavigationItemSelectedListener true
            }
        }
        false
    }

    private fun openFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // If we don't have a fragment (being opened, not recreated), open the gauge fragment by default
        if (supportFragmentManager.findFragmentById(android.R.id.content) == null) {
            // Display the fragment as the main content.
            openFragment(GaugeFragment())
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(baseContext)
        prefs.registerOnSharedPreferenceChangeListener(this);

        HeaterMeter.initPreferences(prefs)

        // Uncomment to use saved sample data instead of live, for testing purposes
        //mHeaterMeter.setHistory(new InputStreamReader(getResources().openRawResource(R.raw.sample_data)));

        updateScreenOn()
        updateAlarmService()

        // Sent when the close button is pressed on the alarm service status message
        if (intent.hasExtra("close")) {
            showCloseMessage()
        }

        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener)
    }

    override fun onPause() {
        super.onPause()

        if (mUpdateTimer != null) {
            mUpdateTimer!!.cancel(false)
            mUpdateTimer = null
        }
    }

    override fun onPostResume() {
        super.onPostResume()

        if (mUpdateTimer == null) {
            mUpdateTimer = mScheduler.scheduleAtFixedRate(mUpdate, 0, HeaterMeter.kMinSampleTime, TimeUnit.MILLISECONDS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        val prefs = PreferenceManager.getDefaultSharedPreferences(baseContext)
        prefs.unregisterOnSharedPreferenceChangeListener(this)

        if (mAllowServiceShutdown) {
            stopAlarmService()
        }
    }

    internal class IncomingHandler(activity: MainActivity) : Handler() {
        private val mActivity: WeakReference<MainActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            val activity = mActivity.get()
            if (activity != null) {
                HeaterMeter.updateMain(msg.obj)

                if (HeaterMeter.mLastStatusMessage != null) {
                    val context = activity.applicationContext
                    val text = HeaterMeter.mLastStatusMessage
                    val duration = Toast.LENGTH_SHORT

                    val toast = Toast.makeText(context, text, duration)
                    toast.show()

                    HeaterMeter.mLastStatusMessage = null
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        // If any preferences change, have the HeaterMeter re-read them all
        HeaterMeter.initPreferences(sharedPreferences)
        updateScreenOn()
    }

    private fun updateScreenOn() {
        if (HeaterMeter.mKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun updateAlarmService() {
        if (HeaterMeter.hasAlarms()) {
            stopAlarmService()
            startAlarmService()
        } else {
            stopAlarmService()
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun startAlarmService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, AlarmService::class.java))
        } else {
            startService(Intent(this, AlarmService::class.java))
        }
        startService(Intent(this, AlarmService::class.java))
    }

    private fun stopAlarmService() {
        stopService(Intent(this, AlarmService::class.java))
    }

    private fun showCloseMessage() {
        val builder = AlertDialog.Builder(this)

        builder.setTitle("Confirm")
        builder.setMessage("You have alarms set, are you sure you want to exit?")

        builder.setPositiveButton("Yes") { dialog, _ ->
            mAllowServiceShutdown = true
            dialog.dismiss()
            finish()
        }

        builder.setNegativeButton("No", null)

        val alert = builder.create()
        alert.show()
    }

    companion object {
        internal val TAG = "MainActivity"
    }
}
