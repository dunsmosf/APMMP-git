package edu.virginia.dtc.SysMan;

import android.content.ContentResolver;
import edu.virginia.dtc.Tvector.Tvector;

public class Mode {
	
	/**
	 * Allowed Modes Definition
	 */
	public static final int NONE_ALLOWED 					= 0;
	public static final int PUMP_ALLOWED 					= 1;
	public static final int SAFETY_ALLOWED 					= 2;
	public static final int PUMP_AND_SAFETY_ALLOWED			= 3;
	public static final int CLOSED_ALLOWED					= 4;
	public static final int PUMP_AND_CLOSED_ALLOWED			= 5;
	public static final int SAFETY_AND_CLOSED_ALLOWED 		= 6;
	public static final int PUMP_SAFETY_AND_CLOSED_ALLOWED 	= 7;
	
	/**
	 * Controllers Activity Definition
	 */
	public static final int CONTROLLER_DISABLED 				= 0;
	public static final int CONTROLLER_ENABLED 					= 1;
	public static final int CONTROLLER_ENABLED_WITHIN_PROFILE 	= 2;
	public static final int CONTROLLER_DISABLED_WITHIN_PROFILE 	= 3;
	
	
	
	/**
	 * Get the allowed modes Integer value from the parameters' setting
	 * @param resolver
	 * @return int allowed modes value
	 */
	public static int getMode(ContentResolver resolver)
	{
		return Params.getInt(resolver, "allowed_modes", NONE_ALLOWED);
	}
	
	
	/**
	 * Get the allowed modes description based on the parameters' Integer value.
	 * @param resolver
	 * @return string Description of the allowed modes
	 */
	public static String getModeString(ContentResolver resolver)
	{
		int mode = getMode(resolver);
		
		switch(mode) {
			case NONE_ALLOWED:						return "No Mode Allowed";
			case PUMP_ALLOWED:						return "Pump Mode Allowed";
			case SAFETY_ALLOWED:					return "Safety Mode Allowed";
			case PUMP_AND_SAFETY_ALLOWED:			return "Pump and Safety Modes Allowed";
			case CLOSED_ALLOWED:					return "Closed Loop Mode Allowed";
			case PUMP_AND_CLOSED_ALLOWED:			return "Pump and Closed Loop Modes Allowed";
			case SAFETY_AND_CLOSED_ALLOWED:			return "Safety and Closed Loop Modes Allowed";
			case PUMP_SAFETY_AND_CLOSED_ALLOWED:	return "Pump, Safety and Closed Loop Modes Allowed";
			default:								return "Unknown Mode";
		}
	}
	
	
	/**
	 * Get the Activity status of the APC controller
	 * @param resolver
	 * @return int Controller activity status value
	 */
	public static int getApcStatus(ContentResolver resolver)
	{
		return Params.getInt(resolver, "apc_enabled", CONTROLLER_DISABLED);
	}
	
	
	/**
	 * Get the Activity status of the BRM controller
	 * @param resolver
	 * @return int Controller activity status value
	 */
	public static int getBrmStatus(ContentResolver resolver)
	{
		return Params.getInt(resolver, "brm_enabled", CONTROLLER_DISABLED);
	}
	
	
	/**
	 * Indicate whether Pump Mode is allowed based on the parameters' allowed modes setting
	 * @param resolver
	 * @return boolean
	 */
	public static boolean isPumpModeAllowed(ContentResolver resolver)
	{
		int mode = getMode(resolver);
		
		switch(mode) {
			case PUMP_ALLOWED:
			case PUMP_AND_SAFETY_ALLOWED:
			case PUMP_AND_CLOSED_ALLOWED:
			case PUMP_SAFETY_AND_CLOSED_ALLOWED: return true;
			
			case NONE_ALLOWED:
			case SAFETY_ALLOWED:
			case CLOSED_ALLOWED:
			case SAFETY_AND_CLOSED_ALLOWED:
			default: return false;
		}
	}
	
	
	/**
	 * Indicate whether Safety Mode is allowed based on the parameters' allowed modes setting
	 * @param resolver
	 * @return boolean
	 */
	public static boolean isSafetyModeAllowed(ContentResolver resolver)
	{
		int mode = getMode(resolver);
		
		switch(mode) {
			case SAFETY_ALLOWED:
			case PUMP_AND_SAFETY_ALLOWED:
			case SAFETY_AND_CLOSED_ALLOWED:
			case PUMP_SAFETY_AND_CLOSED_ALLOWED: return true;
			
			case NONE_ALLOWED:
			case PUMP_ALLOWED:
			case CLOSED_ALLOWED:
			case PUMP_AND_CLOSED_ALLOWED:
			default: return false;
		} 
		
	}
	
	
	/**
	 * Indicate whether Closed Loop Mode is allowed based on the parameters' allowed modes setting
	 * @param resolver
	 * @return boolean
	 */
	public static boolean isClosedLoopAllowed(ContentResolver resolver)
	{
		int mode = getMode(resolver);
		
		switch(mode) {
			case CLOSED_ALLOWED:
			case PUMP_AND_CLOSED_ALLOWED:
			case SAFETY_AND_CLOSED_ALLOWED:
			case PUMP_SAFETY_AND_CLOSED_ALLOWED: return true;
			
			case NONE_ALLOWED:
			case PUMP_ALLOWED:
			case SAFETY_ALLOWED:
			case PUMP_AND_SAFETY_ALLOWED:
			default: return false;
		}
	}
	
	
	/**
	 * Indicates whether Pump Mode is available (allowed + available)
	 * @param resolver
	 * @return boolean
	 */
	public static boolean isPumpModeAvailable(ContentResolver resolver)
	{
		return isPumpModeAllowed(resolver);
	}
	
	
	/**
	 * Indicates whether Safety Mode is available (allowed + available)
	 * @param resolver
	 * @return boolean
	 */
	public static boolean isSafetyModeAvailable(ContentResolver resolver)
	{
		// TODO: Check for SSM availability here if it becomes a 'controller'.
		return isSafetyModeAllowed(resolver);
	}
	
	/**
	 * Indicates whether Safety Mode is available (allowed + available), this is a wrapper
	 * for Closed Loop availability
	 * @param resolver
	 * @param timeInMinutes
	 * @return boolean
	 */
	public static boolean isSafetyModeAvailable(ContentResolver resolver, int timeInMinutes)
	{
		return isClosedLoopAvailable(resolver, timeInMinutes);
	}
	
	
	/**
	 * Indicates whether Closed Loop Mode is available (allowed + available) at a given time
	 * @param resolver
	 * @param timeInMinutes
	 * @return boolean
	 */
	public static boolean isClosedLoopAvailable(ContentResolver resolver, int timeInMinutes)
	{
		// TODO: Also check for SSM availability here if it becomes a 'controller'.
		int apcStatus = getApcStatus(resolver);
		int brmStatus = getBrmStatus(resolver);
		boolean withinProfile = isInProfileRange(resolver, timeInMinutes);
		
		boolean available = 	apcStatus==CONTROLLER_ENABLED 	
							|| 	brmStatus==CONTROLLER_ENABLED
							||	apcStatus==CONTROLLER_ENABLED_WITHIN_PROFILE && withinProfile
							||	brmStatus==CONTROLLER_ENABLED_WITHIN_PROFILE && withinProfile
							||	apcStatus==CONTROLLER_DISABLED_WITHIN_PROFILE && !withinProfile
							||	brmStatus==CONTROLLER_DISABLED_WITHIN_PROFILE && !withinProfile
							;
		
		return isClosedLoopAllowed(resolver) && available;
	}
	
	
	/**
	 * Indicates whether the time value (in minutes) passed as an argument is within the USS BRM Profile range read from the Content Provider
	 * @param resolver
	 * @param timeInMinutes
	 * @return boolean
	 */
	public static boolean isInProfileRange(ContentResolver resolver, int timeInMinutes)
	{
		Tvector safetyRanges = new Tvector(12);
		if (Tvector.readTvector(safetyRanges, Biometrics.USS_BRM_PROFILE_URI, resolver)) {
			for (int i = 0; i < safetyRanges.count(); i++) 
			{
				int t = safetyRanges.get_time(i).intValue();
				int t2 = safetyRanges.get_end_time(i).intValue();
				
				if (t > t2)
				{ 					
					t2 += 24*60;
				}
				
				if ((t <= timeInMinutes && timeInMinutes <= t2) || (t <= (timeInMinutes + 1440) && (timeInMinutes + 1440) <= t2))
				{
					return true;
				}
			}
			return false;			
		}
		else {
			return false;
		}
	}
	
}
