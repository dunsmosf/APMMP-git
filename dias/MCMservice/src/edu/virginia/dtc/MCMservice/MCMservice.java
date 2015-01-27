package edu.virginia.dtc.MCMservice;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Meal;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.Tvector.Tvector;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.Gravity;
import android.widget.Toast;


public class MCMservice extends Service{

	public static final String TAG = "MCMservice";
    public static final String IO_TEST_TAG = "MCMserviceIO";
    
	public int cycle_duration_seconds = 300;
	public int cycle_duration_mins = cycle_duration_seconds/60;
	double latestCR = 0;
	double latestCF = 0;
	
	private BroadcastReceiver mealActivity = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent) 
		{
			Debug.i(TAG, "mealActivity", "Receiver called to start Meal Activity...");
			Intent ui = new Intent();
			ui.setClassName("edu.virginia.dtc.MCMservice", "edu.virginia.dtc.MCMservice.MealActivity");
			ui.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			context.startActivity(ui);
		}
	};
	
	// Messengers for sending responses to DiAsService
    public Messenger mMessengerToService = null;	// DiAsService
    
    final Messenger mMessengerFromService = new Messenger(new IncomingHandler());
    class IncomingHandler extends Handler 
    {
		final String FUNC_TAG = "messengerFromDiAsService";

    	Bundle responseBundle;
    	
    	@Override
        public void handleMessage(Message msg) {
            switch (msg.what) 
            {
            	//MEAL ACTIVITY COMMANDS
            	//-------------------------------------------------------------------------------------------------
	            case Meal.MEAL_ACTIVITY_REGISTER:
	            	Debug.i(TAG, FUNC_TAG, "MEAL_ACTIVITY_REGISTER");
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "MealActivity >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"MEAL_ACTIVITY_REGISTER"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
					//Send message to DiAs Service that Meal Activity has started
					Message reg = Message.obtain(null, Meal.MCM_STARTED);
					try {
						mMessengerToService.send(reg);
					} catch (RemoteException e) {
						e.printStackTrace();
					}
	            	break;
	            case Meal.MEAL_ACTIVITY_CALCULATE:
	            	responseBundle = msg.getData();
	            	
	            	double CHOg = responseBundle.getDouble("MealScreenCHO");
	            	double SMBG = responseBundle.getDouble("MealScreenBG");
	            	
	            	Debug.i(TAG, FUNC_TAG, "CHO: "+CHOg+" SMBG: "+SMBG);
	            	
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "MealActivity >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"MEAL_ACTIVITY_CALCULATE"+", "+
                						"MealScreenCHO="+CHOg+", "+
                						"MealScreenBG="+SMBG
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
	            	
	            	// DO CALCULATION HERE !!!  (THIS IS JUST SAMPLE CODE NOT INTENDED FOR USE!!!)
	            	// ------------------------------------------------------
					double bolus;
					int MCM_status;
					String MCM_description;
					subject_parameters();
					bolus = Math.max(CHOg/latestCR+(SMBG-110.0)/latestCF, 0.0);					
					
					MCM_status = 0;
					MCM_description = "Test";
					
					Message mealCalc = Message.obtain(null, Meal.MCM_CALCULATED_BOLUS, 0, 0);
					responseBundle = new Bundle();
					responseBundle.putDouble("bolus", bolus);
					responseBundle.putInt("MCM_status", MCM_status);
					responseBundle.putString("MCM_description", MCM_description);

					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "(MCMservice) >> DiAsService, IO_TEST"+", "+FUNC_TAG+", "+
                						"MCM_CALCULATED_BOLUS"+", "+
                						"bolus="+bolus+", "+
                						"MCM_status="+MCM_status+", "+
                						"MCM_description="+MCM_description
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
     				mealCalc.setData(responseBundle);
     				
//					try {
//						mMessengerToActivity.send(mealCalc);
//					} 
//					catch (RemoteException e) {
//						e.printStackTrace();
//					}
					// ------------------------------------------------------
	            	break;
	            case Meal.MEAL_ACTIVITY_INJECT:
	            	//Open loop boluses will have meal and corr insulin, closed loop will have credit and spend
	            	//THEY SHOULD NEVER HAVE BOTH IN THIS CONFIGURATION!
	            	
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
					if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "DiAsService >> (MCMservice), IO_TEST"+", "+FUNC_TAG+", "+
                						"MCM_SERVICE_CMD_REGISTER_CLIENT"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
					mMessengerToService = msg.replyTo;
					break;
				case Meal.MCM_UI:
					//Forward message to the UI
					Bundle ui = msg.getData();
					if(ui.getBoolean("end", false))
					{
						Debug.i(TAG, FUNC_TAG, "User canceled the Meal!");
						try {
							mMessengerToService.send(msg);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
					else
					{
//						Debug.i(TAG, FUNC_TAG, "Passing message to UI!");
//						try {
//							mMessengerToActivity.send(msg);
//						} 
//						catch (RemoteException e) {
//							e.printStackTrace();
//						}
					}
					break;
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
	
	public void onCreate()
	{
		super.onCreate();
		
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
        Intent notificationIntent = new Intent(this, MCMservice.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        final int MCM_ID = 100;
        
        // Make this a Foreground Service
        startForeground(MCM_ID, notification);
        
        this.registerReceiver(mealActivity, new IntentFilter("DiAs.MealActivity"));
	}
	
	@Override
	public IBinder onBind(Intent intent) 
	{
		return mMessengerFromService.getBinder();
	}
	
	public void subject_parameters() 
	{
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
	
	public long getCurrentTimeSeconds() 
	{
		final String FUNC_TAG = "getCurrentTimeSeconds";
		return (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970		
	}
	
	public void log_action(String service, String action) 
	{
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
	}
	
	public void log_IO(String tag, String message) 
	{
		final String FUNC_TAG = "log_IO";
		Debug.i(tag, FUNC_TAG, message);
	}
}
