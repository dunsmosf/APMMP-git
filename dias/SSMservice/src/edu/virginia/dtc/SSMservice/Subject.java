//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.SSMservice;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.Tvector.Tvector;

public class Subject extends Object	{
		private static boolean DEBUG_MODE = true;
		public static final String TAG = "DiAsSubjectData";
		public boolean valid = false;
	
		// Default remote monitoring URI
		public static final String REMOTE_MONITORING_URI = "https://";
		// Storage for subject session parameters
		public boolean realTime;
		public String remoteMonitoringURI;
	   	public String subjectName, subjectSession;
	   	public int subjectAIT, subjectWeight, subjectHeight, subjectAge;
	   	public double subjectTDI;
	   	public boolean subjectFemale;
	   	public Tvector subjectCR, subjectCF, subjectBasal, subjectSafety;
		public double CF;
		public double CR;
		public double basal;
	   	
	   	// Field validity flags
	   	public boolean subjectNameValid;
	   	public boolean subjectSessionValid;
	   	public boolean weightValid;
	   	public boolean heightValid;
	   	public boolean ageValid;
	   	public boolean TDIValid;
	   	public boolean sexIsFemale;
	   	public boolean AITValid;
	   	public boolean subjectCFValid;
	   	public boolean subjectCRValid;
	   	public boolean subjectBasalValid;
		public boolean subjectSafetyValid;

		public Subject(long time, Context calling_context) {
			// Allocate Tvectors
	   		subjectCR = new Tvector(24);
	   		subjectCF = new Tvector(24);
	   		subjectBasal = new Tvector(24);
	   		subjectSafety = new Tvector(24);
	   		// Initialize data values
			init();
			if (read(time, calling_context))
				valid = true;
			else
				valid = false;
		}
		
	   	private void init() {
	   		// Instantiation happens  exactly once so initialize all fields to known values here.
	   		remoteMonitoringURI = new String(REMOTE_MONITORING_URI);
	   		realTime = true;
	   		subjectName = new String("");
	   		subjectSession = new String("");
	   		subjectAIT=0;
	   		subjectWeight=0;
	   		subjectHeight=0;
	   		subjectAge=0;
	   		subjectTDI=0.0;
	   		subjectFemale=false;
	   		subjectNameValid = false;
	   		subjectSessionValid = false;
	   		weightValid = false;
	   		heightValid = false;
	   		ageValid = false;
	   		TDIValid = false;
	   		sexIsFemale = true;
	   		AITValid = false;
	   		subjectCFValid = false;
	   		subjectCRValid = false;
	   		subjectBasalValid = false;
	   		subjectSafetyValid = false;
	   	}

	   	public boolean read(long time, Context calling_context) {
			// Fetch the Hardware Settings Preferences
			// If there is a subjectdata table in the biometricsContentProvider database then read it and initialize the field values
			Cursor c = calling_context.getContentResolver().query(Biometrics.SUBJECT_DATA_URI, null, null, null, null);
			Log.i(TAG, "Retrieved SUBJECT_DATA_URI with " + c.getCount() + " items");
			if (c.moveToLast()) {
				// A database exists.  Initialize subject_data.
				subjectName = new String(c.getString(c.getColumnIndex("subjectid")));
				subjectSession = new String(c.getString(c.getColumnIndex("session")));
				subjectWeight = (c.getInt(c.getColumnIndex("weight")));
				subjectHeight = (c.getInt(c.getColumnIndex("height")));
				subjectAge = (c.getInt(c.getColumnIndex("age")));
				subjectTDI = (c.getInt(c.getColumnIndex("TDI")));
				//	     		subjectAIT = (c.getInt(c.getColumnIndex("AIT")));
				subjectAIT = 4; // Force AIT == 4 for safety

				int isfemale = c.getInt(c.getColumnIndex("isfemale"));
				if (isfemale == 1)
					subjectFemale = true;
				else
					subjectFemale = false;

				int SafetyOnlyModeIsEnabled = c.getInt(c.getColumnIndex("SafetyOnlyModeIsEnabled"));
				if (SafetyOnlyModeIsEnabled == 1)
					subjectSafetyValid = true;
				else
					subjectSafetyValid = false;

				int realtime = c.getInt(c.getColumnIndex("realtime"));
				if (realtime == 1)
					realTime = true;
				else
					realTime = false;

				// Set flags
				subjectNameValid = true;
				subjectSessionValid = true;
				weightValid = true;
				heightValid = true;
				ageValid = true;
				TDIValid = true;
				AITValid = true;
			}
			else {
			}
			c.close();
			
			subjectCR = new Tvector(24);
	   		subjectCF = new Tvector(24);
	   		subjectBasal = new Tvector(24);
	   		subjectSafety = new Tvector(24);
			
			if (readTvector(subjectCF, Biometrics.CF_PROFILE_URI, calling_context))
				subjectCFValid = true;
			if (readTvector(subjectCR, Biometrics.CR_PROFILE_URI, calling_context))
				subjectCRValid = true;
			if (readTvector(subjectBasal, Biometrics.BASAL_PROFILE_URI, calling_context))
				subjectBasalValid = true;
			if (readTvector(subjectSafety, Biometrics.USS_BRM_PROFILE_URI, calling_context))
				subjectSafetyValid = true;
			c.close();
			
			// Get the offset in seconds into the current day in the current time zone (based on cell phone time zone setting)
			TimeZone tz = TimeZone.getDefault();
			int UTC_offset_secs = tz.getOffset(time*1000)/1000;
			int timeNowSecs = (int)(time+UTC_offset_secs)%(1440*60);
			debug_message(TAG, "subject_parameters > time="+time+", timeNowSecs="+timeNowSecs);
			List<Integer> indices = new ArrayList<Integer>();
			
			// Get current CF value
			indices = subjectCF.find(">", -1, "<=", timeNowSecs/60);			// Find the list of indices <= time in minutes since today at 00:00
			if (indices == null) {
				indices = subjectCF.find(">", -1, "<", -1);				// Use final value from the previous day's profile
			}
			else if (indices.size() == 0) {
				indices = subjectCF.find(">", -1, "<", -1);				// Use final value from the previous day's profile
			}
			if (indices != null) {
				CF = subjectCF.get_value(indices.get(indices.size()-1));	// Return the last CF in this range		
			}
			else {
				debug_message(TAG, "read > CF read failed");
				return false;
			}

			// Get current CR value
			indices = subjectCR.find(">", -1, "<=", timeNowSecs/60);			// Find the list of indices <= time in minutes since today at 00:00
			if (indices == null) {
				indices = subjectCR.find(">", -1, "<", -1);				// Use final value from the previous day's profile
			}
			else if (indices.size() == 0) {
				indices = subjectCR.find(">", -1, "<", -1);				// Use final value from the previous day's profile
			}
			if (indices != null) {
				CR = subjectCR.get_value(indices.get(indices.size()-1));	// Return the last CR in this range		
			}
			else {
				debug_message(TAG, "read > CR read failed");
				return false;
			}

			// Get current basal value
			indices = subjectBasal.find(">", -1, "<=", timeNowSecs/60);		// Find the list of indices <= time in minutes since today at 00:00
			if (indices == null) {
				indices = subjectBasal.find(">", -1, "<", -1);				// Use final value from the previous day's profile
			}
			else if (indices.size() == 0) {
				indices = subjectBasal.find(">", -1, "<", -1);				// Use final value from the previous day's profile
			}
			if (indices != null) {
				basal = subjectBasal.get_value(indices.get(indices.size()-1));	// Return the last basal in this range		
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
