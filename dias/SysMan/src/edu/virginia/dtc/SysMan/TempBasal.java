package edu.virginia.dtc.SysMan;

public class TempBasal {
	
 	// Temporary Basal Rate status Codes
 	public static final int TEMP_BASAL_RUNNING = 1;					// Temp basal running now
 	public static final int TEMP_BASAL_DURATION_EXPIRED = 2;		// Temp Basal time runs out
 	public static final int TEMP_BASAL_MANUAL_CANCEL = 3;			// User presses "Stop Temp Basal"
 	public static final int TEMP_BASAL_SYSTEM_CANCEL = 4;			// e.g. System transitions to Stopped mode
 	 	
 	// Temporary Basal Rate Owner Codes
 	public static final int TEMP_BASAL_OWNER_DIASSERVICE = 1;		// DiAsService owns control of temp basal
 	public static final int TEMP_BASAL_OWNER_APCSERVICE = 2;		// APCservice owns control of temp basal
 	public static final int TEMP_BASAL_OWNER_SSMSERVICE = 3;
 	
 	// Temporary Basal Activity Command Codes
 	public static final int TEMP_BASAL_START = 1;					// Tell APC Activity to start temp basal
 	public static final int TEMP_BASAL_CANCEL = 2;					// Tell APC Activity to cancel temp basal
 	
 	// Temporary Basal Activity Availability
 	public static final int MODE_NOT_AVAILABLE = 0;
 	public static final int MODE_AVAILABLE_PUMP = 1;
 	public static final int MODE_AVAILABLE_CL = 2;
 	public static final int MODE_NAVAILABLE_PUMP_AND_CL = 3;
 	
}
