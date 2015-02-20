//*********************************************************************************************************************
//  Copyright 2011-2015 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//
//  Last Modification: 2/2015 Mize 
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import java.util.TimeZone;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Mode;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.Tvector.Tvector;

public class HMS {
	public final String TAG = "HMSservice";
	
	// Parameter definitions
    private static double CORRECTION_TARGET = 110.0;
    private static double CORRECTION_THRESHOLD = 180.0;
    private static double CORRECTION_FACTOR = 0.6;
    
    private static double MINIMUM_CORRECTION_BOLUS = 0.10;
    
    private static long MINIMUM_TIME_BETWEEN_CORRECTIONS_MINS = 60;
    
	private static final int CGM_WINDOW_SIZE_SEC = 62*60;		// Accommodate 8 data points: 60 minute window with 2 minutes of margin

	private Context context;
	public Subject subject;
	
	double Iob, Gpred, Gpred_30m;

	public HMS(Context ctx) {
		context = ctx;
		subject = new Subject();
		subject.read(context);
	}
	
	// **************************************************************************************************
	// **************************************************************************************************
	// MAIN CALCULATION
	// **************************************************************************************************
	// **************************************************************************************************
	
	public double HMS_calculation() {
		final String FUNC_TAG = "HMS_calculation";
		
		double bolus = 0.0;
		double reference = 0.0;
		Iob = Gpred = Gpred_30m = 0.0;
		
		// 1. Check mode of operation
		// ==================================================================================
		if ( Mode.getApcStatus(context.getContentResolver()) == Mode.CONTROLLER_DISABLED_WITHIN_PROFILE ) {
			Debug.i(TAG, FUNC_TAG, "APC is disabled within 'Time Profile' - Mode = "+Mode.getMode(context.getContentResolver()));
			
			TimeZone tz = TimeZone.getDefault();
			int UTC_offset_secs = tz.getOffset(getCurrentTimeSeconds()*1000)/1000;
			int timeNowMins = (int)((getCurrentTimeSeconds()+UTC_offset_secs)/60)%1440;
			
			if (Mode.isInProfileRange(context.getContentResolver(), timeNowMins)) {
				Debug.w(TAG, FUNC_TAG, "We are in the time range & mode=" + Mode.getMode(context.getContentResolver()) + ", corrections set to zero!");
				return 0.0;
			}
			else
				Debug.i(TAG, FUNC_TAG, "We are NOT in the time range...proceed with correction");
		}
		else if ( Mode.getApcStatus(context.getContentResolver()) == Mode.CONTROLLER_ENABLED_WITHIN_PROFILE ) {
			
			Debug.i(TAG, FUNC_TAG, "APC is enabled within 'Time Profile' - Mode = "+Mode.getMode(context.getContentResolver()));
			
			TimeZone tz = TimeZone.getDefault();
			int UTC_offset_secs = tz.getOffset(getCurrentTimeSeconds()*1000)/1000;
			int timeNowMins = (int)((getCurrentTimeSeconds()+UTC_offset_secs)/60)%1440;
			
			if (!Mode.isInProfileRange(context.getContentResolver(), timeNowMins)) {
				Debug.w(TAG, FUNC_TAG, "We are not in the time range & mode=" + Mode.getMode(context.getContentResolver()) + ", corrections set to zero!");
				return 0.0;
			}
			else
				Debug.i(TAG, FUNC_TAG, "We are in the time range...proceed with correction");
		}
		else
			Debug.i(TAG, FUNC_TAG, "We are NOT in a BRM only night mode - "+Mode.getMode(context.getContentResolver())+"...proceed with correction");
		
		// 2. Get the most recent state estimate data
		// ==================================================================================
		if(!fetchStateEstimateData()) {
			Debug.w(TAG, FUNC_TAG, "Unable to read State Estimate table...returning zero");
			return 0.0;
		}
		
		// 3. Check for any pending or delivering meals
		// ==================================================================================
		if(!hms_req_meal_protect(10)) {
			Debug.w(TAG, FUNC_TAG, "There is still a meal being delivered within the last 10 minutes...returning zero");
			return 0.0;
		}
		
		// 4. Update the subject data by reading latest profile values from the database
		// ==================================================================================
		if (subject.read(context)) {
			Debug.w(TAG, FUNC_TAG, "There is not sufficient subject data to estimate...returning zero");
			return 0.0;
		}
		
		// 5. Check for previous corrections, so we don't do corrections too frequently
		// ==================================================================================
		if(checkForCorrections(MINIMUM_TIME_BETWEEN_CORRECTIONS_MINS)) {
			Debug.w(TAG, FUNC_TAG, "There was a correctiong within the last "+MINIMUM_TIME_BETWEEN_CORRECTIONS_MINS+" minutes...returning zero");
			return 0.0;
		}
		
		// 6. Calculate reference based on amount of CGM data
		// ==================================================================================
		reference = CGM_8point_protection(CGM_WINDOW_SIZE_SEC, Gpred_30m, Gpred);
		Debug.i(TAG, FUNC_TAG, "Reference: "+reference);
		
		// 7. Calculate correction bolus
		// ==================================================================================
		if (reference > CORRECTION_THRESHOLD && subject.CF >= 10.0 && subject.CF <= 200.0) {
			bolus = CORRECTION_FACTOR * ((reference - CORRECTION_TARGET)/subject.CF - Math.max(Iob, 0.0));
		}
		
		// 8. Check bolus against minimum correction
		// ==================================================================================
		if(bolus <= MINIMUM_CORRECTION_BOLUS) {
			Debug.w(TAG, FUNC_TAG, "Calculated bolus is less than "+MINIMUM_CORRECTION_BOLUS+"...returning zero");
			return 0.0;
		}

		return bolus;
	}
	
	// **************************************************************************************************
	// **************************************************************************************************
	// HELPER FUNCTIONS
	// **************************************************************************************************
	// **************************************************************************************************
	
	private boolean checkForCorrections(long timeFrame) {
		//Checking for any non-zero correction requests within the last 60 minutes
		long time = (System.currentTimeMillis()/1000) - (timeFrame * 60);
   		Cursor c = context.getContentResolver().query(Biometrics.INSULIN_URI, new String[]{"req_time","req_corr"}, "req_corr > 0.0 AND time > "+time, null, null);
   		
   		//Basically, if there are any results, then it is true
   		if(c.moveToFirst()) {
   			c.close();
   			return true;
   		}
   		
   		return false;
   	}
	
	//TODO: check type, this checks all boluses sync or async
	private boolean hms_req_meal_protect(int duration) {
		final String FUNC_TAG = "hms_req_meal_protect";
		boolean return_value = false;
		
		// Function to protect against injecting corrections within a certain amount of time "duration" after requesting a meal bolus.
		Cursor c = context.getContentResolver().query(Biometrics.INSULIN_URI, new String[]{"req_time","req_meal","req_corr","status"}, "req_meal>0 OR req_corr>0", null, "req_time DESC Limit 1");
		long requested_meal_time = 0;
		int status = -1;
		if (c.moveToFirst()) {
			if (!c.isNull(c.getColumnIndex("status"))) {
				requested_meal_time = c.getLong(c.getColumnIndex("req_time"));
				status = c.getInt(c.getColumnIndex("status"));
				if (((getCurrentTimeSeconds()-requested_meal_time) < (duration*60)) && ((status==Pump.PENDING) || (status==Pump.DELIVERING)))
					return_value = true;
			}			
		}
		else {
			Debug.e(TAG, FUNC_TAG, "Insulin Table empty!");
		}
		c.close();
		
		return return_value;
	}
	
	private boolean fetchStateEstimateData() {
		final String FUNC_TAG = "fetchStateEstimateData";
		
		boolean return_value = false;
		
		Iob = Gpred = Gpred_30m = 0.0;
		
		// Fetch most recent synchronous row from State Estimate data records
		Cursor c = context.getContentResolver().query(Biometrics.STATE_ESTIMATE_URI, new String[]{"asynchronous", "time", "IOB", "Gpred", "Gbrakes"}, "asynchronous = 0", null, "time DESC LIMIT 1");
		
		if (c.moveToFirst()) {
			Iob = c.getDouble(c.getColumnIndex("IOB"));
			Gpred = c.getDouble(c.getColumnIndex("Gpred"));			//AKA Gest
			Gpred_30m = c.getDouble(c.getColumnIndex("Gbrakes"));	//AKA Gpred_30m
			return_value = true;
		}
		else {
			Debug.e(TAG, FUNC_TAG, "State Estimate Table empty!");
		}
		c.close();
		
		return return_value;
	}
	
	//TODO: check if this is intended? <= 8 or < 8 ???
	// Function returns Gest if less than 8 points of cgm, Gpred otherwise
	private double CGM_8point_protection(long timeFrame, double Gpred, double Gest) {
    	final String FUNC_TAG = "CGM_8point_protection";
    	
    	long time = (System.currentTimeMillis()/1000) - timeFrame;
    	double reference;
    	
    	Cursor c = context.getContentResolver().query(Biometrics.CGM_URI, new String[]{"cgm", "time"}, "time > "+time, null, "time DESC");
    	
    	if(!c.moveToFirst()) {
    		Debug.e(TAG, FUNC_TAG, "The cursor is null, using Gest!");
    		c.close();
    		return Gest;
    	}
    	
    	if(c.getCount() <= 8) {
    		Debug.i(TAG, FUNC_TAG, "There are 8 or fewer CGM points, using Gest...");
    		reference = Gest;
    	} else {
    		Debug.i(TAG, FUNC_TAG, "There are more than 8 CGM points, using Gpred...");
    		reference = Gpred;
    	}
    	
    	c.close();
    	
    	return reference;
    }
	
	private long getCurrentTimeSeconds() {
		return (System.currentTimeMillis()/1000);			// Seconds since 1/1/1970
	}
}
