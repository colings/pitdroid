package com.bonstead.pitdroid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import au.com.bytecode.opencsv.CSVReader;

public class HeaterMeter
{
	public final static int kNumProbes = 4;
	public final static String kHistoryURL = "/luci/lm/hist";
	public final static String kStatusURL = "/luci/lm/hmstatus";
	// No point in trying to sample faster than this, it's the update rate of the HeaterMeter hardware
    public final static int kMinSampleTime = 1000;
    private final static int kCompactInterval = 2*60;
    
    public String mServerAddress;
	Sampler mSetPoint = new Sampler("Set Point");
	Sampler mFanSpeed = new Sampler("Fan Speed");
	Sampler[] mProbes = new Sampler[kNumProbes];
	private int mNewestTime = 0;
	private int mLastCompactTime = 0;
	private double mMinTemperature = Double.MAX_VALUE;
	private double mMaxTemperature = Double.MIN_VALUE;
    private ArrayList<Listener> mListeners = new ArrayList<Listener>();

    class PackedSample
    {
    	int mTime;
    	double mSetPoint;
    	double mFanSpeed;
    	double[] mProbes = new double[kNumProbes];
    }

	class Sample
    {
    	double mValue;
    	int mTime;
    	
    	Sample(int time, double value)
    	{
    		mTime = time;
    		mValue = value;
    	}
    }
	
	class Sampler
	{
		Sampler(String name)
		{
			mName = name;
		}

		String mName;
	    LinkedList<Sample> mHistory = new LinkedList<Sample>();
	    
		public double getNormalized(double temperature)
		{
			return (temperature - mMinTemperature) / (mMaxTemperature - mMinTemperature);
		}
		
		public double getLatest()
		{
			if (mHistory.size() > 0 && mHistory.getLast().mTime == mNewestTime)
				return mHistory.getLast().mValue;
			else
				return Double.NaN;
		}
	}

	public interface Listener
	{
		public void samplesUpdated();
	}
	
	public HeaterMeter()
	{
		for (int p = 0; p < kNumProbes; p++)
		{
			mProbes[p] = new Sampler("-");
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

	public double getOriginal(double normalized)
	{
		return (normalized * (mMaxTemperature - mMinTemperature)) + mMinTemperature;
	}

	public PackedSample getNewestSample()
	{
	    PackedSample sample = new PackedSample();
	    
	    sample.mTime = mNewestTime;
	    sample.mSetPoint = mSetPoint.getLatest();
		sample.mFanSpeed = mFanSpeed.getLatest();
		for (int i = 0; i < kNumProbes; i++)
			sample.mProbes[i] = mProbes[i].getLatest();
		
		return sample;
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
    
    public Object updateThread()
    {
    	if (mNewestTime == 0)
		{
	    	BufferedReader reader = getUrlReader("http://" + mServerAddress + kHistoryURL);
	    	return parseHistory(reader);
		}
    	else
    	{
    		BufferedReader reader = getUrlReader("http://" + mServerAddress + kStatusURL);
    		return readerToString(reader);
    	}
    }
    
    // Suppress warning since we know it's an ArrayList of packed samples, even if Java doesn't
    @SuppressWarnings("unchecked")
	public void updateMain(Object data)
    {
    	if (data instanceof String)
    	{
    		addStatus((String)data);
    	}
    	else
    	{
    		addHistory((ArrayList<PackedSample>)data);
    	}
    	
    	if (mNewestTime >= mLastCompactTime + kCompactInterval)
    	{
    		compactSamples();
    	}
    	
    	for (int l = 0; l < mListeners.size(); l++)
    		mListeners.get(l).samplesUpdated();

    }

    public void addStatus(String status)
    {
		try
		{
			JSONTokener tokener = new JSONTokener(status);
			JSONObject json = new JSONObject(tokener);
			
			int time = json.getInt("time");

			if (time != mNewestTime)
			{
				mNewestTime = time;

				double value = json.getDouble("set");
				updateMinMax(value);
		    	mSetPoint.mHistory.add(new Sample(time, value));
				
		    	JSONObject faninfo = json.getJSONObject("fan");
		    	int fanspeed = faninfo.getInt("c");
		    	mFanSpeed.mHistory.add(new Sample(time, fanspeed/100.0));

		    	//"lid":0
				
				JSONArray temps = json.getJSONArray("temps");
				for (int i = 0; i < temps.length(); i++)
				{
				    JSONObject row = temps.getJSONObject(i);
				    
				    mProbes[i].mName = row.getString("n");
				    
				    if (!row.isNull("c"))
				    {
				    	value = row.getDouble("c");
				    	updateMinMax(value);
				    	
			    		mProbes[i].mHistory.add(new Sample(time, value));
				    }
				}
			}
    	}
    	catch (JSONException e)
    	{
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    	}
    }
    
    public Object parseHistory(Reader reader)
    {
	    try
	    {
	    	ArrayList<PackedSample> history = new ArrayList<PackedSample>();

	    	CSVReader csvReader = new CSVReader(reader);
		
	    	String [] nextLine;
			while ((nextLine = csvReader.readNext()) != null)
			{
				PackedSample sample = new PackedSample();

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

				// Seventh is the fan speed
				sample.mFanSpeed = Double.parseDouble(nextLine[6]) / 100.0;
				
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
    
    private void addHistory(ArrayList<PackedSample> history)
    {
    	for (int i = 0; i < history.size(); i++)
		{
    		PackedSample sample = history.get(i);

    		int time = sample.mTime;

			mNewestTime = Math.max(mNewestTime, time);

			updateMinMax(sample.mSetPoint);
	    	mSetPoint.mHistory.add(new Sample(time, sample.mSetPoint));
			
	    	mFanSpeed.mHistory.add(new Sample(time, sample.mFanSpeed));
	    	
			for (int p = 0; p < kNumProbes; p++)
			{
			    if (!Double.isNaN(sample.mProbes[p]))
			    {
			    	updateMinMax(sample.mProbes[p]);
		    		mProbes[p].mHistory.add(new Sample(time, sample.mProbes[p]));
			    }
			}
		}
    	
    	mLastCompactTime = mNewestTime;
    }
    
    private void compactSamples()
    {
    	int firstSampleTime = mSetPoint.mHistory.getFirst().mTime;
    	
    	compactSamples(mSetPoint, firstSampleTime);
    	compactSamples(mFanSpeed, firstSampleTime);
		for (int p = 0; p < kNumProbes; p++)
		{
			compactSamples(mProbes[p], firstSampleTime);
		}
		
		mLastCompactTime = mNewestTime;
    }

    private void compactSamples(Sampler sampler, int firstSampleTime)
    {
    	Iterator<Sample> it = sampler.mHistory.iterator();
    	while (it.hasNext())
    	{
    		Sample sample = it.next();
    		
    		// Remove a sample if it is older than our last compact time, and not a 60 second multiple of our original time (history is on 60 second intervals)
    		if (sample.mTime < mLastCompactTime && (sample.mTime - firstSampleTime) % 60 != 0)
    			it.remove();
    	}
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
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
}
