package edu.virginia.dtc.SysMan;

public class FSM {
	
	//Different machine type identifiers
	public static final int MACHINE_SYNC = 0;
	public static final int MACHINE_ASYNC = 1;
	public static final int MACHINE_TBR = 2;
	public static final int MACHINE_DEV = 3;
	
	//Device States
	public static final int DEV_NA			= 0;
	public static final int DEV_WAKE		= 1;
	public static final int DEV_DISCON		= 2;

	//CALL STATES
	public static final int IDLE				= 0;
	public static final int START				= 1;
	public static final int WAKE				= 2;
	public static final int WAKE_RESPONSE		= 3;
	public static final int SSM_CALC_CALL		= 4;
	public static final int SSM_CALC_RESPONSE 	= 5;
	public static final int APC_CALL			= 6;
	public static final int APC_RESPONSE		= 7;
	public static final int BRM_CALL			= 8;
	public static final int BRM_RESPONSE		= 9;
	
	public static final int MCM_REQUEST			= 10;
	public static final int MCM_CANCEL			= 11;

	public static final int SSM_CALL			= 12;
	public static final int SSM_RESPONSE		= 13;
	public static final int PUMP_RESPONSE		= 14;
	public static final int TBR_CALL			= 15;	
	public static final int TBR_RESPONSE		= 16;
	public static final int BREAK				= 17;
	public static final int BREAK_RESPONSE		= 18;
	
	public static final int WAIT				= 19;
	
	public static boolean isSSMbusy(int state)
	{
		if(state == SSM_CALL || state == SSM_RESPONSE || state == SSM_CALC_CALL || state == SSM_CALC_RESPONSE)
			return true;
		else
			return false;
	}
	
	public static String devStateToString(int state)
	{
		switch(state)
		{
			case DEV_NA: return "N/A";
			case DEV_WAKE: return "Waking!";
			case DEV_DISCON: return "Breaking!";
		}
		
		return "Unknown";
	}
	
	public static String callStateToString(int mode)
	{
		switch(mode)
		{
			case IDLE: return "Idle";
			case START: return "Start";
			case WAKE: return "Wake";
			case WAKE_RESPONSE: return "Wake Response";
			case APC_CALL: return "APC Call";
			case APC_RESPONSE: return "APC Response";
			case BRM_CALL: return "BRM Call";
			case BRM_RESPONSE: return "BRM Response";
			case MCM_REQUEST: return "MCM Request";
			case SSM_CALL: return "SSM Call";
			case SSM_RESPONSE: return "SSM Response";
			case SSM_CALC_CALL: return "SSM Calc Call";
			case SSM_CALC_RESPONSE: return "SSM Calc Response";
			case PUMP_RESPONSE: return "Pump Response";
			case TBR_CALL: return "TBR Call";
			case TBR_RESPONSE: return "TBR Response";
			case BREAK: return "Break";
			case BREAK_RESPONSE: return "Break Response";
			default: return "Unknown: "+mode;
		}
	}
}
