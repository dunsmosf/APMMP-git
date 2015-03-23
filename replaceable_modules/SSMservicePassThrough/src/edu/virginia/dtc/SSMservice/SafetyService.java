//*********************************************************************************************************************
//  Copyright 2011-2012 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.SSMservice;

import edu.virginia.dtc.SSMservice.SSM_param;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Constraints;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.Safety;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.SysMan.TempBasal;
import edu.virginia.dtc.Tvector.Tvector;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;
import android.os.Messenger;
import android.os.Message;
import android.os.PowerManager;
import android.os.Handler;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.RemoteException;
import android.app.Dialog;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

import java.lang.Long;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.lang.ref.WeakReference;


public class SafetyService extends Service {
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;	
	private static final boolean DEBUG_MODE = true;
    public static final String TAG = "SSMservice";
    public static final String IO_TEST_TAG = "SSMserviceIO";
	private static final boolean DEBUG_CREDIT_POOL = false;
	private static final double NEGATIVE_EPSILON = -0.000001;			// A bolus cannot be negative but it *can* be zero
	private static final double POSITIVE_EPSILON = 0.000001;
	public int cycle_duration_seconds = 300;
	public int cycle_duration_mins = cycle_duration_seconds/60;
	
	// SSMservice State Variable and Definitions - state for SSMservice only
	private int SSMSERVICE_STATE;
	public static final int SSMSERVICE_STATE_IDLE = 0;
	public static final int SSMSERVICE_STATE_PROCESSING = 1;
	public static final int SSMSERVICE_STATE_WAIT_CONSTRAINT = 2;
	public static final int SSMSERVICE_STATE_WAIT_CONFIRMATION = 3;
	public static final int SSMSERVICE_STATE_WAIT_PUMPSERVICE = 4;
	
	// DiAs State Variable and Definitions - state for the system as a whole
	private int DIAS_STATE;
	public static final int DIAS_STATE_STOPPED = 0;
	public static final int DIAS_STATE_OPEN_LOOP = 1;
	public static final int DIAS_STATE_CLOSED_LOOP = 2;
	public static final int DIAS_STATE_SAFETY_ONLY = 3;
	public static final int DIAS_STATE_SENSOR_ONLY = 4;
	
	// log_action priority levels
	private static final int LOG_ACTION_INFORMATION = 1;
	
    public static final String INSULIN_BASAL_BOLUS = "basal_bolus";
    public static final String INSULIN_MEAL_BOLUS = "meal_bolus";
    public static final String INSULIN_CORR_BOLUS = "corr_bolus";
    
    // Field definitions for STATE_ESTIMATE_TABLE
    public static final String TIME = "time";
    public static final String ENOUGH_DATA = "enough_data";
    public static final String ASYNCHRONOUS = "asynchronous";
    public static final String CGM = "CGM";
    public static final String IOB = "IOB";
    public static final String IOBLAST = "IOBlast";
    public static final String IOBLAST2 = "IOBlast2";
    public static final String GPRED = "Gpred";
    public static final String GBRAKES = "Gbrakes";
    public static final String GPRED_LIGHT = "Gpred_light";
    public static final String XI00 = "Xi00";
    public static final String XI01 = "Xi01";
    public static final String XI02 = "Xi02";
    public static final String XI03 = "Xi03";
    public static final String XI04 = "Xi04";
    public static final String XI05 = "Xi05";
    public static final String XI06 = "Xi06";
    public static final String XI07 = "Xi07";
    public static final String ISMEALBOLUS = "isMealBolus";
    public static final String GPRED_BOLUS = "Gpred_bolus";
    public static final String GPRED_1H = "Gpred_1h";
    public static final String CHOPRED = "CHOpred";
    public static final String ABRAKES = "Abrakes";
    public static final String UMAX_IOB = "Umax_IOB";
    public static final String CGM_CORR = "CGM_corr";
    public static final String IOB_CONTROLLER_RATE = "IOB_controller_rate";
    public static final String SSM_AMOUNT = "SSM_amount";
    public static final String STATE = "State";
    public static final String STOPLIGHT = "stoplight";
    public static final String STOPLIGHT2 = "stoplight2";
    public static final String SSM_STATE = "SSM_state";
    public static final String SSM_STATE_TIMESTAMP = "SSM_state_timestamp";
    public static final String DIAS_state = "DIAS_state";
    public static final String UTC_offset_seconds = "UTC_offset_seconds";
    public static final String BRAKES_COEFF = "brakes_coeff";
    
	public final String TAG_CREDITPOOL = "creditpool";
	    
	// Working storage for current cgm and insulin data
	private Tvector Tvec_cgm_mins, Tvec_insulin_rate1_seconds, Tvec_insulin_bolus1, Tvec_bolus_hist_seconds;
	private Tvector Tvec_basal_bolus_hist_seconds, Tvec_meal_bolus_hist_seconds, Tvec_corr_bolus_hist_seconds;
	private Tvector Tvec_credit, Tvec_spent, Tvec_net;
	private Tvector Tvec_IOB, Tvec_Rate, Tvec_GPRED;
	public double bolus_basal;
//	public double SSM_amount;
	public double bolus_meal;
	public double bolus_correction;
	public double bolusRequested;
	public double differential_basal_rate;
	public double credit_request, spend_request;
	public double interceptor_insulin;
	public boolean asynchronous;
	public boolean exercise;
	public static final int TVEC_SIZE = 288;				// 24 hours of samples at 12 samples per hour (5 minute samples)
	// Store most recent timestamps in seconds for each biometric Tvector
	Long last_Tvec_cgm_time_secs, last_Tvec_insulin_bolus1_time_secs, last_Tvec_requested_insulin_bolus1_time_secs, last_Tvec_insulin_credit_time_secs, last_state_estimate_time_secs;
	long calFlagTime = 0;
	long hypoFlagTime = 0;
	
    public SSM_param ssm_param;
	private Subject subject_data;
    public static ScheduledExecutorService constraintTimeoutScheduler = Executors.newScheduledThreadPool(2);
	public static ScheduledFuture<?> constraintTimer;
	
	public SSM_state_data state_data;
	
	/*
	 * 
	 *  Interface to DiAsService (our only Client)
	 * 
	 */
    public Messenger mMessengerToDiAsService = null;													/* Messenger for sending responses to the client (Application). */
    final Messenger mMessengerFromDiAsService = new Messenger(new IncomingHandlerFromDiAsService());		/* Target we publish for clients to send commands to IncomingHandlerFromDiAsService. */
 
	
//	private InsulinObserver insulinObserver;
	private static final double DEFAULT_IOB_CONSTRAINT_UNITS = 20.0;
    
	// InsulinPumpService commands
	public static final int PUMP_SERVICE_CMD_NULL = 0;
	public static final int PUMP_SERVICE_CMD_START_SERVICE = 1;
	public static final int PUMP_SERVICE_CMD_REGISTER_CLIENT = 2;
	public static final int PUMP_SERVICE_CMD_DELIVER_BOLUS = 3;
	public static final int PUMP_SERVICE_CMD_SET_BASAL_RATE = 4;
	public static final int PUMP_SERVICE_CMD_REQUEST_PUMP_STATUS = 5;
	public static final int PUMP_SERVICE_CMD_REQUEST_PUMP_HISTORY = 6;
	public static final int PUMP_SERVICE_CMD_STOP_SERVICE = 7;
	public static final int PUMP_SERVICE_CMD_SET_HYPO_TIME = 8;
	
	private static final double BASAL_MAX_CONSTRAINT = 0.5;
	private static final double CORRECTION_MAX_CONSTRAINT = 6.0;
	private static final double MEAL_MAX_CONSTRAINT = 18.0;
	
	private static final double BASAL_TOO_HIGH_LIMIT = 1.0;
	private static final double CORRECTION_TOO_HIGH_LIMIT = 30.0;
	private static final double MEAL_TOO_HIGH_LIMIT = 30.0;
	
	public static final int DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK = 25;	
	
	
	// Temporary Basal Rate variables
	private int temp_basal_status_code, temp_basal_percent_of_profile_basal_rate, temp_basal_owner;
	private long temp_basal_start_time, temp_basal_scheduled_end_time;


    private ServiceConnection mConnection;																/* Connection to the Pump Service. */
    Messenger mMessengerToPumpService = null;															/* Messenger for communicating with the Pump Service. */
    final Messenger mMessengerFromPumpService = new Messenger(new IncomingHandlerFromPumpService());		/* Target we publish for clients to send messages to IncomingHandler. */
    boolean mBound;																						/* Flag indicating whether we have called bind on the PumpService. */
    
    @Override
	public void onCreate() {
		SSMSERVICE_STATE = SSMSERVICE_STATE_IDLE;
		calFlagTime = 0;
		hypoFlagTime = 0;
		Toast toast = Toast.makeText(this, "SafetyService  onCreate: Service Created", Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
		toast.show();
		log_action(TAG, "onCreate", LOG_ACTION_INFORMATION);
		interceptor_insulin = 0.0;
		credit_request = 0.0;
		spend_request = 0.0;
		asynchronous = false;
		Tvec_IOB = new Tvector(TVEC_SIZE);
		Tvec_Rate = new Tvector(TVEC_SIZE);
		Tvec_GPRED = new Tvector(TVEC_SIZE);
		Tvec_cgm_mins = new Tvector(TVEC_SIZE);
		Tvec_insulin_rate1_seconds = new Tvector(TVEC_SIZE);
		Tvec_insulin_bolus1 = new Tvector(TVEC_SIZE);
		Tvec_bolus_hist_seconds = new Tvector(TVEC_SIZE);
		Tvec_credit = new Tvector(TVEC_SIZE);
		Tvec_spent = new Tvector(TVEC_SIZE);
		Tvec_net = new Tvector(TVEC_SIZE);
		Tvec_basal_bolus_hist_seconds = new Tvector(TVEC_SIZE);
		Tvec_meal_bolus_hist_seconds = new Tvector(TVEC_SIZE);
		Tvec_corr_bolus_hist_seconds = new Tvector(TVEC_SIZE);
		Tvec_spent = new Tvector(TVEC_SIZE);
		// Initialize most recent timestamps
		last_Tvec_cgm_time_secs = Long.valueOf(0);
		last_state_estimate_time_secs = Long.valueOf(0);
		last_Tvec_insulin_bolus1_time_secs = Long.valueOf(0);
		last_Tvec_insulin_credit_time_secs = Long.valueOf(0);
		last_Tvec_requested_insulin_bolus1_time_secs = Long.valueOf(0);
		
        // Create a ServiceConnection class for interacting with the main interface of the Pump Service.
        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                // This is called when the connection with the pump service has been
                // established, giving us the object we can use to
                // interact with the pump service.  We are communicating with the
                // pump service using a Messenger, so here we get a client-side
                // representation of that from the raw IBinder object.
            	final String FUNC_TAG = "onServiceConnected";
                debug_message(TAG, "onServiceConnected");
                mMessengerToPumpService = new Messenger(service);
                mBound = true;
                debug_message(TAG, "BluetoothPumpServiceStart");
//                debug_message(TAG, "InsulinPumpServiceStart");
                if (!mBound) return;
                try {
                		// Send a register-client message to the service with the client message handler in replyTo
                		Message msg = Message.obtain(null, PUMP_SERVICE_CMD_REGISTER_CLIENT, 0, 0);
                		msg.replyTo = mMessengerFromPumpService;
                		Bundle paramBundle = new Bundle();
                		paramBundle.putLong("simulatedTime", getCurrentTimeSeconds());
        				// Log the parameters for IO testing
        				if (true) {
                    		Bundle b = new Bundle();
                    		b.putString(	"description", "(SSMservice) >> PumpService, IO_TEST"+", "+FUNC_TAG+", "+
                    						"PUMP_SERVICE_CMD_REGISTER_CLIENT"+", "+
                    						"simulatedTime="+paramBundle.getLong("simulatedTime"));
                    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
        				}
                		msg.setData(paramBundle);
                		mMessengerToPumpService.send(msg);
                		// Send an initialize message to the service
                		msg = Message.obtain(null, PUMP_SERVICE_CMD_START_SERVICE, 0, 0);
                		paramBundle = new Bundle();
                		paramBundle.putLong("simulatedTime", getCurrentTimeSeconds());
                		msg.replyTo = mMessengerFromPumpService;
        				// Log the parameters for IO testing
        				if (true) {
                    		Bundle b = new Bundle();
                    		b.putString(	"description", "(SSMservice) >> PumpService, IO_TEST"+", "+FUNC_TAG+", "+
                    						"PUMP_SERVICE_CMD_START_SERVICE"+", "+
                    						"simulatedTime="+paramBundle.getLong("simulatedTime"));
                    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
        				}
                		msg.setData(paramBundle);
                		mMessengerToPumpService.send(msg);
                }
                catch (RemoteException e) {
            		Bundle b = new Bundle();
            		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
                	e.printStackTrace();
                }
           }
        
            public void onServiceDisconnected(ComponentName className) {
                // This is called when the connection with the service has been
                // unexpectedly disconnected -- that is, its process crashed.
                debug_message(TAG, "onServiceDisconnected");
                mMessengerToPumpService = null;
                mBound = false;
            }
        };
        
        // Set up a Notification for this Service
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        int icon = R.drawable.icon;
        CharSequence tickerText = "";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
        CharSequence contentTitle = "Safety Service v1.0";
        CharSequence contentText = "Monitoring Insulin Dosing";
        Intent notificationIntent = new Intent(this, SafetyService.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		Context context = getApplicationContext();
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        final int SAFETY_ID = 1;
        // Make this a Foreground Service
        startForeground(SAFETY_ID, notification);
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();  
	}

	
	@Override
	public void onDestroy() {
		Toast toast = Toast.makeText(this, "SafetyService Service Stopped", Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
		toast.show();
		Log.d(TAG, "onDestroy");
        log_action(TAG, "onDestroy", LOG_ACTION_INFORMATION);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return 0;
	}
	
    /* When binding to the service, we return an interface to our messenger for sending messages to the service. */
    @Override
    public IBinder onBind(Intent intent) {
        Toast toast = Toast.makeText(getApplicationContext(), "SafetyService binding to Application", Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
		toast.show();
        return mMessengerFromDiAsService.getBinder();
    }
    
    private boolean temporaryBasalRateActive() {
		final String FUNC_TAG = "temporaryBasalRateActive";
		boolean retValue = false;
		Cursor c = getContentResolver().query(Biometrics.TEMP_BASAL_URI, null, null, null, "start_time DESC LIMIT 1");
       	if(c!=null)
       	{
       		if(c.moveToLast()) {
       			long time = getCurrentTimeSeconds();
       			temp_basal_start_time = c.getLong(c.getColumnIndex("start_time"));
       			temp_basal_scheduled_end_time = c.getLong(c.getColumnIndex("scheduled_end_time"));
       			temp_basal_status_code = c.getInt(c.getColumnIndex("status_code"));
       			temp_basal_owner = c.getInt(c.getColumnIndex("owner"));
       			if(time >= temp_basal_start_time && time <= temp_basal_scheduled_end_time && temp_basal_status_code == TempBasal.TEMP_BASAL_RUNNING)
       				retValue = true;
       		}
   			c.close();
       	}
		return retValue;
	}

   
    public double getCurrentBasalProfile() {		
		double basal = 0.0;
		Calendar now = Calendar.getInstance();
		now.setTimeInMillis(getCurrentTimeSeconds()*1000);
		int now_minutes = 60*now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE); 
		int minutes_sup = 1440;
		
		Cursor c = getContentResolver().query(Biometrics.BASAL_PROFILE_URI, new String[]{"time", "value"}, null, null, "time ASC");
		if (c != null) {
			c.moveToLast();
			while(c.isBeforeFirst() != false) {
				int time = c.getInt(c.getColumnIndex("time"));
				if ((now_minutes >= time) && (now_minutes < minutes_sup)) {
					basal = c.getDouble(c.getColumnIndex("value"));
				}
				minutes_sup = time;
				c.moveToPrevious();
			}
			if (basal == 0.0) {
				c.moveToLast();
				basal = c.getDouble(c.getColumnIndex("value"));
			}
		}
		c.close();
		
		return basal;
	}

    /* Handles incoming commands from DiAsService. */
    class IncomingHandlerFromDiAsService extends Handler {
    	Bundle paramBundle;
    	Message response;
    	Bundle responseBundle;
    	@Override
        public void handleMessage(Message msg) {
        	final String FUNC_TAG = "IncomingDiAsServiceHandler";
        	if (SSMSERVICE_STATE == SSMSERVICE_STATE_IDLE) {
                switch (msg.what) {
    				case Safety.SAFETY_SERVICE_CMD_NULL:		// null command
    					Debug.i(TAG, FUNC_TAG,"SAFETY_SERVICE_CMD_NULL");
    					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                    		Bundle b = new Bundle();
                    		b.putString(	"description", "DiAsService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
                    						"SAFETY_SERVICE_CMD_NULL");
                    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
    					}
    					break;
    				case Safety.SAFETY_SERVICE_CMD_START_SERVICE:		// start service command
    					SSMSERVICE_STATE = SSMSERVICE_STATE_PROCESSING;
    					Debug.i(TAG, FUNC_TAG,"SAFETY_SERVICE_CMD_START_SERVICE");
    					paramBundle = msg.getData();
    					// Log the parameters for IO testing
    					if (true) {
                    		Bundle b = new Bundle();
                    		b.putString(	"description", "DiAsService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
                    						"SAFETY_SERVICE_CMD_START_SERVICE"+", "+
                    						"simulatedTime="+paramBundle.getLong("simulatedTime"));
                    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
    					}
    			        // Bind to the Insulin Pump Service
    					Intent intent = new Intent();
    					intent.setClassName("edu.virginia.dtc.PumpService", "edu.virginia.dtc.PumpService.PumpService");
    					bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    					SSMSERVICE_STATE = SSMSERVICE_STATE_IDLE;
    					break;
    				case Safety.SAFETY_SERVICE_CMD_REGISTER_CLIENT:
    					SSMSERVICE_STATE = SSMSERVICE_STATE_PROCESSING;
    					mMessengerToDiAsService = msg.replyTo;
    					debug_message(TAG, "mMessengerToDiAsService="+mMessengerToDiAsService);
    					// Log the parameters for IO testing
    					if (true) {
                    		Bundle b = new Bundle();
                    		b.putString(	"description", "DiAsService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
                    						"SAFETY_SERVICE_CMD_REGISTER_CLIENT");
                    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
    					}
    					SSMSERVICE_STATE = SSMSERVICE_STATE_IDLE;
    					break;
    				case Safety.SAFETY_SERVICE_CMD_REQUEST_BOLUS:
    					SSMSERVICE_STATE = SSMSERVICE_STATE_PROCESSING;
    					
    					subject_data = new Subject(getCurrentTimeSeconds(), getApplicationContext());
    					state_data = new SSM_state_data(getCurrentTimeSeconds());
    					paramBundle = msg.getData();
    					DIAS_STATE = paramBundle.getInt("DIAS_STATE", DIAS_STATE_OPEN_LOOP);
    					
    					if (checkSubjectData(subject_data)) {
    						Debug.i(TAG, FUNC_TAG, "Reading SSM Parameters...");
	    					ssm_param = new SSM_param(subject_data.subjectAIT, subject_data.subjectBasal, subject_data.subjectCR, subject_data.subjectCF, subject_data.subjectTDI, subject_data.subjectWeight);
	    					if (ssm_param.isValid) {
	        					Debug.i(TAG, FUNC_TAG,"SAFETY_SERVICE_CMD_REQUEST_BOLUS");
	        					subject_data.read(getCurrentTimeSeconds(), getApplicationContext());
//	        					if (subject_data.valid == false)		// Protect against state estimates with uninitialized data.
//	        						Debug.e(TAG, "SAFETY_SERVICE_CMD_REQUEST_BOLUS", "subject.valid == false");
	        					
	        					// Send a bolus using parameters received from the Application
	        					bolus_meal =  paramBundle.getDouble("bolus_meal", 0);
	        					bolus_correction =  paramBundle.getDouble("bolus_correction", 0);
	        					bolusRequested = bolus_meal + bolus_correction;
	        					differential_basal_rate = paramBundle.getDouble("differential_basal_rate", 0);		// In the range [-6U/hour:6U/hour]
	        					credit_request = 0;
	        					spend_request = 0;
	        					asynchronous = paramBundle.getBoolean("asynchronous", false);
	        					calFlagTime = (long)paramBundle.getLong("calFlagTime", 0);
	        					hypoFlagTime = (long)paramBundle.getLong("hypoFlagTime", 0);
	        					exercise = paramBundle.getBoolean("currentlyExercising", false);
	        					
	        					// Log the parameters for IO testing
	        					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
	                        		Bundle b = new Bundle();
	                        		b.putString(	"description", "DiAsService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
	                        						"SAFETY_SERVICE_CMD_REQUEST_BOLUS"+", "+
	                        						"simulatedTime="+paramBundle.getLong("simulatedTime")+", "+
	                        						"bolus_meal="+bolus_meal+", "+
	                        						"bolus_correction="+bolus_correction+", "+
	                        						"differential_basal_rate="+differential_basal_rate+", "+
	                        						"credit_request="+credit_request+", "+
	                        						"spend_request="+spend_request+", "+
	                        						"asynchronous="+asynchronous+", "+
	                        						"calFlagTime="+calFlagTime+", "+
	                        						"hypoFlagTime="+hypoFlagTime+", "+
	                        						"currentlyExercising="+exercise+", "+
	                        						"DIAS_STATE="+DIAS_STATE
	                        						);
	                        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
	        					}
        	    				SSMprocess();
	    					}
	    					else {
	    						Debug.e(TAG, FUNC_TAG, "SSM_param has not been initialized properly (brakes_param.k value is wrong)");
	    						returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_CALCULATE_RESPONSE, false);
	    					}
    					}
    					else
    					{
    						Debug.e(TAG, FUNC_TAG, "There is not enough data to request a bolus!");
    						returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_NORMAL, false);
    					}
    					    					
    					break;
    				case Safety.SAFETY_SERVICE_CMD_CALCULATE_STATE:
    					SSMSERVICE_STATE = SSMSERVICE_STATE_PROCESSING;
    					
    					subject_data = new Subject(getCurrentTimeSeconds(), getApplicationContext());
    					state_data = new SSM_state_data(getCurrentTimeSeconds());
    					paramBundle = msg.getData();
    					DIAS_STATE = paramBundle.getInt("DIAS_STATE", DIAS_STATE_OPEN_LOOP);
    					
    					if (checkSubjectData(subject_data)) {
    						
    						Debug.i(TAG, FUNC_TAG, "Reading SSM Parameters...");
	    					ssm_param = new SSM_param(subject_data.subjectAIT, subject_data.subjectBasal, subject_data.subjectCR, subject_data.subjectCF, subject_data.subjectTDI, subject_data.subjectWeight);
	    					
	    					if (ssm_param.isValid) {
		    					Debug.i(TAG, FUNC_TAG,"SAFETY_SERVICE_CMD_CALCULATE_STATE");
		    					subject_data.read(getCurrentTimeSeconds(), getApplicationContext());
		    					if (subject_data.valid == false)		// Protect against state estimates with uninitialized data.
		    						Debug.e(TAG, "SAFETY_SERVICE_CMD_REQUEST_BOLUS", "subject.valid == false");
		    					// Evaluate a bolus for safety with parameters received from the Application
		    					bolus_meal =  0.0;
		    					bolus_correction =  0.0;
		    					bolusRequested = 0.0;
		    					differential_basal_rate = 0.0;
		    					credit_request = 0.0;
		    					spend_request = 0.0;
		    					asynchronous = false;
		    					calFlagTime = (long)paramBundle.getLong("calFlagTime", 0);
		    					hypoFlagTime = (long)paramBundle.getLong("hypoFlagTime", 0);
		    					exercise = paramBundle.getBoolean("currentlyExercising", false);
		    					    					
		    					// Log the parameters for IO testing
		    					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
		                    		Bundle b = new Bundle();
		                    		b.putString(	"description", "DiAsService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
		                    						"SAFETY_SERVICE_CMD_CALCULATE_STATE"+", "+
		                    						"simulatedTime="+paramBundle.getLong("simulatedTime")+", "+
		                    						"DIAS_STATE="+DIAS_STATE
		                    						);
		                    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
		    					}
		    					SSMstateEstimation();
	    					}
	    					else {
	    						Debug.e(TAG, FUNC_TAG, "SSM_param has not been initialized properly (brakes_param.k value is wrong)");
	    						returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_CALCULATE_RESPONSE, false);
	    					}
    					}
    					else
    					{
    						Debug.e(TAG, FUNC_TAG, "There is not enough data to run calculate state!");
    						returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_CALCULATE_RESPONSE, false);
    					}
    					break;
    				default:
    					super.handleMessage(msg);
                }        		
        	}
        	else {
				Debug.e(TAG, FUNC_TAG,"SSMSERVICE_STATE == "+SSMSERVICE_STATE+", Message: "+msg.what+" ignored.");
				returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_BUSY);
        	}
        }
    }
    
    private boolean checkSubjectData(Subject s)
    {
    	final String FUNC_TAG = "checkSubjectData";
    	
    	if(s.subjectBasal.count() == 0 || s.subjectCF.count() == 0 || s.subjectCR.count() == 0 || s.valid == false)
    	{
    		Debug.e(TAG, FUNC_TAG, "The subject data is incomplete!");
    		
    		if(DIAS_STATE != State.DIAS_STATE_STOPPED && DIAS_STATE != State.DIAS_STATE_SENSOR_ONLY)
    		{
    			Debug.e(TAG, FUNC_TAG, "There is not enough insulin profile data to run the SSM, returning to Stopped/Sensor mode!");
    			
    			Bundle b = new Bundle();
        		b.putString("description", "SSMservice > There is insufficient profile data to be operating in this mode! Transitioning to Sensor/Stopped mode!");
        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_ALARM);
        		
        		Intent intent = new Intent();
        		intent.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
        		intent.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK);
        		startService(intent);
    		}
    		
    		return false;
    	}
    	
    	return true;
    }

    //***************************************************************************************
    // SSM_processing interface method
    //***************************************************************************************
    private void SSMprocess() {
    	final String FUNC_TAG = "SSMprocess";
    	
    	// Update Tvectors from the database
		fetchNewBiometricData();
		fetchStateEstimateData(getCurrentTimeSeconds());
		if(DEBUG_MODE)
		{
		   	Tvec_IOB.dump(TAG, "Tvec_IOB", 4);
		   	Tvec_GPRED.dump(TAG, "Tvec_GPRED", 4);
		   	Tvec_Rate.dump(TAG, "Tvec_Rate", 4);
		}
		if (Params.getBoolean(getContentResolver(), "enableIO", false)){
        	Bundle b = new Bundle();
        	b.putString(	"description", "Database >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        					"DATABASE_IO_TEST"+", "+
        					"Tvec_cgm_mins=("+Tvec_cgm_mins.get_last_time()+", "+Tvec_cgm_mins.get_last_value()+")"+", "+
        					"Tvec_bolus_hist_seconds=("+Tvec_bolus_hist_seconds.get_last_time()+", "+Tvec_bolus_hist_seconds.get_last_value()+")"+", "+
        					"Tvec_basal_bolus_hist_seconds=("+Tvec_basal_bolus_hist_seconds.get_last_time()+", "+Tvec_basal_bolus_hist_seconds.get_last_value()+")"+", "+
        					"Tvec_meal_bolus_hist_seconds=("+Tvec_meal_bolus_hist_seconds.get_last_time()+", "+Tvec_meal_bolus_hist_seconds.get_last_value()+")"+", "+
        					"Tvec_corr_bolus_hist_seconds=("+Tvec_corr_bolus_hist_seconds.get_last_time()+", "+Tvec_corr_bolus_hist_seconds.get_last_value()+")"
        					);
        	Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
		}

		// Compute the current IOB
		calculateCurrentIOB();
		
		// Calculate the current basal bolus in Units
		bolus_basal = (subject_data.basal + differential_basal_rate)/(60.0/5.0);
		state_data.SSM_amount = bolus_basal;
		
		writeStateEstimateData();
		
		//Checking extreme values on variables...
		if(!(checkExtremes(bolus_meal, "bolus_meal") && checkExtremes(bolus_correction, "bolus_correction") 
				&& checkExtremes(bolus_basal, "bolus_basal")
				&& checkExtremes(state_data.SSM_amount, "SSM_amount")))
		{
			Debug.e(TAG, FUNC_TAG, "There is an error in one of the variables in the SSM...returning to Sensor/Stopped Mode");
			
			Bundle b = new Bundle();
    		b.putString("description", "SSMservice > Check limits reports a component of bolus has an invalid value. System switches to Sensor/Stop Mode");
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_ALARM);
    		
    		Intent intent = new Intent();
    		intent.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
    		intent.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK);
    		startService(intent);
    		
    		return;
		}
		
		// Total bolus > 0?
//		if (bolus_basal+bolus_meal+bolus_correction > NEGATIVE_EPSILON) {
			if(asynchronous)	//Meal so no basal!
				sendBolusToPumpService(0.0, bolus_meal, bolus_correction);
			else				//Synchronous, so system generated
				sendBolusToPumpService(bolus_basal, bolus_meal, bolus_correction);
//		}
//		else {
//			Debug.i(TAG, FUNC_TAG, "No bolus intercept, but bolus is too small");
//			returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_NORMAL);				
//		}
    }    
    
    //***************************************************************************************
    // SSM_processing interface method
    //***************************************************************************************
    private void SSMstateEstimation() {
    	final String FUNC_TAG = "SSMstateEstimation";
    	
    	// Update Tvectors from the database
		fetchNewBiometricData();
		fetchStateEstimateData(getCurrentTimeSeconds());
		if(DEBUG_MODE)
		{
		   	Tvec_IOB.dump(TAG, "Tvec_IOB", 4);
		   	Tvec_GPRED.dump(TAG, "Tvec_GPRED", 4);
		   	Tvec_Rate.dump(TAG, "Tvec_Rate", 4);
		}
		if (Params.getBoolean(getContentResolver(), "enableIO", false)){
        	Bundle b = new Bundle();
        	b.putString(	"description", "Database >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        					"DATABASE_IO_TEST"+", "+
        					"Tvec_cgm_mins=("+Tvec_cgm_mins.get_last_time()+", "+Tvec_cgm_mins.get_last_value()+")"+", "+
        					"Tvec_bolus_hist_seconds=("+Tvec_bolus_hist_seconds.get_last_time()+", "+Tvec_bolus_hist_seconds.get_last_value()+")"+", "+
        					"Tvec_basal_bolus_hist_seconds=("+Tvec_basal_bolus_hist_seconds.get_last_time()+", "+Tvec_basal_bolus_hist_seconds.get_last_value()+")"+", "+
        					"Tvec_meal_bolus_hist_seconds=("+Tvec_meal_bolus_hist_seconds.get_last_time()+", "+Tvec_meal_bolus_hist_seconds.get_last_value()+")"+", "+
        					"Tvec_corr_bolus_hist_seconds=("+Tvec_corr_bolus_hist_seconds.get_last_time()+", "+Tvec_corr_bolus_hist_seconds.get_last_value()+")"
        					);
        	Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
		}
		

		// Compute the current IOB
		calculateCurrentIOB();

		writeStateEstimateData();
		returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_CALCULATE_RESPONSE);				
		
    }    
    
	
    private double calculateCurrentIOB() {
		final String FUNC_TAG = "calculateCurrentIOB";
    	double retValue = 0.0;
    	try {
    		if (state_data != null) {
    			// Calculate and store the insulin history
     			insulin_history_builder(	cycle_duration_mins, 
     										Tvec_insulin_rate1_seconds, 
     										Tvec_bolus_hist_seconds, 
     										Tvec_basal_bolus_hist_seconds, 
     										Tvec_meal_bolus_hist_seconds, 	
     										Tvec_corr_bolus_hist_seconds, 
     										subject_data.subjectBasal, 
     										Tvec_spent, 
     										ssm_param, 
     										getCurrentTimeSeconds(), 
     										state_data);
     			
     			// Compute insulin on board (IOB) based on time "bins" ending at the current time
 				state_data.IOB_meal = calculate_IOB(	state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds, 
											ssm_param.iob_param,  
											getCurrentTimeSeconds());
 				state_data.IOB_no_meal = calculate_IOB(	state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds, 
 											ssm_param.iob_param,  
 											getCurrentTimeSeconds());

				
 				Debug.i(TAG, FUNC_TAG, "IOB_meal="+state_data.IOB_meal+"IOB_no_meal="+state_data.IOB_no_meal);
    		}
    	}
		catch (Exception e) {
			Bundle b = new Bundle();
			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
			Event.addEvent(this, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
    	return retValue;
    }
    
	//
	// Creates two Tvectors of insulin delivery history for the last 8 hours:
	//		state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds
	//		state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds
	//
	private void insulin_history_builder(	int cycle_duration_mins,
											Tvector Tvec_rate_hist_seconds,
											Tvector Tvec_bolus_hist_seconds, 
											Tvector Tvec_basal_bolus_hist_seconds,	
											Tvector Tvec_meal_bolus_hist_seconds,
											Tvector Tvec_corr_bolus_hist_seconds,
											Tvector Tvec_basal_pattern, 				// UTC profile times in minutes
											Tvector Tvec_spent_hist_seconds, 
											SSM_param ssm_param,
											long time,
											SSM_state_data state_data) {
		final String FUNC_TAG = "insulin_history_builder";
		try {
			long ToD_minutes;
			double dvalue, dvalue1;
			int ii, kk;
			
			// Get the offset in minutes into the current day in the current time zone (based on cell phone time zone setting)
			TimeZone tz = TimeZone.getDefault();
			int UTC_offset_secs = tz.getOffset(time*1000)/1000;
			Debug.i(TAG, FUNC_TAG, "UTC_offset_secs="+UTC_offset_secs);
			
			// Create a vector of long discrete times in UTC seconds
			int hist_length = ssm_param.iob_param.hist_length;
			int ndiscrete = hist_length/cycle_duration_mins;
			state_data.discrete_time_seconds = new long[ndiscrete];
			for (ii=ndiscrete-1; ii>=0; ii--) {
				state_data.discrete_time_seconds[ndiscrete-1-ii] = (long)((time)-ii*cycle_duration_mins*60);
			}
			// The value of discrete_time_seconds[ndiscrete-1] is extended 3 minutes into the future from "time" to make sure that
			// we capture any very recent bolus which might have a delivery time > time due to pump clock skew
			state_data.discrete_time_seconds[ndiscrete-1] = state_data.discrete_time_seconds[ndiscrete-1] + 3*60;			
		
			// Dump insulin input Tvectors
			if(DEBUG_MODE)
			{
				Debug.i(TAG, FUNC_TAG, "                                  ");
				Debug.i(TAG, FUNC_TAG, "**********************************");
				Tvec_basal_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_basal_bolus_hist_seconds", 12);	
				Debug.i(TAG, FUNC_TAG, "                                  ");
				Tvec_corr_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_corr_bolus_hist_seconds", 12);	
				Debug.i(TAG, FUNC_TAG, "                                  ");
				Tvec_meal_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_meal_bolus_hist_seconds", 12);	
				Debug.i(TAG, FUNC_TAG, "**********************************");
				Debug.i(TAG, FUNC_TAG, "                                  ");
			}
		
			// *******************************************************
			// Construct Tvec_basal_hist_mins - the programmed basal history over discrete_time[] based on the Tvec_basal_pattern daily profile.
			// *******************************************************
			Tvector Tvec_basal_hist_seconds = new Tvector(ndiscrete);
			List<Integer> indices;	
			double basal_bolus;			
			for (ii=0; ii<ndiscrete; ii++) {											
				ToD_minutes = (state_data.discrete_time_seconds[ii]/60+UTC_offset_secs/60)%1440;			// ToD_minutes is number of minutes into the current day in local time
				if ((indices = Tvec_basal_pattern.find(">", -1, "<=", ToD_minutes)) != null) {
					basal_bolus = Tvec_basal_pattern.get_value(indices.get(indices.size()-1));
				}
				else if ((indices = Tvec_basal_pattern.find(">", -1, "<", -1)) != null) {
					basal_bolus = Tvec_basal_pattern.get_value(indices.get(indices.size()-1));					
				}
				else {
					basal_bolus = 0;
				}
				Tvec_basal_hist_seconds.put(state_data.discrete_time_seconds[ii], (double)basal_bolus);
			}
		
			// *******************************************************
			// Construct Tvec_ins_hist_IOB_with_meal_insulin_seconds for use in IOB calculation
			// Construct Tvec_ins_hist_IOB_no_meal_insulin_seconds for use in bolus interceptor
			// *******************************************************
			state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds = new Tvector(ndiscrete);
			state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds = new Tvector(ndiscrete);
			long ndiscrete_interval_start, ndiscrete_interval_end;
			for (ii=0; ii<ndiscrete; ii++) {
				// Set time intervals for assigning boluses to time intervals
				ndiscrete_interval_end = state_data.discrete_time_seconds[ii];
				if (ii == 0) {
					ndiscrete_interval_start = state_data.discrete_time_seconds[ii]-cycle_duration_mins*60;
				}
				else {
					ndiscrete_interval_start = state_data.discrete_time_seconds[ii-1];
				}
				
				// Add in the basal bolus contribution
				if ((indices = Tvec_basal_bolus_hist_seconds.find(">=", ndiscrete_interval_start, "<", ndiscrete_interval_end)) != null) { 
					dvalue = 0;
					Double Dval;
					for (kk=0; kk<indices.size(); kk++) {
						dvalue = dvalue + Tvec_basal_bolus_hist_seconds.get_value(indices.get(kk));
					}
					// Tvec_ins_hist_IOB_with_meal_insulin_seconds
					if ((Dval=state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)dvalue1);
					// Tvec_ins_hist_IOB_no_meal_insulin_seconds
					if ((Dval=state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)dvalue1);
				}
				
				// Add in the correction bolus contribution
				if ((indices = Tvec_corr_bolus_hist_seconds.find(">=", ndiscrete_interval_start, "<", ndiscrete_interval_end)) != null) {
					dvalue = 0;
					Double Dval;
					for (kk=0; kk<indices.size(); kk++) {
						dvalue = dvalue + Tvec_corr_bolus_hist_seconds.get_value(indices.get(kk));
					}
					// Tvec_ins_hist_IOB_with_meal_insulin_seconds
					if ((Dval=state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)dvalue1);
					// Tvec_ins_hist_IOB_no_meal_insulin_seconds
					if ((Dval=state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)dvalue1);
				}	
				
				// Add in the meal bolus contribution
				if ((indices = Tvec_meal_bolus_hist_seconds.find(">=", ndiscrete_interval_start, "<", ndiscrete_interval_end)) != null) {
					dvalue = 0;
					Double Dval;
					for (kk=0; kk<indices.size(); kk++) {
						dvalue = dvalue + Tvec_meal_bolus_hist_seconds.get_value(indices.get(kk));
					}
					// Tvec_ins_hist_IOB_with_meal_insulin_seconds
					if ((Dval=state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)dvalue1);
				}
			
				// If there are no boluses within this discrete_time interval then fill with basal.
				// Although this will overestimate basal delivery in the case of a missed basal bolus it also avoids the problem of treating time periods
				// in which the system was turned off as missed basal periods (for example at startup), resulting in artificially low (even negative) IOB.
				Double Dval;
				if ((Dval = state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) == null) {
					state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.put(state_data.discrete_time_seconds[ii], Tvec_basal_hist_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])/(60/cycle_duration_mins));
				}
				if ((Dval = state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) == null) {
					state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.put(state_data.discrete_time_seconds[ii], Tvec_basal_hist_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])/(60/cycle_duration_mins));
				}
			}
		
			// *******************************************************
			// Correct Tvec_ins_hist_IOB_with_meal_insulin_seconds by removing basal PROFILE insulin from history
			// *******************************************************
			
			for (ii=0; ii<ndiscrete; ii++) {
				Double Dval;
				double basal_value;
				if ((Dval = state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
					basal_value = 0.0;
					basal_value = Tvec_basal_hist_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])/(60/cycle_duration_mins);
					state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)(Dval.doubleValue()-basal_value));
				}
			}
			
			// *******************************************************
			// Correct Tvec_ins_hist_IOB_no_meal_insulin_seconds by removing basal PROFILE insulin from history
			// *******************************************************
			for (ii=0; ii<ndiscrete; ii++) {
				Double Dval;
				double basal_value;
				if ((Dval = state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
					basal_value = 0.0;
					basal_value = Tvec_basal_hist_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])/(60/cycle_duration_mins);
					state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)(Dval.doubleValue()-basal_value));
				}
			}
		}
		catch (Exception e) {
			Bundle b = new Bundle();
			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
			Event.addEvent(this, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}

		// Dump _history_builder() output Tvectors
		if(DEBUG_MODE)
		{
			Debug.i(TAG, FUNC_TAG, "                                  ");
			Debug.i(TAG, FUNC_TAG, "**********************************");
			state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.dump(TAG, FUNC_TAG+" > Tvec_ins_hist_IOB_with_meal_insulin_seconds", 12);	
			Debug.i(TAG, FUNC_TAG, "                                  ");
			state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.dump(TAG, FUNC_TAG+" > Tvec_ins_hist_IOB_no_meal_insulin_seconds", 12);	
			Debug.i(TAG, FUNC_TAG, "**********************************");
			Debug.i(TAG, FUNC_TAG, "                                  ");
		}
	}	  // end - insulin_history_builder
	

	
	public double calculate_IOB(Tvector Tvec_ins_hist_IOB, IOB_param iob_param, long time) {
		final String FUNC_TAG = "IOB_meal";
		int insulin_history_length = Tvec_ins_hist_IOB.count();
		int ii;
		double IOB = 0.0;
		try {
			for (ii=0; ii<insulin_history_length; ii++) {
				IOB = IOB + Tvec_ins_hist_IOB.get_value(ii)*iob_param.curves_nonrecursive[96-insulin_history_length+ii];
			}
			double IOB4 = 0.0;
			for (ii=0; ii<insulin_history_length; ii++) {
				IOB4 = IOB4 + Tvec_ins_hist_IOB.get_value(ii)*iob_param.fourcurves_nonrecursive[96-insulin_history_length+ii];
			}
			double IOB6 = 0.0;
			for (ii=0; ii<insulin_history_length; ii++) {
				IOB6 = IOB6 + Tvec_ins_hist_IOB.get_value(ii)*iob_param.sixcurves_nonrecursive[96-insulin_history_length+ii];
			}
			double IOB8 = 0.0;
			for (ii=0; ii<insulin_history_length; ii++) {
				IOB8 = IOB8 + Tvec_ins_hist_IOB.get_value(ii)*iob_param.eightcurves_nonrecursive[96-insulin_history_length+ii];
			}
			debug_message(TAG, "IOB > time="+time+",IOB4="+IOB4+", IOB6="+IOB6+",IOB8="+IOB8+",  IOB="+IOB);
		}
 		catch (Exception e) {
 			Bundle b = new Bundle();
 			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
 			Event.addEvent(this, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}	
		return IOB;
	}
	
    
    
	//***************************************************************************************
	// DiAsService interface method
	//***************************************************************************************
    public void returnStatusToDiAsService(int status) {
    	returnStatusToDiAsService(status, true);
    }
    
    public void returnStatusToDiAsService(int status, boolean bundle) {
		// Report error state back to the DiAsService
    	final String FUNC_TAG = "returnStatusToDiAsService";
    	
    	if (state_data == null)
    		return;
		
    	Message response;
		Bundle responseBundle;
		
		response = Message.obtain(null, status, 0, 0);
		
		if (bundle) {
			
			state_data.Processing_State = status;
			responseBundle = new Bundle();
			responseBundle.putInt("stoplight", Safety.GREEN_LIGHT);
			responseBundle.putInt("stoplight2", Safety.GREEN_LIGHT);
			responseBundle.putBoolean("isMealBolus", false);
			responseBundle.putDouble("SSM_amount", state_data.SSM_amount);
			responseBundle.putDouble("rem_error", 0);
			responseBundle.putDouble("basal", bolus_basal);
			responseBundle.putDouble("IOB", state_data.IOB_meal);
			responseBundle.putDouble("CR", state_data.CR);
			responseBundle.putDouble("latestCGM", (double)(Tvec_cgm_mins.get_last_value()));
			responseBundle.putLong("latestCGMTimeMinutes", Tvec_cgm_mins.get_last_time());
			responseBundle.putLong("last_Tvec_cgm_time_secs", last_Tvec_cgm_time_secs);
			response.setData(responseBundle);
			
			// Log the parameters for IO testing
			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
	    		Bundle b = new Bundle();
	    		b.putString(	"description", "(SSMservice) >> DiAsService, IO_TEST"+", "+FUNC_TAG+", "+
	    						"SAFETY_SERVICE_STATE_NORMAL"+", "+
	    						"stoplight="+responseBundle.getInt("stoplight")+", "+
	    						"stoplight2="+responseBundle.getInt("stoplight2")+", "+
	    						"SSM_amount="+responseBundle.getDouble("SSM_amount")+", "+
	    						"rem_error="+responseBundle.getDouble("rem_error")+", "+
	    						"bolusRequested="+responseBundle.getDouble("bolusRequested")+", "+
	    						"brakes_coeff="+responseBundle.getDouble("brakes_coeff")+", "+
	    						"basal="+responseBundle.getDouble("basal")+", "+
	    						"IOB="+responseBundle.getDouble("IOB")+", "+
	    						"CR="+responseBundle.getDouble("CR")+", "+
	    						"latestCGM="+responseBundle.getDouble("latestCGM")+", "+
	    						"latestCGMTimeMinutes="+responseBundle.getLong("latestCGMTimeMinutes")+", "+
	    						"last_Tvec_cgm_time_secs="+responseBundle.getLong("last_Tvec_cgm_time_secs")
	    						);
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
			}
			else if (status == Safety.SAFETY_SERVICE_STATE_INVALID_COMMAND) {
	    		Bundle b = new Bundle();
	    		b.putString(	"description", "(SSMservice) >> DiAsService, SAFETY_SERVICE_STATE_INVALID_COMMAND"+", "+FUNC_TAG
	    						);
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
			}
		}
		
		debug_message("BOLUS_TRACE", "SafetyService > PUMP_STATE_COMMAND_COMPLETE");
		
		try {
			mMessengerToDiAsService.send(response);
		} 
		catch (RemoteException e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
			e.printStackTrace();
		}
		SSMSERVICE_STATE = SSMSERVICE_STATE_IDLE;
    }

	//***************************************************************************************
	// PumpService interface method
	//***************************************************************************************
    private boolean checkExtremes(double value, String descrip)
    {
    	final String FUNC_TAG = "checkExtremes";
    	
    	if(Double.isInfinite(value) || Double.isNaN(value))
    	{
    		Debug.e(TAG, FUNC_TAG, descrip+" value is infinite or NaN! ("+value+")");
    		
    		Bundle b = new Bundle();
    		b.putString("description", "SSMservice > Check extremes reports "+descrip+" value has an invalid value ("+value+"). System switching to Sensor/Stop Mode");
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_INVALID_BOLUS, Event.makeJsonString(b), Event.SET_LOG);
    		return false;
    	}
    	
    	Debug.i(TAG, FUNC_TAG, descrip+" value ("+value+") is within limits");
    	return true;
    }
    
    private double checkLimits(double value, double params_limit, double absolute_limit, double too_high, String type)
    {
    	final String FUNC_TAG = "checkLimits";

    	if(Double.isInfinite(value) || Double.isNaN(value) || value >= too_high)
    	{
    		Debug.e(TAG, FUNC_TAG, "Value is NaN, infinite or too high!  Setting to zero!");
    		
    		Bundle b = new Bundle();
    		b.putString("description", "SSMservice > Check limits reports "+type+" component of bolus has an invalid value ("+value+"). System switches to Sensor/Stop Mode");
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_INVALID_BOLUS, Event.makeJsonString(b), Event.SET_LOG);
    		
    		return -1.0;
    	}
    	
    	if (value < POSITIVE_EPSILON)
    	{
    		if (value < NEGATIVE_EPSILON)
    		{
    			Bundle b = new Bundle();
        		b.putString("description", "SSMservice > Check limits reports "+type+" component of bolus is negative: "+value+". Value constrained to 0.0");
        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_INVALID_BOLUS, Event.makeJsonString(b), Event.SET_LOG);
    		}
    		
    		return 0.0;
    	}
    	
    	double effective_limit;
    	
    	if (params_limit > 0.0) {
    		effective_limit = Math.min(params_limit, absolute_limit);
    	}
    	else {
    		effective_limit = absolute_limit;
    	}
    	
    	if(value > effective_limit)
    	{
    		Debug.e(TAG, FUNC_TAG, "Value being constrained to Limit: "+effective_limit);
    		
    		Bundle b = new Bundle();
    		b.putString("description", "SSMservice > Safety Service constraint was applied to the '"+type+"' component of bolus, "+value+"U requested, constrained to "+effective_limit+"U.");
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_INVALID_BOLUS, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
    		
    		return effective_limit;
    	}
    	
    	Debug.i(TAG, FUNC_TAG, "Value is valid and within limits - Value: "+value+" Limit: "+effective_limit);
		return value;
    }
    
    private void sendBolusToPumpService(double bolus_basal, double bolus_meal, double bolus_correction) {
    	final String FUNC_TAG = "sendBolusToPumpService";
    	Bundle paramBundle;
    	Message response, msg;
    	Bundle responseBundle;
    	
		try {
			// Send a command to the Pump Service to deliver the bolus.
			msg = Message.obtain(null, PUMP_SERVICE_CMD_DELIVER_BOLUS, 0, 0);
			paramBundle = new Bundle();
			paramBundle.putBoolean("asynchronous", asynchronous);
			paramBundle.putInt("DIAS_STATE", DIAS_STATE);
			
//			// (Parameters use have been prevented by using twice the hard-coded constraint value in the comparison)
//			double param_basal_max_constraint, param_correction_max_constraint, param_meal_max_constraint;
//			
//			try {
//				param_basal_max_constraint = Params.getDouble(getContentResolver(), "basal_max_constraint", BASAL_MAX_CONSTRAINT);
//			}
//			catch (Exception e) {
//				param_basal_max_constraint = BASAL_MAX_CONSTRAINT;
//			}
//			
//			try {
//				param_correction_max_constraint = Params.getDouble(getContentResolver(), "correction_max_constraint", CORRECTION_MAX_CONSTRAINT);
//			}
//			catch (Exception e) {
//				param_correction_max_constraint = CORRECTION_MAX_CONSTRAINT;
//			}
//			
//			try {
//				param_meal_max_constraint = Params.getDouble(getContentResolver(), "meal_max_constraint", MEAL_MAX_CONSTRAINT);
//			}
//			catch (Exception e) {
//				param_meal_max_constraint = MEAL_MAX_CONSTRAINT;
//			}
//
//			//TODO: Reactivate/Uncomment the use of parameters once we are comfortable with hard-coded limits
//			
//			bolus_basal = checkLimits(bolus_basal, param_basal_max_constraint, BASAL_MAX_CONSTRAINT, BASAL_TOO_HIGH_LIMIT, "basal");
//			bolus_correction = checkLimits(bolus_correction, param_correction_max_constraint, CORRECTION_MAX_CONSTRAINT, CORRECTION_TOO_HIGH_LIMIT, "correction");
//			bolus_meal = checkLimits(bolus_meal, param_meal_max_constraint, MEAL_MAX_CONSTRAINT, MEAL_TOO_HIGH_LIMIT, "meal");
			
			//Check limits...
			bolus_basal = checkLimits(bolus_basal, BASAL_MAX_CONSTRAINT, BASAL_MAX_CONSTRAINT, BASAL_TOO_HIGH_LIMIT, "basal");
			bolus_correction = checkLimits(bolus_correction, CORRECTION_MAX_CONSTRAINT, CORRECTION_MAX_CONSTRAINT, CORRECTION_TOO_HIGH_LIMIT, "correction");
			bolus_meal = checkLimits(bolus_meal, MEAL_MAX_CONSTRAINT, MEAL_MAX_CONSTRAINT, MEAL_TOO_HIGH_LIMIT, "meal");
			
			if(bolus_basal < 0.0 || bolus_correction < 0.0 || bolus_meal < 0.0)
			{
				bolus_basal = 0.0;
				bolus_correction = 0.0;
				bolus_meal = 0.0;
				
				Bundle b = new Bundle();
	    		b.putString("description", "SSMservice > Check limits reports a component of bolus has an invalid value. System switches to Sensor/Stop Mode");
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_INVALID_BOLUS, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_ALARM);
	    		
	    		Intent intent = new Intent();
	    		intent.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
	    		intent.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK);
	    		startService(intent);
			}
			
			if((bolus_meal) > MEAL_MAX_CONSTRAINT)
			{
				Debug.w(TAG, FUNC_TAG, "Limiting the pre-auth and meal bolus to: "+MEAL_MAX_CONSTRAINT);
				
				bolus_meal = MEAL_MAX_CONSTRAINT;
				
				Bundle b = new Bundle();
	    		b.putString("description", "SSMservice > Meal boluses was greater than the meal constraint." +
	    				" Value constrained to "+bolus_meal+"U (meal) ");
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_INVALID_BOLUS, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
				
			}
			
			//paramBundle.putDouble(INSULIN_BASAL_BOLUS, bolus_basal);
			if (temporaryBasalRateActive()){
				
				Cursor c = getContentResolver().query(Biometrics.TEMP_BASAL_URI, null, null, null, "start_time DESC LIMIT 1");
		       	if(c!=null)
		       	{
		       		if(c.moveToLast()) {
		       			temp_basal_percent_of_profile_basal_rate = c.getInt(c.getColumnIndex("percent_of_profile_basal_rate"));
		       			temp_basal_owner = c.getInt(c.getColumnIndex("owner"));
		       		}
		       	}
			}   	
    		
			if (temporaryBasalRateActive()&&temp_basal_owner==TempBasal.TEMP_BASAL_OWNER_SSMSERVICE){		
			    double basal = getCurrentBasalProfile();
		       	paramBundle.putDouble(INSULIN_BASAL_BOLUS, basal/12*temp_basal_percent_of_profile_basal_rate/100);
			}
			else{
				paramBundle.putDouble(INSULIN_BASAL_BOLUS, bolus_basal);
			}
			
    		paramBundle.putDouble(INSULIN_MEAL_BOLUS, bolus_meal); 
    		paramBundle.putDouble(INSULIN_CORR_BOLUS, bolus_correction); 
			paramBundle.putDouble("pre_authorized", 0);
			double pre = 0;
			state_data.pre_authorized = 0.0;
			paramBundle.putDouble("bolus_max", ssm_param.filter.bolus_max);
     		paramBundle.putLong("simulatedTime", getCurrentTimeSeconds());
			putTvector(paramBundle, subject_data.subjectBasal, "Basaltimes", "Basalvalues");
			msg.setData(paramBundle);
			// Log the parameters for IO testing
			if (Params.getBoolean(getContentResolver(), "enableIO", true)) {
				Tvector tvec_temp = getTvector(paramBundle, "Basaltimes", "Basalvalues");
	    		Bundle b = new Bundle();
	    		b.putString(	"description", "(SSMservice) >> PumpService, IO_TEST"+", "+FUNC_TAG+", "+
	    						"PUMP_SERVICE_CMD_DELIVER_BOLUS"+", "+
	    						", lastBolusSimulatedTime="+paramBundle.getLong("lastBolusSimulatedTime")+", "+
	    						", asynchronous="+paramBundle.getBoolean("asynchronous")+", "+
	    						", DIAS_STATE="+paramBundle.getInt("DIAS_STATE")+", "+
	    						", pre_authorized="+paramBundle.getDouble("pre_authorized")+", "+
	    						", bolus_max="+paramBundle.getDouble("bolus_max")+", "+
	    						", INSULIN_BASAL_BOLUS="+paramBundle.getDouble(INSULIN_BASAL_BOLUS)+", "+
	    						", INSULIN_MEAL_BOLUS="+paramBundle.getDouble(INSULIN_MEAL_BOLUS)+", "+
	    						", INSULIN_CORR_BOLUS="+paramBundle.getDouble(INSULIN_CORR_BOLUS)+", "+
	    						", Tvec_basal=("+tvec_temp.get_last_time()+", "+tvec_temp.get_last_value()+")"
	    						);
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
			}
			mMessengerToPumpService.send(msg);
			state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_AWAITING_PUMP_RESPONSE;
		}
		catch (RemoteException e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
			e.printStackTrace();
			state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_NORMAL;
		}
	}
	

    // Handles messages from PumpService
    class IncomingHandlerFromPumpService extends Handler {
    	Message response;
    	Bundle responseBundle;
    	@Override
    	public void handleMessage(Message msg) {
    	   final String FUNC_TAG = "handleMessage";
           switch (msg.what) {
           		case Pump.PUMP_STATE_IDLE:
           			debug_message(TAG, "PUMP_STATE_COMMAND_IDLE");
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_COMMAND_IDLE"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
    				Bundle responseBundle = msg.getData();
    				SSMSERVICE_STATE = SSMSERVICE_STATE_IDLE;
           			break;
           		case Pump.PUMP_STATE_COMPLETE:
           			debug_message(TAG, "PUMP_STATE_COMMAND_COMPLETE");
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_COMMAND_COMPLETE"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
           			returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_NORMAL);
               		break;
           		case Pump.PUMP_STATE_PUMP_ERROR:
           			debug_message(TAG, "PUMP_STATE_PUMP_ERROR");
           			state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_PUMP_ERROR;
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_PUMP_ERROR"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
           			returnStatusToDiAsService(state_data.Processing_State);
           			break;
           		case Pump.PUMP_STATE_COMMAND_ERROR:
           			debug_message(TAG, "PUMP_STATE_COMMAND_ERROR");
           			state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_PUMP_ERROR;
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_COMMAND_ERROR"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
           			returnStatusToDiAsService(state_data.Processing_State);
           			break;
           		case Pump.PUMP_STATE_NO_RESPONSE:
           			debug_message(TAG, "PUMP_STATE_NO_RESPONSE");
           			state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_PUMP_ERROR;
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_NO_RESPONSE"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
           			returnStatusToDiAsService(state_data.Processing_State);
           			break;
           		default:
           			super.handleMessage(msg);
        			SSMSERVICE_STATE = SSMSERVICE_STATE_IDLE;
           }
       }
   }
    
	public void fetchNewBiometricData() {
    	final String FUNC_TAG = "fetchNewBiometricData";
		try {
			// Fetch cgm
			Long t_minus_8_hours = new Long(getCurrentTimeSeconds() - 8*3600);
//			Cursor c=getContentResolver().query(Biometrics.CGM_URI, null, "time > "+last_Tvec_cgm_time_secs.toString(), null, null);
			Cursor c=getContentResolver().query(Biometrics.CGM_URI, null, "time > "+t_minus_8_hours.toString(), null, null);
			long last_time_temp_secs = 0;
			double cgm_value;
			if (c != null) {
				if (c.moveToFirst()) {
					do{
						cgm_value = (double)c.getDouble(c.getColumnIndex("cgm"));
						if (cgm_value>=39.0 && cgm_value<=401.0) {
							// Save the latest timestamp from the retrieved data
							if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
								last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
							}
							// Round incoming time in seconds down to the nearest minute
							Tvec_cgm_mins.put(c.getLong(c.getColumnIndex("time"))/60, cgm_value);
						}
					} while (c.moveToNext());
					last_Tvec_cgm_time_secs = last_time_temp_secs;
				}
				c.close();
			}

			// Fetch delivered insulin data
			c=getContentResolver().query(Biometrics.INSULIN_URI, null, "deliv_time > "+t_minus_8_hours.toString(), null, null);
			last_time_temp_secs = 0;
			if (c != null) {
				if (c.moveToFirst()) {
					do{
						// Save the latest timestamp from the retrieved data
						if (c.getLong(c.getColumnIndex("deliv_time")) > last_time_temp_secs) {
							last_time_temp_secs = c.getLong(c.getColumnIndex("deliv_time"));
						}
						// Insulin time in seconds
						Tvec_bolus_hist_seconds.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_total")));
						Tvec_basal_bolus_hist_seconds.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_basal")));
						Tvec_meal_bolus_hist_seconds.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_meal")));
						Tvec_corr_bolus_hist_seconds.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_corr")));
					} while (c.moveToNext());
					last_Tvec_insulin_bolus1_time_secs = last_time_temp_secs;
				}
				c.close();
			}
			if(DEBUG_MODE)
			{
				Tvec_bolus_hist_seconds.dump(TAG, "Tvec_bolus_hist_seconds", 8);
				Tvec_basal_bolus_hist_seconds.dump(TAG, "Tvec_basal_bolus_hist_seconds", 8);
				Tvec_meal_bolus_hist_seconds.dump(TAG, "Tvec_meal_bolus_hist_seconds", 8);
				Tvec_corr_bolus_hist_seconds.dump(TAG, "Tvec_corr_bolus_hist_seconds", 8);
			}
			
			if (!DEBUG_CREDIT_POOL) {
				// Fetch insulin credit data
				Long time_one_hour_ago_seconds = new Long(0);
				c=getContentResolver().query(Biometrics.INSULIN_CREDIT_URI, null, time_one_hour_ago_seconds.toString(), null, null);
				if (c != null) {
					if (c.moveToFirst()) {
						do{
							// Insulin time in seconds
							Tvec_credit.put_with_replace(c.getLong(c.getColumnIndex("time")), (double)c.getDouble(c.getColumnIndex("credit")));
							Tvec_spent.put_with_replace(c.getLong(c.getColumnIndex("time")), (double)c.getDouble(c.getColumnIndex("spent")));
							Tvec_net.put_with_replace(c.getLong(c.getColumnIndex("time")), (double)c.getDouble(c.getColumnIndex("net")));
						} while (c.moveToNext());
					}
					c.close();
				}
			}
			else {
				Tvec_credit = new Tvector(TVEC_SIZE);
				Tvec_credit.put(getCurrentTimeSeconds(), 1.0);
				Tvec_spent = new Tvector(TVEC_SIZE);
				Tvec_net = new Tvector(TVEC_SIZE);
			}
			
		}
        catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
        	Log.e("fetchNewBiometricData > Error SafetyService", e.toString());
        }
	}
	
	public boolean fetchStateEstimateData(long time) {
		final String FUNC_TAG = "fetchStateEstimateData";
		boolean return_value = false;
		long last_time_temp_secs = 0;
		Long Time = new Long(time);
		Cursor c=getContentResolver().query(Biometrics.STATE_ESTIMATE_URI, null, "time > "+last_state_estimate_time_secs.toString(), null, null);
		long state_estimate_time;
		if (c != null) {
			if (c.moveToFirst()) {
				do{
					if (c.getInt(c.getColumnIndex("asynchronous")) == 0) {
						state_estimate_time = c.getLong(c.getColumnIndex("time"));
						Tvec_IOB.put(state_estimate_time, c.getDouble(c.getColumnIndex("IOB")));
						Tvec_Rate.put(state_estimate_time, c.getDouble(c.getColumnIndex(IOB_CONTROLLER_RATE)));
						Tvec_GPRED.put(state_estimate_time, c.getDouble(c.getColumnIndex("GPRED")));
						if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
							last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
						}
						return_value = true;
					}
					last_state_estimate_time_secs = last_time_temp_secs;
				} while (c.moveToNext());
			}
			c.close();
		}
		return return_value;
	}
	
	public void writeStateEstimateData() {
    	final String FUNC_TAG = "writeStateEstimateData";
    	if (state_data == null) {
    		return;
    	}
    	else {
    		debug_message(TAG, "writeStateEstimateData > state_data.CGM = "+state_data.CGM);
    	    ContentValues values = new ContentValues();
    	    values.put(TIME, getCurrentTimeSeconds());
    	    if (state_data.asynchronous)
    	    	values.put(ASYNCHRONOUS, 1);
    	    else
    	    	values.put(ASYNCHRONOUS, 0);
    	    values.put(CGM, state_data.CGM);
    	    values.put(IOB, state_data.IOB_meal);
    	    values.put(IOBLAST, state_data.IOB_meal);
    	    values.put(IOBLAST2, state_data.IOB_no_meal);
    	    values.put(SSM_AMOUNT, state_data.SSM_amount);
    	    values.put(STATE, state_data.Processing_State);
    	    values.put(SSM_STATE, state_data.Tvec_state.get_last_value());
    	    values.put(SSM_STATE_TIMESTAMP, state_data.Tvec_state.get_last_time());
    	    values.put(DIAS_state, DIAS_STATE);											// Save the Current DiAs state
    		long timeSeconds = getCurrentTimeSeconds();
    		TimeZone tz = TimeZone.getDefault();
    		int UTC_offset_secs = tz.getOffset(timeSeconds*1000)/1000;
    		values.put(UTC_offset_seconds, UTC_offset_secs);					// Save the current offset from UTC in seconds
    	    try {
        			getContentResolver().insert(Biometrics.STATE_ESTIMATE_URI, values);
    	    }
    	    catch (Exception e) {
        		Bundle b = new Bundle();
        		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
        		Log.e(TAG, e.toString());
    	    }
    	}
	}
	
	public long getCurrentTimeSeconds() {
		return (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970		
	}
	
    public void putTvector(Bundle bundle, Tvector tvector, String timeKey, String valueKey) {
		long[] times = new long[tvector.count()];
		double[] values = new double[tvector.count()];
		int ii;
		for (ii=0; ii<tvector.count(); ii++) {
			times[ii] = tvector.get_time(ii).longValue();
			values[ii] = tvector.get_value(ii).doubleValue();
		}
		bundle.putLongArray(timeKey, times);
		bundle.putDoubleArray(valueKey, values);
    }
	
    public Tvector getTvector(Bundle bundle, String timeKey, String valueKey) {
		int ii;
		long[] times = bundle.getLongArray(timeKey);
		double[] values = bundle.getDoubleArray(valueKey);
		Tvector tvector = new Tvector(times.length);
		for (ii=0; ii<times.length; ii++) {
			tvector.put(times[ii], values[ii]);
		}
		return tvector;
    }

	// This method causes the spend_request to be stored in the insulincredit table
	// by associating it with a credit entry which is the same size or larger.
	public void storeSpentInsulin(double spent_value, long current_time_seconds) {
    	final String FUNC_TAG = "storeSpentInsulin";
		// Fetch credit pool data for the last hour from the database
		Tvector Tvec_credit = new Tvector(TVEC_SIZE);
		Tvector Tvec_spent = new Tvector(TVEC_SIZE);
		Tvector Tvec_net = new Tvector(TVEC_SIZE);
		Tvector Tvec_row_id = new Tvector(TVEC_SIZE);
		Long time_one_hour_ago_seconds = new Long(current_time_seconds - 3600*3);
		Cursor c=getContentResolver().query(Biometrics.INSULIN_CREDIT_URI, null, time_one_hour_ago_seconds.toString(), null, null);
		if (c != null) {
			debug_message(TAG_CREDITPOOL, "SafetyService > storeSpentInsulin > INSULIN_CREDIT_URI rows in last hour="+c.getCount());
			if (c.moveToFirst()) {
				do{
					try {
						Tvec_row_id.put(c.getLong(c.getColumnIndex("time")), (double)c.getInt(c.getColumnIndex("_id")));
						Tvec_credit.put(c.getLong(c.getColumnIndex("time")), (double)c.getDouble(c.getColumnIndex("credit")));
						Tvec_spent.put(c.getLong(c.getColumnIndex("time")), (double)c.getDouble(c.getColumnIndex("spent")));
						Tvec_net.put(c.getLong(c.getColumnIndex("time")), (double)c.getDouble(c.getColumnIndex("net")));
					}
					catch (Exception e) {
	            		Bundle b = new Bundle();
	            		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
	            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
						debug_message(TAG_CREDITPOOL, "SafetyService > storeSpentInsulin > Error reading INSULIN_CREDIT_URI");
					}
				} while (c.moveToNext());
			}
			c.close();
		}
		
		// Update Tvec_spent and Tvec_net
		int ii = 0;
		int ii_count = Tvec_row_id.count();
		double spent_value_accum = spent_value;
		double net_value_ii, spent_value_ii;
		debug_message(TAG_CREDITPOOL,"SafetyService > storeSpentInsulin > ii_count="+ii_count+", spent_value_accum="+spent_value_accum);
		while (ii < ii_count && spent_value_accum > POSITIVE_EPSILON) {
			net_value_ii = Tvec_net.get_value(ii);
			spent_value_ii = Tvec_spent.get_value(ii);
			if (net_value_ii >= spent_value_accum) {
				// There is enough net credit in this element to satisfy the remaining spent_value_accum
				Tvec_spent.replace_value(spent_value_ii+spent_value_accum, ii);
				Tvec_net.replace_value(net_value_ii-spent_value_accum, ii);
				spent_value_accum = 0.0;
			}
			else if (net_value_ii > 0) {
				// There is some net credit left in this element which can be used to reduce spent_value_accum
				Tvec_spent.replace_value(spent_value_ii+net_value_ii, ii);
				Tvec_net.replace_value(0.0, ii);
				spent_value_accum = spent_value_accum - net_value_ii;
			}
			ii++;
		}
		// Verify that all of spent_value has been assigned to Tvec_spent
		if (spent_value_accum > POSITIVE_EPSILON) {
			// Error condition!
			Log.e(TAG, "storeSpentInsulin > Error: Not enough credit to store spent insulin > spent_value_accum="+spent_value_accum+", ii="+ii);
		}
				
		// Write the credit pool information for the last hour back to the database
		int row_id;
		long row_time;
		ContentValues values = new ContentValues();
		ii_count = Tvec_row_id.count();
		for (ii=0; ii<ii_count; ii++) {
			row_id = (int)Math.round(Tvec_row_id.get_value(ii));
			values.put("_id", row_id);
			values.put("time", Tvec_row_id.get_time(ii));
			values.put("credit", Tvec_credit.get_value(ii));
			values.put("spent", Tvec_spent.get_value(ii));				
			values.put("net", Tvec_net.get_value(ii));
			debug_message(TAG_CREDITPOOL, "SafetyService > storeSpentInsulin > row_id="+row_id+", time="+values.getAsString("time")+
					", credit="+values.getAsString("credit")+
					", spent="+values.getAsString("spent")+
					", net="+values.getAsString("net"));
		    try {
				getContentResolver().update(Biometrics.INSULIN_CREDIT_URI, values, "_id="+row_id, null);
		    }
		    catch (Exception e) {
//		    		log_action(TAG, "storeSpentInsulin > Error writing INSULIN_CREDIT_URI with _id="+row_id, current_time_seconds);
        		Bundle b = new Bundle();
        		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
				Log.e(TAG, e.toString());
		    }
		}
	}

	public void log_action(String service, String action, int priority) {
		Log.i(TAG, service+" > "+action);
		
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("priority", priority);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
	}
	
	public void log_IO(String tag, String message) {
		debug_message(tag, message);
		/*
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", tag);
        i.putExtra("Status", message);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
        */
	}

	private static void debug_message(String tag, String message) {
		if (DEBUG_MODE) {
			Log.i(tag, message);
		}
	}
	
	private double clamp(double dvalue) {
		if (dvalue > 0.0) {
			return dvalue;
		}
		else {
			return 0.0;
		}
	}
	
}