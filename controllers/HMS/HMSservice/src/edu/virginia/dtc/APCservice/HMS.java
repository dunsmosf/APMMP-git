//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
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
    private static double MINIMUM_TIME_BETWEEN_CORRECTIONS_MINS = 60;
    private static double MINIMUM_CORRECTION_BOLUS = 0.10;
    
	private static final int CGM_window_size_seconds = 62*60;		// Accommodate 8 data points: 60 minute window with 2 minutes of margin

	private double correctionUnits = 0.0;
	private long correctionTimeSec = -1;
	
	private Context context;
	public Subject subject;
	
	double Iob, Gpred, Gpred_30m;
	
	private boolean firstTick=false;
	private boolean secondTick=false;

	public HMS(Context ctx) {
		context = ctx;
		subject = new Subject(getCurrentTimeSeconds(), ctx);
		
		correctionUnits = 0.0;
		correctionTimeSec = -1;
	}
	
	// **************************************************************************************************
	// **************************************************************************************************
	// MAIN CALCULATION
	// **************************************************************************************************
	// **************************************************************************************************
	
	public double HMS_calculation() {
		final String FUNC_TAG = "HMS_calculation";
		
		long time = getCurrentTimeSeconds();
		double bolus = 0.0;
		double reference = 0.0;
		Iob = Gpred = Gpred_30m = 0.0;
		
		// 1. Get the most recent state estimate data
		// ==================================================================================
		if(!fetchStateEstimateData()) {
			Debug.w(TAG, FUNC_TAG, "Unable to read State Estimate table...returning zero");
			return 0.0;
		}
		
		// 2. Check for any pending or delivering meals
		// ==================================================================================
		if(!hms_req_meal_protect(10)) {
			Debug.w(TAG, FUNC_TAG, "There is still a meal being delivered within the last 10 minutes...returning zero");
			return 0.0;
		}
		
		// 3. Update the subject data by reading latest profile values from the database
		// ==================================================================================
		subject.read(time, context);
		if (!subject.valid) {
			Debug.w(TAG, FUNC_TAG, "There is not sufficient subject data to estimate...returning zero");
			return 0.0;
		}
		
		//TODO: use local function to check for corrections...
		correctionTimeSec = checkForPreviousCorrections();
		
		
		Debug.i(TAG, FUNC_TAG, "First Corr Time in Sec: "+correctionTimeSec);
		Debug.i(TAG, FUNC_TAG, "First Tick: "+firstTick+" Second Tick: "+secondTick);
		
		//Detect the second alg tick
		if (firstTick) {
			secondTick = true;
			firstTick = false;
		}
		
		//Detect the first tick then do nothing if it is the first tick !
		if  (correctionTimeSec == -1) {
			firstTick=true;
		}
		
		reference = CGM_8point_protection(time, Gpred_30m, Gpred);
		Debug.i(TAG, FUNC_TAG, "Reference: "+reference);
		
		//TODO: Add time checks to the bolus return values (simplest...)						DONE
		//TODO: Don't look in state estimate, look in insulin for requested corrections (any!)	DONE
		//TODO: If no boluses in 25 hours then it will run the double bolus check
		//TODO: no corrections until MDI is entered
		
		// 3a. If the predicted BG greater than the threshold then calculate correction bolus
		// ==================================================================================
		if (secondTick){
			if (reference > CORRECTION_THRESHOLD && subject.CF >= 10.0 && subject.CF <= 200.0) {
				bolus = CORRECTION_FACTOR*((reference-CORRECTION_TARGET)/subject.CF - Math.max(Iob, 0.0));
			}
			secondTick=false;
		}
		
		if (time > correctionTimeSec + MINIMUM_TIME_BETWEEN_CORRECTIONS_MINS*60) {
			// 3b. If the predicted BG greater than the threshold then calculate correction bolus
			// ==================================================================================
			if (reference > CORRECTION_THRESHOLD && subject.CF >= 10.0 && subject.CF <= 200.0) {
				bolus = CORRECTION_FACTOR * ((reference - CORRECTION_TARGET)/subject.CF - Math.max(Iob, 0.0));
			}
		}
		Debug.i(TAG, FUNC_TAG,"Second Corr Time in Sec: "+correctionTimeSec+" Corr: "+bolus);
		
		// 4. Enforce a minimum correction bolus size
		// ==================================================================================
		if (bolus > MINIMUM_CORRECTION_BOLUS)
			correctionTimeSec = time;
		else
			bolus = 0.0;
		
		// 5. Check mode of operation
		// *******************************************************************************************
		if ( Mode.getApcStatus(context.getContentResolver()) == Mode.CONTROLLER_DISABLED_WITHIN_PROFILE ) {
			Debug.w(TAG, FUNC_TAG, "APC is disabled within 'BRM Profile' - Mode = "+Mode.getMode(context.getContentResolver()));
			
			TimeZone tz = TimeZone.getDefault();
			int UTC_offset_secs = tz.getOffset(getCurrentTimeSeconds()*1000)/1000;
			int timeNowMins = (int)((getCurrentTimeSeconds()+UTC_offset_secs)/60)%1440;
			
			if (Mode.isInProfileRange(context.getContentResolver(), timeNowMins)) {
				Debug.w(TAG, FUNC_TAG, "We are in the BRM range & mode=" + Mode.getMode(context.getContentResolver()) + ", corrections set to zero!");
				bolus = 0.0;
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
				bolus = 0.0;
			}
			else
				Debug.i(TAG, FUNC_TAG, "We are in the BRM range...");
		}
		else
			Debug.i(TAG, FUNC_TAG, "We are NOT in a BRM only night mode - "+Mode.getMode(context.getContentResolver()));
		
		
		Debug.i(TAG, FUNC_TAG, "--------------------------------------");
		Debug.i(TAG, FUNC_TAG, "Final HMS Return Value: "+bolus);
		Debug.i(TAG, FUNC_TAG, "--------------------------------------");
		return bolus;
	}
	
	// **************************************************************************************************
	// **************************************************************************************************
	// HELPER FUNCTIONS
	// **************************************************************************************************
	// **************************************************************************************************
	
	private long checkForPreviousCorrections() {
   		long time = -1;
   		Cursor c = context.getContentResolver().query(Biometrics.INSULIN_URI, new String[]{"req_time","req_corr"}, "req_corr > 0.0", null, null);
   		
   		if(c.moveToFirst())
   		{
   			time = c.getLong(c.getColumnIndex("req_time"));
   			c.close();
   		}
   		
   		return time;
   	}
	
	//TODO: check type, this checks all boluses sync or async
	private boolean hms_req_meal_protect(int duration) {
		final String FUNC_TAG = "hms_req_meal_protect";
		boolean return_value = false;
		
		// Function to protect against injecting corrections within a certain amount of time "duration" after requesting a meal bolus.
		Cursor c = context.getContentResolver().query(Biometrics.INSULIN_URI,new String[]{"req_time","req_meal","req_corr","status"},"req_meal>0 OR req_corr>0", null, "req_time DESC Limit 1");
		long requested_meal_time = 0;
		int status = -1;
		if (c.moveToFirst()) {
			if (!c.isNull(c.getColumnIndex("status"))) {
				requested_meal_time = c.getLong(c.getColumnIndex("req_time"));
				status=c.getInt(c.getColumnIndex("status"));
				if (((getCurrentTimeSeconds()-requested_meal_time) < (duration*60)) && ((status==Pump.PENDING) || (status==Pump.DELIVERING))) {
					return_value = true;
				}				
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
		
		// Initialize
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
	
	//TODO: check if this is intended? <= 8 or <8 ???
	// Function returns Gest if less than 8 points of cgm, Gpred otherwise
	private double CGM_8point_protection(long time, double Gpred, double Gest) {
    	final String FUNC_TAG = "CGM_8point_protection";
    	
    	// Create the timestamp for 62 minutes ago
    	long duration = time - CGM_window_size_seconds;
    	double reference;
    	
    	Cursor c = context.getContentResolver().query(Biometrics.CGM_URI, new String[]{"cgm", "time"}, "time > "+duration, null, "time DESC");
    	
    	if(!c.moveToFirst()) {
    		Debug.e(TAG, FUNC_TAG, "The cursor is null, using Gest!");
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
