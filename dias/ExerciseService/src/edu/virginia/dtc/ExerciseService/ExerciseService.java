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
	
	private boolean detecting_exercise=true;
	private boolean readingSensor = true;
	
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
		
		sensorControlReceiver = new BroadcastReceiver() 
		{
			final String FUNC_TAG = "sensorControlReceiver";
			
			@Override
			public void onReceive(Context context, Intent intent) 
			{
				//TODO: do something about listening to sensor data or something...
				Debug.i(TAG, FUNC_TAG, "Received sensor control broadcast from driver!");
				
				if(readingSensor)
				{
					readingSensor = false;
					initialize_exercise_state();
				}
				else
					readingSensor = true;
				
				Toast.makeText(getApplicationContext(), "Reading Sensor: "+readingSensor, Toast.LENGTH_SHORT).show();
			}
		};
		registerReceiver(sensorControlReceiver, new IntentFilter("edu.virginia.dtc.sensorcontrol"));
		
		// **************************************************************************************************************
     	// Register to receive one minute ticks from Supervisor
        // **************************************************************************************************************
        TimerTickReceiver = new BroadcastReceiver() {
        	final String FUNC_TAG = "TimerTickReceiver";
        	
     		@Override
            public void onReceive(Context context, Intent intent) 
     		{
 				Debug.i(TAG, FUNC_TAG, "Exercise Controller is ==> "+detecting_exercise);
				if (detecting_exercise && readingSensor)
				{
					Debug.i(TAG, FUNC_TAG, "TimerTick 1 minute, checking exercise detector");   
					
					ContentValues cv = new ContentValues();
					cv.put("time", System.currentTimeMillis()/1000);
					//in case Zephyr if (Zephyr_HXM_EX_Detection())
					if (driverName.equalsIgnoreCase("HR_Driver"))
					{
						Debug.i(TAG, FUNC_TAG, "Starting resting HR calculation thread");
			    		if (! restingHrThread.isAlive())
		        		{
		    				restingHrThread.start();
		        		}
						EX_indicator=Zephyr_HXM_EX_Detection();
						Debug.i(TAG, FUNC_TAG, "Using HXM exercise detection algorithm" ); 
					}
					else if (driverName.equalsIgnoreCase("Bioharness_Driver"))
					{
						EX_indicator=Bioharness_EX_Detection();
						Debug.i(TAG, FUNC_TAG, "Using Bioharness exercise detection algorithm" ); 
					}
					
					if (EX_indicator)
					{
						cv.put("currentlyExercising", Exercise.EXER);
						Debug.i(TAG, FUNC_TAG, "Exercise Service set currently exercising to ==> true" ); 
					}
					else
					{
						cv.put("currentlyExercising", Exercise.NOT_EXER);
	 					Debug.i(TAG, FUNC_TAG, "Exercise Service set currently exercising to ==> false" ); 
					}
					
					if (! checkThread.isAlive())
	        		{
	    				checkThread.start();
	    				Debug.i(TAG, FUNC_TAG, "Check Thread started" );
	        		}
					
					getContentResolver().insert(Biometrics.EXERCISE_STATE_URI, cv);
				}
				else {
					Debug.i(TAG, FUNC_TAG, "Detecting: "+detecting_exercise+" Reading Sensor: "+readingSensor);
				}
			}
     	};
        registerReceiver(TimerTickReceiver, new IntentFilter("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK"));
 
        //Register for driver request
        Driver_Request = new BroadcastReceiver() {
        	final String FUNC_TAG = "Driver_Request";
        	
     		@Override
            public void onReceive(Context context, Intent intent) 
     		{
     			Debug.i(TAG, FUNC_TAG, "Receiving message from HR Driver...");
     			
     			if (intent.getStringExtra("REQUEST").equals("HR_REST"))
     			{
     				if (resting_HR == 0)
					{
						HR_Rest_calculator();
					}
					else
					{
						Intent HRIntent = new Intent("edu.virginia.dtc.intent.action.HR_REST");
				 		HRIntent.putExtra("hr_rest", resting_HR);
					 	sendBroadcast(HRIntent);
					}
     			}
            }
     	};
        registerReceiver(Driver_Request, new IntentFilter("edu.virginia.dtc.HR_Driver.intent.action.GET"));
        
        
        //Register for driver request
        Control_button = new BroadcastReceiver() {
        	final String FUNC_TAG = "Driver_Request";
        	
     		@Override
            public void onReceive(Context context, Intent intent) 
     		{
     			Debug.i(TAG, FUNC_TAG, "Receiving message from BH Driver...");
     			
     			detecting_exercise=intent.getBooleanExtra("toggle_value",true);
     			if (!detecting_exercise) {
     				initialize_exercise_state();
     			}
     			
     			Debug.i(TAG, FUNC_TAG, "detecting exercise is set to >>>  "+detecting_exercise);
            }
     	};
        registerReceiver(Control_button, new IntentFilter("edu.virginia.dtc.intent.EXERCISE_TOGGLE_BUTTON"));
    }

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
    	final String FUNC_TAG = "onStartCommand";
    	if (driverName.equalsIgnoreCase("HR_Driver"))
		{
    		Debug.i(TAG, FUNC_TAG, "Starting resting HR calculation thread");
    		if (! restingHrThread.isAlive())
        		restingHrThread.start();
		}
    		
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
		
		//default value for VCU, not exercising
		if (Params.getInt(getContentResolver(), "exercise_detection_mode", 0)==1){
			currentlyExercising = false;
		}
		
		//default value for camp, exercising
		else if (Params.getInt(getContentResolver(), "exercise_detection_mode", 0)==2){
			//check BRM profile
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
		
		Debug.i(TAG, FUNC_TAG,"  From Exercise Service  currently exercising ==>"+currentlyExercising+"  time in BRM profile range  = "+inRange+now_minutes);
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
	
    private Thread restingHrThread = new Thread() {
    	final String FUNC_TAG = "Update_HR_rest";
    	
		public void run() {
			Debug.i(TAG, FUNC_TAG, "Update_HR_rest");
			
			while (true)
			{
				if (resting_HR == 0)
				{
					HR_Rest_calculator();
				}
				else
				{
					Intent HRIntent = new Intent("edu.virginia.dtc.intent.action.HR_REST");
			 		HRIntent.putExtra("hr_rest", resting_HR);
				 	sendBroadcast(HRIntent);
				}
				
				try {
					Thread.sleep(90000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	};
	
    private void HR_Rest_calculator()
    {
    	final String FUNC_TAG = "HR_Rest_calculator";
    	double hr_rest=0;
    	
    	Debug.i(TAG, FUNC_TAG, "Pulling time in ascending order...");
    	
    	//Get the timestamp of the first HR value
    	Cursor c=getContentResolver().query(Biometrics.EXERCISE_SENSOR_URI, new String[]{"time"}, null, null, "time ASC LIMIT 1");
    	if(c != null && c.moveToFirst())
    	{
	 		long time_hr_first =c.getLong(c.getColumnIndex("time"));
	 		Debug.i(TAG, FUNC_TAG, "First HR timestamp =  "+time_hr_first);
	 		
	 		//Get the first hour of HR value
	    	Cursor avg=getContentResolver().query(Biometrics.EXERCISE_SENSOR_URI, new String[]{"json_data"}, "time < "+(time_hr_first+3601), null, null);
	    	
	    	Debug.i(TAG, FUNC_TAG, "query count = " +avg.getCount());
	    	if(avg != null && avg.moveToFirst())
	    	{
		    	int count=0;
		    	if (getCurrentTimeSeconds()-time_hr_first >= 3600)
		    	{
		    		while (!avg.isAfterLast())
		 			{
		    			String s = avg.getString(avg.getColumnIndex("json_data"));
		    			JSONObject j = null;
		    			double hr = 0;
		    			
		    			try
		    			{
		    				j = new JSONObject(s);
		    				hr = j.getDouble("hr1");
		    				
		    				//Generates a lot of debug output
		    				//Debug.i(TAG, FUNC_TAG, "HR: "+hr)
		    			}
		    			catch(JSONException e)
		    			{
		    				e.printStackTrace();
		    			}
		    			
		    			if (j != null && hr != 0)
		    			{
			    			hr_rest=hr_rest+hr;
			    			count++;
		    			}
		    			else
		    				Debug.e(TAG, FUNC_TAG, "JSON Object is null or HR value is zero!");
		    			
		    			avg.moveToNext();
		 			}
		    	}
		    	else
		    		Debug.i(TAG, FUNC_TAG, "Hasn't been long enough to generate resting heartrate: "+(getCurrentTimeSeconds() - time_hr_first)/60+" minutes elapsed");
	    	
		 		hr_rest=hr_rest/count;	
		 		resting_HR=(int)Math.round(hr_rest);
		 		Debug.i(TAG, FUNC_TAG, "resting HR = " +resting_HR+" count = "+count);
		 				
		 		Intent HRIntent = new Intent("edu.virginia.dtc.intent.action.HR_REST");
		 		HRIntent.putExtra("hr_rest", resting_HR);
			 	sendBroadcast(HRIntent);
	    	}
	    	else
	    		Debug.e(TAG, FUNC_TAG, "Error pulling HR values where time < "+(time_hr_first+3601));
	 		avg.close();
    	}
    	else
    		Debug.e(TAG, FUNC_TAG, "Error pulling time in ascending order!");
    	c.close();
    }
    
    private boolean Bioharness_EX_Detection()
    {
    	final String FUNC_TAG = "Bioharness_EX_Detection";
    	boolean EX=false;
    	/*
    	double activity=Activity_Mean(5*60);
    	if (activity>0.5){
    		EX=true;
    	}   	
    	else {
    		EX=false;
    	}*/
    	ArrayList<Double> means = new ArrayList<Double>();
    	
    	
    	//initialize intervals
    	for (int i=5;i>0;i--){
    		
    		means.add(Activity_Mean(getCurrentTimeSeconds()-i*60, getCurrentTimeSeconds()-(i-1*60)));
    		Debug.i(TAG, FUNC_TAG, "mean number :  "+i+" ===>>"+Activity_Mean(getCurrentTimeSeconds()-i*60, getCurrentTimeSeconds()-(i-1*60)));
    	}
    	double max_mean_value=Collections.max(means);
    	Debug.i(TAG, FUNC_TAG, "Max value of means ==>>"+max_mean_value);
    	
    	if(max_mean_value>=0.1){
    		EX=true;
    	}
    	else
    	{
    		EX=false;
    	}
    	//means calculation
    	
    	means.clear();
    	
    	return EX; 
    }
    
    public boolean Zephyr_HXM_EX_Detection ()
    {
    	boolean EX=false;
    	final String FUNC_TAG="Zephyr_HXM_EX_Detection";
    	double mean = HR_MEAN_VALUE(60);
		
		Debug.i(TAG, FUNC_TAG, "TimerTick 1 minute, mean HR = "+mean+" Threshold = "+(resting_HR*1.25)+" test  ==> "+(mean>(resting_HR*1.25)));   
		if (resting_HR != 0)
		{
			if (mean > (resting_HR*1.25))
			{
				EX=true;
			}
			else
			{
				EX=false;
			}
		}
		return EX;
    }
    
	Thread checkThread = new Thread()
	{
		final String FUNC_TAG = "checkThread";
		
	    @Override
	    public void run() 
	    {
	    	while(true) 
	        {
	        	try {
					Thread.sleep(60000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

	        	//Get the timestamp of the last HR value in the db
			   	Debug.i(TAG, FUNC_TAG, "Querying time from Exercise Sensor data...");
			   		
			   	Cursor c=getContentResolver().query(Biometrics.EXERCISE_SENSOR_URI, null, null, null, "time DESC LIMIT 1");
			   	if(c != null && c.moveToLast())
			   	{
			   		long last_hr_ts =c.getLong(c.getColumnIndex("time"));
			   		
			   		Debug.i(TAG, FUNC_TAG, "Last value Time diff = "+ (getCurrentTimeSeconds()-last_hr_ts));
			   		
			   		int r = (int)(getCurrentTimeSeconds()-last_hr_ts) / 60;
			   		Debug.i(TAG, FUNC_TAG, "r = "+r);
			   		
			   		if (r >= 1) {
			   			
			   			Debug.i(TAG, FUNC_TAG, "No Exercise Sensor Data for more than 1 minute.");
			   			
			   			log_action("ExerciseService", "NO_EXERCISE_SENSOR_VALUE", Debug.LOG_ACTION_WARNING);
			   			
			   			detecting_exercise = false;
			   			
			   			initialize_exercise_state();
			   			
			   			if (r % 5 == 0 && readingSensor) 
			   			{
			   				Bundle b = new Bundle();
			   				Debug.i(TAG, FUNC_TAG, "Five Minutes, Event generation");
				   			b.putString("description", "No Exercise Sensor Data for more than "+ r +" minutes. Check your sensor!");
				   			Event.addEvent(getApplicationContext(), Event.EVENT_EXERCISE_NO_DATA, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
				   		}
			   			else
			   				Debug.w(TAG, FUNC_TAG, "Reading Sensor: "+readingSensor);
			   		}
			   	}
			   	c.close();
	        }
	    }
	};
	
	// Function to return the mean hr value over the last minute
	public double HR_MEAN_VALUE (int time_in_sec)
	{
		final String FUNC_TAG = "HR_MEAN_VALUE";
		double hr_value=0;

    	//Get the timestamp of the first HR value
		Debug.i(TAG, FUNC_TAG, "Getting HR time!");
		
    	Cursor c=getContentResolver().query(Biometrics.EXERCISE_SENSOR_URI, new String[]{"time"}, null, null, "time DESC LIMIT 1");
    	if(c != null && c.moveToFirst())
    	{
    		long time_hr_last =c.getLong(c.getColumnIndex("time"));
    		
    		//Get the last minute of HR value
    		int i = 0;
        	Cursor avg=getContentResolver().query(Biometrics.EXERCISE_SENSOR_URI, null,"time > "+(time_hr_last - time_in_sec), null, null);
        	if(avg != null && avg.moveToFirst())
        	{
        		i = avg.getCount();
        		
	    		while (!avg.isAfterLast())
	    		{
	    			String s = avg.getString(avg.getColumnIndex("json_data"));
	    			JSONObject j = null;
	    			double hr = 0;
	    			
	    			try
	    			{
	    				j = new JSONObject(s);
	    				hr = j.getDouble("hr1");
	    			}
	    			catch(JSONException e)
	    			{
	    				e.printStackTrace();
	    			}
	    			
	    			if (j != null && hr != 0)
	    			{
	    				hr_value= hr_value + hr;
	    			}
	    			else
	    				Debug.e(TAG, FUNC_TAG, "JSON Object is null or HR value is zero!");
	    			
	    			avg.moveToNext();
	    		}
        	}
        	else
        		Debug.e(TAG, FUNC_TAG, "Error getting values from Exercise table from time > "+(time_hr_last - time_in_sec));
     		avg.close();
     		
     		hr_value=hr_value/i;	
     		hr_value=(int)Math.round(hr_value);
    	}
    	else
    		Debug.e(TAG, FUNC_TAG, "Error getting time values from Exercise Table");
 		c.close();
    	
 		Debug.i(TAG, FUNC_TAG, "Mean Value: "+hr_value);
 		return hr_value;
	}
	//Gets the average activity level over the last time_in_sec
	public double Activity_Mean (int time_in_sec)
	{
		final String FUNC_TAG = "Activity_Mean";
		double activity_value=0;
    	    	    	
    	//Get the timestamp of the last Activity value
		Debug.i(TAG, FUNC_TAG, "Getting Activity time!");
		
    	Cursor c=getContentResolver().query(Biometrics.EXERCISE_SENSOR_URI, new String[]{"time"}, null, null, "time DESC LIMIT 1");
    	if(c != null && c.moveToFirst())
    	{
    		//long time_activity_last =c.getLong(c.getColumnIndex("time"));
    		long now = getCurrentTimeSeconds();
    		
    		//Get the last minute of HR value
    		int i = 0;
        	Cursor avg=getContentResolver().query(Biometrics.EXERCISE_SENSOR_URI, null,"time > "+(now - time_in_sec), null, null);
        	if(avg != null && avg.moveToFirst())
        	{
        		i = avg.getCount();
        		
	    		while (!avg.isAfterLast())
	    		{
	    			String s = avg.getString(avg.getColumnIndex("json_data"));
	    			JSONObject j = null;
	    			double activity = 0;
	    			
	    			try
	    			{
	    				j = new JSONObject(s);
	    				activity = j.getDouble("Activity");
	    			}
	    			catch(JSONException e)
	    			{
	    				e.printStackTrace();
	    			}
	    			
	    			if (j != null && activity != 0)
	    			{
	    				activity_value= activity_value + activity;
	    			}
	    			else
	    				Debug.e(TAG, FUNC_TAG, "JSON Object is null or activity value is zero!");
	    			
	    			avg.moveToNext();
	    		}
        	}
        	else
        		Debug.e(TAG, FUNC_TAG, "Error getting values from Exercise table from time > "+(now - time_in_sec));
     		avg.close();
     		
     		activity_value = activity_value / Math.max(time_in_sec, i);	
     		
    	}
    	else
    		Debug.e(TAG, FUNC_TAG, "Error getting time values from Exercise Table");
 		c.close();
    	
 		Debug.i(TAG, FUNC_TAG, "Mean Value: "+activity_value);
 		return activity_value;
	}
	//Function to get the activity mean between a given time interval [start,end]
	public double Activity_Mean (long start,long end)
	{
		final String FUNC_TAG = "Activity_Mean";
		double activity_value=0;
    	    	    	
    	//Get the timestamp of the last Activity value
		Debug.i(TAG, FUNC_TAG, "Getting Activity time!");
		
    	Cursor c=getContentResolver().query(Biometrics.EXERCISE_SENSOR_URI, new String[]{"time"}, null, null, "time DESC LIMIT 1");
    	if(c != null && c.moveToFirst())
    	{
    		//long time_activity_last =c.getLong(c.getColumnIndex("time"));
    		long now = getCurrentTimeSeconds();
    		
    		//Get the last minute of HR value
    		int i = 0;
        	Cursor avg=getContentResolver().query(Biometrics.EXERCISE_SENSOR_URI, null,"time BETWEEN "+start+" AND "+end, null, null);
        	if(avg != null && avg.moveToFirst())
        	{
        		i = avg.getCount();
        		
	    		while (!avg.isAfterLast())
	    		{
	    			String s = avg.getString(avg.getColumnIndex("json_data"));
	    			JSONObject j = null;
	    			double activity = 0;
	    			
	    			try
	    			{
	    				j = new JSONObject(s);
	    				activity = j.getDouble("Activity");
	    			}
	    			catch(JSONException e)
	    			{
	    				e.printStackTrace();
	    			}
	    			
	    			if (j != null && activity != 0)
	    			{
	    				activity_value= activity_value + activity;
	    			}
	    			else
	    				Debug.e(TAG, FUNC_TAG, "JSON Object is null or activity value is zero!");
	    			
	    			avg.moveToNext();
	    		}
        	}
        	else
        		Debug.e(TAG, FUNC_TAG, "Error getting values from Exercise table between "+start+" and "+end);
     		avg.close();
     		
     		activity_value = activity_value / Math.max(end-start+1, i);	
     		Debug.i(TAG, FUNC_TAG,"Number of activity values ==>"+i);
     		
    	}
    	else
    		Debug.e(TAG, FUNC_TAG, "Error getting time values from Exercise Table");
 		c.close();
    	
 		Debug.i(TAG, FUNC_TAG, "Mean Value: "+activity_value);
 		return activity_value;
	}
	
    public static double round(double value, int places) {
        if (places < 0) 
        	throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
    
    public void log_action(String service, String action, int priority) {
        Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("priority", priority);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
    }
    /*
    public Boolean GET_EX_CTRL_SHARED_PREF(){
    	final String FUNC_TAG = "GET_EX_CTRL_SHARED_PREF";
    	
    	Context con;
    	Boolean value=true;
        try{
	        con = createPackageContext("edu.virginia.dtc.HR_Driver", 0);
	        SharedPreferences pref = con.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	        
	        Debug.i(TAG, FUNC_TAG," button value  "+pref.getBoolean("Control", true));
	        value= pref.getBoolean("Control", true);
        }
        catch (Exception e){
        	Debug.i(TAG, FUNC_TAG, e.getMessage());
        }
        return value;
    }
    */
}
