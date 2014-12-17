//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.FSM;
import edu.virginia.dtc.SysMan.Messages;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.SysMan.Tvector;

public class IOMain extends Service {
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	// Identify owner of record in User Table 1
	public static final int MEAL_IOB_CONTROL = 10;
	
	// DiAs State Variable and Definitions - state for the system as a whole
	public int DIAS_STATE;
	public int DIAS_STATE_PREVIOUS;
	
	private static final String VERSION = "1.0.0";
	private static final boolean DEBUG_MODE = true;
	public static final String TAG = "APCservice";
    
	private boolean asynchronous;
	private int Timer_Ticks_Per_Control_Tick = 1;
	public int cycle_duration_seconds = 300;
	public int cycle_duration_mins = cycle_duration_seconds/60;
	
	// Interface definitions for the biometricsContentProvider
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
	private double Gpred_1h;
	public static final int TVEC_SIZE = 96;				// 8 hours of samples at 5 mins per sample
	// Store most recent timestamps in seconds for each biometric Tvector
	Long last_Tvec_cgm1_time_secs, last_Tvec_insulin_bolus1_time_secs, last_Tvec_requested_insulin_bolus1_time_secs;	
	
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
    
	// Commands from DiAsService 
	public static final int APC_SERVICE_CMD_NULL = 0;
	public static final int APC_SERVICE_CMD_START_SERVICE = 1;
	public static final int APC_SERVICE_CMD_REGISTER_CLIENT = 2;
	public static final int APC_SERVICE_CMD_CALCULATE_STATE = 3;
	public static final int APC_SERVICE_CMD_STOP_SERVICE = 4;
	
    // APCservice return values to DiAsService
    public static final int APC_PROCESSING_STATE_NORMAL = 10;
    public static final int APC_PROCESSING_STATE_ERROR = -11;
    public static final int APC_CONFIGURATION_PARAMETERS = 12;		// APController parameter status return

    private ServiceObserver serviceObserver;

    // Messenger for sending responses to DiAsService
    public Messenger mMessengerToClient = null;
    // DiAsService sends commands here
    final Messenger mMessengerFromClient = new Messenger(new IncomingHMSHandler());
 
    @Override
    public IBinder onBind(Intent intent) {
        return mMessengerFromClient.getBinder();
    }
    
    // Handles incoming commands from DiAsService.
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

					// Inform DiAsService of how many ticks you require per control tick
					debug_message(TAG, "Timer_Ticks_Per_Control_Tick="+Timer_Ticks_Per_Control_Tick);
					response = Message.obtain(null, APC_CONFIGURATION_PARAMETERS, 0, 0);
					responseBundle = new Bundle();
					Timer_Ticks_Per_Control_Tick = 1;
					responseBundle.putInt("Timer_Ticks_Per_Control_Tick", Timer_Ticks_Per_Control_Tick);
					
					// Log the parameters for IO testing
					if (true) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "(APC) >> DiAsService, IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_CONFIGURATION_PARAMETERS"+", "+
                						"Timer_Ticks_Per_Control_Tick="+Timer_Ticks_Per_Control_Tick
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
					double DIAS_STATE = paramBundle.getInt("DIAS_STATE", 0);
					double tick_modulus = paramBundle.getInt("tick_modulus", 0);
					boolean currentlyExercising = paramBundle.getBoolean("currentlyExercising", false);

					// Log the input parameters for IO testing
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DIAsService >> (APC), IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_SERVICE_CMD_CALCULATE_STATE"+", "+
                						"asynchronous="+asynchronous+", "+
                						"corrFlagTime="+corrFlagTime+", "+
                						"calFlagTime="+calFlagTime+", "+
                						"hypoFlagTime="+hypoFlagTime+", "+
                						"mealFlagTime="+mealFlagTime+", "+
                						"DIAS_STATE="+DIAS_STATE+", "+
                						"tick_modulus="+tick_modulus
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}

					//****************
					// Calculate Insulin here!!!!
					//****************					
					
					// Package up the results of the insulin calculation and send to DiAsService
					response = Message.obtain(null, APC_PROCESSING_STATE_NORMAL, 0, 0);
					responseBundle = new Bundle();
					responseBundle.putBoolean("doesBolus", true);
					responseBundle.putBoolean("doesRate", false);
					responseBundle.putBoolean("doesCredit", false);
					responseBundle.putDouble("recommended_bolus", 0.0);
					responseBundle.putDouble("creditRequest", 0.0);
					responseBundle.putDouble("spendRequest", 0.0);
					responseBundle.putBoolean("new_differential_rate", false);
					responseBundle.putDouble("differential_basal_rate", 0.0);
					responseBundle.putDouble("IOB", 0.0);
					responseBundle.putBoolean("extendedBolus", false);
					responseBundle.putDouble("extendedBolusMealInsulin", 0.0);
					responseBundle.putDouble("extendedBolusCorrInsulin", 0.0);
						
        			// Log the output parameters for IO testing
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
					
					// Send response to DiAsService
					response.setData(responseBundle);
					try {
						mMessengerToClient.send(response);
					} 
					catch (RemoteException e) {
						e.printStackTrace();
					}
					break;
				case APC_SERVICE_CMD_STOP_SERVICE:
					debug_message(TAG, "APC_SERVICE_CMD_STOP_SERVICE");
					stopSelf();
					break;
				case APC_SERVICE_CMD_REGISTER_CLIENT:
					debug_message(TAG, "APC_SERVICE_CMD_REGISTER_CLIENT");
					mMessengerToClient = msg.replyTo;
            		Bundle b = new Bundle();
            		b.putString(	"description", "DiAsService >> (APC), IO_TEST"+", "+FUNC_TAG+", "+
            						"APC_SERVICE_CMD_REGISTER_CLIENT"
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					debug_message(TAG, "mMessengerToClient="+mMessengerToClient);
//    				Toast.makeText(getApplicationContext(), TAG+" > APC_SERVICE_CMD_REGISTER_CLIENT", Toast.LENGTH_LONG).show();
					break;
				default:
					super.handleMessage(msg);
            		Bundle b2 = new Bundle();
            		b2.putString(	"description", "Invalid command="+msg.what+", "+FUNC_TAG
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b2), Event.SET_LOG);
					break;
            }
        }
    }
	
	@Override
	public void onCreate() {
		DIAS_STATE = State.DIAS_STATE_STOPPED;
		DIAS_STATE_PREVIOUS = State.DIAS_STATE_STOPPED;
        log_action(TAG, "onCreate");
        brakes_coeff = 1.0;
        asynchronous = false;
		Tvec_cgm1 = new Tvector(TVEC_SIZE);
		Tvec_cgm2 = new Tvector(TVEC_SIZE);
		Tvec_insulin_rate1 = new Tvector(TVEC_SIZE);
		Tvec_spent = new Tvector(TVEC_SIZE);
		Tvec_IOB = new Tvector(TVEC_SIZE);
		Tvec_GPRED = new Tvector(TVEC_SIZE);
		Gpred_1h = 0.0;
		// Initialize most recent timestamps
		last_Tvec_cgm1_time_secs = new Long(0);
		last_Tvec_insulin_bolus1_time_secs = new Long(0);
		last_Tvec_requested_insulin_bolus1_time_secs = new Long(0);
		
        // Set up a Notification for this Service
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        int icon = R.drawable.ic_launcher;
        CharSequence tickerText = "";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
        Context context = getApplicationContext();
        CharSequence contentTitle = "APCservice";
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

        serviceObserver = new ServiceObserver(new Handler());
        getContentResolver().registerContentObserver(Biometrics.SERVICE_OUTPUTS_URI, false, serviceObserver);
    }

	@Override
	public void onDestroy() {
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
			Cursor c=getContentResolver().query(Biometrics.CGM_URI, null, "time > "+Time.toString(), null, null);
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
		Gpred_1h = 0.0;
		// Fetch data from State Estimate data records
		Long Time = new Long(time);
		Cursor c=getContentResolver().query(Biometrics.STATE_ESTIMATE_URI, null, Time.toString(), null, null);
		long state_estimate_time;
		if (c.moveToFirst()) {
			do{
				if (c.getInt(c.getColumnIndex("asynchronous")) == 0) {
					state_estimate_time = c.getLong(c.getColumnIndex("time"));
					Tvec_IOB.put(state_estimate_time, c.getDouble(c.getColumnIndex("IOB")));
					Tvec_GPRED.put(state_estimate_time, c.getDouble(c.getColumnIndex("GPRED")));
					Gpred_1h = c.getDouble(c.getColumnIndex("Gpred_1h"));
					return_value = true;
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

	public void log_action(String service, String action) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("time", (long) System.currentTimeMillis()/1000);
        sendBroadcast(i);
	}
	
	private static void debug_message(String tag, String message) {
		if (DEBUG_MODE) {
			Log.i(tag, message);
		}
	}

    class ServiceObserver extends ContentObserver
    {
        final String FUNC_TAG = "ServiceObserver";

        public ServiceObserver(Handler handler)
        {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange)
        {
            this.onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri)
        {
            Cursor c = getContentResolver().query(Biometrics.SERVICE_OUTPUTS_URI, null, null, null, "_id DESC LIMIT 1");
            if(c.moveToFirst())
            {
                int type = c.getInt(c.getColumnIndex("type"));
                int machine = c.getInt(c.getColumnIndex("machine"));
                int source = c.getInt(c.getColumnIndex("source"));
                int message = c.getInt(c.getColumnIndex("message"));
                int destination = c.getInt(c.getColumnIndex("destination"));
                long cycle = c.getLong(c.getColumnIndex("cycle"));

                if(destination == Messages.APC) {
                    if (type == Messages.COMMAND) {
                        switch(message) {
                            case Messages.PING:
                                Debug.i(TAG, FUNC_TAG, "Sending PING response...");
                                sendResponse(source, message, machine, cycle);
                                break;
                            case Messages.UPDATE:
                                break;
                            case Messages.PROCESS:
                                break;
                        }
                    } else if (type == Messages.RESPONSE) {

                    }
                }
            }
            c.close();
        }

        public void sendResponse(int dest, int message, int machine, long cycle)
        {
            ContentValues c = new ContentValues();
            c.put("time", System.currentTimeMillis() / 1000);
            c.put("cycle", cycle);
            c.put("source", Messages.APC);
            c.put("destination", dest);
            c.put("machine", machine);
            c.put("type", Messages.RESPONSE);
            c.put("message", message);
            c.put("data", "");

            getContentResolver().insert(Biometrics.SERVICE_OUTPUTS_URI, c);
        }
    }
}