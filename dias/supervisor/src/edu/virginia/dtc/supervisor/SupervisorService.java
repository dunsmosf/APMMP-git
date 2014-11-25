//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.supervisor;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.lang.System;

import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;

public class SupervisorService extends Service {
 
	private static final String TAG = "SupervisorService";
	
	// SubjectNumber string is used to select which remote monitoring database to use
	private static boolean DEBUG_MODE = true;
	private String SubjectNumber;
	public boolean realTime;
	public int speedupMultiplier;
	
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	public long tickReceiverNextTimeSeconds, nextCGMTimeSeconds;
	private double nextCGMValue;
	private double nextSimulatedPumpValue;
	public long realTimeTickReceiverNextTimeSeconds;
    public static final long tickReceiverDeltaTSeconds = 300;
    public static final long tickReceiverCGMtoPumpOffsetSeconds = 30;
    public long startTimeSeconds;
	public int cycle_duration_seconds = 300;
	public int cycle_duration_mins = cycle_duration_seconds/60;
	
	// Variables to support running with simulated time
	public boolean simulationTimerIsRunning;
	private long simulated_time;
	private long free_running_Time_Tick_counter;
	private long InitializeSimulatedTime= -1;
	
	private boolean batteryCollectionStarted = false;
	
	// Speedup allowed fields
	public static final String SPEEDUP_ALLOWED_DRIVER = "edu.virginia.dtc.standaloneDriver";
	private boolean speedupAllowed = false; 
	private Runnable taskVerifySpeedupAllowed = new Runnable() {		
		public void run() {
			verifySpeedupAllowed();
		}
	};
	
	// SupervisorService Commands
	public static final int SUPERVISOR_SERVICE_COMMAND_NULL = 0;
	public static final int SUPERVISOR_SERVICE_COMMAND_SET_DATA_TRANSMISSION_MODE = 1;
	public static final int SUPERVISOR_SERVICE_COMMAND_SET_TIME_MODE = 2;
	public static final int SUPERVISOR_SERVICE_COMMAND_SET_SIMULATED_TIME = 3;
	public static final int SUPERVISOR_SERVICE_COMMAND_STOP_TIMERS = 4;
	public static final int SUPERVISOR_SERVICE_COMMAND_UPDATE_DIAS_STATE = 5;
	public static final int SUPERVISOR_SERVICE_COMMAND_SET_IOTEST_MODE = 6;
	public static final int SUPERVISOR_SERVICE_COMMAND_VERIFY_SPEEDUP_ALLOWED = 7;
    
    float x,y,z;
    
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private SensorEventListener sensorEventListener = new SensorEventListener() {

        public void onAccuracyChanged(Sensor sensor, int accuracy) 
        {
        }

        public void onSensorChanged(SensorEvent event) 
        {
        	x = event.values[0];
        	y = event.values[1];
        	z = event.values[2];
        	
        	//Log.i(TAG, "X: "+x+" Y: "+y+" Z: "+z);
        	
        	/*
        	ContentValues acc = new ContentValues();
			
			//Collect accelerometer data
			acc.put("time", getCurrentTimeSeconds());
			acc.put("x", x);
			acc.put("y", y);
			acc.put("z", z);
			
			//Insert data into the table
			try 
		    {
		    	getContentResolver().insert(ACC_URI, acc);
		    }
		    catch (Exception e) 
		    {
		    	Log.e("Error",(e.getMessage() == null) ? "null" : e.getMessage());
		    }
		    */
        }
    };
    
    private LocationManager mLoc = null;
	
    public Handler handler;
	public static ScheduledExecutorService systemScheduler = Executors.newSingleThreadScheduledExecutor();
	public static List<ScheduledFuture<?>> systemTimers = new ArrayList<ScheduledFuture<?>>();
	public Runnable tick = new Runnable()
	{
		final String FUNC_TAG = "tick";
		
		public void run() 
		{			
			NotificationCompat.Builder simTimeNotification = new NotificationCompat.Builder(SupervisorService.this);
			simTimeNotification.setOngoing(true);
			simTimeNotification.setSmallIcon(R.drawable.ic_dialog_time);
			simTimeNotification.setContentTitle("Current simulated time");
			simTimeNotification.setContentText(SimpleDateFormat.getDateTimeInstance().format(new Date(simulated_time * 1000)));
			
			NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);			
			if (simulated_time > 0)
				nm.notify(99, simTimeNotification.getNotification());
			else
				nm.cancel(99);
			
			// Turn off speed-up mode if not allowed
			if (!realTime && !speedupAllowed) {
				Debug.e(TAG, FUNC_TAG, "Speed-up not allowed, switching to real-time");
				handler.post(new Runnable() {					
					public void run() {
						stopTimers();
						Bundle timeModeBundle = new Bundle();
						timeModeBundle.putBoolean("realtime", true);
						timeModeBundle.putInt("speedupMultiplier", 1);
						timeModeBundle.putLong("InitializeSimulatedTime", simulated_time);
						setTimeMode(timeModeBundle);
					}
				});
				return; //no tick in this case
			}
			
			// Clock tick every 6 or 60 seconds
		    //TODO: trace SUPERVISOR_TIME_TICK broadcast
   			Intent tickBroadcast = new Intent("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK");
   			tickBroadcast.putExtra("tick", free_running_Time_Tick_counter);
   			tickBroadcast.putExtra("simulatedTime", simulated_time);
			tickBroadcast.putExtra("startTimeSeconds", startTimeSeconds);
   			sendBroadcast(tickBroadcast);
   			
   	 	   	//Intent tickCounter= new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
   	 	   	//tickCounter.putExtra("id", 2);
   	 	   	//tickCounter.putExtra("text",free_running_Time_Tick_counter+" | ");
   	 	   	//tickCounter.putExtra("color", Color.WHITE);
   	 	   	//sendBroadcast(tickCounter);

   	 	   if(Params.getBoolean(getContentResolver(), "acc_enabled", false))
   			{
	   			ContentValues acc = new ContentValues();
	   			
				//Collect accelerometer data
				acc.put("time", getCurrentTimeSeconds());
				acc.put("x", x);
				acc.put("y", y);
				acc.put("z", z);
				
				//Insert data into the table
				try 
			    {
			    	getContentResolver().insert(Biometrics.ACC_URI, acc);
			    }
			    catch (Exception e) 
			    {
			    	Debug.e(TAG, FUNC_TAG, "Error: "+((e.getMessage() == null) ? "null" : e.getMessage()));
			    }
   			}
   			
   			Debug.i(TAG, FUNC_TAG,"TICK! "+free_running_Time_Tick_counter);
   			
    		//if (free_running_Time_Tick_counter % cycle_duration_mins == 0 || getCurrentTimeSeconds() > tickReceiverNextTimeSeconds)
   			if(free_running_Time_Tick_counter == 2)
    		{
    			//free_running_Time_Tick_counter = 0;									
    			
    			// Algorithm tick every 30 or 300 seconds
    			// Reset the counter so that future calls match this timing.  This allows
    			// the incoming CGM data value to force the SUPERVISOR_CONTROL_ALGORITHM_TICK
    			// to occur half way between CGM values to avoid possible driver conflicts.
    			// Set the tickReceiverNextTimeSeconds to one cycle from now
    			
    			//tickReceiverNextTimeSeconds = getCurrentTimeSeconds()  + tickReceiverDeltaTSeconds;
    			Debug.i(TAG, FUNC_TAG, "Supervisor Control Algorithm Tick > getCurrentTimeSeconds()="+getCurrentTimeSeconds()+", free_running_Time_Tick_counter="
					+free_running_Time_Tick_counter+", tickReceiverNextTimeSeconds="+tickReceiverNextTimeSeconds);
			
				//TODO: track ALGORITHM_TICK
				Intent algTickBroadcast = new Intent("edu.virginia.dtc.intent.action.SUPERVISOR_CONTROL_ALGORITHM_TICK");
				algTickBroadcast.putExtra("simulatedTime", simulated_time);
				algTickBroadcast.putExtra("nextSimulatedPumpValue", nextSimulatedPumpValue);
				algTickBroadcast.putExtra("startTimeSeconds", startTimeSeconds);
				sendBroadcast(algTickBroadcast);
    		}
    		
    		if (simulated_time > 0 && nextCGMTimeSeconds > 0) 
    		{
    			simulated_time = nextCGMTimeSeconds;
    		}
    		else if(simulated_time >0)
    		{
    			simulated_time += 60;
    		}
    		
    		if (free_running_Time_Tick_counter % cycle_duration_mins == 0 || getCurrentTimeSeconds() > tickReceiverNextTimeSeconds)
    			free_running_Time_Tick_counter = 0;
    		
    		free_running_Time_Tick_counter += 1;
		}	
	};

	public SupervisorService() {
	}
	
	@Override
	public void onCreate() {
		final String FUNC_TAG = "onCreate";
		
		realTime = false;
		speedupMultiplier = 10;
		SubjectNumber = new String("0");
		simulationTimerIsRunning = false;
		simulated_time = -1;
		InitializeSimulatedTime = -1;
		startTimeSeconds = getCurrentTimeSeconds();
		nextCGMValue = 0.0;
		nextSimulatedPumpValue = 0.0;
		nextCGMTimeSeconds = -1;													// Negative until we receive first value from BluetoothCGM
		tickReceiverNextTimeSeconds = getCurrentTimeSeconds() + 10*365*86400;		// Arbitrarily set this value to 10 years from now until we receive																
		handler = new Handler();

		//Turn on BT if it isn't on
		BluetoothAdapter bt;
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)
			bt = ((android.bluetooth.BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();
		else
			bt = BluetoothAdapter.getDefaultAdapter();
		
		if(!bt.isEnabled())
			bt.enable();
		
		// Log Start Event
    	Bundle bun = new Bundle();
		bun.putString("description", "Supervisor Startup");
		Event.addEvent(this, Event.EVENT_SYSTEM_START, Event.makeJsonString(bun), Event.SET_LOG);

		if(Params.getBoolean(getContentResolver(), "acc_enabled", false))
		{
			sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		    accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		    
		    //May want to change the delay option to determine how fast it updates
		    //Apparently this is only a hint to the system it doesn't adhere to this speed, so it is necessary to collect the data 
		    //in a location other than the event listener, otherwise you will write to the content provider every 10-20ms
		    sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		}
		
		if(Params.getBoolean(getContentResolver(), "gps_enabled", false))
		{
			long interval = Params.getLong(getContentResolver(), "gps_interval", 60000);
			
			Debug.i(TAG, FUNC_TAG, "GPS Enabled");
			Debug.i(TAG, FUNC_TAG, "GPS Interval: "+interval+" ms");
			
			mLoc = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
			LocationListener mLocListener = new gpsListener();
			
			//Request location updates every 5 minutes (the input is in milliseconds)
			mLoc.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 0, mLocListener);
		}
		
		// Start the Battery Data Collection if specified by the Parameters
		boolean collectBatteryStats = Params.getBoolean(getContentResolver(), "collectBatteryStats", false);
		if(collectBatteryStats && !batteryCollectionStarted) {
			
			int collectBatteryDataInterval = Params.getInt(getContentResolver(), "collectBatteryStatsInterval", 15);
			
			Debug.i(TAG, FUNC_TAG, "Start Battery Data Collection through custom Android Settings");
			Intent startApp = new Intent();
			startApp.setAction("edu.virginia.dtc.intent.action.START_COLLECT_BATTERY_STATS");
			startApp.putExtra("collectBatteryDataInterval", collectBatteryDataInterval);
			sendBroadcast(startApp);
			
			batteryCollectionStarted = true;
		}
		else {
			Debug.i(TAG, FUNC_TAG, "No Battery Data Collection");
		}
 
		Toast.makeText(this, "SupervisorService onCreate", Toast.LENGTH_SHORT).show();

        // Set up a Notification for this Service
        final int SUPERVISOR_ID = 4;
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setSmallIcon(R.drawable.icon);
		builder.setContentText("Overseeing DiAs");
		builder.setContentTitle("Supervisor v1.0");
        // Make this a Foreground Service
        startForeground(SUPERVISOR_ID, builder.getNotification());
        log_action(TAG, "onCreate");
        
        handler.post(taskVerifySpeedupAllowed);
        
        // Keep the CPU running even after the screen dims
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wl.acquire();
        
        BroadcastReceiver timeChange = new BroadcastReceiver()
        {
			@Override
			public void onReceive(Context context, Intent intent) {
				
				if(intent.getAction().equalsIgnoreCase(Intent.ACTION_TIMEZONE_CHANGED))
				{
					Debug.e(TAG, FUNC_TAG, "The system time-zone has been changed");
				}
				else if(intent.getAction().equalsIgnoreCase(Intent.ACTION_TIME_CHANGED))
				{
					Debug.e(TAG, FUNC_TAG, "The system time has been changed");
				}
				
				Bundle b = new Bundle();
        		b.putString("description", "System time has been changed, please verify!");
        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_ALARM);
			}
        };
        IntentFilter filt = new IntentFilter();
        filt.addAction(Intent.ACTION_TIME_CHANGED);
        filt.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        this.registerReceiver(timeChange, filt);
        
        //Initialize the system in real-time mode
        Bundle b = new Bundle();
        b.putBoolean("realtime", true);
        setTimeMode(b);
	}
	
    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
    	final String FUNC_TAG = "onStartCommand";
    	
		int command = intent.getIntExtra("SupervisorCommand", 0);
		Debug.i(TAG, FUNC_TAG, "onStartCommand > SupervisorCommand="+command);
		
        switch(command) {
    		case SUPERVISOR_SERVICE_COMMAND_STOP_TIMERS:
    			stopTimers();
    			break;
    		case SUPERVISOR_SERVICE_COMMAND_SET_TIME_MODE:
    			setTimeMode(intent.getExtras());
    			break;
    		case SUPERVISOR_SERVICE_COMMAND_VERIFY_SPEEDUP_ALLOWED:    			
    			handler.post(taskVerifySpeedupAllowed);
    			break;
        }
        return 0;
    }
    
    private void stopTimers() {
		final String FUNC_TAG = "stopTimers";
		
		Debug.i(TAG, FUNC_TAG, "SUPERVISOR_SERVICE_COMMAND_STOP_TIMERS");
		
		for(ScheduledFuture<?> timer : systemTimers)
			timer.cancel(true);
		
		simulationTimerIsRunning = false;    	
    }
    
    private void setTimeMode(Bundle bundle) {
		final String FUNC_TAG = "setTimeMode";
		
		realTime = bundle.getBoolean("realtime", false);
		speedupMultiplier = bundle.getInt("speedupMultiplier", 10);
		InitializeSimulatedTime = bundle.getLong("InitializeSimulatedTime", -1);
		Debug.i(TAG, FUNC_TAG, "SUPERVISOR_SERVICE_COMMAND_SET_TIME_MODE  realtime=" + realTime + " speedup=" + speedupMultiplier + " initialSimTime=" + InitializeSimulatedTime);
			
   		long period, delay = 5000;
		if (realTime) 
		{
			period = 60000; 					// New cgm data every 300 sec Real Time (300 sec Simulated Time), insulin dosing every 300 sec RT (300 sec ST)
			Debug.i(TAG, FUNC_TAG, "Real Time (300 sec/cycle), delay="+delay+", period="+period);
			simulated_time = -1;
			Toast.makeText(this, "Real-time enabled!", Toast.LENGTH_LONG).show();
		}
		else 
		{
			period = 60000 / speedupMultiplier; // New cgm data every 300/speedupMultiplier sec Real Time (300 sec Simulated Time), insulin dosing every 300/speedupMultiplier sec RT (300 sec ST)
			if (simulated_time < 0 && InitializeSimulatedTime > 0) 	// Synchronize system time with the simulated time IF simulated time not yet initialized
			{
				simulated_time = InitializeSimulatedTime;
			}
			Debug.i(TAG, FUNC_TAG, "Simulated Time, delay="+delay+", period="+period + " sim_time=" + simulated_time);
			Toast.makeText(this, "Simulated " + speedupMultiplier + "x time enabled!", Toast.LENGTH_LONG).show();
		}

		for(ScheduledFuture<?> timer : systemTimers)
			timer.cancel(true);
		
    	free_running_Time_Tick_counter = 0;
    	
    	handler.post(taskVerifySpeedupAllowed);
    	systemTimers.add(systemScheduler.scheduleAtFixedRate(tick, 300, period, TimeUnit.MILLISECONDS));
    	if (simulated_time > 0) {
    		systemTimers.add(systemScheduler.scheduleAtFixedRate(new Runnable() {				
				public void run() {	    			
	    			// Toast simulated time if in simulated mode
					Debug.i(TAG, "Simulated Time Toaster", "sim_time=" + simulated_time);
	    			if (simulated_time > 0) {
	    				Toast.makeText(SupervisorService.this, "Simulated time: " + SimpleDateFormat.getTimeInstance().format(new Date(simulated_time)), Toast.LENGTH_LONG).show();
	    			}
				}
			}, 300, period, TimeUnit.MILLISECONDS));
    	}
    	
    	simulationTimerIsRunning = true;       	
    }    

	private synchronized void verifySpeedupAllowed() {
		final String FUNC_TAG = "verifySpeedupAllowed";
		
		boolean speedupDriverFound = false;
		boolean nonSpeedupDriverFound = false;
		for (ApplicationInfo app : getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA)) {
			if (app.metaData != null) {
				Bundle meta = app.metaData;
				//means it's a driver
				if (meta.containsKey("driver_name") && meta.containsKey("driver_cgm") && meta.containsKey("driver_pump") && meta.containsKey("driver_UI")) {
					if (SPEEDUP_ALLOWED_DRIVER.equals(app.packageName)) {
						speedupDriverFound = true;
					} else {
						nonSpeedupDriverFound = true;
					}
				}
			}
		}
		boolean prevSpeedupAllowed = speedupAllowed;
		speedupAllowed = speedupDriverFound && !nonSpeedupDriverFound;
		Debug.i(TAG, FUNC_TAG, "speedupAllowed=" + speedupAllowed + " ==> speedupDriverFound=" + speedupDriverFound + " nonSpeedupDriverFound=" + nonSpeedupDriverFound);
		
		if (prevSpeedupAllowed && !speedupAllowed) {
			if (!speedupDriverFound)
				Debug.w(TAG, FUNC_TAG, "Required speedup driver " + SPEEDUP_ALLOWED_DRIVER + " not found, speedup not allowed");
			if (nonSpeedupDriverFound)
				Debug.w(TAG, FUNC_TAG, "Non-speedup driver found, speedup not allowed");
		}
		
		Intent speedupAllowedIntent = new Intent("edu.virginia.dtc.SPEEDUP_ALLOWED");
		speedupAllowedIntent.putExtra("allowed", speedupAllowed);
		sendBroadcast(speedupAllowedIntent);
	}
    
	@Override
	public void onDestroy() {
		final String FUNC_TAG = "onDestroy";

		for(ScheduledFuture<?> timer : systemTimers)
			timer.cancel(true);
		
		simulationTimerIsRunning = false;
		Debug.i(TAG, FUNC_TAG, "");
        log_action(TAG, "onDestroy");
        wl.release();
        
        sensorManager.unregisterListener(sensorEventListener);
	}
	 
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	public long getCurrentTimeSeconds() {
		return (long)(System.currentTimeMillis()/1000);	  // Seconds since 1/1/1970		
	}
	
	public void log_action(String service, String action) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
	}
	
	private class gpsListener implements LocationListener
	{
		public void onLocationChanged(Location location) {
			final String FUNC_TAG = "onLocationChanged";
			
			Debug.i(TAG, FUNC_TAG, "Lat: "+location.getLatitude()+" Long: "+location.getLongitude());
			Debug.i(TAG, FUNC_TAG, "Loc Time: "+location.getTime()/1000);
			Debug.i(TAG, FUNC_TAG, "Sys Time: "+System.currentTimeMillis()/1000);
			Debug.i(TAG, FUNC_TAG, "Dif Time: "+Math.abs(System.currentTimeMillis()/1000 - location.getTime()/1000));
			
//			ContentValues gps = new ContentValues();
//			
//			//Collect GPS data
//			gps.put("time", getCurrentTimeSeconds());
//			gps.put("gpsLat", location.getLatitude());
//			gps.put("gpsLong", location.getLongitude());
//			gps.put("gpsAlt", location.getAltitude());
//			gps.put("gpsBearing", location.getBearing());
//			gps.put("gpsSpeed", location.getSpeed());
//			gps.put("gpsSysTime", location.getTime());
//			
//			//Insert data into the gps table
//			try 
//		    {
//		    	getContentResolver().insert(Biometrics.GPS_URI, gps);
//		    }
//		    catch (Exception e) 
//		    {
//		    	Debug.e(TAG, FUNC_TAG, "Error: "+((e.getMessage() == null) ? "null" : e.getMessage()));
//		    }
		}

		public void onProviderDisabled(String provider) {
			final String FUNC_TAG = "onProviderDisabled";
			Debug.i(TAG, FUNC_TAG, "Provider disabled!");
		}

		public void onProviderEnabled(String provider) {
			final String FUNC_TAG = "onProviderEnabled";
			Debug.i(TAG, FUNC_TAG, "Provider enabled!");
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
			final String FUNC_TAG = "onStatusChanged";
			Debug.i(TAG, FUNC_TAG, "GPS status changed!");
		}
		
	}
}
