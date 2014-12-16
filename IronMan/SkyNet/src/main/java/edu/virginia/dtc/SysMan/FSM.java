package edu.virginia.dtc.SysMan;

public class FSM
{
    // MACHINE TYPES
    public static final int SCAN        = 0;
    public static final int ALGORITHM   = 1;
    public static final int MEAL        = 2;
    public static final int TBR         = 3;

    // STATES - USED BY ALL
    public static final int IDLE        = 0;
    public static final int ERROR       = 1;

    // STATES - SCAN
    public static final int PING_APC    = 10;
    public static final int PING_BRM    = 11;
    public static final int PING_SSM    = 12;
    public static final int PING_MCM    = 13;

    // STATES - ALGORITHM
    public static final int SSM_UPDATE  = 20;
    public static final int APC_PROCESS = 21;
    public static final int BRM_PROCESS = 22;
    public static final int SSM_PROCESS = 23;





    /*
	public static final int IDLE				= 0;
	public static final int START				= 1;
	public static final int SSM_UPDATE		    = 2;
	public static final int SSM_UPDATE_RESPONSE = 3;
	public static final int APC_CALL			= 4;
	public static final int APC_RESPONSE		= 5;
	public static final int BRM_CALL			= 6;
	public static final int BRM_RESPONSE		= 7;
	
	public static final int MCM_REQUEST			= 8;
	public static final int MCM_CANCEL			= 9;

	public static final int SSM_CALL			= 10;
	public static final int SSM_RESPONSE		= 11;
	public static final int PUMP_RESPONSE		= 12;
	public static final int TBR_CALL			= 13;
	public static final int TBR_RESPONSE		= 14;
	*/


}
