package com.bonstead.pitdroid;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.bonstead.pitdroid.PanZoomTracker.Range;

import android.content.SharedPreferences;
import android.util.Log;
import au.com.bytecode.opencsv.CSVReader;

public class HeaterMeter
{
	static final String TAG = "HeaterMeter";

	public final static int kNumProbes = 4;
	private final static String kHistoryURL = "/luci/lm/hist";
	private final static String kStatusURL = "/luci/lm/hmstatus";
	private final static String kAuthURL = "/luci/admin/lm/home";

	// No point in trying to sample faster than this, it's the update rate of the
	// HeaterMeter hardware
	public final static int kMinSampleTime = 1000;
	// Wait at least this long between full history refreshes
	private final static long kMinHistoryUpdateTime = 5000;
	// If we're updating and it's been more than this long since the last update, force a
	// full history refresh, so we don't have too much data missing.
	private final static long kMaxUpdateDelta = 5000;

	public String mServerAddress;
	public String mAdminPassword;
	public int mBackgroundUpdateTime;
	public boolean mAlwaysSoundAlarm = true;
	public int[] mProbeLoAlarm = new int[kNumProbes];
	public int[] mProbeHiAlarm = new int[kNumProbes];

	public ArrayList<Sample> mSamples = new ArrayList<Sample>();
	public String[] mProbeNames = new String[kNumProbes];

	private long mLastUpdateTime = 0;
	private long mLastHistoryTime = 0;
	private int mNewestTime = 0;
	private double mMinTemperature = Double.MAX_VALUE;
	private double mMaxTemperature = Double.MIN_VALUE;
	private ArrayList<Listener> mListeners = new ArrayList<Listener>();
	private DecimalFormat mOneDec = new DecimalFormat("0.0");

	// For authentication, the cookie that's passed, and the URL token
	private String mAuthCookie;
	private String mAuthToken;

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
		return mOneDec.format(temperature) + "�";
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
		boolean alarmLo = (hasLo && temperature < mProbeLoAlarm[probeIndex]);
		boolean alarmHi = (hasHi && temperature > mProbeHiAlarm[probeIndex]);

		return alarmNaN || alarmLo || alarmHi;
	}

	public double getDegreesPerHour(int probeIndex)
	{
		if (mSamples.size() > 0)
		{
			Sample lastSample = mSamples.get(mSamples.size() - 1);
			double currentTemp = lastSample.mProbes[probeIndex];

			if (!Double.isNaN(currentTemp))
			{
				int time = lastSample.mTime;

				// Target is 59mins30secs ago, allows drawing with scale set to 1hr
				final int targetTime = time - (60 * 60) + 30;

				for (int i = mSamples.size() - 1; i >= 0; --i)
				{
					Sample sample = mSamples.get(i);

					if (sample.mTime <= targetTime && !Double.isNaN(sample.mProbes[probeIndex]))
					{
						double diffTemp = currentTemp - sample.mProbes[probeIndex];
						double diffTime = time - sample.mTime;
						diffTime /= 60.0 * 60.0;
						double degreesPerHour = diffTemp / diffTime;

						return degreesPerHour;
					}
				}
			}
		}

		return 0.0;
	}

	public String getTemperatureChangeText(int probeIndex)
	{
		double degreesPerHour = getDegreesPerHour(probeIndex);

		// Don't display if there isn't clear increase, prevents wild numbers
		if (degreesPerHour < 1.0)
			return null;

		String timeStr = String.format(Locale.US, "%.1f�/hr", degreesPerHour);

		Sample lastSample = mSamples.get(mSamples.size() - 1);
		double currentTemp = lastSample.mProbes[probeIndex];

		// If we've got an alarm set and our most recent sample had a reading for this
		// probe, see if we can calculate an estimated time to alarm.
		if (mProbeHiAlarm[probeIndex] > 0 && !Double.isNaN(currentTemp))
		{
			int minutesRemaining = (int) (((mProbeHiAlarm[probeIndex] - currentTemp) / degreesPerHour) * 60);
			if (minutesRemaining > 0)
			{
				int hoursRemaining = minutesRemaining / 60;
				minutesRemaining = minutesRemaining % 60;

				timeStr += String.format(Locale.US, ", %d:%02d to %d�", hoursRemaining,
						minutesRemaining, (int) mProbeHiAlarm[probeIndex]);
			}
		}

		return timeStr;
	}

	/*
	 * Return the minimum and maximum times from our samples
	 */
	public Range<Number> getTimeRange()
	{
		if (mSamples.size() > 0)
			return new Range<Number>(mSamples.get(0).mTime, mSamples.get(mSamples.size() - 1).mTime);
		else
			return new Range<Number>(0, 0);
	}

	private void updateMinMax(double temp)
	{
		// Round our numbers up/down to a multiple of 10, making sure it's increased at
		// least 1. This gives us some visual headroom in the graph.
		double roundedUp = Math.ceil((temp + 5.0) / 10.0) * 10.0;
		double roundedDown = Math.floor((temp - 5.0) / 10.0) * 10.0;

		mMinTemperature = Math.min(mMinTemperature, roundedDown);
		mMaxTemperature = Math.max(mMaxTemperature, roundedUp);
	}

	public void initPreferences(SharedPreferences prefs)
	{
		mServerAddress = prefs.getString("server", "");
		mAdminPassword = prefs.getString("adminPassword", "");

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
		// scratch based on the history. The upper end check keeps us from blowing memory
		// on hours and hours of high precision samples that you won't even be able to
		// see.
		// The time check is so that we don't read the history, then immediately read it
		// again because our previous read hadn't been processed by the main thread yet.
		if ((mSamples.size() == 0 || mSamples.size() > 500 || timeSinceLastUpdate > kMaxUpdateDelta)
				&& (currentTime - mLastHistoryTime) > kMinHistoryUpdateTime)
		{
			mLastHistoryTime = currentTime;

			if (BuildConfig.DEBUG)
				Log.v(TAG, "Getting history");

			if (false)
			{
				Log.v(TAG, "Generating random data");
				ret = generateDummyData(1000, currentTime);
			}
			else
			{
				BufferedReader reader = getUrlReader("http://" + mServerAddress + kHistoryURL);
				if (reader != null)
					ret = parseHistory(reader);
			}
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

	// Suppress warning since we know it's an ArrayList of samples, even if Java doesn't
	@SuppressWarnings("unchecked")
	public void updateMain(Object data)
	{
		NamedSample latestSample = null;

		if (data instanceof NamedSample)
		{
			latestSample = addStatus((NamedSample) data);
		}
		else if (data != null)
		{
			latestSample = addHistory((ArrayList<Sample>) data);
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

	public ArrayList<Sample> generateDummyData(int numSamples, long currentTime)
	{
		ArrayList<Sample> history = new ArrayList<Sample>();

		currentTime /= 1000;

		for (int j = 0; j < numSamples; j++)
		{
			Sample sample = new Sample();
			sample.mTime = (int) (currentTime - (numSamples - j) * 170);

			sample.mSetPoint = 225.0;

			for (int i = 0; i < 1; i++)
			{
				if (j == 0)
				{
					sample.mProbes[i] = sample.mSetPoint - 50 + (Math.random() * 100);
				}
				else if (j == 1)
				{
					sample.mProbes[i] = history.get(0).mProbes[i] - 4 + (Math.random() * 10);
				}
				else
				{
					int sloping = (history.get(j - 1).mProbes[i] < history.get(j - 2).mProbes[i]) ? -1
							: 1;
					sloping = (Math.random() > 0.95) ? sloping * -1 : sloping;
					sample.mProbes[i] = history.get(j - 1).mProbes[i] + sloping
							* (Math.random() * 2);
				}
			}

			// Seventh is the fan speed/lid open
			sample.mFanSpeed = 0.0;
			sample.mLidOpen = 0.0;

			history.add(sample);
		}
		return history;
	}

	public ArrayList<Sample> parseHistory(Reader reader)
	{
		try
		{
			ArrayList<Sample> history = new ArrayList<Sample>();

			CSVReader csvReader = new CSVReader(reader);

			String[] nextLine;
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
					if (!nextLine[i + 2].equals("nan"))
					{
						sample.mProbes[i] = Double.parseDouble(nextLine[i + 2]);
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

	private NamedSample addHistory(ArrayList<Sample> history)
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
			latestSample = new NamedSample(mSamples.get(mSamples.size() - 1));
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
			for (String line = null; (line = reader.readLine()) != null;)
			{
				builder.append(line).append("\n");

				// FIXME - http://code.google.com/p/android/issues/detail?id=14562
				// For Android 2.x, reader gets closed and throws an exception
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
	
	private void set()
	{
		String setAddr = "http://" + mServerAddress + "/luci/;stok=" + mAuthToken + "/admin/lm/set?";
		setAddr += "sp=200";
		
		HttpURLConnection urlConnection = null;

		try
		{
			URL url = new URL(setAddr);
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setDoOutput(true);
			urlConnection.setChunkedStreamingMode(0);
			urlConnection.setRequestProperty("Cookie", "sysauth=" + mAuthCookie);

			urlConnection.getInputStream();
		}
		catch (MalformedURLException e)
		{
			if (BuildConfig.DEBUG)
				Log.e(TAG, "Bad server address");
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (urlConnection != null)
			urlConnection.disconnect();
	}

	private void authenticate()
	{
		HttpURLConnection urlConnection = null;

		try
		{
			URL url = new URL("http://" + mServerAddress + kAuthURL);
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setDoOutput(true);
			urlConnection.setChunkedStreamingMode(0);

			urlConnection.setDoInput(true);
			urlConnection.setRequestMethod("POST");

			DataOutputStream out = new DataOutputStream(urlConnection.getOutputStream());
			out.writeBytes("username=root&");
			out.writeBytes("password=" + URLEncoder.encode(mAdminPassword, "UTF-8"));
			out.flush();
			out.close();

			String cookieHeader = urlConnection.getHeaderField("Set-Cookie");
			String[] cookies = cookieHeader.split(";");

			for(String cookie: cookies)
			{
				String[] cookieChunks = cookie.split("=");
				String cookieKey = cookieChunks[0];
				if (cookieKey.equals("sysauth"))
					mAuthCookie = cookieChunks[1];
				else if (cookieKey.equals("stok"))
					mAuthToken = cookieChunks[1];
			}
		}
		catch (MalformedURLException e)
		{
			if (BuildConfig.DEBUG)
				Log.e(TAG, "Bad server address");
		}
		catch (ProtocolException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (urlConnection != null)
			urlConnection.disconnect();
	}
}
