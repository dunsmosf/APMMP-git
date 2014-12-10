package edu.virginia.dtc.SysMan;

public class Meal {
	
 	// Meal Activity commands
 	public static final int MEAL_ACTIVITY_REGISTER = -1;
 	public static final int MEAL_ACTIVITY_CALCULATE = -2;
 	public static final int MEAL_ACTIVITY_INJECT = -3;
 	
 	// Meal Activity Bolus Calculation Modes
 	public static final int MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS = 0;
 	public static final int MEAL_ACTIVITY_CALCULATES_BOLUS_PUMP_MODE = 1;
 	public static final int MEAL_ACTIVITY_NEVER_CALCULATES_BOLUS = 2;
 	
 	// MCM Service to Meal Activity
 	public static final int MCM_CALCULATED_BOLUS = 1;
 	public static final int MCM_OCAD_MEAL_ACTIVITY_CALCULATE = 3000;
 	public static final int ADVICE_BOLUS = 3001;

 	// MCM Service commands
  	public static final int MCM_SERVICE_CMD_REGISTER_CLIENT = 10;
 	public static final int MCM_SEND_BOLUS = 11;
 	public static final int MCM_STARTED = 12;
 	public static final int MCM_UI = 13;
 	
 	//MCM Meal Status values
 	public static final int MEAL_STATUS_PENDING = 0;
 	public static final int MEAL_STATUS_APPROVED = 1;
 	public static final int MEAL_STATUS_ABORTED = 2;
 	public static final int MEAL_STATUS_APPROVAL_TIMEOUT = 3;
}
