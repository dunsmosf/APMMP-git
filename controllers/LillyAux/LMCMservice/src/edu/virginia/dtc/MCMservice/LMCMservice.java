package edu.virginia.dtc.MCMservice;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import edu.virginia.dtc.MCMservice.OptCORR_param;

import edu.virginia.dtc.MCMservice.IOB_param;
import edu.virginia.dtc.MCMservice.SSM_param;
import edu.virginia.dtc.MCMservice.SSM_processing;
import edu.virginia.dtc.MCMservice.SSM_state_data;
import edu.virginia.dtc.MCMservice.Subject;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Meal;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.Safety;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.SysMan.TempBasal;
import edu.virginia.dtc.Tvector.Tvector;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;


public class LMCMservice extends Service{

	public static final String TAG = "MCMservice";
    public static final String IO_TEST_TAG = "MCMserviceIO";
    public static final boolean DEBUG = true;
    
    private boolean enableIOtest = false;
	
	public int cycle_duration_seconds = 300;
	public int cycle_duration_mins = cycle_duration_seconds/60;
	double latestCR = 0;
	double latestCF = 0;
	private Tvector USER_BASAL;
	private Tvector USER_CR;
	private int USER_WEIGHT;
	private BroadcastReceiver profileReceiver;
	private OptCORR_param optcorr_param; 
	
	

	private boolean bolus_interceptor_enabled = true;
	
	// Working storage for current cgm and insulin data
	private Tvector Tvec_cgm_mins, Tvec_insulin_rate1_seconds, Tvec_bolus_hist_seconds, Tvec_calibration;
	private Tvector Tvec_basal_bolus_hist_seconds, Tvec_meal_bolus_hist_seconds, Tvec_corr_bolus_hist_seconds;
	private Tvector Tvec_IOB, Tvec_Rate, Tvec_GPRED;
	private Tvector Tvec_credit, Tvec_spent, Tvec_net;
	public double differential_basal_rate;
	public double bolusRequested;
	public double credit_request, spend_request;
	public boolean asynchronous;
	public boolean exercise;
	public double bolus_meal;
	public double bolus_correction;
	
	
    public SSM_param ssm_param;
	
	// Store most recent timestamps in seconds for each biometric Tvector
	Long last_Tvec_cgm_time_secs, last_Tvec_insulin_bolus1_time_secs, last_state_estimate_time_secs;
	long calFlagTime = 0;
	long hypoFlagTime = 0;

	private Subject subject_data;
	
	// Used to calculate and store the SSM_processing object
	public SSM_processing ssm_state_estimate;
	
	// Messengers for sending responses to LillyActivity and DiAsService
	public Messenger mMessengerToActivity = null;	// LillyActivity
    public Messenger mMessengerToService = null;	// DiAsService
    public Messenger mMessengerToDiAsService = null;		
    
    private ServiceConnection mConnection;																/* Connection to the Pump Service. */
    Messenger mMessengerToPumpService = null;															/* Messenger for communicating with the Pump Service. */
    final Messenger mMessengerFromPumpService = new Messenger(new IncomingHandlerFromPumpService());		/* Target we publish for clients to send messages to IncomingHandler. */
    boolean mBound;		
    
    // Incoming messages from LillyActivity and DiAsService handled here
    final Messenger mMessengerForMCM = new Messenger(new IncomingHandler());
    final Messenger mMessengerFromService = new Messenger(new IncomingHandlerFromDiAsService());
    
    
	// SSMservice State Variable and Definitions - state for SSMservice only
	private int SSMSERVICE_STATE;
	public static final int SSMSERVICE_STATE_IDLE = 0;
	public static final int SSMSERVICE_STATE_PROCESSING = 1;
	public static final int SSMSERVICE_STATE_WAIT_CONSTRAINT = 2;
	public static final int SSMSERVICE_STATE_WAIT_CONFIRMATION = 3;
	public static final int SSMSERVICE_STATE_WAIT_PUMPSERVICE = 4;
	
	// DiAs State Variable and Definitions - state for the system as a whole
	private int DIAS_STATE;
	public static final int DIAS_STATE_STOPPED = 0;
	public static final int DIAS_STATE_OPEN_LOOP = 1;
	public static final int DIAS_STATE_CLOSED_LOOP = 2;
	public static final int DIAS_STATE_SAFETY_ONLY = 3;
	public static final int DIAS_STATE_SENSOR_ONLY = 4;
	
	// InsulinPumpService commands
	public static final int PUMP_SERVICE_CMD_NULL = 0;
	public static final int PUMP_SERVICE_CMD_START_SERVICE = 1;
	public static final int PUMP_SERVICE_CMD_REGISTER_CLIENT = 2;
	public static final int PUMP_SERVICE_CMD_DELIVER_BOLUS = 3;
	public static final int PUMP_SERVICE_CMD_SET_BASAL_RATE = 4;
	public static final int PUMP_SERVICE_CMD_REQUEST_PUMP_STATUS = 5;
	public static final int PUMP_SERVICE_CMD_REQUEST_PUMP_HISTORY = 6;
	public static final int PUMP_SERVICE_CMD_STOP_SERVICE = 7;
	public static final int PUMP_SERVICE_CMD_SET_HYPO_TIME = 8;
	
    public static final String INSULIN_BASAL_BOLUS = "basal_bolus";
    public static final String INSULIN_MEAL_BOLUS = "meal_bolus";
    public static final String INSULIN_CORR_BOLUS = "corr_bolus";
	
	// Field definitions for STATE_ESTIMATE_TABLE
    public static final String TIME = "time";
    public static final String ENOUGH_DATA = "enough_data";
    public static final String ASYNCHRONOUS = "asynchronous";
    public static final String CGM = "CGM";
    public static final String IOB = "IOB";
    public static final String IOBLAST = "IOBlast";
    public static final String IOBLAST2 = "IOBlast2";
    public static final String GPRED = "Gpred";
    public static final String GBRAKES = "Gbrakes";
    public static final String GPRED_LIGHT = "Gpred_light";
    public static final String XI00 = "Xi00";
    public static final String XI01 = "Xi01";
    public static final String XI02 = "Xi02";
    public static final String XI03 = "Xi03";
    public static final String XI04 = "Xi04";
    public static final String XI05 = "Xi05";
    public static final String XI06 = "Xi06";
    public static final String XI07 = "Xi07";
    public static final String ISMEALBOLUS = "isMealBolus";
    public static final String GPRED_BOLUS = "Gpred_bolus";
    public static final String GPRED_1H = "Gpred_1h";
    public static final String CHOPRED = "CHOpred";
    public static final String ABRAKES = "Abrakes";
    public static final String UMAX_IOB = "Umax_IOB";
    public static final String CGM_CORR = "CGM_corr";
    public static final String IOB_CONTROLLER_RATE = "IOB_controller_rate";
    public static final String SSM_AMOUNT = "SSM_amount";
    public static final String STATE = "State";
    public static final String STOPLIGHT = "stoplight";
    public static final String STOPLIGHT2 = "stoplight2";
    public static final String SSM_STATE = "SSM_state";
    public static final String SSM_STATE_TIMESTAMP = "SSM_state_timestamp";
    public static final String DIAS_state = "DIAS_state";
    public static final String UTC_offset_seconds = "UTC_offset_seconds";
    public static final String BRAKES_COEFF = "brakes_coeff";
    
	
	public static final int TVEC_SIZE = 288;				// 24 hours of samples at 12 samples per hour (5 minute samples)
	
    
	public void onCreate()
	{
		super.onCreate();
		
		enableIOtest = false;
        log_action(TAG, "onCreate");
        

		Tvec_cgm_mins = new Tvector(TVEC_SIZE);
		Tvec_bolus_hist_seconds = new Tvector(TVEC_SIZE);
		Tvec_basal_bolus_hist_seconds = new Tvector(TVEC_SIZE);
		Tvec_meal_bolus_hist_seconds = new Tvector(TVEC_SIZE);
		Tvec_corr_bolus_hist_seconds = new Tvector(TVEC_SIZE);
		Tvec_insulin_rate1_seconds = new Tvector(TVEC_SIZE);
		
		// Initialize most recent timestamps
		last_Tvec_cgm_time_secs = new Long(0);
		last_Tvec_insulin_bolus1_time_secs = new Long(0);
		
        // Set up a Notification for this Service
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        int icon = R.drawable.ic_launcher;
        CharSequence tickerText = "";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
        Context context = getApplicationContext();
        CharSequence contentTitle = "MCM";
        CharSequence contentText = "Meal Control Module";
        Intent notificationIntent = new Intent(this, LMCMservice.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        final int MCM_ID = 100;
        
        // Make this a Foreground Service
        startForeground(MCM_ID, notification);
        
     // code taken from Phase 4 MealActivity		
        profileReceiver = new BroadcastReceiver(){
        	final String FUNC_TAG = "profileReceiver";
        	
			@Override
			public void onReceive(Context context, Intent intent) {
				Debug.i(TAG, FUNC_TAG, "Profiles changed!  Updating information...");
				
				USER_BASAL =  new Tvector();
				USER_CR = new Tvector();
				Tvec_calibration=new Tvector();
				
//				getSubjectWeight();
//				getSubjectBasal(); // in fact retrieves LA insulin injection times and values
//				getSubjectCR();

				// compose Hue matrices
//				Debug.i(TAG, FUNC_TAG, "pre proc");
//				optcorr_param = new OptCORR_param(USER_BASAL, USER_CR, USER_WEIGHT);
//				Debug.i(TAG, FUNC_TAG, "post proc");
//				Toast.makeText(getApplicationContext(),"matrices built", Toast.LENGTH_SHORT).show();
			}
        };
        // end of taken from Phase 4 MealActivity	
	}
	
	
	
	// response to asyncronous call from Lilly Activity
	
    class IncomingHandler extends Handler 
    {
		final String FUNC_TAG = "IncomingHandlerFromClient";

    	Bundle responseBundle;
    	
    	@Override
        public void handleMessage(Message msg) {
    		switch (msg.what) 
            {
            	//MEAL ACTIVITY COMMANDS
            	//-------------------------------------------------------------------------------------------------
	            case Meal.MEAL_ACTIVITY_REGISTER:
	            	Debug.i(TAG, FUNC_TAG, "my test");
	            	Debug.i(TAG, FUNC_TAG, "MEAL_ACTIVITY_REGISTER");
	            	mMessengerToActivity = msg.replyTo;
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "LillyActivity >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"MEAL_ACTIVITY_REGISTER"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
					//Send message to DiAs Service that Meal Activity has started
//					Message reg = Message.obtain(null, Meal.MCM_STARTED);
//					try {
//						mMessengerToService.send(reg);
//					} catch (RemoteException e) {
//						e.printStackTrace();
//					}
	            	break;
	            case Meal.LILLY_MEAL_ACTIVITY_CALCULATE:
	            	responseBundle = msg.getData();
	            	Debug.i(TAG, FUNC_TAG, "my test 2");
	            	
	            	double CHOg = responseBundle.getDouble("MealScreenCHO");
	            	double SMBG = responseBundle.getDouble("MealScreenBG");
	            	
	            	Debug.i(TAG, FUNC_TAG, "CHO: "+CHOg+" SMBG: "+SMBG);
	            	
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString("description", "LillyActivity >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
                					"MEAL_ACTIVITY_CALCULATE"+", "+"MealScreenCHO="+CHOg+", "+"MealScreenBG="+SMBG);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
	            	
	            	// DO CALCULATION HERE !!!  (THIS IS JUST SAMPLE CODE NOT INTENDED FOR USE!!!)
	            	// ------------------------------------------------------
					edu.virginia.dtc.MCMservice.OptCORR_processing optcorr_bolus_calculation;
					double advised_bolus;
					int MCM_status;
					String MCM_description;
//					subject_parameters();
					
//					fetchNewBiometricData();
//	            	getCalibration();
//	            	debug_message(TAG, "CGM is " + Tvec_cgm_mins.get_last_value());
					
					// some artificial data for testing
					USER_CR = new Tvector();
					USER_CR.put(1, 0.0555);
					double USER_WEIGHT;
					USER_WEIGHT = 88.0024;
					OptCORR_param optcorr_param;
					// --------------------------------
					
					// OptCORR_param returns hue matrices: Ascript, Bscript, B0script, Cscript, Rscript, Qscript
					optcorr_param = new OptCORR_param(USER_CR, USER_WEIGHT);
					
					optcorr_bolus_calculation = new OptCORR_processing(optcorr_param, getApplicationContext());
					
					
//	            	for (int ii = 138; ii <= 143; ii++) {
//		            	Toast.makeText(getApplicationContext(),"CGM fix " + optcorr_bolus_calculation.CGM_fix[ii], Toast.LENGTH_SHORT).show();
//		            	}
		        		advised_bolus = optcorr_bolus_calculation.Ustar;
//		        		advised_bolus = 2;
//		        		        	
	            	MCM_status = 0;
					MCM_description = "Test";
//					
					Message mealCalc = Message.obtain(null, Meal.LMCM_CALCULATED_BOLUS, 0, 0);
					responseBundle = new Bundle();
					responseBundle.putDouble("advised_bolus", advised_bolus);
					responseBundle.putInt("LMCM_status", MCM_status);
					responseBundle.putString("LMCM_description", MCM_description);
//
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "(MCMservice) >> DiAsService, IO_TEST"+", "+FUNC_TAG+", "+
                						"LMCM_CALCULATED_BOLUS"+", "+
                						"advised_bolus="+advised_bolus+", "+
                						"LMCM_status="+MCM_status+", "+
                						"LMCM_description="+MCM_description
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
//					
     				mealCalc.setData(responseBundle);
//     				
					try {
						mMessengerToActivity.send(mealCalc);
					} 
					catch (RemoteException e) {
						e.printStackTrace();
					}
//					// ------------------------------------------------------
//	            	break;
	            case Meal.MEAL_ACTIVITY_INJECT:
	            	//Open loop boluses will have meal and corr insulin, closed loop will have credit and spend
	            	//THEY SHOULD NEVER HAVE BOTH IN THIS CONFIGURATION!
	            	Debug.i(TAG, FUNC_TAG, "my test 3");
	            	
	            	responseBundle = msg.getData();
	            	double mealSize = responseBundle.getDouble("mealSize", 0.0);
	            	double smbg = responseBundle.getDouble("smbg", 0.0);
	            	double meal = responseBundle.getDouble("meal", 0.0);
	            	double corr = responseBundle.getDouble("corr", 0.0);
	            	double credit = responseBundle.getDouble("credit", 0.0);
	            	double spend = responseBundle.getDouble("spend", 0.0);
	            	
	            	double bolusAmount = meal + corr + spend;
	            	
	            	Debug.i(TAG, FUNC_TAG, "Meal: "+meal+" Corr: "+corr+" Spend: "+spend+" Credit: "+credit+" Bolus Amount: "+bolusAmount);
	            	

                	Message sendBolus = Message.obtain(null, Meal.MCM_SEND_BOLUS);
	            	
	            	responseBundle = new Bundle();
	            	
	            	if(meal+corr > 0)
	            		responseBundle.putBoolean("doesBolus", true);
	            	else if(spend+credit > 0)
	            		responseBundle.putBoolean("doesCredit", true);
	            	
	            	responseBundle.putDouble("mealSize", mealSize);
	            	responseBundle.putDouble("smbg", smbg);
	            	responseBundle.putDouble("meal", meal);
	            	responseBundle.putDouble("corr", corr);
	            	responseBundle.putDouble("credit", credit);
	            	responseBundle.putDouble("spend", spend);
	            	
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "LillyActivity >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"MCM_SEND_BOLUS"+", "+
                						"doesBolus="+responseBundle.getBoolean("doesBolus", false)+", "+
                						"doesCredit="+responseBundle.getBoolean("doesCredit", false)+", "+
                						"mealSize="+responseBundle.getDouble("mealSize")+", "+
                						"smbg="+responseBundle.getDouble("smbg")+", "+
                						"meal="+responseBundle.getDouble("meal")+", "+
                						"corr="+responseBundle.getDouble("corr")+", "+
                						"credit="+responseBundle.getDouble("credit")+", "+
                						"spend="+responseBundle.getDouble("spend")
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}

					sendBolus.setData(responseBundle);
	            	
//					try {
//						mMessengerToService.send(sendBolus);
//					} catch (RemoteException e) {
//						e.printStackTrace();
//					}
	            	break;
	            	
            	//MCM SERVICE COMMANDS
	            //-------------------------------------------------------------------------------------------------
				case Meal.MCM_SERVICE_CMD_REGISTER_CLIENT:
					Debug.i(TAG, FUNC_TAG, "MCM_SERVICE_CMD_REGISTER_CLIENT");
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DiAsService >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"MCM_SERVICE_CMD_REGISTER_CLIENT"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
					mMessengerToService = msg.replyTo;
					break;
//				case Meal.MCM_UI:
//					//Forward message to the UI
//					Bundle ui = msg.getData();
//					if(ui.getBoolean("end", false))
//					{
//						Debug.i(TAG, FUNC_TAG, "User canceled the Meal!");
//						try {
//							mMessengerToService.send(msg);
//						} catch (RemoteException e) {
//							e.printStackTrace();
//						}
//					}
//					else
//					{
//						Debug.i(TAG, FUNC_TAG, "Passing message to UI!");
//						try {
//							mMessengerToActivity.send(msg);
//						} 
//						catch (RemoteException e) {
//							e.printStackTrace();
//						}
//					}
//					break;
				default:
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DiAsService >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"UNKNOWN_COMMAND"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					super.handleMessage(msg);
            }
        }
    }
	
	@Override 
	public IBinder onBind(Intent intent) {
		String Action = intent.getAction();
		if (Action.equals("DiAs.MCMservice"))
			return mMessengerForMCM.getBinder();
		else
			return mMessengerFromService.getBinder();
	}
		
	private void sendBolusToPumpService(double bolus_basal, double bolus_meal, double bolus_correction) {
    	final String FUNC_TAG = "sendBolusToPumpService";
    	Bundle paramBundle;
    	Message response, msg;
    	Bundle responseBundle;
		try {
			// Send a command to the Pump Service to deliver the bolus.
			msg = Message.obtain(null, PUMP_SERVICE_CMD_DELIVER_BOLUS, 0, 0);
			paramBundle = new Bundle();
			paramBundle.putBoolean("asynchronous", asynchronous);
			paramBundle.putInt("DIAS_STATE", DIAS_STATE);
			paramBundle.putDouble("SSM_amount", ssm_state_estimate.state_data.SSM_amount);
			paramBundle.putDouble(INSULIN_BASAL_BOLUS, bolus_basal);
			paramBundle.putDouble(INSULIN_MEAL_BOLUS, bolus_meal); 
    		paramBundle.putDouble(INSULIN_CORR_BOLUS, bolus_correction); 
			paramBundle.putDouble("pre_authorized", ssm_state_estimate.state_data.pre_authorized);
			double pre = ssm_state_estimate.state_data.pre_authorized;
			ssm_state_estimate.state_data.pre_authorized = 0.0;
			paramBundle.putDouble("bolus_max", ssm_param.filter.bolus_max);
     		paramBundle.putLong("simulatedTime", getCurrentTimeSeconds());
			putTvector(paramBundle, subject_data.subjectBasal, "Basaltimes", "Basalvalues");
     		double correction = ssm_state_estimate.state_data.SSM_amount-ssm_state_estimate.state_data.basal_added;
			msg.setData(paramBundle);
			// Log the parameters for IO testing
			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
				Tvector tvec_temp = getTvector(paramBundle, "Basaltimes", "Basalvalues");
	    		Bundle b = new Bundle();
	    		b.putString(	"description", "(SSMservice) >> PumpService, IO_TEST"+", "+FUNC_TAG+", "+
	    						"PUMP_SERVICE_CMD_DELIVER_BOLUS"+", "+
	    						", lastBolusSimulatedTime="+paramBundle.getLong("lastBolusSimulatedTime")+", "+
	    						", asynchronous="+paramBundle.getBoolean("asynchronous")+", "+
	    						", DIAS_STATE="+paramBundle.getInt("DIAS_STATE")+", "+
	    						", pre_authorized="+paramBundle.getDouble("pre_authorized")+", "+
	    						", bolus_max="+paramBundle.getDouble("bolus_max")+", "+
	    						", INSULIN_BASAL_BOLUS="+paramBundle.getDouble(INSULIN_BASAL_BOLUS)+", "+
	    						", INSULIN_MEAL_BOLUS="+paramBundle.getDouble(INSULIN_MEAL_BOLUS)+", "+
	    						", INSULIN_CORR_BOLUS="+paramBundle.getDouble(INSULIN_CORR_BOLUS)+", "+
	    						", Tvec_basal=("+tvec_temp.get_last_time()+", "+tvec_temp.get_last_value()+")"
	    						);
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
			}
			mMessengerToPumpService.send(msg);
			ssm_state_estimate.state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_AWAITING_PUMP_RESPONSE;
			debug_message("BOLUS_TRACE", "SafetyService > send bolus to pump > bolus="+ssm_state_estimate.state_data.SSM_amount);
		}
		catch (RemoteException e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
			e.printStackTrace();
			ssm_state_estimate.state_data.Processing_State = Safety.SAFETY_SERVICE_STATE_NORMAL;
		}
	}
		
	class IncomingHandlerFromPumpService extends Handler {
    	Message response;
    	Bundle responseBundle;
    	@Override
    	public void handleMessage(Message msg) {
    	   final String FUNC_TAG = "handleMessage";
           switch (msg.what) {
           		case Pump.PUMP_STATE_IDLE:
           			debug_message(TAG, "PUMP_STATE_COMMAND_IDLE");
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_COMMAND_IDLE"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
    				Bundle responseBundle = msg.getData();
    				SSMSERVICE_STATE = SSMSERVICE_STATE_IDLE;
           			break;
           		case Pump.PUMP_STATE_COMPLETE:
           			debug_message(TAG, "PUMP_STATE_COMMAND_COMPLETE");
           			// Log the parameters for IO testing
           			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        	    		Bundle b = new Bundle();
        	    		b.putString(	"description", "PumpService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        	    						"PUMP_STATE_COMMAND_COMPLETE"
        	    						);
        	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
           			}
           			returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_NORMAL);
               		break;
           		default:
           			super.handleMessage(msg);
        			SSMSERVICE_STATE = SSMSERVICE_STATE_IDLE;
           }
       }
   }

	
	// response to syncronous call from DiAs Service
	
	class IncomingHandlerFromDiAsService extends Handler {
	    	Bundle paramBundle;
	    	Message response;
	    	Bundle responseBundle;
	    	@Override
	        public void handleMessage(Message msg) {
	        	final String FUNC_TAG = "IncomingDiAsServiceHandler";
	        	if (SSMSERVICE_STATE == SSMSERVICE_STATE_IDLE) {
	                switch (msg.what) {
	    				case Safety.SAFETY_SERVICE_CMD_NULL:		// null command
	    					Debug.i(TAG, FUNC_TAG,"SAFETY_SERVICE_CMD_NULL");
	    					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
	                    		Bundle b = new Bundle();
	                    		b.putString(	"description", "DiAsService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
	                    						"SAFETY_SERVICE_CMD_NULL");
	                    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
	    					}
	    					break;
	    				case Safety.SAFETY_SERVICE_CMD_CALCULATE_STATE:
	    					SSMSERVICE_STATE = SSMSERVICE_STATE_PROCESSING;
	    					bolus_interceptor_enabled = Params.getBoolean(getContentResolver(), "bolus_interceptor_enabled", false);
	    					Debug.i(TAG, FUNC_TAG,"SAFETY_SERVICE_CMD_CALCULATE_STATE");
	    					subject_data.read(getCurrentTimeSeconds(), getApplicationContext());
	    					if (subject_data.valid == false)		// Protect against state estimates with uninitialized data.
	    						Debug.e(TAG, "SAFETY_SERVICE_CMD_REQUEST_BOLUS", "subject.valid == false");
	    					// Evaluate a bolus for safety with parameters received from the Application
	    					paramBundle = msg.getData();
	    					bolus_meal =  0.0;
	    					bolus_correction =  0.0;
	    					bolusRequested = 0.0;
	    					differential_basal_rate = 0.0;
	    					credit_request = 0.0;
	    					spend_request = 0.0;
	    					asynchronous = false;
	    					calFlagTime = (long)paramBundle.getLong("calFlagTime", 0);
	    					hypoFlagTime = (long)paramBundle.getLong("hypoFlagTime", 0);
	    					exercise = paramBundle.getBoolean("currentlyExercising", false);
	    					DIAS_STATE = paramBundle.getInt("DIAS_STATE", DIAS_STATE_OPEN_LOOP);
	    					    					
	    					// Log the parameters for IO testing
	    					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
	                    		Bundle b = new Bundle();
	                    		b.putString(	"description", "DiAsService >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
	                    						"SAFETY_SERVICE_CMD_CALCULATE_STATE"+", "+
	                    						"simulatedTime="+paramBundle.getLong("simulatedTime")+", "+
	                    						"DIAS_STATE="+DIAS_STATE
	                    						);
	                    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
	    					}
	    					SSMstateEstimation();
	    					break;
	    				default:
	    					super.handleMessage(msg);
	                }        		
	        	}
	        	else {
					Debug.e(TAG, FUNC_TAG,"SSMSERVICE_STATE == "+SSMSERVICE_STATE+", Message: "+msg.what+" ignored.");
					returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_BUSY);
	        	}
	        }
	    }

    private void SSMstateEstimation() {
    	final String FUNC_TAG = "SSMstateEstimation";
    	
    	// Update Tvectors from the database
		fetchNewBiometricData();
		fetchStateEstimateData(getCurrentTimeSeconds());
	   	Tvec_IOB.dump(TAG, "Tvec_IOB", 4);
	   	Tvec_GPRED.dump(TAG, "Tvec_GPRED", 4);
	   	Tvec_Rate.dump(TAG, "Tvec_Rate", 4);
		if (Params.getBoolean(getContentResolver(), "enableIO", false)){
        	Bundle b = new Bundle();
        	b.putString(	"description", "Database >> (SSMservice), IO_TEST"+", "+FUNC_TAG+", "+
        					"DATABASE_IO_TEST"+", "+
        					"Tvec_cgm_mins=("+Tvec_cgm_mins.get_last_time()+", "+Tvec_cgm_mins.get_last_value()+")"+", "+
        					"Tvec_bolus_hist_seconds=("+Tvec_bolus_hist_seconds.get_last_time()+", "+Tvec_bolus_hist_seconds.get_last_value()+")"+", "+
        					"Tvec_basal_bolus_hist_seconds=("+Tvec_basal_bolus_hist_seconds.get_last_time()+", "+Tvec_basal_bolus_hist_seconds.get_last_value()+")"+", "+
        					"Tvec_meal_bolus_hist_seconds=("+Tvec_meal_bolus_hist_seconds.get_last_time()+", "+Tvec_meal_bolus_hist_seconds.get_last_value()+")"+", "+
        					"Tvec_corr_bolus_hist_seconds=("+Tvec_corr_bolus_hist_seconds.get_last_time()+", "+Tvec_corr_bolus_hist_seconds.get_last_value()+")"
        					);
        	Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
		}
		
		// Allocate SSM_processing
		if (ssm_state_estimate == null) {
			ssm_state_estimate = new SSM_processing(subject_data, getCurrentTimeSeconds(), getApplicationContext());
		}

		// Compute the current IOB
		calculateCurrentIOB();
		
		// Start the SSM_processing calculation
		ssm_state_estimate.start_SSM_processing(subject_data,
												Tvec_cgm_mins,
												Tvec_insulin_rate1_seconds,
												Tvec_bolus_hist_seconds, 
												Tvec_basal_bolus_hist_seconds,
												Tvec_meal_bolus_hist_seconds,
												Tvec_corr_bolus_hist_seconds, 
												Tvec_GPRED,
												Tvec_IOB,
												Tvec_Rate,
												bolusRequested,
												differential_basal_rate,
												Tvec_credit,
												Tvec_spent,
												Tvec_net, ssm_param, 
												getCurrentTimeSeconds(), 
												cycle_duration_mins, 
												ssm_state_estimate.state_data,
												calFlagTime,
												hypoFlagTime,
												credit_request,
												spend_request,
												exercise,
												asynchronous,
												bolus_interceptor_enabled,
												DIAS_STATE);
//		ssm_state_estimate.state_data.asynchronous = asynchronous;

		writeStateEstimateData();
		returnStatusToDiAsService(Safety.SAFETY_SERVICE_STATE_CALCULATE_RESPONSE);				
		
    }    
	
	private double calculateCurrentIOB() {
		final String FUNC_TAG = "calculateCurrentIOB";
    	double retValue = 0.0;
    	try {
    		if (ssm_state_estimate != null) {

    			// Calculate and store the insulin history
     			insulin_history_builder(cycle_duration_mins, 
     									Tvec_insulin_rate1_seconds, 
     									Tvec_bolus_hist_seconds, 
     									Tvec_basal_bolus_hist_seconds, 
     									Tvec_meal_bolus_hist_seconds, 	
     									Tvec_corr_bolus_hist_seconds, 
     									subject_data.subjectBasal, 
     									Tvec_spent, 
     									ssm_param, 
     									getCurrentTimeSeconds(), 
     									ssm_state_estimate.state_data);
     			
     			// Compute insulin on board (IOB) based on time "bins" ending at the current time
 				ssm_state_estimate.state_data.IOB_meal = calculate_IOB(ssm_state_estimate.state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds, 
											ssm_param.iob_param,  
											getCurrentTimeSeconds());
 				ssm_state_estimate.state_data.IOB_no_meal = calculate_IOB(ssm_state_estimate.state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds, 
 											ssm_param.iob_param,  
 											getCurrentTimeSeconds());

				
 				Debug.i(TAG, FUNC_TAG, "IOB_meal="+ssm_state_estimate.state_data.IOB_meal+"IOB_no_meal="+ssm_state_estimate.state_data.IOB_no_meal);
    		}
    	}
		catch (Exception e) {
			Bundle b = new Bundle();
			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
			Event.addEvent(this, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
    	return retValue;
    }
	    
	public void writeStateEstimateData() {
    	final String FUNC_TAG = "writeStateEstimateData";
		debug_message(TAG, "writeStateEstimateData > state_data.CGM = "+ssm_state_estimate.state_data.CGM);
	    ContentValues values = new ContentValues();
	    values.put(TIME, getCurrentTimeSeconds());
	    values.put(ENOUGH_DATA, ssm_state_estimate.state_data.enough_data);
	    if (ssm_state_estimate.state_data.asynchronous)
	    	values.put(ASYNCHRONOUS, 1);
	    else
	    	values.put(ASYNCHRONOUS, 0);
	    if (DIAS_STATE == DIAS_STATE_CLOSED_LOOP || DIAS_STATE == DIAS_STATE_SAFETY_ONLY || (DIAS_STATE == DIAS_STATE_OPEN_LOOP && ssm_state_estimate.state_data.enough_data)) {
		    values.put(CGM, ssm_state_estimate.state_data.CGM);
		    values.put(GPRED, ssm_state_estimate.state_data.Gpred);
		    values.put(GBRAKES, ssm_state_estimate.state_data.Gbrakes);
		    values.put(GPRED_LIGHT, ssm_state_estimate.state_data.Gpred_light);
		    values.put(GPRED_BOLUS, ssm_state_estimate.state_data.Gpred_bolus);
		    values.put(GPRED_1H, ssm_state_estimate.state_data.Gpred_1h);
		    values.put(CGM_CORR, 0);
		    values.put(XI00, ssm_state_estimate.state_data.Xi0[0]);
		    values.put(XI01, ssm_state_estimate.state_data.Xi0[1]);
		    values.put(XI02, ssm_state_estimate.state_data.Xi0[2]);
		    values.put(XI03, ssm_state_estimate.state_data.Xi0[3]);
		    values.put(XI04, ssm_state_estimate.state_data.Xi0[4]);
		    values.put(XI05, ssm_state_estimate.state_data.Xi0[5]);
		    values.put(XI06, ssm_state_estimate.state_data.Xi0[6]);
		    values.put(XI07, ssm_state_estimate.state_data.Xi0[7]);
	    }
	    values.put(STOPLIGHT, ssm_state_estimate.state_data.stoplight);
	    values.put(STOPLIGHT2, ssm_state_estimate.state_data.stoplight2);
	    values.put(IOB, ssm_state_estimate.state_data.IOB_meal);
	    values.put(IOBLAST, ssm_state_estimate.state_data.IOB_meal);
	    values.put(IOBLAST2, ssm_state_estimate.state_data.IOB_no_meal);
	    values.put(ISMEALBOLUS, ssm_state_estimate.state_data.isMealBolus);
	    values.put(CHOPRED, ssm_state_estimate.state_data.CHOpred);
	    values.put(ABRAKES, ssm_state_estimate.state_data.Abrakes);
	    values.put(UMAX_IOB, ssm_state_estimate.state_data.InsulinConstraintInUnits);
	    values.put(IOB_CONTROLLER_RATE, ssm_state_estimate.state_data.Rate);
	    values.put(SSM_AMOUNT, ssm_state_estimate.state_data.SSM_amount);
	    values.put(STATE, ssm_state_estimate.state_data.Processing_State);
	    values.put(SSM_STATE, ssm_state_estimate.state_data.Tvec_state.get_last_value());
	    values.put(SSM_STATE_TIMESTAMP, ssm_state_estimate.state_data.Tvec_state.get_last_time());
	    values.put(BRAKES_COEFF, ssm_state_estimate.state_data.brakes_coeff);
	    values.put(DIAS_state, DIAS_STATE);											// Save the Current DiAs state
		long timeSeconds = getCurrentTimeSeconds();
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(timeSeconds*1000)/1000;
		values.put(UTC_offset_seconds, UTC_offset_secs);					// Save the current offset from UTC in seconds
	    try {
    			getContentResolver().insert(Biometrics.STATE_ESTIMATE_URI, values);
	    }
	    catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
    		Log.e(TAG, e.toString());
	    }
	}
	
	private void insulin_history_builder(int cycle_duration_mins,
											Tvector Tvec_rate_hist_seconds,
											Tvector Tvec_bolus_hist_seconds, 
											Tvector Tvec_basal_bolus_hist_seconds,	
											Tvector Tvec_meal_bolus_hist_seconds,
											Tvector Tvec_corr_bolus_hist_seconds,
											Tvector Tvec_basal_pattern, 				// UTC profile times in minutes
											Tvector Tvec_spent_hist_seconds, 
											SSM_param ssm_param,
											long time,
											SSM_state_data state_data) {
		final String FUNC_TAG = "insulin_history_builder";
		try {
			long ToD_minutes;
			double dvalue, dvalue1;
			int ii, kk;
			
			// Get the offset in minutes into the current day in the current time zone (based on cell phone time zone setting)
			TimeZone tz = TimeZone.getDefault();
			int UTC_offset_secs = tz.getOffset(time*1000)/1000;
			Debug.i(TAG, FUNC_TAG, "UTC_offset_secs="+UTC_offset_secs);
			
			// Create a vector of long discrete times in UTC seconds
			int hist_length = ssm_param.iob_param.hist_length;
			int ndiscrete = hist_length/cycle_duration_mins;
			state_data.discrete_time_seconds = new long[ndiscrete];
			for (ii=ndiscrete-1; ii>=0; ii--) {
				state_data.discrete_time_seconds[ndiscrete-1-ii] = (long)((time)-ii*cycle_duration_mins*60);
			}
			// The value of discrete_time_seconds[ndiscrete-1] is extended 3 minutes into the future from "time" to make sure that
			// we capture any very recent bolus which might have a delivery time > time due to pump clock skew
			state_data.discrete_time_seconds[ndiscrete-1] = state_data.discrete_time_seconds[ndiscrete-1] + 3*60;			
		
			// Dump insulin input Tvectors
			Debug.i(TAG, FUNC_TAG, "                                  ");
			Debug.i(TAG, FUNC_TAG, "**********************************");
			Tvec_basal_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_basal_bolus_hist_seconds", 12);	
			Debug.i(TAG, FUNC_TAG, "                                  ");
			Tvec_corr_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_corr_bolus_hist_seconds", 12);	
			Debug.i(TAG, FUNC_TAG, "                                  ");
			Tvec_meal_bolus_hist_seconds.dump(TAG, FUNC_TAG+" > Tvec_meal_bolus_hist_seconds", 12);	
			Debug.i(TAG, FUNC_TAG, "**********************************");
			Debug.i(TAG, FUNC_TAG, "                                  ");
		
			// *******************************************************
			// Construct Tvec_basal_hist_mins - the programmed basal history over discrete_time[] based on the Tvec_basal_pattern daily profile.
			// *******************************************************
			Tvector Tvec_basal_hist_seconds = new Tvector(ndiscrete);
			List<Integer> indices;	
			double basal_bolus;			
			for (ii=0; ii<ndiscrete; ii++) {											
				ToD_minutes = (state_data.discrete_time_seconds[ii]/60+UTC_offset_secs/60)%1440;			// ToD_minutes is number of minutes into the current day in local time
				if ((indices = Tvec_basal_pattern.find(">", -1, "<=", ToD_minutes)) != null) {
					basal_bolus = Tvec_basal_pattern.get_value(indices.get(indices.size()-1));
				}
				else if ((indices = Tvec_basal_pattern.find(">", -1, "<", -1)) != null) {
					basal_bolus = Tvec_basal_pattern.get_value(indices.get(indices.size()-1));					
				}
				else {
					basal_bolus = 0;
				}
				Tvec_basal_hist_seconds.put(state_data.discrete_time_seconds[ii], (double)basal_bolus);
			}
		
			// *******************************************************
			// Construct Tvec_ins_hist_IOB_with_meal_insulin_seconds for use in IOB calculation
			// Construct Tvec_ins_hist_IOB_no_meal_insulin_seconds for use in bolus interceptor
			// *******************************************************
			state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds = new Tvector(ndiscrete);
			state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds = new Tvector(ndiscrete);
			long ndiscrete_interval_start, ndiscrete_interval_end;
			for (ii=0; ii<ndiscrete; ii++) {
				// Set time intervals for assigning boluses to time intervals
				ndiscrete_interval_end = state_data.discrete_time_seconds[ii];
				if (ii == 0) {
					ndiscrete_interval_start = state_data.discrete_time_seconds[ii]-cycle_duration_mins*60;
				}
				else {
					ndiscrete_interval_start = state_data.discrete_time_seconds[ii-1];
				}
				
				// Add in the basal bolus contribution
				if ((indices = Tvec_basal_bolus_hist_seconds.find(">=", ndiscrete_interval_start, "<", ndiscrete_interval_end)) != null) { 
					dvalue = 0;
					Double Dval;
					for (kk=0; kk<indices.size(); kk++) {
						dvalue = dvalue + Tvec_basal_bolus_hist_seconds.get_value(indices.get(kk));
					}
					// Tvec_ins_hist_IOB_with_meal_insulin_seconds
					if ((Dval=state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)dvalue1);
					// Tvec_ins_hist_IOB_no_meal_insulin_seconds
					if ((Dval=state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)dvalue1);
				}
				
				// Add in the correction bolus contribution
				if ((indices = Tvec_corr_bolus_hist_seconds.find(">=", ndiscrete_interval_start, "<", ndiscrete_interval_end)) != null) {
					dvalue = 0;
					Double Dval;
					for (kk=0; kk<indices.size(); kk++) {
						dvalue = dvalue + Tvec_corr_bolus_hist_seconds.get_value(indices.get(kk));
					}
					// Tvec_ins_hist_IOB_with_meal_insulin_seconds
					if ((Dval=state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)dvalue1);
					// Tvec_ins_hist_IOB_no_meal_insulin_seconds
					if ((Dval=state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)dvalue1);
				}	
				
				// Add in the meal bolus contribution
				if ((indices = Tvec_meal_bolus_hist_seconds.find(">=", ndiscrete_interval_start, "<", ndiscrete_interval_end)) != null) {
					dvalue = 0;
					Double Dval;
					for (kk=0; kk<indices.size(); kk++) {
						dvalue = dvalue + Tvec_meal_bolus_hist_seconds.get_value(indices.get(kk));
					}
					// Tvec_ins_hist_IOB_with_meal_insulin_seconds
					if ((Dval=state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
						dvalue1 = Dval.doubleValue()+dvalue;
					}
					else {
						dvalue1 = dvalue;
					}  				
					state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)dvalue1);
				}
			
				// If there are no boluses within this discrete_time interval then fill with basal.
				// Although this will overestimate basal delivery in the case of a missed basal bolus it also avoids the problem of treating time periods
				// in which the system was turned off as missed basal periods (for example at startup), resulting in artificially low (even negative) IOB.
				Double Dval;
				if ((Dval = state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) == null) {
					state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.put(state_data.discrete_time_seconds[ii], Tvec_basal_hist_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])/(60/cycle_duration_mins));
				}
				if ((Dval = state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) == null) {
					state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.put(state_data.discrete_time_seconds[ii], Tvec_basal_hist_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])/(60/cycle_duration_mins));
				}
			}
		
			// *******************************************************
			// Correct Tvec_ins_hist_IOB_with_meal_insulin_seconds by removing basal PROFILE insulin from history
			// *******************************************************
			
			for (ii=0; ii<ndiscrete; ii++) {
				Double Dval;
				double basal_value;
				if ((Dval = state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
					basal_value = 0.0;
					basal_value = Tvec_basal_hist_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])/(60/cycle_duration_mins);
					state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)(Dval.doubleValue()-basal_value));
				}
			}
			
			// *******************************************************
			// Correct Tvec_ins_hist_IOB_no_meal_insulin_seconds by removing basal PROFILE insulin from history
			// *******************************************************
			for (ii=0; ii<ndiscrete; ii++) {
				Double Dval;
				double basal_value;
				if ((Dval = state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])) != null) {
					basal_value = 0.0;
					basal_value = Tvec_basal_hist_seconds.get_value_using_time_as_index(state_data.discrete_time_seconds[ii])/(60/cycle_duration_mins);
					state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.put_with_replace(state_data.discrete_time_seconds[ii], (double)(Dval.doubleValue()-basal_value));
				}
			}
		}
		catch (Exception e) {
			Bundle b = new Bundle();
			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
			Event.addEvent(this, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}

		// Dump _history_builder() output Tvectors
		Debug.i(TAG, FUNC_TAG, "                                  ");
		Debug.i(TAG, FUNC_TAG, "**********************************");
		state_data.Tvec_ins_hist_IOB_with_meal_insulin_seconds.dump(TAG, FUNC_TAG+" > Tvec_ins_hist_IOB_with_meal_insulin_seconds", 12);	
		Debug.i(TAG, FUNC_TAG, "                                  ");
		state_data.Tvec_ins_hist_IOB_no_meal_insulin_seconds.dump(TAG, FUNC_TAG+" > Tvec_ins_hist_IOB_no_meal_insulin_seconds", 12);	
		Debug.i(TAG, FUNC_TAG, "**********************************");
		Debug.i(TAG, FUNC_TAG, "                                  ");
	}	  // end - insulin_history_builder
	
	public double calculate_IOB(Tvector Tvec_ins_hist_IOB, IOB_param iob_param, long time) {
		final String FUNC_TAG = "IOB_meal";
		int insulin_history_length = Tvec_ins_hist_IOB.count();
		int ii;
		double IOB = 0.0;
		try {
			for (ii=0; ii<insulin_history_length; ii++) {
				IOB = IOB + Tvec_ins_hist_IOB.get_value(ii)*iob_param.curves_nonrecursive[96-insulin_history_length+ii];
			}
			double IOB4 = 0.0;
			for (ii=0; ii<insulin_history_length; ii++) {
				IOB4 = IOB4 + Tvec_ins_hist_IOB.get_value(ii)*iob_param.fourcurves_nonrecursive[96-insulin_history_length+ii];
			}
			double IOB6 = 0.0;
			for (ii=0; ii<insulin_history_length; ii++) {
				IOB6 = IOB6 + Tvec_ins_hist_IOB.get_value(ii)*iob_param.sixcurves_nonrecursive[96-insulin_history_length+ii];
			}
			double IOB8 = 0.0;
			for (ii=0; ii<insulin_history_length; ii++) {
				IOB8 = IOB8 + Tvec_ins_hist_IOB.get_value(ii)*iob_param.eightcurves_nonrecursive[96-insulin_history_length+ii];
			}
			debug_message(TAG, "IOB > time="+time+",IOB4="+IOB4+", IOB6="+IOB6+",IOB8="+IOB8+",  IOB="+IOB);
		}
 		catch (Exception e) {
 			Bundle b = new Bundle();
 			b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
 			Event.addEvent(this, Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}	
		return IOB;
	}
	
    public void returnStatusToDiAsService(int status) {
		// Report error state back to the DiAsService
    	final String FUNC_TAG = "returnStatusToDiAsService";
		Message response;
		Bundle responseBundle;
		// Report results back to the DiAsApp
		ssm_state_estimate.state_data.Processing_State = status;
		response = Message.obtain(null, ssm_state_estimate.state_data.Processing_State, 0, 0);
		responseBundle = new Bundle();
		responseBundle.putInt("stoplight", ssm_state_estimate.state_data.stoplight);
		responseBundle.putInt("stoplight2", ssm_state_estimate.state_data.stoplight2);
		responseBundle.putBoolean("isMealBolus", ssm_state_estimate.state_data.isMealBolus);
		responseBundle.putDouble("SSM_amount", ssm_state_estimate.state_data.SSM_amount);
		responseBundle.putDouble("rem_error", ssm_state_estimate.state_data.rem_error);
		responseBundle.putDouble("bolusRecommended", ssm_state_estimate.state_data.bolus_out);
		responseBundle.putDouble("brakes_coeff", ssm_state_estimate.state_data.brakes_coeff);
		responseBundle.putDouble("basal", ssm_state_estimate.state_data.basal);
		responseBundle.putDouble("IOB", ssm_state_estimate.state_data.IOB_meal);
		responseBundle.putDouble("CR", ssm_state_estimate.state_data.CR);
		responseBundle.putDouble("latestCGM", (double)(ssm_state_estimate.state_data.Tvec_cgm_mins.get_last_value()));
		responseBundle.putLong("latestCGMTimeMinutes", ssm_state_estimate.state_data.Tvec_cgm_mins.get_last_time());
		responseBundle.putLong("last_Tvec_cgm_time_secs", last_Tvec_cgm_time_secs);
		response.setData(responseBundle);
		// Log the parameters for IO testing
		if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
    		Bundle b = new Bundle();
    		b.putString(	"description", "(SSMservice) >> DiAsService, IO_TEST"+", "+FUNC_TAG+", "+
    						"SAFETY_SERVICE_STATE_NORMAL"+", "+
    						"stoplight="+responseBundle.getInt("stoplight")+", "+
    						"stoplight2="+responseBundle.getInt("stoplight2")+", "+
    						"SSM_amount="+responseBundle.getDouble("SSM_amount")+", "+
    						"rem_error="+responseBundle.getDouble("rem_error")+", "+
    						"bolusRequested="+responseBundle.getDouble("bolusRequested")+", "+
    						"brakes_coeff="+responseBundle.getDouble("brakes_coeff")+", "+
    						"basal="+responseBundle.getDouble("basal")+", "+
    						"IOB="+responseBundle.getDouble("IOB")+", "+
    						"CR="+responseBundle.getDouble("CR")+", "+
    						"latestCGM="+responseBundle.getDouble("latestCGM")+", "+
    						"latestCGMTimeMinutes="+responseBundle.getLong("latestCGMTimeMinutes")+", "+
    						"last_Tvec_cgm_time_secs="+responseBundle.getLong("last_Tvec_cgm_time_secs")
    						);
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
		}
		else if (status == Safety.SAFETY_SERVICE_STATE_INVALID_COMMAND) {
    		Bundle b = new Bundle();
    		b.putString(	"description", "(SSMservice) >> DiAsService, SAFETY_SERVICE_STATE_INVALID_COMMAND"+", "+FUNC_TAG
    						);
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
		}
		debug_message("BOLUS_TRACE", "SafetyService > PUMP_STATE_COMMAND_COMPLETE");
		try {
			mMessengerToDiAsService.send(response);
		} 
		catch (RemoteException e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
			e.printStackTrace();
		}
		SSMSERVICE_STATE = SSMSERVICE_STATE_IDLE;
    }
	
	
    
    // Data utilities
	
    // gets subject's weight
    public void getSubjectWeight() {
		Cursor c = getContentResolver().query(Biometrics.SUBJECT_DATA_URI, new String[]{"weight"}, null, null, null);
		if (c.moveToLast()) {
			USER_WEIGHT = (c.getInt(c.getColumnIndex("weight")));
		}
		c.close();
	}
	
    // get subject's LA insulin injection data
	public void getSubjectBasal() {
		USER_BASAL.init();
		//Cursor c = getContentResolver().query(Biometrics.BASAL_PROFILE_URI, new String[]{"time","value"}, null, null, null);
		Cursor c = getContentResolver().query(Biometrics.BASAL_PROFILE_URI, null, null, null, null);
		Log.i(TAG, "Retrieved BASAL_URI with " + c.getCount() + " items");
	if (c.moveToFirst()) {
		do{
		   USER_BASAL.put(c.getLong(c.getColumnIndex("time")), (double)c.getDouble(c.getColumnIndex("value")));
				//return_value = true;
		  }
		 while (c.moveToNext());
	}	
	else
	{
		Bundle bun = new Bundle();
		bun.putString("description", "noSubjectBasal!");
		Event.addEvent(this, Event.EVENT_NETWORK_TIMEOUT, Event.makeJsonString(bun), Event.SET_LOG);
	}
	c.close();
	}		

	public void getSubjectCR() {
		USER_CR.init();
		//Cursor c = getContentResolver().query(Biometrics.CR_PROFILE_URI,  new String[]{"time","value"}, null, null, null);
		Cursor c = getContentResolver().query(Biometrics.CR_PROFILE_URI,  null, null, null, null);
		Log.i(TAG, "Retrieved CR_PROFILE_URI with " + c.getCount() + " items");
		if (c.moveToFirst()) {
			// A database exists. Initialize subject_data.
		  do{	
			USER_CR.put(c.getLong(c.getColumnIndex("time")),(double)c.getDouble(c.getColumnIndex("value")));
		  }
		  while (c.moveToNext()); 
		}
		
		c.close();
	}
	
	public void getCalibration() {
		 
	    Tvec_calibration.init();
	    Cursor c=getContentResolver().query(Biometrics.SMBG_URI, null, null, null, null);

		double calibration_value;
	    if (c.moveToFirst()) {
			do{
				if ((getCurrentTimeSeconds() - c.getLong(c.getColumnIndex("time")))<=43200 ) {
				int calval = c.getInt(c.getColumnIndex("isCalibration"));
					if (calval == 1) {
						calibration_value = (double)c.getDouble(c.getColumnIndex("smbg"));
						// time in mins
						Tvec_calibration.put(c.getLong(c.getColumnIndex("time"))/60, calibration_value);
					}
				
				
					//return_value = true;
				}
			} while (c.moveToNext());
	    }			
		c.close();
		debug_message(TAG,"Calib length " + Tvec_calibration.count());

}
		
	
	public void subject_parameters() {
		final String FUNC_TAG = "subject_parameters";
		
		Tvector CR = new Tvector(24);
		Tvector CF = new Tvector(24);
		// Load up CR Tvector
	  	Cursor c=getContentResolver().query(Biometrics.CR_PROFILE_URI, null ,null, null, null);
 	  	c.moveToFirst();
 	  	if (c.getCount() != 0) {
 	 	  	CR.put(c.getLong(c.getColumnIndex("time")), c.getDouble(c.getColumnIndex("value")));
 	 	  	while (c.moveToNext()) {
 	 	 	  	CR.put(c.getLong(c.getColumnIndex("time")), c.getDouble(c.getColumnIndex("value")));
 	 	  	}
 	  	}
 	  	else {
 	  		Debug.e(TAG, FUNC_TAG, "subject_parameters > CR_PROFILE_URI > c.getCount() == 0");
 	  		log_action(TAG, "Error: subject_parameters > CR_PROFILE_URI > c.getCount() == 0");
 	  	}
 	  	c.close();
		// Load up CF Tvector
	  	c=getContentResolver().query(Biometrics.CF_PROFILE_URI, null ,null, null, null);
 	  	c.moveToFirst();
 	  	if (c.getCount() != 0) {
 	 	  	CF.put(c.getLong(c.getColumnIndex("time")), c.getDouble(c.getColumnIndex("value")));
 	 	  	while (c.moveToNext()) {
 	 	 	  	CF.put(c.getLong(c.getColumnIndex("time")), c.getDouble(c.getColumnIndex("value")));
 	 	  	}
 	  	}
 	  	else {
 	  		Debug.e(TAG, FUNC_TAG, "subject_parameters > CF_PROFILE_URI > c.getCount() == 0");
 	  		log_action(TAG, "Error: subject_parameters > CF_PROFILE_URI > c.getCount() == 0");
 	  	}
 	  	c.close();
		// Get the offset in minutes into the current day in the current time zone (based on cell phone time zone setting)
		long timeSeconds = getCurrentTimeSeconds();
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(timeSeconds*1000)/1000;
		int timeTodayMins = (int)((timeSeconds+UTC_offset_secs)/60)%1440;
		Debug.i(TAG, FUNC_TAG, "subject_parameters > UTC_offset_secs="+UTC_offset_secs+", timeSeconds="+timeSeconds+", timeSeconds/60="+timeSeconds/60+", timeTodayMins="+timeTodayMins);
		// Get currently active CR value
		List<Integer> indices = new ArrayList<Integer>();
		indices = CR.find(">", -1, "<=", timeTodayMins);			// Find the list of indices <= time in minutes since today at 00:00
		if (indices == null) {
			indices = CR.find(">", -1, "<", -1);							// Use final value from the previous day's profile
		}
		else if (indices.size() == 0) {
			indices = CR.find(">", -1, "<", -1);							// Use final value from the previous day's profile
		}
		if (indices == null) {
			Debug.e(TAG, FUNC_TAG, "subject_parameters > Missing CR daily profile");
 	  		log_action(TAG, "Error: subject_parameters > Missing CR daily profile");
		}
		else {
			latestCR = CR.get_value(indices.get(indices.size()-1));		// Return the last CR in this range						
		}
		// Get currently active CF value
		indices = new ArrayList<Integer>();
		indices = CF.find(">", -1, "<=", timeTodayMins);			// Find the list of indices <= time in minutes since today at 00:00
		if (indices == null) {
			indices = CF.find(">", -1, "<", -1);							// Use final value from the previous day's profile
		}
		else if (indices.size() == 0) {
			indices = CF.find(">", -1, "<", -1);							// Use final value from the previous day's profile
		}
		if (indices == null) {
			Debug.e(TAG, FUNC_TAG, "subject_parameters > Missing CF daily profile");
 	  		log_action(TAG, "Error: subject_parameters > Missing CF daily profile");
		}
		else {
			latestCF = CF.get_value(indices.get(indices.size()-1));		// Return the last CF in this range						
		}
		Debug.i(TAG, FUNC_TAG, "subject_parameters > latestCR="+latestCR+", latestCF="+latestCF);
	}
	
	
	public long getCurrentTimeSeconds() {
		final String FUNC_TAG = "getCurrentTimeSeconds";

			return (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970		
	}
	
	
	// get CGM and insulin information
    public void fetchNewBiometricData() {
    	final String FUNC_TAG = "fetchNewBiometricData";
		try {
			// Fetch cgm
			Long t_minus_8_hours = new Long(getCurrentTimeSeconds() - 8*3600);
//			Cursor c=getContentResolver().query(Biometrics.CGM_URI, null, "time > "+last_Tvec_cgm_time_secs.toString(), null, null);
			Cursor c=getContentResolver().query(Biometrics.CGM_URI, null, "time > "+t_minus_8_hours.toString(), null, null);
			long last_time_temp_secs = 0;
			double cgm_value;
			if (c != null) {
				if (c.moveToFirst()) {
					do{
						cgm_value = (double)c.getDouble(c.getColumnIndex("cgm"));
						if (cgm_value>=39.0 && cgm_value<=400.0) {
							// Save the latest timestamp from the retrieved data
							if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
								last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
							}
							// Round incoming time in seconds down to the nearest minute
							Tvec_cgm_mins.put(c.getLong(c.getColumnIndex("time"))/60, cgm_value);
						}
					} while (c.moveToNext());
					last_Tvec_cgm_time_secs = last_time_temp_secs;
				}
				c.close();
			}

			// Fetch delivered insulin data
			c=getContentResolver().query(Biometrics.INSULIN_URI, null, "deliv_time > "+t_minus_8_hours.toString(), null, null);
			last_time_temp_secs = 0;
			if (c != null) {
				if (c.moveToFirst()) {
					do{
						// Save the latest timestamp from the retrieved data
						if (c.getLong(c.getColumnIndex("deliv_time")) > last_time_temp_secs) {
							last_time_temp_secs = c.getLong(c.getColumnIndex("deliv_time"));
						}
						// Insulin time in seconds
						Tvec_bolus_hist_seconds.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_total")));
						Tvec_basal_bolus_hist_seconds.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_basal")));
						Tvec_meal_bolus_hist_seconds.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_meal")));
						Tvec_corr_bolus_hist_seconds.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_corr")));
					} while (c.moveToNext());
					last_Tvec_insulin_bolus1_time_secs = last_time_temp_secs;
				}
				c.close();
			}
			Tvec_bolus_hist_seconds.dump(TAG, "Tvec_bolus_hist_seconds", 8);
			Tvec_basal_bolus_hist_seconds.dump(TAG, "Tvec_basal_bolus_hist_seconds", 8);
			Tvec_meal_bolus_hist_seconds.dump(TAG, "Tvec_meal_bolus_hist_seconds", 8);
			Tvec_corr_bolus_hist_seconds.dump(TAG, "Tvec_corr_bolus_hist_seconds", 8);	
		}
        catch (Exception e) {
    		Bundle b = new Bundle();
    		b.putString("description", "Error in "+FUNC_TAG+" > "+e.toString());
    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
        	Log.e("fetchNewBiometricData > Error SafetyService", e.toString());
        }
	}
    
	public boolean fetchStateEstimateData(long time) {
		final String FUNC_TAG = "fetchStateEstimateData";
		boolean return_value = false;
		long last_time_temp_secs = 0;
		Long Time = new Long(time);
		Cursor c=getContentResolver().query(Biometrics.STATE_ESTIMATE_URI, null, "time > "+last_state_estimate_time_secs.toString(), null, null);
		long state_estimate_time;
		if (c != null) {
			if (c.moveToFirst()) {
				do{
					if (c.getInt(c.getColumnIndex("asynchronous")) == 0) {
						state_estimate_time = c.getLong(c.getColumnIndex("time"));
						Tvec_IOB.put(state_estimate_time, c.getDouble(c.getColumnIndex("IOB")));
						Tvec_Rate.put(state_estimate_time, c.getDouble(c.getColumnIndex(IOB_CONTROLLER_RATE)));
						Tvec_GPRED.put(state_estimate_time, c.getDouble(c.getColumnIndex("GPRED")));
						if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
							last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
						}
						return_value = true;
					}
					last_state_estimate_time_secs = last_time_temp_secs;
				} while (c.moveToNext());
			}
			c.close();
		}
		return return_value;
	}

	public void putTvector(Bundle bundle, Tvector tvector, String timeKey, String valueKey) {
		long[] times = new long[tvector.count()];
		double[] values = new double[tvector.count()];
		int ii;
		for (ii=0; ii<tvector.count(); ii++) {
			times[ii] = tvector.get_time(ii).longValue();
			values[ii] = tvector.get_value(ii).doubleValue();
		}
		bundle.putLongArray(timeKey, times);
		bundle.putDoubleArray(valueKey, values);
    }
	
    public Tvector getTvector(Bundle bundle, String timeKey, String valueKey) {
		int ii;
		long[] times = bundle.getLongArray(timeKey);
		double[] values = bundle.getDoubleArray(valueKey);
		Tvector tvector = new Tvector(times.length);
		for (ii=0; ii<times.length; ii++) {
			tvector.put(times[ii], values[ii]);
		}
		return tvector;
    }
	
	// Other utilities

	private void debug_message(String tag, String message) {
		if (DEBUG) {
			Log.i(tag, message);
		}
	}
			
	
	public void log_action(String service, String action) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
	}
	
	public void log_IO(String tag, String message) {

		
		final String FUNC_TAG = "log_IO";

		Debug.i(tag, FUNC_TAG, message);
	}
	
	
	
}

