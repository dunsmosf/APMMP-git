package edu.virginia.dtc.SysMan;

public class Meal {
 	
 	// Meal Activity Bolus Calculation Modes
 	public static final int MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS = 0;
 	public static final int MEAL_ACTIVITY_CALCULATES_BOLUS_PUMP_MODE = 1;
 	public static final int MEAL_ACTIVITY_NEVER_CALCULATES_BOLUS = 2;
 	
 	//MCM Meal Status values
 	public static final int MEAL_STATUS_PENDING = 0;
 	public static final int MEAL_STATUS_APPROVED = 1;
 	public static final int MEAL_STATUS_ABORTED = 2;
 	public static final int MEAL_STATUS_APPROVAL_TIMEOUT = 3;
 	
 	// MCM Message Definitions
 	public static final int REGISTER = 1;
 	public static final int UI_STARTED = 2;
 	public static final int SSM_CALC_DONE = 3;
 	public static final int INJECT = 4;
 	public static final int UI_CLOSED = 5;
 	public static final int UI_CHANGE = 6;
 	public static final int UI_REGISTER = 7;
 	public static final int MCM_CALCULATED = 8;
 	public static final int MCM_BOLUS = 9;
}
