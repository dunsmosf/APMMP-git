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
import edu.virginia.dtc.SSMservice.Confirmations;
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
	private static final int LOG_ACTION_UNINITIALIZED = 0;
	private static final int LOG_ACTION_INFORMATION = 1;
	private static final int LOG_ACTION_DEBUG = 2;
	private static final int LOG_ACTION_NOT_USED = 3;
	private static final int LOG_ACTION_WARNING = 4;
	private static final int LOG_ACTION_SERIOUS = 5;
	
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
	
	private boolean bolus_interceptor_enabled = true;
    
	// Working storage for current cgm and insulin data
	private Tvector Tvec_cgm_mins, Tvec_insulin_rate1_seconds, Tvec_insulin_bolus1, Tvec_bolus_hist_seconds;
	private Tvector Tvec_basal_bolus_hist_seconds, Tvec_meal_bolus_hist_seconds, Tvec_corr_bolus_hist_seconds;
	private Tvector Tvec_credit, Tvec_spent, Tvec_net;
//	private Tvector Tvec_basal;
	private Tvector Tvec_IOB, Tvec_Rate, Tvec_GPRED;
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
	private long TIMEOUT_CONSTRAINT_MS = 5000;				// Constraint Service timeout is 5 seconds
	
	// Used to calculate and store the SSM_processing object
	public SSM_processing ssm_state_estimate;
	
	/*
	 * 
	 *  Interface to DiAsService (our only Client)
	 * 
	 */
    public Messenger mMessengerToDiAsService = null;													/* Messenger for sending responses to the client (Application). */
    final Messenger mMessengerFromDiAsService = new Messenger(new IncomingHandlerFromDiAsService());		/* Target we publish for clients to send commands to IncomingHandlerFromDiAsService. */
 
    // Elements used in Constraint Service interface
    private ConstraintsObserver constraintsObserver;
    private double insulinUpperConstraint;
    private boolean insulinUpperConstraintValid = false;
    private Uri insulinUpperConstraintUri = null;
	public BroadcastReceiver confirmationReceiver;				// Listens for information broadcasts from Confirmation Activity
	public BroadcastReceiver TBRReceiver;				// Listens for information broadcasts from DiAsUI TBR button pressed

	private boolean confirmationReceiverIsRegistered = false;
	private boolean TBRReceiverIsRegistered = false;

	private double Uconstraint;
	
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
	private static final double CORRECTION_MAX_CONSTRAINT = 10.0;
	private static final double MEAL_MAX_CONSTRAINT = 15.0;
	
	private static final double BASAL_TOO_HIGH_LIMIT = 1.0;
	private static final double CORRECTION_TOO_HIGH_LIMIT = 30.0;
	private static final double MEAL_TOO_HIGH_LIMIT = 30.0;
	
	public static final int DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK = 25;	
	
	
	// Temporary Basal Rate variables
	private int temp_basal_status_code, temp_basal_percent_of_profile_basal_rate, temp_basal_owner;
	private long temp_basal_start_time, temp_basal_scheduled_end_time;
	
	
	public static SSMDB db; 


    private ServiceConnection mConnection;																/* Connection to the Pump Service. */
    Messenger mMessengerToPumpService = null;															/* Messenger for communicating with the Pump Service. */
    final Messenger mMessengerFromPumpService = new Messenger(new IncomingHandlerFromPumpService());		/* Target we publish for clients to send messages to IncomingHandler. */
    boolean mBound;																						/* Flag indicating whether we have called bind on the PumpService. */
    
    @Override
	public void onCreate() {
		SSMSERVICE_STATE = SSMSERVICE_STATE_IDLE;
		bolus_interceptor_enabled = Params.getBoolean(getContentResolver(), "bolus_interceptor_enabled", false);
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
		
//        insulinObserver = new InsulinObserver(new Handler());
//        getContentResolver().registerContentObserver(Biometrics.INSULIN_URI, true, insulinObserver);

        constraintsObserver = new ConstraintsObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.CONSTRAINTS_URI, true, constraintsObserver);
		
     	// Register to receive Confirmation broadcast messages
		confirmationReceiver = new BroadcastReceiver() 
     	{
     		final String FUNC_TAG = "confirmationReceiver";
     		
            @Override
            public void onReceive(Context context, Intent intent) {        			
    			String action = intent.getAction();
    	        int status = intent.getIntExtra("ConfirmationStatus", Confirmations.CONFIRMATION_TIMED_OUT);
    	        double bolus = intent.getDoubleExtra("bolus", 0.0);
    	        Debug.i(TAG, FUNC_TAG, "confirmationReceiver > status="+status+", bolus="+bolus);
				handleInterceptorResult(status, bolus);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("edu.virginia.dtc.intent.action.CONFIRMATION_UPDATE_STATUS");
        registerReceiver(confirmationReceiver, filter);
        confirmationReceiverIsRegistered = true;
        
     // Register to receive TBR broadcast messages
        TBRReceiver = new BroadcastReceiver() 
     	{
     		final String FUNC_TAG = "TBRReceiver";
     		
            @Override
            public void onReceive(Context context, Intent intent) {        			
    			String action = intent.getAction();
    			
    			int TBR_status=intent.getIntExtra("command",0);
    			Toast.makeText(getApplicationContext(), "TBR_status="+TBR_status, Toast.LENGTH_LONG).show();
             

    			if(action.equals("edu.virginia.dtc.intent.action.TEMP_BASAL")&&TBR_status==TempBasal.TEMP_BASAL_START)
    			{
    				try {
    			    Intent i = new Intent(context,TempBasalActivity.class);
    			    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    			    startActivity(i);
    				}
    			    catch (Exception e){
    				Debug.i(TAG, FUNC_TAG, e.getMessage());
    			    }
    			    //Toast.makeText(getApplicationContext(), "end_trigger", Toast.LENGTH_LONG).show();
    	            Debug.i(TAG, FUNC_TAG, "status");
    			}
    			
    			
    			// if TBR is not cancelled in DiAs UI, we need to cancel here when receiving broadcast
//    			if(action.equals("edu.virginia.dtc.intent.action.TEMP_BASAL")&&TBR_status==TempBasal.TEMP_BASAL_CANCEL)
//    			{
//    				cancelTemporaryBasalRate();
//        			Toast.makeText(getApplicationContext(), "cancel_TBR", Toast.LENGTH_LONG).show();
//    			}
            
            }	
        };
        IntentFilter filter1 = new IntentFilter();
        filter1.addAction("edu.virginia.dtc.intent.action.TEMP_BASAL");
        registerReceiver(TBRReceiver, filter1);
        TBRReceiverIsRegistered = true;
        
        
        
     // create SSMDB        
        db = new SSMDB(this.getApplicationContext());
     	Toast.makeText(getApplicationContext(), "SSMDB created", Toast.LENGTH_LONG).show();

     	
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
		unregisterReceiver(confirmationReceiver);
		unregisterReceiver(TBRReceiver);

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
		Cursor c = getContentResolver().query(Biometrics.TEMP_BASAL_URI, null, null, null, null);
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
    					
    					/*
    					// Create new Subject and SSM_param objects
    					subject_data = new Subject(getCurrentTimeSeconds(), getApplicationContext());
    					ssm_param = new SSM_param(subject_data.subjectAIT, subject_data.subjectBasal, subject_data.subjectCR, subject_data.subjectCF, subject_data.subjectTDI, subject_data.subjectWeight);
    					*/
    					
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
                        paramBundle = msg.getData();
    					DIAS_STATE = paramBundle.getInt("DIAS_STATE", DIAS_STATE_OPEN_LOOP);
    					
    					if(checkSubjectData(subject_data))
    					{
    						Debug.i(TAG, FUNC_TAG, "Reading SSM Parameters...");
	    					ssm_param = new SSM_param(subject_data.subjectAIT, subject_data.subjectBasal, subject_data.subjectCR, subject_data.subjectCF, subject_data.subjectTDI, subject_data.subjectWeight);
	    					
	    					if (ssm_param.isValid) {
	    					
		    					bolus_interceptor_enabled = Params.getBoolean(getContentResolver(), "bolus_interceptor_enabled", false);
		    					Debug.i(TAG, FUNC_TAG,"SAFETY_SERVICE_CMD_REQUEST_BOLUS");
		    					subject_data.read(getCurrentTimeSeconds(), getApplicationContext());
		    					if (subject_data.valid == false)		// Protect against state estimates with uninitialized data.
		    						Debug.e(TAG, "SAFETY_SERVICE_CMD_REQUEST_BOLUS", "subject.valid == false");
		    					// Evaluate a bolus for safety with parameters received from the Application
		    					bolus_meal =  paramBundle.getDouble("bolus_meal", 0);
		    					bolus_correction =  paramBundle.getDouble("bolus_correction", 0);
		    					bolusRequested = bolus_meal + bolus_correction;
		    					differential_basal_rate = paramBundle.getDouble("differential_basal_rate", 0);		// In the range [-6U/hour:6U/hour]
		    					credit_request = paramBundle.getDouble("credit_request", 0);
		    					spend_request = paramBundle.getDouble("spend_request", 0);
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
		    					// credit_request must NOT be accompanied by a bolus_meal or bolus_correction
		    					if (credit_request>POSITIVE_EPSILON && (bolus_meal>POSITIVE_EPSILON || bolus_correction>POSITIVE_EPSILON)) {
		    						Debug.e(TAG, FUNC_TAG,"ERROR > credit_request with bolus: cr="+credit_request+", bm="+bolus_meal+", bc="+bolus_correction);
		    						returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_INVALID_COMMAND);
		    					}
		    					if (credit_request<NEGATIVE_EPSILON || Math.abs(credit_request-spend_request)>POSITIVE_EPSILON) {
		    						returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_INVALID_COMMAND);
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
    					paramBundle = msg.getData();
                        DIAS_STATE = paramBundle.getInt("DIAS_STATE", DIAS_STATE_OPEN_LOOP);
    					
    					if(checkSubjectData(subject_data))
    					{
    						Debug.i(TAG, FUNC_TAG, "Reading SSM Parameters...");
	    					ssm_param = new SSM_param(subject_data.subjectAIT, subject_data.subjectBasal, subject_data.subjectCR, subject_data.subjectCF, subject_data.subjectTDI, subject_data.subjectWeight);
	    					
	    					if (ssm_param.isValid) {
		    					bolus_interceptor_enabled = Params.getBoolean(getContentResolver(), "bolus_interceptor_enabled", false);
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
    	
    	if(s.subjectBasal.count() == 0 || s.subjectCF.count() == 0 || s.subjectCR.count() == 0)
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
    private void SSMprocess(double InsulinConstraintInUnits) {
    	final String FUNC_TAG = "SSMprocess";
    	
    	// Update Tvectors from the database
		fetchNewBiometricData();
		fetchStateEstimateData(getCurrentTimeSeconds());
	   	Tvec_IOB.dump(TAG, "Tvec_IOB", 4);
	   	Tvec_GPRED.dump(TAG, "Tvec_GPRED", 4);
	   	Tvec_Rate.dump(TAG, "Tvec_Rate", 4);
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
		
		// Allocate SSM_processing
		if (ssm_state_estimate == null) {
			ssm_state_estimate = new SSM_processing(subject_data, getCurrentTimeSeconds(), getApplicationContext());
		}

		// Compute the current IOB
		calculateCurrentIOB();
		
		ssm_state_estimate.state_data.InsulinConstraintInUnits = InsulinConstraintInUnits;		// Save the insulin constraint from the Constraint Service
		
		// Start the SSM_processing calculation
		ssm_state_estimate.start_SSM_processing(subject_data, Tvec_cgm_mins, Tvec_insulin_rate1_seconds, Tvec_bolus_hist_seconds, 
				Tvec_basal_bolus_hist_seconds, Tvec_meal_bolus_hist_seconds, 	Tvec_corr_bolus_hist_seconds, 
				Tvec_GPRED, Tvec_IOB, Tvec_Rate,
				bolusRequested, differential_basal_rate, Tvec_credit,
				Tvec_spent, Tvec_net, ssm_param, getCurrentTimeSeconds(), cycle_duration_mins, ssm_state_estimate.state_data,
				calFlagTime, hypoFlagTime, credit_request, spend_request, exercise, asynchronous, bolus_interceptor_enabled, DIAS_STATE);
		ssm_state_estimate.state_data.asynchronous = asynchronous;

		// On a normal return write the stateestimate data because processing is complete
		if (ssm_state_estimate.state_data.Processing_State == Safety.SAFETY_SERVICE_STATE_NORMAL) {
			writeStateEstimateData();
		}
		
		//Checking extreme values on variables...
		if(!(checkExtremes(bolus_meal, "bolus_meal") && checkExtremes(bolus_correction, "bolus_correction") 
				&& checkExtremes(ssm_state_estimate.state_data.basal_added, "basal_added") && checkExtremes(ssm_state_estimate.state_data.pre_authorized, "pre_authorized")
				&& checkExtremes(ssm_state_estimate.state_data.SSM_amount, "SSM_amount")))
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
		
		if (ssm_state_estimate.state_data.Processing_State == Safety.SAFETY_SERVICE_STATE_NORMAL) {
			// No bolus intercept
			if (ssm_state_estimate.state_data.SSM_amount + ssm_state_estimate.state_data.pre_authorized > NEGATIVE_EPSILON) {
				// If no bolus intercept then apply Constraint
				if (ssm_state_estimate.state_data.InsulinConstraintInUnits > NEGATIVE_EPSILON) {
					if (applyInsulinConstraint()) {
			    		Bundle b = new Bundle();
			    		b.putString("description", "Insulin bolus constrained to "+ssm_state_estimate.state_data.InsulinConstraintInUnits+" U in "+FUNC_TAG);
			    		Event.addEvent(getApplicationContext(), Event.EVENT_SSM_CONSTRAINT_APPLIED, Event.makeJsonString(b), Event.SET_LOG);
					}
					ssm_state_estimate.state_data.SSM_amount = ssm_state_estimate.state_data.basal_added + bolus_meal + bolus_correction;
					Debug.i(TAG, FUNC_TAG, "b="+ssm_state_estimate.state_data.basal_added+", m="+bolus_meal+", c="+bolus_correction);
				}
				// Send bolus command to PumpService
				Debug.w(TAG, FUNC_TAG, "No bolus intercept! Async: "+asynchronous);
				Debug.w(TAG, FUNC_TAG, "Meal: "+bolus_meal);
				Debug.w(TAG, FUNC_TAG, "Corr: "+bolus_correction);
				
				//TODO: SENDING BOLUS HERE
				//TODO: SENDING BOLUS HERE
				//TODO: SENDING BOLUS HERE
				
				if(asynchronous)	//Meal so no basal!
					sendBolusToPumpService(0.0, bolus_meal, bolus_correction);
				else				//Synchronous, so system generated
					sendBolusToPumpService(ssm_state_estimate.state_data.basal_added, bolus_meal, bolus_correction);
			}
			else {
				// If no bolus intercept but bolus is too small to send then immediately respond to DiAsService
				Debug.i(TAG, FUNC_TAG, "No bolus intercept, but bolus is too small");
				returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_NORMAL);				
			}
		}						
		else {
			// Bolus Intercept
			Debug.i(TAG, FUNC_TAG, "Bolus intercept launching");
			bolusInterceptor(ssm_state_estimate.state_data.Processing_State);
			
			//Meal table is handled in the responses to the intercept
		}    	
    }    
    
    //***************************************************************************************
    // SSM_processing interface method
    //***************************************************************************************
    private void SSMstateEstimation() {
    	final String FUNC_TAG = "SSMstateEstimation";
    	
    	// Update Tvectors from the database
		fetchNewBiometricData();
		fetchStateEstimateData(getCurrentTimeSeconds());
	   	Tvec_IOB.dump(TAG, "Tvec_IOB", 4);
	   	Tvec_GPRED.dump(TAG, "Tvec_GPRED", 4);
	   	Tvec_Rate.dump(TAG, "Tvec_Rate", 4);
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
		
		// Allocate SSM_processing
		if (ssm_state_estimate == null) {
			ssm_state_estimate = new SSM_processing(subject_data, getCurrentTimeSeconds(), getApplicationContext());
		}

		// Compute the current IOB
		calculateCurrentIOB();
				
		// Start the SSM_processing calculation
		ssm_state_estimate.start_SSM_processing(subject_data, Tvec_cgm_mins, Tvec_insulin_rate1_seconds, Tvec_bolus_hist_seconds, 
				Tvec_basal_bolus_hist_seconds, Tvec_meal_bolus_hist_seconds, 	Tvec_corr_bolus_hist_seconds, 
				Tvec_GPRED, Tvec_IOB, Tvec_Rate,
				bolusRequested, differential_basal_rate, Tvec_credit,
				Tvec_spent, Tvec_net, ssm_param, getCurrentTimeSeconds(), cycle_duration_mins, ssm_state_estimate.state_data,
				calFlagTime, hypoFlagTime, credit_request, spend_request, exercise, asynchronous, bolus_interceptor_enabled, DIAS_STATE);
//		ssm_state_estimate.state_data.asynchronous = asynchronous;

		writeStateEstimateData();
		returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_CALCULATE_RESPONSE);				
		
    }    
    
    
    //***************************************************************************************
    // Apply the Insulin Constraint: returns true if constraint is applied
    //***************************************************************************************
    boolean applyInsulinConstraint() {
    	final String FUNC_TAG = "applyInsulinConstraint";
    	boolean asynchronous = ssm_state_estimate.state_data.asynchronous;
    	double b = ssm_state_estimate.state_data.basal_added;
    	double m = bolus_meal;
    	double c = bolus_correction;
    	double constraint = ssm_state_estimate.state_data.InsulinConstraintInUnits;
		Debug.i(TAG, FUNC_TAG, "b="+b+", m="+m+", c="+c+", constraint="+constraint);
    	
		if (ssm_state_estimate.state_data.asynchronous) {
			return false;
		}
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
        		ssm_state_estimate.state_data.basal_added = constraint;		// basal is reduced in size, meal and correction are zero
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
		
		if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
			// Log communication from SSMservice to ConstraintService
			Bundle b = new Bundle();
			b.putString("description", "SSMservice > ConstraintService: time="+cv.getAsString("time")+", status="+cv.getAsString("status"));
			Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
		}
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
/*
       public void onChange(boolean selfChange, Uri uri) 
       {
    	   final String FUNC_TAG = "onChange(boolean selfChange, Uri uri)";
    	   Debug.i(TAG, FUNC_TAG, "Constraints Observer");
    	   if(selfChange)			//We don't trigger on the updates we make to the Constraint Table
    		   return;
    	   handleConstraints(false);
       }
*/	
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

    			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
    				Bundle bc = new Bundle();
    				bc.putString("description", "ConstraintService > SSMservice: time="+time+", status="+status+", _id="+_id+", constraint="+insulinUpperConstraint);
    				Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(bc), Event.SET_LOG);
    			}
    			
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
        	    			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    				Bundle bc = new Bundle();
        	    				bc.putString("description", "SSMservice > ConstraintService: time="+writeValues.getAsString("time")+", status="+writeValues.getAsString("status")+
        	    					", _id="+writeValues.getAsString("_id")+
        	    					", constraint="+writeValues.getAsString("constraint1"));
        	    				Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(bc), Event.SET_LOG);
        	    			}
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
    	    			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
    	    				Bundle bc = new Bundle();
    	    				bc.putString("description", "SSMservice > ConstraintService: time="+writeValues.getAsString("time")+
    	    						", status="+writeValues.getAsString("status")+", _id="+writeValues.getAsString("_id")+", constraint1="+writeValues.getAsString("constraint1"));
    	    				Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(bc), Event.SET_LOG);
    	    			}
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
    
    //***************************************************************************************
    // IOB, State Estimate and traffic light calculation
    //***************************************************************************************
/*    
	class InsulinObserver extends ContentObserver {	
    	private int count;
    	public InsulinObserver(Handler handler) {
    		super(handler);
    		final String FUNC_TAG = "Insulin Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		count = 0;
    	}

       @Override
       public void onChange(boolean selfChange) {
    	   this.onChange(selfChange, null);
       }		

       public void onChange(boolean selfChange, Uri uri) {
  	   		final String FUNC_TAG = "onChange";
 			if (ssm_state_estimate == null) {
 				ssm_state_estimate = new SSM_processing(subject_data, getCurrentTimeSeconds(), getApplicationContext());
 			}
 			count++;
 			Debug.i(TAG, FUNC_TAG, "Insulin Observer: "+count);
        	   Cursor c = getContentResolver().query(Biometrics.INSULIN_URI, null, "status="+Pump.MANUAL, null, null);
        	   if(c != null) {
        		   if(c.moveToLast()) {
    				   long req_time = c.getLong(c.getColumnIndex("req_time"));
    				   if (req_time == 0) {
    					   int _id = c.getInt(c.getColumnIndex("_id"));
        				   long deliv_time = c.getLong(c.getColumnIndex("deliv_time"));
        				   // Update IOB, state estimate and traffic lights
        				   fetchNewBiometricData();
        				   fetchStateEstimateData(getCurrentTimeSeconds());
        				   calculateCurrentIOB();
        				   ssm_state_estimate.start_SSM_processing(subject_data, Tvec_cgm_mins, Tvec_insulin_rate1_seconds, Tvec_bolus_hist_seconds, 
        							Tvec_basal_bolus_hist_seconds, Tvec_meal_bolus_hist_seconds, 	Tvec_corr_bolus_hist_seconds, 
        							Tvec_GPRED, Tvec_IOB, Tvec_Rate,
        							0.0, 0.0, Tvec_credit,
        							Tvec_spent, Tvec_net, ssm_param, getCurrentTimeSeconds(), cycle_duration_mins, ssm_state_estimate.state_data,
        							calFlagTime, hypoFlagTime, 0.0, 0.0, exercise, true, DIAS_STATE);
        				   if (ssm_state_estimate.state_data.Processing_State == SafetyService.SAFETY_SERVICE_STATE_NOT_ENOUGH_DATA) {
        						ssm_state_estimate.complete_processing(subject_data, ssm_state_estimate.state_data, true, DIAS_STATE, getCurrentTimeSeconds(), Tvec_GPRED, Tvec_IOB, Tvec_Rate, calFlagTime);
        				   }
        				   writeStateEstimateData();
        		    	   // Update the insulin table to indicate that this manual insulin input has been used to calculate IOB and traffic lights
        		    	   // and the results have been saved in the stateestimate table
        		    	   ContentValues values = new ContentValues();
        		    	   values.put("req_time", deliv_time);				
        		    	   try {
        		    		   getContentResolver().update(Biometrics.INSULIN_URI, values, "_id="+_id, null);
        		    	   }
        		    	   catch (Exception e) {
        		    		   Bundle b = new Bundle();
        		    		   b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
        		    		   Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
        		    	   }
        		    	   Debug.i(TAG, FUNC_TAG, "IOB="+ssm_state_estimate.state_data.IOB_meal+", req_time="+req_time+", deliv_time="+deliv_time);
    				   }
        		   }
        		   else {
    		    	   Debug.i(TAG, FUNC_TAG, "c.moveToLast() failed");
        		   }
            	   c.close();
           	   }
           	   else {
    		    	   Debug.i(TAG, FUNC_TAG, "No match for Status=Pump.MANUAL");
           	   }
       }		       
    }
*/	
	
	
    private double calculateCurrentIOB() {
		final String FUNC_TAG = "calculateCurrentIOB";
    	double retValue = 0.0;
    	try {
    		if (ssm_state_estimate != null) {
/*    			
    			// Calculate IOB_time
    			long latest_basal_time = Tvec_basal_bolus_hist_seconds.get_last_time();
    			if ( (latest_basal_time-getCurrentTimeSeconds()) >= 0 && (latest_basal_time-getCurrentTimeSeconds() <= 2*60)) {
    				// Handle the case where pump clock skew puts the basal bolus up to 2 minutes ahead of DiAs time
         			ssm_state_estimate.state_data.IOB_time = latest_basal_time;    				
    			}
    			else if ( (latest_basal_time-getCurrentTimeSeconds()) < 0 && (latest_basal_time-getCurrentTimeSeconds() >= -7*60)) {
    				// Handle the case where pump clock skew puts the basal bolus up to 7 minutes behind DiAs time
         			ssm_state_estimate.state_data.IOB_time = latest_basal_time;    				
    			}
    			else {
    				// Handle the case of no very recent basal bolus
         			ssm_state_estimate.state_data.IOB_time = getCurrentTimeSeconds()-4*60;
    			}		
*/    			
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
     										ssm_state_estimate.state_data);
     			
     			// Compute insulin on board (IOB) based on time "bins" ending at the current time
 				ssm_state_estimate.state_data.IOB_meal = calculate_IOB(	ssm_state_estimate.state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds, 
											ssm_param.iob_param,  
											getCurrentTimeSeconds());
 				ssm_state_estimate.state_data.IOB_no_meal = calculate_IOB(	ssm_state_estimate.state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds, 
 											ssm_param.iob_param,  
 											getCurrentTimeSeconds());

				
 				Debug.i(TAG, FUNC_TAG, "IOB_meal="+ssm_state_estimate.state_data.IOB_meal+"IOB_no_meal="+ssm_state_estimate.state_data.IOB_no_meal);
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
			Debug.i(TAG, FUNC_TAG, "                                  ");
			Debug.i(TAG, FUNC_TAG, "**********************************");
			Tvec_basal_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_basal_bolus_hist_seconds", 12);	
			Debug.i(TAG, FUNC_TAG, "                                  ");
			Tvec_corr_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_corr_bolus_hist_seconds", 12);	
			Debug.i(TAG, FUNC_TAG, "                                  ");
			Tvec_meal_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_meal_bolus_hist_seconds", 12);	
			Debug.i(TAG, FUNC_TAG, "**********************************");
			Debug.i(TAG, FUNC_TAG, "                                  ");
		
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
		Debug.i(TAG, FUNC_TAG, "                                  ");
		Debug.i(TAG, FUNC_TAG, "**********************************");
		state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.dump(TAG, FUNC_TAG+" > Tvec_ins_hist_IOB_with_meal_insulin_seconds", 12);	
		Debug.i(TAG, FUNC_TAG, "                                  ");
		state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.dump(TAG, FUNC_TAG+" > Tvec_ins_hist_IOB_no_meal_insulin_seconds", 12);	
		Debug.i(TAG, FUNC_TAG, "**********************************");
		Debug.i(TAG, FUNC_TAG, "                                  ");
	}	  // end - insulin_history_builder
	
/*    
	private void insulin_history_builder(
				int cycle_duration_mins,
				Tvector Tvec_rate_hist_seconds,
				Tvector Tvec_bolus_hist_seconds, 
				Tvector Tvec_basal_bolus_hist_seconds,	
				Tvector Tvec_meal_bolus_hist_seconds,
				Tvector Tvec_corr_bolus_hist_seconds,
				Tvector Tvec_basal_pattern, 
				Tvector Tvec_spent_hist_seconds, 
				SSM_param cbam_param,
				long IOB_time,
				SSM_state_data state_data,
				boolean asynchronous) {
		final String FUNC_TAG = "insulin_history_builder";
		try {
			long ToD_minutes;
			double dvalue, dvalue1;
			int ii, kk;
			// Get the offset in minutes into the current day in the current time zone (based on cell phone time zone setting)
			TimeZone tz = TimeZone.getDefault();
			int UTC_offset_secs = tz.getOffset(IOB_time*1000)/1000;
			Debug.i(TAG, FUNC_TAG, "UTC_offset_secs="+UTC_offset_secs);
			// Create a vector of long discrete times in UTC minutes
			int hist_length = cbam_param.iob_param.hist_length;
			int ndiscrete = hist_length/cycle_duration_mins;
			state_data.discrete_time = new long[ndiscrete];
			for (ii=ndiscrete-1; ii>=0; ii--) {
				state_data.discrete_time[ndiscrete-1-ii] = (long)((IOB_time)-ii*cycle_duration_mins*60);
			}		
			
			// Dump insulin Tvectors
			Debug.i(TAG, FUNC_TAG, "                                  ");
			Debug.i(TAG, FUNC_TAG, "**********************************");
			Tvec_basal_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_basal_bolus_hist_seconds", 8);	
			Tvec_corr_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_corr_bolus_hist_seconds", 8);	
			Tvec_meal_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_meal_bolus_hist_seconds", 8);	
			Debug.i(TAG, FUNC_TAG, "time="+getCurrentTimeSeconds());
			Debug.i(TAG, FUNC_TAG, "IOB_time="+IOB_time+", asynchronous="+asynchronous);
			Debug.i(TAG, FUNC_TAG, "**********************************");
			Debug.i(TAG, FUNC_TAG, "                                  ");
			
			// *******************************************************
			// Construct Tvec_basal_hist - the programmed basal history over discrete_time[] based on the Tvec_basal_pattern daily profile.
			// *******************************************************
			Tvector Tvec_basal_hist = new Tvector(ndiscrete);
			List<Integer> indices;	
			double basal_bolus;			
			for (ii=0; ii<ndiscrete; ii++) {											
				ToD_minutes = (state_data.discrete_time[ii]/60+UTC_offset_secs/60)%1440;			// ToD_minutes is number of minutes into the current day in local time
				if ((indices = Tvec_basal_pattern.find(">", -1, "<=", ToD_minutes)) != null) {
					basal_bolus = Tvec_basal_pattern.get_value(indices.get(indices.size()-1));
				}
				else if ((indices = Tvec_basal_pattern.find(">", -1, "<", -1)) != null) {
					basal_bolus = Tvec_basal_pattern.get_value(indices.get(indices.size()-1));					
				}
				else {
					basal_bolus = 0;
				}
				Tvec_basal_hist.put(state_data.discrete_time[ii]/60, (double)basal_bolus);
			}
			
			// *******************************************************
			// Construct Tvec_ins_hist_KF for use in KF calculation
			// *******************************************************
			state_data.Tvec_ins_hist_KF_mins = new Tvector(ndiscrete);
			for (ii=0; ii<ndiscrete; ii++) {
				// Add in the basal bolus contribution
				if ((indices = Tvec_basal_bolus_hist_seconds.find(">=", (state_data.discrete_time[ii]-cycle_duration_mins*60), "<", state_data.discrete_time[ii])) != null) { 
					dvalue = 0;
					Double Dval;
					for (kk=0; kk<indices.size(); kk++) {
						dvalue = dvalue + Tvec_basal_bolus_hist_seconds.get_value(indices.get(kk));
					}
					if ((Dval=state_data.Tvec_ins_hist_KF_mins.get_value_using_time_as_index(state_data.discrete_time[ii]/60)) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_KF_mins.put_with_replace(state_data.discrete_time[ii]/60, (double)dvalue1);
				}
				// Add in the correction bolus contribution
				if ((indices = Tvec_corr_bolus_hist_seconds.find(">=", (state_data.discrete_time[ii]-cycle_duration_mins*60), "<", state_data.discrete_time[ii])) != null) {
					dvalue = 0;
					Double Dval;
					for (kk=0; kk<indices.size(); kk++) {
						dvalue = dvalue + Tvec_corr_bolus_hist_seconds.get_value(indices.get(kk));
					}
					if ((Dval=state_data.Tvec_ins_hist_KF_mins.get_value_using_time_as_index(state_data.discrete_time[ii]/60)) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_KF_mins.put_with_replace(state_data.discrete_time[ii]/60, (double)dvalue1);
				}	
				// Add in the meal bolus contribution
				if ((indices = Tvec_meal_bolus_hist_seconds.find(">=", (state_data.discrete_time[ii]-cycle_duration_mins*60), "<", state_data.discrete_time[ii])) != null) {
					dvalue = 0;
					Double Dval;
					for (kk=0; kk<indices.size(); kk++) {
						dvalue = dvalue + Tvec_meal_bolus_hist_seconds.get_value(indices.get(kk));
					}
					if ((Dval=state_data.Tvec_ins_hist_KF_mins.get_value_using_time_as_index(state_data.discrete_time[ii]/60)) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_KF_mins.put_with_replace(state_data.discrete_time[ii]/60, (double)dvalue1);
				}
				
				// If there are no boluses within this discrete_time interval then fill with basal
				Double Dval;
				if ((Dval = state_data.Tvec_ins_hist_KF_mins.get_value_using_time_as_index(state_data.discrete_time[ii]/60)) == null) {
					state_data.Tvec_ins_hist_KF_mins.put(state_data.discrete_time[ii]/60, Tvec_basal_hist.get_value_using_time_as_index(state_data.discrete_time[ii]/60)/(60/cycle_duration_mins));
				}
			}
//			state_data.Tvec_ins_hist_KF_mins.dump(TAG, "insulin_history_builder > Tvec_ins_hist_KF 1", 4);
			
			// Update Tvec_ins_hist_KF to include any bolus which has a time after state_data.discrete_time[ndiscrete-1]
			// This can happen because of clock skew between the cell phone and the pump.
			double insulin_additional = 0;
			long insulin_last_time = state_data.Tvec_ins_hist_KF_mins.get_last_time();
			if ((indices = Tvec_basal_bolus_hist_seconds.find(">", state_data.discrete_time[ndiscrete-1], "<", -1)) != null) {
				for (kk=0; kk<indices.size(); kk++) {
					insulin_additional = insulin_additional + Tvec_basal_bolus_hist_seconds.get_value(indices.get(kk));
				}
			}
			double insulin_last_value = state_data.Tvec_ins_hist_KF_mins.get_last_value();
			state_data.Tvec_ins_hist_KF_mins.put_with_replace(insulin_last_time, insulin_last_value+insulin_additional);
			
			// If this is an asynchronous call then initialize Tvec_ins_hist_IOB with all basal insulin but not latest meal and correction
			if (asynchronous) {
//				state_data.Tvec_ins_hist_IOB_mins = new Tvector();
//				int hh = 0;
//				for (hh=0; hh<state_data.Tvec_ins_hist_KF_mins.count(); hh++) {
//					state_data.Tvec_ins_hist_IOB_mins.put( state_data.Tvec_ins_hist_KF_mins.get(hh).time(), state_data.Tvec_ins_hist_KF_mins.get(hh).value() );
//				}
				state_data.Tvec_ins_hist_IOB_mins = new Tvector(state_data.Tvec_ins_hist_KF_mins);
//				state_data.Tvec_ins_hist_IOB_mins.dump(TAG, "insulin_history_builder > Tvec_ins_hist_IOB 1", 4);		
			}
			
			insulin_additional = 0;
			if ((indices = Tvec_corr_bolus_hist_seconds.find(">", state_data.discrete_time[ndiscrete-1], "<", -1)) != null) {
				for (kk=0; kk<indices.size(); kk++) {
					insulin_additional = insulin_additional + Tvec_corr_bolus_hist_seconds.get_value(indices.get(kk));
				}
			}
			if ((indices = Tvec_meal_bolus_hist_seconds.find(">", state_data.discrete_time[ndiscrete-1], "<", -1)) != null) {
				for (kk=0; kk<indices.size(); kk++) {
					insulin_additional = insulin_additional + Tvec_meal_bolus_hist_seconds.get_value(indices.get(kk));
				}
			}			
			insulin_last_value = state_data.Tvec_ins_hist_KF_mins.get_last_value();
			state_data.Tvec_ins_hist_KF_mins.put_with_replace(insulin_last_time, insulin_last_value+insulin_additional);
			Debug.i(TAG, FUNC_TAG, "insulin_additional="+insulin_additional);
//			state_data.Tvec_ins_hist_KF_mins.dump(TAG, "insulin_history_builder > Tvec_ins_hist_KF 2", 4);
		
			// If this is an asynchronous call then Tvec_ins_hist_IOB has all recent insulin entered at current time and not corrected (for addition to IOB)
			if (asynchronous) {
				state_data.asynchronous_insulin_IOB = insulin_additional;
			}
			else {
				state_data.asynchronous_insulin_IOB = 0;
			}
			
			// *******************************************************
			// If this is a synchronous call then Tvec_ins_hist_IOB includes all basal, meal and correction insulin
			// *******************************************************
			if (!asynchronous) {
//				state_data.Tvec_ins_hist_IOB_mins = new Tvector();
//				int hh = 0;
//				for (hh=0; hh<state_data.Tvec_ins_hist_KF_mins.count(); hh++) {
//					state_data.Tvec_ins_hist_IOB_mins.put( state_data.Tvec_ins_hist_KF_mins.get(hh).time(), state_data.Tvec_ins_hist_KF_mins.get(hh).value() );
//				}
 				state_data.Tvec_ins_hist_IOB_mins = new Tvector(state_data.Tvec_ins_hist_KF_mins);
//				state_data.Tvec_ins_hist_IOB_mins.dump(TAG, "insulin_history_builder > Tvec_ins_hist_IOB 2", 4);		
			}
//			state_data.Tvec_ins_hist_IOB_mins.dump(TAG, "insulin_history_builder > Tvec_ins_hist_IOB 3", 4);		
			
			// *******************************************************
			// Correct Tvec_ins_hist_IOB by removing basal insulin
			// *******************************************************
			for (ii=0; ii<ndiscrete; ii++) {
				Double Dval;
				double basal_value;
				if ((Dval = state_data.Tvec_ins_hist_IOB_mins.get_value_using_time_as_index(state_data.discrete_time[ii]/60)) != null) {
					basal_value = 0.0;
					basal_value = Tvec_basal_hist.get_value_using_time_as_index(state_data.discrete_time[ii]/60)/(60/cycle_duration_mins);
					state_data.Tvec_ins_hist_IOB_mins.put_with_replace(state_data.discrete_time[ii]/60, (double)(Dval.doubleValue()-basal_value));
//					Debug.i(TAG, FUNC_TAG, "Dval="+Dval.doubleValue()+", basal_value="+basal_value);
				}
			}
//			state_data.Tvec_ins_hist_IOB_mins.dump(TAG, "insulin_history_builder > Tvec_ins_hist_IOB 4", 4);		
		}
		catch (Exception e) {
			Bundle b = new Bundle();
			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
			Event.addEvent(this, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
	}	  // end - insulin_history_builder
*/	

	
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
	
/*	
	public double IOB_asynch(Tvector Tvec_corr_bolus_hist_seconds, double IOB, long time) {
		final String FUNC_TAG = "IOB_asynch";
		double retValue = IOB;
		double insulin_additional = 0;
		try {
	  		int kk;
		  	//
		  	// Handle the case of an asynchronous  bolus (meal or correction) which has a time after IOB_time, the last IOB calculation time
		  	//
		 	List<Integer> indices;	
	  		if ((indices = Tvec_corr_bolus_hist_seconds.find(">=", time, "<", -1)) != null) {
	  			debug_message(TAG, "IOB_asynch > indices.size()="+indices.size()+", indices="+indices);
	  			for (kk=0; kk<indices.size(); kk++) {
	  				insulin_additional = insulin_additional + Tvec_corr_bolus_hist_seconds.get_value(indices.get(kk));
	  	  			debug_message(TAG, "IOB_asynch > Tvec_corr_bolus_hist_seconds.get_value(indices.get("+kk+")="+Tvec_corr_bolus_hist_seconds.get_value(indices.get(kk)));
	  			}
	  		}
		}
 		catch (Exception e) {
 			Bundle b = new Bundle();
 			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
 			Event.addEvent(this, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}	
		retValue = retValue + insulin_additional;
		debug_message(TAG, "IOB_asynch > IOB="+retValue+", time="+time+", IOB="+IOB);
		return retValue;
	}
*/

    
    //***************************************************************************************
    // ConfirmationActivity interface methods
    //***************************************************************************************
    public void bolusInterceptor(int processingState) {
    	final String FUNC_TAG = "bolusInterceptor";
		Message confirm;
		Bundle confirmationBundle;
		// Activate ConfirmationActivity
		ssm_state_estimate.state_data.Processing_State = processingState;
		confirm = Message.obtain(null, ssm_state_estimate.state_data.Processing_State, 0, 0);
		confirmationBundle = new Bundle();
		double insulinAmountToConfirm;
		int eventCode = Event.EVENT_SSM_UNKNOWN_INTERCEPT;
		switch(ssm_state_estimate.state_data.Processing_State) {
			case Safety.SAFETY_SERVICE_STATE_BOLUS_INTERCEPT:
				insulinAmountToConfirm = bolus_meal+bolus_correction+differential_basal_rate/60.0*cycle_duration_mins;
				confirmationBundle.putDouble("insulinAmountToConfirm", insulinAmountToConfirm);
				confirmationBundle.putString("confirmationMsg1", "The system is about to inject "+String.format("%.1f", insulinAmountToConfirm)+"U of insulin.");
				confirmationBundle.putString("confirmationMsg2", " ");
				confirmationBundle.putString("confirmationMsg3", "If you do not eat at least "+ssm_state_estimate.state_data.CHOmin+"g of carbohydrate your blood sugar is predicted to go below 70 mg/dl.");
//				confirmationBundle.putString("confirmationMsg2", "You may Increase, Decrease, Confirm or Cancel this request.");
				confirmationBundle.putDouble("bolusMax", ssm_state_estimate.state_data.Umax);
				confirmationBundle.putBoolean("userChangeable", false);
				eventCode = Event.EVENT_SSM_BOLUS_INTERCEPT;
				break;
			case Safety.SAFETY_SERVICE_STATE_CREDIT_REQUEST:
				insulinAmountToConfirm = ssm_state_estimate.state_data.credit_request;
				confirmationBundle.putDouble("insulinAmountToConfirm", insulinAmountToConfirm);
				confirmationBundle.putString("confirmationMsg1", "The system is about to inject "+String.format("%.1f", insulinAmountToConfirm)+"U of insulin.");
				confirmationBundle.putString("confirmationMsg2", " ");
				confirmationBundle.putString("confirmationMsg3", "");
				confirmationBundle.putString("confirmationMsg2", "You may Increase, Decrease, Confirm or Cancel this request.");
				confirmationBundle.putDouble("bolusMax", ssm_state_estimate.state_data.Umax);
				confirmationBundle.putBoolean("userChangeable", true);
				eventCode = Event.EVENT_SSM_CREDIT_INTERCEPT;
				break;
			case Safety.SAFETY_SERVICE_STATE_NOT_ENOUGH_DATA:
				insulinAmountToConfirm = ssm_state_estimate.state_data.advised_bolus;
				confirmationBundle.putDouble("insulinAmountToConfirm", insulinAmountToConfirm);
				confirmationBundle.putString("confirmationMsg1", "The system is about to inject "+String.format("%.1f", insulinAmountToConfirm)+"U of insulin.");
				confirmationBundle.putString("confirmationMsg2", " ");
				confirmationBundle.putString("confirmationMsg3", "");
				confirmationBundle.putDouble("bolusMax", ssm_state_estimate.state_data.Umax);
				confirmationBundle.putBoolean("userChangeable", false);
				eventCode = Event.EVENT_SSM_NOT_ENOUGH_DATA_INTERCEPT;
				break;
			default:
				insulinAmountToConfirm = bolus_meal+bolus_correction+differential_basal_rate*cycle_duration_mins;
				confirmationBundle.putDouble("insulinAmountToConfirm", insulinAmountToConfirm);
				confirmationBundle.putString("confirmationMsg1", "The system is about to inject "+String.format("%.1f", insulinAmountToConfirm)+"U of insulin.");
				confirmationBundle.putString("confirmationMsg2", "You may Increase, Decrease, Confirm or Cancel this request.");
				confirmationBundle.putString("confirmationMsg3", "");
				confirmationBundle.putDouble("bolusMax", ssm_state_estimate.state_data.Umax);
				confirmationBundle.putBoolean("userChangeable", false);
				break;
		}
		// Store corresponding Event
		Bundle b = new Bundle();
		b.putString("description", "SSMservice intercept, "+Event.getCodeString(eventCode)+", "+String.format("%.2f", insulinAmountToConfirm)+"U, Event "+eventCode+" in "+FUNC_TAG);
		Event.addEvent(getApplicationContext(), eventCode, Event.makeJsonString(b), Event.SET_LOG);
		
		confirm.setData(confirmationBundle);
        Intent intent = new Intent();
    	intent.setComponent(new ComponentName("edu.virginia.dtc.SSMservice", "edu.virginia.dtc.SSMservice.ConfirmationActivity"));
    	intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	intent.putExtras(confirmationBundle);
		Debug.i(TAG, FUNC_TAG, "start ConfirmationActivity");
		SSMSERVICE_STATE = SSMSERVICE_STATE_WAIT_CONFIRMATION;
        getApplicationContext().startActivity(intent);
    }
	
    
    public void handleInterceptorResult(int status, double bolus) {
    	final String FUNC_TAG = "handleInterceptorResult";
		Bundle b = new Bundle();
		int eventCode = Event.EVENT_SSM_INTERCEPT_TIMEOUT;
    	
    	Cursor c = getContentResolver().query(Biometrics.MEAL_URI, null, null, null, null);
    	
        switch (status) 
        {
	        case Confirmations.CONFIRMATION_TIMED_OUT:
	    		// Store corresponding Event
	        	eventCode = Event.EVENT_SSM_INTERCEPT_TIMEOUT;
	    		b.putString("description", Event.getCodeString(eventCode)+", "+eventCode+" in "+FUNC_TAG);
	    		Event.addEvent(getApplicationContext(), eventCode, Event.makeJsonString(b), Event.SET_LOG);
	    		
        		Debug.i(TAG, FUNC_TAG, "CONFIRMATION_TIMED_OUT");
				log_action(TAG, "Intercept Timed Out > deliver basal", LOG_ACTION_INFORMATION);
				ssm_state_estimate.state_data.SSM_amount = 0;
				ssm_state_estimate.state_data.Abrakes = ssm_state_estimate.state_data.SSM_amount;
				ssm_state_estimate.state_data.risky = 0;
				fetchStateEstimateData(getCurrentTimeSeconds());
				ssm_state_estimate.complete_processing(subject_data, ssm_state_estimate.state_data, true, DIAS_STATE, getCurrentTimeSeconds(), Tvec_GPRED, Tvec_IOB, Tvec_Rate, calFlagTime);
				writeStateEstimateData();
				ssm_state_estimate.state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_NORMAL;				
				if(c!=null)
				{
					if(c.moveToLast())
					{
						int id = c.getInt(c.getColumnIndex("_id"));
						Debug.i(TAG, FUNC_TAG, "ID: "+id);						
						ContentValues cv = new ContentValues();
						cv.put("meal_status", edu.virginia.dtc.SysMan.Meal.MEAL_STATUS_APPROVAL_TIMEOUT);
						// Reset 'received_server' so the row is resent to the server:
						cv.put("received_server", false);
						getContentResolver().update(Biometrics.MEAL_URI, cv, "_id='"+id+"'", null);
					}
					c.close();
				}
				else
					Debug.i(TAG, FUNC_TAG, "Cursor is null for meal table!");
				returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_NORMAL);
        		break;
	        case Confirmations.CONFIRMATION_CANCEL:
	    		// Store corresponding Event
	        	eventCode = Event.EVENT_SSM_INTERCEPT_CANCEL;
	    		b.putString("description", Event.getCodeString(eventCode)+", "+eventCode+" in "+FUNC_TAG);
	    		Event.addEvent(getApplicationContext(), eventCode, Event.makeJsonString(b), Event.SET_LOG);
	    		
        		Debug.i(TAG, FUNC_TAG, "CONFIRMATION_CANCEL");
				log_action(TAG, "Bolus Canceled > deliver basal", LOG_ACTION_INFORMATION);
				ssm_state_estimate.state_data.SSM_amount = 0;
				ssm_state_estimate.state_data.Abrakes = ssm_state_estimate.state_data.SSM_amount;
				ssm_state_estimate.state_data.risky = 0;
				fetchStateEstimateData(getCurrentTimeSeconds());
				ssm_state_estimate.complete_processing(subject_data, ssm_state_estimate.state_data, true, DIAS_STATE, getCurrentTimeSeconds(), Tvec_GPRED, Tvec_IOB, Tvec_Rate, calFlagTime);
				writeStateEstimateData();
				ssm_state_estimate.state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_NORMAL;				
				if(c!=null)
				{
					if(c.moveToLast())
					{
						int id = c.getInt(c.getColumnIndex("_id"));
						Debug.i(TAG, FUNC_TAG, "ID: "+id);						
						ContentValues cv = new ContentValues();
						cv.put("meal_status", edu.virginia.dtc.SysMan.Meal.MEAL_STATUS_ABORTED);
						// Reset 'received_server' so the row is resent to the server:
						cv.put("received_server", false);
						getContentResolver().update(Biometrics.MEAL_URI, cv, "_id='"+id+"'", null);
					}
					c.close();
				}
				else
					Debug.i(TAG, FUNC_TAG, "Cursor is null for meal table!");
				
				returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_NORMAL);
        		break;
	        case Confirmations.CONFIRMATION_ACCEPT:
	    		// Store corresponding Event
	        	eventCode = Event.EVENT_SSM_INTERCEPT_ACCEPT;
	    		b.putString("description", Event.getCodeString(eventCode)+", "+eventCode+" in "+FUNC_TAG);
	    		Event.addEvent(getApplicationContext(), eventCode, Event.makeJsonString(b), Event.SET_LOG);
	        	
        		Debug.i(TAG, FUNC_TAG, "CONFIRMATION_ACCEPT");
        		if (credit_request > POSITIVE_EPSILON) {			// Credit request approved
					log_action(TAG, "Credit approved: "+credit_request+" U", LOG_ACTION_INFORMATION);
					
					// The only credit/spend command permitted is when credit==spend and corr_bolus==meal_bolus==differential_basal_rate==0
					credit_request = bolus;
					ssm_state_estimate.state_data.credit_request = bolus;
					spend_request = bolus;
					ssm_state_estimate.state_data.spend_request = bolus;
					ssm_state_estimate.state_data.SSM_amount = credit_request;
					
					ssm_state_estimate.state_data.Abrakes = ssm_state_estimate.state_data.SSM_amount;
					ssm_state_estimate.state_data.risky = 0;
					fetchStateEstimateData(getCurrentTimeSeconds());
					ssm_state_estimate.complete_processing(subject_data, ssm_state_estimate.state_data, true, DIAS_STATE, getCurrentTimeSeconds(), Tvec_GPRED, Tvec_IOB, Tvec_Rate, calFlagTime);
					writeStateEstimateData();
					
					//Checking extreme values on variables...
					if(!(checkExtremes(bolus_meal, "bolus_meal") && checkExtremes(bolus_correction, "bolus_correction") 
							&& checkExtremes(ssm_state_estimate.state_data.basal_added, "basal_added") && checkExtremes(ssm_state_estimate.state_data.pre_authorized, "pre_authorized")
							&& checkExtremes(ssm_state_estimate.state_data.SSM_amount, "SSM_amount")))
					{
						Debug.e(TAG, FUNC_TAG, "There is an error in one of the variables in the SSM...returning to Sensor/Stopped Mode");
						
						Bundle b1 = new Bundle();
			    		b1.putString("description", "SSMservice > Check limits reports a component of bolus has an invalid value. System switches to Sensor/Stop Mode");
			    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b1), Event.SET_POPUP_AUDIBLE_ALARM);
			    		
			    		Intent intent = new Intent();
			    		intent.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
			    		intent.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK);
			    		startService(intent);
			    		
			    		return;
					}
					
/*
					Debug.i("creditpool", FUNC_TAG, "Credit request approved > credit_request="+ssm_state_estimate.state_data.credit_request+
							", spend_request="+ssm_state_estimate.state_data.spend_request+
							", advised_bolus="+ssm_state_estimate.state_data.advised_bolus+
							", bolus="+bolus);
					// If the subject requests more insulin than was advised put the extra insulin into credit
					if (bolus >= ssm_state_estimate.state_data.advised_bolus) {
						ssm_state_estimate.state_data.credit_request = 	ssm_state_estimate.state_data.credit_request + bolus - ssm_state_estimate.state_data.advised_bolus;
					}
					else {
						// If the subject requested less insulin than was advised reduce the credit_request first
						double bolusReduction = ssm_state_estimate.state_data.advised_bolus - bolus;
						if (bolusReduction < ssm_state_estimate.state_data.credit_request) {
							ssm_state_estimate.state_data.credit_request = ssm_state_estimate.state_data.credit_request - bolusReduction;
						}
						// If necessary set the credit_request to zero and also reduce the spend_request
						else {
							bolusReduction = bolusReduction - ssm_state_estimate.state_data.credit_request;
							ssm_state_estimate.state_data.credit_request = 0.0;
							ssm_state_estimate.state_data.spend_request = ssm_state_estimate.state_data.spend_request - bolusReduction;
						}
					}					
					// Store the approved credit
					storeCreditRequest(ssm_state_estimate.state_data.credit_request);
					// If the credit_request was approved then the spend_request was approved as well.
					// Store the spent insulin and update the spent and net fields
					storeSpentInsulin(ssm_state_estimate.state_data.spend_request, getCurrentTimeSeconds());
					// Append the remaining spend_request to pre_authorized insulin
					ssm_state_estimate.state_data.pre_authorized = ssm_state_estimate.state_data.pre_authorized + ssm_state_estimate.state_data.spend_request;

					// Send insulin
					if ((!asynchronous && ssm_state_estimate.state_data.SSM_amount>NEGATIVE_EPSILON) || (ssm_state_estimate.state_data.pre_authorized>NEGATIVE_EPSILON))
						sendBolusToPumpService(ssm_state_estimate.state_data.basal_added, 0.0, ssm_state_estimate.state_data.SSM_amount-ssm_state_estimate.state_data.basal_added);
					else
	           			returnStatusToDiAsService(SAFETY_SERVICE_STATE_NORMAL);
*/
					// Store the approved credit/spend
					storeCreditRequest(credit_request);
					storeSpentInsulin(spend_request, getCurrentTimeSeconds());
					
					Debug.w(TAG, FUNC_TAG, "Delivering credit (not bolus)!  Async: "+asynchronous);
					
					//TODO: SENDING BOLUS HERE
					//TODO: SENDING BOLUS HERE
					//TODO: SENDING BOLUS HERE
					
					if (asynchronous && bolus>NEGATIVE_EPSILON)		//This is a meal message, so no basal
						sendBolusToPumpService(0.0, bolus, 0.0);
					else if (bolus>NEGATIVE_EPSILON)				//Synchronous, system generated
						sendBolusToPumpService(ssm_state_estimate.state_data.basal_added, bolus, 0.0);
					else
	           			returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_NORMAL);
        		}
        		else {												// Bolus request approved
					log_action(TAG, "Bolus approved", LOG_ACTION_INFORMATION);
					ssm_state_estimate.state_data.SSM_amount = bolus;
					ssm_state_estimate.state_data.Abrakes = ssm_state_estimate.state_data.SSM_amount;
					ssm_state_estimate.state_data.risky = 0;
					fetchStateEstimateData(getCurrentTimeSeconds());
					ssm_state_estimate.complete_processing(subject_data, ssm_state_estimate.state_data, true, DIAS_STATE, getCurrentTimeSeconds(), Tvec_GPRED, Tvec_IOB, Tvec_Rate, calFlagTime);
					writeStateEstimateData();   
					
					Debug.w(TAG, FUNC_TAG, "Delivering bolus (not credit)! Async: "+asynchronous);
					Debug.w(TAG, FUNC_TAG, "Meal: "+bolus_meal);
					Debug.w(TAG, FUNC_TAG, "Corr: "+bolus_correction);
					
					//TODO: SENDING BOLUS HERE
					//TODO: SENDING BOLUS HERE
					//TODO: SENDING BOLUS HERE
					
					if(asynchronous)		//Meal, so don't add basal
						sendBolusToPumpService(0.0, bolus_meal, bolus_correction);
					else					//Synchronous, or system generated (basal added)
						sendBolusToPumpService(ssm_state_estimate.state_data.basal_added, bolus_meal, bolus_correction);
        		}
        		
        		
        		if(c!=null)
				{
					if(c.moveToLast())
					{
						int id = c.getInt(c.getColumnIndex("_id"));
						Debug.i(TAG, FUNC_TAG, "ID: "+id);						
						ContentValues cv = new ContentValues();
						cv.put("meal_status", edu.virginia.dtc.SysMan.Meal.MEAL_STATUS_APPROVED);
						// Reset 'received_server' so the row is resent to the server:
						cv.put("received_server", false);
						getContentResolver().update(Biometrics.MEAL_URI, cv, "_id='"+id+"'", null);
					}
					c.close();
				}
        		else
					Debug.i(TAG, FUNC_TAG, "Cursor is null for meal table!");
        		
        		break;
	        default:
        		break;
        }
    }
    
	//***************************************************************************************
	// DiAsService interface method
	//***************************************************************************************
    public void returnStatusToDiAsService(int status)
    {
    	returnStatusToDiAsService(status, true);
    }
    
    public void returnStatusToDiAsService(int status, boolean bundle) {
		// Report error state back to the DiAsService
    	final String FUNC_TAG = "returnStatusToDiAsService";
    	
		Message response;
		Bundle responseBundle;
		
		response = Message.obtain(null, status, 0, 0);
		
		if(bundle)
		{
			ssm_state_estimate.state_data.Processing_State = status;
			responseBundle = new Bundle();
			responseBundle.putInt("stoplight", ssm_state_estimate.state_data.stoplight);
			responseBundle.putInt("stoplight2", ssm_state_estimate.state_data.stoplight2);
			responseBundle.putBoolean("isMealBolus", ssm_state_estimate.state_data.isMealBolus);
			responseBundle.putDouble("SSM_amount", ssm_state_estimate.state_data.SSM_amount);
			responseBundle.putDouble("rem_error", ssm_state_estimate.state_data.rem_error);
			responseBundle.putDouble("bolusRecommended", ssm_state_estimate.state_data.bolus_out);
			responseBundle.putDouble("brakes_coeff", ssm_state_estimate.state_data.brakes_coeff);
			responseBundle.putDouble("basal", ssm_state_estimate.state_data.basal);
			responseBundle.putDouble("IOB", ssm_state_estimate.state_data.IOB_meal);
			responseBundle.putDouble("CR", ssm_state_estimate.state_data.CR);
			responseBundle.putDouble("latestCGM", (double)(ssm_state_estimate.state_data.Tvec_cgm_mins.get_last_value()));
			responseBundle.putLong("latestCGMTimeMinutes", ssm_state_estimate.state_data.Tvec_cgm_mins.get_last_time());
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
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
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
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
    		
    		return -1.0;
    	}
    	
    	if (value < POSITIVE_EPSILON)
    	{
    		if (value < NEGATIVE_EPSILON)
    		{
    			Bundle b = new Bundle();
        		b.putString("description", "SSMservice > Check limits reports "+type+" component of bolus is negative: "+value+". Value constrained to 0.0");
        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
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
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
    		
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
			paramBundle.putDouble("SSM_amount", ssm_state_estimate.state_data.SSM_amount);
			
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
//			ssm_state_estimate.state_data.pre_authorized = checkLimits(ssm_state_estimate.state_data.pre_authorized, param_meal_max_constraint, MEAL_MAX_CONSTRAINT, MEAL_TOO_HIGH_LIMIT, "pre_authorized");
			
			//Check limits...
			bolus_basal = checkLimits(bolus_basal, BASAL_MAX_CONSTRAINT, BASAL_MAX_CONSTRAINT, BASAL_TOO_HIGH_LIMIT, "basal");
			bolus_correction = checkLimits(bolus_correction, CORRECTION_MAX_CONSTRAINT, CORRECTION_MAX_CONSTRAINT, CORRECTION_TOO_HIGH_LIMIT, "correction");
			bolus_meal = checkLimits(bolus_meal, MEAL_MAX_CONSTRAINT, MEAL_MAX_CONSTRAINT, MEAL_TOO_HIGH_LIMIT, "meal");
			ssm_state_estimate.state_data.pre_authorized = checkLimits(ssm_state_estimate.state_data.pre_authorized, MEAL_MAX_CONSTRAINT, MEAL_MAX_CONSTRAINT, MEAL_TOO_HIGH_LIMIT, "pre_authorized");
			
			
			if(bolus_basal < 0.0 || bolus_correction < 0.0 || bolus_meal < 0.0 || ssm_state_estimate.state_data.pre_authorized < 0.0)
			{
				bolus_basal = 0.0;
				bolus_correction = 0.0;
				bolus_meal = 0.0;
				ssm_state_estimate.state_data.pre_authorized = 0.0;
				
				Bundle b = new Bundle();
	    		b.putString("description", "SSMservice > Check limits reports a component of bolus has an invalid value. System switches to Sensor/Stop Mode");
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_ALARM);
	    		
	    		Intent intent = new Intent();
	    		intent.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
	    		intent.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK);
	    		startService(intent);
			}
			
			if((ssm_state_estimate.state_data.pre_authorized + bolus_meal) > MEAL_MAX_CONSTRAINT)
			{
				Debug.w(TAG, FUNC_TAG, "Limiting the pre-auth and meal bolus to: "+MEAL_MAX_CONSTRAINT);
				
				//Pre-authorized has highest priority
				if(ssm_state_estimate.state_data.pre_authorized >= MEAL_MAX_CONSTRAINT)
				{
					ssm_state_estimate.state_data.pre_authorized = MEAL_MAX_CONSTRAINT;
					bolus_meal = 0.0;
				}
				else	//Pre-authorized is less than Meal constraint so we just need to subtract the rest and put it in bolus_meal
				{
					bolus_meal = MEAL_MAX_CONSTRAINT - ssm_state_estimate.state_data.pre_authorized;
				}
				
				Bundle b = new Bundle();
	    		b.putString("description", "SSMservice > Sum of Pre-Authorized and Meal boluses was greater than the meal constraint." +
	    				" Values constrained to "+bolus_meal+"U (meal) and "+ssm_state_estimate.state_data.pre_authorized+"U (pre_auth)");
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
				
				Debug.w(TAG, FUNC_TAG, "Meal: "+bolus_meal+" Pre-Auth: "+ssm_state_estimate.state_data.pre_authorized);
			}
			
			//paramBundle.putDouble(INSULIN_BASAL_BOLUS, bolus_basal);
			if (temporaryBasalRateActive()){
				
				Cursor c = getContentResolver().query(Biometrics.TEMP_BASAL_URI, null, null, null, null);
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
			paramBundle.putDouble("pre_authorized", ssm_state_estimate.state_data.pre_authorized);
			double pre = ssm_state_estimate.state_data.pre_authorized;
			ssm_state_estimate.state_data.pre_authorized = 0.0;
			paramBundle.putDouble("bolus_max", ssm_param.filter.bolus_max);
     		paramBundle.putLong("simulatedTime", getCurrentTimeSeconds());
			putTvector(paramBundle, subject_data.subjectBasal, "Basaltimes", "Basalvalues");
     		double correction = ssm_state_estimate.state_data.SSM_amount-ssm_state_estimate.state_data.basal_added;
			msg.setData(paramBundle);
			// Log the parameters for IO testing
			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
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
			ssm_state_estimate.state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_AWAITING_PUMP_RESPONSE;
			debug_message("BOLUS_TRACE", "SafetyService > send bolus to pump > bolus="+ssm_state_estimate.state_data.SSM_amount);
		}
		catch (RemoteException e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
			e.printStackTrace();
			ssm_state_estimate.state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_NORMAL;
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
           			ssm_state_estimate.state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_PUMP_ERROR;
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_PUMP_ERROR"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
           			returnStatusToDiAsService(ssm_state_estimate.state_data.Processing_State);
           			break;
           		case Pump.PUMP_STATE_COMMAND_ERROR:
           			debug_message(TAG, "PUMP_STATE_COMMAND_ERROR");
           			ssm_state_estimate.state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_PUMP_ERROR;
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_COMMAND_ERROR"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
           			returnStatusToDiAsService(ssm_state_estimate.state_data.Processing_State);
           			break;
           		case Pump.PUMP_STATE_NO_RESPONSE:
           			debug_message(TAG, "PUMP_STATE_NO_RESPONSE");
           			ssm_state_estimate.state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_PUMP_ERROR;
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_NO_RESPONSE"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
           			returnStatusToDiAsService(ssm_state_estimate.state_data.Processing_State);
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
			Tvec_bolus_hist_seconds.dump(TAG, "Tvec_bolus_hist_seconds", 8);
			Tvec_basal_bolus_hist_seconds.dump(TAG, "Tvec_basal_bolus_hist_seconds", 8);
			Tvec_meal_bolus_hist_seconds.dump(TAG, "Tvec_meal_bolus_hist_seconds", 8);
			Tvec_corr_bolus_hist_seconds.dump(TAG, "Tvec_corr_bolus_hist_seconds", 8);

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
		debug_message(TAG, "writeStateEstimateData > state_data.CGM = "+ssm_state_estimate.state_data.CGM);
	    ContentValues values = new ContentValues();
	    values.put(TIME, getCurrentTimeSeconds());
	    values.put(ENOUGH_DATA, ssm_state_estimate.state_data.enough_data);
	    if (ssm_state_estimate.state_data.asynchronous)
	    	values.put(ASYNCHRONOUS, 1);
	    else
	    	values.put(ASYNCHRONOUS, 0);
	    if (DIAS_STATE == DIAS_STATE_CLOSED_LOOP || DIAS_STATE == DIAS_STATE_SAFETY_ONLY || (DIAS_STATE == DIAS_STATE_OPEN_LOOP && ssm_state_estimate.state_data.enough_data)) {
		    values.put(CGM, ssm_state_estimate.state_data.CGM);
		    values.put(GPRED, ssm_state_estimate.state_data.Gpred);
		    values.put(GBRAKES, ssm_state_estimate.state_data.Gbrakes);
		    values.put(GPRED_LIGHT, ssm_state_estimate.state_data.Gpred_light);
		    values.put(GPRED_BOLUS, ssm_state_estimate.state_data.Gpred_bolus);
		    values.put(GPRED_1H, ssm_state_estimate.state_data.Gpred_1h);
		    values.put(CGM_CORR, ssm_state_estimate.state_data.CGM_corr);
		    values.put(XI00, ssm_state_estimate.state_data.Xi0[0]);
		    values.put(XI01, ssm_state_estimate.state_data.Xi0[1]);
		    values.put(XI02, ssm_state_estimate.state_data.Xi0[2]);
		    values.put(XI03, ssm_state_estimate.state_data.Xi0[3]);
		    values.put(XI04, ssm_state_estimate.state_data.Xi0[4]);
		    values.put(XI05, ssm_state_estimate.state_data.Xi0[5]);
		    values.put(XI06, ssm_state_estimate.state_data.Xi0[6]);
		    values.put(XI07, ssm_state_estimate.state_data.Xi0[7]);
	    }
	    values.put(STOPLIGHT, ssm_state_estimate.state_data.stoplight);
	    values.put(STOPLIGHT2, ssm_state_estimate.state_data.stoplight2);
	    values.put(IOB, ssm_state_estimate.state_data.IOB_meal);
	    values.put(IOBLAST, ssm_state_estimate.state_data.IOB_meal);
	    values.put(IOBLAST2, ssm_state_estimate.state_data.IOB_no_meal);
	    values.put(ISMEALBOLUS, ssm_state_estimate.state_data.isMealBolus);
	    values.put(CHOPRED, ssm_state_estimate.state_data.CHOpred);
	    values.put(ABRAKES, ssm_state_estimate.state_data.Abrakes);
	    values.put(UMAX_IOB, ssm_state_estimate.state_data.InsulinConstraintInUnits);
	    values.put(IOB_CONTROLLER_RATE, ssm_state_estimate.state_data.Rate);
	    values.put(SSM_AMOUNT, ssm_state_estimate.state_data.SSM_amount);
	    values.put(STATE, ssm_state_estimate.state_data.Processing_State);
	    values.put(SSM_STATE, ssm_state_estimate.state_data.Tvec_state.get_last_value());
	    values.put(SSM_STATE_TIMESTAMP, ssm_state_estimate.state_data.Tvec_state.get_last_time());
	    values.put(BRAKES_COEFF, ssm_state_estimate.state_data.brakes_coeff);
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

	
	private void storeCreditRequest(double credit) {
		//
		//  Store the pre_authorized insulin in the database
		//
    	final String FUNC_TAG = "storeCreditRequest";
	    ContentValues values = new ContentValues();
	    values.put(TIME, getCurrentTimeSeconds());
	    values.put("credit", credit);
	    values.put("spent", 0.0);
	    values.put("net", credit);
	    debug_message(TAG_CREDITPOOL, "storeCreditRequest > credit="+values.getAsDouble("credit")+", spent="+values.getAsDouble("spent")+", net="+values.getAsDouble("net"));
	    try {
	    		Uri uri = getContentResolver().insert(Biometrics.INSULIN_CREDIT_URI, values);
	    }
	    catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
	    	Log.e("creditpool",(e.toString() == null) ? "null" : "Error > "+e.toString());
	    }
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