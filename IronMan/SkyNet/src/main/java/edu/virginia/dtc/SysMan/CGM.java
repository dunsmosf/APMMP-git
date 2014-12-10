package edu.virginia.dtc.SysMan;

import java.text.DecimalFormat;

public class CGM {

	//CGM States
	public static final int CGM_NORMAL = 0;
	public static final int CGM_DATA_ERROR = 1;
	public static final int CGM_NOT_ACTIVE = 2;
	public static final int CGM_NONE = 3;
	public static final int CGM_NOISE = 4;
	public static final int CGM_WARMUP = 5;
	public static final int CGM_CALIBRATION_NEEDED = 6;
	public static final int CGM_DUAL_CALIBRATION_NEEDED = 7;
	public static final int CGM_CAL_LOW = 8;
	public static final int CGM_CAL_HIGH = 9;
	public static final int CGM_SENSOR_FAILED = 10;
	
	// Blood Glucose Display Units
	public static final int BG_UNITS_MG_PER_DL = 0;
	public static final int BG_UNITS_MMOL_PER_L = 1;
	
	public static final double MGDL_PER_MMOLL = 18.016; // Molar mass of glucose (180.1559 g/mol) divided by 10 (L to dL).
    
	public static final long CGM_MAX_DELAY_MINS = 20;
	public static final long CGM_MAX_AHEAD_MINS = -10;
	
	public static String stateToString(int state)
	{
		switch(state)
		{
			case CGM_NORMAL: return "NORMAL";
			case CGM_DATA_ERROR: return "DATA_ERROR";
			case CGM_NOT_ACTIVE: return "NOT_ACTIVE";
			case CGM_NONE: return "NONE";
			case CGM_NOISE: return "NOISE";
			case CGM_WARMUP: return "WARMUP";
			case CGM_CALIBRATION_NEEDED: return "CALIBRATION_NEEDED";
			case CGM_DUAL_CALIBRATION_NEEDED: return "DUAL_CALIBRATION_NEEDED";
			case CGM_CAL_LOW: return "CAL_LOW";
			case CGM_CAL_HIGH: return "CAL_HIGH";
			case CGM_SENSOR_FAILED: return "SENSOR_FAILED";
			default: return "UNKNOWN";
		}
	}
	
	public static String cgmToStringWithUnits(double cgm_value, int blood_glucose_display_unit) {
		
		String cgmString = "";
		DecimalFormat decimalFormat = new DecimalFormat();
		
		switch(blood_glucose_display_unit)
		{
			case BG_UNITS_MMOL_PER_L:
				decimalFormat.setMinimumFractionDigits(1);
	    		decimalFormat.setMaximumFractionDigits(1);
	    		cgmString = decimalFormat.format(cgm_value/MGDL_PER_MMOLL) + "mmol/L";
				break;
			
			case BG_UNITS_MG_PER_DL:
			default:
				decimalFormat.setMinimumFractionDigits(0);
	    		decimalFormat.setMaximumFractionDigits(0);
	    		cgmString = decimalFormat.format(cgm_value) + "mg/dL";
				break;
		}
		
		return cgmString;
	};
}
