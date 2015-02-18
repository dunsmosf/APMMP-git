package edu.virginia.dtc.SysMan;

public class Safety
{
    // SSMservice status static variables for traffic lights
    public static final int GREEN_LIGHT = 0;
    public static final int YELLOW_LIGHT = 1;
    public static final int RED_LIGHT = 2;
    public static final int UNKNOWN_LIGHT = 3;

    // Traffic light control (set in parameters.xml)
    public static final int TRAFFIC_LIGHT_CONTROL_DISABLED = 0;
    public static final int TRAFFIC_LIGHT_CONTROL_SSMSERVICE = 1;
    public static final int TRAFFIC_LIGHT_CONTROL_APCSERVICE = 2;
    public static final int TRAFFIC_LIGHT_CONTROL_BRMSERVICE = 3;
}
