package edu.virginia.dtc.SysMan;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Debug {
	
	public static final int LOG_DEBUG = 0;
	public static final int LOG_WARNING = 1;
	public static final int LOG_ERROR = 2;
	
	public static final int DEBUG = Log.DEBUG;
	public static final int INFO = Log.INFO;
	public static final int WARN = Log.WARN;
	public static final int VERBOSE = Log.VERBOSE;
	public static final int ERROR = Log.ERROR;
	
	public static final boolean GLOBAL_DEBUG = true;
	
	private static final String delimiter = " >>> ";
	private static final String[] exclusions = {};		//{"diasmain", "biometricsContentProvider", "networkservice", "DiAsService", "DiAsDriversData"};
	
	public static void log(Context context, String tag, String message)
	{
		log(context, tag, message, LOG_DEBUG);
	}
	
	public static void log(Context context, String tag, String message, int priority) 
	{
		Intent log = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        log.putExtra("Service", tag);
        log.putExtra("Status", message);
        log.putExtra("priority", priority);
        log.putExtra("time", System.currentTimeMillis()/1000);
        context.sendBroadcast(log);
	}
	
	private static long start, stop, lap;

	public static void start()
	{
		start = System.nanoTime();
	}
	
	public static void lap(String tag, String function, String message)
	{
		lap = System.nanoTime();
		
		long result = lap - start;
		start = lap;
		
		message = function + delimiter + "Comp Time: "+(result)+"ns "+(result/1000000)+"ms "+(result/1000000000)+"s"+" > "+message;
		log(Log.DEBUG, tag, message, null);
	}
	
	public static void stop(String tag, String function, String message)
	{
		stop = System.nanoTime();
		long result = stop - start;
		
		message = function + delimiter + "Comp Time: "+(result)+"ns "+(result/1000000)+"ms "+(result/1000000000)+"s"+" > "+message;
		log(Log.DEBUG, tag, message, null);
	}
	
	public static void d(String tag, String function, String message)
	{
		message = function + delimiter + message;
		
		log(Log.DEBUG, tag, message, null);
	}
	
	public static void i(String tag, String function, String message)
	{
		message = function + delimiter + message;
		
		log(Log.INFO, tag, message, null);
	}
	
	public static void w(String tag, String function, String message)
	{
		message = function + delimiter + message;
		
		log(Log.WARN, tag, message, null);
	}
	
	public static void v(String tag, String function, String message)
	{
		message = function + delimiter + message;
		
		log(Log.VERBOSE, tag, message, null);
	}
	
	public static void e(String tag, String function, String message)
	{
		message = function + delimiter + message;
		
		log(Log.ERROR, tag, message, null);
	}
	
	public static void e(String tag, String function, String message, Throwable tr)
	{
		log(Log.ERROR, tag, message, tr);
	}
	
	private static void log(int type, String tag, String message, Throwable tr)
	{
		if(GLOBAL_DEBUG)
		{
			for(String s:exclusions)
			{
				if(s.equalsIgnoreCase(tag))
					return;
			}
			
			switch(type)
			{
				case Log.ERROR:
					Log.e(tag, message, tr);
					break;
				case Log.VERBOSE:
					Log.v(tag, message);
					break;
				case Log.DEBUG:
					Log.d(tag, message);
					break;
				case Log.INFO:
					Log.i(tag, message);
					break;
				case Log.WARN:
					Log.w(tag, message);
					break;
			
			}
		}
	}
}
