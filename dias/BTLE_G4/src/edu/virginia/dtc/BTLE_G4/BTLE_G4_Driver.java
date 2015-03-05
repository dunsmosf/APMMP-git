/*

 * Driver for reading Dexcom CGM data, based off the Standalone driver
 */
package edu.virginia.dtc.BTLE_G4;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.dexcom.G4DevKit.EstimatedGlucoseRecord;
import com.dexcom.G4DevKit.InsertionTimeRecord;
import com.dexcom.G4DevKit.MeterRecord;
import com.dexcom.G4DevKit.ReceiverUpdateService;
import com.dexcom.G4DevKit.ReceiverUpdateService.ServiceBinder;
import com.dexcom.G4DevKit.ServiceIntents;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;

public class BTLE_G4_Driver extends Service {

	private static final String TAG = "BTLE_G4";

	private static final String RESTART_RECEIVER_SERVICE = "edu.virginia.dtc.RESTART_RECEIVER_SERVICE";
		
	// Messages to UI
	private static final int DRIVER2UI_NULL = 0;
	private static final int DRIVER2UI_UPDATE = 1;
	private static final int DRIVER2UI_FINISH = 5;

	// Messages from UI
	private static final int UI2DRIVER_NULL = 0;
	private static final int UI2DRIVER_REGISTER = 1;
	private static final int UI2DRIVER_CGM_START = 2;
	private static final int UI2DRIVER_CGM_STOP = 3;
	private static final int UI2DRIVER_CGM_NO_ANT = 6;
	private static final int UI2DRIVER_SCAN = 9;
	private static final int UI2DRIVER_CONNECT = 10;

	// Commands to CGM Service
	private static final int DRIVER2CGM_SERVICE_NEW_CGM_DATA = 0;
	private static final int DRIVER2CGM_SERVICE_PARAMETERS = 1;
	private static final int DRIVER2CGM_SERVICE_STATUS_UPDATE = 2;
	private static final int DRIVER2CGM_SERVICE_CALIBRATE_ACK = 3;
  	private static final int CGM_SERVICE_CMD_DISCONNECT = 2;

	// Commands for CGM Driver
	private static final int CGM_SERVICE2DRIVER_NULL = 0;
	private static final int CGM_SERVICE2DRIVER_REGISTER = 1;
	private static final int CGM_SERVICE2DRIVER_CALIBRATE = 2;
	private static final int CGM_SERVICE2DRIVER_DIAGNOSTIC = 3;
	private static final int CGM_SERVICE2DRIVER_DISCONNECT = 4;

	public static final int CGM_SERVICE_CMD_INIT = 3;
	
	// Misc. definitions
	private BroadcastReceiver EGVReceiver, stopDriverReceiver, tickReceiver;
	
	private boolean SHOW_TOAST = true;
	
	private List<EstimatedGlucoseRecord> egvRecordList;
	private List<MeterRecord> meterList;
	
	public SharedPreferences ApplicationPreferences;
	private final boolean m_sendDataWithIntent = true;
	private Intent m_receiverUpdateServiceIntent;
	public static final int RECEIVER_CHECK_INTERVAL = 300; 		// Seconds between checks for receiver's presence
	public static ReceiverUpdateService m_receiverService;

	private Driver drv;
	private WakeLock wakelock;
	
	private Thread sendEGVThread;
	
	private long time;				// Storage for real or simulated time value
	private boolean prevConnected = false;

	private final Messenger messengerFromCgmService = new Messenger(new incomingCgmHandler());
	private final Messenger messengerFromUI = new Messenger(new incomingUIHandler());

	private Messenger messengerToCgmService = null;
	private Messenger messengerToUI = null;

	private Context thisServ;
	
	public static List<listDevice> devices;
	private boolean scanning;
	private Handler handler = new Handler();
	private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private static ScheduledFuture<?> syncTimer;
	
	private BluetoothManager btleManager;
	private BluetoothAdapter btleAdapter;
	
	private final ServiceConnection m_serviceConnection = new ServiceConnection() {
		final String FUNC_TAG = "m_serviceConnection";

		public void onServiceDisconnected(ComponentName name) {
		}

		public void onServiceConnected(ComponentName name, IBinder service) {
			ServiceBinder binder = (ServiceBinder) service;
			m_receiverService = binder.getService();
			Debug.i(TAG, FUNC_TAG, "Receiver service connected - " + m_receiverService);
		}
	};
	
	public BluetoothAdapter.LeScanCallback callBack = new BluetoothAdapter.LeScanCallback() {
		final String FUNC_TAG = "callBack";
		
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) 
		{	
			Debug.i(TAG, FUNC_TAG, "Device found: "+device.getName() + " " + device.getAddress());

			String sub = device.getAddress().substring(0, 8);
			
			Debug.i(TAG, FUNC_TAG, "Substring: "+sub);
			if(device.getName().equalsIgnoreCase("bluesb"))
			{
				boolean exists = false;
				for(listDevice d:devices)
				{
					if(d.address.equalsIgnoreCase(device.getAddress()))
					{
						Debug.i(TAG, FUNC_TAG, "Device already found in the list...");
						exists = true;
					}
				}
				if(!exists)
				{
					Debug.i(TAG, FUNC_TAG, "Adding device..."+device.getName()+" > "+device.getAddress()+" RSSI: "+rssi);
					devices.add(new listDevice(device.getAddress(), device));
				}
			}
			updateUI();
		}
	};

	@Override
	public void onCreate() {
		final String FUNC_TAG = "onCreate";

		super.onCreate();
		
		Debug.i(TAG, FUNC_TAG, "Creation...");
		drv = Driver.getInstance();
		
		thisServ = this;

		// Wakelock
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

		// Set up a Notification for this Service
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		int icon = R.drawable.ic_launcher;
		CharSequence tickerText = "";
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);
		Context context = getApplicationContext();
		CharSequence contentTitle = "Dexcom Local Driver";
		CharSequence contentText = "USBDexcomLocal";
		Intent notificationIntent = new Intent(this, BTLE_G4_Driver.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		final int DRVR_ID = 1;
		//mNotificationManager.notify(DRVR_ID, notification);
		startForeground(DRVR_ID, notification);

		// SET UP DEXCOM INTENTS
		// Get the application preferences
		ApplicationPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		// Start the receiver update service
		m_receiverUpdateServiceIntent = new Intent(BTLE_G4_Driver.this, ReceiverUpdateService.class);
		m_receiverUpdateServiceIntent.putExtra("SEND_DATA_WITH_INTENT", m_sendDataWithIntent);
		startService(m_receiverUpdateServiceIntent);
		
		ApplicationPreferences.edit().putBoolean("serviceStatus", true).commit();
		ApplicationPreferences.edit().putBoolean("sendDataWithIntent", m_sendDataWithIntent).commit();
		
		tickReceiver = new BroadcastReceiver() {
			final String FUNC_TAG = "tickReceiver";
			
			@Override
			public void onReceive(Context context, Intent intent) {
				if(intent.getExtras().getLong("tick") == 1)
				{
					Debug.i(TAG, FUNC_TAG, "TICK: "+intent.getExtras().getLong("tick"));
					
					//Send broadcast
					startSync(0);
				}
			}
			
		};
		this.registerReceiver(tickReceiver, new IntentFilter("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK"));
		
		EGVReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				wakelock.acquire();
				updateDriverDetails("Name:  Dexcom Gen 4\nMin Value:  39\nMax Value:  400");
				
				String action = intent.getAction();
				Debug.i(TAG, FUNC_TAG, "EGVReceiver onReceive "+action);
				
				if (action.equals(ServiceIntents.NEW_EGV_DATA)) 
				{
					Debug.e(TAG, FUNC_TAG, "NEW EGV at time: "+System.currentTimeMillis());
					
					drv.progress = false;
					drv.connected = true;
					
					sendDeviceResult();		//Device is connected and running so check the CGM box in the Devices UI
					
					egvRecordList = (List<EstimatedGlucoseRecord>) m_receiverService.egvRecords;
					
					if (egvRecordList == null) 
					{
						Debug.i(TAG, FUNC_TAG, "EGV RECORD LIST NULL");
						return;
					}
					
					Debug.i(TAG, FUNC_TAG, "New EGV data count=" + egvRecordList.size());
					if (egvRecordList.size() > 0) 
					{
						showToast("RECORDS: "+egvRecordList.size());
						sendEGVData(drv.cgm);
					}
				} 
				else if(action.equals(ServiceIntents.NEW_INSERTION_DATA))
				{
					List<InsertionTimeRecord> insertionList = (List<InsertionTimeRecord>) m_receiverService.insertionRecords;
					
					if (insertionList != null && !insertionList.isEmpty()) {
						for(InsertionTimeRecord r:insertionList)
						{
							Debug.i(TAG, FUNC_TAG, "Insertion Record -------------------------------------------");
							Debug.i(TAG, FUNC_TAG, r.InsertionDisplayTime.toString());
							Debug.i(TAG, FUNC_TAG, r.toDetailedString());
							Debug.i(TAG, FUNC_TAG, "------------------------------------------------------------");
						}
					}
				}
				else if (action.equals(ServiceIntents.NEW_METER_DATA)) 
				{
					Debug.i(TAG, FUNC_TAG, "NEW_METER_DATA broadcast received!");
					
					if(drv.cgm != null)
					{
						Device d  = drv.cgm;
						meterList = (List<MeterRecord>) m_receiverService.meterRecords;
						
						if (meterList == null) {
							Debug.i(TAG, FUNC_TAG, "METER RECORD LIST NULL");
							return;
						}
						
						Debug.i(TAG, FUNC_TAG, "New meter data count=" + meterList.size());
						
						if (meterList.size() > 0) {
							while (d.meter_index < meterList.size()) 
							{
								MeterRecord r = meterList.get(d.meter_index);
								
								Debug.i(TAG, FUNC_TAG, "Meter Record ------------------------------------------------------");
								Debug.i(TAG, FUNC_TAG, r.toDetailedString());
								Debug.i(TAG, FUNC_TAG, "System Time: "+r.SystemTime.getTime()/1000);
								Debug.i(TAG, FUNC_TAG, "Phone Time : "+System.currentTimeMillis()/1000);
								Debug.i(TAG, FUNC_TAG, "-------------------------------------------------------------------");
								
								long cal_time = r.DisplayTime.getTime()/1000;
								
								if(m_receiverService.systemOffset > 0)
								{
									Debug.i(TAG, FUNC_TAG, "Adding the system offset to time...");
									cal_time = (r.SystemTime.getTime()/1000) + m_receiverService.systemOffset;
								}

								if(BTLE_G4_UI.meterList != null)
									BTLE_G4_UI.meterList.add(r.Value + "\nRecord: " + r.RecordNumber);
								
								Debug.i(TAG, FUNC_TAG, "Calibration value "+r.Value+" at "+cal_time+" is being entered");
								Bundle data = new Bundle();
								data.putInt("cal_value", (int)r.Value);
								data.putLong("cal_time", cal_time);
								sendDataMessage(messengerToCgmService, data, DRIVER2CGM_SERVICE_CALIBRATE_ACK, 0, 0);
								
								d.meter_index++;
							}
						}
					}
				} 
				else if (action.equals(ServiceIntents.UNKNOWN_ERROR)) 
				{
					Debug.i(TAG, FUNC_TAG, "UNKNOWN ERROR");
					
					drv.status = "Unkown error!  Please restart the driver!";
					drv.progress = false;
					updateUI();
				} 
				else if (action.equals(ServiceIntents.NO_DATA_ERROR)) 
				{
					Debug.i(TAG, FUNC_TAG, "No data on receiver, waiting until receiver is ready...");
					
					drv.status = "No data on device!";
					drv.progress = false;
					updateUI();
				} 
				else if (action.equals(ServiceIntents.RECEIVER_CONNECTED)) 
				{
					drv.connected = intent.getBooleanExtra("connected", false);
					
//					if(!drv.lowPower)
//						drv.lowPower = m_receiverService.setCurrentUsbPowerLevel(UsbPowerLevel.PwrSuspend);
					
					if (drv.connected)
						drv.status = "Listening for next CGM point...";
					else
					{
						drv.status = "Please connect the receiver!";
						drv.lowPower = false;
					}
					
					drv.progress = false;
					updateUI();
				} 
				else if (action.equals("edu.virginia.dtc.DRIVER_UPDATE"))
				{
					sendDeviceResult();
				}
				else if (action.equals("edu.virginia.dtc.BLEG4_BATTERY"))
				{
					drv.battery = intent.getIntExtra("battery", -1);
					updateUI();
				}
			}
		};
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(ServiceIntents.NEW_EGV_DATA);
		filter.addAction(ServiceIntents.NEW_METER_DATA);
		filter.addAction(RESTART_RECEIVER_SERVICE);
		filter.addAction("edu.virginia.dtc.DRIVER_UPDATE");
		filter.addAction(ServiceIntents.UNKNOWN_ERROR);
		filter.addAction(ServiceIntents.NO_DATA_ERROR);
		filter.addAction(ServiceIntents.RECEIVER_CONNECTED);
		filter.addAction(ServiceIntents.NEW_INSERTION_DATA);
		filter.addAction("edu.virginia.dtc.BLEG4_BATTERY");
		
		registerReceiver(EGVReceiver, filter);
		
		stopDriverReceiver = new BroadcastReceiver(){
			public void onReceive(Context context, Intent intent) {
				if (intent.getStringExtra("package").equals("edu.virginia.dtc.BTLE_G4"))
				{
					Debug.i(TAG, FUNC_TAG, "Finishing...");
					sendDataMessage(messengerToUI, null, DRIVER2UI_FINISH, 0, 0);
					
					Debug.i(TAG, FUNC_TAG,"Disconnecting CGM #" + drv.cgm.my_dev_index);
					Intent cgmIntent = new Intent();
				    cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
					cgmIntent.putExtra("state", drv.my_state_index);
				    cgmIntent.putExtra("dev", drv.cgm.my_dev_index);
					cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_DISCONNECT);
					startService(cgmIntent);
					unbindService(m_serviceConnection);
					stopService(m_receiverUpdateServiceIntent);
					stopSelf();
				}
			}			
		};
		registerReceiver(stopDriverReceiver, new IntentFilter("edu.virginia.dtc.STOP_DRIVER"));
		
		devices = new ArrayList<listDevice>();
		btleManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		btleAdapter = btleManager.getAdapter();
		
		bindService(m_receiverUpdateServiceIntent, m_serviceConnection, Context.BIND_ABOVE_CLIENT);
		Debug.i(TAG, FUNC_TAG, "Bound Receiver Update Service");

		updateDriverDetails("Name: Dexcom Gen 4\nMin Value:  39\nMax Value: 400");
		
		if (drv.cgm != null){
			handler.postDelayed(new Runnable(){
				public void run(){
					initializeCGM();
				}
			}, 1000);
		}
		
		String mac = ApplicationPreferences.getString("mac", "");
		if(!mac.equalsIgnoreCase(""))
		{
			drv.deviceMac = mac;
			
			Debug.i(TAG, FUNC_TAG, "MAC address found!  Trying to connect to stored device...");
			
			handler.postDelayed(new Runnable(){
				public void run()
				{
					Intent i = new Intent("com.dexcom.g4devkit.action.UPDATE_MAC");
					i.putExtra("mac", drv.deviceMac);
					sendBroadcast(i);
					
					startSync(0);
				}
			}, 3000);
		}
		else
		{
			drv.deviceMac = "";
			Debug.i(TAG, FUNC_TAG, "There is no stored device, starting discovery!");
			discoverLeDevices(true);
		}
	}
	
	public void sendDeviceResult()
	{
		Debug.i(TAG, "sendDeviceResult", "Sending device change...");
		int num = 0;
		
		Intent intentResult = new Intent("edu.virginia.dtc.DEVICE_RESULT");
		if(drv.connected)
			num = 1;
		intentResult.putExtra("cgms", num);
		intentResult.putExtra("name", "BTLE_G4");
		sendBroadcast(intentResult);
	}

	public void discoverLeDevices(final boolean enable)
	{
		final String FUNC_TAG = "discoverLeDevices";
		
		scanning = false;
		
		devices.clear();
		
		if(!scanning && enable)
		{
			handler.postDelayed(new Runnable()
			{
				public void run() 
				{
					scanning = false;
					btleAdapter.stopLeScan(callBack);
				}
			}, 10000);
			
			scanning = true;
			btleAdapter.startLeScan(callBack);
		}
		else
		{
			if(scanning)
				Debug.i(TAG, FUNC_TAG, "BTLE is already scanning...");
			scanning = false;
			btleAdapter.stopLeScan(callBack);
		}
	}
	
	public void sendEGVData(final Device d) {
		final String FUNC_TAG = "sendEGVData";

		if (sendEGVThread == null || !sendEGVThread.isAlive()) 
		{
			sendEGVThread = new Thread() 
			{
				public void run() 
				{
					double cgmValue = 0;
					long cgmTime = 0;
					
					Debug.i(TAG, FUNC_TAG, "EGV device data index=" + d.data_index + " Record list size: "+egvRecordList.size());
					showToast("EGV device data index=" + d.data_index);
					
					Debug.i(TAG, FUNC_TAG, "System Offset: "+m_receiverService.systemOffset);
					
					while (d.data_index < egvRecordList.size()) 
					{
						EstimatedGlucoseRecord record = egvRecordList.get(d.data_index);
						
						Debug.i(TAG, FUNC_TAG, "EGV Record ------------------------------------------------------");
						
						Debug.i(TAG, FUNC_TAG, record.toDetailedString());
						Debug.i(TAG, FUNC_TAG, "DisplayOnly=" + record.IsDisplayOnly + " Value="+ record.Value + " Special=" + record.SpecialValue);
						
						Debug.i(TAG, FUNC_TAG, "R_SYS: "+(record.SystemTime.getTime()/1000));
						Debug.i(TAG, FUNC_TAG, "P_SYS: "+System.currentTimeMillis()/1000);
						
						Debug.i(TAG, FUNC_TAG, "-----------------------------------------------------------------");
						
						// Return new CGM data from Dexcom to service
						if(m_receiverService.systemOffsetReady)
							time = (record.SystemTime.getTime()/1000) + m_receiverService.systemOffset;
						else
							time = record.DisplayTime.getTime()/1000;
						
						cgmValue = record.Value;
						int trend = 3;
						
						switch (record.TrendArrow) 
						{
							case DoubleUp:
							case SingleUp:
								trend = 2;
								break;
							case DoubleDown:
							case SingleDown:
								trend = -2;
								break;
							case Flat:
								trend = 0;
								break;
							case FortyFiveDown:
								trend = -1;
								break;
							case FortyFiveUp:
								trend = 1;
								break;
							case None:
							case NotComputable:
							case RateOutOfRange:
							default:
								trend = 5;
								break;
						}
						
						//If the sensor is inserted and the current time is less than the insertion time plus 2 hours, its warming up
						d.state = CGM.CGM_NORMAL;
						
						if(record.SpecialValue != null)
						{
							if(record.SpecialValue.equalsIgnoreCase("sensorOutOfCal"))
							{
								d.state = CGM.CGM_WARMUP;
								Debug.i(TAG, FUNC_TAG, "Warmup mode");
							}
							else if(record.SpecialValue.equalsIgnoreCase("Aberration3"))
							{
								d.state = CGM.CGM_NOISE;
								Debug.i(TAG, FUNC_TAG, "Abb 3");
							}
							else if(record.SpecialValue.equalsIgnoreCase("Aberration2"))
							{
								d.state = CGM.CGM_DATA_ERROR;
								Debug.i(TAG, FUNC_TAG, "Abb 2");
							}
							else
								Debug.i(TAG, FUNC_TAG, "None");
						}
						
						Debug.i(TAG, FUNC_TAG, "CGM State: "+d.state);
						
						Bundle data = new Bundle();
						data.putLong("time", time);
						data.putDouble("cgmValue", (double) cgmValue);
						data.putInt("trend", trend);
						data.putInt("minToNextCalibration", 720);
						data.putInt("calibrationType", 0);
						data.putInt("cgm_state", d.state);

						//Only store the last time
						cgmTime = time;
						
						if(d.state == CGM.CGM_NORMAL)
						{
							ContentValues dv = new ContentValues();
							Debug.e(TAG, FUNC_TAG, "Adding to running CGM DB value!");
							dv.put("running_cgm", "edu.virginia.dtc.BTLE_G4.BTLE_G4_Driver");					
							
							Debug.e(TAG, FUNC_TAG, "Running CGM updated in Hardware Configuration table!");
							getContentResolver().update(Biometrics.HARDWARE_CONFIGURATION_URI, dv, null, null);
						}
						
						if (messengerToCgmService == null) 
						{
							Debug.i(TAG, FUNC_TAG, "messengerToCgmService null");
						} 
						else if(!record.IsDisplayOnly)
							sendDataMessage(messengerToCgmService, data, DRIVER2CGM_SERVICE_NEW_CGM_DATA, drv.my_state_index, d.my_dev_index);
						
						log(FUNC_TAG, "Sent CGM value=" + cgmValue + " time=" + DateFormat.format("MM/dd hh:mm", new Date(data.getLong("time") * 1000)));
						
						if(BTLE_G4_UI.cgmList != null)
							BTLE_G4_UI.cgmList.add(DateFormat.format("hh:mm:ss", new Date(time * 1000)) + "\n"
								+ cgmValue 
								+ ((record.IsDisplayOnly) ? " -- Display only" : "")
								+ ((record.SpecialValue != null) ? " -- " + record.SpecialValue : ""));
						
						d.data_index++;
					}
					
					//adjustTiming(cgmTime);
				}
			};
			sendEGVThread.run();
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String FUNC_TAG = "onStartCommand";

		Debug.i(TAG, FUNC_TAG, "Received onStartCommand...");
		
		if(!intent.getBooleanExtra("auto", false))
		{
			Intent uiIntent = new Intent();
			uiIntent.setClassName("edu.virginia.dtc.BTLE_G4", "edu.virginia.dtc.BTLE_G4.BTLE_G4_UI");
			uiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(uiIntent);
		}
		
		return 0;
	}

	@Override
	public void onStart(Intent intent, int startId) {
		final String FUNC_TAG = "onStart";
		Debug.i(TAG, FUNC_TAG, "Received onStart...");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		stopSync();
		
		unregisterReceiver(EGVReceiver);
		unregisterReceiver(stopDriverReceiver);
		unbindService(m_serviceConnection);
		updateDriverDetails("");
	}

	// onBind supports two connections due to the dual nature of the standalone driver (these are filtered based on connection intent)
	@Override
	public IBinder onBind(Intent arg0) {
		final String FUNC_TAG = "onBind";

		Debug.i(TAG, FUNC_TAG, arg0.getAction());
		if (arg0.getAction().equalsIgnoreCase(Driver.CGM_INTENT))
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
			switch (msg.what) 
			{
				case CGM_SERVICE2DRIVER_NULL:
					break;
				case CGM_SERVICE2DRIVER_REGISTER:
					messengerToCgmService = msg.replyTo;
					Debug.i(TAG, FUNC_TAG, "CGM Service replyTo registered, sending parameters...");
	
					// Gather state and device indexes, these values come from the
					// CGM Service so device creation and deletion (see CGM_DRIVER_DISCONNECT) 
					// are started at the CGM Service and filter down here
					drv.my_state_index = msg.arg1;
	
					ContentValues cgmValues = new ContentValues();
					cgmValues.put("min_cgm", 40);
					cgmValues.put("max_cgm", 400);
					cgmValues.put("phone_calibration", 0);			//Indicates whether the calibration data is entered on the phone or the CGM device
					
					getContentResolver().update(Biometrics.CGM_DETAILS_URI, cgmValues, null, null);
	
					sendDataMessage(messengerToCgmService, null, DRIVER2CGM_SERVICE_PARAMETERS, msg.arg1, msg.arg2);
	
					drv.registered = true;
					drv.status = "Registered with CGM Service";
					drv.progress = false;
					updateUI();
					break;
				case CGM_SERVICE2DRIVER_CALIBRATE:
					data = msg.getData();
					int BG = data.getInt("calibration_value");
	
					Debug.i(TAG, FUNC_TAG, "Calibration received: " + BG);
					break;
				case CGM_SERVICE2DRIVER_DISCONNECT:
					Debug.i(TAG, FUNC_TAG, "Removing device-" + msg.arg2);
					break;
			}
		}
	}

	class incomingUIHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			final String FUNC_TAG = "handleMessage";

			switch (msg.what) 
			{
				case UI2DRIVER_NULL:
					break;
				case UI2DRIVER_REGISTER:
					messengerToUI = msg.replyTo;
					break;
				case UI2DRIVER_SCAN:
					Debug.i(TAG, FUNC_TAG, "Cancelling receiver service and clearing old MAC address...");
					
					ApplicationPreferences.edit().putString("mac", null).commit();
					
					//Cancel update timer if running
					stopSync();
					
					discoverLeDevices(true);
					break;
				case UI2DRIVER_CONNECT:
					Bundle b = msg.getData();
					int index = b.getInt("index");
					listDevice d = devices.get(index);
					Debug.i(TAG, FUNC_TAG, "Connecting to device: "+d.dev.getAddress());
					
					Intent i = new Intent("com.dexcom.g4devkit.action.UPDATE_MAC");
					i.putExtra("mac", d.dev.getAddress());
					sendBroadcast(i);
					
					drv.deviceMac = d.dev.getAddress();
					ApplicationPreferences.edit().putString("mac", d.dev.getAddress()).commit();
					
					startSync(0);
					break;
			}
		}
	}

	private void sendDataMessage(Messenger messenger, Bundle bundle, int what, int arg1, int arg2) {
		if (messenger == null) // error handling
			return;
		
		Message msg = Message.obtain(null, what);
		msg.arg1 = arg1;
		msg.arg2 = arg2;
		msg.setData(bundle);

		try {
			messenger.send(msg);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	public void updateUI()
	{
		sendDataMessage(messengerToUI, null, DRIVER2UI_UPDATE, 0, 0);
	}
	
	public void adjustTiming(long cgmTime)
	{
		final String FUNC_TAG = "adjustTiming";

		final int RECORD_BTLE_DELAY = 50;
		final int THRESHOLD = 60;
		
		int secAgo = (int)((System.currentTimeMillis()/1000) - cgmTime);
		int timeDelay = 0;
		boolean skip = false;
	   		
   		Debug.w(TAG, FUNC_TAG, "Value was generated: "+secAgo+" seconds ago!");
   	
   		if(secAgo < RECORD_BTLE_DELAY)
   		{
   			Debug.i(TAG, FUNC_TAG, "Less than BTLE delay!");
   			timeDelay = RECORD_BTLE_DELAY - secAgo;
   		}
   		if(secAgo < THRESHOLD)
   		{
   			Debug.i(TAG, FUNC_TAG, "Less than THRESHOLD, skipping since period is fine!");
   			timeDelay = 0;
   			skip = true;
   		}
   		else if(secAgo < RECEIVER_CHECK_INTERVAL)				//Greater than zero but less than 300
   		{
   			Debug.i(TAG, FUNC_TAG, "Less than 300");
   			timeDelay = RECEIVER_CHECK_INTERVAL - secAgo;		//Get the remaining time then try to compensate for BTLE
   		}
   		else													//Greater than or equal to 300 seconds
   		{
   			Debug.i(TAG, FUNC_TAG, "Greater than 300");
   			timeDelay = 0;		//(RECEIVER_CHECK_INTERVAL - Math.abs(RECEIVER_CHECK_INTERVAL - secAgo)) - RECORD_BTLE_DELAY;
   		}
   		
   		if(timeDelay < 0)
   		{
   			Debug.i(TAG, FUNC_TAG, "Setting timeDelay to zero since its negative!");
   			timeDelay = 0;
   		}
   		
   		Debug.i(TAG, FUNC_TAG, "Modifying interval to start in "+timeDelay+" seconds!");

   		if(!skip)
   			startSync(timeDelay);
   		else
   			Debug.i(TAG, FUNC_TAG, "Don't mess with timing since its close enough in sync!");
   		
	}
	
	private void stopSync()
	{
		Debug.i(TAG, "stopSync", "Stopping sync timer!");
		
		if(syncTimer != null)
			syncTimer.cancel(true);
	}
	
	private void startSync(int timeDelay)
	{
		final String FUNC_TAG = "startSync";
		
		Debug.i(TAG, FUNC_TAG, "Adjust timing event firing!");
		Intent updateReceiverIntent = new Intent(ServiceIntents.UPDATE_RECEIVER_DATA);
		thisServ.sendBroadcast(updateReceiverIntent);
		
		/*
		Debug.i(TAG, FUNC_TAG, "Scheduling update in "+timeDelay+"s then 300s after!");
		
		stopSync();
			
   		syncTimer = scheduler.scheduleAtFixedRate(new Runnable(){
				public void run() {
					Debug.i(TAG, FUNC_TAG, "Adjust timing event firing!");
					Intent updateReceiverIntent = new Intent(ServiceIntents.UPDATE_RECEIVER_DATA);
					thisServ.sendBroadcast(updateReceiverIntent);
					
				}
		}, timeDelay, RECEIVER_CHECK_INTERVAL, TimeUnit.SECONDS);
		*/
	}
	
	public void showToast(String message)
	{
		if(SHOW_TOAST)
			Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
	}
	
	public void initializeCGM(){
		if (drv.cgm == null)
			return;
		Intent cgmIntent = new Intent();
		cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
		cgmIntent.putExtra("reset", true);
		cgmIntent.putExtra("driver_intent", Driver.CGM_INTENT);
		cgmIntent.putExtra("driver_name", Driver.DRIVER_NAME);
		cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_INIT);
		startService(cgmIntent);	
	}

	public void updateDriverDetails(String details) {
		final String FUNC_TAG = "updateDriverDetails";

		ContentValues values = new ContentValues();
		values.put("details", details);
		try{
			getContentResolver().update(Biometrics.CGM_DETAILS_URI, values, null, null);
		}
		catch (Exception e)
		{
			Debug.e(TAG, FUNC_TAG, e.getMessage());
		}
	}
	
	public void log(String FUNC_TAG, String log){
		Debug.i(TAG, FUNC_TAG, log);
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
}
