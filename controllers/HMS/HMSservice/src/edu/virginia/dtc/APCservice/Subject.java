//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import android.content.SharedPreferences;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.DiAsSubjectData;
import edu.virginia.dtc.Tvector.Tvector;

public class Subject {
	public double CF;
	public double CR;
	public double basal;
	public double TDI;
	public double weight;
	public double height;
	public double age;
	public boolean valid = false;
	
	private static final String TAG = "HMSservice";

	public Subject(long time, Context calling_context) {
		valid = read(time, calling_context);
	}
	
	public void dump() {
		final String FUNC_TAG = "dump";
		Debug.i(TAG, FUNC_TAG, "CF:"+CF+" CR:"+CR+" Basal:"+basal+" TDI:"+TDI);
		Debug.i(TAG, FUNC_TAG, "Weight:"+weight+" Height:"+height+" Age:"+age+" Valid:"+valid);
	}
	
	public boolean read(long time, Context calling_context) {
		final String FUNC_TAG = "read";
		
		// Get the offset in seconds into the current day in the current time zone (based on cell phone time zone setting)
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(time*1000)/1000;
		int timeNowSecs = (int)(time+UTC_offset_secs)%(1440*60);
		
		Debug.i(TAG, FUNC_TAG, "Time:"+time+" Time Now Seconds:"+timeNowSecs);
		List<Integer> indices = new ArrayList<Integer>();
		
		// Get the latest subject data and profiles from biometricsContentProvider
		DiAsSubjectData subject_data;
		if ((subject_data = readDiAsSubjectData(calling_context)) == null) {
			Debug.w(TAG, FUNC_TAG, "Subject database failed to be read...");
			return false;
		}
		
		// Get subject parameters
		TDI = subject_data.subjectTDI;
		weight = subject_data.subjectWeight;
		height = subject_data.subjectHeight;
		age = subject_data.subjectAge;
		
		// Get current CF value
		indices = subject_data.subjectCF.find(">", -1, "<=", timeNowSecs/60);			// Find the list of indices <= time in minutes since today at 00:00
		if (indices == null) {
			indices = subject_data.subjectCF.find(">", -1, "<", -1);				// Use final value from the previous day's profile
		}
		else if (indices.size() == 0) {
			indices = subject_data.subjectCF.find(">", -1, "<", -1);				// Use final value from the previous day's profile
		}
		if (indices != null) {
			CF = subject_data.subjectCF.get_value(indices.get(indices.size()-1));	// Return the last CF in this range		
		}
		else {
			Debug.w(TAG, FUNC_TAG, "CF read failed...");
			return false;
		}

		// Get current CR value
		indices = subject_data.subjectCR.find(">", -1, "<=", timeNowSecs/60);			// Find the list of indices <= time in minutes since today at 00:00
		if (indices == null) {
			indices = subject_data.subjectCR.find(">", -1, "<", -1);				// Use final value from the previous day's profile
		}
		else if (indices.size() == 0) {
			indices = subject_data.subjectCR.find(">", -1, "<", -1);				// Use final value from the previous day's profile
		}
		if (indices != null) {
			CR = subject_data.subjectCR.get_value(indices.get(indices.size()-1));	// Return the last CR in this range		
		}
		else {
			Debug.w(TAG, FUNC_TAG, "CR read failed...");
			return false;
		}

		// Get current basal value
		indices = subject_data.subjectBasal.find(">", -1, "<=", timeNowSecs/60);		// Find the list of indices <= time in minutes since today at 00:00
		if (indices == null) {
			indices = subject_data.subjectBasal.find(">", -1, "<", -1);				// Use final value from the previous day's profile
		}
		else if (indices.size() == 0) {
			indices = subject_data.subjectBasal.find(">", -1, "<", -1);				// Use final value from the previous day's profile
		}
		if (indices != null) {
			basal = subject_data.subjectBasal.get_value(indices.get(indices.size()-1));	// Return the last basal in this range		
		}
		else {
			Debug.w(TAG, FUNC_TAG, "Basal read failed...");
			return false;
		}

		return true;
	}
	
	public int getTimeOfDayOffsetSecs(long time) {
		// Get the offset in seconds into the current day in the current time zone (based on cell phone time zone setting)
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(time*1000)/1000;
		int timeNowSecs = (int)(time+UTC_offset_secs)%(1440*60);
		
		Debug.i(TAG, "getTimeOfDayOffsetSecs", "Time Now Seconds: "+timeNowSecs);
		
		return timeNowSecs;
	}

	private DiAsSubjectData readDiAsSubjectData(Context calling_context) {
		DiAsSubjectData subject_data = new DiAsSubjectData();
		
		// If there is a subjectdata table in the biometricsContentProvider database then read it and initialize the field values
		Cursor c = calling_context.getContentResolver().query(Biometrics.SUBJECT_DATA_URI, null, null, null, null);
		Log.i(TAG, "Retrieved SUBJECT_DATA_URI with " + c.getCount() + " items");
		if (c.moveToLast()) {
			// Initialize subject_data.
			subject_data.subjectName = new String(c.getString(c.getColumnIndex("subjectid")));
			subject_data.subjectSession = new String(c.getString(c.getColumnIndex("session")));
			subject_data.subjectWeight = (c.getInt(c.getColumnIndex("weight")));
			subject_data.subjectHeight = (c.getInt(c.getColumnIndex("height")));
			subject_data.subjectAge = (c.getInt(c.getColumnIndex("age")));
			subject_data.subjectTDI = (c.getInt(c.getColumnIndex("TDI")));
			subject_data.subjectAIT = 4; // Force AIT == 4 for safety

			int isfemale = c.getInt(c.getColumnIndex("isfemale"));
			if (isfemale == 1)
				subject_data.subjectFemale = true;
			else
				subject_data.subjectFemale = false;

			int SafetyOnlyModeIsEnabled = c.getInt(c.getColumnIndex("SafetyOnlyModeIsEnabled"));
			if (SafetyOnlyModeIsEnabled == 1)
				subject_data.subjectSafetyValid = true;
			else
				subject_data.subjectSafetyValid = false;

			int realtime = c.getInt(c.getColumnIndex("realtime"));
			if (realtime == 1)
				subject_data.realTime = true;
			else
				subject_data.realTime = false;

			// Set flags
			subject_data.subjectNameValid = true;
			subject_data.subjectSessionValid = true;
			subject_data.weightValid = true;
			subject_data.heightValid = true;
			subject_data.ageValid = true;
			subject_data.TDIValid = true;
			subject_data.AITValid = true;
		}
		else {
			return null;
		}
		c.close();
		
		if (readTvector(subject_data.subjectCF, Biometrics.CF_PROFILE_URI, calling_context))
			subject_data.subjectCFValid = true;
		else
			return null;
		if (readTvector(subject_data.subjectCR, Biometrics.CR_PROFILE_URI, calling_context))
			subject_data.subjectCRValid = true;
		else
			return null;
		if (readTvector(subject_data.subjectBasal, Biometrics.BASAL_PROFILE_URI, calling_context))
			subject_data.subjectBasalValid = true;
		else
			return null;
		if (readTvector(subject_data.subjectSafety, Biometrics.SAFETY_PROFILE_URI, calling_context))
			subject_data.subjectSafetyValid = true;
		
		c.close();
		return subject_data;
	}
	
	private boolean readTvector(Tvector tvector, Uri uri, Context calling_context) {
		boolean retvalue = false;
		Cursor c = calling_context.getContentResolver().query(uri, null, null, null, null);
		long t, t2 = 0;
		double v;
		if (c.moveToFirst()) {
			do {
				t = c.getLong(c.getColumnIndex("time"));
				if (c.getColumnIndex("endtime") < 0){
					v = c.getDouble(c.getColumnIndex("value"));
					tvector.put_with_replace(t, v);
				} else if (c.getColumnIndex("value") < 0){
					t2 = c.getLong(c.getColumnIndex("endtime"));
					tvector.put_time_range_with_replace(t, t2);
				}
			} while (c.moveToNext());
			retvalue = true;
		}
		c.close();
		return retvalue;
	}
}
