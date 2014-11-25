//*********************************************************************************************************************
//  Copyright 2011-2012 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.SSMservice;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Constraints;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.Safety;
import edu.virginia.dtc.SysMan.State;
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
//	private double nextSimulatedPumpValue = 0.0;
//	private long lastBolusSimulatedTime;
	public int cycle_duration_seconds = 300;
	public int cycle_duration_mins = cycle_duration_seconds/60;
	
	public static final String IOB_CONTROLLER_RATE = "IOB_controller_rate";
	
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
	private static final int LOG_ACTION_UNINITIALIZED = 0;
	private static final int LOG_ACTION_INFORMATION = 1;
	private static final int LOG_ACTION_DEBUG = 2;
	private static final int LOG_ACTION_NOT_USED = 3;
	private static final int LOG_ACTION_WARNING = 4;
	private static final int LOG_ACTION_SERIOUS = 5;
	
    public static final String INSULIN_BASAL_BOLUS = "basal_bolus";
    public static final String INSULIN_MEAL_BOLUS = "meal_bolus";
    public static final String INSULIN_CORR_BOLUS = "corr_bolus";
	// Working storage for current cgm and insulin data
	private Tvector Tvec_cgm_mins, Tvec_insulin_rate1_seconds, Tvec_insulin_bolus1, Tvec_bolus_hist_seconds;
	private Tvector Tvec_basal_bolus_hist_seconds, Tvec_meal_bolus_hist_seconds, Tvec_corr_bolus_hist_seconds;
//	private Tvector Tvec_credit, Tvec_spent, Tvec_net;
//	private Tvector Tvec_basal;
	private Tvector Tvec_IOB, Tvec_Rate, Tvec_GPRED;
	public double bolus_basal;
	public double bolus_meal;
	public double bolus_correction;
	public double bolusRequested;
	public double differential_basal_rate;
	public double credit_request, spend_request;
	public double interceptor_insulin;
	public boolean asynchronous;
	public boolean exercise;
	
	public double IOBvalue = 0.0;
	public int hypoLight = Safety.GREEN_LIGHT;
	public int hyperLight = Safety.GREEN_LIGHT;
	
	public static final int TVEC_SIZE = 96;				// 8 hours of samples at 5 mins per sample
	// Store most recent timestamps in seconds for each biometric Tvector
	Long last_Tvec_cgm_time_secs, last_Tvec_insulin_bolus1_time_secs, last_Tvec_requested_insulin_bolus1_time_secs, last_Tvec_insulin_credit_time_secs, last_state_estimate_time_secs;
	long calFlagTime = 0;
	long hypoFlagTime = 0;
	
	
	private Subject subject;
    public static ScheduledExecutorService constraintTimeoutScheduler = Executors.newScheduledThreadPool(2);
	public static ScheduledFuture<?> constraintTimer;
	private long TIMEOUT_CONSTRAINT_MS = 5000;				// Constraint Service timeout is 5 seconds
	
	// Used to calculate and store the SSM_processing object
//	public SSM_processing ssm_state_estimate;
	
	/*
	 * 
	 *  Interface to DiAsService (our only Client)
	 * 
	 */
	// safetyService commands
	public static final int SAFETY_SERVICE_CMD_NULL = 0;
	public static final int SAFETY_SERVICE_CMD_START_SERVICE = 1;
	public static final int SAFETY_SERVICE_CMD_REGISTER_CLIENT = 2;
	public static final int SAFETY_SERVICE_CMD_REQUEST_BOLUS= 4;
    
    // safetyService return status values
    public static final int SAFETY_SERVICE_STATE_NORMAL = 0;
    public static final int SAFETY_SERVICE_STATE_NOT_ENOUGH_DATA = 1;
    public static final int SAFETY_SERVICE_STATE_CREDIT_REQUEST = 2;
    public static final int SAFETY_SERVICE_STATE_BOLUS_INTERCEPT = 3;
    public static final int SAFETY_SERVICE_STATE_AWAITING_PUMP_RESPONSE = 4;
    public static final int SAFETY_SERVICE_STATE_BUSY = 5;
    public static final int SAFETY_SERVICE_STATE_PUMP_ERROR = -1;
    public static final int SAFETY_SERVICE_STATE_INVALID_COMMAND = -2;

    public Messenger mMessengerToDiAsService = null;													/* Messenger for sending responses to the client (Application). */
    final Messenger mMessengerFromDiAsService = new Messenger(new IncomingHandlerFromDiAsService());		/* Target we publish for clients to send commands to IncomingHandlerFromDiAsService. */
 
    // Elements used in Constraint Service interface
    private ConstraintsObserver constraintsObserver;
    private double insulinUpperConstraint;
    private boolean insulinUpperConstraintValid = false;
    private Uri insulinUpperConstraintUri = null;
	public BroadcastReceiver confirmationReceiver;				// Listens for information broadcasts from Confirmation Activity
	private boolean confirmationReceiverIsRegistered = false;
//	private double Uconstraint;
	
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

    private Subject subject_data;
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
//		Tvec_IOB = new Tvector(TVEC_SIZE);
//		Tvec_Rate = new Tvector(TVEC_SIZE);
//		Tvec_GPRED = new Tvector(TVEC_SIZE);
		Tvec_cgm_mins = new Tvector(TVEC_SIZE);
		Tvec_insulin_rate1_seconds = new Tvector(TVEC_SIZE);
		Tvec_insulin_bolus1 = new Tvector(TVEC_SIZE);
		Tvec_bolus_hist_seconds = new Tvector(TVEC_SIZE);
//		Tvec_credit = new Tvector(TVEC_SIZE);
//		Tvec_spent = new Tvector(TVEC_SIZE);
//		Tvec_net = new Tvector(TVEC_SIZE);
		Tvec_basal_bolus_hist_seconds = new Tvector(TVEC_SIZE);
		Tvec_meal_bolus_hist_seconds = new Tvector(TVEC_SIZE);
		Tvec_corr_bolus_hist_seconds = new Tvector(TVEC_SIZE);
//		Tvec_spent = new Tvector(TVEC_SIZE);
//		lastBolusSimulatedTime = -1;
		// Initialize most recent timestamps
		last_Tvec_cgm_time_secs = new Long(0);
		last_state_estimate_time_secs = new Long(0);
		last_Tvec_insulin_bolus1_time_secs = new Long(0);
		last_Tvec_insulin_credit_time_secs = new Long(0);
		last_Tvec_requested_insulin_bolus1_time_secs = new Long(0);
		
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
//        mNotificationManager.notify(SAFETY_ID, notification);
        // Make this a Foreground Service
        startForeground(SAFETY_ID, notification);
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();  
		
        constraintsObserver = new ConstraintsObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.CONSTRAINTS_URI, true, constraintsObserver);
	}

	@Override
	public void onDestroy() {
		Toast toast = Toast.makeText(this, "SafetyService Service Stopped", Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
		toast.show();
		Log.d(TAG, "onDestroy");
        log_action(TAG, "onDestroy", LOG_ACTION_INFORMATION);
		if(constraintsObserver != null)
			getContentResolver().unregisterContentObserver(constraintsObserver);
		if(confirmationReceiver != null)
			unregisterReceiver(confirmationReceiver);
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
    				case SAFETY_SERVICE_CMD_NULL:		// null command
    					Debug.i(TAG, FUNC_TAG,"SAFETY_SERVICE_CMD_NULL");
    					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                    		Bundle b = new Bundle();
                    		b.putString(	"description", "DiAsService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
                    						"SAFETY_SERVICE_CMD_NULL");
                    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
    					}
    					break;
    				case SAFETY_SERVICE_CMD_START_SERVICE:		// start service command
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
    					subject_data = new Subject(getCurrentTimeSeconds(), getApplicationContext());
    					
    			        // Bind to the Insulin Pump Service
    					Intent intent = new Intent();
    					intent.setClassName("edu.virginia.dtc.PumpService", "edu.virginia.dtc.PumpService.PumpService");
    					bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    					SSMSERVICE_STATE = SSMSERVICE_STATE_IDLE;
    					break;
    				case SAFETY_SERVICE_CMD_REGISTER_CLIENT:
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
    				case SAFETY_SERVICE_CMD_REQUEST_BOLUS:
    					SSMSERVICE_STATE = SSMSERVICE_STATE_PROCESSING;
    					Debug.i(TAG, FUNC_TAG,"SAFETY_SERVICE_CMD_REQUEST_BOLUS");
    					// Evaluate a bolus for safety with parameters received from the Application
    					paramBundle = msg.getData();
    					
    					bolus_meal =  paramBundle.getDouble("bolus_meal", 0);
    					bolus_correction =  paramBundle.getDouble("bolus_correction", 0);
    					
    					bolusRequested = bolus_meal + bolus_correction;
    					differential_basal_rate = paramBundle.getDouble("differential_basal_rate", 0);		// In the range [-6U/hour:6U/hour]
    					credit_request = paramBundle.getDouble("credit_request", 0);
    					spend_request = paramBundle.getDouble("spend_request", 0);
    					asynchronous = paramBundle.getBoolean("asynchronous", false);
    					
    					if (asynchronous && spend_request > POSITIVE_EPSILON) {		// This is a way to test spend_request
    						bolus_meal = spend_request;
    						bolus_correction = 0;
    						bolusRequested = bolus_meal;
    					}
    					
    					calFlagTime = (long)paramBundle.getLong("calFlagTime", 0);
    					hypoFlagTime = (long)paramBundle.getLong("hypoFlagTime", 0);
    					exercise = paramBundle.getBoolean("currentlyExercising", false);
    					DIAS_STATE = paramBundle.getInt("DIAS_STATE", DIAS_STATE_OPEN_LOOP);
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
    					if (!constraintServiceInstalled()) {
        					// If the Constraint Service is not installed then start the SSM calculation with a fixed IOB constraint
    	    				insulinUpperConstraint = DEFAULT_IOB_CONSTRAINT_UNITS;
    	    				SSMprocess(insulinUpperConstraint);
    					}
    					else {
    	    				// Else call the Constraint Service 
    						constraintServiceCall();
    					}
    					    					
    					break;
    				case Safety.SAFETY_SERVICE_CMD_CALCULATE_STATE:
    					SSMSERVICE_STATE = SSMSERVICE_STATE_PROCESSING;
    					Debug.i(TAG, FUNC_TAG,"SAFETY_SERVICE_CMD_CALCULATE_STATE");
    					
    					// Evaluate a bolus for safety with parameters received from the Application
    					paramBundle = msg.getData();
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
    					DIAS_STATE = paramBundle.getInt("DIAS_STATE", DIAS_STATE_OPEN_LOOP);
    					    					
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
    					break;
    				default:
    					super.handleMessage(msg);
                }        		
        	}
        	else {
				Debug.e(TAG, FUNC_TAG,"SSMSERVICE_STATE == "+SSMSERVICE_STATE+", Message: "+msg.what+" ignored.");
				returnStatusToDiAsService(SAFETY_SERVICE_STATE_BUSY);
        	}
        }
    }

    //***************************************************************************************
    // SSM_processing interface method
    //***************************************************************************************
    private void SSMstateEstimation() {
    	final String FUNC_TAG = "SSMstateEstimation";
    	
    	// Update Tvectors from the database
		fetchNewBiometricData();
	   	
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
		
		// Allocate SSM_processing here...

		// Compute the current IOB
		calculateCurrentIOB(false);
				
		// Start the SSM_processing calculation...

		writeStateEstimateData();
		returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_CALCULATE_RESPONSE);				
    } 

    
    private void SSMprocess(double InsulinConstraintInUnits) {
    	final String FUNC_TAG = "SSMprocess";
    	
		fetchNewBiometricData();
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
		subject = new Subject(getCurrentTimeSeconds(), this);
		if (!asynchronous) {
			bolus_basal = subject.basal/(12.0);
		}
		IOBvalue = calculateCurrentIOB(asynchronous);
		calculateTrafficLights(Tvec_cgm_mins.get_last_value());
		
		if (constraintServiceInstalled()) {
			if (applyInsulinConstraint(InsulinConstraintInUnits)) {
	    		Bundle b = new Bundle();
	    		b.putString("description", "Insulin bolus constrained to "+InsulinConstraintInUnits+" U in "+FUNC_TAG);
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SSM_CONSTRAINT_APPLIED, Event.makeJsonString(b), Event.SET_LOG);
			}
		}
		
		writeStateEstimateData();
		if (hypoLight != Safety.RED_LIGHT) {
			sendBolusToPumpService(bolus_basal, bolus_meal, bolus_correction);
		}
		else {
			sendBolusToPumpService(0.0, 0.0, 0.0);
		}
    }
    
    //***************************************************************************************
    // Apply the Insulin Constraint: returns true if constraint is applied
    //***************************************************************************************
    boolean applyInsulinConstraint(double InsulinConstraintInUnits) {
    	final String FUNC_TAG = "applyInsulinConstraint";
    	double constraint = InsulinConstraintInUnits;
    	double b = bolus_basal;
    	double m = bolus_meal;
    	double c = bolus_correction;
		Debug.i(TAG, FUNC_TAG, "b="+b+", m="+m+", c="+c+", constraint="+InsulinConstraintInUnits);
    	
	    if (DIAS_STATE != DIAS_STATE_CLOSED_LOOP && DIAS_STATE != DIAS_STATE_SAFETY_ONLY) {
	    	return false;
	    }
	    		
    	if (constraint >= NEGATIVE_EPSILON) {	// Negative constraint is invalid
        	if (constraint-b >=0) {
        		constraint = constraint - b;
        		if (constraint - m >= 0) {
        			constraint = constraint - m;
        			if (constraint - c >=0) {
        				return false;						// correction, meal and basal are unchanged
        			}
        			else {
        				bolus_correction = constraint;		// correction is reduced in size, basal and meal unchanged
        				return true;
        			}
        		}
        		else {
        			bolus_meal = constraint;				// meal is reduced in size, correction is zero, basal unchanged
        			bolus_correction = 0;
        			return true;
        		}
        	}
        	else {
        		bolus_basal = constraint;		// basal is reduced in size, meal and correction are zero
    			bolus_meal = 0;
    			bolus_correction = 0;
    			return true;
        	}
    	}
    	else {
    		return false;
    	}
    }
    
    
    //***************************************************************************************
    // Constraint Service interface methods
    //***************************************************************************************
	private boolean constraintServiceInstalled()
    {
    	//Does a quick scan to check if the ConstraintService application is installed, if so it returns true
   		final PackageManager pm = this.getPackageManager();
		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
		for(ApplicationInfo a: packages)
		{
			if(a.packageName.equalsIgnoreCase("edu.virginia.dtc.ConstraintService"))
			{
				return true;
			}
		}
   		return false;
    }
    public void constraintServiceCall()
    {
		SSMSERVICE_STATE = SSMSERVICE_STATE_WAIT_CONSTRAINT;
		// Insert row into Constraint Service with status CONSTRAINT_REQUESTED to signify it should calculate
		ContentValues cv = new ContentValues();
		cv.put("time", getCurrentTimeSeconds());	
		cv.put("status", Constraints.CONSTRAINT_REQUESTED);									
		insulinUpperConstraint = DEFAULT_IOB_CONSTRAINT_UNITS;							// Default upper insulin constraint so lax as to be meaningless
		insulinUpperConstraintValid = false;
		insulinUpperConstraintUri=getContentResolver().insert(Biometrics.CONSTRAINTS_URI, cv);
		startConstraintServiceTimer();
    }
    
	class ConstraintsObserver extends ContentObserver 
    {	
    	private int count;
    	public ConstraintsObserver(Handler handler) 
    	{
    		super(handler);    		
    		final String FUNC_TAG = "Constraints Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		count = 0;
    	}
    	
       @Override
       public void onChange(boolean selfChange) 
       {
    	   final String FUNC_TAG = "onChange(boolean selfChange)";
    	   Debug.i(TAG, FUNC_TAG, "Constraints Observer");
    	   if(selfChange)			//We don't trigger on the updates we make to the Constraint Table
    		   return;
    	   handleConstraints(false);
       }		

    }
    private void startConstraintServiceTimer()
    {
		final String FUNC_TAG = "startConstraintServiceTimer";
    	Debug.i(TAG, FUNC_TAG,"Command Timer > Starting ConstraintService watchdog timer");
    	if(constraintTimer!= null)				//Cancel the Constraint Service timeout routine if running
			constraintTimer.cancel(true);
		constraintTimer = constraintTimeoutScheduler.schedule(constraintServiceTimeOut, TIMEOUT_CONSTRAINT_MS, TimeUnit.MILLISECONDS);  // 5 second timeout
    }
    
    public Runnable constraintServiceTimeOut = new Runnable()
	{
		public void run() 
		{	
			final String FUNC_TAG = "constraintServiceTimeOut";
    		Debug.i(TAG, FUNC_TAG, "Constraint Service timed out");
			SSMSERVICE_STATE = SSMSERVICE_STATE_PROCESSING;
    		handleConstraints(true);
		}
	};
	
    private void handleConstraints(boolean timedOut)
    {
    	final String FUNC_TAG = "handleConstraints";
    	insulinUpperConstraintValid = false;
    	Cursor c = getContentResolver().query(Biometrics.CONSTRAINTS_URI, null, null, null, null);
    	if (c != null) {
    		if (c.moveToLast()) {
    			long time = c.getLong(c.getColumnIndex("time"));
    			long status = c.getInt(c.getColumnIndex("status"));
    			int _id = c.getInt(c.getColumnIndex("_id"));
    			insulinUpperConstraint = c.getDouble(c.getColumnIndex("constraint1"));
 			   
    			if (status == Constraints.CONSTRAINT_REQUESTED) {
    				// Do nothing - the Constraint Service isn't finished yet
    				Debug.i(TAG, FUNC_TAG, "status==CONSTRAINT_REQUESTED, no response from Constraint Service");
    			}
    			else if (status == Constraints.CONSTRAINT_TIMED_OUT) {
					SSMSERVICE_STATE = SSMSERVICE_STATE_PROCESSING;
    				// Do nothing - we canceled the Constraint calculation
    				Debug.i(TAG, FUNC_TAG, "status==CONSTRAINT_TIMED_OUT, Constraint calculation canceled by SSMservice");
    			}
    			else if (status == Constraints.CONSTRAINT_WRITTEN) {
    				// Constraint calculation is complete
    				if(constraintTimer!= null)				//Cancel the Constraint Service timeout routine if running
    					constraintTimer.cancel(true);
					SSMSERVICE_STATE = SSMSERVICE_STATE_PROCESSING;
    				insulinUpperConstraintValid = true;
    				Debug.i(TAG, FUNC_TAG, "status==CONSTRAINT_WRITTEN, Constraint=="+insulinUpperConstraint);
    				if (!timedOut) {
        				ContentValues writeValues = new ContentValues();
        				writeValues.put("time", time);
        				writeValues.put("status", Constraints.CONSTRAINT_READ);		// status == CONSTRAINT_READ means we have already read this Constraint
        				writeValues.put("_id", _id);
        				writeValues.put("constraint1", insulinUpperConstraint);
        				try {
        					//Write values to database
        					getContentResolver().update(Biometrics.CONSTRAINTS_URI, writeValues, "_id="+_id, null);
        					Debug.i(TAG, FUNC_TAG, "> update");
        				}
        				catch (Exception e) {
                    		Bundle b = new Bundle();
                    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
                    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
        					log_action(TAG, "Error writing values to constraints table:"+e.toString(), LOG_ACTION_WARNING);
        				}
        				// Call SSM_processing with IOB constraint
        				SSMprocess(insulinUpperConstraint);
    				}
    			}
    			else if (status == Constraints.CONSTRAINT_READ) {
    				// Do nothing - we have already read this Constraint 
    				Debug.i(TAG, FUNC_TAG, "status==CONSTRAINT_READ, Constraint calculation already received by SSMservice");
    			}
    			else {
    				// Do nothing - invalid status
    				Debug.i(TAG, FUNC_TAG, "status=="+status+", out of range");
    			}
    			
    			if (timedOut) {
					SSMSERVICE_STATE = SSMSERVICE_STATE_PROCESSING;
    				ContentValues writeValues = new ContentValues();
    				writeValues.put("time", time);
    				writeValues.put("status", Constraints.CONSTRAINT_TIMED_OUT);		// status == CONSTRAINT_TIMED_OUT means canceled because of timeout
    				writeValues.put("_id", _id);
    				writeValues.put("constraint1", insulinUpperConstraint);
    				try {
    					//Write values to database
    					getContentResolver().update(Biometrics.CONSTRAINTS_URI, writeValues, "_id="+_id, null);
    					Debug.i(TAG, FUNC_TAG, "> update");
    				}
    				catch (Exception e) {
                		Bundle b = new Bundle();
                		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
    					log_action(TAG, "Error writing values to constraints table:"+e.toString(), LOG_ACTION_WARNING);
    				}
    				// Call SSM_processing with IOB constraint
    				insulinUpperConstraint = DEFAULT_IOB_CONSTRAINT_UNITS;				// Set to pump maximum since there was no response from Constraint Service
    				SSMprocess(insulinUpperConstraint);
    			}
    		}
    	}
    	else {
    		Debug.e(TAG, FUNC_TAG, "Constraints table empty.");
    	}
    	c.close();
	}
    
	
	
    private double calculateCurrentIOB(boolean asynchronous_call) {			
		final String FUNC_TAG = "calculateCurrentIOB";
    	double retValue = 0.0;
    	//
    	// Insert your IOB calculation here!!!
    	//
    	return retValue;
    }
    
    private void calculateTrafficLights(double cgm_value) {
       	// Simple threshold code for updating Traffic Lights
       	hypoLight = Safety.GREEN_LIGHT;
       	hyperLight = Safety.GREEN_LIGHT;       	
		if (cgm_value <= 70) {
			hypoLight = Safety.RED_LIGHT;
			hyperLight = Safety.GREEN_LIGHT;
		} else if (cgm_value <= 90) {
			hypoLight = Safety.YELLOW_LIGHT;
			hyperLight = Safety.GREEN_LIGHT;
		} else if (cgm_value < 250) {
			hypoLight = Safety.GREEN_LIGHT;
			hyperLight = Safety.GREEN_LIGHT;
		} else if (cgm_value < 300) {
			hypoLight = Safety.GREEN_LIGHT;
			hyperLight = Safety.YELLOW_LIGHT;
		} else {
			hypoLight = Safety.GREEN_LIGHT;
			hyperLight = Safety.RED_LIGHT;
		}
    }
        
	//***************************************************************************************
	// DiAsService interface method
	//***************************************************************************************
    public void returnStatusToDiAsService(int status) {
		// Report error state back to the DiAsService
    	final String FUNC_TAG = "returnStatusToDiAsService";
		Message response;
		Bundle responseBundle;
		// Report results back to the DiAsApp
		response = Message.obtain(null, status, 0, 0);
		responseBundle = new Bundle();
		responseBundle.putInt("stoplight", hypoLight);
		responseBundle.putInt("stoplight2", hyperLight);
		responseBundle.putBoolean("isMealBolus", false);
		responseBundle.putDouble("brakes_coeff", 1.0);
		responseBundle.putDouble("IOB", IOBvalue);
		response.setData(responseBundle);
		// Log the parameters for IO testing
		if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
    		Bundle b = new Bundle();
    		b.putString(	"description", "(SSMservice) >> DiAsService, IO_TEST"+", "+FUNC_TAG+", "+
    						"SAFETY_SERVICE_STATE_NORMAL"+", "+
    						"stoplight="+responseBundle.getInt("stoplight")+", "+
    						"stoplight2="+responseBundle.getInt("stoplight2")+", "+
    						"isMealBolus="+responseBundle.getBoolean("isMealBolus")+", "+
    						"brakes_coeff="+responseBundle.getDouble("brakes_coeff")+", "+
    						"basal="+responseBundle.getDouble("basal")+", "+
    						"IOB="+responseBundle.getDouble("IOB")
    						);
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
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
	private void sendBolusToPumpService(double bolus_basal, double bolus_meal, double bolus_correction) {
    	final String FUNC_TAG = "sendBolusToPumpService";
    	Bundle paramBundle;
    	Message response, msg;
    	Bundle responseBundle;
		try {
			// Send a command to the Pump Service to deliver the bolus.
			msg = Message.obtain(null, PUMP_SERVICE_CMD_DELIVER_BOLUS, 0, 0);
			paramBundle = new Bundle();
     		paramBundle.putLong("lastBolusSimulatedTime", getCurrentTimeSeconds());
			paramBundle.putBoolean("asynchronous", asynchronous);
			paramBundle.putDouble("pre_authorized", 0.0);
			paramBundle.putDouble("bolus_max", 20.0);
			paramBundle.putDouble(INSULIN_BASAL_BOLUS, bolus_basal);
    		paramBundle.putDouble(INSULIN_MEAL_BOLUS, bolus_meal); 
    		paramBundle.putDouble(INSULIN_CORR_BOLUS, bolus_correction); 
			putTvector(paramBundle, subject.subjectBasal, "Basaltimes", "Basalvalues");
			msg.setData(paramBundle);
			// Log the parameters for IO testing
			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
				Tvector tvec_temp = getTvector(paramBundle, "Basaltimes", "Basalvalues");
	    		Bundle b = new Bundle();
	    		b.putString(	"description", "(SSMservice) >> PumpService, IO_TEST"+", "+FUNC_TAG+", "+
	    						"PUMP_SERVICE_CMD_DELIVER_BOLUS"+", "+
	    						", lastBolusSimulatedTime="+paramBundle.getLong("lastBolusSimulatedTime")+", "+
	    						", asynchronous="+paramBundle.getBoolean("asynchronous")+", "+
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
			double SSM_amount = bolus_basal + bolus_meal + bolus_correction;
			debug_message("BOLUS_TRACE", "SafetyService > send bolus to pump > bolus="+SSM_amount);
		}
		catch (RemoteException e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
			e.printStackTrace();
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
           			returnStatusToDiAsService(SAFETY_SERVICE_STATE_NORMAL);
               		break;
           		case Pump.PUMP_STATE_PUMP_ERROR:
           			debug_message(TAG, "PUMP_STATE_PUMP_ERROR");
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_PUMP_ERROR"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
           			returnStatusToDiAsService(SAFETY_SERVICE_STATE_PUMP_ERROR);
           			break;
           		case Pump.PUMP_STATE_COMMAND_ERROR:
           			debug_message(TAG, "PUMP_STATE_COMMAND_ERROR");
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_COMMAND_ERROR"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
           			returnStatusToDiAsService(SAFETY_SERVICE_STATE_PUMP_ERROR);
           			break;
           		case Pump.PUMP_STATE_NO_RESPONSE:
           			debug_message(TAG, "PUMP_STATE_NO_RESPONSE");
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_NO_RESPONSE"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
           			returnStatusToDiAsService(SAFETY_SERVICE_STATE_PUMP_ERROR);
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

			// Fetch  insulin data
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
			Tvec_bolus_hist_seconds.dump(TAG, "Tvec_bolus_hist_seconds", 4);
			Tvec_basal_bolus_hist_seconds.dump(TAG, "Tvec_basal_bolus_hist_seconds", 4);
			Tvec_meal_bolus_hist_seconds.dump(TAG, "Tvec_meal_bolus_hist_seconds", 4);
			Tvec_corr_bolus_hist_seconds.dump(TAG, "Tvec_corr_bolus_hist_seconds", 4);			
		}
        catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
        	Log.e("fetchNewBiometricData > Error SafetyService", e.toString());
        }
	}
	
	public void writeStateEstimateData() {
    	final String FUNC_TAG = "writeStateEstimateData";
    	double SSM_amount = bolus_basal + bolus_meal + bolus_correction;
	    ContentValues values = new ContentValues();
	    values.put("time", getCurrentTimeSeconds());
	    values.put("enough_data", true);
	    if (asynchronous)
	    	values.put("asynchronous", 1);
	    else
	    	values.put("asynchronous", 0);
	    values.put("CGM", Tvec_cgm_mins.get_last_value());
	    values.put("Gpred", 0.0);
	    values.put("Gbrakes", 0.0);
	    values.put("Gpred_light", 0.0);
	    values.put("Xi00", 0.0);
	    values.put("Xi01", 0.0);
	    values.put("Xi02", 0.0);
	    values.put("Xi03", 0.0);
	    values.put("Xi04", 0.0);
	    values.put("Xi05", 0.0);
	    values.put("Xi06", 0.0);
	    values.put("Xi07", 0.0);
	    values.put("Gpred_bolus", 0.0);
	    values.put("CHOpred", 0.0);
	    values.put("Abrakes", 0.0);
	    values.put("CGM_corr", 0.0);
	    values.put("IOB_controller_rate", 0.0);
	    values.put("SSM_amount", 0.0);
	    values.put("State", 0.0);
	    values.put("SSM_state_timestamp", getCurrentTimeSeconds());

	    values.put("stoplight", hypoLight);
	    values.put("stoplight2", hyperLight);
	    values.put("IOB", IOBvalue);
	    values.put("IOBlast", IOBvalue);
	    values.put("IOBlast2", IOBvalue);
	    values.put("isMealBolus", 0);
	    values.put("SSM_amount", SSM_amount);
	    values.put("brakes_coeff", 1.0);
	    values.put("DIAS_state", DIAS_STATE);											// Save the Current DiAs state
		long timeSeconds = getCurrentTimeSeconds();
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(timeSeconds*1000)/1000;
		values.put("UTC_offset_seconds", UTC_offset_secs);					// Save the current offset from UTC in seconds
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