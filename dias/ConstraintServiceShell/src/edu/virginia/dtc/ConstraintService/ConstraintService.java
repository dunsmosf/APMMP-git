package edu.virginia.dtc.ConstraintService;

import edu.virginia.dtc.ConstraintService.R;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Constraints;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

/***************************************************************************
* This is a shell for the ConstraintService application.  It contains
* listeners for the 1 and 5 minute system-wide timer ticks.  These are the
* entry points for the developer supplied "ProcessConstraints" function.
* "ProcessConstraints" contains examples of reading from the database, that
* can be used to read from other database tables.  It also contains the code
* for writing to the constraints table.  This table can be read in the 
* Safety Service Shell via a function call "ReadConstraints", which is 
* currently inactive.  The calls to "ProcessConstraints" are currently
* commented to prevent needless database accesses.
***************************************************************************/

public class ConstraintService extends Service{

	private static final String TAG = "ConstraintService";
	private static final boolean DEBUG = true;
    public static final String IO_TEST_TAG = "ConstraintServiceIO";
	
	private static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
	
	private PowerManager pm;
	private WakeLock wl;
	
	private SystemObserver sysObserver;
	private ConstraintsObserver constraintsObserver;
	
	@Override
	public void onCreate() {
		final String FUNC_TAG = "onCreate";

		super.onCreate();
		
		log(Debug.VERBOSE, FUNC_TAG, "onCreate");
		
		//System wide Broadcast that runs every minute
		BroadcastReceiver Tick = new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent)
			{
				// Currently does nothing
			}
		};
		registerReceiver(Tick, new IntentFilter("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK"));
		
		//System wide Broadcast every 5 minutes
		BroadcastReceiver AlgorithmTick = new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent)
			{
			}
		};
		registerReceiver(AlgorithmTick, new IntentFilter("edu.virginia.dtc.intent.action.SUPERVISOR_CONTROL_ALGORITHM_TICK"));
		
		// Set up a Notification for this Service
    	String ns = Context.NOTIFICATION_SERVICE;
    	NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
    	int icon = R.drawable.ic_launcher;
    	CharSequence tickerText = "Constraint Service v1.0";
    	long when = System.currentTimeMillis();
    	Notification notification = new Notification(icon, tickerText, when);
    	Context context = getApplicationContext();
    	CharSequence contentTitle = "Constraint Service v1.0";
    	CharSequence contentText = "Constraints";
    	Intent notificationIntent = new Intent(this, ConstraintService.class);
    	PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
    	notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
    	final int CONSTRAINT_ID = 50;
//    	mNotificationManager.notify(CONSTRAINT_ID, notification);
    	
    	// Make this a Foreground Service
    	startForeground(CONSTRAINT_ID, notification);
    	
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();
		
		constraintsObserver = new ConstraintsObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.CONSTRAINTS_URI, true, constraintsObserver);
		
		// You may uncomment the sysObserver if the ConstraintService needs to handle changes to the System state 
//		sysObserver = new SystemObserver(new Handler());
//		getContentResolver().registerContentObserver(Biometrics.SYSTEM_URI, true, sysObserver);
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		
		if(constraintsObserver != null)
			getContentResolver().unregisterContentObserver(constraintsObserver);
		
//		if(sysObserver != null)
//			getContentResolver().unregisterContentObserver(sysObserver);
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	//-------------------------------------------------------------------------------
	//Constraint Processing Function
	//-------------------------------------------------------------------------------
	private void ProcessConstraints(int constraints_row_id)
	{
		final String FUNC_TAG = "ProcessConstraints";
		Debug.i(TAG, FUNC_TAG, "> Start ProcessConstraints()");

		log(Debug.VERBOSE, FUNC_TAG, "Processing constraints...");
		
		//***************************************************
		//Read from Database using any of the input URIs
		//These are shown here as examples
		//***************************************************
		readCgmDb();
		readInsulinDb();
		readSmbgDb();
		
		
		//***************************************************
		//Do whatever processing you need to here
		//***************************************************
		
		
		//*********************************************
		//Write output to Constraints Table in Database
		//*********************************************
		ContentValues writeValues = new ContentValues();
		writeValues.put("time", getTime());
		writeValues.put("status", Constraints.CONSTRAINT_WRITTEN);		// status CONSTRAINT_WRITTEN indicates successful completion
		
		//These are EXAMPLES, there are 20 columns "constraint1" to "constraint20"
		
		writeValues.put("constraint1", 0.2);
		writeValues.put("constraint2", 2.0);
		writeValues.put("constraint3", 3.0);
		writeValues.put("constraint4", 4.0);
		writeValues.put("constraint5", 5.0);
		
		// Log the constraints for IO testing
		if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
			storeUserTable1Data();						// Attempt to write unprotected table - verify success message from biometricsContentProvider
			storeUserTable2Data();						// Attempt to write protected table - verify error message from biometricsContentProvider
		}
		
		Cursor constraintCursor;
		try {
			//Write values to database
			getContentResolver().update(Biometrics.CONSTRAINTS_URI, writeValues, "_id="+constraints_row_id, null);
			Debug.i(TAG, FUNC_TAG, "> update");
		}
		catch (Exception e) {
			log(Debug.ERROR, FUNC_TAG, "Error writing values to constraints table:  "+e.getMessage());
		}
			
		// If the Contraints table has more than 10 rows then delete the oldest row
		Cursor readDb = getContentResolver().query(Biometrics.CONSTRAINTS_URI, null, null, null, null);
		Debug.i(TAG, FUNC_TAG, "> count="+readDb.getCount());
		if (readDb.getCount() > 10) {
			try {
				readDb.moveToFirst();
				int _id = readDb.getInt(readDb.getColumnIndex("_id"));
				getContentResolver().delete(Biometrics.CONSTRAINTS_URI, "_id="+_id, null);
				Debug.i(TAG, FUNC_TAG, "> delete: _id="+_id);
			}
			catch (Exception e) {
				log(Debug.ERROR, FUNC_TAG, "Error deleting constraints table:  "+e.getMessage());
			}
		}
	}
	
	//-------------------------------------------------------------------------------
	// Database Observer Methods
	//-------------------------------------------------------------------------------
	
	class ConstraintsObserver extends ContentObserver 
    {	
    	private int count;
    	
    	public ConstraintsObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "Constraints Observer";
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
    	   
    	   if(selfChange)			//We don't trigger on the updates we make to the Constraint Table
    		   return;
    	   
    	   count++;
    	   Debug.i(TAG, FUNC_TAG, "Constraints Observer: "+count);
    	   
    	   Cursor c = getContentResolver().query(Biometrics.CONSTRAINTS_URI, null, null, null, null);
    	   
    	   if(c!=null)
    	   {
    		   if(c.moveToLast())
    		   {
    			   long time = c.getLong(c.getColumnIndex("time"));
    			   int _id = c.getInt(c.getColumnIndex("_id"));
    			   int status = c.getInt(c.getColumnIndex("status"));
    			   
    			   if(status == Constraints.CONSTRAINT_REQUESTED)		//We will use a status value CONSTRAINT_REQUESTED to indicate a request from DiAs Service for constraint calculation
    			   {
    				   //*******************************************
    				   //Developer code runs from this function call
    				   //*******************************************
    				   ProcessConstraints(_id);
    			   }
    		   }
    	   }
    	   c.close();
       }		
    }
    
	class SystemObserver extends ContentObserver 
    {	
    	private int count;
    	
    	public SystemObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "System Observer";
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
    	   Debug.i(TAG, FUNC_TAG, "System Observer: "+count);
    	   
    	   Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, null, null, null, null);
       	
	       	if(c!=null)
	       	{
	       		if(c.moveToLast())
	       		{
	       			//You may use any of these you like, this function is called as there are updates to the table (and thus the values)
	       			
	       			long time = c.getLong(c.getColumnIndex("time"));
	       	    	long sysTime = c.getLong(c.getColumnIndex("sysTime"));
	       	    	
	       	    	int diasState = c.getInt(c.getColumnIndex("diasState"));
	       	    	int battery = c.getInt(c.getColumnIndex("battery"));
	       	    	
	       	    	boolean safetyMode = (c.getInt(c.getColumnIndex("safetyMode"))==1) ? true : false;
	       	    	
	       	    	double cgmValue = c.getDouble(c.getColumnIndex("cgmValue"));
	       	    	int cgmTrend = c.getInt(c.getColumnIndex("cgmTrend"));
	       	    	long cgmLastTime = c.getLong(c.getColumnIndex("cgmLastTime"));
	       	    	int cgmState = c.getInt(c.getColumnIndex("cgmState"));
	       	    	String cgmStatus = c.getString(c.getColumnIndex("cgmStatus"));
	       	    	
	       	    	double pumpLastBolus = c.getDouble(c.getColumnIndex("pumpLastBolus"));
	       	    	long pumpLastBolusTime = c.getLong(c.getColumnIndex("pumpLastBolusTime"));
	       	    	int pumpState = c.getInt(c.getColumnIndex("pumpState"));
	       	    	String pumpStatus = c.getString(c.getColumnIndex("pumpStatus"));
	       	    	
	       	    	double iobValue = c.getDouble(c.getColumnIndex("iobValue"));
	       	    	
	       	    	String hypoLight = c.getString(c.getColumnIndex("hypoLight"));
	       	    	String hyperLight = c.getString(c.getColumnIndex("hyperLight"));
	       	    	
	       	    	double apcBolus = c.getDouble(c.getColumnIndex("apcBolus"));
	       	    	int apcStatus = c.getInt(c.getColumnIndex("apcStatus"));
	       	    	int apcType = c.getInt(c.getColumnIndex("apcType"));
	       	    	String apcString = c.getString(c.getColumnIndex("apcString"));
	       	    	
	       	    	boolean exercising = (c.getInt(c.getColumnIndex("exercising"))==1) ? true : false;
	       	    	boolean alarmNoCgm = (c.getInt(c.getColumnIndex("alarmNoCgm"))==1) ? true : false;
	       	    	boolean alarmHypo = (c.getInt(c.getColumnIndex("alarmHypo"))==1) ? true : false;
	       		}
	       	}
	       	c.close();
       }		
    }
	
	//-------------------------------------------------------------------------------
	// Database Read/Write Methods
	//-------------------------------------------------------------------------------
	
	private void readCgmDb()
	{
		final String FUNC_TAG = "readCgmDb";

		Cursor readDb = getContentResolver().query(Biometrics.CGM_URI, null, null, null, null);
		log(Debug.VERBOSE, FUNC_TAG, "Querying "+Biometrics.CGM_URI.toString());
		long time = 0;
		double cgm1 = 0.0;
		int state = 0;
		
		if(Biometrics.CGM_URI != Uri.EMPTY)
		{
			if(readDb.moveToFirst())
			{
				do{
					//Reads all entries in the table
					time = readDb.getLong(readDb.getColumnIndex("time"));
					cgm1 = readDb.getDouble(readDb.getColumnIndex("cgm"));
					state = readDb.getInt(readDb.getColumnIndex("state"));
					
					log(Debug.VERBOSE, FUNC_TAG, "Time: "+time+" CGM: "+cgm1+" State: "+state);
				} while(readDb.moveToNext());
			}
			else
				log(Debug.DEBUG, FUNC_TAG, "Table is empty!");
			
			readDb.close();
			
			// Log the latest CGM read value for IO testing
			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        		Bundle b = new Bundle();
        		b.putString(	"description", "Database >> ConstraintService, IO_TEST"+", "+FUNC_TAG+", "+
        						"time="+time+", cgm="+cgm1);
        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
			}
		}
		else
			log(Debug.DEBUG, FUNC_TAG, "The URI is empty, please set it to a valid URI!");
	}
	
	private int readInsulinDb()
	{
		final String FUNC_TAG = "readInsulinDb";
		int retValue = 0;

		Cursor readDb = getContentResolver().query(Biometrics.INSULIN_URI, null, null, null, null);
		retValue = readDb.getCount();
		log(Debug.VERBOSE, FUNC_TAG, "Querying "+Biometrics.INSULIN_URI.toString());
		long time = 0;
		double basal=0.0, corr=0.0, meal=0.0;
		
		if(Biometrics.INSULIN_URI != Uri.EMPTY)
		{
			if(readDb.moveToFirst())
			{
				do{
					//Reads all entries in the table
					time = readDb.getLong(readDb.getColumnIndex("deliv_time"));
					basal = readDb.getDouble(readDb.getColumnIndex("deliv_basal"));
					corr = readDb.getDouble(readDb.getColumnIndex("deliv_corr"));
					meal = readDb.getDouble(readDb.getColumnIndex("deliv_meal"));
					
					log(Debug.VERBOSE, FUNC_TAG, "Time: "+time+" Basal: "+basal+" Meal: "+meal+" Corr: "+corr);
				} while(readDb.moveToNext());
			}
			else
				log(Debug.DEBUG, FUNC_TAG, "Table is empty!");
			
			readDb.close();
			
			// Log the latest insulin read value for IO testing
			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        		Bundle b = new Bundle();
        		b.putString(	"description", "Database >> ConstraintService, IO_TEST"+", "+FUNC_TAG+", "+
        						"time="+time+", basal="+basal+", corr="+corr+", meal="+meal);
        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
			}
		}
		else
			log(Debug.DEBUG, FUNC_TAG, "The URI is empty, please set it to a valid URI!");
		return retValue;
	}
	
	private int readSmbgDb() //
	{
		final String FUNC_TAG = "readSmbgDb";
		int retValue = 0;

		Cursor readDb = getContentResolver().query(Biometrics.SMBG_URI, null, null, null, null);
		retValue = readDb.getCount();
		log(Debug.VERBOSE, FUNC_TAG, "Querying "+Biometrics.SMBG_URI.toString());
		long time = 0;
		double value = 0.0;
		boolean cal = false;
		
		if(Biometrics.SMBG_URI != Uri.EMPTY)
		{
			if(readDb.moveToFirst())
			{
				do{
					//Reads all entries in the table
					time = readDb.getLong(readDb.getColumnIndex("time"));
					value = readDb.getDouble(readDb.getColumnIndex("smbg"));
					cal = readDb.getInt(readDb.getColumnIndex("isCalibration")) == 1;
					
					log(Debug.VERBOSE, FUNC_TAG, "Time: "+time+" Value: "+value+", is Calibration: "+cal);
				} while(readDb.moveToNext());
			}
			else
				log(Debug.DEBUG, FUNC_TAG, "Table is empty!");
			
			readDb.close();
			
			// Log the latest smbg read value for IO testing
			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        		Bundle b = new Bundle();
        		b.putString(	"description", "Database >> ConstraintService, IO_TEST"+", "+FUNC_TAG+", "+
        						"time="+time+", value="+value+", is Calibration="+cal);
        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
			}
		}
		else
			log(Debug.DEBUG, FUNC_TAG, "The URI is empty, please set it to a valid URI!");
		return retValue;
	}
	
	public void storeUserTable1Data() {
		final String FUNC_TAG = "storeUserTable1Data";

	  	ContentValues values = new ContentValues();
	  	double d0 = 1.0;
	  	double d1 = 3.141592654;
       	values.put("d0", d0);
       	values.put("d1", d1);
       	Uri uri;
       	try {
       		uri = getContentResolver().insert(Biometrics.USER_TABLE_1_URI, values);
			Debug.i(TAG, FUNC_TAG, "storeUserTable1Data > d0="+d0+", d1="+d1);
			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        		Bundle b = new Bundle();
				if (uri!=null) {
	        		b.putString("description", "ConstraintService >> Database, IO_TEST"+", "+FUNC_TAG+", "+
	        					"USER_TABLE_1 Write SUCCESS");
				} else {
	        		b.putString("description", "ConstraintService >> Database, IO_TEST"+", "+FUNC_TAG+", "+
	        					"USER_TABLE_1 Write FAIL (write protection)");
				}
        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
			}
       	}
       	catch (Exception e) {
       		Debug.e(TAG, FUNC_TAG, e.getMessage());
       	}		
	}
	
	public void storeUserTable2Data() {
		final String FUNC_TAG = "storeUserTable2Data";

	  	ContentValues values = new ContentValues();
	  	double d0 = 1.0;
	  	double d1 = 3.141592654;
       	values.put("d0", d0);
       	values.put("d1", d1);
       	Uri uri;
       	try {
       		uri = getContentResolver().insert(Biometrics.USER_TABLE_2_URI, values);
			Debug.i(TAG, FUNC_TAG, "storeUserTable2Data > d0="+d0+", d1="+d1);
			if (Params.getBoolean(getContentResolver(), "enableIO", false)) {
        		Bundle b = new Bundle();
				if (uri!=null) {
	        		b.putString("description", "ConstraintService >> Database, IO_TEST"+", "+FUNC_TAG+", "+
	        					"USER_TABLE_2 Write SUCCESS");
				} else {
	        		b.putString("description", "ConstraintService >> Database, IO_TEST"+", "+FUNC_TAG+", "+
	        					"USER_TABLE_2 Write FAIL (write protection)");
				}
        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
			}
       	}
       	catch (Exception e) {
       		Debug.e(TAG, FUNC_TAG, e.getMessage());
       	}		
	}
	
	//-------------------------------------------------------------------------------
	//Utility Functions
	//-------------------------------------------------------------------------------
	private long getTime()
	{
		return System.currentTimeMillis()/1000;
	}
	
//	public void log_IO(String tag, String message) {
//		Debug.i(tag, FUNC_TAG, message);
//		/*
//		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
//        i.putExtra("Service", tag);
//        i.putExtra("Status", message);
//        i.putExtra("time", (long)getCurrentTimeSeconds());
//        sendBroadcast(i);
//        */
//	}
	
	private void log(int verbosity, String FUNC_TAG, String message)
	{
		if(DEBUG)
		{
			switch(verbosity)
			{
				case Debug.VERBOSE:
					Debug.v(TAG, FUNC_TAG, message);
					break;
				case Debug.DEBUG:
					Debug.d(TAG, FUNC_TAG, message);
					break;
				case Debug.WARN:
					Debug.w(TAG, FUNC_TAG, message);
					break;
				case Debug.ERROR:
					Debug.e(TAG, FUNC_TAG, message);
					break;
			}
		}
		
		//Add code to pipe this to file IO if necessary
	}
}
