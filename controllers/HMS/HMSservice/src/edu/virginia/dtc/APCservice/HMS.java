//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import java.util.List;
import java.util.TimeZone;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Mode;
import edu.virginia.dtc.Tvector.Tvector;

public class HMS {
	private Context context;
	private Subject subject;
	public HMSData hms_data; 		// This class encapsulates the state variables that are being estimated and that must persist.
	public final String TAG = "HMSservice";
	
	// Identify owner of record in User Table 1
	public static final int HMS_IOB_CONTROL = 50;

	// Parameter definitions
    static double CORRECTION_TARGET = 110.0;
    static double CORRECTION_THRESHOLD = 180.0;
    static double CORRECTION_FACTOR = 0.6;
    static double MINIMUM_TIME_BETWEEN_CORRECTIONS_MINS = 60;
    static double MINIMUM_CORRECTION_BOLUS = 0.10;
    
	public static final int CGM_window_size_seconds = 62*60;		// Accommodate 8 data points: 60 minute window with 2 minutes of margin
	
	boolean valid = false;
	boolean firstTick=false;
	boolean secondTick=false;

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
		if ((hms_data = HMSData.getInstance()) != null) {
			valid = subject.valid;
		}
	}
	
	public double HMS_calculation(
			long time, 
			double IOB,
			Tvector Tvec_cgm,
			double Gest,
			double Gpred_30m, 
			Context calling_context) {
		
		final String FUNC_TAG = "HMS_calculation";
		double return_value = 0.0;
		
		// 1. Update the subject data by reading latest profile values from the database
		// *******************************************************************************************
		subject.read(time, context);
		if (subject.valid == false) {		// Protect against state estimates with uninitialized data.
			return 0.0;
		}
		
		// 2. Are we in a time interval during which HMS corrections are permitted?
		// *******************************************************************************************
		hms_data.read(calling_context);
		Debug.i(TAG, FUNC_TAG, "First Corr Time in Sec: "+hms_data.correction_time_in_seconds);
		Debug.i(TAG, FUNC_TAG, "First Tick: "+firstTick+" Second Tick: "+secondTick);
		
		//Detect the second alg tick
		if (firstTick) {
			secondTick=true;
			firstTick=false;
		}
		
		//Detect the first tick then do nothing if it is the first tick !
		if  (hms_data.correction_time_in_seconds == -1) {
			firstTick=true;
		}
		
		Debug.i(TAG, FUNC_TAG,"Reference: "+CGM_8point_protection(time,Tvec_cgm, Gpred_30m, Gest));
		
		//TODO: Add time checks to the bolus return values (simplest...)
		//TODO: Don't look in state estimate, look in insulin for requested corrections (any!)
		//TODO: If no boluses in 25 hours then it will run the double bolus check
		//TODO: no corrections until MDI is entered
		
		// 3a. If the predicted BG greater than the threshold then calculate correction bolus
		// *******************************************************************************************
		if (secondTick){
			if (CGM_8point_protection(time,Tvec_cgm, Gpred_30m, Gest)>CORRECTION_THRESHOLD && subject.CF>=10.0 && subject.CF<=200.0) {
				return_value = CORRECTION_FACTOR*((CGM_8point_protection(time,Tvec_cgm, Gpred_30m, Gest)-CORRECTION_TARGET)/subject.CF - Math.max(IOB, 0.0));
			}
			secondTick=false;
		}
		
		if (hms_data.valid) {
			if (time > hms_data.correction_time_in_seconds + MINIMUM_TIME_BETWEEN_CORRECTIONS_MINS*60) {
				// 3b. If the predicted BG greater than the threshold then calculate correction bolus
				// *******************************************************************************************
				if (CGM_8point_protection(time,Tvec_cgm, Gpred_30m, Gest)>CORRECTION_THRESHOLD && subject.CF>=10.0 && subject.CF<=200.0) {
					return_value = CORRECTION_FACTOR*((CGM_8point_protection(time,Tvec_cgm, Gpred_30m, Gest)-CORRECTION_TARGET)/subject.CF - Math.max(IOB, 0.0));
				}
			}
			Debug.i(TAG, FUNC_TAG,"Second Corr Time in Sec: "+hms_data.correction_time_in_seconds+" Corr: "+return_value);
		}
		
		// 4. Enforce a minimum correction bolus size
		// *******************************************************************************************
		if (return_value > MINIMUM_CORRECTION_BOLUS) {
			hms_data.correction_time_in_seconds = time;
		}
		else {
			return_value = 0.0;
		}
		
		// 5. Check mode of operation
		// *******************************************************************************************
		if ( Mode.getApcStatus(context.getContentResolver()) == Mode.CONTROLLER_DISABLED_WITHIN_PROFILE ) {
			
			Debug.w(TAG, FUNC_TAG, "APC is disabled within 'BRM Profile' - Mode = "+Mode.getMode(context.getContentResolver()));
			
			TimeZone tz = TimeZone.getDefault();
			int UTC_offset_secs = tz.getOffset(getCurrentTimeSeconds()*1000)/1000;
			int timeNowMins = (int)((getCurrentTimeSeconds()+UTC_offset_secs)/60)%1440;
			
			if (Mode.isInProfileRange(context.getContentResolver(), timeNowMins)) {
				Debug.w(TAG, FUNC_TAG, "We are in the BRM range & mode=" + Mode.getMode(context.getContentResolver()) + ", corrections set to zero!");
				return_value = 0.0;
			}
			else
				Debug.i(TAG, FUNC_TAG, "We are NOT in the BRM range...");
		}
		else if ( Mode.getApcStatus(context.getContentResolver()) == Mode.CONTROLLER_ENABLED_WITHIN_PROFILE ) {
			
			Debug.w(TAG, FUNC_TAG, "APC is enabled within 'BRM Profile' - Mode = "+Mode.getMode(context.getContentResolver()));
			
			TimeZone tz = TimeZone.getDefault();
			int UTC_offset_secs = tz.getOffset(getCurrentTimeSeconds()*1000)/1000;
			int timeNowMins = (int)((getCurrentTimeSeconds()+UTC_offset_secs)/60)%1440;
			
			if (!Mode.isInProfileRange(context.getContentResolver(), timeNowMins)) {
				Debug.w(TAG, FUNC_TAG, "We are not in the BRM range & mode=" + Mode.getMode(context.getContentResolver()) + ", corrections set to zero!");
				return_value = 0.0;
			}
			else
				Debug.i(TAG, FUNC_TAG, "We are in the BRM range...");
		}
		else
			Debug.i(TAG, FUNC_TAG, "We are NOT in a BRM only night mode - "+Mode.getMode(context.getContentResolver()));
		
		
		Debug.i(TAG, FUNC_TAG, "--------------------------------------");
		Debug.i(TAG, FUNC_TAG, "Final HMS Return Value: "+return_value);
		Debug.i(TAG, FUNC_TAG, "--------------------------------------");
		return return_value;
	}
	
	
	public long getCurrentTimeSeconds() {
		long SystemTime = (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970
			return SystemTime;
	}
	
	//function returns Gest if less than 8 pints cgm , Gpred otherwise
    public double CGM_8point_protection(long time, Tvector Tvec_cgm, double Gpred, double Gest) {
    	final String FUNC_TAG = "CGM_8point_protection";
    	List<Integer> indices;
    	double reference=0;
    	if ((indices = Tvec_cgm.find(">", time-CGM_window_size_seconds, "<=", time)) != null) {
				if (indices.size() <= 8) {
					reference=Gest;
					Debug.i(TAG, FUNC_TAG,"There is more than 8 ==>> "+indices.size());
				}
				else { 
					reference=Gpred;	
					Debug.i(TAG, FUNC_TAG,"There is less than 8 ==>> "+indices.size());
				}
			
    	}
    	return reference;
		
    }
 	
	public void log_action(String service, String action, int priority) {
		Debug.i(TAG, "log_action", action);
		
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("priority", priority);
        i.putExtra("time", (long)(System.currentTimeMillis()/1000));
        context.sendBroadcast(i);
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
				try {
					context.getContentResolver().insert(Biometrics.USER_TABLE_1_URI, values);
				}
				catch (Exception e) {
					Log.e(TAG, e.getMessage());
				}		
		}

}
