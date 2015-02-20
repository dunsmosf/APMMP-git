//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.BRMservice;

import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Controllers;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.Tvector.Tvector;

public class IOMain extends Service {
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	private static final String TAG = "BRMservice";
    
	private InsulinTherapy it;
	private Subject subject;
    private BroadcastReceiver BRMparamReceiver;

    private Messenger mMessengerToClient = null;
    private final Messenger mMessengerFromClient = new Messenger(new IncomingBRMHandler());
	public static BrmDB db;
	
	//Task to calculate TDI every hour based on insulin history
	private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> calculate_TDI;
	private Runnable calc_TDI = new Runnable()
	{
		final String FUNC_TAG = "calc_TDI";
		
		public void run() 
		{
			long timeDiff = getCurrentTimeSeconds() - retrieveTimestampOfFirstInsulinDelivery();
			Debug.i(TAG, FUNC_TAG, "Time Difference: "+timeDiff);
			
			if (timeDiff > (86100)) {
				Debug.i(TAG, FUNC_TAG, "Start Time:  " + CalculateTDIfromInsulinDelivery(retrieveTimestampOfLastInsulinDelivery() - (1+24*60*60)));
				
				SaveTDIctobrmDB(CalculateTDIfromInsulinDelivery(retrieveTimestampOfLastInsulinDelivery()-(1+24*60*60)));
				TDIestCalculateandUpdate(144);
			}
		}
	};
	
    /* When binding to the service, we return an interface to our messenger for sending messages to the service. */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessengerFromClient.getBinder();
    }
    
	@Override
	public void onCreate() {
        // Set up a Notification for this Service
        String ns = Context.NOTIFICATION_SERVICE;
        getSystemService(ns);
        int icon = R.drawable.icon;
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
        
        // Make this a Foreground Service
        startForeground(APC_ID, notification);
        
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();  
		
		it = null;
		subject = new Subject();
		
		// Register to receive params button broadcast messages
        BRMparamReceiver = new BroadcastReceiver() 
     	{
     		final String FUNC_TAG = "BRMparamReceiver";
     		
            @Override
            public void onReceive(Context context, Intent intent) {        			
    			String action = intent.getAction();
    			
    			if(action.equals("edu.virginia.dtc.DiAsUI.parametersAction"))
    			{
    				//TODO: This was removed for camp studies where it isn't needed
    			    Intent i = new Intent(context, BRM_param_activity.class);
    			    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    			    startActivity(i);
    			}
            }	
        };
        registerReceiver(BRMparamReceiver, new IntentFilter("edu.virginia.dtc.DiAsUI.parametersAction"));
        
		db = new BrmDB(this.getApplicationContext());

	    // First record is the default setting for Insulin Therapy
	    db.addtoBrmDB(getCurrentTimeSeconds(), getCurrentTimeSeconds(), 0, 3, 2, 0);	// need to fix later
	    
	    // TDI calculation scheduling
	 	calculate_TDI = scheduler.scheduleAtFixedRate(calc_TDI, 1, 60, TimeUnit.MINUTES);
    }

	@Override
	public void onDestroy() {
		unregisterReceiver(BRMparamReceiver);
	}
	
    class IncomingBRMHandler extends Handler {
    	final String FUNC_TAG = "IncomingBRMHandler";
    	
    	Bundle paramBundle, responseBundle;
    	Message response;
    	
    	@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
				case Controllers.APC_SERVICE_CMD_START_SERVICE:	
					Debug.i(TAG, FUNC_TAG, "APC_SERVICE_CMD_START_SERVICE");
					
					// Create and initialize the Subject object
					subject = new Subject();
					subject.read(getApplicationContext());
					
					// Log the parameters for IO testing
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DiAsService >> (BRMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_SERVICE_CMD_START_SERVICE"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					break;
				case Controllers.APC_SERVICE_CMD_CALCULATE_STATE:
					Debug.i(TAG, FUNC_TAG, "APC_SERVICE_CMD_CALCULATE_STATE");
					paramBundle = msg.getData();
					boolean asynchronous = paramBundle.getBoolean("asynchronous");
					double DIAS_STATE = paramBundle.getInt("DIAS_STATE", 0);

					// Log the parameters for IO testing
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DiAsService >> (BRMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"APC_SERVICE_CMD_CALCULATE_STATE"+", "+
                						"asynchronous="+asynchronous+", "+
                						"DIAS_STATE="+DIAS_STATE
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
					response = Message.obtain(null, Controllers.APC_PROCESSING_STATE_NORMAL, 0, 0);
					responseBundle = new Bundle();
					responseBundle.putBoolean("asynchronous", asynchronous);
					responseBundle.putBoolean("doesBolus", false);
					responseBundle.putBoolean("doesRate", true);
					responseBundle.putDouble("recommended_bolus", 0.0);		//recommended_bolus);
					responseBundle.putBoolean("new_differential_rate", true);
					responseBundle.putDouble("IOB", 0.0);
					
					double differential_basal_rate = 0.0;
					
					// Closed Loop
					// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
					if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP) {
						if (!asynchronous) {
							
							
							//Pull (300*24+2*60) of CGM Data and get State estimate for 27 minutes????
							
							if (it == null)
								it = new InsulinTherapy(getApplicationContext());
							
							differential_basal_rate = it.insulin_therapy_calculation();
						}
					}
					
					// All other modes...
					// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
					else 
						differential_basal_rate = 0.0;
					
					responseBundle.putDouble("differential_basal_rate", differential_basal_rate);
						
        			// Log the parameters for IO testing
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "(BRMservice) >> DiAsService, IO_TEST"+", "+FUNC_TAG+", "+
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
					try {
						mMessengerToClient.send(response);
					} 
					catch (RemoteException e) {
						e.printStackTrace();
					}
					break;
				default:
            		Bundle b2 = new Bundle();
            		b2.putString(	"description", "(BRMservice) > IO_TEST"+", "+FUNC_TAG+", "+
            						"UNKNOWN_COMMAND="+msg.what
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b2), Event.SET_LOG);
					super.handleMessage(msg);
            }
        }
    }
	
	private long getCurrentTimeSeconds() {
		return (System.currentTimeMillis()/1000);			// Seconds since 1/1/1970		
	}

	private long retrieveTimestampOfFirstInsulinDelivery() {
		String FUNC_TAG = "retrieveTimestampOfFirstInsulinDelivery";
		
		long timeStamp = -1;
		Cursor c=getContentResolver().query(Biometrics.INSULIN_URI, new String[]{"deliv_time"}, null, null, "deliv_time ASC LIMIT 1");
	
		if(c != null && c.moveToFirst()){
			timeStamp = c.getInt(c.getColumnIndex("deliv_time"));
		}		
		c.close();
        
		Debug.i(TAG, FUNC_TAG, "Time of first delivery: "+timeStamp);
	
		return timeStamp;
	}
	
	//TODO: optimize this query
	private long retrieveTimestampOfLastInsulinDelivery() {
		final String FUNC_TAG = "retrieveTimestampOfLastInsulinDelivery";
		
		long timeStamp = -1;
		Cursor c=getContentResolver().query(Biometrics.INSULIN_URI, null, null, null, null);
		
		try {
			if (c.moveToLast()) {
				timeStamp = c.getInt(c.getColumnIndex("deliv_time"));
			}		
			c.close();
		}
        catch (Exception e) {
        	Debug.e(TAG, FUNC_TAG, "Error: "+e.getMessage());
        }
			
		return timeStamp;
	}
	
	private double CalculateTDIfromInsulinDelivery(long start_time) {
		final String FUNC_TAG = "CalculateTDIfromInsulinDelivery";
		
		double tdi=0;
		Cursor c=getContentResolver().query(Biometrics.INSULIN_URI, null, "deliv_time > "+start_time, null, null);
		
		try {
			if (c.moveToFirst()) {
				if (c.getCount()>240) {			//If we have a disconnection of 4 hours or more, we don't take it into account
					while (c.moveToNext()) 
					{
						tdi =(double)Math.round((tdi+ c.getDouble(c.getColumnIndex("deliv_total"))) * 100) / 100;
					}
				}
			}
			c.close();
		}		
        catch (Exception e) {
        	Debug.e(TAG, FUNC_TAG, "Error: "+e.getMessage());
        }
		
		return tdi;
	}
	
	private void SaveTDIctobrmDB(double TDI) {
		db = new BrmDB(getApplicationContext());
		db.addTDItoBrmDB(subject.sessionID, getCurrentTimeSeconds(), TDI,0);
	}
	
	// Method to calculate the TDIest based on a window of "h" hours
	private void TDIestCalculateandUpdate (int h){
		final String FUNC_TAG = "TDIestCalculateandUpdate";
		
		Tvector TDIc = new Tvector(144);
		db = new BrmDB(getApplicationContext());
		
		Settings st;
		st=db.getLastTDIestBrmDB(subject.sessionID);
		
		TDIc=db.getTDIcHistory(subject.sessionID, st.time-h*60*60);
		
		// Get all the 0 values (missing TDIs due to missed bolus injections for more than 4 hours)
		
		//double [] xintp=new double[144];
		//double [] t=new double[144];
		//double [] v=new double [144];
		
		Debug.i(TAG,FUNC_TAG,"TDIc Count >>>>"+TDIc.count());
		
		// Code to replace missing TDIc (due to missing boluses) by the subject TDI
		for (int i=0;i<TDIc.count();i++){
			Debug.i(TAG,FUNC_TAG,"TDIc value >>>>"+TDIc.get_value(i)+"TDIc time >>>>"+TDIc.get_time(i));
			if (TDIc.get_value(i)==0){
				db.UpdateTDIc(subject.sessionID, TDIc.get_time(i), subject.TDI);
			}
			
		}
		
		double temp_TDIest=subject.TDI*0.96+TDIc.get_value(0)*0.04;
		Debug.i(TAG,FUNC_TAG,"Initial TDIest >>>>"+temp_TDIest);
		
		for (int k=1;k<TDIc.count();k++)
			temp_TDIest=temp_TDIest*0.96+TDIc.get_value(k)*0.04;
		
		Debug.i(TAG,FUNC_TAG,"TDIest >>>>"+temp_TDIest);
		
		db.UpdateTDIest(subject.sessionID, TDIc.get_last_time(), temp_TDIest);
	}
}