/*
 * This device driver will be a simulator style driver that represents what was previously
 * the "Standalone" operating mode.  This driver also is a good representation of a driver
 * like the iDex where both the Pump and CGM 
 */
package edu.virginia.dtc.HR_Driver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Exercise;

import zephyr.android.HxMBT.BTClient;
import zephyr.android.HxMBT.ZephyrProtocol;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class HR_Driver extends Service {

	private static final String TAG = "HR_Driver";
	public static final String PREFS = "Exercise_Controller";
	
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
	private static final int UI2DRIVER_HR_FINISH = 4;
	
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
	
	private BroadcastReceiver  driverUpdateReceiver;

	private Driver thisDriver;

	private final Messenger messengerFromExerciseService = new Messenger(new incomingExerciseHandler());
	private final Messenger messengerFromUI = new Messenger(new incomingUIHandler());

	private Messenger messengerToExerciseService = null;
	private Messenger messengerToUI = null;
	
	private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> reconnect;
	
	private boolean disconnecting = false;
	
	public static List<listDevice> devices;
	public static BluetoothAdapter bt;
	public static BluetoothManager bm;
	public static Hxm hxm;
	
	//Zephyr Driver
	BTClient _bt;
	ZephyrProtocol _protocol;
	NewConnectedListener _NConnListener;
	
	private final int HEART_RATE = 0x100;
	private final int INSTANT_SPEED = 0x101;
	
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
					Debug.w(TAG, FUNC_TAG, "The HxM is connected...no action necessary!");
				}
				else
				{
					Debug.w(TAG, FUNC_TAG, "The HxM is disconnected...sending connection signal!");
					connectHxm();
				}
			}
			else
			{
				Debug.e(TAG, FUNC_TAG, "The BT connection thread is null...starting thread!");
				connectHxm();
			}
		}
	};
	
	private BroadcastReceiver btReceiver = new BroadcastReceiver()
	{
		final String FUNC_TAG = "btReceiver";
		
		@Override
    	public void onReceive(Context context, Intent intent) 
    	{
    		String action = intent.getAction();
    		Debug.d(TAG, FUNC_TAG, "Action: "+action);
       		
    		if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
    		{
    			Debug.i(TAG, FUNC_TAG, "Discovery complete...");
    			HR_DriverUI.scan.setEnabled(true);
    		}
    		if (BluetoothDevice.ACTION_FOUND.equals(action)) 
       		{
    			BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    			
       			Debug.i(TAG, FUNC_TAG, "Device found: "+dev.getName() + " " + dev.getAddress());

       			if(dev.getName() == null || !dev.getName().startsWith("HXM"))
       			{
       				Debug.w(TAG, FUNC_TAG, "Device found is not HxM...");
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
       			
       			if(hxm.getDevice() == null)
       			{
       				Debug.e(TAG, FUNC_TAG, "The HxM BT device is null...");
       				return;
       			}
       			
       			if(dev.getAddress().equals(hxm.getDevice().getAddress()))
       			{
       				hxm.connected = true;
       				
       				Debug.i(TAG, FUNC_TAG, "Adding HR Driver to running misc. driver field...");
       				ContentValues dv = new ContentValues();
       				dv.put("running_misc", "edu.virginia.dtc.HR_Driver.HR_Driver");					
       				getContentResolver().update(Biometrics.HARDWARE_CONFIGURATION_URI, dv, null, null);
       				
       				SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
       				prefs.edit().putString("mac", hxm.getDevice().getAddress()).commit();
       				
       				if(bt.isDiscovering())
       				{
       					Debug.i(TAG, FUNC_TAG, "Canceling discovery: "+bt.cancelDiscovery());
       					HR_DriverUI.scan.setEnabled(true);
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
       				Debug.w(TAG, FUNC_TAG, "This message isn't for us (it's for "+dev.getName()+" "+dev.getAddress()+")");
             }
             if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) 
             {
            	 BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            	
            	 if(hxm.getDevice() == null)
            	 {
    				Debug.e(TAG, FUNC_TAG, "The Hxm BT device is null...");
    				return;
            	 }
            	 
            	 if(dev.getAddress().equals(hxm.getDevice().getAddress()))
            	 {
            		 Debug.e(TAG, FUNC_TAG, "The Hxm was disconnected...");
					
            		 hxm.connected = false;
            		 
            		 if(_bt != null)
            			 _bt.Close();
					
            		 //If disconnecting is true, we are trying to turn it off
            		 if(disconnecting)
            		 {
            			 Debug.i(TAG, FUNC_TAG, "Skipping reconnection since we are intentionally disconnecting...");
            			 
            			 Debug.i(TAG, FUNC_TAG, "Removing Hxm from running misc. driver field...");
            			 ContentValues dv = new ContentValues();
            			 dv.put("running_misc", "");					
            			 getContentResolver().update(Biometrics.HARDWARE_CONFIGURATION_URI, dv, null, null);
            			 
            			 Debug.i(TAG, FUNC_TAG, "Removing device from application saved data...");
            			 SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
            			 prefs.edit().putString("mac", "").commit();
            			 
            			 hxm = new Hxm();
            			 
            			 disconnecting = false;
            		 }
            		 else
            		 {
            			 Debug.i(TAG, FUNC_TAG, "Starting reconnection...");
					
            			 if(reconnect != null)
            				 reconnect.cancel(true);
            			 reconnect = scheduler.scheduleAtFixedRate(recon, 0, 10, TimeUnit.SECONDS);
            		 }
            	 }
            	 else
        			Debug.w(TAG, FUNC_TAG, "This message isn't for us (it's for "+dev.getName()+" "+dev.getAddress()+")");
             }  
             
             reportUIChange();
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
		
		hxm = new Hxm();
		
		devices = new ArrayList<listDevice>();
		
		// Set up a Notification for this Service
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		int icon = R.drawable.ic_launcher;
		CharSequence tickerText = "";
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);
		Context context = getApplicationContext();
		CharSequence contentTitle = "Device Driver";
		CharSequence contentText = "Zephyr HxM";
		Intent notificationIntent = new Intent(this, HR_Driver.class);
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
		hrIntent.putExtra("type", Exercise.DEV_ZEPHYR_HRM);
		hrIntent.putExtra("HRCommand", HR_SERVICE_CMD_INIT);
        startService(hrIntent);
        
        this.registerReceiver(btReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        this.registerReceiver(btReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        this.registerReceiver(btReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        this.registerReceiver(btReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
		String mac = prefs.getString("mac", "");
		
		if(!mac.equalsIgnoreCase(""))
		{
			Debug.d(TAG, FUNC_TAG, "Saved device found: "+mac);
			disconnecting = false;
			
			hxm = new Hxm(bt.getRemoteDevice(mac));
			
			if(reconnect != null)
				reconnect.cancel(true);
				
			Debug.i(TAG, FUNC_TAG, "Starting HxM...");
			reconnect = scheduler.scheduleAtFixedRate(recon, 0, 10, TimeUnit.SECONDS);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String FUNC_TAG = "onStartCommand";
		
		Debug.d(TAG, FUNC_TAG, "Received onStartCommand...");
		
		if(!intent.getBooleanExtra("auto", false))
		{
			Intent uiIntent = new Intent();
			uiIntent.setClassName("edu.virginia.dtc.HR_Driver", "edu.virginia.dtc.HR_Driver.HR_DriverUI");
			uiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(uiIntent);
		}
		
		return 0;
	}

	@Override
	public void onStart(Intent intent, int startId) 
	{
		final String FUNC_TAG = "onStart";
		Debug.d(TAG, FUNC_TAG, "Received onStart...");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		thisDriver.zephyr = null;
		
		unregisterReceiver(driverUpdateReceiver);
	}

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

	class incomingExerciseHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
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

	class incomingUIHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
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
					
					hxm = new Hxm(bt.getRemoteDevice(mac));
					
					if(reconnect != null)
 						reconnect.cancel(true);
 					
 					reconnect = scheduler.scheduleAtFixedRate(recon, 0, 10, TimeUnit.SECONDS);
 					
					reportExerciseServiceChange(dev);
					break;
				case UI2DRIVER_HR_STOP:
					disconnecting = true;
					
					dev = thisDriver.zephyr;
					dev.status = "Stopped";
					dev.running = false;
					dev.connected = false;
					dev.cgm_ant = false;
					
					//This disconnects listener from acting on received messages	
					_bt.removeConnectedEventListener(_NConnListener);
					_bt.Close();
					
					if (!_bt.IsConnected())
					{
						Debug.i(TAG, FUNC_TAG, "HxM Disconnected!");	
					}

					reportExerciseServiceChange(dev);
					break;
				case UI2DRIVER_HR_FINISH:
					Debug.e(TAG, FUNC_TAG, "I have been chosen! I go on to a better place...");
					android.os.Process.killProcess(android.os.Process.myPid());
					break;
			}
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
	
	private void reportUIChange(int hr, int battery, long time) {
		if (messengerToUI != null) {
			try {
				Message msg = Message.obtain(null, DRIVER2UI_NEW_HR);
				msg.arg1 = hr;
				msg.arg2 = battery;
				msg.obj=time;
				
				messengerToUI.send(msg);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

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
	
	public void connectHxm()
	{
		final String FUNC_TAG = "connectHxm";
		
		_bt = new BTClient(bt, hxm.getDevice().getAddress());
		_NConnListener = new NewConnectedListener(HrHandler, HrHandler);
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
	
	final Handler HrHandler = new Handler()
	{
		final String FUNC_TAG = "HrHandler";
    	public void handleMessage(Message msg)
    	{
    		switch (msg.what)
    		{
	    		case HEART_RATE:
	    			String HeartRatetext = msg.getData().getString("HeartRate");
	    			long HR_time = msg.getData().getLong("time");
	    			String Battery_Text = msg.getData().getString("battery");
	    			String InstantSpeedtext = msg.getData().getString("InstantSpeed");
	    			
	    			//Debug.i(TAG, FUNC_TAG, "Heart Rate Info is "+ HeartRatetext);
	    			
	    			if(thisDriver.zephyr != null)
	    			{
						JSONObject j = new JSONObject();
						try
						{
							j.put("hr1", HeartRatetext);
							j.put("instantspeed", InstantSpeedtext);
							j.put("battery", Battery_Text);
						} 
						catch (JSONException e1) {
							e1.printStackTrace();
						}
						
						Debug.i(TAG, FUNC_TAG, "JSON: "+j.toString());
						
						// Write HR data to biometricsContentProvider
						ContentValues values = new ContentValues();
						values.put("time", HR_time);
						values.put("json_data", j.toString());
						
					 	try {
					 		getContentResolver().insert(Biometrics.EXERCISE_SENSOR_URI, values);
					 	}
					 	catch (Exception e) {
					 		Debug.e(TAG, FUNC_TAG, e.getMessage());
					 	}
					 	
					 	reportUIChange(Integer.parseInt(HeartRatetext), Integer.valueOf(Battery_Text), System.currentTimeMillis());
	    			} 
	    			break;
	    		case INSTANT_SPEED:
	    			String speed = msg.getData().getString("InstantSpeed");
	    			Debug.i(TAG, FUNC_TAG, speed);
	    			break;
    		}
    	}
    };
    
    private class HRReceiver extends BroadcastReceiver {
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
	
	public class Hxm
	{
		private BluetoothDevice device = null;
		private boolean connected = false;
		
		public Hxm()
		{
			device = null;
			connected = false;
		}
		
		public Hxm(BluetoothDevice d)
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
