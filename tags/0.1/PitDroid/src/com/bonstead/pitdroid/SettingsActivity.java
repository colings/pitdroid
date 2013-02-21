package com.bonstead.pitdroid;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.bonstead.pitdroid.R;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;

public class SettingsActivity extends SherlockPreferenceActivity implements OnSharedPreferenceChangeListener
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        
        //EditTextPreference pref = (EditTextPreference)findPreference("backgroundUpdateTime");
        //pref.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
    }

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
        if (key.equals("server"))
        {
        	String serverAddress = sharedPreferences.getString(key, "");

            // Set summary to be the user-description for the selected value
            Preference connectionPref = findPreference(key);
            connectionPref.setSummary(serverAddress);
            
            // Update the address on the HeaterMeter
        	HeaterMeter heaterMeter = ((PitDroidApplication)this.getApplication()).mHeaterMeter;
        	heaterMeter.mServerAddress = serverAddress; 
        }
    }
    
    @Override
    protected void onResume()
    {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}
