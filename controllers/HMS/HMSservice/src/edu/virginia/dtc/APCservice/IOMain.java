//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Log;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.State;

import android.content.ContentValues;
import android.content.Context;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Message;
import android.os.Handler;
import android.os.RemoteException;
import android.app.Notification;
import android.app.PendingIntent;

public class IOMain extends Service {
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	private static final String TAG = "HMSservice";
    private boolean asynchronous;
	private int Timer_Ticks_Per_Control_Tick = 1;
	private int Timer_Ticks_To_Next_Meal_From_Last_Rate_Change = 1;
	
	// Used to calculate and store HMS data
	private HMS hms;
	
    // HMSservice interface definitions
	private static final int APC_SERVICE_CMD_NULL = 0;
	private static final int APC_SERVICE_CMD_START_SERVICE = 1;
	private static final int APC_SERVICE_CMD_REGISTER_CLIENT = 2;
	private static final int APC_SERVICE_CMD_CALCULATE_STATE = 3;
	private static final int APC_SERVICE_CMD_STOP_SERVICE = 4;
	private static final int APC_SERVICE_CMD_CALCULATE_BOLUS = 5;
	
    // HMSservice return values
    private static final int APC_PROCESSING_STATE_NORMAL = 10;
    private static final int APC_CONFIGURATION_PARAMETERS = 12;		// APController parameter status return
  
    private Messenger mMessengerToClient = null;
    private final Messenger mMessengerFromClient = new Messenger(new IncomingHMSHandler());
    
    @Override
    public IBinder onBind(Intent intent) {
        return mMessengerFromClient.getBinder();
    }
    
	@Override
	public void onCreate() {
		Log.log_action(this, TAG, "onCreate", System.currentTimeMillis()/1000, Log.LOG_ACTION_DEBUG);
        
        asynchronous = false;
        hms = null;
		
        // Set up a Notification for this Service
        String ns = Context.NOTIFICATION_SERVICE;
        getSystemService(ns);
        int icon = edu.virginia.dtc.APCservice.R.drawable.icon;
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
        startForeground(APC_ID, notification);
        
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();
    }

	@Override
	public void onDestroy() {
		Debug.i(TAG, "onDestroy", "");
		Log.log_action(this, TAG, "onDestroy", System.currentTimeMillis()/1000, Log.LOG_ACTION_DEBUG);
	}
	
	class IncomingHMSHandler extends Handler {
    	final String FUNC_TAG = "IncomingHMSHandler";
    	Bundle paramBundle, responseBundle;
    	Message response;
    	@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
				case APC_SERVICE_CMD_NULL:	
					Debug.i(TAG, FUNC_TAG, "APC_SERVICE_CMD_NULL");
					break;
				case APC_SERVICE_CMD_START_SERVICE:	
					// Create Param object with subject parameters received from Application
					Debug.i(TAG, FUNC_TAG, "APC_SERVICE_CMD_START_SERVICE");
					paramBundle = msg.getData();
					double TDI = (double)paramBundle.getDouble("TDI");
					int IOB_curve_duration_hours = paramBundle.getInt("IOB_curve_duration_hours");
					
					// Log the parameters for IO testing
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DIAsService >> (APC), IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_SERVICE_CMD_START_SERVICE"+", "+
                						"TDI="+TDI+", "+
                						"IOB_curve_duration_hours="+IOB_curve_duration_hours
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}

					// Inform DiAsService of the APC_TYPE and how many ticks you require per control tick
					Debug.i(TAG, FUNC_TAG, "Timer_Ticks_Per_Control_Tick="+Timer_Ticks_Per_Control_Tick);
					response = Message.obtain(null, APC_CONFIGURATION_PARAMETERS, 0, 0);
					responseBundle = new Bundle();
					Timer_Ticks_Per_Control_Tick = 1;
					responseBundle.putInt("Timer_Ticks_Per_Control_Tick", Timer_Ticks_Per_Control_Tick);
    				Timer_Ticks_To_Next_Meal_From_Last_Rate_Change = 1;
    				responseBundle.putInt("Timer_Ticks_To_Next_Meal_From_Last_Rate_Change", Timer_Ticks_To_Next_Meal_From_Last_Rate_Change);	// Ticks from meal announcement to meal start
					
					// Log the parameters for IO testing
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
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
					Debug.i(TAG, FUNC_TAG, "APC_SERVICE_CMD_CALCULATE_STATE");
					paramBundle = msg.getData();
					asynchronous = (boolean)paramBundle.getBoolean("asynchronous");
					long corrFlagTime = (long)paramBundle.getLong("corrFlagTime", 0);
					long hypoFlagTime = (long)paramBundle.getLong("hypoFlagTime", 0);
					long calFlagTime = (long)paramBundle.getLong("calFlagTime", 0);
					long mealFlagTime = (long)paramBundle.getLong("mealFlagTime", 0);
					int DIAS_STATE = paramBundle.getInt("DIAS_STATE", 0);
					double tick_modulus = paramBundle.getInt("tick_modulus", 0);
					paramBundle.getBoolean("currentlyExercising", false);

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
                						"DIAS_STATE="+DIAS_STATE+", "+
                						"tick_modulus="+tick_modulus
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
					// Closed Loop
					// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
					if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP) {
						// Only run this in synchronous mode (asynchronous is for meals)
						if (!asynchronous) {
							Debug.i(TAG, FUNC_TAG, "Synchronous call...");
							
							// Calculate a correction bolus if needed
							if (hms == null)
								hms = new HMS(getApplicationContext());
							
							double recommended_bolus = hms.HMS_calculation();
							
							Debug.i(TAG, FUNC_TAG, "Recommended Bolus: "+recommended_bolus);
							
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
						}
					}
					
					// All other modes...
					// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
					else {
						Debug.i(TAG, FUNC_TAG, "DiAs State: "+State.stateToString(DIAS_STATE));
						
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
					storeHMSTableData(getCurrentTimeSeconds(), responseBundle.getDouble("recommended_bolus"), responseBundle.getDouble("differential_basal_rate", 0.0));
					
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
					Debug.i(TAG, FUNC_TAG, "APC_SERVICE_CMD_STOP_SERVICE");
					stopSelf();
					break;
				case APC_SERVICE_CMD_REGISTER_CLIENT:
					Debug.i(TAG, FUNC_TAG, "APC_SERVICE_CMD_REGISTER_CLIENT");
					mMessengerToClient = msg.replyTo;
            		Bundle b = new Bundle();
            		b.putString(	"description", "DiAsService >> APC, IO_TEST"+", "+FUNC_TAG+", "+
            						"APC_SERVICE_CMD_REGISTER_CLIENT"
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					break;
				default:
					super.handleMessage(msg);
            }
        }
    }
	
	private long getCurrentTimeSeconds() {
		return (long)(System.currentTimeMillis()/1000);	// Seconds since 1/1/1970		
	}

	private void storeHMSTableData(long time, double correction, double differential_basal_rate) {
		ContentValues values = new ContentValues();
		values.put("time", time);
		values.put("correction_in_units", correction);
		values.put("differential_basal_rate", differential_basal_rate);
		
		try {
			getContentResolver().insert(Biometrics.HMS_STATE_ESTIMATE_URI, values);
		}
		catch (Exception e) {
			Debug.e(TAG, "storeHMSTableData", e.getMessage());
		}		
	}
}