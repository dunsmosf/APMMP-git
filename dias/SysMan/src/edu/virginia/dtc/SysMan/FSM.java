package edu.virginia.dtc.SysMan;

public class FSM {
	
	//Controller Types
	public static final int APC = 1;
	public static final int BRM = 2;
	public static final int SSM = 3;
	public static final int MCM = 4;
	
	public static final int NONE = 0;
	public static final int APC_ONLY = 1;
	public static final int BRM_ONLY = 2;
	public static final int APC_BRM = 3;
	
	//Different machine type identifiers
	public static final int MACHINE_SYNC = 0;
	public static final int MACHINE_ASYNC = 1;
	public static final int MACHINE_TBR = 2;

	//Call States
	public static final int IDLE				= 0;
	public static final int START				= 1;
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
	
	public static final int WAIT				= 19;
	
	public static String configToString(int config)
	{
		switch(config)
		{
			case NONE: return "None";
			case APC_ONLY: return "APC Only";
			case BRM_ONLY: return "BRM Only";
			case APC_BRM: return "APC and BRM";
			default: return "Unknown";
		}
	}
	
	public static String callStateToString(int mode)
	{
		switch(mode)
		{
			case IDLE: return "Idle";
			case START: return "Start";
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
			default: return "Unknown: "+mode;
		}
	}
}
