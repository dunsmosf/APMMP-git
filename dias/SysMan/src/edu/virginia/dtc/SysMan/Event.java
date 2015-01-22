package edu.virginia.dtc.SysMan;

import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;

public class Event{
	
	private static final String TAG = "Event";
	
	//Event codes
	public static final int EVENT_PUMP_TIME_ERROR 						= 1;
	public static final int EVENT_PUMP_STOPPED 							= 2;
	public static final int EVENT_PUMP_WARNING_ERROR 					= 3;
	public static final int EVENT_PUMP_LOW_RESERVOIR 					= 5;
	public static final int EVENT_PUMP_LOW_BATTERY 						= 6;
	public static final int EVENT_PUMP_MISSED_BOLUS 					= 7;
	public static final int EVENT_PUMP_DISCONNECT_WARN 					= 8;
	public static final int EVENT_PUMP_DISCONNECT_TIMEOUT 				= 9;
	public static final int EVENT_PUMP_TBR 								= 10;
	public static final int EVENT_MDI_INPUT								= 11;
	public static final int EVENT_PUMP_MISSED_THRES						= 12;
	public static final int EVENT_PUMP_PAIR								= 13;
	
	public static final int EVENT_NETWORK_TIMEOUT 						= 100;
	public static final int EVENT_NETWORK_SERVER_UNREACHABLE 			= 101;
	public static final int EVENT_NETWORK_DATA_ERROR 					= 102;
	
	public static final int EVENT_CGM_TIME_ERROR 						= 200;
	public static final int EVENT_CGM_WARN 								= 201;
	
	public static final int EVENT_SYSTEM_ERROR 							= 300;
	public static final int EVENT_SYSTEM_HYPO_ALARM 					= 301;
	public static final int EVENT_SYSTEM_NO_CGM_ALARM 					= 302;
	public static final int EVENT_SYSTEM_HYPER_ALARM					= 303;
	public static final int EVENT_SYSTEM_START 							= 304;
	public static final int EVENT_SYSTEM_HYPO_TREATMENT 				= 305;
	public static final int EVENT_SYSTEM_NO_HYPO_TREATMENT 				= 306;
	public static final int EVENT_SYSTEM_DATABASE_ACCESS_ERROR 			= 307;
	public static final int EVENT_SYSTEM_IO_TEST 						= 308;
	public static final int EVENT_SYSTEM_BATTERY						= 309;

	public static final int EVENT_SYSTEM_POWER_OFF						= 310;
	public static final int EVENT_SYSTEM_BOOT_COMPLETE					= 311;
	public static final int EVENT_SYSTEM_RECOVERY						= 312;
	public static final int EVENT_SYSTEM_HYPO 							= 313;
	public static final int EVENT_SYSTEM_TIME							= 314;
	public static final int EVENT_SYSTEM_INVALID_BOLUS					= 315;
	public static final int EVENT_SYSTEM_HYPER_MUTE						= 316;
	
	// Mode switching events
	public static final int EVENT_STOPPED_MODE 							= 320;
	public static final int EVENT_PUMP_MODE 							= 321;
	public static final int EVENT_CLOSED_LOOP_MODE 						= 322;
	public static final int EVENT_SAFETY_MODE 							= 323;
	public static final int EVENT_SENSOR_MODE 							= 324;
	public static final int EVENT_BEGIN_EXERCISE 						= 325;
	public static final int EVENT_END_EXERCISE 							= 326;
	
	public static final int EVENT_UNKNOWN_MODE 							= 339;
	
	public static final int EVENT_BASAL_PAUSED							= 340;
	public static final int EVENT_BASAL_RESUMED							= 341;
	
	public static final int EVENT_TEMP_BASAL_STARTED					= 342;
	public static final int EVENT_TEMP_BASAL_CANCELED					= 343;
	
	public static final int EVENT_BRM_PARAM_CHANGED                     = 344;
	
	// UI actions events
	public static final int EVENT_UI_HYPO_BUTTON_PRESSED 				= 350;
	
	public static final int EVENT_MEAL_ERROR 							= 400;
	public static final int EVENT_MCM_REQUEST 							= 401;
	public static final int EVENT_MCM_DEAD								= 402;

	public static final int EVENT_SSM_CONSTRAINT_APPLIED 				= 500;
	public static final int EVENT_SSM_BOLUS_INTERCEPT 					= 501;
	public static final int EVENT_SSM_CREDIT_INTERCEPT 					= 502;
	public static final int EVENT_SSM_NOT_ENOUGH_DATA_INTERCEPT 		= 503;
	public static final int EVENT_SSM_UNKNOWN_INTERCEPT 				= 504;
	public static final int EVENT_SSM_INTERCEPT_TIMEOUT 				= 511;
	public static final int EVENT_SSM_INTERCEPT_CANCEL 					= 512;
	public static final int EVENT_SSM_INTERCEPT_ACCEPT			 		= 513;
	public static final int EVENT_SSM_DEAD								= 514;

	// Popup alert dismiss Events
	public static final int EVENT_USER_RESPONSE 						= 600;
	public static final int EVENT_AUTOMATICALLY_DISMISSED 				= 601;

	public static final int EVENT_APC_ERROR 							= 700;
	public static final int EVENT_APC_RESPONSE 							= 701;
	public static final int EVENT_APC_DEAD								= 702;

	public static final int EVENT_BRM_ERROR 							= 800;
	public static final int EVENT_BRM_RESPONSE 							= 801;
	public static final int EVENT_BRM_DEAD								= 802;
	
	public static final int EVENT_HW_PUMP								= 900;
	public static final int EVENT_HW_CGM								= 901;
	
	public static final int EVENT_EXERCISE_NO_DATA						= 1000;

	//Settings codes
	public static final int SET_LOG						= 0;
	public static final int SET_POPUP					= 1;
	public static final int SET_POPUP_AUDIBLE			= 2;
	public static final int SET_POPUP_VIBE				= 3;
	public static final int SET_POPUP_AUDIBLE_VIBE		= 4;
	public static final int SET_POPUP_AUDIBLE_ALARM		= 5;
	public static final int SET_CUSTOM					= 6;
	public static final int SET_HIDDEN_AUDIBLE			= 7;
	public static final int SET_HIDDEN_VIBE				= 8;
	public static final int SET_HIDDEN_AUDIBLE_VIBE		= 9;
	public static final int SET_POPUP_AUDIBLE_HYPO		= 10;
	
	//Audible alarms threshold
	public static final int AUDIBLE_ALARM_HYPO_ONLY		= 0;
	public static final int AUDIBLE_ALARM_ALL_EVENTS	= 1;
	
	//Booleans as Integers
	public static final int FALSE_INT					= 0;
	public static final int TRUE_INT					= 1;
	
	public static final int[] IMPORTANT_EVENT_CODES = {
		EVENT_SYSTEM_HYPO_ALARM,
		EVENT_SYSTEM_HYPO,
		EVENT_SYSTEM_NO_CGM_ALARM,
		EVENT_BASAL_PAUSED,
		EVENT_PUMP_MISSED_BOLUS,
		EVENT_PUMP_MISSED_THRES,
	};
	
	public static String getCodeString(int code)
	{
		switch(code)
		{
			case EVENT_PUMP_TIME_ERROR: return "Pump Time Error";
			case EVENT_PUMP_STOPPED: return "Pump Stopped";
			case EVENT_PUMP_WARNING_ERROR: return "Pump Warning Error";
			case EVENT_PUMP_LOW_RESERVOIR: return "Pump Low Reservoir";
			case EVENT_PUMP_LOW_BATTERY: return "Pump Low Battery";
			case EVENT_PUMP_MISSED_BOLUS: return "Pump Bolus Timed Out";
			case EVENT_PUMP_DISCONNECT_WARN: return "Pump Disconnect Warning";
			case EVENT_PUMP_DISCONNECT_TIMEOUT: return "Pump Disconnect Timeout";
			case EVENT_PUMP_TBR: return "Pump TBR";
			case EVENT_MDI_INPUT: return "MDI input";
			
			case EVENT_NETWORK_TIMEOUT: return "Network Timeout";
			case EVENT_NETWORK_SERVER_UNREACHABLE: return "Server Unreachable";
			
			case EVENT_CGM_TIME_ERROR: return "CGM Time Error";
			case EVENT_CGM_WARN: return "CGM Warning";
			
			case EVENT_SYSTEM_START: return "System Start";
			case EVENT_STOPPED_MODE: return "Switched to STOPPED mode";
			case EVENT_PUMP_MODE: return "Switched to PUMP mode";
			case EVENT_CLOSED_LOOP_MODE: return "Switched to CLOSED LOOP mode";
			case EVENT_SAFETY_MODE: return "Switched to SAFETY mode";
			case EVENT_SENSOR_MODE: return "Switched to SENSOR mode";
			case EVENT_BEGIN_EXERCISE: return "Exercise begun";
			case EVENT_END_EXERCISE: return "Exercise ended";
			case EVENT_UNKNOWN_MODE: return "Switched to UNKNOWN mode";

			case EVENT_SYSTEM_ERROR: return "System Error";
			case EVENT_SYSTEM_HYPO_ALARM: return "Hypo Alarm";
			case EVENT_SYSTEM_HYPO_TREATMENT: return "Hypo Treatment";
			case EVENT_SYSTEM_NO_HYPO_TREATMENT: return "Hypo No Treatment";
			case EVENT_UI_HYPO_BUTTON_PRESSED: return "Hypo Treatment Button";
			case EVENT_SYSTEM_HYPO: return "Hypo Alarm";
			case EVENT_SYSTEM_NO_CGM_ALARM: return "No CGM Alarm";
			case EVENT_SYSTEM_HYPER_ALARM: return "Hyper Alarm";
			case EVENT_SYSTEM_DATABASE_ACCESS_ERROR: return "Database Access Error";
			case EVENT_SYSTEM_IO_TEST: return "System IO Test";
			case EVENT_SYSTEM_BATTERY: return "Low Battery";
			case EVENT_SYSTEM_TIME: return "System Time";
			
			case EVENT_EXERCISE_NO_DATA: return "No Exercise Data";
			
			case EVENT_MEAL_ERROR: return "Meal Error";
			case EVENT_MCM_REQUEST: return "MCM Request";
				
			case EVENT_APC_ERROR: return "APC Error";
			case EVENT_APC_RESPONSE: return "APC Response";
				
			case EVENT_BRM_ERROR: return "BRM Error";
			case EVENT_BRM_RESPONSE: return "BRM Response";
			case EVENT_BRM_PARAM_CHANGED : return "BRM parameters changed";
				
			case EVENT_SSM_CONSTRAINT_APPLIED: return "IOB Constraint Applied";
			
			case EVENT_USER_RESPONSE: return "User Responded to Popup";
			case EVENT_AUTOMATICALLY_DISMISSED: return "Popup Automatically Dismissed";

			case EVENT_SSM_BOLUS_INTERCEPT: return "Bolus Request Intercepted";
			case EVENT_SSM_CREDIT_INTERCEPT: return "Credit Request Intercepted";
			case EVENT_SSM_NOT_ENOUGH_DATA_INTERCEPT: return "Not Enough Data Intercept";
			case EVENT_SSM_UNKNOWN_INTERCEPT: return "Unknown Intercept";
			case EVENT_SSM_INTERCEPT_TIMEOUT: return "Intercept Timed Out";
			case EVENT_SSM_INTERCEPT_CANCEL: return "Intercept Canceled";
			case EVENT_SSM_INTERCEPT_ACCEPT: return "Intercept Approved";
			default: return "Unknown";
		}
	}
	
	public static void addEvent(Context context, int code, String json, int settings)
	{
		ContentValues cv = new ContentValues();
		cv.put("time", System.currentTimeMillis()/1000);
		cv.put("code", code);
		cv.put("json", json);
		cv.put("settings", settings);
		cv.put("popup_displayed", FALSE_INT);
		
		context.getContentResolver().insert(Biometrics.EVENT_URI, cv);
	}
	
	//Bundles sent to this function must contain all string data (i.e. you must pre-parse data to strings)
	public static String makeJsonString(Bundle b)
	{
		final String FUNC_TAG = "makeJsonString";
		
		JSONObject j = new JSONObject();
		Set<String> set = b.keySet();
		for(String s:set)
		{
			try {
				j.put(s, b.getString(s));
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		
		Debug.i(TAG, FUNC_TAG, "JSON String: "+j.toString());
		
		return j.toString();
	}
}
