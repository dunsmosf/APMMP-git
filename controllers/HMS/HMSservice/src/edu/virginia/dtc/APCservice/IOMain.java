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
import edu.virginia.dtc.SysMan.Controllers;
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
	
	// Used to calculate and store HMS data
	private HMS hms;
  
    private Messenger mMessengerToClient = null;
    private final Messenger mMessengerFromClient = new Messenger(new IncomingHMSHandler());
    
    @Override
    public IBinder onBind(Intent intent) {
        return mMessengerFromClient.getBinder();
    }
    
	@Override
	public void onCreate() {
		Log.log_action(this, TAG, "onCreate", System.currentTimeMillis()/1000, Log.LOG_ACTION_DEBUG);
        
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
    	
    	@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
				case Controllers.APC_SERVICE_CMD_START_SERVICE:	
					// Create Param object with subject parameters received from Application
					Debug.i(TAG, FUNC_TAG, "APC_SERVICE_CMD_START_SERVICE");
					mMessengerToClient = msg.replyTo;
					
					// Log the parameters for IO testing
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DIAsService >> (APC), IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_SERVICE_CMD_START_SERVICE"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					break;
				case Controllers.APC_SERVICE_CMD_CALCULATE_STATE:
					Debug.i(TAG, FUNC_TAG, "APC_SERVICE_CMD_CALCULATE_STATE");
					Bundle paramBundle = msg.getData();
					boolean asynchronous = paramBundle.getBoolean("asynchronous");
					int DIAS_STATE = paramBundle.getInt("DIAS_STATE", 0);

					// Log the parameters for IO testing
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DIAsService >> (APC), IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_SERVICE_CMD_CALCULATE_STATE"+", "+
                						"asynchronous="+asynchronous+", "+
                						"DIAS_STATE="+DIAS_STATE
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
					// Message includes everything but recommended_bolus
					Message response = Message.obtain(null, Controllers.APC_PROCESSING_STATE_NORMAL, 0, 0);
					Bundle responseBundle = new Bundle();
					responseBundle.putBoolean("doesBolus", true);
					responseBundle.putBoolean("doesRate", false);
					responseBundle.putBoolean("new_differential_rate", false);
					responseBundle.putDouble("differential_basal_rate", 0.0);
					responseBundle.putDouble("IOB", 0.0);
					responseBundle.putBoolean("asynchronous", asynchronous);
					
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
							
							responseBundle.putDouble("recommended_bolus", recommended_bolus);
						}
					}
					
					// All other modes...
					// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
					else {
						Debug.i(TAG, FUNC_TAG, "DiAs State: "+State.stateToString(DIAS_STATE));
						
						responseBundle.putDouble("recommended_bolus", 0.0);
					}
						
        			// Log the parameters for IO testing
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "(APC) >> DiAsService, IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_PROCESSING_STATE_NORMAL"+", "+
                						"doesBolus="+responseBundle.getBoolean("doesBolus")+", "+
                						"doesRate="+responseBundle.getBoolean("doesRate")+", "+
                						"recommended_bolus="+responseBundle.getDouble("recommended_bolus")+", "+
                						"new_differential_rate="+responseBundle.getBoolean("new_differential_rate")+", "+
                						"differential_basal_rate="+responseBundle.getDouble("differential_basal_rate")+", "+
                						"IOB="+responseBundle.getDouble("IOB")
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
        			}        			
					
					// Log response data to HMS State Estimate
					storeHMSTableData(getCurrentTimeSeconds(), responseBundle.getDouble("recommended_bolus"), responseBundle.getDouble("differential_basal_rate", 0.0));
					
					// Send response to DiAsService
					response.setData(responseBundle);
					sendResponse(response);
					break;
				default:
					super.handleMessage(msg);
            }
        }
    }
	
	private void sendResponse(Message m)
	{
		final String FUNC_TAG = "sendMessage";
		
		if(mMessengerToClient != null)
		{
			try {
				mMessengerToClient.send(m);
			} catch (RemoteException e) {
				Debug.e(TAG, FUNC_TAG, "Error: "+e.getMessage());
			}
		} else
			Debug.e(TAG, FUNC_TAG, "The messenger is null!");
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