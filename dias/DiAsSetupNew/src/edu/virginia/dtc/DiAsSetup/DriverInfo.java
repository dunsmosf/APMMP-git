package edu.virginia.dtc.DiAsSetup;

public class DriverInfo
{
	// Types of devices used, new devices will be enumerated here
	public static final int CGM = 0;
	public static final int PUMP = 1;
	public static final int CGM_PUMP = 2;
	public static final int MISC = 3;
	
	public String name;
	public String displayname;
	public String package_name;
	public String service_name;
	public boolean started = false;
	public int type;
	
	public DriverInfo(String n, String d, String p, String s, int t)
	{
		name = n;
		displayname = d;
		package_name = p;
		service_name = s;
		type = t;
	}
	
	public String toString(){
		return name + " || " + package_name ;
	}
}