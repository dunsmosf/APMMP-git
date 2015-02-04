package edu.virginia.dtc.RocheData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.Bundle;
import edu.virginia.dtc.RocheDriver.BluetoothConn;
import edu.virginia.dtc.RocheDriver.Driver;
import edu.virginia.dtc.RocheDriver.Driver.Events;
import edu.virginia.dtc.RocheDriver.Driver.InPacket;
import edu.virginia.dtc.RocheDriver.Driver.packetObj;
import edu.virginia.dtc.RocheDriver.Driver.rtFrame;
import edu.virginia.dtc.RocheDriver.InterfaceData;
import edu.virginia.dtc.RocheDriver.RocheDriver;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.FSM;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;

public class Application 
{
	public static final byte BOLUS_DATA_SIZE                     =(byte)22;           	// number bytes in bolus info

	// Application layer control commands
	public static final byte AL_VERSION_MAJOR                    =(byte)0x01;         	// application major version # (upper 4 bits of version #)
	public static final byte AL_VERSION_MINOR                    =(byte)0x00;         	// application major version # (lower 4 bits of version #)
	public static final byte AL_VERSION                          =(byte)((AL_VERSION_MAJOR <<4)|AL_VERSION_MINOR);        // application layer version number major|minor
	public static final byte AL_CONTROL                          =(byte)0x00;         	// application layer control service ID
	public static final byte AL_CONNECT_REQ_MSB                  =(byte)0x90;         	// application layer connect request - MSB
	public static final byte AL_CONNECT_REQ_LSB                  =(byte)0x55;         	// application layer connect request - LSB
	public static final byte AL_DISCONNECT_MSB                   =(byte)0x00;         	// application layer disconnect - MSB 
	public static final byte AL_DISCONNECT_LSB                   =(byte)0x5A;         	// application layer disconnect - LSB 
	public static final byte AL_SERVICE_ACTIVATE_REQ_MSB         =(byte)0x90;         	// application layer activate service request - MSB
	public static final byte AL_SERVICE_ACTIVATE_REQ_LSB         =(byte)0x66;         	// application layer activate service request - LSB
	public static final byte AL_SERVICE_DEACTIVATE_REQ_MSB       =(byte)0x90;         	// application layer deactivate service request - MSB
	public static final byte AL_SERVICE_DEACTIVATE_REQ_LSB       =(byte)0x69;         	// application layer deactivate service request - MSB
	public static final byte AL_SERVICE_DEACTIVATE_ALL_REQ_MSB   =(byte)0x90;         	// application layer deactivate service all request - MSB
	public static final byte AL_SERVICE_DEACTIVATE_ALL_REQ_LSB   =(byte)0x6A;         	// application layer deactivate service all request - LSB
	public static final byte AL_ERROR_MSB                        =(byte)0x00;         	// application layer error - MSB
	public static final byte AL_ERROR_LSB                        =(byte)0xAA;         	// application layer error - LSB

	public static final short AL_SERVICE_VERSION_REQ             =(short)0x9065;		// application layer service version request
	public static final short AL_BINDING_REQ                     =(short)0x9095;    	// application layer binding
		
	public static final int APP_SEND_CONNECT = 0;
	public static final int APP_SEND_DISCONNECT = 1;
	public static final int APP_SERVICE_VERSION = 2;
	public static final int APP_SERVICE_ACTIVATE = 3;
	public static final int APP_SERVICE_DEACTIVATE = 4;
	public static final int APP_SERVICE_DEACTIVATE_ALL = 5;
	public static final int APP_ERROR = 6;
	public static final int APP_COMMAND = 7;
	public static final int APP_DISCONNECT_LINK = 8;
	public static final int APP_SEND_KEY = 9;
	public static final int APP_SEND_ALIVE = 10;
	
	// Command IDs
	public static final short CBOL_BOLUS_AVAILABLITY_CMD                   = (short)0x9656;
	public static final short CBOL_BOLUS_DELIVER_CMD                       = (short)0x9669;
	public static final short CBOL_READ_BOLUS_SETTINGS_CMD                 = (short)0x9659;
	public static final short CBOL_READ_LAST_EXT_SETTINGS_CMD              = (short)0x965A;
	public static final short CBOL_READ_LAST_MW_SETTINGS_CMD               = (short)0x9665;
	public static final short CBOL_IMMEDIATE_BOLUS_STATUS_CMD              = (short)0x966A;
	public static final short CBOL_CANCEL_BOLUS_CMD                        = (short)0x9695;
	public static final short READ_HISTORY_BLOCK_CMD                       = (short)0x9996;
	public static final short CONFIRM_HISTORY_BLOCK_CMD                    = (short)0x9999;
	public static final short READ_OP_STATUS_CMD                           = (short)0x9A9A;
	public static final short READ_ERROR_WARNING_CMD                       = (short)0x9AA5;
	public static final short READ_TIME_DATE_CMD                           = (short)0x9AA6;
	public static final short RETRIGGER_AUTO_OFF_CMD                       = (short)0x9AA9;
	public static final short PING_CMD                                     = (short)0x9AAA;
	
	public static final int NONE = -1;
	public static final int COMMANDS_SERVICES_VERSION = 0;
	public static final int REMOTE_TERMINAL_VERSION = 1;
	public static final int BINDING = 2;
	public static final int BOLUS_AVAILABILITY = 3;
	public static final int DELIVER_BOLUS = 4;
	public static final int READ_BOLUS_SETTINGS = 5;
	public static final int READ_LAST_EXT_SETTINGS = 6;
	public static final int READ_LAST_MW_SETTINGS = 7;
	public static final int IMMEDIATE_BOLUS_STATUS = 8;
	public static final int CANCEL_BOLUS = 9;
	public static final int READ_HISTORY_BLOCK = 10;
	public static final int CONFIRM_HISTORY_BLOCK = 11;
	public static final int READ_OPERATION_STATUS = 12;
	public static final int READ_ERROR_WARNING_STATUS = 13;
	public static final int READ_TIME_DATE = 14;
	public static final int RETRIGGER_AUTO_OFF = 15;
	public static final int PING = 16;
	public static final int SEND_RT_KEY_STATUS = 17;
	public static final int SEND_RT_ALIVE = 18;
	
	public static final int COMMANDS_COMMAND_ACTIVATE = 19;
	public static final int COMMANDS_RT_ACTIVATE = 20;
	
	public static final int COMMANDS_COMMAND_DEACTIVATE = 25;
	public static final int COMMANDS_RT_DEACTIVATE = 26;
	
	public static final byte REMOTE_TERMINAL_SERVICE_ID      = (byte) 0x48;
	public static final byte COMMAND_MODE_SERVICE_ID         = (byte) 0xB7;
	
	//Reliable Command Response IDs
	public static final short AL_CONNECT_RES					=(short)0xA055;
	public static final short AL_SERVICE_VERSION_RES			=(short)0xA065;
	public static final short AL_SERVICE_ACTIVATE_RES			=(short)0xA066;
	public static final short AL_BINDING_RES					=(short)0xA095;
	public static final short AL_DISCONNECT_RES					=(short)0x005A;
	public static final short AL_SERVICE_DEACTIVATE_RES			=(short)0xA069;
	public static final short AL_SERVICE_DEACTIVATE_ALL_RES		=(short)0xA06A;
	public static final short AL_SERVICE_ERROR_RES				=(short)0x00AA;
	public static final short PING_RES							=(short)0xAAAA;
	public static final short HIST_READ_RES						=(short)0xA996;
	public static final short HIST_CONF_RES						=(short)0xA999;
	public static final short CBOL_BOLUS_STATUS					=(short)0xA66A;
	public static final short CBOL_BOLUS_DELIVER				=(short)0xA669;
	public static final short CBOL_CANCEL_BOLUS					=(short)0xA695;
	
	public static final short READ_OP_STATUS_RESP				=(short)0xAA9A;
	public static final short READ_ERR_STATUS_RESP				=(short)0xAAA5;
	public static final short READ_TIME_RESP					=(short)0xAAA6;
	
	//Unreliable Command Response IDs
	public static final short RT_DISPLAY						=(short)0x0555;
	public static final short RT_KEY_CONF						=(short)0x0556;
	public static final short RT_AUDIO							=(short)0x0559;
	public static final short RT_VIB							=(short)0x055A;
	public static final short RT_KEY_STATUS						=(short)0x0565;
	public static final short RT_ALIVE							=(short)0x0566;
	public static final short RT_PAUSE							=(short)0x0569;
	public static final short RT_RELEASE						=(short)0x056A;

	//RT Keys
	public static byte NO_KEY				=(byte)0x00;
	public static byte MENU					=(byte)0x03;
	public static byte CHECK				=(byte)0x0C;
	public static byte UP					=(byte)0x30;
	public static byte DOWN					=(byte)0xC0;
	public static byte MENU_CHECK			=(byte)0x0F;
	public static byte MENU_UP				=(byte)0x33;
	public static byte MENU_DOWN			=(byte)0xC3;
	public static byte CHECK_UP				=(byte)0x3C;
	public static byte CHECK_DOWN			=(byte)0xCC;
	public static byte UP_DOWN				=(byte)0xF0;
	public static byte MENU_CHECK_UP		=(byte)0x3F;
	public static byte MENU_CHECK_DOWN		=(byte)0xCF;
	public static byte MENU_UP_DOWN			=(byte)0xF3;
	public static byte CHECK_UP_DOWN		=(byte)0xFC;
	public static byte MENU_CHECK_UP_DOWN	=(byte)0xFF;
	
	private static final String TAG = "Application";

	private static final byte COMMAND_MODE_MAJOR_VERSION = 0x01;
	private static final byte COMMAND_MODE_MINOR_VERSION = 0x00;
	
	private static final byte RT_MODE_MAJOR_VERSION = 0x01;
	private static final byte RT_MODE_MINOR_VERSION = 0x00;
	
	private static byte[] connect_app_layer = new byte[]
	{                                                                                                                                              
	// VERSION: 8 bits \    Service ID \               Command ID     
	        AL_VERSION    ,  AL_CONTROL,    AL_CONNECT_REQ_LSB, AL_CONNECT_REQ_MSB
	};
	private static byte[] disconnect_app_layer = new byte[]
	{                                                                                                                                              
	// VERSION: 8 bits \    Service ID \                Command ID     
	        AL_VERSION    ,  AL_CONTROL,    AL_DISCONNECT_LSB,  AL_DISCONNECT_MSB
	};
	private static byte[] service_activate = new byte[]
	{                                                                                                                                              
	// VERSION: 8 bits \    Service ID \                Command ID     
	        AL_VERSION    , AL_CONTROL ,    AL_SERVICE_ACTIVATE_REQ_LSB, AL_SERVICE_ACTIVATE_REQ_MSB
	};
	private static byte[] service_deactivate = new byte[]
	{                                                                                                                                              
	// VERSION: 8 bits \    Service ID \                Command ID     
	        AL_VERSION    ,  AL_CONTROL,    AL_SERVICE_DEACTIVATE_REQ_LSB, AL_SERVICE_DEACTIVATE_REQ_MSB
	};
	private static byte[] service_deactivate_all = new byte[]
	{                                                                                                                                              
	// VERSION: 8 bits \    Service ID \                Command ID     
	        AL_VERSION    ,  AL_CONTROL,    AL_SERVICE_DEACTIVATE_ALL_REQ_LSB, AL_SERVICE_DEACTIVATE_ALL_REQ_MSB
	};
	private static byte[] error_app_layer = new byte[]
	{                                                                                                                                              
	// VERSION: 8 bits \    Service ID \                Command ID     
	        AL_VERSION    ,  AL_CONTROL,    AL_ERROR_LSB, AL_ERROR_MSB
	};
	
	private int modeErrorCount = 0;
	private short rtSeq = 0;
	
	private Driver drv;
	private int bolusDelay = 0;
	private long bolusId = -1;
	private long histId = -1;
	
	boolean reconnecting = false;
	
	private long levelCount = -1;
	private long pingTime, aliveTime, retryTime;
	
	private static final int MODE_ERROR_THRESH = 5;
	
	private static final int APP_THREAD_SLEEP = 10;
	public static Queue<InPacket> appMessages;
	private static volatile Thread app;
	private static boolean appRunning, appStop;
	
	private static int state, prevState;
	private int bolusState = BOLUS_CMD_IDLE, prevBolusState = BOLUS_CMD_IDLE; 
	
	private LinkedList<packetObj> tx = new LinkedList<packetObj>();
	private LinkedList<Short> rx = new LinkedList<Short>();
	private packetObj currentTx;
	
	private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private static ScheduledFuture<?> statusTimer, rtTimer, disconnectTimer, warningTimer, commandTimer, checkTimer;
	
	private static boolean connecting = false, wasConnected = false;
	private String descrip = "";
	
	private int rtState = 0, prevRtState = 0;
	private boolean tbrRefresh = false;
	private static rtFrame frame;
	
	private long start, stop;
	
	private int alConnectErrors = 0;
	
	/***********************************************************************************************/
	/***********************************************************************************************/
	// Runnables and Startup Functions
	/***********************************************************************************************/
	/***********************************************************************************************/
	
	private Runnable check = new Runnable()
	{
		final String FUNC_TAG = "check";
		
		public void run()
		{
			Debug.e(TAG, FUNC_TAG, "Still checking on the bolus being delivered!");
			cmdBolusStatus();
		}
	};
	
	private Runnable command = new Runnable()
	{
		final String FUNC_TAG = "bolusCommand";
		
		public void run()
		{
			Debug.e(TAG, FUNC_TAG, "");
			setBolusState(BOLUS_CMD_IDLE);
		}
	};
	
	private Runnable warning = new Runnable()
	{
		public void run()
		{
			//Fire warning event
			Bundle b = new Bundle();
    		b.putString("description", "The pump has been disconnected for 15 minutes!  Please reconnect or move closer to the pump.");
			Event.addEvent(drv.serv, Event.EVENT_PUMP_DISCONNECT_WARN, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE);
		}
	};
	
	private Runnable disconnect = new Runnable()
	{
		public void run()
		{
			//Fire disconnect event
			Bundle b = new Bundle();
    		b.putString("description", "The pump has been disconnected for 20 minutes!  Disconnecting from system.");
			Event.addEvent(Driver.serv, Event.EVENT_PUMP_DISCONNECT_TIMEOUT, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE);
			
			drv.updatePumpState(Pump.DISCONNECTED);
		}
	};
	
	private Runnable rt = new Runnable()
	{
		final String FUNC_TAG = "rt";
		
		public void run()
		{
			if(Driver.getMode() == Driver.RT)
			{
				Debug.e(TAG, FUNC_TAG, "Timeout firing from RT mode, switching to Command mode!");
				startMode(Driver.COMMAND, false);
			}
			else
				Debug.i(TAG, FUNC_TAG, "Already out of RT mode so all is well...");
		}
	};
	
	private Runnable status = new Runnable()
	{
		final String FUNC_TAG = "status";

		public void run()
		{
			Debug.i(TAG, FUNC_TAG, "Sending bolus status check!");
			cmdBolusStatus();
		}
	};
	
	public void stopRunnables()
	{
		if(statusTimer != null)
			statusTimer.cancel(true);
		if(rtTimer != null)
			rtTimer.cancel(true);
		if(disconnectTimer != null)
			disconnectTimer.cancel(true);
		if(warningTimer != null)
			warningTimer.cancel(true);
		if(commandTimer != null)
			commandTimer.cancel(true);
	}
	
	public Application(Driver driver)
	{
		stopRunnables();
		
		tx.clear();
		rx.clear();
		
		drv = driver;
		state = Driver.NONE;
		prevState = Driver.NONE;
		bolusState = BOLUS_CMD_IDLE;
		
		frame = drv.new rtFrame();
		
		appStop = true;
		if(app != null)
		{
			if(app.isAlive())
			{
				try {
					app.join();
					appRunning = false;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		startAppThread();
	}
	
	/***********************************************************************************************/
	//	   ___            _           ___                       _           
	//	  | _ \___ __ ___(_)_ _____  | _ \_ _ ___  __ ___ _____(_)_ _  __ _ 
	//	  |   / -_) _/ -_) \ V / -_) |  _/ '_/ _ \/ _/ -_|_-<_-< | ' \/ _` |
	//	  |_|_\___\__\___|_|\_/\___| |_| |_| \___/\__\___/__/__/_|_||_\__, |
	//	                                                              |___/                    
	/***********************************************************************************************/
	
	private void startAppThread()
	{
		final String FUNC_TAG = "startAppThread";
		
		appMessages = new ConcurrentLinkedQueue<Driver.InPacket>();
		
		if(!appRunning)
		{
			appRunning = true;
			
			app = new Thread()
			{
				public void run()
				{
					Debug.i("Thread", FUNC_TAG, "App Thread starting!");
					appStop = false;
					
					while(!appStop)
					{
						InPacket ip = appMessages.poll();					//Get and process new incoming data
						if(ip != null)
						{
							processAppResponse(ip.packet, ip.reliable);
							Driver.stats.rxAppPackets++;
						}
						
						runSystem();										//Always run the state machine
						
						try {
							Thread.sleep(APP_THREAD_SLEEP);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			};
			
			app.start();
		}
	}
	
	private void processAppResponse(byte[] payload, boolean reliable)
	{
		final String FUNC_TAG = "processAppResponse";

		String descrip = "";
		ByteBuffer b = ByteBuffer.wrap(payload);
		b.order(ByteOrder.LITTLE_ENDIAN);
		
		byte mVersion = b.get();
		byte servId = b.get();
		short commId = b.getShort();
		
		Debug.i(TAG, FUNC_TAG, "Service ID: "+String.format("%X", servId)+" Comm ID: "+String.format("%X", commId));
		
		if(reliable)											//If its a reliable packet the next 2 bytes are an error code to be evaluated
		{
			short error = b.getShort();
			if(!cmdProcessError(error))
			{
				descrip = "Error";
				Debug.i(TAG, FUNC_TAG, "Packet type: "+descrip);
				return;
			}
		
			switch(commId)
			{
				case AL_CONNECT_RES:
					descrip = "AL_CONNECT_RES";
					if(getAppState() == Application.CONNECT_RESP)
						setAppState(Application.COMM_VER);
					else if(getAppState() == Application.SERVICE_CONNECT)			//We are restarting a connection after P&A
						setAppState(Application.SERVICE_ACTIVATE);
					break;
				case AL_SERVICE_VERSION_RES:
					descrip = "AL_SERVICE_VER_RES";
					switch(getAppState())
					{
						case Application.COMM_VER_RESP:
							setAppState(Application.RT_VER);
							break;
						case Application.RT_VER_RESP:
							setAppState(Application.BIND);
							break;
					}
					break;
				case AL_BINDING_RES:
					descrip = "AL_BINDING_RES";
					if(getAppState() == Application.BIND_RESP)
					{
						setAppState(Application.BOUND);
					}
					break;
				case AL_SERVICE_ACTIVATE_RES:
					descrip = "AL_SERVICE_ACTIVATE_RES";
					if(getAppState() == Application.SERVICE_ACTIVATE_SENT)
					{	
						setAppState(Application.SERVICE_STARTUP);						
					}
					Debug.i(TAG, FUNC_TAG, "Service activate response!");
					break;
				case AL_DISCONNECT_RES:
					descrip = "AL_DISCONNECT_RES";
					Debug.i(TAG, FUNC_TAG, "Disconnect response!");
					
					break;
				case AL_SERVICE_DEACTIVATE_RES:
					descrip = "AL_DEACTIVATE_RES";
					Debug.i(TAG, FUNC_TAG, "Service deactivate response!");
					break;
				case AL_SERVICE_ERROR_RES:
					descrip = "AL_SERVICE_ERROR_RES";
					Debug.i(TAG, FUNC_TAG, "Service error response!");
					break;
				case AL_SERVICE_DEACTIVATE_ALL_RES:
					descrip = "AL_DEACTIVATE_ALL_RES";
					Debug.i(TAG, FUNC_TAG, "Service deactivate all response!  Activating desired service...");
					setAppState(Application.SERVICE_ACTIVATE);
					break;
				case PING_RES:
					descrip = "PING_RES";
					break;
				case HIST_READ_RES:
					descrip = "HISTORY_READ_RES";
					cmdProcessHistory(b);
					break;
				case HIST_CONF_RES:
					descrip = "HISTORY_CONF_RES";
					
					if(getBolusState() == BOLUS_CMD_HIST_CONF_RESP)
					{
						Debug.i(TAG, FUNC_TAG, "Bolus cycle complete, block confirmed, moving to idle state!");
						setBolusState(BOLUS_CMD_IDLE);
					}
					break;
				case CBOL_CANCEL_BOLUS:
					descrip = "CBOL_CANCEL_BOLUS";
					cmdProcessCancelBolus(b);
					break;
				case CBOL_BOLUS_STATUS:
					descrip = "CBOL_BOLUS_STATUS";
					cmdProcessBolusStatus(b);
					break;
				case CBOL_BOLUS_DELIVER:
					descrip = "CBOL_BOLUS_DELIVER";
					cmdProcessBolusDeliver(b);
					break;
				case READ_OP_STATUS_RESP:
					descrip = "READ_OP_STATUS_RESP";
					cmdProcessOpStatus(b);
					break;
				case READ_ERR_STATUS_RESP:
					descrip = "READ_ERR_STATUS_RESP";
					cmdProcessErrStatus(b);
					break;
				case READ_TIME_RESP:
					descrip = "READ_TIME_RESP";
					cmdProcessTime(b);
					break;
				default:
					descrip = "UNKNOWN";
					break;
			}
		}
		else
		{
			switch(commId)
			{
				case RT_DISPLAY:
					descrip = "RT_DISPLAY";
					rtProcessDisplay(b);
					break;
				case RT_KEY_CONF:
					descrip = "RT_KEY_CONF";
					rtProcessKeyConfirmation(b);
					break;
				case RT_AUDIO:
					descrip = "RT_AUDIO";
					rtProcessAudio(b);
					break;
				case RT_VIB:
					descrip = "RT_VIB";
					rtProcessVibe(b);
					break;
				case RT_ALIVE:
					descrip = "RT_ALIVE";
					rtProcessAlive(b);
					break;
				case RT_PAUSE:
					descrip = "RT_PAUSE";
					break;
				case RT_RELEASE:
					descrip = "RT_RELEASE";
					break;
				default:
					descrip = "UNKNOWN";
					break;
			}
		}
		
		rx.add(commId);
		
		Debug.i(TAG, FUNC_TAG, "Packet Type: "+descrip);
	}

	/***********************************************************************************************/
	//	   _____                       _ _     ___                       _           
	//	  |_   _| _ __ _ _ _  ____ __ (_) |_  | _ \_ _ ___  __ ___ _____(_)_ _  __ _ 
	//	    | || '_/ _` | ' \(_-< '  \| |  _| |  _/ '_/ _ \/ _/ -_|_-<_-< | ' \/ _` |
	//	    |_||_| \__,_|_||_/__/_|_|_|_|\__| |_| |_| \___/\__\___/__/__/_|_||_\__, |
	//	                                                                       |___/ 
	/***********************************************************************************************/
	
	private void sendAppLayerCommand(int command, int subCommand)
	{
		ByteBuffer payload = null;
		descrip = "";
		
		switch(command)
		{
			case APP_SEND_CONNECT:
				descrip = "APP_SEND_CONNECT";
				payload = ByteBuffer.allocate(8);				//4 bytes for application header, 4 for payload
				payload.put(connect_app_layer);					//Add prefix array
				payload.order(ByteOrder.LITTLE_ENDIAN);
				payload.putInt(Integer.parseInt("12345"));		//Add the serial number payload				
				break;
			case APP_SEND_DISCONNECT:
				descrip = "APP_SEND_DISCONNECT";
				payload = ByteBuffer.allocate(6);
				payload.put(disconnect_app_layer);
				payload.order(ByteOrder.LITTLE_ENDIAN);
				payload.putShort((short) 0x6003);				//Regular termination by user
				break;
			case APP_SERVICE_VERSION:
				break;
			case APP_SERVICE_ACTIVATE:
				descrip = "APP_SERVICE_ACTIVATE";
				payload = addSubCommand(subCommand);
				break;
			case APP_SERVICE_DEACTIVATE:
				descrip = "APP_SERVICE_DEACTIVATE";
				payload = addSubCommand(subCommand);
				break;
			case APP_SERVICE_DEACTIVATE_ALL:
				payload = ByteBuffer.allocate(4);
				payload.put(service_deactivate_all);
				break;
			case APP_ERROR:
				break;
			case APP_COMMAND:
				descrip = "APP_COMMAND";
				payload = addSubCommand(subCommand);
				break;
			case APP_DISCONNECT_LINK:
				break;
			default:
				break;
		}
		
		sendData(payload, descrip, (short)0, MAX_RETRIES, PKT_TIMEOUT, 0);
	}
	
	private ByteBuffer addSubCommand(int command)
	{
		ByteBuffer payload = null;
		
		String s = "";
			
		switch(command)
		{
			case COMMANDS_COMMAND_ACTIVATE:
				s = "COMMAND_ACTIVATE";
				payload = ByteBuffer.allocate(7);
				payload.put(service_activate);
				payload.put(COMMAND_MODE_SERVICE_ID);
				payload.put(COMMAND_MODE_MAJOR_VERSION);
				payload.put(COMMAND_MODE_MINOR_VERSION);
				break;
			case COMMANDS_COMMAND_DEACTIVATE:
				s = "COMMAND DEACTIVATE";
				payload = ByteBuffer.allocate(5);
				payload.put(service_deactivate);
				payload.put(COMMAND_MODE_SERVICE_ID);
				break;
			case COMMANDS_RT_ACTIVATE:
				s = "RT_ACTIVATE";
				payload = ByteBuffer.allocate(7);
				payload.put(service_activate);
				payload.put(REMOTE_TERMINAL_SERVICE_ID);
				payload.put(RT_MODE_MAJOR_VERSION);
				payload.put(RT_MODE_MINOR_VERSION);
				break;
			case COMMANDS_RT_DEACTIVATE:
				s = "RT DEACTIVATE";
				payload = ByteBuffer.allocate(5);
				payload.put(service_deactivate);
				payload.put(REMOTE_TERMINAL_SERVICE_ID);
				break;
			case COMMANDS_SERVICES_VERSION:
				s = "COMMAND_SERVICES_VERSION";
				payload = ByteBuffer.allocate(5);
				payload.put(AL_VERSION);
				payload.put(AL_CONTROL);
				payload.put((byte) (AL_SERVICE_VERSION_REQ & 0xFF));
				payload.put((byte) ((AL_SERVICE_VERSION_REQ>>8) & 0xFF));
				payload.put(COMMAND_MODE_SERVICE_ID);
	            break;
	        case REMOTE_TERMINAL_VERSION:
	        	s = "REMOTE_TERMINAL_VERSION";
	        	payload = ByteBuffer.allocate(5);
				payload.put(AL_VERSION);
				payload.put(AL_CONTROL);
				payload.put((byte) (AL_SERVICE_VERSION_REQ & 0xFF));
				payload.put((byte) ((AL_SERVICE_VERSION_REQ>>8) & 0xFF));
				payload.put(REMOTE_TERMINAL_SERVICE_ID);
	            break;
	        case BINDING:
	        	s = "BINDING";
	        	payload = ByteBuffer.allocate(5);
				payload.put(AL_VERSION);
				payload.put(AL_CONTROL);
				payload.put((byte) (AL_BINDING_REQ & 0xFF));
				payload.put((byte) ((AL_BINDING_REQ>>8) & 0xFF));
	        	payload.put((byte) 0x48);		//Binding OK
	            break;
			case NONE:
			default:
				s = "";
				break;
		}
		
		descrip += " " + s;
		
		return payload;
	}
	
	private void sendData(ByteBuffer payload, String descrip, short resp, long maxRetry, long timeout, int bolus)
	{
		final String FUNC_TAG = "sendData";

		Debug.i(TAG, FUNC_TAG, "Adding "+descrip+" to TX queue...");
		tx.add(drv.new packetObj(payload, descrip, resp, maxRetry, timeout, bolus));
	}

	/***********************************************************************************************/
	//	   ___ _____   __  __         _     
	//	  | _ \_   _| |  \/  |___  __| |___ 
	//	  |   / | |   | |\/| / _ \/ _` / -_)
	//	  |_|_\ |_|   |_|  |_\___/\__,_\___|
	//	                                    
	/***********************************************************************************************/
	
	// RT Transmit Functions
	// *********************************************************************************************
	private void rtAlive()
	{
		ByteBuffer payload = ByteBuffer.allocate(6);
		payload.put(AL_VERSION);
    	payload.put(REMOTE_TERMINAL_SERVICE_ID);
    	payload.put((byte) (RT_ALIVE & 0xFF));
		payload.put((byte) ((RT_ALIVE>>8) & 0xFF));
		
		payload.put((byte) (rtSeq & 0xFF));
		payload.put((byte) ((rtSeq>>8) & 0xFF));
		
		sendData(payload, "RT_ALIVE", (short)0, 0, 0, 0);
		rtSeq++;
	}

	private void rtSendKey(byte key, boolean changed)
	{
		ByteBuffer payload = ByteBuffer.allocate(8);
		
		payload.put(AL_VERSION);
    	payload.put(REMOTE_TERMINAL_SERVICE_ID);
    	payload.put((byte) 0x65);	
    	payload.put((byte) 0x05);
    	
    	payload.put((byte) (rtSeq & 0xFF));
		payload.put((byte) ((rtSeq>>8) & 0xFF));
		
		payload.put(key);
		
		if(changed)
			payload.put((byte) 0xB7);
		else
			payload.put((byte) 0x48);
    	
    	sendData(payload, "RT_KEY_STATUS", (short)0, 0, 0, 0);
    	rtSeq++;
	}
	
	// RT Receive Functions
	// *********************************************************************************************
	private void rtProcessVibe(ByteBuffer b)
	{
		final String FUNC_TAG = "processVib";
		
		short sequence = b.getShort();
		int vibe = b.getInt();
		
		Debug.i(TAG, FUNC_TAG, "Seq: "+sequence+" | Vibe: "+vibe);
	}
	
	private void rtProcessAudio(ByteBuffer b)
	{
		final String FUNC_TAG = "processAudio";
		
		short sequence = b.getShort();
		int audio = b.getInt();
		
		Debug.i(TAG, FUNC_TAG, "Seq: "+sequence+" | Audio: "+audio);
	}
	
	private void rtProcessAlive(ByteBuffer b)
	{
		final String FUNC_TAG = "processAlive";
		
		short sequence = b.getShort();
		
		Debug.i(TAG, FUNC_TAG, "Seq: "+sequence);
	}
	
	private void rtProcessKeyConfirmation(ByteBuffer b)
	{
		final String FUNC_TAG = "processKeyConfirmation";
		
		short sequence = b.getShort();
		
		Debug.i(TAG, FUNC_TAG, "Seq: "+sequence);
	}

	private void rtProcessDisplay(ByteBuffer b)
	{
		final String FUNC_TAG = "rtProcessDisplay";
		
		short sequence = b.getShort();
		byte reason = b.get();
		int index = (int)(b.get() & 0xFF);
		byte row = b.get();
		
		byte[] map = new byte[96];		//New array
		b.get(map);						//Read in array from packet
		
		String r = "";					//Determine reason
		if(reason == (byte)0x48)
			r = "Pump";
		else if(reason == (byte)0xB7)
			r = "DM";
		
		Debug.i(TAG, FUNC_TAG, "Seq: "+sequence+" | Reason: "+r+" | Index: "+index+" | Row: "+String.format("%X", row));
		
		String[] screen = new String[]{"","","","","","","",""};
		for(byte d:map)
		{
			if((d & 0x01) == 0x01)
				screen[0] += "8";
			else
				screen[0] += " ";
			
			if((d & 0x02) == 0x02)
				screen[1] += "8";
			else
				screen[1] += " ";
			
			if((d & 0x04) == 0x04)
				screen[2] += "8";
			else
				screen[2] += " ";
			
			if((d & 0x08) == 0x08)
				screen[3] += "8";
			else
				screen[3] += " ";
			
			if((d & 0x10) == 0x10)
				screen[4] += "8";
			else
				screen[4] += " ";
			
			if((d & 0x20) == 0x20)
				screen[5] += "8";
			else
				screen[5] += " ";
			
			if((d & 0x40) == 0x40)
				screen[6] += "8";
			else
				screen[6] += " ";
			
			if((d & 0x80) == 0x80)
				screen[7] += "8";
			else
				screen[7] += " ";
		}
		
		screen[0] = new StringBuffer(screen[0]).reverse().toString();
		screen[1] = new StringBuffer(screen[1]).reverse().toString();
		screen[2] = new StringBuffer(screen[2]).reverse().toString();
		screen[3] = new StringBuffer(screen[3]).reverse().toString();
		screen[4] = new StringBuffer(screen[4]).reverse().toString();
		screen[5] = new StringBuffer(screen[5]).reverse().toString();
		screen[6] = new StringBuffer(screen[6]).reverse().toString();
		screen[7] = new StringBuffer(screen[7]).reverse().toString();
		
		if(frame.index == -1)
		{
			Debug.w(TAG, FUNC_TAG, "Brand new frame!");
			frame.index = index;
			frame.reason = reason;
		}
		else if(frame.index != index)
		{
			Debug.w(TAG, FUNC_TAG, "Different index so we need to start a new frame!");
			frame = drv.new rtFrame();		//Generate a new frame
			frame.index = index;			//Copy the index
			frame.reason = reason;
		}
		
		if(row == (byte)0x47)		//Row 1
			frame.addR1(screen);
		else if(row == (byte)0x48)	//Row 2
			frame.addR2(screen);
		else if(row == (byte)0xB7)	//Row 3
			frame.addR3(screen);
		else if(row == (byte)0xB8)	//Row 4
			frame.addR4(screen);
		
		if(frame.isComplete())
		{
			//final String TAG = "rtFrame";
			
			Debug.w(TAG, FUNC_TAG, "Found complete frame!");
			
			int i = 0;	
			Debug.i(TAG, FUNC_TAG, "----------------------------------------------------------------------------------------------------");
			for(i = 0;i<8;i++)
				Debug.i(TAG, FUNC_TAG, frame.row1[i]);
			Debug.i(TAG, FUNC_TAG, "----------------------------------------------------------------------------------------------------");
			for(i = 0;i<8;i++)
				Debug.i(TAG, FUNC_TAG, frame.row2[i]);
			Debug.i(TAG, FUNC_TAG, "----------------------------------------------------------------------------------------------------");
			for(i = 0;i<8;i++)
				Debug.i(TAG, FUNC_TAG, frame.row3[i]);
			Debug.i(TAG, FUNC_TAG, "----------------------------------------------------------------------------------------------------");
			for(i = 0;i<8;i++)
				Debug.i(TAG, FUNC_TAG, frame.row4[i]);
			Debug.i(TAG, FUNC_TAG, "----------------------------------------------------------------------------------------------------");
		
			rtTbrFSM();
		}
	}
	
	// RT TBR Functions
	// *********************************************************************************************
	private static final int TBR_NONE						= -1;
	private static final int TBR_CLEAR_WARNING				= 0;
	private static final int TBR_EVALUATE_MAIN_SCREEN 		= 1;
	private static final int TBR_CHECK_INSULIN_LEVEL 		= 2;
	private static final int TBR_BACK_TO_MAIN_FROM_INSULIN	= 3;
	private static final int TBR_FIND_MENU					= 4;
	private static final int TBR_READ_TBR_PERCENT			= 5;
	private static final int TBR_READ_TBR_TIME				= 6;
	private static final int TBR_BACK_TO_MAIN_FROM_TBR		= 7;
	private static final int TBR_BACK_TO_MAIN_FROM_CANCEL	= 8;
	
	private static final int TBR_CHECK_AT_ZERO				= 50;
	
	private void rtTbrFSM()
	{
		final String FUNC_TAG = "rtTbrFSM";
		int point = 0;
		
		switch(getRtState())
		{
			case TBR_CLEAR_WARNING:
				Debug.i(TAG, FUNC_TAG, "Clearing warning screen!");

				rtSendKey(Application.CHECK, true);
				rtSendKey(Application.NO_KEY, true);
				
				rtSendKey(Application.CHECK, true);
				rtSendKey(Application.NO_KEY, true);
				
				setRtState(Application.TBR_BACK_TO_MAIN_FROM_CANCEL);
				break;
			case TBR_BACK_TO_MAIN_FROM_CANCEL:
				point = 0;
				if(rtCompare(frame.row1, point, 7, RTConstants.clock))	
				{
					Debug.i(TAG, FUNC_TAG, "Found clock, so we're back at the main screen...");
					setRtState(TBR_EVALUATE_MAIN_SCREEN);
				}
				break;
			case TBR_EVALUATE_MAIN_SCREEN:
				if(rtTimer!=null)
					rtTimer.cancel(true);
				rtTimer = scheduler.schedule(rt, 30, TimeUnit.SECONDS);
				
				point = 58;
				if(rtCompare(frame.row1, point, 7, RTConstants.arrow))
				{
					Debug.i(TAG, FUNC_TAG, "Temporary basal is active!");
					tbrRefresh = true;
				}
				else
				{
					Debug.i(TAG, FUNC_TAG, "Temporary basal is inactive!");
					tbrRefresh = false;
				}
				
				point = 48;
				if(rtCompare(frame.row4, point, 11, RTConstants.lowBatt))
				{
					Debug.i(TAG, FUNC_TAG, "Battery is low!");
					
					Bundle b = new Bundle();
		    		b.putString("description", "The pump is reporting a low battery, please replace soon!");
					Event.addEvent(Driver.serv, Event.EVENT_PUMP_LOW_BATTERY, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_ALARM);
				}
				else
					Debug.i(TAG, FUNC_TAG, "Battery is normal...");

				rtSendKey(Application.CHECK, true);
				rtSendKey(Application.NO_KEY, true);
					
				setRtState(TBR_CHECK_INSULIN_LEVEL);
				break;
			case TBR_CHECK_INSULIN_LEVEL:
				if(frame.reason == (byte)0xB7)
				{
					Debug.i(TAG, FUNC_TAG, "Checking insulin level...");
					
					point = 38;
					int lvl1 = RTConstants.getTbrPercent(frame.row2, point, point+8);
					
					point = 50;
					int lvl2 = RTConstants.getTbrPercent(frame.row2, point, point+8);
					
					point = 62;
					int lvl3 = RTConstants.getTbrPercent(frame.row2, point, point+8);
					
					Debug.i(TAG, FUNC_TAG, "Digits:  "+lvl1+" "+lvl2+" "+lvl3);
					
					int insulinLevel = 0;
					if(lvl1 > 0)
						insulinLevel += (100*lvl1);
					if(lvl2 > 0)
						insulinLevel += (10*lvl2);
					if(lvl3 > 0)
						insulinLevel += (lvl3);
					
					Debug.i(TAG, FUNC_TAG, "Insulin Level: "+insulinLevel+" U");
					
					if(insulinLevel > 0)
					{
						if(insulinLevel < (int)RocheDriver.LOW_RES_THRESH)
						{
							if(levelCount > 0)
							{
								if(((System.currentTimeMillis()/1000) - levelCount) > (RocheDriver.WARNING_THRESH * 60))
									levelCount = -1;
							}
							
							if(levelCount < 0)
							{
								levelCount = System.currentTimeMillis()/1000;
								
								Bundle bun = new Bundle();
					    		bun.putString("description", "Pump has "+insulinLevel+" units remaining!");
								Event.addEvent(Driver.serv, Event.EVENT_PUMP_LOW_RESERVOIR, Event.makeJsonString(bun), Event.SET_POPUP_AUDIBLE);
							}
						}
						
						if(drv.tbrTarget == 100 && !tbrRefresh)
						{
							Debug.i(TAG, FUNC_TAG, "The target is 100% and there is no TBR so there is no need to set one!");
							setRtState(TBR_CHECK_AT_ZERO);
						}
						else
						{
							rtSendKey(Application.CHECK, true);				//Press the check button to return to the main screen
							rtSendKey(Application.NO_KEY, true);
							
							rtSendKey(Application.CHECK, true);
							rtSendKey(Application.NO_KEY, true);
							
							setRtState(TBR_BACK_TO_MAIN_FROM_INSULIN);
						}
					}
				}
				else
					Debug.w(TAG, FUNC_TAG, "This is not the frame you're looking for...(waves hand mysteriously)");
				break;
			case TBR_BACK_TO_MAIN_FROM_INSULIN:
				point = 0;
				if(rtCompare(frame.row1, point, 7, RTConstants.clock))	
				{
					Debug.i(TAG, FUNC_TAG, "Found clock, so we're back at the main screen...");
					setRtState(TBR_FIND_MENU);
				}
				break;
			case TBR_FIND_MENU:
				if(frame.reason == (byte)0xB7)
				{
					if(
						frame.row2[0].equalsIgnoreCase(RTConstants.tbr[0]) &&
						frame.row2[1].equalsIgnoreCase(RTConstants.tbr[1]) &&
						frame.row2[2].equalsIgnoreCase(RTConstants.tbr[2]) &&
						frame.row2[3].equalsIgnoreCase(RTConstants.tbr[3]) &&
						frame.row2[4].equalsIgnoreCase(RTConstants.tbr[4]) &&
						frame.row2[5].equalsIgnoreCase(RTConstants.tbr[5]) &&
						frame.row2[6].equalsIgnoreCase(RTConstants.tbr[6]) &&
						frame.row2[7].equalsIgnoreCase(RTConstants.tbr[7])
					)
					{
						Debug.i(TAG, FUNC_TAG, "Found TBR Menu!");
						
						rtSendKey(Application.CHECK, true);				//Enter into the menu
						rtSendKey(Application.NO_KEY, true);
						
						setRtState(TBR_READ_TBR_PERCENT);
					}
					else
					{
						rtSendKey(Application.MENU, true);
						rtSendKey(Application.NO_KEY, true);
						Debug.i(TAG, FUNC_TAG, "Pressing Menu key...");
					}
				}
				break;
			case TBR_READ_TBR_PERCENT:
				int percent = 0;
				point = 38;
				int num1 = RTConstants.getTbrPercent(frame.row2, point, point+8);
				
				point = 50;
				int num2 = RTConstants.getTbrPercent(frame.row2, point, point+8);
				
				point = 62;
				int num3 = RTConstants.getTbrPercent(frame.row2, point, point+8);
				
				Debug.i(TAG, FUNC_TAG, "Percentage: "+num1+" "+num2+" "+num3);
				
				if(num3 != -1)						//This means we have all the values (i.e. it isn't possible for the one's place to be blank)
				{
					if(num1 >=0 )
						percent += (num1*100);
					if(num2 >=0)
						percent += (num2*10);
					if(num3 >=0)
						percent += num3;
					
					Debug.i(TAG, FUNC_TAG, "TBR Percent: "+percent);
					
					int targ = (drv.tbrTarget - percent)/10;
					if(targ > 0)
					{
						Debug.i(TAG, FUNC_TAG, "Pressing up "+Math.abs(targ)+" time(s)");
						for(int i=0;i<Math.abs(targ);i++)
						{
							rtSendKey(Application.UP, true);
							rtSendKey(Application.NO_KEY, true);
						}
					}
					else if(targ < 0)
					{
						Debug.i(TAG, FUNC_TAG, "Pressing down "+Math.abs(targ)+" time(s)");
						for(int i=0;i<Math.abs(targ);i++)
						{
							rtSendKey(Application.DOWN, true);
							rtSendKey(Application.NO_KEY, true);
						}
					}
					else
						Debug.i(TAG, FUNC_TAG, "Percentage at target!");
					
					if(drv.tbrTarget == 100)
					{
						Debug.i(TAG, FUNC_TAG, "Percentage is 100% so there is no need to check time and duration...");
						
						rtSendKey(Application.CHECK, true);
						rtSendKey(Application.NO_KEY, true);
						
						setRtState(TBR_CHECK_AT_ZERO);
					}
					else
					{
						rtSendKey(Application.MENU, true);
						rtSendKey(Application.NO_KEY, true);
						
						setRtState(TBR_READ_TBR_TIME);
					}
				}
				break;
			case TBR_READ_TBR_TIME:
				int hours = 0, min = 0;
				
				point = 32;
				int dig1 = RTConstants.getTbrPercent(frame.row2, point, point+8);
				
				point = 44;
				int dig2 = RTConstants.getTbrPercent(frame.row2, point, point+8);
				
				point = 62;
				int dig3 = RTConstants.getTbrPercent(frame.row2, point, point+8);
				
				point = 74;
				int dig4 = RTConstants.getTbrPercent(frame.row2, point, point+8);

				Debug.i(TAG, FUNC_TAG, "Time: "+dig1+" "+dig2+" : "+dig3+" "+dig4);
				
				if(dig4 != -1 && dig3 != -1 && dig2 != -1 && dig1 != -1)
				{
					hours = (dig1*10)+dig2;
					min = (dig3*10)+dig4;
						
					Debug.i(TAG, FUNC_TAG, "Desired TBR Time: "+drv.tbrDuration);
					Debug.i(TAG, FUNC_TAG, "Current TBR Time: "+hours+":"+min);
					
					int total = 0, press = 0;
					
					if(!tbrRefresh)
					{
						total = (hours*60) + min;
						total /= 15;
						
						press = (drv.tbrDuration/15) - total;										// NOTE: This has been changed to be variable depending on what is sent in the message (Default is 30 min)
						
						Debug.i(TAG, FUNC_TAG, "Setting new TBR!");
						if(press > 0)
						{
							Debug.i(TAG, FUNC_TAG, "Pressing up "+Math.abs(press)+" time(s)!");
							for(int i=0;i<Math.abs(press);i++)
							{
								rtSendKey(Application.UP, true);
								rtSendKey(Application.NO_KEY, true);
							}
						}
						else if(press < 0)
						{
							Debug.i(TAG, FUNC_TAG, "Pressing down "+Math.abs(press)+" time(s)!");
							for(int i=0;i<Math.abs(press);i++)
							{
								rtSendKey(Application.DOWN, true);
								rtSendKey(Application.NO_KEY, true);
							}
						}
						else
							Debug.i(TAG, FUNC_TAG, "Already at target!");
						
						rtSendKey(Application.CHECK, true);
						rtSendKey(Application.NO_KEY, true);
					}
					else
					{
						total = (hours*60) + min;	
						press = (drv.tbrDuration - total);				// NOTE: This has been changed to be variable depending on what is sent in the message (Default is 30 min)
						
						Debug.i(TAG, FUNC_TAG, "Refreshing TBR!");
						
						if(press % 15 > 0)
						{
							press /= 15;
							press++;
						}
						else if(press % 15 < 0)
						{
							press /= 15;
							press--;
						}
						else
							press /= 15;
						
						if(press > 0)
						{
							Debug.i(TAG, FUNC_TAG, "Pressing up "+press+" time(s)");
							for(int i=0;i<Math.abs(press);i++)
							{
								rtSendKey(Application.UP, true);
								rtSendKey(Application.NO_KEY, true);
							}
						}
						else if(press < 0)
						{
							Debug.i(TAG, FUNC_TAG, "Pressing down "+press+" time(s)");
							for(int i=0;i<Math.abs(press);i++)
							{
								rtSendKey(Application.DOWN, true);
								rtSendKey(Application.NO_KEY, true);
							}
						}
						else
						{
							Debug.i(TAG, FUNC_TAG, "At target time!");
						}
						
						Debug.i(TAG, FUNC_TAG, "Pressing check to return to main menu and confirm changes!");
						rtSendKey(Application.CHECK, true);
						rtSendKey(Application.NO_KEY, true);
					}
					
					setRtState(TBR_BACK_TO_MAIN_FROM_TBR);
				}
				break;
			case TBR_BACK_TO_MAIN_FROM_TBR:
				point = 0;
				if(rtCompare(frame.row1, point, 7, RTConstants.clock))	
				{
					Debug.i(TAG, FUNC_TAG, "Found clock, so we're back at the main screen...");
					setRtState(TBR_CHECK_AT_ZERO);
				}
				break;
			case TBR_CHECK_AT_ZERO:
				boolean isZero = false;
				
				point = 0;
				if(rtCompare(frame.row4, point, 44, RTConstants.zeroTbr))
				{
					Debug.i(TAG, FUNC_TAG, "TBR is confirmed to be zero!");
					isZero = true;
				}
				else
				{
					Debug.i(TAG, FUNC_TAG, "TBR is not zero!");
					isZero = false;
				}

				Bundle bun = new Bundle();
				if(isZero)
					bun.putString("description", "TBR complete: Zero");
				else
					bun.putString("description", "TBR complete: NOT zero");
	    		bun.putBoolean("isZero", isZero);
				Event.addEvent(Driver.serv, Event.EVENT_PUMP_TBR, Event.makeJsonString(bun), Event.SET_LOG);
				
				RocheDriver.sendPumpMessage(null, Pump.DRIVER2PUMP_SERVICE_TBR_RESP);	//Send response to Pump Service no matter what
				
				stop = now();
				Debug.e(TAG, FUNC_TAG, "Total time to set TBR: "+(stop-start)+"ms!");
				
				if(rtTimer != null)
				{
					Debug.i(TAG, FUNC_TAG, "Cancelling RT timeout!");
					rtTimer.cancel(true);
				}
				
				setRtState(TBR_NONE);
				startMode(Driver.COMMAND, false);
				break;
		}
	}
	
	private boolean rtCompare(String[] row, int start, int stop, String[] symbol)
	{
		final String FUNC_TAG = "compare";
		
		if(
			row[0].substring(start, start+stop).equalsIgnoreCase(symbol[0]) &&
			row[1].substring(start, start+stop).equalsIgnoreCase(symbol[1]) &&
			row[2].substring(start, start+stop).equalsIgnoreCase(symbol[2]) &&
			row[3].substring(start, start+stop).equalsIgnoreCase(symbol[3]) &&
			row[4].substring(start, start+stop).equalsIgnoreCase(symbol[4]) &&
			row[5].substring(start, start+stop).equalsIgnoreCase(symbol[5]) &&
			row[6].substring(start, start+stop).equalsIgnoreCase(symbol[6]) &&
			row[7].substring(start, start+stop).equalsIgnoreCase(symbol[7]) 
		)
			return true;
		else
			return false;
	}
	
	private String rtStateToString(int state)
	{
		switch(state)
		{
			case TBR_NONE						: return "TBR NONE";
			case TBR_CLEAR_WARNING				: return "TBR CLEAR WARNING";
			case TBR_EVALUATE_MAIN_SCREEN 		: return "TBR EVALUATE MAIN SCREEN";
			case TBR_CHECK_INSULIN_LEVEL 		: return "TBR CHECK INSULIN LEVEL";
			case TBR_BACK_TO_MAIN_FROM_INSULIN	: return "TBR BACK TO MAIN FROM INSULIN";
			case TBR_FIND_MENU					: return "TBR FIND MENU";
			case TBR_READ_TBR_PERCENT			: return "TBR READ TBR PERCENT";
			case TBR_READ_TBR_TIME				: return "TBR READ TIME";
			case TBR_BACK_TO_MAIN_FROM_TBR		: return "TBR BACK TO MAIN FROM TBR";
			case TBR_CHECK_AT_ZERO				: return "TBR CHECK AT ZERO";
			default								: return "UNKNOWN";
		}
	}
	
	private int getRtState()
	{
		return rtState;
	}
	
	private void setRtState(int st)
	{
		final String FUNC_TAG = "setRtState";
		
		prevRtState = rtState;
		rtState = st;
		
		Debug.i(TAG, FUNC_TAG, "RT State: "+rtStateToString(rtState)+" Prev State: "+rtStateToString(prevRtState));
	}
	
	/***********************************************************************************************/
	//	    ___                              _   __  __         _     
	//	   / __|___ _ __  _ __  __ _ _ _  __| | |  \/  |___  __| |___ 
	//	  | (__/ _ \ '  \| '  \/ _` | ' \/ _` | | |\/| / _ \/ _` / -_)
	//	   \___\___/_|_|_|_|_|_\__,_|_||_\__,_| |_|  |_\___/\__,_\___|
	//	                                                              
	/***********************************************************************************************/
	
	// Command Variables
	// *********************************************************************************************
	private static final int BOLUS_CMD_IDLE =49;
	private static final int BOLUS_CMD = 50;
	private static final int BOLUS_CMD_RESP = 51;
	private static final int BOLUS_CMD_STATUS = 52;
	private static final int BOLUS_CMD_HIST = 53;
	private static final int BOLUS_CMD_HIST_RESP = 54;
	private static final int BOLUS_CMD_HIST_PROCESS = 55;
	private static final int BOLUS_CMD_HIST_CONF = 56;
	private static final int BOLUS_CMD_HIST_CONF_RESP = 57;
	
	// Command Transmit Functions
	// *********************************************************************************************
	private void cmdCancelBolus()
	{
		final String FUNC_TAG = "cancelBolus";
		
		Debug.i(TAG, FUNC_TAG, "Cancelling standard bolus...");
		
		ByteBuffer payload = ByteBuffer.allocate(5);
		
		payload.put(AL_VERSION);
		payload.put(COMMAND_MODE_SERVICE_ID);
		payload.put((byte) 0x95);
		payload.put((byte) 0x96);
		payload.put((byte) 0x47);		//Bolus Type:  Standard Wave Bolus
		
		sendData(payload, "CANCEL_BOLUS", CBOL_CANCEL_BOLUS, MAX_RETRIES, PKT_TIMEOUT, 0);
	}
	
	private void cmdBolusStatus()
	{
		final String FUNC_TAG = "bolusStatus";

//		if(getBolusState() == BOLUS_CMD_STATUS)
//		{
			ByteBuffer payload = ByteBuffer.allocate(4);
			
			payload.put(AL_VERSION);
	    	payload.put(COMMAND_MODE_SERVICE_ID);
	    	payload.put((byte) 0x6A);				//CBOL_IMMEDIATE_BOLUS_STATUS_REQ Command
	    	payload.put((byte) 0x96);
	    	
	    	sendData(payload, "SEND_CBOL_STATUS", CBOL_BOLUS_STATUS, MAX_RETRIES, PKT_TIMEOUT, 0);
//		}
//		else
//			Debug.i(TAG, FUNC_TAG, "Not in BOLUS_CMD_STATUS state!");
	}
	
	public void cmdDeliverBolus(int bolus, long id)
	{
		final String FUNC_TAG = "deliverBolus";

		if(getBolusState() != BOLUS_CMD_IDLE)
		{
			Debug.i(TAG, FUNC_TAG, "FAILED:  Bolus already in progress...");
			return;
		}
		
		cmdOpStatus();
		cmdErrStatus();
		
		setBolusState(BOLUS_CMD);
		
		bolusId = id;
		bolusDelay = (bolus*500) + (1000);					//Calculate infusion time in milliseconds
		Debug.i(TAG, FUNC_TAG, "Bolus command sent for "+((float)bolus)/10.0+" U, infusion time: "+bolusDelay);
		
		ByteBuffer payload = ByteBuffer.allocate(26);
		long retries = 0;
		long timeout = PKT_TIMEOUT;
		
    	payload.put(AL_VERSION);
    	payload.put(COMMAND_MODE_SERVICE_ID);
    	payload.put((byte) 0x69);				//CBOL_DELIVER_BOLUS Command
    	payload.put((byte) 0x96);
    	
    	//CRC starts here
    	payload.put((byte) 0x55);				//Hard coded standard bolus mode
    	payload.put((byte) 0x59);
    	
    	payload.order(ByteOrder.LITTLE_ENDIAN);
    	
    	payload.putShort((short) bolus);			//Ch1 amount 0.1U increments
    	payload.putShort((short) 0);				//Ch1 duration in minutes
    	payload.putShort((short) 0);				//Ch1 fast amount
    	
    	payload.putFloat((float) bolus);			//Ch2 amount 0.1U increments
    	payload.putFloat(0);						//Ch2 duration in minutes
    	payload.putFloat(0);						//Ch2 fast amount
    	//CRC ends here
    	
    	short crc = Security.BTCRCINIT;
    	for(int i = 4;i<24;i++)
    		crc = drv.t.s.updateCrc(crc, payload.get(i));
    	
    	payload.putShort(crc);
    	
    	if(bolus <= 10)								//If the bolus is less than 1U we can use retries, otherwise no retries
    		retries = MAX_RETRIES;
    	else
    	{
    		retries = 0;
    		timeout = PKT_TIMEOUT * 2;
    	}
    	
    	sendData(payload, "SEND_CBOL_DELIVER", CBOL_BOLUS_DELIVER, retries, timeout, bolus);		//Send packet with appropriately set retry limit
    	
    	setBolusState(BOLUS_CMD_RESP);
    	
    	if(commandTimer != null)
    		commandTimer.cancel(true);
    	
    	commandTimer = scheduler.schedule(command, MAX_RETRIES*PKT_TIMEOUT, TimeUnit.MILLISECONDS);
	}
	
	private void cmdPing()
	{
    	ByteBuffer payload = ByteBuffer.allocate(4);
    	payload.put(AL_VERSION);
    	payload.put(COMMAND_MODE_SERVICE_ID);
    	payload.put((byte) (PING_CMD & 0xFF));
		payload.put((byte) ((PING_CMD>>8) & 0xFF));
		
		sendData(payload, "PING", PING_RES, MAX_RETRIES, PKT_TIMEOUT, 0);
	}
	
	private void cmdHistoryRead()
	{
		final String FUNC_TAG = "historyRead";
		
		ByteBuffer payload = ByteBuffer.allocate(4);
		payload.put(AL_VERSION);
    	payload.put(COMMAND_MODE_SERVICE_ID);
    	payload.put((byte) (READ_HISTORY_BLOCK_CMD & 0xFF));
		payload.put((byte) ((READ_HISTORY_BLOCK_CMD>>8) & 0xFF));
		
		sendData(payload, "HISTORY_READ", HIST_READ_RES, MAX_RETRIES, PKT_TIMEOUT, 0);
		
		if(getBolusState() == BOLUS_CMD_HIST)			//Await the response
		{
			Debug.i(TAG, FUNC_TAG, "Sending history read to confirm...");
			setBolusState(BOLUS_CMD_HIST_RESP);
		}
	}
	
	private void cmdConfirmHistoryBlock()
	{
		ByteBuffer payload = ByteBuffer.allocate(4);
		payload.put(AL_VERSION);
    	payload.put(COMMAND_MODE_SERVICE_ID);
    	payload.put((byte) (CONFIRM_HISTORY_BLOCK_CMD & 0xFF));
		payload.put((byte) ((CONFIRM_HISTORY_BLOCK_CMD>>8) & 0xFF));
		
		sendData(payload, "CONFIRM_BLOCK", HIST_CONF_RES, MAX_RETRIES, PKT_TIMEOUT, 0);
		
		if(getBolusState() == BOLUS_CMD_HIST_CONF)
		{
			setBolusState(BOLUS_CMD_HIST_CONF_RESP);
		}
	}
	
	private void cmdReadTime()
	{
    	String s = "READ_TIME_DATE";
    	ByteBuffer payload = ByteBuffer.allocate(4);
    	payload.put(AL_VERSION);
    	
    	if(Driver.getMode() == Driver.COMMAND)
    		payload.put(COMMAND_MODE_SERVICE_ID);
    	else
    		payload.put(REMOTE_TERMINAL_SERVICE_ID);
    	
    	payload.put((byte) (READ_TIME_DATE_CMD & 0xFF));
		payload.put((byte) ((READ_TIME_DATE_CMD>>8) & 0xFF));
		
		sendData(payload, s, READ_TIME_RESP, MAX_RETRIES, PKT_TIMEOUT, 0);
	}
	
	private void cmdOpStatus()
	{
    	String s = "READ_OPERATION_STATUS";
    	ByteBuffer payload = ByteBuffer.allocate(4);
    	payload.put(AL_VERSION);
    	
    	if(Driver.getMode() == Driver.COMMAND)
    		payload.put(COMMAND_MODE_SERVICE_ID);
    	else
    		payload.put(REMOTE_TERMINAL_SERVICE_ID);
    	
    	payload.put((byte) (READ_OP_STATUS_CMD & 0xFF));
		payload.put((byte) ((READ_OP_STATUS_CMD>>8) & 0xFF));
		
		sendData(payload, s, READ_OP_STATUS_RESP, MAX_RETRIES, PKT_TIMEOUT, 0);
	}
	
	private void cmdErrStatus()
	{
    	String s = "READ_ERROR_WARNING_STATUS";
    	ByteBuffer payload = ByteBuffer.allocate(4);
    	payload.put(AL_VERSION);
    	
    	if(Driver.getMode() == Driver.COMMAND)
    		payload.put(COMMAND_MODE_SERVICE_ID);
    	else
    		payload.put(REMOTE_TERMINAL_SERVICE_ID);
    	
    	payload.put((byte) (READ_ERROR_WARNING_CMD & 0xFF));
		payload.put((byte) ((READ_ERROR_WARNING_CMD>>8) & 0xFF));
		
		sendData(payload, s, READ_ERR_STATUS_RESP, MAX_RETRIES, PKT_TIMEOUT, 0);
	}
	
	// Command Receive Functions
	// *********************************************************************************************
	private void cmdProcessCancelBolus(ByteBuffer b)
	{
		final String FUNC_TAG = "processCancelBolus";
		
		byte status = b.get();
		
		if(status == 0x48)
		{
			Debug.i(TAG, FUNC_TAG, "Bolus cancelled!");
		}
		else
		{
			Debug.i(TAG, FUNC_TAG, "Bolus not cancelled!");
		}
	}
	
	private void cmdProcessBolusStatus(ByteBuffer b)
	{
		final String FUNC_TAG = "processBolusStatus";

		Debug.i(TAG, FUNC_TAG, "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
		Debug.i(TAG, FUNC_TAG, "Processing immediate bolus status! Length of buffer: "+b.capacity());
		
		b.order(ByteOrder.LITTLE_ENDIAN);
		
		byte type = b.get();
		byte status = b.get();
		short rem = b.getShort();
		short crc = b.getShort();
		
		if(type == 0x47)
			Debug.i(TAG, FUNC_TAG, "Standard Bolus");
		else if(type == 0xB7)
			Debug.i(TAG, FUNC_TAG, "Multi-wave Fast Bolus");
		
		switch(status)
		{
			case (byte)0x55: 
				Debug.i(TAG, FUNC_TAG, "Not delivering");
				checkReconnect();
				break;
			case (byte)0x66: 
				Debug.i(TAG, FUNC_TAG, "Delivering"); 
				if(reconnecting)
				{
					if(checkTimer != null)
						checkTimer.cancel(true);
					
					Debug.e(TAG, FUNC_TAG, "The pump is still delivering another bolus...check until it is done!");
					checkTimer = scheduler.schedule(check, 10, TimeUnit.SECONDS);
				}
				break;
			case (byte)0x99: 
				Debug.i(TAG, FUNC_TAG, "Delivered");
				checkReconnect();
				break;
			case (byte)0xA9: 
				Debug.i(TAG, FUNC_TAG, "Cancelled");
				checkReconnect();
				break;
			case (byte)0xAA: 
				Debug.i(TAG, FUNC_TAG, "Aborted");
				checkReconnect();
				break;
		}
		
		//TODO: Fix delivering state to retry if not done
		Debug.i(TAG, FUNC_TAG, rem/10+" units remaining...");
		Debug.i(TAG, FUNC_TAG, "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
			
		if(getBolusState() == BOLUS_CMD_STATUS)
		{	
			setBolusState(BOLUS_CMD_HIST);
			cmdHistoryRead();
		}
		else
			Debug.i(TAG, FUNC_TAG, "Duplicate Status");
	}
	
	private void checkReconnect()
	{
		if(reconnecting)
		{
			reconnecting = false;
			
			if(checkTimer != null)
				checkTimer.cancel(true);
			
			Debug.d(TAG, "checkReconnect", "Finishing reconnection, reading history and check operational status!");
			
			cmdHistoryRead();
			cmdOpStatus();
		}
	}
	
	private void cmdProcessBolusDeliver(ByteBuffer b)
	{
		final String FUNC_TAG = "processBolusDeliver";

		if(getBolusState() == BOLUS_CMD_RESP)
		{
			Debug.i(TAG, FUNC_TAG, "Cancelling bolus command timeout!");
			
			if(commandTimer != null)
				commandTimer.cancel(true);
			
			b.order(ByteOrder.LITTLE_ENDIAN);
			
			byte start = b.get();
			
			if(start == 0x48)
			{
				Debug.i(TAG, FUNC_TAG, "Bolus started...");
				
				//Only update this value when the bolus is acknowledged
				histId = bolusId;
				
				if(statusTimer != null)
					statusTimer.cancel(true);
				
				statusTimer = scheduler.schedule(status, bolusDelay, TimeUnit.MILLISECONDS);

				Bundle data = new Bundle();
				data.putDouble("totalDelivered", 0.0);
				data.putLong("infusionTime", bolusDelay+10000);					//Time in milliseconds to infuse (add 10 seconds for extra time)
				data.putLong("identifier", bolusId);
				RocheDriver.sendPumpMessage(data, Pump.DRIVER2PUMP_SERVICE_BOLUS_COMMAND_ACK);
				
				setBolusState(BOLUS_CMD_STATUS);
			}
			else if(start == 0xB7)
			{
				Debug.i(TAG, FUNC_TAG, "Bolus not started!");
				setBolusState(BOLUS_CMD_IDLE);
			}
		}
		else
			Debug.i(TAG, FUNC_TAG, "Duplicate Delivery Response");
	}
	
	private void cmdProcessHistory(ByteBuffer b)
	{
		final String FUNC_TAG = "processHistory";

		b.order(ByteOrder.LITTLE_ENDIAN);
		
		short histRem = b.getShort();
		byte end = b.get();
		byte histGap = b.get();
		byte numEvents = b.get();
		
		//*************************************************************************************
		//Write the data to the History object for the driver
		Driver.history.remainingEvents = histRem;
		Driver.history.historyGap = histGap;
		
		Driver.history.endReached = true;
		if(end == 0x48)							//0x48 indicates more events are available
			Driver.history.endReached = false;
		//*************************************************************************************
		
		Debug.i(TAG+"_HISTORY", FUNC_TAG, "Processing History - Length: "+b.capacity()+" Num of Events: "+numEvents+" Remaining Events (including): "+histRem);
		
		drv.histEvents = String.format("%d", numEvents);
		drv.histRemEvents = String.format("%d", histRem);
		
		byte[] data = new byte[4];
		byte[] time = new byte[4];
		
		if(getBolusState() == BOLUS_CMD_HIST_RESP)
			setBolusState(BOLUS_CMD_HIST_PROCESS);
			
		for(int i=0;i<numEvents;i++)
		{
			//Extract the data from the event
			b.get(time);
			b.get(data);
			
			short eventId = b.getShort();
			short check = b.getShort();
			long eventCnt = (long)b.getInt();
			short checkCnt = b.getShort();
			
			Debug.w(TAG, FUNC_TAG, "Event Start ----------------------------------------------------------------------------------");
			Debug.i(TAG+"_HISTORY", FUNC_TAG, "Data: "+data+" Event ID: "+eventId+" Data CRC: "+check+" Event Counter: "+eventCnt+" Counter CRC: "+checkCnt);
			
			ByteBuffer d = ByteBuffer.wrap(data);
			d.order(ByteOrder.LITTLE_ENDIAN);
			
			// Get the time data *******************************************************************************
			int seconds = time[0] & 0x3F;
			int minutes = (time[0] >> 6) & 0x03;
			minutes |= (time[1] << 2) & 0x3C;
			int hours = (time[1] >> 4) & 0x0F;
			hours |= (time[2] << 4) & 0x10;
			int day = (time[2] >> 1) & 0x1F;
			int month = (time[2] >> 6) & 0x03;
			month |= (time[3] << 2) & 0x0C;
			int year = (time[3] >> 2) & 0x3F;
			
			long ts = dateToSeconds(day, month, year, hours, minutes, seconds);
			Debug.i(TAG+"_HISTORY", FUNC_TAG, "Timestamp: "+month+"/"+day+"/"+year+" "+hours+":"+minutes+"."+seconds);
			// *************************************************************************************************
			
			Events e = drv.new Events(histId, eventId, check, (long)eventCnt, checkCnt, data, Events.UNPROCESSED);
			e.timestamp = ts;					//Write the timestamp to the event
			
			if(eventId == 15 || eventId == 7)	//Delivered insulin
			{
				short infused = d.getShort();
				
				//Update Event
				e.bolus = (double)infused/10.0;
				if(eventId == 7)
				{
					//These are manually infused events (and marked with a -1 for the ID)
					e.description = "Manual Infused Insulin";
					e.bolusId = -1;
				}
				else	//Event 15
					e.description = "Infused Insulin";
				
				Debug.d(TAG, FUNC_TAG, "Adding event: "+e.description+" with ID "+e.bolusId+" for "+e.bolus+"U");
				
				if(getBolusState() == BOLUS_CMD_HIST_PROCESS && eventId == 15)
				{
					Debug.i(TAG, FUNC_TAG, "Confirming delivery of "+(float)(infused/10.0)+"U");
					
					Bundle bolusBundle = new Bundle();
					bolusBundle.putDouble("totalDelivered", 0);
					bolusBundle.putLong("time", e.timestamp);
					bolusBundle.putDouble("remainingInsulin", 100);
					bolusBundle.putDouble("batteryVoltage", 3.00);
					bolusBundle.putDouble("deliveredInsulin", e.bolus);
					bolusBundle.putInt("status", Pump.DELIVERED);
					
					RocheDriver.sendPumpMessage(bolusBundle, Pump.DRIVER2PUMP_SERVICE_BOLUS_DELIVERY_ACK);
					
					setBolusState(BOLUS_CMD_HIST_CONF);
					
					Debug.i(TAG, FUNC_TAG, "Marking this infusion as processed since its part of a bolus response...");
					e.isProcessed = Events.PROCESSED;
				}
				
				drv.db.addHistoryEvent(e);
			}
			else if(eventId == 14 || eventId == 6)		//Requested insulin
			{
				short requested = d.getShort();
				
				e.bolus = (double)requested/10.0;
				if(eventId == 6)	
				{
					//Count manual requests as processed since we don't use them
					e.description = "Manual Requested Insulin";
					e.bolusId = -1;
					e.isProcessed = Events.PROCESSED;
				}
				else
					e.description = "Requested Insulin";
				
				Debug.d(TAG, FUNC_TAG, "Adding event: "+e.description+" with ID "+e.bolusId+" for "+e.bolus+"U");
				
				if(getBolusState() == BOLUS_CMD_HIST_PROCESS && eventId == 14)
				{
					Debug.i(TAG, FUNC_TAG, "Marking this request as processed since its part of a bolus response...");
					e.isProcessed = Events.PROCESSED;
				}
				
				drv.db.addHistoryEvent(e);
			}
			
			Debug.i(TAG+"_HISTORY", FUNC_TAG, String.format("Hist Rem: %X End: %X Hist Gap: %X No. Events: %X", histRem, end, histGap, numEvents));
			Debug.w(TAG, FUNC_TAG, "Event End ----------------------------------------------------------------------------------");
		}	//End of for loop
		
		if(numEvents >= 0)
		{
			Debug.i(TAG+"_HISTORY", FUNC_TAG, "Confirming block...");
			cmdConfirmHistoryBlock();
		}
		
		if((histRem - numEvents) > 0)
		{
			Debug.w(TAG+"_HISTORY", FUNC_TAG, "Reading again since theres is more history ("+(histRem - numEvents)+")!");
			cmdHistoryRead();
		}
		else
		{
			Debug.w(TAG, FUNC_TAG, "There is no more history to read...reconciling insulin!");
			
			Debug.i(TAG, FUNC_TAG, "Checking manual...");
			reportManualInsulin();
			
			Debug.i(TAG, FUNC_TAG, "Checking missed...");
			reconcileInsulin();
			
			drv.histRead = true;
		}
	}
	
	private void reconcileInsulin()
	{
		final String FUNC_TAG = "reconcileInsulin";
		
		List<Events> process = drv.db.getUnprocessedCommandEvents();
		
		if(process == null || process.isEmpty())
		{
			Debug.w(TAG, FUNC_TAG, "No new Roche insulin data to process...");
			return;
		}
		
		for(Events e:process)
			Debug.d(TAG, FUNC_TAG, process.indexOf(e)+" > "+e.getEventString());
		
		Debug.i(TAG, FUNC_TAG, "Getting unknown insulin entries from the Insulin table...");
		Cursor c = Driver.serv.getContentResolver().query(Biometrics.INSULIN_URI, new String[]{"req_total", "identifier"}, "status = "+Pump.PENDING+" OR status = "+Pump.DELIVERING, null, null);
		if(c != null)
		{
			c.moveToFirst();
			do
				Debug.d(TAG, FUNC_TAG, "Req Total: "+c.getDouble(c.getColumnIndex("req_total"))+" ID: "+c.getInt(c.getColumnIndex("identifier")));
			while(c.moveToNext());
			
			c.moveToFirst();
			if(c.getCount() == 1)
			{
				Debug.i(TAG, FUNC_TAG, "Found a single bolus to be reconciled...");
				if(process.size() == 2)
				{
					Debug.i(TAG, FUNC_TAG, "Great!  We also only have one new bolus from the pump!");
					
					Events d = null, r = null;
					if(process.get(0).eventID == 15)
					{
						d = process.get(0);
						r = process.get(1);
					}
					else if(process.get(1).eventID == 15)
					{
						d = process.get(1);
						r = process.get(0);
					}
					
					double req_total = c.getDouble(c.getColumnIndex("req_total"));
					checkTotals(req_total, d, r);
				}
				else if(process.size() > 2)
				{
					Debug.w(TAG, FUNC_TAG, "There are more bolus entries from the pump than missing bolus information from the Insulin table...this should be very rare (if impossible)!");
				}
				else
				{
					Debug.e(TAG, FUNC_TAG, "There are not enough new events from the pump to process the missing bolus information from the Insulin table...");
				}
			}
			else if(c.getCount() > 1)
			{
				Debug.w(TAG, FUNC_TAG, "Found multiple Insulin table boluses to be reconciled...we'll need to match by size and ID...");
				if(process.size() == 2)
				{
					do
					{
						Events d = null, r = null;
						if(process.get(0).eventID == 15)
						{
							d = process.get(0);
							r = process.get(1);
						}
						else if(process.get(1).eventID == 15)
						{
							d = process.get(1);
							r = process.get(0);
						}
						
						double req_total = c.getDouble(c.getColumnIndex("req_total"));
						if(checkTotals(req_total, d, r))
						{
							c.close();
							return;
						}
					}
					while(c.moveToNext());
				}
				else if(process.size() >= 2)
				{
					Debug.e(TAG, FUNC_TAG, "This means there are multiple boluses missing in the Insulin table and there are multiple new boluses from the pump!");
					//TODO: we'd have to do the above for each set of bolus events (not actually sure this is possible)
				}
				else
					Debug.e(TAG, FUNC_TAG, "This shouldn't occur, basically there should only be even numbers of entries in this table...");
			}
			else
				Debug.i(TAG, FUNC_TAG, "There is no missing Insulin table bolus information...");
			
			c.close();
		}
		else
			Debug.e(TAG, FUNC_TAG, "The cursor is null, so there is no missing Insulin table bolus information!");
	}
	
	private boolean checkTotals(double req_total, Events d, Events r)
	{
		final String FUNC_TAG = "reconcileInsulin";
		
		double diff = Math.abs(req_total - r.bolus);
		Debug.d(TAG, FUNC_TAG, "Difference is "+Math.abs(diff));
		
		if(diff < 0.05)
		{
			Debug.i(TAG, FUNC_TAG, "Good, the requested amounts match! (I: "+req_total+" to P: "+r.bolus+")");
			
			r.isProcessed = d.isProcessed = Events.PROCESSED;
			
			//Mark events as processed for future reconciliation
			drv.db.modifyHistoryEvent(r);
			drv.db.modifyHistoryEvent(d);
			
			int status = Pump.DELIVERED;
			if(r.bolus != d.bolus)
				status = Pump.CANCELLED;
			
			Bundle queryData = new Bundle();
			queryData.putInt("status", status);
			queryData.putInt("queryId", (int)d.bolusId);
			queryData.putDouble("delivered_amount_U", d.bolus);
			queryData.putString("description", d.description);
			queryData.putLong("time", d.timestamp);
			queryData.putBoolean("reconnect", true);
			
			RocheDriver.sendPumpMessage(queryData, Pump.DRIVER2PUMP_SERVICE_QUERY_RESP);
			
			return true;
		}
		else
		{
			Debug.e(TAG, FUNC_TAG, "The request amounts don't match! (I: "+req_total+" to P: "+r.bolus+")");
			return false;
		}
	}
	
	private void cmdProcessOpStatus(ByteBuffer b)
	{
		final String FUNC_TAG = "processOpStatus";
		
		Debug.w(TAG, FUNC_TAG, "AL Connection Errors: "+alConnectErrors);
		
		byte mode = b.get();
		
		if(mode == (byte)0x48)
		{
			Debug.i(TAG, FUNC_TAG, "Stopped mode!");
			
			Bundle bun = new Bundle();
    		bun.putString("description", "The pump is in stopped mode, please start the pump!");
			Event.addEvent(Driver.serv, Event.EVENT_PUMP_STOPPED, Event.makeJsonString(bun), Event.SET_POPUP_AUDIBLE_VIBE);
			
			drv.startMode = false;
		}
		else
		{
			Debug.i(TAG, FUNC_TAG, "Running mode!");
			drv.startMode = true;
			
			Debug.e(TAG, FUNC_TAG, "Cancelling warning and disconnect timers!");
			if(warningTimer != null)
				warningTimer.cancel(true);
			if(disconnectTimer != null)
				disconnectTimer.cancel(true);
			
			wasConnected = true;
		}
		
		if(drv.histRead && drv.startMode && drv.timeSync)
			drv.updatePumpState(Pump.CONNECTED);
		else
		{
			Driver.log("ROCHE", FUNC_TAG, "The pump cannot be started due to a failed initial condition! (Time, history, or operating status)");
			Debug.e(TAG, FUNC_TAG, "The pump cannot be started due to a failed initial condition! (Time, history, or operating status)");
			drv.updatePumpState(Pump.DISCONNECTED);
		}
	}
	
	private void cmdProcessTime(ByteBuffer b)
	{
		final String FUNC_TAG = "processTime";
		
		short year = b.getShort();
		int month = (int)b.get();
		int day = (int)b.get();
		int hour = (int)b.get();
		int min = (int)b.get();
		int sec = (int)b.get();
		
		String rocheTime = month+"."+day+"."+year+", "+hour+":"+min+"."+sec;
		
		Calendar rocheCal = Calendar.getInstance();
		rocheCal.set(Calendar.YEAR, year);
	    rocheCal.set(Calendar.MONTH, (month-1));
	    rocheCal.set(Calendar.DAY_OF_MONTH, day);
	    rocheCal.set(Calendar.HOUR_OF_DAY, hour);
	    rocheCal.set(Calendar.MINUTE, min);
	    rocheCal.set(Calendar.SECOND, sec);

		Debug.i(TAG, FUNC_TAG, "Roche Time: "+rocheTime);
		
		Debug.i(TAG, FUNC_TAG, "Roche Date: "+rocheCal.getTimeInMillis() / 1000);
		Debug.i(TAG, FUNC_TAG, "Syst  Date: "+System.currentTimeMillis()/1000);
		
		boolean timeWrong = false;
		long diff = Math.abs(rocheCal.getTimeInMillis()/1000 - System.currentTimeMillis()/1000);
		
		Debug.i(TAG, FUNC_TAG, "Difference in time in seconds: "+diff);
		
		if(diff > 120)
		{
			Debug.i(TAG, FUNC_TAG, "Difference is great enough to advise subject!");
			timeWrong = true;
		}
		else
			Debug.i(TAG, FUNC_TAG, "Difference is close enough...");
		
		if(timeWrong)
		{
			drv.timeSync = false;
			
			Bundle bun = new Bundle();
    		bun.putString("description", "The time settings on devices are not sufficiently close, please adjust time!");
			Event.addEvent(Driver.serv, Event.EVENT_PUMP_TIME_ERROR, Event.makeJsonString(bun), Event.SET_POPUP_AUDIBLE);
		}
		else
			drv.timeSync = true;
	}
	
	private void cmdProcessErrStatus(ByteBuffer b)
	{
		final String FUNC_TAG = "processErrStatus";
		
		byte error = b.get();
		byte warn = b.get();
		
		boolean er = false, wn = false;
		
		if(error == (byte)0x48)
			Debug.i(TAG, FUNC_TAG, "No error!");
		else
		{
			Debug.i(TAG, FUNC_TAG, "Error found!");
			er = true;
		}
		
		if(warn == (byte)0x48)
			Debug.i(TAG, FUNC_TAG, "No warning/reminder!");
		else
		{
			Debug.i(TAG, FUNC_TAG, "Warning/reminder found!");
			wn = true;
		}
		
		String message = "";
		
		if(wn && er)			//Both an error and warning!
		{
			message = "The pump has both a warning and an error message, please clear to continue...";
		}
		else if(wn && !er)		//A warning!
		{
			message = "The pump has a warning message, please clear to continue...";
		}
		else if(!wn && er)		//An error!
		{
			message = "The pump has an error message, please clear to continue...";
		}
		
		if(drv.cancelBolus)
		{
			drv.cancelBolus = false;
			
			if(wn || er)
			{
				Debug.i(TAG, FUNC_TAG, "Clearing warning as part of stopping TBR!");
				setRtState(Application.TBR_CLEAR_WARNING);
			}
			else
			{
				Debug.i(TAG, FUNC_TAG, "No warnings to clear...");
				setRtState(Application.TBR_EVALUATE_MAIN_SCREEN);
			}
			
			if(Driver.getMode() == Driver.COMMAND)					//We have to transition to RT
				startMode(Driver.RT, false);
		}
		else if(!message.equalsIgnoreCase(""))
		{
			Bundle bun = new Bundle();
    		bun.putString("description", message);
			Event.addEvent(Driver.serv, Event.EVENT_PUMP_WARNING_ERROR, Event.makeJsonString(bun), Event.SET_POPUP_AUDIBLE_ALARM);
		}
	}
	
	private boolean cmdProcessError(short error)
	{
		final String FUNC_TAG = "processError";

		String sError = "Error > "+String.format("%X", error)+" ";
		
		if(error == 0x0000)
		{
			sError = "No error found!";
			//Debug.i(TAG, FUNC_TAG, sError);
			return true;
		}
		else
		{
			switch(error)
			{
				//Application Layer **********************************************//
				case (short)0xF003: sError = "Unknown Service ID, AL, RT, or CMD"; break;
				case (short)0xF005: sError = "Incompatible AL packet version"; break;
				case (short)0xF006: sError = "Invalid payload length"; break;
				case (short)0xF056: 
					sError = "AL not connected";
					alConnectErrors++;
					startMode(Driver.COMMAND, true);
					break;
					
				case (short)0xF059: sError = "Incompatible service version"; break;
				case (short)0xF05A: sError = "Version, activate, deactivate request with unknown service ID"; break;
				case (short)0xF05C: sError = "Service activation not allowed"; break;
				case (short)0xF05F: 
					sError = "Command not allowed, RT while in CMD mode, CMD while in RT mode";
					modeErrorCount++;
					
					Debug.w(TAG, FUNC_TAG, "Mode error count: "+modeErrorCount);
					Driver.log("ROCHE", FUNC_TAG, "Mode error count: "+modeErrorCount);
					
					if(modeErrorCount > MODE_ERROR_THRESH)
					{
						Debug.e(TAG, FUNC_TAG, "The system is in the wrong mode, transitioning to COMMAND mode!");
						Driver.log("ROCHE", FUNC_TAG, "The system is in the wrong mode, transitioning to COMMAND mode!");
						
						modeErrorCount = 0;
						startMode(Driver.COMMAND, false);
					}
					break;
				
				//Remote Terminal ************************************************//
				case (short)0xF503: sError = "RT payload wrong length"; break;
				case (short)0xF505: sError = "RT display with incorrect row index, update, or display index"; break;
				case (short)0xF506: sError = "RT display timeout, obsolete"; break;
				case (short)0xF509: sError = "RT unknown audio sequence"; break;
				case (short)0xF50A: sError = "RT unknown vibra sequence"; break;
				case (short)0xF50C: sError = "RT command has incorrect sequence number"; break;
				case (short)0xF533: sError = "RT alive timeout expired"; break;
				
				//Command Mode ***************************************************//
				case (short)0xF605: sError = "CBOL values not within threshold"; break;
				case (short)0xF606: sError = "CBOL wrong bolus type"; break;
				case (short)0xF60A: sError = "CBOL bolus not delivering"; break;
				case (short)0xF60C: sError = "History read EEPROM error"; break;
				case (short)0xF633: sError = "History confirm FRAM not readable or writeable"; break;
				case (short)0xF635: sError = "Unknown bolus type, obsolete"; break;
				case (short)0xF636: sError = "CBOL bolus is not available at the moment";
					Driver.log("ROCHE", FUNC_TAG, "For some reason we are unable to currently process a bolus, resetting to command mode!");
					break;	
				case (short)0xF639: sError = "CBOL incorrect CRC value"; break;
				case (short)0xF63A: sError = "CBOL ch1 and ch2 values inconsistent"; break;
				case (short)0xF63C: sError = "CBOL pump has internal error (RAM values changed)"; break;
			}
			
			Debug.i(TAG, FUNC_TAG, sError);
			Driver.log("ROCHE", FUNC_TAG, "Error - "+sError);
			
			return false;
		}
	}

	/***********************************************************************************************/
	//	   __  __      _        ___ ___ __  __ 
	//	  |  \/  |__ _(_)_ _   | __/ __|  \/  |
	//	  | |\/| / _` | | ' \  | _|\__ \ |\/| |
	//	  |_|  |_\__,_|_|_||_| |_| |___/_|  |_|
	//	                                       
	/***********************************************************************************************/
	
	// FSM States
	// *********************************************************************************************
	private static final int CONNECT = 1;
	private static final int CONNECT_RESP = 2;
	private static final int COMM_VER = 3;
	private static final int COMM_VER_RESP = 4;
	private static final int RT_VER = 5;
	private static final int RT_VER_RESP = 6;
	private static final int BIND = 7;
	private static final int BIND_RESP = 8;
	private static final int BOUND = 9;
	private static final int RESET_PUMP_TRANSPORT = 10;
	private static final int DISCONNECTING = 11;
	private static final int DISCONNECTED = 12;
	
	private static final int SERVICE_SYN = 13;
	private static final int SERVICE_CONNECT = 14;
	private static final int SERVICE_ACTIVATE = 15;
	private static final int SERVICE_ACTIVATE_SENT = 19;
	private static final int SERVICE_STARTUP = 16;
	private static final int SERVICE_DEACTIVATING = 17;
	private static final int SERVICE_DEACTIVATE_SENT = 18;
	
	private static final int SERVICE_COMMAND_IDLE = 22;	
	private static final int SERVICE_COMMAND_SEND = 23;
	private static final int SERVICE_COMMAND_RESP = 24;
	private static final int SERVICE_COMMAND_TIMED_OUT = 25;
	
	private static final int SERVICE_RT_IDLE = 32;
	private static final int SERVICE_RT_SEND = 33;
	private static final int SERVICE_RT_RESP = 34;
	
	private static final long PING_TIME 		= 5000;		//5 second ping message (I think max is 8 seconds)
	private static final long PKT_TIMEOUT 		= 3000;
	private static final long MAX_RETRIES 		= 5;		//All packets adhere to this retry limit except boluses over 1U, they do not retry at this level
	private static final long RT_KEEP_ALIVE 	= 1000;
	
	private void runSystem()
	{
		final String FUNC_TAG = "runSystem";
		
		if(InterfaceData.remotePumpBt != null)
		{
			switch(InterfaceData.remotePumpBt.getState())
			{
				case BluetoothConn.CONNECTED:
					runModes();														//Run the state machine
					break;
				case BluetoothConn.CONNECTING:
					if(wasConnected)												//Only switch to reconnecting if we were connected before
					{
						if(!Params.getBoolean(Driver.serv.getContentResolver(), "connection_scheduling", false))
							drv.updatePumpState(Pump.RECONNECTING);
							
						drv.histRead = drv.startMode = drv.timeSync = false;		//Reset the UI check boxes
						
						if(warningTimer != null)
							warningTimer.cancel(true);
						if(disconnectTimer != null)
							disconnectTimer.cancel(true);
						
						Debug.e(TAG, FUNC_TAG, "Starting warning and disconnect timers...");
						
						warningTimer = scheduler.schedule(warning, 15, TimeUnit.MINUTES);
						disconnectTimer = scheduler.schedule(disconnect, 20, TimeUnit.MINUTES);
						
						startMode(Driver.COMMAND, false);							//Always return to command mode if you break the connection
						wasConnected = false;
					}
				case BluetoothConn.LISTENING:
				case BluetoothConn.NONE:
				default:
					break;
			}
		}
	}
	
	private void runModes()
	{
		if(drv.histRead && drv.startMode && drv.timeSync)
			drv.updatePumpState(Pump.CONNECTED);
		
		switch(Driver.getMode())
		{
			case Driver.PAIRING_AUTH:
				runPairingAuthFSM();
				break;
			case Driver.COMMAND:
			case Driver.RT:
				if(connecting)
					runActivateFSM();
				else
					switch(Driver.getMode())
					{
						case Driver.COMMAND:
							runCommandFSM();
							break;
						case Driver.RT:
							runRtFSM();
							break;
					}
				break;
			case Driver.IDLE:
			case Driver.NONE:
			default:
				break;
		}
	}
	
	private void runPairingAuthFSM()
	{
		final String FUNC_TAG = "runPairingAuthFSM";

		switch(getAppState())
		{
			case CONNECT:
				sendAppLayerCommand(Application.APP_SEND_CONNECT, Application.NONE);
				setAppState(Application.CONNECT_RESP);
				break;
			case COMM_VER:
				Debug.i(TAG, FUNC_TAG, "Unbound, so checking service versions...");
				sendAppLayerCommand(APP_COMMAND, COMMANDS_SERVICES_VERSION);
				setAppState(Application.COMM_VER_RESP);
				break;
			case RT_VER:
				Debug.i(TAG, FUNC_TAG, "Command service version compelte!");
				sendAppLayerCommand(APP_COMMAND, REMOTE_TERMINAL_VERSION);
				setAppState(Application.RT_VER_RESP);
				break;
			case BIND:
				Debug.i(TAG, FUNC_TAG, "RT service version complete!");
				sendAppLayerCommand(APP_COMMAND, BINDING);
				setAppState(Application.BIND_RESP);
				break;
			case BOUND:
				Debug.i(TAG, FUNC_TAG, "Binding complete...finishing P&A!");		//Apparently you need to do a SYN to the pump and reset it's Transport Layer
				drv.t.finishPairingAuthentication();								//Then proceed with the Application layer disconnect
				setAppState(Application.RESET_PUMP_TRANSPORT);						//This is a wait state until the Transport layer gets back to us
				break;
			case DISCONNECTING:
				Debug.i(TAG, FUNC_TAG, "Disconnecting the application layer!");
				sendAppLayerCommand(Application.APP_SEND_DISCONNECT, Application.NONE);
				setAppState(Application.DISCONNECTED);
		}
		
		if(!tx.isEmpty())
		{
			packetObj t = tx.poll();
			drv.t.sendReliableData(t.buffer, t.descrip);
		}
	}
	
	private void runActivateFSM()
	{
		final String FUNC_TAG = "runActivateFSM";
		
		switch(getAppState())
		{
			case SERVICE_SYN:
				Debug.i(TAG, FUNC_TAG, "Connected...asking TX Layer to start SYN!");
				drv.t.synAck();
				break;
			case SERVICE_CONNECT:
				Debug.i(TAG, FUNC_TAG, "Trying to connect service!");
				sendAppLayerCommand(Application.APP_SEND_CONNECT, Application.NONE);
				break;
			case SERVICE_ACTIVATE:
				if(Driver.getMode() == Driver.COMMAND)
				{
					Debug.i(TAG, FUNC_TAG, "Trying to activate Command service!");
					sendAppLayerCommand(Application.APP_SERVICE_ACTIVATE, Application.COMMANDS_COMMAND_ACTIVATE);
				}
				else if(Driver.getMode() == Driver.RT)
				{
					Debug.i(TAG, FUNC_TAG, "Trying to activate RT service!");
					sendAppLayerCommand(Application.APP_SERVICE_ACTIVATE, Application.COMMANDS_RT_ACTIVATE);
				}
				retryTime = now();
				setAppState(Application.SERVICE_ACTIVATE_SENT);
				break;
			case SERVICE_ACTIVATE_SENT:
				if((now()-retryTime) > 2300)
				{
					Debug.w(TAG, FUNC_TAG, "Retrying service activation...");
					setAppState(Application.SERVICE_ACTIVATE);
				}
				break;
			case SERVICE_STARTUP:
				connecting = false;			//Turn off connecting mode
				
				Debug.i(TAG, FUNC_TAG, "TX Size: "+tx.size()+" RX Size: "+rx.size());
				
				tx.clear();
				rx.clear();
				
				switch(Driver.getMode())
				{
					case Driver.COMMAND:
						Debug.w(TAG, FUNC_TAG, "Command service starting up...");
						setAppState(Application.SERVICE_COMMAND_IDLE);
						
						reconnecting = true;
						
						cmdErrStatus();
						cmdReadTime();
						cmdBolusStatus();
						
						cmdPing();
						pingTime = now();
						break;
					case Driver.RT:
						Debug.w(TAG, FUNC_TAG, "RT service starting up, sequence restarted...");
						setAppState(Application.SERVICE_RT_IDLE);
						
						rtSeq = 0;
						aliveTime = now();
						break;
				}
				break;
			case SERVICE_DEACTIVATING:
				sendAppLayerCommand(APP_SERVICE_DEACTIVATE_ALL, Application.NONE);
				retryTime = now();
				setAppState(Application.SERVICE_DEACTIVATE_SENT);
				break;
			case SERVICE_DEACTIVATE_SENT:
				if((now()-retryTime) > 2300)
				{
					Debug.w(TAG, FUNC_TAG, "Retrying service deactivation...");
					setAppState(Application.SERVICE_DEACTIVATING);
				}
				break;
		}
		
		if(!tx.isEmpty() && (connecting))
		{
			packetObj t = tx.poll();
			drv.t.sendReliableData(t.buffer, t.descrip);
		}
	}
	
	private void runCommandFSM()
	{
		final String FUNC_TAG = "runCommandFSM";

		switch(getAppState())
		{
			case SERVICE_COMMAND_IDLE:
				if(!tx.isEmpty())
					setAppState(Application.SERVICE_COMMAND_SEND);				//If the TX queue isn't empty then transition states
				else
				{
					if((now() - pingTime) > PING_TIME)		//Check if we have received a response within the timeframe
						cmdPing();							//Adds ping to queue
				}
				break;
			case SERVICE_COMMAND_SEND:
				if(!tx.isEmpty())
				{
					currentTx = tx.getFirst();																//Grab the first message
					
					Debug.i(TAG, FUNC_TAG, "Sending "+currentTx.descrip+"...");
					drv.t.sendReliableData(currentTx.buffer, currentTx.descrip);
					retryTime = now();
					
					setAppState(Application.SERVICE_COMMAND_RESP);												//Set the state to await a response
				}
				break;
			case SERVICE_COMMAND_RESP:
				if(!rx.isEmpty())
				{
					if(currentTx.expResp == rx.getFirst())
					{
						Debug.i(TAG, FUNC_TAG, "Packet: "+currentTx.descrip+" found response...resetting ping timer as well");
						
						pingTime = now();		//Reset ping
						rx.clear();				//Clear the receive buffer
						tx.removeFirst();		//Remove the TX packet from the queue
						
						setAppState(Application.SERVICE_COMMAND_IDLE);		//Transition to idle state
					}
					else
					{
						Debug.i(TAG, FUNC_TAG, "Incorrect response, removing from queue...");
						rx.removeFirst();		//If it doesn't match then remove it
					}
				}
				
				if(retryTime > 0 && ((now() - retryTime) > currentTx.timeout))
					setAppState(Application.SERVICE_COMMAND_TIMED_OUT);				//If we time out then change state
				break;
			case SERVICE_COMMAND_TIMED_OUT:
				if(!tx.isEmpty())
				{
					Debug.e(TAG, FUNC_TAG, "Timeout!");
					packetObj current = tx.getFirst();
					
					setBolusState(BOLUS_CMD_IDLE);			//Reset the bolus command state
					
					if(current.maxRetries == 0)
						tx.removeFirst();					//Remove it from the current queue
					else
					{
						current.timesRetried++;
						Driver.stats.timeouts++;
						
						if(current.timesRetried >= current.maxRetries)
						{
							Debug.i(TAG, FUNC_TAG, "Removing first element, resetting bolus state!");
							tx.removeFirst();
						}
						else
							Debug.i(TAG, FUNC_TAG, "Retrying attempt " + current.timesRetried);
					}
				}
					
				setAppState(Application.SERVICE_COMMAND_IDLE);
				break;
		}
	}

	private void runRtFSM()
	{
		final String FUNC_TAG = "runRtFSM";
		
		switch(getAppState())
		{
			case SERVICE_RT_IDLE:
				if(!tx.isEmpty())
					setAppState(Application.SERVICE_RT_SEND);		//If the TX queue isn't empty then transition states
				else												//Use keep-alive time
				{
					if((now() - aliveTime) > RT_KEEP_ALIVE)			//Check if we have received a response within the time-frame
						rtAlive();									//Adds alive to queue
				}
				break;
			case SERVICE_RT_SEND:
				if(!tx.isEmpty())
				{
					currentTx = tx.getFirst();												//Grab the first message
					
					Debug.i(TAG, FUNC_TAG, "Sending "+currentTx.descrip+"...");
					drv.t.sendUnreliableData(currentTx.buffer, currentTx.descrip);
					
					setAppState(Application.SERVICE_RT_RESP);								//Set the state to await a response
				}
				break;
			case SERVICE_RT_RESP:
				tx.removeFirst();							//Remove the sent command
				rx.clear();									//Don't think we need to keep RX queue at all
				aliveTime = now();							//Track alive time
				
				setAppState(Application.SERVICE_RT_IDLE);	//Set to IDLE state
				break;
		}
	}
	
	private String getStateString(int st)
	{
		switch(st)
		{
			case CONNECT: return "CONNECT";
			case CONNECT_RESP: return "CONNECT_RESP";
			case COMM_VER: return "COMM_VER";
			case COMM_VER_RESP: return "COMM_VER_RESP";
			case RT_VER: return "RT_VER";
			case RT_VER_RESP: return "RT_VER_RESP";
			case BIND: return "BIND";
			case BIND_RESP: return "BIND_RESP";
			case BOUND: return "BOUND";
			case RESET_PUMP_TRANSPORT: return "RESET_PUMP_TRANSPORT";
			case DISCONNECTING: return "DISCONNECTING";
			case DISCONNECTED: return "DISCONNECTED";
			
			case SERVICE_SYN: return "SERVICE_SYN";
			case SERVICE_CONNECT: return "SERVICE_CONNECT";
			
			case SERVICE_COMMAND_IDLE: return "SERVICE_COMMAND_IDLE";
			case SERVICE_COMMAND_SEND: return "SERVICE_COMMAND_SEND";
			case SERVICE_COMMAND_RESP: return "SERVICE_COMMAND_RESP";
			case SERVICE_COMMAND_TIMED_OUT: return "SERVICE_COMMAND_TIMED_OUT";
			
			case SERVICE_RT_IDLE: return "SERVICE_RT_IDLE";
			case SERVICE_RT_SEND: return "SERVICE_RT_SEND";
			case SERVICE_RT_RESP: return "SERVICE_RT_RESP";
			
			default: return "UNKNOWN";
		}
	}
	
	private int getAppState()
	{
		return state;
	}
	
	private void setAppState(int st)
	{
		final String FUNC_TAG = "setState";

//		if(getAppState() == DISCONNECTING && st == SERVICE_COMMAND_RESP)
//		{
//			Debug.w(TAG, FUNC_TAG, "State is disconnecting, disregarding state change!");
//			return;
//		}
		
		drv.appFsm = getStateString(st);
		
		prevState = state;			//Save the previous state
		state = st;					//Set new state
		
		Debug.i(TAG, FUNC_TAG, "State: "+state+" Prev: "+prevState);
	}
	
	/***********************************************************************************************/
	//	   __  __ _           ___             _   _             
	//	  |  \/  (_)_____    | __|  _ _ _  __| |_(_)___ _ _  ___
	//	  | |\/| | (_-< _|_  | _| || | ' \/ _|  _| / _ \ ' \(_-<
	//	  |_|  |_|_/__|__(_) |_| \_,_|_||_\__|\__|_\___/_||_/__/
	//	                                                                                               
	/***********************************************************************************************/

	private long now()
	{
		return System.currentTimeMillis();
	}
	
	public void setTbr()
	{
		final String FUNC_TAG = "setTBR";
		
//		rtState = Application.SET_TBR_CHECK_TBR;
//		rtMenuCount = 0;
//		
//		if(Driver.getMode() == Driver.COMMAND)					//We have to transition states to RT
//			startMode(Driver.RT);
//		else
//			Debug.i(TAG, FUNC_TAG, "Driver is in mode: "+Driver.getMode());
//		
		Debug.i(TAG, FUNC_TAG, "Temp Basal Rate - Target: "+drv.tbrTarget+" Duration: "+drv.tbrDuration);
		
		start = now();
		startMode(Driver.RT, false);
		setRtState(TBR_EVALUATE_MAIN_SCREEN);
	}
	
	
	public void clearBolusCancelTbr()
	{
		final String FUNC_TAG = "clearBolusCancelTbr";
		
		Debug.i(TAG, FUNC_TAG, "Clearing TBR and cancelling boluses!");
		
		cmdCancelBolus();		//Cancel bolus
		cmdErrStatus();			//Still check error status first
		
		Debug.i(TAG, FUNC_TAG, "Temp Basal Rate - Target: "+drv.tbrTarget+" Duration: "+drv.tbrDuration);
	}
	
	private void reportManualInsulin()
	{
		final String FUNC_TAG = "reportManualInsulin";
		
		List<Events> manualEvents = drv.db.getManualInsulinHistory();
		
		for(Events e:manualEvents)
		{
			Debug.i(TAG, "MANUAL_EVENT", e.getEventString());
			drv.db.modifyHistoryEvent(e);
			
			if(e.eventID == 7)
			{
				Debug.i(TAG, FUNC_TAG, "Sending Manual Insulin entry "+e.eventCounter+"!");
				Bundle manIns = new Bundle();
				manIns.putDouble("bolus", e.bolus);
				manIns.putLong("id", e.eventCounter);
				manIns.putLong("time", e.timestamp);
				RocheDriver.sendPumpMessage(manIns, Pump.DRIVER2PUMP_SERVICE_MANUAL_INSULIN);
			}
		}
	}
	
	private long dateToSeconds(int day, int month, int year, int hours, int minutes, int seconds)
	{
		final String FUNC_TAG = "dateToSeconds";
		
		long total = 0;
		
		Calendar epoch = Calendar.getInstance();
		epoch.clear();
		epoch.set(year+2000, month-1, day, hours, minutes, seconds);
		total = epoch.getTimeInMillis()/1000;
		
		Debug.i(TAG, FUNC_TAG, "Timestamp in seconds = " + total);
		
		return total; 
	}
	
	private void setBolusState(int st)
	{		
		final String FUNC_TAG = "setBolusState";
		
		prevBolusState = bolusState;
		bolusState = st;
		
		Debug.i(TAG, FUNC_TAG, "State: "+bolusString(bolusState)+" Prev State: "+bolusString(prevBolusState));
	}
	
	private int getBolusState()
	{
		return bolusState;
	}
	
	private String bolusString(int state)
	{
		switch(state)
		{
			case BOLUS_CMD_IDLE: return "Idle";
			case BOLUS_CMD: return "Bolus Command";
			case BOLUS_CMD_RESP: return "Bolus Command Response";
			case BOLUS_CMD_STATUS: return "Bolus Status";
			case BOLUS_CMD_HIST: return "History Request";
			case BOLUS_CMD_HIST_RESP: return "History Resp.";
			case BOLUS_CMD_HIST_PROCESS: return "Process History";
			case BOLUS_CMD_HIST_CONF: return "Confirm History";
			case BOLUS_CMD_HIST_CONF_RESP: return "Confirm History Resp.";
		}
		
		return "Unknown";
	}
	
	public void startMode(int newMode, boolean force)
	{
		final String FUNC_TAG = "startMode";
		
		Debug.i(TAG, FUNC_TAG, "Mode: "+newMode+" Current Mode: "+Driver.getMode());
		Debug.i(TAG, FUNC_TAG, "Application State: "+getStateString(getAppState()));
		
		tx.clear();
		rx.clear();
		
		connecting = true;
		setBolusState(BOLUS_CMD_IDLE);		//Always set the state to be idle when resetting
		
		if((Driver.getMode() != Driver.RT && Driver.getMode() != Driver.COMMAND) || newMode == Driver.getMode() || force)
		{
			Driver.setMode(newMode);							//Set system mode
			setAppState(Application.SERVICE_SYN);				//Set AL mode to start connecting the desired mode
		}
		else													//This means that we were previously in a differnt mode than we want to be in
		{
			Debug.e(TAG, FUNC_TAG, "We need to deactivate the current service!");
			setAppState(Application.SERVICE_DEACTIVATING);
			Driver.setMode(newMode);							//Set it to the new mode
		}
	}
	
	public void synAckd()
	{
		final String FUNC_TAG = "synAckd";

		switch(getAppState())
		{
			case Application.SERVICE_SYN:
				Debug.i(TAG, FUNC_TAG, "Receive SYN ACK changing to SERVICE_CONNECT");
				setAppState(Application.SERVICE_CONNECT);
				break;
		}
	}
	
	public void startP3()
	{
		//Start Phase 3 of the Pairing and Authentication process
		setAppState(Application.CONNECT);
	}
	
	public void disconnectAppLayer()
	{
		Debug.w(TAG, "disconnect", "Sending disconnect command!");
		setAppState(Application.DISCONNECTING);
	}
}
