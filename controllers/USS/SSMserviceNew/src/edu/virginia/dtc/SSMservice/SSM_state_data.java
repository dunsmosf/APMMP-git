//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.SSMservice;

import edu.virginia.dtc.SysMan.Safety;
import edu.virginia.dtc.Tvector.Tvector;

public class SSM_state_data {
	public int Processing_State;
	public Tvector Tvec_state;
	public double pre_authorized;
	public double sp_req_mem;
	public double RATE;							// Updated by BOLUS_assign
	public double BOLUS;
	public double BOLUS_original;
	public double Usugg;
	public double Uinterceptor;
	public double CF;							// Currently valid CF (correction factor)
	public double CR;							// Currently valid CR (carbohydrate ratio)
	public double basal;
	public double basal_added;					// This is the amount of insulin added as basal by the Safety System
	public double b;
	public boolean enough_data;					// Whether there is enough CGM data to use
	public boolean asynchronous;				// Whether a particular call to the Safety Service was asynchronous
	public double CGM;							// Most recent CGM value or -1.0 if none 
	public Tvector Tvec_ins_hist_IOB_with_meal_insulin_seconds;			// Insulin history for use in IOB calculation - meal insulin is included
	public Tvector Tvec_ins_hist_IOB_no_meal_insulin_seconds;			// Insulin history for use by bolus interceptor - no meal insulin included
	public long[] discrete_time_seconds;		// ssm_param.iob_param.hist_length in seconds spaced at
												// intervals of cycle_duration
	// New fields for meal-informed power brakes
	public double[][] Mbuf = {{0.0}, {0.0}};
	public double[][] Mstate = {{0.0}, {0.0}};
	public double[][] Ibuf = {{0.0}, {0.0}};
	public double[][] Istate = {{0.0}, {0.0}, {0.0}, {0.0}};
	public double[][] KFstate = {{0.0}, {0.0}};
	public double Gest;
	public double BrakeAction;
	public double Risk;
	public double RiskEX;
	public double Risky;
	
	public double IOB_meal;
	public double IOB_no_meal;
	public long IOB_time;
	public double asynchronous_insulin_IOB;
	public double Gpred;							
	public double Gbrakes;
	public double Gpred_light;
	public double Gpred_brakes;
	public double Gpred_bolus;
	public double Gpred_1h;
	public double CHOpred;
	public double[] Xi0 = {0, 0, 0, 0, 0, 0, 0, 0};
	public long tXi0;
	public double brakes_coeff;
	public boolean isMealBolus;
	public double risky;
	public double Abrakes;
	public double InsulinConstraintInUnits;
	public double SSM_amount;
	public double bolus_out;
	public int current_state;
	public int state_out;
	public int stoplight;
	public int stoplight2;
	public double Rate;
	public double rem_error;
	public double CGM_corr;
	
	// These values are SSM recommendations that are passed to the application when a bolus is intercepted
	public double Umax;
	public int CHOmin;
	public double advised_bolus;
	
	// These values are stored copies of parameters to SSM_processing.  They are needed when SSM_processing is interrupted
	// by a user confirmation and needs to resume later with consistent parameters.
	public int cycle_duration_mins;
	public long time;
	public boolean correction_bolus;
	public Tvector Tvec_cgm_mins;
	public long calFlagTime;
	public SSM_param ssm_param;
	
	// State data associated with meal informed state estimation
	

	public SSM_state_data(long time) {
		// Initialize needed values
		stoplight = Safety.UNKNOWN_LIGHT;
		stoplight2 = Safety.UNKNOWN_LIGHT;
		IOB_meal = 0.0;
		IOB_no_meal = 0.0;
		IOB_time = time;
		asynchronous_insulin_IOB = 0.0;
		pre_authorized = 0.0;
		Gpred = 0;
		Gbrakes = 0;
		Gpred_light = 0;
		Gpred_brakes = 0;
		Gpred_bolus = 0;
		Risk = 0;
		RiskEX = 0;
		Risky = 0;
		risky = 0;
		tXi0 = 0;											// This will force the KF to run using the last 45 minutes of data
		brakes_coeff = 1;
		Processing_State = Safety.SAFETY_SERVICE_STATE_NORMAL;
		state_out = 0;
//		CHOgrams = 0;
		Tvec_state = new Tvector(2016);		// Stores up to 1 weeks' worth of state data at 5 minute intervals
	}

}
