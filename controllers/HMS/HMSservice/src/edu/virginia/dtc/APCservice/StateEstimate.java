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

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Array;

import edu.virginia.dtc.Tvector.Tvector;



public class StateEstimate {
	private boolean BUGS_FIXED = true;						// Simulate system operation before vaious bugs fixed
	private static final boolean DEBUG_MODE = true;
	private static final double FDA_MANDATED_MAXIMUM_CORRECTION_BOLUS = 3.0;
	private Context context;
	private Subject subject;
	public final String TAG = "HMSservice";
	
	// Parameter definitions
	double[][] DER_init = {{0.04}, {0.02}, {0.0}, {-0.02}, {-0.04}};
	Matrix MatDER = new Matrix(DER_init);
	double[][] MatINS_target_init = {{0.0}, {0.0}, {0.0}, {0.0}, {0.0}};
	Matrix MatINS_target = new Matrix(MatINS_target_init);
	public static final int INS_targetslope_window_size_seconds = 27*60;		// Accommodate 5 data points: 25 minute window with 2 minutes of margin
	public final static double T2tgt = 30.0;
	public final static double PredH = 60.0;
	public boolean valid = false;

	public StateData state_data; 		// This class encapsulates the state variables that are being estimated and that must persist.

	public StateEstimate(
								Tvector Tvec_cgm1, 				// cgm1 history with time stamps in minutes since 1/1/1970
								Tvector Tvec_spent, 
								Tvector Tvec_IOB,
								Tvector Tvec_Gest,
								Params control_param, 
								long time,									// Seconds since 1/1/1970 
								int cycle_duration_mins,
								long corrFlagTime,
								long hypoFlagTime,
								long calFlagTime,
								long mealFlagTime,
								Context calling_context,
								double brakes_coeff) {
		context = calling_context;
		subject = new Subject(time, calling_context);
		log_action(TAG, "Create StateEstimate");
		// Initialize the class that holds our state data
		state_data = new StateData(time);
		// Generate the first state estimate
		if (state_estimate(Tvec_cgm1, Tvec_spent, Tvec_IOB, Tvec_Gest, control_param, time, cycle_duration_mins, state_data, corrFlagTime, hypoFlagTime, calFlagTime, mealFlagTime, brakes_coeff))
			valid = true;
		else
			valid = false;
	}
	
	public boolean state_estimate(
			Tvector Tvec_cgm1, 							// cgm1 history with time stamps in minutes since 1/1/1970
			Tvector Tvec_spent, 
			Tvector Tvec_IOB,
			Tvector Tvec_Gest,
			Params control_param, 
			long time,									// Seconds since 1/1/1970 
			int cycle_duration_mins,
			StateData state_data,
			long corrFlagTime,
			long hypoFlagTime,
			long calFlagTime,
			long mealFlagTime,
			double brakes_coeff) {
		boolean return_value = false;
		// 1. Update the subject data by reading latest profile values from the database
		subject.read(time, context);
		if (subject.valid == false)		// Protect against state estimates with uninitialized data.
			return false;
		debug_message(TAG, "CF="+subject.CF+", CR="+subject.CR+", basal="+subject.basal);
		// 2. Compute the differential_basal_rate
		state_data.differential_basal_rate = differentialBasalRate(time, subject.basal, subject.CF, Tvec_Gest, Tvec_IOB);

		
		
/*		
		// 2.  Determine whether the CGM data is recent enough and retrieve the last value
		state_data.enough_data = CGM_filter(Tvec_cgm1, time, state_data);		
		// 3.  Build the insulin history
		insulin_history_builder(cycle_duration_mins, Tvec_rate_hist_secs, Tvec_bolus_hist_secs, Tvec_basal_bolus_hist_seconds, Tvec_meal_bolus_hist_seconds, 	
				Tvec_corr_bolus_hist_seconds, control_param.basal, Tvec_spent, control_param, time, state_data);
//		for (int ii=0; ii<control_param.iob_param.hist_length/cycle_duration_mins; ii++) {
//			debug_message("discrete_time", "discrete_time["+ii+"]="+state_data.discrete_time[ii]);
//			debug_message(HMSservice.TAG,"Tvec_ins_hist_corr["+ii+"]="+state_data.Tvec_ins_hist_corr.get_value(ii));
//		}
		// 4.  Compute insulin on board (IOB)
		debug_message("IOBdebug", "HMS > IOBlast="+state_data.IOBlast+", IOBlast2="+state_data.IOBlast2+", Tvec_ins_hist_corr(last)="+state_data.Tvec_ins_hist_corr.get_last_value());
		state_data.Tvec_ins_hist_corr.dump("IOBdebug", "HMS > Tvec_ins_hist_corr", 8);
		state_data.IOB = 0.0;
		debug_message("IOBdebug", "HMS > IOB="+state_data.IOB+", IOBlast="+state_data.IOBlast+", IOBlast2="+state_data.IOBlast2);
		debug_message("IOBdebug", " ");
		// 5.  Use the Kalman filter to update the internal state estimate
		state_estimation(control_param.basal, state_data.Tvec_ins_hist, cycle_duration_mins, control_param.filter, Tvec_cgm1, control_param.model,
				time, control_param.bolus_param, control_param.iob_param.hist_length/cycle_duration_mins, state_data);
		debug_message(TAG, "Xi0={"+state_data.Xi0[0]+", "+state_data.Xi0[1]+", "+state_data.Xi0[2]+", "+state_data.Xi0[3]+", "+state_data.Xi0[4]+", "+state_data.Xi0[5]+", "+state_data.Xi0[6]+", "+state_data.Xi0[7]);
		debug_message(TAG, "Gpred_bolus="+state_data.Gpred_bolus);
		debug_message(TAG, "Gpred="+state_data.Gpred);
		// 6.  Compute the appropriate bolus given the current subject state
		state_data.bolus_amount = cbam_compute_bolus(control_param.bolus_param, corrFlagTime, hypoFlagTime, calFlagTime, mealFlagTime, state_data.Gpred_bolus, state_data.CF,
				state_data.IOB, time, state_data.Gpred);
		debug_message(TAG, "recommended bolus="+state_data.bolus_amount);
*/		
		return true;
	}
	
	public double glucoseTarget(long time) {
		// Get the offset in hours into the current day in the current time zone (based on cell phone time zone setting)
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(time*1000)/1000;
		int timeNowMins = (int)((time+UTC_offset_secs)/60)%1440;
		double ToD_hours = (double)timeNowMins/60.0;
//		debug_message(TAG, "subject_parameters > time="+time+", time/60="+time/60+", timeNowMins="+timeNowMins+", ToD_hours="+ToD_hours);
		double x;
		if (ToD_hours<6.0) {
			x = (1.0+ToD_hours)/7.0;
		}
		else if (ToD_hours>=6.0 && ToD_hours<7.0) {
			x = 7.0-ToD_hours;
		}
		else if (ToD_hours>=7.0 && ToD_hours<23.0) {
			x = 0.0;
		}
		else {
			x = (ToD_hours-23.0)/7.0;
		}
		return 160.0-45.0*Math.pow((x/0.5),3.0)/(1.0+Math.pow((x/0.5),3.0));
	}
	
	// Return differential basal rate in U/hour
	public double differentialBasalRate(long time, double basal, double CF, Tvector Tvec_Gest, Tvector Tvec_IOB) {
		double return_value = 0.0;
	   	List<Integer> indices;
	   	long t;
	   	double v;
	   	int ii;
	   	Tvector Tvec_glucose_target = new Tvector(5);
	   	Tvector Tvec_insulin_target = new Tvector(5);
	   	double INS_target, INS_target_sat, INS_target_predicted;
	   	double INS_target_slope, INS_target_slope_sat;
		if ((indices = Tvec_Gest.find(">", time-INS_targetslope_window_size_seconds, "<=", time)) != null) {
			if (indices.size() == 5) {
				// Initialize Tvec_insulin_target
				// - We have the 5 most recent Gest values, calculate corresponding INS_target values
				try {
					Iterator<Integer> iterator = indices.iterator();
					int jj=0;
					while (iterator.hasNext()) {
						ii = iterator.next();
						t = Tvec_Gest.get_time(ii);
						v = (Tvec_Gest.get_value(ii) - glucoseTarget(t))/CF;
						Tvec_insulin_target.put(t, v);
						MatINS_target.set(jj++, 0, v);
					}
					Tvec_insulin_target.dump(TAG, "Tvec_insulin_target");
					// Calculate INS_target_slope
					INS_target_slope = MatDER.transpose().times(MatINS_target).trace();
					debug_message(TAG, "INS_target_slope="+INS_target_slope);
					// Calculate the predicted value of INS_target
					INS_target_sat = INS_target_saturate(Tvec_insulin_target.get_last_value(), CF);
					INS_target_slope_sat = INS_target_slope_saturate(INS_target_slope);
					INS_target_predicted = INS_target_sat + PredH*INS_target_slope_sat;
					// Calculate a Rate
					double Rate = (INS_target_predicted - Tvec_IOB.get_last_value())/T2tgt;
					// Calculate the differential basal rate
					if (Rate > 2.0*basal/60.0) {
						return_value = 2.0*basal/60.0;
					}
					else if (Rate > 0.0 && Rate <= 2.0*basal/60.0) {
						return_value = Rate;
					}
					else {
						return_value = 0.0;
					}
					debug_message(TAG, "differentialBasalRate="+return_value);
				}
				catch (IllegalArgumentException ex) {
					debug_message(TAG, "differentialBasalRate > Matrix inner dimensions must agree.");
					return 0.0;
				}
				catch (ArrayIndexOutOfBoundsException ex) {
					debug_message(TAG, "differentialBasalRate > Array index out of bounds.");
					return 0.0;
				}
			} else {
				return 0.0;
			}
		} else {
			return 0.0;
		}
		return 60.0*return_value;
	}
	
	public double INS_target_saturate(double INS_target, double CF) {
		if (INS_target < 90.0/CF)
			return INS_target;
		else
			return (90.0/CF);
	}
	
	public double INS_target_slope_saturate(double INS_target_slope) {
		if (INS_target_slope > 1.0/PredH)
			return 1.0/PredH;
		else if (INS_target_slope < -10.0/PredH)
			return -10.0/PredH;
		else
			return INS_target_slope;
	}
		
 	public void log_action(String service, String action) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
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
	
}
