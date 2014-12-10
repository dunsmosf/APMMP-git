package edu.virginia.dtc.SysMan;

public class State {

	// DiAs State Variable and Definitions - state for the system as a whole
	public static final int DIAS_STATE_STOPPED = 0;
	public static final int DIAS_STATE_OPEN_LOOP = 1;
	public static final int DIAS_STATE_CLOSED_LOOP = 2;
	public static final int DIAS_STATE_SAFETY_ONLY = 3;
	public static final int DIAS_STATE_SENSOR_ONLY = 4;
	public static final int DIAS_STATE_BRM = 5;
	
	public static String stateToString(int state)
	{
		switch(state)
		{
			case DIAS_STATE_STOPPED: 		return "STOPPED";
			case DIAS_STATE_OPEN_LOOP: 		return "OPEN LOOP";
			case DIAS_STATE_CLOSED_LOOP: 	return "CLOSED LOOP";
			case DIAS_STATE_SAFETY_ONLY:	return "SAFETY ONLY";
			case DIAS_STATE_SENSOR_ONLY: 	return "SENSOR ONLY";
			case DIAS_STATE_BRM: 			return "BRM";
			default: 						return "UNKNOWN";
		}
	}
}
