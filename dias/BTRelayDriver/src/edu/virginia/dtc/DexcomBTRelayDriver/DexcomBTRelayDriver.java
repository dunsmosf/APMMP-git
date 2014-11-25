/*
- * Driver for reading Dexcom data from a receiver into a relay phone (i.e. Xperia Active)
  * This data is then sent via BT to the CPMP
*/

package edu.virginia.dtc.DexcomBTRelayDriver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.usb.UsbManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;

import com.dexcom.G4DevKit.EstimatedGlucoseRecord;
import com.dexcom.G4DevKit.InsertionTimeRecord;
import com.dexcom.G4DevKit.MeterRecord;
import com.dexcom.G4DevKit.ReceiverUpdateService;
import com.dexcom.G4DevKit.ReceiverUpdateService.ServiceBinder;
import com.dexcom.G4DevKit.ServiceIntents;
import com.dexcom.G4DevKit.enums.UsbPowerLevel;

import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;

public class DexcomBTRelayDriver extends Service {

	private static final String TAG = "DexcomBTRelayDriver";

	private static final String ACTIVATE_USB = "edu.virginia.dtc.ACTIVATE_USB";
	private static final String DEACTIVATE_USB = "edu.virginia.dtc.DEACTIVATE_USB";
	private static final String RESTART_RECEIVER_SERVICE = "edu.virginia.dtc.RESTART_RECEIVER_SERVICE";
	private BroadcastReceiver EGVReceiver;
	
	//Command values that come from BT strings
	private static final int CGM_DATA = 0;
	private static final int CALIB_DATA = 1;
	
	private static final boolean LOGGING = true;
	private static final long WARMUP_IN_SEC = 7200;
	
	// CGM states of operation
	public static final int CGM_NORMAL = 0;
	public static final int CGM_DATA_ERROR = 1;
	public static final int CGM_NOT_ACTIVE = 2;
	public static final int CGM_NONE = 3;
	public static final int CGM_NOISE = 4;
	public static final int CGM_WARMUP = 5;
	public static final int CGM_CALIBRATION_NEEDED = 6;
	public static final int CGM_DUAL_CALIBRATION_NEEDED = 7;
	public static final int CGM_CAL_LOW = 8;
	public static final int CGM_CAL_HIGH = 9;
	public static final int CGM_SENSOR_FAILED = 10;
	
	// Messages to UI
	private static final int DRIVER2UI_NULL = 0;
	private static final int DRIVER2UI_DEV_STATUS = 1;
	private static final int DRIVER2UI_UPDATE_STATUS = 4;
	private static final int DRIVER2UI_FINISH = 5;

	// Messages from UI
	private static final int UI2DRIVER_NULL = 0;
	private static final int UI2DRIVER_REGISTER = 1;
	private static final int UI2DRIVER_CGM_START = 2;
	private static final int UI2DRIVER_CGM_STOP = 3;
	private static final int UI2DRIVER_CGM_NO_ANT = 6;
	private static final int UI2DRIVER_RESTART_RECEIVER_SERVICE = 9;

	// Commands to CGM Service
	private static final int DRIVER2CGM_SERVICE_NEW_CGM_DATA = 0;
	private static final int DRIVER2CGM_SERVICE_PARAMETERS = 1;
	private static final int DRIVER2CGM_SERVICE_STATUS_UPDATE = 2;
	private static final int DRIVER2CGM_SERVICE_CMD_INIT = 3;
  	private static final int CGM_SERVICE_CMD_DISCONNECT = 2;

	// Commands for CGM Driver
	private static final int CGM_SERVICE2DRIVER_NULL = 0;
	private static final int CGM_SERVICE2DRIVER_REGISTER = 1;
	private static final int CGM_SERVICE2DRIVER_CALIBRATE = 2;
	private static final int CGM_SERVICE2DRIVER_DIAGNOSTIC = 3;
	private static final int CGM_SERVICE2DRIVER_DISCONNECT = 4;
	
	// Commands for DiAsService to calibrate device
	public static final int DIAS_SERVICE_COMMAND_CALIBRATE_SENSOR = 16;

	// Misc. definitions	
	private List<EstimatedGlucoseRecord> egvRecordList;
	private List<MeterRecord> meterList;
	public SharedPreferences ApplicationPreferences;
	private final boolean m_sendDataWithIntent = true;
	private Intent m_receiverUpdateServiceIntent;
	private ReceiverUpdateTools m_receiverUpdateTools;
	public static final int RECEIVER_CHECK_INTERVAL = 10; // seconds between checks for receiver's presence
	
	private Timer timer;
	private TimerTask updater;
	
	public static final boolean ENABLE_BATTERY_LOOP = false;
	public static final int UPDATE_INTERVAL = 600; // seconds between each check for new data from the Dexcom
	public static boolean updateScheduled = false;
	private long onTimeMillis, offTimeMillis;

	private Intent m_getEGVDataIntent;
	private ReceiverUpdateService m_receiverService;

	private Driver thisDriver;
	private Device device;
	private final int NUM_PAST_DEXCOM_RECORDS = 2; // number of past records to pass to CGM service from dexcom when first connected
	private boolean isFirstSetOfData = true;
	private WakeLock wakelock;
	public PendingIntent activateUSBIntent;
	public PendingIntent deactivateUSBIntent;
//	public static final int USB_ON_INTERVAL = 0; // secs for USB to be on before turning it off, 0 for instant off
	public static final int TURN_OFF_USB_DELAY_SECS = 20; // secs before USB is turned off
	public static String unknownErrorTime = "";
	private AlarmManager alarmManager;
	private Thread sendEGVThread;
	
	private long time;				// Storage for real or simulated time value
	private boolean realTime;
	private boolean prevConnected = false;
	
	private final Messenger messengerFromUI = new Messenger(new incomingUIHandler());
	private final Handler selfHandler = new Handler();

	private Messenger messengerToUI = null;
	
	private String logFile;
	private boolean logReady = false;
	private boolean lowPower = false;
	
	private InterfaceData data;
	private int btState = -1;
	
	private int batteryLevel = 100;
	private int oldBatteryLevel = 100;
	
	private long lastValidCalibTime = -1;
	private long lastValidCgmTime = -1;

	private final ServiceConnection m_serviceConnection = new ServiceConnection() {
		public void onServiceDisconnected(ComponentName name) {
		}

		public void onServiceConnected(ComponentName name, IBinder service) {
			final String FUNC_TAG = "onServiceConnected";

			ServiceBinder binder = (ServiceBinder) service;
			m_receiverService = binder.getService();
			Debug.i(TAG, FUNC_TAG, "Receiver service connected - " + m_receiverService);
		}
	};

	@Override
	public void onCreate() {
		final String FUNC_TAG = "onCreate";

		super.onCreate();
		
		data = InterfaceData.getInstance();
		
		//Turn on the BT radio if it isn't already
		if(!data.bt.isEnabled())
			data.bt.enable();
		
		//Setup the BT device for CGM
		if(InterfaceData.remoteCgmBt == null)
		{
    		InterfaceData.remoteCgmBt = new BluetoothConn(data.bt, InterfaceData.CGM_UUID, "CgmBT", InterfaceData.cgmMessages, InterfaceData.cgmLock, true);
		}
		
		Debug.i(TAG, FUNC_TAG, "Creation...");
		thisDriver = Driver.getInstance();
		
		activateUSBIntent = PendingIntent.getBroadcast(this.getApplicationContext(), 0, new Intent(ACTIVATE_USB), 0);
		deactivateUSBIntent = PendingIntent.getBroadcast(this.getApplicationContext(), 0, new Intent(DEACTIVATE_USB), 0);

		SimpleDateFormat sdfDate = new SimpleDateFormat("MM-dd-yyyy");
	    Date now = new Date();
	    String strDate = sdfDate.format(now);
		String path = "DexcomBTRelayLogFile_"+strDate+".txt";

		logFile = path;
		lowPower = false;
		
		// Make sure USB is on
		turnOnUSB();
		
		createLogFile();

		// Wakelock
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		alarmManager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);

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
		Intent notificationIntent = new Intent(this, DexcomBTRelayDriver.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		final int DRVR_ID = 1;
		mNotificationManager.notify(DRVR_ID, notification);
		startForeground(DRVR_ID, notification);

		// SET UP DEXCOM INTENTS
		
		// Get the application preferences
		ApplicationPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		int currentUpdateIntervalSetting = ApplicationPreferences.getInt("serviceUpdateInterval", RECEIVER_CHECK_INTERVAL);

		// Repeating intent to check for new receiver data
		m_receiverUpdateTools = new ReceiverUpdateTools(this);
		m_receiverUpdateTools.setPeriodicUpdate(this, currentUpdateIntervalSetting);

		// Start the receiver update service
		m_receiverUpdateServiceIntent = new Intent(DexcomBTRelayDriver.this, ReceiverUpdateService.class);
		m_receiverUpdateServiceIntent.putExtra("SEND_DATA_WITH_INTENT", m_sendDataWithIntent);
		startService(m_receiverUpdateServiceIntent);
		
		ApplicationPreferences.edit().putBoolean("serviceStatus", true).commit();
		ApplicationPreferences.edit().putBoolean("sendDataWithIntent", m_sendDataWithIntent).commit();
		
		String mac = ApplicationPreferences.getString("mac", ""); 
		
		//If there is a MAC address stored, check it and re-connect if possible
		if(!mac.equalsIgnoreCase("") && BluetoothAdapter.checkBluetoothAddress(mac))
		{
			Debug.i(TAG, FUNC_TAG, "Found existing MAC address...Connecting!");
			BluetoothDevice dev = data.bt.getRemoteDevice(mac);
			InterfaceData.remoteCgmBt.connect(dev, true);
		}
		else
			Debug.i(TAG, FUNC_TAG, "No previous MAC address found!");
		
		//Restore settings if this phone has to be restarted
		lastValidCalibTime = ApplicationPreferences.getLong("lastValidCalibTime", System.currentTimeMillis()/1000);  //The defaults for these are always set to the current time if they are blank
		lastValidCgmTime = ApplicationPreferences.getLong("lastValidCgmTime", System.currentTimeMillis()/1000);	

		EGVReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				wakelock.acquire();
				
				String action = intent.getAction();
				Debug.i(TAG, FUNC_TAG, "EGVReceiver onReceive "+action);
				
				if (action.equals(ServiceIntents.NEW_EGV_DATA)) 
				{
//					if(!lowPower)
//					{
						boolean success = m_receiverService.setCurrentUsbPowerLevel(UsbPowerLevel.PwrSuspend);
						if(success)
						{
							Debug.i(TAG, FUNC_TAG, "Power level set to standby!");
							lowPower = true;
						}
						else
						{
							Debug.i(TAG, FUNC_TAG, "Power level could not be changed!");
							lowPower = false;
						}
//					}
					
					egvRecordList = (List<EstimatedGlucoseRecord>) m_receiverService.egvRecords;
					offTimeMillis = System.currentTimeMillis();
					if (egvRecordList == null) {
						Debug.i(TAG, FUNC_TAG, "EGV RECORD LIST NULL");
						return;
					}
					Debug.i(TAG, FUNC_TAG, "New EGV data count=" + egvRecordList.size());
					if (egvRecordList.size() > 0) {
						realTime = true;
						sendEGVData(thisDriver.cgm);
					}
				} 
				else if(action.equals(ServiceIntents.NEW_INSERTION_DATA))
				{
					List<InsertionTimeRecord> insertionList = (List<InsertionTimeRecord>) m_receiverService.insertionRecords;
					
					if (insertionList != null && !insertionList.isEmpty()) {
						for(InsertionTimeRecord r:insertionList)
						{
							Debug.i("EGVReceiver", FUNC_TAG, "Insertion Record -------------------------------------------");
							Debug.i("EGVReceiver", FUNC_TAG, r.InsertionDisplayTime.toString());
							Debug.i("EGVReceiver", FUNC_TAG, r.toDetailedString());
							Debug.i("EGVReceiver", FUNC_TAG, "------------------------------------------------------------");
						}
					}
				}
				else if (action.equals(ServiceIntents.NEW_METER_DATA)) 
				{
					Debug.i("EGVReceiver", FUNC_TAG, "NEW_METER_DATA broadcast received!");
					
					Device d = thisDriver.cgm;
					
					meterList = (List<MeterRecord>) m_receiverService.meterRecords;
					
					if (meterList == null) {
						Debug.i("EGVReceiver", FUNC_TAG, "METER RECORD LIST NULL");
						return;
					}
					
					Debug.i("EGVReceiver", FUNC_TAG, "New meter data count=" + meterList.size());
					
			    	if(lastValidCalibTime < 0)		//A -1 signifies that there has been no data collected
			    	{
			    		//Set the last valid time to current time if its blank
			    		lastValidCalibTime = System.currentTimeMillis()/1000;
			    		ApplicationPreferences.edit().putLong("lastValidCalibTime", lastValidCalibTime).commit();
			    		Debug.i("EGVReceiver", FUNC_TAG, "There is no calibration data, so the last valid time is now (probably initialization)");
			    	}
					
					if (meterList.size() > 0) {
						while (d.meter_index < meterList.size()) 
						{
							MeterRecord r = meterList.get(d.meter_index);
							//USBDexcomLocalUI.meterList.add(r.Value + "\nRecord: " + r.RecordNumber);
							
							Debug.i("EGVReceiver", FUNC_TAG, "Meter Record ------------------------------------------------------");
							Debug.i("EGVReceiver", FUNC_TAG, r.toDetailedString());
							Debug.i("EGVReceiver", FUNC_TAG, "-------------------------------------------------------------------");
							
							if((r.DisplayTime.getTime()/1000) > lastValidCalibTime)		//If the last valid time is -1 it will be true by default so it should need to be initialized
							{
								/*
								Debug.i("EGVReceiver", FUNC_TAG, "Calibration value "+r.Value+" is being entered");
								Intent intent1 = new Intent();
						 		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
						 		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_CALIBRATE_SENSOR);
						 		intent1.putExtra("BG", (double)(r.Value));
						 		startService(intent1);
						 		*/
						 		
						 		/******************************************************************************************************************/
								//DATA IS SENT VIA BT HERE******************************************************************************************
								/******************************************************************************************************************/
								
								//Generate output string and send data to the other phone via bluetooth (2 values long, first is the command)
								String out = String.format("%d,%f", CALIB_DATA, (double)r.Value);
								sendBtString(out);
								
								//Update the last valid time so we don't resend data to the phone
								lastValidCalibTime = r.DisplayTime.getTime()/1000;
					    		ApplicationPreferences.edit().putLong("lastValidCalibTime", lastValidCalibTime).commit();
							}
							else
							{
								Debug.i("EGVReceiver", FUNC_TAG, "Meter value "+r.Value+" is too old to add");
							}
							
							d.meter_index++;
						}
					}
				} 
				//No longer active b/c the value isn't used and I think it picks up the broadcast for BT-LE
				else if (action.equals("edu.virginia.dtc.ACTION.USE_PERMISSION")) 
				{
					boolean allowed = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
					Debug.i(TAG, FUNC_TAG, "ACTION.USE_PERMISSIONS received=" + allowed);
				} 
				else if (action.equals(ACTIVATE_USB)) 
				{
					Debug.i(TAG, FUNC_TAG, "ACTIVATE_USB = " + ENABLE_BATTERY_LOOP);
					if (ENABLE_BATTERY_LOOP) {
						turnOnUSB();
						onTimeMillis = System.currentTimeMillis();
						m_receiverUpdateTools.setPeriodicUpdate(DexcomBTRelayDriver.this, RECEIVER_CHECK_INTERVAL);
					}
				} 
				else if (action.equals(DEACTIVATE_USB)) 
				{
					Debug.i(TAG, FUNC_TAG, "DEACTIVATE_USB = " + ENABLE_BATTERY_LOOP);
					if (ENABLE_BATTERY_LOOP) {
						turnOffUSB();
					}
				} 
				else if (action.equals(ServiceIntents.UNKNOWN_ERROR)) 
				{
					Debug.i(TAG, FUNC_TAG, "UNKNOWN ERROR");
					Bundle statusBundle;
					statusBundle = new Bundle();
					statusBundle.putString("status", "UNKNOWN ERROR: Connect Receiver");
					statusBundle.putInt("checkbox", 1);
					statusBundle.putBoolean("checked", false);
					statusBundle.putBoolean("progressbar", false);
					statusBundle.putInt("color", Color.RED);
					if (messengerToUI != null)
						sendDataMessage(messengerToUI, statusBundle, DRIVER2UI_UPDATE_STATUS, 0, 0);
				} 
				else if (action.equals(ServiceIntents.NO_DATA_ERROR)) 
				{
					Debug.i(TAG, FUNC_TAG, "No data on receiver, waiting until receiver is ready...");
					Bundle statusBundle;
					statusBundle = new Bundle();
					statusBundle.putString("status", "NO DATA: Waiting for warmup...");
					statusBundle.putInt("checkbox", 1);
					statusBundle.putBoolean("checked", true);
					statusBundle.putBoolean("progressbar", false);
					statusBundle.putInt("color", Color.YELLOW);
					if (messengerToUI != null)
						sendDataMessage(messengerToUI, statusBundle, DRIVER2UI_UPDATE_STATUS, 0, 0);
					
				} 
				else if (action.equals(ServiceIntents.RECEIVER_CONNECTED)) 
				{
					boolean connected = intent.getBooleanExtra("connected", false);
					Bundle statusBundle = new Bundle();
					
					//Check the status of the BT connection here too
					if(btState != InterfaceData.remoteCgmBt.getState() && (InterfaceData.remoteCgmBt != null))
					{
						Debug.i(TAG, FUNC_TAG, "CGM state change!");
						btState = InterfaceData.remoteCgmBt.getState();
						reportUIChange();
					}
					
					if (connected)
						statusBundle.putString("status", "Awaiting next data point...");
					else
						statusBundle.putString("status", "Connect receiver now");
					
					statusBundle.putBoolean("progressbar", false);
					statusBundle.putInt("checkbox", 1);
					statusBundle.putBoolean("checked", connected);
					sendDataMessage(messengerToUI, statusBundle, DRIVER2UI_UPDATE_STATUS, 0, 0);
				} 
				else if(intent.getAction().equalsIgnoreCase("android.intent.action.BATTERY_CHANGED"))
        		{
					oldBatteryLevel = batteryLevel;
		        	batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 999);
		        	Debug.i(TAG, FUNC_TAG, "Battery Level " + ((batteryLevel == oldBatteryLevel) ? "SAME" : "CHANGED") + ": "+batteryLevel+"%");
		        	if (batteryLevel != oldBatteryLevel)
		        		logData("BATTERY_CHANGED Broadcast Received", "Battery Level: "+batteryLevel+"%");
        		}
			}
		};
		IntentFilter filter = new IntentFilter();
		filter.addAction(ServiceIntents.NEW_EGV_DATA);
		filter.addAction(ServiceIntents.NEW_METER_DATA);
		
		filter.addAction(ServiceIntents.UNKNOWN_ERROR);
		filter.addAction(ServiceIntents.NO_DATA_ERROR);
		filter.addAction(ServiceIntents.RECEIVER_CONNECTED);
		filter.addAction(ServiceIntents.NEW_INSERTION_DATA);
		
		filter.addAction(ACTIVATE_USB);
		filter.addAction(DEACTIVATE_USB);
		filter.addAction(RESTART_RECEIVER_SERVICE);
		filter.addAction("android.intent.action.BATTERY_CHANGED");
		//filter.addAction("edu.virginia.dtc.ACTION.USE_PERMISSION");
		registerReceiver(EGVReceiver, filter);
		
		bindService(m_receiverUpdateServiceIntent, m_serviceConnection, Context.BIND_ABOVE_CLIENT);
		Debug.i(TAG, FUNC_TAG, "Bound Receiver Update Service");
	}

	public void sendEGVData(final Device d) {
		final String FUNC_TAG = "sendEGVData";

		if (sendEGVThread == null || !sendEGVThread.isAlive()) 
		{
			sendEGVThread = new Thread() {
				public void run() 
				{
					double cgmValue = 0;
					String specialValue = "";
					Debug.i(TAG, FUNC_TAG, "EGV device data index=" + d.data_index);
					
					if (egvRecordList.size() - d.data_index > NUM_PAST_DEXCOM_RECORDS && isFirstSetOfData) 
					{
						Debug.i(TAG, FUNC_TAG, "Setting device data index to " + (egvRecordList.size() - NUM_PAST_DEXCOM_RECORDS));
						d.data_index = egvRecordList.size() - NUM_PAST_DEXCOM_RECORDS;
					}
					
					while (d.data_index < egvRecordList.size()) {
						boolean sendToCGMService = true;
						
						EstimatedGlucoseRecord test = egvRecordList.get(d.data_index);
						
						Debug.i("sendEGVData", FUNC_TAG, "EGV Record ------------------------------------------------------");
						Debug.i("sendEGVData", FUNC_TAG, test.toDetailedString());
						Debug.i("sendEGVData", FUNC_TAG, "-----------------------------------------------------------------");
						
						if (egvRecordList.get(d.data_index).Value <= 0)		//If value is 0 due to warm up period or CGM noise or something 
						{ 
							Debug.i(TAG, FUNC_TAG, "EGV value is display=" + egvRecordList.get(d.data_index).IsDisplayOnly + " value="+ egvRecordList.get(d.data_index).Value + " special=" + egvRecordList.get(d.data_index).SpecialValue);
						}
						
						// Return new CGM data from Dexcom to service
						time = egvRecordList.get(d.data_index).DisplayTime.getTime() / 1000; 	//SECONDS!!
						cgmValue = egvRecordList.get(d.data_index).Value;
						specialValue = egvRecordList.get(d.data_index).SpecialValue;
						int trend = 3;
						
						switch (egvRecordList.get(d.data_index).TrendArrow) {
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
						
						String special = egvRecordList.get(d.data_index).SpecialValue;
						Debug.i(TAG, FUNC_TAG, "Special: "+special);
						
						List<InsertionTimeRecord> insertionList = (List<InsertionTimeRecord>) m_receiverService.insertionRecords;
						InsertionTimeRecord last = null;
						
						if(insertionList != null && !insertionList.isEmpty())		//Get the last insertion record
							last = insertionList.get(insertionList.size()-1);
						
						//If the sensor is inserted and the current time is less than the insertion time plus 2 hours, its warming up
						d.state = CGM_NORMAL;
						if(last != null && last.IsInserted && ((System.currentTimeMillis()/1000) <= ((last.InsertionDisplayTime.getTime()/1000) + WARMUP_IN_SEC)))
						{
							d.state = CGM_WARMUP;
							Debug.i(TAG, FUNC_TAG, "Warmup mode");
						}
						else
						{
							if(special!=null)
							{
								if(special.equalsIgnoreCase("sensorOutOfCal"))
								{
									d.state = CGM_CALIBRATION_NEEDED;
									Debug.i(TAG, FUNC_TAG, "Cal needed");
								}
								else if(special.equalsIgnoreCase("Aberration3"))
								{
									d.state = CGM_NOISE;
									Debug.i(TAG, FUNC_TAG, "Abb 3");
								}
								else if(special.equalsIgnoreCase("Aberration2"))
								{
									d.state = CGM_DATA_ERROR;
									Debug.i(TAG, FUNC_TAG, "Abb 2");
								}
								else
									Debug.i(TAG, FUNC_TAG, "None");
							}
						}
						
						Debug.i(TAG, FUNC_TAG, "CGM State: "+d.state);
						
						/*
						Bundle output = new Bundle();
						output.putLong("time", time);
						output.putDouble("cgmValue", (double) cgmValue);
						output.putInt("trend", trend);
						output.putInt("minToNextCalibration", 720);
						output.putInt("calibrationType", 0);
						output.putInt("cgm_state", d.state);
						*/
						
						log(FUNC_TAG, "Sent CGM value=" + cgmValue + " time=" + DateFormat.format("MM/dd hh:mm", new Date(time * 1000)) + " State: "+ CGM.stateToString(d.state));
						
						DexcomBTRelayDriverUI.cgmList.add(DateFormat.format("hh:mm:ss", new Date(time * 1000)) + ((sendToCGMService) ? "" : " -- NOT SENT") + "\n"
							+ cgmValue + ((egvRecordList.get(d.data_index).IsDisplayOnly) ? " -- Display only" : "")
							+ ((specialValue != null) ? " -- " + specialValue : ""));
						
						/******************************************************************************************************************/
						//DATA IS SENT VIA BT HERE******************************************************************************************
						/******************************************************************************************************************/
						
						if((!egvRecordList.get(d.data_index).IsDisplayOnly) && (time > lastValidCgmTime))		//Only send the value if it is NOT display only
						{
							//Generate output string and send data to the other phone via bluetooth (8 values long, first is the command)
							String out = String.format("%d,%d,%f,%d,%d,%d,%d,%d", CGM_DATA, time, (double)cgmValue, trend, 720, 0, d.state, batteryLevel);
							sendBtString(out);
							
							//Update the last valid time so we don't resend data to the phone
							lastValidCgmTime = time;
				    		ApplicationPreferences.edit().putLong("lastValidCgmTime", lastValidCgmTime).commit();
						}
						
						d.data_index++;
					}
					
					Bundle statusBundle = new Bundle();
					statusBundle.putString("status", "Successfully Connected");
					statusBundle.putBoolean("progressbar", false);
					sendDataMessage(messengerToUI, statusBundle, DRIVER2UI_UPDATE_STATUS, 0, 0);
					
					if (ENABLE_BATTERY_LOOP) {
						if (!isFirstSetOfData) {
							m_receiverUpdateTools.cancelPeriodicUpdate(DexcomBTRelayDriver.this);
							selfHandler.postDelayed(new Thread() {
								public void run() {
									turnOffUSB();
								}
							}, TURN_OFF_USB_DELAY_SECS * 1000);
							if (!updateScheduled) {
								long x = 0;
								alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + (UPDATE_INTERVAL - (x)) * 1000,
										UPDATE_INTERVAL * 1000, activateUSBIntent);
								updateScheduled = true;
								Debug.i(TAG, FUNC_TAG, "updateScheduled for activateUSB: " + x + " secs from now");
							}
						} else {
							Debug.i(TAG, FUNC_TAG, "isFirstSetOfData set to false");
							isFirstSetOfData = false;
						}
					}
					if (!isFirstSetOfData)
						wakelock.release();
				}
			};
			sendEGVThread.run();
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String FUNC_TAG = "onStartCommand";

		Debug.i(TAG, FUNC_TAG, "Received onStartCommand...");
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

		m_receiverUpdateTools.cancelPeriodicUpdate(this);
		unregisterReceiver(EGVReceiver);
		
		unbindService(m_serviceConnection);
		alarmManager.cancel(activateUSBIntent); // cancel the previous alarm
	}

	// onBind supports two connections due to the dual nature of the standalone driver (these are filtered based on connection intent)
	@Override
	public IBinder onBind(Intent arg0) {
		final String FUNC_TAG = "onBind";

		Debug.i(TAG, FUNC_TAG, arg0.getAction());
		if (arg0.getAction().equalsIgnoreCase(Driver.UI_INTENT))
			return messengerFromUI.getBinder();
		else
			return null;
	}

	class incomingUIHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			final String FUNC_TAG = "incomingUIHandler";

			switch (msg.what) {
				case UI2DRIVER_NULL:
					break;
				case UI2DRIVER_REGISTER:
					messengerToUI = msg.replyTo;
					break;
				case UI2DRIVER_CGM_START:
					break;
				case UI2DRIVER_CGM_STOP:
					break;
				case UI2DRIVER_CGM_NO_ANT:
					break;
				case UI2DRIVER_RESTART_RECEIVER_SERVICE:
					Debug.i(TAG, FUNC_TAG, "Restarting Receiver Service");
					unbindService(m_serviceConnection);
					stopService(m_receiverUpdateServiceIntent);
					m_receiverUpdateServiceIntent = new Intent(DexcomBTRelayDriver.this, ReceiverUpdateService.class);
					m_receiverUpdateServiceIntent.putExtra("SEND_DATA_WITH_INTENT", m_sendDataWithIntent);
					startService(m_receiverUpdateServiceIntent);
					bindService(m_receiverUpdateServiceIntent, m_serviceConnection, Context.BIND_ABOVE_CLIENT);
					break;
			}
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
	
	private void sendBtString(String out)
	{
		final String FUNC_TAG = "sendBtString";

		if(InterfaceData.remoteCgmBt != null && (InterfaceData.remoteCgmBt.getState() == BluetoothConn.CONNECTED))
		{
			//Send the data via strings to end device
			Debug.i(TAG, FUNC_TAG, "BT String: " + out);
			
			try 
			{
				InterfaceData.remoteCgmBt.write(out.getBytes("UTF-8"));
			} 
			catch (UnsupportedEncodingException e) 
			{
				Debug.i(TAG, FUNC_TAG, "Unable to send CGM value, unsupported encoding");
			}
		}
	}	
	
	public static void turnOnUSB() {
		USBController.turnOnUSB();
	}

	public static void turnOffUSB() {
		USBController.turnOffUSB();
	}

	public static void resetUSB() {
		USBController.resetUSB();
	}
	
	public void log(String function, String log){
		Debug.i("CGMDriver", function, log);
	}	
	
	private void logData(String function, String message)
	{
		if(LOGGING)
		{
			Debug.i(TAG, "logData", function + " > " + message);
			if(logReady)
			{
				File path = Environment.getExternalStorageDirectory();
		        File file = new File(path, logFile);			//This is either created when restoring or extracted from saved settings
		
			    try {
			        OutputStream os = new FileOutputStream(file, true);
			        
			        SimpleDateFormat sdfDate = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
				    Date now = new Date();
				    String strDate = sdfDate.format(now);
			        
				    String output = strDate+" = "+function+" > "+message+"\n";
			        os.write(output.getBytes());
			        os.close();
			        
			        MediaScannerConnection.scanFile(this.getApplicationContext(), new String[] { file.toString() }, null,
		        		new MediaScannerConnection.OnScanCompletedListener() {
		            		public void onScanCompleted(String path, Uri uri) {
		            		}	
		        		}
			        );
			    } catch (IOException e) {
			        Debug.e("ExternalStorage", "logData", "Error writing " + file, e);
			    }
			}
		}
	}
	
	void createLogFile() {
		final String FUNC_TAG = "createLogFile";

		boolean mExternalStorageAvailable = false;
		boolean mExternalStorageWriteable = false;
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state)) {
		    mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
		    mExternalStorageAvailable = true;
		    mExternalStorageWriteable = false;
		} else {
		    mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
		
		if(mExternalStorageAvailable && mExternalStorageWriteable)
		{
			File path = Environment.getExternalStorageDirectory();
	        File file = new File(path, logFile);			//This is either created when restoring or extracted from saved settings
		    Debug.i(TAG, FUNC_TAG, file.getAbsolutePath());
	
		    try {
		        OutputStream os = new FileOutputStream(file, true);
		        logReady = true;
		        
		        SimpleDateFormat sdfDate = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
			    Date now = new Date();
			    String strDate = sdfDate.format(now);
			    
		        String data = strDate+ " = createLogFile > Dexcom BT Driver started...Log file created\n";
		        os.write(data.getBytes());
		        data = strDate+ " = createLogFile > File location: " + file.getAbsolutePath()+"\n";
		        os.write(data.getBytes());
		        os.close();
		        
		        MediaScannerConnection.scanFile(this.getApplicationContext(), new String[] { file.toString() }, null,
	        		new MediaScannerConnection.OnScanCompletedListener() {
	            		public void onScanCompleted(String path, Uri uri) {
	            		}	
	        		}
		        );
		    } catch (IOException e) {
		        Debug.e("ExternalStorage", FUNC_TAG, "Error writing " + file, e);
		    }
		}
		else
			Debug.i(TAG, FUNC_TAG, "Log file unable to be created...not mountable or writeable");
	}
}
