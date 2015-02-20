//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.BRMservice;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import android.R.string;
import android.content.SharedPreferences;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import edu.virginia.dtc.Tvector.Tvector;

public class Subject {
	public double CF;
	public double CR;
	public double basal;
	public double TDI;
	public double weight;
	public double height;
	public double age;
	public String sessionID;
	public boolean valid = false;
	
	private static boolean DEBUG_MODE = true;
	public static final String TAG = "BRMservice";
	// Interface definitions for the biometricsContentProvider
	public static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
	public static final Uri SUBJECT_DATA_URI = Uri.parse("content://" + PROVIDER_NAME + "/subjectdata");
	public static final Uri CF_PROFILE_URI = Uri.parse("content://" + PROVIDER_NAME + "/cfprofile");
	public static final Uri CR_PROFILE_URI = Uri.parse("content://" + PROVIDER_NAME + "/crprofile");
	public static final Uri BASAL_PROFILE_URI = Uri.parse("content://" + PROVIDER_NAME + "/basalprofile");
	public static final Uri SAFETY_PROFILE_URI = Uri.parse("content://" + PROVIDER_NAME + "/safetyprofile");

	public Subject(long time, Context calling_context) {
		if (read(time, calling_context))
			valid = true;
		else
			valid = false;
	}
	
	public void dump() {
		debug_message(TAG, "Subject> CF="+CF+", CR="+CR+", basal="+basal+", TDI="+TDI);
		debug_message(TAG, "Subject> weight="+weight+", height="+height+", age="+age+", valid="+valid);
	}
	
	
	public boolean read(long time, Context calling_context) {
		// Get the offset in seconds into the current day in the current time zone (based on cell phone time zone setting)
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(time*1000)/1000;
		int timeNowSecs = (int)(time+UTC_offset_secs)%(1440*60);
		debug_message(TAG, "subject_parameters > time="+time+", timeNowSecs="+timeNowSecs);
		List<Integer> indices = new ArrayList<Integer>();
		
		// Get the latest subject data and profiles from biometricsContentProvider
		DiAsSubjectData subject_data;
		if ((subject_data = readDiAsSubjectData(calling_context)) == null) {
			debug_message(TAG, "read > readDiAsSubjectData failed");
			return false;
		}
		
		// Get subject parameters
		TDI = subject_data.subjectTDI;
		weight = subject_data.subjectWeight;
		height = subject_data.subjectHeight;
		age = subject_data.subjectAge;
		sessionID = subject_data.subjectSession;
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
			debug_message(TAG, "read > CF read failed");
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
			debug_message(TAG, "read > CR read failed");
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
			debug_message(TAG, "read > basal read failed");
			return false;
		}

		return true;
	}
	
	public int getTimeOfDayOffsetSecs(long time) {
		// Get the offset in seconds into the current day in the current time zone (based on cell phone time zone setting)
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(time*1000)/1000;
		int timeNowSecs = (int)(time+UTC_offset_secs)%(1440*60);
		debug_message(TAG, "getTimeNowSecs="+timeNowSecs);
		return timeNowSecs;
	}

	private DiAsSubjectData readDiAsSubjectData(Context calling_context) {
		DiAsSubjectData subject_data = DiAsSubjectData.getInstance();
		// Fetch the Hardware Settings Preferences
		// If there is a subjectdata table in the biometricsContentProvider database then read it and initialize the field values
		Cursor c = calling_context.getContentResolver().query(SUBJECT_DATA_URI, null, null, null, null);
		Log.i(TAG, "Retrieved SUBJECT_DATA_URI with " + c.getCount() + " items");
		if (c.moveToLast()) {
			// A database exists.  Initialize subject_data.
			subject_data.subjectName = new String(c.getString(c.getColumnIndex("subjectid")));
			subject_data.subjectSession = new String(c.getString(c.getColumnIndex("session")));
			subject_data.subjectWeight = (c.getInt(c.getColumnIndex("weight")));
			subject_data.subjectHeight = (c.getInt(c.getColumnIndex("height")));
			subject_data.subjectAge = (c.getInt(c.getColumnIndex("age")));
			subject_data.subjectTDI = (c.getInt(c.getColumnIndex("TDI")));
			//	     		subject_data.subjectAIT = (c.getInt(c.getColumnIndex("AIT")));
			subject_data.subjectAIT = 4; // Force AIT == 4 for safety

			int isfemale = c.getInt(c.getColumnIndex("isfemale"));
			if (isfemale == 1)
				subject_data.subjectFemale = true;
			else
				subject_data.subjectFemale = false;

//			int SafetyOnlyModeIsEnabled = c.getInt(c.getColumnIndex("SafetyOnlyModeIsEnabled"));
//			if (SafetyOnlyModeIsEnabled == 1)
//				subject_data.subjectSafetyValid = true;
//			else
//				subject_data.subjectSafetyValid = false;
//
//			int realtime = c.getInt(c.getColumnIndex("realtime"));
//			if (realtime == 1)
//				subject_data.realTime = true;
//			else
//				subject_data.realTime = false;

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
		if (readTvector(subject_data.subjectCF, CF_PROFILE_URI, calling_context))
			subject_data.subjectCFValid = true;
		else
			return null;
		if (readTvector(subject_data.subjectCR, CR_PROFILE_URI, calling_context))
			subject_data.subjectCRValid = true;
		else
			return null;
		if (readTvector(subject_data.subjectBasal, BASAL_PROFILE_URI, calling_context))
			subject_data.subjectBasalValid = true;
		else
			return null;
		if (readTvector(subject_data.subjectSafety, SAFETY_PROFILE_URI, calling_context))
			subject_data.subjectSafetyValid = true;
//		else
//			return null;
		c.close();
		return subject_data;
	}
	
	public boolean readTvector(Tvector tvector, Uri uri, Context calling_context) {
		boolean retvalue = false;
		Cursor c = calling_context.getContentResolver().query(uri, null, null, null, null);
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
	
	private static void debug_message(String tag, String message) {
		if (DEBUG_MODE) {
			Log.i(tag, message);
		}
	}

}
