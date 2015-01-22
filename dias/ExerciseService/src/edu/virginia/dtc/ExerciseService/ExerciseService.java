//*********************************************************************************************************************
//  Copyright 2011-2012 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************

package edu.virginia.dtc.ExerciseService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Exercise;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.Tvector.Tvector;

public class ExerciseService extends Service 
{	
	// HR Service commands
	public static final int HR_SERVICE_CMD_NULL = 0;
	public static final int HR_SERVICE_CMD_DISCONNECT = 1;
	public static final int HR_SERVICE_CMD_INIT = 2;
	
	private static final String TAG = "ExerciseService";
	
    public long tickReceiverNextTimeSeconds;
    public static final long tickReceiverDeltaTSeconds = 300;
    double hr1_value;
    public Long last_Tvec_hr1_time_secs=0l;
    public int deviceType = 0;
    
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	public SharedPreferences ApplicationPreferences;
	public static final String PREFS = "Exercise_Controller";
	
	private int resting_HR=0;
	private String driverName="";
	private boolean EX_indicator=false;
	
	public BroadcastReceiver TimerTickReceiver = null;
	public BroadcastReceiver Driver_Request = null;
	public BroadcastReceiver Control_button = null;
	public BroadcastReceiver sensorControlReceiver = null;
	
    @Override
    public void onCreate() 
    {
        super.onCreate();
        
        final String FUNC_TAG = "onCreate";
        
        Debug.e(TAG, FUNC_TAG, "");
        
    	// Set up a Notification for this Service
    	String ns = Context.NOTIFICATION_SERVICE;
    	NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
    	int icon = R.drawable.icon;
    	CharSequence tickerText = "Exercise Service v1.0";
    	long when = System.currentTimeMillis();
    	Notification notification = new Notification(icon, tickerText, when);
    	Context context = getApplicationContext();
    	CharSequence contentTitle = "Exercise Service v1.0";
    	CharSequence contentText = "Exercise";
    	Intent notificationIntent = new Intent(this, ExerciseService.class);
    	PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
    	notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
    	final int EXERCISE_ID = 1;
    	
    	// Make this a Foreground Service
    	startForeground(EXERCISE_ID, notification);
    	
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();  
		
		initialize_exercise_state();
		
		// **************************************************************************************************************
     	// Register to receive one minute ticks from Supervisor
        // **************************************************************************************************************
        TimerTickReceiver = new BroadcastReceiver() {
        	final String FUNC_TAG = "TimerTickReceiver";
        	
     		@Override
            public void onReceive(Context context, Intent intent) 
     		{
     			Debug.i(TAG, FUNC_TAG, "Setting Exercise State");
 				initialize_exercise_state();
			}
     	};
        registerReceiver(TimerTickReceiver, new IntentFilter("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK"));
        
        log_action(TAG, "onCreate", Debug.LOG_ACTION_INFORMATION);
    }

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
    	final String FUNC_TAG = "onStartCommand";
    		
    	deviceType = intent.getIntExtra("type", Exercise.DEV_ZEPHYR_HRM);
    	Debug.i(TAG, FUNC_TAG, "Device type is: "+deviceType);
    	
		int command = intent.getIntExtra("HRCommand", 0);
		driverName = intent.getStringExtra("driver_name");
		
		Debug.i(TAG, FUNC_TAG, "Service started by "+driverName);
		
        switch(command) 
        {
    		case HR_SERVICE_CMD_NULL:
    			break;
    		case HR_SERVICE_CMD_INIT:
    			Debug.i(TAG, FUNC_TAG, "Service started by "+driverName);
    			break;
        	default:
       	        break;
        }
        
        return 0;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        final String FUNC_TAG = "onDestroy";
        
        Toast toast = Toast.makeText(this, "Exercise Service Stopped", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
        toast.show();
        
        Debug.e(TAG, FUNC_TAG, "");
    } 
    
    @Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

    public void initialize_exercise_state()
	{
		final String FUNC_TAG = "initialize_exercise_state";
		Calendar now = Calendar.getInstance();
		now.setTimeInMillis(getCurrentTimeSeconds()*1000);
		int now_minutes = 60*now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE); 
		
		boolean inRange= inBrmRange(now_minutes) ;
		boolean currentlyExercising = false;
		
		//Default value for camp, exercising
		if (Params.getInt(getContentResolver(), "exercise_detection_mode", 0)==2){
			//Check BRM profile
			if (inRange){
				currentlyExercising=false;
			}
			else {
				currentlyExercising=true;
			}
		}
		
		ContentValues cv = new ContentValues();
		cv.put("time", System.currentTimeMillis()/1000);
		cv.put("currentlyExercising", currentlyExercising);
		getContentResolver().insert(Biometrics.EXERCISE_STATE_URI, cv);
		
		Debug.i(TAG, FUNC_TAG," From Exercise Service currently exercising "+currentlyExercising+" time in BRM profile range  "+inRange+" "+now_minutes);
	}
    
    public boolean inBrmRange(int timeNowMins) 
	{
		final String FUNC_TAG = "inBrmRange";
//		Tvector safetyRanges = DiAsSubjectData.getInstance().subjectSafety;
		Tvector safetyRanges = new Tvector(12);
		if (readTvector(safetyRanges, Biometrics.USS_BRM_PROFILE_URI, this)) {
			for (int i = 0; i < safetyRanges.count(); i++) 
			{
				int t = safetyRanges.get_time(i).intValue();
				int t2 = safetyRanges.get_end_time(i).intValue();
				
				if (t > t2)			//Handle case of range over midnight
				{ 					
					t2 += 24*60;
				}
				
				if ((t <= timeNowMins && timeNowMins <= t2) || (t <= (timeNowMins + 1440) && (timeNowMins + 1440) <= t2))
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
    public boolean readTvector(Tvector tvector, Uri uri, Context calling_context) {
		boolean retvalue = false;
		Cursor c = calling_context.getContentResolver().query(uri, null, null, null, null);
		long t, t2 = 0;
		double v;
		if (c.moveToFirst()) {
			do {
				t = c.getLong(c.getColumnIndex("time"));
				if (c.getColumnIndex("endtime") < 0){
					v = c.getDouble(c.getColumnIndex("value"));
					//Log.i(TAG, "readTvector: t=" + t + ", v=" + v);
					tvector.put_with_replace(t, v);
				} else if (c.getColumnIndex("value") < 0){
					//Log.i(TAG, "readTvector: t=" + t + ", t2=" + t2);
					t2 = c.getLong(c.getColumnIndex("endtime"));
					tvector.put_time_range_with_replace(t, t2);
				}
			} while (c.moveToNext());
			retvalue = true;
		}
		c.close();
		return retvalue;
	}
    
	public long getCurrentTimeSeconds() 
	{
		return (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970
	}
    
    private void log_action(String service, String action, int priority) {
        Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("priority", priority);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
    }
}
