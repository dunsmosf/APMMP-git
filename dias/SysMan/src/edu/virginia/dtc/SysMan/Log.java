package edu.virginia.dtc.SysMan;

import android.content.Context;
import android.content.Intent;

public class Log {
	
	/***
	 * Log Priority Levels
	 */
	public static final int LOG_ACTION_DEBUG = 1;
	public static final int LOG_ACTION_INFORMATION = 2;
	public static final int LOG_ACTION_WARNING = 3;
	public static final int LOG_ACTION_ERROR = 4;
	
	
	/***
	 * Sends a broadcast intent containing a Log's 'service', 'status', 'time' and 'priority' based on the current context.
	 * 
	 * @param context - Context of the Application running the 'log_action' function 
	 * @param service - Log field
	 * @param status - Log field
	 * @param time - Log field
	 * @param priority - Log field
	 */
	public static void log_action(Context context, String service, String status, long time, int priority) {
			Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
			
			i.putExtra("Service", service);
			i.putExtra("Status", status);
			i.putExtra("time", time);
			i.putExtra("priority", priority);
			
			context.sendBroadcast(i);
	}
	
	/**
	 * Info log_action
	 */
	public static void i(Context context, String service, String status, long time) {
		log_action(context, service, status, time, LOG_ACTION_INFORMATION);
	}
	
	/**
	 * Debug log_action
	 */
	public static void d(Context context, String service, String status, long time) {
		log_action(context, service, status, time, LOG_ACTION_DEBUG);
	}
	
	/**
	 * Warning log_action
	 */
	public static void w(Context context, int priority_threshold, String service, String status, long time) {
		log_action(context, service, status, time, LOG_ACTION_WARNING);
	}
	
	/**
	 * Error log_action
	 */
	public static void e(Context context, int priority_threshold, String service, String status, long time) {
		log_action(context, service, status, time, LOG_ACTION_ERROR);
	}
	
}
