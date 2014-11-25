//*********************************************************************************************************************
//  Copyright 2011-2012 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.SSMservice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Safety;
import edu.virginia.dtc.Tvector.Pair;
import edu.virginia.dtc.Tvector.Tvector;

import Jama.*;
import android.content.Context;
import android.database.Cursor;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;


public class SSM_processing {
	private static final boolean DEBUG_MODE = true;
	public final String TAG = "SSMservice";
	// Identify owner of record in User Table 1
	public static final int USS_SAFETY_SERVICE = 11;
	public final String TAG_CREDITPOOL = "creditpool";
	public final String TAG_HYPO_LIGHT = "HypoLight";
	public final String TAG_HYPER_LIGHT = "HyperLight";
	public static final int TVEC_SIZE = 96;				// 8 hours of samples at 5 mins per sample
	public double EPSILON = 0.000001;						// Effectively zero for doubles
	private Context calling_context;
	private boolean bolus_interceptor_enabled;
	
	// User Table 1 globals for logging
	double Gest_user1;
	double BrakeAction_user1;
	double XI0_user1;
	double XI1_user1;
	double XI2_user1;
	double XI3_user1;
	double XI4_user1;
	double XI5_user1;
	double XI6_user1;
	double XI7_user1;
	double Gpred_user1;
	double Gpred_light_user1;
	double Gpred_1h_user1;
	

	// DiAs State Variable and Definitions - state for the system as a whole
	public static final int DIAS_STATE_STOPPED = 0;
	public static final int DIAS_STATE_OPEN_LOOP = 1;
	public static final int DIAS_STATE_CLOSED_LOOP = 2;
	public static final int DIAS_STATE_SAFETY_ONLY = 3;
	public static final int DIAS_STATE_SENSOR_ONLY = 4;
	
    // Interface definitions for biometricsContentProvider
    public static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
    public static final Uri INSULIN_CREDIT_URI = Uri.parse("content://"+ PROVIDER_NAME + "/insulincredit");
    public static final Uri USER_TABLE_1_URI = Uri.parse("content://"+ PROVIDER_NAME + "/user1");

	// CGM sensor constants
	public final static int PERMISSIBLE_CGM_GAP_MINUTES = 25;
	
	// Pump constants - in this case Insulet OmniPod
	public static final double BOLUS_MIN = 0.05;
	public static final double BOLUS_MAX = 6.0;
	public static final double BASAL_RATE_MAX = 60.0;

	// Parameters for IOB based hyperglycemia light
	double[][] DER_init = {{0.04}, {0.02}, {0.0}, {-0.02}, {-0.04}};
	Matrix MatDER = new Matrix(DER_init);
	double[][] MatINS_target_init = {{0.0}, {0.0}, {0.0}, {0.0}, {0.0}};
	Matrix MatINS_target = new Matrix(MatINS_target_init);
	public static final int INS_targetslope_window_size_seconds = 27*60;		// Accommodate 5 data points: 25 minute window with 2 minutes of margin
	public final static double T2tgt = 30.0;
	public final static double PredH = 60.0;
	public final static double hyperlight_IOB_threshold = 0.5;
	
	// Parameters for meal-informed power brakes
	private static final double Gop = 90.0;
	public long window_length = 21600; //secs
	public long window_interval = 300;  //sec
	Matrix INS_A;
	Matrix INS_B;
	Matrix INS_C;
	Matrix INS_D;
	Matrix MEAL_A;
	Matrix MEAL_B;
	Matrix MEAL_C;
	Matrix MEAL_D;
	Matrix CORE_pred_A;
	Matrix CORE_pred_C;
	Matrix mat_1h_pred_A;
	Matrix mat_1h_pred_C;
	Matrix CORE_pred_A_light;
	Matrix CORE_pred_B_light;
	Matrix CORE_pred_C_light;
	Matrix KF_A;
	Matrix KF_B;
	Matrix KF_C;
	Matrix KF_D;
	Matrix MEAL_BUFF_A;
	Matrix MEAL_BUFF_B;
	Matrix INSULIN_BUFF_A;
	Matrix INSULIN_BUFF_B;
	Matrix BUFF_C;
	Matrix BUFF_D;
	
	public SSM_state_data state_data; 										// This class encapsulates the CBAM state variables that are being estimated and
																			// that must persist.
	
	double Dma,Uma,Dma1,Dma2,CGMlatest,beta0,beta1;
	int HyperRL,HypoLight;
	Matrix Xr,Yr;
	
	public SSM_processing(
								Subject subject_data,						// Data about the subject
								long time,									// Seconds since 1/1/1970 
								Context callingContext) {
		
 		final String FUNC_TAG = "SSM_processing";
		// Initialize matrices with body weight dependency
		double BW = subject_data.subjectWeight;
		if (BW<EPSILON || BW>200) {
			BW = 70.0;
		}
		double[][] INS_A_init = {	{0.9048, 6.256e-6, 1.498e-6, 3.023e-6}, 											// Now includes BW dependency
				{0.0, 0.4107, 0.5301/BW, 0.2800/BW}, 
				{0.0, 0.0, 0.9048, 0.0452}, 
				{0.0, 0.0, 0.0, 0.9048}};
		INS_A = new Matrix(INS_A_init);
		double[][] INS_B_init = {	{2.7923e-6/BW}, 																	// Now includes BW dependency
									{0.8035/BW}, 
									{0.1170},
									{4.7581}	
		};
		INS_B = new Matrix(INS_B_init);
		double[][] INS_C_init = {	{1.0, 0.0, 0.0, 0.0}, 
				{0.0, 1.0, 0.0, 0.0}, 
				{0.0, 0.0, 1.0, 0.0}, 
				{0.0, 0.0, 0.0, 1.0}};
		INS_C = new Matrix(INS_C_init);
		double[][] INS_D_init = {{0.0}, {0.0}, {0.0}, {0.0}};
		INS_D = new Matrix(INS_D_init);

		double[][] MEAL_A_init = {{0.9048, 0.04524}, {0.0, 0.9048}};
		MEAL_A = new Matrix(MEAL_A_init);
		double[][] MEAL_B_init = {{0.117}, {4.758}};
		MEAL_B = new Matrix(MEAL_B_init);	
		double[][] MEAL_C_init = {{0.02, 0.01}, {1.0, 0.0}, {0.0, 1.0}};
		MEAL_C = new Matrix(MEAL_C_init);
		double[][] MEAL_D_init = {{0.0}, {0.0}, {0.0}};
		MEAL_D = new Matrix(MEAL_D_init);

		double[][] CORE_pred_A_init = {			{0.5488, -1997.2, -1481.8, 0.1733/BW, 0.1127/BW, -0.0148, -0.0186/BW, -0.0318/BW}, // Now includes BW dependency
												{0.0, 0.9704, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
												{0.0, 0.0, 0.5488, 0.0, 0.0, 6.89e-6, 1.75e-5/BW, 2.794e-5/BW},
												{0.0, 0.0, 0.0, 0.5488, 0.1646, 0.0, 0.0, 0.0},
												{0.0, 0.0, 0.0, 0.0, 0.5488, 0.0, 0.0, 0.0},
												{0.0, 0.0, 0.0, 0.0, 0.0, 4.8e-3, 0.4315/BW, 0.5836/BW},
												{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.5488, 0.0},
												{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.1646, 0.5488},
		};
		CORE_pred_A = new Matrix(CORE_pred_A_init);
		double[][] CORE_pred_C_init = {{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}};
		CORE_pred_C = new Matrix(CORE_pred_C_init);
		
		double[][] mat_1h_pred_A_init = {		{0.30119, -3034.3, -1626.4, 0.19023/BW, 0.1522/BW, -0.018416, -0.058026/BW, -0.084929/BW}, // Now includes BW dependency
												{0.0, 0.94176, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
												{0.0, 0.0, 0.30119, 0.0, 0.0, 3.8123e-6, 2.6778e-5/BW, 3.4682e-5/BW},
												{0.0, 0.0, 0.0, 0.30119, 0.18072, 0.0, 0.0, 0.0},
												{0.0, 0.0, 0.0, 0.0, 0.30119, 0.0, 0.0, 0.0},
												{0.0, 0.0, 0.0, 0.0, 0.0, 2.3e-5, 0.33495/BW, 0.32308/BW},
												{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.30119, 0.0},
												{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.18072, 0.30119},
		};
		mat_1h_pred_A = new Matrix(mat_1h_pred_A_init);
		double[][] mat_1h_pred_C_init = {{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}};
		mat_1h_pred_C = new Matrix(mat_1h_pred_C_init);
		

		double[][] CORE_pred_A_light_init = {	{0.8187, -811.51, -736.86, 0.08618/BW, 0.0474/BW, -4.6399e-3, -0.0015418/BW, -0.0029271/BW},  // Now includes BW dependency
												{0.0, 0.99, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
												{0.0, 0.0, 0.8187, 0.0, 0.0, 8.229e-6, 5.694e-6/BW, 8.738e-6/BW},
												{0.0, 0.0, 0.0, 0.8187, 0.0819, 0.0, 0.0, 0.0},
												{0.0, 0.0, 0.0, 0.0, 0.8187, 0.0, 0.0, 0.0},
												{0.0, 0.0, 0.0, 0.0, 0.0, 1.686e-1, 0.3924/BW, 0.6974/BW},
												{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.8187, 0.0},
												{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0819, 0.8187},
		};
		CORE_pred_A_light = new Matrix(CORE_pred_A_light_init);
		double[][] CORE_pred_B_light_init = {	{0.2457/BW}, 									// Now includes BW dependency
												{0.0},
												{0.0},
												{0.4381},
												{9.635},
												{0.0},
												{0.0},
												{0.0}
		};
		CORE_pred_B_light = new Matrix(CORE_pred_B_light_init);
		double[][] CORE_pred_C_light_init = {{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}};
		CORE_pred_C_light = new Matrix(CORE_pred_C_light_init);

		double[][] KF_A_init = {{0.006, -407.177}, 
			{0.0007, 0.9048}};
		KF_A = new Matrix(KF_A_init);
		double[][] KF_B_init = {{-0.0021, 2.5043/BW, 0.8988}, {9.5163e-6, 0.0, -7.3045e-4}};		// Now includes BW dependency
		KF_B = new Matrix(KF_B_init);
		double[][] KF_C_init = {{0.3699, 0.0}, {8.0728e-4, 1.0}};
		KF_C = new Matrix(KF_C_init);
		double[][] KF_D_init = {{0.0, 0.0, 0.6301}, {0.0, 0.0, -8.0728e-4}};
		KF_D = new Matrix(KF_D_init);

		double[][] MEAL_BUFF_A_init = {	{0.9048, 0.0905}, 
				{0.0, 0.9048}};
		MEAL_BUFF_A = new Matrix(MEAL_BUFF_A_init);
		double[][] MEAL_BUFF_B_init = {	{4.679e-3}, 
				{9.516e-2}};
		MEAL_BUFF_B = new Matrix(MEAL_BUFF_B_init);
		double[][] BUFF_C_init = {	{1.0, 0.0}};
		BUFF_C = new Matrix(BUFF_C_init);
		double[][] BUFF_D_init = {	{0.0}};
		BUFF_D = new Matrix(BUFF_D_init);
		double[][] INSULIN_BUFF_A_init = {	{0.6065, 0.3033}, 
				{0.0, 0.6065}};
		INSULIN_BUFF_A = new Matrix(INSULIN_BUFF_A_init);
		double[][] INSULIN_BUFF_B_init = {	{0.0902}, 
				{0.3935}};
		INSULIN_BUFF_B = new Matrix(INSULIN_BUFF_B_init);
		
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
//								double UmaxIOB,								// IOB constraint
								Tvector Tvec_Rate,							// Vector of recent Rate values calculated by hyper_light
								double BOLUS,
								double differential_basal_rate,
								Tvector Tvec_credit_hist_seconds, 
								Tvector Tvec_spent_hist_seconds, 
								Tvector Tvec_net_hist_seconds, 
								SSM_param ssm_param, 
								long time,										// Seconds since 1/1/1970 
								int cycle_duration_mins,
								SSM_state_data state_data,
								long calFlagTime,
								long hypoFlagTime,
								double credit_request,
								double spend_request,
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
 					Tvec_meal_bolus_hist_seconds, 	Tvec_corr_bolus_hist_seconds, BOLUS, differential_basal_rate, Tvec_credit_hist_seconds, 
 					Tvec_spent_hist_seconds, Tvec_net_hist_seconds, 
 					ssm_param, time, cycle_duration_mins, state_data, calFlagTime, hypoFlagTime, credit_request,
 					spend_request, exercise, asynchronous, DIAS_STATE);
 			//	iii)		Bolus interception and user confirmation
 			if (state_data.enough_data == false && (DIAS_STATE == DIAS_STATE_CLOSED_LOOP || DIAS_STATE == DIAS_STATE_SAFETY_ONLY)) {
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
 			
 			// Create a copy of Tvec_cgm_mins within state_data
// 			state_data.Tvec_cgm_mins = new Tvector();
//			int hh = 0;
//			for (hh=0; hh<Tvec_cgm_mins.count(); hh++) {
//				state_data.Tvec_cgm_mins.put( Tvec_cgm_mins.get(hh).time(), Tvec_cgm_mins.get(hh).value() );
//			}
			state_data.Tvec_cgm_mins = new Tvector(Tvec_cgm_mins);
			
 			state_data.Tvec_credit_hist_seconds = new Tvector(Tvec_credit_hist_seconds);
 			state_data.Tvec_spent_hist_seconds = new Tvector(Tvec_spent_hist_seconds);
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
	public void calculate_state_estimate(
								Subject subject_data,				// Current subject data
								Tvector Tvec_cgm_mins, 							// cgm1 history with time stamps in minutes since 1/1/1970
								Tvector Tvec_rate_hist_seconds, 			// insulin rate history with time stamps in seconds since 1/1/1970
								Tvector Tvec_bolus_hist_seconds,			// insulin bolus history with time stamps in seconds since 1/1/1970
								Tvector Tvec_basal_bolus_hist_seconds,		// insulin basal_bolus history with time stamps in seconds since 1/1/1970
								Tvector Tvec_meal_bolus_hist_seconds,		// insulin meal_bolus history with time stamps in seconds since 1/1/1970
								Tvector Tvec_corr_bolus_hist_seconds,		// insulin corr_bolus history with time stamps in seconds since 1/1/1970
								double BOLUS,
								double differential_basal_rate,
								Tvector Tvec_credit_hist_seconds, 
								Tvector Tvec_spent_hist_seconds, 
								Tvector Tvec_net_hist_seconds, 
								SSM_param ssm_param, 
								long time,									// Seconds since 1/1/1970 
								int cycle_duration_mins,
								SSM_state_data state_data,
								long calFlagTime,
								long hypoFlagTime,
								double credit_request,
								double spend_request,
								boolean exercise,
								boolean asynchronous,
								int DIAS_STATE) {
 		final String FUNC_TAG = "calculate_state_estimate";
 		try {
 			// 1.  Set up Credit pool authorization
 			authorized(subject_data, state_data, Tvec_credit_hist_seconds, Tvec_spent_hist_seconds, Tvec_net_hist_seconds, credit_request, spend_request, BOLUS, time, DIAS_STATE);
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
 			if (DIAS_STATE == DIAS_STATE_CLOSED_LOOP || DIAS_STATE == DIAS_STATE_SAFETY_ONLY || (DIAS_STATE == DIAS_STATE_OPEN_LOOP && cgmAvailable)) {

// 	 			meal_informed_state_estimation(	state_data, 
// 	 											ssm_param.filter, 
// 	 											ssm_param.model, 
// 	 											ssm_param.brakes, 
// 	 											ssm_param.brakes_param,
// 	 											ssm_param.hypo_alarm, 
// 	 											Tvec_cgm_mins,
// 	 											subject_data.subjectBasal, 
// 	 											state_data.Tvec_ins_hist_KF_mins, 
// 	 											time, cycle_duration_mins, 
// 	 											ssm_param.iob_param.hist_length/cycle_duration_mins, 
// 	 											exercise_level, 
// 	 											asynchronous);
 	 			
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
 			if (DIAS_STATE == DIAS_STATE_CLOSED_LOOP) {
 				state_data.isMealBolus = classify(subject_data, state_data, ssm_param.flag_param, cycle_duration_mins, time);
 			}
 			else if (DIAS_STATE == DIAS_STATE_SAFETY_ONLY|| (DIAS_STATE == DIAS_STATE_OPEN_LOOP && cgmAvailable)) {		// No meal bolus in Safety Only or Open Loop but run classify(...) to update state
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
 			if (DIAS_STATE == DIAS_STATE_CLOSED_LOOP || DIAS_STATE == DIAS_STATE_SAFETY_ONLY || (DIAS_STATE == DIAS_STATE_OPEN_LOOP && cgmAvailable)) {
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
	public boolean not_enough_data(long time, int DIAS_STATE) {
 		final String FUNC_TAG = "not_enough_data";
 		boolean retValue = false;
 		try {
 			if (DIAS_STATE == DIAS_STATE_CLOSED_LOOP || DIAS_STATE == DIAS_STATE_SAFETY_ONLY ) {
 	 			state_data.SSM_amount = state_data.b;
 	 			if (state_data.BOLUS > 0  ||  state_data.credit_request > 0 ) {
 	 				debug_message(TAG, "HYPORED > not_enough_data > state_data. > BOLUS="+state_data.BOLUS+", credit_request="+state_data.credit_request);
 	 				state_data.stoplight = Safety.RED_LIGHT;
 	 				state_data.state_out = 3;
 	 				state_data.Umax = Math.max(0, state_data.BOLUS+state_data.credit_request);
 	 				state_data.CHOmin = 0;
 	 				state_data.advised_bolus = (0.01*Math.round(100*(state_data.BOLUS+state_data.credit_request)));
 	 				state_data.risky = 0;
 	 				state_data.Abrakes = state_data.SSM_amount;
// 	 				state_data.Umax_IOB = state_data.SSM_amount;
 	 				retValue = true;
 	 			}
 	 			else {
 	 				state_data.risky = 0;
 	 				state_data.Abrakes = state_data.SSM_amount;
// 	 				state_data.Umax_IOB = state_data.SSM_amount;
 	 				retValue = false;
 	 			}
 			}
 			else if (DIAS_STATE == DIAS_STATE_OPEN_LOOP) {
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
//	public int hyper_light(long time, double basal, double CF, Tvector Tvec_Gest, Tvector Tvec_IOB, Tvector Tvec_Rate, long calFlagTime) {
// 		final String FUNC_TAG = "hyper_light";
//		int return_value = Safety.GREEN_LIGHT;
//	   	List<Integer> indices1;
//	   	List<Integer> indices2;
//	   	List<Integer> indices3;
//	   	long t;
//	   	double v;
//	   	int ii;
//	   	Tvector Tvec_glucose_target = new Tvector(5);
//	   	Tvector Tvec_insulin_target = new Tvector(5);
//	   	double INS_target, INS_target_sat, INS_target_predicted;
//	   	double INS_target_slope, INS_target_slope_sat;
//	   	debug_message(TAG, "hyper_light > time="+time+", basal="+basal+", CF="+CF);
//	   	Tvec_Gest.dump(TAG, "Tvec_Gest");
//	   	Tvec_IOB.dump(TAG, "Tvec_IOB");
//	   	Tvec_Rate.dump(TAG, "Tvec_Rate");
//		indices1 = Tvec_Gest.find(">", time-INS_targetslope_window_size_seconds, "<=", time);
//		try {
//			if (indices1 != null) {
//				debug_message(TAG, "indices1.size()="+indices1.size());
//				if (indices1.size() >= 5) {
//					// Initialize Tvec_insulin_target
//					// - We have the 5 most recent Gest values, calculate corresponding INS_target values
//					indices2 = Tvec_Gest.getLastN(5);
//					if (indices2 != null) {
//						try {
//							Iterator<Integer> iterator = indices2.iterator();
//							int jj=0;
//							while (iterator.hasNext()) {
//								ii = iterator.next();
//								t = Tvec_Gest.get_time(ii);
//								v = (Tvec_Gest.get_value(ii) - glucoseTarget(t))/CF;
//								Tvec_insulin_target.put(t, v);
//								MatINS_target.set(jj++, 0, v);
//							}
//							Tvec_insulin_target.dump(TAG, "Tvec_insulin_target");
//							// Calculate INS_target_slope
//							INS_target_slope = -MatDER.transpose().times(MatINS_target).trace();
//							debug_message(TAG, "INS_target_slope="+INS_target_slope);
//							// Calculate the predicted value of INS_target
//							INS_target_sat = INS_target_saturate(Tvec_insulin_target.get_last_value(), CF);
//							INS_target_slope_sat = INS_target_slope_saturate(INS_target_slope, time, calFlagTime);
//							INS_target_predicted = INS_target_sat + PredH*INS_target_slope_sat;
//							debug_message(TAG, "INS_target_sat="+INS_target_sat+", INS_target_slope_sat="+INS_target_slope_sat+", INS_target_predicted="+INS_target_predicted);
//							// Calculate a Rate
//							double Rate = (INS_target_predicted - Math.max(Tvec_IOB.get_last_value(), 0.0))/T2tgt;
//							double Rate_pos;
//							if (Rate >=0)
//								Rate_pos = Rate;
//							else
//								Rate_pos = 0.0;
//							state_data.Rate = Rate;
//							Tvec_Rate.put(time, Rate);
//							// Check for redlight condition: A saturated Rate plus the 3 preceding Rate values also saturated
//							boolean redlight = true;
//							if (state_data.Rate < 2.0*basal/60.0) {
//								redlight = false;
//							}
//							else if (Tvec_Gest.get_last_value() < 180.0) {
//								redlight = false;
//							}
//							else {
//								indices3 = Tvec_Rate.find(">", time-17*60, "<=", time);
//								int kk;
//								if (indices3 != null) {
//									Iterator<Integer> iterator3 = indices3.iterator();
//									if (indices3.size() >= 3) {
//										while (iterator3.hasNext()) {
//											kk = iterator3.next();
//											if (Tvec_Rate.get_value(kk) < 2.0*basal/60.0) {
//												redlight = false;
//											}
//										}
//									}
//									else {
//										redlight = false;
//									}
//								}
//								else {
//									redlight = false;
//								}
//							}
//							debug_message(TAG, "Rate="+Rate+", Rate_pos="+Rate_pos+", 2.0*basal/60.0="+2.0*basal/60.0);
//							// Calculate the hyperlight
//							if (redlight) {
//								return_value = Safety.RED_LIGHT;
//							}
//							else if (Rate > 0.0) {
////							else if (Rate > 0.0 || Tvec_IOB.get_last_value() > hyperlight_IOB_threshold) {
//								return_value = Safety.YELLOW_LIGHT;
//							}
//							else {
//								return_value = Safety.GREEN_LIGHT;
//							}
//							debug_message(TAG, "hyper_light="+return_value);
//						}
//						catch (IllegalArgumentException ex) {
//		            		Bundle b = new Bundle();
//		            		b.putString("description", "IllegalArgumentException in "+FUNC_TAG+" > "+ex.toString());
//		            		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//							debug_message(TAG, "hyper_light > Matrix inner dimensions must agree.");
//							return Safety.YELLOW_LIGHT;
//						}
//						catch (ArrayIndexOutOfBoundsException ex) {
//		            		Bundle b = new Bundle();
//		            		b.putString("description", "ArrayIndexOutOfBoundsException in "+FUNC_TAG+" > "+ex.toString());
//		            		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//							debug_message(TAG, "hyper_light > Array index out of bounds.");
//							return Safety.YELLOW_LIGHT;
//						}
//					}
//				} 
//				else {
//					return Safety.GREEN_LIGHT;
//				}
//			}
//		}
//		catch (Exception e) {
//    		Bundle b = new Bundle();
//    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
//    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//		}
//		return return_value;
//	}
	
	// Returns the value of the hyper-light: 0==green, 1==yellow, 2==red
	public int hyper_light(long time, double basal, double CF, Tvector Tvec_Gest, Tvector Tvec_IOB, Tvector Tvec_Rate, long calFlagTime) {
	 		final String FUNC_TAG = "hyper_light";
			
	 		HyperRL = Safety.GREEN_LIGHT;

			double Thres = -0.015;			
			
			if (Dma<Thres && CGMlatest>250 && beta1>-0.2){  //Dma from KF
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
	
	public double glucoseTarget(long time) {
 		final String FUNC_TAG = "glucoseTarget";
		double x = 0.0;
 		try {
 			// Get the offset in hours into the current day in the current time zone (based on cell phone time zone setting)
 			TimeZone tz = TimeZone.getDefault();
 			int UTC_offset_secs = tz.getOffset(time*1000)/1000;
 			int timeNowMins = (int)((time+UTC_offset_secs)/60)%1440;
 			double ToD_hours = (double)timeNowMins/60.0;
// 			debug_message(TAG, "subject_parameters > time="+time+", time/60="+time/60+", timeNowMins="+timeNowMins+", ToD_hours="+ToD_hours);
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
 		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
		return 160.0-45.0*Math.pow((x/0.5),3.0)/(1.0+Math.pow((x/0.5),3.0));
	}

	public double INS_target_saturate(double INS_target, double CF) {
 		final String FUNC_TAG = "INS_target_saturate";
 		double retValue = 90.0/CF;
 		try {
 			if (INS_target < 90.0/CF)
 				retValue = INS_target;
 			else
 				retValue = (90.0/CF);
 		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
 		return retValue;
	}
	
	public double INS_target_slope_saturate(double INS_target_slope, long time, long calFlagTime) {
 		final String FUNC_TAG = "INS_target_slope_saturate";
 		double retValue = 0.0;
 		try {
 			if (time-calFlagTime > 30*60) {
 				// The last calibration occurred more than 30 minutes ago
 				if (INS_target_slope > 1.0/PredH)
 					retValue = 1.0/PredH;
 				else if (INS_target_slope < -10.0/PredH)
 					retValue = -10.0/PredH;
 				else
 					retValue = INS_target_slope;
 			}
 			else {
 				// A calibration occurred within the last 30 minutes
 				if (INS_target_slope > 1.0/PredH)
 					retValue = 0.0;
 				else if (INS_target_slope < -10.0/PredH)
 					retValue = -10.0/PredH;
 				else
 					retValue = INS_target_slope;
 			}
 		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
		return retValue;
	}	
			
	//
	//	Handle the case where the bolus is not a meal bolus but there may be need for a credit request
	//
	public boolean correction_filter(
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
	        if (DIAS_STATE == DIAS_STATE_CLOSED_LOOP || DIAS_STATE == DIAS_STATE_SAFETY_ONLY) {
	        	
	        	if (brakes_param.k < 0.0) {
	        		Bundle b = new Bundle();
	        		b.putString("description", "Incorrect value of CF or TDI in brakes_param.k calculation");
	        		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
	        		
	        		return false; //TODO: Is that right???
	        	}
	        	
	            if (state_data.risky > 0) {
	    				state_data.Abrakes=Math.min(cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins),(subject_data.basal*add_basal/(60/cycle_duration_mins))/(1+brakes_param.k*state_data.risky));
//	    				double I_g = Math.max(0,(state_data.CGM-iob_param.target)/subject_data.CF-state_data.IOBlast2);
//	    	            double Umax_IOB = I_g + subject_data.basal*add_basal/(60/cycle_duration_mins);
//	    	            state_data.Umax_IOB = Umax_IOB;
	    	            //changed by CHK 09/07/2012
//	    	            state_data.basal_added = Math.min(state_data.Abrakes,state_data.Umax_IOB);
	    	            state_data.basal_added = state_data.Abrakes;
	    	            Debug.i(TAG, FUNC_TAG, "basal_added="+state_data.basal_added+", Abrakes="+state_data.Abrakes);
	    				//state_data.basal_added = subject.basal*add_basal/(60/cycle_duration_mins)/(1+brakes_param.k*state_data.risky);
	            
	            }
	            else {
	    				state_data.Abrakes=(cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins));
//	    				double I_g = Math.max(0,(state_data.CGM-iob_param.target)/subject_data.CF-state_data.IOBlast2);
//	    	            double Umax_IOB = I_g + subject_data.basal*add_basal/(60/cycle_duration_mins);
//	    	            state_data.Umax_IOB = Umax_IOB;
	    	            //changed by CGK 09/07/2012
//	    	            state_data.basal_added = Math.min((cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins)), state_data.Umax_IOB);
	    	            state_data.basal_added = cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins);
	    	            Debug.i(TAG, FUNC_TAG, "basal_added="+state_data.basal_added+", Usugg="+state_data.Usugg+", basal="+subject_data.basal+", Abrakes="+state_data.Abrakes);
	            }
	            state_data.brakes_coeff=1/(1+brakes_param.k*state_data.risky);
	            
	        }
	        else {
				state_data.Abrakes=(cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins));
//				double Umax_IOB = (cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins));
//		        state_data.Umax_IOB = Umax_IOB;
				//changed by CHK 09/07/2012
				state_data.basal_added = (cycle_duration_mins*state_data.Usugg+subject_data.basal*add_basal/(60/cycle_duration_mins));
	            Debug.i(TAG, FUNC_TAG, "basal_added="+state_data.basal_added+", Usugg="+state_data.Usugg+", basal="+subject_data.basal+", add_basal="+add_basal);
				//state_data.basal_added = subject.basal*add_basal/(60/cycle_duration_mins);
		        state_data.brakes_coeff=1;
		        
	        }
	        if (asynchronous) {
	        	state_data.basal_added = 0.0;
	        }
	        else {
	        	double alpha_corr = 0.0;	// The proportion of the total requested insulin that is being approved for delivery
	        	double alpha_corr_denominator = ((double)cycle_duration_mins*state_data.Usugg + subject_data.basal*add_basal/(60.0/(double)cycle_duration_mins));
	        	if (alpha_corr_denominator>EPSILON || alpha_corr_denominator<-EPSILON) {
	        		alpha_corr = state_data.basal_added/alpha_corr_denominator;
	        	}
	        	state_data.basal_added = Math.max(0.0, state_data.basal_added - alpha_corr*state_data.BOLUS_original);
	        }
        	Debug.i(TAG, FUNC_TAG, "asynchronous="+asynchronous+", basal_added="+state_data.basal_added);
        	
	        // compute SSM correction
//	        state_data.SSM_amount = Math.min(state_data.Abrakes,state_data.Umax_IOB); 
	        state_data.SSM_amount = state_data.Abrakes; 
	        if (state_data.credit_request > 0) {
	        		state_data.Umax = Math.max(0.0, 0.01*Math.round(100.0*state_data.credit_request));
	        		state_data.CHOmin = (int)(Math.max(0.0, ((state_data.Gpred-flag_param.low_target)/subject_data.CF)-state_data.IOB_meal)*subject_data.CR);
	        		state_data.advised_bolus = 0.01*Math.round(100.0*state_data.credit_request);
	        		retValue = true;
	        }
	        debug_message(TAG, "state_data.credit_request="+state_data.credit_request+", state_data.spend_request="+state_data.spend_request);
		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
		return retValue;
	}
	
	//
	//	Handle meal bolus detected
	//
	public boolean meal_bolus_detected(
								Subject subject_data,
								int cycle_duration_mins,
								SSM_flag_param flag_param,
								long time,
								int DIAS_STATE) {
 		final String FUNC_TAG = "meal_bolus_detected";
		boolean retValue = true;
		try {
 			if (DIAS_STATE == DIAS_STATE_CLOSED_LOOP) {
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
 			    	state_data.advised_bolus = 0.01*Math.round(100.0*(cycle_duration_mins*state_data.Usugg+state_data.credit_request));
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
	

	public void authorized(
								Subject subject_data,
								SSM_state_data state_data,
								Tvector Tvec_credit_hist_seconds,
								Tvector Tvec_spent_hist_seconds,
								Tvector Tvec_net_hist_seconds,
								double credit_request,
								double spend_request,
								double BOLUS,
								long time,
								int DIAS_STATE) {
 		final String FUNC_TAG = "authorized";
 		try {
 			state_data.Tvec_credit_hist_seconds = new Tvector(Tvec_credit_hist_seconds);
 			state_data.Tvec_spent_hist_seconds = new Tvector(Tvec_spent_hist_seconds);
 			state_data.Tvec_net_hist_seconds = new Tvector(Tvec_net_hist_seconds);
 			state_data.credit_request = credit_request;
 			state_data.spend_request = spend_request;
 			state_data.BOLUS = BOLUS;
 			state_data.BOLUS_original = BOLUS;
 			double total_credit, total_spent, total_net;
 			double net=0.0, net2=0.0, pre_authorized_delta=0.0;
 			int ii;
 			debug_message(TAG_CREDITPOOL, "   ");
 			debug_message(TAG_CREDITPOOL, "in > pre_authorized="+state_data.pre_authorized+", spend_request="+state_data.spend_request+", credit_request="+state_data.credit_request);
 			state_data.Tvec_credit_hist_seconds.dump(TAG_CREDITPOOL, "authorized > Tvec_credit_hist_seconds");
 			state_data.Tvec_spent_hist_seconds.dump(TAG_CREDITPOOL, "authorized > Tvec_spent_hist_seconds");
 			state_data.Tvec_net_hist_seconds.dump(TAG_CREDITPOOL, "authorized > Tvec_net_hist_seconds");
 			debug_message(TAG, "authorized > ");
 			// Calculate the amount of credit that arrived in the last hour
 			total_credit = 0.0;
 			List<Integer> indices = new ArrayList<Integer>();
 			indices = state_data.Tvec_credit_hist_seconds.find(">", time-3600*3, "<=", time);			// time converted to minutes
 			if (indices != null) {				
// 				debug_message(TAG_CREDITPOOL, "time="+time+", credit size="+indices.size());
 				for (ii=0; ii<indices.size(); ii++) {
 					total_credit=total_credit+state_data.Tvec_credit_hist_seconds.get_value(indices.get(ii));
 				}
 			}
 			// Calculate the amount spent in the last hour
 			total_spent = 0.0;
 			if (indices != null) {				
 				indices.clear();
 			}
 			indices = state_data.Tvec_spent_hist_seconds.find(">", time-3600*3, "<=", time);			// time converted to minutes
 			if (indices != null) {				
// 				debug_message(TAG_CREDITPOOL, "spent size="+indices.size());
 				for (ii=0; ii<indices.size(); ii++) {
 					total_spent=total_spent+state_data.Tvec_spent_hist_seconds.get_value(indices.get(ii));
 				}
 			}
 			// Calculate the current amount of net credit in the last hour
 			total_net = 0.0;
 			if (indices != null) {				
 				indices.clear();
 			}
 			indices = state_data.Tvec_net_hist_seconds.find(">", time-3600*3, "<=", time);			// time converted to minutes
 			if (indices != null) {				
// 				debug_message(TAG_CREDITPOOL, "net size="+indices.size());
 				for (ii=0; ii<indices.size(); ii++) {
 					total_net=total_net+state_data.Tvec_net_hist_seconds.get_value(indices.get(ii));
 				}
 			}
 			// Database sanity check here.  Net should not differ from credit-spent.
 			if (Math.abs(total_net-(total_credit-total_spent)) > EPSILON) {
 				log_action(TAG, "authorized > net credit mismatch at t="+time, time);
 				Log.e(TAG_CREDITPOOL, "authorized > net credit mismatch at t="+time);
 			}
 			// Do not remember pre_authorized insulin from previous calls
 			state_data.pre_authorized = 0.0;
 			state_data.sp_req_mem = state_data.spend_request;
 			net = total_credit - total_spent - state_data.spend_request;
 			debug_message(TAG_CREDITPOOL, "total_credit="+total_credit+", total_spent="+total_spent+", state_data.spend_request="+state_data.spend_request+
 					", net="+net);
 			// Trying to spend less than is already permitted
 			if (net > 0.0) {
 				state_data.pre_authorized = state_data.pre_authorized + state_data.spend_request;
 				pre_authorized_delta = state_data.spend_request;
 				state_data.spend_request = 0.0;
 			}
 			// Trying to spend more than already allowed but asking for enough additional credit to cover
 			else if (state_data.credit_request > -net) {
 				state_data.pre_authorized = state_data.pre_authorized + total_credit - total_spent;
 				pre_authorized_delta = total_credit - total_spent;
 				state_data.spend_request = state_data.spend_request - state_data.pre_authorized;
 			}
 			// Trying to spend more than already allowed without requesting enough additional credit
 			else {
 				state_data.pre_authorized = state_data.pre_authorized + total_credit - total_spent;
 				pre_authorized_delta = total_credit - total_spent;
 				net2 = state_data.spend_request - state_data.pre_authorized - state_data.credit_request;
 				// Ask for enough credit to cover the spend request
 				state_data.credit_request = state_data.credit_request + net2;
 				state_data.spend_request = state_data.spend_request - state_data.pre_authorized;
 			}
 			// If the entire spend request is satisfied then update the database now
 			debug_message(TAG_CREDITPOOL, "authorized > pre_authorized_delta="+pre_authorized_delta);
 			if (pre_authorized_delta > 0.0) {
 				storeSpentInsulin(pre_authorized_delta, time);
 			}
 			// Clamp values to be not less than zero
 			state_data.pre_authorized = clamp(state_data.pre_authorized);
 			state_data.spend_request = clamp(state_data.spend_request);
 			state_data.credit_request = clamp(state_data.credit_request);
 			state_data.Tvec_credit_hist_seconds.dump(TAG_CREDITPOOL, "credit", 12);
 			state_data.Tvec_spent_hist_seconds.dump(TAG_CREDITPOOL, "spent", 12);
 			debug_message(TAG_CREDITPOOL, "out > pre_authorized="+state_data.pre_authorized+", spend_request="+state_data.spend_request+", credit_request="+state_data.credit_request);
 			debug_message(TAG_CREDITPOOL, "time="+time+", net="+net+", net2="+net2+", BOLUS="+state_data.BOLUS);
		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
	}
	
	public void BOLUS_assign(
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
 			//storeUserTableData(Biometrics.USER_TABLE_2_URI, time, state_data.Usugg, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
 			storeUserTableData(Biometrics.USER_TABLE_2_URI, time, differential_basal_rate/60.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
 			debug_message(TAG,"APC_output<<<<<<<<<<<<<<<<<<");

 		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
	}
	
	public void subject_parameters(
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
 		    if (enough_data || DIAS_STATE == DIAS_STATE_OPEN_LOOP) {
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
	
	public boolean CGM_filter(Tvector cgm1, long time, SSM_state_data state_data) {
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
		final String FUNC_TAG = "readCgmStatefromSystemTable";
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
	
//	public void meal_informed_state_estimation(	SSM_state_data state_data,
//												Filter filter,
//												SSM_model model,
//												SSM_brakes brakes,
//												SSM_brakes_param brakes_param,
//												SSM_hypo_alarm hypo_alarm,
//												Tvector Tvec_cgm_mins,
//												Tvector Tvec_basal_pattern,
//												Tvector Tvec_ins_hist_KF_mins,
//												long time,
//												int cycle_duration_mins,
//												int ndiscrete,
//												double exercise_level,
//												boolean asynchronous) {
//		final String FUNC_TAG = "meal_informed_state_estimation";
//		try {
//			// Get the offset in minutes into the current day in the current time zone (based on cell phone time zone setting)
//			TimeZone tz = TimeZone.getDefault();
//			int UTC_offset_secs = tz.getOffset(time*1000)/1000;
//			long tlocal;
//	  		Pair p = Tvec_basal_pattern.getLastInRange(">", -1, "<", -1);
//	  		double basal_t = p.value();
//		  	//*********************
//		  	// Kalman Filter Setup
//		  	//*********************
//			double[][] M_init_2x1 = {{0.0}, {0.0}};
//			double[][] M_init_3x1 = {{0.0}, {0.0}, {0.0}};
//			double[][] M_init_4x1 = {{0.0}, {0.0}, {0.0}, {0.0}};
//			double[][] M_init_8x1 = {{0.0}, {0.0}, {0.0}, {0.0}, {0.0}, {0.0}, {0.0}, {0.0}};
//			// GI system storage
//			Matrix Mbuf = new Matrix(state_data.Mbuf);
//			double Mbuf_out = 0.0;
//			Matrix Mstate = new Matrix(state_data.Mstate);
//			Matrix Mout = new Matrix(M_init_3x1);
//			Matrix Ms_out = new Matrix(M_init_2x1);
//			Matrix Ms_out_temp = new Matrix(M_init_2x1);
//			double Rak = 0.0;
//			// Insulin system storage
//			Matrix Ibuf = new Matrix(state_data.Ibuf);
//			double Ibuf_out = 0.0;
//			Matrix Istate = new Matrix(state_data.Istate);
//			Matrix Iout = new Matrix(M_init_4x1);
//			Matrix Is_out = new Matrix(M_init_2x1);
//			double Ik = 0.0, Xpredk = 0.0;
//			// KF system storage
//			Matrix KFstate = new Matrix(state_data.KFstate);
//			Matrix KFout = new Matrix(M_init_3x1);
//			Matrix KFu = new Matrix(M_init_3x1);
//			double CGM, CGM_mem;
//			boolean CGM_mem_valid;
//			double Gest=0.0, Gpred=0.0, Gpred_light=0.0, Gpred_1h=0.0, Risk=0.0, RiskEX=0.0, BrakeAction=1.0;
//			// Predictor storage
//			Matrix XI = new Matrix(M_init_8x1);
//
//		  	int ii, jj;
//		  	double J, dtemp2, dtemp3, dtemp4;
//		  	int index;
//		  	List<Integer> indices1 = null;
//		  	List<Integer> indices2 = null;
//		  	double[] Xpred = {0, 0, 0, 0, 0, 0, 0, 0};
//		  	long tX_mins=time/60;
//		  	
//		  	// If the KF has not been run for more than 2 hours, re-initialize stored copies of Mstate, Ibuf and KFstate
//		  	// and run the KF on the last 2 hours of data
//		  	if (state_data.tXi0 < tX_mins-120) {
//			  	indices1 = Tvec_cgm_mins.find(">=", tX_mins-120, "<=", tX_mins);
//			  	if (indices1 != null) {
//			  		if (indices1.size() > 0) {
//			  			state_data.tXi0 = Tvec_cgm_mins.get_time(indices1.get(0));
//			  		}
//			  		else {
//			  		  	state_data.tXi0 = tX_mins;
//			  		}
//			  	}
//		  		else {
//		  		  	state_data.tXi0 = tX_mins;
//		  		}
//				for (jj=0; jj<2; jj++) {
//					state_data.Mbuf[jj][0] = 0.0;
//				  	state_data.Mstate[jj][0] = 0.0;
//					state_data.Ibuf[jj][0] = 0.0;
//				  	state_data.KFstate[jj][0] = 0.0;
//				}
//				for (jj=0; jj<4; jj++) {
//				  	state_data.Istate[jj][0] = 0.0;
//				}
//		  	}
//
//		  	// Initialize local copies of Mbuf, Mstate, Ibuf and KFstate from the stored copies
//		  	for (jj=0; jj<2; jj++) {
//			  		Mbuf.set(jj, 0, state_data.Mbuf[jj][0]);
//			  		Mstate.set(jj, 0, state_data.Mstate[jj][0]);
//			  		Ibuf.set(jj, 0, state_data.Ibuf[jj][0]);
//			  		KFstate.set(jj, 0, state_data.KFstate[jj][0]);
//			  	}
//		  	for (jj=0; jj<4; jj++) {
//		  		Istate.set(jj, 0, state_data.Istate[jj][0]);
//		  	}
//		  	
//		  	// Set the starting time t for the KF loop
//		  	long dt_mins = 5;
//		  	long t_mins = state_data.tXi0 + dt_mins;
//		  	if (t_mins > tX_mins) {							
//		  		if (tX_mins > state_data.tXi0) {
//		  			t_mins = tX_mins;
//		  		}
//		  		else if (state_data.tXi0 + 1 < tX_mins) {
//		  			t_mins = state_data.tXi0 + 1;
//		  		}
//		  		else {
//		  			t_mins = tX_mins - 1;
//		  		}
//		  	}
//		  	
//	/*
//		  	debug_message(TAG, "MEAL_BUFF_A("+MEAL_BUFF_A.getRowDimension()+", "+MEAL_BUFF_A.getColumnDimension()+"), MEAL_BUFF_B("+MEAL_BUFF_B.getRowDimension()+", "+MEAL_BUFF_B.getColumnDimension()+")");
//		  	debug_message(TAG, "MEAL_A("+MEAL_A.getRowDimension()+", "+MEAL_A.getColumnDimension()+"), MEAL_B("+MEAL_B.getRowDimension()+", "+MEAL_B.getColumnDimension()+")");
//		  	debug_message(TAG, "MEAL_C("+MEAL_C.getRowDimension()+", "+MEAL_C.getColumnDimension()+"), MEAL_D("+MEAL_D.getRowDimension()+", "+MEAL_D.getColumnDimension()+")");
//		  	debug_message(TAG, "INS_A("+INS_A.getRowDimension()+", "+INS_A.getColumnDimension()+"), INS_B("+INS_B.getRowDimension()+", "+INS_B.getColumnDimension()+")");
//		  	debug_message(TAG, "INS_C("+INS_C.getRowDimension()+", "+INS_C.getColumnDimension()+"), INS_D("+INS_D.getRowDimension()+", "+INS_D.getColumnDimension()+")");
//		  	debug_message(TAG, "KF_A("+KF_A.getRowDimension()+", "+KF_A.getColumnDimension()+"), KF_B("+KF_B.getRowDimension()+", "+KF_B.getColumnDimension()+")");
//		  	debug_message(TAG, "KF_C("+KF_C.getRowDimension()+", "+KF_C.getColumnDimension()+"), KF_D("+KF_D.getRowDimension()+", "+KF_D.getColumnDimension()+")");
//		  	
//		  	debug_message(TAG, "Mbuf("+Mbuf.getRowDimension()+", "+Mbuf.getColumnDimension()+"), Mstate("+Mstate.getRowDimension()+", "+Mstate.getColumnDimension()+")");
//		  	debug_message(TAG, "Ms_out("+Ms_out.getRowDimension()+", "+Ms_out.getColumnDimension()+"), Mout("+Mout.getRowDimension()+", "+Mout.getColumnDimension()+")");
//		  	debug_message(TAG, "Ibuf("+Ibuf.getRowDimension()+", "+Ibuf.getColumnDimension()+"), Istate("+Istate.getRowDimension()+", "+Istate.getColumnDimension()+")");
//		  	debug_message(TAG, "Is_out("+Is_out.getRowDimension()+", "+Is_out.getColumnDimension()+"), Iout("+Iout.getRowDimension()+", "+Iout.getColumnDimension()+")");
//		  	debug_message(TAG, "KFstate("+KFstate.getRowDimension()+", "+KFstate.getColumnDimension()+"), KFout("+KFout.getRowDimension()+", "+KFout.getColumnDimension()+")");
//	*/	  	
//		  	boolean kf_loop_flag=false;
//		  	int kf_loop_count = 0;
//		  	while (t_mins <= tX_mins) {			// Start KF Loop
//		  		//********************
//		  		// Kalman Filter Loop
//		  		//********************
//			  	kf_loop_count++;
//		  		
//				// Fetch the currently active meal (if any)
//				Meal meal_current = new Meal(calling_context);
//				double meal_amount;
//				if (meal_current.readActive()) {
//					if (!meal_current.extended_bolus && t_mins*60-meal_current.time <= 300 && t_mins*60-meal_current.time >= 0) {
//						meal_amount = 1000.0*meal_current.meal_size_grams/5.0;
//					}
//					else {
//						meal_amount = 0.0;
//					}
//				}
//				else {
//					meal_amount = 0.0;
//				}
//			  	Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count+", t_mins="+t_mins+", tX_mins="+tX_mins+", meal_amount="+meal_amount);
//				// 1. GI code for one 5-minute time step
//				try {
//					Mbuf_out = BUFF_C.times(Mbuf).trace();
//					Mbuf = MEAL_BUFF_A.times(Mbuf).plus(MEAL_BUFF_B.times(meal_amount));					// Mbuf update step
//					Mout = MEAL_C.times(Mstate).plus(MEAL_D.times(Mbuf_out));
//					Mstate = MEAL_A.times(Mstate).plus(MEAL_B.times(Mbuf_out));
//					Rak = Mout.get(0, 0);
//					Ms_out.set(0, 0, Mout.get(1, 0));
//					Ms_out.set(1, 0, Mout.get(2, 0));
//					Ms_out_temp = Ms_out.copy();
//					Debug.i(TAG, FUNC_TAG, "Ms_out(1):"+Ms_out.get(0,0));
//				  	Debug.i(TAG, FUNC_TAG, "Ms_out_temp(1):"+Ms_out_temp.get(0,0));
//	
//				}
//				catch (IllegalArgumentException ex) {
//	        		Bundle b = new Bundle();
//	        		b.putString("description", "IllegalArgumentException in "+FUNC_TAG+" > "+ex.toString());
//	        		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//					Log.e(TAG, "GI >"+ex.toString());
//				}
//				catch (ArrayIndexOutOfBoundsException ex) {
//	        		Bundle b = new Bundle();
//	        		b.putString("description", "ArrayIndexOutOfBoundsException in "+FUNC_TAG+" > "+ex.toString());
//	        		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//					Log.e(TAG, "GI >"+ex.toString());
//				}
///*
//				Debug.i(TAG, FUNC_TAG, "GI > Mbuf_out="+Mbuf_out);
//				for (jj=0; jj<2; jj++) {
//					Debug.i(TAG, FUNC_TAG, "GI > Mbuf["+jj+"]="+Mbuf.get(jj, 0));
//				}
//				for (jj=0; jj<2; jj++) {
//					Debug.i(TAG, FUNC_TAG, "GI > Mstate["+jj+"]="+Mstate.get(jj, 0));
//				}
//				for (jj=0; jj<2; jj++) {
//					Debug.i(TAG, FUNC_TAG, "GI > Mout["+jj+"]="+Mout.get(jj, 0));
//				}
//*/
//				
//				Tvec_ins_hist_KF_mins.dump(TAG, "kf_loop_count", 8);
//		  		
//				// 2. Insulin system code for one 5-minute time step
//		  		tlocal = (t_mins+UTC_offset_secs/60)%1440;						// tlocal the number of minutes into the current day in local time
//		  		try {
//			  		if ((p = Tvec_basal_pattern.getLastInRange(">", -1, "<=", tlocal)) == null) {
//			      		p = Tvec_basal_pattern.getLastInRange(">", -1, "<", -1);
//			  		}
//			  		basal_t = p.value();
//		  		}
//				catch (Exception e) {
//		 			Bundle b = new Bundle();
//		 			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
//		 			Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//				}	
//		  		// Find the element of discrete_time just less than t_mins
//		  		index = -1;
//		  		for (jj=0; jj<ndiscrete; jj++) {
//		  			if (state_data.discrete_time_seconds[jj] <= t_mins) {
//		  				index=jj;
//		  			}
//		  		}
//		  		if (index < 0) {
//		  			dtemp4=0.0;
//		  			J=0.0;
//		  		}
//		  		else {
//		  			Double Dval = Tvec_ins_hist_KF_mins.get_value_using_time_as_index(state_data.discrete_time_seconds[index]);
//		  			if (Dval != null) {
//			  			dtemp4 = Dval.doubleValue();
//					  	Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count+", Dval.doubleValue()="+Dval.doubleValue());
//		  			}
//		  			else {
//			  			dtemp4 = 0.0;
//					  	Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count+", Dval==null");
//		  			}
//		  		//J=(dtemp4*60.0/(double)cycle_duration_mins-basal_t);
//		  			J=(dtemp4*1000*6/5-basal_t*1000*6/60);  // by Boyi
//		  		}
//		  		
//			  	Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count+", index_time="+state_data.discrete_time_seconds[index]+", index_value="+Tvec_ins_hist_KF_mins.get_value_using_time_as_index(state_data.discrete_time_seconds[index]));
//			  	Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count+", last_time="+Tvec_ins_hist_KF_mins.get_last_time()+", last_value="+Tvec_ins_hist_KF_mins.get_last_value());
//
//			  	Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count+", index="+index+", discrete_time[index]="+state_data.discrete_time_seconds[index]);
//			  	Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count+", discrete_time[index]/60="+state_data.discrete_time_seconds[index]/60);
//		  		Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count+", J="+J+", Tvec_ins_hist_KF_mins[...]="+dtemp4);
//				try {
//					Ibuf_out = BUFF_C.times(Ibuf).plus(BUFF_D.times(J)).trace();
//					Ibuf = INSULIN_BUFF_A.times(Ibuf).plus(INSULIN_BUFF_B.times(J));				// Ibuf update step
//					Iout = INS_C.times(Istate).plus(INS_D.times(Ibuf_out));
//					Istate = INS_A.times(Istate).plus(INS_B.times(Ibuf_out));
//					Ik = Iout.get(1, 0);
//					Xpredk = Iout.get(0, 0);
//					Is_out.set(0, 0, Iout.get(2, 0));
//					Is_out.set(1, 0, Iout.get(3, 0));
//				}
//				catch (IllegalArgumentException ex) {
//	        		Bundle b = new Bundle();
//	        		b.putString("description", "IllegalArgumentException in "+FUNC_TAG+" > "+ex.toString());
//	        		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//					Log.e(TAG, "Insulin >"+ex.toString());
//				}
//				catch (ArrayIndexOutOfBoundsException ex) {
//	        		Bundle b = new Bundle();
//	        		b.putString("description", "ArrayIndexOutOfBoundsException in "+FUNC_TAG+" > "+ex.toString());
//	        		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//					Log.e(TAG, "Insulin >"+ex.toString());
//				}
//				Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count+", Ik="+Ik+", Xpredk="+Xpredk);
//				
//				// 3. KF estimate for one 5-minute time step
//	  			// Set the value of CGM
//	  			CGM_mem_valid = false;
//	  			CGM = 0.0;
//	  			CGM_mem = CGM;
//	  			indices1 = Tvec_cgm_mins.find(">", t_mins-filter.width, "<", t_mins+filter.width);
//	  			indices2 = null;
//	  			if (indices1 == null) {
//	  				CGM=model.Gref;
//	  				CGM_mem = CGM;
//	  				CGM_mem_valid = true;	
//	  			}
//	  			else if (indices1.size() > 1) {
//	  				indices2=Tvec_cgm_mins.find(">", -1, "<=", t_mins);		// Don't exclude last value
//	  				if (indices2 == null) {
//						if (CGM_mem_valid) {
//							CGM = CGM_mem;
//						}
//						else {
//							CGM = model.Gref;
//						}
//	  				}
//	  				else if (indices2.size() > 0) {
//						CGM = Tvec_cgm_mins.get_value(indices2.get(indices2.size()-1));
//						CGM_mem = CGM;
//						CGM_mem_valid = true;	  					
//	  				}
//	  				else {
//	  					Debug.i(TAG, FUNC_TAG, "state_estimation > error: indices2 is not null but has size()==0");
//	  				}
//	  			}
//	  			else if (indices1.size() == 1) {
//	  				CGM = Tvec_cgm_mins.get_value(indices1.get(0));
//	  				CGM_mem = CGM;
//	  				CGM_mem_valid = true;	
//	 			}
//	  			else {
//	  				Debug.i(TAG, FUNC_TAG, "state_estimation > error: indices1 is not null but has size()==0");
//	  			}
//				try {
//					KFu.set(0, 0, Ik);
//					KFu.set(1, 0, Rak);
//					KFu.set(2, 0, CGM-Gop);
//					KFout = KF_C.times(KFstate).plus(KF_D.times(KFu));
//					KFstate = KF_A.times(KFstate).plus(KF_B.times(KFu));
//					for (jj=0; jj<2; jj++) {
//						Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count+", KFstate["+jj+"]="+KFstate.get(jj, 0));
//					}
//				}
//				catch (IllegalArgumentException ex) {
//	        		Bundle b = new Bundle();
//	        		b.putString("description", "IllegalArgumentException in "+FUNC_TAG+" > "+ex.toString());
//	        		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//					Log.e(TAG, "KF >"+ex.toString());
//				}
//				catch (ArrayIndexOutOfBoundsException ex) {
//	        		Bundle b = new Bundle();
//	        		b.putString("description", "ArrayIndexOutOfBoundsException in "+FUNC_TAG+" > "+ex.toString());
//	        		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//					Log.e(TAG, "KF >"+ex.toString());
//				}
//				Gest = KFout.get(0, 0) + Gop;
//				
//				// Store Insulin and KF values for later inspection
//				storeUserTableData(	Biometrics.USER_TABLE_2_URI,
//						time,
//						(double)t_mins,
//						(double)kf_loop_count,
//						Ibuf_out, 
//						J,
//						Ik,
//						Xpredk,
//						Ibuf.get(0, 0), Ibuf.get(1, 0), 
//						Iout.get(0, 0), Iout.get(1, 0), Iout.get(2, 0), Iout.get(3, 0),
//						KFout.get(0, 0), KFout.get(1, 0), 
//						KFstate.get(0, 0), KFstate.get(1, 0));
//				
//				
//				// Save state values for next iteration
//				state_data.Mbuf[0][0] = Mbuf.get(0, 0);
//				state_data.Mbuf[1][0] = Mbuf.get(1, 0);
//				state_data.Ibuf[0][0] = Ibuf.get(0, 0);
//				state_data.Ibuf[1][0] = Ibuf.get(1, 0);
//				state_data.Mstate[0][0] = Mstate.get(0, 0);
//				state_data.Mstate[1][0] = Mstate.get(1, 0);
//				state_data.Istate[0][0] = Istate.get(0, 0);
//				state_data.Istate[1][0] = Istate.get(1, 0);
//				state_data.Istate[2][0] = Istate.get(2, 0);
//				state_data.Istate[3][0] = Istate.get(3, 0);
//				state_data.KFstate[0][0] = KFstate.get(0, 0);
//				state_data.KFstate[1][0] = KFstate.get(1, 0);
//				
//				t_mins = t_mins + dt_mins;
//		  		Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count+" >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
//		  		Debug.i(TAG, FUNC_TAG, "kf_loop_count="+kf_loop_count);
//		  	}	// End of KF loop
//		  	
//			// Make the prediction
//			try {
//				XI.set(0, 0, KFout.get(0, 0));
//				XI.set(1, 0, KFout.get(1, 0)-Xpredk);
//				XI.set(2, 0, Xpredk);
//				XI.set(3, 0, Ik);
//				XI.set(4, 0, Is_out.get(0, 0));
//				XI.set(5, 0, Is_out.get(1, 0));
//				XI.set(6, 0, Ms_out_temp.get(0, 0));
//				XI.set(7, 0, Ms_out_temp.get(1, 0));
//				
//				Debug.i(TAG, FUNC_TAG, "Is_out(1):"+Is_out.get(0,0));
//
//			  	Debug.i(TAG, FUNC_TAG, "Ms_out(1):"+Ms_out.get(0,0));
//			  	
//			  	Debug.i(TAG, FUNC_TAG, "Ms_out_temp(1):"+Ms_out_temp.get(0,0));
//
//
//				for (jj=0; jj<8; jj++) {
//					Debug.i(TAG, FUNC_TAG, "XI["+jj+"]="+XI.get(jj, 0));
//				}
//				Gpred = CORE_pred_C.times(CORE_pred_A).times(XI).trace() + (Gop);				
//				Gpred_light = CORE_pred_C_light.times((CORE_pred_A_light).times(XI).plus(CORE_pred_B_light.times(-basal_t))).trace() + (Gop);
//				Gpred_1h = mat_1h_pred_C.times(mat_1h_pred_A).times(XI).trace() + (Gop);				
//			}
//			catch (IllegalArgumentException ex) {
//	    		Bundle b = new Bundle();
//	    		b.putString("description", "IllegalArgumentException in "+FUNC_TAG+" > "+ex.toString());
//	    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//				Log.e(TAG, "Prediction >"+ex.toString());
//			}
//			catch (ArrayIndexOutOfBoundsException ex) {
//	    		Bundle b = new Bundle();
//	    		b.putString("description", "ArrayIndexOutOfBoundsException in "+FUNC_TAG+" > "+ex.toString());
//	    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//				Log.e(TAG, "Prediction >"+ex.toString());
//			}
//			
//	  		// If we executed the Kalman Filter update Xi0, tXi0
//	  		if (kf_loop_count > 0) {
//	  			state_data.tXi0=tX_mins;
//	  			for (ii=0; ii<8; ii++) {
//	  				state_data.Xi0[ii]=XI.get(ii, 0);
//	  			}
//	  		}
//		  	
//		  	// Compute risks and Brake Action
//		  	if (Gpred <= 112.5) {
//		  		Risk=10*Math.pow((brakes_param.risk.a* (Math.pow( Math.log(Gpred), brakes_param.risk.b)-brakes_param.risk.c)), 2);
//		  	}
//		  	else {
//		  		Risk = 0.0;
//		  	}
//		  	if (Gpred <= 140.0) {
//		  		RiskEX=10*Math.pow((brakes_param.risk.a_ex* (Math.pow( Math.log(Gpred), brakes_param.risk.b_ex)-brakes_param.risk.c_ex)), 2);
//		  	}
//		  	else {
//		  		RiskEX = 0.0;
//		  	}
//			state_data.Gpred_light = Gpred_light;
//			state_data.Gpred_1h = Gpred_1h;
//		  	BrakeAction = 1.0/(1.0 + 2.5*(exercise_level*RiskEX + (1.0-exercise_level)*Risk));
//		  	state_data.Gpred = Gest;
//		  	state_data.Gbrakes = Gpred;
//		  	state_data.Gest = Gest;
//		  	state_data.BrakeAction = BrakeAction;
//		  	state_data.Risk = Risk;
//		  	state_data.RiskEX = RiskEX;
//		  	state_data.risky = exercise_level*RiskEX + (1.0-exercise_level)*Risk;
//		  	state_data.tXi0 = tX_mins;
//		  	Debug.i(TAG, FUNC_TAG, "Gpred="+state_data.Gpred+", Gbrakes="+state_data.Gbrakes+", Gpred_light="+state_data.Gpred_light+", Gest="+state_data.Gest);
//		  	Debug.i(TAG, FUNC_TAG, "BrakesAction="+state_data.brakes_coeff+", tXi0="+state_data.tXi0);
//		  	Debug.i(TAG, FUNC_TAG, "Risk="+state_data.Risk+", RiskEX="+state_data.RiskEX+", risky="+state_data.risky+", Gpred_1h="+state_data.Gpred_1h);
//		  	// Save values for User Table 1
//		  	Gest_user1 = Gest;
//		  	BrakeAction_user1 = BrakeAction;
//		  	XI0_user1 = XI.get(0, 0);
//		  	XI1_user1 = XI.get(1, 0);
//		  	XI2_user1 = XI.get(2, 0);
//		  	XI3_user1 = XI.get(3, 0);
//		  	XI4_user1 = XI.get(4, 0);
//		  	XI5_user1 = XI.get(5, 0);
//		  	XI6_user1 = XI.get(6, 0);
//		  	XI7_user1 = XI.get(7, 0);
//		  	Gpred_user1 = Gpred;
//		  	Gpred_light_user1 = Gpred_light;
//		  	Gpred_1h_user1 = Gpred_1h;
//		  	
//			//storeUserTableData(Biometrics.USER_TABLE_3_URI, time, Gest_user1, BrakeAction_user1, XI0_user1, XI1_user1, XI2_user1, XI3_user1, XI4_user1, XI5_user1, XI6_user1, XI7_user1, Gpred_user1, Gpred_light_user1, Gpred_1h_user1, 0.0, 0.0, 0.0);
//		}
//		catch (Exception e) {
// 			Bundle b = new Bundle();
// 			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
// 			Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
//		}	
//	}
	
	public void meal_informed_state_estimation_new(	SSM_state_data state_data,
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
		
		
//		debug_message(TAG,"KF inputs>>>>>>>>>>");
//		int i;
//		for(i=0;i<inputs.steps;i++){debug_message(TAG, "Ik"+"["+i+"]="+kf_processing.kf.KFuall[0][i]+"\n");}
//		for(i=0;i<inputs.steps;i++){debug_message(TAG, "Rak"+"["+i+"]="+kf_processing.kf.KFuall[1][i]+"\n");}
//		for(i=0;i<inputs.steps;i++){debug_message(TAG, "dCGM"+"["+i+"]="+kf_processing.kf.KFuall[2][i]+"\n");}
//
//		
//		debug_message(TAG,"KF outputs>>>>>>>>>");
//		
//
//		for(i=0;i<inputs.steps;i++){debug_message(TAG, "Ge"+"["+i+"]="+kf_processing.kf.KFout[0][i]+"\n");}
//		for(i=0;i<inputs.steps;i++){debug_message(TAG, "Xe"+"["+i+"]="+kf_processing.kf.KFout[1][i]+"\n");}
//		
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


		// generate hypolight
//		double thres=70;
//		if (kf_processing.Gpred_light<thres){
//			HypoLight =2;
//		}else{
//			if (kf_processing.BrakeAction<1){
//				HypoLight = 1;
//			}else{
//				HypoLight = 0;
//			}
//		}
//
//		debug_message(TAG,"HypoLight: "+HypoLight);

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
	  	
	  	// Save values for User Table 1
//	  	Gest_user1 = kf_processing.Gest;
//	  	BrakeAction_user1 = kf_processing.BrakeAction;
//	  	XI0_user1 = kf_processing.PREDstate_init[0][0];
//	  	XI1_user1 = kf_processing.PREDstate_init[1][0];
//	  	XI2_user1 = kf_processing.PREDstate_init[2][0];
//	  	XI3_user1 = kf_processing.PREDstate_init[3][0];
//	  	XI4_user1 = kf_processing.PREDstate_init[4][0];
//	  	XI5_user1 = kf_processing.PREDstate_init[5][0];
//	  	XI6_user1 = kf_processing.PREDstate_init[6][0];
//	  	XI7_user1 = kf_processing.PREDstate_init[7][0];
//	  	Gpred_user1 = kf_processing.Gpred;
//	  	Gpred_light_user1 = kf_processing.Gpred_light;
//	  	Gpred_1h_user1 = kf_processing.Gpred_1h;
		//storeUserTableData(Biometrics.USER_TABLE_3_URI, time, Gest_user1, BrakeAction_user1, XI0_user1, XI1_user1, XI2_user1, XI3_user1, XI4_user1, XI5_user1, XI6_user1, XI7_user1, Gpred_user1, Gpred_light_user1, Gpred_1h_user1, 0.0, 0.0, 0.0);
		

	}
	
	
	public double calculate_deltaweighted(Inputs inputs, double Dma1,double Dma2){
		double returnvalue=0;
		
		Long mealtime= (long) 0;
		Long hypotime= (long) 0;
		if (inputs.Tvec_meal.count()>=1){
			mealtime = inputs.Tvec_meal.get_last_time();
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
	
	public double[] simple_regression(double[] x, double[] y) {
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
		


	
	public boolean classify(Subject subject_data, SSM_state_data state_data, SSM_flag_param flag_param, int cycle_duration_mins, long time) 
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
	
	public int hypo_light(
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
			// Set hypo stoplight color and SSM state
			debug_message(TAG_HYPO_LIGHT, "state_data.SSM_amount="+state_data.SSM_amount);
			debug_message(TAG_HYPO_LIGHT, "state_data.Umax="+state_data.Umax);
			debug_message(TAG_HYPO_LIGHT, "state_data.Usugg="+state_data.Usugg);
			debug_message(TAG_HYPO_LIGHT, "subject.basal="+subject_data.basal);
			debug_message(TAG_HYPO_LIGHT, "state_data.risky="+state_data.risky);
			debug_message(TAG_HYPO_LIGHT, "state_data.Abrakes="+state_data.Abrakes);
			debug_message(TAG_HYPO_LIGHT, "state_data.isMealBolus="+state_data.isMealBolus);
	        // Basal insulin is only added when the bolus request is not asynchronous - i.e. when it arrives at a regular timer tick
	        double add_basal;
	        if (asynchronous)
	        		add_basal = 0;
	        else
	        		add_basal = 1;
	        
	        // Set stoplight and Tvec_state value based upon meal bolus interception...
/*	        
			if (state_data.isMealBolus) {
				// We only get here upon returning from bolus intercept screen
				if (state_data.SSM_amount > state_data.Umax) {
					debug_message(TAG_HYPO_LIGHT, "HYPORED > meal bolus and SSM_amount > Umax > SSM_amount="+state_data.SSM_amount+", Umax="+state_data.Umax);
					debug_message(TAG_HYPO_LIGHT, "hypo_light > Meal Bolus > state_data.stoplight = Safety.RED_LIGHT");
					state_data.stoplight = Safety.RED_LIGHT;
				}
				else {
					debug_message(TAG_HYPO_LIGHT, "hypo_light > No Bolus > state_data.stoplight = Safety.GREEN_LIGHT");
					state_data.stoplight=Safety.GREEN_LIGHT;
				}
			}
			// ...or the reduction of basal rate
			else if (state_data.SSM_amount < (cycle_duration*state_data.Usugg+subject_data.basal*add_basal/(60.0/cycle_duration)) || state_data.risky > 0.0) {
				state_data.stoplight=Safety.YELLOW_LIGHT;
			}
			// else it's a green light
			else {
				state_data.stoplight=Safety.GREEN_LIGHT;
			}
*/			
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
					debug_message(TAG_HYPO_LIGHT, "HYPORED > hypo_light > state_data.stoplight=Safety.RED_LIGHT > 2");
					debug_message(TAG_HYPO_LIGHT, "HYPORED > Gpred_light="+state_data.Gpred_light+", CGM="+state_data.CGM+", thresG="+hypo_alarm.thresG);
					state_data.stoplight=Safety.RED_LIGHT;
					if (DIAS_STATE == DIAS_STATE_CLOSED_LOOP || DIAS_STATE == DIAS_STATE_SAFETY_ONLY) {
						state_data.SSM_amount = 0.0;    // No insulin injected during red lights.
						state_data.basal_added = 0.0;	// No insulin injected during red lights.
						Debug.i(TAG, FUNC_TAG, "red light 1 > basal_added="+state_data.basal_added);
					}
	                log_action(TAG, "hypo_light="+state_data.stoplight+" > outside cal window > CalFlagTime="+CalFlagTime+", width="+hypo_alarm.calibration_window_width, time);
	                
				}
			}
			else {
				if (state_data.CGM < hypo_alarm.thresG) {
					debug_message(TAG_HYPO_LIGHT, "hypo_light > state_data.stoplight=Safety>RED_LIGHT > 3");
					state_data.stoplight=Safety.RED_LIGHT;
					if (DIAS_STATE == DIAS_STATE_OPEN_LOOP || DIAS_STATE == DIAS_STATE_CLOSED_LOOP || DIAS_STATE == DIAS_STATE_SAFETY_ONLY) {
						state_data.SSM_amount = 0.0;    // No insulin injected during red lights.
						state_data.basal_added = 0.0;	// No insulin injected during red lights.
						Debug.i(TAG_HYPO_LIGHT, FUNC_TAG, "red light 2 > basal_added="+state_data.basal_added);
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

	
	private double clamp(double dvalue) {
		final String FUNC_TAG = "clamp";
		if (dvalue > 0.0) {
			return dvalue;
		}
		else {
			return 0.0;
		}
	}
	
	public boolean dbl_compare(double a, double b) {
		final String FUNC_TAG = "dbl_compare";
		if (Math.abs(a-b) < 1e-7) {
			return true;
		}
		return false;
	}

	private static void debug_message(String tag, String message) {
		final String FUNC_TAG = "debug_message";
		if (DEBUG_MODE) {
			Log.i(tag, message);
		}
	}
	
	public void log_action(String service, String action, long time) {
		final String FUNC_TAG = "log_action";
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("time", time);
        calling_context.sendBroadcast(i);
	}
	
	// This method causes the spend_request to be stored in the insulincredit table
	// by associating it with a credit entry which is the same size or larger.
	public void storeSpentInsulin(double spent_value, long current_time_seconds) {
		final String FUNC_TAG = "storeSpentInsulin";
		try {
			// Fetch credit pool data for the last hour from the database
			Tvector Tvec_credit = new Tvector(TVEC_SIZE);
			Tvector Tvec_spent = new Tvector(TVEC_SIZE);
			Tvector Tvec_net = new Tvector(TVEC_SIZE);
			Tvector Tvec_row_id = new Tvector(TVEC_SIZE);
			Long time_one_hour_ago_seconds = new Long(current_time_seconds - 3600*3);
			Cursor c=calling_context.getContentResolver().query(INSULIN_CREDIT_URI, null, time_one_hour_ago_seconds.toString(), null, null);
			debug_message(TAG_CREDITPOOL, "SSM_processing > storeSpentInsulin > INSULIN_CREDIT_URI rows in last hour="+c.getCount());
			if (c.moveToFirst()) {
				do{
					try {
						Tvec_row_id.put(c.getLong(c.getColumnIndex("time")), (double)c.getInt(c.getColumnIndex("_id")));
						Tvec_credit.put(c.getLong(c.getColumnIndex("time")), (double)c.getDouble(c.getColumnIndex("credit")));
						Tvec_spent.put(c.getLong(c.getColumnIndex("time")), (double)c.getDouble(c.getColumnIndex("spent")));
						Tvec_net.put(c.getLong(c.getColumnIndex("time")), (double)c.getDouble(c.getColumnIndex("net")));
					}
					catch (Exception e) {
			    		Bundle b = new Bundle();
			    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
			    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
						debug_message(TAG_CREDITPOOL, "SSM_processing > storeSpentInsulin > Error reading INSULIN_CREDIT_URI");
					}
				} while (c.moveToNext());
			}
			c.close();
			Tvec_row_id.dump(TAG_CREDITPOOL, "SSM_processing > storeSpentInsulin > Tvec_row_id > before");
			Tvec_credit.dump(TAG_CREDITPOOL, "SSM_processing > storeSpentInsulin > Tvec_credit > before");
			Tvec_spent.dump(TAG_CREDITPOOL, "SSM_processing > storeSpentInsulin > Tvec_spent > before");
			Tvec_net.dump(TAG_CREDITPOOL, "SSM_processing > storeSpentInsulin > Tvec_net > before");
			
			// Update Tvec_spent and Tvec_net
			int ii = 0;
			int ii_count = Tvec_row_id.count();
			double spent_value_accum = spent_value;
			double net_value_ii, spent_value_ii;
			debug_message(TAG_CREDITPOOL,"SSM_processing > storeSpentInsulin > ii_count="+ii_count+", spent_value_accum="+spent_value_accum);
			while (ii < ii_count && spent_value_accum > EPSILON) {
				net_value_ii = Tvec_net.get_value(ii);
				spent_value_ii = Tvec_spent.get_value(ii);
				if (net_value_ii >= spent_value_accum) {
					// There is enough net credit in this element to satisfy the remaining spent_value_accum
					Tvec_spent.replace_value(spent_value_ii+spent_value_accum, ii);
					Tvec_net.replace_value(net_value_ii-spent_value_accum, ii);
					spent_value_accum = 0.0;
				}
				else if (net_value_ii > 0) {
					// There is some net credit left in this element which can be used to reduce spent_value_accum
					Tvec_spent.replace_value(spent_value_ii+net_value_ii, ii);
					Tvec_net.replace_value(0.0, ii);
					spent_value_accum = spent_value_accum - net_value_ii;
				}
				ii++;
			}
			// Verify that all of spent_value has been assigned to Tvec_spent
			if (spent_value_accum > EPSILON) {
				// Error condition!
				Log.e(TAG, "storeSpentInsulin > Error: Not enough credit to store spent insulin!");
				log_action(TAG, "storeSpentInsulin > Error: Not enough credit to store spent insulin!", current_time_seconds);
			}
			Tvec_row_id.dump(TAG_CREDITPOOL, "SSM_processing > storeSpentInsulin > Tvec_row_id > after");
			Tvec_credit.dump(TAG_CREDITPOOL, "SSM_processing > storeSpentInsulin > Tvec_credit > after");
			Tvec_spent.dump(TAG_CREDITPOOL, "SSM_processing > storeSpentInsulin > Tvec_spent > after");
			Tvec_net.dump(TAG_CREDITPOOL, "SSM_processing > storeSpentInsulin > Tvec_net > after");
					
			// Write the credit pool information back to the database
			int row_id;
			long row_time;
			ContentValues values = new ContentValues();
			ii_count = Tvec_row_id.count();
			for (ii=0; ii<ii_count; ii++) {
				row_id = (int)Math.round(Tvec_row_id.get_value(ii));
				values.put("_id", row_id);
				values.put("time", Tvec_row_id.get_time(ii));
				values.put("credit", Tvec_credit.get_value(ii));
				values.put("spent", Tvec_spent.get_value(ii));				
				values.put("net", Tvec_net.get_value(ii));
				debug_message(TAG_CREDITPOOL, "SSM_processing > storeSpentInsulin > row_id="+row_id+", time="+values.getAsString("time")+
						", credit="+values.getAsString("credit")+
						", spent="+values.getAsString("spent")+
						", net="+values.getAsString("net"));
			    try {
					calling_context.getContentResolver().update(INSULIN_CREDIT_URI, values, "_id="+row_id, null);
			    }
			    catch (Exception e) {
		    		Bundle b = new Bundle();
		    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
		    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
			    	log_action(TAG, "SSM_processing > storeSpentInsulin > Error writing INSULIN_CREDIT_URI with _id="+row_id, current_time_seconds);
					Log.e(TAG, e.toString());
			    }
			}
		}
		catch (Exception e) {
			Bundle b = new Bundle();
			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
			Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}	
	}

	private void storePreauthorizedInsulin(boolean asynchronous, long lastBolusSimulatedTime, double pre_authorized, double credit, long currentTime) {
		final String FUNC_TAG = "storePreauthorizedInsulin";
		//
		//  Store the pre_authorized insulin in the database
		//
	    ContentValues values = new ContentValues();
	    if (asynchronous) {
		    values.put("time", lastBolusSimulatedTime);
	    }
	    else {
	    		values.put("time", currentTime);
	    }
	    	values.put("spent", pre_authorized);
	    	values.put("credit", credit);
	    try {
	    	Uri uri = calling_context.getContentResolver().insert(INSULIN_CREDIT_URI, values);
	    }
	    catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
	    	Log.e("Error",(e.toString() == null) ? "null" : e.toString());
	    }
	}
	
	public boolean isCurrentlyExercising(){
	    
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
		
	public long getCurrentTimeSeconds() {
		return (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970		
	}
	
	public void storeUserTableData(	Uri user_table,
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
		Uri uri;
		try {
			uri = calling_context.getContentResolver().insert(user_table, values);
		}
		catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(calling_context, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
			Log.e(TAG, e.toString());
		}		
	}

}
