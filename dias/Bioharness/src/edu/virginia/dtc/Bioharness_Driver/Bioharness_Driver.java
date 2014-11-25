/*
 * This device driver will be a simulator style driver that represents what was previously
 * the "Standalone" operating mode.  This driver also is a good representation of a driver
 * like the iDex where both the Pump and CGM 
 */
package edu.virginia.dtc.Bioharness_Driver;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

import zephyr.android.BioHarnessBT.BTClient;
import zephyr.android.BioHarnessBT.ZephyrProtocol;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;

public class Bioharness_Driver extends Service {

	private static final String TAG = "Bioharness_Driver";
	
	public static boolean PRINT_DATA = false;
	
	public static final int DIAS_SERVICE_COMMAND_SET_EXERCISE_STATE = 4;
	
	// Messages to UI
	private static final int DRIVER2UI_NULL = 0;
	private static final int DRIVER2UI_DEV_STATUS = 1;
	private static final int DRIVER2UI_FINISH = 2;
	private static final int DRIVER2UI_NEW_HR = 3;
	private static final int DRIVER2UI_RESTING_HR=4;

	// Messages from UI
	private static final int UI2DRIVER_NULL = 0;
	private static final int UI2DRIVER_REGISTER = 1;
	private static final int UI2DRIVER_HR_START = 2;
	private static final int UI2DRIVER_HR_STOP = 3;
	
	// Commands to CGM Service
	public static final int HR_SERVICE_CMD_NULL = 0;
	public static final int HR_SERVICE_CMD_DISCONNECT = 1;
	public static final int HR_SERVICE_CMD_INIT = 2;

	// Commands for CGM Driver
	private static final int EXERCISE_SERVICE2DRIVER_NULL = 0;
	private static final int EXERCISE_SERVICE2DRIVER_REGISTER = 1;
	private static final int EXERCISE_SERVICE2DRIVER_DISCONNECT = 2;
	private static final int EXERCISE_SERVICE2DRIVER_HR_REST = 3;

	// Commands for CGM Service from Driver
	private static final int DRIVER2EXERCISE_SERVICE_NEW_CGM_DATA = 0;
	private static final int DRIVER2EXERCISE_SERVICE_PARAMETERS = 1;
	private static final int DRIVER2EXERCISE_SERVICE_STATUS_UPDATE = 2;

	// Pump and CGM intent fields
	private static final String DEV_NAME = "device_name";
	private static final String DEV_ADDR = "device_address";
	private static final String DEV_DRVR = "device_driver";
	
	private BroadcastReceiver  driverUpdateReceiver;

	private Driver thisDriver;
	
	public static List<listDevice> devices;
	public static BluetoothAdapter bt;
	public static BluetoothManager bm;
	public static Bioharness bh;
	
	private final Messenger messengerFromExerciseService = new Messenger(new incomingExerciseHandler());
	private final Messenger messengerFromUI = new Messenger(new incomingUIHandler());

	private Messenger messengerToExerciseService = null;
	private Messenger messengerToUI = null;
	
	//Zephyr Driver
	BTClient _bt;
	ZephyrProtocol _protocol;
	NewConnectedListener _NConnListener;
	
	private BroadcastReceiver mReceiver = null;
	
	int  GSR, HRV, HR, Posture, ROG, BatteryLevel, BreathingRate_conf, HeartRate_conf, ROGstatus, SeqNum, System_conf,VersionNumber;
	double Activity, Voltage, BreathingWaveAmplitude, BreathingWaveAmplitudeNoise, ECGAmplitude;
	double ECGNoise, Lateral_AxisAccnMin, Lateral_AxisAccnPeak, MsofDay, PeakAcceleration, RespirationRate, Sagittal_AxisAccnMin; 
	double Sagittal_AxisAccnPeak, Skintemperature, Vertical_AxisAccnMin, Vertical_AxisAccnPeak;

	private final int GSR_b= 0x100;							
	private final int HRV_b=  0x101;
	private final int HR_b=  0x102;										
	private final int Posture_b=  0x103;										
	private final int ROG_b=  0x104;										
	private final int BatteryLevel_b=  0x105;										
	private final int BreathingRate_conf_b=  0x106;					
	private final int HeartRate_conf_b=  0x107;					
	private final int ROGstatus_b=  0x108;					
	private final int SeqNum_b=  0x109;					
	private final int System_conf_b=  0x110;					
	private final int VersionNumber_b=  0x111;					
	private final int Activity_b=  0x112;					
	private final int Voltage_b=  0x113;					
	private final int BreathingWaveAmplitude_b=  0x114;					
	private final int BreathingWaveAmplitudeNoise_b=  0x115;					
	private final int ECGAmplitude_b=  0x116;					
	private final int ECGNoise_b=  0x117;					
	private final int Lateral_AxisAccnMin_b=  0x118;					
	private final int Lateral_AxisAccnPeak_b=  0x119;					
	private final int MsofDay_b=  0x120;					
	private final int PeakAcceleration_b=  0x121;					
	private final int RespirationRate_b=  0x122;					
	private final int Sagittal_AxisAccnMin_b=  0x123;					
	private final int Sagittal_AxisAccnPeak_b=  0x124;					
	private final int Skintemperature_b=  0x125;					
	private final int Vertical_AxisAccnMin_b=  0x126;					
	private final int Vertical_AxisAccnPeak_b=  0x127;
	
	public static final String PREFS = "Exercise_Controller";
	
	private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> reconnect;
	
	private boolean disconnecting = false;
	
	private Runnable recon = new Runnable()
	{
		final String FUNC_TAG = "recon";
		
		@Override
		public void run() 
		{
			if(_bt != null)
			{
				if(_bt.IsConnected())
				{
					Debug.w(TAG, FUNC_TAG, "The BH is connected...no action necessary!");
				}
				else
				{
					Debug.w(TAG, FUNC_TAG, "The BH is disconnected...sending connection signal!");
					connectBioharness();
				}
			}
			else
			{
				Debug.e(TAG, FUNC_TAG, "The BT connection thread is null...starting thread!");
				connectBioharness();
			}
		}
	};
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		final String FUNC_TAG = "onCreate";

        IntentFilter filter3 = new IntentFilter("edu.virginia.dtc.intent.action.HR_REST");
        this.getApplicationContext().registerReceiver(new HRReceiver(), filter3);

		Debug.i(TAG, FUNC_TAG, "");

		thisDriver = Driver.getInstance();

		bm = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		bt = bm.getAdapter();
		
		bh = new Bioharness();
		
		// Set up a Notification for this Service
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		int icon = R.drawable.ic_launcher;
		CharSequence tickerText = "";
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);
		Context context = getApplicationContext();
		CharSequence contentTitle = "Device Driver";
		CharSequence contentText = "Zephyr BH";
		Intent notificationIntent = new Intent(this, Bioharness_Driver.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		final int DRVR_ID = 1;
		startForeground(DRVR_ID, notification);
		
		//Start the Exercise service each time the driver is started
		Intent hrIntent = new Intent();
		hrIntent.setClassName("edu.virginia.dtc.ExerciseService", "edu.virginia.dtc.ExerciseService.ExerciseService");
		hrIntent.putExtra("driver_intent", Driver.DRIVER_INTENT);
		hrIntent.putExtra("driver_name", Driver.DRIVER_NAME);
		hrIntent.putExtra("reset", true);
		hrIntent.putExtra("HRCommand", HR_SERVICE_CMD_INIT);
        startService(hrIntent);
        
        devices = new ArrayList<listDevice>();
        
        mReceiver = new BroadcastReceiver() 
        {
        	final String FUNC_TAG = "mReceiver";
        	
        	@Override
        	public void onReceive(Context context, Intent intent) 
        	{
        		String action = intent.getAction();
        		Debug.d(TAG, FUNC_TAG, "Action: "+action);
           		
        		if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
        		{
        			Debug.i(TAG, FUNC_TAG, "Discovery complete...");
        			Bioharness_DriverUI.scan.setEnabled(true);
        		}
        		if (BluetoothDevice.ACTION_FOUND.equals(action)) 
           		{
        			BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        			
           			Debug.i(TAG, FUNC_TAG, "Device found: "+dev.getName() + " " + dev.getAddress());

           			if(dev.getName() == null || !dev.getName().startsWith("BH"))
           			{
           				Debug.w(TAG, FUNC_TAG, "Device found is not Bioharness...");
           				return;
           			}
           			
        			boolean exists = false;
        			for(listDevice d:devices)
        			{
        				if(d.address.equalsIgnoreCase(dev.getAddress()))
        				{
        					Debug.i(TAG, FUNC_TAG, "Device already found in the list...");
        					exists = true;
        				}
        			}
        			if(!exists)
        			{
        				Debug.i(TAG, FUNC_TAG, "Adding device...");
        				devices.add(new listDevice(dev.getAddress(), dev));
        			}
        			
        			reportUIChange();
           		}
        		if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) 
           		{
           			BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
           			
           			if(bh.getDevice() == null)
           			{
           				Debug.e(TAG, FUNC_TAG, "The bioharness BT device is null...");
           				return;
           			}
           			
           			if(dev != null && dev.getAddress().equals(bh.getDevice().getAddress()))
           			{
           				bh.connected = true;
           				
           				Debug.i(TAG, FUNC_TAG, "Adding Bioharness to running misc. driver field...");
           				ContentValues dv = new ContentValues();
           				dv.put("running_misc", "edu.virginia.dtc.Bioharness_Driver.Bioharness_Driver");					
           				getContentResolver().update(Biometrics.HARDWARE_CONFIGURATION_URI, dv, null, null);
           				
           				SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
           				prefs.edit().putString("mac", bh.getDevice().getAddress()).commit();
           				
           				if(bt.isDiscovering())
           				{
           					Debug.i(TAG, FUNC_TAG, "Canceling discovery: "+bt.cancelDiscovery());
           					Bioharness_DriverUI.scan.setEnabled(true);
           				}
           				
	           			if(reconnect != null)
	           			{
	           				Debug.i(TAG, FUNC_TAG, "Cancelling reconnection timers...");
	           				reconnect.cancel(true);
	           			}
	           			
	           			boolean controller_button;
	                	 
	           			SharedPreferences exercise_ctrl = getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
	           			controller_button = exercise_ctrl.getBoolean("Control", true);
	                	 
	           			Intent i = new Intent();
	           			i.putExtra("toggle_value", controller_button);
	           			i.setAction("edu.virginia.dtc.intent.EXERCISE_TOGGLE_BUTTON");
	           			sendBroadcast(i);
	                	 
	           			Debug.i(TAG, FUNC_TAG, "Set 'Exercise detection' to toggle button value ("+controller_button+") after reconnection.");
           			}
           			else
           			{
           				if(dev == null)
           					Debug.w(TAG, FUNC_TAG, "This message isn't for us, and the device is null!");
           				else
           					Debug.w(TAG, FUNC_TAG, "This message isn't for us (it's for "+dev.getName()+" "+dev.getAddress()+")");
           			}
                 }
                 if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) 
                 {
                	 BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                	
                	 if(bh.getDevice() == null)
                	 {
        				Debug.e(TAG, FUNC_TAG, "The bioharness BT device is null...");
        				return;
                	 }
                	 
                	 if(dev != null && dev.getAddress().equals(bh.getDevice().getAddress()))
                	 {
                		 Debug.e(TAG, FUNC_TAG, "The BH was disconnected...");
 					
                		 bh.connected = false;
                		 
                		 if(_bt != null)
                			 _bt.Close();
 					
                		 //If disconnecting is true, we are trying to turn it off
                		 if(disconnecting)
                		 {
                			 Debug.i(TAG, FUNC_TAG, "Skipping reconnection since we are intentionally disconnecting...");
                			 
                			 Debug.i(TAG, FUNC_TAG, "Removing Bioharness from running misc. driver field...");
                			 ContentValues dv = new ContentValues();
                			 dv.put("running_misc", "");					
                			 getContentResolver().update(Biometrics.HARDWARE_CONFIGURATION_URI, dv, null, null);
                			 
                			 Debug.i(TAG, FUNC_TAG, "Removing device from application saved data...");
                			 SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
                			 prefs.edit().putString("mac", "").commit();
                			 
                			 bh = new Bioharness();
                			 
                			 disconnecting = false;
                		 }
                		 else
                		 {
                			 Debug.i(TAG, FUNC_TAG, "Starting reconnection...");
						
                			 if(reconnect != null)
                				 reconnect.cancel(true);
                			 reconnect = scheduler.scheduleAtFixedRate(recon, 0, 30, TimeUnit.SECONDS);
                		 }
                	 }
                	 else
                	 {
                		if(dev != null)
                		{
                			Debug.w(TAG, FUNC_TAG, "This message isn't for us (it's for "+dev.getName()+" "+dev.getAddress()+")");
                		}
                		else
                		{
                			Debug.w(TAG, FUNC_TAG, "This message isn't for us and the device is null!");
                		}
                	 }
                 }  
                 
                 reportUIChange();
           	}
       };
       this.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
       this.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
       this.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
       this.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
      
      	SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
		String mac = prefs.getString("mac", "");
		
		if(!mac.equalsIgnoreCase(""))
		{
			Debug.d(TAG, FUNC_TAG, "Saved device found: "+mac);
			disconnecting = false;
			
			bh = new Bioharness(bt.getRemoteDevice(mac));
			
			if(reconnect != null)
				reconnect.cancel(true);
				
			Debug.i(TAG, FUNC_TAG, "Starting Bioharness...");
			reconnect = scheduler.scheduleAtFixedRate(recon, 0, 30, TimeUnit.SECONDS);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String FUNC_TAG = "onStartCommand";
		
		Debug.d(TAG, FUNC_TAG, "Received onStartCommand...");
		
		if(!intent.getBooleanExtra("auto", false))
		{
			Intent uiIntent = new Intent();
			uiIntent.setClassName("edu.virginia.dtc.Bioharness_Driver", "edu.virginia.dtc.Bioharness_Driver.Bioharness_DriverUI");
			uiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(uiIntent);
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
		thisDriver.zephyr = null;
		
		unregisterReceiver(driverUpdateReceiver);
	}

	// onBind supports two connections due to the dual nature of the standalone driver (these are filtered based on connection intent)
	@Override
	public IBinder onBind(Intent arg0) {
		final String FUNC_TAG = "onBind";
		
		Debug.i(TAG, FUNC_TAG, arg0.getAction());
		
		if (arg0.getAction().equalsIgnoreCase(Driver.DRIVER_INTENT))
			return messengerFromExerciseService.getBinder();
		else if (arg0.getAction().equalsIgnoreCase(Driver.UI_INTENT))
			return messengerFromUI.getBinder();
		else
			return null;
	}

	class incomingExerciseHandler extends Handler 
	{
		@Override
		public void handleMessage(Message msg) 
		{
			final String FUNC_TAG = "incomingExerciseHandler";

			switch (msg.what) 
			{
				case EXERCISE_SERVICE2DRIVER_NULL:
					break;
				case EXERCISE_SERVICE2DRIVER_REGISTER:
					//If the service is registering then it is restarted and we must clear the known list of CGM devices
					messengerToExerciseService = msg.replyTo;
					Debug.i(TAG, FUNC_TAG, "CGM Service replyTo registered, sending parameters...");
	
					// Gather state and device indices, these values come from the
					// CGM Service so device creation and deletion (see CGM_DRIVER_DISCONNECT) 
					// are started at the CGM Service and filter down here
					thisDriver.my_state_index = msg.arg1;
	
					reportUIChange();
					break;
				case EXERCISE_SERVICE2DRIVER_DISCONNECT:
					Debug.i(TAG, FUNC_TAG, "Removing device-" + msg.arg2);
					
					reportUIChange();
					break;
			}
		}
	}
	
	class incomingUIHandler extends Handler 
	{
		@Override
		public void handleMessage(Message msg) 
		{
			final String FUNC_TAG = "incomingUIHandler";
			
			Device dev;

			switch (msg.what) 
			{
				case UI2DRIVER_NULL:
					break;
				case UI2DRIVER_REGISTER:
					messengerToUI = msg.replyTo;
					break;
				case UI2DRIVER_HR_START:
					dev = thisDriver.zephyr;
					dev.status = "Started";
					dev.running = true;
					dev.connected = true;
					dev.cgm_ant = true;
					
					disconnecting = false;
					
					String mac = msg.getData().getString("mac");
					
					bh = new Bioharness(bt.getRemoteDevice(mac));
					
					if(reconnect != null)
 						reconnect.cancel(true);
 					
 					reconnect = scheduler.scheduleAtFixedRate(recon, 0, 30, TimeUnit.SECONDS);
 					
					reportExerciseServiceChange(dev);
					break;
				case UI2DRIVER_HR_STOP:
//					Intent cgmState = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
//			    	cgmState.putExtra("id", 1);
//			    	cgmState.putExtra("color", Color.WHITE);
//			    	cgmState.putExtra("remove", true);
//					sendBroadcast(cgmState);

					disconnecting = true;
					
					if(reconnect != null)
						reconnect.cancel(true);
					
					dev = thisDriver.zephyr;
					dev.status = "Stopped";
					dev.running = false;
					dev.connected = false;
					dev.cgm_ant = false;
					
					//This disconnects listener from acting on received messages
					if(_bt != null)
					{
						_bt.removeConnectedEventListener(_NConnListener);
						_bt.Close();
					}
					
					if (_bt != null && !_bt.IsConnected())
					{
						Debug.i(TAG, FUNC_TAG, "BH Disconnected!");	
					}

					reportExerciseServiceChange(dev);
					break;
			}
			
			reportUIChange();
		}
	}

	// This sends a message to the CGM Service to update a device status or variable
	private void reportExerciseServiceChange(Device dev) {
		if (messengerToExerciseService != null) {
			Bundle data = new Bundle();
			data.putBoolean("running", dev.running);
			data.putBoolean("connected", dev.connected);
			data.putString("status", dev.status);

			sendDataMessage(messengerToExerciseService, data, DRIVER2EXERCISE_SERVICE_STATUS_UPDATE, thisDriver.my_state_index, dev.my_dev_index);
		}
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
	}
	
	private void reportUIChange(double activity, int battery, int time) {
		
		Bioharness_DriverUI.battery = battery;
		Bioharness_DriverUI.hr_val_List.insert("Activity: "+ activity + " || Date: "+ time, 0);
		
//		if (messengerToUI != null) {
//			try {
//				Message msg = Message.obtain(null, DRIVER2UI_NEW_HR);
//				
//				msg.arg1 = time;
//				msg.arg2 = battery;
//				msg.obj=activity;
//				
//				messengerToUI.send(msg);
//			} catch (RemoteException e) {
//				e.printStackTrace();
//			}
//		}

	}
	
	private void reportUIChange(int hr_rest,int what) {
		if (messengerToUI != null) {
			try {
				Message msg = Message.obtain(null, DRIVER2UI_RESTING_HR);
				msg.arg1 = hr_rest;
				messengerToUI.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

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

	public void connectBioharness()
	{
		final String FUNC_TAG = "ConnectBioharness";
		
		_bt = new BTClient(bt, bh.getDevice().getAddress());
		_NConnListener = new NewConnectedListener(Newhandler,Newhandler);
		_bt.addConnectedEventListener(_NConnListener);
		
		if (_bt != null)
		{
			if(_bt.IsConnected())
			{
				Debug.i(TAG, FUNC_TAG, "Starting BT connecting thread!");
				_bt.start();
			}
			else
			{        	
				Debug.e(TAG, FUNC_TAG, "The device is NOT connected!");
			}
		}
	}
	
	public StringBuffer MsOfDay_convert (int ms)
	{
		int SECOND = 1000;
		int MINUTE = 60 * SECOND;
		int HOUR = 60 * MINUTE;
		int DAY = 24 * HOUR;
	
		StringBuffer text = new StringBuffer("");
		
		if (ms > HOUR) {
		  text.append(ms / HOUR).append(" hours ");
		  ms %= HOUR;
		}
		if (ms > MINUTE) {
		  text.append(ms / MINUTE).append(" minutes ");
		  ms %= MINUTE;
		}
		if (ms > SECOND) {
		  text.append(ms / SECOND).append(" seconds ");
		  ms %= SECOND;
		}
		text.append(ms + " ms");
		System.out.println(text.toString());
		
		return text;
	}
	
	//Bioharness message handler
	final  Handler Newhandler = new Handler()
	{
		final String FUNC_TAG = "BioharnessMessageHandler";
		
    	public void handleMessage(Message msg)
    	{
    		String data = "";
    		
    		switch (msg.what)
    		{
    			case GSR_b:
    			try{
					GSR = msg.getData().getInt("GSR");
	    			data += "\n GSR is "+ GSR;
	    			
	    			HRV = msg.getData().getInt("HRV");
	        		data += "\n HRV is "+ HRV;
	    			
	    			HR = msg.getData().getInt("HR");
	        		data += "\n HR is "+ HR;
	
	    			Posture = msg.getData().getInt("Posture");
	        		data += "\n Posture is "+ Posture;
	
	    			ROG = msg.getData().getInt("ROG");
	        		data += "\n ROG is "+ ROG;
	
	    			BatteryLevel = msg.getData().getInt("BatteryLevel");
	        		data += "\n BatteryLevel is "+ BatteryLevel;
	        			
	    			BreathingRate_conf = msg.getData().getInt("BreathingRate_conf");
	        		data += "\n BreathingRate_conf is "+ BreathingRate_conf;
	
	    			HeartRate_conf = msg.getData().getInt("HeartRate_conf");
	        		data += "\n HeartRate_conf is "+ HeartRate_conf;
	
	    			ROGstatus = msg.getData().getInt("ROGstatus");
	        		data += "\n ROGstatus is "+ ROGstatus;
	
	    			SeqNum = msg.getData().getInt("SeqNum");
	        		data += "\n SeqNum is "+ SeqNum;
	
	    			System_conf = msg.getData().getInt("System_conf");
	        		data += "\n System_conf is "+ System_conf;
	
	    			VersionNumber = msg.getData().getInt("VersionNumber");
	        		data += "\n VersionNumber is "+ VersionNumber;
	        			
	    			Activity = msg.getData().getDouble("Activity");
	        		data += "\n Activity is "+ Activity;
	
	    			Voltage = msg.getData().getDouble("Voltage");
	        		data += "\n Voltage is "+ Voltage;
	
	    			BreathingWaveAmplitude = msg.getData().getDouble("BreathingWaveAmplitude");
	        		data += "\n BreathingWaveAmplitude is "+ BreathingWaveAmplitude;
	
	    			BreathingWaveAmplitudeNoise = msg.getData().getDouble("BreathingWaveAmplitudeNoise");
	        		data += "\n BreathingWaveAmplitudeNoise is "+ BreathingWaveAmplitudeNoise;
	
	    			ECGAmplitude = msg.getData().getDouble("ECGAmplitude");
	        		data += "\n ECGAmplitude is "+ ECGAmplitude;
	
	    			ECGNoise = msg.getData().getDouble("ECGNoise");
	        		data += "\n ECGNoise is "+ ECGNoise;
	
	    			Lateral_AxisAccnMin = msg.getData().getDouble("Lateral_AxisAccnMin");
	        		data += "\n Lateral_AxisAccnMin is "+ Lateral_AxisAccnMin;
	
	    			Lateral_AxisAccnPeak = msg.getData().getDouble("Lateral_AxisAccnPeak");
	        		data += "\n Lateral_AxisAccnPeak is "+ Lateral_AxisAccnPeak;
	
	    			MsofDay = msg.getData().getDouble("MsofDay");
	        		data += "\n MsofDay is "+ MsofDay;
	
	    			PeakAcceleration = msg.getData().getDouble("PeakAcceleration");
	        		data += "\n PeakAcceleration is "+ PeakAcceleration;
	
	    			RespirationRate = msg.getData().getDouble("RespirationRate");
	        		data += "\n RespirationRate is "+ RespirationRate;
	        		
	    			Sagittal_AxisAccnMin = msg.getData().getDouble("Sagittal_AxisAccnMin");
	        		data += "\n Sagittal_AxisAccnMin is "+ Sagittal_AxisAccnMin;
	
	    			Sagittal_AxisAccnPeak = msg.getData().getDouble("Sagittal_AxisAccnPeak");
	        		data += "\n Sagittal_AxisAccnPeak is "+ Sagittal_AxisAccnPeak;
	
	    			Skintemperature = msg.getData().getDouble("Skintemperature");
	        		data += "\n Skintemperature is "+ Skintemperature;
	
	    			Vertical_AxisAccnMin = msg.getData().getDouble("Vertical_AxisAccnMin");
	        		data += "\n Vertical_AxisAccnMin is "+ Vertical_AxisAccnMin;
	
	    			Vertical_AxisAccnPeak = msg.getData().getDouble("Vertical_AxisAccnPeak");
	        		data += "\n Vertical_AxisAccnPeak is "+ Vertical_AxisAccnPeak;
	    			
	        		if(PRINT_DATA)
	        			Debug.d(TAG, FUNC_TAG, data);

//	        		// Edited on 08-05-14
//	        		long d = new Date().getTime();
//	        		int offset = TimeZone.getDefault().getOffset(d);
//	        		d = ((d + offset)/ 86400000l) * 86400000l - offset;
	        		
	        		long d = getCurrentTimeSeconds();
	        		
	        		// End Edited on 08-05-14
	        		
	        		insertDB (GSR,HRV,HR, Posture, ROG, BatteryLevel,
	        	    		 BreathingRate_conf, HeartRate_conf, ROGstatus,  SeqNum,
	        	    		 System_conf, VersionNumber, Activity, Voltage, BreathingWaveAmplitude,
	        	    		 BreathingWaveAmplitudeNoise, ECGAmplitude, ECGNoise, 
	        	    		 Lateral_AxisAccnMin,
	        	    		 Lateral_AxisAccnPeak,
	        	    		 MsofDay,
	        	    		 PeakAcceleration,
	        	    		 RespirationRate,
	        	    		 Sagittal_AxisAccnMin,
	        	    		 Sagittal_AxisAccnPeak,
	        	    		 Skintemperature,
	        	    		 Vertical_AxisAccnMin,
	        	    		 Vertical_AxisAccnPeak,
	        	    		 //(long)((d+MsofDay)/1000)		// Edited on 08-05-14
	        	    		 d
	        				);
	        		
//	        		Intent cgmState = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
//			    	cgmState.putExtra("id", 1);
//			    	cgmState.putExtra("text", " EX: "+String.format("%.1f", Activity)+" | ");
//			    	cgmState.putExtra("color", Color.WHITE);
//					sendBroadcast(cgmState);
	        		
	        		//reportUIChange(Activity, BatteryLevel, (int)((d +MsofDay)/1000));		// Edited on 08-05-14
	        		reportUIChange(Activity, BatteryLevel, (int)d);
    			}
    			catch (Exception e){
    				Debug.i(TAG,FUNC_TAG,"Exception "+ e.getMessage());
    			}
    			break;
    		}
    	}
    };
    
    public void insertDB (int GSR,int HRV,int HR,int Posture,int ROG,int BatteryLevel,
    		int BreathingRate_conf,int HeartRate_conf,int ROGstatus, int SeqNum,
    		int System_conf,int	VersionNumber,double Activity,double Voltage,double BreathingWaveAmplitude,
    		double BreathingWaveAmplitudeNoise,double ECGAmplitude,double ECGNoise, 
    		double Lateral_AxisAccnMin,
    		double Lateral_AxisAccnPeak,
    		double MsofDay,
    		double PeakAcceleration,
    		double RespirationRate,
    		double Sagittal_AxisAccnMin,
    		double Sagittal_AxisAccnPeak,
    		double Skintemperature,
    		double Vertical_AxisAccnMin,
    		double Vertical_AxisAccnPeak,
    		long time
    		)
    {
    	final String FUNC_TAG = "insertDB";
    	
    	if(thisDriver.zephyr != null)
		{
			JSONObject j = new JSONObject();
			try
			{
				j.put("GSR", GSR);
				j.put("HRV", HRV);
				j.put("HR", HR);
				j.put("Posture", Posture);
				j.put("ROG", ROG);
				j.put("BatteryLevel", BatteryLevel);
				j.put("BreathingRate_conf", BreathingRate_conf);
				j.put("HeartRate_conf", HeartRate_conf);
				j.put("ROGstatus", ROGstatus);
				j.put("SeqNum", SeqNum);
				j.put("System_conf", System_conf);
				j.put("VersionNumber", VersionNumber);
				j.put("Activity", Activity);
				j.put("Voltage", Voltage);
				j.put("BreathingWaveAmplitude", BreathingWaveAmplitude);
				j.put("BreathingWaveAmplitudeNoise", BreathingWaveAmplitudeNoise);
				j.put("ECGAmplitude", ECGAmplitude);
				j.put("ECGNoise", ECGNoise);
				j.put("Lateral_AxisAccnMin", Lateral_AxisAccnMin);
				j.put("Lateral_AxisAccnPeak", Lateral_AxisAccnPeak);
				j.put("MsofDay", MsofDay);
				j.put("PeakAcceleration", PeakAcceleration);
				j.put("RespirationRate", RespirationRate);
				j.put("Sagittal_AxisAccnMin", Sagittal_AxisAccnMin);
				j.put("Sagittal_AxisAccnPeak", Sagittal_AxisAccnPeak);
				j.put("Skintemperature", Skintemperature);
				j.put("Vertical_AxisAccnMin", Vertical_AxisAccnMin);
				j.put("Vertical_AxisAccnPeak", Vertical_AxisAccnPeak);
			} 
			catch (JSONException e1) 
			{
				e1.printStackTrace();
			}
			
			if(PRINT_DATA)
				Debug.i(TAG, FUNC_TAG, "JSON: "+j.toString());
			
			// Write HR data to biometricsContentProvider
			ContentValues values = new ContentValues();
			values.put("time", time);
			values.put("json_data", j.toString());
			
		 	try {
		 		getContentResolver().insert(Biometrics.EXERCISE_SENSOR_URI, values);
		 	}
		 	catch (Exception e) {
		 		Debug.e(TAG, FUNC_TAG, e.getMessage());
		 	}
		} 
    }

    private class HRReceiver extends BroadcastReceiver 
    {
    	final String FUNC_TAG = "HRReceiver";
    	
		@Override
		public void onReceive(Context context, Intent intent) {
			try {
				Bundle b = intent.getExtras();
				int temp =b.getInt("hr_rest");
				reportUIChange(temp, 0);
			}
			catch (Exception  e) {
				Debug.i(TAG, FUNC_TAG, e.getMessage());
			}
		}
    }
	
	public long getCurrentTimeSeconds() 
	{
		long currentTimeSeconds = (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970
		return currentTimeSeconds;
	}
	
	public class listDevice
	{
		String address;
		BluetoothDevice dev;
		
		public listDevice(){};
		
		public listDevice(String address, BluetoothDevice dev)
		{
			this.address = address;
			this.dev = dev;
		}
	}
	
	public class Bioharness
	{
		private BluetoothDevice device = null;
		private boolean connected = false;
		
		public Bioharness()
		{
			device = null;
			connected = false;
		}
		
		public Bioharness(BluetoothDevice d)
		{
			device = d;
			connected = false;
		}
		
		public BluetoothDevice getDevice()
		{
			return device;
		}
		
		public boolean isConnected()
		{
			return connected;
		}
	}
}
