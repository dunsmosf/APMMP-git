//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.Tvector.Tvector;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.IntentFilter;
import android.content.UriMatcher;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;
import android.os.Message;
import android.os.Handler;
import android.os.Bundle;
import android.os.RemoteException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

public class IOMain extends Service {
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	// Identify owner of record in User Table 1
	public static final int MEAL_IOB_CONTROL = 10;
	
	// DiAs State Variable and Definitions - state for the system as a whole
	public int DIAS_STATE;
	public int DIAS_STATE_PREVIOUS;
	public static final int DIAS_STATE_SENSOR_ONLY = 4;
	public static final int DIAS_STATE_UNKNOWN = -1;
	public static final int DIAS_STATE_STOPPED = 0;
	public static final int DIAS_STATE_OPEN_LOOP = 1;
	public static final int DIAS_STATE_CLOSED_LOOP = 2;
	public static final int DIAS_STATE_SAFETY_ONLY = 3;

	// Bolus status static variables (These are from Tandem, but seem to cover most possibilities)
	public static final int UNKNOWN = -1;
	public static final int PENDING = 0;
	public static final int DELIVERING = 1;
	public static final int DELIVERED = 2;
	public static final int CANCELLED = 3;
	public static final int INTERRUPTED = 4;
	public static final int INVALID_REQ = 5;
	
	public static final int PRE_MANUAL = 21;
	
	// Only query for MDI insulin if the system has not delivered a bolus for two hours or more
	private static final int TIME_FROM_LAST_BOLUS_SECONDS = 7200;
	private boolean MDI_injection_amount_has_been_received = false;
	
	private static final String VERSION = "1.0.0";
	private static final boolean DEBUG_MODE = true;
	public static final String TAG = "HMSservice";
    public static final String IO_TEST_TAG = "HMSserviceIO";
    
	private boolean asynchronous;
	private int Timer_Ticks_Per_Control_Tick = 1;
	private int Timer_Ticks_To_Next_Meal_From_Last_Rate_Change = 1;
	public int cycle_duration_seconds = 300;
	public int cycle_duration_mins = cycle_duration_seconds/60;
	public static final int MAX_DELAY_FROM_BOLUS_CALC_TO_BOLUS_APPROVE_SECONDS = 120;
	
	// Interface definitions for the biometricsContentProvider
	public static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
    public static final Uri CGM_URI = Uri.parse("content://"+ PROVIDER_NAME + "/cgm");
    public static final Uri INSULIN_URI = Uri.parse("content://"+ PROVIDER_NAME + "/insulin");					//Compressed to a single table ("/insulin")
    public static final Uri STATE_ESTIMATE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/stateestimate");
    public static final Uri HMS_STATE_ESTIMATE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/hmsstateestimate");
    public static final Uri MEAL_URI = Uri.parse("content://"+ PROVIDER_NAME + "/meal");
    public static final Uri USER_TABLE_1_URI = Uri.parse("content://"+ PROVIDER_NAME + "/user1");
    public static final Uri USER_TABLE_2_URI = Uri.parse("content://"+ PROVIDER_NAME + "/user2");
    public static final String TIME = "time";
    public static final String CGM1 = "cgm";
    public static final String INSULINRATE1 = "Insurate1";
    public static final String INSULINBOLUS1= "Insubolus1";
    public static final String INSULIN_BASAL_BOLUS = "basal_bolus";
    public static final String INSULIN_MEAL_BOLUS = "meal_bolus";
    public static final String INSULIN_CORR_BOLUS = "corr_bolus";
    public static final String SSM_STATE = "SSM_state";
    public static final String SSM_STATE_TIMESTAMP = "SSM_state_timestamp";

    // Field definitions for HMS_STATE_ESTIMATE_TABLE
    public static final String IOB = "IOB";
    public static final String GPRED = "Gpred";
    public static final String GPRED_CORRECTION = "Gpred_correction";
    public static final String GPRED_BOLUS = "Gpred_bolus";
    public static final String XI00 = "Xi00";
    public static final String XI01 = "Xi01";
    public static final String XI02 = "Xi02";
    public static final String XI03 = "Xi03";
    public static final String XI04 = "Xi04";
    public static final String XI05 = "Xi05";
    public static final String XI06 = "Xi06";
    public static final String XI07 = "Xi07";
    public static final String BRAKES_COEFF = "brakes_coeff";
    public static final String BOLUS_AMOUNT = "bolus_amount";
	
	// Working storage for current cgm and insulin data
    private double brakes_coeff = 1.0;
	Tvector Tvec_cgm1, Tvec_cgm2, Tvec_insulin_rate1, Tvec_spent;
	Tvector Tvec_IOB, Tvec_GPRED;
	private double Gpred_30m;
	public static final int TVEC_SIZE = 96;				// 8 hours of samples at 5 mins per sample
	// Store most recent timestamps in seconds for each biometric Tvector
	Long last_Tvec_cgm1_time_secs, last_Tvec_insulin_bolus1_time_secs, last_Tvec_requested_insulin_bolus1_time_secs;
	
	// Used to calculate and store Param object
	private Params params;				// This class contains controller parameters
	public Subject subject;		 		// This class encapsulates current Subject SI parameters
	private Context context;
	
	// Used to calculate and store state_estimate object
	public InsulinTherapy insulin_therapy;
	// Used to calculate and store HMSData object
	public HMS hms;
	
	
    public BroadcastReceiver TickReceiver; 

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
    
	/*
	 * 
	 *  Interface to the Application (our only Client)
	 * 
	 */
	// HMSservice interface definitions
	public static final int APC_SERVICE_CMD_NULL = 0;
	public static final int APC_SERVICE_CMD_START_SERVICE = 1;
	public static final int APC_SERVICE_CMD_REGISTER_CLIENT = 2;
	public static final int APC_SERVICE_CMD_CALCULATE_STATE = 3;
	public static final int APC_SERVICE_CMD_STOP_SERVICE = 4;
	public static final int APC_SERVICE_CMD_CALCULATE_BOLUS = 5;
	
    // HMSservice return values
    public static final int APC_PROCESSING_STATE_NORMAL = 10;
    public static final int APC_PROCESSING_STATE_ERROR = -11;
    public static final int APC_CONFIGURATION_PARAMETERS = 12;		// APController parameter status return
//    public static final int APC_CALCULATED_BOLUS = 13;    	// Calculated bolus return value
    
    // APC_SERVICE_CMD_CALCULATE_BOLUS return status
//	public static final int APC_CALCULATED_BOLUS_SUCCESS = 0;
//	public static final int APC_CALCULATED_BOLUS_MISSING_SUBJECT_DATA = -1;
//	public static final int APC_CALCULATED_BOLUS_MISSING_STATE_ESTIMATE_DATA = -2;
//	public static final int APC_CALCULATED_BOLUS_INVALID_CREDIT_REQUEST = -3;
  
    // APController type
    private int APC_TYPE;
    public static final int APC_TYPE_HMS = 1;
    public static final int APC_TYPE_RCM = 2;
    public static final int APC_TYPE_AMYLIN = 3;
    public static final int APC_TYPE_MEALIOB = 4;
    public static final int APC_TYPE_HMSIOB = 5;
    public static final int APC_TYPE_SHELL = 9999;

    // Define AP Controller behavior
    private int APC_MEAL_CONTROL;
    public static final int APC_NO_MEAL_CONTROL = 1;
    public static final int APC_WITH_MEAL_CONTROL = 2;

    /* Messenger for sending responses to the client (Application). */
    public Messenger mMessengerToClient = null;
    /* Target we publish for clients to send commands to IncomingHandlerFromClient. */
    final Messenger mMessengerFromClient = new Messenger(new IncomingHMSHandler());
 
    /* When binding to the service, we return an interface to our messenger for sending messages to the service. */
    @Override
    public IBinder onBind(Intent intent) {
//        Toast toast = Toast.makeText(getApplicationContext(), TAG+" binding to Application", Toast.LENGTH_SHORT);
//		toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
//		toast.show();
        return mMessengerFromClient.getBinder();
    }
    
    /* Handles incoming commands from DiAsService. */
    class IncomingHMSHandler extends Handler {
    	final String FUNC_TAG = "IncomingHMSHandler";
    	Bundle paramBundle, responseBundle;
    	Message response;
    	@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
				case APC_SERVICE_CMD_NULL:		// null command
					debug_message(TAG, "APC_SERVICE_CMD_NULL");
//                	Toast.makeText(getApplicationContext(), TAG+" > APC_SERVICE_CMD_NULL", Toast.LENGTH_LONG).show();
					break;
				case APC_SERVICE_CMD_START_SERVICE:		// start service command
					// Create Param object with subject parameters received from Application
					debug_message(TAG, "APC_SERVICE_CMD_START_SERVICE");
					paramBundle = msg.getData();
					double TDI = (double)paramBundle.getDouble("TDI");
					int IOB_curve_duration_hours = paramBundle.getInt("IOB_curve_duration_hours");
					// Create and initialize the Subject object
					subject = new Subject(getCurrentTimeSeconds(), getApplicationContext());
					
					// Log the parameters for IO testing
					if (true) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DIAsService >> (APC), IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_SERVICE_CMD_START_SERVICE"+", "+
                						"TDI="+TDI+", "+
                						"IOB_curve_duration_hours="+IOB_curve_duration_hours
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}

					// Inform DiAsService of the APC_TYPE and how many ticks you require per control tick
					debug_message(TAG, "Timer_Ticks_Per_Control_Tick="+Timer_Ticks_Per_Control_Tick);
					response = Message.obtain(null, APC_CONFIGURATION_PARAMETERS, 0, 0);
					responseBundle = new Bundle();
					Timer_Ticks_Per_Control_Tick = 1;
					responseBundle.putInt("Timer_Ticks_Per_Control_Tick", Timer_Ticks_Per_Control_Tick);
    				Timer_Ticks_To_Next_Meal_From_Last_Rate_Change = 1;
    				responseBundle.putInt("Timer_Ticks_To_Next_Meal_From_Last_Rate_Change", Timer_Ticks_To_Next_Meal_From_Last_Rate_Change);	// Ticks from meal announcement to meal start
					
					// Log the parameters for IO testing
					if (true) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "(APC) >> DiAsService, IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_CONFIGURATION_PARAMETERS"+", "+
                						"Timer_Ticks_Per_Control_Tick="+Timer_Ticks_Per_Control_Tick+", "+
                						"Timer_Ticks_To_Next_Meal_From_Last_Rate_Change="+Timer_Ticks_To_Next_Meal_From_Last_Rate_Change
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
					response.setData(responseBundle);
					try {
						mMessengerToClient.send(response);
					} 
					catch (RemoteException e) {
						e.printStackTrace();
					}
					break;
				case APC_SERVICE_CMD_CALCULATE_STATE:
					debug_message(TAG, "APC_SERVICE_CMD_CALCULATE_STATE");
					paramBundle = msg.getData();
					asynchronous = (boolean)paramBundle.getBoolean("asynchronous");
					long corrFlagTime = (long)paramBundle.getLong("corrFlagTime", 0);
					long hypoFlagTime = (long)paramBundle.getLong("hypoFlagTime", 0);
					long calFlagTime = (long)paramBundle.getLong("calFlagTime", 0);
					long mealFlagTime = (long)paramBundle.getLong("mealFlagTime", 0);
//					brakes_coeff = paramBundle.getDouble("brakes_coeff", 1.0);
					double DIAS_STATE = paramBundle.getInt("DIAS_STATE", 0);
					double tick_modulus = paramBundle.getInt("tick_modulus", 0);
					boolean currentlyExercising = paramBundle.getBoolean("currentlyExercising", false);
					Bolus meal_bolus;

					// Log the parameters for IO testing
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DIAsService >> (APC), IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_SERVICE_CMD_CALCULATE_STATE"+", "+
                						"asynchronous="+asynchronous+", "+
                						"corrFlagTime="+corrFlagTime+", "+
                						"calFlagTime="+calFlagTime+", "+
                						"hypoFlagTime="+hypoFlagTime+", "+
                						"mealFlagTime="+mealFlagTime+", "+
//                						"brakes_coeff="+brakes_coeff+", "+
                						"DIAS_STATE="+DIAS_STATE+", "+
                						"tick_modulus="+tick_modulus
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					//
					// Closed Loop handler
					//
					if (DIAS_STATE == DIAS_STATE_CLOSED_LOOP) {
						// Synchronous operation
						if (!asynchronous) {
							double spend_request = 0.0;
							double differential_basal_rate = 0.0;
							double recommended_bolus = 0.0;
							debug_message(TAG, "Synchronous call...");
							// Calculate insulin therapy if there is some recent CGM, Gpred and IOB data to work with...
							if (fetchAllBiometricData(getCurrentTimeSeconds()-(300*24+2*60)) && !hms_req_meal_protect(10) && fetchStateEstimateData(getCurrentTimeSeconds()-(300*5+2*60))) {
								
								// 2. Calculate a correction bolus if needed
								if (hms == null) {
									hms = new HMS(	
													getCurrentTimeSeconds(),
													Tvec_IOB.get_last_value(),
													Tvec_cgm1.get_last_value(),
													Tvec_GPRED.get_last_value(),
													Gpred_30m,
													getApplicationContext()
													);
									if (hms.valid) {
										recommended_bolus = hms.HMS_calculation(
																getCurrentTimeSeconds(),
																Tvec_IOB.get_last_value(),
																Tvec_cgm1,
																Tvec_GPRED.get_last_value(),
																Gpred_30m,
																getApplicationContext()
															);
									}
								}
								else {
									recommended_bolus = hms.HMS_calculation(
											getCurrentTimeSeconds(),
											Tvec_IOB.get_last_value(),
											Tvec_cgm1,
											Tvec_GPRED.get_last_value(),
											Gpred_30m,
											getApplicationContext()
										);
								}
							}
							// Disable basal rate modulation so differential_basal_rate is always zero
							differential_basal_rate = 0.0;
							response = Message.obtain(null, APC_PROCESSING_STATE_NORMAL, 0, 0);
							responseBundle = new Bundle();
							responseBundle.putBoolean("doesBolus", true);
							responseBundle.putBoolean("doesRate", false);
							responseBundle.putBoolean("doesCredit", false);
							responseBundle.putDouble("recommended_bolus", recommended_bolus);
							responseBundle.putDouble("creditRequest", 0.0);
							responseBundle.putDouble("spendRequest", 0.0);
							responseBundle.putBoolean("new_differential_rate", false);
							responseBundle.putDouble("differential_basal_rate", 0.0);
							responseBundle.putDouble("IOB", 0.0);
							debug_message(TAG, "return_value: recommended_bolus="+recommended_bolus);
						}
					}
					//
					// Open Loop handler
					//
					else if (DIAS_STATE == DIAS_STATE_OPEN_LOOP) {
						response = Message.obtain(null, APC_PROCESSING_STATE_NORMAL, 0, 0);
						responseBundle = new Bundle();
						responseBundle.putDouble("recommended_bolus", 0.0);
						responseBundle.putDouble("creditRequest", 0.0);
						responseBundle.putDouble("spendRequest", 0.0);
						responseBundle.putBoolean("new_differential_rate", false);
						responseBundle.putDouble("differential_basal_rate", 0.0);
						responseBundle.putDouble("IOB", 0.0);
						responseBundle.putBoolean("extendedBolus", false);
						responseBundle.putDouble("extendedBolusMealInsulin", 0.0);
						responseBundle.putDouble("extendedBolusCorrInsulin", 0.0);
					}
					else {
						response = Message.obtain(null, APC_PROCESSING_STATE_NORMAL, 0, 0);
						responseBundle = new Bundle();
						responseBundle.putDouble("recommended_bolus", 0.0);
						responseBundle.putDouble("creditRequest", 0.0);
						responseBundle.putDouble("spendRequest", 0.0);
						responseBundle.putBoolean("new_differential_rate", false);
						responseBundle.putDouble("differential_basal_rate", 0.0);
						responseBundle.putDouble("IOB", 0.0);
						responseBundle.putBoolean("extendedBolus", false);
						responseBundle.putDouble("extendedBolusMealInsulin", 0.0);
						responseBundle.putDouble("extendedBolusCorrInsulin", 0.0);
						log_IO(TAG, "Error > Invalid DiAs State == "+DIAS_STATE);
					}
						
        			// Log the parameters for IO testing
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "(APC) >> DiAsService, IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_PROCESSING_STATE_NORMAL"+", "+
                						"doesBolus="+responseBundle.getBoolean("doesBolus")+", "+
                						"doesRate="+responseBundle.getBoolean("doesRate")+", "+
                						"doesCredit="+responseBundle.getBoolean("doesCredit")+", "+
                						"recommended_bolus="+responseBundle.getDouble("recommended_bolus")+", "+
                						"creditRequest="+responseBundle.getDouble("creditRequest")+", "+
                						"spendRequest="+responseBundle.getDouble("spendRequest")+", "+
                						"new_differential_rate="+responseBundle.getBoolean("new_differential_rate")+", "+
                						"differential_basal_rate="+responseBundle.getDouble("differential_basal_rate")+", "+
                						"IOB="+responseBundle.getDouble("IOB")+", "+
                						"extendedBolus="+responseBundle.getDouble("extendedBolus")+", "+
                						"extendedBolusMealInsulin="+responseBundle.getDouble("extendedBolusMealInsulin")+", "+
                						"extendedBolusCorrInsulin="+responseBundle.getDouble("extendedBolusCorrInsulin")
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
        			}        			
					responseBundle.putBoolean("asynchronous", asynchronous);
					
					// Log response data to hmsstateestimate
					storeHMSTableData(getCurrentTimeSeconds(),
							responseBundle.getDouble("recommended_bolus"),
							responseBundle.getDouble("creditRequest"),
							responseBundle.getDouble("spendRequest"),
							responseBundle.getDouble("differential_basal_rate", 0.0));
					
					// Send response to DiAsService
					response.setData(responseBundle);
					try {
						mMessengerToClient.send(response);
					} 
					catch (RemoteException e) {
						e.printStackTrace();
					}
					break;
				case APC_SERVICE_CMD_CALCULATE_BOLUS:
					break;
				case APC_SERVICE_CMD_STOP_SERVICE:
					debug_message(TAG, "APC_SERVICE_CMD_STOP_SERVICE");
					stopSelf();
					break;
				case APC_SERVICE_CMD_REGISTER_CLIENT:
					debug_message(TAG, "APC_SERVICE_CMD_REGISTER_CLIENT");
					mMessengerToClient = msg.replyTo;
            		Bundle b = new Bundle();
            		b.putString(	"description", "DiAsService >> APC, IO_TEST"+", "+FUNC_TAG+", "+
            						"APC_SERVICE_CMD_REGISTER_CLIENT"
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					debug_message(TAG, "mMessengerToClient="+mMessengerToClient);
//    				Toast.makeText(getApplicationContext(), TAG+" > APC_SERVICE_CMD_REGISTER_CLIENT", Toast.LENGTH_LONG).show();
					break;
				default:
					super.handleMessage(msg);
            }
        }
    }
	
	@Override
	public void onCreate() {
//		Toast toast = Toast.makeText(this, TAG+" onCreate: Service Created", Toast.LENGTH_SHORT);
//		toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
//		toast.show();
		DIAS_STATE = DIAS_STATE_STOPPED;
		DIAS_STATE_PREVIOUS = DIAS_STATE_STOPPED;
        log_action(TAG, "onCreate");
        brakes_coeff = 1.0;
        asynchronous = false;
        insulin_therapy = null;
        hms = null;
		Tvec_cgm1 = new Tvector(TVEC_SIZE);
		Tvec_cgm2 = new Tvector(TVEC_SIZE);
		Tvec_insulin_rate1 = new Tvector(TVEC_SIZE);
		Tvec_spent = new Tvector(TVEC_SIZE);
		Tvec_IOB = new Tvector(TVEC_SIZE);
		Tvec_GPRED = new Tvector(TVEC_SIZE);
		Gpred_30m = 0.0;
		// Initialize most recent timestamps
		last_Tvec_cgm1_time_secs = new Long(0);
		last_Tvec_insulin_bolus1_time_secs = new Long(0);
		last_Tvec_requested_insulin_bolus1_time_secs = new Long(0);
		// Set up controller parameters
		params = new Params();
		context = getApplicationContext();
		

		
		
        // Set up a Notification for this Service
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        int icon = edu.virginia.dtc.APCservice.R.drawable.icon;
//        int icon = R.drawable.icon;
        CharSequence tickerText = "";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
        Context context = getApplicationContext();
        CharSequence contentTitle = "BRM Service";
        CharSequence contentText = "Mitigating Hyperglycemia";
        Intent notificationIntent = new Intent(this, IOMain.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        final int APC_ID = 3;
//        mNotificationManager.notify(APC_ID, notification);
        // Make this a Foreground Service
        startForeground(APC_ID, notification);
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();  
    }

	@Override
	public void onDestroy() {
//		Toast toast = Toast.makeText(this, TAG+" Stopped", Toast.LENGTH_LONG);
//		toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
//		toast.show();
		debug_message(TAG, "onDestroy");
        log_action(TAG, "onDestroy");
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
			return 0;
		}
	
	public boolean fetchAllBiometricData(long time) {
		boolean return_value = false;
		// Clear CGM Tvector
		Tvec_cgm1.init();
		Long Time = new Long(time);
		// Fetch full sensor1 time/data from cgmiContentProvider
		try {
			// Fetch the last 2 hours of CGM data
			Cursor c=getContentResolver().query(CGM_URI, null, "time > "+Time.toString(), null, null);
//			debug_message(TAG,"CGM > c.getCount="+c.getCount());
			long last_time_temp_secs = 0;
			double cgm1_value, cgm2_value;
			if (c.moveToFirst()) {
				do{
					// Fetch the cgm1 and cgm2 values so that they can be screened for validity
					cgm1_value = (double)c.getDouble(c.getColumnIndex("cgm"));
					// Make sure that cgm1_value is in the range of validity
					if (cgm1_value>=39.0 && cgm1_value<=401.0) {
						// Save the latest timestamp from the retrieved data
						if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
							last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
						}
						// time in seconds
						Tvec_cgm1.put(c.getLong(c.getColumnIndex("time")), cgm1_value);
						return_value = true;
					}
				} while (c.moveToNext());
			}
			c.close();
			last_Tvec_cgm1_time_secs = last_time_temp_secs;
		}
        catch (Exception e) {
        		Log.e("Error SafetyService", e.getMessage());
        }
		return return_value;
	}

	
	public boolean fetchStateEstimateData(long time) {
		boolean return_value = false;
		// Clear Tvectors
		Tvec_IOB.init();
		Tvec_GPRED.init();
		Gpred_30m = 0.0;
		// Fetch data from State Estimate data records
		Long Time = new Long(time);
		Cursor c=getContentResolver().query(STATE_ESTIMATE_URI, null, Time.toString(), null, null);
		long state_estimate_time;
		if (c.moveToFirst()) {
			do{
				if (!c.isNull(c.getColumnIndex("asynchronous"))) {
					if (c.getInt(c.getColumnIndex("asynchronous")) == 0) {
						state_estimate_time = c.getLong(c.getColumnIndex("time"));
						Tvec_IOB.put(state_estimate_time, c.getDouble(c.getColumnIndex("IOB")));
						Tvec_GPRED.put(state_estimate_time, c.getDouble(c.getColumnIndex("GPRED")));
						Gpred_30m = c.getDouble(c.getColumnIndex("Gbrakes"));
						return_value = true;
					}
				}
				brakes_coeff = c.getDouble(c.getColumnIndex("brakes_coeff"));
			} while (c.moveToNext());
		}
		else {
			debug_message(TAG, "State Estimate Table empty!");
		}
		c.close();
		return return_value;
	}
	//function to protect against injecting corrections within a certain amount of time "duration" after requesting a meal bolus.
	//duration is in minutes
	public boolean hms_req_meal_protect(int duration) {
		boolean return_value = false;
		
		// Fetch data from insulin data records
		Cursor c=getContentResolver().query(INSULIN_URI,new String[]{"req_time","req_meal","req_corr","status"},"req_meal>0 OR req_corr>0",null,"req_time DESC Limit 1");
		long requested_meal_time=0;
		int status=-1;
		if (c.moveToFirst()) {
			if (!c.isNull(c.getColumnIndex("status"))) {
				requested_meal_time = c.getLong(c.getColumnIndex("req_time"));
				status=c.getInt(c.getColumnIndex("status"));
				if (((getCurrentTimeSeconds()-requested_meal_time)<(duration*60))&&((status==Pump.PENDING)||(status==Pump.DELIVERING))){
					return_value = true;
				}				
			}			
		}
		else {
			debug_message(TAG, "Insulin Table empty!");
		}
		c.close();
		return return_value;
	}
	
	public double glucoseTarget(long time) {
		// Get the offset in hours into the current day in the current time zone (based on cell phone time zone setting)
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(time*1000)/1000;
		int timeNowMins = (int)((time+UTC_offset_secs)/60)%1440;
		double ToD_hours = (double)timeNowMins/60.0;
//		debug_message(TAG, "subject_parameters > time="+time+", time/60="+time/60+", timeNowMins="+timeNowMins+", ToD_hours="+ToD_hours);
		double x;
		if (ToD_hours<6.0) {
			x = (1.0+ToD_hours)/7.0;
		}
		else if (ToD_hours>=6.0 && ToD_hours<7.0) {
			x = 7.0-ToD_hours;
		}
		else if (ToD_hours>=7.0 && ToD_hours<23.0) {
			x = 0.0;
		}
		else {
			x = (ToD_hours-23.0)/7.0;
		}
		return 160.0-45.0*Math.pow((x/0.5),3.0)/(1.0+Math.pow((x/0.5),3.0));
	}
	
	public long getCurrentTimeSeconds() {
		return (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970		
	}

	public void storeUserTable1Data(long time,
									double INS_target_sat,
									double INS_target_slope_sat,
									double differential_basal_rate, 
									double MealBolusA,
									double MealBolusArem,
									double spend_request,
									double CorrA,
									double IOBrem,
									double d,
									double h,
									double H,
									double cgm_slope1,
									double cgm_slope2,
									double cgm_slope_diff,
									double X,
									double detect_meal
									) {
	  	ContentValues values = new ContentValues();
	  	values.put("time", time);
	  	values.put("l0", MEAL_IOB_CONTROL);
       	values.put("d0", INS_target_sat);
       	values.put("d1", INS_target_slope_sat);
       	values.put("d2", differential_basal_rate);
       	values.put("d3", MealBolusA);
       	values.put("d4", MealBolusArem);
       	values.put("d5", spend_request);
       	values.put("d6", CorrA);
       	values.put("d7", IOBrem);
       	values.put("d8", d);
       	values.put("d9", h);
       	values.put("d10", H);
       	values.put("d11", cgm_slope1);
       	values.put("d12", cgm_slope2);
       	values.put("d13", cgm_slope_diff);
       	values.put("d14", X);
       	values.put("d15", detect_meal);
       	Uri uri;
       	try {
       		uri = getContentResolver().insert(USER_TABLE_1_URI, values);
       	}
       	catch (Exception e) {
       		Log.e(TAG, e.getMessage());
       	}		
	}
		
	public void storeHMSTableData(long time,
			double correction_in_units,
			double creditRequest,
			double spendRequest,
			double differential_basal_rate
			) {
		ContentValues values = new ContentValues();
		values.put("time", time);
		// If there is no valid hmsstateestimate data yet then save a marker of correction_in_units==-2
		// This is used as a "fake correction" to make sure no real correction is given within the first hour after startup
		if (hms != null) {
			if (!hms.hms_data.valid) {
				values.put("correction_in_units", -2.0);
			}
			else if (correction_in_units < 0.001) {
				values.put("correction_in_units", 0.0);
			}
			else {
				values.put("correction_in_units", correction_in_units);
			}
			values.put("creditRequest", creditRequest);
			values.put("spendRequest", spendRequest);
			values.put("differential_basal_rate", differential_basal_rate);
			Uri uri;
			try {
				uri = getContentResolver().insert(HMS_STATE_ESTIMATE_URI, values);
			}
			catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}		
		}
	}
	
	private long retrieveTimestampOfMostRecentMDIInsulinDelivery() {
		long timeStamp = -1;
		Cursor c=getContentResolver().query(INSULIN_URI, null, null, null, null);
		
		try {
			if (c.moveToFirst()) {
				while (c.getCount() != 0 && c.isAfterLast() == false) 
				{
					if (c.getInt(c.getColumnIndex("status")) == PRE_MANUAL) {
						timeStamp = c.getInt(c.getColumnIndex("deliv_time"));
					}
					c.moveToNext();
				}
			}		
			c.close();
		}
        catch (Exception e) {
        		Log.e("Error retrieveTimestampOfMostRecentInsulinDelivery", e.getMessage());
        }
			
		return timeStamp;
	}
	
	private void storeInjectedInsulin(double insulin_injected) {
		//  Write MDI values to the database in the INSULIN table
		debug_message(TAG, "storeMDIInsulin");
		
		/*
		valuesdelivered.put(TIME, getCurrentTimeSeconds());
		valuesdelivered.put(INSULINRATE1, 0.0);
		valuesdelivered.put(INSULINBOLUS1, 0.0);				// Store the total bolus delivered here
		valuesdelivered.put(INSULIN_BASAL_BOLUS,  0.0);
		valuesdelivered.put(INSULIN_CORR_BOLUS,  0.0);		    	
		valuesdelivered.put(INSULIN_MEAL_BOLUS,  0.0);
		*/
		
		ContentValues values = new ContentValues();
	    
		values.put("req_time", getCurrentTimeSeconds());		//Zero out all requested and delivered fields for this type of call
	    values.put("req_total", 0.0);
	    
		values.put("req_basal", 0.0);
		values.put("req_meal", 0.0);
		values.put("req_corr", insulin_injected);
		
	    values.put("deliv_time", getCurrentTimeSeconds());
	    values.put("deliv_total", insulin_injected);
	    
		values.put("deliv_basal", 0.0);
		values.put("deliv_meal", 0.0);
		values.put("deliv_corr", insulin_injected);
		
		values.put("identifier", getCurrentTimeSeconds());
		values.put("status", PRE_MANUAL);
		
		try {
			getContentResolver().insert(INSULIN_URI, values);
		}
		catch (Exception e) {
			Log.e("Error",(e.getMessage() == null) ? "null" : e.getMessage());
		}
	}

	public void log_action(String service, String action) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
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
}