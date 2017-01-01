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
		Preference minTemp = findPreference(KEY_MIN_TEMP);
		Preference maxTemp = findPreference(KEY_MAX_TEMP);

		int minTempValue = Integer.valueOf(sharedPreferences.getString(KEY_MIN_TEMP, ""));
		int maxTempValue = Integer.valueOf(sharedPreferences.getString(KEY_MAX_TEMP, ""));

		minTempValue -= minTempValue % 50;
		maxTempValue -= maxTempValue % 50;

		if (maxTempValue <= minTempValue)
		{
			maxTempValue = minTempValue * 50;
		}

		minTemp.setSummary(Integer.toString(minTempValue) + "°");
		maxTemp.setSummary(Integer.toString(maxTempValue) + "°");
	}
}
