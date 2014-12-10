//*********************************************************************************************************************
//  Copyright 2011-2012 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.DiAsUI;

import java.text.DecimalFormat;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.text.format.Time;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Mode;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.Safety;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.SysMan.TempBasal;
import edu.virginia.dtc.SysMan.Tvector;

public class DiAsMain extends Activity implements OnGestureListener {
	GestureDetector gestureScanner;
	
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
    public static final String IO_TEST_TAG = "DiAsMainIO";
    
	private static final String VERSION_IDENTIFIER = 
			"Diabetes Assistant v3.0\n" +
			"Build 11-22-2013\n" +
			"Copyright 2011-2013\n" +
			"University of Virginia\n" +
			"Center for Diabetes Technology";
	
	public final String TAG = "DiAsMain";
	
	/************************************************************************************************************************/
	//  System Statics and Constants
	/************************************************************************************************************************/

    // APController type
    private int APC_TYPE;
    public static final int APC_TYPE_HMS = 1;
    public static final int APC_TYPE_RCM = 2;
    
    // Define AP Controller behavior
    private int APC_MEAL_CONTROL;
    public static final int APC_NO_MEAL_CONTROL = 1;
    public static final int APC_WITH_MEAL_CONTROL = 2;
    
	// DiAsUI State variable and definitions of major display states
	public static final int DIAS_UI_STATE_MAIN = 0;
	public static final int DIAS_UI_STATE_PLOTS = 17;
	public static final int DIAS_UI_STATE_BOLUS_INTERCEPTOR = 18;
	
	// Commands from DiAsService
	public static final int DIASMAIN_UI_NULL = 0;
	public static final int DIASMAIN_UI_INIT = 1;
	public static final int DIASMAIN_UI_UPDATE_STATUS_LIGHTS = 2;
	public static final int DIASMAIN_UI_UPDATE_STATUS_MESSAGE = 3;
	public static final int DIASMAIN_UI_DISPLAY_CORRECTION_BOLUS_MESSAGE = 4;
//	public static final int DIASMAIN_UI_DISPLAY_BOLUS_INTERCEPTOR_MESSAGE = 5;
	public static final int DIASMAIN_UI_UPDATE_TRAFFIC_LIGHTS = 6;
	public static final int DIASMAIN_UI_UPDATE_DIAS_STATE = 7;
	public static final int DIASMAIN_UI_UPDATE_MEAL_BOLUS_FAILED = 9;
//	public static final int DIASMAIN_UI_CLEAR_BOLUS_INTERCEPTOR_MESSAGE = 10;
	public static final int DIASMAIN_UI_APC_RETURN_BOLUS = 13;
	public static final int DIASMAIN_UI_CONNECTIVITY = 14;
	
	// DiAsService Commands
	public static final int DIAS_SERVICE_COMMAND_NULL = 0;
	public static final int DIAS_SERVICE_COMMAND_STOP_AUDIBLE_ALARM = 3;
	public static final int DIAS_SERVICE_COMMAND_SET_EXERCISE_STATE = 4;
	public static final int DIAS_SERVICE_COMMAND_SET_HYPO_FLAG_TIME = 6;
//	public static final int DIAS_SERVICE_COMMAND_CANCEL_BOLUS = 8;
//	public static final int DIAS_SERVICE_COMMAND_CONFIRM_BOLUS = 9;
	public static final int DIAS_SERVICE_COMMAND_SEND_MEAL_BOLUS = 10;
	public static final int DIAS_SERVICE_COMMAND_START_CLOSED_LOOP_CLICK = 11;
	public static final int DIAS_SERVICE_COMMAND_STOP_CLICK = 12;
	public static final int DIAS_SERVICE_COMMAND_START_OPEN_LOOP_CLICK = 13;
	public static final int DIAS_SERVICE_COMMAND_RELOAD_SAFETY_SERVICE_PROFILES = 14;
	public static final int DIAS_SERVICE_COMMAND_RELOAD_HMS_PROFILES = 15;
	public static final int DIAS_SERVICE_COMMAND_SENSOR_CALIBRATION_COMPLETE = 17;
	public static final int DIAS_SERVICE_COMMAND_SET_MINS_TO_NEXT_CALIBRATION = 18;
	public static final int DIAS_SERVICE_COMMAND_SET_INSULIN_UNITS_REMAINING = 19;
//	public static final int DIAS_SERVICE_COMMAND_APC_CALCULATE_BOLUS = 23;
	public static final int DIAS_SERVICE_COMMAND_START_OPEN_LOOP_STOP_ALARM = 24;
	public static final int DIAS_SERVICE_COMMAND_INITIALIZE = 42;
	public static final int DIAS_SERVICE_COMMAND_EXIT = -978;
	public static final int DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK = 25;
	public static final int DIAS_SERVICE_COMMAND_START_SAFETY_CLICK = 26;
	public static final int DIAS_SERVICE_COMMAND_RECOVERY = 50;
	
    // safetyService status values
    public static final int SAFETY_SERVICE_STATE_NORMAL = 0;
    public static final int SAFETY_SERVICE_STATE_NOT_ENOUGH_DATA = 1;
    public static final int SAFETY_SERVICE_STATE_CREDIT_REQUEST = 2;
    public static final int SAFETY_SERVICE_STATE_BOLUS_INTERCEPT = 3;
    public static final int SAFETY_SERVICE_STATE_AWAITING_PUMP_RESPONSE = 4;
    public static final int SAFETY_SERVICE_STATE_RETURN_STATUS = 5;
    public static final int SAFETY_SERVICE_STATE_PUMP_ERROR = -1;

    // Confirmation dialogs
 	public static final int DIALOG_CLOSED_LOOP_NO_CGM = 254;	
 	public static final int DIALOG_CONFIRM_STOP = 256;
 	public static final int DIALOG_NEW_SUBJECT_CONFIRM = 257;	
 	public static final int DIALOG_PASSWORD = 258;
 	public static final int DIALOG_CONFIRM_CONFIG = 259;
 	public static final int DIALOG_CONFIRM_EXERCISE = 260;
 	public static final int DIALOG_CONFIRM_HYPO_TREATMENT = 261;
 	public static final int DIALOG_CONFIRM_CALIBRATION = 262;
 	public static final int DIALOG_BEGIN_CALIBRATION = 263;
 	public static final int DIALOG_CONFIRM_CANCEL_TEMP_BASAL = 264;
 	
 	// Passworded button codes
 	public static int BUTTON_CURRENT;
 	public static final int BUTTON_NEW_SUBJECT = 0;
 	public static final int BUTTON_OPEN_LOOP = 1;
 	public static final int BUTTON_CLOSED_LOOP = 2;
 	public static final int BUTTON_HOME = 3;
 	public static final int BUTTON_SENSOR_ONLY = 4;
 	public static final int BUTTON_SAFETY = 5;
    
    // Activity Result IDs
    private static final int DEFAULT_MEAL = 1;
    private static final int PLOTS = 2;
    private static final int ALARM = 3;
    private static final int SMBG = 4;
    private static final int TEMP_BASAL = 5;
    
    // Alarm Activity IDs
    private static final int HYPO_ALARM = 0;
    private static final int NO_CGM_ALARM = 1;
    
    // Misc Constants
    private final int BATTERY_SHUTDOWN_THRESHOLD = 10;
	
 	/************************************************************************************************************************/
	//  System Variables
	/************************************************************************************************************************/
	
    //GLOBALS		******************************************************************************
    
  	private long SIM_TIME;
  	private int DIAS_UI_STATE;
  	
  	private int DIAS_STATE, PREV_DIAS_STATE;
  	private int BATTERY, PREV_BATTERY;
  	private double CGM_VALUE, PREV_CGM_VALUE;
  	private int CGM_STATE, PREV_CGM_STATE;
  	private int PUMP_STATE, PREV_PUMP_STATE;
  	private boolean EXERCISING, PREV_EXERCISING;
  	private boolean TEMPORARY_BASAL_RATE;
  	private boolean ENABLE_IO, PREV_ENABLE_IO;
  	
  	private boolean PHONE_CALIB;
    
  	//VARIABLES		******************************************************************************
  	
	private final DiAsMain main = this;
	
	public BroadcastReceiver ServiceReceiver;				// Listens for information broadcasts from DiAsService
	private boolean ServiceReceiverIsRegistered = false;
	
	public BroadcastReceiver TickReceiver;					// Listens for Time Tick broadcasts from DiAsService
	private boolean TickReceiverIsRegistered = false;

	public boolean noCgmInClosedLoopAlarmPlaying;
	
	// No CGM Watchdog timer
	private Timer NoCgmWatchdogTimer;
	
	private TimerTask NoCgmWatchdogTimerTask;
	private static double NO_CGM_WATCHDOG_TIMEOUT_SECONDS = 300;
	
	// Correction bolus dialog
	AlertDialog correctionBolusDialog = null;
	String correctionBolusMessage = "The system plans to inject...";
	public static final int DIALOG_CORRECTION_BOLUS = 16;
	
	// Credit confirmation dialog
	private double creditBolusMax = 0.0;
	
	// Used in dialogs
	public TextView textViewPassword;
	public EditText editTextPassword;
	
	private boolean alarmActivityRunning = false;
	
	// CGM data gap constants
	//TODO: fix these calibrated and other connection booleans to be from states
	private boolean insulinSetupComplete = false;
	
	private int midFrameW, midFrameH;
	
	private SystemObserver sysObserver;
	private CgmDetailsObserver cgmObserver;
	
	/************************************************************************************************************************/
	//  Overridden Activity Functions
	/************************************************************************************************************************/
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	final String FUNC_TAG = "onCreate";
        super.onCreate(savedInstanceState);
       
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.ui_main);
        
        gestureScanner = new GestureDetector(this);
        
        Debug.i(TAG, FUNC_TAG, "My UID="+android.os.Process.myUid());
             				
        ServiceReceiverIsRegistered = false;
        TickReceiverIsRegistered = false;
        noCgmInClosedLoopAlarmPlaying = false;
        alarmActivityRunning = false;
        
        APC_TYPE = APC_TYPE_HMS;
        APC_MEAL_CONTROL = APC_NO_MEAL_CONTROL;
        TEMPORARY_BASAL_RATE = false;			
		
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		// Keep the CPU running even after the screen dims
//		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
//		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
//		wl.acquire();  
		
		// This is the main method of UI update now, it listens to changes on the SYSTEM table
		sysObserver = new SystemObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.SYSTEM_URI, true, sysObserver);
		
		cgmObserver = new CgmDetailsObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.CGM_DETAILS_URI, true, cgmObserver);
		
        registerReceivers();
        
        if (!isMyServiceRunning()) 
   	    {
            initTrafficLights();	// If DiAsService not running Initialize the traffic lights
            initCGMMessage();		// Initialize the CGM message
        }
   	    else 
   	    	update();
   	    
        if(main.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
        {
	        // Initialize the correction bolus message
//	        initCorrectionBolusMessage();
	        
	        // Don't initially show the bolus interceptor messages and buttons
//	        hideBolusInterceptorMessage();
	        
		 	updateDiasMainState(DIAS_UI_STATE_MAIN);
        }
        
        final View tv = ((LinearLayout)this.findViewById(R.id.linearMid));
        ViewTreeObserver vto = tv.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {

            public void onGlobalLayout() {
                ViewTreeObserver obs = tv.getViewTreeObserver();
                
                midFrameW = tv.getMeasuredWidth();
        		midFrameW += (0.07*midFrameW);
        		midFrameH = tv.getMeasuredHeight();
        		midFrameH += (0.07*midFrameH);
        		
        		Debug.i(TAG, FUNC_TAG, "MID FRAME WIDTH "+midFrameW+" MID FRAME HEIGHT "+midFrameH);
                
                obs.removeGlobalOnLayoutListener(this);
            }
        });
        
        
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        final String FUNC_TAG = "onConfigurationChanged";
        Debug.i(TAG, FUNC_TAG, "");
        
        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) 
        {
            Debug.i(TAG, FUNC_TAG, "Landscape");
            setContentView(R.layout.ui_main);
            
//	        initCorrectionBolusMessage();
//	        hideBolusInterceptorMessage();
		 	updateDiasMainState(DIAS_UI_STATE_MAIN);
		 	update();
        } 
        else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            Debug.i(TAG, FUNC_TAG, "Portrait");
        }
    }
    
    @Override
    protected void onStart() {
    	final String FUNC_TAG = "onStart";
        super.onStart();
        Debug.i(TAG, FUNC_TAG, "");
       
        update();
		
		Cursor c = getContentResolver().query(Biometrics.SUBJECT_DATA_URI, new String[]{"insulinSetupComplete"}, null, null, null);
		if (c.moveToFirst())
			insulinSetupComplete = c.getInt(c.getColumnIndex("insulinSetupComplete")) == 1;
		else
			insulinSetupComplete = false;
		
		c.close();
    }    
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
    	final String FUNC_TAG = "onWindowFocusChanged";
    	super.onWindowFocusChanged(hasFocus);
    	
    	if(hasFocus)
    	{
//    		//Gather Middle frame height and width (and for some reason add a little more?)
//    		midFrameW = ((LinearLayout)this.findViewById(R.id.linearMid)).getMeasuredWidth();
//    		midFrameW += (0.07*midFrameW);
//    		midFrameH = ((LinearLayout)this.findViewById(R.id.linearMid)).getMeasuredHeight();
//    		midFrameH += (0.07*midFrameH);
//
//    		// Force parameters for Nexus 5
//    		//midFrameW = 1152;
//    		//midFrameH = 1075;
//
//    		Debug.i(TAG, FUNC_TAG, "MID FRAME WIDTH "+midFrameW+" MID FRAME HEIGHT "+midFrameH);
    	}
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) 
    {
    	final String FUNC_TAG = "onActivityResult";
    	
    	//Determine the activity that is returning a result
    	switch(requestCode)
    	{
	    	case DEFAULT_MEAL:
	    		Debug.i(TAG, FUNC_TAG, "DEFAULT_MEAL");
	    		updateDiasMainState(DIAS_UI_STATE_MAIN);
	    		break;
	    	case PLOTS:
	    		Debug.i(TAG, FUNC_TAG, "PLOTS");
	    		updateDiasMainState(DIAS_UI_STATE_MAIN);
	    		break;
	    	case ALARM:
	    		Debug.i(TAG, FUNC_TAG, "ALARM");
	    		if(resultCode == RESULT_OK)
	    		{
	    			int closingMode = data.getIntExtra("loopMode", State.DIAS_STATE_STOPPED);
	    			switch(closingMode)
	    			{
		    			case State.DIAS_STATE_STOPPED:
		    				Debug.i(TAG, FUNC_TAG, "ALARM > Mode: STOPPED");
		    				stopConfirm();
		    				break;
		    			case State.DIAS_STATE_OPEN_LOOP:
		    				Debug.i(TAG, FUNC_TAG, "ALARM > Mode: OPEN LOOP");
		    				openLoopConfirm();
		    				break;
	    			}
	    			cancelNoCgmWatchdogTimer();
	    		}
	    		alarmActivityRunning = false;
	    		updateDiasMainState(DIAS_UI_STATE_MAIN);
	    		break;
	    	case SMBG:
	    		updateDiasMainState(DIAS_UI_STATE_MAIN);
	    		break;
	    	case TEMP_BASAL:
	    		break;
    	}
    }
    
    @Override
    protected void onStop() 
    {
    	final String FUNC_TAG = "onStop";
    	super.onStop();
    	Debug.i(TAG, FUNC_TAG, "");
    }    
    
    @Override
    protected void onDestroy() 
    {
    	final String FUNC_TAG = "onDestroy";
		super.onDestroy();
		Debug.i(TAG, FUNC_TAG, "");
		
		if (ServiceReceiverIsRegistered) 
		{
			unregisterReceiver(ServiceReceiver);
			ServiceReceiverIsRegistered = false;
		}
		
		if (TickReceiverIsRegistered) 
		{
			unregisterReceiver(TickReceiver);
			TickReceiverIsRegistered = false;
		}
		
		if(sysObserver != null)
			getContentResolver().unregisterContentObserver(sysObserver);
		
		if(cgmObserver != null)
			getContentResolver().unregisterContentObserver(cgmObserver);
    }    
    
    @Override
    protected void onRestart() 
    {
    	final String FUNC_TAG = "onRestart";
        super.onRestart();
        Debug.i(TAG, FUNC_TAG, "");
    }
    
    @Override
    protected void onResume() 
    {
    	final String FUNC_TAG = "onResume";
        super.onResume();        
        
        Debug.i(TAG, FUNC_TAG, "DIAS UI on resume > "+DIAS_UI_STATE);
        
        if (!isMyServiceRunning()) 
        {
            initTrafficLights();	// If DiAsService not running Initialize the traffic lights
            initCGMMessage();		// Initialize the CGM message
        }
        else
            update();   
    }
    
    @Override
    protected void onPause() {
    	final String FUNC_TAG = "onPause";
        super.onPause();
        Debug.i(TAG, FUNC_TAG, "");
    }
    
    /************************************************************************************************************************/
	//  Gesture Listeners
	/************************************************************************************************************************/
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureScanner.onTouchEvent(event);
    }

    public boolean onDown(MotionEvent e) {
    	final String FUNC_TAG = "onDown";
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
    	final String FUNC_TAG = "onFling";
    	if (Params.getBoolean(getContentResolver(), "night_screen_enabled", false)) {
        	double v = Math.sqrt(velocityX*velocityX + velocityY*velocityY);
            Debug.i(TAG, FUNC_TAG, "Fling v="+v);
            if (v > 5000) {
            	Intent alarmClockIntent = new Intent();
            	alarmClockIntent.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.AlarmClockActivity"));
            	alarmClockIntent.putExtra("height", midFrameH);
            	alarmClockIntent.putExtra("width", midFrameW);
            	alarmClockIntent.putExtra("SIM_TIME", SIM_TIME);
            	startActivity(alarmClockIntent);
            }
    	}
        return true;
    }

    public void onLongPress(MotionEvent e) {
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
            float distanceY) {
    	final String FUNC_TAG = "onScroll";
        return true;
    }

    public void onShowPress(MotionEvent e) {
    }

    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    /************************************************************************************************************************/
	//  Context Menu, Dialog, and Key Stroke Functions
	/************************************************************************************************************************/
    
    protected Dialog onCreateDialog(int id) 
    {
        Dialog dialog;
        switch(id) 
        {
/*        
	        case DIALOG_CORRECTION_BOLUS:
	            AlertDialog.Builder cBolus = new AlertDialog.Builder(this);
	            // Change button order to match Negative-Positive conventions
	            cBolus.setMessage(correctionBolusMessage)
	                   .setCancelable(false)
	                   .setPositiveButton("Cancel", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   cancelBolus(null);
	                       }
	                   })
	                   .setNegativeButton("Accept", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   acceptBolus(null);
	                       }
	                   });
	            dialog = cBolus.create();
	            break;
*/	            
	        case DIALOG_CONFIRM_STOP:       
	            AlertDialog.Builder stopBuild = new AlertDialog.Builder(this);
	            String state = "";
	            switch (DIAS_STATE)
	            {
		            case State.DIAS_STATE_CLOSED_LOOP:
		        		state = "Closed Loop";
		        		break;
		            case State.DIAS_STATE_SAFETY_ONLY:
		            	state = "Safety";
		            	break;
		            case State.DIAS_STATE_SENSOR_ONLY:
		            	state = "Sensor";
		            	break;
		            case State.DIAS_STATE_OPEN_LOOP:
		            	state = "Pump";
		            	break;
	            }
	            stopBuild.setMessage("Do you want to stop " + state + " Mode now?")
	            // Change button order to match Negative-Positive conventions
	                   .setCancelable(false)
	                   .setPositiveButton("No", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   main.removeDialog(DIALOG_CONFIRM_STOP);
	                       }
	                   })
	                   .setNegativeButton("Yes", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   main.removeDialog(DIALOG_CONFIRM_STOP);
	                    	   stopConfirm();
	                       }
	                   });
	            dialog = stopBuild.create();
	            break;
	        case DIALOG_CONFIRM_CONFIG:       
	            AlertDialog.Builder confBuild = new AlertDialog.Builder(this);	     	
				String confirmstate="";
	            switch (BUTTON_CURRENT) {
					case BUTTON_OPEN_LOOP:
						confirmstate = "Pump";
						break;
					case BUTTON_CLOSED_LOOP:
						confirmstate = "Closed Loop";
						break;
					case BUTTON_SAFETY:
						confirmstate = "Safety";
						break;
					case BUTTON_SENSOR_ONLY:
						confirmstate = "Sensor";
						break;
				}
	            // Here we are deliberately changing Negative button label to "Yes" and Positive button label to "No"
	            // This is done because we need to build with SDK version 10 in order to have the Option button on screen
	            // but version 10 displays Positive-Negative where we need Negative-Positive to be consistent with the
	            // rest of the DiAs UI.
	            confBuild.setMessage("Do you want to start " + confirmstate + " Mode now?")
	                   .setCancelable(false)
	                   .setPositiveButton("No", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
      							dialog.dismiss();
	                       }
	                   })
	                   .setNegativeButton("Yes", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
								switch (BUTTON_CURRENT) {
									case BUTTON_OPEN_LOOP:
										openLoopConfirm();
										dialog.dismiss();
										break;
									case BUTTON_SENSOR_ONLY:
										sensorOnlyConfirm();
										dialog.dismiss();
										break;
									case BUTTON_CLOSED_LOOP:
										closedLoopConfirm();
										dialog.dismiss();
										break;
									case BUTTON_SAFETY:
										safetyConfirm();
										dialog.dismiss();
										break;
									}
	                       }
	                   });
	            dialog = confBuild.create();
	            dialog.show();
	            break;
	        case DIALOG_CONFIRM_EXERCISE:     
	        	if(Params.getInt(getContentResolver(), "exercise_detection_mode", 0) == 0)
    			{
		            AlertDialog.Builder exBuild = new AlertDialog.Builder(this);
		            state = "";
		            if (EXERCISING)
		            	state = "stopping";
		            else
		            	state = "starting to";
		            exBuild.setMessage("Are you " + state + " exercise now?")
		            // Change button order to match Negative-Positive conventions
		                   .setCancelable(false)
		                   .setPositiveButton("No", new DialogInterface.OnClickListener() {
		                       public void onClick(DialogInterface dialog, int id) {
		                    	   main.removeDialog(DIALOG_CONFIRM_EXERCISE);
		                       }
		                   })
		                   .setNegativeButton("Yes", new DialogInterface.OnClickListener() {
		                       public void onClick(DialogInterface dialog, int id) {
		                    	   main.removeDialog(DIALOG_CONFIRM_EXERCISE);
		                    	   exerciseConfirm();
		                       }
		                   });
		            dialog = exBuild.create();
    			}
	        	else
	        	{
	        		Toast.makeText(this, "Automatic detection is enabled!", Toast.LENGTH_SHORT).show();
	        		dialog = null;
	        	}
	            break;
	        case DIALOG_CONFIRM_CANCEL_TEMP_BASAL:       
	            AlertDialog.Builder tbrBuild = new AlertDialog.Builder(this);
	            state = "";
	            if (temporaryBasalRateActive()) {
	            	tbrBuild.setMessage("Do you wish to resume normal basal delivery now?")
	            	// Change button order to match Negative-Positive conventions
	                   .setCancelable(false)
	                   .setPositiveButton("No", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   main.removeDialog(DIALOG_CONFIRM_CANCEL_TEMP_BASAL);
	                       }
	                   })
	                   .setNegativeButton("Yes", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   main.removeDialog(DIALOG_CONFIRM_CANCEL_TEMP_BASAL);
	                    	   cancelTemporaryBasalRate();
	                       }
	                   });
	            }
	            dialog = tbrBuild.create();
	            break;
	        case DIALOG_CONFIRM_HYPO_TREATMENT:
	        	AlertDialog.Builder htBuild = new AlertDialog.Builder(this);
	            htBuild.setMessage("Have you just treated yourself for hypoglycemia?")
	            // Change button order to match Negative-Positive conventions
	                   .setCancelable(false)
	                   .setPositiveButton("No", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   		main.removeDialog(DIALOG_CONFIRM_HYPO_TREATMENT);
	                    	   		hypoTreatmentConfirm(false);
	                       }
	                   })
	                   .setNegativeButton("Yes", new DialogInterface.OnClickListener() {
	                       public void onClick(DialogInterface dialog, int id) {
	                    	   		main.removeDialog(DIALOG_CONFIRM_HYPO_TREATMENT);
	                    	   		hypoTreatmentConfirm(true);
	                       }
	                   });
	            dialog = htBuild.create();
	            break;
	        case DIALOG_NEW_SUBJECT_CONFIRM:       
	            AlertDialog.Builder nsBuild = new AlertDialog.Builder(this);
	            nsBuild.setMessage("New Subject - Delete current database?")
	            // Change button order to match Negative-Positive conventions
	          		.setCancelable(false)
	          		.setPositiveButton("No", new DialogInterface.OnClickListener() {
	          			public void onClick(DialogInterface dialog, int id) {
	          				main.removeDialog(DIALOG_NEW_SUBJECT_CONFIRM);
	          			}
	          		})
	          		.setNegativeButton("Yes", new DialogInterface.OnClickListener() {
	          			public void onClick(DialogInterface dialog, int id) {  
	          				main.removeDialog(DIALOG_NEW_SUBJECT_CONFIRM);
	          				main.showDialog(DIALOG_PASSWORD);
	          			}
	          		});
	            dialog = nsBuild.create();
	            break;
	        case DIALOG_PASSWORD:
	    		dialog = new Dialog(this);
	    		dialog.setContentView(R.layout.ui_password_dialog);
	    		String title = "";
				switch (BUTTON_CURRENT) {
					case BUTTON_NEW_SUBJECT:
						title = "New Subject";
						break;
					case BUTTON_OPEN_LOOP:
						title = "Start Open Loop";
						break;
					case BUTTON_CLOSED_LOOP:
						title = "Start Closed Loop";
						break;
					case BUTTON_SAFETY:
						title = "Start Safety";
						break;
					case BUTTON_SENSOR_ONLY:
						title = "Start Sensor Mode";
						break;
					case BUTTON_HOME:
						title = "Open Launch Screen";
						break;
				}
	    		dialog.setTitle(title);
	    	
	    		textViewPassword = (TextView) dialog.findViewById(R.id.textViewPassword);
	    		editTextPassword = (EditText) dialog.findViewById(R.id.editTextPassword);   
	    		Cursor c = getContentResolver().query(Biometrics.PASSWORD_URI, null, null, null, null);
				if (c.moveToLast()) {
					final String PASSWORD = c.getString(c.getColumnIndex("password"));
					final String BACKUP = Params.getString(getContentResolver(), "backup_password", null);
					
					textViewPassword.setText("    Enter password    ");
					((Button) dialog.findViewById(R.id.buttonPasswordOk)).setOnClickListener(new OnClickListener() {
						public void onClick(View v) {
							if (!editTextPassword.getText().toString().equals(PASSWORD) && !editTextPassword.getText().toString().equals(BACKUP)) {
								textViewPassword.setText("Invalid password, try again");
								editTextPassword.setText("");
							} 
							else {
								removeDialog(DIALOG_PASSWORD);
								switch (BUTTON_CURRENT) {
									case BUTTON_NEW_SUBJECT:
										newSubject();
										break;
									case BUTTON_OPEN_LOOP:
										openLoopConfirm();
										break;
									case BUTTON_SENSOR_ONLY:
										sensorOnlyConfirm();
										break;
									case BUTTON_CLOSED_LOOP:
										closedLoopConfirm();
										break;
									case BUTTON_SAFETY:
										safetyConfirm();
										break;
									case BUTTON_HOME:
										homeConfirm();
										break;
									}
								}
							}
						});
						((Button) dialog.findViewById(R.id.buttonPasswordCancel)).setOnClickListener(new OnClickListener() {
							public void onClick(View v) {
								main.removeDialog(DIALOG_PASSWORD);
							}
						});
					} else {
						textViewPassword.setText("No password found. Go to Setup to create password.");		
		        		editTextPassword.setEnabled(false);
						((Button) dialog.findViewById(R.id.buttonPasswordOk)).setOnClickListener(new OnClickListener() {
							public void onClick(View v) {
								main.removeDialog(DIALOG_PASSWORD);
							}
						});
						((Button) dialog.findViewById(R.id.buttonPasswordCancel)).setOnClickListener(new OnClickListener() {
							public void onClick(View v) {
								main.removeDialog(DIALOG_PASSWORD);
							}
						});
					}
					c.close();
					
	        	break;	        	
	        default:
	            dialog = null;
        }
        return dialog;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.clear();
    	boolean retCode = false;
        MenuInflater inflater = getMenuInflater();
        
        Debug.i(TAG, "onPrepareOptionsMenu", "Context Menu"+menu.size());
        
        switch(DIAS_UI_STATE)
        {
    		case DIAS_UI_STATE_MAIN:			//There isn't an alternative state since the plots are in their own activity now
    			switch(DIAS_STATE)
    			{
    				case State.DIAS_STATE_STOPPED:
    					inflater.inflate(R.menu.mainstopped, menu);
    					retCode = true;
    					break;
    				case State.DIAS_STATE_OPEN_LOOP:
    					inflater.inflate(R.menu.mainopen, menu);
    					retCode = true;
    					break;
    				case State.DIAS_STATE_SENSOR_ONLY:
    					inflater.inflate(R.menu.mainsensoronly, menu);
    					retCode = true;
    					break;
    				case State.DIAS_STATE_CLOSED_LOOP:
    				case State.DIAS_STATE_SAFETY_ONLY:
    					inflater.inflate(R.menu.mainclosed, menu);
    					retCode = true;
    					break;
    			}
    			break;
        }
        
        ioContextMenu(menu);
        
        return retCode;
    }
    
    private void ioContextMenu(Menu menu)
    {
    	for(int i = 0;i < menu.size();i++)
        	Debug.i(TAG, "IO_TEST", "Menu: "+menu.getItem(i).toString());
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {  			
    		case R.id.menuStoppedSaveDatabase:
    			main.getContentResolver().delete(Uri.parse("content://"+ Biometrics.PROVIDER_NAME + "/save"), null, null);
    			return true;
    		case R.id.menuStoppedNewSubject:
    			newSubjectClick(null);
    			return true;
    		case R.id.menuStoppedSubjectInformation:
    			goToSetupScreen(0);
    			return true;
    		case R.id.menuDeviceManager:
    			goToSetupScreen(5);
    			return true;
    		case R.id.menuAddBG:
    			addBgClick();	
    			return true;
    		case R.id.menuViewer:
    			Intent viewActivity = new Intent();
    	 		viewActivity.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.ViewerActivity"));
    	 		startActivity(viewActivity);
    			return true;
    		case R.id.menuParameters:
    			Intent controllerParams = new Intent();
    			String action = "edu.virginia.dtc.DiAsUI.parametersAction";
    			controllerParams.setAction(action);
    			sendBroadcast(controllerParams);
    			Debug.i(TAG, "onOptionItemSelected", "Params Button pressed, action: broadcast \""+ action +"\" intent");
    			//Toast.makeText(main, "Params Button pressed, action: broadcast \""+ action +"\" intent", Toast.LENGTH_SHORT).show();
    			return true;
    		default:
    			return super.onOptionsItemSelected(item);
        }
    }

    @Override 
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    //Handle the back button
		boolean retCode;
		switch (keyCode)
		{
			case KeyEvent.KEYCODE_BACK:
				updateDiasMainState(DIAS_UI_STATE_MAIN);
				retCode = true;
				break;
			case KeyEvent.KEYCODE_HOME:
				// If already on HOME screen then prompt to exit to system
				if (DIAS_UI_STATE == DIAS_UI_STATE_MAIN) {
					BUTTON_CURRENT = BUTTON_HOME;
					showDialog(DIALOG_PASSWORD);
				}
				retCode = true;
				break;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
				//Eat volume keys
				retCode = true;
				break;
			case KeyEvent.KEYCODE_APP_SWITCH:
				retCode = true;
				break;
			default:
				retCode =  super.onKeyDown(keyCode, event);
				break;
	    }
	    return retCode;
    }
    
    @Override 
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		boolean retCode;
		switch (keyCode)
		{
			case KeyEvent.KEYCODE_HOME:
				retCode = true;
				break;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
				// Eat volume keys
				retCode = true;
				break;
			case KeyEvent.KEYCODE_APP_SWITCH:
				retCode = true;
				break;
			default:
				retCode =  super.onKeyDown(keyCode, event);
				break;
	    }
	    return retCode;
    }
    
    /************************************************************************************************************************/
	//  Main initialization (Broadcast Receivers etc.)
	/************************************************************************************************************************/
    
    private void registerReceivers() 
    {
		if (getIntent().getBooleanExtra("goToSetup", false))
			newSubject();
		
		editTextPassword = new EditText(this);

		// **************************************************************************************************************
     	// Register to receive status updates from DiAsService
        // **************************************************************************************************************
		
     	ServiceReceiver = new BroadcastReceiver() 
     	{
     		final String FUNC_TAG = "ServiceReceiver";
     		
            @Override
            public void onReceive(Context context, Intent intent) {        			
    			String action = intent.getAction();
    			Debug.i(TAG, FUNC_TAG, "ServiceReceiver -- "+action);  	
    			
    			// Handles commands for DiAsMain
    	        int command = intent.getIntExtra("DiAsMainCommand", 0);
    	        Debug.i(TAG, FUNC_TAG, "ServiceReceiver > command="+command);
    	        
    	        switch (command) 
    	        {
/*    	        
        	        case DIASMAIN_UI_NULL:
    	        		Debug.i(TAG, FUNC_TAG, "DIASMAIN_UI_NULL");
    	        		break;
        	        case DIASMAIN_UI_UPDATE_STATUS_LIGHTS:
    	    			//CGM_is_calibrated = intent.getBooleanExtra("CGM_is_calibrated", false);
    	    			//Pump_connected = intent.getBooleanExtra("Pump_connected", false);
    	    			break;
        	        case DIASMAIN_UI_UPDATE_TRAFFIC_LIGHTS:
	    				Debug.i(TAG, FUNC_TAG, "DIASMAIN_UI_UPDATE_TRAFFIC_LIGHTS");
	    				break;
        	        case DIASMAIN_UI_UPDATE_DIAS_STATE:
    	        		APC_TYPE = intent.getIntExtra("APC_TYPE", APC_TYPE_HMS);
    	        		APC_MEAL_CONTROL = intent.getIntExtra("APC_MEAL_CONTROL", APC_NO_MEAL_CONTROL);
    	        		Debug.i(TAG, FUNC_TAG, "DIASMAIN_UI_UPDATE_DIAS_STATE = "+DIAS_STATE+", APC_TYPE="+APC_TYPE);
    	        		//CGM_is_calibrated = intent.getBooleanExtra("CGM_is_calibrated", false);
    	        		//Pump_connected = intent.getBooleanExtra("Pump_connected", false);
    					break;
        	        case DIASMAIN_UI_UPDATE_STATUS_MESSAGE:
        				break;
        	        case DIASMAIN_UI_DISPLAY_CORRECTION_BOLUS_MESSAGE:
        				Debug.i(TAG, FUNC_TAG, "DIASMAIN_UI_DISPLAY_CORRECTION_BOLUS_MESSAGE");
        				displayCorrectionBolusMessage(intent.getDoubleExtra("bolusCorrection", 0.0));
        				break;
        	        case DIASMAIN_UI_UPDATE_MEAL_BOLUS_FAILED:
        				Debug.i(TAG, FUNC_TAG, "DIASMAIN_UI_UPDATE_MEAL_BOLUS_FAILED");
        				Toast.makeText(main, "System busy, please try again", Toast.LENGTH_SHORT).show();
        	        	break;
        	        case DIAS_SERVICE_COMMAND_EXIT:
        				Debug.i(TAG, FUNC_TAG, "DIAS_SERVICE_COMMAND_EXIT");
        				finish();
        				break;
        	        case DIASMAIN_UI_APC_RETURN_BOLUS:
    	        		break;
*/    	        		
        	        default:
         				Bundle b = new Bundle();
         	    		b.putString("description", "DiAsMain > unexpected command: "+command);
         	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
    	        		break;
    	        }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("edu.virginia.dtc.intent.action.DIAS_SERVICE_UPDATE_STATUS");
        registerReceiver(ServiceReceiver, filter);
        ServiceReceiverIsRegistered = true;
      
        // Register to receive Supervisor Time Tick
        TickReceiver = new BroadcastReceiver() 
        {
        	final String FUNC_TAG = "TickReceiver";
        	
            @Override
            public void onReceive(Context context, Intent intent) 
            {
            	SIM_TIME = intent.getLongExtra("simulatedTime", -1);
        		Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, null, null, null, null);
        		
            	if(c!=null)
            	{
            		if(c.moveToLast())
            		{
            			double cgmValue = c.getDouble(c.getColumnIndex("cgmValue"));
            			long cgmLastTime = c.getLong(c.getColumnIndex("cgmLastTime"));
            			
            			Debug.i(TAG, FUNC_TAG, "cgmValue: "+cgmValue+" time(in min): "+getTimeSeconds()/60+" cgmLastTime: "+cgmLastTime/60);
                		
                		if (cgmLastTime > 0) 
                		{
             		   		int minsAgo = (int)(getTimeSeconds() - cgmLastTime)/60;
             		   		
             		   		if (minsAgo < 0)
             		   			minsAgo = 0;
             		   	
             		   		Debug.i(TAG, FUNC_TAG, "Minutes ago: "+minsAgo);
             		   		
	             		   	String minsString = (minsAgo == 1) ? "min" : "mins";
	             		   	((TextView)findViewById(R.id.textViewCGMTime)).setText(minsAgo + " " + minsString + " ago");
	             		   	
	             		   	if (minsAgo == 0 || cgmValue < 39 || cgmValue > 401)
	                 		   	((TextView)findViewById(R.id.textViewCGMTime)).setVisibility(View.INVISIBLE);
	             		   	else
	                 		   	((TextView)findViewById(R.id.textViewCGMTime)).setVisibility(View.VISIBLE);
                		}
            			
            			c.close();
            		}
            		else
            			c.close();
            	}
            	update();
            }
        };
        IntentFilter filter1 = new IntentFilter();
        filter1.addAction("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK");
        registerReceiver(TickReceiver, filter1);    
        TickReceiverIsRegistered = true;
    }
    
    /************************************************************************************************************************/
	//  UI and System Update Functions
	/************************************************************************************************************************/
    
    class CgmDetailsObserver extends ContentObserver 
    {	
    	private int count;
    	
    	public CgmDetailsObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "Cgm Details Observer";
    		Debug.i(TAG, FUNC_TAG, "Constructor");
    		
    		count = 0;
    		
    		Debug.i(TAG, FUNC_TAG, "Checking current phone calibration!");
    		
    		Cursor c = getContentResolver().query(Biometrics.CGM_DETAILS_URI, null, null, null, null);
           	
	       	if(c!=null)
	       	{
	       		if(c.moveToLast())
	       		{
	       			int pc = c.getInt(c.getColumnIndex("phone_calibration"));
	       			if(pc > 0)
	       				PHONE_CALIB = true;
	       			else
	       				PHONE_CALIB = false;
	       			c.close();
	       		}
	       		else
	       		{
	       			c.close();
	       			return;
	       		}
	       	}
	       	else
	       		return;
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
    	   	Debug.i(TAG, FUNC_TAG, "Cgm Details Observer: "+count);
    	   
    	   	Cursor c = getContentResolver().query(Biometrics.CGM_DETAILS_URI, null, null, null, null);
       	
	       	if(c!=null)
	       	{
	       		if(c.moveToLast())
	       		{
	       			int pc = c.getInt(c.getColumnIndex("phone_calibration"));
	       			if(pc > 0)
	       				PHONE_CALIB = true;
	       			else
	       				PHONE_CALIB = false;
	       			c.close();
	       		}
	       		else
	       		{
	       			c.close();
	       			return;
	       		}
	       	}
	       	else
	       		return;
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
    	   
    	   update();
       }		
    }
    
    public void update()
    {
    	final String FUNC_TAG = "update";
  	
    	Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, null, null, null, null);
    	
    	if(c!=null)
    	{
    		if(c.moveToLast())
    		{
    			Debug.i(TAG, FUNC_TAG, "Updating UI...");	
    			updateUI(c);
    			c.close();
    		}
    		else
    		{
    			c.close();
    			return;
    		}
    	}
    	else
    		return;
    }
    
    public void updateUI(Cursor c)
    {
    	final String FUNC_TAG = "updateUI";
    	
    	long start = System.currentTimeMillis();
    	long stop;
    	
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
    	
    	int hypoLight = c.getInt(c.getColumnIndex("hypoLight"));
    	int hyperLight = c.getInt(c.getColumnIndex("hyperLight"));
    	
    	double apcBolus = c.getDouble(c.getColumnIndex("apcBolus"));
    	int apcStatus = c.getInt(c.getColumnIndex("apcStatus"));
    	int apcType = c.getInt(c.getColumnIndex("apcType"));
    	String apcString = c.getString(c.getColumnIndex("apcString"));
    	
    	boolean exercising = (c.getInt(c.getColumnIndex("exercising"))==1) ? true : false;
    	boolean alarmNoCgm = (c.getInt(c.getColumnIndex("alarmNoCgm"))==1) ? true : false;
    	boolean alarmHypo = (c.getInt(c.getColumnIndex("alarmHypo"))==1) ? true : false;
    	
    	if(Params.getBoolean(getContentResolver(), "enableIO", false))
    	{
    		final String IO_TAG = "IO_TEST";
	    	Debug.i(TAG, IO_TAG, "time: "+time+" sysTime: "+sysTime+" diasState: "+diasState+" battery: "+battery+" safetyMode: "+safetyMode+" enableIOTest: "+Params.getBoolean(getContentResolver(), "enableIO", false));
	    	Debug.i(TAG, IO_TAG, "cgmValue: "+cgmValue+" cgmTrend: "+cgmTrend+" cgmLastTime: "+cgmLastTime+" cgmState: "+cgmState+" cgmStatus: "+cgmStatus);
	    	Debug.i(TAG, IO_TAG, "pumpLastBolus: "+pumpLastBolus+" pumpLastBolusTime: "+pumpLastBolusTime+" pumpState: "+pumpState+" pumpStatus: "+pumpStatus);
	    	Debug.i(TAG, IO_TAG, "hypoLight: "+hypoLight+" hyperLight: "+hyperLight);
	    	Debug.i(TAG, IO_TAG, "apcBolus: "+apcBolus+" apcStatus: "+apcStatus+" apcType: "+apcType+" apcString: "+apcString);
	    	Debug.i(TAG, IO_TAG, "exercising: "+exercising+" alarmNoCgm: "+alarmNoCgm+" alarmHypo: "+alarmHypo);
	    	Debug.i(TAG, IO_TAG, "----------------------------------------------------------------------------------------------------------------------------------");
    	}
    	
    	//Call functions to update the UI each time there is a change in the SYSTEM table
    	if(this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
    	{
	    	updateDiasState(diasState);
	    	
	    	updateLastBolus(diasState, pumpLastBolus, pumpLastBolusTime);
	    	
	    	updateTrafficLights(diasState, hypoLight, hyperLight, alarmHypo);
	    	
	    	updateCgm(cgmValue, cgmTrend, cgmLastTime, cgmState, alarmNoCgm);
    	}
    	else
    		Debug.w(TAG, FUNC_TAG, "The activity is in portrait mode...");
    	
    	//Update constants for system
    	PREV_DIAS_STATE = DIAS_STATE;
    	PREV_CGM_VALUE = CGM_VALUE;
    	PREV_CGM_STATE = CGM_STATE;
    	PREV_PUMP_STATE = PUMP_STATE;
    	PREV_BATTERY = BATTERY;
    	PREV_ENABLE_IO = ENABLE_IO;
    	PREV_EXERCISING = EXERCISING;
    	
    	DIAS_STATE = diasState;
    	CGM_VALUE = cgmValue;
    	PUMP_STATE = pumpState;
    	CGM_STATE = cgmState;
    	BATTERY = battery;
    	EXERCISING = exercising;
    	
    	//Update the DiAs UI state (show/hide objects)
    	if(this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
    	{
    		updateDiasMain();
    	}
    	else
    		Debug.w(TAG, FUNC_TAG, "The activity is in portrait mode...");
    	
    	//Custom Flex Button Icons
    	updateFlexButtons();
    	
    	stop = System.currentTimeMillis();
    	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    private void updateFlexButtons()
    {
    	Drawable icon = getResources().getDrawable(R.drawable.meal_button_normal);
        try 
        {
        	icon = getPackageManager().getApplicationIcon("edu.virginia.dtc.MealActivity");
        } 
        catch (NameNotFoundException e) 
        {
        	e.printStackTrace();
        }
         
        Button b = (Button)this.findViewById(R.id.buttonMeal);
        if(icon != null)
        	b.setBackground(icon);
    }
    
    private void updateDiasState(int diasState)
    {
    	final String FUNC_TAG = "updateDiasState";
    	long start = System.currentTimeMillis();
    	long stop;
    	
    	String dias_state = "", ui_state = "";
		
    	switch(diasState)
    	{
	    	case State.DIAS_STATE_STOPPED:
				dias_state = "STOPPED";
				break;
			case State.DIAS_STATE_OPEN_LOOP:
				dias_state = "OPEN";
				break;
			case State.DIAS_STATE_CLOSED_LOOP:
				dias_state = "CLOSED";
				break;
			case State.DIAS_STATE_SAFETY_ONLY:
				dias_state = "SAFETY_ONLY";
				break;
			case State.DIAS_STATE_SENSOR_ONLY:
				dias_state = "SENSOR_ONLY";
				break;
			default:
				dias_state = "WHAT/"+diasState;
				break;
    	}

		switch (DIAS_UI_STATE) 
		{
			case DIAS_UI_STATE_MAIN:
				ui_state = "MAIN";
				break;
			case DIAS_UI_STATE_PLOTS:
				ui_state = "PLOTS";
				break;
			case DIAS_UI_STATE_BOLUS_INTERCEPTOR:
				ui_state = "BOLUS_INTERCEPTOR";
				break;
		}
		
		Debug.i(TAG, FUNC_TAG, "Current state: " + dias_state + " --- " + ui_state);
		
    	stop = System.currentTimeMillis();
    	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    public void updateDiasMain() 
    {
		final String FUNC_TAG = "updateDiasMain";
		long start = System.currentTimeMillis();
    	long stop;

		Debug.i(TAG, FUNC_TAG, "Updating Dias Main...");
		
		if (DIAS_STATE == State.DIAS_STATE_STOPPED) 
		{
			mainGroupShow();
		} 
		else if (DIAS_STATE != State.DIAS_STATE_STOPPED) 
		{
			switch (DIAS_UI_STATE) 
			{
				case DIAS_UI_STATE_MAIN:
					mainGroupShow();    
					break;
				case DIAS_UI_STATE_PLOTS:
					//mainGroupHide();
					break;
			}
		}
		
		stop = System.currentTimeMillis();
    	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    private void updateLastBolus(int diasState, double pumpLastBolus, long pumpLastBolusTime)
    {
    	final String FUNC_TAG = "updateLastBolus";
    	long start = System.currentTimeMillis();
    	long stop;
    	
    	TextView textViewBolus = (TextView)this.findViewById(R.id.textViewBolusInfo);
  	   	if (diasState == State.DIAS_STATE_SENSOR_ONLY) 
  	   	{
  	   		textViewBolus.setVisibility(TextView.INVISIBLE);
  	   	}
  	   	else 
  	   	{
  	   		textViewBolus.setVisibility(TextView.VISIBLE);
  	   		
  	   		Time time = new Time();
  	   		time.set(pumpLastBolusTime*1000);
  	   		
  	   		if ((int)pumpLastBolus == -1)
  	   			textViewBolus.setText(" Last bolus: --");
  	   		else
  	   		{
  	   			DecimalFormat bolusFormat = new DecimalFormat();
  	   			bolusFormat.setMaximumFractionDigits(2);
  	   			bolusFormat.setMinimumFractionDigits(2);
  	   			textViewBolus.setText(" Last bolus: " + bolusFormat.format(pumpLastBolus) + " U at " + time.format("%I:%M %p").toUpperCase());
  	   		}
  	   	}
  	   	
  	   	stop = System.currentTimeMillis();
  	   	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    public void updateTrafficLights(int diasState, int hypoLight, int hyperLight, boolean alarmHypo)
    {
    	final String FUNC_TAG = "updateTrafficLights";
    	long start = System.currentTimeMillis();
    	long stop;
    	
    	final int RED_FRAME = Color.rgb(255, 25, 0);
		
		ImageView hypoLightRed = (ImageView)this.findViewById(R.id.hypoLightRed);
		ImageView hypoLightYellow = (ImageView)this.findViewById(R.id.hypoLightYellow);
		ImageView hypoLightGreen = (ImageView)this.findViewById(R.id.hypoLightGreen);
		ImageView hypoLightBorder = (ImageView)this.findViewById(R.id.hypoLightBorder);
		ImageView hyperLightRed = (ImageView)this.findViewById(R.id.hyperLightRed);
		ImageView hyperLightYellow = (ImageView)this.findViewById(R.id.hyperLightYellow);
		ImageView hyperLightGreen = (ImageView)this.findViewById(R.id.hyperLightGreen);
		ImageView hyperLightBorder = (ImageView)this.findViewById(R.id.hyperLightBorder);
		ImageView hypoTreatButtonBorder = (ImageView)this.findViewById(R.id.android_hypo_treatment_button_border);
		
		TextView hypoText = (TextView) findViewById(R.id.textViewHypo);
		TextView hyperText = (TextView) findViewById(R.id.textViewHyper);

		// Traffic lights are only illuminated when DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP
		makeTrafficLightsInvisible();
		
		hypoText.setTextColor(Color.rgb(0xBB, 0xBB, 0xBB)); // #BBBBBB
		hypoLightBorder.clearColorFilter();
		
		hyperText.setTextColor(Color.rgb(0xBB, 0xBB, 0xBB)); // #BBBBBB
		hyperLightBorder.clearColorFilter();
		hypoTreatButtonBorder.setVisibility(Button.INVISIBLE);
		
		if (diasState == State.DIAS_STATE_CLOSED_LOOP || diasState == State.DIAS_STATE_OPEN_LOOP || diasState == State.DIAS_STATE_SAFETY_ONLY || diasState == State.DIAS_STATE_SENSOR_ONLY) 
		{
			switch(hypoLight)
			{
				case Safety.RED_LIGHT:
					hypoLightRed.setVisibility(View.VISIBLE);
					if (alarmHypo) 
					{
						// Remove Dialog boxes that might be in the way
						removeDialog(DIALOG_PASSWORD);
						removeDialog(DIALOG_CONFIRM_STOP);
						removeDialog(DIALOG_CONFIRM_EXERCISE);
						removeDialog(DIALOG_CONFIRM_HYPO_TREATMENT);
						
//						showAlarmActivity(HYPO_ALARM);
					}
					
					// Make Hypo frame glow red to be super obvious that there is a problem
					hypoText.setTextColor(RED_FRAME);
					hypoLightBorder.setColorFilter(RED_FRAME);
					hypoTreatButtonBorder.setVisibility(Button.VISIBLE);
					hypoTreatButtonBorder.setColorFilter(RED_FRAME);
					break;
				case Safety.YELLOW_LIGHT:
					hypoLightYellow.setVisibility(View.VISIBLE);
					break;
				case Safety.GREEN_LIGHT:
					hypoLightGreen.setVisibility(View.VISIBLE);
					break;
				default:
					hypoLightBorder.setVisibility(View.VISIBLE);
					Debug.i(TAG, FUNC_TAG, "Invalid hypolight="+hypoLight);
					break;
			}
			
			switch(hyperLight)
			{
				case Safety.RED_LIGHT:
					hyperLightRed.setVisibility(View.VISIBLE);
					
					// Make hyper frame glow red to be super obvious that there is a problem
					hyperText.setTextColor(RED_FRAME);
					hyperLightBorder.setColorFilter(RED_FRAME);
					break;
				case Safety.YELLOW_LIGHT:
					hyperLightYellow.setVisibility(View.VISIBLE);
					break;
				case Safety.GREEN_LIGHT:
					hyperLightGreen.setVisibility(View.VISIBLE);
					break;
				default:
					hyperLightBorder.setVisibility(View.VISIBLE);
					Debug.i(TAG, FUNC_TAG, "Invalid hyperlight="+hyperLight);
					break;
			}
		}
		
		stop = System.currentTimeMillis();
    	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    private void updateCgm(double cgmValue, int cgmTrend, long cgmLastTime, int cgmState, boolean alarmNoCgm)
    {
    	final String FUNC_TAG = "updateCgm";
    	long start = System.currentTimeMillis();
    	long stop;
    	int blood_glucose_display_units = Params.getInt(getContentResolver(), "blood_glucose_display_units", CGM.BG_UNITS_MG_PER_DL);
    	
    	double cgmValueInSelectedUnits = cgmValue;
    	String unit_string = new String(" mg/dl");
    	if (blood_glucose_display_units == CGM.BG_UNITS_MMOL_PER_L) {
    		cgmValueInSelectedUnits = cgmValueInSelectedUnits/CGM.MGDL_PER_MMOLL;
    		unit_string = " mmol/L";
    	}

		TextView textViewCGM = (TextView)this.findViewById(R.id.textViewCGM);
		TextView textViewCGMTime = (TextView)this.findViewById(R.id.textViewCGMTime);
		
		DecimalFormat decimalFormat = new DecimalFormat();
    	if (blood_glucose_display_units == CGM.BG_UNITS_MMOL_PER_L) {
    		decimalFormat.setMinimumFractionDigits(1);
    		decimalFormat.setMaximumFractionDigits(1);
    	}
    	else {
    		decimalFormat.setMinimumFractionDigits(0);
    		decimalFormat.setMaximumFractionDigits(0);
    	}
		String CGMString = decimalFormat.format(cgmValueInSelectedUnits);
		
		int minsAgo = (int)((getTimeSeconds() - cgmLastTime)/60);
		if (minsAgo < 0)
			minsAgo = 0;
		String minsString = (minsAgo == 1) ? "min" : "mins";

		switch(cgmState)
		{
			case CGM.CGM_NORMAL:
				// We no longer enforce hard limits here since the DiAs Service 
				// will already do that and is capable of adjusting based on device parameters
				textViewCGM.setText(CGMString+unit_string);
				textViewCGMTime.setText((minsAgo == 0) ? "" : (minsAgo + " " + minsString + " ago"));
				Debug.i(TAG, FUNC_TAG, "CGM State Normal: "+minsAgo+" min old");
				break;
			case CGM.CGM_DATA_ERROR:
				textViewCGM.setText("Data Error");
				break;
			case CGM.CGM_NOT_ACTIVE:
				textViewCGM.setText("CGM Inactive");
				break;
			case CGM.CGM_NONE:
				textViewCGM.setText("");
				break;
			case CGM.CGM_NOISE:
				textViewCGM.setText("CGM Noise");
				break;
			case CGM.CGM_WARMUP:
				textViewCGM.setText("Warm-Up");
				break;
			case CGM.CGM_CALIBRATION_NEEDED:
				textViewCGM.setText("Calibrate");
				break;
			case CGM.CGM_DUAL_CALIBRATION_NEEDED:
				textViewCGM.setText("Calibrate");
				break;
			case CGM.CGM_CAL_LOW:
				textViewCGM.setText("Cal Low");
				break;
			case CGM.CGM_CAL_HIGH:
				textViewCGM.setText("Cal High");
				break;
			case CGM.CGM_SENSOR_FAILED:
				textViewCGM.setText("CGM Sensor Failed");
				break;
		}
		
		if (alarmNoCgm && !noCgmInClosedLoopAlarmPlaying) 
		{
			// Remove Dialog boxes that might be in the way
			removeDialog(DIALOG_PASSWORD);
			removeDialog(DIALOG_CONFIRM_STOP);
			removeDialog(DIALOG_CONFIRM_EXERCISE);
			removeDialog(DIALOG_CONFIRM_HYPO_TREATMENT);
			
			Debug.i(TAG, FUNC_TAG, "START OPEN LOOP");
			
			//showAlarmActivity(NO_CGM_ALARM);
			
			startNoCgmWatchdogTimer();
		}
		
		noCgmInClosedLoopAlarmPlaying = alarmNoCgm;
		
		ImageView imageViewArrow = (ImageView)this.findViewById(R.id.imageViewArrow);

		if(cgmReady())		//Only show the trend if a value is being shown
		{
			switch (cgmTrend)
			{
	 			case 2:
	 				Debug.i(TAG, FUNC_TAG, "Trend Up");
	 				imageViewArrow.setBackgroundResource(R.drawable.arrow_2);
	 				imageViewArrow.setVisibility(ImageView.VISIBLE);
	 				textViewCGMTime.setVisibility(TextView.VISIBLE);
	 				break;
	 			case 1:
	 				Debug.i(TAG, FUNC_TAG, "Trend Up-Right");
	 				imageViewArrow.setBackgroundResource(R.drawable.arrow_1);
	 				imageViewArrow.setVisibility(ImageView.VISIBLE);
	 				textViewCGMTime.setVisibility(TextView.VISIBLE);
	 				break;
	 			case 0:
	 				Debug.i(TAG, FUNC_TAG, "Trend Flat");
	 				imageViewArrow.setBackgroundResource(R.drawable.arrow_0);
	 				imageViewArrow.setVisibility(ImageView.VISIBLE);
	 				textViewCGMTime.setVisibility(TextView.VISIBLE);
	 				break;
	 			case -1:
	 				Debug.i(TAG, FUNC_TAG, "Trend Down-Right");
	 				imageViewArrow.setBackgroundResource(R.drawable.arrow_m1);
	 				imageViewArrow.setVisibility(ImageView.VISIBLE);
	 				textViewCGMTime.setVisibility(TextView.VISIBLE);
	 				break;
	 			case -2:
	 				Debug.i(TAG, FUNC_TAG, "Trend Down");
	 				imageViewArrow.setBackgroundResource(R.drawable.arrow_m2);
	 				imageViewArrow.setVisibility(ImageView.VISIBLE);
	 				textViewCGMTime.setVisibility(TextView.VISIBLE);
	 				break;
	 			case 5:
	 				Debug.i(TAG, FUNC_TAG, "No Trend");
	 				imageViewArrow.setVisibility(ImageView.INVISIBLE);
	 				break;
	 			default:
	 				Debug.i(TAG, FUNC_TAG, "Unknown Trend");
	 				imageViewArrow.setVisibility(ImageView.INVISIBLE);
	 				textViewCGMTime.setVisibility(TextView.INVISIBLE);
			}
		}
		
		stop = System.currentTimeMillis();
    	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    private boolean pumpReady()
    {
    	Debug.i(TAG, "pumpReady", "PUMP_STATE: "+PUMP_STATE);
    	
    	switch(PUMP_STATE)
	    {
    		case Pump.RECONNECTING:
	    	case Pump.CONNECTED:
	    	case Pump.CONNECTED_LOW_RESV:
    			return true;
	    	default:
	    		return false;
    	}
    }
    
    private boolean pumpReadyNoReco()
    {
    	Debug.i(TAG, "pumpReady", "PUMP_STATE: "+PUMP_STATE);
    	
    	switch(PUMP_STATE)
	    {
    		case Pump.CONNECTED:
	    	case Pump.CONNECTED_LOW_RESV:
    			return true;
	    	default:
	    		return false;
    	}
    }
    
    private boolean cgmReady()
    {
    	Debug.i(TAG, "cgmReady", "CGM_STATE: "+CGM_STATE);
    	
    	switch(CGM_STATE)
    	{
	    	case CGM.CGM_NORMAL:
	    	case CGM.CGM_CAL_HIGH:
	    	case CGM.CGM_CAL_LOW:
	    	case CGM.CGM_CALIBRATION_NEEDED:
	    	case CGM.CGM_DUAL_CALIBRATION_NEEDED:
	    		return true;
    		default:
    			return false;
    	}
    }
    
    private boolean batteryReady()
    {
    	Debug.i(TAG, "batteryReady", "Battery: "+BATTERY);
    	
    	if(BATTERY > BATTERY_SHUTDOWN_THRESHOLD)
    		return true;
    	else
    		return false;
    }
    
    /************************************************************************************************************************/
	//  UI Helper Functions
	/************************************************************************************************************************/
    
    public void updateDiasMainState(int state)		
    {
    	//This is a bit of a retro-fit for the system, since these updates only effect the UI its fine,
    	//they aren't involved with system wide changes in data.  It was updated to be called only when
    	//DIAS_UI_STATE was actually assigned something
    	
    	final String FUNC_TAG = "updateDiasMainState";
    	
    	Debug.i(TAG, FUNC_TAG, "Previous UI state: "+DIAS_UI_STATE);
    	
    	DIAS_UI_STATE = state;
    	
    	Debug.i(TAG, FUNC_TAG, "New UI state: "+state);
    	
    	updateDiasMain();		//This just allows us to more easily track changes to the UI
    }
       
	public void initTrafficLights() 
	{
		makeTrafficLightsInvisible();
		ImageView hypoLightOff = (ImageView)this.findViewById(R.id.hypoLightOff);
		ImageView hyperLightOff = (ImageView)this.findViewById(R.id.hyperLightOff);
		hypoLightOff.setVisibility(View.VISIBLE);
		hyperLightOff.setVisibility(View.VISIBLE);
	}
	
    public void makeTrafficLightsInvisible() 
    {
		ImageView hypoLightRed = (ImageView)this.findViewById(R.id.hypoLightRed);
		ImageView hypoLightYellow = (ImageView)this.findViewById(R.id.hypoLightYellow);
		ImageView hypoLightGreen = (ImageView)this.findViewById(R.id.hypoLightGreen);
		ImageView hyperLightRed = (ImageView)this.findViewById(R.id.hyperLightRed);
		ImageView hyperLightYellow = (ImageView)this.findViewById(R.id.hyperLightYellow);
		ImageView hyperLightGreen = (ImageView)this.findViewById(R.id.hyperLightGreen);
		hypoLightRed.setVisibility(View.INVISIBLE);
		hypoLightYellow.setVisibility(View.INVISIBLE);
		hypoLightGreen.setVisibility(View.INVISIBLE);
		hyperLightRed.setVisibility(View.INVISIBLE);
		hyperLightYellow.setVisibility(View.INVISIBLE);
		hyperLightGreen.setVisibility(View.INVISIBLE);
     }
    
    public void initCGMMessage() 
    {
 	   TextView textViewCGMMessage = (TextView)this.findViewById(R.id.textViewCGM);		   
       textViewCGMMessage.setText("");
    }
   
   	/************************************************************************************************************************/
    //  Main DiAs UI Buttons (Loop modes, plots, treatment, exercise, etc.)
    /************************************************************************************************************************/
   
   	public void stopClick(View view) 
   	{
		soundClick();
   		showDialog(DIALOG_CONFIRM_STOP);
   		
   		//TODO: check this, but it shouldn't be possible since the pop-up activity will have precedence
 		//Button alarmStopButton = (Button)this.findViewById(R.id.buttonStopAudibleAlarm);
 		//alarmStopButton.setVisibility(ImageView.INVISIBLE);
	}
   	
   	public void openLoopClick(View view){
		soundClick();
	 	BUTTON_CURRENT = BUTTON_OPEN_LOOP;
	 	onCreateDialog(DIALOG_CONFIRM_CONFIG);
   	}
   	
   	public void closedLoopClick(View view){
		soundClick();
	 	BUTTON_CURRENT = BUTTON_CLOSED_LOOP;
	 	onCreateDialog(DIALOG_CONFIRM_CONFIG);
   	}
   	   	
   	public void safetyClick(View view){
		soundClick();
	 	BUTTON_CURRENT = BUTTON_SAFETY;
	 	onCreateDialog(DIALOG_CONFIRM_CONFIG);
   	}
   	   	
   	public void sensorOnlyClick(View view){
		soundClick();
	 	BUTTON_CURRENT = BUTTON_SENSOR_ONLY;
	 	onCreateDialog(DIALOG_CONFIRM_CONFIG);
   	}
   	   	
   	public void newSubjectClick(View view){
		soundClick();
	 	BUTTON_CURRENT = BUTTON_NEW_SUBJECT;
   		showDialog(DIALOG_NEW_SUBJECT_CONFIRM);
   	}
 
    public void hypoTreatmentClick(View view) {
    	final String FUNC_TAG = "hypoTreatmentClick";
    	
 	   Debug.i(TAG, FUNC_TAG, "tapclink1");
 	   soundClick();
 	   Bundle b = new Bundle();
 	   b.putString("description", "Hypo treatment first button pressed");
 	   Event.addEvent(getApplicationContext(), Event.EVENT_UI_HYPO_BUTTON_PRESSED, Event.makeJsonString(b), Event.SET_CUSTOM);
  	
 	   //showDialog(DIALOG_CONFIRM_HYPO_TREATMENT);
    }
    
    public void addBgClick() 
    {
    	Intent smbgAct = new Intent();
    	smbgAct.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.SmbgActivity"));
    	smbgAct.putExtra("height", midFrameH);
    	smbgAct.putExtra("width", midFrameW);
    	smbgAct.putExtra("state", DIAS_STATE);
    	smbgAct.putExtra("simulatedTime", getTimeSeconds());
    	smbgAct.putExtra("standaloneInstalled", standaloneDriverAvailable());
 		startActivityForResult(smbgAct, SMBG);
    }
    
    public void temporaryBasalStartClick(View view)
    {
    	final String FUNC_TAG = "temporaryBasalStartClick";
 	   	Debug.i(TAG, FUNC_TAG, "tapclink1");
 	   	soundClick();
 	   	if (temporaryBasalRateActive()) {
	   		Bundle b = new Bundle();
			b.putString("description", FUNC_TAG+" while temporaryBasalRateActive==true");
			Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
 	   	}
 	   	else {
 	    	if (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP && Params.getBoolean(getContentResolver(), "temporaryBasalRateEnabled", false)) {
 		 		Intent tbIntent = new Intent();
 		 		tbIntent.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.TempBasalActivity"));
 		 		startActivityForResult(tbIntent, TEMP_BASAL);
 	    	}
 	    	else if ((DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) && 
 	    			Params.getBoolean(getContentResolver(), "temporaryBasalRateEnabled", false)) {
 			    Intent intentBroadcast = new Intent("edu.virginia.dtc.intent.action.TEMP_BASAL");
 			    intentBroadcast.putExtra("command", TempBasal.TEMP_BASAL_START);
 		        sendBroadcast(intentBroadcast);
 	    	}
 	    	else {
 		   		Bundle b = new Bundle();
 				b.putString("description", FUNC_TAG+" while invalid mode or temp basal not enabled");
 				Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
 	    	}
 	   	}
    }
    
    public void temporaryBasalCancelClick(View view)
    {
    	final String FUNC_TAG = "temporaryBasalCancelClick";
 	   	Debug.i(TAG, FUNC_TAG, "tapclink1");
 	   	soundClick();
 	   	if (temporaryBasalRateActive()) {
 	   		if (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP) {
 	 	 	   	showDialog(DIALOG_CONFIRM_CANCEL_TEMP_BASAL);
 	   		}
 	   		else if ((DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) && 
 	   			Params.getBoolean(getContentResolver(), "temporaryBasalRateEnabled", false)) //&& temporaryBasalRateActivityAvailable()) 
 	   		{
 			    //Intent intentBroadcast = new Intent("edu.virginia.dtc.intent.action.TEMP_BASAL");
 			    //intentBroadcast.putExtra("command", TempBasal.TEMP_BASAL_CANCEL);
 		        //sendBroadcast(intentBroadcast);
 		        showDialog(DIALOG_CONFIRM_CANCEL_TEMP_BASAL); // cancel TBR in DiAs Ui
 	   		}
 	   		else {
 		   		Bundle b = new Bundle();
 				b.putString("description", FUNC_TAG+" while invalid mode or temp basal not enabled");
 				Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
 	   		}
 	   	}
 	   	else {
	   		Bundle b = new Bundle();
			b.putString("description", FUNC_TAG+" while temporaryBasalRateActive==false");
			Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
 	   	}
    }
    
    public void exerciseClick(View view) {
    	final String FUNC_TAG = "exerciseClick";
 	   	Debug.i(TAG, FUNC_TAG, "tapclink1");
 	   	soundClick();
 	   	showDialog(DIALOG_CONFIRM_EXERCISE);
    }
            
    public void startOpenLoopStopAlarmClick(View view) {
    	final String FUNC_TAG = "startOpenLoopStopAlarmClick";
    	
 	   	Debug.i(TAG, FUNC_TAG, "");
 		Intent intent1 = new Intent();
 		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
 		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_OPEN_LOOP_STOP_ALARM);
 		startService(intent1);
    }
    
   	public void mealClick(View view) {
   		final String FUNC_TAG = "mealClick";
   		
	 	soundClick();
	 	
	 	if(mealActivityAvailable())
	 	{
	 		Debug.i(TAG, FUNC_TAG, "Starting MealActivity!");
	 		
	 		Intent mealIntent = new Intent();
	 		mealIntent.setComponent(new ComponentName("edu.virginia.dtc.MealActivity", "edu.virginia.dtc.MealActivity.MealActivity"));
	 		mealIntent.putExtra("height", midFrameH);
	 		mealIntent.putExtra("width", midFrameW);
	 		
	 		Debug.i(TAG, "IO_TEST", "Sending Meal Activity Intent! ("+mealIntent.toString()+")");
	 		
	 		startActivity(mealIntent);
	 	}
	 	else
	 	{
	 		Debug.i(TAG, FUNC_TAG, "No meal activity installed!");
	 	}
   	}
   	    
   	public void plotsClick(View view) 
    {   		
   		final String FUNC_TAG = "plotsClick";
   		
    	soundClick();
    	Debug.i(TAG, FUNC_TAG, "before plotsClick");
    	 
    	Intent plotsDisplay = new Intent();
 		plotsDisplay.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.PlotsActivity"));
    	plotsDisplay.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
 		plotsDisplay.putExtra("height", midFrameH);
 		plotsDisplay.putExtra("width", midFrameW);
 		plotsDisplay.putExtra("state", DIAS_STATE);
 		plotsDisplay.putExtra("simTime", getTimeSeconds());
 		startActivityForResult(plotsDisplay, PLOTS);
    	 
 		updateDiasMainState(DIAS_UI_STATE_PLOTS);	//Update the display so the UI mode catches
 		
 		Debug.i(TAG, FUNC_TAG, "after plotsClick");
     }
   		
   	public void checkVisible(Button b, int v)
   	{
   		if(b.getVisibility() == v)
   			return;
   		else
   			b.setVisibility(v);
   	}
   	   	
   	public void checkVisible(FrameLayout f, int v)
   	{
   		if(f.getVisibility() == v)
   			return;
   		else
   			f.setVisibility(v);
   	}
   	   	
   	public void checkVisible(LinearLayout l, int v)
   	{
   		if(l.getVisibility() == v)
   			return;
   		else
   			l.setVisibility(v);
   	}
   	   	
   	public void mainGroupShow() 
   	{
   	   final String FUNC_TAG = "mainGroupShow";
   	   
	   long start = System.currentTimeMillis();
	   long stop;
   		
	   FrameLayout mainButtonsHigh = (FrameLayout)findViewById(R.id.frameMidHighButtons);
	   FrameLayout mainButtonsLow = (FrameLayout)findViewById(R.id.frameMidLowButtons);
	   FrameLayout frame1 = (FrameLayout) findViewById(R.id.frameExerciseNewsubject);
	   FrameLayout frame2 = (FrameLayout) findViewById(R.id.frameHypoOpenloop);
	   FrameLayout frame3 = (FrameLayout) findViewById(R.id.frameTemporaryBasal);
//	   FrameLayout frame3 = (FrameLayout) findViewById(R.id.frameCalibrationClosedloop);
	   FrameLayout frame4 = (FrameLayout) findViewById(R.id.frameStopStart);
	   
	   LinearLayout infoScreen = (LinearLayout)findViewById(R.id.linearInfoScreen);
	   LinearLayout infoCGMStatus = (LinearLayout)findViewById(R.id.linearMidInfoCGM);
	   LinearLayout infoExtra = (LinearLayout)findViewById(R.id.linearMidInfo);
	   
 	   // Find buttons
 	   Button buttonExercise = (Button)this.findViewById(R.id.buttonExercise);
// 	   Button buttonCalibration = (Button)this.findViewById(R.id.buttonCalibration);
 	   Button buttonStartTemporaryBasal = (Button)this.findViewById(R.id.buttonStartTemporaryBasal);
 	   Button buttonCancelTemporaryBasal = (Button)this.findViewById(R.id.buttonCancelTemporaryBasal);
 	   Button buttonHypoTreatment = (Button)this.findViewById(R.id.buttonHypoTreatment);
 	   Button buttonStop = (Button)this.findViewById(R.id.buttonStop);
 	   Button buttonMeal = (Button)this.findViewById(R.id.buttonMeal);
 	   Button buttonPlots = (Button)this.findViewById(R.id.buttonPlots);
 	   Button buttonSensorOnly = (Button) findViewById(R.id.buttonSensorOnly);
 	   Button buttonOpenLoop = (Button) findViewById(R.id.buttonOpenLoop);
 	   Button buttonSafety = (Button) findViewById(R.id.buttonSafety);	  
 	   Button buttonClosedLoop = (Button) findViewById(R.id.buttonClosedLoop);	  
 	   
 	   checkVisible(mainButtonsHigh, FrameLayout.VISIBLE);
 	   checkVisible(mainButtonsLow, FrameLayout.VISIBLE);
 	   
 	   buttonSensorOnly.setVisibility(Button.INVISIBLE);
 	   buttonOpenLoop.setVisibility(Button.INVISIBLE);
 	   buttonClosedLoop.setVisibility(Button.INVISIBLE);
 	   
 	   // Get the offset in minutes into the current day in the current time zone (based on smartphone time zone setting)
 	   TimeZone tz = TimeZone.getDefault();
 	   int UTC_offset_secs = tz.getOffset(getTimeSeconds()*1000)/1000;
 	   int timeNowMins = (int)((getTimeSeconds()+UTC_offset_secs)/60)%1440;    		
 	   
 	   boolean OLenable = Mode.isOLavailable(getContentResolver());
 	   boolean CLenable = Mode.isCLavailable(getContentResolver());
 	   if (CLenable && Mode.getMode(getContentResolver()) == Mode.OL_ALWAYS_CL_NIGHT_AVAILABLE) {
 		   CLenable = inBrmRange(timeNowMins);
 	   }

 	  if(!Params.getBoolean(getContentResolver(), "apc_enabled", false) && !Params.getBoolean(getContentResolver(), "brm_enabled", false))
 	  {
 		  Debug.i(TAG, FUNC_TAG, "There is no APC or BRM so there is no closed loop!");
 		  CLenable = false;
 	  }
 	   
 	   Debug.i(TAG, FUNC_TAG, "CLenable: "+CLenable+" OLenable: "+OLenable+" mode:"+Mode.getMode(getContentResolver()));
 	   
 	   // Update button visibility
 	   switch (DIAS_STATE) 
 	   {
 	   		case State.DIAS_STATE_STOPPED:
 	   			checkVisible(mainButtonsHigh, FrameLayout.VISIBLE);
 	   			checkVisible(frame1, FrameLayout.INVISIBLE);
 	   			checkVisible(frame2, FrameLayout.VISIBLE);
 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	   			checkVisible(frame4, FrameLayout.INVISIBLE); 	   			
 	   			checkVisible(infoScreen, LinearLayout.VISIBLE);
 	   			checkVisible(infoCGMStatus, LinearLayout.VISIBLE);
 	   			checkVisible(infoExtra, LinearLayout.INVISIBLE);
 	   			
 	   			checkVisible(buttonMeal, Button.GONE);
// 	   			checkVisible(buttonCalibration, Button.INVISIBLE);
 	   			checkVisible(buttonStartTemporaryBasal, Button.INVISIBLE);
 	   			checkVisible(buttonCancelTemporaryBasal, Button.INVISIBLE);
 	   			checkVisible(buttonHypoTreatment, Button.INVISIBLE);
 	   			checkVisible(buttonSensorOnly, Button.GONE);
 	   			checkVisible(buttonOpenLoop, Button.INVISIBLE);
 	   			checkVisible(buttonSafety, Button.INVISIBLE);
 	   			checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   			checkVisible(buttonPlots, Button.INVISIBLE);
 	   			checkVisible(buttonExercise, ToggleButton.INVISIBLE);
 	   			
 	   			Debug.i(TAG, FUNC_TAG, "InsulinComplete: "+insulinSetupComplete);
 	   			
 	   			//if (pumpReadyNoReco() && insulinSetupComplete && batteryReady())
 	   			if (pumpReadyNoReco() && insulinSetupComplete)
 	   			{
 	   				checkVisible(frame2, FrameLayout.VISIBLE);
 	   				checkVisible(buttonSensorOnly, Button.GONE);
 	   				if(OLenable) checkVisible(buttonOpenLoop, Button.VISIBLE);
 	   				//if (cgmReady() && insulinSetupComplete && batteryReady())
 	   				if (cgmReady() && insulinSetupComplete)
 	   				{
 	   					checkVisible(frame3, FrameLayout.VISIBLE);
 	   					if(CLenable)
 	   						checkVisible(buttonClosedLoop, Button.VISIBLE);
 	   					else
 	   						checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   					if(CLenable)
 	   						checkVisible(buttonSafety, Button.VISIBLE);
 	   					else
 	   						checkVisible(buttonSafety, Button.INVISIBLE);
 	   				}
 	 				else {
 						checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   					checkVisible(buttonSafety, Button.INVISIBLE);
 	 				}
 	   			}
 	   			//else if (cgmReady() && batteryReady())
 	   			else if (cgmReady()) 	   			
 	   			{
 	   				checkVisible(frame3, FrameLayout.VISIBLE);
 	   				checkVisible(buttonOpenLoop, Button.GONE);
 	   				checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   				checkVisible(buttonSafety, Button.INVISIBLE);
 	   				checkVisible(buttonSensorOnly, Button.VISIBLE);
    			}
 	   			break;
 	   		case State.DIAS_STATE_CLOSED_LOOP:
 	   			checkVisible(mainButtonsHigh, FrameLayout.VISIBLE);
 	   			checkVisible(frame1, FrameLayout.VISIBLE);
 	   			checkVisible(frame2, FrameLayout.VISIBLE);
 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	   			checkVisible(frame4, FrameLayout.VISIBLE);
 	   			checkVisible(buttonSensorOnly, Button.GONE);
 	   			checkVisible(buttonSafety, Button.VISIBLE);
 	   			checkVisible(buttonClosedLoop, Button.GONE);
 	   			
	   			if (EXERCISING)
	   			{
		   			buttonExercise.setBackgroundResource(R.drawable.button_exercising);
	   				buttonExercise.setText("");
		   		} 
	   			else 
		   		{
	   				buttonExercise.setBackgroundResource(R.drawable.button_not_exercising);
		   			buttonExercise.setText("");
		   		}
	   			checkVisible(buttonExercise, ToggleButton.VISIBLE);
// 	   			if (Params.getBoolean(getContentResolver(), "temporaryBasalRateEnabled", false) && temporaryBasalRateActivityAvailable()) {
 	   			if (Params.getBoolean(getContentResolver(), "temporaryBasalRateEnabled", false)) {
 	   				checkVisible(frame3, FrameLayout.VISIBLE);
 	   				if (temporaryBasalRateActive()) {
 	 	 	   			checkVisible(buttonStartTemporaryBasal, Button.GONE);
 	 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.VISIBLE);
 	   				}
 	   				else {
 	 	 	   			checkVisible(buttonStartTemporaryBasal, Button.VISIBLE);
 	 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.GONE);
 	   				}
 	   			}
 	   			else {
 	 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	 	   			checkVisible(buttonStartTemporaryBasal, Button.INVISIBLE);
 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.INVISIBLE);
 	   			}
	   			
/*
 	   			checkVisible(buttonCalibration, Button.INVISIBLE);
	   			if (cgmReady()) 
	   			{
	   				if(PHONE_CALIB)
	   					checkVisible(buttonCalibration, Button.VISIBLE);
	   			}
*/	   			
	   			checkVisible(buttonHypoTreatment, Button.VISIBLE);
	   			checkVisible(buttonMeal, Button.VISIBLE);
	   			checkVisible(infoScreen, LinearLayout.VISIBLE);
	   			checkVisible(infoCGMStatus, LinearLayout.VISIBLE);
	   			checkVisible(infoExtra, LinearLayout.VISIBLE);
	   			checkVisible(buttonPlots, Button.VISIBLE);
	   			if(OLenable) checkVisible(buttonOpenLoop, Button.VISIBLE);
	   			checkVisible(buttonStop, Button.VISIBLE);
				break;
 	   		case State.DIAS_STATE_SAFETY_ONLY:
 	   			checkVisible(frame1, FrameLayout.VISIBLE);
 	   			checkVisible(frame2, FrameLayout.VISIBLE);
 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	   			checkVisible(frame4, FrameLayout.VISIBLE);
 	   			checkVisible(infoScreen, LinearLayout.VISIBLE);
 	   			checkVisible(infoCGMStatus, LinearLayout.VISIBLE);
 	   			checkVisible(infoExtra, LinearLayout.VISIBLE);
 	   			checkVisible(buttonSensorOnly, Button.GONE);
 	   			checkVisible(buttonOpenLoop, Button.VISIBLE);
 	   			checkVisible(buttonSafety, Button.GONE);
 	   			checkVisible(buttonClosedLoop, Button.VISIBLE);
 	   			checkVisible(buttonStop, Button.VISIBLE);
 	   			
 	   			if (EXERCISING){
 	   				buttonExercise.setBackgroundResource(R.drawable.button_exercising);
 	   				buttonExercise.setText("");
 	   			} else {
 	   				buttonExercise.setBackgroundResource(R.drawable.button_not_exercising);
 	   				buttonExercise.setText("");
 	   			}
 	   			
 	   			checkVisible(buttonExercise, ToggleButton.VISIBLE);
// 	   			if (Params.getBoolean(getContentResolver(), "temporaryBasalRateEnabled", false) && temporaryBasalRateActivityAvailable()) {
 	   			if (Params.getBoolean(getContentResolver(), "temporaryBasalRateEnabled", false)) {
 	   				checkVisible(frame3, FrameLayout.VISIBLE);
 	   				if (temporaryBasalRateActive()) {
 	 	 	   			checkVisible(buttonStartTemporaryBasal, Button.GONE);
 	 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.VISIBLE);
 	   				}
 	   				else {
 	 	 	   			checkVisible(buttonStartTemporaryBasal, Button.VISIBLE);
 	 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.GONE);
 	   				}
 	   			}
 	   			else {
 	 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	 	   			checkVisible(buttonStartTemporaryBasal, Button.INVISIBLE);
 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.INVISIBLE);
 	   			}
/* 	   			
 	   			checkVisible(buttonCalibration, Button.INVISIBLE);
 	   			if (cgmReady()) 
 	   			{
 	   				if(PHONE_CALIB)
 	   				checkVisible(buttonCalibration, Button.VISIBLE);
 	   			}
*/ 	   			
 	   			
 	   			checkVisible(buttonHypoTreatment, Button.VISIBLE);
 	   			checkVisible(buttonMeal, Button.VISIBLE);
 	   			checkVisible(buttonPlots, Button.VISIBLE);
 	   			buttonStop.setClickable(true);
 	   			break;
 	   		case State.DIAS_STATE_OPEN_LOOP:
 	   			checkVisible(mainButtonsHigh, FrameLayout.VISIBLE);
 	   			checkVisible(frame1, FrameLayout.VISIBLE);
 	   			checkVisible(frame2, FrameLayout.VISIBLE);
 	   			if (Params.getBoolean(getContentResolver(), "temporaryBasalRateEnabled", false)) {
 	   				checkVisible(frame3, FrameLayout.VISIBLE);
 	   				if (temporaryBasalRateActive()) {
 	 	 	   			checkVisible(buttonStartTemporaryBasal, Button.GONE);
 	 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.VISIBLE);
 	   				}
 	   				else {
 	 	 	   			checkVisible(buttonStartTemporaryBasal, Button.VISIBLE);
 	 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.GONE);
 	   				}
 	   			}
 	   			else {
 	 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	 	   			checkVisible(buttonStartTemporaryBasal, Button.INVISIBLE);
 	 	   			checkVisible(buttonCancelTemporaryBasal, Button.INVISIBLE);
 	   			}
 	   			checkVisible(frame4, FrameLayout.VISIBLE);
 	   			checkVisible(buttonSensorOnly, Button.GONE);
 	   			checkVisible(buttonOpenLoop, Button.GONE);
 	   			checkVisible(buttonSafety, Button.VISIBLE);
 	   			
 	   			if (EXERCISING)
 	   			{
 		   			buttonExercise.setBackgroundResource(R.drawable.button_exercising);
 	   				buttonExercise.setText("");
 		   		} 
 	   			else 
 		   		{
 	   				buttonExercise.setBackgroundResource(R.drawable.button_not_exercising);
 		   			buttonExercise.setText("");
 		   		}
 	   			
 	   			checkVisible(buttonExercise, ToggleButton.VISIBLE);
/* 	   			
 	   			checkVisible(buttonCalibration, Button.INVISIBLE);
 	   			
 	   			if (cgmReady()) 
 	   			{
 	   				if(PHONE_CALIB)
 	   					checkVisible(buttonCalibration, Button.VISIBLE);
 	   			}
*/ 	   			
 	   			
 	   			checkVisible(buttonHypoTreatment, Button.VISIBLE);
 	   			checkVisible(buttonMeal, Button.VISIBLE);
 	   			checkVisible(infoScreen, LinearLayout.VISIBLE);
 	   			checkVisible(infoCGMStatus, LinearLayout.VISIBLE);
 	   			checkVisible(infoExtra, LinearLayout.VISIBLE);
 	   			checkVisible(buttonPlots, Button.VISIBLE);
 	   			
 				//if (cgmReady() && batteryReady()) 
 	   			if (cgmReady()) 
 				{
 					if(CLenable)
 						checkVisible(buttonClosedLoop, Button.VISIBLE);
 					else
 						checkVisible(buttonClosedLoop, Button.INVISIBLE);
	   				if(CLenable)
	   					checkVisible(buttonSafety, Button.VISIBLE);
	   				else
	   					checkVisible(buttonSafety, Button.INVISIBLE);
	   				
 				}
 				else {
					checkVisible(buttonClosedLoop, Button.INVISIBLE);
   					checkVisible(buttonSafety, Button.INVISIBLE);
 				}
 				checkVisible(buttonStop, Button.VISIBLE);	  	
 				break;
 	   		case State.DIAS_STATE_SENSOR_ONLY:
 	   			checkVisible(mainButtonsHigh, FrameLayout.VISIBLE);
 	   			checkVisible(frame1, FrameLayout.VISIBLE);
 	   			checkVisible(frame2, FrameLayout.VISIBLE);
 	   			checkVisible(frame3, FrameLayout.INVISIBLE);
 	   			checkVisible(frame4, FrameLayout.VISIBLE);
 	   			checkVisible(buttonSafety, Button.GONE);
 	   			checkVisible(buttonOpenLoop, Button.INVISIBLE);
 	   			checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   			checkVisible(buttonSensorOnly, Button.GONE);
 	   			checkVisible(buttonExercise, ToggleButton.INVISIBLE);
 	   			checkVisible(buttonStartTemporaryBasal, Button.INVISIBLE);
 	   			checkVisible(buttonCancelTemporaryBasal, Button.INVISIBLE);
// 	   			checkVisible(buttonCalibration, Button.INVISIBLE);
 	   			// =====
 	   			//if (pumpReadyNoReco() && insulinSetupComplete && batteryReady())
 	   			if (pumpReadyNoReco() && insulinSetupComplete)
	   			{
 	   				Debug.i(TAG, FUNC_TAG, "=== Pump in Sensor Only");
	   				//checkVisible(frame2, FrameLayout.VISIBLE);
	   				//checkVisible(buttonSensorOnly, Button.GONE);
	   				if(OLenable)
	   				{
	   					checkVisible(buttonOpenLoop, Button.VISIBLE);
	   				}
	   				if (cgmReady())
	   				{
	   					Debug.i(TAG, FUNC_TAG, "=== Pump+CGM in Sensor Only");
	   					//checkVisible(frame3, FrameLayout.VISIBLE);
	   					if(CLenable) checkVisible(buttonClosedLoop, Button.VISIBLE);
	   					if(CLenable) checkVisible(buttonSafety, Button.VISIBLE);
	   				}
	   			}
	   			else if (cgmReady())
	   			{
	   				Debug.i(TAG, FUNC_TAG, "=== CGM in Sensor Only");
/*	   				
	   				if(PHONE_CALIB)
	   				{
 	   					checkVisible(buttonCalibration, Button.VISIBLE);
	   				}
*/	   				
	   				//if(batteryReady()) 
	   				if(true) 
	   				{
	   					Debug.i(TAG, FUNC_TAG, "=== CGM+Battery in Sensor Only");
		   				//checkVisible(frame3, FrameLayout.VISIBLE);
		   				checkVisible(buttonOpenLoop, Button.INVISIBLE);
		   				checkVisible(buttonClosedLoop, Button.INVISIBLE);
		   				//checkVisible(buttonSafety, Button.INVISIBLE);
		   				//checkVisible(buttonSensorOnly, Button.VISIBLE);
	   				}
	   			}
 	   			// =====
 	   			/*if (cgmReady()) 
 	   			{
 	   				
 	   				if(pumpReady() && insulinSetupComplete && batteryReady())
 	   				{
 	   					if(OLenable) checkVisible(buttonOpenLoop, Button.VISIBLE);
 	   					if(CLenable) checkVisible(buttonClosedLoop, Button.VISIBLE);
 	   				}
 	   			}*/
 	   			checkVisible(buttonHypoTreatment, Button.VISIBLE);
 	   			checkVisible(buttonMeal, Button.GONE);
 	   			checkVisible(infoScreen, LinearLayout.VISIBLE);
 	   			checkVisible(infoCGMStatus, LinearLayout.VISIBLE);
 	   			checkVisible(infoExtra, LinearLayout.VISIBLE);
 	   			//checkVisible(buttonClosedLoop, Button.INVISIBLE);
 	   			checkVisible(buttonPlots, Button.VISIBLE);
 				checkVisible(buttonStop, Button.VISIBLE);
 				break;
 	   		default:
 	   			break;
 	   }
 	   
 	   stop = System.currentTimeMillis();
 	   Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
   	    
   	public void mainGroupHide() 
   	{
 	   FrameLayout frameMidHighButtons = (FrameLayout)findViewById(R.id.frameMidHighButtons);
   	   FrameLayout frameMidLowButtons = (FrameLayout)findViewById(R.id.frameMidLowButtons);
   	   frameMidHighButtons.setVisibility(FrameLayout.INVISIBLE);
   	   frameMidLowButtons.setVisibility(FrameLayout.INVISIBLE);
     }
   	   	
   	/************************************************************************************************************************/
	//  Button Confirmation Functions (Pop-up Yes/No dialogs)
	/************************************************************************************************************************/
   	   	
   	public void stopConfirm() {
   		final String FUNC_TAG = "stopConfirm";
   		
   		Debug.i(TAG, "IO_TEST", "Stop Confirm Button Click!");
   		
   		Debug.i(TAG, FUNC_TAG, "STOP CONFIRM");
	    Intent intent1 = new Intent();
		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_STOP_CLICK);
		startService(intent1); 		
   	}
   	   	
   	public void openLoopConfirm(){
   		final String FUNC_TAG = "openLoopConfirm";
   		
   		Debug.i(TAG, "IO_TEST", "Pump Mode Confirm Button Click!");
   		Debug.i(TAG, FUNC_TAG, "OPEN LOOP CONFIRM");
   		
   		final Intent intent1 = new Intent();
		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_OPEN_LOOP_CLICK);
		startService(intent1);		
   	}
   	   	
   	public void safetyConfirm(){
   		final String FUNC_TAG = "openLoopConfirm";
   		
   		Debug.i(TAG, "IO_TEST", "Safety Confirm Button Click!");
   		Debug.i(TAG, FUNC_TAG, "SAFETY CONFIRM");
   		
   		final Intent intent1 = new Intent();
		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_SAFETY_CLICK);
		startService(intent1);		
   	}
   	   	
   	public void closedLoopConfirm(){
		// Get the offset in minutes into the current day in the current time zone (based on cell phone time zone setting)
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(getTimeSeconds()*1000)/1000;
		int timeNowMins = (int)((getTimeSeconds()+UTC_offset_secs)/60)%1440;
		
		Debug.i(TAG, "IO_TEST", "Closed Loop Confirm Button Click!");
		
		//TODO: check that DiAs Service is responsible for this, as it should be
//		if ( FDA_MANDATED_SAFETY_ONLY_OPERATION_ENABLED && timeIsInSafetyRange(timeNowMins)) {
//	   		DIAS_STATE = State.DIAS_STATE_SAFETY_ONLY;
//		}
//		else {
//	   		DIAS_STATE = State.DIAS_STATE_CLOSED_LOOP;
//		}
		
		final Intent intent1 = new Intent();
		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_CLOSED_LOOP_CLICK);  
		startService(intent1);		
   	}
   	   	
   	public void sensorOnlyConfirm(){
   		
   		Debug.i(TAG, "IO_TEST", "Sensory Only Confirm Button Click!");
   		
		Intent intent1 = new Intent();
		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_START_SENSOR_ONLY_CLICK);
		startService(intent1);		 		
   	}
   	   	
   	public void goToSetupScreen(int screen){
   		final String FUNC_TAG = "configConfirm";
   		
		Debug.i(TAG, FUNC_TAG, "screen="+screen);		
		
		Intent i = new Intent();
		i.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsSetup.DiAsSetup1"));
		i.putExtra("setupScreenIDNumber", screen);
		startActivity(i);
		
		int pid = android.os.Process.myPid();
		android.os.Process.killProcess(pid);		
	}
   	   	
   	public void exerciseConfirm(){ 
   		final String FUNC_TAG = "exerciseConfirm";
   		
        Button exercise = (Button) findViewById(R.id.buttonExercise);
        boolean exerPress = false;
        
        if (EXERCISING)
        {
        	exerPress = false; 	   
     	   	exercise.setBackgroundResource(R.drawable.button_not_exercising);
     	   	exercise.setText("");
        } 
        else 
        {
        	exerPress = true;
     	   	exercise.setBackgroundResource(R.drawable.button_exercising);
     	   	exercise.setText("");
        }
    	   
        if (exerPress) 
        {
        	Debug.i(TAG, FUNC_TAG, "Exercising");
        	Intent intent1 = new Intent();
        	intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
    		intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_SET_EXERCISE_STATE);
    		intent1.putExtra("currentlyExercising", true);
    		startService(intent1);
        }
        else 
        {
        	Debug.i(TAG, FUNC_TAG, "Not exercising");		   
        	Intent intent1 = new Intent();
        	intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
        	intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_SET_EXERCISE_STATE);
        	intent1.putExtra("currentlyExercising", false);
        	startService(intent1);
        }
     }
   	   	
   	public void hypoTreatmentConfirm(boolean didTreatHypo)
    {
   		final String FUNC_TAG = "hypoTreatmentConfirm";
   		
   		if(didTreatHypo)
   		{
   			Debug.i(TAG, FUNC_TAG, "Adding Hypo event...");
	   		Bundle b = new Bundle();
			b.putString("description", "Hypo treatment button pressed");
			Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_HYPO_TREATMENT, Event.makeJsonString(b), Event.SET_LOG);
   		}   	
    }
   	     
    public void homeConfirm(){
 		Intent homeIntent =  new Intent(Intent.ACTION_MAIN, null);
 		homeIntent.addCategory(Intent.CATEGORY_HOME);
 		homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
 		startActivity(homeIntent);
 		sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));    	
     }
    
   	/************************************************************************************************************************/
	//  UI Alarm Pop-up Functions (Hypo, No CGM, etc.)
	/************************************************************************************************************************/
   	
   	public void showAlarmActivity(int alarmType)
   	{
   		final String FUNC_TAG = "showAlarmActivity";
   		
   		if(!alarmActivityRunning)
   		{
	   		Debug.i(TAG, FUNC_TAG, "Alarm sounding!  Opening AlarmActivity... (ID: "+alarmType+")");
	   		alarmActivityRunning = true;
	   		
	   		Intent alarmDisplay = new Intent();
	 		alarmDisplay.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.AlarmActivity"));
	 		alarmDisplay.putExtra("height", midFrameH);
	 		alarmDisplay.putExtra("width", midFrameW);
	 		alarmDisplay.putExtra("state", DIAS_STATE);
	 		alarmDisplay.putExtra("alarmType", alarmType);
	 		startActivityForResult(alarmDisplay, ALARM);
   		}
   		else
   			Debug.i(TAG, FUNC_TAG, "Alarm activity already running!");
   	}
	
    /************************************************************************************************************************/
	//  Utility Functions (Sounds, DB checks, time, etc.)
	/************************************************************************************************************************/
    
    public void newSubject()
   	{
    	final String FUNC_TAG = "newSubject";
    	//This is called when a new subject is started (Context Menu button)
    	
		//DIAS_STATE = State.DIAS_STATE_STOPPED;
		// Delete everything in the biometricsContentProvider
		main.getContentResolver().delete(Uri.parse("content://"+ Biometrics.PROVIDER_NAME + "/all"), null, null);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Reboot required")
				.setMessage("The database has been cleared. Please reboot the phone.")
				.setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						// Do nothing
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
		
   	}
    
    public void soundClink() {
   	 	MediaPlayer mMediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.tapclink1);
   	 	mMediaPlayer.start();    	 
    }

    public void soundClick() {
//   	 	MediaPlayer mMediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.tapclick);
//   	 	mMediaPlayer.start();    	 
    }

    public boolean inSimMode()
    {
    	if(SIM_TIME > 0)
    		return true;
    	else
    		return false;
    }
    
	public boolean inBrmRange(int timeNowMins) 
	{
		final String FUNC_TAG = "inBrmRange";
//		Tvector safetyRanges = DiAsSubjectData.getInstance().subjectSafety;
		Tvector safetyRanges = new Tvector(12);
		if (readTvector(safetyRanges, Biometrics.USS_BRM_PROFILE_URI, this)) {
			for (int i = 0; i < safetyRanges.count(); i++) 
			{
				int t = safetyRanges.get_time(i).intValue();
				int t2 = safetyRanges.get_end_time(i).intValue();
				
				if (t > t2)			//Handle case of range over midnight
				{ 					
					t2 += 24*60;
				}
				
				if ((t <= timeNowMins && timeNowMins <= t2) || (t <= (timeNowMins + 1440) && (timeNowMins + 1440) <= t2))
				{
					return true;
				}
			}
			return false;			
		}
		else {
			return false;
		}
	}
	
	public boolean readTvector(Tvector tvector, Uri uri, Context calling_context) {
		boolean retvalue = false;
		Cursor c = calling_context.getContentResolver().query(uri, null, null, null, null);
		long t, t2 = 0;
		double v;
		if (c.moveToFirst()) {
			do {
				t = c.getLong(c.getColumnIndex("time"));
				if (c.getColumnIndex("endtime") < 0){
					v = c.getDouble(c.getColumnIndex("value"));
					Log.i(TAG, "readTvector: t=" + t + ", v=" + v);
					tvector.put_with_replace(t, v);
				} else if (c.getColumnIndex("value") < 0){
					Log.i(TAG, "readTvector: t=" + t + ", t2=" + t2);
					t2 = c.getLong(c.getColumnIndex("endtime"));
					tvector.put_time_range_with_replace(t, t2);
				}
			} while (c.moveToNext());
			retvalue = true;
		}
		c.close();
		return retvalue;
	}

	public long getTimeSeconds() 
	{
		if (SIM_TIME > 0) 
			return SIM_TIME;			//Simulated time passed on timer tick
		else 
			return (long)(System.currentTimeMillis()/1000);
	}
	
	private void startNoCgmWatchdogTimer() 
	{
		final String FUNC_TAG = "startNoCgmWatchdogTimer";
		
   		NoCgmWatchdogTimer = new Timer();
   		NoCgmWatchdogTimerTask = new TimerTask() 
   		{
    		public void run() 
    		{
   			    Intent intent = new Intent();
   				intent.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
   				intent.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_STOP_CLICK);
   				startService(intent);
   				main.removeDialog(DIALOG_CLOSED_LOOP_NO_CGM);					
	    		log_action(TAG, "NoCgmWatchdogTimer > Timed Out");
	        	Debug.i(TAG, FUNC_TAG, "Timed out");
    		}
		};
		
		long timeout;
		
		if (inSimMode())
			timeout = (long)(NO_CGM_WATCHDOG_TIMEOUT_SECONDS/10)*1000;
		else
			timeout = (long)(NO_CGM_WATCHDOG_TIMEOUT_SECONDS)*1000;
		
		long timeout_seconds = timeout/1000;
		NoCgmWatchdogTimer.schedule(NoCgmWatchdogTimerTask,timeout);
		Debug.i(TAG, FUNC_TAG, "Started > , timeout="+timeout_seconds+" seconds");           				
   }

	private void cancelNoCgmWatchdogTimer() {
		final String FUNC_TAG = "cancelNoCgmWatchdogTimer";
		
		if (NoCgmWatchdogTimer != null) 
		{
			NoCgmWatchdogTimer.cancel();
			Debug.i(TAG, FUNC_TAG, "Canceled");       				
		}
	}
    
    public boolean mealActivityAvailable()
   	{
   		//Does a quick scan to check if the MealService application is installed, if so it returns true
   		final PackageManager pm = this.getPackageManager();
		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

		for(ApplicationInfo a: packages)
		{
			if(a.packageName.equalsIgnoreCase("edu.virginia.dtc.MealActivity"))
			{
				return true;
			}
		}
   		return false;
   	}
    
    public boolean standaloneDriverAvailable()
   	{
   		//Does a quick scan to check if the StandaloneDriver application is installed, if so it returns true
   		final PackageManager pm = this.getPackageManager();
		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

		for(ApplicationInfo a: packages)
		{
			if(a.packageName.equalsIgnoreCase("edu.virginia.dtc.standaloneDriver"))
			{
				return true;
			}
		}
   		return false;
   	}
    
    public boolean temporaryBasalRateActivityAvailable()
   	{		
   		//Does a quick scan to check if the APCService temporaryBasalRateActivity is supported by the ControllerPackage, if so it returns true
    	String controllerPackageName = new String("edu.virginia.dtc.APCservice");
		String activityName = Params.getString(getContentResolver(), "temporaryBasalRateActivity", "null");
    	if (Params.getBoolean(getContentResolver(), "temporaryBasalRateEnabled", false)) {
       		final PackageManager pm = this.getPackageManager();
    		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

    		for(ApplicationInfo a: packages)
    		{
    			if(a.packageName.equalsIgnoreCase(controllerPackageName))
    			{
    	    		try {
        	    		PackageInfo pi = pm.getPackageInfo(controllerPackageName, PackageManager.GET_ACTIVITIES);
        	    		if(pi.activities != null) {
	        	    		for (ActivityInfo ai: pi.activities) {
	        	    			if(ai.name.equalsIgnoreCase(activityName)) {
	        	    				return true;
	        	    			}
	        	    		}
        	    		}
    	    		}
    	    		catch (PackageManager.NameNotFoundException e) {
         				Bundle b = new Bundle();
         	    		b.putString("description", "DiAsMain > PackageManager.NameNotFoundException: "+controllerPackageName+", "+e.getMessage());
         	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_LOG);
    	    		}
    			}
    		}
    	}
   		return false;
   	}
    
	private void cancelTemporaryBasalRate() {
		final String FUNC_TAG = "cancelTemporaryBasalRate";
	 	Button buttonStartTemporaryBasal = (Button)this.findViewById(R.id.buttonStartTemporaryBasal);
	 	Button buttonCancelTemporaryBasal = (Button)this.findViewById(R.id.buttonCancelTemporaryBasal);
  		checkVisible(buttonStartTemporaryBasal, Button.VISIBLE);
  		checkVisible(buttonCancelTemporaryBasal, Button.GONE);

  		ContentValues values = new ContentValues();
	    long time = getTimeSeconds();
	    values.put("actual_end_time", time);
	    values.put("status_code", TempBasal.TEMP_BASAL_MANUAL_CANCEL);	    
		Bundle b = new Bundle();
		try 
	    {
	    	getContentResolver().update(Biometrics.TEMP_BASAL_URI, values, null, null);
 	    	b.putString("description", "DiAsMain > cancelTempBasalDelivery, time= "+time);
 	    	Event.addEvent(getApplicationContext(), Event.EVENT_TEMP_BASAL_CANCELED, Event.makeJsonString(b), Event.SET_LOG);
	    }
	    catch (Exception e) 
	    {
	    	Debug.e("Error", FUNC_TAG,(e.getMessage() == null) ? "null" : e.getMessage());
 	    	b.putString("description", "DiAsMain > cancelTempBasalDelivery failed, time= "+time+", "+e.getMessage());
 	    	Event.addEvent(getApplicationContext(), Event.EVENT_TEMP_BASAL_CANCELED, Event.makeJsonString(b), Event.SET_LOG);
	    }
		Toast.makeText(getApplicationContext(), FUNC_TAG+", actual_end_time="+values.getAsLong("actual_end_time"), Toast.LENGTH_SHORT).show();
	}
	
	private boolean temporaryBasalRateActive() {
		final String FUNC_TAG = "temporaryBasalRateActive";
		boolean retValue = false;
		Cursor c = getContentResolver().query(Biometrics.TEMP_BASAL_URI, null, null, null, null);
       	if(c!=null)
       	{
       		if(c.moveToLast()) {
       			long time = getTimeSeconds();
       			long start_time = c.getLong(c.getColumnIndex("start_time"));
       			long scheduled_end_time = c.getLong(c.getColumnIndex("scheduled_end_time"));
       			int status_code = c.getInt(c.getColumnIndex("status_code"));
       			if(time >= start_time && time <= scheduled_end_time && status_code == TempBasal.TEMP_BASAL_RUNNING)
       				retValue = true;
       		}
   			c.close();
       	}
		return retValue;
	}
	
    private boolean isMyServiceRunning() {
    	final String FUNC_TAG = "isMyServiceRunning";
    	
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
        	Debug.i(TAG, FUNC_TAG, service.service.getClassName());
            if ("edu.virginia.dtc.DiAsService.DiAsService".equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    /************************************************************************************************************************/
	//  Log Functions
	/************************************************************************************************************************/
    
 	public void log_action(String service, String action) {
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", service);
        i.putExtra("Status", action);
        i.putExtra("time", (long)(System.currentTimeMillis()/1000));
        sendBroadcast(i);
	}
}
