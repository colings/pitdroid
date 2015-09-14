package com.bonstead.pitdroid;

import android.app.Application;

public class PitDroidApplication extends Application
{
	public HeaterMeter mHeaterMeter = new HeaterMeter();

	// Create PanZoomTracker to hold pan/zoom window details
	public PanZoomTracker mPanZoomTracker = new PanZoomTracker();
}
