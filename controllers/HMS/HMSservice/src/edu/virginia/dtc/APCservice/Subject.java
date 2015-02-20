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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

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
	
	
	private static final String TAG = "HMSservice";

	public Subject() 
	{
		
	}
	
	public boolean read(Context calling_context) {
		final String FUNC_TAG = "read";
		
		long time = System.currentTimeMillis()/1000;
		// Get the offset in seconds into the current day in the current time zone (based on cell phone time zone setting)
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(time*1000)/1000;
		int timeNowSecs = (int)(time+UTC_offset_secs)%(1440*60);
		
		Debug.i(TAG, FUNC_TAG, "Time:"+time+" Time Now Seconds:"+timeNowSecs);
		List<Integer> indices = new ArrayList<Integer>();
		
		// Get the latest subject data and profiles from biometricsContentProvider
		DiAsSubjectData subject_data;
		if ((subject_data = DiAsSubjectData.readDiAsSubjectData(calling_context)) == null) {
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
}
