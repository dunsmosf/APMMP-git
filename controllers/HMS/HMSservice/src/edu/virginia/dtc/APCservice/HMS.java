//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import Jama.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.TimeZone;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Array;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Mode;
import edu.virginia.dtc.SysMan.State;

import edu.virginia.dtc.Tvector.Tvector;

public class HMS {
	private static final boolean DEBUG_MODE = true;
	private static final double FDA_MANDATED_MAXIMUM_CORRECTION_BOLUS = 3.0;
	private Context context;
	private Subject subject;
	public HMSData hms_data; 		// This class encapsulates the state variables that are being estimated and that must persist.
	public final String TAG = "HMSservice";
	// Identify owner of record in User Table 1
	public static final int HMS_IOB_CONTROL = 50;
	// Interface definitions for the biometricsContentProvider
	public static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
    public static final Uri USER_TABLE_1_URI = Uri.parse("content://"+ PROVIDER_NAME + "/user1");
    public static final Uri HMS_STATE_ESTIMATE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/hmsstateestimate");

	// Parameter definitions
    double CORRECTION_TARGET = 110.0;
    double CORRECTION_THRESHOLD = 180.0;
    double CORRECTION_FACTOR = 0.6;
    double MINIMUM_TIME_BETWEEN_CORRECTIONS_MINS = 60;
    double MINIMUM_CORRECTION_BOLUS = 0.10;
    
	// log_action priority levels
	private static final int LOG_ACTION_UNINITIALIZED = 0;
	private static final int LOG_ACTION_INFORMATION = 1;
	private static final int LOG_ACTION_DEBUG = 2;
	private static final int LOG_ACTION_NOT_USED = 3;
	private static final int LOG_ACTION_WARNING = 4;
	private static final int LOG_ACTION_SERIOUS = 5;
	
	// TIme intervals during which HMS may recommend bolus
//	int HMS_START_TIME_HOURS = 7;			// HMS may recommend boluses starting at 7 AM
//	int HMS_STOP_TIME_HOURS = 22;			// HMS must stop recommending boluses at 10 PM
	
	public static final int CGM_window_size_seconds = 62*60;		// Accommodate 8 data points: 60 minute window with 2 minutes of margin
	
	public boolean valid = false;
	boolean first_alg_tick=false;
	boolean Second_alg_tick=false;

	public HMS(
				long time,				// Seconds since 1/1/1970 
				double IOB,
				double cgm,
				double Gest,
				double Gpred_30m, 
				Context calling_context) {
				
		context = calling_context;
		subject = new Subject(time, calling_context);
		// Initialize the class that holds our state data
		if ((hms_data = new HMSData()) != null) {
			// Generate the first state estimate
			if (subject.valid) {
				valid = true;
			} 
			else {
				valid = false;
			}
		}
	}
	
	public double HMS_calculation(
			long time,									// Seconds since 1/1/1970 
			double IOB,
			Tvector Tvec_cgm,
			double Gest,
			double Gpred_30m, 
			Context calling_context) {
		double return_value = 0.0;
		
		// 1. Update the subject data by reading latest profile values from the database
		subject.read(time, context);
		if (subject.valid == false) {		// Protect against state estimates with uninitialized data.
			return 0.0;
		}
		// 2. Are we in a time interval during which HMS corrections are permitted?
		int timeOfDayOffsetSecs = subject.getTimeOfDayOffsetSecs(time);
		hms_data.read(calling_context);
		Log.i("Corr_Time"," first  "+Long.toString(hms_data.correction_time_in_seconds));
		
		
				//detect the second alg tick
				if (first_alg_tick)
				{
					Second_alg_tick=true;
					first_alg_tick=false;
				}
				//Detect the first tick then do nothing if it is the first tick !
				if  (hms_data.correction_time_in_seconds==-1)
				{
					first_alg_tick=true;
				}
				Log.i("Corr_Time"," first Alg Tick =  "+first_alg_tick);
				Log.i("Corr_Time"," Second Alg Tick =  "+Second_alg_tick);
				Log.i("Corr_Time"," reference    "+CGM_8point_protection(time,Tvec_cgm, Gpred_30m, Gest));
				if (Second_alg_tick){
					// 3. If the predicted BG greater than the threshold then calculate correction bolus
					if (CGM_8point_protection(time,Tvec_cgm, Gpred_30m, Gest)>CORRECTION_THRESHOLD && subject.CF>=10.0 && subject.CF<=200.0) {
						return_value = CORRECTION_FACTOR*((CGM_8point_protection(time,Tvec_cgm, Gpred_30m, Gest)-CORRECTION_TARGET)/subject.CF - Math.max(IOB, 0.0));
					}
					Second_alg_tick=false;
				}
				
				if (hms_data.valid) {
					if (time>hms_data.correction_time_in_seconds+MINIMUM_TIME_BETWEEN_CORRECTIONS_MINS*60) {
						
						// 3. If the predicted BG greater than the threshold then calculate correction bolus
						if (CGM_8point_protection(time,Tvec_cgm, Gpred_30m, Gest)>CORRECTION_THRESHOLD && subject.CF>=10.0 && subject.CF<=200.0) {
							return_value = CORRECTION_FACTOR*((CGM_8point_protection(time,Tvec_cgm, Gpred_30m, Gest)-CORRECTION_TARGET)/subject.CF - Math.max(IOB, 0.0));
						}
						
					}
				Log.i("Corr_Time"," Second  "+Long.toString(hms_data.correction_time_in_seconds)+"  corr = "+return_value);
				
			
		}
		/*
		// 3. Enforce a maximum correction bolus size
		if (return_value > FDA_MANDATED_MAXIMUM_CORRECTION_BOLUS) {
			return_value = FDA_MANDATED_MAXIMUM_CORRECTION_BOLUS;
		}
		*/
		// 4. Enforce a minimum correction bolus size
		if (return_value > MINIMUM_CORRECTION_BOLUS) {
			hms_data.correction_time_in_seconds = time;
		}
		else {
			return_value = 0.0;
		}
		debug_message(TAG, "HMS_calculation: return_value="+return_value);
		return return_value;
	}
	
	
	//function returns Gest if less than 8 pints cgm , Gpred otherwise
    public double CGM_8point_protection(long time, Tvector Tvec_cgm, double Gpred, double Gest){
    	List<Integer> indices;
    	double reference=0;
    	if ((indices = Tvec_cgm.find(">", time-CGM_window_size_seconds, "<=", time)) != null) {
			//debug_message("VCU_test", "cgm ind size "+indices.size());
					
				if (indices.size() <= 8) {
					reference=Gest;
					Log.i("protection"," || There is more than 8 ==>> "+indices.size());
				}
				else { 
					reference=Gpred;	
					Log.i("protection"," || There is less than 8 ==>> "+indices.size());
				}
			
    	}
    	return reference;
		
    }
			
 	public void log_action(String service, String action) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("time", (long)(System.currentTimeMillis()/1000));
        context.sendBroadcast(i);
	}
 	
	public void log_action(String service, String action, int priority) {
		Log.i(TAG, "LOG ACTION > "+action);
		
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("priority", priority);
        i.putExtra("time", (long)(System.currentTimeMillis()/1000));
        context.sendBroadcast(i);
	}

	private static void debug_message(String tag, String message) {
		if (DEBUG_MODE) {
			Log.i(tag, message);
		}
	}
	
	private static void error_message(String tag, String message) {
		Log.e(tag, "Error: "+message);
	}
	
	public void storeUserTable1Data(long time,
			double INS_target_sat,
			double INS_target_slope_sat,
			double differential_basal_rate, 
			double MealBolusA,
			double MealBolusArem,
			double spend_request,
			double CorrA,
			double IOBrem,
			double d,
			double h,
			double H,
			double cgm_slope1,
			double cgm_slope2,
			double cgm_slope_diff,
			double X,
			double detect_meal) {
				ContentValues values = new ContentValues();
				values.put("time", time);
				values.put("l0", HMS_IOB_CONTROL);
				values.put("d0", INS_target_sat);
				values.put("d1", INS_target_slope_sat);
				values.put("d2", differential_basal_rate);
				values.put("d3", MealBolusA);
				values.put("d4", MealBolusArem);
				values.put("d5", spend_request);
				values.put("d6", CorrA);
				values.put("d7", IOBrem);
				values.put("d8", d);
				values.put("d9", h);
				values.put("d10", H);
				values.put("d11", cgm_slope1);
				values.put("d12", cgm_slope2);
				values.put("d13", cgm_slope_diff);
				values.put("d14", X);
				values.put("d15", detect_meal);
				Uri uri;
				try {
					uri = context.getContentResolver().insert(USER_TABLE_1_URI, values);
				}
				catch (Exception e) {
					Log.e(TAG, e.getMessage());
				}		
		}

}
