package edu.virginia.dtc.SysMan;

public class APC {
	
	public static final int MODE_NONE_INSTALLED 	= 0;
	public static final int MODE_APC_INSTALLED 		= 1;
	public static final int MODE_BRM_INSTALLED 		= 2;
	public static final int MODE_APC_BRM_INSTALLED 	= 3;
	
	public static String modeToString(int mode)
	{
		switch(mode)
		{
			case MODE_NONE_INSTALLED:
				return "None Installed";
			case MODE_APC_INSTALLED:
				return "APC Installed";
			case MODE_BRM_INSTALLED:
				return "BRM Installed";
			case MODE_APC_BRM_INSTALLED:
				return "APC and BRM Installed";
			default:
				return "Unknown";
		}
	}
}
