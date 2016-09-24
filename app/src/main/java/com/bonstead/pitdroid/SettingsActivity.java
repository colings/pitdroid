package com.bonstead.pitdroid;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class SettingsActivity extends PreferenceFragmentCompat
{
	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
	{
		// Load the preferences from an XML resource
		setPreferencesFromResource(R.xml.preferences, rootKey);
/*
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
		*/
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = super.onCreateView(inflater, container, savedInstanceState);
		view.setBackgroundColor(getResources().getColor(android.R.color.white));
		return view;
	}
}
