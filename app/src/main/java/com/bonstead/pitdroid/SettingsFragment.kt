package com.bonstead.pitdroid

import android.content.SharedPreferences
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceFragment

class SettingsFragment : PreferenceFragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences)
    }

    override fun onResume() {
        super.onResume()

        val prefs = preferenceScreen.sharedPreferences
        prefs.registerOnSharedPreferenceChangeListener(this)

        onTemperatureChanged(prefs)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == KEY_MIN_TEMP || key == KEY_MAX_TEMP) {
            onTemperatureChanged(sharedPreferences)
        }
    }

    private fun onTemperatureChanged(sharedPreferences: SharedPreferences) {
        var (initialMinTempValue, initialMaxTempValue) = getMinMax(sharedPreferences)

        // If a temperature range value has changed, ensure that the min and max are both multiples
        // of 50, and that the max is greater than the min.
        val minTempValue = initialMinTempValue - initialMinTempValue % 50
        var maxTempValue = initialMaxTempValue - initialMaxTempValue % 50

        if (maxTempValue <= minTempValue) {
            maxTempValue = minTempValue + 50
        }

        if (initialMinTempValue != minTempValue || initialMaxTempValue != maxTempValue) {
            val edit = sharedPreferences.edit()
            edit.putString(KEY_MIN_TEMP, Integer.toString(minTempValue))
            edit.putString(KEY_MAX_TEMP, Integer.toString(maxTempValue))
            edit.apply()
        }

        findPreference(KEY_MIN_TEMP).summary = Integer.toString(minTempValue) + "°"
        findPreference(KEY_MAX_TEMP).summary = Integer.toString(maxTempValue) + "°"
    }

    companion object {
        const val KEY_MIN_TEMP = "minTemp"
        const val KEY_MAX_TEMP = "maxTemp"

        public fun getMinMax(sharedPreferences: SharedPreferences): Pair<Int, Int> {
            var minTemp = 50
            var maxTemp = 350

            val minTempStr = sharedPreferences.getString(KEY_MIN_TEMP, null)
            if (minTempStr != null) {
                minTemp = Integer.valueOf(minTempStr)
            }
            val maxTempStr = sharedPreferences.getString(KEY_MAX_TEMP, null)
            if (maxTempStr != null) {
                maxTemp = Integer.valueOf(maxTempStr)
            }

            return Pair(minTemp, maxTemp)
        }
    }
}
