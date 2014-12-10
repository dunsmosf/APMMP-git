package edu.virginia.dtc.SysMan;

public class Safety {

	// SSMservice status static variables for traffic lights
	public static final int GREEN_LIGHT = 0;
	public static final int YELLOW_LIGHT = 1;
	public static final int RED_LIGHT = 2;
	public static final int UNKNOWN_LIGHT = 3;
	
	// safetyService commands
	public static final int SAFETY_SERVICE_CMD_NULL = 0;
	public static final int SAFETY_SERVICE_CMD_START_SERVICE = 1;
	public static final int SAFETY_SERVICE_CMD_REGISTER_CLIENT = 2;
	public static final int SAFETY_SERVICE_CMD_REQUEST_BOLUS= 4;
	public static final int SAFETY_SERVICE_CMD_CALCULATE_STATE= 5;
    
    // safetyService return status values
    public static final int SAFETY_SERVICE_STATE_NORMAL = 0;
    public static final int SAFETY_SERVICE_STATE_NOT_ENOUGH_DATA = 1;
    public static final int SAFETY_SERVICE_STATE_CREDIT_REQUEST = 2;
    public static final int SAFETY_SERVICE_STATE_BOLUS_INTERCEPT = 3;
    public static final int SAFETY_SERVICE_STATE_AWAITING_PUMP_RESPONSE = 4;
    public static final int SAFETY_SERVICE_STATE_BUSY = 5;
    public static final int SAFETY_SERVICE_STATE_PUMP_ERROR = -1;
    public static final int SAFETY_SERVICE_STATE_INVALID_COMMAND = -2;
	public static final int SAFETY_SERVICE_STATE_CALCULATE_RESPONSE = 6;
    
    // traffic light control (set in parameters.xml)
    public static final int TRAFFIC_LIGHT_CONTROL_DISABLED = 0;
    public static final int TRAFFIC_LIGHT_CONTROL_SSMSERVICE = 1;
    public static final int TRAFFIC_LIGHT_CONTROL_APCSERVICE = 2;
    public static final int TRAFFIC_LIGHT_CONTROL_BRMSERVICE = 3;
}
