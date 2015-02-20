//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.BRMservice;

import java.util.List;
import java.util.TimeZone;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Log;
import edu.virginia.dtc.Tvector.Tvector;

public class InsulinTherapy {
	private final String TAG = "BRMservice";
	
	private Context context;
	private Subject subject;
	
	// Identify owner of record in User Table 1
	private static final int MEAL_IOB_CONTROL = 10;

	private static final int CGM_WINDOW_SIZE_SEC = 62*60;		// Accommodate 8 data points: 60 minute window with 2 minutes of margin
	private final static double T2tgt = 30.0;
	
	// Store data in globals for logging to User Table 1
	private double INS_target_sat_user1;
	private double INS_target_slope_sat_user1;
	private double cgm_slope1_user1;
	private double cgm_slope2_user1;
	private double cgm_slope_diff_user1;
	private double X_user1;
	private double detect_meal_user1;
	
	//Variables created for glucose target calculation (glucose target can be tuned by changing these parameters)
	private static final int N_end=7;
	private static final int N_start=23;
	private static final int N_length=8;
	private final static int N_glucoseTarget=7;
	private static final double Taux=0.2;
	private static final double Gmax=160;
	private static final double Gspred=40;
	
	private double Iob, Gest, Gbrakes;
	private long GestTime, GbrakesTime;
	
	public InsulinTherapy(Context calling_context) {
		context = calling_context;
		subject = new Subject();
	}
	
	public double insulin_therapy_calculation() {
		final String FUNC_TAG = "insulin_therapy";
		
		double diff_rate = 0.0;
		Iob = Gest = Gbrakes = 0.0;
		GestTime = GbrakesTime = 0;
		
		// 1. Update the subject data by reading latest profile values from the database
		// ==================================================================================
		if(!subject.read(context)) {
			Debug.w(TAG, FUNC_TAG, "There is not sufficient subject data to estimate...returning 0.0");
			return 0.0;
		}
		
		// 2. Fetch state estimate data
		// ==================================================================================
		if(!fetchStateEstimateData()) {
			Debug.w(TAG, FUNC_TAG, "Unable to read State Estimate table...returning zero");
			return 0.0;
		}

		// 3. Compute the differential basal rate
		// ==================================================================================
		diff_rate = differentialBasalRate(CGM_WINDOW_SIZE_SEC, subject.basal, saturate_CF(subject.CF));
		
		// 4. Save internal data into user table 1
		// ==================================================================================
		storeUserTable1Data(System.currentTimeMillis()/1000, INS_target_sat_user1, INS_target_slope_sat_user1, diff_rate, 
				0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 
				cgm_slope1_user1, cgm_slope2_user1, cgm_slope_diff_user1, X_user1, detect_meal_user1);
		
		return diff_rate;
	}	
	
	// Return differential basal rate in U/hour
	private double differentialBasalRate(long timeFrame, double basal, double CF) {
    	final String FUNC_TAG = "differentialBasalRate";
    	
		double return_value = 0.0;

	   	double INS_target, INS_target_sat, INS_target_predicted;
	   	long time = (System.currentTimeMillis()/1000) - timeFrame;	// Calculate duration using time frame
	   	
	   	Debug.i(TAG, FUNC_TAG, "Min 1800/TDI - CF: "+CF);
	   	
	   	Cursor c = context.getContentResolver().query(Biometrics.CGM_URI, new String[]{"cgm", "time"}, "time > "+time, null, "time DESC");
    	
	   	// Insulin Target is calculated based on the test : if we have 8 values or more in the last value (use G=Gbrakes (predicted glucose over 30 min)) 
  		// If there is less than 8 values in the last hour, use the value: G=Gpred (in this case Gest)
  		// Insulin target= (G-G_target)/min(1800/TDI,CF)
	   	
    	if(c.getCount() >= 8) {
    		Debug.i(TAG, FUNC_TAG, "There are 8 or more CGM points...");
    		INS_target=(Gbrakes-glucoseTarget(GbrakesTime))/CF;
    	} else {
    		Debug.i(TAG, FUNC_TAG, "There are less than 8 CGM points...");
    		INS_target=(Gest-glucoseTarget(GestTime))/CF;
    	}
    	Debug.i(TAG, FUNC_TAG, "INS_target: "+INS_target);
			
		// Calculate the predicted value of INS_target
		INS_target_sat = INS_target_saturate(INS_target, CF);
		INS_target_predicted = INS_target_sat;
		INS_target_sat_user1 = INS_target_sat;
		
		// Calculate a Rate
		double rate = (INS_target_predicted - Math.max(Iob, 0.0))/T2tgt;
		Debug.i(TAG, FUNC_TAG, "rate: "+rate+ " INS_target_pred: "+INS_target_predicted+" IOB_sat: "+Math.max(Iob, 0.0));
		
		// Calculate the differential basal rate
		double basal_ceiling = basal_saturate(Gest, basal);
		Debug.i(TAG, FUNC_TAG, "basal: "+basal+" basal_ceiling: "+basal_ceiling);
		
		if (rate > (basal_ceiling/60.0))
			return_value = (basal_ceiling/60.0);
		else if (rate > 0.0 && rate <= (basal_ceiling/60.0))
			return_value = rate;
		else
			return_value = 0.0;
		
		// Log glucose and insulin targets
		Log.log_action(context, TAG, 
				" INS_target: "+String.format("%.2f", INS_target)+
				" INS_target_sat: "+String.format("%.2f", INS_target_sat)+
				" IOB_sat: "+String.format("%.2f", Math.max(Iob, 0.0))+
				" basal_ceiling: "+String.format("%.2f", basal_ceiling)+
				" diff_rate: "+String.format("%.2f", 60.0*return_value), 
				System.currentTimeMillis()/1000, Log.LOG_ACTION_DEBUG);
		
		Debug.i(TAG, FUNC_TAG, "Output: "+return_value+" Output(U/Hr): "+60*return_value);
		
		return 60.0*return_value;
	}
	
	private boolean fetchStateEstimateData() {
		final String FUNC_TAG = "fetchStateEstimateData";
		
		Iob = Gest = Gbrakes = 0.0;
		GestTime = GbrakesTime = 0;
		
		// Fetch most recent synchronous row from State Estimate data records
		Cursor c = context.getContentResolver().query(Biometrics.STATE_ESTIMATE_URI, new String[]{"asynchronous", "time", "IOB", "Gpred", "Gbrakes"}, "asynchronous = 0", null, "time DESC LIMIT 1");
		
		if (c.moveToFirst()) {
			Iob = c.getDouble(c.getColumnIndex("IOB"));
			Gest = c.getDouble(c.getColumnIndex("Gpred"));			//AKA Gpred
			Gbrakes = c.getDouble(c.getColumnIndex("Gbrakes"));		//AKA Gpred_30m
			GestTime = GbrakesTime = c.getLong(c.getColumnIndex("time"));
			c.close();
			
			Debug.i(TAG, FUNC_TAG, "IOB: "+Iob+" Gest: "+Gest+" Gbrakes: "+Gbrakes);
			return true;
		}
		else {
			Debug.w(TAG, FUNC_TAG, "State estimate table empty!");
		}
		c.close();
		
		return false;
	}
	
	private double glucoseTarget(long time) {
		final String FUNC_TAG = "glucoseTarget";
		
		// Get the offset in hours into the current day in the current time zone (based on cell phone time zone setting)
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(time*1000)/1000;
		int timeNowMins = (int)((time+UTC_offset_secs)/60)%1440;
		double ToD_hours = (double)timeNowMins/60.0;
		double x;
		
		if (ToD_hours<N_end)
			x = (ToD_hours+24-N_start)/N_length;
		else if (ToD_hours>N_start)
			x = (ToD_hours-N_start)/N_length;
		else if ((ToD_hours>=N_end)&&(ToD_hours<=N_end+1))
			x = 1-ToD_hours+N_end;
		else
			x = 0;
		
		double x1 = Math.pow((x/Taux),N_glucoseTarget)/(1.0+Math.pow((x/Taux),N_glucoseTarget));
		double glucose_target = Gmax-Gspred*x1;
		
		Log.log_action(context, TAG, "ToD_hours="+String.format("%.2f", ToD_hours)+", glucose_target="+String.format("%.2f", glucose_target), System.currentTimeMillis()/1000, Log.LOG_ACTION_DEBUG);
		
		Debug.i(TAG, FUNC_TAG, "ToD_hours: "+ToD_hours+" x: "+x+" x1: "+x1);
		Debug.i(TAG, FUNC_TAG, "Time: "+time+", ToD_hours="+ToD_hours+", glucose_target="+glucose_target);
		
		return glucose_target;
	}
	
	private double saturate_CF(double CF) {
		final String FUNC_TAG = "saturate_CF";
		
		double TDI = get_adaptive_TDI();
		
		Debug.i(TAG, FUNC_TAG, "TDI: "+TDI);
		if (CF > (1800/TDI))
			return 1800/TDI;
		else if (CF < (1500/TDI))
			return 1500/TDI;
		else return CF;	
	}
	
	private double get_adaptive_TDI() {
		final String FUNC_TAG = "get_adaptive_TDI";
		
		Settings st = IOMain.db.getLastTDIestBrmDB(subject.sessionID);
		
		Debug.i(TAG, FUNC_TAG, "Time: "+getCurrentTimeSeconds()+" BrmDB-Time: "+st.time+" BrmDB-TDI: "+st.TDIest);
		if (st.TDIest == 0)
			return subject.TDI;
		else
			return st.TDIest;
	}
	
	private double INS_target_saturate(double INS_target, double CF) {
		if (INS_target < 90.0/CF)
			return INS_target;
		else
			return (90.0/CF);
	}
	
	private double basal_saturate (double last_Gpred, double basal) {
		final String FUNC_TAG = "basal_saturate";
		
		Settings st = IOMain.db.getLastBrmDB();
		
		Debug.i(TAG, FUNC_TAG, "time= "+getCurrentTimeSeconds());
		Debug.i(TAG, FUNC_TAG, "BrmDB:time= "+st.time);
		Debug.i(TAG, FUNC_TAG, "BrmDB:starttime= "+st.starttime);
		Debug.i(TAG, FUNC_TAG, "BrmDB:duration= "+st.duration);
		Debug.i(TAG, FUNC_TAG, "BrmDB:BGhigh= "+st.d1);
		Debug.i(TAG, FUNC_TAG, "BrmDB:BGlow= "+st.d2);
		Debug.i(TAG, FUNC_TAG, "BrmDB:d3= "+st.d3);
		
		double BGhigh = 0;
		double BGlow = 0;
		int mode = 1;   	// Set mode: 0 fixed (default), 1 changeable
		switch (mode) {
			case 0:
				BGhigh = 3;
				BGlow = 2;
				break;
			case 1:
				BGhigh = st.d1;
				BGlow = st.d2;
				break;
		}
		Debug.i(TAG, FUNC_TAG, "BGhigh: "+BGhigh+" BGlow: "+BGlow);
		
		double saturated_basal = BGlow * basal;
		if (last_Gpred < 180)
			 saturated_basal =  BGlow * basal;
		else if (last_Gpred >= 180)
			saturated_basal = BGhigh * basal;
		
		return saturated_basal;
	}
	
	private long getCurrentTimeSeconds() {
		return (System.currentTimeMillis()/1000);			// Seconds since 1/1/1970 in UTC
	}
	
	private void storeUserTable1Data(long time,
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
			final String FUNC_TAG = "storeUserTable1Data";
		
			ContentValues values = new ContentValues();
			values.put("time", time);
			values.put("l0", MEAL_IOB_CONTROL);
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
			} catch (Exception e) {
				Debug.e(TAG, FUNC_TAG, "Error: "+e.getMessage());
			}		
	}
}
