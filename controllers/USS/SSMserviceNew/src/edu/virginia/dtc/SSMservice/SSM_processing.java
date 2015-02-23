//*********************************************************************************************************************
//  Copyright 2011-2012 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.SSMservice;

import java.util.List;
import java.util.TimeZone;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.Safety;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.Tvector.Tvector;

import Jama.*;
import android.content.Context;
import android.database.Cursor;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class SSM_processing {
	private final String TAG = "SSMservice";
	private Context calling_context;
	private boolean bolus_interceptor_enabled;
	
	// CGM sensor constants
	private final static int PERMISSIBLE_CGM_GAP_MINUTES = 25;
	
	// Parameters for IOB based hyperglycemia light
	private final static double PredH = 60.0;
	
	// Parameters for meal-informed power brakes
	private static final double Gop = 90.0;
	private long window_length = 21600; //secs
	private long window_interval = 300;  //sec
	
	public SSM_state_data state_data; 										// This class encapsulates the CBAM state variables that are being estimated and that must persist.
	
	private double Dma, Uma, Dma1, Dma2, CGMlatest, beta0, beta1;
	private int HyperRL;
	private Matrix Xr,Yr;
	
	public SSM_processing(
								Subject subject_data,						// Data about the subject
								long time,									// Seconds since 1/1/1970 
								Context callingContext) {
		
		// Initialize the class that holds our state data
		state_data = new SSM_state_data(time);
		// Save the Context
		calling_context = callingContext;
		// Generate the first state estimate
		debug_message("SSM_processing", "creating new state estimate");
	}
	
	//
	//  Start running the SSM.
	//				i)  	Filter the CGM data, construct the , insulin history, calculate IOB, update the system state and classify the bolus.
	//				ii) 	Bolus interception or other required user confirmation results in early exit with retValue==true and state_data.Processing_State
	//							set appropriately.
	//				iii)	Update lights, adjusting the bolus if necessary, handle saturation and bolus remnants and finish with retValue==false and
	//							state_data.Processing_State==SAFETY_SERVICE_STATE_NORMAL
	//
	public boolean start_SSM_processing(
								Subject subject_data,						// Data about the subject
								Tvector Tvec_cgm_mins, 						// cgm1 history with time stamps in minutes since 1/1/1970
								Tvector Tvec_rate_hist_seconds, 			// insulin rate history with time stamps in seconds since 1/1/1970
								Tvector Tvec_bolus_hist_seconds,			// insulin bolus history with time stamps in seconds since 1/1/1970
								Tvector Tvec_basal_bolus_hist_seconds,		// insulin basal_bolus history with time stamps in seconds since 1/1/1970
								Tvector Tvec_meal_bolus_hist_seconds,		// insulin meal_bolus history with time stamps in seconds since 1/1/1970
								Tvector Tvec_corr_bolus_hist_seconds,		// insulin corr_bolus history with time stamps in seconds since 1/1/1970
								Tvector Tvec_GPRED,							// Vector of recent Blood Glucose estimates
								Tvector Tvec_IOB,							// Vector of recent IOB estimates
								Tvector Tvec_Rate,							// Vector of recent Rate values calculated by hyper_light
								double BOLUS,
								double differential_basal_rate,
								SSM_param ssm_param, 
								long time,										// Seconds since 1/1/1970 
								int cycle_duration_mins,
								SSM_state_data state_data,
								long calFlagTime,
								long hypoFlagTime,
								boolean exercise,
								boolean asynchronous,
								boolean interceptor_enabled,
								int DIAS_STATE) {
 		final String FUNC_TAG = "start_SSM_processing";
 		bolus_interceptor_enabled = interceptor_enabled;
		boolean retValue = false;
 		try {
 			//  i)		Set the (externally generated) IOB constraint
// 			state_data.Umax_IOB = UmaxIOB;
 			
 			//	ii)  	Construct the filter CGM data, insulin history, calculate IOB, update the system state and classify the bolus.
 			calculate_state_estimate(subject_data, Tvec_cgm_mins, Tvec_rate_hist_seconds, Tvec_bolus_hist_seconds, Tvec_basal_bolus_hist_seconds, 
 					Tvec_meal_bolus_hist_seconds, 	Tvec_corr_bolus_hist_seconds, BOLUS, differential_basal_rate, 
 					ssm_param, time, cycle_duration_mins, state_data, calFlagTime, hypoFlagTime, exercise, asynchronous, DIAS_STATE);
 			
 			//	iii)	Bolus interception and user confirmation
 			if (state_data.enough_data == false && (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY)) {
 				if (not_enough_data(time, DIAS_STATE)) {
 					state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_NOT_ENOUGH_DATA;
 					retValue = true;
 				}
 				else {																	// Not enough data but no need for user interaction - normal return
 					state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_NORMAL;
 					retValue = false;
 				}
 			}
 			else {
 				// Note: correction_filter will run if a meal bolus is not detected
 				if (state_data.isMealBolus == false) {					// Not a meal bolus but we might need credit request confirmation
 					if (correction_filter(subject_data, ssm_param.flag_param, cycle_duration_mins, ssm_param.brakes_param, ssm_param.hypo_alarm, ssm_param.iob_param, time, calFlagTime, exercise, asynchronous, DIAS_STATE)) {						
 						state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_CREDIT_REQUEST;
 						retValue = true;					
 					}
 					else {																// Not a meal bolus, no credit request confirmation needed
 						state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_NORMAL;
 						retValue = false;					
 					}
 				}
 				else {																	// Meal bolus
 					meal_bolus_detected(subject_data, cycle_duration_mins, ssm_param.flag_param, time, DIAS_STATE);
 					
 					state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_BOLUS_INTERCEPT;
 					retValue = true;					
 				}
 			}
 			// Store current state for additional processing
 			state_data.cycle_duration_mins = cycle_duration_mins;
 			state_data.time = time;
 			state_data.calFlagTime = calFlagTime;
 			
			state_data.Tvec_cgm_mins = new Tvector(Tvec_cgm_mins);
 			state_data.ssm_param = ssm_param;
 			
 			//	iii)	If no user confirmation is required then continue processing
 			if (state_data.Processing_State == Safety.SAFETY_SERVICE_STATE_NORMAL) {
 				debug_message(TAG, "complete_processing - no user confirmation required");
 				Tvec_GPRED.put(time, state_data.Gpred);
 				Tvec_IOB.put(time, state_data.IOB_meal);
 				complete_processing(subject_data, state_data, asynchronous, DIAS_STATE, time, Tvec_GPRED, Tvec_IOB, Tvec_Rate, calFlagTime);
 				retValue = false;
 			}
 			// else continue processing after user confirmation
 			else {
 				debug_message(TAG, "Return to application - user confirmation required");
 				retValue = true;
 			}
 			debug_message("classify", "enough_data="+state_data.enough_data+", isMealBolus"+state_data.isMealBolus+", Processing_State="+state_data.Processing_State+", retValue"+retValue);
 		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}	
		return retValue;
	}
	
	//
	//  Calculates IOB and the system state and determines whether the insulin request should be treated as a meal bolus.
	//
	private void calculate_state_estimate(
								Subject subject_data,				// Current subject data
								Tvector Tvec_cgm_mins, 							// cgm1 history with time stamps in minutes since 1/1/1970
								Tvector Tvec_rate_hist_seconds, 			// insulin rate history with time stamps in seconds since 1/1/1970
								Tvector Tvec_bolus_hist_seconds,			// insulin bolus history with time stamps in seconds since 1/1/1970
								Tvector Tvec_basal_bolus_hist_seconds,		// insulin basal_bolus history with time stamps in seconds since 1/1/1970
								Tvector Tvec_meal_bolus_hist_seconds,		// insulin meal_bolus history with time stamps in seconds since 1/1/1970
								Tvector Tvec_corr_bolus_hist_seconds,		// insulin corr_bolus history with time stamps in seconds since 1/1/1970
								double BOLUS,
								double differential_basal_rate,
								SSM_param ssm_param, 
								long time,									// Seconds since 1/1/1970 
								int cycle_duration_mins,
								SSM_state_data state_data,
								long calFlagTime,
								long hypoFlagTime,
								boolean exercise,
								boolean asynchronous,
								int DIAS_STATE) {
 		final String FUNC_TAG = "calculate_state_estimate";
 		try {
 			// 2.  Compute Usugg and assign bolus amount
 			BOLUS_assign(subject_data, state_data, state_data.BOLUS, cycle_duration_mins, differential_basal_rate,time);
 			
 			// 3.  Determine whether the CGM data is recent enough and retrieve the last value
			state_data.enough_data = CGM_filter(Tvec_cgm_mins, time, state_data);
 			int cgmState = readCgmStatefromSystemTable();
 			boolean cgmAvailable = (cgmState == CGM.CGM_NORMAL || cgmState == CGM.CGM_CALIBRATION_NEEDED || cgmState == CGM.CGM_DUAL_CALIBRATION_NEEDED);
 			
 			// 4.  Get the values of CF, CR, basal  and b that valid for this subject at this time from the daily profile.
 			subject_parameters(subject_data, state_data, state_data.enough_data, Tvec_cgm_mins, time, cycle_duration_mins, differential_basal_rate, DIAS_STATE);
 			// 6.  Use the Kalman filter to update the internal state estimate
 			double exercise_level;
 			if (exercise) {
 				exercise_level = 1.0;
 			}
 			else {
 				exercise_level = 0.0;
 			}
 			
 			// 7.  Use the Kalman filter to update the internal state estimate
 			if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY || (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP && cgmAvailable)) {
 	 			debug_message(TAG,"meal_informed_state_estimation_new_start");

 	 			meal_informed_state_estimation_new(state_data,
 	 					                           time,
 	 					                           exercise_level,
 	 					                           asynchronous,
 	 					                           subject_data,
 	 					                           ssm_param,
 	 					                           calling_context);
 	 			debug_message(TAG,"meal_informed_state_estimation_new_end");
 			}
 			// 8.  Determine whether or not to classify the insulin request as a meal bolus
 			//      If we are NOT in DIAS_STATE_CLOSED_LOOP then it is not a meal bolus
 			if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP) {
 				state_data.isMealBolus = classify(subject_data, state_data, ssm_param.flag_param, cycle_duration_mins, time);
 			}
 			else if (DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY|| (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP && cgmAvailable)) {		// No meal bolus in Safety Only or Open Loop but run classify(...) to update state
 				classify(subject_data, state_data, ssm_param.flag_param, cycle_duration_mins, time);
 				state_data.isMealBolus = false;
 			}
 			else {
 				state_data.isMealBolus = false;
 			}
 			Debug.i(TAG, FUNC_TAG, "isMealBolus="+state_data.isMealBolus);
 		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}	
	}
	
	
	//
	//  Updates the lights (adjusting the insulin dose if necessary).  Handles saturation (>6 units) and remainder insulin from previous doses.
	//
	public void complete_processing(Subject subject_data, SSM_state_data state_data, boolean asynchronous, int DIAS_STATE, long time, Tvector Tvec_GPRED, Tvector Tvec_IOB, Tvector Tvec_Rate, long calFlagTime) {
 		final String FUNC_TAG = "complete_processing";
 		try {
 			int cgmState = readCgmStatefromSystemTable();
 			boolean cgmAvailable = (cgmState == CGM.CGM_NORMAL || cgmState == CGM.CGM_CALIBRATION_NEEDED || cgmState == CGM.CGM_DUAL_CALIBRATION_NEEDED);
 			Debug.i(TAG, FUNC_TAG, "cgmState="+cgmState+", cgmAvailable="+cgmAvailable);
 			// Lights are only updated in DIAS_STATE_CLOSED_LOOP, DIAS_STATE_SAFETY_ONLY or DIAS_STATE_OPEN_LOOP if there is CGM data
 			if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY || (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP && cgmAvailable)) {
 				// 9.  Update the hypo light
 				state_data.stoplight = hypo_light(subject_data, state_data, state_data.cycle_duration_mins, state_data.ssm_param.hypo_alarm, state_data.time, state_data.calFlagTime, asynchronous, DIAS_STATE);
 				// 10. Calculate the hyper light state based upon IOB
 				state_data.stoplight2 = hyper_light(time, subject_data.basal, subject_data.CF, Tvec_GPRED, Tvec_IOB, Tvec_Rate, calFlagTime);
 				// 10a.  If hypo light is yellow or red then the hyper light must be green
 				if (state_data.stoplight==Safety.YELLOW_LIGHT || state_data.stoplight==Safety.RED_LIGHT) {
 					state_data.stoplight2=Safety.GREEN_LIGHT;
 				}
 			}
 			else {
 				// Traffic lights invalid
 				state_data.stoplight=Safety.UNKNOWN_LIGHT;
 				state_data.stoplight2=Safety.UNKNOWN_LIGHT;
 			}
 			Debug.i(TAG, FUNC_TAG, "stoplight="+state_data.stoplight+", stoplight2="+state_data.stoplight2);
		}
 		catch (Exception e) {
 			Bundle b = new Bundle();
 			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
 			Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}	
	}


	//
	//	Handles the case of not enough data
	//
	private boolean not_enough_data(long time, int DIAS_STATE) {
 		final String FUNC_TAG = "not_enough_data";
 		boolean retValue = false;
 		try {
 			if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY ) {
 	 			state_data.SSM_amount = state_data.b;
 	 			if (state_data.BOLUS > 0) {
 	 				debug_message(TAG, "HYPORED > not_enough_data > state_data. > BOLUS="+state_data.BOLUS);
 	 				state_data.stoplight = Safety.RED_LIGHT;
 	 				state_data.state_out = 3;
 	 				state_data.Umax = Math.max(0, state_data.BOLUS);
 	 				state_data.CHOmin = 0;
 	 				state_data.advised_bolus = (0.01*Math.round(100*(state_data.BOLUS)));
 	 				state_data.risky = 0;
 	 				state_data.Abrakes = state_data.SSM_amount;
 	 				retValue = true;
 	 			}
 	 			else {
 	 				state_data.risky = 0;
 	 				state_data.Abrakes = state_data.SSM_amount;
 	 				retValue = false;
 	 			}
 			}
 			else if (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP) {
 				state_data.stoplight=Safety.UNKNOWN_LIGHT;
 				state_data.stoplight2=Safety.UNKNOWN_LIGHT;
 			}
 			else {
 				state_data.stoplight=Safety.UNKNOWN_LIGHT;
 				state_data.stoplight2=Safety.UNKNOWN_LIGHT;
 			}
 		}
 		catch (Exception e) {
 			Bundle b = new Bundle();
 			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
 			Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}	
 		return retValue;
	}
	
	// Returns the value of the hyper-light: 0==green, 1==yellow, 2==red
	private int hyper_light(long time, double basal, double CF, Tvector Tvec_Gest, Tvector Tvec_IOB, Tvector Tvec_Rate, long calFlagTime) {
 		HyperRL = Safety.GREEN_LIGHT;

		double Thres = -0.015;			
		
		if (Dma < Thres && CGMlatest >= 200 && beta1 > -0.2) {  //Dma from KF
		  HyperRL = Safety.RED_LIGHT;	
		}else{
			if (Uma>0){  //Uma from KF
				HyperRL = Safety.YELLOW_LIGHT;
			}else{
			    HyperRL = Safety.GREEN_LIGHT;	
			}
		}
		
		return HyperRL;
	}		
	
	//	Handle the case where the bolus is not a meal bolus but there may be need for a credit request
	private boolean correction_filter(
								Subject subject_data,
								SSM_flag_param flag_param,
								int cycle_duration_mins,
								SSM_brakes_param brakes_param,
								SSM_hypo_alarm hypo_alarm,
								IOB_param iob_param,
								long time,
								long calFlagTime,
								boolean exercise,
								boolean asynchronous,
								int DIAS_STATE) {
 		final String FUNC_TAG = "correction_filter";
		boolean retValue = false;
		try {
	        // construct IOB corrected sensor
	        double CGM_corr=Math.max(20.0,  brakes_param.alpha*state_data.CGM+(1-brakes_param.alpha)*(state_data.CGM-Math.max(0.0, state_data.IOB_meal)*subject_data.CF));
	        state_data.CGM_corr = CGM_corr;
	        // Log some values for debug
	        debug_message(TAG, "MI_SE > CGM_corr="+CGM_corr+", state_data.Gbrakes="+state_data.Gbrakes+", brakes_param.risk.thres_ex="+brakes_param.risk.thres_ex);
	        //compute brake action using CGM_corr or KF prediction (minimum)
	        
            // exercise state is read from exercise_state table directly; not using exercise from the arguments
	        if (isCurrentlyExercising()) {                    //if (exercise)
	        	state_data.risky = state_data.RiskEX;
	        }
	        else {
	        	state_data.risky = state_data.Risk;    
	        }          
	        
			Debug.i(TAG,FUNC_TAG,"(boolean)exercise="+isCurrentlyExercising());
			Debug.i(TAG,FUNC_TAG,"Risk="+state_data.Risk);
			Debug.i(TAG,FUNC_TAG,"RiskEX="+state_data.RiskEX);
			Debug.i(TAG,FUNC_TAG,"risky="+state_data.risky);

	        // Only add in basal rate when we are not asynchronous - i.e. when we are acting at a regular timer tick
	        double add_basal;
	        if (asynchronous)
	        	add_basal = 0;
	        else
	        	add_basal = 1;
	        	
	        // The brakes are only applied in Closed Loop and Safety Only modes
	        if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) {
	        	
	        	if (brakes_param.k < 0.0) {
	        		Bundle b = new Bundle();
	        		b.putString("description", "Incorrect value of CF or TDI in brakes_param.k calculation");
	        		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
	        		
	        		return false; //TODO: Is that right???
	        	}
	        	
	            if (state_data.risky > 0) {
	    				state_data.Abrakes=Math.min(cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins),(subject_data.basal*add_basal/(60/cycle_duration_mins))/(1+brakes_param.k*state_data.risky));
	    	            state_data.basal_added = state_data.Abrakes;
	    	            Debug.i(TAG, FUNC_TAG, "basal_added="+state_data.basal_added+", Abrakes="+state_data.Abrakes);
	            
	            }
	            else {
	    				state_data.Abrakes=(cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins));
	    	            state_data.basal_added = cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins);
	    	            Debug.i(TAG, FUNC_TAG, "basal_added="+state_data.basal_added+", Usugg="+state_data.Usugg+", basal="+subject_data.basal+", Abrakes="+state_data.Abrakes);
	            }
	            state_data.brakes_coeff=1/(1+brakes_param.k*state_data.risky);
	            
	        }
	        else {
				state_data.Abrakes=(cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins));
				state_data.basal_added = (cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins));
	            Debug.i(TAG, FUNC_TAG, "basal_added="+state_data.basal_added+", Usugg="+state_data.Usugg+", basal="+subject_data.basal+", add_basal="+add_basal);
		        state_data.brakes_coeff=1;
		        
	        }
	        if (asynchronous) {
	        	state_data.basal_added = 0.0;
	        }
	        else {
	        	double alpha_corr = 0.0;	// The proportion of the total requested insulin that is being approved for delivery
	        	double alpha_corr_denominator = ((double)cycle_duration_mins*state_data.Usugg + subject_data.basal*add_basal/(60.0/(double)cycle_duration_mins));
	        	if (alpha_corr_denominator > Pump.EPSILON || alpha_corr_denominator < -Pump.EPSILON) {
	        		alpha_corr = state_data.basal_added/alpha_corr_denominator;
	        	}
	        	state_data.basal_added = Math.max(0.0, state_data.basal_added - alpha_corr*state_data.BOLUS_original);
	        }
        	Debug.i(TAG, FUNC_TAG, "asynchronous="+asynchronous+", basal_added="+state_data.basal_added);
        	
	        // compute SSM correction
	        state_data.SSM_amount = state_data.Abrakes; 
		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
		return retValue;
	}
	
	private boolean meal_bolus_detected(
								Subject subject_data,
								int cycle_duration_mins,
								SSM_flag_param flag_param,
								long time,
								int DIAS_STATE) {
 		final String FUNC_TAG = "meal_bolus_detected";
		boolean retValue = true;
		try {
 			if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP) {
 				// If the hypo light is already red then no meal bolus is allowed
 				if (state_data.stoplight == Safety.RED_LIGHT) {
 					state_data.Umax = 0;
 			    	state_data.advised_bolus = 0;
 				}
 				else {
 					debug_message(TAG, "HYPORED > meal bolus detected");
 					state_data.stoplight = Safety.RED_LIGHT;
 					state_data.state_out = 3;
 					state_data.Umax = Math.max(0.0, 0.01*Math.round(100.0*((state_data.CGM-flag_param.low_target)/subject_data.CF-state_data.IOB_meal)));
 					state_data.CHOmin = (int)(Math.max(0.0, ((state_data.Gpred-flag_param.low_target)/subject_data.CF)-state_data.IOB_meal)*subject_data.CR);
 			    	state_data.advised_bolus = 0.01*Math.round(100.0*(cycle_duration_mins*state_data.Usugg));
 				}
 			}
 			else {
				state_data.stoplight = Safety.UNKNOWN_LIGHT;
 			}
		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
		return retValue;
	}
	
	private void BOLUS_assign(
								Subject subject_data,
								SSM_state_data state_data,
								double BOLUS,								// U
								int cycle_duration_mins,
								double differential_basal_rate,
								long time) {
 		final String FUNC_TAG = "BOLUS_assign";
 		try {
 			state_data.RATE = 0.0;
 			state_data.Usugg = state_data.BOLUS/cycle_duration_mins + differential_basal_rate/60.0;		// Usugg has units U / min
 			debug_message(TAG,"APC_output>>>>>>>>>>>>>>>>>>");
 			debug_message(TAG,"state_data.Usugg: "+state_data.Usugg);
 			storeUserTableData(Biometrics.USER_TABLE_2_URI, time, differential_basal_rate/60.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
 			debug_message(TAG,"APC_output<<<<<<<<<<<<<<<<<<");

 		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
	}
	
	private void subject_parameters(
								Subject subject_data,
								SSM_state_data state_data, 
								boolean enough_data, 
								Tvector Tvec_cgm_mins, 
								long time, 
								int cycle_duration_mins,
								double differential_basal_rate,
								int DIAS_STATE) {
 		final String FUNC_TAG = "subject_parameters";
 		try {
 			// Calculate b
 			double last_cgm_value = 0.0;
 			last_cgm_value = Tvec_cgm_mins.get_last_value();
 		    if (enough_data || DIAS_STATE == State.DIAS_STATE_OPEN_LOOP) {
 		    	state_data.b = subject_data.basal/(60.0/cycle_duration_mins);		// b has units of U / cycle
 		    }
 		    else {
		    	if (last_cgm_value < 100.0) {
 		    		state_data.b = 0.0;
 		    	}
		    	else {
 		        	state_data.b = subject_data.basal/(60.0/cycle_duration_mins);
		    	}
 		    }
 			debug_message(TAG, "subject_parameters > state_data.b="+state_data.b);
 		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
	}
	
	private boolean CGM_filter(Tvector cgm1, long time, SSM_state_data state_data) {
 		final String FUNC_TAG = "CGM_filter";
 		boolean retValue = false;
 		try {
 			if (cgm1.count() > 0) {
 				state_data.CGM = cgm1.get_last_value();
 				debug_message(TAG, "CGM_filter > state_data.CGM = "+state_data.CGM);
 				List<Integer> indices = cgm1.find(">=", (time/60)-PERMISSIBLE_CGM_GAP_MINUTES, "<", -1);			// Find the list of indices within the last 10 minutes
 				debug_message(TAG, "CGM_filter > indices="+indices);
 				if (indices == null) {
 					retValue = false;
 				}
 				else {
 					retValue = true;
 				}
 			}
 			else {
 				state_data.CGM = -1.0;
 				retValue = false;
 			}
 		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
 		return retValue;
	}
	
	private int readCgmStatefromSystemTable() {
		int retValue = CGM.CGM_NOT_ACTIVE;
		Cursor c = calling_context.getContentResolver().query(Biometrics.SYSTEM_URI, null, null, null, null);
    	if(c!=null)
    	{
    		if(c.moveToLast())
    		{
    			retValue = c.getInt(c.getColumnIndex("cgmState"));
    			c.close();
    		}
    		else {
    			c.close();
    		}
    	}
    	return retValue;
	}

	private void meal_informed_state_estimation_new(	SSM_state_data state_data,
			long time,
			double exercise_level,
			boolean asynchronous,
			Subject subject_data,
			SSM_param ssm_param,
			Context callingContext) {
		final String FUNC_TAG = "meal_informed_state_estimation_new";
		
		//construct inputs
		
		Inputs inputs = new Inputs(window_length,window_interval,Gop);
		
		debug_message(TAG,"initilize inputs success");  

		debug_message(TAG,"inputs.steps:"+inputs.steps+"inputs.Gop:"+inputs.Gop);  

	 	inputs.reconstruct(time,callingContext); //construct timestamped inputs
	 	
		debug_message(TAG, "reconstruct inputs success");

		
        //carry out KF estimation and power break calculation
		KF_processing kf_processing = new KF_processing(inputs,subject_data,Gop,ssm_param,exercise_level);
		
		if(!kf_processing.isValid) {
			Bundle b = new Bundle();
			b.putString("description", "Invalid KF Processing initialisation: error in Uma, delta 1 hour or delta 3 hours windows.");
			Event.addEvent(callingContext, Event.EVENT_SYSTEM_ERROR, b.toString(), Event.SET_LOG);
			
			return;
		}

		debug_message(TAG,"KF success");
		
		// parameters for traffic lights
        Dma1=kf_processing.delta_1h_window;
        Dma2=kf_processing.delta_3h_window;
        Uma=kf_processing.Uma;
		
		Dma=calculate_deltaweighted(inputs,Dma1,Dma2);
		
		debug_message(TAG,"delta_1h_window:"+Dma1+"\n"+ "delta_3h_window:"+Dma2);
		debug_message(TAG,"deltaweighted: "+Dma);
		debug_message(TAG,"APCweighted: "+Uma);
		
		int Nr = 7;
		int Nt = inputs.timestamp.length;
		double[] predictor = new double[Nr];
		double[] responsor = new double[Nr];
		for (int i=0;i<Nr;i++) {
			predictor[i]=(double) inputs.timestamp[Nt-(Nr-i)]/60; // mins
			if (inputs.CGM[Nt-(Nr-i)]<20){
				responsor[i] = 20;
			}else {
				responsor[i] = inputs.CGM[Nt-(Nr-i)];
			}
		}
		debug_message(TAG,"regression starts--> ");

		double[] regression_parameters = simple_regression(predictor,responsor);
		beta0 = regression_parameters[0];
		beta1 = regression_parameters[1];
		if (beta1<-2) {beta1=-2;}
		if (beta1>2) {beta1=2;}
		CGMlatest = inputs.CGM[Nt-1];
		debug_message(TAG,"regression b0 = "+beta0);
		debug_message(TAG,"regression b1 = "+beta1);
		debug_message(TAG,"CGMlatest = "+CGMlatest);

    	state_data.Gpred_light = kf_processing.Gpred_light;
		state_data.Gpred_1h = kf_processing.Gpred_1h;
	  	state_data.Gpred = kf_processing.Gest;
	  	state_data.Gbrakes = kf_processing.Gpred;
	  	state_data.Gest = kf_processing.Gest;
	  	state_data.BrakeAction = kf_processing.BrakeAction;
	  	state_data.Risk = kf_processing.Risk;
	  	state_data.RiskEX = kf_processing.RiskEX;
	  	state_data.risky = kf_processing.risky;
	  	state_data.tXi0 = inputs.currentTimemins;
	  	int ii;
	  	for (ii=0; ii<8; ii++) {
				state_data.Xi0[ii]=kf_processing.PREDstate_init[ii][0];
		}
	  	
		debug_message(TAG,"read state_data: state_data.Gpred:"+state_data.Gpred+" state_data.Gest"+state_data.Gest);
		int jj;
		for (jj=0; jj<8; jj++) {
			Debug.i(TAG, FUNC_TAG, "XI["+jj+"]="+kf_processing.PREDstate_init[jj][0]);
		}
	}
	
	private double calculate_deltaweighted(Inputs inputs, double Dma1,double Dma2){
		double returnvalue=0;
		
		Long mealtime= (long) 0;
		Long hypotime= (long) 0;
		
		if (inputs.Tvec_meal.count()>=1){
			
			int mealN = inputs.Tvec_meal.count();

			int mealindex = mealN-1;
			while (mealindex>=0) {
				if (inputs.Tvec_meal.get(mealindex).value() !=0) {
					break;
				}else {
					mealindex = mealindex-1;
				}	
			}
			
			if (mealindex>=0) {	
			    mealtime = inputs.Tvec_meal.get(mealindex).time();
			}else{
				mealtime = (long) 0;
			}
		
		}
		
		
		if (inputs.Tvec_hypo.count()>=1){
			hypotime = inputs.Tvec_hypo.get_last_time();
		}
		
		double dT =(double)(inputs.currentTimeseconds-Math.max(mealtime,hypotime))/60; //mins
			
		if (dT<0){
			dT=(double)0;
		}
		
		double n=7,tau=180;
		double p=Math.pow(dT/tau, n)/(1+Math.pow(dT/tau, n));
		
		if (p<0) {p=0;}
		if (p>1) {p=1;}
		
		debug_message(TAG,"dT: "+dT);
		debug_message(TAG,"p: "+p);
		
		returnvalue=Dma1*p+(1-p)*Dma2;
		
		return returnvalue;
	}
	
	private double[] simple_regression(double[] x, double[] y) {
		double[] Beta={0,0};
		double[][] param_init = {{0},{0}};
		Matrix param = new Matrix(param_init);
		
		int N = x.length;
		double[][] Xr_init = new double[N][2];
		for (int i=0;i<N;i++){
			Xr_init[i][0] = 1;
			Xr_init[i][1] = x[i];
		}
		
		double[][] Yr_init = new double[N][1];
		for (int i=0;i<N;i++){
			Yr_init[i][0] = y[i];
		}
		
		Xr = new Matrix(Xr_init);
		Yr = new Matrix(Yr_init);
		debug_message(TAG,"Xr[0][0] = "+Xr.get(0,0));
		debug_message(TAG,"Yr[0][0] = "+Yr.get(0,0));

		
		param = (Xr.transpose().times(Xr)).inverse().times(Xr.transpose()).times(Yr);
		Beta[0] = param.get(0,0);
		Beta[1] = param.get(1,0);
		
		return Beta;
	}

	private boolean classify(Subject subject_data, SSM_state_data state_data, SSM_flag_param flag_param, int cycle_duration_mins, long time) 
	{
		final String FUNC_TAG = "classify";
		boolean retValue = false;
		if (bolus_interceptor_enabled) {
			try {
				// Classify the insulin request
				// add the Usugg (differential basal rate) to the interceptor
				double maxU = Math.max(0.0, state_data.Usugg);
				state_data.Gpred_bolus = state_data.Gpred - (state_data.IOB_no_meal+cycle_duration_mins*maxU)*subject_data.CF;
				state_data.CHOpred = (state_data.IOB_no_meal+cycle_duration_mins*maxU-Math.max(0.0, (state_data.Gpred-flag_param.target)/subject_data.CF))*subject_data.CR;
				//
				debug_message("classify", " ");
				debug_message("classify", "subject.basal/60.0="+subject_data.basal/60.0);
				debug_message("classify", "Gpred_bolus="+state_data.Gpred_bolus+", flag_param.g_thres="+flag_param.g_thres);
				debug_message("classify", "CHOpred="+state_data.CHOpred+", flag_param.cho_thres="+flag_param.cho_thres);
				debug_message("classify", "IOB="+state_data.IOB_no_meal+", Gpred="+state_data.Gpred+", flag_param.target="+flag_param.target+", CF="+subject_data.CF+", CR="+subject_data.CR);
				//
				if ((maxU>=subject_data.basal/60.0) && (state_data.Gpred_bolus<flag_param.g_thres) && (state_data.CHOpred>flag_param.cho_thres)) {
					retValue = true;
				}
				else {
					retValue = false;
				}
			}
			catch (Exception e) {
	 			Bundle b = new Bundle();
	 			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
	 			Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
			}	
		}
		return retValue;
	}
	
	private int hypo_light(
								Subject subject_data,
								SSM_state_data state_data,
								int cycle_duration,
								SSM_hypo_alarm hypo_alarm,
								long time,
								long CalFlagTime,
								boolean asynchronous,
								int DIAS_STATE) {
		final String FUNC_TAG = "hypo_light";
		try {
//			// Set hypo stoplight color and SSM state
//			debug_message(TAG_HYPO_LIGHT, "state_data.SSM_amount="+state_data.SSM_amount);
//			debug_message(TAG_HYPO_LIGHT, "state_data.Umax="+state_data.Umax);
//			debug_message(TAG_HYPO_LIGHT, "state_data.Usugg="+state_data.Usugg);
//			debug_message(TAG_HYPO_LIGHT, "subject.basal="+subject_data.basal);
//			debug_message(TAG_HYPO_LIGHT, "state_data.risky="+state_data.risky);
//			debug_message(TAG_HYPO_LIGHT, "state_data.Abrakes="+state_data.Abrakes);
//			debug_message(TAG_HYPO_LIGHT, "state_data.isMealBolus="+state_data.isMealBolus);
	        
			// Yellow light if basal rate is reducedbasal rate
			if (state_data.risky > 0.0) {
				state_data.stoplight=Safety.YELLOW_LIGHT;
			}
			else {
				state_data.stoplight=Safety.GREEN_LIGHT;
			}
			
			// Red light alert if immediate hypo predicted
			if (time/60 >= CalFlagTime/60+hypo_alarm.calibration_window_width) {					// All times in minutes
				if ((state_data.Gpred_light < hypo_alarm.thresG) || (state_data.CGM < hypo_alarm.thresG)) {
					state_data.stoplight=Safety.RED_LIGHT;
					if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) {
						state_data.SSM_amount = 0.0;    // No insulin injected during red lights.
						state_data.basal_added = 0.0;	// No insulin injected during red lights.
						Debug.i(TAG, FUNC_TAG, "red light 1 > basal_added="+state_data.basal_added);
					}
	                log_action(TAG, "hypo_light="+state_data.stoplight+" > outside cal window > CalFlagTime="+CalFlagTime+", width="+hypo_alarm.calibration_window_width, time);
	                
				}
			}
			else {
				if (state_data.CGM < hypo_alarm.thresG) {
					state_data.stoplight=Safety.RED_LIGHT;
					if (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP || DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) {
						state_data.SSM_amount = 0.0;    // No insulin injected during red lights.
						state_data.basal_added = 0.0;	// No insulin injected during red lights.
					}
				}
			}
		}
		catch (Exception e) {
			Bundle b = new Bundle();
			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
			Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}	
		return state_data.stoplight;
	}

	
	private static void debug_message(String tag, String message) {
			Log.i(tag, message);
	}
	
	private void log_action(String service, String action, long time) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("time", time);
        calling_context.sendBroadcast(i);
	}

	private boolean isCurrentlyExercising(){
	    
		   Cursor c = calling_context.getContentResolver().query(Biometrics.EXERCISE_STATE_URI, null, null, null, null);
			    
			    int exercise_state=0;
			    long time=0;
			    
				if (c.moveToLast()) {
				  
					  exercise_state = (int)c.getDouble(c.getColumnIndex("currentlyExercising")); 
					  time = (long)c.getDouble(c.getColumnIndex("time")); 
		 
			    }	
			    c.close();
			    
		   debug_message(TAG, "time="+time+"    exercise_state from table="+exercise_state);


		   if (getCurrentTimeSeconds()-time>120){
			   exercise_state=0;
		   }
		   debug_message(TAG, "currenttime="+getCurrentTimeSeconds()+"    exercise output="+exercise_state);

		   
		   if (exercise_state==1){
			   return true;
		   }else{
			   return false;
		   }
			     
	}
		
	private long getCurrentTimeSeconds() {
		return (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970		
	}
	
	private void storeUserTableData(	Uri user_table,
									long time,
									double d0,
									double d1,
									double d2, 
									double d3,
									double d4,
									double d5,
									double d6,
									double d7,
									double d8,
									double d9,
									double d10,
									double d11,
									double d12,
									double d13,
									double d14,
									double d15) {
		final String FUNC_TAG = "storeUserTable3Data";
		ContentValues values = new ContentValues();
		values.put("time", time);
		values.put("d0", d0);
		values.put("d1", d1);
		values.put("d2", d2);
		values.put("d3", d3);
		values.put("d4", d4);
		values.put("d5", d5);
		values.put("d6", d6);
		values.put("d7", d7);
		values.put("d8", d8);
		values.put("d9", d9);
		values.put("d10", d10);
		values.put("d11", d11);
		values.put("d12", d12);
		values.put("d13", d13);
		values.put("d14", d14);
		values.put("d15", d15);
		try {
			calling_context.getContentResolver().insert(user_table, values);
		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
			Log.e(TAG, e.toString());
		}		
	}

}
