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
    
    public LinkedList<Sample> mSamples;
    public String[] mProbeNames = new String[kNumProbes];
    
	private int mNewestTime = 0;
	private int mLastCompactTime = 0;
	private double mMinTemperature = Double.MAX_VALUE;
	private double mMaxTemperature = Double.MIN_VALUE;
    private ArrayList<Listener> mListeners = new ArrayList<Listener>();

    class Sample
    {
    	int mTime;
    	double mFanSpeed;
    	double mLidOpen;
    	double mSetPoint;
    	double[] mProbes = new double[kNumProbes];
    }
    
    class NamedSample extends Sample
    {
    	String[] mProbeNames = new String[kNumProbes];
    }

	public interface Listener
	{
		public void samplesUpdated(final LinkedList<Sample> samples, final String[] names);
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
    		return parseStatus(readerToString(reader));
    	}
    }
    
    // Suppress warning since we know it's an LinkedList of samples, even if Java doesn't
    @SuppressWarnings("unchecked")
	public void updateMain(Object data)
    {
    	if (data instanceof NamedSample)
    	{
    		addStatus((NamedSample)data);
    	}
    	else if (data != null)
    	{
    		addHistory((LinkedList<Sample>)data);
    	}
    	
    	if (mNewestTime >= mLastCompactTime + kCompactInterval)
    	{
    		compactSamples();
    	}
    	
    	for (int l = 0; l < mListeners.size(); l++)
    		mListeners.get(l).samplesUpdated(mSamples, mProbeNames);

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

    private void addStatus(NamedSample sample)
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
    	}
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

    private void addHistory(LinkedList<Sample> history)
    {
    	mSamples = history;

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
    	
    	mLastCompactTime = mNewestTime;
    }
    
    private void compactSamples()
    {/*
    	int firstSampleTime = mSetPoint.mHistory.getFirst().mTime;
    	
    	compactSamples(mSetPoint, firstSampleTime);
    	compactSamples(mFanSpeed, firstSampleTime);
		for (int p = 0; p < kNumProbes; p++)
		{
			compactSamples(mProbes[p], firstSampleTime);
		}
		*/
		mLastCompactTime = mNewestTime;
    }
/*
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
*/
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
