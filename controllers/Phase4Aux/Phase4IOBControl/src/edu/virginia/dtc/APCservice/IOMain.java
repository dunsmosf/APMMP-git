//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;



//import edu.virginia.dtc.HMSservice.R;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.IntentFilter;
import android.content.UriMatcher;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;
import android.os.Message;
import android.os.Handler;
import android.os.Bundle;
import android.os.RemoteException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

public class IOMain extends Service {
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	// Identify owner of record in User Table 1
	public static final int MEAL_IOB_CONTROL = 10;
	

	// DiAs State Variable and Definitions - state for the system as a whole
	public static final int DIAS_STATE_STOPPED = 0;
	public static final int DIAS_STATE_OPEN_LOOP = 1;
	public static final int DIAS_STATE_CLOSED_LOOP = 2;
	public static final int DIAS_STATE_SAFETY_ONLY = 3;
	
	private static final String VERSION = "1.0.0";
	private static final boolean DEBUG_MODE = true;
	private boolean enableIOtest = false;
	public static final String TAG = "MealIOBControl";
    public static final String IO_TEST_TAG = "MealIOBControlIO";
    
	private long simulatedTime = -1;					// Used in development to receive simulated time from Application (-1 means not valid)
	private boolean asynchronous;
	private int Timer_Ticks_Per_Control_Tick = 1;
	private int Timer_Ticks_To_Next_Meal_From_Last_Rate_Change = 1;
	public int cycle_duration_seconds = 300;
	public int cycle_duration_mins = cycle_duration_seconds/60;
	public static final int MAX_DELAY_FROM_BOLUS_CALC_TO_BOLUS_APPROVE_SECONDS = 120;
	
	// Interface definitions for the biometricsContentProvider
	public static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
    public static final Uri CGM_URI = Uri.parse("content://"+ PROVIDER_NAME + "/cgm");
    public static final Uri INSULIN_URI = Uri.parse("content://"+ PROVIDER_NAME + "/insulin");					//Compressed to a single table ("/insulin")
    public static final Uri STATE_ESTIMATE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/stateestimate");
    public static final Uri HMS_STATE_ESTIMATE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/hmsstateestimate");
    public static final Uri MEAL_URI = Uri.parse("content://"+ PROVIDER_NAME + "/meal");
    public static final Uri USER_TABLE_1_URI = Uri.parse("content://"+ PROVIDER_NAME + "/user1");
    public static final Uri USER_TABLE_2_URI = Uri.parse("content://"+ PROVIDER_NAME + "/user2");
    public static final String TIME = "time";
    public static final String CGM1 = "cgm1";
    public static final String INSULINRATE1 = "Insurate1";
    public static final String INSULINBOLUS1= "Insubolus1";
    public static final String INSULIN_BASAL_BOLUS = "basal_bolus";
    public static final String INSULIN_MEAL_BOLUS = "meal_bolus";
    public static final String INSULIN_CORR_BOLUS = "corr_bolus";
    public static final String SSM_STATE = "SSM_state";
    public static final String SSM_STATE_TIMESTAMP = "SSM_state_timestamp";

    // Field definitions for HMS_STATE_ESTIMATE_TABLE
    public static final String IOB = "IOB";
    public static final String GPRED = "Gpred";
    public static final String GPRED_CORRECTION = "Gpred_correction";
    public static final String GPRED_BOLUS = "Gpred_bolus";
    public static final String XI00 = "Xi00";
    public static final String XI01 = "Xi01";
    public static final String XI02 = "Xi02";
    public static final String XI03 = "Xi03";
    public static final String XI04 = "Xi04";
    public static final String XI05 = "Xi05";
    public static final String XI06 = "Xi06";
    public static final String XI07 = "Xi07";
    public static final String BRAKES_COEFF = "brakes_coeff";
    public static final String BOLUS_AMOUNT = "bolus_amount";
	
	// Working storage for current cgm and insulin data
    private double brakes_coeff = 1.0;
	Tvector Tvec_cgm1, Tvec_cgm2, Tvec_insulin_rate1, Tvec_spent;
	Tvector Tvec_IOB, Tvec_GPRED;
	public static final int TVEC_SIZE = 96;				// 8 hours of samples at 5 mins per sample
	// Store most recent timestamps in seconds for each biometric Tvector
	Long last_Tvec_cgm1_time_secs, last_Tvec_insulin_bolus1_time_secs, last_Tvec_requested_insulin_bolus1_time_secs;
	
	// Used to calculate and store Param object
	private Params params;				// This class contains controller parameters
	public Subject subject;		 		// This class encapsulates current Subject SI parameters
	private Context context;
	
	// Used to calculate and store state_estimate object
	public InsulinTherapy insulin_therapy;
	
    public BroadcastReceiver TickReceiver; 

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
    
	/*
	 * 
	 *  Interface to the Application (our only Client)
	 * 
	 */
	// HMSservice interface definitions
	public static final int APC_SERVICE_CMD_NULL = 0;
	public static final int APC_SERVICE_CMD_START_SERVICE = 1;
	public static final int APC_SERVICE_CMD_REGISTER_CLIENT = 2;
	public static final int APC_SERVICE_CMD_CALCULATE_STATE = 3;
	public static final int APC_SERVICE_CMD_STOP_SERVICE = 4;
	public static final int APC_SERVICE_CMD_CALCULATE_BOLUS = 5;
	
    // HMSservice return values
    public static final int APC_PROCESSING_STATE_NORMAL = 10;
    public static final int APC_PROCESSING_STATE_ERROR = -11;
    public static final int APC_CONFIGURATION_PARAMETERS = 12;		// APController parameter status return
    public static final int APC_CALCULATED_BOLUS = 13;    	// Calculated bolus return value
    
    // APC_SERVICE_CMD_CALCULATE_BOLUS return status
	public static final int APC_CALCULATED_BOLUS_SUCCESS = 0;
	public static final int APC_CALCULATED_BOLUS_MISSING_SUBJECT_DATA = -1;
	public static final int APC_CALCULATED_BOLUS_MISSING_STATE_ESTIMATE_DATA = -2;
	public static final int APC_CALCULATED_BOLUS_INVALID_CREDIT_REQUEST = -3;
  
    // APController type
    private int APC_TYPE;
    public static final int APC_TYPE_HMS = 1;
    public static final int APC_TYPE_RCM = 2;
    public static final int APC_TYPE_AMYLIN = 3;
    public static final int APC_TYPE_MEALIOB = 3;
    public static final int APC_TYPE_P4IOB = 4;
    public static final int APC_TYPE_SHELL = 9999;

    // Define AP Controller behavior
    private int APC_MEAL_CONTROL;
    public static final int APC_NO_MEAL_CONTROL = 1;
    public static final int APC_WITH_MEAL_CONTROL = 2;

    /* Messenger for sending responses to the client (Application). */
    public Messenger mMessengerToClient = null;
    /* Target we publish for clients to send commands to IncomingHandlerFromClient. */
    final Messenger mMessengerFromClient = new Messenger(new IncomingHandlerFromClient());
 
    /* When binding to the service, we return an interface to our messenger for sending messages to the service. */
    @Override
    public IBinder onBind(Intent intent) {
        Toast toast = Toast.makeText(getApplicationContext(), "SafetyService binding to Application", Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
		toast.show();
        return mMessengerFromClient.getBinder();
    }
    
    /* Handles incoming commands from the client (Application). */
    class IncomingHandlerFromClient extends Handler {
    	Bundle paramBundle, responseBundle;
    	Message response;
    	@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
				case APC_SERVICE_CMD_NULL:		// null command
					debug_message(TAG, "APC_SERVICE_CMD_NULL");
                	Toast.makeText(getApplicationContext(), "MealIOBControl > APC_SERVICE_CMD_NULL", Toast.LENGTH_LONG).show();
					break;
				case APC_SERVICE_CMD_START_SERVICE:		// start service command
					// Create Param object with subject parameters received from Application
					debug_message(TAG, "APC_SERVICE_CMD_START_SERVICE");
					paramBundle = msg.getData();
					enableIOtest = (boolean)paramBundle.getBoolean("enableIOtest");
					double TDI = (double)paramBundle.getDouble("TDI");
					int IOB_curve_duration_hours = paramBundle.getInt("IOB_curve_duration_hours");
					simulatedTime = paramBundle.getLong("simulatedTime", -1);
					Tvector tvectorCR = getTvector(paramBundle, "CRtimes", "CRvalues");
					Tvector tvectorCF = getTvector(paramBundle, "CFtimes", "CFvalues");
					Tvector tvectorBasal = getTvector(paramBundle, "Basaltimes", "Basalvalues");
					// Create and initialize the Subject object
					subject = new Subject(getCurrentTimeSeconds(), getApplicationContext());
					
					// Log the parameters for IO testing
					if (enableIOtest) {
						int ii;
						log_IO(IO_TEST_TAG, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
						log_IO(IO_TEST_TAG, "> APC_SERVICE_CMD_START_SERVICE");
						log_IO(IO_TEST_TAG, "> TDI="+TDI);
						log_IO(IO_TEST_TAG, "> IOB_curve_duration_hours="+IOB_curve_duration_hours);
						log_IO(IO_TEST_TAG, "> simulatedTime="+simulatedTime);
						for (ii=0; ii<tvectorCR.count(); ii++) {
							log_IO(IO_TEST_TAG, "> tvectorCR["+ii+"]=("+tvectorCR.get_time(ii)+", "+tvectorCR.get_value(ii)+")");
						}
						for (ii=0; ii<tvectorCF.count(); ii++) {
							log_IO(IO_TEST_TAG, "> tvectorCF["+ii+"]=("+tvectorCF.get_time(ii)+", "+tvectorCF.get_value(ii)+")");
						}
						for (ii=0; ii<tvectorBasal.count(); ii++) {
							log_IO(IO_TEST_TAG, "> tvectorBasal["+ii+"]=("+tvectorBasal.get_time(ii)+", "+tvectorBasal.get_value(ii)+")");
						}
					}

					// Inform DiAsService of the APC_TYPE and how many ticks you require per control tick
                	Toast.makeText(getApplicationContext(), "MealIOBControl > APC_SERVICE_CMD_START_SERVICE, Timer_Ticks_Per_Control_Tick="+Timer_Ticks_Per_Control_Tick, Toast.LENGTH_LONG).show();
					debug_message(TAG, "Timer_Ticks_Per_Control_Tick="+Timer_Ticks_Per_Control_Tick);
					response = Message.obtain(null, APC_CONFIGURATION_PARAMETERS, 0, 0);
					responseBundle = new Bundle();
					Timer_Ticks_Per_Control_Tick = 1;
					responseBundle.putInt("Timer_Ticks_Per_Control_Tick", Timer_Ticks_Per_Control_Tick);
    				Timer_Ticks_To_Next_Meal_From_Last_Rate_Change = 1;
    				responseBundle.putInt("Timer_Ticks_To_Next_Meal_From_Last_Rate_Change", Timer_Ticks_To_Next_Meal_From_Last_Rate_Change);	// Ticks from meal announcement to meal start
    				APC_TYPE = APC_TYPE_P4IOB;
    				responseBundle.putInt("APC_TYPE", APC_TYPE_P4IOB);
    				APC_MEAL_CONTROL = APC_WITH_MEAL_CONTROL;
					responseBundle.putInt("APC_MEAL_CONTROL", APC_WITH_MEAL_CONTROL);
					
					// Log the parameters for IO testing
					if (enableIOtest) {
						log_IO(IO_TEST_TAG, "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
						log_IO(IO_TEST_TAG, "< APC_CONFIGURATION_PARAMETERS");
						log_IO(IO_TEST_TAG, "< Timer_Ticks_Per_Control_Tick="+Timer_Ticks_Per_Control_Tick);
						log_IO(IO_TEST_TAG, "< Timer_Ticks_To_Next_Meal_From_Last_Rate_Change="+Timer_Ticks_To_Next_Meal_From_Last_Rate_Change);
						log_IO(IO_TEST_TAG, "< APC_TYPE="+APC_TYPE);
						log_IO(IO_TEST_TAG, "< APC_MEAL_CONTROL="+APC_MEAL_CONTROL);
					}
					
					response.setData(responseBundle);
					try {
						mMessengerToClient.send(response);
					} 
					catch (RemoteException e) {
						e.printStackTrace();
					}
					break;
				case APC_SERVICE_CMD_CALCULATE_STATE:
					debug_message(TAG, "APC_SERVICE_CMD_CALCULATE_STATE");
					paramBundle = msg.getData();
					enableIOtest = (boolean)paramBundle.getBoolean("enableIOtest");
					simulatedTime = paramBundle.getLong("simulatedTime", -1);
					asynchronous = (boolean)paramBundle.getBoolean("asynchronous");
					long corrFlagTime = (long)paramBundle.getLong("corrFlagTime", 0);
					long hypoFlagTime = (long)paramBundle.getLong("hypoFlagTime", 0);
					long calFlagTime = (long)paramBundle.getLong("calFlagTime", 0);
					long mealFlagTime = (long)paramBundle.getLong("mealFlagTime", 0);
//					brakes_coeff = paramBundle.getDouble("brakes_coeff", 1.0);
					double DIAS_STATE = paramBundle.getInt("DIAS_STATE", 0);
					double tick_modulus = paramBundle.getInt("tick_modulus", 0);
					boolean currentlyExercising = paramBundle.getBoolean("currentlyExercising", false);
					Bolus meal_bolus;

					// Log the parameters for IO testing
					if (enableIOtest) {
						log_IO(IO_TEST_TAG, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
						log_IO(IO_TEST_TAG, "> APC_SERVICE_CMD_CALCULATE_STATE");
						log_IO(IO_TEST_TAG, "> simulatedTime="+simulatedTime);
						log_IO(IO_TEST_TAG, "> asynchronous="+asynchronous);
						log_IO(IO_TEST_TAG, "> corrFlagTime="+corrFlagTime);
						log_IO(IO_TEST_TAG, "> calFlagTime="+calFlagTime);
						log_IO(IO_TEST_TAG, "> hypoFlagTime="+hypoFlagTime);
						log_IO(IO_TEST_TAG, "> mealFlagTime="+mealFlagTime);
						log_IO(IO_TEST_TAG, "> brakes_coeff="+brakes_coeff);
						log_IO(IO_TEST_TAG, "> DIAS_STATE="+DIAS_STATE);
						log_IO(IO_TEST_TAG, "> tick_modulus="+tick_modulus);
					}
					//
					// Closed Loop handler
					//
					if (DIAS_STATE == DIAS_STATE_CLOSED_LOOP) {
						// Synchronous operation
						if (!asynchronous) {
							double spend_request = 0.0;
							double differential_basal_rate = 0.0;
							// Calculate insulin therapy if there is some recent CGM, Gpred and IOB data to work with...
							if (fetchAllBiometricData(getCurrentTimeSeconds()-(300*24+2*60)) && fetchStateEstimateData(getCurrentTimeSeconds()-(300*5+2*60))) {
								Tvec_cgm1.dump(TAG, "CGM");
								Tvec_IOB.dump(TAG, "IOB");
								Tvec_GPRED.dump(TAG, "GPRED");
								if (insulin_therapy == null) {
									insulin_therapy = new InsulinTherapy(Tvec_cgm1, 
																		Tvec_spent, 
																		Tvec_IOB,
																		Tvec_GPRED,
																		params, 
																		getCurrentTimeSeconds(), 
																		cycle_duration_mins, 
																		corrFlagTime, 
																		hypoFlagTime, 
																		calFlagTime, 
																		mealFlagTime, 
																		getApplicationContext(),
																		brakes_coeff);
									if (insulin_therapy.valid) {
										differential_basal_rate = insulin_therapy.therapy_data.differential_basal_rate;
										spend_request = insulin_therapy.therapy_data.spend_request;
									}
									else {
										differential_basal_rate = 0.0;
										spend_request = 0.0;
									}
								}
								else {
									if (insulin_therapy.insulin_therapy(Tvec_cgm1, 
																		Tvec_spent, 
																		Tvec_IOB,
																		Tvec_GPRED,
																		params, 
																		getCurrentTimeSeconds(), 
																		cycle_duration_mins, 
																		corrFlagTime, 
																		hypoFlagTime, 
																		calFlagTime, 
																		mealFlagTime, 
																		brakes_coeff)) {
										differential_basal_rate = insulin_therapy.therapy_data.differential_basal_rate;
										spend_request = insulin_therapy.therapy_data.spend_request;
									}
									else {
										differential_basal_rate = 0.0;
										spend_request = 0.0;
									}
								}
							}
							response = Message.obtain(null, APC_PROCESSING_STATE_NORMAL, 0, 0);
							responseBundle = new Bundle();
							responseBundle.putDouble("recommended_bolus", 0.0);
							responseBundle.putDouble("creditRequest", 0.0);
							responseBundle.putDouble("spendRequest", spend_request);
							responseBundle.putBoolean("new_differential_rate", true);
							responseBundle.putDouble("differential_basal_rate", differential_basal_rate);
							responseBundle.putDouble("IOB", 0.0);
							debug_message(TAG, "return_value: spend_request="+spend_request+", differential_basal_rate="+differential_basal_rate);
						}
						// Asynchronous parameter state indicates that a meal may have just been approved (will require further verification)
						else {
							Meal meal_current = new Meal(context);
							// Check to see if there is an active meal
							meal_current.read();
							if (meal_current.approved && !meal_current.treated) {
								if (meal_current.time_announce - getCurrentTimeSeconds() < MAX_DELAY_FROM_BOLUS_CALC_TO_BOLUS_APPROVE_SECONDS) {
									response = Message.obtain(null, APC_PROCESSING_STATE_NORMAL, 0, 0);
									responseBundle = new Bundle();
									responseBundle.putDouble("recommended_bolus", 0.0);
									responseBundle.putDouble("creditRequest", 0.0);
									responseBundle.putDouble("spendRequest", meal_current.meal_screen_meal_bolus + meal_current.meal_screen_corr_bolus);
									responseBundle.putBoolean("new_differential_rate", true);
									responseBundle.putDouble("differential_basal_rate", 0.0);									
								}
							}
						}						
					}
					//
					// Open Loop handler
					//
					else if (DIAS_STATE == DIAS_STATE_OPEN_LOOP) {
						log_IO(TAG, "Error > Invalid DiAs State == "+DIAS_STATE);
						response = Message.obtain(null, APC_PROCESSING_STATE_NORMAL, 0, 0);
						responseBundle = new Bundle();
						responseBundle.putDouble("recommended_bolus", 0.0);
						responseBundle.putDouble("creditRequest", 0.0);
						responseBundle.putDouble("spendRequest", 0.0);
						responseBundle.putBoolean("new_differential_rate", true);
						responseBundle.putDouble("differential_basal_rate", 0.0);									
					}
					//
					// Otherwise
					//
					else {
						log_IO(TAG, "Error > Invalid DiAs State == "+DIAS_STATE);
						response = Message.obtain(null, APC_PROCESSING_STATE_NORMAL, 0, 0);
						responseBundle = new Bundle();
						responseBundle.putDouble("recommended_bolus", 0.0);
						responseBundle.putDouble("creditRequest", 0.0);
						responseBundle.putDouble("spendRequest", 0.0);
						responseBundle.putBoolean("new_differential_rate", true);
						responseBundle.putDouble("differential_basal_rate", 0.0);									
					}
						
        			// Log the parameters for IO testing
        			if (enableIOtest) {
        				log_IO(IO_TEST_TAG, "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        				log_IO(IO_TEST_TAG, "< APC_PROCESSING_STATE_NORMAL");
        				log_IO(IO_TEST_TAG, "< IOB="+responseBundle.getDouble("IOB"));
        				log_IO(IO_TEST_TAG, "< bolusCorrection="+responseBundle.getDouble("bolusCorrection"));
        				log_IO(IO_TEST_TAG, "< creditRequest="+responseBundle.getDouble("creditRequest"));
        				log_IO(IO_TEST_TAG, "< spendRequest="+responseBundle.getDouble("spendRequest"));
        				log_IO(IO_TEST_TAG, "< extendedBolusMealInsulin="+responseBundle.getDouble("extendedBolusMealInsulin"));
        				log_IO(IO_TEST_TAG, "< extendedBolusCorrInsulin="+responseBundle.getDouble("extendedBolusCorrInsulin"));
        				log_IO(IO_TEST_TAG, "< new_differential_rate="+true);
        				log_IO(IO_TEST_TAG, "< differential_basal_rate="+responseBundle.getDouble("differential_basal_rate", 0.0));
        			}        			

					responseBundle.putBoolean("asynchronous", asynchronous);
					response.setData(responseBundle);
					try {
						mMessengerToClient.send(response);
					} 
					catch (RemoteException e) {
						e.printStackTrace();
					}
					break;
				case APC_SERVICE_CMD_CALCULATE_BOLUS:
					debug_message(TAG, "APC_SERVICE_CMD_CALCULATE_BOLUS");
        			Toast.makeText(getApplicationContext(), "MealIOBControl > APC_SERVICE_CMD_CALCULATE_BOLUS", Toast.LENGTH_LONG).show();
					paramBundle = msg.getData();
					enableIOtest = (boolean)paramBundle.getBoolean("enableIOtest");
					double MealScreenCHO = (double)paramBundle.getDouble("CHOg", 0);
					double MealScreenBG = (double)paramBundle.getDouble("SMBG", 0);
					simulatedTime = paramBundle.getLong("simulatedTime", -1);
					int type = (int)paramBundle.getDouble("type", 0);

					// Log the parameters for IO testing
					if (enableIOtest) {
						log_IO(IO_TEST_TAG, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
						log_IO(IO_TEST_TAG, "> APC_SERVICE_CMD_CALCULATE_BOLUS");
						log_IO(IO_TEST_TAG, "> CHOg="+MealScreenCHO);
						log_IO(IO_TEST_TAG, "> SMBG="+MealScreenBG);
					}
					
        			// Calculate recommended bolus value
					Bolus bolus = new Bolus(context);
					double totalCreditRequest = 0;
					int completion_status = -5;		// 0 indicates success
					if (fetchStateEstimateData(getCurrentTimeSeconds()-(300*5+2*60))) {
						if (subject.read(getCurrentTimeSeconds(), getApplicationContext())) {
							// 1. Calculate meal bolus amount MealBolusA
							double MealASat;
							if (MealScreenCHO >= 100.0)
								MealASat = 100.0;
							else if (MealScreenCHO > 0.0)
								MealASat = MealScreenCHO;
							else
								MealASat = 0.0;
							bolus.MealBolusA = MealASat/subject.CR;
							// Apply limits to MealBolusA
							if (bolus.MealBolusA > 30.0) {
								bolus.MealBolusA = 30.0;
							}
							else if (bolus.MealBolusA < 0.0) {
								bolus.MealBolusA = 0.0;
							}
							bolus.MealBolusArem = bolus.MealBolusA;
							debug_message(TAG, "APC_SERVICE_CMD_CALCULATE_BOLUS > CR="+subject.CR+", CF="+subject.CF);
							debug_message(TAG, "APC_SERVICE_CMD_CALCULATE_BOLUS > MealBolusA="+bolus.MealBolusA);
							
							totalCreditRequest = bolus.MealBolusA;
							if (totalCreditRequest <= 0) {
								totalCreditRequest = 0.0;
								completion_status = APC_CALCULATED_BOLUS_INVALID_CREDIT_REQUEST;		// Invalid credit request
							}
							else {
								bolus.save(getCurrentTimeSeconds());
								completion_status = APC_CALCULATED_BOLUS_SUCCESS;
							}
							bolus.dump();
						}
						else {
							completion_status = APC_CALCULATED_BOLUS_MISSING_SUBJECT_DATA;			// Missing Subject data
						}
					}
					else {
						completion_status = APC_CALCULATED_BOLUS_MISSING_STATE_ESTIMATE_DATA;				// Missing State Estimate data
					}
					

					// Log the parameters for IO testing
					if (enableIOtest) {
						log_IO(IO_TEST_TAG, "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
						log_IO(IO_TEST_TAG, "< APC_CALCULATED_BOLUS");
						log_IO(IO_TEST_TAG, "< calculated_bolus="+totalCreditRequest);
					}
					response = Message.obtain(null, APC_CALCULATED_BOLUS, 0, 0);
					responseBundle = new Bundle();
					responseBundle.putDouble("calculated_bolus", totalCreditRequest);
					responseBundle.putInt("completion_status", completion_status);
					response.setData(responseBundle);
    				try {
    					mMessengerToClient.send(response);
    				} 
    				catch (RemoteException e) {
    					e.printStackTrace();
    				}
					break;
				case APC_SERVICE_CMD_STOP_SERVICE:
					debug_message(TAG, "APC_SERVICE_CMD_STOP_SERVICE");
    				Toast.makeText(getApplicationContext(), "MealIOBControl > APC_SERVICE_CMD_STOP_SERVICE", Toast.LENGTH_LONG).show();
					stopSelf();
					break;
				case APC_SERVICE_CMD_REGISTER_CLIENT:
					debug_message(TAG, "APC_SERVICE_CMD_REGISTER_CLIENT");
					mMessengerToClient = msg.replyTo;
					if (msg.arg1 == 1) {
						enableIOtest = true;
            			log_IO(IO_TEST_TAG, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
            			log_IO(IO_TEST_TAG, "> APC_SERVICE_CMD_REGISTER_CLIENT");
					} else {
						enableIOtest = false;
					}
					debug_message(TAG, "mMessengerToClient="+mMessengerToClient);
    				Toast.makeText(getApplicationContext(), "MealIOBControl > APC_SERVICE_CMD_REGISTER_CLIENT", Toast.LENGTH_LONG).show();
					break;
				default:
					super.handleMessage(msg);
            }
        }
    }
	
	@Override
	public void onCreate() {
		Toast toast = Toast.makeText(this, "HMSservice  onCreate: Service Created", Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
		toast.show();
        log_action(TAG, "onCreate");
        brakes_coeff = 1.0;
        asynchronous = false;
		Tvec_cgm1 = new Tvector(TVEC_SIZE);
		Tvec_cgm2 = new Tvector(TVEC_SIZE);
		Tvec_insulin_rate1 = new Tvector(TVEC_SIZE);
		Tvec_spent = new Tvector(TVEC_SIZE);
		Tvec_IOB = new Tvector(TVEC_SIZE);
		Tvec_GPRED = new Tvector(TVEC_SIZE);
		// Initialize most recent timestamps
		last_Tvec_cgm1_time_secs = new Long(0);
		last_Tvec_insulin_bolus1_time_secs = new Long(0);
		last_Tvec_requested_insulin_bolus1_time_secs = new Long(0);
		// Set up controller parameters
		params = new Params();
		context = getApplicationContext();
		
        // Set up a Notification for this Service
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        int icon = R.drawable.icon;
        CharSequence tickerText = "";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
        Context context = getApplicationContext();
        CharSequence contentTitle = "HMS v1.0";
        CharSequence contentText = "Mitigating Hyperglycemia";
        Intent notificationIntent = new Intent(this, IOMain.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        final int APC_ID = 3;
        //mNotificationManager.notify(APC_ID, notification);
        // Make this a Foreground Service
        startForeground(APC_ID, notification);
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();  
    }

	@Override
	public void onDestroy() {
//		getApplicationContext().unregisterReceiver(TickReceiver);	   
		Toast toast = Toast.makeText(this, "MealIOBControl  Stopped", Toast.LENGTH_LONG);
		toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
		toast.show();
		debug_message(TAG, "onDestroy");
        log_action(TAG, "onDestroy");
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
			return 0;
		}
	
	public boolean fetchAllBiometricData(long time) {
		boolean return_value = false;
		// Clear CGM Tvector
		Tvec_cgm1.init();
		Long Time = new Long(time);
		// Fetch full sensor1 time/data from cgmiContentProvider
		try {
			// Fetch the last 2 hours of CGM data
			Cursor c=getContentResolver().query(CGM_URI, null, Time.toString(), null, null);
//			debug_message(TAG,"CGM > c.getCount="+c.getCount());
			long last_time_temp_secs = 0;
			double cgm1_value, cgm2_value;
			if (c.moveToFirst()) {
				do{
					// Fetch the cgm1 and cgm2 values so that they can be screened for validity
					cgm1_value = (double)c.getDouble(c.getColumnIndex("cgm1"));
					// Make sure that cgm1_value is in the range of validity
					if (cgm1_value>=39.0 && cgm1_value<=401.0) {
						// Save the latest timestamp from the retrieved data
						if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
							last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
						}
						// time in seconds
						Tvec_cgm1.put(c.getLong(c.getColumnIndex("time")), cgm1_value);
						return_value = true;
					}
				} while (c.moveToNext());
			}
			c.close();
			last_Tvec_cgm1_time_secs = last_time_temp_secs;
/*			
			// Fetch INSULIN data
			c=getContentResolver().query(INSULIN_URI, null, last_Tvec_insulin_bolus1_time_secs.toString(), null, null);
			debug_message(TAG,"INSULIN > c.getCount="+c.getCount());
			last_time_temp_secs = 0;
			if (c.moveToFirst()) {
				do{
					// Save the latest timestamp from the retrieved data
					if (c.getLong(c.getColumnIndex("deliv_time")) > last_time_temp_secs) {
						last_time_temp_secs = c.getLong(c.getColumnIndex("deliv_time"));
					}
					// Round incoming time in seconds down to the nearest minute
					Tvec_insulin_rate1.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_total")));
					Tvec_basal_bolus_hist_seconds.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_basal")));
					Tvec_meal_bolus_hist_seconds.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_meal")));
					Tvec_corr_bolus_hist_seconds.put(c.getLong(c.getColumnIndex("deliv_time")), (double)c.getDouble(c.getColumnIndex("deliv_corr")));
				} while (c.moveToNext());
			}
			c.close();
			last_Tvec_insulin_bolus1_time_secs = last_time_temp_secs;
*/			
		}
        catch (Exception e) {
        		Log.e("Error SafetyService", e.getMessage());
        }
		return return_value;
	}

	
	public boolean fetchStateEstimateData(long time) {
		boolean return_value = false;
		// Clear Tvectors
		Tvec_IOB.init();
		Tvec_GPRED.init();
		// Fetch data from State Estimate data records
		Long Time = new Long(time);
		Cursor c=getContentResolver().query(STATE_ESTIMATE_URI, null, Time.toString(), null, null);
		long state_estimate_time;
		if (c.moveToFirst()) {
			do{
				if (c.getInt(c.getColumnIndex("asynchronous")) == 0) {
					state_estimate_time = c.getLong(c.getColumnIndex("time"));
					Tvec_IOB.put(state_estimate_time, c.getDouble(c.getColumnIndex("IOB")));
					Tvec_GPRED.put(state_estimate_time, c.getDouble(c.getColumnIndex("GPRED")));
					return_value = true;
				}
				brakes_coeff = c.getDouble(c.getColumnIndex("brakes_coeff"));
			} while (c.moveToNext());
		}
		else
		{
			debug_message(TAG, "State Estimate Table empty!");
		}
		c.close();
		return return_value;
	}
	
	public double glucoseTarget(long time) {
		// Get the offset in hours into the current day in the current time zone (based on cell phone time zone setting)
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(time*1000)/1000;
		int timeNowMins = (int)((time+UTC_offset_secs)/60)%1440;
		double ToD_hours = (double)timeNowMins/1440.0;
//		debug_message(TAG, "subject_parameters > time="+time+", time/60="+time/60+", timeNowMins="+timeNowMins+", ToD_hours="+ToD_hours);
		double x;
		if (ToD_hours<6.0) {
			x = (1.0+ToD_hours)/7.0;
		}
		else if (ToD_hours>=6.0 && ToD_hours<7.0) {
			x = 7.0-ToD_hours;
		}
		else if (ToD_hours>=7.0 && ToD_hours<23.0) {
			x = 0.0;
		}
		else {
			x = (ToD_hours-23.0)/7.0;
		}
		return 160.0-45.0*Math.pow((x/0.5),3.0)/(1.0+Math.pow((x/0.5),3.0));
	}
	
	public long getCurrentTimeSeconds() {
		if (simulatedTime > 0) {
//			debug_message(TAG, "getCurrentTimeSeconds > returning simulatedTime="+simulatedTime);
			return simulatedTime;			// simulatedTime passed to us by Application for development mode
		}
		else {
			return (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970		
		}
	}

	public void storeUserTable1Data(long time,
									double INS_target_sat,
									double INS_target_slope_sat,
									double differential_basal_rate, 
									double MealBolusA,
									double MealBolusArem,
									double spend_request,
									double CorrA,
									double IOBrem,
									double d,
									double h,
									double H,
									double cgm_slope1,
									double cgm_slope2,
									double cgm_slope_diff,
									double X,
									double detect_meal
									) {
	  	ContentValues values = new ContentValues();
	  	values.put("time", time);
	  	values.put("l0", MEAL_IOB_CONTROL);
       	values.put("d0", INS_target_sat);
       	values.put("d1", INS_target_slope_sat);
       	values.put("d2", differential_basal_rate);
       	values.put("d3", MealBolusA);
       	values.put("d4", MealBolusArem);
       	values.put("d5", spend_request);
       	values.put("d6", CorrA);
       	values.put("d7", IOBrem);
       	values.put("d8", d);
       	values.put("d9", h);
       	values.put("d10", H);
       	values.put("d11", cgm_slope1);
       	values.put("d12", cgm_slope2);
       	values.put("d13", cgm_slope_diff);
       	values.put("d14", X);
       	values.put("d15", detect_meal);
       	Uri uri;
       	try {
       		uri = getContentResolver().insert(USER_TABLE_1_URI, values);
       	}
       	catch (Exception e) {
       		Log.e(TAG, e.getMessage());
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
		debug_message(tag, message);
		/*
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", tag);
        i.putExtra("Status", message);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
        */
	}
	
	private static void debug_message(String tag, String message) {
		if (DEBUG_MODE) {
			Log.i(tag, message);
		}
	}
}