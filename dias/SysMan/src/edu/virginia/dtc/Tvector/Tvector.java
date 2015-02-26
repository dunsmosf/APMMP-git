//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.Tvector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.virginia.dtc.SysMan.Debug;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class Tvector extends Object {
	private static final String TAG = "Tvector > ";
	private final int TvectorDefaultCapacity = 2048;
    private List<Pair> pairs = new ArrayList<Pair>();
	private int capacity;
	private int pairCount;
	
	public Tvector() {
		capacity = TvectorDefaultCapacity;
		pairCount = 0;
	}
	
	public Tvector(int size) {
		capacity = size;
		pairCount = 0;
	}
	
	public Tvector(Tvector another) {
		this.capacity = another.capacity;
		int hh = 0;
		for (hh=0; hh<another.count(); hh++) {
			this.put( another.get(hh).time(), another.get(hh).value() );
		}
	}
	
	public void init() {
		pairs.clear();
		pairCount = 0;
	}
	
	public Integer count() {
		return Integer.valueOf(pairCount);
	}

	public Integer capacity() {
		return Integer.valueOf(capacity);
	}
	
	public void put_with_sum(long t, double v) {
		Pair p = new Pair();
		double oldValue;
		// If the new pair has the same time as an existing pair then add to the value of the existing pair
		int index = Collections.binarySearch(pairs, p, new PairComparator());
		if (index  <  0) {
			if (pairCount < capacity) {
				pairs.add(pairCount++, p);
			}
			else {
				pairs.remove(0);
				pairs.add(0, p);			
			}
			Collections.sort(pairs, new PairComparator());			
		}
		else {
//			Log.i("Tvector > put_with_sum", "Add to element with duplicate time.");
			oldValue = pairs.get(index).value();
			pairs.remove(index);
			p.put(t, oldValue+v);
			pairs.add(index, p);
		}
	}
	
	public void put_with_replace(long t, double v) {
		Pair p = new Pair();
		p.put(t, v);
		int index;
		// If the new pair has the same time as an existing pair then replace the existing pair
//		Log.i("Collections.binarySearch", Collections.binarySearch(pairs, p, new PairComparator())+"");
		if ((index = Collections.binarySearch(pairs, p, new PairComparator())) <  0) {
			if (pairCount < capacity) {
				pairs.add(pairCount++, p);
			}
			else {
				pairs.remove(0);
				pairs.add(0, p);			
			}
			Collections.sort(pairs, new PairComparator());			
		}
		else {
			Log.i("Tvector > put_with_replace", "Replace element with duplicate time.");
			pairs.remove(index);
			pairs.add(index, p);
		}
	}
	
	public void put_time_range_with_replace(long startTime, long endTime) {
		Pair p = new Pair();
		p.put(startTime, endTime);
		int index;
		// If the new pair has the same time as an existing pair then replace the existing pair
//		Log.i("Collections.binarySearch", Collections.binarySearch(pairs, p, new PairComparator())+"");
		if ((index = Collections.binarySearch(pairs, p, new PairComparator())) <  0) {
			if (pairCount < capacity) {
				pairs.add(pairCount++, p);
			}
			else {
				pairs.remove(0);
				pairs.add(0, p);			
			}
			Collections.sort(pairs, new PairComparator());			
		}
		else {
			Log.i("Tvector > put_with_replace", "Replace element with duplicate time.");
			pairs.remove(index);
			pairs.add(index, p);
		}
	}
	
	public void remove(int n) {
		if (pairCount > 0) {
			pairs.remove(n);
			pairCount--;
		}
	}
	
	public void put(long t, double v) {
		Pair p = new Pair();
		p.put(t, v);
		// Do not insert a pair that has a duplicate time
//		Log.i("Collections.binarySearch", Collections.binarySearch(pairs, p, new PairComparator())+"");
		if (Collections.binarySearch(pairs, p, new PairComparator()) <  0) {
			if (pairCount < capacity) {
				pairs.add(pairCount++, p);
			}
			else {
				pairs.remove(0);
				pairs.add(0, p);			
			}
			Collections.sort(pairs, new PairComparator());			
		}
		else {
			Log.i("Tvector > put", "Attempt to insert element with duplicate time.");
		}
	}
	
	public void put_range(long t, long t2) {
		Pair p = new Pair();
		p.put(t, t2);
		// Do not insert a pair that has a duplicate time
//		Log.i("Collections.binarySearch", Collections.binarySearch(pairs, p, new PairComparator())+"");
		if (Collections.binarySearch(pairs, p, new PairComparator()) <  0) {
			if (pairCount < capacity) {
				pairs.add(pairCount++, p);
			}
			else {
				pairs.remove(0);
				pairs.add(0, p);			
			}
			Collections.sort(pairs, new PairComparator());			
		}
		else {
			Log.i("Tvector > put", "Attempt to insert element with duplicate time.");
		}
	}
	
	public Pair get(int n) {
		if (n < 0 || n > pairCount || n > capacity) {
			return null;
		}
		else {
			return pairs.get(n);			
		}
	}
	
	public Double get_last_value() {
		if (pairCount == 0) {
			return Double.NaN;
		}
		else {
			return pairs.get(pairCount-1).value();			
		}
	}
	
	public Long get_last_time() {
		if (pairCount == 0) {
			return new Long(0);
		}
		else {
			return pairs.get(pairCount-1).time();			
		}
	}
	
	public Double get_value_using_time_as_index(long t) {
		Pair p = new Pair();
		p.put(t, 0);
		int index = Collections.binarySearch(pairs, p, new PairComparator());
		if (index >= 0)
			return pairs.get(index).value();
		else
			return null;
//		return pairs.get(Collections.binarySearch(pairs, p, new PairComparator())).value();
	}
	
	public Double get_value(int n) {
		if (pairCount == 0) {
			return Double.NaN;
		}
		else {
			return pairs.get(n).value();			
		}
	}
	
	public Long get_time(int n) {
		return pairs.get(n).time();			
	}
	
	public Long get_end_time(int n){
		return pairs.get(n).endTime();
	}
	
	public Integer replace(long time, double value, int n) {
		if (n < 0 || n > pairCount || n > capacity) {
			return Integer.valueOf(0);
		}
		else {
			pairs.get(n).put(time, value);
			return Integer.valueOf(1);
		}
	}
	
	public Integer replace_value(double value, int n) {
		if (n < 0 || n > pairCount || n > capacity) {
			return Integer.valueOf(0);
		}
		else {
			long time = pairs.get(n).time();
			pairs.get(n).put(time, value);
			return Integer.valueOf(1);
		}		
	}
	
	public Integer replace_last_value(double value) {
		if (pairCount == 0) {
			return Integer.valueOf(0);
		}
		else {
			long time = pairs.get(pairCount-1).time();
			pairs.get(pairCount-1).put(time, value);
			return Integer.valueOf(1);
		}		
	}
	
	public Pair getLastInRange(String tmin_cmp, long tmin, String tmax_cmp, long tmax) {
		List<Integer> indices = find(tmin_cmp, tmin, tmax_cmp, tmax);
		if (indices != null) {
			return get(indices.get(indices.size()-1));			
		}
		else {
			return null;
		}
	}

	public List<Integer> getLastN(int N) {
		int iiMin, iiMax, ii;
		// Find the lower index of the selected range
		iiMin = pairCount - N;
		iiMax = pairCount - 1;
		if (iiMin > 0 && iiMin <= iiMax) {		// At least one pair is in range
			List<Integer> index = new ArrayList<Integer>();
			ii = iiMin;
			for (ii=iiMin; ii<=iiMax; ii++) {
				index.add(Integer.valueOf(ii));
			}
			return index;
		}
		else {
			return null;
		}
	}
	
	public List<Integer> find(String tmin_cmp, long tmin, String tmax_cmp, long tmax) {
		int iiMin, iiMax, ii;
		// Find the lower index of the selected range
		if (tmin < 0) {
			iiMin = 0;
		}
		else if (tmin_cmp.equals(">")) {
			iiMin = capacity;
			ii = pairCount-1;
			while (ii >= 0) {
				if (pairs.get(ii).time() > tmin) {
					iiMin = ii;
				}
				ii--;
			}
		}
		else {
			iiMin = capacity;
			ii = pairCount-1;
			while (ii >= 0) {
//				Log.i("Tvector", "tmin="+tmin+", time["+ii+"]="+pairs.get(ii).time()+", value["+ii+"]="+pairs.get(ii).value());
				if (pairs.get(ii).time() >= tmin) {
					iiMin = ii;
				}
				ii--;
			}
		}
		// Find the upper index of the selected range
		if (tmax < 0) {
			iiMax = pairCount-1;
		}
		else if (tmax_cmp.equals("<")) {
			iiMax = -1;
			ii = 0;
			while (ii<pairCount) {
				if (pairs.get(ii).time() < tmax) {
					iiMax = ii;
				}
				ii++;
			}
		}
		else {
			iiMax = -1;
			ii = 0;
			while (ii < pairCount) {
				if (pairs.get(ii).time() <= tmax) {
					iiMax = ii;
				}
				ii++;
			}
		}
		if (iiMin <= iiMax) {		// At least one pair is in range
			List<Integer> index = new ArrayList<Integer>();
			ii = iiMin;
			for (ii=iiMin; ii<=iiMax; ii++) {
				index.add(Integer.valueOf(ii));
			}
			return index;
		}
		else {
			return null;
		}
	}
	
	public void dump() {
		int ii;
		Log.i(TAG, "dump()");
		for (ii=0; ii<pairCount; ii++)	{
			Log.i(TAG,"ii="+ii+", time="+get_time(ii)+", value="+get_value(ii));
		}
	}

	public void dump(String s) {
		int ii;
		Log.i(TAG, "dump("+s+ ")");
		for (ii=0; ii<pairCount; ii++)	{
			Log.i(s,"ii="+ii+", time="+get_time(ii)+", value="+get_value(ii));
		}
	}
	
	public void dump(String s1, String s2) {
		int ii;
		for (ii=0; ii<pairCount; ii++)	{
			Log.i(s1, s2+" > ii="+ii+", time="+get_time(ii)+", value="+get_value(ii));
		}
	}
	
	public void dump(String s1, String s2, int n) {				// dump last n pairs
		int ii;
		int startIndex;
		if (pairCount-n-1 < 0)
			startIndex = 0;
		else
			startIndex = pairCount-n-1;
		for (ii=startIndex; ii<pairCount; ii++)	{
			Log.i(s1, s2+" > ii="+ii+", time="+get_time(ii)+", value="+get_value(ii));
		}
	}
	
	
	public static void putTvector(Bundle bundle, Tvector tvector, String startTimeKey, String endTimeKey, String valueKey) {
		long[] times = new long[tvector.count()];
		long[] endTimes = new long[tvector.count()];
		double[] values = new double[tvector.count()];
		int ii;
		
		for (ii = 0; ii < tvector.count(); ii++) {
			times[ii] = tvector.get_time(ii).longValue();
			endTimes[ii] = tvector.get_end_time(ii).longValue();
			values[ii] = tvector.get_value(ii).doubleValue();
		}
		
		if (startTimeKey != null)
			bundle.putLongArray(startTimeKey, times);
		if (endTimeKey != null)
			bundle.putLongArray(endTimeKey, endTimes);
		if (valueKey != null)
			bundle.putDoubleArray(valueKey, values);
	}
	
	
	public static Tvector getTvector(Bundle bundle, String startTimeKey, String endTimeKey, String valueKey) {
		int ii;
		long[] times = bundle.getLongArray(startTimeKey);
		long[] endTimes = bundle.getLongArray(endTimeKey);
		double[] values = bundle.getDoubleArray(valueKey);
		Tvector tvector = new Tvector(times.length);
		for (ii=0; ii<times.length; ii++) {
			if (endTimes == null){
				tvector.put(times[ii], values[ii]);	
			} else if (values == null){
				tvector.put_range(times[ii], endTimes[ii]);
			}			
		}
		return tvector;
    }
	
	public static boolean readTvector(Tvector tvector, Uri uri, ContentResolver resolver) {
		boolean retvalue = false;
		Cursor c = resolver.query(uri, null, null, null, null);
		long t, t2 = 0;
		double v;
		if (c.moveToFirst()) {
			do {
				t = c.getLong(c.getColumnIndex("time"));
				if (c.getColumnIndex("endtime") < 0){
					v = c.getDouble(c.getColumnIndex("value"));
					Log.i(TAG, "readTvector: t=" + t + ", v=" + v);
					tvector.put_with_replace(t, v);
				} else if (c.getColumnIndex("value") < 0){
					Log.i(TAG, "readTvector: t=" + t + ", t2=" + t2);
					t2 = c.getLong(c.getColumnIndex("endtime"));
					tvector.put_time_range_with_replace(t, t2);
				}
			} while (c.moveToNext());
			retvalue = true;
		}
		c.close();
		return retvalue;
	}
	
	public static void saveTvector(Tvector tvector, Uri uri, boolean value, ContentResolver resolver) 
	{
		final String FUNC_TAG = "saveTvector";
		
		int ii;
		ContentValues content_values = new ContentValues();
		for (ii = 0; ii < tvector.count(); ii++) 
		{
			content_values.put("time", tvector.get_time(ii).longValue());
			
			if (tvector.get_end_time(ii) != 0)
				content_values.put("endtime", tvector.get_end_time(ii).longValue());
			
			if (tvector.get_value(ii) >= 0 && value)
				content_values.put("value", (tvector.get_value(ii).doubleValue()));
			
			try {
				resolver.insert(uri, content_values);
			} catch (Exception e) {
				Debug.e(TAG, FUNC_TAG,"Error: " + e.getMessage());
			}
		}
	}
	
}
