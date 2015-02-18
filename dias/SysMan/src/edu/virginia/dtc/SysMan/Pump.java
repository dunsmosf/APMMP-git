package edu.virginia.dtc.SysMan;

public class Pump {

	public static double EPSILON = 0.000001;
	
	public static final int TYPE_SYNC 		= 1;
	public static final int TYPE_ASYNC 		= 2;
	public static final int TYPE_MANUAL		= 3;
	
	// Bolus status static variables
	public static final int PENDING = 0;
	public static final int DELIVERING = 1;
	public static final int DELIVERED = 2;
	public static final int CANCELLED = 3;
	public static final int INTERRUPTED = 4;
	public static final int INVALID_REQ = 5;
	public static final int MANUAL = 20;
	public static final int PRE_MANUAL = 21;
	
	public static String statusToString(int status)
	{
		switch(status)
		{
			case PENDING:		return "Pending";
			case DELIVERING:	return "Delivering";
			case DELIVERED:		return "Delivered";
			case CANCELLED:		return "Cancelled";
			case INTERRUPTED:	return "Interrupted";
			case INVALID_REQ:	return "Invalid Request";
			case MANUAL:		return "Manual";
			case PRE_MANUAL:	return "Pre-manual";
			default:			return "Unknown";
		}
	}
	
	// Pump Service States
	//--------------------------------------------------------
	public static final int PUMP_STATE_PUMP_ERROR = -1;
    public static final int PUMP_STATE_COMMAND_ERROR = -2;
    public static final int PUMP_STATE_NO_RESPONSE = -3;
    public static final int PUMP_STATE_IDLE = 1;				//Normal state
    
    public static final int PUMP_STATE_DELIVER = 2;				//Delivering a bolus
    public static final int PUMP_STATE_ACC = 3;					//Accumulating (Zero or too small)
    
    public static final int PUMP_STATE_CMD_TIMEOUT = 4;			
    public static final int PUMP_STATE_DELIVER_TIMEOUT = 5;
    
    public static final int PUMP_STATE_COMPLETE = 6;
    
    public static final int PUMP_STATE_SET_TBR = 7;				//Set the TBR
    public static final int PUMP_STATE_TBR_COMPLETE = 8;		//Command complete
    public static final int PUMP_STATE_TBR_TIMEOUT = 9;			//TBR timed out
    public static final int PUMP_STATE_TBR_DISABLED = 10;
    //--------------------------------------------------------
    
    // Pump connectivity states
    public static final int DISCONNECTED = -2;
    public static final int RECONNECTING = -1;
    public static final int NONE = 0;
    public static final int REGISTERED = 1;
    public static final int CONNECTING = 2;
    public static final int CONNECTED = 3;
    public static final int CONNECTED_LOW_RESV = 4;
    
	// Pump Service commands embedded in Intents from SafetyService
	public static final int PUMP_SERVICE_CMD_NULL = 0;
	public static final int PUMP_SERVICE_CMD_START_SERVICE = 1;
	public static final int PUMP_SERVICE_CMD_REGISTER_CLIENT = 2;
	public static final int PUMP_SERVICE_CMD_DELIVER_BOLUS = 3;
	public static final int PUMP_SERVICE_CMD_REQUEST_PUMP_STATUS = 5;
	public static final int PUMP_SERVICE_CMD_REQUEST_PUMP_HISTORY = 6;
	public static final int PUMP_SERVICE_CMD_STOP_SERVICE = 7;
	public static final int PUMP_SERVICE_CMD_SET_HYPO_TIME = 8;
	public static final int PUMP_SERVICE_CMD_INIT = 9;
	public static final int PUMP_SERVICE_CMD_DISCONNECT = 10;
	public static final int PUMP_SERVICE_CMD_SET_TBR = 11;
    
    // Commands to Pump Service
 	public static final int DRIVER2PUMP_SERVICE_PARAMETERS = 0;
 	public static final int DRIVER2PUMP_SERVICE_STATUS_UPDATE = 1;
 	public static final int DRIVER2PUMP_SERVICE_RESERVOIR = 2;
 	public static final int DRIVER2PUMP_SERVICE_BOLUS_COMMAND_ACK = 3;
 	public static final int DRIVER2PUMP_SERVICE_BOLUS_DELIVERY_ACK = 4;
 	public static final int DRIVER2PUMP_SERVICE_USB_CONNECT = 5;
 	public static final int DRIVER2PUMP_SERVICE_USB_DISCONNECT = 6;
 	public static final int DRIVER2PUMP_SERVICE_QUERY_RESP = 7;
 	public static final int DRIVER2PUMP_SERVICE_TBR_RESP = 8;
 	public static final int DRIVER2PUMP_SERVICE_MANUAL_INSULIN = 10;
 	
 	// Commands for Pump Driver
 	public static final int PUMP_SERVICE2DRIVER_NULL = 0;
 	public static final int PUMP_SERVICE2DRIVER_REGISTER = 1;
 	public static final int PUMP_SERVICE2DRIVER_DISCONNECT = 2;
 	public static final int PUMP_SERVICE2DRIVER_FLAGS = 3;
 	public static final int PUMP_SERVICE2DRIVER_BOLUS = 4;
 	public static final int PUMP_SERVICE2DRIVER_QUERY = 5;
 	public static final int PUMP_SERVICE2DRIVER_TBR = 6;
 	
 	public static boolean isBusy(int state)
 	{
 		if(state == PUMP_STATE_IDLE || state == PUMP_STATE_COMPLETE || state == PUMP_STATE_CMD_TIMEOUT || 
 				state == PUMP_STATE_DELIVER_TIMEOUT || state == PUMP_STATE_TBR_DISABLED ||
 				state == PUMP_STATE_TBR_COMPLETE || state == PUMP_STATE_TBR_TIMEOUT)
 			return false;
 		return true;
 	}
 	
 	public static String serviceStateToString(int state)
 	{
 		switch(state)
 		{
	 		case PUMP_STATE_PUMP_ERROR: return "Pump Error";
	 		case PUMP_STATE_COMMAND_ERROR: return "Command Error";
	 		case PUMP_STATE_NO_RESPONSE: return "No Response";
	 		case PUMP_STATE_IDLE: return "Idle";
	 		case PUMP_STATE_DELIVER: return "Deliver";
	 		case PUMP_STATE_ACC: return "Accumulate";
	 		case PUMP_STATE_CMD_TIMEOUT: return "Command Timeout";
	 		case PUMP_STATE_DELIVER_TIMEOUT: return "Deliver Timeout";
	 		case PUMP_STATE_COMPLETE: return "Complete";
	 		case PUMP_STATE_SET_TBR: return "Set TBR";
	 		case PUMP_STATE_TBR_COMPLETE: return "TBR Complete";
	 		case PUMP_STATE_TBR_TIMEOUT: return "TBR Timeout";
	 		case PUMP_STATE_TBR_DISABLED: return "TBR Disabled";
	 		default: return "Unknown";
 		}
 	}
 	
    public static String stateToString(int state)
    {
    	switch(state)
    	{
	    	case DISCONNECTED: return "DISCONNECTED";
	    	case RECONNECTING: return "RECONNECTING";
	    	case NONE: return "NONE";
	    	case REGISTERED: return "REGISTERED";
	    	case CONNECTING: return "CONNECTING";
	    	case CONNECTED: return "CONNECTED";
	    	case CONNECTED_LOW_RESV: return "CONNECTED_LOW_RESV";
	    	default: return "UNKNOWN";
    	}
    }
    
    public static boolean isConnected(int state)
    {
    	switch(state)
    	{
    		case RECONNECTING:
	    	case CONNECTING:
	    	case CONNECTED:
	    	case CONNECTED_LOW_RESV:
	    		return true;
    		default:
    			return false;
    	}
    }
    
    public static boolean notConnected(int state)
    {
    	switch(state)
    	{
	    	case DISCONNECTED:
	    	case NONE:
	    		return true;
	    	default:
	    		return false;
    	}
    }
}
