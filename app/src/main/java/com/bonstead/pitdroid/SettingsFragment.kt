package com.bonstead.pitdroid

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
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
        val (initialMinTempValue, initialMaxTempValue) = getMinMax(sharedPreferences)

        // If a temperature range value has changed, ensure that the min and max are both multiples
        // of 50, and that the max is greater than the min.
        val minTempValue = initialMinTempValue - initialMinTempValue % 50
        var maxTempValue = initialMaxTempValue - initialMaxTempValue % 50

        if (maxTempValue <= minTempValue) {
            maxTempValue = minTempValue + 50
        }

        if (initialMinTempValue != minTempValue || initialMaxTempValue != maxTempValue) {
            val edit = sharedPreferences.edit()
            edit.putString(KEY_MIN_TEMP, minTempValue.toString())
            edit.putString(KEY_MAX_TEMP, maxTempValue.toString())
            edit.apply()
        }

        findPreference<EditTextPreference>(KEY_MIN_TEMP)!!.summary = minTempValue.toString() + "°"
        findPreference<EditTextPreference>(KEY_MAX_TEMP)!!.summary = maxTempValue.toString() + "°"
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
