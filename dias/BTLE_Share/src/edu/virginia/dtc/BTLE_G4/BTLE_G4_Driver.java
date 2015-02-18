package edu.virginia.dtc.BTLE_G4;

import java.util.ArrayList;
import java.util.List;

import edu.virginia.dtc.G4DevKit.MeterRecord;
import edu.virginia.dtc.G4DevKit.ReceiverUpdateService.ServiceBinder;
import edu.virginia.dtc.G4DevKit.EstimatedGlucoseRecord;
import edu.virginia.dtc.G4DevKit.ServiceIntents;
import edu.virginia.dtc.G4DevKit.ReceiverUpdateService;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import android.app.Notification;
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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class BTLE_G4_Driver extends Service
{
	//GLOBAL STATIC VARIABLES
	//*******************************************************
	private static final String TAG = "BTLE_G4_Driver";
	
	public static final String Driver_Name = "BTLE_G4";
	public static final String UI_Intent = "Driver.UI.BTLE_G4";
	public static final String Cgm_Intent = "Driver.Cgm.BTLE_G4";
	
	// Messages from UI
	public static final int UI2DRIVER_NULL = 0;
	public static final int UI2DRIVER_REGISTER = 1;
	public static final int UI2DRIVER_CGM_START = 2;
	public static final int UI2DRIVER_CGM_STOP = 3;
	public static final int UI2DRIVER_CGM_NO_ANT = 6;
	public static final int UI2DRIVER_SCAN = 9;
	public static final int UI2DRIVER_CONNECT = 10;

	
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
	
 	//GLOBAL VARIABLES
 	//*******************************************************
	private static ReceiverUpdateService receiver;
	
	private final Messenger messengerFromCgmService = new Messenger(new incomingCgmHandler());
	private Messenger messengerToCgmService = null;
	
	public static String mac, code, egv, meter;
	public static boolean sysTimeReady;
	public static long sysTime, sysTimeOffset;
	
	private SharedPreferences settings;
	
	private List<EstimatedGlucoseRecord> egvRecords;
	private List<MeterRecord> meterRecords;
	private int egvIndex = 0;
	private int meterIndex = 0;
	
	private BroadcastReceiver driver;
	
	private final ServiceConnection serviceConnection = new ServiceConnection() 
	{
		final String FUNC_TAG = "serviceConnection";

		public void onServiceDisconnected(ComponentName name) 
		{
			Debug.e(TAG, FUNC_TAG, "Service Disconnected!");
		}

		public void onServiceConnected(ComponentName name, IBinder service) 
		{
			ServiceBinder binder = (ServiceBinder) service;
			receiver = binder.getService();
			Debug.i(TAG, FUNC_TAG, "Receiver Service Connected: "+receiver);
		}
	};
	
	private BroadcastReceiver dexcom = new BroadcastReceiver() 
	{
		final String FUNC_TAG = "dexcom";
		
		@Override
		public void onReceive(Context context, Intent intent) 
		{
			String action = intent.getAction();
			Debug.i(TAG, FUNC_TAG, "Dexcom onReceive: "+action);
			
			if (action.equals(ServiceIntents.NEW_EGV_DATA)) 
			{
				egvRecords = (List<EstimatedGlucoseRecord>)receiver.egvRecords;
				
				if(egvRecords != null && !egvRecords.isEmpty())
				{
					Debug.i(TAG, FUNC_TAG, "System Offset: "+receiver.systemOffset);
					
					while(egvIndex < egvRecords.size())
					{
						EstimatedGlucoseRecord r = egvRecords.get(egvIndex);
						Debug.i(TAG, FUNC_TAG, r.RecordNumber+" "+r.Value);
						
						sendEgv(r);
						
						egvIndex++;
					}
				}
			} 
			else if(action.equals(ServiceIntents.NEW_INSERTION_DATA))
			{
			}
			else if (action.equals(ServiceIntents.NEW_METER_DATA)) 
			{
				meterRecords = (List<MeterRecord>)receiver.meterRecords;
				
				if(meterRecords != null && !meterRecords.isEmpty())
				{
					while(meterIndex < meterRecords.size())
					{
						MeterRecord r = meterRecords.get(meterIndex);
						Debug.i(TAG, FUNC_TAG, r.RecordNumber+" "+r.Value);
						
						sendMeter(r);
						
						meterIndex++;
					}
				}
			} 
			else if (action.equals(ServiceIntents.UNKNOWN_ERROR)) 
			{
			} 
			else if (action.equals(ServiceIntents.NO_DATA_ERROR)) 
			{
			} 
			else if (action.equals(ServiceIntents.RECEIVER_CONNECTED)) 
			{
			} 
		}
	};
	
	@Override
	public void onCreate() 
	{
		super.onCreate();
		
		final String FUNC_TAG = "onCreate";
		
		Debug.i(TAG, FUNC_TAG, "");

		int icon = R.drawable.ic_launcher;
		CharSequence tickerText = "";
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);
		Context context = getApplicationContext();
		CharSequence contentTitle = "Dexcom Share Driver";
		CharSequence contentText = "BTLE";
		Intent notificationIntent = new Intent(this, BTLE_G4_Driver.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		final int DRVR_ID = 94;
		startForeground(DRVR_ID, notification);
		
		driver = new BroadcastReceiver()
		{
			public void onReceive(Context context, Intent intent)
			{
				Debug.i(TAG, FUNC_TAG, "Updating Device Manager!");
				updateDevices();
			}
		};
		registerReceiver(driver, new IntentFilter("edu.virginia.dtc.DRIVER_UPDATE"));
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(ServiceIntents.NEW_EGV_DATA);
		filter.addAction(ServiceIntents.NEW_METER_DATA);
		filter.addAction(ServiceIntents.UNKNOWN_ERROR);
		filter.addAction(ServiceIntents.NO_DATA_ERROR);
		filter.addAction(ServiceIntents.RECEIVER_CONNECTED);
		filter.addAction(ServiceIntents.NEW_INSERTION_DATA);
		registerReceiver(dexcom, filter);
		
		
		new Handler().postDelayed(new Runnable()
		{
			public void run()
			{
				Debug.i(TAG, FUNC_TAG, "Starting CGM Service...");
				Intent cgmIntent = new Intent();
				cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
				cgmIntent.putExtra("reset", true);
				cgmIntent.putExtra("driver_intent", BTLE_G4_Driver.Cgm_Intent);
				cgmIntent.putExtra("driver_name", BTLE_G4_Driver.Driver_Name);
				cgmIntent.putExtra("CGMCommand", 3);
				startService(cgmIntent);
			}
		}, 1000);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String FUNC_TAG = "onStartCommand";
		
		Debug.i(TAG, FUNC_TAG, "");
		
		if(!intent.getBooleanExtra("auto", false))
		{
			Intent uiIntent = new Intent();
			uiIntent.setClassName("edu.virginia.dtc.BTLE_G4", "edu.virginia.dtc.BTLE_G4.BTLE_G4_UI");
			uiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(uiIntent);
		}
		else if(intent.getBooleanExtra("nfc", false))
		{
			String mac = intent.getStringExtra("mac");
			String code = intent.getStringExtra("code");
			
			BTLE_G4_Driver.mac = mac;
			BTLE_G4_Driver.code = code;
			
			Debug.i(TAG, FUNC_TAG, "Writing MAC: "+BTLE_G4_Driver.mac+" and Code!");
			settings = getSharedPreferences("Share", 0);
			settings.edit().putString("mac", BTLE_G4_Driver.mac).commit();
			settings.edit().putString("code", BTLE_G4_Driver.code).commit();
			
			Debug.i(TAG, FUNC_TAG, "MAC: "+mac+" Code: "+code);
			
			Intent rxIntent = new Intent(BTLE_G4_Driver.this, ReceiverUpdateService.class);
			rxIntent.putExtra("mac", mac);
			rxIntent.putExtra("code", code);
			startService(rxIntent);
			
			bindService(rxIntent, serviceConnection, Context.BIND_AUTO_CREATE);
		}
		else
		{
			Debug.i(TAG, FUNC_TAG, "Auto start from the supervisor!");
			
			settings = getSharedPreferences("Share", 0);
			String mac, code;
			
			mac = settings.getString("mac", "");
			code = settings.getString("code", "");
			
			if(!mac.equalsIgnoreCase("") && !code.equalsIgnoreCase("")) {
				Debug.w(TAG, FUNC_TAG, "Found previous device - MAC: "+mac+" Code: "+code);
				
				BTLE_G4_Driver.mac = mac;
				BTLE_G4_Driver.code = code;
				
				Intent rxIntent = new Intent(BTLE_G4_Driver.this, ReceiverUpdateService.class);
				rxIntent.putExtra("mac", mac);
				rxIntent.putExtra("code", code);
				startService(rxIntent);
				
				bindService(rxIntent, serviceConnection, Context.BIND_AUTO_CREATE);
			}
			
		}
		
		return 0;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		final String FUNC_TAG = "onDestroy";
		
		Debug.i(TAG, FUNC_TAG, "");
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return messengerFromCgmService.getBinder();
	}
	
	/*****************************************************************************************
	 * Helper Functions
	 *****************************************************************************************/
	
	private void sendMeter(MeterRecord r)
	{
		final String FUNC_TAG = "sendMeter";
		
		long time;
		
		BTLE_G4_Driver.meter = r.toDetailedString();
		
		if(receiver.systemOffsetReady)
			time = (r.SystemTime.getTime()/1000) + receiver.systemOffset;
		else
			time = r.DisplayTime.getTime()/1000;
		
		Debug.i(TAG, FUNC_TAG, "Calibration value "+r.Value+" at "+time+" is being entered");
		
		Bundle data = new Bundle();
		data.putInt("cal_value", (int)r.Value);
		data.putLong("cal_time", time);
		
		sendDataMessage(messengerToCgmService, data, DRIVER2CGM_SERVICE_CALIBRATE_ACK, 0, 0);
	}
	
	private void sendEgv(EstimatedGlucoseRecord r)
	{
		final String FUNC_TAG = "sendEgv";
		long time;
		int state;
		
		BTLE_G4_Driver.egv = r.toDetailedString();
		
		if(receiver.systemOffsetReady)
			time = (r.SystemTime.getTime()/1000) + receiver.systemOffset;
		else
			time = r.DisplayTime.getTime()/1000;
		
		BTLE_G4_Driver.sysTime = time;
		BTLE_G4_Driver.sysTimeOffset = receiver.systemOffset;
		BTLE_G4_Driver.sysTimeReady = receiver.systemOffsetReady;
		
		int trend = 3;
		switch (r.TrendArrow) 
		{
			case DoubleUp:
			case SingleUp: 		trend = 2;	break;
			case DoubleDown:
			case SingleDown: 	trend = -2;	break;
			case Flat: 			trend = 0; 	break;
			case FortyFiveDown: trend = -1; break;
			case FortyFiveUp:	trend = 1;	break;
			case None:
			case NotComputable:
			case RateOutOfRange:
			default:			trend = 5;	break;
		}
		
		state = CGM.CGM_NORMAL;
		if(r.SpecialValue != null)
		{
			if(r.SpecialValue.equalsIgnoreCase("sensorOutOfCal"))
			{
				state = CGM.CGM_WARMUP;
				Debug.i(TAG, FUNC_TAG, "Warmup mode");
			}
			else if(r.SpecialValue.equalsIgnoreCase("Aberration3"))
			{
				state = CGM.CGM_NOISE;
				Debug.i(TAG, FUNC_TAG, "Abb 3");
			}
			else if(r.SpecialValue.equalsIgnoreCase("Aberration2"))
			{
				state = CGM.CGM_DATA_ERROR;
				Debug.i(TAG, FUNC_TAG, "Abb 2");
			}
			else
				Debug.i(TAG, FUNC_TAG, "None");
		}
		
		Bundle data = new Bundle();
		data.putLong("time", time);
		data.putDouble("cgmValue", (double) r.Value);
		data.putInt("trend", trend);
		data.putInt("minToNextCalibration", 720);
		data.putInt("calibrationType", 0);
		data.putInt("cgm_state", state);

		//TODO: fix this writing to CP
		if(state == CGM.CGM_NORMAL)
		{
			ContentValues dv = new ContentValues();
			Debug.e(TAG, FUNC_TAG, "Adding to running CGM DB value!");
			dv.put("running_cgm", "edu.virginia.dtc.BTLE_G4.BTLE_G4_Driver");					
			
			Debug.e(TAG, FUNC_TAG, "Running CGM updated in Hardware Configuration table!");
			getContentResolver().update(Biometrics.HARDWARE_CONFIGURATION_URI, dv, null, null);
		}
		
		if(!r.IsDisplayOnly)
			sendDataMessage(messengerToCgmService, data, DRIVER2CGM_SERVICE_NEW_CGM_DATA, 0, 0);
		else
			Debug.e(TAG, FUNC_TAG, "Value is display only!");
	}
	
	/*****************************************************************************************
	 * Message Handlers
	 *****************************************************************************************/
	
	class incomingCgmHandler extends Handler 
	{
		@Override
		public void handleMessage(Message msg) 
		{
			final String FUNC_TAG = "handleMessage";

			Bundle data;
			switch (msg.what) 
			{
				case CGM_SERVICE2DRIVER_NULL:
					break;
				case CGM_SERVICE2DRIVER_REGISTER:
					messengerToCgmService = msg.replyTo;
					Debug.i(TAG, FUNC_TAG, "CGM Service replyTo registered, sending parameters...");
	
					ContentValues cgmValues = new ContentValues();
					cgmValues.put("min_cgm", 40);
					cgmValues.put("max_cgm", 400);
					cgmValues.put("phone_calibration", 0);			//Indicates whether the calibration data is entered on the phone or the CGM device
					
					getContentResolver().update(Biometrics.CGM_DETAILS_URI, cgmValues, null, null);
					sendDataMessage(messengerToCgmService, null, DRIVER2CGM_SERVICE_PARAMETERS, msg.arg1, msg.arg2);
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
	
	private void sendDataMessage(Messenger messenger, Bundle bundle, int what, int arg1, int arg2) 
	{
		final String FUNC_TAG = "sendDataMessage";
		
		if (messenger == null) // error handling
		{
			Debug.e(TAG, FUNC_TAG, "Messenger null!");
			return;
		}
		
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
	
	public void updateDevices()
	{
		Intent intent = new Intent("edu.virginia.dtc.DEVICE_RESULT");
		intent.putExtra("started", true);
		intent.putExtra("name", "BTLE_G4");
		sendBroadcast(intent);
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
	
	public class OutPacket
	{
		public String name = "UNKNOWN";
		public int responseFrames = 0;
		public List<byte[]> frames = new ArrayList<byte[]>();
		
		public OutPacket(){};
	}
}
