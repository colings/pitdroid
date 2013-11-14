package com.bonstead.pitdroid;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.bonstead.pitdroid.R;

import android.os.Bundle;
import android.preference.Preference;

public class SettingsActivity extends SherlockPreferenceActivity
{
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		final HeaterMeter heaterMeter = ((PitDroidApplication) this.getApplication()).mHeaterMeter;

		// The pit set temp is a special setting that we don't actually care about
		// storing. All we use it for is to get the value from the user and push it to the
		// HeaterMeter.
		Preference setTemp = findPreference("pitSetTemp");
		if (heaterMeter.isAuthenticated())
		{
			setTemp.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
			{
				public boolean onPreferenceChange(Preference preference, Object newValue)
				{
					final int newTemp = Integer.parseInt((String) newValue);

					Thread trd = new Thread(new Runnable()
					{
						@Override
						public void run()
						{
							heaterMeter.changePitSetTemp(newTemp);
						}
					});
					trd.start();

					// Return false, so the setting never gets saved
					return false;
				}
			});
		}
		else
		{
			setTemp.setSelectable(false);
			setTemp.setEnabled(false);
		}
	}
}
