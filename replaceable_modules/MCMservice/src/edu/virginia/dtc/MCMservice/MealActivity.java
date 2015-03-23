package edu.virginia.dtc.MCMservice;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import edu.virginia.dtc.MCMservice.MCMservice.SystemObserver;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Meal;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.State;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MealActivity extends Activity{
	
	public static final String TAG = "MealActivity";
	public static final boolean DEBUG = true;
    public static final String IO_TEST_TAG = "MealActivityIO";
    
    public static int DIAS_STATE;
    
	public static double carbs = 0.0;
	public static double carbsInsulin = 0.0;
	public static double bg = 0.0;
	public static double bgInsulin = 0.0;
	public static double corrInsulin = 0.0;
	public static double iobInsulin = 0.0;
	public static double totalInsulin = 0.0;
	
	private TextView infoText;
	private EditText carbsInput, carbsOutput;
	private EditText bgInput, bgOutput;
	private EditText corrInput;
	private EditText iobOutput;
	private EditText totalOutput;
	private CheckBox iob;
	private Button injectMeal;
	
	private ServiceConnection MCMservice;
    private final Messenger MCMmessenger = new Messenger(new IncomingMCMhandler());
    private Messenger MCM = null;	
    
    public static boolean inProgress;
    public static boolean carbsValid, bgValid, corrValid, totalValid;
    public static boolean iobChecked, injectEnabled;
    
    public static String info = "";
    
    private SystemObserver sysObserver;
    
    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private static ScheduledFuture<?> inactivityTimer;
	
	private static final int INACTIVITY_TIMEOUT = 60;
	
	private Runnable inactivity = new Runnable() {
		final String FUNC_TAG = "inactivity";
		
		public void run() {
			Debug.w(TAG, FUNC_TAG, "Inactivity timer elapsed...closing meal screen!");
			finish();
		}
	};
    
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.defaultmealscreen);
		
		Debug.i(TAG, FUNC_TAG, "Orientation: "+getResources().getConfiguration().orientation);
		
		getSystemStatus();
		
		if (getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE)
		{
			WindowManager.LayoutParams params = getWindow().getAttributes();
			params.height = getIntent().getIntExtra("height", 1075);
			params.width = getIntent().getIntExtra("width", 1152);
			
			Debug.i(TAG, FUNC_TAG, "HEIGHT: "+getIntent().getIntExtra("height", 100)+" WIDTH: "+getIntent().getIntExtra("width", 100));
			
			ViewGroup.LayoutParams lParams = this.findViewById(R.id.defaultMealLayout).getLayoutParams();
			
			lParams.height = params.height;
			lParams.height -= (0.07*lParams.height);
			
			lParams.width = params.width;
			lParams.width -= (0.07*lParams.width);

			params.gravity=Gravity.TOP;
			
			(this.findViewById(R.id.defaultMealLayout)).setLayoutParams(lParams);
			
			this.getWindow().setAttributes(params);
		}
		
		initializeScreen();
		startMCM();
		updateUi();
		resetInactivityTimer();
		
		sysObserver = new SystemObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.SYSTEM_URI, true, sysObserver);
	}
	
	@Override
	public void onDestroy()
	{
		final String FUNC_TAG = "onDestroy";

		inProgress = true;
		
		if(sysObserver != null)
			getContentResolver().unregisterContentObserver(sysObserver);
		
		try {
    		Message msg = Message.obtain(null, Meal.UI_CLOSED, 0, 0);
    		MCM.send(msg);
        }
        catch (RemoteException e) {
    		e.printStackTrace();
        }
		
		if(MCMservice != null)
			this.unbindService(MCMservice);
		
		super.onDestroy();
		Debug.i(TAG, FUNC_TAG, "");
	}
	
	class IncomingMCMhandler extends Handler 
	{
        @Override
        public void handleMessage(Message msg)
        {
        	final String FUNC_TAG = "IncomingMCMhandler";
        	
            switch (msg.what)
            {
		        case Meal.MCM_CALCULATED:
		        	Debug.i(TAG, FUNC_TAG, "MCM_CALCULATED");
		        	updateUi();
		        	break;
            }
        }
    }
	
	/**
	 * Resets the inactivity timer, for instance, if you change the BG value,
	 * we would reset the timer
	 */
	private void resetInactivityTimer()
	{
		if(inactivityTimer != null)
			inactivityTimer.cancel(true);
		
		Debug.i(TAG, "resetInactivityTimer", "Resetting timer to "+INACTIVITY_TIMEOUT+" seconds!");
		
		inactivityTimer = scheduler.schedule(inactivity, INACTIVITY_TIMEOUT, TimeUnit.SECONDS);
	}
	
	/**
	 * Update the user interface, buttons, and calculated values
	 */
    private void updateUi()
    {
    	final String FUNC_TAG = "updateUi";
    	
		String invalid = "---";
		
		infoText.setText(info);
		
		if(injectEnabled) {
			injectMeal.setEnabled(true);
		}
		else {
			injectMeal.setEnabled(false);
		}
		
		if(iobChecked) {
			if(iobInsulin > 0.0)
				iobOutput.setText(String.format("%.2f", iobInsulin));
			else
				iobOutput.setText(invalid);
		}
		else
		{
			iobOutput.setText(invalid);
		}
		
		if(inProgress) {
			((LinearLayout)MealActivity.this.findViewById(R.id.progressLayout)).setVisibility(View.VISIBLE);
			((LinearLayout)MealActivity.this.findViewById(R.id.mealLayout)).setVisibility(View.GONE);
    	}
    	else {
    		((LinearLayout)MealActivity.this.findViewById(R.id.progressLayout)).setVisibility(View.GONE);
			((LinearLayout)MealActivity.this.findViewById(R.id.mealLayout)).setVisibility(View.VISIBLE);
    	}
		
		if(carbsValid) {
			carbsInput.setTextColor(Color.GRAY);
			carbsOutput.setText(String.format("%.2f", carbsInsulin));
		}
		else {
			carbsInput.setTextColor(Color.RED);
			carbsOutput.setText(invalid);
		}
		
		if(bgValid) {
			bgInput.setTextColor(Color.GRAY);
			bgOutput.setText(String.format("%.2f", bgInsulin));
		}
		else {
			bgInput.setTextColor(Color.RED);
			bgOutput.setText(invalid);
		}
		
		if(corrValid) {
			corrInput.setTextColor(Color.GRAY);
		}
		else {
			corrInput.setTextColor(Color.RED);
		}
		
		if(totalValid) {
			totalOutput.setText(String.format("%.2f", totalInsulin));
			injectMeal.setEnabled(true);
		}
		else {
			totalOutput.setText(invalid);
			injectMeal.setEnabled(false);
		}
    }
    
 	/***********************************************************************
 	  ________   ___  ___    _____  _____  __  ________
 	 / ___/ _ | / _ \/ _ )  /  _/ |/ / _ \/ / / /_  __/
 	/ /__/ __ |/ , _/ _  | _/ //    / ___/ /_/ / / /   
 	\___/_/ |_/_/|_/____/ /___/_/|_/_/   \____/ /_/    
 	                                                   
	***********************************************************************/
	
    private void carbInput(String input)
    {
    	final String FUNC_TAG = "carbInput";
    	
    	if(input.length() <= 0)
    		carbs = 0.0;
    	else {
 			try {
 				carbs = Double.parseDouble(input);
 			} catch(NumberFormatException c) {
 				carbs = 0.0;
 			}
    	}
    		
    	reportChangeToMcm();
    }
    
 	private void carbListeners()
 	{
 		final String FUNC_TAG = "carbListeners";
 		
	 	carbsInput.addTextChangedListener(new TextWatcher()
	 	{
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				carbInput(carbsInput.getText().toString());
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
				carbInput(carbsInput.getText().toString());
			}

			public void afterTextChanged(Editable s) {
				carbInput(carbsInput.getText().toString());
			}
	 		
	 	});
 	}

 	/*************************************************************
 	   ______  ______  _____  _____  _____  __  ________
	  / __/  |/  / _ )/ ___/ /  _/ |/ / _ \/ / / /_  __/
	 _\ \/ /|_/ / _  / (_ / _/ //    / ___/ /_/ / / /   
	/___/_/  /_/____/\___/ /___/_/|_/_/   \____/ /_/    
                                                
 	*************************************************************/
 	
 	private void smbgInput(String input)
 	{
 		final String FUNC_TAG = "smbgInput";
 		
 		if(input.length() <= 0)
 			bg = 0.0;
 		else {
 			try {
 				bg = Double.parseDouble(input);
 			} catch(NumberFormatException c) {
 				bg = 0.0;
 			}
 		}
		
		reportChangeToMcm();
 	}
 	
 	private void smbgListeners()
 	{
 		final String FUNC_TAG = "smbgListeners";
 		
 		bgInput.addTextChangedListener(new TextWatcher(){

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				smbgInput(bgInput.getText().toString());
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
				smbgInput(bgInput.getText().toString());
			}

			public void afterTextChanged(Editable s) {
				smbgInput(bgInput.getText().toString());
			}
 			
 		});
  	}
 	
 	/***********************************************************************
 	  _________  ___  ___    _____  _____  __  ________
	 / ___/ __ \/ _ \/ _ \  /  _/ |/ / _ \/ / / /_  __/
	/ /__/ /_/ / , _/ , _/ _/ //    / ___/ /_/ / / /   
	\___/\____/_/|_/_/|_| /___/_/|_/_/   \____/ /_/    
                                               
 	***********************************************************************/
 	
 	private void corrInput(String input)
 	{
 		if(input.length() <= 0)
 			corrInsulin = 0.0;
 		else {
 			try {
 				corrInsulin = Double.parseDouble(input);
 			} catch(NumberFormatException c) {
 				corrInsulin = 0.0;
 			}
 		}
 		
 		reportChangeToMcm();
 	}
 	
 	private void corrListeners()
 	{
 		final String FUNC_TAG = "corrListeners";
 		
 		corrInput.addTextChangedListener(new TextWatcher(){

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				corrInput(corrInput.getText().toString());
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
				corrInput(corrInput.getText().toString());
			}

			public void afterTextChanged(Editable s) {
				corrInput(corrInput.getText().toString());
			}
 		});
	}
	
	/************************************************************************************
	* Listener Functions
	************************************************************************************/
	
	public void checkboxIOBClick(View view) 
	{
 		final String FUNC_TAG = "checkboxIOBClick";
 		
 		iobChecked = iob.isChecked();
 		
 		reportChangeToMcm();
   	}
	
	public void injectMealBolusClick(View view) 
	{
		final String FUNC_TAG = "injectMealBolusClick";
		
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
    	alert.setTitle("Confirm Injection");
    	alert.setMessage("Do you want to inject "+String.format("%.2f", totalInsulin)+" U?");

    	alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() 
    	{
			public void onClick(DialogInterface dialog, int whichButton) 
			{
				try {
		    		Message msg = Message.obtain(null, Meal.INJECT, 0, 0);
		    		MCM.send(msg);
		        }
		        catch (RemoteException e) {
		    		e.printStackTrace();
		        }
				
				finish();
			}
		});
    	
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			}
		});
    	
    	alert.show();
	}
	
	/************************************************************************************
	* Auxiliary Functions
	************************************************************************************/
	
	/**
	 * Gets the current DIAS_STATE for the system
	 */
	private void getSystemStatus()
	{
		final String FUNC_TAG = "getSystemStatus";
		
		Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, null, null, null, null);
		if(c!=null)
		{
			if(c.moveToLast())
			{
				DIAS_STATE = c.getInt(c.getColumnIndex("diasState"));
				Debug.i(TAG, FUNC_TAG, "DiAs State: "+State.stateToString(DIAS_STATE));
			}
			c.close();
		}
	}
	
	/**
	 * Send a message to the MCM so it knows we have changed the interface
	 */
	private void reportChangeToMcm()
	{
		try {
    		Message msg = Message.obtain(null, Meal.UI_CHANGE, 0, 0);
    		MCM.send(msg);
        }
        catch (RemoteException e) {
    		e.printStackTrace();
        }
		
		resetInactivityTimer();
	}
	
	private void initializeScreen()
	{
		final String FUNC_TAG = "initMealScreen";
		
		info = "";
		
		inProgress = true;
		carbsValid = bgValid = corrValid = totalValid = injectEnabled = false;
		carbsInsulin = bgInsulin = corrInsulin = iobInsulin = totalInsulin = 0.0;
		bg = carbs = 0.0;
		
    	int blood_glucose_display_units = Params.getInt(getContentResolver(), "blood_glucose_display_units", CGM.BG_UNITS_MG_PER_DL);
		TextView unit_string_mmol = (TextView)this.findViewById(R.id.bgTextUnitLabelMMolPerL);
		TextView unit_string_mgdl = (TextView)this.findViewById(R.id.bgTextUnitLabelMgPerDl);
		
		if (blood_glucose_display_units == CGM.BG_UNITS_MMOL_PER_L) 
		{
			unit_string_mmol.setVisibility(View.VISIBLE);
			unit_string_mgdl.setVisibility(View.GONE);
		}
		else {
			unit_string_mmol.setVisibility(View.GONE);
			unit_string_mgdl.setVisibility(View.VISIBLE);
		}
		
		infoText = (TextView)this.findViewById(R.id.infoText);
		injectMeal = (Button)this.findViewById(R.id.injectMealBolusButton);
		carbsInput = (EditText)this.findViewById(R.id.editMealCarbs);
		bgInput = (EditText)this.findViewById(R.id.editBg);
		carbsOutput = (EditText)this.findViewById(R.id.editMealCarbsTotal);
		bgOutput = (EditText)this.findViewById(R.id.editBgTotal);
		iobOutput = (EditText)this.findViewById(R.id.editIobTotal);
		corrInput = (EditText)this.findViewById(R.id.editCorrTotal);
		totalOutput = (EditText)this.findViewById(R.id.editAllTotal);
		iob = (CheckBox)this.findViewById(R.id.iobCheckbox);
		infoText.setTextColor(Color.RED);
		infoText.setText("");
		
	 	carbListeners();
	 	smbgListeners();
	 	corrListeners();
	 	
	 	int meal_activity_bolus_calculation_mode = Params.getInt(getContentResolver(), "meal_activity_bolus_calculation_mode", Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS);
        switch(meal_activity_bolus_calculation_mode) 
        {
        	case Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS:
				mealScreenOpenLoop();
        		break;
        	case Meal.MEAL_ACTIVITY_CALCULATES_BOLUS_PUMP_MODE:
                switch(DIAS_STATE) {
    				case State.DIAS_STATE_OPEN_LOOP:
    					mealScreenOpenLoop();
    					break;
    				case State.DIAS_STATE_SAFETY_ONLY:
    				case State.DIAS_STATE_CLOSED_LOOP:
    					mealScreenClosedLoop();
    					break;
                }
        		break;
        	case Meal.MEAL_ACTIVITY_NEVER_CALCULATES_BOLUS:
				mealScreenClosedLoop();
        		break;
        	default:
				mealScreenOpenLoop();
        		break;
        }
	}
	
	private void mealScreenClosedLoop() 
	{
		final String FUNC_TAG = "mealScreenClosedLoop";
		
		Debug.i(TAG, FUNC_TAG, "Default Closed-Loop Mode Meal Screen");
	   
		Debug.i(TAG, FUNC_TAG, "Hiding IOB and Correction input elements...setting IOB checked to false!");
		this.findViewById(R.id.mealIobLayout).setVisibility(View.GONE);
		this.findViewById(R.id.mealCorrLayout).setVisibility(View.GONE);
		
		iob.setChecked(false);
		iobChecked = false;
	}

	private void mealScreenOpenLoop() 
	{
		final String FUNC_TAG = "mealScreenOpenLoop";
		
		Debug.i(TAG, FUNC_TAG, "Default Open-Loop Mode Meal Screen");
	   
		iob.setVisibility(View.VISIBLE);
		iobOutput.setVisibility(View.VISIBLE);	
		corrInput.setVisibility(View.VISIBLE);	
		
		iob.setChecked(true);
		iobChecked = true;
	}
	
	private void startMCM()
	{
		MCMservice = new ServiceConnection() 
		{
        	final String FUNC_TAG = "startMCM";
        	
            public void onServiceConnected(ComponentName className, IBinder service) 
            {
                Debug.i(TAG, FUNC_TAG, "onServiceConnected Safety Service");
                MCM = new Messenger(service);
                Debug.i(TAG, FUNC_TAG, "MCM service");

                try {
            		// Send a register-client message to the service with the client message handler in replyTo
            		Message msg = Message.obtain(null, Meal.UI_REGISTER, 0, 0);
            		msg.replyTo = MCMmessenger;
            		MCM.send(msg);
                }
                catch (RemoteException e) {
            		e.printStackTrace();
                }
           }

           public void onServiceDisconnected(ComponentName className) 
           {
        	   Debug.i(TAG, FUNC_TAG, "onServiceDisconnected");
        	   MCM = null;
           }
        };
        
        Intent intent = new Intent("DiAs.MCMservice");
        bindService(intent, MCMservice, Context.BIND_AUTO_CREATE);
	}
	
	public long getCurrentTimeSeconds() 
	{
		return System.currentTimeMillis()/1000;			// Seconds since 1/1/1970 in UTC
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

       @Override
       public void onChange(boolean selfChange, Uri uri) 
       {
    	   final String FUNC_TAG = "onChange";
    	   
    	   count++;
    	   
    	   Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, null, null, null, null);
    	   if(c!=null)
    	   {
    		   if(c.moveToLast())
    		   {
    			   int PUMP_STATE = c.getInt(c.getColumnIndex("pumpState"));
    			   
    			   if(!(PUMP_STATE == Pump.CONNECTED || PUMP_STATE == Pump.CONNECTED_LOW_RESV))
    			   {
    				   Debug.e(TAG, FUNC_TAG, "Pump is disconnected!  State: "+Pump.stateToString(PUMP_STATE));
    				   Toast.makeText(getApplicationContext(), "Sorry, the pump is disconnected and a meal bolus cannot be processed!", Toast.LENGTH_LONG).show();
    				   finish();
    			   }
    		   }
    		   c.close();
    	   }
       }		
    }
	
	/************************************************************************************
	* Log Functions
	************************************************************************************/
	
	public void log_action(String tag, String message)
	{
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", tag);
        i.putExtra("Status", message);
        i.putExtra("time", getCurrentTimeSeconds());
        sendBroadcast(i);
	}
}
