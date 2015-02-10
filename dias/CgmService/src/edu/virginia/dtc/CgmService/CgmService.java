//*********************************************************************************************************************
//  Copyright 2011-2012 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************


package edu.virginia.dtc.CgmService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.State;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

/**
 * This is the main Activity that displays the current chat session.
 */
public class CgmService extends Service {	
	// Debugging
    private static final boolean D = true;
    private static final boolean LOGGING = true;
    
	// log_action priority levels
	private static final int LOG_ACTION_UNINITIALIZED = 0;
	private static final int LOG_ACTION_INFORMATION = 1;
	private static final int LOG_ACTION_DEBUG = 2;
	private static final int LOG_ACTION_NOT_USED = 3;
	private static final int LOG_ACTION_WARNING = 4;
	private static final int LOG_ACTION_SERIOUS = 5;
	
	// CGM Service commands
	public static final int CGM_SERVICE_CMD_NULL = 0;
	public static final int CGM_SERVICE_CMD_CALIBRATE = 1;
	public static final int CGM_SERVICE_CMD_DISCONNECT = 2;
	public static final int CGM_SERVICE_CMD_INIT = 3;
	
	// Commands for CGM Driver
	private static final int CGM_SERVICE2DRIVER_NULL = 0;
	private static final int CGM_SERVICE2DRIVER_REGISTER = 1;
	private static final int CGM_SERVICE2DRIVER_CALIBRATE = 2;
	private static final int CGM_SERVICE2DRIVER_DIAGNOSTIC = 3;
	private static final int CGM_SERVICE2DRIVER_DISCONNECT = 4;
	
	// Commands for CGM Service from Driver
	private static final int DRIVER2CGM_SERVICE_NEW_CGM_DATA = 0;
	private static final int DRIVER2CGM_SERVICE_PARAMETERS = 1;
	private static final int DRIVER2CGM_SERVICE_STATUS_UPDATE = 2;
	private static final int DRIVER2CGM_SERVICE_CALIBRATE_ACK = 3;

    //CGM Variables
 	public double EPSILON = 0.000001;						// Effectively zero for doubles
    
	private static final String TAG = "CGMService";
	
    public long tickReceiverNextTimeSeconds;
    public static final long tickReceiverDeltaTSeconds = 300;
    double cgm1_value;
    public Long last_Tvec_cgm1_time_secs=0l;
    
	// DiAs State Variable and Definitions - state for the system as a whole
	public int DIAS_STATE;
    
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	private Messenger driverReceiver = new Messenger(new driverMessageHandler());
	private Messenger driverTransmitter;
	private ServiceConnection driverConnection;
	
	private Cgm activeCgm;
	
	private SystemObserver sysObserver;

    @Override
    public void onCreate() {
    	final String FUNC_TAG = "onCreate";

        super.onCreate();
        
        if(D) 
        	Debug.e(TAG, FUNC_TAG, "+++ ON CREATE +++");
        
        Debug.i("CGM", FUNC_TAG,"CGM");
        log_action(TAG, "onCreate", LOG_ACTION_INFORMATION);
        
        DIAS_STATE = State.DIAS_STATE_STOPPED;
        
        // Register to receive Supervisor Control Algorithm Tick
        BroadcastReceiver AlgTickReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) 
            {
            	final String FUNC_TAG = "onReceive";
           }
        };
        registerReceiver(AlgTickReceiver, new IntentFilter("edu.virginia.dtc.intent.action.SUPERVISOR_CONTROL_ALGORITHM_TICK"));    
    	
    	// Set up a Notification for this Service
    	String ns = Context.NOTIFICATION_SERVICE;
    	NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
    	int icon = R.drawable.icon;
    	CharSequence tickerText = "CGM Service v1.0";
    	long when = System.currentTimeMillis();
    	Notification notification = new Notification(icon, tickerText, when);
    	Context context = getApplicationContext();
    	CharSequence contentTitle = "CGM Service v1.0";
    	CharSequence contentText = "CGM";
    	Intent notificationIntent = new Intent(this, CgmService.class);
    	PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
    	notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
    	final int CGM_ID = 1;
    	//mNotificationManager.notify(CGM_ID, notification);
    	
    	// Make this a Foreground Service
    	startForeground(CGM_ID, notification);
    	
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();  
		
		sysObserver = new SystemObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.SYSTEM_URI, true, sysObserver);
    }

    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
    	final String FUNC_TAG = "onStartCommand";

		int command = intent.getIntExtra("CGMCommand", 0);
		Debug.i(TAG, FUNC_TAG, "onStartCommand");
        
        Message msg;
        Bundle transmit;
        
        switch(command) {
    		case CGM_SERVICE_CMD_NULL:
    			Debug.i(TAG, FUNC_TAG, "CGM_SERVICE_CMD_NULL");
    			break;
    		case CGM_SERVICE_CMD_INIT:
    			Debug.i(TAG, FUNC_TAG, "CGM_SERVICE_CMD_INIT");
    			String intentName = intent.getStringExtra("driver_intent");
    			String driverName = intent.getStringExtra("driver_name");
    			
    			Debug.i(TAG, FUNC_TAG, "Intent: "+intentName+" Driver: "+driverName);
    			
    			bindToNewDriver(intentName, driverName);
    			break;
    		case CGM_SERVICE_CMD_CALIBRATE:
    			double BG = intent.getDoubleExtra("BG", 0);		
				int BGint = (int)BG;
				
				Debug.i(TAG, FUNC_TAG, "CGM_SERVICE_CMD_CALIBRATE > BG="+BG);
				
				msg = Message.obtain(null, CGM_SERVICE2DRIVER_CALIBRATE);
				transmit = new Bundle();
				
				transmit.putInt("calibration_value", BGint);
				msg.setData(transmit);
				
				msg.replyTo = driverReceiver;
				
				try {
					driverTransmitter.send(msg);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
    			break;
    		case CGM_SERVICE_CMD_DISCONNECT:
    			Message msg1 = Message.obtain(null, CGM_SERVICE2DRIVER_DISCONNECT, 0, 0);
    			
				try {
					driverTransmitter.send(msg1);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
    			break;
        	default:
       	        break;
        }
        return 0;
    }

    @Override
    public void onDestroy() {
    	final String FUNC_TAG = "onDestroy";

        super.onDestroy();
    } 
    
    @Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

    /*****************************************************************************************************
     * Driver Message Handler
     ****************************************************************************************************/
    class driverMessageHandler extends Handler {
    	@Override
    	public void handleMessage(Message msg)
    	{
        	final String FUNC_TAG = "handleMessage";
    		
    		Bundle response;
    		switch(msg.what)
    		{
	    		case DRIVER2CGM_SERVICE_NEW_CGM_DATA:
	    			response = msg.getData();
	    			activeCgm.time = response.getLong("time", 0L);
					activeCgm.value = response.getDouble("cgmValue", 0.0);
					activeCgm.trend = response.getInt("trend", 0);
					activeCgm.state = response.getInt("cgm_state", 0);
					
					Debug.i(TAG, FUNC_TAG,"CGM Driver Message > Time: "+activeCgm.time+", Value: "
						+activeCgm.value+", Trend: "+activeCgm.trend+", State: "+ CGM.stateToString(activeCgm.state));
					
					//TODO: for now the system will choose an active CGM and use it alone to update the database
					// Send Cgm object and store content values
					if(activeCgm!=null)
						sendCGMData(activeCgm);
					
	    			break;
	    		case DRIVER2CGM_SERVICE_PARAMETERS:
	    			Cursor c = getContentResolver().query(Biometrics.CGM_DETAILS_URI, null, null, null, null);
      				if (c.moveToLast())
      				{
      					activeCgm.min_valid_BG = c.getDouble(c.getColumnIndex("min_cgm"));
          				activeCgm.max_valid_BG = c.getDouble(c.getColumnIndex("max_cgm"));
          				
          				if(c.getInt(c.getColumnIndex("phone_calibration")) == 0)
          					activeCgm.phone_calibration = false;
          				else
          					activeCgm.phone_calibration = true;
      				}
      				c.close();
      				
	    			Debug.i(TAG, FUNC_TAG,"Minimum: "+activeCgm.min_valid_BG+" Maximum: "+activeCgm.max_valid_BG);
	    			break;
	    		case DRIVER2CGM_SERVICE_CALIBRATE_ACK:
	    			response = msg.getData();	    			
	    			activeCgm.cal_value = response.getInt("cal_value");
	    			long cal_time = response.getLong("cal_time", getCurrentTimeSeconds());

            	 	// Log the CGM time and value
    				log_action(TAG, "Calibration response value: "+activeCgm.cal_value, LOG_ACTION_INFORMATION);
    				
    				//Only notify DiAsService of the active CGM's calibration completion
    				if(activeCgm!=null)
    				{
    					activeCgm.last_valid_calib_time = (System.currentTimeMillis()/1000) - (Params.getInt(getContentResolver(), "cgm_history_hrs", 12)*60*60);
						
						Cursor c_time = getContentResolver().query(Biometrics.SMBG_URI, null, "isCalibration = 1", null, null);
						if(c_time != null)
						{
					    	if(c_time.getCount() > 0)
					    	{
					    		if(c_time.moveToLast())
					    		{
					    			//Get the latest CGM time from the database
					    			activeCgm.last_valid_calib_time = c_time.getLong(c_time.getColumnIndex("time"));
					    			Debug.i(TAG, FUNC_TAG, "Last valid meter calibration time is: "+activeCgm.last_valid_calib_time);
					    		}
					    	}
					    	else
					    		Debug.i(TAG, FUNC_TAG, "The SMBG database is empty so using the current time minus parameter for CGM History!");
					    	c_time.close();
						}
    					
						if(cal_time > activeCgm.last_valid_calib_time)
						{
							Debug.d(TAG, FUNC_TAG, "Storing Meter value: "+activeCgm.cal_value+" time: "+cal_time);
							
		            		ContentValues values = new ContentValues();
		            	    values.put("time", cal_time);
		            	    values.put("smbg", (double)activeCgm.cal_value);
		            	    values.put("isCalibration", 1);
		            	    values.put("isHypo", 0);
		            	    
		            	    try {
		            			getContentResolver().insert(Biometrics.SMBG_URI, values);
		            	    }
		            	    catch (Exception e) {
		            			Debug.i(TAG, FUNC_TAG, e.getMessage());
		            	    }
						}
    				}
	    			break;
    		}
    	}
    }
    
    private void sendCGMData(Cgm cgm)
    {
    	final String FUNC_TAG = "sendCGMData";

    	Cursor c_time = getContentResolver().query(Biometrics.CGM_URI, null, null, null, null);
    	if(c_time.getCount() > 0)
    	{
    		if(c_time.moveToLast())
    		{
    			//Get the latest CGM time from the database
    			cgm.last_valid_CGM_time = c_time.getLong(c_time.getColumnIndex("time"));
    			if(((System.currentTimeMillis()/1000)-cgm.last_valid_CGM_time) > (12*60*60))
				{
					cgm.last_valid_CGM_time = ((System.currentTimeMillis()/1000)-(12*60*60));
					Debug.i(TAG, FUNC_TAG, "The time can't exceed 12 hours so the valid time is: "+cgm.last_valid_CGM_time);
				}
				else
					Debug.i(TAG, FUNC_TAG, "The new valid CGM time is : "+cgm.last_valid_CGM_time);
    		}
    	}
    	else if(cgm.last_valid_CGM_time < 0)
    	{
			int hours = Params.getInt(getContentResolver(), "cgm_history_hrs", 12);
			long minutes = hours*60;
			long seconds = minutes*60;
			
			cgm.last_valid_CGM_time = (System.currentTimeMillis()/1000)-seconds; 
			Debug.i(TAG, FUNC_TAG, "There is no data in the table, so the last valid time is "+cgm.last_valid_CGM_time);
    	}
    	c_time.close();
    	
    	if(cgm.time > cgm.last_valid_CGM_time)
    	{    		
			// Write CGM data to biometricsContentProvider
			ContentValues values = new ContentValues();
			Debug.i("CGM", FUNC_TAG, "time="+cgm.time+", cgm="+cgm.value);
			
			//TODO: hardcoded levels, change this in the future
			if((cgm.value < 40 || cgm.value > 400) && (cgm.state == CGM.CGM_NORMAL))
			{
				//Basically, if the value is invalid there is some error and CGM cannot be normal
				cgm.state = CGM.CGM_DATA_ERROR;
			}
			
			values.put("time", cgm.time);
			values.put("diasState", DIAS_STATE);
			values.put("state", cgm.state);
			values.put("cgm", cgm.value);
			values.put("trend", cgm.trend);
			values.put("recv_time", getCurrentTimeSeconds());
			
		 	try {
		 		getContentResolver().insert(Biometrics.CGM_URI, values);
		 		Cursor c=getContentResolver().query(Biometrics.CGM_URI, null, null, null, null);
		 		Integer i=c.getCount();
		 		Debug.i("DB verifier", FUNC_TAG, i.toString());
		 		c.close();
		 	}
		 	catch (Exception e) {
		 		Debug.e("Error", FUNC_TAG,(e.getMessage() == null) ? "null" : e.getMessage());
		 	}
		 	// Log the CGM time and value
			log_action(TAG, "CGM="+cgm.value+", time="+cgm.time, LOG_ACTION_INFORMATION);
				
    	}
    	else
    	{
    		Debug.i(TAG, FUNC_TAG, "CGM value "+cgm.value+" is too old to add since it was at "+cgm.time);
    	}
    }
    
    /*****************************************************************************************************
     * Device Utility Functions
     ****************************************************************************************************/
	
    class SystemObserver extends ContentObserver 
    {	
    	private int count;
    	
    	public SystemObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "System Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		
    		count = 0;
    	}

       @Override
       public void onChange(boolean selfChange) 
       {
    	   this.onChange(selfChange, null);
       }		

       public void onChange(boolean selfChange, Uri uri) 
       {
    	   final String FUNC_TAG = "onChange";
    	   
    	   count++;
    	   Debug.i(TAG, FUNC_TAG, "System Observer: "+count);
    	   
    	   
    	   Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, new String[]{"diasState"}, null, null, null);
       	
    	   if(c!=null)
    	   {
    		   if(c.moveToLast())
    		   {
    			   DIAS_STATE = c.getInt(c.getColumnIndex("diasState"));
    		   }
    	   }
    	   c.close(); 
       }		
    }
    
	// Main method for binding to driver service, this method defines a new state
    // for storing the service connection and necessary messengers as well as the device(s)
    // attached to the driver
    private void bindToNewDriver(String intentName, String driverName)
    {	
    	final String FUNC_TAG = "bindToNewDriver";

    	// Create new state information storage for service connection to driver
    	activeCgm = new Cgm();
    	
    	// Setup device connection for this driver
    	driverConnection = new ServiceConnection(){
    		public void onServiceConnected(ComponentName className, IBinder service) {
    			Debug.i(TAG, FUNC_TAG,"onServiceConnected...");
    			driverTransmitter = new Messenger(service);
    			
				Message msg = Message.obtain(null, CGM_SERVICE2DRIVER_REGISTER);

				msg.replyTo = driverReceiver;
				
				// Register reply source for driver
				try {
					driverTransmitter.send(msg);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
    		}
    		
    		public void onServiceDisconnected(ComponentName className) {
    			Debug.e(TAG, FUNC_TAG,"onServiceDisconnected...");
    		}
    	};
    	
    	Intent intent = new Intent(intentName);
    	bindService(intent, driverConnection, Context.BIND_AUTO_CREATE);
    }
    

    /*****************************************************************************************************
     * Miscellaneous Utility Functions
     ****************************************************************************************************/
    
	public long getCurrentTimeSeconds() {
		final String FUNC_TAG = "getCurrentTimeSeconds";

		long currentTimeSeconds = (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970
		Debug.i(TAG, FUNC_TAG, "getCurrentTimeSeconds > returning System Time="+currentTimeSeconds);
		return currentTimeSeconds;
	}
	
	public void log_action(String service, String action, int priority) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("priority", priority);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
	}
}
