package edu.virginia.dtc.RocheDriver;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import edu.virginia.dtc.RocheData.Application;
import edu.virginia.dtc.RocheData.Key;
import edu.virginia.dtc.RocheData.Packet;
import edu.virginia.dtc.RocheData.Security;
import edu.virginia.dtc.RocheData.Transport;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Pump;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class Driver 
{
	public static final String TAG = "RocheDriver";
	public static final String PREFS = "RochePrefs";
	
	// Required static information belonging to the driver, DRIVER_NAME must match meta-data string
	public static final String DRIVER_NAME = "Roche";
	public static final String DEVICE_CXN = "BT";
	
	// Intents for binding connections
	public static final String PUMP_INTENT = "Driver.Pump." + DRIVER_NAME;
	public static final String UI_INTENT = "Driver.UI." + DRIVER_NAME;
	
	//Primary modes of operation and states for the Driver
	public static final int NONE = 0;
	public static final int PAIRING_AUTH = 1;
	public static final int COMMAND = 2;
	public static final int RT = 3;
	public static final int IDLE = 4;
	
	public static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	public static ScheduledFuture<?> connectTimer, timeoutTimer;
	
	public SharedPreferences settings;
	
	private static Driver instance = null;
	
	public ArrayAdapter<String> histList;
	
	public Device pump;
	
	public static boolean retrying = false;
	
	public String deviceMac;
	
	//UI global storage
	public String appFsm = "Unknown", txFsm = "Unknown", command = "Unknown";
	public String histEvents = "N/A", histRemEvents = "N/A";
	
	public int tbrDuration = 30;
	public int tbrTarget = 0;
	public boolean cancelBolus = false;
	public static boolean firstRun;
	
	public static History history;
	
	public boolean histRead = false, startMode = false, timeSync = false;
	public boolean sdpFound = false;
	
	public Context ui;
	public static Context serv;
	
	public Transport t;
	public Application a;
	
	public RocheDB db;
	
	public static Stats stats;
	
	public RocheUI main;
	
	//Roche Parameters**************************************************************************************//
	
	public byte[] k_10;
	public Object pd_key, dp_key;

	public byte addresses, seqNo, recvSeqNo;
	public int serverId;
	public String deviceId;
	
	public static int mode, prevMode;
	public static int uiState;
	
	public View discoveryView, LogView;
	
	public static boolean sendConnect = true, sendCommand = true;
	
	//******************************************************************************************************//
	
	protected Driver()
	{			
		firstRun = true;
		
		pump = new Device();
		
		history = new History();
		
		stats = new Stats();
		
		t = new Transport(this);
		a = new Application(this);
	}
	
	public static void log(String service, String function, String action) 
	{
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", function + " >> " + action);
        i.putExtra("priority", 5);
        i.putExtra("time", System.currentTimeMillis()/1000);
        serv.sendBroadcast(i);
	}
	
	public void resetDriver()
	{
		final String TAG = "RocheDriver";
		final String FUNC_TAG = "resetDriver";
		
		Debug.i(TAG, FUNC_TAG, "Restart begun ----------------------------------------------");
		Debug.i(TAG, FUNC_TAG, "Removing paired flag...");
		
		firstRun = true;
		
		if(Driver.timeoutTimer != null)
		{
			Driver.timeoutTimer.cancel(true);
			Driver.timeoutTimer = null;
		}
		
		if(Driver.connectTimer != null)
		{
			Driver.connectTimer.cancel(true);
			Driver.connectTimer = null;
		}
		
		setMode(Driver.NONE);
		
		Editor edit = settings.edit();
		edit.putBoolean("paired", false);		//Set flag to false
		edit.commit();
		
		InterfaceData data = InterfaceData.getInstance();
		
		//Phone has to be listening so the pump can use SDP to find it
		if(InterfaceData.remotePumpBt == null)	
		{
			Debug.i(TAG, FUNC_TAG, "New pump connection created and SDP setup!");    		
		}
		else
		{
			Debug.i(TAG, FUNC_TAG, "Stopping current BT connection...");
			InterfaceData.remotePumpBt.stop();
		}
		
		Debug.i(TAG, FUNC_TAG, "Putting BT in listening mode...");
		InterfaceData.remotePumpBt = new BluetoothConn(data.bt, InterfaceData.PUMP_UUID, "SerialLink", true);
		InterfaceData.remotePumpBt.listen();
		
		Debug.i(TAG, FUNC_TAG, "Resetting UI and booleans...");
		
		sendCommand = true;
		sendConnect = true;
		uiState = 0;
		
		Debug.i(TAG, FUNC_TAG, "Resetting Application...");
		a = new Application(this);
		
		Debug.i(TAG, FUNC_TAG, "Resetting Transport...");
		t = new Transport(this);
		
		updatePumpState(Pump.NONE);
		
		Debug.i(TAG, FUNC_TAG, "Restart complete -----------------------------------------");
	}
	
	public static Driver getInstance()
	{
		if(instance == null)
		{
			synchronized(Driver.class)
			{
				if(instance == null)
					instance = new Driver();
			}
		}
		return instance;
	}
	
	public void updateDevState(int state)
	{
		ContentValues cv = new ContentValues();
		cv.put("dev_resp", state);
		serv.getContentResolver().update(Biometrics.STATE_URI, cv, null, null);
	}
	
	public void updatePumpState(int state)
	{
		final String FUNC_TAG = "updatePumpState";
		
		if(Driver.getInstance().pump != null)
		{
			if(Driver.getInstance().pump.devState != state)
			{
				Debug.i("Application", FUNC_TAG, "State Change: "+state);
				
				Driver.getInstance().pump.devState = state;
			
				ContentValues pv = new ContentValues();
				pv.put("state", state);
				
				serv.getContentResolver().update(Biometrics.PUMP_DETAILS_URI, pv, null, null);
				
				updateDevices();
			}
		}
	}
	
	public static void updateDevices()
	{
		Intent intent = new Intent("edu.virginia.dtc.DEVICE_RESULT");
		
		if(Driver.getInstance().pump.devState >= Pump.CONNECTED)
			intent.putExtra("pumps",  1);
		else
			intent.putExtra("pumps",  0);
		
		intent.putExtra("started", true);
		intent.putExtra("name", Driver.DRIVER_NAME);
		Driver.getInstance().serv.sendBroadcast(intent);
	}
	
	public static void setMode(int state)
	{
		prevMode = mode;
		mode = state;
	}
	
	public static int getMode()
	{
		return mode;
	}
	
	public class Device
	{
		public int devState = Pump.NONE;
		
		public Device()
		{}
	}
	
	public class History
	{
		public static final int NO_GAP 		= 0xB7;		//No history gap
		public static final int NORMAL_GAP 	= 0x48;		//Normal gap, history counter not reset
		public static final int RESET_GAP	= 0xB8;		//History counter reset
		
		public int remainingEvents;			
		public int totalEvents;
		public boolean endReached;
		public int historyGap;
		
		public History(){}
	}
	
	public class Events
	{
		public static final int UNPROCESSED = 0;
		public static final int PROCESSED = 1;
		
		public long timestamp;				//Values that are from the history entry
		public long bolusId;
		
		public short eventID;
		public short checksum;
		public short checksumCounter;
		public long eventCounter;
		
		public int status;
		public int isProcessed;
		
		byte[] data;
		
		public double bolus;				//Values for ease of use for us
		public String description;
		
		public Events(){}
		
		public Events(long bid, short eid, short check, long ecnt, short checkcnt, byte[] d, int p)
		{
			bolusId = bid;
			eventID = eid;
			checksum = check;
			eventCounter = ecnt;
			checksumCounter = checkcnt;
			data = d;
			isProcessed = p;
		}
		
		public String getEventString()
		{
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm.ss");
		 	String date = sdf.format(new Date(this.timestamp*1000));
			String s = date + " | " + this.bolusId + " | " + this.eventCounter + " | " + this.eventID + " | " + this.description + " | " + this.bolus +" U | isProcessed: "+this.isProcessed;
			
			return s;
		}
	}
	
	public class InPacket
	{
		public byte[] packet;
		public boolean reliable;
		
		public InPacket(byte[] p, boolean r)
		{
			packet = p;
			reliable = r;
		}
	}
	
	public class OutPacket
	{
		public byte[] packet;
		public String descrip;
		public byte sequence;
		
		public OutPacket(byte[] p, boolean r, String s, byte seq)
		{
			packet = p;
			descrip = s;
			sequence = seq;
		}
	}

	public class packetObj
	{
		public short expResp;
		public String descrip;
		public ByteBuffer buffer;
		public long maxRetries, timesRetried;
		public long timeout;
		public int bolusAmount;
		
		public packetObj(ByteBuffer b, String s, short r, long ret, long t, int bol)
		{
			buffer = b;
			descrip = s;
			expResp = r;
			maxRetries = ret;
			timesRetried = 0;
			timeout = t;
			bolusAmount = bol;
		}
	}
	
	public class Stats
	{
		public int txAppPackets, rxAppPackets;
		public int txPackets, rxPackets;
		public int skippedPackets;
		public int timeouts;
	}
	
	public class rtFrame
	{
		public int index;
		public String[] row1 = new String[8];
		public String[] row2 = new String[8];
		public String[] row3 = new String[8];
		public String[] row4 = new String[8];
		public boolean r1,r2,r3,r4;
		public byte reason;
		
		public rtFrame()
		{
			index = -1;						//This is an invalid value (valid are 0-255)
			r1 = r2 = r3 = r4 = false;		//Invalidate rows
		}
		
		public void addR1(String[] s)
		{
			row1 = s;
			r1 = true;
		}
		
		public void addR2(String[] s)
		{
			row2 = s;
			r2 = true;
		}
		
		public void addR3(String[] s)
		{
			row3 = s;
			r3 = true;
		}
		public void addR4(String[] s)
		{
			row4 = s;
			r4 = true;
		}
		
		public boolean isComplete()
		{
			if(r1 && r2 && r3 &&r4)
				return true;
			else
				return false;
		}
	}
}
