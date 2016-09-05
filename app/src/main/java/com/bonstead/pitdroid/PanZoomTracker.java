package com.bonstead.pitdroid;

/*
 * Holds pan/zoom window details
 * GraphActivity is appears to be destroyed and recreated when changing
 * orientation.  This class holds the pan and zoom window dimensions
 * so that when GraphActivity is reinstantiated the same portion of the
 * graph is displayed.
 */
public class PanZoomTracker
{
	public int domainWindowSpan = 0;
	public Range<Number> domainWindow = null;

	public boolean panning = false;

	/*
	 * Convenience class for storing min and max of a range Value of the class is the
	 * delta between min and max
	 */
	public static class Range<T extends Number>
	{
		public T min;
		public T max;

		public Range(T min, T max)
		{
			this.min = min;
			this.max = max;
		}

		public T getMin()
		{
			return min;
		}

		public T getMax()
		{
			return max;
		}

		// Return range delta as the value
		public int intValue()
		{
			return max.intValue() - min.intValue();
		}

		public float floatValue()
		{
			return max.floatValue() - min.floatValue();
		}

		public void setMin(T newValue)
		{
			min = newValue;
		}

		public void setMax(T newValue)
		{
			max = newValue;
		}
	}
}
