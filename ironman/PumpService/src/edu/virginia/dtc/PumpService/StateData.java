//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.PumpService;

import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.Tvector.Tvector;

public class StateData {
	public int Processing_State;
	public Tvector Tvec_rate_requested_history;
	public Tvector Tvec_bolus_requested_history;
	public Tvector Tvec_insulin_delivered_history;
	public Tvector Tvec_pump_state_history;
	public double bolus_out;							// The bolus that is actually sent to the pump
	public double rem_error_meal;						// Remainder from previous meal bolus that must be added to current bolus
	public double rem_error_corr;						// Remainder from previous correction bolus that must be added to current bolus
	public double rem_error_basal;						// Remainder from previous basal bolus that must be added to current bolus
	public double basal_rate;							// Basal rate sent to the pump.  Only used when closed loop control is disabled.
	
	public StateData() {
		// Initialize needed values
		Processing_State = Pump.PUMP_STATE_IDLE;
		Tvec_rate_requested_history = new Tvector(2016);			// Stores up to 1 weeks' worth of state data at 5 minute intervals
		Tvec_bolus_requested_history = new Tvector(2016);			// 		...
		Tvec_insulin_delivered_history = new Tvector(2016);			// 		...
		Tvec_pump_state_history = new Tvector(2016);				// 		...
		bolus_out = 0;
		rem_error_meal = 0;
		rem_error_corr = 0;
		rem_error_basal = 0;
		basal_rate = 0;
	}
}
