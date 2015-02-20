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
import edu.virginia.dtc.SysMan.DiAsSubjectData;
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
	
    private Messenger mMessengerToClient = null;
    private final Messenger mMessengerFromClient = new Messenger(new IncomingHMSHandler());
    
    @Override
    public IBinder onBind(Intent intent) {
        return mMessengerFromClient.getBinder();
    }
    
	@Override
	public void onCreate() {
		Log.log_action(this, TAG, "onCreate", System.currentTimeMillis()/1000, Log.LOG_ACTION_DEBUG);
        
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
					
					double correction = 0.0, diff_rate = 0.0;
					boolean new_rate = false;
					
					// Closed Loop
					// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
					if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP) {
						// Only run this in synchronous mode (asynchronous is for meals)
						if (!asynchronous) {
							Debug.i(TAG, FUNC_TAG, "Synchronous call...");
							
							//**********************************************************
							//**********************************************************
							// RUN CALCULATION HERE
							//**********************************************************
							//**********************************************************
							
							Debug.w(TAG, FUNC_TAG, "THIS IS ALL JUST TEST CODE AND DOESN'T ACTUALLY IMPLEMENT AN APC!");
							
							//This is an example call that will read all the subject data from the DB
							//You don't have to call this here, this is just how to call it and fill the fields
							//The read function returns true if successful and false if it fails to read
							Subject subject = new Subject();
							subject.read(getApplicationContext());
							
							//For corrections use the "correction" value (in Units)
							//For rate changes set "new_rate" to true and use "diff_rate" for the value (+/- subject's basal)
						}
					}
					
					// All other modes...
					// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
					else {
						Debug.i(TAG, FUNC_TAG, "DiAs State: "+State.stateToString(DIAS_STATE));
						
						//Defaults are all zero and false
						correction = diff_rate = 0.0;
						new_rate = false;
					}
					
					// Message includes everything but recommended_bolus
					Message response = Message.obtain(null, Controllers.APC_PROCESSING_STATE_NORMAL, 0, 0);
					Bundle responseBundle = new Bundle();
					responseBundle.putBoolean("doesBolus", true);
					responseBundle.putBoolean("doesRate", false);
					responseBundle.putDouble("recommended_bolus", correction);
					responseBundle.putBoolean("new_differential_rate", new_rate);
					responseBundle.putDouble("differential_basal_rate", diff_rate);
					responseBundle.putDouble("IOB", 0.0);
					responseBundle.putBoolean("asynchronous", asynchronous);
						
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
}