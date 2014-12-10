/*
 * Driver (that resides on the CPMP) for reading Dexcom CGM data from a BT enabled G4 Receiver
 */
package edu.virginia.dtc.DexcomBTRelayDriver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.text.format.DateFormat;
import android.widget.ArrayAdapter;
import android.widget.Toast;

public class DexcomBTRelayDriver extends Service {

	private static final String TAG = "DexcomBTDriver";
	
	public static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
	public static final Uri CGM_DETAILS_URI = Uri.parse("content://" + PROVIDER_NAME + "/cgmdetails");
	public static final Uri SMBG_URI = Uri.parse("content://"+ PROVIDER_NAME + "/smbg");

	private BroadcastReceiver stopDriverReceiver, driverUpdateReceiver;
	
	private static final boolean LOGGING = true;
	
	//Command values that come from BT strings
	private static final int CGM_DATA = 0;
	private static final int CALIB_DATA = 1;
	private static final int DEV_DATA = 2;
	
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

	// Commands to CGM Service
	private static final int DRIVER2CGM_SERVICE_NEW_CGM_DATA = 0;
	private static final int DRIVER2CGM_SERVICE_PARAMETERS = 1;
	private static final int DRIVER2CGM_SERVICE_STATUS_UPDATE = 2;
	private static final int DRIVER2CGM_SERVICE_CALIBRATE_ACK = 3;
	
  	private static final int CGM_SERVICE_CMD_DISCONNECT = 2;
  	public static final int CGM_SERVICE_CMD_INIT = 3;

	// Commands for CGM Driver
	private static final int CGM_SERVICE2DRIVER_NULL = 0;
	private static final int CGM_SERVICE2DRIVER_REGISTER = 1;
	private static final int CGM_SERVICE2DRIVER_CALIBRATE = 2;
	private static final int CGM_SERVICE2DRIVER_DIAGNOSTIC = 3;
	private static final int CGM_SERVICE2DRIVER_DISCONNECT = 4;

	private Driver thisDriver;
	private WakeLock wakelock;

	private final Messenger messengerFromCgmService = new Messenger(new incomingCgmHandler());
	private final Messenger messengerFromUI = new Messenger(new incomingUIHandler());

	private Messenger messengerToCgmService = null;
	private Messenger messengerToUI = null;
	
	private String logFile;
	private boolean logReady = false;
	
	private InterfaceData data;
	private static InterpreterThread interpret = null;

	/************************************************************************************************/
	/* Main Functions */
	/************************************************************************************************/
	
	@Override
	public void onCreate() {
		final String FUNC_TAG = "onCreate";

		super.onCreate();
		
		Debug.i(TAG, FUNC_TAG, "Creation...");
		thisDriver = Driver.getInstance();

		// Setup log file
		SimpleDateFormat sdfDate = new SimpleDateFormat("MM-dd-yyyy");
	    Date now = new Date();
	    String strDate = sdfDate.format(now);
		String path = "DexcomBTLogFile_"+strDate+".txt";
		
		logFile = path;
		
//		if(LOGGING)
//			createLogFile();

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
		Intent notificationIntent = new Intent(this, DexcomBTRelayDriver.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		final int DRVR_ID = 1;
		//mNotificationManager.notify(DRVR_ID, notification);
		startForeground(DRVR_ID, notification);

		stopDriverReceiver = new BroadcastReceiver(){
			public void onReceive(Context context, Intent intent) {
				if (intent.getStringExtra("package").equals("edu.virginia.dtc.USBDexcomLocalDriver")){
					Debug.i(TAG, FUNC_TAG, "Finishing...");
					sendDataMessage(messengerToUI, null, DRIVER2UI_FINISH, 0, 0);
					Debug.i(TAG, FUNC_TAG,"Disconnecting CGM #" + thisDriver.cgms.get(0).my_dev_index);
					Intent cgmIntent = new Intent();
				    cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
					cgmIntent.putExtra("state", thisDriver.my_state_index);
				    cgmIntent.putExtra("dev", thisDriver.cgms.get(0).my_dev_index);
					cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_DISCONNECT);
					startService(cgmIntent);
					stopSelf();
				}
			}			
		};
		registerReceiver(stopDriverReceiver, new IntentFilter("edu.virginia.dtc.STOP_DRIVER"));
		
		driverUpdateReceiver = new BroadcastReceiver(){
			public void onReceive(Context context, Intent i) {
				Intent intent = new Intent("edu.virginia.dtc.DEVICE_RESULT");
				int pumps = 0, cgms = 0;
				boolean started = false;
				
				if(InterfaceData.remoteCgmBt!=null && InterfaceData.remoteCgmBt.getState() == BluetoothConn.CONNECTED)
				{
					cgms = 1;
					started = true;
				}
				
				intent.putExtra("cgms", cgms);
				intent.putExtra("pumps", pumps);
				intent.putExtra("started", started);
				intent.putExtra("name", "Dexcom BT");
				sendBroadcast(intent);
			}
		};
		registerReceiver(driverUpdateReceiver, new IntentFilter("edu.virginia.dtc.DRIVER_UPDATE"));		

		updateDriverDetails("Name:  Dexcom Gen 4\nMin Value:  39\nMax Value:  400");
		
		initializeCGM();
		
		Toast.makeText(getApplicationContext(), "DexCom BT Driver starting...", Toast.LENGTH_SHORT).show();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String FUNC_TAG = "onStartCommand";

		Debug.i(TAG, FUNC_TAG, "Received onStartCommand...");
		
		boolean auto = intent.getBooleanExtra("auto", false);
		if(auto)
		{
			
		}
		else
		{
			Intent uiIntent = new Intent();
			uiIntent.setClassName("edu.virginia.dtc.DexcomBTRelayDriver", "edu.virginia.dtc.DexcomBTRelayDriver.DexcomBTRelayDriverUI");
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
		final String FUNC_TAG = "onDestroy";

		super.onDestroy();
		thisDriver.cgms.clear();
		unregisterReceiver(stopDriverReceiver);
		updateDriverDetails("");
		
		//Kill the interpret thread
		if(interpret != null)
    	{
    		interpret.run = false;
    		try 
    		{
				interpret.join(1000);
			} 
    		catch (InterruptedException e) 
    		{
				Debug.i(TAG, FUNC_TAG, "Interpret thread interrupted...");
			}
    		interpret = null;
    	}
		
		//Kill the BT interface for the CGM
		if(InterfaceData.remoteCgmBt != null)
    	{
    		InterfaceData.remoteCgmBt.stop();
    		InterfaceData.remoteCgmBt = null;
    	}
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

	/************************************************************************************************/
	/* Message Handlers */
	/************************************************************************************************/
	
	class incomingCgmHandler extends Handler {
		final String FUNC_TAG = "InterpreterThread";

		@Override
		public void handleMessage(Message msg) {
			Device cgm;
			Bundle statusBundle;
			switch (msg.what) {
			case CGM_SERVICE2DRIVER_NULL:
				break;
			case CGM_SERVICE2DRIVER_REGISTER:
				messengerToCgmService = msg.replyTo;
				Debug.i(TAG, FUNC_TAG, "CGM Service replyTo registered, sending parameters...");

				// Gather state and device indexes, these values come from the
				// CGM Service so device creation and deletion (see CGM_DRIVER_DISCONNECT) 
				// are started at the CGM Service and filter down here
				thisDriver.my_state_index = msg.arg1;
				thisDriver.cgms.add(new Device(msg.arg2));
				Debug.i(TAG, FUNC_TAG, "added to driver=" + thisDriver.hashCode() + " device = " + msg.arg2);
				reportUIChange();

				ContentValues cgmValues = new ContentValues();
				cgmValues.put("min_cgm", 40);
				cgmValues.put("max_cgm", 400);
				cgmValues.put("phone_calibration", 0);			//Indicates whether the calibration data is entered on the phone or the CGM device
				
				getContentResolver().update(CGM_DETAILS_URI, cgmValues, null, null);

				sendDataMessage(messengerToCgmService, null, DRIVER2CGM_SERVICE_PARAMETERS, msg.arg1, msg.arg2);

				for (Device dev : thisDriver.cgms) 
				{
					dev.status = "Started";
					dev.running = true;
					dev.connected = true;
					dev.cgm_ant = true;

					reportCgmServiceChange(dev);
				}
				
				//Start the BT if not already on and put into Listening mode
				data = InterfaceData.getInstance();
				
				if(!data.bt.isEnabled())
					data.bt.enable();
				
				if(InterfaceData.remoteCgmBt == null)
	    		{
		    		InterfaceData.remoteCgmBt = new BluetoothConn(data.bt, InterfaceData.CGM_UUID, "CgmBT", InterfaceData.cgmMessages, InterfaceData.cgmLock, true);
		    		InterfaceData.remoteCgmBt.listen();
	    		}
				
				//Start the interpreter thread
				if(interpret == null)
	    		{
		    		interpret = new InterpreterThread();
		    		interpret.start();
	    		}
				
				//Update the UI with the information
				statusBundle = new Bundle();
				statusBundle.putString("status", "Registered with CGM Service - BT Listening");
				sendDataMessage(messengerToUI, statusBundle, DRIVER2UI_UPDATE_STATUS, 0, 0);
				break;
			case CGM_SERVICE2DRIVER_CALIBRATE:
				Debug.i(TAG, FUNC_TAG, "Calibration is handled by the end device!");
				break;
			case CGM_SERVICE2DRIVER_DISCONNECT:
				Debug.i(TAG, FUNC_TAG, "Removing device - " + msg.arg2);

				cgm = thisDriver.cgms.get(thisDriver.getDeviceArrayIndex(msg.arg2));

				thisDriver.cgms.remove(cgm);
				if (thisDriver.cgms.size() == 0) {
					statusBundle = new Bundle();
					statusBundle.putString("status", "");
					sendDataMessage(messengerToUI, statusBundle, DRIVER2UI_UPDATE_STATUS, 0, 0);
				} else
					reportUIChange();
				break;
			}
		}
	}

	class incomingUIHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			Device dev;
			
			switch (msg.what) {
				case UI2DRIVER_NULL:
					break;
				case UI2DRIVER_REGISTER:
					messengerToUI = msg.replyTo;
					break;
				case UI2DRIVER_CGM_START:
					dev = thisDriver.cgms.get(thisDriver.getDeviceArrayIndex(msg.arg2));
					dev.status = "Started";
					dev.running = true;
					dev.connected = true;
					dev.cgm_ant = true;
	
					reportCgmServiceChange(dev);
					reportUIChange();
					break;
				case UI2DRIVER_CGM_STOP:
					dev = thisDriver.cgms.get(thisDriver.getDeviceArrayIndex(msg.arg2));
					dev.status = "Stopped";
					dev.running = false;
					dev.connected = false;
					dev.cgm_ant = false;
	
					reportCgmServiceChange(dev);
					reportUIChange();
					break;
			}
		}
	}
	
	/************************************************************************************************/
	/* BT Message Handler */
	/************************************************************************************************/
	
	private class InterpreterThread extends Thread
	{
		private int cgmState = -1;
		public boolean run = true;
		
		public InterpreterThread()
		{
			final String FUNC_TAG = "InterpreterThread";

			Debug.i(TAG, FUNC_TAG, "Interpreter created...");
		}
		
		public void run()
		{
			final String FUNC_TAG = "run";

			Debug.i(TAG, FUNC_TAG, "Interpreter Thread running...");
			while(run)
			{
				if(cgmState != InterfaceData.remoteCgmBt.getState())
				{
					Debug.i(TAG, FUNC_TAG, "CGM BT state change!");
					cgmState = InterfaceData.remoteCgmBt.getState();
					reportUIChange();
				}
				
				// CGM message parsing
				if(InterfaceData.cgmMessages.size() > 0)
				{
					//Read a string then delete it from the list
					String mess = data.getMessage(InterfaceData.cgmMessages, InterfaceData.cgmLock);
					data.removeMessage(InterfaceData.cgmMessages, InterfaceData.cgmLock);
					
					String[] sub = mess.split(",");
					
					Debug.i(TAG, FUNC_TAG, "Interpret CGM substrings: ");
					for(String s:sub)
						Debug.i(TAG, FUNC_TAG, s);
					
					int command = Integer.parseInt(sub[0]);			//Parse the command value (CGM, Calibration, and Device)
					
					switch(command)
					{
						case CGM_DATA:								//Length is 7
							Bundle data = new Bundle();
							data.putLong("time", Long.parseLong(sub[1]));
							data.putDouble("cgmValue", Double.parseDouble(sub[2]));
							data.putInt("trend", Integer.parseInt(sub[3]));
							data.putInt("minToNextCalibration", Integer.parseInt(sub[4]));
							data.putInt("calibrationType", Integer.parseInt(sub[5]));
							data.putInt("cgm_state", Integer.parseInt(sub[6]));
							
							InterfaceData.g4Battery = Integer.parseInt(sub[7]);
							
							Debug.i(TAG, FUNC_TAG, "Battery: "+InterfaceData.g4Battery+"%");
							Debug.i(TAG, FUNC_TAG, "Sent CGM value=" + data.getDouble("cgmValue") + " time=" + DateFormat.format("MM/dd hh:mm", new Date(data.getLong("time") * 1000)));
							Debug.i(TAG, FUNC_TAG, "CGM State: "+ CGM.stateToString(Integer.parseInt(sub[6])));
							
							log(FUNC_TAG, "Sent CGM value=" + data.getDouble("cgmValue") + " time=" + DateFormat.format("MM/dd hh:mm", new Date(data.getLong("time") * 1000)));
							log_action(TAG, "BTG4 Battery: "+InterfaceData.g4Battery+"%", 1);
							
							if(DexcomBTRelayDriverUI.cgmList == null)
							{
								DexcomBTRelayDriverUI.cgmList = new ArrayAdapter<String> (getApplicationContext(),R.layout.listsettings);
							}
							
							//new PostCgmToList().execute(DateFormat.format("hh:mm:ss", new Date(data.getLong("time") * 1000)) + "\n" + data.getDouble("cgmValue"));
							
							if (messengerToCgmService != null) 
							{
								sendDataMessage(messengerToCgmService, data, DRIVER2CGM_SERVICE_NEW_CGM_DATA, thisDriver.my_state_index, 0);
							}
							
							reportUIChange();
							break;
						case CALIB_DATA:							//Length is 2
					 		if(DexcomBTRelayDriverUI.meterList == null)
					 		{
					 			DexcomBTRelayDriverUI.meterList = new ArrayAdapter<String> (getApplicationContext(),R.layout.listsettings);
					 		}
					 		
					 		//new PostMeterToList().execute(DateFormat.format("hh:mm:ss", new Date(System.currentTimeMillis())) + "\n" + Double.parseDouble(sub[1]));
					 		
					 		Debug.i(TAG, FUNC_TAG, "Sent calibration value="+Double.parseDouble(sub[1]));
					 		
					 		data = new Bundle();
							data.putInt("cal_value", (int)Double.parseDouble(sub[1]));
							sendDataMessage(messengerToCgmService, data, DRIVER2CGM_SERVICE_CALIBRATE_ACK, 0, 0);
					 		
					 		reportUIChange();
							break;
						case DEV_DATA:
							
							break;
						default:
							break;
					}
				}
				
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					Debug.i(TAG, FUNC_TAG, "Interpret thread interrupted...");
				}
			}
		}
	}

	/************************************************************************************************/
	/* Misc. Functions */
	/************************************************************************************************/
	
	public void initializeCGM() {
		if (thisDriver.cgms.size() > 0)
			return;
		Intent cgmIntent = new Intent();
		cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
		cgmIntent.putExtra("reset", true);
		cgmIntent.putExtra("driver_intent", Driver.CGM_INTENT);
		cgmIntent.putExtra("driver_name", Driver.DRIVER_NAME);
		cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_INIT);
		startService(cgmIntent);	
	}
	
	public void sendDeviceResult(int num)
	{
		Intent intentResult = new Intent("edu.virginia.dtc.DEVICE_RESULT");
		intentResult.putExtra("cgms", num);
		intentResult.putExtra("started", true);
		intentResult.putExtra("name", "DexcomLocal");
		sendBroadcast(intentResult);
	}
	
	// This sends a message to the CGM Service to update a device status or variable
	private void reportCgmServiceChange(Device dev) {
		if (messengerToCgmService != null) {
			Bundle data = new Bundle();
			data.putBoolean("running", dev.running);
			data.putBoolean("connected", dev.connected);
			data.putString("status", dev.status);

			sendDataMessage(messengerToCgmService, data, DRIVER2CGM_SERVICE_STATUS_UPDATE, thisDriver.my_state_index, dev.my_dev_index);
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
		else	//If the UI isn't hooked up then manually report the connection results to the Device Manager
		{
			Intent intentResult = new Intent("edu.virginia.dtc.DEVICE_RESULT");
			intentResult.putExtra("started", true);
			intentResult.putExtra("name", "Dexcom BT");
			
			int cgms = 0;
			if(InterfaceData.remoteCgmBt != null && InterfaceData.remoteCgmBt.getState() == BluetoothConn.CONNECTED)
				cgms = 1;
			
			intentResult.putExtra("cgms", cgms);
			sendBroadcast(intentResult);
		}
	}

	private void sendDataMessage(Messenger messenger, Bundle bundle, int what, int arg1, int arg2) {
		if (messenger == null) 	// Error handling
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

	public void updateDriverDetails(String details) {
		final String FUNC_TAG = "updateDriverDetails";

		ContentValues values = new ContentValues();
		values.put("details", details);
		try{
			getContentResolver().update(CGM_DETAILS_URI, values, null, null);
		}
		catch (Exception e)
		{
			Debug.e(TAG, FUNC_TAG, e.getMessage());
		}
	}
	
	/************************************************************************************************/
	/* Async Task Classes */
	/************************************************************************************************/
	
	private class PostCgmToList extends AsyncTask<String, Void, String>
	{
		@Override
		protected String doInBackground(String... params) {
			return params[0];
		}
		
		@Override
	    protected void onPostExecute(String result) {
			DexcomBTRelayDriverUI.cgmList.add(result);
	    }
	}
	
	private class PostMeterToList extends AsyncTask<String, Void, String>
	{
		@Override
		protected String doInBackground(String... params) {
			return params[0];
		}
		
		@Override
	    protected void onPostExecute(String result) {
			DexcomBTRelayDriverUI.meterList.add(result);
	    }
	}
	
	/************************************************************************************************/
	/* Logging */
	/************************************************************************************************/
	
	public void log_action(String service, String action, int priority) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("priority", priority);
        i.putExtra("time", (long)(System.currentTimeMillis()/1000));
        sendBroadcast(i);
	}
	
	public void log(String FUNC_TAG, String log){
		Debug.i("CGMDriver", FUNC_TAG, log);
	}
	
//	public void debug_message(String tag, String message)
//	{
//		logData(tag, message);
//		
//		Debug.i(tag, FUNC_TAG, message);
//	}
	
//	private void logData(String function, String message)
//	{
//		if(LOGGING)
//		{
//			Debug.i(TAG, FUNC_TAG, function + " > " + message);
//			
//			if(logReady)
//			{
//				File path = Environment.getExternalStorageDirectory();
//		        File file = new File(path, logFile);			//This is either created when restoring or extracted from saved settings
//		
//			    try {
//			        OutputStream os = new FileOutputStream(file, true);
//			        
//			        SimpleDateFormat sdfDate = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
//				    Date now = new Date();
//				    String strDate = sdfDate.format(now);
//			        
//				    String output = strDate+" = "+function+" > "+message+"\n";
//			        os.write(output.getBytes());
//			        os.close();
//			        
//			        MediaScannerConnection.scanFile(this.getApplicationContext(), new String[] { file.toString() }, null,
//		        		new MediaScannerConnection.OnScanCompletedListener() {
//		            		public void onScanCompleted(String path, Uri uri) {
//		            		}	
//		        		}
//			        );
//			    } catch (IOException e) {
//			        Debug.w("ExternalStorage", FUNC_TAG, "Error writing " + file, e);
//			    }
//			}
//		}
//	}
	
//	void createLogFile() {
//		boolean mExternalStorageAvailable = false;
//		boolean mExternalStorageWriteable = false;
//		String state = Environment.getExternalStorageState();
//
//		if (Environment.MEDIA_MOUNTED.equals(state)) {
//		    mExternalStorageAvailable = mExternalStorageWriteable = true;
//		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
//		    mExternalStorageAvailable = true;
//		    mExternalStorageWriteable = false;
//		} else {
//		    mExternalStorageAvailable = mExternalStorageWriteable = false;
//		}
//		
//		if(mExternalStorageAvailable && mExternalStorageWriteable)
//		{
//			File path = Environment.getExternalStorageDirectory();
//	        File file = new File(path, logFile);			//This is either created when restoring or extracted from saved settings
//		    Debug.i(TAG, FUNC_TAG, file.getAbsolutePath());
//	
//		    try {
//		        OutputStream os = new FileOutputStream(file, true);
//		        logReady = true;
//		        
//		        SimpleDateFormat sdfDate = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
//			    Date now = new Date();
//			    String strDate = sdfDate.format(now);
//			    
//		        String data = strDate+ " = createLogFile > Dexcom BT Driver started...Log file created\n";
//		        os.write(data.getBytes());
//		        data = strDate+ " = createLogFile > File location: " + file.getAbsolutePath()+"\n";
//		        os.write(data.getBytes());
//		        os.close();
//		        
//		        MediaScannerConnection.scanFile(this.getApplicationContext(), new String[] { file.toString() }, null,
//	        		new MediaScannerConnection.OnScanCompletedListener() {
//	            		public void onScanCompleted(String path, Uri uri) {
//	            		}	
//	        		}
//		        );
//		    } catch (IOException e) {
//		        Debug.w("ExternalStorage", FUNC_TAG, "Error writing " + file, e);
//		    }
//		}
//		else
//			Debug.i(TAG, FUNC_TAG, "Log file unable to be created...not mountable or writeable");
//	}
}
