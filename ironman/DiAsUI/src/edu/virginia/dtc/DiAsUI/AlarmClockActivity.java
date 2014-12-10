package edu.virginia.dtc.DiAsUI;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import edu.virginia.dtc.DiAsUI.DiAsMain.SystemObserver;
import edu.virginia.dtc.SysMan.Debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AnalogClock;
import android.widget.TextView.OnEditorActionListener;

public class AlarmClockActivity extends Activity implements OnGestureListener {
	GestureDetector gestureScanner;
	
	public static final String TAG = "AlarmClockActivity";
	public static final boolean DEBUG = true;
    public static final String IO_TEST_TAG = "AlarmClockActivityIO";
    
	private static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
    public static final Uri CGM_URI = Uri.parse("content://"+ PROVIDER_NAME + "/cgm");
    public static final Uri SUBJECT_DATA_URI = Uri.parse("content://"+ PROVIDER_NAME + "/subjectdata");
    public static final Uri CF_PROFILE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/cfprofile");
    public static final Uri CR_PROFILE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/crprofile");
    public static final Uri BASAL_PROFILE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/basalprofile");
    public static final Uri MEAL_URI = Uri.parse("content://"+ PROVIDER_NAME + "/meal");
    public static final Uri PUMP_DETAILS_URI = Uri.parse("content://" + PROVIDER_NAME + "/pumpdetails");
    public static final Uri SYSTEM_URI = Uri.parse("content://"+ PROVIDER_NAME + "/system");
    	
	public static final int DIAS_STATE_STOPPED = 0;
	public static final int DIAS_STATE_OPEN_LOOP = 1;
	public static final int DIAS_STATE_CLOSED_LOOP = 2;
	public static final int DIAS_STATE_SAFETY_ONLY = 3;
	public static final int DIAS_STATE_SENSOR_ONLY = 4;
	
	// CGM states of operation
	public static final int CGM_NULL = -1;
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

	private int DIAS_STATE, APC_TYPE, PUMP_STATE;
  	private long SIM_TIME;
	
	private SystemObserver sysObserver;
	public BroadcastReceiver TickReceiver;
			
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";
		
		super.onCreate(savedInstanceState);
        gestureScanner = new GestureDetector(this);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.default_alarm_clock_screen);
		
		
		if (getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE){
			WindowManager.LayoutParams params = getWindow().getAttributes();
			params.height = getIntent().getIntExtra("height", 100);
			params.width = getIntent().getIntExtra("width", 100);
			SIM_TIME = getIntent().getLongExtra("SIM_TIME", -1);
			
			params.height = 480;
			params.width = 854;
			
			Debug.i(TAG, FUNC_TAG, "HEIGHT: "+getIntent().getIntExtra("height", 100)+" WIDTH: "+getIntent().getIntExtra("width", 100));
			
			ViewGroup.LayoutParams lParams = this.findViewById(R.id.defaultAlarmClockLayout).getLayoutParams();
			
			lParams.height = params.height;
//			lParams.height -= (0.07*lParams.height);
			
			lParams.width = params.width;
//			lParams.width -= (0.07*lParams.width);

			params.gravity=Gravity.TOP;
			
//			(this.findViewById(R.id.defaultAlarmClockLayout)).setLayoutParams(lParams);
			
			this.getWindow().setAttributes(params);
			}
			
			if (getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT){
				WindowManager.LayoutParams params = getWindow().getAttributes();
				params.gravity=Gravity.BOTTOM;
			}
			
		Debug.i(TAG, FUNC_TAG, "OnCreate");
		
		setResult(RESULT_CANCELED);		//Set the result to cancelled unless we actually send a bolus with the UI
		
        //Gather DiAs state and the APC type
		DIAS_STATE = getIntent().getIntExtra("state", DIAS_STATE_STOPPED);

		Debug.i(TAG, FUNC_TAG, "STATE: "+DIAS_STATE);
		
		initAlarmScreen();
		
		// This is the main method of UI update now, it listens to changes on the SYSTEM table
		sysObserver = new SystemObserver(new Handler());
		getContentResolver().registerContentObserver(SYSTEM_URI, true, sysObserver);
		registerReceivers();
		// Update on entry
		update();
		
		//Setup the UI
		switch(DIAS_STATE)
		{
			case DIAS_STATE_OPEN_LOOP:
				alarmScreenOpenLoop();
				break;
			case DIAS_STATE_CLOSED_LOOP:
				alarmScreenClosedLoop();
				break;
		}
	}
	
	
	
	@Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
		final String FUNC_TAG = "onWindowFocusChanged";
    	super.onWindowFocusChanged(hasFocus);
    	
    	if(hasFocus)
    	{
//    		Debug.i(TAG, FUNC_TAG, "M_HEIGHT: "+this.findViewById(R.id.defaultAlarmClockLayout).getHeight()+" M_WIDTH: "+this.findViewById(R.id.defaultAlarmClockLayout).getWidth());
    	}
    }
	
	@Override
	public void onStart()
	{
		super.onStart();
	}
	
	@Override
	public void onDestroy()
	{
		final String FUNC_TAG = "onDestroy";
		
		super.onDestroy();
		Debug.i(TAG, FUNC_TAG, "");
		finish();
	}

    /************************************************************************************************************************/
	//  Gesture Listeners
	/************************************************************************************************************************/
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // TODO Auto-generated method stub
        return gestureScanner.onTouchEvent(event);
    }

    public boolean onDown(MotionEvent e) {
        // TODO Auto-generated method stub
    	final String FUNC_TAG = "onDown";
//    	Debug.i(TAG, FUNC_TAG, "finish()");
//    	finish();
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
            float velocityY) {
//        Log.i("Test", "On Fling");
    	finish();
        return true;
    }

    public void onLongPress(MotionEvent e) {
        // TODO Auto-generated method stub

    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
            float distanceY) {
        // TODO Auto-generated method stub
        return true;
    }

    public void onShowPress(MotionEvent e) {
        // TODO Auto-generated method stub

    }

    public boolean onSingleTapUp(MotionEvent e) {
        // TODO Auto-generated method stub
        return false;
    }

    
	private void initAlarmScreen()
	{
		final String FUNC_TAG = "initAlarmScreen";
		
		 		
	}
	
	public void alarmScreenClosedLoop() 
	{
		final String FUNC_TAG = "alarmScreenClosedLoop";
		
	   Debug.i(TAG, FUNC_TAG, "Default Closed-Loop Mode Alarm Screen");
	   
//	   this.findViewById(R.id.mealIobLayout).setVisibility(View.GONE);
//	   this.findViewById(R.id.mealCorrLayout).setVisibility(View.GONE);
	   
	}

	public void alarmScreenOpenLoop() 
	{
		final String FUNC_TAG = "alarmScreenOpenLoop";
		
	   Debug.i(TAG, FUNC_TAG, "Default Open-Loop Mode Alarm Screen");
	   
	}
	
    /************************************************************************************************************************/
	//  UI and System Update Functions
	/************************************************************************************************************************/
    
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
    	public boolean deliverSelfNotifications() {
    		return false;
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
  	
    	Cursor c = getContentResolver().query(SYSTEM_URI, null, null, null, null);
    	
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
    	
    	String hypoLight = c.getString(c.getColumnIndex("hypoLight"));
    	String hyperLight = c.getString(c.getColumnIndex("hyperLight"));
    	
    	double apcBolus = c.getDouble(c.getColumnIndex("apcBolus"));
    	int apcStatus = c.getInt(c.getColumnIndex("apcStatus"));
    	int apcType = c.getInt(c.getColumnIndex("apcType"));
    	String apcString = c.getString(c.getColumnIndex("apcString"));
    	
    	boolean exercising = (c.getInt(c.getColumnIndex("exercising"))==1) ? true : false;
    	boolean alarmNoCgm = (c.getInt(c.getColumnIndex("alarmNoCgm"))==1) ? true : false;
    	boolean alarmHypo = (c.getInt(c.getColumnIndex("alarmHypo"))==1) ? true : false;
    	
    	Debug.i(TAG, FUNC_TAG, "time: "+time+" sysTime: "+sysTime+" diasState: "+diasState+" battery: "+battery+" safetyMode: "+safetyMode);
    	Debug.i(TAG, FUNC_TAG, "cgmValue: "+cgmValue+" cgmTrend: "+cgmTrend+" cgmLastTime: "+cgmLastTime+" cgmState: "+cgmState+" cgmStatus: "+cgmStatus);
    	Debug.i(TAG, FUNC_TAG, "pumpLastBolus: "+pumpLastBolus+" pumpLastBolusTime: "+pumpLastBolusTime+" pumpState: "+pumpState+" pumpStatus: "+pumpStatus);
    	Debug.i(TAG, FUNC_TAG, "hypoLight: "+hypoLight+" hyperLight: "+hyperLight);
    	Debug.i(TAG, FUNC_TAG, "apcBolus: "+apcBolus+" apcStatus: "+apcStatus+" apcType: "+apcType+" apcString: "+apcString);
    	Debug.i(TAG, FUNC_TAG, "exercising: "+exercising+" alarmNoCgm: "+alarmNoCgm+" alarmHypo: "+alarmHypo);
    	Debug.i(TAG, FUNC_TAG, "----------------------------------------------------------------------------------------------------------------------------------");
    	
    	if (alarmHypo) {
    		Debug.i(TAG, FUNC_TAG, "alarmHypo == true, return to main UI");
    		finish();
    	}
    	else if (alarmNoCgm) {
    		Debug.i(TAG, FUNC_TAG, "alarmNoCgm == true, return to main UI");
    		finish();
    	}
    	updateCgm(cgmValue, cgmTrend, cgmLastTime, cgmState, hypoLight);

    	
    	//Call functions to update the UI each time there is a change in the SYSTEM table
/*
    	updateDiasState(diasState);
    	
    	updateLastBolus(diasState, pumpLastBolus, pumpLastBolusTime);
    	
    	updateTrafficLights(diasState, hypoLight, hyperLight, alarmHypo);
    	
    	updateCgm(cgmValue, cgmTrend, cgmLastTime, cgmState, alarmNoCgm);
    	
    	//Update constants for system
    	PREV_DIAS_STATE = DIAS_STATE;
    	PREV_CGM_VALUE = CGM_VALUE;
    	PREV_CGM_STATE = CGM_STATE;
    	PREV_BATTERY = BATTERY;
    	PREV_ENABLE_IO = ENABLE_IO;
    	PREV_EXERCISING = EXERCISING;
    	
    	DIAS_STATE = diasState;
    	CGM_VALUE = cgmValue;
    	CGM_STATE = cgmState;
    	BATTERY = battery;
    	ENABLE_IO = enableIOTest;
    	EXERCISING = exercising;
    	
    	//Update the DiAs UI state (show/hide objects)
    	updateDiasMain();
*/    	
    	stop = System.currentTimeMillis();
    	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    private void updateCgm(double cgmValue, int cgmTrend, long cgmLastTime, int cgmState, String hypoLight)
    {
    	final String FUNC_TAG = "updateCgm";
    	long start = System.currentTimeMillis();
    	long stop;

    	TextView textViewCGM = (TextView)this.findViewById(R.id.textViewAlarmClockCGM);
		TextView textViewCGMsuffix = (TextView)this.findViewById(R.id.textViewAlarmClockCGMsuffix);
		TextView textViewCGMTime = (TextView)this.findViewById(R.id.textViewAlarmClockCGMTime);
		
		DecimalFormat decimalFormat = new DecimalFormat();
		decimalFormat.setMinimumFractionDigits(0);
		decimalFormat.setMaximumFractionDigits(0);
		String CGMString = decimalFormat.format(cgmValue);
		
		int minsAgo = (int)(getTimeSeconds()/60 - cgmLastTime);
		if (minsAgo < 0)
			minsAgo = 0;
		String minsString = (minsAgo == 1) ? "min" : "mins";

		switch(cgmState)
		{
			case CGM_NORMAL:
				// We no longer enforce hard limits here since the DiAs Service 
				// will already do that and is capable of adjusting based on device parameters
				Paint paint = new Paint();
				if (hypoLight.contentEquals("g")) {
					textViewCGM.setTextColor(Color.GREEN);
					textViewCGMsuffix.setTextColor(Color.GREEN);
				}
				else if (hypoLight.contentEquals("o")) {
					textViewCGM.setTextColor(Color.YELLOW);
					textViewCGMsuffix.setTextColor(Color.YELLOW);
				}
				else if (hypoLight.contentEquals("r")) {
					textViewCGM.setTextColor(Color.RED);
					textViewCGMsuffix.setTextColor(Color.RED);
				}
				else {
					textViewCGM.setTextColor(Color.WHITE);
					textViewCGMsuffix.setTextColor(Color.WHITE);
				}
				textViewCGM.setText(CGMString);
//				textViewCGM.setText(CGMString+" mg/dl");
				textViewCGMTime.setText((minsAgo == 0) ? "" : (minsAgo + " " + minsString + " ago"));
				break;
			case CGM_DATA_ERROR:
				textViewCGM.setText("Data Error");
				break;
			case CGM_NOT_ACTIVE:
				textViewCGM.setText("Not Active");
				break;
			case CGM_NONE:
				textViewCGM.setText("No CGM");
				break;
			case CGM_NOISE:
				textViewCGM.setText("CGM Noise");
				break;
			case CGM_WARMUP:
				textViewCGM.setText("Warmup");
				break;
			case CGM_CALIBRATION_NEEDED:
				textViewCGM.setText("Calibrate");
				break;
			case CGM_DUAL_CALIBRATION_NEEDED:
				textViewCGM.setText("Calibrate");
				break;
			case CGM_CAL_LOW:
				textViewCGM.setText("Cal Low");
				break;
			case CGM_CAL_HIGH:
				textViewCGM.setText("Cal High");
				break;
			case CGM_SENSOR_FAILED:
				textViewCGM.setText("CGM Sensor Failed");
				break;
		}
/*				
		ImageView imageViewArrow = (ImageView)this.findViewById(R.id.imageViewArrow);
		switch (cgmTrend)
		{
 			case 2:
 				imageViewArrow.setBackgroundResource(R.drawable.arrow_2);
 				imageViewArrow.setVisibility(ImageView.VISIBLE);
 				textViewCGMTime.setVisibility(TextView.VISIBLE);
 				break;
 			case 1:
 				imageViewArrow.setBackgroundResource(R.drawable.arrow_1);
 				imageViewArrow.setVisibility(ImageView.VISIBLE);
 				textViewCGMTime.setVisibility(TextView.VISIBLE);
 				break;
 			case 0:
 				imageViewArrow.setBackgroundResource(R.drawable.arrow_0);
 				imageViewArrow.setVisibility(ImageView.VISIBLE);
 				textViewCGMTime.setVisibility(TextView.VISIBLE);
 				break;
 			case -1:
 				imageViewArrow.setBackgroundResource(R.drawable.arrow_m1);
 				imageViewArrow.setVisibility(ImageView.VISIBLE);
 				textViewCGMTime.setVisibility(TextView.VISIBLE);
 				break;
 			case -2:
 				imageViewArrow.setBackgroundResource(R.drawable.arrow_m2);
 				imageViewArrow.setVisibility(ImageView.VISIBLE);
 				textViewCGMTime.setVisibility(TextView.VISIBLE);
 				break;
 			case 5:
 				imageViewArrow.setVisibility(ImageView.INVISIBLE);
 				break;
 			default:
 				imageViewArrow.setVisibility(ImageView.INVISIBLE);
 				textViewCGMTime.setVisibility(TextView.INVISIBLE);
		}
*/		
		stop = System.currentTimeMillis();
    	Debug.i(TAG, FUNC_TAG, "Update Complete..."+(stop-start)+" ms to complete!");
    }
    
    private void registerReceivers() 
    {
        // Register to receive Supervisor Time Tick
        TickReceiver = new BroadcastReceiver() 
        {
        	final String FUNC_TAG = "TickReceiver";
        	
            @Override
            public void onReceive(Context context, Intent intent) 
            {
        		Cursor c = getContentResolver().query(SYSTEM_URI, null, null, null, null);
        		update();
            }
        };
        IntentFilter filter1 = new IntentFilter();
        filter1.addAction("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK");
        registerReceiver(TickReceiver, filter1);    
    }
    
    
	/************************************************************************************
	* Listeners Functions
	************************************************************************************/
	
	/************************************************************************************
	* Auxillary Functions
	************************************************************************************/
	public long getTimeSeconds() 
	{
		if (SIM_TIME > 0) 
			return SIM_TIME;			//Simulated time passed on timer tick
		else 
			return (long)(System.currentTimeMillis()/1000);
	}
	
	/************************************************************************************
	* Log Messaging Functions
	************************************************************************************/
	
	public void log_action(String tag, String message)
	{
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", tag);
        i.putExtra("Status", message);
        i.putExtra("time", (long)getTimeSeconds());
        sendBroadcast(i);
	}
}
