package edu.virginia.dtc.SysMan;

import android.content.ContentResolver;

public class Mode {
	
	public static final int NONE 							= 0;			//No operating modes
	public static final int OL_AVAILABLE 					= 1;			//Open Loop mode only
	public static final int CL_AVAILABLE					= 2;			//Closed Loop mode only
	public static final int OL_CL_AVAILABLE					= 3;			//Both Open Loop and Closed Loop available
	public static final int OL_ALWAYS_CL_NIGHT_AVAILABLE 	= 4;			//Open Loop always available, Closed Loop (BRM) available overnight only (by USS-BRM schedule)
	
	public static int getMode(ContentResolver resolver)
	{
		return Params.getInt(resolver, "mode", NONE);
	}
	
	public static String getModeString(ContentResolver resolver)
	{
		int mode = Mode.getMode(resolver);
		
		switch(mode)
		{
			case OL_AVAILABLE:					return "OL Available";
			case OL_CL_AVAILABLE:				return "OL and CL Available";
			case CL_AVAILABLE:					return "CL Available";
			case OL_ALWAYS_CL_NIGHT_AVAILABLE:	return "OL Always, CL Overnight";
			case NONE:							return "No Modes Available";
			default:							return "Unknown Mode";
		}
	}

	public static boolean isOLavailable(ContentResolver resolver)
	{
		int mode = Params.getInt(resolver, "mode", NONE);
		
		switch(mode)
		{
			case OL_AVAILABLE:
			case OL_CL_AVAILABLE:
			case OL_ALWAYS_CL_NIGHT_AVAILABLE:
				return true;
				
			case CL_AVAILABLE:
			case NONE:
			default:
				return false;
		}
	}
	
	public static boolean isCLavailable(ContentResolver resolver)
	{
		int mode = Params.getInt(resolver, "mode", NONE);
		
		switch(mode)
		{
			case CL_AVAILABLE:
			case OL_CL_AVAILABLE:
			case OL_ALWAYS_CL_NIGHT_AVAILABLE:
				return true;
				
			case OL_AVAILABLE:
			case NONE:
			default:
				return false;
		}
	}
}
