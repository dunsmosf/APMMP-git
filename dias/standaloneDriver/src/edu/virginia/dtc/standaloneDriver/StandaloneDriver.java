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
	
	private static final int UI2DRIVER_DATABASE_CGM_DATA = 9;
	
	// Commands to CGM Service
	private static final int DRIVER2CGM_SERVICE_NEW_CGM_DATA = 0;
	private static final int DRIVER2CGM_SERVICE_PARAMETERS = 1;
	private static final int DRIVER2CGM_SERVICE_STATUS_UPDATE = 2;
	private static final int DRIVER2CGM_SERVICE_CALIBRATE_ACK = 3;
	
  	private static final int CGM_SERVICE_CMD_DISCONNECT = 2;

	// Commands to Pump Service
	public static final int DRIVER2PUMP_SERVICE_PARAMETERS = 0;
	public static final int DRIVER2PUMP_SERVICE_STATUS_UPDATE = 1;
	public static final int DRIVER2PUMP_SERVICE_RESERVOIR = 2;
	public static final int DRIVER2PUMP_SERVICE_BOLUS_COMMAND_ACK = 3;
	public static final int DRIVER2PUMP_SERVICE_BOLUS_DELIVERY_ACK = 4;

	// Commands for CGM Driver
	private static final int CGM_SERVICE2DRIVER_NULL = 0;
	private static final int CGM_SERVICE2DRIVER_REGISTER = 1;
	private static final int CGM_SERVICE2DRIVER_CALIBRATE = 2;
	private static final int CGM_SERVICE2DRIVER_DIAGNOSTIC = 3;
	private static final int CGM_SERVICE2DRIVER_DISCONNECT = 4;
	private static final int CGM_SERVICE_CMD_INIT = 3;

	// Commands for Pump Driver
	private static final int PUMP_SERVICE2DRIVER_NULL = 0;
	private static final int PUMP_SERVICE2DRIVER_REGISTER = 1;
	private static final int PUMP_SERVICE2DRIVER_DISCONNECT = 2;
	private static final int PUMP_SERVICE2DRIVER_FLAGS = 3;
	private static final int PUMP_SERVICE2DRIVER_BOLUS = 4;
 	private static final int PUMP_SERVICE_CMD_DISCONNECT = 10;
 	private static final int PUMP_SERVICE_CMD_INIT = 9;

	private double[] CGMFILEData;
	private int dataMax;
	
	private double[] databaseCGMValues;
	private long[] databaseCGMTimes;
	private int databaseCGMCount;
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

	private final Messenger messengerFromCgmService = new Messenger(new incomingCgmHandler());
	private final Messenger messengerFromPumpService = new Messenger(new incomingPumpHandler());
	private final Messenger messengerFromUI = new Messenger(new incomingUIHandler());

	private Messenger messengerToCgmService = null;
	private Messenger messengerToPumpService = null;
	private Messenger messengerToUI = null;

	@Override
	public void onCreate() {
		final String FUNC_TAG = "onCreate";

		super.onCreate();

		Debug.d(TAG, FUNC_TAG, "Creation...");

		// TODO: consider moving this to per device, and add dialog
		CGMFILEData = new double[2880];
		dataMax = getCgmDataFile();

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

				// TODO right now this only sends data from the first CGM until update to bio content provider
				if(drv.cgm != null)
				{
					double cgmValue = 0;

					// Return new CGM data from device to service
					if (drv.cgm.csv_index >= dataMax - 1)
						drv.cgm.csv_index = 0;
					
					switch(drv.cgm.inputMode)
					{
						case USER:
							cgmValue = drv.cgm.userValue;
							break;	
						case CSV:
							cgmValue = CGMFILEData[drv.cgm.csv_index];
							drv.cgm.csv_index++;
							break;
						case DATABASE:
							timePassedInDatabaseMode += timeDiff;
							if (drv.cgm.db_index >= databaseCGMCount) {
								cgmValue = 0;
							} else if (timePassedInDatabaseMode < databaseCGMTimes[drv.cgm.db_index]){
								cgmValue = 0;
							} else {
								cgmValue = databaseCGMValues[drv.cgm.db_index];
								drv.cgm.db_index++;								
							}		
							break;	
					}

					
					Bundle data = new Bundle();
					data.putLong("time", time);
					data.putDouble("cgmValue", cgmValue);
					data.putInt("trend", trendArrow());
					data.putInt("minToNextCalibration", 720);
					data.putInt("calibrationType", 0);
					data.putInt("cgm_state", drv.cgm.state);

					Debug.i(TAG, FUNC_TAG, "CGM: "+cgmValue+" Time: "+time+ " State: "+drv.cgm.state);
					
					if(drv.antenna)		//We can simulate loss of antenna issues
						sendDataMessage(messengerToCgmService, data, DRIVER2CGM_SERVICE_NEW_CGM_DATA, 0, 0);
				}
			}
		};
		registerReceiver(algorithmTickReceiver, new IntentFilter("edu.virginia.dtc.intent.action.SUPERVISOR_CONTROL_ALGORITHM_TICK"));
		
		stopDriverReceiver = new BroadcastReceiver(){
			public void onReceive(Context context, Intent intent) {
				if (intent.getStringExtra("package").equals("edu.virginia.dtc.standaloneDriver")){
					Debug.d(TAG, FUNC_TAG, "Finishing...");
					sendDataMessage(messengerToUI, null, DRIVER2UI_FINISH, 0, 0);
					if (drv.cgm != null) {
						Debug.i(TAG, FUNC_TAG, "Disconnecting standalone CGM");
						Intent cgmIntent = new Intent();
						cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
						cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_DISCONNECT);
						startService(cgmIntent);
					}
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
				if(!drv.cgmList.isEmpty())
				{
					cgms = 1;
				}
				
				intent.putExtra("cgms", cgms);
				intent.putExtra("pumps", pumps);
				intent.putExtra("started", cgms > 0 || pumps > 0);
				intent.putExtra("name", "Standalone");
				sendBroadcast(intent);
				
				Debug.i(TAG, FUNC_TAG, "Sending service updates!");
			}
		};
		registerReceiver(driverUpdateReceiver, new IntentFilter("edu.virginia.dtc.DRIVER_UPDATE"));		
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String FUNC_TAG = "onStartCommand";

		Debug.d(TAG, FUNC_TAG, "Received onStartCommand...");
		
		boolean auto = intent.getBooleanExtra("auto", false);
		if(auto)
		{
			Debug.i(TAG, FUNC_TAG,"Automatic operation starting!");
			
			Intent cgmIntent = new Intent();
		    cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
		    cgmIntent.putExtra("driver_intent", Driver.CGM_INTENT);
		    cgmIntent.putExtra("driver_name", Driver.DRIVER_NAME);
		    cgmIntent.putExtra("reset", true);
		    cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_INIT);
	        startService(cgmIntent);
	        
	        Intent pumpIntent = new Intent();
			pumpIntent.setClassName("edu.virginia.dtc.PumpService", "edu.virginia.dtc.PumpService.PumpService");
			pumpIntent.putExtra("driver_intent", Driver.PUMP_INTENT);
			pumpIntent.putExtra("driver_name", Driver.DRIVER_NAME);
			cgmIntent.putExtra("reset", true);
			pumpIntent.putExtra("PumpCommand", PUMP_SERVICE_CMD_INIT);
			startService(pumpIntent);
			
			Timer delay = new Timer();
			TimerTask wait = new TimerTask()
			{
				public void run()
				{
					if(drv.cgm != null)
					{
						drv.cgm.state = CGM.CGM_NORMAL;
						drv.cgmList.add("CGM \n"+drv.cgm.status);
					}

					if(drv.pump != null)
					{
						drv.updatePumpState(Pump.CONNECTED);
						drv.pumpList.add("PUMP \n"+drv.pump.status);
					}
					
					reportUIChange();
				}
			};
			
			delay.schedule(wait, 3000);
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
		else if (arg0.getAction().equalsIgnoreCase(Driver.CGM_INTENT))
			return messengerFromCgmService.getBinder();
		else if (arg0.getAction().equalsIgnoreCase(Driver.UI_INTENT))
			return messengerFromUI.getBinder();
		else
			return null;
	}

	class incomingCgmHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			final String FUNC_TAG = "handleMessage";
			Bundle data;

			switch (msg.what) {
			case CGM_SERVICE2DRIVER_NULL:
				break;
			case CGM_SERVICE2DRIVER_REGISTER:
				//If the service is registering then it is restarted and we must clear the known list of CGM devices
				messengerToCgmService = msg.replyTo;
				Debug.i(TAG, FUNC_TAG, "CGM Service replyTo registered, sending parameters...");

				drv.cgm = new Device();
				reportUIChange();
				
				ContentValues cgmValues = new ContentValues();
				cgmValues.put("min_cgm", 40);
				cgmValues.put("max_cgm", 400);
				cgmValues.put("phone_calibration", 1);			//Indicates whether the calibration data is entered on the phone or the CGM device
				
				getContentResolver().update(Biometrics.CGM_DETAILS_URI, cgmValues, null, null);

				sendDataMessage(messengerToCgmService, null, DRIVER2CGM_SERVICE_PARAMETERS, 0, 0);
				break;
			case CGM_SERVICE2DRIVER_CALIBRATE:
				data = msg.getData();
				int BG = data.getInt("calibration_value");

				Debug.i(TAG, FUNC_TAG, "Calibration received: " + BG + "! Sending ACK...");
				
				data = new Bundle();
				data.putInt("cal_value", BG);
				sendDataMessage(messengerToCgmService, data, DRIVER2CGM_SERVICE_CALIBRATE_ACK, 0, 0);
				break;
			case CGM_SERVICE2DRIVER_DISCONNECT:
				Debug.i(TAG, FUNC_TAG, "Removing device");
				drv.cgm=null;
				reportUIChange();
				break;
			}
		}
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
			case UI2DRIVER_DATABASE_CGM_DATA:
				databaseCGMValues = msg.getData().getDoubleArray("cgmValues");		
				databaseCGMCount = databaseCGMValues.length;
				
				long[] originalTimes = msg.getData().getLongArray("cgmTimes");
				long originalStartingTime = originalTimes[0];
				databaseCGMTimes = new long[databaseCGMCount];
				for (int i = 0; i < originalTimes.length; i++) 
				{
					databaseCGMTimes[i] = originalTimes[i] - originalStartingTime; //only keep track of offsets from a base time (which is now)
				}

				drv.cgm.db_index = 0;	
				timePassedInDatabaseMode = 0;
				lastAlgorithmTickTime = 0;
				Debug.i(TAG, FUNC_TAG, "Received database cgm data from UI of length=" + databaseCGMCount);
				break;
			}
		}
	}
	
	public int trendArrow() {
		final String FUNC_TAG = "trendArrow";
		
		double slope = calculateSlope();
		
		if (slope > 2)
			return 2;
		else if (slope > 1)
			return 1;
		else if (slope > -1)
			return 0;
		else if (slope > -2)
			return -1;
		else
			return -2;
	}
	
	private double calculateSlope()
	{
		final String FUNC_TAG = "calculateSlope";
		
		Cursor c = getContentResolver().query(Biometrics.CGM_URI, new String[] {"cgm","time"}, null, null, null);
		double p1=0, p2=0, slope = 0;
		long t1=0, t2=0;
		
		if(c!=null)
		{
			if(c.getCount() > 2)
			{
				if(c.moveToLast())
				{
					p1 = c.getDouble(c.getColumnIndex("cgm"));
					t1 = c.getLong(c.getColumnIndex("time"));
				}
				if(c.moveToPrevious())
				{
					p2 = c.getDouble(c.getColumnIndex("cgm"));
					t2 = c.getLong(c.getColumnIndex("time"));
				}
				
				slope = (double)(p2-p1)/(t2-t1);
				
				Debug.i(TAG, FUNC_TAG, "Slope: "+slope);
			}
		}
		
		c.close();
		
		return 0;
	}
	
	// This acknowledgement fires based on the bolus requested and infusion rate
	private void startInfusionTimer(double bolusReq) {
		final String FUNC_TAG = "startInfusionTimer";

		Timer infusionTimer = new Timer();
		final double bolus = bolusReq;
		
		long start = (((long) (((bolusReq)) / INFUSION_RATE)) * 1000) + 10000;
	
		TimerTask infusionTimerTask = new TimerTask() {
			public void run() {
				totalDelivered += (bolus);
				
				Debug.i(TAG, FUNC_TAG, "Total insulin delivered: " + totalDelivered);

				long extTime = ((long) ((bolus)/ INFUSION_RATE));		//Leave out multiply by 1000 since its in seconds, this is to tack 
																		//on the delivered time to get a more realistic timestamp in the DB
				time = System.currentTimeMillis()/1000;
				
				Debug.i(TAG, FUNC_TAG, "Delivered Time: "+(time+extTime)+" Infusion time added: "+extTime);
				
				Bundle data = new Bundle();
				data.putLong("time", time+extTime);
				data.putDouble("batteryVoltage", 3.0);
				if(drv.pump.status.equals("No Insulin")){
					data.putDouble("deliveredInsulin", 0.0);
				}
				else {
					data.putDouble("deliveredInsulin", bolus);
				}
				data.putDouble("remainingInsulin", 300);
				data.putBoolean("lowReservoir", false);
				data.putDouble("totalDelivered", totalDelivered);	// Send in pulses for now
				data.putInt("status", Pump.DELIVERED);

				Debug.i(TAG, FUNC_TAG, "Sending bolus ACK...");
				sendDataMessage(messengerToPumpService, data, DRIVER2PUMP_SERVICE_BOLUS_DELIVERY_ACK, 0, 0);
			}
		};
		
		//Send confirmation of bolus command to Pump Service
		Bundle data = new Bundle();
		data.putDouble("totalDelivered", totalDelivered);
		data.putLong("infusionTime", start);
		sendDataMessage(messengerToPumpService, data, DRIVER2PUMP_SERVICE_BOLUS_COMMAND_ACK, 0, 0);
		
		if (!drv.pump.status.equals("Missing Bolus"))
		{
			infusionTimer.schedule(infusionTimerTask, start);
		}
		Debug.i(TAG, FUNC_TAG, "Infusion timer set for " + start + "ms");
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
	public int getCgmDataFile() {
		final String FUNC_TAG = "getCgmDataFile";

		String state = Environment.getExternalStorageState();
		boolean useSD = false;
		BufferedReader reader = null;
		File dir = Environment.getExternalStorageDirectory();
		File simDataOnCard = new File(dir, "standalone/cgmdata");

		Debug.i("fetchCGMDataFile", FUNC_TAG, "Loading internal file");
		InputStream inputStream = getResources().openRawResource(R.raw.dexcom);
		if (inputStream == null) {
			Debug.i("fetchCGMDataFile", FUNC_TAG, "null inputStream");
		}
		reader = new BufferedReader(new InputStreamReader(inputStream));

		if ((Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) && simDataOnCard.exists()) {
			Debug.i("fetchCGMDataFile", FUNC_TAG, "SD is available and file exists, loading file from SD");
			useSD = true;
			try {
				reader = new BufferedReader(new FileReader(simDataOnCard));
			} catch (FileNotFoundException e) {
				Debug.i("fetchCGMDataFile", FUNC_TAG, "File not found!");
			}
		} else {
			Debug.i("fetchCGMDataFile", FUNC_TAG, "SD inaccessible, continuing with internal file...");
		}
		Debug.i("fetchCGMDataFile", FUNC_TAG, "Reading file...");
		int ii = 0;
		try {
			String line;
			String[] values;
			while ((line = reader.readLine()) != null) {
				if (useSD) {
					values = line.split(";");
					CGMFILEData[ii] = Float.parseFloat(values[0]);
				} else {
					values = line.split(";");
					CGMFILEData[ii] = Float.parseFloat(values[1]);
				}
				ii = ii + 1;
			}
		} catch (IOException ie) {
			Debug.i("fetchCGMDataFile", FUNC_TAG, "Error parsing from file");
		}
		Debug.i("fetchCGMDataFile", FUNC_TAG, "Load complete...");
		return ii;
	}
	
	public void updateDriverDetails() {
		String cgmDetails = "";
		if (!drv.cgmList.isEmpty()) {
			cgmDetails = "Name:  Standalone";
			cgmDetails += "\nCGM - " + drv.cgm.status;
		}
		String pumpDetails = "";
		if (!drv.pumpList.isEmpty()) {
			DecimalFormat df = new DecimalFormat("#.##");
			pumpDetails = "Name:  Standalone\nInterface:  Simulation\nTotal Delivered:  " + df.format(totalDelivered) + "U\nStatus:  " + drv.pump.status;
		}
		
		ContentValues cgmValues = new ContentValues();
		cgmValues.put("details", cgmDetails);
		ContentValues pumpValues = new ContentValues();
		pumpValues.put("details", pumpDetails);
		
		getContentResolver().update(Biometrics.CGM_DETAILS_URI, cgmValues, null, null);
		getContentResolver().update(Biometrics.PUMP_DETAILS_URI, pumpValues, null, null);
	}
}
