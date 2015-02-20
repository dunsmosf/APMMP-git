//*********************************************************************************************************************
//  Copyright 2011-2012 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.DiAsService;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Constraints;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.DiAsSubjectData;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Exercise;
import edu.virginia.dtc.SysMan.FSM;
import edu.virginia.dtc.SysMan.Meal;
import edu.virginia.dtc.SysMan.Mode;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.Safety;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.SysMan.TempBasal;
import edu.virginia.dtc.Tvector.Tvector;

public class DiAsService extends Service 
{
	//********************************************************************************************************
	// CONSTANTS
	//********************************************************************************************************
	
    private static final String IO_TEST_TAG = "DiAsServiceIO";
	private final String TAG = "DiAsService";
	
    private static final String EXERCISE_FLAG_TIME_START = "exerciseFlagTimeStart";
    private static final String EXERCISE_FLAG_TIME_STOP = "exerciseFlagTimeStop";
    private static final String HYPO_FLAG_TIME = "hypoFlagTime";
    private static final String CORR_FLAG_TIME = "corrFlagTime";
	
	// DiAsService Commands
	private static final int DIAS_SERVICE_COMMAND_NULL = 0;
	private static final int DIAS_SERVICE_COMMAND_INIT = 1;
	private static final int DIAS_SERVICE_COMMAND_SET_EXERCISE_STATE = 4;
	private static final int DIAS_SERVICE_COMMAND_SET_HYPO_MUTE_DURATION = 6;
	private static final int DIAS_SERVICE_COMMAND_SET_HYPER_MUTE_DURATION = 7;
	private static final int DIAS_SERVICE_COMMAND_START_CLOSED_LOOP_CLICK = 11;
	private static final int DIAS_SERVICE_COMMAND_STOP_CLICK = 12;
	private static final int DIAS_SERVICE_COMMAND_START_OPEN_LOOP_CLICK = 13;
	private static final int DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK = 25;
	private static final int DIAS_SERVICE_COMMAND_START_SAFETY_CLICK = 26;
	private static final int DIAS_SERVICE_COMMAND_RECOVERY = 50;
	
	// MDI_Activity status returns
	private static final int MDI_ACTIVITY_STATUS_SUCCESS = 0;
	private static final int MDI_ACTIVITY_STATUS_TIMEOUT = -1;

	// APCservice commands
	private static final int APC_SERVICE_CMD_START_SERVICE = 1;
	private static final int APC_SERVICE_CMD_REGISTER_CLIENT = 2;
	private static final int APC_SERVICE_CMD_CALCULATE_STATE = 3;
	
    // APCservice return values
    private static final int APC_PROCESSING_STATE_NORMAL = 10;
    private static final int APC_PROCESSING_STATE_ERROR = -11;
    private static final int APC_CONFIGURATION_PARAMETERS = 12;

	//********************************************************************************************************
	// VARIABLES
	//********************************************************************************************************
	
	// DiAs State Variable and Definitions - state for the system as a whole
	private int DIAS_STATE;
    
  	private boolean TEMP_BASAL_ENABLED;
  	private boolean initialized = false;
	
	private BroadcastReceiver TimerTickReceiver = null;
	private BroadcastReceiver AlgTickReceiver = null;
	private BroadcastReceiver ConnectivityReceiver = null;
	private BroadcastReceiver WifiRssi = null;
	private BroadcastReceiver Power = null;
	private BroadcastReceiver ProfileReceiver = null;
	private BroadcastReceiver BatteryStatsReceiver = null;
	
	private double nextSimulatedPumpValue = 0.0;								// Used to handle simulated pump input from a file
	private int Timer_Ticks_Per_Control_Tick = 1;								// Multiple of SupervisorService algorithm ticks that is counted to before calling APController
	private long Supervisor_Tick_Free_Running_Counter = 0;						// A free running counter incremented on each Supervisor Algorithm Tick
	private int Timer_Ticks_To_Next_Meal_From_Last_Rate_Change = 3;
	
	// BRM hour parameters from Subject Information
	private boolean brmEnabled = true;
	
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	private final int control_algorithm_cycle_time_mins = 5;
	private final int pump_cycle_time_seconds = 60*control_algorithm_cycle_time_mins;
	
	// CGM variables
	private int cgm_value = -1;
	private int cgm_state = CGM.CGM_NONE;
	private int cgm_trend = -3;
	private long cgm_last_time_sec;
	private long cgm_last_normal_time_sec;
	
	// Pump variables
	private int pump_service_state = Pump.PUMP_STATE_IDLE;
	private int prev_pump_service_state = Pump.PUMP_STATE_IDLE;
	private int pump_state = Pump.NONE;
	private int prev_pump_state = Pump.NONE;
	private double pump_last_bolus = -1;
	private long pump_last_bolus_time;
	
	private boolean tbrOn = false;
	
	// Temporary Basal Rate variables
	private int temp_basal_status_code, temp_basal_percent_of_profile_basal_rate, temp_basal_owner;
	private long temp_basal_start_time, temp_basal_scheduled_end_time;
	
	// Battery charge remaining - in percent
	private int battCharge = 100;
	private int battIsCharging = 999;
	private boolean alert20 = false, alert15 = false, alert10 = false;
	private String batteryStats = null;
	
	// State from Safety System
	private int hypoLight = Safety.UNKNOWN_LIGHT;
	private int hyperLight = Safety.UNKNOWN_LIGHT;
	private boolean isMealBolus = false;
	private double brakes_coeff = 1.0;
	private double latestIOB;
	
	// Last four CGM data points stored for slope calculation
	private Tvector Tvec_cgm1;
	private long startTimeSeconds;			// The system start time which is broadcast by the supervisorService
	
	// Track time stamps and exercise state
	private boolean currentlyExercising = false;
	private boolean previouslyExercising = false;
	
	private long exerciseFlagTimeStart;
	private long exerciseFlagTimeStop;
	
	private long corrFlagTime;
	private long mealFlagTime;
	private long calFlagTime;
	
	private long hyperFlagTime;
	private int hyperMuteDuration;
	private final int maxHyperMuteDuration = 120;
	
	private long hypoFlagTime;
	private int hypoMuteDuration;
	private final int maxHypoMuteDuration = 60;
	
	private Controller Apc, Brm, Mcm, Ssm;
	
	private double cgm_min_value;
	private double cgm_max_value;
		
    private CellularRssiListener cellRssi;
	private TelephonyManager telMan;
	
	private String connection = "";
	private boolean cell = false, wifi = false;
	private int conn_rssi;
	
	private int sysUpdate = 0;
	private int updateCount = 0;
	private long recoveryStart = -1;
	
	private CgmObserver cgmObserver;
	private PumpObserver pumpObserver;
	private InsulinObserver insulinObserver;
	private EventObserver eventObserver;
	private StateObserver stateObserver;
	private FsmObserver fsmObserver;
	private TempBasalObserver tempBasalObserver;
	private ExerciseStateObserver exerciseStateObserver;
	
	private final int CGM_WARN_DELAY_MINS = 12;
	
	private int basalPauseDuration = 0;
	private final int basalPauseDurationOnGreenLight = 0;
	private final int basalPauseDurationOnYellowLight = 30;
	private final int basalPauseDurationOnRedLight = 60;
	private static final String PREFS_NAME = "BasalPause";
	
	private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
	private static ScheduledFuture<?> waitTimer, pingTimer;
	
	private Machine ASYNC, SYNC, TBR;
	private int DEF_CONFIG = FSM.NONE, CONFIG = FSM.NONE;
	
	private static DiAsSubjectData subject_data;
	
	private Runnable wait = new Runnable()
	{
		final String FUNC_TAG = "wait";
		
		public void run()
		{
			if(ASYNC.state == FSM.WAIT)
			{
				if(SYNC.isBusy() || TBR.isBusy())
				{
					Debug.i(TAG, FUNC_TAG, "Still waiting for other FSMs to finish...");
				}
				else
				{
					Debug.i(TAG, FUNC_TAG, "Changing state to start...");
					changeAsyncState(FSM.START);
				}
			}
		}
	};
	
	//*******************************************************************************************************************************
	//	 ___ ___ __  __   ___              _        
	//	/ __/ __|  \/  | / __| ___ _ ___ _(_)__ ___ 
	//	\__ \__ \ |\/| | \__ \/ -_) '_\ V / / _/ -_)
	//	|___/___/_|  |_| |___/\___|_|  \_/|_\__\___|
	//
	//*******************************************************************************************************************************
	
    class IncomingSSMHandler extends Handler {
        @Override
        public void handleMessage(Message msg) 
        {
        	final String FUNC_TAG = "IncomingSSMHandler";
        	
        	Bundle responseBundle;
        	
            switch (msg.what) {
        		case Safety.SAFETY_SERVICE_STATE_AWAITING_PUMP_RESPONSE:
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "SSMservice >> (DiAsService), IO_TEST"+", "+FUNC_TAG+", "+
                						"SAFETY_SERVICE_STATE_AWAITING_PUMP_RESPONSE");
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
        			}        			
        			break;
        		case Safety.SAFETY_SERVICE_STATE_CALCULATE_RESPONSE:
        			Debug.i(TAG, FUNC_TAG, "Safety Service Calculate Response!");
        			
        			// Log the response from the APC
					log_action(TAG, "SSMservice >>>"+
							" Calculate Response", 
							Debug.LOG_ACTION_INFORMATION);
        			
        			if(SYNC.state == FSM.SSM_CALC_CALL)
        			{
        				changeSyncState(FSM.SSM_CALC_RESPONSE);
        			}
        			if(ASYNC.state == FSM.SSM_CALC_CALL)
        			{
        				changeAsyncState(FSM.SSM_CALC_RESPONSE);
        			}
        			break;
        		case Safety.SAFETY_SERVICE_STATE_NORMAL:
					responseBundle = msg.getData();
	       	        writeTrafficLights(Safety.TRAFFIC_LIGHT_CONTROL_SSMSERVICE, responseBundle.getInt("stoplight", Safety.UNKNOWN_LIGHT), responseBundle.getInt("stoplight2", Safety.UNKNOWN_LIGHT));
					
					isMealBolus = responseBundle.getBoolean("isMealBolus", false);
					brakes_coeff = responseBundle.getDouble("brakes_coeff", 1.0);
					latestIOB = responseBundle.getDouble("IOB", 0.0);
        			Debug.i(TAG, FUNC_TAG, "SAFETY_SERVICE_STATE_NORMAL > IOB="+latestIOB+", brakes_coeff="+brakes_coeff);
        			
        			log_action(TAG, "SSMservice >>>"+
							" Processing State Normal Response"+
							" IOB: "+String.format("%.2f", latestIOB)+
							" Brakes: "+String.format("%.2f", brakes_coeff),
							Debug.LOG_ACTION_INFORMATION);
        			
        			updateSystem(null);		// Store latestIOB in the system table
        			
        			// Log the parameters for IO testing
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "SSMservice >> (DiAsService), IO_TEST"+", "+FUNC_TAG+", "+
        								"SAFETY_SERVICE_STATE_NORMAL"+", "+
                						"stoplight="+hypoLight+", "+
                						"stoplight2="+hyperLight+", "+
                						"brakes_coeff="+brakes_coeff+", "+
                						"IOB="+latestIOB
                				);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
        			}

					Debug.i(TAG, FUNC_TAG, "SAFETY_SERVICE_STATE_NORMAL > latestIOB="+latestIOB+"stoplight="+hypoLight+", stoplight2="+hyperLight);
        			
        			//Check Hypo Alarm
        			checkForHypo(); //-> Also included in the 1 minute tick
        			
        			//Check for CGM -> switch to Open Loop and pause basal injection
        			updateBasalPauseStatus();
        			
        			//Check Hyper Alarm
        			checkForHyper();
        			
        			if(SYNC.state == FSM.SSM_CALL) {
    					changeSyncState(FSM.SSM_RESPONSE);
        			}
        			
        			if(ASYNC.state == FSM.MCM_REQUEST) {
        				changeAsyncState(FSM.SSM_RESPONSE);
        			}
        			break;
        		case Safety.SAFETY_SERVICE_STATE_PUMP_ERROR:
					responseBundle = msg.getData();
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "SSMservice >> (DiAsService), IO_TEST"+", "+FUNC_TAG+", "+
        								"SAFETY_SERVICE_STATE_PUMP_ERROR"
                				);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
        			}        			
        			break;
        		case Safety.SAFETY_SERVICE_STATE_BUSY:
					responseBundle = msg.getData();
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "SSMservice >> (DiAsService), IO_TEST"+", "+FUNC_TAG+", "+
        								"SAFETY_SERVICE_STATE_BUSY"
                				);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
        			}        			
        			Debug.i(TAG, FUNC_TAG, "SAFETY_SERVICE_STATE_BUSY");
        			break;
        		case Safety.SAFETY_SERVICE_STATE_INVALID_COMMAND:
					responseBundle = msg.getData();
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "SSMservice >> (DiAsService), IO_TEST"+", "+FUNC_TAG+", "+
        								"SAFETY_SERVICE_STATE_INVALID_COMMAND"
                				);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
        			}        			
        			Debug.i(TAG, FUNC_TAG, "SAFETY_SERVICE_STATE_INVALID_COMMAND");
        			break;
	        	default:
            		Bundle b = new Bundle();
            		b.putString(	"description", "SSMservice >> (DiAsService), IO_TEST"+", "+FUNC_TAG+", "+
    								"UNKNOWN_MESSAGE="+msg.what
            				);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					Debug.i(TAG, FUNC_TAG, "UNKNOWN_MESSAGE="+msg.what);
	        		super.handleMessage(msg);
            }
            
            updateBarIcon();
            updateStatusNotifications();
            updateSystem(null);
        }
    }
    
    public void startSSM()
    {
        Ssm.cxn = new ServiceConnection() {
        	final String FUNC_TAG = "mSafetyServiceConnection";
        	
            public void onServiceConnected(ComponentName className, IBinder service) {
                Debug.i(TAG, FUNC_TAG, "onServiceConnected Safety Service");
                Ssm.tx = new Messenger(service);
                Ssm.bound = true;
                Debug.i(TAG, FUNC_TAG, "SafetyServiceStart");

        		// Send a register-client message to the service with the client message handler in replyTo
        		Message msg = Message.obtain(null, Safety.SAFETY_SERVICE_CMD_REGISTER_CLIENT, 0, 0);
        		Ssm.send(msg);
        		
				// Log the parameters for IO testing
				if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
            		Bundle b = new Bundle();
            		b.putString(	"description", "(DiAsService) >> SSMservice, IO_TEST"+", "+FUNC_TAG+", "+
    								"SAFETY_SERVICE_CMD_REGISTER_CLIENT"
            				);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
				}
				
        		// Send an initialize message to the service
        		msg = Message.obtain(null, Safety.SAFETY_SERVICE_CMD_START_SERVICE, 0, 0);
        		Bundle paramBundle = new Bundle();
        		paramBundle.putLong("simulatedTime", getCurrentTimeSeconds());
        		msg.setData(paramBundle);
        		Ssm.send(msg);

				// Log the parameters for IO testing
				if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
            		Bundle b = new Bundle();
            		b.putString(	"description", "(DiAsService) >> SSMservice, IO_TEST"+", "+FUNC_TAG+", "+
    								"SAFETY_SERVICE_CMD_START_SERVICE"+", "+
    								"simulatedTime="+paramBundle.getLong("simulatedTime")
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
				}
           }

           public void onServiceDisconnected(ComponentName className) {
        	   Debug.i(TAG, FUNC_TAG, "onServiceDisconnected");
        	   Ssm.tx = null;
        	   Ssm.bound = false;
        	   
        	   Bundle b = new Bundle();
        	   b.putString(	"description", "SSM Error: "+Event.EVENT_SSM_DEAD);
        	   Event.addEvent(getApplicationContext(), Event.EVENT_SSM_DEAD, Event.makeJsonString(b), Event.SET_LOG);
       			
        	   if(DIAS_STATE != State.DIAS_STATE_STOPPED && DIAS_STATE != State.DIAS_STATE_SENSOR_ONLY) {
       				Debug.i(TAG, FUNC_TAG, "Transition from insulin delivering mode to sensor/stopped mode!");
	       			updateDiasService(State.DIAS_STATE_SENSOR_ONLY, false);
        	   }
        	   
        	   Debug.e(TAG, FUNC_TAG, "The SSM service connection was killed attempting to restart...");
        	   startSSM();
           }
        };
        
        // Bind to the Safety Service
        Intent intent = new Intent("DiAs.SSMservice");
        bindService(intent, Ssm.cxn, Context.BIND_AUTO_CREATE);
    }
    
    //*******************************************************************************************************************************
	//    _   ___  ___   ___              _        
	//   /_\ | _ \/ __| / __| ___ _ ___ _(_)__ ___ 
	//  / _ \|  _/ (__  \__ \/ -_) '_\ V / / _/ -_)
	// /_/ \_\_|  \___| |___/\___|_|  \_/|_\__\___|
	//                                             
    //*******************************************************************************************************************************
    
    class IncomingAPCHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
        	final String FUNC_TAG = "IncomingAPCHandler";
        	
        	Bundle APCBundle;
        	
            switch (msg.what) {
        		case APC_PROCESSING_STATE_NORMAL:
					APCBundle = msg.getData();
					double APC_IOB = APCBundle.getDouble("IOB", 0.0);
					Apc.doesBolus = APCBundle.getBoolean("doesBolus", false);
					Apc.doesRate = APCBundle.getBoolean("doesRate", false);
					Apc.correction = APCBundle.getDouble("recommended_bolus");
					boolean new_diff_rate = APCBundle.getBoolean("new_differential_rate", false);
					Apc.last_calc_time = getCurrentTimeSeconds();
					if (new_diff_rate)
    					Apc.diff_rate = APCBundle.getDouble("differential_basal_rate", 0.0);
					
					writeTrafficLights(Safety.TRAFFIC_LIGHT_CONTROL_APCSERVICE, APCBundle.getInt("stoplight", Safety.UNKNOWN_LIGHT), APCBundle.getInt("stoplight2", Safety.UNKNOWN_LIGHT));
					
        			// Log the parameters for IO testing
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "APC >> (DiAsService), IO_TEST"+", "+FUNC_TAG+", "+
        								"APC_PROCESSING_STATE_NORMAL"+", "+
        								"APC_IOB="+APC_IOB+", "+
    									"doesBolus="+Apc.doesBolus+", "+
    									"doesRate="+Apc.doesRate+", "+
        								"bolusCorrection="+Apc.correction+", "+
        								"new_differential_rate="+new_diff_rate+", "+
        								"differential_basal_rate="+Apc.diff_rate
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
        			}      			
					
        			// Log the response from the APC
					log_action(TAG, "APCservice >>>"+
							" Processing State Normal Response"+
							" IOB: "+String.format("%.2f", APC_IOB)+
							" Corr: "+String.format("%.2f", Apc.correction)+
							" NewRate: "+new_diff_rate+" DiffRate: "+String.format("%.2f", Apc.diff_rate)+
							" DoesBolus: "+Apc.doesBolus+" DoesRate: "+Apc.doesRate, 
							Debug.LOG_ACTION_INFORMATION);
					
					
					Debug.i(TAG, FUNC_TAG, "APCservice >>>"+
							" IOB: "+String.format("%.2f", APC_IOB)+
							" Corr: "+String.format("%.2f", Apc.correction)+
							" DiffRate: "+String.format("%.2f", Apc.diff_rate)+
							" DoesBolus: "+Apc.doesBolus+" DoesRate: "+Apc.doesRate);
					
                	changeSyncState(FSM.APC_RESPONSE);
        			break;
        		case APC_PROCESSING_STATE_ERROR:
	    			Debug.i(TAG, FUNC_TAG, "APC_PROCESSING_STATE_ERROR");
	    			break;
	           	case APC_CONFIGURATION_PARAMETERS:
					APCBundle = msg.getData();
					Timer_Ticks_Per_Control_Tick = APCBundle.getInt("Timer_Ticks_Per_Control_Tick", 1);
					Timer_Ticks_To_Next_Meal_From_Last_Rate_Change = APCBundle.getInt("Timer_Ticks_To_Next_Meal_From_Last_Rate_Change", 3);
					Supervisor_Tick_Free_Running_Counter = 0;
					
        			// Log the parameters for IO testing
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b1 = new Bundle();
                		b1.putString(	"description", "APC >> (DiAsService), IO_TEST"+", "+FUNC_TAG+", "+
        								"APC_CONFIGURATION_PARAMETERS"+
        								"Timer_Ticks_Per_Control_Tick="+Timer_Ticks_Per_Control_Tick+", "+
        								"Timer_Ticks_To_Next_Meal_From_Last_Rate_Change="+Apc.correction+", "+
        								"creditRequest="+Timer_Ticks_To_Next_Meal_From_Last_Rate_Change
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
        			}        			
					Debug.i(TAG, FUNC_TAG, "Timer_Ticks_Per_Control_Tick="+Timer_Ticks_Per_Control_Tick);
					Debug.i(TAG, FUNC_TAG, "Timer_Ticks_To_Next_Meal_From_Last_Rate_Change="+Timer_Ticks_To_Next_Meal_From_Last_Rate_Change);
	           		break;
	        	default:
					Debug.i(TAG, FUNC_TAG, "UNKNOWN_MESSAGE="+msg.what);
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b1 = new Bundle();
                		b1.putString(	"description", "APC >> (DiAsService), IO_TEST"+", "+FUNC_TAG+", "+
        								"UNKNOWN_MESSAGE="+msg.what
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
        			}
	        		super.handleMessage(msg);
	        		break;
            }
            
            updateBarIcon();
            updateStatusNotifications();
            updateSystem(null);
        }
    }

    public void startAPC()
    {
    	Apc.cxn = new ServiceConnection() {
        	final String FUNC_TAG = "mAPCConnection";
        	
            public void onServiceConnected(ComponentName className, IBinder service) {
                Debug.i(TAG, FUNC_TAG, "onServiceConnected APC");
                Apc.tx = new Messenger(service);
                Apc.bound = true;
                Debug.i(TAG, FUNC_TAG, "APC Start");

	    		// Send a register-client message to the APC service with the client message handler in replyTo
	    		Message msg = Message.obtain(null, APC_SERVICE_CMD_REGISTER_CLIENT, 0, 0);
	    		if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
	        		Bundle b1 = new Bundle();
	        		b1.putString(	"description", "(DiAsService) >> APC, IO_TEST"+", "+FUNC_TAG+", "+
									"APC_SERVICE_CMD_REGISTER_CLIENT"
	        					);
	        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
	    		}
	    		
	    		Apc.send(msg);
	    		
	    		// Send an initialize message to the service
	    		msg = Message.obtain(null, APC_SERVICE_CMD_START_SERVICE, 0, 0);
	    		Bundle paramBundle = new Bundle();
	    		paramBundle.putInt("IOB_curve_duration_hours", subject_data.subjectAIT);
	    		paramBundle.putInt("pump_cycle_time_seconds", pump_cycle_time_seconds);
	    		Tvector.putTvector(paramBundle, subject_data.subjectCR, "CRtimes", null, "CRvalues");
	    		Tvector.putTvector(paramBundle, subject_data.subjectCF, "CFtimes", null, "CFvalues");
	    		Tvector.putTvector(paramBundle, subject_data.subjectBasal, "Basaltimes", null, "Basalvalues");
	    		Tvector.putTvector(paramBundle, subject_data.subjectSafety, "SafetyStartimes", "SafetyEndtimes", null);
	    		paramBundle.putDouble("TDI", subject_data.subjectTDI);
	    		paramBundle.putLong("simulatedTime", getCurrentTimeSeconds());
	    		
	    		if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
	        		Bundle b1 = new Bundle();
	        		b1.putString(	"description", "(DiAsService) >> APC, IO_TEST"+", "+FUNC_TAG+", "+
									"APC_SERVICE_CMD_START_SERVICE"+", "+
									"IOB_curve_duration_hours="+subject_data.subjectAIT+", "+
									"pump_cycle_time_seconds="+pump_cycle_time_seconds+", "+
									"simulatedTime="+getCurrentTimeSeconds()
	        					);
	        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
	    		}
	    		
	    		msg.setData(paramBundle);
	    		Apc.send(msg);
           }
            
           public void onServiceDisconnected(ComponentName className) {
        	   // This is called when the connection with the APC service has been unexpectedly disconnected -- that is, its process crashed
        	   Debug.i(TAG, FUNC_TAG, "onServiceDisconnected APC");
        	   Apc.tx = null;
        	   Apc.bound = false;
        	   
        	   	Bundle b = new Bundle();
      			b.putString(	"description", "APC Error: "+Event.EVENT_APC_DEAD);
      			Event.addEvent(getApplicationContext(), Event.EVENT_APC_DEAD, Event.makeJsonString(b), Event.SET_LOG);

      			if(DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP)
      			{
      				Debug.i(TAG, FUNC_TAG, "Transition from insulin delivering mode to safety, pump, sensor or stopped mode (DESC availability)");
      				if (Mode.isSafetyModeAvailable(getContentResolver()))
      					updateDiasService(State.DIAS_STATE_SAFETY_ONLY, false);
      				else if (Mode.isPumpModeAvailable(getContentResolver()))
      					updateDiasService(State.DIAS_STATE_OPEN_LOOP, false);
      				else
      					updateDiasService(State.DIAS_STATE_SENSOR_ONLY, false);
      			}
        	   
        	   Debug.e(TAG, FUNC_TAG, "The APC service connection was killed attempting to restart...");
        	   startAPC();
           }
        };
        
        // Bind to the APC Service or other AP Controller module
        Intent intent = new Intent("DiAs.APCservice");
        bindService(intent, Apc.cxn, Context.BIND_AUTO_CREATE);     
    }
    
    //*******************************************************************************************************************************
	//   ___ ___ __  __   ___              _        
	//  | _ ) _ \  \/  | / __| ___ _ ___ _(_)__ ___ 
	//  | _ \   / |\/| | \__ \/ -_) '_\ V / / _/ -_)
	//  |___/_|_\_|  |_| |___/\___|_|  \_/|_\__\___|
	//                                                
    //*******************************************************************************************************************************
    
    class IncomingBRMHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
        	final String FUNC_TAG = "IncomingBRMHandler";
        	
        	Bundle BRMBundle = msg.getData();
        	
            switch (msg.what) {
        		case APC_PROCESSING_STATE_NORMAL:
        			Brm.doesBolus = BRMBundle.getBoolean("doesBolus", false);
					Brm.doesRate = BRMBundle.getBoolean("doesRate", false);
					boolean new_diff_rate = BRMBundle.getBoolean("new_differential_rate", false);
					Brm.last_calc_time = getCurrentTimeSeconds();
					if (new_diff_rate) 
    					Brm.diff_rate = BRMBundle.getDouble("differential_basal_rate", 0.0);
					
					// Log the response from the APC
					log_action(TAG, "BRMservice >>>"+
							" Processing State Normal Response"+
							" Corr: "+String.format("%.2f", Apc.correction)+
							" NewRate: "+new_diff_rate+" DiffRate: "+String.format("%.2f", Apc.diff_rate)+
							" DoesBolus: "+Apc.doesBolus+" DoesRate: "+Apc.doesRate, 
							Debug.LOG_ACTION_INFORMATION);
					
					writeTrafficLights(Safety.TRAFFIC_LIGHT_CONTROL_BRMSERVICE, BRMBundle.getInt("stoplight", Safety.UNKNOWN_LIGHT), BRMBundle.getInt("stoplight2", Safety.UNKNOWN_LIGHT));
					
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
    	        		// Store BRM_RESPONSE Event
    	        		Bundle b = new Bundle();
                		b.putString(	"description", "BRMservice >> (DiAsService), IO_TEST"+", "+FUNC_TAG+", "+
        								"APC_PROCESSING_STATE_NORMAL"+", "+
    									"doesBolus="+Brm.doesBolus+", "+
    									"doesRate="+Brm.doesRate+", "+
        								"bolusCorrection="+Brm.correction+", "+
        								"new_differential_rate="+new_diff_rate+", "+
        								"differential_basal_rate="+Brm.diff_rate
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_BRM_RESPONSE, Event.makeJsonString(b), Event.SET_LOG);
        			}
        			
					changeSyncState(FSM.BRM_RESPONSE);
					break;
	        	default:
					Debug.i(TAG, FUNC_TAG, "UNKNOWN_MESSAGE="+msg.what);
        			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b1 = new Bundle();
                		b1.putString(	"description", "BRM >> (DiAsService), IO_TEST"+", "+FUNC_TAG+", "+
        								"UNKNOWN_MESSAGE="+msg.what
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
        			}
	        		super.handleMessage(msg);
	        		break;
            }
        }
    }
    
    public void startBRM()
    {
    	Brm.cxn = new ServiceConnection() {
        	final String FUNC_TAG = "mBRMConnection";
        	
            public void onServiceConnected(ComponentName className, IBinder service) {
                Debug.i(TAG, FUNC_TAG, "onServiceConnected BRM");
                Brm.tx = new Messenger(service);
                Brm.bound = true;
                
        		Message msg1 = Message.obtain(null, APC_SERVICE_CMD_REGISTER_CLIENT, 0, 0);
        		if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
            		Bundle b1 = new Bundle();
            		b1.putString(	"description", "(DiAsService) >> BRMservice, IO_TEST"+", "+FUNC_TAG+", "+
    								"APC_SERVICE_CMD_REGISTER_CLIENT"
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
        		} 
        		Brm.send(msg1);
        		
        		// Send an initialize message to the service
        		msg1 = Message.obtain(null, APC_SERVICE_CMD_START_SERVICE, 0, 0);
        		Bundle paramBundle = new Bundle();
        		paramBundle.putInt("IOB_curve_duration_hours", subject_data.subjectAIT);
        		paramBundle.putInt("pump_cycle_time_seconds", pump_cycle_time_seconds);
        		Tvector.putTvector(paramBundle, subject_data.subjectCR, "CRtimes", null, "CRvalues");
        		Tvector.putTvector(paramBundle, subject_data.subjectCF, "CFtimes", null, "CFvalues");
        		Tvector.putTvector(paramBundle, subject_data.subjectBasal, "Basaltimes", null, "Basalvalues");
        		Tvector.putTvector(paramBundle, subject_data.subjectSafety, "SafetyStartimes", "SafetyEndtimes", null);
        		paramBundle.putDouble("TDI", subject_data.subjectTDI);
        		paramBundle.putLong("simulatedTime", getCurrentTimeSeconds());
        		
        		if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
            		Bundle b1 = new Bundle();
            		b1.putString(	"description", "(DiAsService) >> BRMservice, IO_TEST"+", "+FUNC_TAG+", "+
    								"APC_SERVICE_CMD_START_SERVICE"+", "+
    								"IOB_curve_duration_hours="+subject_data.subjectAIT+", "+
    								"pump_cycle_time_seconds="+pump_cycle_time_seconds+", "+
    								"simulatedTime="+getCurrentTimeSeconds()
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
        		}
        		
        		msg1.setData(paramBundle);
        		Brm.send(msg1);
           }
            
           public void onServiceDisconnected(ComponentName className) {
        	   // This is called when the connection with the BRM service has been unexpectedly disconnected -- that is, its process crashed
        	   Debug.i(TAG, FUNC_TAG, "onServiceDisconnected BRM");
        	   Brm.tx = null;
        	   Brm.bound = false;
        	   
        	   	Bundle b = new Bundle();
      			b.putString(	"description", "BRM Error: "+Event.EVENT_BRM_DEAD);
      			Event.addEvent(getApplicationContext(), Event.EVENT_BRM_DEAD, Event.makeJsonString(b), Event.SET_LOG);
      			
      			if(DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP)
      			{
      				if (Mode.isSafetyModeAvailable(getContentResolver()))
      					updateDiasService(State.DIAS_STATE_SAFETY_ONLY, false);
      				else if (Mode.isPumpModeAvailable(getContentResolver()))
      					updateDiasService(State.DIAS_STATE_OPEN_LOOP, false);
      				else
      					updateDiasService(State.DIAS_STATE_SENSOR_ONLY, false);
      			}

        	   
        	   Debug.e(TAG, FUNC_TAG, "The BRM service connection was killed attempting to restart...");
        	   startBRM();
           }
        };
        
        // Bind to the BRM Service or other BRM module
        Intent intentBRM = new Intent("DiAs.BRMservice");
        bindService(intentBRM, Brm.cxn, Context.BIND_AUTO_CREATE); 
    }
    
    //*******************************************************************************************************************************
	//   __  __  ___ __  __   ___              _        
	//  |  \/  |/ __|  \/  | / __| ___ _ ___ _(_)__ ___ 
	//  | |\/| | (__| |\/| | \__ \/ -_) '_\ V / / _/ -_)
	//  |_|  |_|\___|_|  |_| |___/\___|_|  \_/|_\__\___|
	//                                                    
    //*******************************************************************************************************************************
    
    class IncomingMCMHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
        	final String FUNC_TAG = "IncomingMCMHandler";
        	
        	Bundle MCMBundle = msg.getData();
        	
            switch (msg.what) 
            {
	            case Meal.UI_STARTED:
	            	Debug.i(TAG, FUNC_TAG, "UI_STARTED");
            		changeAsyncState(FSM.START);
	            	break;
	            case Meal.UI_CLOSED:
	            	Debug.i(TAG, FUNC_TAG, "UI_CLOSED");
	            	changeAsyncState(FSM.MCM_CANCEL);
	            	break;
	            case Meal.MCM_BOLUS:
	            	Debug.i(TAG, FUNC_TAG, "MCM_BOLUS");
	            	
	            	//Set the MCM values
	            	Mcm.meal = MCMBundle.getDouble("meal", 0.0);
	            	Mcm.correction = MCMBundle.getDouble("correction", 0.0);
	            	
	            	if((Mcm.meal + Mcm.correction) > (Constraints.MAX_MEAL + Constraints.MAX_CORR))
	            	{
	            		Debug.e(TAG, FUNC_TAG, "Trying to exceed maximum constraints!");
	            		changeAsyncState(FSM.MCM_CANCEL);
	            		break;
	            	}
	            	
	            	changeAsyncState(FSM.MCM_REQUEST);
	            	break;
            }
        }
    }
    
    public void startMCM()
    {
    	Mcm.cxn = new ServiceConnection() {
        	final String FUNC_TAG = "mMCMConnection";
        	
            public void onServiceConnected(ComponentName className, IBinder service) {
                Debug.i(TAG, FUNC_TAG, "onServiceConnected MCM");
                Mcm.tx = new Messenger(service);
                Mcm.bound = true;
                Debug.i(TAG, FUNC_TAG, "MCM Start");
                
        		Message msg1 = Message.obtain(null, Meal.REGISTER, 0, 0);
        		
        		Bundle paramBundle = new Bundle();
        		paramBundle.putInt("pump_cycle_time_seconds", pump_cycle_time_seconds);
        		
        		if (Params.getBoolean(getContentResolver(), "enableIO", false)) 
        		{
            		Bundle b1 = new Bundle();
            		b1.putString(	"description", "(DiAsService) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
    								"MCM_SERVICE_CMD_REGISTER_CLIENT"+", "+
    								"pump_cycle_time_seconds="+pump_cycle_time_seconds
            					);
            		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
        		}
        		
        		msg1.setData(paramBundle);
        		Mcm.send(msg1);
           }
            
           public void onServiceDisconnected(ComponentName className) {
        	   // This is called when the connection with the MCM service has been unexpectedly disconnected -- that is, its process crashed
        	   Debug.i(TAG, FUNC_TAG, "onServiceDisconnected MCM");
        	   Mcm.tx = null;
        	   Mcm.bound = false;
        	   
        	   	Bundle b = new Bundle();
      			b.putString(	"description", "MCM Error: "+Event.EVENT_MCM_DEAD);
      			Event.addEvent(getApplicationContext(), Event.EVENT_MCM_DEAD, Event.makeJsonString(b), Event.SET_LOG);
      			
      			if(DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP)
      			{
      				if (Mode.isSafetyModeAvailable(getContentResolver()))
      					updateDiasService(State.DIAS_STATE_SAFETY_ONLY, false);
      				else if (Mode.isPumpModeAvailable(getContentResolver()))
      					updateDiasService(State.DIAS_STATE_OPEN_LOOP, false);
      				else
      					updateDiasService(State.DIAS_STATE_SENSOR_ONLY, false);
      			}
        	   
        	   Debug.e(TAG, FUNC_TAG, "The MCM service connection was killed attempting to restart...");
        	   startMCM();
           }
        };
        
        // Bind to the MCM Service
        Intent intentMCM = new Intent("DiAs.MCMservice");
        bindService(intentMCM, Mcm.cxn, Context.BIND_AUTO_CREATE);
    }
    
    // ******************************************************************************************************************************
 	// STARTUP ROUTINES
 	// ******************************************************************************************************************************
    
	@Override
    public void onCreate() {
		final String FUNC_TAG = "onCreate";
		
		super.onCreate();
		
		initialized = false;
		
        Debug.i(TAG, FUNC_TAG, "");
        log_action(TAG, "Created", Debug.LOG_ACTION_INFORMATION);
        
        ASYNC = new Machine(FSM.MACHINE_ASYNC);
        SYNC = new Machine(FSM.MACHINE_SYNC);
        TBR = new Machine(FSM.MACHINE_TBR);
        
        hypoLight = Safety.UNKNOWN_LIGHT;
        hyperLight = Safety.UNKNOWN_LIGHT;
        
        cellRssi = new CellularRssiListener();
		telMan = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        
		changeSyncState(FSM.IDLE);
		changeAsyncState(FSM.IDLE);
		changeTbrState(FSM.IDLE);
		
        cgm_min_value = 39.0;
        cgm_max_value = 401.0;
        
        // Initialize some values
        Supervisor_Tick_Free_Running_Counter = 0;
        
        // where a user (or more likely a tester) requests a meal before the RCM has run.
        Timer_Ticks_To_Next_Meal_From_Last_Rate_Change = 3;
        
        Apc = new Controller("APC");
        Brm = new Controller("BRM");
        Mcm = new Controller("MCM");
        Ssm = new Controller("SSM");
        
        Apc.rx = new Messenger(new IncomingAPCHandler());
        Brm.rx = new Messenger(new IncomingBRMHandler());
        Mcm.rx = new Messenger(new IncomingMCMHandler());
        Ssm.rx = new Messenger(new IncomingSSMHandler());
        
        cgm_trend = -3;
        cgm_value = -1;
        cgm_last_time_sec = (long)0;
        cgm_last_normal_time_sec = (long)0;

        latestIOB = 0.0;
        initialize_exercise_state();
        
        // Scan the database to properly initialize time stamps of calibration, hypo, correction and meal events
    	initializeFlagValues();
    	initializeCalFlagTime();
    	initializeMealFlagTime();
    	hypoMuteDuration = 1;
    	Debug.i(TAG, FUNC_TAG, "calFlagTime="+calFlagTime+", hypoFlagTime="+hypoFlagTime+", corrFlagTime="+corrFlagTime+", mealFlagTime="+mealFlagTime+", hypoMuteDuration="+hypoMuteDuration);
		
		isMealBolus = false;
		Tvec_cgm1 = new Tvector(12);
		battCharge = 100;
		
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();    
		
		updateDiasService(State.DIAS_STATE_STOPPED, false);
		
        // Initialize the value of prev_pump_state, pump_state and TEMP_BASAL_ENABLED for the pump
 	   	Cursor c = getContentResolver().query(Biometrics.PUMP_DETAILS_URI, new String[]{"state", "service_state", "temp_basal"}, null, null, null);
 	   	if(c != null)
 	   	{
 	   		if(c.moveToLast())
 	   		{
// 	   			prev_pump_state = pump_state;
// 			   	pump_state = c.getInt(c.getColumnIndex("state"));
// 			   	pump_service_state = c.getInt(c.getColumnIndex("service_state"));
	       		int tb = c.getInt(c.getColumnIndex("temp_basal"));
	       		if(tb > 0)
	       			TEMP_BASAL_ENABLED = true;
	       		else
	       			TEMP_BASAL_ENABLED = false; 			   
 	   		}
 	   		c.close();
 	   	}
 	   
        cgmObserver = new CgmObserver(new Handler());
        getContentResolver().registerContentObserver(Biometrics.CGM_URI, true, cgmObserver);
        
        pumpObserver = new PumpObserver(new Handler());
        getContentResolver().registerContentObserver(Biometrics.PUMP_DETAILS_URI, true, pumpObserver);
        
        insulinObserver = new InsulinObserver(new Handler());
        getContentResolver().registerContentObserver(Biometrics.INSULIN_URI, true, insulinObserver);
        
        eventObserver = new EventObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.EVENT_URI, true, eventObserver);
		
		stateObserver = new StateObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.STATE_ESTIMATE_URI, true, stateObserver);
		
		fsmObserver = new FsmObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.STATE_URI, true, fsmObserver);

		tempBasalObserver = new TempBasalObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.TEMP_BASAL_URI, true, tempBasalObserver);
		
		exerciseStateObserver = new ExerciseStateObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.EXERCISE_STATE_URI, true, exerciseStateObserver);
		
		//Start CGM check
		cgmObserver.onChange(false);
		
        // **************************************************************************************************************
     	// Connectivity Receivers
        // **************************************************************************************************************
        WifiRssi = new BroadcastReceiver() {
    		@Override
    		public void onReceive(Context context, Intent intent) {
    			WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    	        
    	        int rssi = wifiManager.getConnectionInfo().getRssi();
    	        int level = WifiManager.calculateSignalLevel(rssi, 100);
    	        
    	        conn_rssi = level;
    		}
    	};
    	
    	ProfileReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				checkInitialization();
			}
    		
    	};
    	this.registerReceiver(ProfileReceiver, new IntentFilter(DiAsSubjectData.PROFILE_CHANGE));
    	
        ConnectivityReceiver = new BroadcastReceiver() {
        	final String FUNC_TAG = "ConnectivityReceiver";
        	
        	@Override
        	public void onReceive(Context context, Intent intent)
        	{
        		if(intent.getAction().equalsIgnoreCase("android.intent.action.BATTERY_CHANGED"))
        		{
        			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 999);
        			int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 999);
        			
        			battCharge = level*100/scale;
        			Debug.i(TAG, FUNC_TAG, "Battery level: "+level+", scale: "+scale+" > pct: "+battCharge+"%");
        			
		        	battIsCharging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
		        	String charge = "";
		        	switch (battIsCharging) {
		        		case 0: charge = "Battery is not charging"; break;
		        		case BatteryManager.BATTERY_PLUGGED_AC: charge = "Battery is charging: AC"; break;
		        		case BatteryManager.BATTERY_PLUGGED_USB: charge = "Battery is charging: USB"; break;
		        		default: charge = "Battery plugged: UNKNOWN"; 
		        	}
		        	Debug.i(TAG, FUNC_TAG, charge);
		        	
		        	manageBatteryAlerts();
		        	
        		}
        		else if(intent.getAction().equalsIgnoreCase(ConnectivityManager.CONNECTIVITY_ACTION))
        		{
    				NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
    				int type = info.getType();
					Debug.i(TAG, FUNC_TAG, "Connectivity Broadcast, type="+type);
    				
    				switch (type)
    				{
        				case ConnectivityManager.TYPE_MOBILE:
        					if(wifi)
        					{
        						unregisterReceiver(WifiRssi);
        						wifi = false;
        					}
        					
        					telMan.listen(cellRssi, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        					connection = "3G";
        					cell = true;
        					break;
        				case ConnectivityManager.TYPE_WIFI:
        					if(!wifi)
        					{
        						Debug.i(TAG, FUNC_TAG, "Getting WiFi RSSI in onCreate!");
        						WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        		    	        
        		    	        int rssi = wifiManager.getConnectionInfo().getRssi();
        		    	        int level = WifiManager.calculateSignalLevel(rssi, 100);
        		    	        
        		    	        conn_rssi = level;
        					}
        					
        					if(cell)
        					{
        						telMan.listen(cellRssi, PhoneStateListener.LISTEN_NONE);
        						cell = false;
        					}
        					
    						registerReceiver(WifiRssi, new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));
        					connection = "W";
        					wifi = true;
        					break;
    				}

    				if (info.isConnectedOrConnecting()){
    					Debug.i(TAG, FUNC_TAG, "BROADCAST RECEIVED > " + connection + " connected="+info.isConnected());
    					
    					return;
    				}
    				
    				if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, true)){
    					Debug.i(TAG, FUNC_TAG, "BROADCAST RECEIVED > No " + connection + " connectivity");

    					connection = "None";
    					conn_rssi = 0;
    					
    					if(cell)
    						telMan.listen(cellRssi, PhoneStateListener.LISTEN_NONE);		//Disable phone state listener and WiFi receiver
    					
    					if(wifi)
    						unregisterReceiver(WifiRssi);
    					
    					cell = false;
    					wifi = false;
    					return;
    				}
        		}
        	}
        };
        IntentFilter filt = new IntentFilter();
        filt.addAction("android.intent.action.BATTERY_CHANGED");
        filt.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(ConnectivityReceiver, filt);
        
        Power = new BroadcastReceiver()
        {
			@Override
			public void onReceive(Context context, Intent intent) 
			{
				Debug.e(TAG, "Power Receiver", "Device is powering off!");
				
				Bundle b = new Bundle();
	    		b.putString("description", "Device is powering off ("+intent.getAction()+")");
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_POWER_OFF, Event.makeJsonString(b), Event.SET_LOG);
				
	    		//We don't store stopped mode
	    		if(DIAS_STATE != State.DIAS_STATE_STOPPED)
	    		{
					ContentValues dv = new ContentValues();
					dv.put("last_state", DIAS_STATE);
					dv.put("ask_at_startup", 1);
					getContentResolver().update(Biometrics.HARDWARE_CONFIGURATION_URI, dv, null, null);
	    		}
			}
        };
        IntentFilter powerFilter = new IntentFilter();
        powerFilter.addAction("android.intent.action.ACTION_SHUTDOWN");
        powerFilter.addAction("android.intent.action.QUICKBOOT_POWEROFF");
        registerReceiver(Power, powerFilter);
        
        // **************************************************************************************************************
     	// Register to receive one minute ticks from Supervisor
        // **************************************************************************************************************
        TimerTickReceiver = new BroadcastReceiver() {
        	final String FUNC_TAG = "TimerTickReceiver";
        	
     		@Override
            public void onReceive(Context context, Intent intent) 
     		{
     			long tick = intent.getLongExtra("tick", -1);
     			
        		int OLD_DIAS_STATE = DIAS_STATE;
        		
    			if (DIAS_STATE != State.DIAS_STATE_SENSOR_ONLY && DIAS_STATE != State.DIAS_STATE_STOPPED) {
    				if (!checkProfiles()) {
    					updateDiasService(State.DIAS_STATE_STOPPED, false);
            			Bundle b = new Bundle();
        	    		b.putString("description", "Missing profile data - system Stopped.");
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_ALARM);
    				}
    			}
    			
    			if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) {
    				if (!Mode.isClosedLoopAvailable(getContentResolver(), timeNowInMinutes()) || !Mode.isSafetyModeAvailable(getContentResolver())) {
    					if (Mode.isPumpModeAvailable(getContentResolver())) {
          					updateDiasService(State.DIAS_STATE_OPEN_LOOP, false);
	    					Bundle b = new Bundle();
	        	    		b.putString("description", "Scheduled switch to Pump mode.");
	        	    		Event.addEvent(getApplicationContext(), Event.EVENT_PUMP_MODE, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE);
    					}
          				else {
          					updateDiasService(State.DIAS_STATE_SENSOR_ONLY, false);
          				}
            			
    				}
    			}
    			
				if (DIAS_STATE != OLD_DIAS_STATE) 
					Debug.i(TAG, FUNC_TAG, "State transition: "+OLD_DIAS_STATE+" => "+DIAS_STATE);
				
    			OLD_DIAS_STATE = DIAS_STATE;
    			
    			// Check for Hypo event and CGM data
    			checkForHypo();
    			checkForCgm();
    			
    			updateBarIcon();
                updateStatusNotifications();
                updateSystem(null);
    			
    			recovery();
            }
     	};
        registerReceiver(TimerTickReceiver, new IntentFilter("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK"));
 
        // **************************************************************************************************************
     	// Register to receive Supervisor Control Algorithm Tick
        // **************************************************************************************************************
     	AlgTickReceiver = new BroadcastReceiver() {
     		final String FUNC_TAG = "TickReceiver";
     		
            @Override
            public void onReceive(Context context, Intent intent) 
            {
            	ContentValues bv = new ContentValues();
     			bv.put("time", (System.currentTimeMillis()/1000));
				bv.put("battery", String.format("%d", battCharge));
				bv.put("plugged", battIsCharging);
				bv.put("network_type", connection);
				bv.put("network_strength", String.format("%d", conn_rssi));
				
				if (batteryStats != null) {
					bv.put("battery_stats", batteryStats);
					batteryStats = null;
				}
				
				try {
	  			    getContentResolver().insert(Biometrics.DEV_DETAILS_URI, bv);
				}
  			    catch (Exception e) {
  			    	Debug.e(TAG, FUNC_TAG, e.getMessage());
  			    }
				
        		nextSimulatedPumpValue = intent.getDoubleExtra("nextSimulatedPumpValue", 0.0);
        		Supervisor_Tick_Free_Running_Counter = Supervisor_Tick_Free_Running_Counter+1;
        		
        		Debug.i(TAG, FUNC_TAG, "TickReceiver > Supervisor_Tick_Free_Running_Counter="+Supervisor_Tick_Free_Running_Counter+", startTimeSeconds="+startTimeSeconds+", latestCGMTime="+cgm_last_time_sec);
    			Debug.i(TAG, FUNC_TAG, "TickReceiver > DIAS_STATE == "+DIAS_STATE);
    			
    			if (DIAS_STATE != State.DIAS_STATE_SENSOR_ONLY && DIAS_STATE != State.DIAS_STATE_STOPPED) 
    			{
    				if (!checkProfiles()) 
    				{
    					updateDiasService(State.DIAS_STATE_STOPPED, false);
            			Bundle b = new Bundle();
        	    		b.putString("description", "Missing profile data - system Stopped.");
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_ALARM);
    				}
    			}
    			
    			// Get the offset in minutes into the current day in the current time zone (based on smartphone time zone setting)
    			if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) {
    				if (!Mode.isClosedLoopAvailable(getContentResolver(), timeNowInMinutes()) || !Mode.isSafetyModeAvailable(getContentResolver())) {
    					if (Mode.isPumpModeAvailable(getContentResolver())) {
          					updateDiasService(State.DIAS_STATE_OPEN_LOOP, false);
	    					Bundle b = new Bundle();
	        	    		b.putString("description", "Scheduled switch to Pump mode.");
	        	    		Event.addEvent(getApplicationContext(), Event.EVENT_PUMP_MODE, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE);
    					}
          				else {
          					updateDiasService(State.DIAS_STATE_SENSOR_ONLY, false);
          				}
    				}
    			}
    			
    			if(initialized)
    				changeSyncState(FSM.START);
    			else
    				Debug.e(TAG, FUNC_TAG, "DiAs Service has not been initialized!");
    			
    			updateBarIcon();
                updateStatusNotifications();
                updateSystem(null);
        	}
     	};
        registerReceiver(AlgTickReceiver, new IntentFilter("edu.virginia.dtc.intent.action.SUPERVISOR_CONTROL_ALGORITHM_TICK"));

        
    	BroadcastReceiver dialogReceiver = new BroadcastReceiver(){
			public void onReceive(Context context, Intent intent) 
			{
				int status = intent.getIntExtra("status", MDI_ACTIVITY_STATUS_TIMEOUT);
				int state_change_command = intent.getIntExtra("state_change_command", State.DIAS_STATE_SAFETY_ONLY);
				double insulin_injected = intent.getDoubleExtra("insulin_injected", 0.0);
            	Toast.makeText(getApplicationContext(), "Insulin Injected: "+insulin_injected, Toast.LENGTH_LONG).show();
	    		Bundle b;
            	if (status == MDI_ACTIVITY_STATUS_SUCCESS) {
            		
            		if (insulin_injected > Pump.EPSILON)
            			storeInjectedInsulin(insulin_injected);
            		else
            			storeInjectedInsulin(0.0);
            		
    	    		b = new Bundle();
    	    		b.putString(	"description", "DiAsService, MDI_ACTIVITY_STATUS_SUCCESS, insulin_injected="+insulin_injected+"U"+", "+
    	    						"state_change_command="+state_change_command);
    	    		Event.addEvent(getApplicationContext(), Event.EVENT_MDI_INPUT, Event.makeJsonString(b), Event.SET_LOG);
            	}
            	else {
    	    		b = new Bundle();
    	    		b.putString(	"description", "DiAsService, MDI_ACTIVITY_STATUS_TIMEOUT, No insulin_injected"+", "+
    								"state_change_command="+state_change_command);
    	    		Event.addEvent(getApplicationContext(), Event.EVENT_MDI_INPUT, Event.makeJsonString(b), Event.SET_LOG);
            	}
    			updateDiasService(getModeFromClickCommand(intent.getIntExtra("state_change_command", DIAS_SERVICE_COMMAND_START_SAFETY_CLICK)), true);
			}			
		};
		registerReceiver(dialogReceiver, new IntentFilter("edu.virginia.dtc.intent.action.MDI_INJECTION"));  
		
		
		// **************************************************************************************************************
	 	// Register to receive Battery Stats from the modified Settings application
	    // **************************************************************************************************************
		
		BatteryStatsReceiver = new BroadcastReceiver() {
			final String FUNC_TAG = "BatteryStatsReceiver";
			
			public void onReceive(Context context, Intent intent) {
				Debug.i(TAG, FUNC_TAG, "onReceive: "+intent.getStringExtra("batteryStats"));
				batteryStats = intent.getStringExtra("batteryStats");
			}
		};
		registerReceiver(BatteryStatsReceiver, new IntentFilter("com.android.customBatteryStats.Broadcast"));
	}
	
    @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
    	final String FUNC_TAG = "onStartCommand";
		int command = intent.getIntExtra("DiAsCommand", 0);
		Date date;
		Intent queryMDI;
		
        switch(command) {
    		case DIAS_SERVICE_COMMAND_NULL:
    			Debug.i(TAG, FUNC_TAG, "DIAS_SERVICE_COMMAND_NULL");
    			updateBarIcon();
                updateStatusNotifications();
                updateSystem(null);
    			break;
    		case DIAS_SERVICE_COMMAND_INIT:
    			checkInitialization();
    			break;
    		case DIAS_SERVICE_COMMAND_RECOVERY:
    			recovery();
    			break;
    		case DIAS_SERVICE_COMMAND_SET_HYPO_MUTE_DURATION:
    			int duration = intent.getIntExtra("muteDuration", 0);
    			if ((duration > 0) && (duration < maxHypoMuteDuration)) {
    				// Set mute Duration
    				hypoMuteDuration = duration;
    				
    	    		Bundle b = new Bundle();
    	    		b.putString("description", "Hypo Mute Alarm, duration="+duration+" minutes");
    	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_HYPO_TREATMENT, Event.makeJsonString(b), Event.SET_LOG);

		    		Debug.i(TAG, FUNC_TAG, "DIAS_SERVICE_COMMAND_SET_HYPO_MUTE_DURATION");
            		hypoFlagTime = getCurrentTimeSeconds();
        			date = new Date(hypoFlagTime*1000);
        			Toast toast;
        			toast = Toast.makeText(this, "Hypo Alarm Muted at "+DateFormat.getDateTimeInstance().format(date)+" for "+ duration +"minutes.", Toast.LENGTH_LONG);
        			toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
        			toast.show();
    			}
    			updateBarIcon();
                updateStatusNotifications();
                updateSystem(null);
    			break;
    		case DIAS_SERVICE_COMMAND_SET_HYPER_MUTE_DURATION:
    			int muteDuration = intent.getIntExtra("muteDuration", 0);
    			if((muteDuration > 0) && (muteDuration <= maxHyperMuteDuration))
    			{
    				hyperMuteDuration = muteDuration;
    				
    				Bundle b = new Bundle();
    	    		b.putString("description", "Hyper Mute Alarm, duration="+muteDuration+" minutes");
    	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_HYPER_MUTE, Event.makeJsonString(b), Event.SET_LOG);

		    		Debug.i(TAG, FUNC_TAG, "DIAS_SERVICE_COMMAND_SET_HYPER_MUTE_DURATION");
            		hyperFlagTime = getCurrentTimeSeconds();
        			date = new Date(hyperFlagTime*1000);
        			Toast.makeText(this, "Hyper Alarm Muted at "+DateFormat.getDateTimeInstance().format(date)+" for "+ muteDuration +"minutes.", Toast.LENGTH_LONG).show();
    			}
    			break;
    		case DIAS_SERVICE_COMMAND_START_CLOSED_LOOP_CLICK:
	    		Bundle b6 = new Bundle();
	    		b6.putString("description", "DiAsUI -> DiAsService, DIAS_SERVICE_COMMAND_START_CLOSED_LOOP_CLICK");
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b6), Event.SET_LOG);
    			if (DIAS_STATE == State.DIAS_STATE_STOPPED && Params.getBoolean(getContentResolver(), "mdi_requested_at_startup", false)) {
    				queryMDI = new Intent();
    				queryMDI.setComponent(new ComponentName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.MDI_Activity"));
    				queryMDI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    				queryMDI.putExtra("state_change_command", command);
    				startActivity(queryMDI);        			
    			}
    			else {
        			Debug.i(TAG, FUNC_TAG, "DIAS_UI_START_CLOSED_LOOP_CLICK");
        			updateDiasService(getModeFromClickCommand(command), true);
    			}
    			break;
    		case DIAS_SERVICE_COMMAND_START_SAFETY_CLICK:
	    		Bundle b5 = new Bundle();
	    		b5.putString("description", "DiAsUI -> DiAsService, DIAS_SERVICE_COMMAND_START_SAFETY_CLICK");
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b5), Event.SET_LOG);
    			if (DIAS_STATE == State.DIAS_STATE_STOPPED && Params.getBoolean(getContentResolver(), "mdi_requested_at_startup", false)) {
    				queryMDI = new Intent();
    				queryMDI.setComponent(new ComponentName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.MDI_Activity"));
    				queryMDI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    				queryMDI.putExtra("state_change_command", command);
    				startActivity(queryMDI);        			
    			}
    			else {
        			Debug.i(TAG, FUNC_TAG, "DIAS_UI_START_SAFETY_CLICK");
        			updateDiasService(getModeFromClickCommand(command), true);
    			}
    			break;
    		case DIAS_SERVICE_COMMAND_START_OPEN_LOOP_CLICK:
	    		Bundle b4 = new Bundle();
	    		b4.putString("description", "DiAsUI -> DiAsService, DIAS_SERVICE_COMMAND_START_OPEN_LOOP_CLICK");
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b4), Event.SET_LOG);
    			if (DIAS_STATE == State.DIAS_STATE_STOPPED && Params.getBoolean(getContentResolver(), "mdi_requested_at_startup", false)) {
    				queryMDI = new Intent();
    				queryMDI.setComponent(new ComponentName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.MDI_Activity"));
    				queryMDI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    				queryMDI.putExtra("state_change_command", command);
    				startActivity(queryMDI);        			
    			}
    			else {
        			Debug.i(TAG, FUNC_TAG, "DIAS_UI_START_OPEN_LOOP_CLICK");
        			updateDiasService(getModeFromClickCommand(command), true);
    			}
    			break;
    		case DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK:
	    		Bundle b7 = new Bundle();
	    		b7.putString("description", "DiAsUI -> DiAsService, DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK");
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b7), Event.SET_LOG);
    			Debug.i(TAG, FUNC_TAG, "DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK");
    			updateDiasService(getModeFromClickCommand(command), true);
    			break;
    		case DIAS_SERVICE_COMMAND_STOP_CLICK:
    			Debug.i(TAG, FUNC_TAG, "DIAS_SERVICE_COMMAND_STOP_CLICK");
    			updateDiasService(getModeFromClickCommand(command), true);
	    		Bundle b3 = new Bundle();
	    		b3.putString("description", "DiAsUI -> DiAsService, DIAS_SERVICE_COMMAND_STOP_CLICK");
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b3), Event.SET_LOG);
    			break;
    		case DIAS_SERVICE_COMMAND_SET_EXERCISE_STATE:
    			Debug.i(TAG, FUNC_TAG, "DIAS_SERVICE_COMMAND_SET_EXERCISE_STATE");
    			
    			if(Params.getInt(getContentResolver(), "exercise_detection_mode", 0) == 0)
    			{
	    			currentlyExercising = intent.getBooleanExtra("currentlyExercising", false);
	    			if (currentlyExercising) {
	        			exerciseFlagTimeStart = getCurrentTimeSeconds();
	        			date = new Date(exerciseFlagTimeStart*1000);
	        			
	        			if (currentlyExercising != previouslyExercising) {
		    	    		Bundle b = new Bundle();
		    	    		b.putString("description", "Exercise started at "+DateFormat.getDateTimeInstance().format(date));
		    	    		Event.addEvent(getApplicationContext(), Event.EVENT_BEGIN_EXERCISE, Event.makeJsonString(b), Event.SET_LOG);
	        			}
	        			previouslyExercising = true;
	    			}
	    			else {
	        			exerciseFlagTimeStop = getCurrentTimeSeconds();
	        			date = new Date(getCurrentTimeSeconds()*1000);
	        			
	        			if (currentlyExercising != previouslyExercising) {
		    	    		Bundle b = new Bundle();
		    	    		b.putString("description", "Exercise stopped at "+DateFormat.getDateTimeInstance().format(date));
		    	    		Event.addEvent(getApplicationContext(), Event.EVENT_END_EXERCISE, Event.makeJsonString(b), Event.SET_LOG);
	        			}
	        			previouslyExercising = false;
	    			}
	    			updateBarIcon();
	                updateStatusNotifications();
	                updateSystem(null);
    			}
    			else
    				Debug.e(TAG, FUNC_TAG, "Exercise detection is turned on, so we aren't accepting user input!");
    			break;
        }
		return 0;
    }
    
    @Override
	public void onDestroy() {
		final String FUNC_TAG = "onDestroy";
		Debug.i(TAG, FUNC_TAG, "");
        log_action(TAG, "Destroyed", Debug.LOG_ACTION_INFORMATION);
        
        unregisterReceiver(AlgTickReceiver);
        unregisterReceiver(TimerTickReceiver);
        if(ConnectivityReceiver != null)
        	unregisterReceiver(ConnectivityReceiver);
        
        if(cgmObserver != null)
			getContentResolver().unregisterContentObserver(cgmObserver);
        
        if(insulinObserver != null)
        	getContentResolver().unregisterContentObserver(insulinObserver);
        
        if(pumpObserver != null)
        	getContentResolver().unregisterContentObserver(pumpObserver);
        
        if(eventObserver != null)
        	getContentResolver().unregisterContentObserver(eventObserver);
        	
        if(stateObserver != null)
        	getContentResolver().unregisterContentObserver(stateObserver);
        
        if(fsmObserver != null)
        	getContentResolver().unregisterContentObserver(fsmObserver);
        
		//Clear icons on destruction
		Intent removeIconsIntent = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON_REMOVE_ALL");
		sendBroadcast(removeIconsIntent);
	}
    
    @Override
	public IBinder onBind(Intent intent) 
    {
		return null;
	}
    
    // ******************************************************************************************************************************
  	// STARTUP SERVICE CONNECTIONS
  	// ******************************************************************************************************************************
    
    private void checkInitialization()
    {
    	final String FUNC_TAG = "checkInitialization";
    	
    	Debug.i(TAG, FUNC_TAG, "Reading subject data...");
		subject_data = readDiAsSubjectData();
		
		if(!initialized)
		{
			initialized = true;
			
			Debug.i(TAG, FUNC_TAG, "DiAs Service is initializing service connections...");
	        
	        initializeServiceConnections();
	        updateBarIcon();
            updateStatusNotifications();
            updateSystem(null);
		}
		else
			Debug.i(TAG, FUNC_TAG, "DiAs Service is already initialized!");
    }
    
    private void initializeServiceConnections() 
    {
    	final String FUNC_TAG = "initializeServiceConnections";

    	boolean apc = Params.getInt(getContentResolver(), "apc_enabled", 0) > 0;
    	boolean brm = Params.getInt(getContentResolver(), "brm_enabled", 0) > 0;
    	
    	if(apc && brm) {
    		CONFIG = FSM.APC_BRM;
    	}
    	else if(apc) {
    		CONFIG = FSM.APC_ONLY;
    	}
    	else if(brm) {
    		CONFIG = FSM.BRM_ONLY;
    	}
    	else {
    		CONFIG = FSM.NONE;
    	}

    	//Keep track of the default configuration for later comparison
    	Debug.i(TAG, FUNC_TAG, "Configuration: "+FSM.configToString(CONFIG));
    	DEF_CONFIG = CONFIG;
    	
    	switch(CONFIG)
    	{
	    	case FSM.APC_BRM:
	    		startAPC();
	    		startBRM();
	    		break;
	    	case FSM.APC_ONLY:
	    		startAPC();
	    		break;
	    	case FSM.BRM_ONLY:
	    		startBRM();
	    		break;
    	}
    	
        startSSM();
        startMCM();
        
        // Set up a Notification for this Service
        int icon = R.drawable.icon;
        CharSequence tickerText = "";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
        Context context = getApplicationContext();
        CharSequence contentTitle = "DiAs v1.0";
        CharSequence contentText = "Diabetes Assistant";
        Intent notificationIntent = new Intent(this, DiAsService.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        final int DIAS_ID = 5;
        
        // Make this a Foreground Service
        startForeground(DIAS_ID, notification);
    }

	// ******************************************************************************************************************************
	// CONTENT OBSERVER CLASSES
	// ******************************************************************************************************************************
		
    class FsmObserver extends ContentObserver
    {
    	private int count;
    	
		public FsmObserver(Handler handler) 
		{
			super(handler);
			
			final String FUNC_TAG = "FSM Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		
    		count = 0;
		}
		
		@Override
		public void onChange(boolean selfChange) 
		{
			this.onChange(selfChange, null);
		}		

		public void onChange(boolean selfChange, Uri uri) 
		{
			final String FUNC_TAG = "FSM onChange";
    	   
    	   	count++;
    	   	Debug.i(TAG, FUNC_TAG, "FSM Observer: "+count);
    	   
    	   	Cursor c = getContentResolver().query(Biometrics.STATE_URI, null, null, null, null);
    	   	if(c != null)
    	   	{
    	   		if(c.moveToLast())
    	   		{
    	   			//Store previous states
    	   			SYNC.prev_state = SYNC.state;
    	   			ASYNC.prev_state = ASYNC.state;
    	   			TBR.prev_state = TBR.state;
    	   			
    	   			//Read new states
    	   			SYNC.state = c.getInt(c.getColumnIndex("sync_state"));
    	   			ASYNC.state = c.getInt(c.getColumnIndex("async_state"));
    	   			TBR.state = c.getInt(c.getColumnIndex("tbr_state"));
    	   			
    	   			Debug.i(TAG, FUNC_TAG, "Sync > State: "+FSM.callStateToString(SYNC.state)+" Prev State: "+FSM.callStateToString(SYNC.prev_state));
    	   			Debug.i(TAG, FUNC_TAG, "Async > State: "+FSM.callStateToString(ASYNC.state)+" Prev State: "+FSM.callStateToString(ASYNC.prev_state));
    	   			Debug.i(TAG, FUNC_TAG, "Tbr > State: "+FSM.callStateToString(TBR.state)+" Prev State: "+FSM.callStateToString(TBR.prev_state));
    	   			
    	   			//Run FSMs on state transitions only//
    	   			//----------------------------------//
    	   			if(SYNC.prev_state != SYNC.state)
    	   				syncFSM();
    	   			
    	   			if(ASYNC.prev_state != ASYNC.state)
    	   				asyncFSM();
    	   			
    	   			if(TBR.prev_state != TBR.state)
    	   				tbrFSM();
    	   		}
    	   	}
    	   	c.close();
		}
    }
    
    class StateObserver extends ContentObserver
    {
    	private int count;
    	
    	public StateObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "State Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		
    		count = 0;
    	}

       @Override
       public void onChange(boolean selfChange) 
       {
    	   this.onChange(selfChange, null);
       }		

       public void onChange(boolean selfChange, Uri uri) 
       {
    	   	final String FUNC_TAG = "onChange";
    	   
    	   	count++;
    	   	Debug.i(TAG, FUNC_TAG, "State Observer: "+count);
    	   
       		int traffic_light = Params.getInt(getContentResolver(), "traffic_lights", Safety.TRAFFIC_LIGHT_CONTROL_DISABLED);
       		
     	   Cursor c = getContentResolver().query(Biometrics.STATE_ESTIMATE_URI, new String[]{"IOB", "stoplight", "stoplight2"}, null, null, null);
    	   if(c != null)
    	   {
    		   if(c.moveToLast())
    		   {
    			   double iob = c.getDouble(c.getColumnIndex("IOB"));
    			   int hypo = c.getInt(c.getColumnIndex("stoplight"));
    			   int hyper = c.getInt(c.getColumnIndex("stoplight2"));
    			   
    			   Debug.i(TAG, FUNC_TAG, "State Observer: stoplight="+hypo+" stoplight2="+hyper);
    			   
    			   latestIOB = iob;
    			   
    			   if (traffic_light != Safety.TRAFFIC_LIGHT_CONTROL_APCSERVICE 
    	       				&& traffic_light != Safety.TRAFFIC_LIGHT_CONTROL_BRMSERVICE
    	       				&& traffic_light != Safety.TRAFFIC_LIGHT_CONTROL_DISABLED
    	       				&& DIAS_STATE == State.DIAS_STATE_STOPPED) 
    			   {
	    			   hypoLight = hypo;
	    			   hyperLight = hyper;
	    			   
	    			   Debug.w(TAG, FUNC_TAG, "System is in stopped mode!, writing the lights in the observer from the SE Table!");
	    			   Debug.i(TAG, FUNC_TAG, "State Observer: HyperLight = "+hyperLight+" HypoLight = "+hypoLight+" IOB = "+latestIOB);
    			   }
    			   
    			   updateSystem(null);
    		   }
    	   }
    	   c.close();
       	}
    }
    
    class EventObserver extends ContentObserver 
    {	
		private int count;
    	
    	public EventObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "Event Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		
    		count = 0;
    	}

       @Override
       public void onChange(boolean selfChange) 
       {
    	   this.onChange(selfChange, null);
       }		

       public void onChange(boolean selfChange, Uri uri) 
       {
    	   final String FUNC_TAG = "onChange";
    	   
    	   count++;
    	   Debug.i(TAG, FUNC_TAG, "Event Observer: "+count);
    	   
    	   Cursor c = getContentResolver().query(Biometrics.EVENT_URI, null, null, null, null);
    	   if(c != null)
    	   {
    		   if(c.moveToLast())
    		   {
    			   analyzeEvent(c);
    			   Debug.i(TAG, FUNC_TAG, "Description: "+c.getString(c.getColumnIndex("json")));      
    		   }
    	   }
    	   c.close();
       	}		
    }
    
	class CgmObserver extends ContentObserver 
    {	
    	private int count;
    	
    	public CgmObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "CGM Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		
    		count = 0;
    	}

       @Override
       public void onChange(boolean selfChange) 
       {
    	   this.onChange(selfChange, null);
       }		

       public void onChange(boolean selfChange, Uri uri) 
       {
    	   final String FUNC_TAG = "onChange";
    	   
    	   count++;
    	   Debug.i(TAG, FUNC_TAG, "CGM Observer: "+count);
    	   
    	   Cursor c = getContentResolver().query(Biometrics.CGM_URI, null, null, null, null);
    	   if(c != null)
    	   {
    		   if(c.moveToLast())
    		   {
    			   cgm_value = (int)c.getDouble(c.getColumnIndex("cgm"));
    			   cgm_state = c.getInt(c.getColumnIndex("state"));
    			   cgm_trend = c.getInt(c.getColumnIndex("trend"));
    			   cgm_last_time_sec = c.getLong(c.getColumnIndex("time"));
    			   
    			   if (cgm_state == CGM.CGM_NORMAL) {
    				   cgm_last_normal_time_sec = c.getLong(c.getColumnIndex("time"));
    			   }
    			   
    		    	int traffic_light = Params.getInt(getContentResolver(), "traffic_lights", 0);
    			   	// Handle State.DIAS_STATE_SENSOR_ONLY
	       	        if (DIAS_STATE == State.DIAS_STATE_SENSOR_ONLY && traffic_light != Safety.TRAFFIC_LIGHT_CONTROL_APCSERVICE && traffic_light != Safety.TRAFFIC_LIGHT_CONTROL_BRMSERVICE) 
	       	        {
	       	        	// Simple threshold code for updating Traffic Lights - replace with call to SafetyService
	       	        	if(cgm_state == CGM.CGM_NORMAL)
	       	        	{
		       	        	hypoLight = Safety.GREEN_LIGHT;
		       	        	hyperLight = Safety.GREEN_LIGHT;
		       	        	
		   					if (cgm_value <= 70) {
		   						hypoLight = Safety.RED_LIGHT;
		   						hyperLight = Safety.GREEN_LIGHT;
		   					} else if (cgm_value <= 90) {
		   						hypoLight = Safety.YELLOW_LIGHT;
		   						hyperLight = Safety.GREEN_LIGHT;
		   					} else if (cgm_value < 250) {
		   						hypoLight = Safety.GREEN_LIGHT;
		   						hyperLight = Safety.GREEN_LIGHT;
		   					} else if (cgm_value < 300) {
		   						hypoLight = Safety.GREEN_LIGHT;
		   						hyperLight = Safety.YELLOW_LIGHT;
		   					} else {
		   						hypoLight = Safety.GREEN_LIGHT;
		   						hyperLight = Safety.RED_LIGHT;
		   					}
	       	        	}
	       	        	else
	       	        	{
	       	        		Debug.i(TAG, FUNC_TAG, "CGM is not in a valid state to produce lights");
	       	        		hypoLight = Safety.UNKNOWN_LIGHT;
	       	        		hyperLight = Safety.UNKNOWN_LIGHT;
	       	        	}
	       	        }      
    		   }
    	   }
    	   c.close();
    	   
    	   updateBarIcon();
           updateStatusNotifications();
           updateSystem(null);
       }		
    }
	
	class PumpObserver extends ContentObserver 
    {	
    	private int count;
    	public boolean delivering;
    	
    	public PumpObserver(Handler handler) 
    	{
    		super(handler);
    		
    		delivering = false;
    		
    		final String FUNC_TAG = "Pump Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		
    		count = 0;
    	}

       @Override
       public void onChange(boolean selfChange) 
       {
    	   this.onChange(selfChange, null);
       }		

       public void onChange(boolean selfChange, Uri uri) 
       {
    	   final String FUNC_TAG = "PumpObserver onChange";
    	   
    	   count++;
    	   Debug.i(TAG, FUNC_TAG, "Pump Observer: "+count);
    	   
    	   Cursor c = getContentResolver().query(Biometrics.PUMP_DETAILS_URI, new String[]{"state", "service_state", "temp_basal"}, null, null, null);
    	   if(c != null)
    	   {
    		   if(c.moveToLast())
    		   {
    			   	prev_pump_state = pump_state;
    			   	pump_state = c.getInt(c.getColumnIndex("state"));
    			   	
    			   	prev_pump_service_state = pump_service_state;
    			   	pump_service_state = c.getInt(c.getColumnIndex("service_state"));
	       			int tb = c.getInt(c.getColumnIndex("temp_basal"));
	       			
	       			Debug.i(TAG, FUNC_TAG, "Service State: "+Pump.serviceStateToString(pump_service_state));
	       			
	       			if(pump_service_state != prev_pump_service_state)
	       			{
	       				if(pump_service_state == Pump.PUMP_STATE_TBR_DISABLED)
	       				{
	       					if(TBR.state == FSM.TBR_CALL)
	       						changeTbrState(FSM.TBR_RESPONSE);
	       					if(SYNC.state == FSM.TBR_CALL)
	       						changeSyncState(FSM.TBR_RESPONSE);
	       				}
	       				
	       				if(pump_service_state == Pump.PUMP_STATE_COMPLETE 
	       						|| pump_service_state == Pump.PUMP_STATE_CMD_TIMEOUT
	       						|| pump_service_state == Pump.PUMP_STATE_DELIVER_TIMEOUT)
	       				{
	       					if(SYNC.state == FSM.SSM_RESPONSE)
	       						changeSyncState(FSM.PUMP_RESPONSE);
	       					if(ASYNC.state == FSM.SSM_RESPONSE)
	       						changeAsyncState(FSM.PUMP_RESPONSE);
	       				}
	       				
	       				if(pump_service_state == Pump.PUMP_STATE_TBR_COMPLETE 
	       						|| pump_service_state == Pump.PUMP_STATE_TBR_TIMEOUT)
	       				{
	       					if(SYNC.state == FSM.TBR_CALL)
	       						changeSyncState(FSM.TBR_RESPONSE);
	       					if(TBR.state == FSM.TBR_CALL)
	       						changeTbrState(FSM.TBR_RESPONSE);
	       				}
	       			}
	       			
	       			if(tb > 0)
	       				TEMP_BASAL_ENABLED = true;
	       			else
	       				TEMP_BASAL_ENABLED = false;
    			   
    			   if(Pump.isConnected(prev_pump_state) && Pump.notConnected(pump_state) && DIAS_STATE != State.DIAS_STATE_STOPPED)
    			   {
    				   updateDiasService(State.DIAS_STATE_SENSOR_ONLY, false);
    			   }
    		   }
        	   c.close();
    	   }
    	   
    	   updateBarIcon();
           updateStatusNotifications();
           updateSystem(null);
       }		
    }
	
	class InsulinObserver extends ContentObserver 
    {	
    	private int count;
    	
    	public InsulinObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "Insulin Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		
    		count = 0;
    	}

       @Override
       public void onChange(boolean selfChange) 
       {
    	   this.onChange(selfChange, null);
       }		

       public void onChange(boolean selfChange, Uri uri) 
       {
    	   final String FUNC_TAG = "onChange";
    	   
    	   count++;
    	   Debug.i(TAG, FUNC_TAG, "Insulin Observer: "+count);
    	   
    	   Cursor c = getContentResolver().query(Biometrics.INSULIN_URI, new String[]{"deliv_time","deliv_meal","deliv_corr"}, 
    			   "deliv_time > "+pump_last_bolus_time+" AND status != 21 AND (deliv_meal > 0 OR deliv_corr > 0)", 
    			   null, null);
    	   
    	   if(c != null)
    	   {
    		   if(c.moveToLast())
    		   { 
    			   double meal = 0, corr = 0;
    			   long time;
    			   		   
    			   time = c.getLong(c.getColumnIndex("deliv_time"));
    			   meal = c.getDouble(c.getColumnIndex("deliv_meal"));
    			   corr = c.getDouble(c.getColumnIndex("deliv_corr"));
    			   
    			   Debug.i(TAG, FUNC_TAG, "Time: "+time+" M: "+meal+" C:"+corr);
    			   
    			   pump_last_bolus = 0;
				   pump_last_bolus_time = time;
				   
    			   if(meal > Pump.EPSILON)
    				   pump_last_bolus += meal;
    			   if(corr > Pump.EPSILON)
    				   pump_last_bolus += corr;
    		   }
    	   }
    	   c.close();
    	   
    	   updateBarIcon();
           updateStatusNotifications();
           updateSystem(null);
       }		
    }

	class TempBasalObserver extends ContentObserver 
    {	
    	private int count;
    	
    	public TempBasalObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "TempBasalObserver";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		
    		count = 0;
    	}

       @Override
       public void onChange(boolean selfChange) 
       {
    	   this.onChange(selfChange, null);
       }		

       public void onChange(boolean selfChange, Uri uri) {
    	   final String FUNC_TAG = "onChange";
    	   
    	   count++;
    	   Debug.i(TAG, FUNC_TAG, "count: "+count);
    	   
    	   Cursor c = getContentResolver().query(Biometrics.TEMP_BASAL_URI, null, null, null, null);
    	   if(c!=null) {
          		if(c.moveToLast()) {
          			temp_basal_start_time = c.getLong(c.getColumnIndex("start_time"));
          			temp_basal_scheduled_end_time = c.getLong(c.getColumnIndex("scheduled_end_time"));
          			temp_basal_status_code = c.getInt(c.getColumnIndex("status_code"));
          			temp_basal_owner = c.getInt(c.getColumnIndex("owner"));
          			temp_basal_percent_of_profile_basal_rate = c.getInt(c.getColumnIndex("percent_of_profile_basal_rate"));
          		}
      			c.close();
    	   }
    	   updateBarIcon();
           updateStatusNotifications();
           updateSystem(null);
       }
    }
	
	class ExerciseStateObserver extends ContentObserver 
    {	
    	private int count;
    	
    	public ExerciseStateObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "Exercise State Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		
    		count = 0;
    	}

       @Override
       public void onChange(boolean selfChange) 
       {
    	   this.onChange(selfChange, null);
       }		

       public void onChange(boolean selfChange, Uri uri) 
       {
    	   final String FUNC_TAG = "onChange";
    	   
    	   count++;
    	   Debug.i(TAG, FUNC_TAG, "Exercise State Observer: "+count);
    	   
    	   Cursor c = getContentResolver().query(Biometrics.EXERCISE_STATE_URI, null, null, null, null);
    	   if(c != null)
    	   {
    		   if(c.moveToLast())
    		   { 
    			   int exercising = c.getInt(c.getColumnIndex("currentlyExercising"));
    			   
    			   switch(exercising)
    			   {
	    			   case Exercise.EXER:
	    				   currentlyExercising = true;
	    				   break;
	    			   case Exercise.NOT_EXER:
	    				   currentlyExercising = false;
	    			   case Exercise.DONT_CARE:
    				   default:
    					   break;
    			   }
    			   
    			   Debug.i(TAG, FUNC_TAG, "CurrentlyExercising: "+currentlyExercising);
    			   
    			   if(exercising != Exercise.DONT_CARE)
    			   {
	    			   if (currentlyExercising) 
	    			   {
	    				   Date date = new Date(getCurrentTimeSeconds()*1000);
	    				   if(currentlyExercising != previouslyExercising) {
		    				   Bundle b = new Bundle();
		    				   b.putString("description", "Exercise started at "+DateFormat.getDateTimeInstance().format(date));
		    				   Event.addEvent(getApplicationContext(), Event.EVENT_BEGIN_EXERCISE, Event.makeJsonString(b), Event.SET_LOG);
	    				   }
	    				   previouslyExercising = true;
	    			   }
	    			   else 
	    			   {
	    				   Date date = new Date(getCurrentTimeSeconds()*1000);
	    				   if(currentlyExercising != previouslyExercising) {
		    				   Bundle b = new Bundle();
		    				   b.putString("description", "Exercise stopped at "+DateFormat.getDateTimeInstance().format(date));
		    				   Event.addEvent(getApplicationContext(), Event.EVENT_END_EXERCISE, Event.makeJsonString(b), Event.SET_LOG);
	    				   }
	    				   previouslyExercising = false;
	    			   }
	    			   
	    			   updateBarIcon();
	    	           updateStatusNotifications();
	    	           updateSystem(null);
    			   }
    			   else
    				   Debug.i(TAG, FUNC_TAG, "We don't care about exercise...");
    		   }
    	   }
    	   c.close();
       }		
    }
	
	// ******************************************************************************************************************************
	// DIAS SERVICE UPDATE ROUTINES
	// ******************************************************************************************************************************
	
	public void updateDiasService(int requestedMode, boolean isClick) {
		final String FUNC_TAG = "updateDiasService";
		
		Debug.i(TAG, FUNC_TAG, "Requested Mode="+requestedMode+", DIAS_STATE="+DIAS_STATE+", is Button Click="+isClick);
		updateCount++;
		Debug.i(TAG, FUNC_TAG, "COUNT: "+updateCount);
		
		long begin = System.currentTimeMillis();
		long start = System.currentTimeMillis();
		long stop;
		
		Debug.i(TAG, FUNC_TAG, "Start: "+begin);
		
		updateDiasState(requestedMode, isClick);
		stop = System.currentTimeMillis() - start;
		Debug.i(TAG, FUNC_TAG, "updateDiasState: "+stop);
		start = System.currentTimeMillis();
		
		updateStatusNotifications();
		stop = System.currentTimeMillis() - start;
		Debug.i(TAG, FUNC_TAG, "updateStatusNotifications: "+stop);
		start = System.currentTimeMillis();
		
		updateSystem(null);
		stop = System.currentTimeMillis() - start;
		Debug.i(TAG, FUNC_TAG, "updateSystem: "+stop);
		
		stop = System.currentTimeMillis() - begin;
		Debug.i(TAG, FUNC_TAG, "Stop: "+stop);
	}
	
	/**
	 * Updates Status Bar Notifications
	 */
	private void updateStatusNotifications() {
		final String FUNC_TAG = "updateStatusNotifications";
		
        // Update CGM trend arrow
        // ***************************************************************
        int arrowResource = R.drawable.arrow_0;
		
		switch (cgm_trend) 
		{
			case 2:
				arrowResource = R.drawable.arrow_2;
				break;
			case 1:
				arrowResource = R.drawable.arrow_1;
				break;
			case 0:
				arrowResource = R.drawable.arrow_0;
				break;
			case -1:
				arrowResource = R.drawable.arrow_m1;
				break;
			case -2:
				arrowResource = R.drawable.arrow_m2;
				break;
		}

    	int blood_glucose_display_units = Params.getInt(getContentResolver(), "blood_glucose_display_units", CGM.BG_UNITS_MG_PER_DL);
    	
    	double cgm_value_in_selected_units = cgm_value;
    	String unit_string = new String(" mg/dl");
		DecimalFormat decimalFormat = new DecimalFormat();
    	if (blood_glucose_display_units == CGM.BG_UNITS_MMOL_PER_L) {
    		cgm_value_in_selected_units = cgm_value_in_selected_units/CGM.MGDL_PER_MMOLL;
    		unit_string = " mmol/L";
    		decimalFormat.setMinimumFractionDigits(1);
    		decimalFormat.setMaximumFractionDigits(1);
    	}
    	else {
    		decimalFormat.setMinimumFractionDigits(0);
    		decimalFormat.setMaximumFractionDigits(0);
    	}
		String CGMString = decimalFormat.format(cgm_value_in_selected_units);
    	
		if ((cgm_value_in_selected_units) > 0 && (cgm_state == CGM.CGM_NORMAL)) {
			Intent cgmValueIntent = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
			cgmValueIntent.putExtra("id", 9);
			cgmValueIntent.putExtra("text", " " + CGMString + unit_string);
			cgmValueIntent.putExtra("color", Color.rgb(0xfe, 0xfe, 0xfe)); //Custom icon ignores a totally white color (#FFFFFF), so it needs to be just slightly off-white if you want a white color... I should change that
			sendBroadcast(cgmValueIntent);
			
			Intent cgmArrowIntent = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
			cgmArrowIntent.putExtra("id", 10);
			cgmArrowIntent.putExtra("resourcePackage", "edu.virginia.dtc.DiAsService");	
			cgmArrowIntent.putExtra("resourceID", arrowResource);
			sendBroadcast(cgmArrowIntent);
		}
		else
		{
			Intent cgmValueIntent = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
			cgmValueIntent.putExtra("id", 9);
			cgmValueIntent.putExtra("remove", true);
			sendBroadcast(cgmValueIntent);
			
			Intent cgmArrowIntent = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
			cgmArrowIntent.putExtra("id", 10);
			cgmArrowIntent.putExtra("remove", true);
			sendBroadcast(cgmArrowIntent);
		}
        
		// Update CGM status light
        // ***************************************************************
		int cgmColor= Color.DKGRAY;
		String cgmText = " CGM ";
		
		if (cgm_state == CGM.CGM_NOISE) {
			cgmColor = Color.rgb(255, 239, 0);		// No cgm signal - yellow			
		}
		else if (cgm_state == CGM.CGM_WARMUP) {
			cgmText = " WRM-UP ";
			cgmColor = Color.rgb(255, 239, 0);		// Warm-up - yellow
		}
		else if (cgm_state == CGM.CGM_CALIBRATION_NEEDED || cgm_state == CGM.CGM_CAL_HIGH || cgm_state == CGM.CGM_CAL_LOW) {
			cgmText = " CALIB ";
			cgmColor = Color.rgb(255, 0, 0);   		// Calibration needed - red
		}
		else if (cgmReady()) {
			cgmColor = Color.rgb(0, 255, 0);		// Calibrated and does not currently need calibration - green light				
		}
		else {
			cgmColor = Color.DKGRAY;				// Not connected - dim light
		}
		
    	Intent cgmState = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
    	cgmState.putExtra("id", 12);
    	cgmState.putExtra("text", cgmText);
    	cgmState.putExtra("color",cgmColor);
		sendBroadcast(cgmState);
    			
		// Update Pump status light
		// ***************************************************************
    	int pumpColor=Color.DKGRAY;
    	String pumpText = " PUMP";
    	
    	if (pump_state == Pump.RECONNECTING)
    	{
			pumpText = " RCNCT";
			pumpColor= Color.rgb(255, 0, 0);
		}
    	else if(pump_service_state == Pump.PUMP_STATE_DELIVER || pump_service_state == Pump.PUMP_STATE_ACC)
		{
			pumpText = " DLVR";
			pumpColor= Color.rgb(255, 239, 0);
		}
		else if(pump_service_state == Pump.PUMP_STATE_PUMP_ERROR)
		{
			pumpText = " ERR";
			pumpColor= Color.rgb(255, 0, 0);
		}
		else if(pump_service_state == Pump.PUMP_STATE_SET_TBR)
		{
			pumpText = " TBR";
			pumpColor= Color.rgb(255, 239, 0);
		}
		else if (pump_state == Pump.CONNECTED_LOW_RESV){
			pumpText = " LOW-RES";
			pumpColor= Color.rgb(255, 239, 0);  			//Low-reservoir yellow
		}
		else if (pump_state == Pump.CONNECTED){
			pumpColor= Color.rgb(0, 255, 0);
		}
		else
		{
			pumpColor= Color.DKGRAY;
		}
		
		Intent pumpState = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
    	pumpState.putExtra("id", 14);
    	pumpState.putExtra("text", pumpText);
    	pumpState.putExtra("color", pumpColor);
    	sendBroadcast(pumpState);

    	Intent statusBreakLine= new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
    	statusBreakLine.putExtra("id", 7);
    	statusBreakLine.putExtra("text","|");
    	statusBreakLine.putExtra("color", Color.GRAY);
    	sendBroadcast(statusBreakLine);
    	
    	if (cgm_value > 0) 
    	{
	    	Intent statusBreakLine2= new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
	    	statusBreakLine2.putExtra("id", 11);
	    	statusBreakLine2.putExtra("text"," |");
	    	statusBreakLine2.putExtra("color", Color.GRAY);
	    	sendBroadcast(statusBreakLine2);
    	}
    	
    	Intent statusBreakLine3= new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
    	statusBreakLine3.putExtra("id", 13);
    	statusBreakLine3.putExtra("text","|");
    	statusBreakLine3.putExtra("color", Color.GRAY);
    	sendBroadcast(statusBreakLine3);
    	
		// Update Temporary Basal Rate status light
		// ***************************************************************
    	
 	   // Erase status if no temporary basal rate is active
 	   long time = getCurrentTimeSeconds();
 	   Intent statusTempBasal= new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
 	   statusTempBasal.putExtra("id", 3);
 	   temporaryBasalRateActive();
 	   
 	   if (temp_basal_status_code == TempBasal.TEMP_BASAL_RUNNING && time >= temp_basal_start_time && time <= temp_basal_scheduled_end_time) 
 	   {		   
 		   int hours_left = (int)(temp_basal_scheduled_end_time - time)/3600;
 		   int minutes_left = (int)(temp_basal_scheduled_end_time - time - (long)hours_left*3600)/60;
 		   String tempBasalPercentage = String.format("%3d", temp_basal_percent_of_profile_basal_rate);
 		   String tempBasalTimeRemaining = String.format("%02d:%02d", hours_left, minutes_left);
 		   statusTempBasal.putExtra("text", tempBasalPercentage+"%, " + tempBasalTimeRemaining);
 		   if (temp_basal_percent_of_profile_basal_rate == 100) {
 			   statusTempBasal.putExtra("color", Color.WHITE);
 		   }
 		   else if (temp_basal_percent_of_profile_basal_rate > 100) {
 			   statusTempBasal.putExtra("color", Color.GREEN);
 		   }
 		   else {
 			   statusTempBasal.putExtra("color", Color.RED);
 		   }
 	   }
 	   else 
 	   {
// 		   statusTempBasal.putExtra("text","---%, --:--");
// 		   statusTempBasal.putExtra("color", Color.DKGRAY);
 		   statusTempBasal.putExtra("remove", true);
 	   }
 	   sendBroadcast(statusTempBasal);
 	   
 	   Intent statusBreakLine4= new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
 	   statusBreakLine4.putExtra("id", 4);
 	   statusBreakLine4.putExtra("text"," |");
 	   statusBreakLine4.putExtra("color", Color.GRAY);
 	   sendBroadcast(statusBreakLine4);

	}
	
	/**
	 * Checks for Hypo Red Light when in Closed Loop or Safety Mode
	 * - Generates an Event (Hypo Events lead to custom dialog after first alert)
	 */	
	private void checkForHypo() {
		final String FUNC_TAG = "checkForHypo";
		
        Debug.i(TAG, FUNC_TAG, "Updating hypo alarm...");
        
        if (hypoLight == Safety.RED_LIGHT && !isMealBolus && (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY)) {
    		if (DIAS_STATE != State.DIAS_STATE_SENSOR_ONLY) {
    			if (getCurrentTimeSeconds() - hypoFlagTime > hypoMuteDuration*60) {
    				
    				hypoMuteDuration = 1;
        			Debug.i(TAG, FUNC_TAG, "hypo red light alarm > isMealBolus="+isMealBolus);
        			
        			int unit = Params.getInt(getContentResolver(), "blood_glucose_display_units", 0);
        			
        			String cgm_unit_display = "", cgm_display = "";
        			DecimalFormat decimalFormat = new DecimalFormat();
        			
        			switch (unit){
        				case CGM.BG_UNITS_MMOL_PER_L:
        					cgm_unit_display = "mmol/L";
        					decimalFormat.setMinimumFractionDigits(1);
        		    		decimalFormat.setMaximumFractionDigits(1);
        		    		cgm_display = decimalFormat.format((double)cgm_value/CGM.MGDL_PER_MMOLL) +" "+ cgm_unit_display;
        					break;
        				case CGM.BG_UNITS_MG_PER_DL:
        				default:
        					cgm_unit_display = "mg/dL";
        					decimalFormat.setMinimumFractionDigits(0);
        		    		decimalFormat.setMaximumFractionDigits(0);
        		    		cgm_display = decimalFormat.format(cgm_value) +" "+ cgm_unit_display;
        					break;
        			}
        			
        			Bundle b = new Bundle();
        			b.putString("description", "Hypoglycemia predicted. Current CGM is "+cgm_display+". Please check your blood glucose.");
    	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_HYPO_ALARM, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_HYPO);
    			}
    		}
        }
	}
	
	/**
	 * Checks for Hyper Red Light when in Closed Loop or Safety Mode
	 * - Generates an Event
	 */
	private void checkForHyper() {
		final String FUNC_TAG = "checkForHyper";
		
		Debug.i(TAG, FUNC_TAG, "Checking hyper alarm...");
        
        if (hyperLight == Safety.RED_LIGHT && (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY)) 
        {
        	if (DIAS_STATE != State.DIAS_STATE_SENSOR_ONLY) {
    			if (getCurrentTimeSeconds() - hyperFlagTime > hyperMuteDuration*60) {
    				
    				hyperMuteDuration = 1;
        			Debug.i(TAG, FUNC_TAG, "Hyper red light alarm!");
        			
        			int unit = Params.getInt(getContentResolver(), "blood_glucose_display_units", 0);
        			
        			String cgm_unit_display = "", cgm_display = "";
        			DecimalFormat decimalFormat = new DecimalFormat();
        			
        			switch (unit){
        				case CGM.BG_UNITS_MMOL_PER_L:
        					cgm_unit_display = "mmol/L";
        					decimalFormat.setMinimumFractionDigits(1);
        		    		decimalFormat.setMaximumFractionDigits(1);
        		    		cgm_display = decimalFormat.format((double)cgm_value/CGM.MGDL_PER_MMOLL) +" "+ cgm_unit_display;
        					break;
        				case CGM.BG_UNITS_MG_PER_DL:
        				default:
        					cgm_unit_display = "mg/dL";
        					decimalFormat.setMinimumFractionDigits(0);
        		    		decimalFormat.setMaximumFractionDigits(0);
        		    		cgm_display = decimalFormat.format(cgm_value) +" "+ cgm_unit_display;
        					break;
        			}
        			
        			Bundle b = new Bundle();
        			b.putString("description", "Hyperglycemia predicted. Current CGM is "+cgm_display+". Please check your blood glucose.");
    	    		Event.addEvent(this, Event.EVENT_SYSTEM_HYPER_ALARM, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
    			}
    		}
        }
	}
	
	/**
	 * Checks for CGM value in the range "CGM_WARN_DELAY_MINS" to "CGM_MAX_DELAY_MINS" and issues an alert for No CGM
	 * - Generates an Event
	 */
	private void checkForCgm()
	{
		final String FUNC_TAG = "checkForCGM";
		
		long diff = getCurrentTimeSeconds() - cgm_last_normal_time_sec;
		int diff_mins = (int)diff/60;
		
		if (diff_mins >= CGM_WARN_DELAY_MINS && diff_mins < CGM.CGM_MAX_DELAY_MINS) {
			// Event/alert generated every two minutes
			if (diff_mins % 2 == 0) {
				String desc = "Warning:  No CGM for "+diff_mins+" minutes!";
				
				Debug.i(TAG, FUNC_TAG, desc);
				
				Bundle b = new Bundle();
	    		int setting;
	    		
	    		if (diff_mins > CGM_WARN_DELAY_MINS+2) {
	    			setting = Event.SET_POPUP_AUDIBLE_ALARM;
	    		}
	    		else {
	    			setting = Event.SET_POPUP_AUDIBLE_VIBE;
	    		}
	    		
	    		b.putString("description", desc);
	    		
	    		Event.addEvent(getApplicationContext(), Event.EVENT_CGM_WARN, Event.makeJsonString(b), setting);
			}
		}
	}

	/**
	 * Switch to Pump Mode if No CGM for "CGM_MAX_DELAY_MINS" and update Basal Pause/Resume Status
	 * - Generates an Event
	 */
	private void updateBasalPauseStatus() {
		final String FUNC_TAG = "updateBasalPauseStatus";
		
		SharedPreferences basalPauseSetting = getSharedPreferences(PREFS_NAME, 0);
		// True if we have *valid* CGM data from within the last 20 minutes
    
		// Handle loss of CGM when we are in Closed Loop mode
		if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) {
			if(!cgmGetsRecentValues()){
				
				// Depending on the last Hypolight state, Basal will be paused for a given duration
				switch (hypoLight) {
					case Safety.GREEN_LIGHT: basalPauseDuration = basalPauseDurationOnGreenLight; break;
					case Safety.YELLOW_LIGHT: basalPauseDuration = basalPauseDurationOnYellowLight; break;
					case Safety.RED_LIGHT: basalPauseDuration = basalPauseDurationOnRedLight; break;
					default: break;
				}
				
				// Make the switch to Pump mode
				if (Mode.isPumpModeAvailable(getContentResolver()))
  					updateDiasService(State.DIAS_STATE_OPEN_LOOP, false);
  				else
  					updateDiasService(State.DIAS_STATE_SENSOR_ONLY, false);

				// Set the temporary basal rate
				if (basalPauseDuration > 0) {
					startTempBasal(basalPauseDuration*60, 0);
				}
				
				Bundle b = new Bundle();
				b.putString("description", "Switch to Pump Only mode because of missing CGM data. Basal injection Paused for "+ basalPauseDuration +" minutes");
				Event.addEvent(getApplicationContext(), Event.EVENT_BASAL_PAUSED, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
				Debug.i(TAG, FUNC_TAG, "Loss of CGM data signal, switch to Open Loop.");
			}
	    }
	}
	
	private void updateDiasState(int requestedMode, boolean isClick)
	{
		final String FUNC_TAG = "updateDiasState";
		Debug.i(TAG, FUNC_TAG, "requested Mode="+requestedMode+", DIAS_STATE="+DIAS_STATE);
		
		int oldState = DIAS_STATE;
		
		//If we transition modes then we clear the recovery flag
		clearRecoveryFlag();
		
		// TODO: Figure out whether we want to check 'Sensor Only' mode availability.
		switch(DIAS_STATE) {
			case State.DIAS_STATE_STOPPED:
				if (pumpReady() && cgmReady() && requestedMode == State.DIAS_STATE_CLOSED_LOOP && checkModeAvailability(State.DIAS_STATE_CLOSED_LOOP, isClick)) {
					changeDiasState(State.DIAS_STATE_CLOSED_LOOP);
				}
				else if (pumpReady() && cgmReady() && requestedMode == State.DIAS_STATE_SAFETY_ONLY && checkModeAvailability(State.DIAS_STATE_SAFETY_ONLY, isClick)) {
					changeDiasState(State.DIAS_STATE_SAFETY_ONLY);
				}
				else if (pumpReady() && requestedMode == State.DIAS_STATE_OPEN_LOOP && checkModeAvailability(State.DIAS_STATE_OPEN_LOOP, isClick)) {
					changeDiasState(State.DIAS_STATE_OPEN_LOOP);
				}
				else if (cgmReady() && requestedMode == State.DIAS_STATE_SENSOR_ONLY) {
					changeDiasState(State.DIAS_STATE_SENSOR_ONLY);
				}
				break;
			case State.DIAS_STATE_OPEN_LOOP:
				if (pumpReady() && cgmReady() && requestedMode == State.DIAS_STATE_CLOSED_LOOP && checkModeAvailability(State.DIAS_STATE_CLOSED_LOOP, isClick)) {
					changeDiasState(State.DIAS_STATE_CLOSED_LOOP);
				}
				else if (pumpReady() && cgmReady() && requestedMode == State.DIAS_STATE_SAFETY_ONLY && checkModeAvailability(State.DIAS_STATE_SAFETY_ONLY, isClick)) {
					changeDiasState(State.DIAS_STATE_SAFETY_ONLY);
				}
				else if(requestedMode == State.DIAS_STATE_SENSOR_ONLY)
				{
					if(cgmReady())
					{
						changeDiasState(State.DIAS_STATE_SENSOR_ONLY);
					}
					else
					{
						changeDiasState(State.DIAS_STATE_STOPPED);
					}
				}
				else if (requestedMode == State.DIAS_STATE_STOPPED) {
					changeDiasState(State.DIAS_STATE_STOPPED);
				}
				else if (!pumpReady()) {
					changeDiasState(State.DIAS_STATE_STOPPED);
				}
				break;
			case State.DIAS_STATE_CLOSED_LOOP:
				if (!(pumpReady())) {
					if(cgmReady())
						changeDiasState(State.DIAS_STATE_SENSOR_ONLY);
					else
						changeDiasState(State.DIAS_STATE_STOPPED);
				}
				else if (pumpReady() && requestedMode == State.DIAS_STATE_OPEN_LOOP && checkModeAvailability(State.DIAS_STATE_OPEN_LOOP, isClick)) {
					changeDiasState(State.DIAS_STATE_OPEN_LOOP);
				}
				else if (pumpReady() && cgmReady() && requestedMode == State.DIAS_STATE_SAFETY_ONLY && checkModeAvailability(State.DIAS_STATE_SAFETY_ONLY, isClick)) {
					changeDiasState(State.DIAS_STATE_SAFETY_ONLY);
				}
				else if(requestedMode == State.DIAS_STATE_SENSOR_ONLY)
				{
					if(cgmReady())
					{
						changeDiasState(State.DIAS_STATE_SENSOR_ONLY);
					}
					else
					{
						changeDiasState(State.DIAS_STATE_STOPPED);
					}
				}
				else if (requestedMode == State.DIAS_STATE_STOPPED) {
					changeDiasState(State.DIAS_STATE_STOPPED);
				}
				break;
			case State.DIAS_STATE_SENSOR_ONLY:
				if ( !(cgmReady())) {
					changeDiasState(State.DIAS_STATE_STOPPED);
				}
				else if (pumpReady() && requestedMode == State.DIAS_STATE_OPEN_LOOP && checkModeAvailability(State.DIAS_STATE_OPEN_LOOP, isClick)) {
					changeDiasState(State.DIAS_STATE_OPEN_LOOP);
				}
				else if (pumpReady() && cgmReady() && requestedMode == State.DIAS_STATE_CLOSED_LOOP && checkModeAvailability(State.DIAS_STATE_CLOSED_LOOP, isClick)) {
					changeDiasState(State.DIAS_STATE_CLOSED_LOOP);
				}
				else if (pumpReady() && cgmReady() && requestedMode == State.DIAS_STATE_SAFETY_ONLY && checkModeAvailability(State.DIAS_STATE_SAFETY_ONLY, isClick)) {
					changeDiasState(State.DIAS_STATE_SAFETY_ONLY);
				}
				else if (requestedMode == State.DIAS_STATE_STOPPED) {
					changeDiasState(State.DIAS_STATE_STOPPED);
				}
				break;
			case State.DIAS_STATE_SAFETY_ONLY:
				if (pumpReady() && requestedMode == State.DIAS_STATE_OPEN_LOOP && checkModeAvailability(State.DIAS_STATE_OPEN_LOOP, isClick)) {
					changeDiasState(State.DIAS_STATE_OPEN_LOOP);
				}
				else if (pumpReady() && cgmReady() && requestedMode == State.DIAS_STATE_CLOSED_LOOP && checkModeAvailability(State.DIAS_STATE_CLOSED_LOOP, isClick)) {
					changeDiasState(State.DIAS_STATE_CLOSED_LOOP);
				}
				else if(requestedMode == State.DIAS_STATE_SENSOR_ONLY)
				{
					if(cgmReady())
					{
						changeDiasState(State.DIAS_STATE_SENSOR_ONLY);
					}
					else
					{
						changeDiasState(State.DIAS_STATE_STOPPED);
					}
				}
				else if (requestedMode == State.DIAS_STATE_STOPPED) {
					changeDiasState(State.DIAS_STATE_STOPPED);
				}
				else if (!(pumpReady())) {
					changeDiasState(State.DIAS_STATE_STOPPED);
				}
				break;
		}
		
		if (oldState != DIAS_STATE) 
		{
			updateTbr(oldState);
		}
		
		updateBarIcon();
	}
	
	
	private void updateTbr(int oldState)
	{
		final String FUNC_TAG = "updateTbr";
		Debug.i(TAG, FUNC_TAG, oldState+" => "+DIAS_STATE);
        
        if(oldState != DIAS_STATE)
        {
        	if((oldState == State.DIAS_STATE_STOPPED || oldState == State.DIAS_STATE_SENSOR_ONLY) && 
        			(DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_OPEN_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY))
        	{
        		sendTbrCommand(true);
        	}
        	else if((oldState == State.DIAS_STATE_CLOSED_LOOP || oldState == State.DIAS_STATE_OPEN_LOOP || oldState == State.DIAS_STATE_SAFETY_ONLY) &&
    				(DIAS_STATE == State.DIAS_STATE_STOPPED || DIAS_STATE == State.DIAS_STATE_SENSOR_ONLY))
    		{
        		sendTbrCommand(false);
    		}
        }
	}
	
	
	private void updateBarIcon()
	{
		//If the state changes then change the label in the notification bar
		int stateColor=Color.rgb(255, 0, 0);
		String stateText=" STOPPED ";
		
    	switch(DIAS_STATE)
    	{
	    	case State.DIAS_STATE_STOPPED:
				stateText=" STOPPED ";
				stateColor=Color.rgb(255, 0, 0);
				break;
			case State.DIAS_STATE_OPEN_LOOP:
				stateText= " PUMP MODE ";
				stateColor= Color.rgb(0, 128, 255);
				break;
			case State.DIAS_STATE_CLOSED_LOOP:
				stateText=" CLOSED ";
				stateColor= Color.rgb(0, 255, 0);
				break;
			case State.DIAS_STATE_SAFETY_ONLY:
				stateText= " SAFETY ";
				stateColor=Color.rgb(186, 85, 211);
				break;
			case State.DIAS_STATE_SENSOR_ONLY:
				stateText= " SENSOR ";
				stateColor= Color.rgb(255, 255, 0);
				break;
    	}
    	
    	Intent controllerState = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
    	controllerState.putExtra("id", 6);
    	controllerState.putExtra("text", stateText);
    	controllerState.putExtra("color", stateColor);
    	sendBroadcast(controllerState);
	}
	
	// ******************************************************************************************************************************
	// DIAS SERVICE STATUS ACCESSORS
	// ******************************************************************************************************************************
    private void changeDiasState(int diasState)
    {
    	int OLD_DIAS_STATE = DIAS_STATE;
    	DIAS_STATE = diasState;
    	String state_string = new String();
    	int event_code;
    	boolean insulin_dosing = false;
    	if (temporaryBasalRateActive()) {
    		if (temp_basal_owner == TempBasal.TEMP_BASAL_OWNER_DIASSERVICE)
    			cancelTemporaryBasalRate();
    	}
    	switch (DIAS_STATE) {
    		case State.DIAS_STATE_STOPPED:
    			state_string = "DIAS_STATE_STOPPED";
    			event_code= Event.EVENT_STOPPED_MODE;
    			cancelTemporaryBasalRate();
    			break;
    		case State.DIAS_STATE_OPEN_LOOP:
    			state_string = "DIAS_STATE_OPEN_LOOP";
    			event_code= Event.EVENT_PUMP_MODE;
    			insulin_dosing = true;
    			cancelTemporaryBasalRate();
    			break;
    		case State.DIAS_STATE_CLOSED_LOOP:
    			state_string = "DIAS_STATE_CLOSED_LOOP";
    			event_code= Event.EVENT_CLOSED_LOOP_MODE;
    			insulin_dosing = true;
    			cancelTemporaryBasalRate();
    			break;
    		case State.DIAS_STATE_SAFETY_ONLY:
    			state_string = "DIAS_STATE_SAFETY_ONLY";
    			event_code= Event.EVENT_SAFETY_MODE;
    			insulin_dosing = true;
    			cancelTemporaryBasalRate();
    			break;
    		case State.DIAS_STATE_SENSOR_ONLY:
    			state_string = "DIAS_STATE_SENSOR_ONLY";
    			event_code= Event.EVENT_SENSOR_MODE;
    			cancelTemporaryBasalRate();
    			break;
    		default:
    			state_string = "UNKNOWN";
    			event_code= Event.EVENT_UNKNOWN_MODE;
    			break;
    	}
		Bundle b = new Bundle();
		if (OLD_DIAS_STATE == State.DIAS_STATE_STOPPED && insulin_dosing && !Params.getBoolean(getContentResolver(), "tbr_enabled", false)) {
			b.putString("description", "TBR disabled.  Make sure that basal insulin delivery is DISABLED on your pump.");
			Event.addEvent(getApplicationContext(), event_code, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
		}
		else if (OLD_DIAS_STATE == State.DIAS_STATE_STOPPED && insulin_dosing && Params.getBoolean(getContentResolver(), "tbr_enabled", false) && TEMP_BASAL_ENABLED) {
			b.putString("description", "TBR enabled.  Make sure that basal insulin delivery is ENABLED on your pump.");
			Event.addEvent(getApplicationContext(), event_code, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
		}
		else {
			b.putString("description", "New DiAs State "+state_string);
			Event.addEvent(getApplicationContext(), event_code, Event.makeJsonString(b), Event.SET_LOG);
		}
    }
    
    private void writeTrafficLights(int calling, int hypo, int hyper)
    {
    	final String FUNC_TAG = "writeTrafficLights";
    	
    	int traffic_light = Params.getInt(getContentResolver(), "traffic_lights", Safety.TRAFFIC_LIGHT_CONTROL_DISABLED);
		if(DIAS_STATE != State.DIAS_STATE_SENSOR_ONLY || (DIAS_STATE == State.DIAS_STATE_SENSOR_ONLY &&  (traffic_light == Safety.TRAFFIC_LIGHT_CONTROL_APCSERVICE || traffic_light == Safety.TRAFFIC_LIGHT_CONTROL_BRMSERVICE)))
		{
			if(traffic_light == calling && DIAS_STATE != State.DIAS_STATE_STOPPED)
			{
				hypoLight = hypo;
				hyperLight = hyper;
				
				Debug.i(TAG, FUNC_TAG, "Traffic Light Output Hyper: "+hyperLight+" Hypo: "+hypoLight+" (Written by Mode="+calling+")");
			}
			else if(DIAS_STATE == State.DIAS_STATE_STOPPED)
				Debug.w(TAG, FUNC_TAG, "The system is in STOPPED mode, so we don't write the lights...");
			else
				Debug.w(TAG, FUNC_TAG, "The calling service (Calling Mode="+calling+") is not in control of the traffic lights (Traffic Light Mode="+traffic_light+")!");
		}
    }
    
    private boolean cgmGetsRecentValues()
    {
    	long diff = getCurrentTimeSeconds() - cgm_last_normal_time_sec;
    	
    	//Make sure last normal CGM isn't 20 minutes behind or 10 minutes ahead
    	return (diff < (CGM.CGM_MAX_DELAY_MINS*60) && diff > (CGM.CGM_MAX_AHEAD_MINS*60));
    }
    
    private boolean cgmReady()
    {
    	long diff = getCurrentTimeSeconds() - cgm_last_time_sec;
    	
    	//Make sure CGM isn't 20 minutes behind or 10 minutes ahead
    	if (diff < (CGM.CGM_MAX_DELAY_MINS*60) && diff > (CGM.CGM_MAX_AHEAD_MINS*60)) {
    		return true;
    	}
    	else {
    		cgm_state = CGM.CGM_NOT_ACTIVE;	
    		return false;
    	}
    }
    
    private boolean pumpReady()
    {
    	switch(pump_state)
	    {
    		case Pump.RECONNECTING:
	    	case Pump.CONNECTED:
	    	case Pump.CONNECTED_LOW_RESV:
    			return true;
	    	default:
	    		return false;
    	}
    }
    
    private boolean batteryReady()
    {
    	final int BATT_SHUTDOWN_THRESH = 10;
    	
    	if(battCharge > BATT_SHUTDOWN_THRESH)
    		return true;
    	else
    		return false;
    }
	
	// ******************************************************************************************************************************
	// ALARM MISC FUNCTIONS
	// ******************************************************************************************************************************
	
    private void manageBatteryAlerts()
    {
    	int type = Event.SET_POPUP_AUDIBLE;
    	String message = null;
    	boolean fire = false;
    	
    	if(!alert10 && battCharge <= 10 && battIsCharging<=0)
    	{
    		alert20 = alert15 = alert10 = true;		//If this event is on, then we don't need to fire the others
    		type = Event.SET_POPUP_AUDIBLE_ALARM;
    		message = "Battery charge critical! Please plug in the device!";
    		fire = true;
    	}
    	
    	if(!alert15 && battCharge <= 15 && battIsCharging<=0)
    	{
    		alert20 = alert15 = true;
    		message = "Battery charge low! Please plug in the device!";
    		fire = true;
    	}
    	
    	if(!alert20 && battCharge <= 20 && battIsCharging<=0)
    	{
    		alert20 = true;
    		message = "Battery charge low!";
    		fire = true;
    	}
    	
    	if(battIsCharging>0) {
    		alert20 = alert15 = alert10 = false;
    	}
    	
    	if(fire)
    	{
	    	Bundle b = new Bundle();
			b.putString("description", message);
			Event.addEvent(this, Event.EVENT_SYSTEM_BATTERY, Event.makeJsonString(b), type);
    	}
    }
    
	public void setMinAudioVolume(double minVolumePerc) {
		AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		double currentVolumePerc = max / (double)am.getStreamVolume(AudioManager.STREAM_MUSIC);
		int volume = (int)(Math.max(minVolumePerc, currentVolumePerc) * max);
		am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_VIBRATE); 
	}
	
	// ******************************************************************************************************************************
 	// SYSTEM TABLE FUNCTIONS
 	// ******************************************************************************************************************************
    
    public void updateSystem(ContentValues sv)
    {
    	final String FUNC_TAG = "updateSystem";
    	
    	ContentValues out = new ContentValues();
    	
    	out.put("time", System.currentTimeMillis()/1000);
    	out.put("sysTime", getCurrentTimeSeconds());
        out.put("exercising", (currentlyExercising == true)? 1:0);
        out.put("diasState", DIAS_STATE);
        out.put("safetyMode", (brmEnabled == true) ? 1:0);
        out.put("battery", battCharge);
        	     	
        out.put("cgmValue", cgm_value);
        out.put("cgmTrend", cgm_trend);
        out.put("cgmLastTime", cgm_last_time_sec);
        out.put("cgmState", cgm_state);
        out.put("cgmStatus", "BLANK");
        
        out.put("pumpLastBolus", pump_last_bolus);
        out.put("pumpLastBolusTime", pump_last_bolus_time);
        out.put("pumpState", pump_state);
        out.put("pumpStatus", "BLANK");
        
        out.put("iobValue", latestIOB);
        
        out.put("hypoLight", hypoLight);
        out.put("hyperLight", hyperLight);
        
        out.put("alarmNoCgm", 0);
        out.put("alarmHypo", 0);
        
        if(systemDiff(out))
        {
        	sysUpdate++;
        	Debug.i(TAG, FUNC_TAG, "Re-writing system table row! Number: "+sysUpdate);
        	getContentResolver().insert(Biometrics.SYSTEM_URI, out);
        }
        else
        	Debug.i(TAG, FUNC_TAG, "The system table is the same so no need to re-write...");
    }
    
    public boolean systemDiff(ContentValues out)
    {
    	Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, null, null, null, null);
    	
    	if(c == null)		//If the cursor is null then its empty and we need to return that there is a difference
    		return true;
    	
    	if(c.moveToLast())	//If we can't move to the last row then return true to signify a row should be added
    	{
    		if(diffInt(out, c, "safetyMode")){out.put("apcString", "safetyMode"); return true;}
    		if(diffInt(out, c, "diasState")){out.put("apcString", "diasState"); return true;}
    		
    		if(!batteryReady())
    			if(diffInt(out, c, "battery")){out.put("apcString", "battery"); return true;}
    		
    		if(diffDouble(out, c, "cgmValue")){out.put("apcString", "cgmValue"); return true;}
    		if(diffInt(out, c, "cgmTrend")){out.put("apcString", "cgmTrend"); return true;}
    		if(diffLong(out, c, "cgmLastTime")){out.put("apcString", "cgmLastTime"); return true;}
    		if(diffInt(out, c, "cgmState")){out.put("apcString", "cgmState"); return true;}
    		if(diffString(out, c, "cgmStatus")){out.put("apcString", "cgmStatus"); return true;}
    		
    		if(diffDouble(out, c, "pumpLastBolus")){out.put("apcString", "pumpLastBolus"); return true;}
    		if(diffLong(out, c, "pumpLastBolusTime")){out.put("apcString", "pumpLastBolusTime"); return true;}
    		if(diffInt(out, c, "pumpState")){out.put("apcString", "pumpState"); return true;}
    		if(diffString(out, c, "pumpStatus")){out.put("apcString", "pumpStatus"); return true;}
    		
    		if(diffInt(out, c, "hypoLight")){out.put("apcString", "hypoLight"); return true;}
    		if(diffInt(out, c, "hyperLight")){out.put("apcString", "hyperLight"); return true;}
    		
    		if(diffDouble(out, c, "iobValue")){out.put("apcString", "iobValue"); return true;}
    		
    		if(diffInt(out, c, "exercising")){out.put("apcString", "exercising"); return true;}
    		if(diffInt(out, c, "alarmNoCgm")){out.put("apcString", "alarmNoCgm"); return true;}
    		if(diffInt(out, c, "alarmHypo")){out.put("apcString", "alarmHypo"); return true;}
    		
    		c.close();
    		return false;
    	}
    	else
    	{
    		c.close();
    		return true;
    	}
    }
    
    public boolean diffInt(ContentValues cv, Cursor c, String field)
    {
    	final String FUNC_TAG = "diffInt";
    	
    	if(cv.getAsInteger(field) != c.getInt(c.getColumnIndex(field)))
    	{
    		Debug.i(TAG, FUNC_TAG, "Field: "+field+" is different!");
    		c.close();
    		return true;
    	}
    	else
    		return false;
    }
    
    public boolean diffDouble(ContentValues cv, Cursor c, String field)
    {
    	final String FUNC_TAG = "diffDouble";
    	
    	if(cv.getAsDouble(field) != c.getDouble(c.getColumnIndex(field)))
    	{
    		Debug.i(TAG, FUNC_TAG, "Field: "+field+" is different!");
    		c.close();
    		return true;
    	}
    	else
    		return false;
    }
    
    public boolean diffLong(ContentValues cv, Cursor c, String field)
    {
    	final String FUNC_TAG = "diffLong";
    	
    	if(cv.getAsLong(field) != c.getLong(c.getColumnIndex(field)))
    	{
    		Debug.i(TAG, FUNC_TAG, "Field: "+field+" is different!");
    		c.close();
    		return true;
    	}
    	else
    		return false;
    }
    
    public boolean diffString(ContentValues cv, Cursor c, String field)
    {
    	final String FUNC_TAG = "diffString";
    	
    	if(!(cv.getAsString(field).equalsIgnoreCase(c.getString(c.getColumnIndex(field)))))		//If the strings aren't equal
    	{
    		Debug.i(TAG, FUNC_TAG, "Field: "+field+" is different!");
    		c.close();
    		return true;
    	}
    	else
    		return false;
    }
    
    // ******************************************************************************************************************************
 	// APC CALL FUNCTIONS
 	// ******************************************************************************************************************************
    
    public void changeTbrState(int state)
    {
    	final String FUNC_TAG = "changeTbrState";
    	
    	Debug.i(TAG, FUNC_TAG, "Writing Tbr state to table: "+FSM.callStateToString(state));
    	
    	ContentValues cv = new ContentValues();
    	cv.put("tbr_state", state);
    	getContentResolver().update(Biometrics.STATE_URI, cv, null, null);
    }
    
    public void changeAsyncState(int state)
    {
    	final String FUNC_TAG = "changeAsyncState";
    	
    	Debug.i(TAG, FUNC_TAG, "Writing Async state to table: "+FSM.callStateToString(state));
    	
    	ContentValues cv = new ContentValues();
    	cv.put("async_state", state);
    	getContentResolver().update(Biometrics.STATE_URI, cv, null, null);
    }
    
    public void changeSyncState(int state)
    {
    	final String FUNC_TAG = "changeSyncState";
    	
    	Debug.i(TAG, FUNC_TAG, "Writing Sync state to table: "+FSM.callStateToString(state));
    	
    	ContentValues cv = new ContentValues();
    	cv.put("sync_state", state);
    	getContentResolver().update(Biometrics.STATE_URI, cv, null, null);
    }
    
    private void sendTbrCommand(boolean turnOn)
    {
    	final String FUNC_TAG = "sendTbrCommand";
    	
    	tbrOn = turnOn;
    	
    	if(Params.getBoolean(getContentResolver(), "tbr_enabled", false))
    		changeTbrState(FSM.START);
    	else
    		Debug.w(TAG, FUNC_TAG, "TBR is not enabled at the application level!");
    }
    
    public void tbrFSM()
    {
    	final String FUNC_TAG = "tbrFSM";
    	
    	switch(TBR.state)
    	{
	    	case FSM.IDLE:
	    		break;
	    	case FSM.START:
	    		if(SYNC.isBusy()) {
	    			Debug.i(TAG, FUNC_TAG, "SYNC is running, so we'll chain the TBR for after...");
	    			changeTbrState(FSM.IDLE);
	    		}
	    		else {
		    		changeTbrState(FSM.TBR_CALL);
	    		}
	    		break;
	    	case FSM.TBR_CALL:
	    		Intent pumpIntent = new Intent();
	    		pumpIntent.setClassName("edu.virginia.dtc.PumpService", "edu.virginia.dtc.PumpService.PumpService");
	    		if(!tbrOn) {
	    			Debug.e(TAG, FUNC_TAG, "Turning off TBR!");
	    			pumpIntent.putExtra("time", 0);
	    			pumpIntent.putExtra("target", 100);
	    			pumpIntent.putExtra("cancel", true);
	    		}	
	    		pumpIntent.putExtra("PumpCommand", Pump.PUMP_SERVICE_CMD_SET_TBR);
	    		startService(pumpIntent);
	    		break;
	    	case FSM.TBR_RESPONSE:
	    		changeTbrState(FSM.IDLE);
	    		break;
    	}
    }
    
    public void asyncFSM()
    {
    	final String FUNC_TAG = "asyncFSM";
    	
    	switch(ASYNC.state) {
    		case FSM.IDLE:
    			break;
    		case FSM.WAIT:
    			break;
    		case FSM.START:
    			if(TBR.isBusy() || SYNC.isBusy()) {
    				changeAsyncState(FSM.WAIT);
    				
    				if(waitTimer != null)
    					waitTimer.cancel(true);
    				
    				waitTimer = scheduler.scheduleAtFixedRate(wait, 0, 5, TimeUnit.SECONDS);
    			}
    			else {
    				if(waitTimer != null)
    					waitTimer.cancel(true);    				
    			
		    		changeAsyncState(FSM.SSM_CALC_CALL);
		    		Debug.i(TAG, FUNC_TAG, "Calling SSM Calculate...");
    			}
    			break;
    		case FSM.SSM_CALC_CALL:
	    		Ssm.send(callCalcSSM());
	        	Debug.i(TAG, FUNC_TAG, "Call SSM Calculate - Success");
    			break;
    		case FSM.SSM_CALC_RESPONSE:
    			Debug.i(TAG, FUNC_TAG, "SSM has run, show the UI!");
    			Mcm.send(Message.obtain(null, Meal.SSM_CALC_DONE, 0, 0));
    			break;
    		case FSM.MCM_REQUEST:
    			//Send the request to the SSM
	    		sendInsulinRequestToSafetySystem(Mcm.meal, Mcm.correction, 0.0, true);
    			break;
    		case FSM.SSM_RESPONSE:
    			break;
    		case FSM.PUMP_RESPONSE:
    			changeAsyncState(FSM.IDLE);
    			break;
	    	case FSM.MCM_CANCEL:
	    		if(waitTimer != null)
	    			waitTimer.cancel(true);
	    		
	    		changeAsyncState(FSM.IDLE);
	    		break;
    	}
    }
    
    public void syncFSM()
    {
    	final String FUNC_TAG = "syncFSM";
    	
    	switch(SYNC.state)
    	{
	    	case FSM.IDLE:
	    		break;
	    	case FSM.START:
	    		if(ASYNC.isBusy() || TBR.isBusy()) {
	    			Debug.i(TAG, FUNC_TAG, "Skipping SYNC loop for now...");
    				changeSyncState(FSM.IDLE);
    			}
	    		else {    				
		    		changeSyncState(FSM.SSM_CALC_CALL);
		    		Debug.i(TAG, FUNC_TAG, "Calling SSM Calculate...");
	    		}
	    		break;
	    	case FSM.SSM_CALC_CALL:
	    		Ssm.send(callCalcSSM());
	      		Debug.i(TAG, FUNC_TAG, "Call SSM Calculate - Success");
	    		break;
	    	case FSM.SSM_CALC_RESPONSE:
	    		//Wait for response via SSM Service Connection
	    		switch(CONFIG) {
		    		case FSM.APC_BRM:
		    		case FSM.APC_ONLY:
		    			changeSyncState(FSM.APC_CALL);
		    			Debug.i(TAG, FUNC_TAG, "Calling APC...");
		    			break;
		    		case FSM.BRM_ONLY:
		    			changeSyncState(FSM.BRM_CALL);
		    			Debug.i(TAG, FUNC_TAG, "Calling BRM...");
		    			break;
		    		case FSM.NONE:
		    			changeSyncState(FSM.SSM_CALL);
		    			Debug.i(TAG, FUNC_TAG, "Calling SSM...");
		    			break;
	    		}
	    		break;
	    	case FSM.APC_CALL:
	    		Apc.send(syncCall("APC"));
    			Debug.i(TAG, FUNC_TAG, "Call APC - Success");
	    		break;
	    	case FSM.APC_RESPONSE:
	    		//Wait for response from APC Service Connection
	    		switch(CONFIG) {
		    		case FSM.APC_BRM:
		    			changeSyncState(FSM.BRM_CALL);
		    			Debug.i(TAG, FUNC_TAG, "Calling BRM...");
		    			break;
		    		case FSM.APC_ONLY:
		    			changeSyncState(FSM.SSM_CALL);
		    			Debug.i(TAG, FUNC_TAG, "Calling SSM...");
		    			break;
		    		case FSM.BRM_ONLY:
		    		case FSM.NONE:
		    			Debug.i(TAG, FUNC_TAG, "Nothing to do...");
		    			break;
	    		}
	    		break;
	    	case FSM.BRM_CALL:
	    		Brm.send(syncCall("BRMservice"));
	    		Debug.i(TAG, FUNC_TAG, "Call BRM - Success");
	    		break;
	    	case FSM.BRM_RESPONSE:
	    		//Wait for response from BRM Service Connection
	    		switch(CONFIG)
	    		{
		    		case FSM.APC_BRM:
		    		case FSM.BRM_ONLY:
		    			changeSyncState(FSM.SSM_CALL);
		    			Debug.i(TAG, FUNC_TAG, "Calling SSM...");
		    			break;
		    		case FSM.APC_ONLY:
		    		case FSM.NONE:
		    			Debug.i(TAG, FUNC_TAG, "Nothing to do...");
		    			break;
	    		}
	    		break;
	    	case FSM.SSM_CALL:
		    	callSSM();
	    		break;
	    	case FSM.SSM_RESPONSE:
	    		//Wait for response from SSM Service Connection
	    		break;
	    	case FSM.PUMP_RESPONSE:
	    		//Wait for change in pump service state to verify delivery response
	    		changeSyncState(FSM.TBR_CALL);
	    		break;
	    	case FSM.TBR_CALL:
	    		Debug.i(TAG, FUNC_TAG, "TBR CALL");
	    		
	    		if(Params.getBoolean(getContentResolver(), "tbr_enabled", false))
	    		{
		    		Intent pumpIntent = new Intent();
		    		pumpIntent.setClassName("edu.virginia.dtc.PumpService", "edu.virginia.dtc.PumpService.PumpService");
		    		pumpIntent.putExtra("PumpCommand", Pump.PUMP_SERVICE_CMD_SET_TBR);
		    		startService(pumpIntent); 
	    		}
	    		else
	    			changeSyncState(FSM.IDLE);
	    		break;
	    	case FSM.TBR_RESPONSE:
	    		changeSyncState(FSM.IDLE);
	    		break;
    	}
    }
    
    public Message callCalcSSM()
    {
    	final String FUNC_TAG = "callCalcSSM";
    	
    	Message ssmMessage = Message.obtain(null, Safety.SAFETY_SERVICE_CMD_CALCULATE_STATE, 0, 0);
		Bundle paramBundle = new Bundle();
		paramBundle.putLong("simulatedTime", getCurrentTimeSeconds());
		paramBundle.putLong("hypoFlagTime", hypoFlagTime);
		paramBundle.putLong("calFlagTime", calFlagTime);
		paramBundle.putDouble("brakes_coeff", brakes_coeff);
		paramBundle.putBoolean("currentlyExercising", currentlyExercising);
		paramBundle.putInt("DIAS_STATE", DIAS_STATE);
		ssmMessage.setData(paramBundle);
		
		log_action(TAG, "SSMservice >>>"+
				" Sent Command to Calculate State",
				Debug.LOG_ACTION_INFORMATION);
		
    	return ssmMessage;
    }
    
    public void callSSM()
    {
    	final String FUNC_TAG = "callSSM";
    	
    	double diff_rate = 0.0, correction = 0.0;
    	
		//TODO: setup what the modes and backups are really supposed to do
		
    	switch(DIAS_STATE)
    	{
	    	case State.DIAS_STATE_CLOSED_LOOP:
	    		switch(CONFIG) {
	    	    	case FSM.APC_BRM:
	    	    		if(Apc.doesBolus)
	    	    			correction = Apc.correction;
	    	    		
	    	    		if(Brm.doesRate)
	    	    			diff_rate = Brm.diff_rate;
	    	    		break;
	    	    	case FSM.APC_ONLY:
	    	    		if(Apc.doesRate)
	    	    			diff_rate = Apc.diff_rate;
	    	    		if(Apc.doesBolus)
	    	    			correction = Apc.correction;
	    	    		break;
	    	    	case FSM.BRM_ONLY:
	    	    		if(Brm.doesRate)
	    	    			diff_rate = Brm.diff_rate;
	    	    		if(Brm.doesBolus)
	    	    			correction = Brm.correction;
	    	    		break;
	    	    	case FSM.NONE:
	    	    		Debug.i(TAG, FUNC_TAG, "No controllers, setting all values to zero and delivering basal profile...");
	    	    		diff_rate = correction = 0.0;
	    	    		break;
	        	}
	    		
	    		sendInsulinRequestToSafetySystem(0.0, correction, diff_rate, false);
	    		break;
	    	case State.DIAS_STATE_OPEN_LOOP:
	    		if (temporaryBasalRateActive()) {
	    			if (temp_basal_owner == TempBasal.TEMP_BASAL_OWNER_DIASSERVICE) {
		    			Cursor c = getContentResolver().query(Biometrics.TEMP_BASAL_URI, null, null, null, null);
		    			if(c!=null) {
		    	          	if(c.moveToLast()) {
		    	          		temp_basal_start_time = c.getLong(c.getColumnIndex("start_time"));
		    	          		temp_basal_scheduled_end_time = c.getLong(c.getColumnIndex("scheduled_end_time"));
		    	          		temp_basal_status_code = c.getInt(c.getColumnIndex("status_code"));
		    	          		temp_basal_owner = c.getInt(c.getColumnIndex("owner"));
		    	          		temp_basal_percent_of_profile_basal_rate = c.getInt(c.getColumnIndex("percent_of_profile_basal_rate"));
		    	          	}
		    	      		c.close();
		    			}
		    			double basal = getCurrentBasalProfile();
		    			double temporary_differential_basal_rate = basal*((float)(temp_basal_percent_of_profile_basal_rate-100)/100.0);
		    			temporary_differential_basal_rate = Math.max(temporary_differential_basal_rate, -basal);	// >= -basal
		    			temporary_differential_basal_rate = Math.min(temporary_differential_basal_rate, 5.0);		// <= 5.0 U/hour
		    			sendInsulinRequestToSafetySystem(0.0, 0.0, temporary_differential_basal_rate, false);
	    			}
	    			else {
	    				// If we are in Pump mode and DiAsService is not the owner then we need to cancel this temporary basal rate
	    				cancelTemporaryBasalRate();
	    			}
	    		}
	    		else {
	    			//Administer basal insulin per profile
	    			sendInsulinRequestToSafetySystem(0.0, 0.0, 0.0, false);
	    		}
	    		break;
	    	case State.DIAS_STATE_SAFETY_ONLY:
    			//Administer basal insulin per profile
    			sendInsulinRequestToSafetySystem(0.0, 0.0, 0.0, false);
	    		break;
	    	case State.DIAS_STATE_SENSOR_ONLY:
	    	case State.DIAS_STATE_STOPPED:
	    		changeSyncState(FSM.IDLE);
	    		break;
    	}
    }
    
    public Message syncCall(String process)
	{
    	final String FUNC_TAG = "syncApcCall";

    	Debug.i(TAG, FUNC_TAG, "syncApcCall > APC_SERVICE_CMD_CALCULATE_STATE");
		Message msg1 = Message.obtain(null, APC_SERVICE_CMD_CALCULATE_STATE, 0, 0);
		Bundle paramBundle = new Bundle();
		paramBundle.putBoolean("asynchronous", false);
		paramBundle.putLong("simulatedTime", getCurrentTimeSeconds());
		paramBundle.putLong("corrFlagTime", corrFlagTime);
		paramBundle.putLong("hypoFlagTime", hypoFlagTime);
		paramBundle.putLong("calFlagTime", calFlagTime);
		paramBundle.putLong("mealFlagTime", mealFlagTime);
		paramBundle.putDouble("brakes_coeff", brakes_coeff);
		paramBundle.putBoolean("currentlyExercising", currentlyExercising);
		paramBundle.putInt("DIAS_STATE", DIAS_STATE);
		paramBundle.putInt("tick_modulus", (int)Supervisor_Tick_Free_Running_Counter % Timer_Ticks_Per_Control_Tick);
		msg1.setData(paramBundle);
		
		if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
    		Bundle b = new Bundle();
    		b.putString(	"description", "(DiAsService) >> "+process+", IO_TEST"+", "+FUNC_TAG+", "+
    						"APC_SERVICE_CMD_CALCULATE_STATE"+", "+
    						"simulatedTime="+getCurrentTimeSeconds()+", "+
    						"asynchronous="+paramBundle.getBoolean("asynchronous")+", "+
    						"corrFlagTime="+corrFlagTime+", "+
    						"calFlagTime="+calFlagTime+", "+
    						"hypoFlagTime="+hypoFlagTime+", "+
    						"mealFlagTime="+mealFlagTime+", "+
    						"brakes_coeff="+brakes_coeff+", "+
    						"DIAS_STATE="+DIAS_STATE+", "+
    						"tick_modulus="+(int)Supervisor_Tick_Free_Running_Counter % Timer_Ticks_Per_Control_Tick
    					);
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
		}
		
		return msg1;
	}

    // ******************************************************************************************************************************
 	// SSMservice INTERFACE FUNCTIONS
 	// ******************************************************************************************************************************
    
    public void sendInsulinRequestToSafetySystem(	double bolus_meal,
    												double bolus_correction,
    												double differential_basal_rate,
    												boolean asynchronous) {
    	final String FUNC_TAG = "sendInsulinRequestToSafetySystem";
    	
	 	// Create and send a message to the Safety Service to deliver the bolus subject to approval by the Safety System.
		Debug.i(TAG, FUNC_TAG, "sendInsulinRequestToSafetySystem > bolus_meal="+bolus_meal+", bolus_correction="+bolus_correction+", differential_basal_rate="+differential_basal_rate);
		Message msg1 = Message.obtain(null, Safety.SAFETY_SERVICE_CMD_REQUEST_BOLUS, 0, 0);
		Bundle paramBundle = new Bundle();
		paramBundle.putBoolean("asynchronous", asynchronous);
		paramBundle.putInt("DIAS_STATE", DIAS_STATE);
		paramBundle.putBoolean("currentlyExercising", currentlyExercising);
		paramBundle.putLong("calFlagTime", calFlagTime);
		paramBundle.putLong("hypoFlagTime", hypoFlagTime);
		paramBundle.putLong("simulatedTime", getCurrentTimeSeconds());
		paramBundle.putDouble("nextSimulatedPumpValue", nextSimulatedPumpValue);
		paramBundle.putDouble("bolus_meal", bolus_meal);
		paramBundle.putDouble("bolus_correction", bolus_correction);
		paramBundle.putDouble("credit_request", 0.0);
		paramBundle.putDouble("spend_request", 0.0);
		paramBundle.putDouble("differential_basal_rate", differential_basal_rate);
		msg1.setData(paramBundle);
		
		// Log the parameters for IO testing
		if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
    		Bundle b = new Bundle();
    		b.putString(	"description", "(DiAsService) >> SSMservice, IO_TEST"+", "+FUNC_TAG+", "+
    						"SAFETY_SERVICE_CMD_REQUEST_BOLUS"+", "+
    						"bolus_meal="+bolus_meal+", "+
    						"bolus_correction="+bolus_correction+", "+
    						"asynchronous="+asynchronous+", "+
    						"differential_basal_rate="+differential_basal_rate+", "+
    						"currentRate="+0.0+", "+
    						"credit_request="+0.0+", "+
    						"spend_request="+0.0+", "+
    						"calFlagTime="+calFlagTime+", "+
    						"hypoFlagTime="+hypoFlagTime+", "+
    						"currentlyExercising="+currentlyExercising+", "+
    						"DIAS_STATE="+DIAS_STATE
    					);
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
		}
		
		log_action(TAG, "SSMservice >>>"+
				" Sent Command to Request Bolus"+
				" Meal: "+String.format("%.2f", bolus_meal)+
				" Corr: "+String.format("%.2f", bolus_correction)+
				" DiffRate: "+String.format("%.2f", differential_basal_rate)+
				" Async: "+asynchronous,
				Debug.LOG_ACTION_INFORMATION);
		
		Debug.i(TAG, FUNC_TAG, "sendInsulinRequestToSafetySystem > bolus_meal="+bolus_meal+", bolus_correction="+bolus_correction+", differential_basal_rate="+differential_basal_rate);
		Ssm.send(msg1);
    }
    
    // ******************************************************************************************************************************
 	// STATE ESTIMATE TABLE FUNCTIONS
 	// ******************************************************************************************************************************
    
	private void initializeMealFlagTime() {
		final String FUNC_TAG = "initializeMealFlagTime";
		
		// Fetch Meal Time flag data from Meal data records
		Cursor c=getContentResolver().query(Biometrics.MEAL_URI, null, null, null, null);
		Debug.i(TAG, FUNC_TAG, "initializeMealFlagTime > MEAL_URI > c.getCount="+c.getCount());
		mealFlagTime = 0;
		long tempMealFlagTime=0;
		if (c.moveToFirst()) {
			do{
				tempMealFlagTime = c.getLong(c.getColumnIndex("time"));
				if (tempMealFlagTime > mealFlagTime) {
					mealFlagTime = tempMealFlagTime;
				}
			} while (c.moveToNext());
		}
		
		c.close();
	}    
    	
	private void initializeCalFlagTime() {
		final String FUNC_TAG = "initializeCalFlagTime";
		
		// Fetch Calibration flag data from Calibration data records
		Cursor c=getContentResolver().query(Biometrics.SMBG_URI, null, null, null, null);
		Debug.i(TAG, FUNC_TAG, "initializeCalFlagTime > CALIBRATION_URI > c.getCount="+c.getCount());
    	calFlagTime = 0;
    	long tempCalFlagTime=0;
		if (c.moveToFirst()) {
			do{
				tempCalFlagTime = c.getLong(c.getColumnIndex("time"));
				if (tempCalFlagTime > calFlagTime) {
					calFlagTime = tempCalFlagTime;
				}
			} while (c.moveToNext());
		}
		
		c.close();
	}    
    	
	private void initializeFlagValues() {
		final String FUNC_TAG = "initializeFlagValues";
		
		// Fetch flag data from State Estimate data records
		Cursor c=getContentResolver().query(Biometrics.STATE_ESTIMATE_URI, null, null, null, null);
		Debug.i(TAG, FUNC_TAG, "initializeFlagValues > STATE_ESTIMATE_URI > c.getCount="+c.getCount());
		currentlyExercising = false;
		exerciseFlagTimeStart = 0;
		exerciseFlagTimeStop = 0;
        hypoFlagTime = 0;
		corrFlagTime = 0;
		long tempExerciseFlagTimeStart=0, tempExerciseFlagTimeStop=0, tempHypoFlagTime=0, tempCorrFlagTime=0;
		
		if (c.moveToFirst()) {
			do{
				tempExerciseFlagTimeStart = c.getLong(c.getColumnIndex(EXERCISE_FLAG_TIME_START));
				tempExerciseFlagTimeStop = c.getLong(c.getColumnIndex(EXERCISE_FLAG_TIME_STOP));
				tempHypoFlagTime = c.getLong(c.getColumnIndex(HYPO_FLAG_TIME));
				tempCorrFlagTime = c.getLong(c.getColumnIndex(CORR_FLAG_TIME));
				if (tempExerciseFlagTimeStart > exerciseFlagTimeStart) {
					exerciseFlagTimeStart = tempExerciseFlagTimeStart;
				}
				if (tempExerciseFlagTimeStop > exerciseFlagTimeStop) {
					exerciseFlagTimeStop = tempExerciseFlagTimeStop;
				}
				// Based on exercise start and stop flags update the current exercise state
				if (exerciseFlagTimeStart > exerciseFlagTimeStop) {
					currentlyExercising = true;
				}
				else {
					currentlyExercising = false;
				}
				if (tempHypoFlagTime > hypoFlagTime) {
					hypoFlagTime = tempHypoFlagTime;
				}
				if (tempCorrFlagTime > corrFlagTime) {
					corrFlagTime = tempCorrFlagTime;
				}
			} while (c.moveToNext());
		}
		
		c.close();
	}    
	
	
	private boolean checkModeAvailability(int mode, boolean popup)
	{
		boolean result = false;
		Calendar now = Calendar.getInstance();
		now.setTimeInMillis(getCurrentTimeSeconds()*1000);
		int now_minutes = 60*now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE);
		
		switch (mode) {
			case State.DIAS_STATE_STOPPED:
			case State.DIAS_STATE_SENSOR_ONLY: result = true; break;
			case State.DIAS_STATE_OPEN_LOOP: result = Mode.isPumpModeAvailable(getContentResolver()); break;
			case State.DIAS_STATE_SAFETY_ONLY: result = Mode.isSafetyModeAvailable(getContentResolver()); break;
			case State.DIAS_STATE_CLOSED_LOOP: result = Mode.isClosedLoopAvailable(getContentResolver(), now_minutes); break;
			default: break;
		}
		
		if (!result && popup) {
			Bundle b = new Bundle(); 
			b.putString("description", "This Mode is not available right now. Please check the parameters or controllers status.");
			Event.addEvent(this, Event.EVENT_MODE_NOT_AVAILABLE, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
		}
		
		return result;
	}
	
	
	// ******************************************************************************************************************************
	// MISC FUNCTIONS
	// ******************************************************************************************************************************
	
	private int getModeFromClickCommand(int clickType)
	{
		switch (clickType) {
			case DIAS_SERVICE_COMMAND_START_CLOSED_LOOP_CLICK:		return State.DIAS_STATE_CLOSED_LOOP;
			case DIAS_SERVICE_COMMAND_START_SAFETY_CLICK:			return State.DIAS_STATE_SAFETY_ONLY;
			case DIAS_SERVICE_COMMAND_START_OPEN_LOOP_CLICK:		return State.DIAS_STATE_OPEN_LOOP;
			case DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK:		return State.DIAS_STATE_SENSOR_ONLY;
			case DIAS_SERVICE_COMMAND_STOP_CLICK:
			default:									return State.DIAS_STATE_STOPPED;
		}
	}
	
	
	private int timeNowInMinutes()
	{
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(getCurrentTimeSeconds()*1000)/1000;
		int timeNowMins = (int)((getCurrentTimeSeconds()+UTC_offset_secs)/60)%1440;
		return timeNowMins;
	}
	
	public void initialize_exercise_state ()
	{
		final String FUNC_TAG = "initialize_exercise_state";
		Calendar now = Calendar.getInstance();
		now.setTimeInMillis(getCurrentTimeSeconds()*1000);
		int now_minutes = 60*now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE); 
		boolean inRange= Mode.isInProfileRange(getContentResolver(), now_minutes);
		//default value for VCU, not exercising
		if (Params.getInt(getContentResolver(), "exercise_detection_mode", 0)==1){
			currentlyExercising = false;
		}
		//default value for camp, exercising
		else if (Params.getInt(getContentResolver(), "exercise_detection_mode", 0)==2){
			//check BRM profile
			if (inRange){
				currentlyExercising=false;
			}
			else {
				currentlyExercising=true;
			}
		}
		
		Debug.i(TAG, FUNC_TAG,"  From DiAs Service  currently exercising ==>"+currentlyExercising+"  time in BRM profile range  = "+inRange+now_minutes);
		
	}
	
	private void clearRecoveryFlag()
	{
		Debug.i(TAG, "clearRecoveryFlag", "Clearing the recovery flag...");
		
		//Clear the flag
		ContentValues dv = new ContentValues();
		dv.put("ask_at_startup", 0);
		getContentResolver().update(Biometrics.HARDWARE_CONFIGURATION_URI, dv, null, null);
	}
	
	private void recovery()
	{
		final String FUNC_TAG = "recovery";
		
		if(recoveryStart < 0)
			recoveryStart = getCurrentTimeSeconds();
		
		Cursor c = this.getContentResolver().query(Biometrics.HARDWARE_CONFIGURATION_URI, new String[]{"last_state", "ask_at_startup"}, null, null, null);
		int ask = 0, state = 0;
		
		if(c!=null)
		{
			if(c.moveToLast())
			{
				ask = c.getInt(c.getColumnIndex("ask_at_startup"));
				state = c.getInt(c.getColumnIndex("last_state"));
				Debug.w(TAG, FUNC_TAG, "Found ask at startup value: "+ask);
			}
		}
		c.close();
		
		if(ask==1)
			checkPreviousState(state);
	}
	
	private void checkPreviousState(int state)
	{
		final String FUNC_TAG = "checkPreviousState";
		final int RECOVER_WARN_MIN = 3;
		
		boolean show = false;
		String prevState = "";
		int clickType = -1;
		
		switch(state)
		{
    		case State.DIAS_STATE_STOPPED:
    			Debug.i(TAG, FUNC_TAG, "Previous state was stopped so do nothing!");
    			break;
    		case State.DIAS_STATE_SENSOR_ONLY:
    			Debug.i(TAG, FUNC_TAG, "Previous state was sensor only...");
    			prevState = "Sensor Only Mode";
    			clickType = DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK;
    			if(cgmReady())
    				show = true;
    			break;
    		case State.DIAS_STATE_OPEN_LOOP:
    			Debug.i(TAG, FUNC_TAG, "Previous state was open loop...");
    			prevState = "Pump Mode";
    			clickType = DIAS_SERVICE_COMMAND_START_OPEN_LOOP_CLICK;
    			if(pumpReady())
    				show = true;
    			break;
    		case State.DIAS_STATE_SAFETY_ONLY:
    			Debug.i(TAG, FUNC_TAG, "Previous state was safety...");
    			prevState = "Safety Mode";
    			clickType = DIAS_SERVICE_COMMAND_START_SAFETY_CLICK;
    			if(pumpReady() && cgmReady())
    				show = true;
    			break;
    		case State.DIAS_STATE_CLOSED_LOOP:
    			Debug.i(TAG, FUNC_TAG, "Previous state was closed loop...");
    			prevState = "Closed Loop Mode";
    			clickType = DIAS_SERVICE_COMMAND_START_CLOSED_LOOP_CLICK;
    			if(pumpReady() && cgmReady())
    				show = true;
    			break;
		}

    	if(show)
    	{
    		Intent dismissStartUpActivityBroadcast = new Intent("edu.virginia.dtc.intent.action.DISMISS_STARTUP_ACTIVITY");
   			sendBroadcast(dismissStartUpActivityBroadcast);
    		
    		Intent startupActivty = new Intent();
	 		startupActivty.setClass(this, StartupActivity.class);
	 		startupActivty.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	 		startupActivty.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
	 		startupActivty.putExtra("title", "Recovery");
	 		startupActivty.putExtra("message", "It looks like you were running in "+prevState+".  Would you like to resume now?");
	 		startupActivty.putExtra("click", clickType);
	 		startActivity(startupActivty);
    	}
    	else if((getCurrentTimeSeconds() - recoveryStart) >= (RECOVER_WARN_MIN * 60))
    	{
    		Intent dismissStartUpActivityBroadcast = new Intent("edu.virginia.dtc.intent.action.DISMISS_STARTUP_ACTIVITY");
   			sendBroadcast(dismissStartUpActivityBroadcast);
    		
    		Intent startupActivty = new Intent();
	 		startupActivty.setClass(this, StartupActivity.class);
	 		startupActivty.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	 		startupActivty.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
	 		startupActivty.putExtra("title", "Recovery");
	 		startupActivty.putExtra("message", "The system is unable to return you to the previous running mode!  Please connect devices and return to "+prevState+" manually!");
	 		startupActivty.putExtra("click", -1);
	 		startActivity(startupActivty);
    	}
	}
	
	private void analyzeEvent(Cursor c)
	{
		final String FUNC_TAG = "analyzeEvent";
		JSONObject j;
		String description = "", title = "Alert";
		int settings = Event.SET_LOG;
		int id = -1;
		int code = -1;
		boolean popup_displayed = false;
		
		try {
			j = new JSONObject(c.getString(c.getColumnIndex("json")));
			code = c.getInt(c.getColumnIndex("code"));
			description = j.getString("description");
			id = c.getInt(c.getColumnIndex("_id"));
			settings = c.getInt(c.getColumnIndex("settings"));
			if(c.getInt(c.getColumnIndex("popup_displayed")) == Event.TRUE_INT)
				popup_displayed = true;
		} 
		catch (JSONException e1) {
			e1.printStackTrace();
		}
		
		//If the settings are just to log it in the DB, then don't pop the activity
		if(settings != Event.SET_LOG && !popup_displayed)
		{
			// Set the popup_displayed flag to avoid multiple popups from same Event
			if (id > 0) {
				ContentValues values = new ContentValues();
				values.put("popup_displayed", Event.TRUE_INT);
			    try {
					getContentResolver().update(Biometrics.EVENT_URI, values, "_id="+id, null);
			    }
			    catch (Exception e) {
			    	Debug.i(TAG, FUNC_TAG, e.getMessage());
			    }
			}
			
			// Dismiss any existing popup to avoid big stack of popups
   			Intent dismissActivityBroadcast = new Intent("edu.virginia.dtc.intent.action.DISMISS_EVENT_ACTIVITY");
   			//if(code == Event.EVENT_SYSTEM_HYPO_ALARM) {
   			//	dismissActivityBroadcast.putExtra("prev_code", Event.EVENT_SYSTEM_HYPO_ALARM);
   			//}
   			sendBroadcast(dismissActivityBroadcast);

			Intent eventActivity = new Intent();
	 		eventActivity.setClass(this, EventActivity.class);
	 		eventActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	 		eventActivity.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
	 		eventActivity.putExtra("id", id);
	 		eventActivity.putExtra("code", code);
	 		eventActivity.putExtra("title", title);
	 		eventActivity.putExtra("message", description);
	 		eventActivity.putExtra("settings", settings);
	 		startActivity(eventActivity);
		}
	}
	
    public boolean checkIfEnoughCgmData() {
    	final String FUNC_TAG = "checkIfEnoughCgmData";
    	
    	boolean retVal = false;
		List<Integer> indices;
		long currentTimeMinutes = getCurrentTimeSeconds()/60;
		indices = Tvec_cgm1.find(">", currentTimeMinutes-60, "<=", currentTimeMinutes);
		if (indices != null) {
			if (indices.size() > 0) {
				long last_time = Tvec_cgm1.get_time(indices.get(indices.size()-1));
				long first_time = Tvec_cgm1.get_time(indices.get(0));
				long elapsed_time = last_time-first_time;
				Debug.i(TAG, FUNC_TAG, "last_time="+last_time+", first_time="+first_time+", elapsed_time="+elapsed_time+", count="+indices.size());
				if (indices != null) {
					if (indices.size() >= 4 && (last_time-first_time)>=45) {
						retVal = true;
						int ii;
						double val;
						for (ii=0; ii<indices.size(); ii++) {
							val = Tvec_cgm1.get_value(indices.get(ii));
							if (val >= 2048) {
								val = val - 2048;				// Remove bit 11 (calibration bit) if present
							}
							if(val < cgm_min_value || val > cgm_max_value) {
								retVal = false;
							}
						}
					}
				}
			}
		}
   		return retVal;
    }
	
	public void log_IO(String tag, String message) {
		Debug.i(IO_TEST_TAG, tag, message);
	}
	
	private class CellularRssiListener extends PhoneStateListener
	{
		//Scale slightly adjusted to Google reference for 4 position status bar
		//http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/2.2_r1.1/com/android/server/status/StatusBarPolicy.java#StatusBarPolicy.updateSignalStrength%28%29
		
		@Override
		public void onSignalStrengthsChanged(SignalStrength sig)
		{
			final String FUNC_TAG = "onSignalStrengthsChanged";
			super.onSignalStrengthsChanged(sig);
	
			conn_rssi = sig.getGsmSignalStrength();
		}
	}
    
	
	private void storeInjectedInsulin(double insulin_injected) {
		long time = getCurrentTimeSeconds();
		ContentValues values = new ContentValues(); 
		values.put("req_time", time);
	    values.put("req_total", insulin_injected);
		values.put("req_basal", 0.0);
		values.put("req_meal", 0.0);
		values.put("req_corr", insulin_injected);
	    values.put("deliv_time", time);
	    values.put("deliv_total", insulin_injected);
		values.put("deliv_basal", 0.0);
		values.put("deliv_meal", 0.0);
		values.put("deliv_corr", insulin_injected);
	    values.put("recv_time", time);
		values.put("identifier", time);
		values.put("type", Pump.TYPE_MANUAL);
		values.put("status", Pump.PRE_MANUAL);
		values.put("num_retries", 0);
		try {
			getContentResolver().insert(Biometrics.INSULIN_URI, values);
		}
		catch (Exception e) {
			Log.e("Error",(e.getMessage() == null) ? "null" : e.getMessage());
		}
	}
	
	private void startTempBasal(long durationInSeconds, int percent_of_profile_basal_rate) {
		final String FUNC_TAG = "startTempBasal";
				
	    ContentValues values = new ContentValues();
	    	    
	    long time = getCurrentTimeSeconds();
	    values.put("start_time", time);
	    values.put("scheduled_end_time", time + durationInSeconds);
	    values.put("actual_end_time", 0);
	    values.put("percent_of_profile_basal_rate", percent_of_profile_basal_rate);
	    values.put("status_code", TempBasal.TEMP_BASAL_RUNNING);	 
	    values.put("owner", TempBasal.TEMP_BASAL_OWNER_DIASSERVICE);
		Bundle b = new Bundle();
		try 
	    {
	    	Uri uri = getContentResolver().insert(Biometrics.TEMP_BASAL_URI, values);
 	    	b.putString("description", "DiAsService > startTempBasal, start_time= "+time);
 	    	Event.addEvent(getApplicationContext(), Event.EVENT_TEMP_BASAL_STARTED, Event.makeJsonString(b), Event.SET_LOG);
	    }
	    catch (Exception e) 
	    {
 	    	b.putString("description", "DiAsService > startTempBasal failed, start_time= "+time+", "+e.getMessage());
 	    	Event.addEvent(getApplicationContext(), Event.EVENT_TEMP_BASAL_STARTED, Event.makeJsonString(b), Event.SET_LOG);
	    	Debug.e("Error", FUNC_TAG,(e.getMessage() == null) ? "null" : e.getMessage());
	    }
		Toast.makeText(getApplicationContext(), FUNC_TAG+", start_time="+values.getAsLong("start_time"), Toast.LENGTH_SHORT).show();
	}
	
	private void cancelTemporaryBasalRate() {
		final String FUNC_TAG = "cancelTemporaryBasalRate";

		if (temporaryBasalRateActive()) {
			ContentValues values = new ContentValues();
		    
		    long time = getCurrentTimeSeconds();
		    values.put("actual_end_time", time);
		    values.put("status_code", TempBasal.TEMP_BASAL_MANUAL_CANCEL);	    
			Bundle b = new Bundle();
			try 
		    {
		    	getContentResolver().update(Biometrics.TEMP_BASAL_URI, values, null, null);
	 	    	b.putString("description", "Temporary Basal Rate Canceled, time= "+time);
	 	    	Event.addEvent(getApplicationContext(), Event.EVENT_TEMP_BASAL_CANCELED, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE);
		    }
		    catch (Exception e) 
		    {
		    	Debug.e("Error", FUNC_TAG,(e.getMessage() == null) ? "null" : e.getMessage());
	 	    	b.putString("description", "DiAsMain > cancelTempBasalDelivery failed, time= "+time+", "+e.getMessage());
	 	    	Event.addEvent(getApplicationContext(), Event.EVENT_TEMP_BASAL_CANCELED, Event.makeJsonString(b), Event.SET_LOG);
		    }
			Toast.makeText(getApplicationContext(), FUNC_TAG+", actual_end_time="+values.getAsLong("actual_end_time"), Toast.LENGTH_SHORT).show();
		}
	}
	
	private boolean temporaryBasalRateActive() {
		final String FUNC_TAG = "temporaryBasalRateActive";
		boolean retValue = false;
		Cursor c = getContentResolver().query(Biometrics.TEMP_BASAL_URI, null, null, null, null);
		if(c.moveToLast()) {
   			long time = getCurrentTimeSeconds();
   			long start_time = c.getLong(c.getColumnIndex("start_time"));
   			long scheduled_end_time = c.getLong(c.getColumnIndex("scheduled_end_time"));
   			int status_code = c.getInt(c.getColumnIndex("status_code"));
   			if(time >= start_time && time <= scheduled_end_time && status_code == TempBasal.TEMP_BASAL_RUNNING)   {     				
   				retValue = true;
       			Debug.i(TAG, FUNC_TAG, "Temporary Basal Rate is active.");
       		}
   			else if (time > scheduled_end_time && status_code == TempBasal.TEMP_BASAL_RUNNING) {
   				ContentValues values = new ContentValues();
   				values.put("status_code", TempBasal.TEMP_BASAL_DURATION_EXPIRED);
   				values.put("actual_end_time", time);
   				getContentResolver().update(Biometrics.TEMP_BASAL_URI, values, null, null);
   				Debug.i(TAG, FUNC_TAG, "Temporary Basal Rate expired, updating status.");
   			}
       	}
       	c.close();
		return retValue;
	}
	
	private long getCurrentTimeSeconds() {
		final String FUNC_TAG = "getCurrentTimeSeconds";
		
			long SystemTime = (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970
			//I'm sick of this...
			//Debug.i(TAG, FUNC_TAG, "getCurrentTimeSeconds > returning System Time="+SystemTime);
			return SystemTime;
	}
	
	private void log_action(String service, String action, int priority) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("priority", priority);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
	}
	
	private boolean checkProfiles() {
		if (checkOneProfile(Biometrics.BASAL_PROFILE_URI) && checkOneProfile(Biometrics.CR_PROFILE_URI) && checkOneProfile(Biometrics.CF_PROFILE_URI)) {
			return true;
		}
		else {
			return false;
		}
	}
	
	private boolean checkOneProfile(Uri uri) {
		boolean retValue = false;
		Cursor c1 = getContentResolver().query(uri, null, null, null, null);
		if (c1 == null) {
			return false;
		}
		else {
			if (c1.getCount() == 0) {
				retValue = false;
			}
			else {
				retValue = true;
			}
		}
		c1.close();
		return retValue;
	}
	
	private DiAsSubjectData readDiAsSubjectData() {
		final String FUNC_TAG = "readDiAsSubjectData";
		
		DiAsSubjectData subject_data = new DiAsSubjectData();

		Cursor c = getContentResolver().query(Biometrics.SUBJECT_DATA_URI, null, null, null, null);
		Debug.i(TAG, FUNC_TAG,"Retrieved SUBJECT_DATA_URI with " + c.getCount() + " items");
		if (c.moveToLast()) 
		{
			// A database exists.  Initialize subject_data.
			subject_data.subjectName = new String(c.getString(c.getColumnIndex("subjectid")));
			subject_data.subjectSession = new String(c.getString(c.getColumnIndex("session")));
			subject_data.subjectWeight = (c.getInt(c.getColumnIndex("weight")));
			subject_data.subjectHeight = (c.getInt(c.getColumnIndex("height")));
			subject_data.subjectAge = (c.getInt(c.getColumnIndex("age")));
			subject_data.subjectTDI = (c.getInt(c.getColumnIndex("TDI")));
			
			// subject_data.subjectAIT = (c.getInt(c.getColumnIndex("AIT")));
			subject_data.subjectAIT = 4; // Force AIT == 4 for safety

			int isfemale = c.getInt(c.getColumnIndex("isfemale"));
			if (isfemale == 1)
				subject_data.subjectFemale = true;
			else
				subject_data.subjectFemale = false;

			int SafetyOnlyModeIsEnabled = c.getInt(c.getColumnIndex("SafetyOnlyModeIsEnabled"));
			if (SafetyOnlyModeIsEnabled == 1)
				subject_data.subjectSafetyValid = true;
			else
				subject_data.subjectSafetyValid = false;

			int realtime = c.getInt(c.getColumnIndex("realtime"));
			if (realtime == 1)
				subject_data.realTime = true;
			else
				subject_data.realTime = false;

			// Set flags
			subject_data.subjectNameValid = true;
			subject_data.subjectSessionValid = true;
			subject_data.weightValid = true;
			subject_data.heightValid = true;
			subject_data.ageValid = true;
			subject_data.TDIValid = true;
			subject_data.AITValid = true;
		}
		c.close();
		
		if (Tvector.readTvector(subject_data.subjectCF, Biometrics.CF_PROFILE_URI, this.getContentResolver()))
			subject_data.subjectCFValid = true;
		if (Tvector.readTvector(subject_data.subjectCR, Biometrics.CR_PROFILE_URI, this.getContentResolver()))
			subject_data.subjectCRValid = true;
		if (Tvector.readTvector(subject_data.subjectBasal, Biometrics.BASAL_PROFILE_URI, this.getContentResolver()))
			subject_data.subjectBasalValid = true;
		if (Tvector.readTvector(subject_data.subjectSafety, Biometrics.USS_BRM_PROFILE_URI, this.getContentResolver()))
			subject_data.subjectSafetyValid = true;
		c.close();
		
		return subject_data;
	}

		
	private double getCurrentBasalProfile() {
		
		double basal = 0.0;
		Calendar now = Calendar.getInstance();
		now.setTimeInMillis(getCurrentTimeSeconds()*1000);
		int now_minutes = 60*now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE); 
		int minutes_sup = 1440;
		
		Cursor c = getContentResolver().query(Biometrics.BASAL_PROFILE_URI, new String[]{"time", "value"}, null, null, "time ASC");
		if (c != null) {
			c.moveToLast();
			while(c.isBeforeFirst() != false) {
				int time = c.getInt(c.getColumnIndex("time"));
				if ((now_minutes >= time) && (now_minutes < minutes_sup)) {
					basal = c.getDouble(c.getColumnIndex("value"));
				}
				minutes_sup = time;
				c.moveToPrevious();
			}
			if (basal == 0.0) {
				c.moveToLast();
				basal = c.getDouble(c.getColumnIndex("value"));
			}
		}
		c.close();
		
		return basal;
	}
	
	class Machine
	{
		int prev_state, state;
		int type;
		
		public Machine(int type)
		{
			this.state = FSM.IDLE;
			this.prev_state = FSM.IDLE;
			this.type = type;
		};
		
		public boolean isBusy()
		{
			if(this.state != FSM.IDLE)
				return true;
			else
				return false;
		}
	}
	
	class Controller
	{
		ServiceConnection cxn;
		Messenger rx, tx;
		boolean bound, ping;
		String type;
		
		boolean doesBolus, doesRate;
		
		double correction, meal;
		double diff_rate;
		
		long last_calc_time;
		
		public Controller(String t)
		{
			type = t;
			
			correction = 0.0;
			meal = 0.0;
			diff_rate = 0.0;
			
			last_calc_time = 0;
			
			doesBolus = doesRate = false;
			bound = ping = false;
		}
		
		public boolean isGood()
		{
			return (bound && ping);
		}
		
		public void send(Message m)
		{
			if(this.tx != null) {
				try {
					m.replyTo = this.rx;
					this.tx.send(m);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			else
				Debug.e(TAG, "send", type+": send error!");
		}
	}
}