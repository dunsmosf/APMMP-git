package edu.virginia.dtc.MCMservice;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Meal;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.Tvector.Tvector;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;


	public class MCMservice_new extends Service{

	public static final String TAG = "MCMservice";
    public static final String IO_TEST_TAG = "MCMserviceIO";

	public static final boolean DEBUG = true;
    
    private boolean enableIOtest = false;
	private long simulatedTime = -1;	// Used in development to receive simulated time from Application (-1 means not valid)
	public int cycle_duration_seconds = 300;
	public int cycle_duration_mins = cycle_duration_seconds/60;
	double latestCR = 0;
	double latestCF = 0;
	
	private Tvector USER_BASAL,Tvec_basal;
	private Tvector USER_CR;
	private int USER_WEIGHT;
	
	private static final int MCM_OCAD_MEAL_ACTIVITY_CALCULATE = 3000;
	private static final int ADVISED_BOLUS = 3001;
	
	public Tvector Tvec_cgm1, Tvec_meal, Tvec_insulin_rate1, Tvec_calibration;//Tvec_basal_bolus_hist_seconds,	Tvec_meal_bolus_hist_seconds,	Tvec_corr_bolus_hist_seconds;
	public long last_Tvec_cgm1_time_secs, last_Tvec_insulin_rate1_time_secs, last_Tvec_meal_time_secs ,time;
	public long currentTimeseconds,timesinceLastCalib;
		
	private OptCORR_param optcorr_param; 
	
	/* Messenger for sending responses to the client (Application). */
	public Messenger mMessengerToActivity = null;
    public Messenger mMessengerToService = null;
    final Messenger mMessengerFromService = new Messenger(new IncomingHandler());
    
    private BroadcastReceiver profileReceiver;
    
	public void onCreate()
	{
		super.onCreate();
		
		enableIOtest = false;
        log_action(TAG, "onCreate");
		
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
        Intent notificationIntent = new Intent(this, MCMservice_new.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        final int MCM_ID = 100;
        
        // Make this a Foreground Service
        startForeground(MCM_ID, notification);
        
        profileReceiver = new BroadcastReceiver(){
        	final String FUNC_TAG = "profileReceiver";
        	
			@Override
			public void onReceive(Context context, Intent intent) {
				Debug.i(TAG, FUNC_TAG, "Profiles changed!  Updating information...");
				
				Tvec_cgm1 = new Tvector();
				Tvec_meal = new Tvector();
				Tvec_insulin_rate1 = new Tvector();
				USER_BASAL =  new Tvector();
				USER_CR = new Tvector();
				Tvec_basal  = new Tvector();
				Tvec_calibration=new Tvector();
				
				getSubjectWeight();
				getSubjectBasal();
				getSubjectCR();

				// compose Hue matrices
				Debug.i(TAG, FUNC_TAG, "pre proc");
				optcorr_param = new OptCORR_param(USER_BASAL, USER_CR, USER_WEIGHT);
				Debug.i(TAG, FUNC_TAG, "post proc");
				Toast.makeText(getApplicationContext(),"matrices built", Toast.LENGTH_SHORT).show();
			}
        };
        
        IntentFilter filter = new IntentFilter("edu.virginia.dtc.intent.action.DIASSETUP_PROFILE_CHANGED");
        this.registerReceiver(profileReceiver, filter);
	}
	
    class IncomingHandler extends Handler 
    {
		final String FUNC_TAG = "IncomingHandlerFromClient";

    	Bundle responseBundle;
    	
    	@Override
        public void handleMessage(Message msg) {
            edu.virginia.dtc.MCMservice.OptCORR_processing optcorr_bolus_calculation;
			double advised_bolus;
			switch (msg.what) 
            {
				//**********************************************************
				// Communication with MCMservice
				//**********************************************************
	            case Meal.MEAL_ACTIVITY_REGISTER:
	            	Debug.i(TAG, FUNC_TAG, "MEAL_ACTIVITY_REGISTER");
	            	mMessengerToActivity = msg.replyTo;
	            	if (enableIOtest) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "MealActivity >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"MEAL_ACTIVITY_REGISTER"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
	            	break;
	            case MCM_OCAD_MEAL_ACTIVITY_CALCULATE:
	            	responseBundle = msg.getData();
					
	            	double CHOg = responseBundle.getDouble("MealScreenCHO");
	            	double SMBG = responseBundle.getDouble("MealScreenBG");
	            	
	            	Debug.i(TAG, FUNC_TAG, "CHO: "+CHOg+" SMBG: "+SMBG);
	            	
	            	if (enableIOtest) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "MealActivity >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"MEAL_ACTIVITY_CALCULATE"+", "+
                						"MealScreenCHO="+CHOg+", "+
                						"MealScreenBG="+SMBG
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
	            	
	            	// DO BOLUS CALCULATION ---------------------------------------------------
	            	int MCM_status;
					String MCM_description;
					subject_parameters();
	            	
					
					
					currentTimeseconds = getCurrentTimeSeconds();
	            	fetchAllBiometricData();
	            	getCalibration();
	            	debug_message(TAG, "CGM is " + Tvec_cgm1.get_last_value());
	            	optcorr_bolus_calculation = new OptCORR_processing_new(optcorr_param,
												            			Tvec_meal,
												            			Tvec_cgm1,
												            			Tvec_insulin_rate1,
												            			Tvec_basal,
												            			currentTimeseconds,
												            			Tvec_calibration);
//	            	for (int ii = 138; ii <= 143; ii++) {
//	            	Toast.makeText(getApplicationContext(),"CGM fix " + optcorr_bolus_calculation.CGM_fix[ii], Toast.LENGTH_SHORT).show();
//	            	}
	        		advised_bolus = optcorr_bolus_calculation.Ustar;
	        		        		
					MCM_status = 0;
					MCM_description = "Test";
					Message matrixCalc = Message.obtain(null, ADVISED_BOLUS, 0, 0);
					responseBundle = new Bundle();
					responseBundle.putDouble("advised_bolus", advised_bolus);
					responseBundle.putInt("MCM_status", MCM_status);
					responseBundle.putString("MCM_description", MCM_description);

					if (enableIOtest) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "(MCMservice) >> DiAsService, IO_TEST"+", "+FUNC_TAG+", "+
                						"MCM_CALCULATED_BOLUS"+", "+
                						"bolus= "+advised_bolus+", "+
                						"MCM_status= "+MCM_status+", "+
                						"MCM_description= "+MCM_description
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					

     				matrixCalc.setData(responseBundle);
     				
					try {
						mMessengerToActivity.send(matrixCalc);
					} 
					catch (RemoteException e) {
						e.printStackTrace();
					}
					// ------------------------------------------------------
	            	break;
	            	case Meal.MEAL_ACTIVITY_INJECT:
	            	responseBundle = msg.getData();
	            	double mealSize = responseBundle.getDouble("mealSize", 0.0);
	            	double smbg = responseBundle.getDouble("smbg", 0.0);
	            	double mealBolus = responseBundle.getDouble("mealPart", 0.0);
	            	double corrBolus = responseBundle.getDouble("corrPart", 0.0);
	            	double credit = responseBundle.getDouble("credit", 0.0);
	            	double spend = responseBundle.getDouble("spend", 0.0);
	            	
	            	double bolusAmount = mealBolus + corrBolus + spend;
	            	
	            	Debug.i(TAG, FUNC_TAG, "Meal: "+ mealBolus + " Corr: " + corrBolus
	            						+ " Spend: " + spend + " Credit: " + credit
	            						+ " Bolus Amount: " + bolusAmount);
	            	
	            	// send the data to DiAs service for the injection
	            	Message sendBolus = Message.obtain(null, Meal.MCM_SEND_BOLUS);
	            	responseBundle = new Bundle();
	            	responseBundle.putDouble("mealSize", mealSize);
	            	responseBundle.putDouble("smbg", smbg);
	            	responseBundle.putDouble("meal", mealBolus);
	            	responseBundle.putDouble("corr", corrBolus);
	            	responseBundle.putDouble("credit", credit);
	            	responseBundle.putDouble("spend", spend);
	            	responseBundle.putBoolean("doesBolus", true);
	            	
	            	if (enableIOtest) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "MealActivity >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
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
	            	
					try {
						mMessengerToService.send(sendBolus);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
	            	break;
	            	
            	//MCM SERVICE COMMANDS
	            //-------------------------------------------------------------------------------------------------
				case Meal.MCM_SERVICE_CMD_REGISTER_CLIENT:
					Debug.i(TAG, FUNC_TAG, "MCM_SERVICE_CMD_REGISTER_CLIENT");
					if (enableIOtest) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DiAsService >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"MCM_SERVICE_CMD_REGISTER_CLIENT"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					mMessengerToService = msg.replyTo;
					break;
				default:
					if (enableIOtest) {
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
		return mMessengerFromService.getBinder();
	}
	
	public long getCurrentTimeSeconds() {
		final String FUNC_TAG = "getCurrentTimeSeconds";

		if (simulatedTime > 0) {
			Debug.i(TAG, FUNC_TAG, "getCurrentTimeSeconds > returning simulatedTime="+simulatedTime);
			return simulatedTime;			// simulatedTime passed to us by Application for development mode
		}
		else {
			return (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970		
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
	
	public void getSubjectWeight() {
		//boolean retValue = false;
		final String FUNC_TAG1 = "profileReceiver1";
		//Cursor c = getContentResolver().query(Biometrics.SUBJECT_DATA_URI, new String[]{"weight"}, null, null, null);
		Cursor c = getContentResolver().query(Biometrics.SUBJECT_DATA_URI, null, null, null, null);
		Log.i(TAG, "Retrieved SUBJECT_DATA_URI with " + c.getCount() + " items");
		if (c.moveToLast()) {
			// A database exists. Initialize subject_data.
			USER_WEIGHT = (c.getInt(c.getColumnIndex("weight")));
//			if (USER_WEIGHT>=27 && USER_WEIGHT<=136) {
//				retValue = true;
//			}
		}
//		return retValue;
		c.close();
	}

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

			
	

	public void fetchAllBiometricData() {
	       
		//	boolean return_value = false;		
		Tvec_cgm1.init();
		Tvec_insulin_rate1.init();
		Tvec_meal.init();
		Tvec_basal.init();
		
		try {
		
			// (1) Fetch CGM data
		    Cursor c=callingContext.getContentResolver().query(Biometrics.CGM_URI, null, null, null, null);

	        //debug_message(TAG,"CGM > c.getCount="+c.getCount());
			long last_time_temp_secs = 0;
			double cgm1_value;
			
			if (c.moveToFirst()) {
				do{
					// Fetch the cgm1 and cgm2 values so that they can be screened for validity
					cgm1_value = (double)c.getDouble(c.getColumnIndex("cgm"));
					// Make sure that cgm1_value is in the range of validity
					if (cgm1_value>=39.0 && cgm1_value<=401.0) {
						// Save the latest timestamp from the retrieved data
						if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
							last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
						}
						// time in seconds
						Tvec_cgm1.put(c.getLong(c.getColumnIndex("time"))/60, cgm1_value);  // min
						//return_value = true;
					}
				} while (c.moveToNext());
		    }						    
			c.close();
			//last_Tvec_cgm1_time_secs = last_time_temp_secs;			
			
			  debug_message(TAG, "Tvec_cgm1(0)"+Tvec_cgm1.get_value(0)+"Tvec_cgm1(end)"+Tvec_cgm1.get_last_value());

			
			
			// (2) Fetch INSULIN data
			//c=getContentResolver().query(INSULIN_URI, null, last_Tvec_insulin_rate1_time_secs.toString(), null, null);
			c=callingContext.getContentResolver().query(Biometrics.INSULIN_URI, null, null, null, null);

			//debug_message(TAG,"INSULIN > c.getCount="+c.getCount());
			last_time_temp_secs = 0;
			double insulin_value;
			
			if (c.moveToFirst()) {
				do {
					insulin_value=(double)c.getDouble(c.getColumnIndex("deliv_total"));
					if	(insulin_value>=0) {		
					// Save the latest timestamp from the retrieved data
						if (c.getLong(c.getColumnIndex("deliv_time")) > last_time_temp_secs) {
							last_time_temp_secs = c.getLong(c.getColumnIndex("deliv_time"));
						}
					// Round incoming time in seconds down to the nearest minute
						Tvec_insulin_rate1.put(c.getLong(c.getColumnIndex("deliv_time"))/60, insulin_value*1000); // min 
					}
				 } while(c.moveToNext());		    
			}	
			c.close();
			//last_Tvec_insulin_rate1_time_secs = last_time_temp_secs;			

			debug_message(TAG, "Tvec_insulin_rate1(0)"+Tvec_insulin_rate1(0)+"Tvec_insulin_rate1(end)"+Tvec_insulin_rate1.get_last_value());


			
			// (3) Fetch MEAL data
			//c=getContentResolver().query(MEAL_URI, null, Time.toString(), null, null);
			c=callingContext.getContentResolver().query(Biometrics.MEAL_URI, null, null, null, null);

	        //debug_message(TAG,"MEAL > c.getCount="+c.getCount());
			double meal_value;
			last_time_temp_secs = 0;
			if (c.moveToFirst()) {
				do{
					meal_value = (double)c.getDouble(c.getColumnIndex("meal_size_grams"));
	                if (meal_value>=0) {
	                	if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
							last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
						}
					// Round incoming time in seconds down to the nearest minute
					Tvec_meal.put(c.getLong(c.getColumnIndex("time"))/60, meal_value*1000); // min       	
	                }				
				} while (c.moveToNext());

		    }
			c.close();
			//last_Tvec_meal_time_secs = last_time_temp_secs;
			
			  debug_message(TAG, "Tvec_meal(0)"+Tvec_meal.get_value(0)+"Tvec_meal(end)"+Tvec_meal.get_last_value());


			
			// (4) Fetch basal profile data		
		    c = callingContext.getContentResolver().query(Biometrics.BASAL_PROFILE_URI, null, null, null, null);
		    
		    double basal_value;
			last_time_temp_secs = 0;
			//Log.i(TAG, "Retrieved BASAL_URI with " + c.getCount() + " items");
		    if (c.moveToFirst()) {
			   do{
				  basal_value = (double)c.getDouble(c.getColumnIndex("value")); 
				  
				  if(basal_value>=0){
					  if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
							last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
						}				  				  
				  Tvec_basal.put(c.getLong(c.getColumnIndex("time")), basal_value*1000/60);  //min mU/min
				  }      
			      //return_value = true;
			   }while (c.moveToNext());
		    }	
		    c.close();
		    
			 debug_message(TAG, "Tvec_basal(0)"+Tvec_basal.get_value(0)+"Tvec_basal(end)"+Tvec_basal.get_last_value());
	  	 
			
		  }
	       catch (Exception e) {
	  		 Log.e("Error Fetch data", e.getMessage());
	      }
	  }	
	
	public void getCalibration() {
		 
	    Tvec_calibration.init();
	    Cursor c=getContentResolver().query(Biometrics.SMBG_URI, null, null, null, null);

		double calibration_value;
	    if (c.moveToFirst()) {
			do{
				if ( (currentTimeseconds -c.getLong(c.getColumnIndex("time")))<=43200 ) {
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
	
	private void debug_message(String tag, String message) {
		if (DEBUG) {
			Log.i(tag, message);
		}
	}
		
}