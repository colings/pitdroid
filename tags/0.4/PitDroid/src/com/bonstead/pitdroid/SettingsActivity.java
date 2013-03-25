package com.bonstead.pitdroid;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.bonstead.pitdroid.R;

import android.os.Bundle;

public class SettingsActivity extends SherlockPreferenceActivity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}
