//*******************************************************************
//*  PumpService.java
// *  Copyright 2011-2012 by the Center for Diabetes Technology
// *  University of Virginia
// * 
// *  Created by Patrick Keith-Hynes, Najib Ben Brahim, 
// *  and Benton Mize
// ******************************************************************

package edu.virginia.dtc.PumpService;

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
import android.os.DeadObjectException;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.view.Gravity;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import edu.virginia.dtc.DiAsService.R;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.FSM;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.SysMan.Tvector;

public class PumpService extends Service {
	
    // Debugging
    private static final String TAG = "PumpService";
    private static final String VERSION_NUMBER = "1.10";
    public static final String IO_TEST_TAG = "PumpServiceIO";
	private static final boolean QUERY_LOG = true;
	
	public static final int DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK = 25;
	
	// DiAs State Variable and Definitions - state for the system as a whole
	public int DIAS_STATE, PREV_DIAS_STATE;
	public int PUMP_STATE = Pump.NONE;
	
    public static final String INSULIN_BASAL_BOLUS = "basal_bolus";
    public static final String INSULIN_MEAL_BOLUS = "meal_bolus";
    public static final String INSULIN_CORR_BOLUS = "corr_bolus";
    
    private PowerManager pm;
    private PowerManager.WakeLock wl;
    
	public StateData state_data;
	
	private double current_delivered_U = 0.0;
	
	private double bolus_max = 0;						// This is from safety service
	
	private double EPSILON = 0.000001;					// Effectively zero for doubles
	private long TIMEOUT_ADDITION = 30000;				// Additional time for timeout from pump service
	private int TBR_TIMEOUT = 45;						// Timeout for TBR setup in seconds
	
	// If the sum of all remaining unrequested boluses exceeds this threshold then the system will immediately request another bolus after receiving command confirmation
	public double BOLUS_THRESHOLD = 0.3;	
	
	// Requested but not yet delivered insulin values.  Used to account for delivered insulin 
	double undelivered_basal_insulin, undelivered_corr_insulin, undelivered_meal_insulin;
    
    private final Messenger ssmMessenger = new Messenger(new ssmMessageHandler());
    private Messenger ssmMessageTx = null;
    
    private String logFile = "";
    private boolean logReady = true;
    private boolean asynchronous = false;
    
    private int tbrTimeouts = 0;
    private int unackCount = 0;
    
    private BroadcastReceiver TickReceiver;
    
    public static ScheduledExecutorService bolusTimeoutScheduler = Executors.newSingleThreadScheduledExecutor();
	public static ScheduledFuture<?> deliveryTimer, commandTimer, tbrTimer, wakeTimer;
	
	private int consecutiveMissedBolus = 0;
	
	private SystemObserver sysObserver;
	
	PumpService me;
	
	public Runnable tbrTimeOut = new Runnable()
	{
		public void run()
		{
			final String FUNC_TAG = "tbrTimeOut";
			
			tbrTimeouts++;
			
			if(tbrTimeouts >= 6)
			{
				Debug.i(TAG, FUNC_TAG, "Number of missed TBR settings exceeds threshold, transitioning to stopped or sensor!");
	    		
	    		Bundle b = new Bundle();
	    		b.putString("description", "Number of missed TBR settings is too high, transitioning to stopped or sensor mode!");
	    		Event.addEvent(me, Event.EVENT_PUMP_MISSED_THRES, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_ALARM);
	    		
	    		final Intent intent1 = new Intent();
	    		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
	    		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK);  
	    		startService(intent1);	
			}
			
			Debug.i(TAG, FUNC_TAG, "TBR response timed out");
			
			Bundle b = new Bundle();
    		b.putString("description", "TBR Timeout!");
    		Event.addEvent(me, Event.EVENT_PUMP_TBR, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
    		
			updatePumpState(Pump.PUMP_STATE_TBR_TIMEOUT, false);
		}
	};
	
	public Runnable commandTimeOut = new Runnable()
	{
		public void run() 
		{	
			final String FUNC_TAG = "commandTimeOut";
			
			updatePumpState(Pump.PUMP_STATE_CMD_TIMEOUT, false);
			
			consecutiveMissedBolus += 1;

    		Debug.i(TAG, FUNC_TAG, "Command confirmation timed out!");
			
			log_action(TAG, "CMD TIMED OUT b="+PUMP.sent_basal_U+", c="+PUMP.sent_corr_U+", m="+PUMP.sent_meal_U, Debug.LOG_WARNING);
			
			Bundle b = new Bundle();
			b.putString("description", "Pump command confirmation timed out, for b="+PUMP.sent_basal_U+", c="+PUMP.sent_corr_U+", m="+PUMP.sent_meal_U);
			//Event.addEvent(me, Event.EVENT_PUMP_MISSED_BOLUS, Event.makeJsonString(b), Event.SET_LOG);
    		
			int eventSetting = 0;
			if (consecutiveMissedBolus == 1)
				eventSetting = Event.SET_LOG;
				b.putString("description", "Warning: Pump timed out - please check your pump to confirm insulin delivery! b="+PUMP.sent_basal_U+", c="+PUMP.sent_corr_U+", m="+PUMP.sent_meal_U);
//				b.putString("description", "Missed bolus - Pump command confirmation timed out, for b="+PUMP.sent_basal_U+", c="+PUMP.sent_corr_U+", m="+PUMP.sent_meal_U);
			if (consecutiveMissedBolus > 1) {
				double insulinsum = Math.round((PUMP.sent_basal_U + PUMP.sent_corr_U + PUMP.sent_meal_U)*10.0)/10.0;
				b.putString("description", "Warning: "+ consecutiveMissedBolus +" pump timeouts. Last timeout: "+ insulinsum +"U. Please check your pump to confirm.");
			}
			if (consecutiveMissedBolus == 2)
				eventSetting = Event.SET_POPUP_AUDIBLE_VIBE;
			if (consecutiveMissedBolus > 2)
				eventSetting = Event.SET_POPUP_AUDIBLE_ALARM;
			
			evaluateMissedBoluses();
			
			Event.addEvent(me, Event.EVENT_PUMP_MISSED_BOLUS, Event.makeJsonString(b), eventSetting);
			
			retryBolus(PUMP.sent_meal_U, PUMP.sent_corr_U, PUMP.sent_basal_U);
		}
	};
	
	class DeliveryTimeOut implements Runnable
	{
		private long id;
		
		public DeliveryTimeOut(long id)
		{
			this.id = id;
		}

		public void run() 
		{
			final String FUNC_TAG = "DeliveryTimeOut";

			updatePumpState(Pump.PUMP_STATE_DELIVER_TIMEOUT, false);
			
			consecutiveMissedBolus += 1;
			
    		Debug.w(TAG, FUNC_TAG, "Delivery confirmation timed out! (ID: "+id+")");

			log_action(TAG, "DLVR TIMED OUT b="+PUMP.sent_basal_U+", c="+PUMP.sent_corr_U+", m="+PUMP.sent_meal_U, Debug.LOG_WARNING);
    		
			Bundle b = new Bundle();
			b.putString("description", "Pump delivery confirmation timed out, for b="+PUMP.sent_basal_U+", c="+PUMP.sent_corr_U+", m="+PUMP.sent_meal_U);
			
			int eventSetting = 0;
			if (consecutiveMissedBolus == 1)
				eventSetting = Event.SET_LOG;
				b.putString("description", "Pump delivery confirmation timed out, for b="+PUMP.sent_basal_U+", c="+PUMP.sent_corr_U+", m="+PUMP.sent_meal_U);
			if (consecutiveMissedBolus > 1) {
				double insulinsum = Math.round((PUMP.sent_basal_U + PUMP.sent_corr_U + PUMP.sent_meal_U)*10.0)/10.0;
				b.putString("description", "Warning: "+ consecutiveMissedBolus +" pump timeouts! Last timeout: "+ insulinsum +"U. Please check your pump to confirm.");
			}
			if (consecutiveMissedBolus == 2)
				eventSetting = Event.SET_POPUP_AUDIBLE_VIBE;
			if (consecutiveMissedBolus > 2)
				eventSetting = Event.SET_POPUP_AUDIBLE_ALARM;
			
			Event.addEvent(me, Event.EVENT_PUMP_MISSED_BOLUS, Event.makeJsonString(b), eventSetting);
			
			evaluateMissedBoluses();
			
			retryBolus(PUMP.sent_meal_U, PUMP.sent_corr_U, PUMP.sent_basal_U);
		}
	};
    
    private PumpSystem PUMP;
    private Thread logThread;
	private boolean logStop, logRunning;
    
    @Override
    public void onCreate() {
		final String FUNC_TAG = "onCreate";

        super.onCreate();
        
        me = this;
        
        PUMP = PumpSystem.getInstance();
        PUMP.driverReceiver = new Messenger(new driverMessageHandler());
        
        DIAS_STATE = State.DIAS_STATE_STOPPED;
        PREV_DIAS_STATE = State.DIAS_STATE_STOPPED;
        
		current_delivered_U = 0.0;
		asynchronous = false;
        
        undelivered_basal_insulin = 0.0;
        undelivered_corr_insulin = 0.0;
        undelivered_meal_insulin = 0.0;
        
        PUMP.state = Pump.PUMP_STATE_IDLE;
        
        Debug.i(TAG, FUNC_TAG, "onCreate");
        log_action(TAG, "onCreate", Debug.LOG_DEBUG);
     
		state_data = new StateData();
		
        // Set up a Notification for this Service
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        int icon = R.drawable.ic_launcher;
        CharSequence tickerText = "Pump Service v"+VERSION_NUMBER;
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
        Context context = getApplicationContext();
        CharSequence contentTitle = "Pump Service v"+VERSION_NUMBER;
        CharSequence contentText = "Insulin Delivery";
        Intent notificationIntent = new Intent(this, PumpService.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        final int PUMP_ID = 1;
//        mNotificationManager.notify(PUMP_ID, notification);
        
        // Make this a Foreground Service
        startForeground(PUMP_ID, notification);
        
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wl.acquire();
        
        // Listens to changes on the SYSTEM table
        sysObserver = new SystemObserver(new Handler());
        getContentResolver().registerContentObserver(Biometrics.SYSTEM_URI, true, sysObserver);
        
        logRunning = logStop = false;
		if(Params.getBoolean(getContentResolver(), "logcatToSd", false))
		{
			Debug.i(TAG, FUNC_TAG, "Logcat to SD is enabled!");
			startLogThread();
		}
		else
			Debug.i(TAG, FUNC_TAG, "Logcat to SD is disabled!");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
		final String FUNC_TAG = "onStartCommand";

    	int command = intent.getIntExtra("PumpCommand", 0);
    	
    	switch(command)
    	{
    		case Pump.PUMP_SERVICE_CMD_NULL:
    			Debug.i(TAG, FUNC_TAG,"onStartCommand > NULL");
    			break;
    		case Pump.PUMP_SERVICE_CMD_INIT:
    			String intentName = intent.getStringExtra("driver_intent");
    			String driverName = intent.getStringExtra("driver_name");
    			bindToNewDriver(intentName, driverName);
    			break;
    		case Pump.PUMP_SERVICE_CMD_DISCONNECT:
    			Debug.i(TAG, FUNC_TAG,"Pump service command disconnect");
    			
    			Message msg = Message.obtain(null, Pump.PUMP_SERVICE2DRIVER_DISCONNECT);
    			if(PUMP.driverTransmitter != null)
    			{
					try {
						PUMP.driverTransmitter.send(msg);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
    			}
    			else
    				Debug.i(TAG, FUNC_TAG, "onStartCommand > Driver Transmitter is null, the Pump Service was most likely restarted and trying to disconnect a non-existant device");
				
				if(PUMP.driverConnection != null)
					unbindService(PUMP.driverConnection);
    			break;
    		case Pump.PUMP_SERVICE_CMD_SET_TBR:
    			Debug.i(TAG, FUNC_TAG, "Setting TBR if valid...");
    			
    			updatePumpState(Pump.PUMP_STATE_SET_TBR);
    			
    			int time = intent.getIntExtra("time", 30);
    			int target = intent.getIntExtra("target", 0);
    			boolean cancel = intent.getBooleanExtra("cancel", false);

    			Debug.i(TAG, FUNC_TAG, "Time: "+time+" Target: "+target+" Cancel Bolus: "+cancel);
    			
    			setTBR(time, target, cancel);
    			break;
    	}
		return 0;
    }

    @Override
    public void onDestroy() 
    {
		final String FUNC_TAG = "onDestroy";

        super.onDestroy();
        
        log_action(TAG, "onDestroy", Debug.LOG_DEBUG);
        Debug.i(TAG, FUNC_TAG, "onDestroy");
        
        if(TickReceiver !=null)
        	unregisterReceiver(TickReceiver);
        
        if(sysObserver != null)
			getContentResolver().unregisterContentObserver(sysObserver);
        
        logStop = true;
		if(logThread != null)
		{
			if(logThread.isAlive())
			{
				try {
					logThread.join();
					logRunning = false;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
    }
    
    @Override
	public IBinder onBind(Intent intent) {
        return ssmMessenger.getBinder();
	}
    
    /**********************************************************************************************************
	 * Pump Driver Handler Function
	*********************************************************************************************************/
    
  	class driverMessageHandler extends Handler {
      	@Override
      	public void handleMessage(Message msg)
      	{
    		final String FUNC_TAG = "handleMessage";

      		double total_delivered_U;
      		Bundle response;
      		switch(msg.what)
      		{
      			case Pump.DRIVER2PUMP_SERVICE_PARAMETERS:
      				/*
      				response = msg.getData();
      				PUMP.low_reservoir_level = response.getInt("low_reservoir_threshold_U");
      				PUMP.reservoir_size = response.getInt("reservoir_size_U");
      				PUMP.infusion_rate = response.getDouble("infusion_rate_U/sec");
      				PUMP.conversion = response.getDouble("unit_conversion");
      				PUMP.conversion_unit = response.getString("unit_name");
      				PUMP.min_bolus = response.getDouble("min_bolus_U");
      				PUMP.max_bolus = response.getDouble("max_bolus_U");
      				PUMP.min_quanta = response.getDouble("min_quanta_U");
      				*/
      				
      				Cursor c = getContentResolver().query(Biometrics.PUMP_DETAILS_URI, null, null, null, null);
      				if (c.moveToLast())
      				{
      					PUMP.low_reservoir_level = c.getDouble(c.getColumnIndex("low_reservoir_threshold_U"));
          				PUMP.reservoir_size = c.getDouble(c.getColumnIndex("reservoir_size_U"));
          				PUMP.infusion_rate = c.getDouble(c.getColumnIndex("infusion_rate_U_sec"));
          				PUMP.min_bolus = c.getDouble(c.getColumnIndex("min_bolus_U"));
          				PUMP.max_bolus = c.getDouble(c.getColumnIndex("max_bolus_U"));
          				PUMP.min_quanta = c.getDouble(c.getColumnIndex("min_quanta_U"));
          				
          				if(c.getInt(c.getColumnIndex("queryable")) > 0)
          					PUMP.queryable = true;
          				else
          					PUMP.queryable = false;
          				
          				if(c.getInt(c.getColumnIndex("temp_basal")) > 0)
          					PUMP.tempBasal = true;
          				else
          					PUMP.tempBasal = false;
          				
          				if(c.getInt(c.getColumnIndex("retries")) > 0)
          					PUMP.retries = true;
          				else
          					PUMP.retries = false;
          				
          				PUMP.tempBasalDuration = c.getInt(c.getColumnIndex("temp_basal_time"));
          				PUMP.max_retries = c.getInt(c.getColumnIndex("max_retries"));
      				}
      				c.close();
      				
      				if(commandTimer!= null)				//Cancel the bolus command timeout routine
      					commandTimer.cancel(true);
      				if(deliveryTimer != null)			//Cancel any previously running timer and start a new one on the infusion time
      					deliveryTimer.cancel(true);
      				
      				updatePumpState(Pump.PUMP_STATE_IDLE);
      				
      				Debug.i(TAG, FUNC_TAG, "Parameters MIN: "+PUMP.min_bolus+" MAX: "+PUMP.max_bolus+" QUANTA: "+PUMP.min_quanta);
      				Debug.i(TAG, FUNC_TAG, "Parameters pulled from DB quanta is "+PUMP.min_quanta);
      				break;
      			case Pump.DRIVER2PUMP_SERVICE_BOLUS_COMMAND_ACK:
      				response = msg.getData();
      				total_delivered_U = response.getDouble("totalDelivered");
      				long infusionTime = response.getLong("infusionTime") + TIMEOUT_ADDITION;
      				long id = response.getLong("identifier", 0);			
      				
      				Debug.i(TAG, FUNC_TAG, "ID to be checked: "+id);
      				updateBolusStatus(id, Pump.DELIVERING);		//Update the bolus ID field
      				
      				Debug.i(TAG, FUNC_TAG, "COMMAND ACK > Delivery timeout for Bolus ID: "+id+" set for infusion time of "+infusionTime+"ms");
      				
      				if(commandTimer!= null)				//Cancel the bolus command timeout routine
      					commandTimer.cancel(true);
      				
      				if(deliveryTimer != null)			//Cancel any previously running timer and start a new one on the infusion time
      					deliveryTimer.cancel(true);
      				
      				deliveryTimer = bolusTimeoutScheduler.schedule(new DeliveryTimeOut(id), infusionTime, TimeUnit.MILLISECONDS);
					
            		if (total_delivered_U >= current_delivered_U) {
            			current_delivered_U = total_delivered_U;
            		}
            		else {
        				Debug.i(TAG, FUNC_TAG, "COMMAND ACK > Insulin running total smaller than previous value\n"+"Insulin running total = "+total_delivered_U+"\nPrevious Value = "+current_delivered_U);
            		}
      				break;
      			case Pump.DRIVER2PUMP_SERVICE_BOLUS_DELIVERY_ACK:
            		if(deliveryTimer != null)			//Cancel the delivery timer if running
      					deliveryTimer.cancel(true);
            		
            		// Reset the consecutive missed bolus counter
            		consecutiveMissedBolus = 0;
            		
            		//Debug.i(TAG, FUNC_TAG, "DeliveryACK > Delivery timer cancelled: "+deliveryTimer.isCancelled());
            		//Debug.i(TAG, FUNC_TAG, "DeliveryACK > Command timer cancelled: "+commandTimer.isCancelled());
            		
            		response = msg.getData();
					total_delivered_U = response.getDouble("totalDelivered");
					double previous_delivered_U = current_delivered_U;
					
					if (total_delivered_U >= current_delivered_U) {
						current_delivered_U = total_delivered_U;
					}
					else {
						Debug.i(TAG, FUNC_TAG, "DELIVERY_ACK > Insulin running total smaller than previous value\n"+"Insulin running total = "+total_delivered_U+" U\nPrevious Value = "+current_delivered_U+" U");
					}
					
                	log_action(TAG, "Driver Message Handler > BOLUS_DELIVERY_ACK", Debug.LOG_DEBUG);
                	
            		double requested_amt_U = PUMP.sent_basal_U + PUMP.sent_corr_U + PUMP.sent_meal_U;
            		double requested_amt_U_max = requested_amt_U + PUMP.min_quanta;						//Maximum value for comparison
            		double requested_amt_U_min = Math.max(requested_amt_U - PUMP.min_quanta, 0);		//Minimum value for comparison (takes the maximum value so that its non-negative)
            		
            		PUMP.time_delivered = response.getLong("time");
            		PUMP.delivered_U = response.getDouble("deliveredInsulin");
            		PUMP.remaining_U = response.getDouble("remainingInsulin");
            		PUMP.device_status = response.getInt("deviceStatus");
            		PUMP.voltage = response.getDouble("batteryVoltage");
            		PUMP.low_reservoir = response.getBoolean("lowReservoir");
            		int bolusStatus = response.getInt("status");
            		
            		Debug.i(TAG, FUNC_TAG, "DELIVERY_ACK > delivered_insulin_amount="+PUMP.delivered_U+" delivered_time="+PUMP.time_delivered+" remaining_U="+PUMP.remaining_U+" battery_voltage="+PUMP.voltage);
				
            		// Pop a toast with information about the insulin confirmation
            		String toast_message;
            		
            		//TODO: check on how to handle this, the idea is to catch extraneous delivery ACKs when we aren't expecting them
//            		if(!Pump.isBusy(PUMP.state)) 
//            		{
            			if((PUMP.delivered_U >= requested_amt_U_min) && (PUMP.delivered_U <= requested_amt_U_max)) 
            			{
            				Debug.i(TAG, FUNC_TAG, "DELIVERY_ACK > Delivered="+PUMP.delivered_U);
            				toast_message = new String("Delivered "+String.format("%.2f", PUMP.delivered_U)+" U");
            			}
            			else 
            			{
            				Debug.i(TAG, FUNC_TAG, "DELIVERY_ACK > bolus mismatch, delivered="+PUMP.delivered_U+" Total Bolus Sent="+requested_amt_U+" Min: "+requested_amt_U_min+" Max: "+requested_amt_U_max);
            				toast_message = new String("Expected "+String.format("%.2f", requested_amt_U)+" U "+"Delivered "+String.format("%.2f", PUMP.delivered_U)); 
            			}
//            		}
//            		else 
//            		{
//            			Debug.i(TAG, FUNC_TAG, "DELIVERY_ACK > Error = bolus delivered="+PUMP.delivered_U+", PUMP_STATE="+PUMP.state);
//            			toast_message = new String("Delivered "+String.format("%.2f", PUMP.delivered_U)+" U "+", PUMP_STATE="+PUMP.state); 
//            		}
            		
            		Toast deliveryToast = Toast.makeText(getApplicationContext(), toast_message, Toast.LENGTH_SHORT);
            		deliveryToast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
            		deliveryToast.show();
				
            		// Store the confirmed insulin delivery in the database
            		Debug.i(TAG, FUNC_TAG, "DELIVERY_ACK > Sent_Basal_U="+PUMP.sent_basal_U+", Sent_Corr_U="+PUMP.sent_corr_U+", Sent_Meal_U="+PUMP.sent_meal_U);
            		Debug.i(TAG, FUNC_TAG, "DELIVERY_ACK > Accumulator_basal="+PUMP.acc_basal_U+", Accumulator_corr="+PUMP.acc_corr_U+", Accumulator_meal="+PUMP.acc_meal_U);
            		Debug.i(TAG, FUNC_TAG, "DELIVERY_ACK > Requested: "+requested_amt_U+" U  Requested max: "+requested_amt_U_max+" U Requested min: "+requested_amt_U_min);
            		
            		// Full insulin delivery (permit one additional quanta).  Store the confirmed insulin delivery in the database and clear out bolus storage values.
            		if (PUMP.delivered_U >= requested_amt_U_min && PUMP.delivered_U <= requested_amt_U_max) {
            			Debug.i(TAG, FUNC_TAG,"DELIVERY_ACK > Within range in bolus delivery ACK");
            			
            			storeDeliveredInsulin(PUMP.time_delivered, PUMP.delivered_U, current_delivered_U, bolusStatus);
            			PUMP.sent_basal_U = 0;
            			PUMP.sent_corr_U = 0;
            			PUMP.sent_meal_U = 0;
            		}
            		// Partial insulin delivery.  Store the confirmed insulin delivery in the database and add undelivered insulin into the accumulated insulin reservoir.
            		else if (PUMP.delivered_U < requested_amt_U_min) {
            			Debug.i(TAG, FUNC_TAG,"DELIVERY_ACK > Less than requested amount in bolus delivery ACK");
            			
            			storeDeliveredInsulin(PUMP.time_delivered, PUMP.delivered_U, current_delivered_U, bolusStatus);
            			
            			//We NEVER do this under any circumstances!
            			/*
            			PUMP.acc_basal_U += PUMP.sent_basal_U;
            			PUMP.acc_corr_U += PUMP.sent_corr_U;
            			PUMP.acc_meal_U += PUMP.sent_meal_U;
            			*/
            			
            			PUMP.sent_basal_U = 0;
            			PUMP.sent_corr_U = 0;
            			PUMP.sent_meal_U = 0;
            		}
            		// Error conditions - clear out bolus storage values and do not store delivered insulin in the database
            		else if (PUMP.delivered_U > requested_amt_U_max || PUMP.delivered_U < EPSILON) {
            			Debug.i(TAG, FUNC_TAG,"DELIVERY_ACK > Error conditions in bolus delivery ACK");
            			
    					PUMP.sent_basal_U = 0;
    					PUMP.sent_corr_U = 0;
    					PUMP.sent_meal_U = 0;
            		}
				
            		// Any insulin that was supposed to be delivered that is unconfirmed will *NOT* be re-requested
            		// Quantize the accumulated insulin in chunks of min_bolus
            		double bolusToSend;
            		
            		// If there is more accumulated insulin to send
            		if ((bolusToSend = quantizeAndSegmentAccumulatedBolus()) >= PUMP.min_bolus) {
            			
            			//TODO: Bolus sent when there is partial insulin delivery after a command to deliver insulin
            			final double bolus = bolusToSend;
            			final Bundle data = new Bundle();
            			data.putDouble("bolus", bolusToSend);
            			data.putInt("bolusId", (int)(System.currentTimeMillis()/1000));
            			
            			sendPumpBolus(PUMP.driverTransmitter, Pump.PUMP_SERVICE2DRIVER_BOLUS, data);
            			
            			storeRequestedInsulin(asynchronous, PUMP.sent_basal_U, PUMP.sent_meal_U, PUMP.sent_corr_U, current_delivered_U, data.getInt("bolusId"), 0);
            		}
 					else {		// There is no more insulin to send right now
 						
 						Debug.i(TAG, FUNC_TAG, "DELIVERY_ACK > No Accumulated Insulin");
 						
 						// This seems like the best place to check for unacknowledged since we would have confirmed any insulin at this point
 						unackCount = countUnacknowledgedInsulin();
 						if(unackCount > 0)
 							checkUnacknowledgedInsulin();
 						
 						// Wait until after we've checked unacknowledged insulin and reset the TBR before transitioning to IDLE
 						
 						//	bolusAccumulatorIDEX_basal = 0;
 						//	bolusAccumulatorIDEX_corr = 0;
 						//	bolusAccumulatorIDEX_meal = 0;
 						
 						//If the value isn't above EPSILON then store the zero delivered insulin
 						//TODO: not sure if this shouldn't be delivered storage
						//storeRequestedInsulin(asynchronous, lastBolusSimulatedTime, 0.0, 0.0, 0.0, current_delivered_U);
 						
 						updatePumpState(Pump.PUMP_STATE_COMPLETE);
 					}
            		
            		Debug.i(TAG, FUNC_TAG, "Total number of TBR timeouts since start up: "+tbrTimeouts+" *************************************");
      				break;
      			case Pump.DRIVER2PUMP_SERVICE_QUERY_RESP:
      				response = msg.getData();
      				
      				int status = response.getInt("status");
      				int queryId = response.getInt("queryId");
      				String description = response.getString("description");
      				boolean reconnect = response.getBoolean("reconnect", false);
      				long time = -1;
      				double deliv;
      				
      				if(reconnect)
      				{
      					Debug.w(TAG, FUNC_TAG, "Reconnection query response, clearing consecutive missed boluses...");
      					consecutiveMissedBolus = 0;
      				}
      				
      				Cursor q=getContentResolver().query(Biometrics.INSULIN_URI, null, null, null, null);
      	    		q.moveToFirst();
      				while (q.getCount() != 0 && q.isAfterLast() == false) {
      					
      	    			int _id = q.getInt(q.getColumnIndex("_id"));
      	    			int _bolusId = q.getInt(q.getColumnIndex("identifier"));
      	    			
      	    			if(_bolusId == queryId)		//Make sure we found the right row in the insulin table by matching the unique identifier
      	    			{
      	    				Debug.w(TAG, FUNC_TAG, "Found the query ID in row "+_id);
      	    				
	      	  				//If the insulin is delivered then the columns are filled in for the bolus
	      	    			if(status == Pump.DELIVERED || status == Pump.CANCELLED)
	          				{
	      	    				//Fix the time in the database to the delivered time if it has been cancelled or delivered
	      	    				time = response.getLong("time");
	      	    				deliv = response.getDouble("delivered_amount_U");
	      	    				
		      	  			    Debug.i(TAG, FUNC_TAG, "Bolus ID: "+_bolusId+" of "+deliv+"U with status "+Pump.statusToString(status)+" was delivered at "+time+" as confirmed by query");
				      	  		
				      	  		double basal_bolus = 0;
				      	  		double meal_bolus = 0;
				      	  		double corr_bolus = 0;
		      	  		
			      	  			basal_bolus = q.getDouble(q.getColumnIndex("req_basal"));
			      	  			meal_bolus = q.getDouble(q.getColumnIndex("req_meal"));
			      	  			corr_bolus = q.getDouble(q.getColumnIndex("req_corr"));
			      	  			
			      	  			Debug.i(TAG, FUNC_TAG, "Requested Basal: "+basal_bolus);
			      	  			Debug.i(TAG, FUNC_TAG, "Requested Corr: "+corr_bolus);
			      	  			Debug.i(TAG, FUNC_TAG, "Requested Meal: "+meal_bolus);
			      	  			
				      	  		ContentValues values = new ContentValues();
			      	  			values.put("deliv_time", time);
			      	  			values.put("deliv_total", deliv);			//Put the received amount delivered in the table
			      	  			values.put("status", status);
			      	  			
			      	  			if(status == Pump.CANCELLED)
			      	  			{
			      	  				double cncl_meal, cncl_corr, cncl_basal;
			      	  				double cncl_deliv = deliv;
			      	  				
				      	  			if (meal_bolus >= cncl_deliv) 
				      	  			{		
				      	  	 			cncl_meal = cncl_deliv;			//If the meal bolus is greater than the delivered then it all goes to meal
				      	  	 			cncl_deliv = 0;
				      	  	  		}
				      	  	  		else 
				      	  	  		{	
				      	  	  			cncl_meal = meal_bolus;			//If it isn't, then we set the we set them equal to the requested and subtract it from the total
				      	  	  		 	cncl_deliv -= meal_bolus;	
				      	  	  		}
				      	  			
				      	  			if (corr_bolus >= cncl_deliv) 
				      	  			{
				      	  	 			cncl_corr = cncl_deliv;
				      	  	 			cncl_deliv = 0;
				      	  	  		}
				      	  	  		else {
				      	  	  			cncl_corr = corr_bolus;
				      	  	  			cncl_deliv -= corr_bolus;
				      	  	  		}
				      	  			
				      	  			if (basal_bolus >= cncl_deliv) 
				      	  			{
				      	  	 			cncl_basal = cncl_deliv;
				      	  	 			cncl_deliv = 0;
				      	  	  		}
				      	  	  		else 
				      	  	  		{
				      	  	  			cncl_basal = basal_bolus;
				      	  	  			cncl_deliv -= basal_bolus;
				      	  	  		}
			      	  				
				      	  			Debug.i(TAG, FUNC_TAG, "Cancelled values > Meal: "+cncl_meal+" Corr: "+cncl_corr+" Basal: "+cncl_basal);
				      	  			
				      	  			values.put("deliv_basal", cncl_basal);
					      	  		values.put("deliv_corr", cncl_corr);
					      	  		values.put("deliv_meal", cncl_meal);
			      	  			}
			      	  			else
			      	  			{
				      	  			values.put("deliv_basal", basal_bolus);
					      	  		values.put("deliv_corr", corr_bolus);
					      	  		values.put("deliv_meal", meal_bolus);
			      	  			}
			      	  			
			      	  			values.put("recv_time", getCurrentTimeSeconds());
		      	  			    
		      	  			    try {
		      	  			    	getContentResolver().update(Biometrics.INSULIN_URI, values, "_id="+_id, null);
		      	  			    }
		      	  			    catch (Exception e) {
		      	  			    	Debug.e(TAG, FUNC_TAG, e.getMessage());
		      	  			    }
		      	  			    
		      	  			    if(QUERY_LOG)
		      	  			    {
		      	  			    	if(status == Pump.DELIVERED)
		      	  			    		Toast.makeText(getApplicationContext(), "Bolus ID: "+_bolusId+" was delivered at "+time+" as confirmed by query", Toast.LENGTH_SHORT).show();
		      	  			    	else if(status == Pump.CANCELLED)
		      	  			    		Toast.makeText(getApplicationContext(), "Bolus ID: "+_bolusId+" was cancelled at "+time+" as confirmed by query", Toast.LENGTH_SHORT).show();
		      	  			    }
		      	  			    
		      	  			    log_action(TAG, "Bolus ID: "+_bolusId+" of "+deliv+"U was delivered at "+time+" as confirmed by query", Debug.LOG_DEBUG);
	          				}
	          				else
	          				{
	          					//Report the status and the description for missing insulin from the table
	          					Debug.i(TAG, FUNC_TAG, "Bolus ID: "+_bolusId+" was not delivered after query");
	          					
	          					if(QUERY_LOG)
	          						Toast.makeText(getApplicationContext(), "Bolus ID: "+_bolusId+" has status: "+description+" after query", Toast.LENGTH_SHORT).show();
	          					
	          					log_action(TAG, "Bolus ID: "+_bolusId+" has status: "+description+" after query", Debug.LOG_DEBUG);
	          					
	          					ContentValues values = new ContentValues();
			      	  			values.put("status", status);
			      	  			
			      	  			try {
		      	  			    	getContentResolver().update(Biometrics.INSULIN_URI, values, "_id="+_id, null);
		      	  			    }
		      	  			    catch (Exception e) {
		      	  			    	Debug.e(TAG, FUNC_TAG, e.getMessage());
		      	  			    }
	          				}
      	    			}
      	    			
      	    			q.moveToNext();
      				}
      				
      				q.close();
      				
      				if(unackCount > 0)					//Use this count that is initialized in the bolus loop to prevent this from running off into the weeds if the BT is broken
      				{
      					Debug.i(TAG, FUNC_TAG, "Checking next entry in the insulin table that appears unacknowledged!");
      					checkUnacknowledgedInsulin();
      					unackCount--;
      				}
      				else
      				{
      					Debug.i(TAG, FUNC_TAG, "Unacknowledged count is zero, so it will wait until the next complete bolus delivery...");
      				}
      				break;
      			case Pump.DRIVER2PUMP_SERVICE_TBR_RESP:
      				Debug.i(TAG, FUNC_TAG, "Received response that TBR is complete!");
      				
      				//Reset TBR timeouts
      				tbrTimeouts = 0;
      				
      				if(tbrTimer != null)
      					tbrTimer.cancel(true);
      				
      				updatePumpState(Pump.PUMP_STATE_TBR_COMPLETE);
      				break;
      			case Pump.DRIVER2PUMP_SERVICE_MANUAL_INSULIN:
      				Debug.i(TAG, FUNC_TAG, "Storing manual insulin!");
      				response = msg.getData();
      				
      				double bolus = response.getDouble("bolus");
      				long bolus_time = response.getLong("time");
      				long bolus_id = response.getLong("id");
      				
      				storeManualDeliveredInsulin(bolus_time, bolus_id, bolus);
      				break;
      		}
      	}
  	}
  	
  	/**********************************************************************************************************
	 * SSM Handler Function
	*********************************************************************************************************/
  	
  	class ssmMessageHandler extends Handler {
  		Bundle paramBundle;
      	@Override
          public void handleMessage(Message msg) {
    		final String FUNC_TAG = "handleMessage";

  			paramBundle = msg.getData();
  			Debug.i(TAG, FUNC_TAG, "SSM Handler > simulatedTime from handleMessage");
  			
  			switch (msg.what) {
				case Pump.PUMP_SERVICE_CMD_NULL:					// null command
					Debug.i(TAG, FUNC_TAG,"SSM Handler > PUMP_SERVICE_CMD_NULL");
					break;
				case Pump.PUMP_SERVICE_CMD_START_SERVICE:		// start service command
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "SSMservice >> (PumpService), IO_TEST"+", "+FUNC_TAG+", "+
                						"PUMP_SERVICE_CMD_START_SERVICE");
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					// Create Param object with subject parameters received from Application
					Debug.i(TAG, FUNC_TAG, "SSM Handler > PUMP_SERVICE_CMD_START_SERVICE");
					updatePumpState(Pump.PUMP_STATE_IDLE);
					break;
				case Pump.PUMP_SERVICE_CMD_REGISTER_CLIENT:
					Debug.i(TAG, FUNC_TAG, "SSM Handler > PUMP_SERVICE_CMD_REGISTER_CLIENT");
					updatePumpState(Pump.PUMP_STATE_IDLE);
					ssmMessageTx = msg.replyTo;
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "SSMservice >> (PumpService), IO_TEST"+", "+FUNC_TAG+", "+
                						"PUMP_SERVICE_CMD_REGISTER_CLIENT");
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					Debug.i(TAG, FUNC_TAG, "SSM Handler > ssmMessageTx="+ssmMessageTx);
					break;
				case Pump.PUMP_SERVICE_CMD_DELIVER_BOLUS:
					Debug.i(TAG, FUNC_TAG, "SSM Handler > PUMP_SERVICE_CMD_DELIVER_BOLUS - PUMP_STATE="+PUMP.state);
					
					reportCommandStatusToSafetyService(Pump.PUMP_STATE_COMPLETE);
					
					asynchronous = paramBundle.getBoolean("asynchronous", false);
					//DIAS_STATE = paramBundle.getInt("DIAS_STATE", DIAS_STATE_STOPPED);
					double pre_authorized = paramBundle.getDouble("pre_authorized", 0.0);
					bolus_max = paramBundle.getDouble("bolus_max", -1.0);

					// Disable the ability to test bolus failure in standalone mode
					double basal_bolus = paramBundle.getDouble(INSULIN_BASAL_BOLUS, 0.0);
					double meal_bolus = paramBundle.getDouble(INSULIN_MEAL_BOLUS, 0.0);
					meal_bolus = meal_bolus + pre_authorized;
					double corr_bolus = paramBundle.getDouble(INSULIN_CORR_BOLUS, 0.0);
					log_action(TAG, "PUMP_SERVICE_CMD_DELIVER_BOLUS > b="+basal_bolus+", c="+corr_bolus+", m="+meal_bolus, Debug.LOG_DEBUG);
					
					//Error check values for all insulin delivery
					if(Double.isNaN(basal_bolus) || Double.isInfinite(basal_bolus))
					{
						log_action(TAG, "Value exception in basal insulin: "+basal_bolus, Debug.LOG_WARNING);
						basal_bolus = 0.0;
					}
					
					if(Double.isNaN(meal_bolus) || Double.isInfinite(meal_bolus))
					{
						log_action(TAG, "Value exception in meal insulin: "+meal_bolus, Debug.LOG_WARNING);
						meal_bolus = 0.0;
					}
					
					if(Double.isNaN(corr_bolus) || Double.isInfinite(corr_bolus))
					{
						log_action(TAG, "Value exception in correction insulin: "+corr_bolus, Debug.LOG_WARNING);
						corr_bolus = 0.0;
					}
					
					// Perform safety checks on the output of the Safety System (input to Pump Service) based upon the system mode, basal profile and time of day
					// Get the offset in minutes into the current day in the current time zone (based on cell phone time zone setting)
					TimeZone tz = TimeZone.getDefault();
					int UTC_offset_secs = tz.getOffset(getCurrentTimeSeconds()*1000)/1000;
					int timeNowMins = (int)((getCurrentTimeSeconds()+UTC_offset_secs)/60)%1440;
		
					// Get currently active basal value
					Tvector tvectorBasal = getTvector(paramBundle, "Basaltimes", "Basalvalues");
					double basal = 0.0;
					List<Integer> indices = new ArrayList<Integer>();
					indices = tvectorBasal.find(">", -1, "<=", timeNowMins);					// Find the list of indices <= time in minutes since today at 00:00
					if (indices == null) {
						indices =tvectorBasal.find(">", -1, "<", -1);							// Use final value from the previous day's profile
					}
					else if (indices.size() == 0) {
						indices = tvectorBasal.find(">", -1, "<", -1);							// Use final value from the previous day's profile
					}
					if (indices == null) {
						basal = 0.0;
					}
					else {
						basal = tvectorBasal.get_value(indices.get(indices.size()-1));			// Return the last basal in this range						
					}
					Debug.i(TAG, FUNC_TAG, "SSM Handler > DIAS_STATE="+DIAS_STATE+", basal="+basal);
					
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "SSMservice >> (PumpService), IO_TEST"+", "+FUNC_TAG+", "+
                						"PUMP_SERVICE_CMD_DELIVER_BOLUS"+", "+
                						"asynchronous="+asynchronous+", "+
                						"pre_authorized="+pre_authorized+", "+
                						"bolus_max="+bolus_max+", "+
                						"INSULIN_BASAL_BOLUS="+basal_bolus+", "+
                						"INSULIN_MEAL_BOLUS="+meal_bolus+", "+
                						"INSULIN_CORR_BOLUS="+corr_bolus+", "+
                						"tvectorBasal="+"("+tvectorBasal.get_last_time()+","+tvectorBasal.get_last_value()+")"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
					switch (DIAS_STATE) {
						case State.DIAS_STATE_STOPPED:
							if (basal_bolus > EPSILON || corr_bolus > EPSILON || meal_bolus > EPSILON || pre_authorized > EPSILON) {
								log_action(TAG, "Nonzero bolus in DIAS_STATE_STOPPED", Debug.LOG_WARNING);
								log_action(TAG, "basal="+basal_bolus+", corr="+corr_bolus+", meal="+meal_bolus, Debug.LOG_WARNING);
								basal_bolus = 0.0;
								corr_bolus = 0.0;
								meal_bolus = 0.0;
								pre_authorized = 0.0;
							}
							break;
						case State.DIAS_STATE_OPEN_LOOP:
						case State.DIAS_STATE_CLOSED_LOOP:
						case State.DIAS_STATE_SAFETY_ONLY:
							//Limits on total bolus allowed parameterized from Pump
							if((basal_bolus + corr_bolus + meal_bolus) > (2 * PUMP.max_bolus))
							{
								log_action(TAG, "Total bolus exceeds maximum of "+PUMP.max_bolus+"U, resetting values to zero!", Debug.LOG_WARNING);
								
								Debug.w(TAG, FUNC_TAG, "MEAL: "+meal_bolus);
			      	  			Debug.w(TAG, FUNC_TAG, "CORR: "+corr_bolus);
			      	  			Debug.w(TAG, FUNC_TAG, "BASAL: "+basal_bolus);
			      	  			Debug.w(TAG, FUNC_TAG, "------------------------");
								
								double total = 2 * PUMP.max_bolus;
								
								if (meal_bolus >= total) 
			      	  			{		
			      	  	 			meal_bolus = total;			//If the meal bolus is greater than the delivered then it all goes to meal
			      	  	 			corr_bolus = 0.0;
			      	  	 			basal_bolus = 0.0;
			      	  	  		}
			      	  	  		else 
			      	  	  		{	
			      	  	  			total -= meal_bolus;
			      	  	  			
			      	  	  			if(corr_bolus >= total)
			      	  	  			{
			      	  	  				corr_bolus = total;
			      	  	  				basal_bolus = 0.0;
			      	  	  			}
			      	  	  			else
			      	  	  			{
			      	  	  				total -= corr_bolus;
			      	  	  				
			      	  	  				if(basal_bolus >= total)
			      	  	  				{
			      	  	  					basal_bolus = total;
			      	  	  				}
			      	  	  			}
			      	  	  		}
			      	  			
			      	  			Debug.w(TAG, FUNC_TAG, "MEAL: "+meal_bolus);
			      	  			Debug.w(TAG, FUNC_TAG, "CORR: "+corr_bolus);
			      	  			Debug.w(TAG, FUNC_TAG, "BASAL: "+basal_bolus);
							}
							
							/*
							if (basal_bolus > basal) {
								log_action(TAG, "Basal bolus too large in DIAS_STATE="+DIAS_STATE, Debug.LOG_ACTION_WARNING);
								log_action(TAG, "basal_bolus request="+basal_bolus+", basal profile="+basal, Debug.LOG_ACTION_WARNING);
								basal_bolus = basal;
							}
							*/
							break;
							/*							
						case State.DIAS_STATE_SAFETY_ONLY:
							if (basal_bolus > basal) {
								log_action(TAG, "Basal bolus too large in DIAS_STATE="+DIAS_STATE, Debug.LOG_ACTION_WARNING);
								log_action(TAG, "basal_bolus request="+basal_bolus+", basal profile="+basal, Debug.LOG_ACTION_WARNING);
								basal_bolus = basal;
							}
							if (corr_bolus > EPSILON) {
								log_action(TAG, "Nonzero corr_bolus in DIAS_STATE_SAFETY_ONLY", Debug.LOG_ACTION_WARNING);
								log_action(TAG, "corr_bolus="+corr_bolus, Debug.LOG_ACTION_WARNING);
								corr_bolus = 0.0;
							}
							if (meal_bolus > EPSILON) {
								log_action(TAG, "Nonzero meal_bolus in DIAS_STATE_SAFETY_ONLY", Debug.LOG_ACTION_WARNING);
								log_action(TAG, "meal_bolus="+meal_bolus, Debug.LOG_ACTION_WARNING);
								meal_bolus = 0.0;
							}
							if (pre_authorized > EPSILON) {
								log_action(TAG, "Nonzero pre_authorized in DIAS_STATE_SAFETY_ONLY", Debug.LOG_ACTION_WARNING);
								log_action(TAG, "pre_authorized="+pre_authorized, Debug.LOG_ACTION_WARNING);
								pre_authorized = 0.0;
							}
							break;
							*/							
						default:
							break;
					}
					
					// Log the inputs to the Pump Service
					log_action(TAG, "basal="+basal_bolus+", corr="+corr_bolus, Debug.LOG_DEBUG);
					log_action(TAG, "meal="+meal_bolus+", async="+asynchronous, Debug.LOG_DEBUG);
					log_action(TAG, "bmax="+bolus_max+", pre_auth="+pre_authorized, Debug.LOG_DEBUG);
					
					Debug.i(TAG, FUNC_TAG, "CMD_DELIVER_BOLUS > START OF BOLUS COMMAND --------------------------------------------------------------");
					
					deliverBolus(asynchronous, pre_authorized, bolus_max, basal_bolus, meal_bolus, corr_bolus);
					break;
				case Pump.PUMP_SERVICE_CMD_SET_BASAL_RATE:
					if (PUMP.state == Pump.PUMP_STATE_IDLE || PUMP.state == Pump.PUMP_STATE_COMPLETE) 
					{
						state_data.basal_rate = paramBundle.getDouble("basal_rate", -1.0);
						Debug.i(TAG, FUNC_TAG, "PUMP_SERVICE_CMD_SET_RATE > basal_rate="+state_data.basal_rate);		//The basal rate that should be set in the pump is stored in state_data.basal_rate
					}
					else 
					{
						Debug.i(TAG, FUNC_TAG, "PUMP_SERVICE_CMD_SET_RATE > Error: Not ready for command, PUMP_STATE="+PUMP.state);
					}
					break;
				case Pump.PUMP_SERVICE_CMD_REQUEST_PUMP_STATUS:
					Debug.i(TAG, FUNC_TAG, "Pump Handler > PUMP_SERVICE_CMD_REQUEST_PUMP_STATUS");
					break;
				case Pump.PUMP_SERVICE_CMD_REQUEST_PUMP_HISTORY:
					Debug.i(TAG, FUNC_TAG, "Pump Handler > PUMP_SERVICE_CMD_REQUEST_PUMP_HISTORY");
					break;
				case Pump.PUMP_SERVICE_CMD_STOP_SERVICE:
					Debug.i(TAG, FUNC_TAG, "Pump Handler > PUMP_SERVICE_CMD_STOP_SERVICE");
					stopSelf();
					break;
				case Pump.PUMP_SERVICE_CMD_SET_HYPO_TIME:
					// Get the hypoFlagTime passed down from DiAsService
					paramBundle = msg.getData();
					long hypoFlagTime = paramBundle.getLong("hypoFlagTime", 0);
					Debug.i(TAG, FUNC_TAG, "Pump Handler > PUMP_SERVICE_CMD_SET_HYPO_TIME="+hypoFlagTime);
					
					Message hypoMsg = Message.obtain(null,Pump.PUMP_SERVICE2DRIVER_FLAGS);
					Bundle data = new Bundle();
					data.putLong("hypo_flag",hypoFlagTime);
					hypoMsg.setData(data);
					try {
						PUMP.driverTransmitter.send(hypoMsg);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
					break;
				default:
					super.handleMessage(msg);
  			}
      	}
  	}
    
    private void bindToNewDriver(String intentName, String driverName)
    {	
		final String FUNC_TAG = "bindToNewDriver";

    	// Setup device connection for this driver
    	PUMP.driverConnection = new ServiceConnection(){
    		public void onServiceConnected(ComponentName className, IBinder service) {
    			Debug.i(TAG, FUNC_TAG,"onServiceConnected...");
    			
    			PUMP.driverTransmitter = new Messenger(service);
    			
				Message msg = Message.obtain(null, Pump.PUMP_SERVICE2DRIVER_REGISTER);
				
				msg.replyTo = PUMP.driverReceiver;
				
				// Register reply source for driver
				try {
					PUMP.driverTransmitter.send(msg);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
    		}
    		
    		public void onServiceDisconnected(ComponentName className) {
    			Debug.i(TAG, FUNC_TAG,"onServiceDisconnected...");
    		}
    	};
    	
    	Intent intent = new Intent(intentName);
    	bindService(intent, PUMP.driverConnection, Context.BIND_AUTO_CREATE);
    }
    
    private void startCommandTimer()
    {
		final String FUNC_TAG = "startCommandTimer";

    	Debug.i(TAG, FUNC_TAG,"Command Timer > Starting command watchdog timer");
    	
    	if(commandTimer!= null)				//Cancel the bolus command timeout routine if running
			commandTimer.cancel(true);
    	
		commandTimer = bolusTimeoutScheduler.schedule(commandTimeOut, TIMEOUT_ADDITION, TimeUnit.MILLISECONDS);	//Schedule command timeout for 30 seconds
    }
    
    private void updatePumpState(int state)
    {
    	updatePumpState(state, true);
    }
    
    private void updatePumpState(int state, boolean delay) {
		final String FUNC_TAG = "updatePumpState";

		PUMP.state = state;
		Debug.i(TAG, FUNC_TAG, "PUMP_STATE="+Pump.serviceStateToString(PUMP.state)+" ("+PUMP.state+")");
		
		final ContentValues cv = new ContentValues();
		cv.put("service_state", PUMP.state);
		
		if(delay)
		{
			new Handler().postDelayed(new Runnable()
			{
				public void run()
				{
					getContentResolver().update(Biometrics.PUMP_DETAILS_URI, cv, null, null);
				}
			}, 3000);
		}
		else
			getContentResolver().update(Biometrics.PUMP_DETAILS_URI, cv, null, null);
    }
    
    public boolean setTBR(int time, int target, boolean cancel)
    {
    	final String FUNC_TAG = "setTBR";
    	
    	//Check if the driver supports TBR, the call is synchronous, and TBR is enable in the parameter file
    	if(PUMP.tempBasal && Params.getBoolean(getContentResolver(), "tbr_enabled", true))
    	{
    		//If the temp basal is active and so is the connection scheduling then we stop the pump in the 
    		//TBR timeout or when the TBR returns in the message handler
    		
    		Debug.i(TAG, FUNC_TAG, "Setting temporary basal rate of zero!");
    		
    		int tbr_time = 30, tbr_targ = 0;
    		
    		if(time != -1 && target != -1)
    		{
    			Debug.i(TAG, FUNC_TAG, "Input parameters are valid, using "+target+"% and "+time+" minutes");
    			tbr_time = time;
    			tbr_targ = target;
    		}
    		else
    		{
    			Debug.i(TAG, FUNC_TAG, "Checking DB for TBR parameters...");
	    		Cursor c = getContentResolver().query(Biometrics.PUMP_DETAILS_URI, new String[]{"set_temp_basal_time", "set_temp_basal_target"}, null, null, null);
	    		if(c != null)
	    		{
	    			if(c.moveToLast())
	    			{
	    				tbr_time = c.getInt(c.getColumnIndex("set_temp_basal_time"));
	    				tbr_targ = c.getInt(c.getColumnIndex("set_temp_basal_target"));
	    				Debug.i(TAG, FUNC_TAG, "TBR from DB is "+tbr_targ+"% for "+tbr_time+" minutes!");
	    			}
	    		}
	    		c.close();
	    		
	    		if(tbr_time <= 0)
	    		{
		    		Debug.i(TAG, FUNC_TAG, "There is no entry in the DB, using 0% and 30 minutes");
	    			tbr_time = 30;		//If there is no entry the default is 30 minutes
	    			tbr_targ = 0;		//Default is 0%
	    		}
    		}
    		
    		//Send command to the driver
    		final Bundle tbrData = new Bundle();
    		tbrData.putInt("time", tbr_time);
    		tbrData.putInt("target", tbr_targ);
    		tbrData.putBoolean("cancel", cancel);
    		
    		Handler h = new Handler();				//Post delayed so system can settle immediately after bolus
    		h.postDelayed(new Runnable()
    		{
				public void run() 
				{
					sendPumpMessage(PUMP.driverTransmitter, Pump.PUMP_SERVICE2DRIVER_TBR, tbrData);
		    		
		    		Debug.i(TAG, FUNC_TAG, "Setting TBR timeout for "+TBR_TIMEOUT+" seconds!");
		    		tbrTimer = bolusTimeoutScheduler.schedule(tbrTimeOut, TBR_TIMEOUT, TimeUnit.SECONDS);
				}
			}, 5000);
    		
    		return true;
    	}
    	else
    	{
    		if(!PUMP.tempBasal)
    			Debug.i(TAG, FUNC_TAG, "The pump doesn't support TBR!");
    		if(asynchronous)
    			Debug.i(TAG, FUNC_TAG, "The call was asynchronous so no TBR call!");
    		if(!pumpReady())
    			Debug.i(TAG, FUNC_TAG, "The pump isn't ready, state = "+PUMP_STATE);
    		
    		updatePumpState(Pump.PUMP_STATE_TBR_DISABLED);
    		
    		return false;
    	}
    }
    
    public void retryBolus(double meal, double correction, double basal)
    {
    	final String FUNC_TAG = "retryBolus";
    	
    	int retryCount = -1;
    	
    	Debug.i(TAG, FUNC_TAG, "Retrying bolus ID: "+PUMP.retryId+"!  Meal: "+meal+" Corr: "+correction+" Basal: "+basal);
    	
//    	double notify_thresh = Params.getDouble(this.getContentResolver(), "bolus_notify_threshold_units", 1.0);
//    	
//    	Debug.i(TAG, FUNC_TAG, "Notify threshold is "+notify_thresh+" U");
    	
//    	if((meal+correction+basal) > notify_thresh)
//    	{
//    		Bundle b = new Bundle();
//    		b.putString("description", "Bolus missed!");
//    		b.putString("amount", String.valueOf(meal+correction+basal));	
//    		Event.addEvent(me, Event.EVENT_PUMP_MISSED_BOLUS, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
//    	}
    	
    	if(PUMP.retries)		//Check if the pump supports retrying
    	{
    		//Check the insulin table in reverse for the Retry ID
//    		Cursor c=getContentResolver().query(INSULIN_URI, null, null, null, null);
//    		c.moveToLast();
//			while (c.getCount() != 0 && c.isBeforeFirst() == false) 
//			{
//    			int id = c.getInt(c.getColumnIndex("identifier"));
//    			Debug.i(TAG, FUNC_TAG, "ID: "+id+" found in table!");
//    			if(id == PUMP.retryId)
//    			{  
//    				retryCount = c.getInt(c.getColumnIndex("num_retries"));
//    			}
//    			c.moveToPrevious();
//			}
//			c.close();
//    		
//			Debug.i(TAG, FUNC_TAG, "Retry Count: "+retryCount+" Max Retries: "+PUMP.max_retries);
//			
//			if(retryCount != -1 && retryCount < PUMP.max_retries)
//			{
//	    		//Run bolus command again
//	    		double bolus_total_U = meal + correction + basal;
//	    		retryCount++;
//	    		
//	    		Debug.i(TAG, FUNC_TAG, "Bolus for "+bolus_total_U+" U resent, retry number "+retryCount+"!");
//	    		
//	    		Bundle data = new Bundle();
//				data.putDouble("bolus", bolus_total_U*PUMP.conversion);
//				data.putInt("bolusId", PUMP.retryId);										//Use retry ID here so we don't rewrite things
//	    		
//	    		if(bolus_total_U < 1)				//If the bolus is less than 1U then we can automatically retry
//	    		{
//					sendPumpBolus(PUMP.driverTransmitter, PUMP_SERVICE2DRIVER_BOLUS, data);
//					
//					updatePumpState(Pump.PUMP_STATE_COMMAND_AWAITING_DEVICE_RESPONSE);
//					
//					updateRequestedInsulin(data.getInt("bolusId"), retryCount);
//	    		}
//	    		else								//Otherwise the bolus is greater or equal to 1U and we need to inform the user 
//	    		{
//	    			Debug.i(TAG, FUNC_TAG, "Launching retry activity...");
//	    			
//	    			Intent retryActivity = new Intent();
//	    	 		retryActivity.setClass(me, RetryActivity.class);
//	    	 		retryActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//	    	 		retryActivity.putExtra("bolus", bolus_total_U);				//Don't convert for the pop-up version
//	    	 		retryActivity.putExtra("bolusId", PUMP.retryId);
//	    	 		me.startActivity(retryActivity);
//	    			
//	    	 		Debug.i(TAG, FUNC_TAG, "Updating retry count!");
//	    	 		
//	    			updateRequestedInsulin(data.getInt("bolusId"), retryCount);
//	    		}
//			}
//			else
//			{
//				PUMP.sent_basal_U = 0;		// If a bolus command times out then clear all current bolus memory
//				PUMP.sent_corr_U = 0;
//				PUMP.sent_meal_U = 0;
//				
//	    		// The only reason these values should be non-zero is if the bolus has multiple segments and if a segment fails all additional segments are cleared
//	    		// It is possible for basal boluses to arrive and be accumulated while a bolus is being sent so do not clear the basal accumulator
//				PUMP.acc_corr_U = 0;
//				PUMP.acc_meal_U = 0;
//				
//				//Be sure to return the Pump state to idle
//				updatePumpState(Pump.PUMP_STATE_COMMAND_IDLE);
//				
//				Debug.i(TAG, FUNC_TAG, "Retry ID not found or over the maximum retries...resetting bolus memory!");
//			}
    	}
    	else
    	{
    		Debug.i(TAG, FUNC_TAG, "Retries not enabled!");
    		
    		PUMP.sent_basal_U = 0;		// If a bolus command times out then clear all current bolus memory
			PUMP.sent_corr_U = 0;
			PUMP.sent_meal_U = 0;
			
    		// The only reason these values should be non-zero is if the bolus has multiple segments and if a segment fails all additional segments are cleared
    		// It is possible for basal boluses to arrive and be accumulated while a bolus is being sent so do not clear the basal accumulator
			PUMP.acc_corr_U = 0;
			PUMP.acc_meal_U = 0;
			
			//Be sure to return the Pump state to idle
			//updatePumpState(Pump.PUMP_STATE_COMMAND_IDLE);
    	}
    	
    	//reportCommandStatusToSafetyService(Pump.PUMP_STATE_COMMAND_COMPLETE);
    	
    	Debug.i(TAG, FUNC_TAG, "Retry function end...");
    }
    
    private void evaluateMissedBoluses()
    {
    	final String FUNC_TAG = "evaluateMissedBoluses";
    	
    	int threshold = Params.getInt(getContentResolver(), "bolus_missed_threshold", 5);
    	
    	Debug.i(TAG, FUNC_TAG, "Missed bolus threshold before transition: "+threshold);
    	
    	if(consecutiveMissedBolus >= threshold)
    	{
    		Debug.i(TAG, FUNC_TAG, "Number of missed boluses exceeds threshold, transitioning to stopped or sensor!");
    		
    		Bundle b = new Bundle();
    		b.putString("description", "Number of missed boluses is too high, transitioning to stopped or sensor mode!");
    		Event.addEvent(me, Event.EVENT_PUMP_MISSED_THRES, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_ALARM);
    		
    		final Intent intent1 = new Intent();
    		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
    		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK);  
    		startService(intent1);	
    		
    		consecutiveMissedBolus = 0;
    	}
    }
    
    public int countUnacknowledgedInsulin()
    {
		final String FUNC_TAG = "countUnacknowledgedInsuiln";

    	int num = 0;
    	
    	if(PUMP.queryable)
    	{
    		Debug.i(TAG, FUNC_TAG, "Counting unacknowleged insulin rows in the table");
    		
    		Cursor c=getContentResolver().query(Biometrics.INSULIN_URI, new String[]{"identifier"}, "status = "+Pump.DELIVERING+" OR status = "+Pump.PENDING, null, null);
    		if(c != null)
    		{
    			if(c.getCount() > 0)
    			{
    				num = c.getCount();
    				
    				c.moveToFirst();
    				do
    				{
    					Debug.w(TAG, FUNC_TAG, "Bolus ID "+c.getInt(c.getColumnIndex("identifier"))+" is in contention!");
    				}
    				while(c.moveToNext());
    			}
    			c.close();
    		}
    		
	    	if(QUERY_LOG && num > 0)
				Toast.makeText(getApplicationContext(), "Found "+num+" unacknowledged entries!", Toast.LENGTH_SHORT).show();
    	}
    	
    	return num;
    }
    
    public void checkUnacknowledgedInsulin()
    {
		final String FUNC_TAG = "checkUnacknowledgedInsulin";

    	if(PUMP.queryable)
    	{
    		Debug.i(TAG, FUNC_TAG, "Checking for unacknowleged insulin in the table");
    		
    		Cursor c=getContentResolver().query(Biometrics.INSULIN_URI, new String[]{"_id, identifier", "status"}, "status = "+Pump.DELIVERING+" OR status = "+Pump.PENDING, null, null);
    		if(c.moveToFirst())
    		{
    			int status = c.getInt(c.getColumnIndex("status"));
	    		int id = c.getInt(c.getColumnIndex("identifier"));
    			int row = c.getInt(c.getColumnIndex("_id"));
    			
				Debug.w(TAG, FUNC_TAG, "Row: "+row+" Bolus ID: "+id+" was not acknowledged (Status:  "+Pump.statusToString(status)+")!");
				
				if(QUERY_LOG)
					Toast.makeText(getApplicationContext(), "Bolus ID: "+id+" was not acknowledged (Status: "+Pump.statusToString(status)+")!", Toast.LENGTH_SHORT).show();
				
				Bundle data = new Bundle();
				data.putInt("bolusId", id);
				sendPumpMessage(PUMP.driverTransmitter, Pump.PUMP_SERVICE2DRIVER_QUERY, data);
				
				c.close();
				
				return;
    		}
    	}
    }

    public void deliverBolus(boolean asynchronous, double pre_authorized, double bolus_max, double basal_bolus, double meal_bolus, double corr_bolus) 
	{
		final String FUNC_TAG = "deliverBolus";

		Debug.i(TAG, FUNC_TAG, "Current state before delivery: "+Pump.serviceStateToString(PUMP.state));
		
		if(PUMP.state == Pump.PUMP_STATE_COMPLETE || PUMP.state == Pump.PUMP_STATE_CMD_TIMEOUT || PUMP.state == Pump.PUMP_STATE_DELIVER_TIMEOUT 
				|| PUMP.state == Pump.PUMP_STATE_TBR_COMPLETE || PUMP.state == Pump.PUMP_STATE_TBR_DISABLED || PUMP.state == Pump.PUMP_STATE_TBR_TIMEOUT)
			PUMP.state = Pump.PUMP_STATE_IDLE;
		
		Debug.i(TAG, FUNC_TAG, "Parameters MIN: "+PUMP.min_bolus+" MAX: "+PUMP.max_bolus+" QUANTA: "+PUMP.min_quanta);
		Debug.i(TAG, FUNC_TAG, "basal_bolus="+basal_bolus+", meal_bolus="+meal_bolus+", corr_bolus="+corr_bolus);
		
		Debug.i(TAG, FUNC_TAG, "Before rounding - Corr: "+corr_bolus+"U    Meal: "+meal_bolus+"U");
		
		if(corr_bolus >= PUMP.min_bolus)
			corr_bolus = Math.round(corr_bolus / PUMP.min_quanta) * PUMP.min_quanta;			//Not sure this is the best way to round this stuff
		
		if(meal_bolus >= PUMP.min_bolus)
			meal_bolus = Math.round(meal_bolus / PUMP.min_quanta) * PUMP.min_quanta;			//We don't do remainders or anything with correction and meal insulin
		
		Debug.i(TAG, FUNC_TAG, "After rounding - Corr: "+corr_bolus+"U    Meal: "+meal_bolus+"U");
		
		//If we are delivering then we need to not change the state (like ACC or COMPLETE) until the delivering completes or times-out
		boolean isDelivering = (PUMP.state == Pump.PUMP_STATE_DELIVER) ? true : false;
		
		if(isDelivering)
			Debug.e(TAG, FUNC_TAG, "PUMP IS DELIVERING, NOT PROCESSING OR CHANGING STATES UNTIL COMPLETE...");
		
		if (basal_bolus+corr_bolus+meal_bolus > EPSILON)		//Check that the total is non-zero 
		{	
			if (PUMP.state == Pump.PUMP_STATE_IDLE) 
			{
		        PUMP.acc_basal_U = PUMP.acc_basal_U + basal_bolus;			//Put everything into accumulated insulin
		        PUMP.acc_corr_U = PUMP.acc_corr_U + corr_bolus;
		        PUMP.acc_meal_U = PUMP.acc_meal_U + meal_bolus;
		        
				double bolus_total_U = 0.0;
				
				if ((bolus_total_U = quantizeAndSegmentAccumulatedBolus()) >= PUMP.min_bolus) 
				{
					Debug.w(TAG, FUNC_TAG, "BOLUS IS BEING PROCESSED...");
					
					Debug.i(TAG, FUNC_TAG, "Sent_basal="+PUMP.sent_basal_U+", Sent_corr="+PUMP.sent_corr_U+", Sent_meal="+PUMP.sent_meal_U);
					Debug.i(TAG, FUNC_TAG, "Acc_basal="+PUMP.acc_basal_U+", Acc_corr="+PUMP.acc_corr_U+", Acc_meal="+PUMP.acc_meal_U);
					
					log_action(TAG, "Sent_basal="+PUMP.sent_basal_U+", Sent_corr="+PUMP.sent_corr_U+", Sent_meal="+PUMP.sent_meal_U, Debug.LOG_DEBUG);
					log_action(TAG, "Acc_basal="+PUMP.acc_basal_U+", Acc_corr="+PUMP.acc_corr_U+", Acc_meal="+PUMP.acc_meal_U, Debug.LOG_DEBUG);

					
					//This call is initiated by a command to the Pump Service from the SSM
					Bundle data = new Bundle();
					data.putDouble("bolus", bolus_total_U);
					data.putInt("bolusId", (int)(System.currentTimeMillis()/1000));
					PUMP.retryId = data.getInt("bolusId");											//Hang on to ID for retries
					
					sendPumpBolus(PUMP.driverTransmitter, Pump.PUMP_SERVICE2DRIVER_BOLUS, data);
					
					//Completion status is from timeouts or actually delivery acknowledgment
					updatePumpState(Pump.PUMP_STATE_DELIVER);
					
					Debug.i(TAG, FUNC_TAG, "Bolus sent: "+bolus_total_U+" U");
					
					// Store the insulin request in the "insulin" table.
					storeRequestedInsulin(asynchronous, PUMP.sent_basal_U, PUMP.sent_meal_U, PUMP.sent_corr_U, current_delivered_U, data.getInt("bolusId"), 0);
				}
				else 
				{
					Debug.w(TAG, FUNC_TAG, "BOLUS IS LESS THAN MIN BOLUS...ACCUMULATING");
					
					Debug.i(TAG, FUNC_TAG, "Accumulating: "+bolus_total_U+" U");
					
					if(!isDelivering)
						updatePumpState(Pump.PUMP_STATE_ACC);
					
					Toast toast4 = Toast.makeText(getApplicationContext(), "Accumulating "+bolus_total_U+" U", Toast.LENGTH_SHORT);
					toast4.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
					toast4.show();
					
					//If the value isn't above the minimum bolus then store the zero delivered insulin
					storeZeroDeliveredInsulin();
					
					unackCount = countUnacknowledgedInsulin();
					if(unackCount > 0)
						checkUnacknowledgedInsulin();
					
					if(!isDelivering)
						updatePumpState(Pump.PUMP_STATE_COMPLETE);
				}
			}
			else		//The pump state isn't ready for some reason
			{
				Debug.w(TAG, FUNC_TAG, "PUMP STATE IS NOT IDLE (CURRENT: "+Pump.serviceStateToString(PUMP.state)+")...ACCUMULATING");
				
				log_action(TAG, "deliverBolus > Pump state is not IDLE (it is "+Pump.serviceStateToString(PUMP.state)+")", Debug.LOG_WARNING);
				
				if(!isDelivering)
					updatePumpState(Pump.PUMP_STATE_ACC);
				
				PUMP.acc_basal_U = PUMP.acc_basal_U + basal_bolus;
				PUMP.acc_corr_U = PUMP.acc_corr_U + corr_bolus;
				PUMP.acc_meal_U = PUMP.acc_meal_U + meal_bolus;
				
				double acc_bolus_U = basal_bolus + corr_bolus + meal_bolus;
				
				Toast toast5 = Toast.makeText(getApplicationContext(), "Accumulating "+acc_bolus_U+" U", Toast.LENGTH_SHORT);
				toast5.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
				toast5.show();
				
				Debug.i(TAG, FUNC_TAG, "Accumulated Insulin = Acc_basal:"+PUMP.acc_basal_U+" Acc_corr:"+PUMP.acc_corr_U+" Acc_meal:"+PUMP.acc_meal_U);
				
				if(!isDelivering)
					updatePumpState(Pump.PUMP_STATE_COMPLETE);
			}
		}
		else 
		{
			Debug.w(TAG, FUNC_TAG, "BOLUS IS LESS THAN ZERO (EPSILON 0.000001)...ACCUMULATING");
			
			if(!isDelivering)
				updatePumpState(Pump.PUMP_STATE_ACC);
			
			storeZeroDeliveredInsulin();
			
			unackCount = countUnacknowledgedInsulin();
			if(unackCount > 0)
				checkUnacknowledgedInsulin();
			
			if(!isDelivering)
				updatePumpState(Pump.PUMP_STATE_COMPLETE);
		}
	}
    
    private double quantizeAndSegmentAccumulatedBolus() 
    {    	
		final String FUNC_TAG = "quantizeAndSegmentAccumulatedBolus";

    	double bolus_send_U, bolus_rem_U, bolus_send_temp_U;
    	
    	if(PUMP.acc_basal_U < EPSILON)		//Check for zeros or negatives
    		PUMP.acc_basal_U = 0.0;
    	if(PUMP.acc_corr_U < EPSILON)
    		PUMP.acc_corr_U = 0.0;
    	if(PUMP.acc_meal_U < EPSILON)
    		PUMP.acc_meal_U = 0.0;
    	
    	bolus_send_U = PUMP.acc_basal_U + PUMP.acc_corr_U + PUMP.acc_meal_U;		//Sum all accumulated insulin
    	
    	Debug.i(TAG, FUNC_TAG, "Values BEFORE quantization processing...");
    	Debug.i(TAG, FUNC_TAG, "Sent = Basal: "+PUMP.sent_basal_U+" Correct: "+PUMP.sent_corr_U+" Meal: "+PUMP.sent_meal_U);
    	Debug.i(TAG, FUNC_TAG, "Accumulated = Basal: "+PUMP.acc_basal_U+" Correct: "+PUMP.acc_corr_U+" Meal: "+PUMP.acc_meal_U);
  		Debug.i(TAG, FUNC_TAG, "bolus_send_U: "+bolus_send_U+" U");
  		
  		// ---------------------------------------------------------------------------------------------------------------------------
  		// Quantize the bolus into chunks of size min_bolus and do some test rounding
  		long bolus_send_rnd = (long)((PUMP.acc_basal_U + PUMP.acc_corr_U + PUMP.acc_meal_U)/PUMP.min_quanta);
  		
  		Debug.i(TAG, FUNC_TAG, "Rounded int of bolus_send_U: "+bolus_send_rnd);
  		
  		bolus_send_U = bolus_send_rnd * PUMP.min_quanta;
  		bolus_rem_U = (PUMP.acc_basal_U + PUMP.acc_corr_U + PUMP.acc_meal_U) - bolus_send_U;								//Remainder is the sum minus the rounded portion
  		
  		Debug.i(TAG, FUNC_TAG, "bolus_send_U: "+bolus_send_U+"U bolus_rem_U: "+bolus_rem_U+"U");
  		
  		double test_rem = bolus_rem_U + (PUMP.min_quanta * (0.1));
  		Debug.i(TAG, FUNC_TAG, "bolusRem + minBolus: "+test_rem+" 1/10th of minimum quanta: "+(PUMP.min_quanta * (0.1)));	
  		
  		if((test_rem >= PUMP.min_quanta) && (bolus_rem_U > EPSILON))		//If the remainder is within 1/10 of the minimum bolus and greater than zero
  		{
  			bolus_send_U += PUMP.min_quanta;								//We add on another min_bolus sized bolus
  			bolus_rem_U -= PUMP.min_quanta;
  			PUMP.acc_basal_U = bolus_send_U - PUMP.acc_corr_U - PUMP.acc_meal_U;		//We must also adjust the stored accumulated value, since the corr and meal should already have been rounded
  																						//it should belong to basal insulin
  			Debug.i(TAG, FUNC_TAG, "Adding to bolus_send_U: "+bolus_send_U);		
  		}
  		else
  		{
  			Debug.i(TAG, FUNC_TAG, "test_rem is NOT greater than minimum bolus quanta");		//Otherwise we just hang on to the remainder
  		}
  		// ---------------------------------------------------------------------------------------------------------------------------
  		
  		// First apply a hard limit of PUMP.max_bolus which comes from the pump parameters
  		if (bolus_send_U > PUMP.max_bolus) {
  			Debug.i(TAG, FUNC_TAG, "Bolus is over the maximum!");
  			
  			bolus_rem_U = bolus_send_U - PUMP.max_bolus;
  			bolus_send_U = PUMP.max_bolus;					//bolus_send_U becomes the maximum bolus value
	  	  	bolus_send_temp_U = bolus_send_U;				//bolus_send_temp_U is also maximum value
	  	  	
  			segmentBolus(bolus_send_temp_U);				//Segment bolus into proper fields (meal, correction, basal)
  		}
  		// Next ensure that the bolus is above the minimum size
  		else if(bolus_send_U >= PUMP.min_bolus) {
  			Debug.i(TAG, FUNC_TAG, "Bolus is under the maximum size and over the minimum size");
  			
  	  		if(bolus_rem_U < EPSILON)
  	  			bolus_rem_U = 0.0;
  	  		
  	  		Debug.i(TAG, FUNC_TAG, "bolus_send: "+bolus_send_U+" bolus_rem: "+bolus_rem_U);
  	  		
  	  		if (bolus_rem_U < EPSILON) {
  	  			Debug.i(TAG, FUNC_TAG, "Bolus remainder is less than EPSILON (or zero), setting all accumulated to sent...");
  	  			
  	  			// If there is no remainder than the assumption is that all the insulin accumulated is rounded and quantized correctly
  	  			// thus it can all be sent
  	  			PUMP.sent_basal_U = PUMP.acc_basal_U;
  	  			PUMP.sent_corr_U = PUMP.acc_corr_U;
  	  			PUMP.sent_meal_U = PUMP.acc_meal_U;
  	  			
  	  			PUMP.acc_basal_U = 0;
  	  			PUMP.acc_corr_U = 0;
  	  			PUMP.acc_meal_U = 0;
  	  		}
  	  		else {
  	  			Debug.i(TAG, FUNC_TAG, "Not a multiple of the minimum bolus size, storing remainder...");
  	  			// Not a multiple of minimum bolus size so we need to store the remainder
  	  	  		bolus_send_temp_U = bolus_send_U;
  	  	  		
  	  	  		segmentBolus(bolus_send_temp_U);
  	  		}
  		}
	  	// Finally, catch the case where the value is less than the minimum, nothing is to be done 
  		else
  		{
  			Debug.i(TAG, FUNC_TAG, "Bolus to send is less than the minimum bolus size, we just hang on to it...");
  			
  			// The total is less than the minimum that can be sent so nothing is changed, accumulated insulin is untouched
  			PUMP.sent_basal_U = 0;
  			PUMP.sent_corr_U = 0;
  			PUMP.sent_meal_U = 0;
  		}
  		
  		Debug.i(TAG, FUNC_TAG, "Values AFTER quantization processing...");
  		Debug.i(TAG, FUNC_TAG, "Sent = Basal: "+PUMP.sent_basal_U+" Correct: "+PUMP.sent_corr_U+" Meal: "+PUMP.sent_meal_U);
    	Debug.i(TAG, FUNC_TAG, "Accumulated = Basal: "+PUMP.acc_basal_U+" Correct: "+PUMP.acc_corr_U+" Meal: "+PUMP.acc_meal_U);
  		
  		Debug.i(TAG, FUNC_TAG, "Total bolus to be sent: "+bolus_send_U);
  		
  		return bolus_send_U;
    }

    public void segmentBolus(double bolus_U)
    {
		// First update meal insulin
		if (PUMP.acc_meal_U >= bolus_U) {		
 			PUMP.sent_meal_U = bolus_U;					//If the accumulated meal is higher than the bolus size
 			PUMP.acc_meal_U -= PUMP.sent_meal_U;		//all insulin is put to meal
  			bolus_U = 0;
  		}
  		else {	
  		 	PUMP.sent_meal_U = PUMP.acc_meal_U;			//Otherwise, the accumulated meal insulin is put to the meal
 			PUMP.acc_meal_U = 0;						//insulin to be sent and is subtracted from the temporary total
  		 	bolus_U -= PUMP.sent_meal_U;				//to continue sorting
  		}
		
		// Next update the correction insulin
		if (PUMP.acc_corr_U >= bolus_U) {
 			PUMP.sent_corr_U = bolus_U;
 			PUMP.acc_corr_U -= PUMP.sent_corr_U;
 			bolus_U = 0;
  		}
  		else {
  			PUMP.sent_corr_U = PUMP.acc_corr_U;
  			PUMP.acc_corr_U = 0;
  			bolus_U -= PUMP.sent_corr_U;
  		}
		
  	  	// Finally update the basal insulin quantities
		if (PUMP.acc_basal_U >= bolus_U) {
 			PUMP.sent_basal_U = bolus_U;
 			PUMP.acc_basal_U -= PUMP.sent_basal_U;
 			bolus_U = 0;
  		}
  		else {
  		 	PUMP.sent_basal_U = PUMP.acc_basal_U;
 			PUMP.acc_basal_U = 0;
 			bolus_U -= PUMP.sent_basal_U;
  		}
    }
    
	public long getCurrentTimeSeconds() 
	{
		final String FUNC_TAG = "getCurrentTimeSeconds";

		return System.currentTimeMillis()/1000;			// Seconds since 1/1/1970		
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
		final String FUNC_TAG = "log_action";

		Debug.i(TAG, FUNC_TAG, "LOG ACTION > "+action);
		
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("priority", priority);
        i.putExtra("time", getCurrentTimeSeconds());
        sendBroadcast(i);
	}
	
	private void reportCommandStatusToSafetyService(int status) {
		final String FUNC_TAG = "reportCommandStatusToSafetyService";

		Message response = Message.obtain(null, status, 0, 0);
		// Report command delivered to the SafetyService
		try {
			ssmMessageTx.send(response);
			String status_string;
			switch (status) {
			case Pump.PUMP_STATE_COMPLETE:
				status_string = new String("PUMP_STATE_COMMAND_COMPLETE");
				if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
            		Bundle b = new Bundle();
            		b.putString(	"description", "(PumpService) >> SSMservice, IO_TEST"+", "+FUNC_TAG+", "+
            						"PUMP_STATE_COMMAND_COMPLETE"
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
				}
				break;
			case Pump.PUMP_STATE_PUMP_ERROR:
				status_string = new String("PUMP_STATE_PUMP_ERROR");
				if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
            		Bundle b = new Bundle();
            		b.putString(	"description", "(PumpService) >> SSMservice, IO_TEST"+", "+FUNC_TAG+", "+
            						"PUMP_STATE_PUMP_ERROR"
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
				}
				break;
			case Pump.PUMP_STATE_COMMAND_ERROR:
				status_string = new String("PUMP_STATE_COMMAND_ERROR");
				if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
            		Bundle b = new Bundle();
            		b.putString(	"description", "(PumpService) >> SSMservice, IO_TEST"+", "+FUNC_TAG+", "+
            						"PUMP_STATE_COMMAND_ERROR"
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
				}
				break;
			case Pump.PUMP_STATE_NO_RESPONSE:
				status_string = new String("PUMP_STATE_NO_RESPONSE");
				if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
            		Bundle b = new Bundle();
            		b.putString(	"description", "(PumpService) >> SSMservice, IO_TEST"+", "+FUNC_TAG+", "+
            						"PUMP_STATE_NO_RESPONSE"
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
				}
				break;
			default:
				status_string = new String(" "+status);
				break;
			}
			Debug.i(TAG, FUNC_TAG,"reportToSSM > Send "+status_string+" to SafetyService");
		} 
		catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	public void sendPumpMessage(Messenger messenger, int what, Bundle data)
	{
		if(messenger!=null)
		{
			Message cmd = Message.obtain(null, what);
			cmd.setData(data);
			
			try
			{
				messenger.send(cmd);
			}
			catch (RemoteException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public void sendPumpBolus(Messenger messenger, int what, Bundle data)
	{	
		final String FUNC_TAG = "sendPumpBolus";
		
		if(messenger!=null)
		{
			double bolus = data.getDouble("bolus");
			//Toast.makeText(getApplicationContext(), "Sending "+String.format("%.2f",bolus)+" U", Toast.LENGTH_SHORT).show();
		
			Debug.i(TAG, FUNC_TAG, "Sending "+String.format("%.2f",bolus)+" U");
			
			Message cmd = Message.obtain(null, what);
			cmd.setData(data);
			
			try
			{
				messenger.send(cmd);
			}
			catch (RemoteException e)
			{
				e.printStackTrace();
			}
			
			startCommandTimer();
		}
		else
			Debug.i(TAG, FUNC_TAG, "Messenger NULL!");
	}
	
//	private void wakeDriver()
//	{
//		final String FUNC_TAG = "wakeDriver";
//		
//		Debug.e(TAG, FUNC_TAG, "There is an error with the pump...");
//		updatePumpState(Pump.PUMP_STATE_PUMP_ERROR);
//		
//		//Fire event!
//		Bundle b = new Bundle();
//		b.putString("description", "Pump Error!");
//		Event.addEvent(this, Event.EVENT_PUMP_STOPPED, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
//		
//		//Start driver!
//		Debug.e(TAG, FUNC_TAG, "Restarting pump...");
//		
//		Cursor c = getContentResolver().query(Biometrics.HARDWARE_CONFIGURATION_URI, null, null, null, null);
//		if(c!=null)
//		{
//			if(c.moveToFirst())
//			{
//				String pump = c.getString(c.getColumnIndex("running_pump"));
//				
//				Intent driver = new Intent();
//				if(pump != null && !pump.equalsIgnoreCase(""))
//				{
//					Debug.w(TAG, FUNC_TAG, "Previously running pump was found: "+pump);
//					String p_pump = pump.substring(0, pump.lastIndexOf('.'));
//					Debug.w(TAG, FUNC_TAG, "Pump Package name: "+p_pump);
//					
//					driver.setClassName(p_pump, pump);
//					driver.putExtra("auto", true);
//					this.startService(driver);
//				}
//				else
//					Debug.i(TAG, FUNC_TAG, "No previously running Pump!");
//			}
//		}
//		else
//			Debug.i(TAG, FUNC_TAG, "Cursor is null!");
//		
//		c.close();
//	}
	
	/**********************************************************************************************************
	 * Content Provider Access Functions
	*********************************************************************************************************/
	
	//Delivered insulin accessors
	//*******************************************************************
	
	private void storeManualDeliveredInsulin(long time, long id, double bolus) {
		final String FUNC_TAG = "storeManualDeliveredInsulin";

		Cursor c = getContentResolver().query(Biometrics.INSULIN_URI, null, "identifier = '"+id+"'", null, null);
		if(c.getCount() > 0)
		{
			Debug.i(TAG, FUNC_TAG, "Attempted to enter ID: "+id+" multiple times, entry already exists...rejecting!");
			return;
		}
		c.close();
		
		Debug.i(TAG, FUNC_TAG, "Storing manual insulin...ID: "+id+" Delivered Time: "+time+" Bolus: "+bolus);
		
		Toast.makeText(getApplicationContext(), "Manual Insulin Delivery - ID: "+id+" Bolus: "+bolus+"U", Toast.LENGTH_SHORT).show();
		
		ContentValues values = new ContentValues();
	    
		values.put("req_time", time);	
	    values.put("req_total", 0.0);
	    
		values.put("req_basal", 0.0);
		values.put("req_meal", 0.0);
		values.put("req_corr", 0.0);
		
		values.put("type", Pump.TYPE_MANUAL);
		
	    values.put("deliv_time", time);
	    values.put("deliv_total", bolus);
	    
		values.put("deliv_basal", 0.0);
		values.put("deliv_meal", 0.0);
		values.put("deliv_corr", bolus);
		
		values.put("status", Pump.MANUAL);						// Since this is zero, it is more of a placeholder and cannot be queried thus it is automatically delivered, since it never gets sent
		values.put("identifier", id);
		
		values.put("recv_time", getCurrentTimeSeconds());
		
		try {
			getContentResolver().insert(Biometrics.INSULIN_URI, values);
		}
		catch (Exception e) {
			Debug.e("Error", FUNC_TAG,(e.getMessage() == null) ? "null" : e.getMessage());
		}
	}
	
	private void storeZeroDeliveredInsulin() {
		final String FUNC_TAG = "storeZeroDeliveredInsulin";

		//  Write values to the database in the INSULIN table
		Debug.i(TAG, FUNC_TAG, "storeZeroDeliveredInsulin");
		
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
		values.put("req_corr", 0.0);
		
	    values.put("deliv_time", getCurrentTimeSeconds());
	    values.put("deliv_total", 0.0);
	    
		values.put("deliv_basal", 0.0);
		values.put("deliv_meal", 0.0);
		values.put("deliv_corr", 0.0);
		
		values.put("status", Pump.DELIVERED);						// Since this is zero, it is more of a placeholder and cannot be queried thus it is automatically delivered, since it never gets sent
		
		values.put("recv_time", getCurrentTimeSeconds());
		
		try {
			getContentResolver().insert(Biometrics.INSULIN_URI, values);
		}
		catch (Exception e) {
			Debug.e("Error", FUNC_TAG,(e.getMessage() == null) ? "null" : e.getMessage());
		}
	}
	
	private void storeDeliveredInsulin(long deliveryTime, double deliveredInsulin, double currentPumpDeliveredInsulinClicks, int status) {
		final String FUNC_TAG = "storeDeliveredInsulin";

		double insulin_to_deliver = deliveredInsulin;					// Make a working copy so we can remove bits of insulin as we assign them to categories
		double basal_delivered=0, corr_delivered=0, meal_delivered=0;
		
		Debug.i(TAG, FUNC_TAG, "storeDeliveredInsulin > Sent_basal="+PUMP.sent_basal_U+", Sent_corr="+PUMP.sent_corr_U+", Sent_meal="+PUMP.sent_meal_U+", deliveredInsulin="+PUMP.delivered_U+", bolusStatus="+status);
		
		double requested_amt_U = PUMP.sent_basal_U + PUMP.sent_corr_U + PUMP.sent_meal_U;
		double requested_amt_U_max = requested_amt_U + PUMP.min_quanta;						//Maximum value for comparison
		double requested_amt_U_min = Math.max(requested_amt_U - PUMP.min_quanta, 0);		//Minimum value for comparison (takes the maximum value so that its non-negative)
		
		// Check that deliveredInsulin matches what we expect
		if (deliveredInsulin >= requested_amt_U_min && deliveredInsulin <= requested_amt_U_max) {		//Same as in bolus delivery ACK, we look at a range of valid insulin (accurate to +/- 1 quanta)
			Debug.i(TAG, FUNC_TAG, "storeDeliveredInsulin > delivered insulin matches requested");
			
			basal_delivered = PUMP.sent_basal_U;
			corr_delivered = PUMP.sent_corr_U;
			meal_delivered = PUMP.sent_meal_U;
			
			PUMP.sent_basal_U = 0;
			PUMP.sent_corr_U = 0;
			PUMP.sent_meal_U = 0;
		}
		else {
			Debug.i(TAG, FUNC_TAG, "storeDeliveredInsulin > delivered insulin does NOT match requested!");
			// Assign delivered insulin to the appropriate categories
			// Meal insulin has the highest priority
			if (insulin_to_deliver <= PUMP.sent_meal_U) {						// All remaining insulin_to_deliver goes to meal category
				meal_delivered = insulin_to_deliver;
				PUMP.sent_meal_U = PUMP.sent_meal_U - insulin_to_deliver;
				insulin_to_deliver = 0;
			}
			else if (PUMP.sent_meal_U > EPSILON) {								// A portion of insulin_to_deliver is categorized as meal.
				meal_delivered = PUMP.sent_meal_U;								// All bolusSentIDEX_meal insulin is now accounted for
				insulin_to_deliver = insulin_to_deliver - PUMP.sent_meal_U;
				PUMP.sent_meal_U = 0;
			}
			else {																// There is no undelivered correction insulin
				meal_delivered = 0.0;
			}
			
			// Correction insulin has the next highest priority
			if (insulin_to_deliver <= PUMP.sent_corr_U) {						// All remaining insulin_to_deliver goes to corr category
				corr_delivered = insulin_to_deliver;
				PUMP.sent_corr_U = PUMP.sent_corr_U - insulin_to_deliver;
				insulin_to_deliver = 0;
			}
			else if (PUMP.sent_corr_U > EPSILON) {								// A portion of insulin_to_deliver is categorized as corr.
				corr_delivered = PUMP.sent_corr_U;								// All bolusSentIDEX_corr insulin is now accounted for
				insulin_to_deliver = insulin_to_deliver - PUMP.sent_corr_U;
				PUMP.sent_corr_U = 0;
			}
			else {																// There is no undelivered corr insulin
				corr_delivered = 0.0;
			}
			
			// Basal insulin has the lowest priority
			if (insulin_to_deliver <= PUMP.sent_basal_U) {						// All of insulin_to_deliver goes to basal category
				basal_delivered = insulin_to_deliver;
				PUMP.sent_basal_U = PUMP.sent_basal_U - insulin_to_deliver;
				insulin_to_deliver = 0;
			}
			else if (PUMP.sent_basal_U > EPSILON) {								// A portion of insulin_to_deliver is categorized as basal.
				basal_delivered = PUMP.sent_basal_U;							// All bolusSentIDEX_basal insulin is now accounted for
				insulin_to_deliver = insulin_to_deliver - PUMP.sent_basal_U;
				PUMP.sent_basal_U = 0;
			}
			else {																// There is no undelivered basal insulin
				basal_delivered = 0.0;
			}
		}
		
		// Write values to the database in the INSULIN table
		Debug.i(TAG, FUNC_TAG, "storeDeliveredInsulin > undelivered insulin after processing:  basal="+undelivered_basal_insulin+", corr="+undelivered_corr_insulin+", meal="+undelivered_meal_insulin);
		
		// Fetch the most recent record in the INSULIN table
		Cursor c=getContentResolver().query(Biometrics.INSULIN_URI, null, null, null, null);
		int _id = 0;
		
		if (c.moveToLast()) 
		{
			_id = c.getInt(c.getColumnIndex("_id"));
			
			Debug.i(TAG, FUNC_TAG, "storeDeliveredInsulin > Filling delivered values in the most recent entry from insulin table - ID:"+_id);
			
			ContentValues values = new ContentValues();
			
			values.put("deliv_time", deliveryTime);				//Update the row with the delivered values and new status of the bolus
			values.put("deliv_total", deliveredInsulin);
			
			values.put("deliv_basal", basal_delivered);
			values.put("deliv_meal", meal_delivered);
			values.put("deliv_corr", corr_delivered);		    	
			
			values.put("status", status);
			
			values.put("recv_time", getCurrentTimeSeconds());
			
		    try {
				getContentResolver().update(Biometrics.INSULIN_URI, values, "_id="+_id, null);
		    }
		    catch (Exception e) {
		    	log_action(TAG, "storeDeliveredInsulin > Error writing INSULIN_URI with _id="+_id+", "+deliveryTime, Debug.LOG_WARNING);
				Debug.e(TAG, FUNC_TAG, e.getMessage());
		    }
		}
		c.close();
	}
	
	//Requested insulin accessors
	//*******************************************************************
	
	private void updateBolusStatus(long identifier, int status)
	{
		final String FUNC_TAG = "updateBolusStatus";

		// Fetch the most recent record in the "insulin" table
		Cursor c=getContentResolver().query(Biometrics.INSULIN_URI, null, null, null, null);
		int _id = 0;
		
		if (c.moveToLast()) 
		{
			_id = c.getInt(c.getColumnIndex("_id"));
			
			Debug.i(TAG, FUNC_TAG, "Row ID at the end: "+_id);
			
			//Verify the identifiers are the same since they are generated on the pump service side now (not by the Tandem driver)
			if(c.getInt(c.getColumnIndex("identifier")) == identifier)
			{
				Debug.i(TAG, FUNC_TAG, "updateRequestedInsulinId > Changing status of bolus "+identifier+" in row "+_id+" to DELIVERING");
				
				// If the most recent record exists in the "insulin" table and it does not have a valid delivery time then update it with deliveryTime
				ContentValues values = new ContentValues();
				values.put("status", status);
				
			    try 
			    {
					getContentResolver().update(Biometrics.INSULIN_URI, values, "_id="+_id, null);
			    }
			    catch (Exception e) 
			    {
			    	log_action(TAG, "updateRequestedInsulinId > Error writing INSULIN_URI with _id="+_id, Debug.LOG_WARNING);
					Debug.e(TAG, FUNC_TAG, e.getMessage());
			    }
			}
			else
				Debug.i(TAG, FUNC_TAG, "updateBolusStatus > The identifiers for the last bolus requested and the ID returned by the pump do not match!");
		}
		
		c.close();
	}
	
	private void updateRequestedInsulin(int bolusId, int retryCount)
	{
		final String FUNC_TAG = "updateRequestedInsulin";
		
		ContentValues c = new ContentValues();
		c.put("num_retries", retryCount);
		
		try 
	    {
	    	getContentResolver().update(Biometrics.INSULIN_URI, c, "identifier = '"+bolusId+"'", null);
	    }
	    catch (Exception e) 
	    {
	    	Debug.e("Error", FUNC_TAG,(e.getMessage() == null) ? "null" : e.getMessage());
	    }
	}
	
	private void storeRequestedInsulin(boolean asynchronous, double basal_req_U, double meal_req_U, double corr_req_U, double current_total_U, int bolusId, int retryCount) 
	{
		final String FUNC_TAG = "storeRequestedInsulin";

		//  Store the state_data.bolus_out that we just sent to the pump in the biometrics content provider database
		//  -> The bolus must be divided correctly among the various components
		//  -> This is requested insulin!
		
		Debug.i(TAG, FUNC_TAG, "storeRequestedInsulin > asynchronous="+asynchronous+", basal_requested="+basal_req_U+" U, meal_requested="+meal_req_U+" U, corr_requested="+corr_req_U+" U, current_total_U="+current_total_U+" U");
		
	    ContentValues values = new ContentValues();
	    
	    double total_bolus_requested_U = basal_req_U + meal_req_U + corr_req_U;
	    
	    values.put("req_time", getCurrentTimeSeconds());
	    values.put("req_total", total_bolus_requested_U);
	    
		values.put("req_basal", basal_req_U);
		values.put("req_meal", meal_req_U);
		values.put("req_corr", corr_req_U);
		
		values.put("running_total", current_total_U);
		
		values.put("identifier", bolusId);
		values.put("status", Pump.PENDING);				// Initial status is pending since we just added it to the table
		
		if(asynchronous)
			values.put("type", Pump.TYPE_ASYNC);
		else
			values.put("type", Pump.TYPE_SYNC);
		
		values.put("num_retries", retryCount);								
	    
		try 
	    {
	    	Uri uri = getContentResolver().insert(Biometrics.INSULIN_URI, values);
	    }
	    catch (Exception e) 
	    {
	    	Debug.e("Error", FUNC_TAG,(e.getMessage() == null) ? "null" : e.getMessage());
	    }
	}
	
	private boolean pumpReady()
    {
    	Debug.i(TAG, "pumpReady", "PUMP_STATE: "+PUMP_STATE);
    	
    	switch(PUMP_STATE)
	    {
	    	case Pump.CONNECTED:
	    	case Pump.CONNECTED_LOW_RESV:
    			return true;
	    	default:
	    		return false;
    	}
    }
	
	private void startLogThread()
	{
		if(logThread == null || !logThread.isAlive())
		{
			logThread = new Thread()
			{
				final String FUNC_TAG = "logThread";
				
				public void run()
				{
					File log = new File(Environment.getExternalStorageDirectory().getPath() + "/pumpServiceLogcat.txt");
					
					while(!logStop)
					{
						try 
						{
							Process process = Runtime.getRuntime().exec("logcat -v time -d");
							BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
							BufferedWriter bW = new BufferedWriter(new FileWriter(log, true));
							String line;
							
							while ((line = bufferedReader.readLine()) != null) 
							{
								if(!line.equals("--------- beginning of /dev/log/main"))
								{
									bW.write(line);
									bW.newLine();
								}
							}
							
							process = Runtime.getRuntime().exec("logcat -c");
							
							bW.flush();
							bW.close();
						} 
						catch (IOException e) 
						{
						}
						
						try {
							Thread.sleep(5000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			};
			logThread.start();
		}
	}
	
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

       @Override
       public void onChange(boolean selfChange, Uri uri) 
       {
    	   	final String FUNC_TAG = "onChange";
    	   
    	   	count++;
    	   	Debug.i(TAG, FUNC_TAG, "System Observer: "+count);
    	   
    	   	Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, new String[]{"diasState", "pumpState"}, null, null, null);
       	
	       	if(c!=null)
	       	{
	       		if(c.moveToLast())
	       		{
	       			int diasState = c.getInt(c.getColumnIndex("diasState"));
	       			PUMP_STATE = c.getInt(c.getColumnIndex("pumpState"));
	       			
	       			PREV_DIAS_STATE = DIAS_STATE;
	       			DIAS_STATE = diasState;
	       			
       			}
	       		c.close();
	       	}
       }		
    }
	
	class MealBolus
	{
		private double first_meal, first_corr, second_meal, second_corr;
		private boolean first_sent, second_sent;
		private boolean first_delivered, second_delivered;
		private long first_id, second_id;
		
		public MealBolus()
		{
			first_meal = first_corr = 0.0;
			second_meal = second_corr = 0.0;
			first_sent = second_sent = false;
			first_delivered = second_delivered = false;
			first_id = second_id = -1;
		}
	}
}
