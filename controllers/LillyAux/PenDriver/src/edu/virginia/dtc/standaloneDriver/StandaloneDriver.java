/*
 * This device driver will be a simulator style driver that represents what was previously
 * the "Standalone" operating mode.  This driver also is a good representation of a driver
 * like the iDex where both the Pump and CGM 
 */
package edu.virginia.dtc.standaloneDriver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Pump;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class StandaloneDriver extends Service {

	private static final String TAG = "StandaloneDriver";

	// Messages to UI
	private static final int DRIVER2UI_NULL = 0;
	private static final int DRIVER2UI_DEV_STATUS = 1;
	private static final int DRIVER2UI_FINISH = 5;

	// Messages from UI
	private static final int UI2DRIVER_NULL = 0;
	private static final int UI2DRIVER_REGISTER = 1;
	
	// Commands to Pump Service
	public static final int DRIVER2PUMP_SERVICE_PARAMETERS = 0;
	public static final int DRIVER2PUMP_SERVICE_STATUS_UPDATE = 1;
	public static final int DRIVER2PUMP_SERVICE_RESERVOIR = 2;
	public static final int DRIVER2PUMP_SERVICE_BOLUS_COMMAND_ACK = 3;
	public static final int DRIVER2PUMP_SERVICE_BOLUS_DELIVERY_ACK = 4;

	// Commands for Pump Driver
	private static final int PUMP_SERVICE2DRIVER_NULL = 0;
	private static final int PUMP_SERVICE2DRIVER_REGISTER = 1;
	private static final int PUMP_SERVICE2DRIVER_DISCONNECT = 2;
	private static final int PUMP_SERVICE2DRIVER_FLAGS = 3;
	private static final int PUMP_SERVICE2DRIVER_BOLUS = 4;
 	private static final int PUMP_SERVICE_CMD_DISCONNECT = 10;
 	private static final int PUMP_SERVICE_CMD_INIT = 9;

	private int dataMax;
	
	private long timePassedInDatabaseMode;
	private long lastAlgorithmTickTime;
	
	private BroadcastReceiver algorithmTickReceiver, tickReceiver, stopDriverReceiver, driverUpdateReceiver;

	private Driver drv;

	//OmniPod Infusion Rate
	//private static final double INFUSION_RATE = 1.5 / 60.0;
	
	//Tandem Infusion Rate
	//private static final double INFUSION_RATE = 0.285714;
    
    //Roche Infusion Rate
	private static final double INFUSION_RATE = 0.5;

	private double totalDelivered;
	private long time;

	private final Messenger messengerFromPumpService = new Messenger(new incomingPumpHandler());
	private final Messenger messengerFromUI = new Messenger(new incomingUIHandler());

	private Messenger messengerToPumpService = null;
	private Messenger messengerToUI = null;

	@Override
	public void onCreate() {
		final String FUNC_TAG = "onCreate";

		super.onCreate();

		Debug.d(TAG, FUNC_TAG, "Creation...");

		// TODO: consider moving this to per device, and add dialog
		drv = Driver.getInstance();

		drv.context = this;
		
		totalDelivered = 0;

		// Set up a Notification for this Service
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		int icon = R.drawable.ic_launcher;
		CharSequence tickerText = "";
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);
		Context context = getApplicationContext();
		CharSequence contentTitle = "Device Driver";
		CharSequence contentText = "Standalone";
		Intent notificationIntent = new Intent(this, StandaloneDriver.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		final int DRVR_ID = 1;
		startForeground(DRVR_ID, notification);

		// Because this is a standalone driver the algorithm timer drives transmission of CGM data
		algorithmTickReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Debug.i(TAG, FUNC_TAG, "Algorithm tick");

				time = (long) (System.currentTimeMillis() / 1000);
				Debug.i(TAG, FUNC_TAG, "Time is real: " + time);
				
				long timeDiff = (lastAlgorithmTickTime != 0) ? time - lastAlgorithmTickTime : 0;
				lastAlgorithmTickTime = time;

				}
		};
		registerReceiver(algorithmTickReceiver, new IntentFilter("edu.virginia.dtc.intent.action.SUPERVISOR_CONTROL_ALGORITHM_TICK"));
		
		stopDriverReceiver = new BroadcastReceiver(){
			public void onReceive(Context context, Intent intent) {
				if (intent.getStringExtra("package").equals("edu.virginia.dtc.standaloneDriver")){
					Debug.d(TAG, FUNC_TAG, "Finishing...");
					sendDataMessage(messengerToUI, null, DRIVER2UI_FINISH, 0, 0);
						if (drv.pump != null) {
						Debug.i(TAG, FUNC_TAG, "Disconnecting standalone Pump");
						Intent pumpIntent = new Intent();
						pumpIntent.setClassName("edu.virginia.dtc.PumpService", "edu.virginia.dtc.PumpService.PumpService");;
						pumpIntent.putExtra("PumpCommand", PUMP_SERVICE_CMD_DISCONNECT);
						startService(pumpIntent);
					}
					stopSelf();
				}
			}			
		};
		registerReceiver(stopDriverReceiver, new IntentFilter("edu.virginia.dtc.STOP_DRIVER"));
		
		driverUpdateReceiver = new BroadcastReceiver(){
			public void onReceive(Context context, Intent i) {
				Intent intent = new Intent("edu.virginia.dtc.DEVICE_RESULT");
				int pumps = 0, cgms = 0;
				
				if(!drv.pumpList.isEmpty())
				{
					pumps = 1;
				}
				intent.putExtra("pumps", pumps);
				intent.putExtra("started", cgms > 0 || pumps > 0);
				intent.putExtra("name", "Standalone");
				sendBroadcast(intent);
				
				Debug.i(TAG, FUNC_TAG, "Sending service updates!");
			}
		};
		registerReceiver(driverUpdateReceiver, new IntentFilter("edu.virginia.dtc.DRIVER_UPDATE"));		
		
		Debug.i(TAG, FUNC_TAG,"Automatic operation starting!");
		
		Intent pumpIntent = new Intent();
		pumpIntent.setClassName("edu.virginia.dtc.PumpService", "edu.virginia.dtc.PumpService.PumpService");
		pumpIntent.putExtra("driver_intent", Driver.PUMP_INTENT);
		pumpIntent.putExtra("driver_name", Driver.DRIVER_NAME);
		pumpIntent.putExtra("PumpCommand", PUMP_SERVICE_CMD_INIT);
		startService(pumpIntent);
		
		Timer delay = new Timer();
		TimerTask wait = new TimerTask()
		{
			public void run()
			{
			
				if(drv.pump != null)
				{
					drv.updatePumpState(Pump.CONNECTED);
				}
				
				Intent intent = new Intent("edu.virginia.dtc.DEVICE_RESULT");
				int pumps = 0, cgms = 0;
				if(drv.pump!=null)
					pumps = 1;
				
				intent.putExtra("pumps", pumps);
				intent.putExtra("started", cgms > 0 || pumps > 0);
				intent.putExtra("name", "Standalone");
				sendBroadcast(intent);
				
				reportUIChange();
			}
		};
		
		delay.schedule(wait, 3000);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String FUNC_TAG = "onStartCommand";

		Debug.d(TAG, FUNC_TAG, "Received onStartCommand...");
		
		boolean auto = intent.getBooleanExtra("auto", false);
		if(auto)
		{
			
		}
		else
		{
			Intent uiIntent = new Intent();
			uiIntent.setClassName("edu.virginia.dtc.standaloneDriver", "edu.virginia.dtc.standaloneDriver.StandaloneUI");
			uiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(uiIntent);
			
			Debug.i(TAG, FUNC_TAG,"Standard startup");
		}
		
		return 0;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		final String FUNC_TAG = "onStart";

		Debug.d(TAG, FUNC_TAG, "Received onStart...");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(algorithmTickReceiver);
		unregisterReceiver(stopDriverReceiver);
		unregisterReceiver(driverUpdateReceiver);
	}

	// onBind supports two connections due to the dual nature of the standalone driver (these are filtered based on connection intent)
	@Override
	public IBinder onBind(Intent arg0) {
		final String FUNC_TAG = "onBind";

		Debug.i(TAG, FUNC_TAG, arg0.getAction());
		if (arg0.getAction().equalsIgnoreCase(Driver.PUMP_INTENT))
			return messengerFromPumpService.getBinder();
		else if (arg0.getAction().equalsIgnoreCase(Driver.UI_INTENT))
			return messengerFromUI.getBinder();
		else
			return null;
	}


	class incomingPumpHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			final String FUNC_TAG = "handleMessage";

			switch (msg.what) {
			case PUMP_SERVICE2DRIVER_NULL:
				break;
			case PUMP_SERVICE2DRIVER_REGISTER:
				messengerToPumpService = msg.replyTo;
				Debug.i(TAG, FUNC_TAG, "Pump Service replyTo registered, sending parameters...");

				// There is only a single pump so indices are not used
				drv.pump = new Device();
				reportUIChange();
				
				ContentValues pumpValues = new ContentValues();
				pumpValues.put("max_bolus_U", 25.0);
				pumpValues.put("min_bolus_U", 0.1);							//The lowest value is 0.1
				pumpValues.put("min_quanta_U", 0.1);
				pumpValues.put("infusion_rate_U_sec", INFUSION_RATE);
				pumpValues.put("reservoir_size_U", 300.0);
				pumpValues.put("low_reservoir_threshold_U", 50.0);
				pumpValues.put("unit_name", "units");
				pumpValues.put("unit_conversion", 1.0);
				pumpValues.put("queryable", 0);

				pumpValues.put("temp_basal", 0);							//Indicates if Temp Basals are possible
				pumpValues.put("temp_basal_time", 0);						//Indicates the time frame of a Temp Basal

				pumpValues.put("retries", 0);
				pumpValues.put("max_retries", 0);
				
				getContentResolver().update(Biometrics.PUMP_DETAILS_URI, pumpValues, null, null);

				sendDataMessage(messengerToPumpService, null, DRIVER2PUMP_SERVICE_PARAMETERS, msg.arg1, msg.arg2);
				
				drv.updatePumpState(Pump.REGISTERED);
				break;
			case PUMP_SERVICE2DRIVER_DISCONNECT:
				Debug.i(TAG, FUNC_TAG, "Disconnecting pump...");
				drv.pump = null;
				reportUIChange();
				
				drv.updatePumpState(Pump.DISCONNECTED);
				break;
			case PUMP_SERVICE2DRIVER_FLAGS:
				Debug.i(TAG, FUNC_TAG, "Receiving flags...");

				Long hypoTime = msg.getData().getLong("hypo_flag");
				Debug.i(TAG, FUNC_TAG, "Received hypo flag time: " + hypoTime);
				break;
			case PUMP_SERVICE2DRIVER_BOLUS:
				Debug.i(TAG, FUNC_TAG, "Receiving bolus command!");
				
				double bolusReq = msg.getData().getDouble("bolus");
				Debug.i(TAG, FUNC_TAG, "Bolus of " + bolusReq + " was requested");

				if(drv.pump.state > 0)
				{
					startInfusionTimer(bolusReq);
					updateDriverDetails();
				
					Debug.i(TAG, FUNC_TAG, "Continuing to total insulin and count as delivered");
					totalDelivered += bolusReq;
				}
				else
					Debug.i(TAG, FUNC_TAG, "Pump state is "+drv.pump.state+" so skipping delivery!"); 

				break;
			}
		}
	}

	class incomingUIHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			final String FUNC_TAG = "handleMessage";

			Device dev;

			switch (msg.what) {
			case UI2DRIVER_NULL:
				break;
			case UI2DRIVER_REGISTER:
				messengerToUI = msg.replyTo;
				break;
			}
		}
	}
	
	// This acknowledgement fires based on the bolus requested and infusion rate
	private void startInfusionTimer(double bolusReq) {

		final String FUNC_TAG = "startInfusionTimer";

		Timer infusionTimer = new Timer();
		final double bolus = bolusReq;
		
		
		//Send confirmation of bolus command to Pump Service
		Bundle data = new Bundle();
		data.putDouble("totalDelivered", totalDelivered);
		data.putLong("infusionTime", 20000);
		sendDataMessage(messengerToPumpService, data, DRIVER2PUMP_SERVICE_BOLUS_COMMAND_ACK, 0, 0);
		
		data = new Bundle();
		data.putLong("time", System.currentTimeMillis()/1000);
		data.putDouble("batteryVoltage", 3.0);
		data.putDouble("deliveredInsulin", bolus);
		data.putDouble("remainingInsulin", 300);
		data.putBoolean("lowReservoir", false);
		data.putDouble("totalDelivered", totalDelivered);	// Send in pulses for now
		data.putInt("status", Pump.DELIVERED);

		Debug.i(TAG, FUNC_TAG, "Sending bolus ACK...");
		sendDataMessage(messengerToPumpService, data, DRIVER2PUMP_SERVICE_BOLUS_DELIVERY_ACK, 0, 0);
	}

	// This method sends a null message to the UI which triggers it to update
	// the information it's displaying (pulls changes from singleton Driver object)
	private void reportUIChange() {
		if (messengerToUI != null) {
			try {
				messengerToUI.send(Message.obtain(null, DRIVER2UI_DEV_STATUS));
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		updateDriverDetails();
	}

	private void sendDataMessage(Messenger messenger, Bundle bundle, int what, int arg1, int arg2) {
		final String FUNC_TAG = "sendDataMessage";

		Message msg = Message.obtain(null, what);
		msg.arg1 = arg1;
		msg.arg2 = arg2;
		msg.setData(bundle);

		if(messenger!=null)
		{
			try {
				messenger.send(msg);
			} catch (RemoteException e) {
				Debug.i(TAG, FUNC_TAG, "Messenger is null!");
			}
		}
	}

	// Because this is a simulator driver, the system pulls CGM data from a file or internal log
	
	
	public void updateDriverDetails() {
		String pumpDetails = "";
		if (!drv.pumpList.isEmpty()) {
			DecimalFormat df = new DecimalFormat("#.##");
			pumpDetails = "Name:  Standalone\nInterface:  Simulation\nTotal Delivered:  " + df.format(totalDelivered) + "U\nStatus:  " + drv.pump.status;
		}
		
		ContentValues pumpValues = new ContentValues();
		pumpValues.put("details", pumpDetails);
		
		getContentResolver().update(Biometrics.PUMP_DETAILS_URI, pumpValues, null, null);
	}
}
