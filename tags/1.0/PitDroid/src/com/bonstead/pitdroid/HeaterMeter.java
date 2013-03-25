package com.bonstead.pitdroid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.SharedPreferences;
import android.util.Log;
import au.com.bytecode.opencsv.CSVReader;

public class HeaterMeter
{
	static final String TAG = "HeaterMeter";

	public final static int kNumProbes = 4;
	private final static String kHistoryURL = "/luci/lm/hist";
	private final static String kStatusURL = "/luci/lm/hmstatus";
	// No point in trying to sample faster than this, it's the update rate of the HeaterMeter hardware
    public final static int kMinSampleTime = 1000;
    // Wait at least this long between full history refreshes
    private final static long kMinHistoryUpdateTime = 5000;
    // If we're updating and it's been more than this long since the last update, force a
    // full history refresh, so we don't have too much data missing.
    private final static long kMaxUpdateDelta = 5000;

    public String mServerAddress;
    public int mBackgroundUpdateTime;
    public boolean mAlwaysSoundAlarm = true;
    public int[] mProbeLoAlarm = new int[kNumProbes];
    public int[] mProbeHiAlarm = new int[kNumProbes];
    
    public LinkedList<Sample> mSamples = new LinkedList<Sample>();
    public String[] mProbeNames = new String[kNumProbes];
 
    private long mLastUpdateTime = 0;
	private long mLastHistoryTime = 0;
	private int mNewestTime = 0;
	private double mMinTemperature = Double.MAX_VALUE;
	private double mMaxTemperature = Double.MIN_VALUE;
    private ArrayList<Listener> mListeners = new ArrayList<Listener>();
	private DecimalFormat mOneDec = new DecimalFormat("0.0");
	
    class Sample
    {
    	int mTime;
    	double mFanSpeed;
    	double mLidOpen;
    	double mSetPoint;
    	double[] mProbes = new double[kNumProbes];
    	
    	Sample()
    	{
    		mTime = 0;
    		mFanSpeed = 0;
    		mLidOpen = 0;
    		mSetPoint = Double.NaN;
    		for (int p = 0; p < kNumProbes; p++)
    			mProbes[p] = Double.NaN;
    	}
    	
    	Sample(Sample otherSample)
    	{
    		mTime = otherSample.mTime;
    		mFanSpeed = otherSample.mFanSpeed;
    		mLidOpen = otherSample.mLidOpen;
    		mSetPoint = otherSample.mSetPoint;
    		for (int p = 0; p < kNumProbes; p++)
    			mProbes[p] = otherSample.mProbes[p];
    	}
    }
    
    class NamedSample extends Sample
    {
    	String[] mProbeNames = new String[kNumProbes];
    	
    	NamedSample()
    	{
    		super();
    	}

    	NamedSample(Sample otherSample)
    	{
    		super(otherSample);
    	}
    }

	public interface Listener
	{
		public void samplesUpdated(final NamedSample latestSample);
	}
	
	public HeaterMeter()
	{
		for (int p = 0; p < kNumProbes; p++)
		{
			mProbeNames[p] = "-";
		}
	}

	public void addListener(Listener listener)
	{
		mListeners.add(listener);
	}
	
	public void removeListener(Listener listener)
	{
		mListeners.remove(listener);
	}

	public double getNormalized(double temperature)
	{
		return (temperature - mMinTemperature) / (mMaxTemperature - mMinTemperature);
	}
	
	public double getOriginal(double normalized)
	{
		return (normalized * (mMaxTemperature - mMinTemperature)) + mMinTemperature;
	}

	public String formatTemperature(double temperature)
	{
		return mOneDec.format(temperature) + "°";
	}

	public boolean hasAlarms()
	{
		for (int p = 0; p < kNumProbes; p++)
		{
			if (mProbeLoAlarm[p] > 0 || mProbeHiAlarm[p] > 0)
				return true;
		}
		
		return false;
	}
	
	public boolean isAlarmed(int probeIndex, double temperature)
	{
		boolean hasLo = mProbeLoAlarm[probeIndex] > 0;
		boolean hasHi = mProbeHiAlarm[probeIndex] > 0;
		
		boolean alarmNaN = (hasLo || hasHi) && Double.isNaN(temperature);
		boolean alarmLo  = (hasLo && temperature < mProbeLoAlarm[probeIndex]);
		boolean alarmHi  = (hasHi && temperature > mProbeHiAlarm[probeIndex]);

		return alarmNaN || alarmLo || alarmHi;
	}

	private void updateMinMax(double temp)
    {
    	// Round our numbers up/down to a multiple of 10, making sure it's increased at
    	// least 1.  This gives us some visual headroom in the graph.
    	double roundedUp = Math.ceil((temp + 5.0) / 10.0) * 10.0;
    	double roundedDown = Math.floor((temp - 5.0) / 10.0) * 10.0;

    	mMinTemperature = Math.min(mMinTemperature, roundedDown);
    	mMaxTemperature = Math.max(mMaxTemperature, roundedUp);
    }

	public void initPreferences(SharedPreferences prefs)
	{
		mServerAddress = prefs.getString("server", "");

		mBackgroundUpdateTime = Integer.valueOf(prefs.getString("backgroundUpdateTime", "15"));
		
		mAlwaysSoundAlarm = prefs.getBoolean("alwaysSoundAlarm", true);

        for (int p = 0; p < kNumProbes; p++)
        {
        	String loName = "alarm" + p + "Lo";
        	mProbeLoAlarm[p] = prefs.getInt(loName, -70);
            
        	String hiName = "alarm" + p + "Hi";
        	mProbeHiAlarm[p] = prefs.getInt(hiName, -200);
        }
	}

	public void preferencesChanged(SharedPreferences prefs)
	{
        SharedPreferences.Editor editor = prefs.edit();

        for (int p = 0; p < kNumProbes; p++)
        {
        	String loName = "alarm" + p + "Lo";
            editor.putInt(loName, mProbeLoAlarm[p]);
            
        	String hiName = "alarm" + p + "Hi";
            editor.putInt(hiName, mProbeHiAlarm[p]);
        }
        
        editor.commit();
	}
	
	public NamedSample getSample()
	{
		BufferedReader reader = getUrlReader("http://" + mServerAddress + kStatusURL);
		if (reader != null)
		{
			return parseStatus(readerToString(reader));
		}
		
		return null;
	}
	
    public Object updateThread()
    {
    	Object ret = null;

    	long currentTime = System.currentTimeMillis();

    	long timeSinceLastUpdate = currentTime - mLastUpdateTime;

    	// If we don't have any samples, or we have over 500, rebuild the samples from
    	// scratch based on the history.  The upper end check keeps us from blowing memory
    	// on hours and hours of high precision samples that you won't even be able to see.
    	// The time check is so that we don't read the history, then immediately read it
    	// again because our previous read hadn't been processed by the main thread yet.
    	if ((mSamples.size() == 0 || mSamples.size() > 500 || timeSinceLastUpdate > kMaxUpdateDelta) &&
    		(currentTime - mLastHistoryTime) > kMinHistoryUpdateTime)
		{
    		mLastHistoryTime = currentTime;

        	if (BuildConfig.DEBUG)
        		Log.v(TAG, "Getting history");
    		
	    	BufferedReader reader = getUrlReader("http://" + mServerAddress + kHistoryURL);
	    	if (reader != null)
	    		ret = parseHistory(reader);
		}
    	else
    	{
    		BufferedReader reader = getUrlReader("http://" + mServerAddress + kStatusURL);
    		if (reader != null)
    			ret = parseStatus(readerToString(reader));
    	}
    	
    	if (ret != null)
    	{
    		mLastUpdateTime = currentTime;
    	}
    	
    	return ret;
    }
    
    // Suppress warning since we know it's an LinkedList of samples, even if Java doesn't
    @SuppressWarnings("unchecked")
	public void updateMain(Object data)
    {
    	NamedSample latestSample = null;

    	if (data instanceof NamedSample)
    	{
    		latestSample = addStatus((NamedSample)data);
    	}
    	else if (data != null)
    	{
    		latestSample = addHistory((LinkedList<Sample>)data);
    	}

    	for (int l = 0; l < mListeners.size(); l++)
    		mListeners.get(l).samplesUpdated(latestSample);
    }

    public NamedSample parseStatus(String status)
    {
		try
		{
			JSONTokener tokener = new JSONTokener(status);
			JSONObject json = new JSONObject(tokener);
			
			NamedSample sample = new NamedSample();

			sample.mTime = json.getInt("time");
			sample.mSetPoint = json.getDouble("set");
			
	    	JSONObject faninfo = json.getJSONObject("fan");
	    	sample.mFanSpeed = faninfo.getDouble("c");

	    	sample.mLidOpen = json.getDouble("lid");
			
			JSONArray temps = json.getJSONArray("temps");
			for (int i = 0; i < temps.length(); i++)
			{
			    JSONObject row = temps.getJSONObject(i);
			    
			    sample.mProbeNames[i] = row.getString("n");
			    
			    if (!row.isNull("c"))
			    {
		    		sample.mProbes[i] = row.getDouble("c");
			    }
			    else
			    {
			    	sample.mProbes[i] = Double.NaN;
			    }
			}
			
			return sample;
    	}
    	catch (JSONException e)
    	{
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    		return null;
    	}
    }

    private NamedSample addStatus(NamedSample sample)
    {
    	if (mNewestTime != sample.mTime)
    	{
    		mNewestTime = sample.mTime;
    		
			for (int p = 0; p < kNumProbes; p++)
				mProbeNames[p] = sample.mProbeNames[p];

			updateMinMax(sample.mSetPoint);
	    	
			for (int p = 0; p < kNumProbes; p++)
			{
			    if (!Double.isNaN(sample.mProbes[p]))
			    {
			    	updateMinMax(sample.mProbes[p]);
			    }
			}
			
			Sample simpleSample = new Sample(sample);

			mSamples.add(simpleSample);
    	}
    	
    	return sample;
    }

    public LinkedList<Sample> parseHistory(Reader reader)
    {
	    try
	    {
	    	LinkedList<Sample> history = new LinkedList<Sample>();

	    	CSVReader csvReader = new CSVReader(reader);
		
	    	String [] nextLine;
			while ((nextLine = csvReader.readNext()) != null)
			{
				Sample sample = new Sample();

				// First parameter is the time
				sample.mTime = Integer.parseInt(nextLine[0]);

				// Second is the set point
				sample.mSetPoint = Double.parseDouble(nextLine[1]); 
				
				// Third through sixth are the probe temps
				for (int i = 0; i < kNumProbes; i++)
				{
					if (!nextLine[i+2].equals("nan"))
					{
						sample.mProbes[i] = Double.parseDouble(nextLine[i+2]);
					}
					else
					{
						sample.mProbes[i] = Double.NaN;
					}
				}

				// Seventh is the fan speed/lid open
				sample.mFanSpeed = Double.parseDouble(nextLine[6]);
				if (sample.mFanSpeed < 0)
				{
					sample.mLidOpen = 1.0;
					sample.mFanSpeed = 0.0;
				}
				else
				{
					sample.mLidOpen = 0.0;
					sample.mFanSpeed = sample.mFanSpeed;
				}
				
				history.add(sample);
			}
			
			return history;
		}
	    catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
    }

    private NamedSample addHistory(LinkedList<Sample> history)
    {
    	mSamples = history;

    	mMinTemperature = Double.MAX_VALUE;
    	mMaxTemperature = Double.MIN_VALUE;
    	
    	Iterator<Sample> it = mSamples.iterator();
    	while (it.hasNext())
		{
    		Sample sample = it.next();

			mNewestTime = Math.max(mNewestTime, sample.mTime);

			updateMinMax(sample.mSetPoint);
	    	
			for (int p = 0; p < kNumProbes; p++)
			{
			    if (!Double.isNaN(sample.mProbes[p]))
			    {
			    	updateMinMax(sample.mProbes[p]);
			    }
			}
		}
    	
    	NamedSample latestSample = null;
    	if (mSamples.size() > 0)
    	{
    		latestSample = new NamedSample(mSamples.getLast());
    		for (int p = 0; p < kNumProbes; p++)
    		{
    			latestSample.mProbeNames[p] = mProbeNames[p];
    		}
    	}
    	
    	return latestSample;
    }

    private BufferedReader getUrlReader(String urlName)
    {
		try
		{
			URL url = new URL(urlName);
			return new BufferedReader(new InputStreamReader(url.openStream()));
		}
		catch (MalformedURLException e)
		{
        	if (BuildConfig.DEBUG)
        		Log.e(TAG, "Bad server address");
		}
		catch (UnknownHostException e)
		{
        	if (BuildConfig.DEBUG)
        		Log.e(TAG, "Unknown host: " + e.getLocalizedMessage());
		}
		catch (IOException e)
		{
        	if (BuildConfig.DEBUG)
        		Log.e(TAG, "IO exception");
		}
		
		return null;
    }

    private String readerToString(BufferedReader reader)
    {
		try
		{
			StringBuilder builder = new StringBuilder();
			for (String line = null; (line = reader.readLine()) != null; )
			{
			    builder.append(line).append("\n");
			    
			    // FIXME - http://code.google.com/p/android/issues/detail?id=14562
			    // For Android 2.x, reader gets closed and throws and exception
			    if (!reader.ready())
			    {
			        break;
			    }
			}
			
			return builder.toString();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
    }
    
    /*
	function degPerHour(probeIdx)
	{
	    var pname = "#dph" + probeIdx; 
	    var data = graphData[mapJson[probeIdx]].data;
	    if (data.length != 0 && !isNaN(data[data.length-1][1]))
	    {
	        var val = data[data.length-1][1];
	       
	        var time = data[data.length-1][0];
	        // Target is 59mins30secs ago, allows drawing with scale set to 1hr
	        var targetTime = time - (60 * 60 * 1000) + 30000;
	        for (var i=data.length-1; i>=0; --i)
	            if (data[i][0] <= targetTime && !isNaN(data[i][1]))
	            {
	                var diffTemp = val - data[i][1];
	                var diffTime = time - data[i][0];
	                diffTime /= (60.0 * 60.0 * 1000.0);
	                var dph = diffTemp / diffTime;
	                // Don't display if there isn't clear increase, prevents wild numbers
	                if (dph < 1.0)
	                    break;
	                var timeRemain180 = ((180.0 - val) / dph) * 3600;
	                timeRemain180 = (timeRemain180 > 0) ? formatTimer(timeRemain180, false) + " to 180&deg;<br />" : "";
	                var timeRemain200 = ((200.0 - val) / dph) * 3600;
	                timeRemain200 = (timeRemain200 > 0) ? formatTimer(timeRemain200, false) + " to 200&deg;" : "";
	                $(pname).html(diffTemp.toFixed(1) + "&deg;/hr<br />" + timeRemain180 + timeRemain200).show();
	                return;
	            }
	    }  // if has valid data
	    $(pname).hide();
	}
    */
}
