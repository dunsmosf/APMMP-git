//*********************************************************************************************************************
//  Copyright 2011-2012 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************

package edu.virginia.dtc.ExerciseService;

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
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.Gravity;
import android.widget.Toast;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Exercise;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Safety;
import edu.virginia.dtc.SysMan.State;

public class ExerciseService extends Service 
{	
	// HR Service commands
	public static final int HR_SERVICE_CMD_NULL = 0;
	public static final int HR_SERVICE_CMD_DISCONNECT = 1;
	public static final int HR_SERVICE_CMD_INIT = 2;
	
	private static final String TAG = "ExerciseServiceShell";
	
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	public SharedPreferences ApplicationPreferences;
	public static final String PREFS = "ExerciseShell";
	
	private BroadcastReceiver TimerTickReceiver = null;
	private ExerciseObserver exerciseObserver;
	private int deviceType = 0;
	
    @Override
    public void onCreate() 
    {
        super.onCreate();
        
        final String FUNC_TAG = "onCreate";
        
        Debug.i(TAG, FUNC_TAG, "");
        
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
		
     	// Register to receive one minute ticks from Supervisor
        TimerTickReceiver = new BroadcastReceiver() {
        	final String FUNC_TAG = "TimerTickReceiver";
        	
     		@Override
            public void onReceive(Context context, Intent intent) 
     		{
     			Debug.i(TAG, FUNC_TAG, "System 1 minute tick firing...");
     			
				writeStateData(Exercise.DONT_CARE);
            }
     	};
        registerReceiver(TimerTickReceiver, new IntentFilter("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK"));
        
        // Register Content Observer on the Exercise Sensor table
        // CAUTION:  Because of the speed of accelerometer or HRM data, you may not want to use this method
        exerciseObserver = new ExerciseObserver(new Handler());
        getContentResolver().registerContentObserver(Biometrics.EXERCISE_SENSOR_URI, true, exerciseObserver);
    }

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
    	final String FUNC_TAG = "onStartCommand";
    		
    	deviceType = intent.getIntExtra("type", Exercise.DEV_ZEPHYR_HRM);
    	Debug.i(TAG, FUNC_TAG, "Device type is: "+deviceType);
    	
		int command = intent.getIntExtra("HRCommand", 0);
		
        switch(command) 
        {
    		case HR_SERVICE_CMD_NULL:
    			break;
    		case HR_SERVICE_CMD_INIT:
    			String driverName = intent.getStringExtra("driver_name");
    			Debug.i(TAG, FUNC_TAG, "Service started by "+driverName+"!");
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

        getContentResolver().unregisterContentObserver(exerciseObserver);
        
        Debug.e(TAG, FUNC_TAG, "");
    } 
    
    @Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

    //So this function is used to parse the JSON string data from the bCP
    //You would want to make your query and then call this on the "json_data" string values
    //There is a real SQL column for "time" so that you can still filter and sort based on time values
    private void parseJsonExerciseData(String s)
    {
    	final String FUNC_TAG = "parseJsonExerciseData";
    	
    	JSONObject j = null;
    	
    	try 
    	{
			j = new JSONObject(s);
		} 
    	catch (JSONException e) 
    	{
			e.printStackTrace();
		}
    	
    	//Error catching stuff
    	if(j == null)
    	{
    		Debug.e(TAG, FUNC_TAG, "JSON Object is null...exiting!");
    		return;
    	}
    	
    	switch(deviceType)
    	{
	    	case Exercise.DEV_ZEPHYR_HRM:
	    		double hr = -1;
	    		double instantSpeed = -1;
	    		int battery = -1;
	    		
	    		//These are place holder values, you would want to take the results
	    		//and use them as you choose or store them in other objects
				try {
					hr = j.getDouble("hr1");
					battery = j.getInt("battery");
					instantSpeed = j.getDouble("instantspeed");
				} catch (JSONException e) {
					e.printStackTrace();
				}
	    		
	    		break;
	    	case Exercise.DEV_BIO_HARNESS:
	    		//TODO: Add support for Bioharness Data
	    		
	    		break;
    		default:
    			Debug.e(TAG, FUNC_TAG, "Unknown device type!");
    			break;
    	}
    }
    
    private void readSensorData()
    {
    	Cursor c = getContentResolver().query(Biometrics.EXERCISE_SENSOR_URI, null, null, null, null);
    	if(c != null)
    	{
    		//This will grab all the data that has been put in the Exercise Sensor table.
    		//This can be extremely costly if there is a lot of data, avoid using it or add
    		//arguments to sort based on time, etc.
    		
    		//Consider using functions like moveToFirst() or moveToLast() etc. in combination
    		//with filters in the query to limit the amount of DB access time
    	}
    	c.close();
    }
    
    private void writeStateData(int exercising)
    {
    	JSONObject j = new JSONObject();
    	
    	//An example for adding data to JSON Objects
    	/*
    	try {
			j.put("test", 50);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		*/
    	
    	ContentValues cv = new ContentValues();

    	//Add what values you'd like to the table...
    	cv.put("time", System.currentTimeMillis()/1000);
    	cv.put("currentlyExercising", exercising);
    	//cv.put("json_data", j.toString());
    	
    	//Write them to the Exercise State table (You won't have permission elsewhere)
    	getContentResolver().insert(Biometrics.EXERCISE_STATE_URI, cv);
    }
    
    public void log_action(String service, String action, int priority) {
        Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("priority", priority);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
    }

    public long getCurrentTimeSeconds() 
	{
		return (long)(System.currentTimeMillis()/1000);
	}

    // This is a content observer, basically it will fire the onChange method each time a modification
    // is made to the table it is observing, in this case the Exercise Sensor table.  This means anytime
    // a device adds a heart rate value or accelerometer value to the table.  Understand that this means 
    // this routine may be running at a very high rate (potentially as fast as the data rate of your sensor)
    class ExerciseObserver extends ContentObserver 
    {	
    	private int count;
    	
    	public ExerciseObserver(Handler handler) 
    	{
    		super(handler);
    		count = 0;
    	}

       @Override
       public void onChange(boolean selfChange) 
       {
    	   this.onChange(selfChange, null);
       }		

       public void onChange(boolean selfChange, Uri uri) 
       {
    	   final String FUNC_TAG = "ExerciseObserver";
    	   
    	   count++;
    	   Debug.i(TAG, FUNC_TAG, "onChange: "+count);
    	   
    	   //This would be a typical implementation BUT AGAIN, because of the potentially high volume of 
    	   //data, care should be taken when trying to read lots of data fromt he DB each time it is added
    	   
    	   // This is an example of a query that only pulls the entries from the last 5 seconds
    	   Cursor c = getContentResolver().query(Biometrics.EXERCISE_SENSOR_URI, null, "time > "+((System.currentTimeMillis()/1000) - 5), null, null);
    	   if(c != null)
    	   {
    		   Debug.i(TAG, FUNC_TAG, "Row count: "+c.getCount());
    		   
    		   //This basically means we are grabbing the most recently entered value
    		   if(c.moveToLast())
    		   {
    			   Debug.i(TAG, FUNC_TAG, "Data: "+c.getString(c.getColumnIndex("json_data")));
    		   }
    	   }
    	   c.close();
       }		
    }
}
