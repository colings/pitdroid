package com.bonstead.pitdroid;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener
{
	public static final String KEY_MIN_TEMP = "minTemp";
	public static final String KEY_MAX_TEMP = "maxTemp";

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
		prefs.registerOnSharedPreferenceChangeListener(this);

		onTemperatureChanged(prefs);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (key.equals(KEY_MIN_TEMP) || key.equals(KEY_MAX_TEMP))
		{
			onTemperatureChanged(sharedPreferences);
		}
	}

	private void onTemperatureChanged(SharedPreferences sharedPreferences)
	{
		int initialMinTempValue = Integer.valueOf(sharedPreferences.getString(KEY_MIN_TEMP, "50"));
		int initialMaxTempValue = Integer.valueOf(sharedPreferences.getString(KEY_MAX_TEMP, "350"));

		// If a temperature range value has changed, ensure that the min and max are both multiples
		// of 50, and that the max is greater than the min.
		int minTempValue = initialMinTempValue - (initialMinTempValue % 50);
		int maxTempValue = initialMaxTempValue - (initialMaxTempValue % 50);

		if (maxTempValue <= minTempValue)
		{
			maxTempValue = minTempValue + 50;
		}

		if (initialMinTempValue != minTempValue || initialMaxTempValue != maxTempValue)
		{
			SharedPreferences.Editor edit = sharedPreferences.edit();
			edit.putString(KEY_MIN_TEMP, Integer.toString(minTempValue));
			edit.putString(KEY_MAX_TEMP, Integer.toString(maxTempValue));
			edit.apply();
		}

		findPreference(KEY_MIN_TEMP).setSummary(Integer.toString(minTempValue) + "°");
		findPreference(KEY_MAX_TEMP).setSummary(Integer.toString(maxTempValue) + "°");
	}
}
