package edu.virginia.dtc.MealActivity;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.FSM;
import edu.virginia.dtc.SysMan.Meal;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.Safety;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.SysMan.Tvector;

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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnFocusChangeListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class MealActivity extends Activity{
	
	public static final String TAG = "MealActivity";
	public static final boolean DEBUG = true;
    public static final String IO_TEST_TAG = "MealActivityIO";
    
    
    // Meal screen correction BG target
 	private static final double MealScreenTargetBG = 110.0;
	
	public static final int DIAS_SERVICE_COMMAND_SEND_MEAL_BOLUS = 10;
	
	private static final int ACT_TIMEOUT = 60;		//Number of seconds until we close due to inactivity
 	
	private int DIAS_STATE, PUMP_STATE, PUMP_SERV_STATE, HYPO_LIGHT;
	private int TBR, SYNC;
	private double IOB;
	
	//TODO: Remove those hard-coded values and use the constraints from SysMan / SSMservice
	private static final double MEAL_MAX_CONSTRAINT = 18.0;
	private static final double CORRECTION_MAX_CONSTRAINT = 6.0;
	
	private double latestCR, latestCF;
	
	// Values that are needed for the Meal Screen
	private boolean apcProcessing = false;
	
	//Double storage for input from UI
	private double carbs = 0.0;				//Carb input
	private double carbsInsulin = 0.0;		//Carb insulin
	private double bg = 0.0;				//SMBG input
	private double bgInsulin = 0.0;			//SMBG insulin
	private double corrInsulin = 0.0;		//Additional correction insulin
	private double allInsulin = 0.0;		//Total insulin bolus
	
	private boolean carbsValid = false;
	private boolean bgValid = false;
	private boolean corrValid = false;
	private boolean allValid = false;
	
	//UI Objects
	private EditText carbsInput;
	private EditText carbsTotal;
	private EditText bgInput;
	private EditText bgTotal;
	private EditText corrInput;
	private EditText iobTotal;
	private EditText allTotal;
	
	private TextView waitDetail;
	private CheckBox iob;
	private Button injectMeal;
	
	private double mealBolus, corrBolus;
	
	//Content Observer
	private SystemObserver sysObserver;
	private PumpObserver pumpObserver;
	private StateObserver stateObserver;
	
	private ServiceConnection MCMservice;
    final Messenger MCMmessenger = new Messenger(new IncomingMCMhandler());
    Messenger MCM = null;	
    boolean MCMbound;		
    
    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> activityTimeout;
    
    private Runnable activity = new Runnable()
    {
    	final String FUNC_TAG = "activity";
    	
		public void run() 
		{
			//At this point we haven't had any user interaction in the allotted time frame so close
			Debug.i(TAG, FUNC_TAG, "No activity in "+ACT_TIMEOUT+", closing Meal Screen!");
			
			Message uiMessage = Message.obtain(null, Meal.MCM_UI);
			Bundle b = new Bundle();
			b.putBoolean("end", true);
			uiMessage.setData(b);
			
			try {
				MCM.send(uiMessage);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
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
		
		if (getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE)
		{
			WindowManager.LayoutParams params = getWindow().getAttributes();
			params.height = getIntent().getIntExtra("height", 100);
			params.width = getIntent().getIntExtra("width", 100);
			
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
		
		Debug.i(TAG, FUNC_TAG, "OnCreate");
		
		if(true)	//Params.getBoolean(getContentResolver(), "connection_scheduling", false))
		{
			((LinearLayout)this.findViewById(R.id.progressLayout)).setVisibility(View.VISIBLE);
			((LinearLayout)this.findViewById(R.id.mealLayout)).setVisibility(View.GONE);
			waitDetail = (TextView)this.findViewById(R.id.waitDetail);
		}
		else
		{
			((LinearLayout)this.findViewById(R.id.progressLayout)).setVisibility(View.GONE);
			((LinearLayout)this.findViewById(R.id.mealLayout)).setVisibility(View.VISIBLE);
		}
		
		setResult(RESULT_CANCELED);		//Set the result to cancelled unless we actually send a bolus with the UI

		injectMeal = (Button)this.findViewById(R.id.injectMealBolusButton);
		
		//Read startup values
		readStartupValues();
		initMealScreen();
		
		sysObserver = new SystemObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.SYSTEM_URI, true, sysObserver);

		pumpObserver = new PumpObserver(new Handler());
        getContentResolver().registerContentObserver(Biometrics.PUMP_DETAILS_URI, true, pumpObserver);
        
        stateObserver = new StateObserver(new Handler());
        getContentResolver().registerContentObserver(Biometrics.STATE_URI, true, stateObserver);
        
		//Setup the UI
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
        
		startMCM();
		
		if(activityTimeout != null)
			activityTimeout.cancel(true);
		
		Debug.i(TAG, FUNC_TAG, "Starting activity timeout timer!");
		activityTimeout = scheduler.schedule(activity, ACT_TIMEOUT, TimeUnit.SECONDS);
	}
	
	@Override
	public void onDestroy()
	{
		final String FUNC_TAG = "onDestroy";

        if(MCMbound) 
        {
            unbindService(MCMservice);
            MCMbound = false;
        }

        if(stateObserver != null)
        	getContentResolver().unregisterContentObserver(stateObserver);
        
		if(sysObserver != null)
			getContentResolver().unregisterContentObserver(sysObserver);
		
		if(pumpObserver != null)
			getContentResolver().unregisterContentObserver(pumpObserver);
		
		super.onDestroy();
		Debug.i(TAG, FUNC_TAG, "");
	}
	
	@Override
    public void onBackPressed() {
        // Prevent window for closing when hitting the back button.
		Debug.i(TAG, "onBackPressed", "Canceling meal screen by exiting!");
		
		Message uiMessage = Message.obtain(null, Meal.MCM_UI);
		Bundle b = new Bundle();
		b.putBoolean("end", true);
		uiMessage.setData(b);
		
		try {
			MCM.send(uiMessage);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		
		finish();
    }
	
	class IncomingMCMhandler extends Handler {
        @Override
        public void handleMessage(Message msg)
        {
        	final String FUNC_TAG = "IncomingMCMhandler";
        	
        	Bundle responseBundle;
        	
            switch (msg.what) {
	            case Meal.MCM_UI:
	            	responseBundle = msg.getData();
	            	
	            	if(responseBundle.getBoolean("connected",false))
	            	{
	            		((LinearLayout)MealActivity.this.findViewById(R.id.progressLayout)).setVisibility(View.GONE);
	        			((LinearLayout)MealActivity.this.findViewById(R.id.mealLayout)).setVisibility(View.VISIBLE);
	            	}
	            	else
	            	{
	            		Debug.i(TAG, FUNC_TAG, "Connection has failed or ignoring because another loop is busy");
	            		finish();
	            	}
	            	break;
	            case Meal.MCM_CALCULATED_BOLUS:
	            	responseBundle = msg.getData();
	            	
	            	double bolus = responseBundle.getDouble("bolus");
	            	int status = responseBundle.getInt("MCM_status");
					String description = responseBundle.getString("MCM_description");

					if (true) {
                		Bundle b1 = new Bundle();
                		b1.putString(	"description", "MCMservice >> (MealActivity), IO_TEST"+", "+FUNC_TAG+", "+
                						"MCM_CALCULATED_BOLUS"+", "+
                						"bolus="+bolus+", "+
                						"status="+status+", "+
                						"description="+description
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
					}
	            	
					Debug.i(TAG, FUNC_TAG, "BOLUS: "+bolus);
					
					if(bolus > (MEAL_MAX_CONSTRAINT + CORRECTION_MAX_CONSTRAINT))
					{
						Debug.w(TAG, FUNC_TAG, "Limiting bolus to maximum: "+(MEAL_MAX_CONSTRAINT + CORRECTION_MAX_CONSTRAINT));
						bolus = (MEAL_MAX_CONSTRAINT + CORRECTION_MAX_CONSTRAINT);
					}
					
					allValid = true;
					allInsulin = bolus;
					allTotal.setText(String.format("%.2f", allInsulin));
	            	break;
	        	default:
					Debug.i(TAG, FUNC_TAG, "UNKNOWN_MESSAGE="+msg.what);
	        		super.handleMessage(msg);
            }
        }
    }
	
	private void startMCM()
	{
		MCMservice = new ServiceConnection() 
		{
        	final String FUNC_TAG = "MCMservice";
        	
            public void onServiceConnected(ComponentName className, IBinder service) 
            {
                Debug.i(TAG, FUNC_TAG, "onServiceConnected Safety Service");
                MCM = new Messenger(service);
                MCMbound = true;
                Debug.i(TAG, FUNC_TAG, "MCM service");

                try {
            		// Send a register-client message to the service with the client message handler in replyTo
					if (true) {
                		Bundle b1 = new Bundle();
                		b1.putString(	"description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
                						"MEAL_ACTIVITY_REGISTER"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
					}
            		Message msg = Message.obtain(null, Meal.MEAL_ACTIVITY_REGISTER, 0, 0);
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
        	   MCMbound = false;
           }
        };
        
        // Bind to the Safety Service
        Intent intent = new Intent("DiAs.MCMservice");
        bindService(intent, MCMservice, Context.BIND_AUTO_CREATE);
	}
	
	private void refreshActivity()
	{
		final String FUNC_TAG = "refreshActivity";
		
		Debug.i(TAG, FUNC_TAG, "Refreshing activity timer, setting back to "+ACT_TIMEOUT+" seconds!");
		
		if(activityTimeout != null)
			activityTimeout.cancel(true);
		
		activityTimeout = scheduler.schedule(activity, ACT_TIMEOUT, TimeUnit.SECONDS);
	}

	private void initMealScreen()
	{
		final String FUNC_TAG = "initMealScreen";
    	int blood_glucose_display_units = Params.getInt(getContentResolver(), "blood_glucose_display_units", CGM.BG_UNITS_MG_PER_DL);
		TextView unit_string_mmol = (TextView)this.findViewById(R.id.bgTextUnitLabelMMolPerL);
		TextView unit_string_mgdl = (TextView)this.findViewById(R.id.bgTextUnitLabelMgPerDl);
		if (blood_glucose_display_units == CGM.BG_UNITS_MMOL_PER_L) {
			unit_string_mmol.setVisibility(View.VISIBLE);
			unit_string_mgdl.setVisibility(View.GONE);
		}
		else {
			unit_string_mmol.setVisibility(View.GONE);
			unit_string_mgdl.setVisibility(View.VISIBLE);
		}
		
		carbsValid = false;
		corrValid = false;
		bgValid = false;
		allValid = false;
			 	
		carbsInput = (EditText)this.findViewById(R.id.editMealCarbs);
		bgInput = (EditText)this.findViewById(R.id.editBg);
		carbsTotal = (EditText)this.findViewById(R.id.editMealCarbsTotal);
		bgTotal = (EditText)this.findViewById(R.id.editBgTotal);
		iobTotal = (EditText)this.findViewById(R.id.editIobTotal);
		corrInput = (EditText)this.findViewById(R.id.editCorrTotal);
		allTotal = (EditText)this.findViewById(R.id.editAllTotal);
		iob = (CheckBox)this.findViewById(R.id.iobCheckbox);
		
		//Getting most recent subject information
 		subject_parameters();
	 	Debug.i(TAG, FUNC_TAG, "latest CR:"+latestCR+" CF: "+latestCF);

	 	carbListeners();
	 	smbgListeners();
	 	corrListeners();
	}
	 	
 	/***********************************************************************
 	  ________   ___  ___    _____  _____  __  ________
 	 / ___/ _ | / _ \/ _ )  /  _/ |/ / _ \/ / / /_  __/
 	/ /__/ __ |/ , _/ _  | _/ //    / ___/ /_/ / / /   
 	\___/_/ |_/_/|_/____/ /___/_/|_/_/   \____/ /_/    
 	                                                   
	***********************************************************************/
	private boolean carbInput(String input)
	{
		final String FUNC_TAG = "carbInput";
		
		carbs = Double.parseDouble(input);
		Debug.i(TAG, FUNC_TAG, "Input: "+input+" Carbs: "+carbs);
		
		double limit = MEAL_MAX_CONSTRAINT * latestCR;
		Debug.i(TAG, FUNC_TAG, "Limit of "+limit+" carbs can be entered");
		
		if(carbs >= 0.0 && carbs <= limit)
		{
			carbsInput.setTextColor(Color.GRAY);
			carbsValid = true;
			carbsInsulin = carbs/latestCR;
			Debug.i(TAG, FUNC_TAG, "Carb Insulin: "+carbsInsulin+" U");
			
			int calc_mode = Params.getInt(getContentResolver(), "meal_activity_bolus_calculation_mode", Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS);
			if (calc_mode == Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS || calc_mode == Meal.MEAL_ACTIVITY_CALCULATES_BOLUS_PUMP_MODE && DIAS_STATE == State.DIAS_STATE_OPEN_LOOP)
		 		carbsTotal.setText(String.format("%.2f", (double)((int)(carbsInsulin*100))/100));
			else 
		 		carbsTotal.setText("");
			
			calculateBolus();
			return false;
		}
		else
		{
			carbsInput.setTextColor(Color.RED);		//Set input to red text
			carbsTotal.setText("");					//Set total to blank
			calculateBolus();
			return true;
		}
	}
	
 	private void carbListeners()
 	{
 		final String FUNC_TAG = "carbListeners";
 		
	 	carbsInput.setOnEditorActionListener(new OnEditorActionListener() 
	 	{
	 		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) 
	 		{
	 			refreshActivity();
	 			
	 			carbsValid = allValid = false;
	 			
	 			if (v.getText().toString().length() > 0) 
	 			{
	 				return carbInput(v.getText().toString());
	 			}
	 			else
	 			{
	 				carbsTotal.setText("");					//Set total to blank
	 				calculateBolus();
	 				return true;
	 			}
	 		}
	 	});
	 	carbsInput.setOnFocusChangeListener(new OnFocusChangeListener()
	 	{
            public void onFocusChange(View v, boolean hasFocus)
            {
            	refreshActivity();
	 			
        		if (!hasFocus) 
        		{
        			carbsValid = allValid = false;

    	 			if (((TextView)v).getText().toString().length() > 0) 
    	 			{
    	 				carbInput(((TextView)v).getText().toString());
    	 			}
    	 			else
    	 			{
    	 				carbsTotal.setText("");					//Set total to blank
    	 				calculateBolus();
    	 			}
        		}
            }
	 	});
	 	
	 	carbsInput.setText("");
	 	carbsTotal.setText("");
 	}

 	/*************************************************************
 	   ______  ______  _____  _____  _____  __  ________
	  / __/  |/  / _ )/ ___/ /  _/ |/ / _ \/ / / /_  __/
	 _\ \/ /|_/ / _  / (_ / _/ //    / ___/ /_/ / / /   
	/___/_/  /_/____/\___/ /___/_/|_/_/   \____/ /_/    
                                                
 	*************************************************************/
 	private boolean smbgInput(String input)
 	{
 		final String FUNC_TAG = "smbgInput";
 		
 		bg = Double.parseDouble(input);
 		Debug.i(TAG, FUNC_TAG, "Input: "+input+" SMBG: "+bg);
 		
 		int blood_glucose_display_units = Params.getInt(getContentResolver(), "blood_glucose_display_units", CGM.BG_UNITS_MG_PER_DL);
		if (blood_glucose_display_units == CGM.BG_UNITS_MMOL_PER_L) 
			bg = bg * CGM.MGDL_PER_MMOLL;
		
		Debug.i(TAG, FUNC_TAG, "Post Conversion - SMBG: "+bg);
		
		double limit = (CORRECTION_MAX_CONSTRAINT * latestCF) + MealScreenTargetBG;
		Debug.i(TAG, FUNC_TAG, "Limit of "+limit+" mg/dL can be entered");
		
		if(bg >= 39.0 && bg <= 401.0)
		{
			bgInput.setTextColor(Color.GRAY);
			bgValid = true;
			
			calculateBolus();
			return false;
		}
		else
		{
			bgTotal.setText("");				//Set total to blank
			bgInput.setTextColor(Color.RED);	//Set input to red text
			calculateBolus();
			return true;
		}
 	}
 	
 	private void smbgListeners()
 	{
 		final String FUNC_TAG = "smbgListeners";
 		
	 	bgInput.setOnEditorActionListener(new OnEditorActionListener() 
	 	{
	 		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) 
	 		{
	 			refreshActivity();
	 			
	 			bgValid = allValid = false;
    	 		
	 			if (v.getText().toString().length() > 0) 
	 			{
	 				return smbgInput(v.getText().toString());
	 			}
	 			else
	 			{
	 				calculateBolus();
	 				bgTotal.setText("");
	 				return true;
	 			}
	 		}
	 	});
 		bgInput.setOnFocusChangeListener(new OnFocusChangeListener()
 		{
 			public void onFocusChange(View v, boolean hasFocus){
 				refreshActivity();
	 			
        		if (!hasFocus) 
        		{
        			bgValid = allValid = false;
        	 		
    	 			if (((TextView)v).getText().toString().length() > 0) 
    	 			{
    	 				smbgInput(((TextView)v).getText().toString());
    	 			}
    	 			else
    	 			{
    	 				calculateBolus();
    	 				bgTotal.setText("");
    	 			}
        		}
            }
        });
 		
	 	bgInput.setText("");
	 	bgTotal.setText("");
 	}
 	
 	/***********************************************************************
 	  _________  ___  ___    _____  _____  __  ________
	 / ___/ __ \/ _ \/ _ \  /  _/ |/ / _ \/ / / /_  __/
	/ /__/ /_/ / , _/ , _/ _/ //    / ___/ /_/ / / /   
	\___/\____/_/|_/_/|_| /___/_/|_/_/   \____/ /_/    
                                               
 	***********************************************************************/
 	private boolean corrInput(String input)
 	{
 		corrInsulin = Double.parseDouble(input);
 		
 		if(corrInsulin >= -20 && corrInsulin <= CORRECTION_MAX_CONSTRAINT)
 		{
 			corrInput.setTextColor(Color.GRAY);
 			corrValid = true;
 			
 			calculateBolus();
 			return false;
 		}
 		else
 		{
 			corrValid = false;
 			corrInput.setTextColor(Color.RED);
 			calculateBolus();
 			return true;
 		}
 	}
 	
 	private void corrListeners()
 	{
 		final String FUNC_TAG = "corrListeners";
 		
	 	corrInput.setOnEditorActionListener(new OnEditorActionListener() 
	 	{
	 		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) 
	 		{
	 			refreshActivity();
	 			
	 			if (v.getText().toString().length() > 0) 
	 			{
	 				return corrInput(v.getText().toString());
	 			}
	 			else
	 			{
	 				calculateBolus();
	 				return true;
	 			}
	 		}
	 	});
	 	corrInput.setOnFocusChangeListener(new OnFocusChangeListener()
	 	{
            public void onFocusChange(View v, boolean hasFocus)
            {
            	refreshActivity();
	 			
        		if (!hasFocus) 
        		{
    	 			if (((TextView)v).getText().toString().length() > 0) 
    	 			{
    	 				corrInput(((TextView)v).getText().toString());
    	 			}
    	 			else
    	 				calculateBolus();
        		}
            }
        });
	 	
	 	corrValid = true;
	}
 	
 	/***********************************************************************************
 	   __ ________   ___  _______    ______  ___  _____________________  _  ______
  	  / // / __/ /  / _ \/ __/ _ \  / __/ / / / |/ / ___/_  __/  _/ __ \/ |/ / __/
 	 / _  / _// /__/ ___/ _// , _/ / _// /_/ /    / /__  / / _/ // /_/ /    /\ \  
	/_//_/___/____/_/  /___/_/|_| /_/  \____/_/|_/\___/ /_/ /___/\____/_/|_/___/  
                                                                              
 	***********************************************************************************/
	public void mealScreenClosedLoop() 
	{
		final String FUNC_TAG = "mealScreenClosedLoop";
		
		Debug.i(TAG, FUNC_TAG, "Default Closed-Loop Mode Meal Screen");
	   
		Debug.i(TAG, FUNC_TAG, "Hiding IOB and Correction input elements...setting IOB checked to false!");
		this.findViewById(R.id.mealIobLayout).setVisibility(View.GONE);
		this.findViewById(R.id.mealCorrLayout).setVisibility(View.GONE);
		iob.setChecked(false);
	   
		calculateBolus();
	}

	public void mealScreenOpenLoop() 
	{
		final String FUNC_TAG = "mealScreenOpenLoop";
		
		Debug.i(TAG, FUNC_TAG, "Default Open-Loop Mode Meal Screen");
	   
		iob.setVisibility(View.VISIBLE);
		iobTotal.setVisibility(View.VISIBLE);	
		
		Debug.i(TAG, FUNC_TAG, "Setting IOB value...");
		if (iob.isChecked()) 
		{
		   DecimalFormat iobFormat = new DecimalFormat();
		   iobFormat.setMaximumFractionDigits(2);
		   iobFormat.setMinimumFractionDigits(2);
		   
		   if (IOB > 0)
			   iobTotal.setText(iobFormat.format(IOB));
		   else
			   iobTotal.setText("");
		}
		else 
		{
		   iobTotal.setText("");
		}		   
	   
		corrInput.setVisibility(View.VISIBLE);	

		calculateBolus();
	}
	
	public void calculateBolus()
	{
		final String FUNC_TAG = "calculateBolus";
		
		Debug.i(TAG, FUNC_TAG, "Starting bolus calculation...");
		
	 	double carb_insulin;
	 	double iob_insulin;
	 	double bg_insulin;
	 	double corr_insulin;
	 	
	    int calc_mode = Params.getInt(getContentResolver(), "meal_activity_bolus_calculation_mode", Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS);
	    
	    //OPEN LOOP MEAL ACTIVITY CALCULATION MODE
	    //----------------------------------------------------------------------------------------------------------------------------------------------------------------
	    if (calc_mode == Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS || calc_mode == Meal.MEAL_ACTIVITY_CALCULATES_BOLUS_PUMP_MODE && DIAS_STATE == State.DIAS_STATE_OPEN_LOOP)
	 	{
	    	Debug.d(TAG, FUNC_TAG, "Meal Activity is calculating bolus...");
	    	
	 		DecimalFormat iobFormat = new DecimalFormat();
	 		iobFormat.setMaximumFractionDigits(2);
	 		iobFormat.setMinimumFractionDigits(2);
	 		
	 		if (iob.isChecked()) 
	 		{
	 			if (IOB > 0) 
	 			{
	 				iobTotal.setText(iobFormat.format(IOB));
	 				iob_insulin = IOB;
	 			}
	 			else 
	 			{
	 				iobTotal.setText("");
	 				iob_insulin = 0.0;
	 			}
	 		}
	 		else 
	 			iob_insulin = 0.0;
	 		
	 		if (carbsValid) 
	 			carb_insulin = carbsInsulin;
	 		else 
	 			carb_insulin = 0;
	 		
	 		if (corrValid) 
	 			corr_insulin = corrInsulin;
	 		else 
	 			corr_insulin = 0;
	 		
	 		if (bgValid) 
	 		{
	 			subject_parameters();
	 			
	 			// Negative BG correction insulin is permitted
	 			bg_insulin = (bg-MealScreenTargetBG)*(1/latestCF);
	 			bgTotal.setText(""+(((double)(int)(bg_insulin*100))/100));
	 		} 
	 		else 
	 			bg_insulin = 0.0;
	 		
	 		// Added on 07-25-14
//	 		double MealScreenTotalBolusMax = Math.min(20.0, 100.0/latestCR);
//	 		Cursor c = getContentResolver().query(Biometrics.PUMP_DETAILS_URI, null, null, null, null);
//			if (c.moveToLast())
//			{
//				MealScreenTotalBolusMax = 2 * c.getDouble(c.getColumnIndex("max_bolus_U"));
//			}
//			c.close();
			double maxBolus = MEAL_MAX_CONSTRAINT + CORRECTION_MAX_CONSTRAINT;
			// End of Added on 07-25-14
			
			Debug.i(TAG, FUNC_TAG, "Maximum allowed bolus is "+maxBolus+" U!");
	 		
			mealBolus = carb_insulin;
			corrBolus = bg_insulin - iob_insulin + corr_insulin;
			
			allInsulin = mealBolus + corrBolus;
			Debug.w(TAG, FUNC_TAG, "All insulin before calculation: "+allInsulin+" U");
			
			if(corrValid)
			{
				if(!carbsValid)					//We will only allow correction alone if you don't have carbs entered
					allValid = true;
				else if(carbsValid && bgValid)	//Correction with meal and BG is allowed
					allValid = true;
				else
					allValid = false;
			}
			Debug.e(TAG, FUNC_TAG, "Validity > Carb: "+carbsValid+" BG: "+bgValid+" Corr: "+corrValid);
			
			if(allValid)
			{
	 			if (allInsulin <= 0.0) 						//Insulin is less than zero
	 			{
	 				Debug.w(TAG, FUNC_TAG, "Insulin is zero or less...");
	 				
	 				mealBolus = 0.0;
	 				corrBolus = 0.0;
	 				
	 				allInsulin = 0.0;
	 			}
	 			else 	//if(allInsulin > maxBolus)				//Insulin is over the maximum
	 			{
	 				if(mealBolus > MEAL_MAX_CONSTRAINT)
	 				{
	 					Debug.w(TAG, FUNC_TAG, "Meal bolus is being capped at: "+MEAL_MAX_CONSTRAINT+" U");
	 					mealBolus = MEAL_MAX_CONSTRAINT;
	 				}
	 				
	 				if(corrBolus > CORRECTION_MAX_CONSTRAINT)
	 				{
	 					Debug.w(TAG, FUNC_TAG, "Correction bolus is being capped at: "+CORRECTION_MAX_CONSTRAINT+" U");
	 					corrBolus = CORRECTION_MAX_CONSTRAINT;
	 				}
	 				
	 				if (corrBolus < 0.0) 
 	 				{
	 					Debug.w(TAG, FUNC_TAG, "Correction is negative, modifying meal!");
 	 					if (mealBolus + corrBolus > 0.0) 
 	 					{
 	 						mealBolus += corrBolus;
 	 						corrBolus = 0.0;
 	 					}
 	 					else 
 	 					{
 	 						mealBolus = 0.0;
 	 						corrBolus = 0.0;
 	 					}
 	 				}
	 				
	 				Debug.i(TAG, FUNC_TAG, "Meal Bolus: "+mealBolus);
	 				Debug.i(TAG, FUNC_TAG, "Corr Bolus: "+corrBolus);
	 				
	 				/*
	 	 				// Make sure the limit (min(20,100/CHI)) is enforced
	 	 				if((MealScreenMealBolus + MealScreenCorrectionBolus) > allInsulin)
	 	 				{
	 	 					Debug.i(TAG, FUNC_TAG, "Enforcing min(20,100/CHI) limit, Original boluses:  MealBolus: "+MealScreenMealBolus+" CorrectionBolus: "+MealScreenCorrectionBolus);
	 	 					double diff = (MealScreenMealBolus + MealScreenCorrectionBolus)-allInsulin;
	 	 					if(MealScreenCorrectionBolus < diff)		//If the difference is greater than the correction bolus we need to zero correction and subtract the remainder from meal bolus
	 	 					{
	 	 						diff -= MealScreenCorrectionBolus;		//Remove the size of correction from the difference
	 	 						MealScreenCorrectionBolus = 0.0;		//Zero out the correction bolus
	 	 						MealScreenMealBolus -= diff;			//Take the remaining difference from the meal bolus
	 	 					}
	 	 					else										//If the correction is greater than or equal to the difference, then we can just subtract it from the correction bolus and we're done
	 	 					{
	 	 						MealScreenCorrectionBolus -= diff;
	 	 					}
	 	 					Debug.i(TAG, FUNC_TAG, "Limit enforced:  MealBolus: "+MealScreenMealBolus+" CorrectionBolus: "+MealScreenCorrectionBolus);
	 	 				}
	 	 			*/
	 				
	 				allInsulin = mealBolus + corrBolus;
	 			}
			}
			else
			{
				Debug.w(TAG, FUNC_TAG, "Not all inputs are valid > Carb: "+carbsValid+" BG: "+bgValid+" Corr: "+corrValid);
				allInsulin = 0.0;
			}
 			
 			Debug.i(TAG, FUNC_TAG, "Total: "+allInsulin+" U");
 			
	 		allTotal.setText(String.format("%.2f", allInsulin));
	 	}
	    //CLOSED LOOP/MCM ACTIVITY CALCULATION MODE
	    //----------------------------------------------------------------------------------------------------------------------------------------------------------------
	 	else 
	 	{
	 		// MCM calculates bolus
	 		Debug.d(TAG, FUNC_TAG, "MCM is calculating bolus...");
	 		
	 		if (carbsValid && bgValid && !apcProcessing && allValid) 
	 		{
	 			apcProcessing = true;
	 			
	 			Message calcMessage = Message.obtain(null, Meal.MEAL_ACTIVITY_CALCULATE);
	 			Bundle b = new Bundle();
	 			b.putDouble("MealScreenCHO", carbs);
	 			b.putDouble("MealScreenBG", bg);
	 			calcMessage.setData(b);
	 			
	 			try {
					if (true) {
                		Bundle b1 = new Bundle();
                		b1.putString(	"description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
                						"MEAL_ACTIVITY_CALCULATE"+", "+
                						"MealScreenCHO="+carbs+", "+
                						"MealScreenBG="+bg
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
					}
					MCM.send(calcMessage);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
	 		}
	 		else 
    	 		Debug.i(TAG, FUNC_TAG, "Skiping MEAL_ACTIVITY_CALCULATE > Carb: "+carbsValid+" BG: "+bgValid+" Corr: "+corrValid+" APC Processing: "+apcProcessing);
	 	}
	}
	
	/************************************************************************************
	* Listener Functions
	************************************************************************************/
	
	public void checkboxIOBClick(View view) 
	{
		refreshActivity();
		
 		if (iob.isChecked()) 
 		{
 			DecimalFormat iobFormat = new DecimalFormat();
 			iobFormat.setMaximumFractionDigits(2);
 			iobFormat.setMinimumFractionDigits(2);
 			
 			if (IOB > 0)
 				iobTotal.setText(iobFormat.format(IOB));
 			else
 		 		iobTotal.setText("");
	 	}
	 	else 
	 	{
	 		iobTotal.setText("");
	 	}
 		
 		calculateBolus();
   	}
	
	public void injectMealBolusClick(View view) {
		final String FUNC_TAG = "injectMealBolusClick";
		
		refreshActivity();
		
	 	if (allValid && allInsulin >= 0.1) 
	 	{
     	 	Debug.i(TAG, FUNC_TAG, "Inject "+allInsulin+" U of insulin.");
     	 	log_action(TAG, "MEAL INJECT CLICK = "+allInsulin+"U");
     	 
    	    int calc_mode = Params.getInt(getContentResolver(), "meal_activity_bolus_calculation_mode", Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS);
    	    //CLOSED LOOP/MCM ACTIVITY CALCULATION MODE
    	    //----------------------------------------------------------------------------------------------------------------------------------------------------------------
    	    if (calc_mode == Meal.MEAL_ACTIVITY_NEVER_CALCULATES_BOLUS || calc_mode == Meal.MEAL_ACTIVITY_CALCULATES_BOLUS_PUMP_MODE && DIAS_STATE != State.DIAS_STATE_OPEN_LOOP) 
    	    {
				Message calcMessage = Message.obtain(null, Meal.MEAL_ACTIVITY_INJECT);
	 			Bundle b = new Bundle();
	 			b.putDouble("mealSize", carbs);
	 			b.putDouble("smbg", bg);
	 			b.putDouble("corr", 0.0);
	 			b.putDouble("meal", 0.0);
	 			b.putDouble("credit", allInsulin);
	 			b.putDouble("spend", allInsulin);
	 			calcMessage.setData(b);
	 			
	 			try 
	 			{
					if (true) 
					{
                		Bundle b1 = new Bundle();
                		b1.putString(	"description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
                						"MEAL_ACTIVITY_INJECT"+", "+
                						"mealSize="+b.getDouble("mealSize")+", "+
                						"smbg="+b.getDouble("smbg")+", "+
                						"corr="+b.getDouble("corr")+", "+
                						"meal="+b.getDouble("meal")+", "+
                						"credit="+b.getDouble("credit")+", "+
                						"spend="+b.getDouble("spend")
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
					}
					MCM.send(calcMessage);
				} catch (RemoteException e) {
					e.printStackTrace();
				}				
				setResult(RESULT_OK);
				finish();
     	 	}
    	    //OPEN LOOP MEAL ACTIVITY CALCULATION MODE
    	    //----------------------------------------------------------------------------------------------------------------------------------------------------------------
     	 	else
     	 	{
	     	 	AlertDialog.Builder alert = new AlertDialog.Builder(this);
		    	alert.setTitle("Confirm Injection");
		    	alert.setMessage("Do you want to inject "+String.format("%.2f",allInsulin)+" U?");
	
		    	alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() 
		    	{
					public void onClick(DialogInterface dialog, int whichButton) 
					{
						// Added on 07-25-14
						if (corrBolus > CORRECTION_MAX_CONSTRAINT)
				 		{
							corrBolus = CORRECTION_MAX_CONSTRAINT;
				 			
				 			Bundle b1 = new Bundle();
			        		b1.putString(	"description", "Meal bolus is being capped at the maximum "+corrBolus+" U!");
			        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_POPUP_AUDIBLE);
				 		}
						if (mealBolus > MEAL_MAX_CONSTRAINT)
				 		{
							mealBolus = MEAL_MAX_CONSTRAINT;
				 			
				 			Bundle b1 = new Bundle();
			        		b1.putString(	"description", "Meal bolus is being capped at the maximum "+mealBolus+" U!");
			        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_POPUP_AUDIBLE);
				 		}
						// End of Added on 07-25-14
						
						Message calcMessage = Message.obtain(null, Meal.MEAL_ACTIVITY_INJECT);
			 			Bundle b = new Bundle();
			 			b.putDouble("mealSize", carbs);
			 			b.putDouble("smbg", bg);
			 			b.putDouble("corr", corrBolus);
			 			b.putDouble("meal", mealBolus);
			 			b.putDouble("credit", 0.0);
			 			b.putDouble("spend", 0.0);
			 			calcMessage.setData(b);
			 			
			 			try {
							if (true) {
		                		Bundle b1 = new Bundle();
		                		b1.putString(	"description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
		                						"MEAL_ACTIVITY_INJECT"+", "+
		                						"mealSize="+b.getDouble("mealSize")+", "+
		                						"smbg="+b.getDouble("smbg")+", "+
		                						"corr="+b.getDouble("corr")+", "+
		                						"meal="+b.getDouble("meal")+", "+
		                						"credit="+b.getDouble("credit")+", "+
		                						"spend="+b.getDouble("spend")
		                					);
		                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
							}
							MCM.send(calcMessage);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
						setResult(RESULT_OK);
						finish();
					}
				});
		    	
				alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
					}
				});
		    	
		    	alert.show();
     	 	}
	 	}
	}
	
	/************************************************************************************
	* Auxillary Functions
	************************************************************************************/

	private void checkInjectButton()
	{
		final String FUNC_TAG = "checkInjectButton";
	
		if(Pump.isBusy(PUMP_SERV_STATE) || TBR != FSM.IDLE || SYNC != FSM.IDLE)
		{
			Debug.i(TAG, FUNC_TAG, "Service state is busy!");
			injectMeal.setEnabled(false);
		}
		else if(!(PUMP_STATE == Pump.CONNECTED || PUMP_STATE == Pump.CONNECTED_LOW_RESV))
		{
			Debug.i(TAG, FUNC_TAG, "Pump is not connected!");
			injectMeal.setEnabled(false);
		}
		else if((HYPO_LIGHT == Safety.RED_LIGHT) && (DIAS_STATE != State.DIAS_STATE_OPEN_LOOP))
		{
			Debug.i(TAG, FUNC_TAG, "Red light...");
			injectMeal.setEnabled(false);
		}
		else
		{
			Debug.i(TAG, FUNC_TAG, "All is well...");
			injectMeal.setEnabled(true);
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

       @Override
       public void onChange(boolean selfChange, Uri uri) 
       {
    	   final String FUNC_TAG = "onChange";
    	   
    	   count++;
    	   Debug.i(TAG, FUNC_TAG, "State Observer: "+count);
    	   
    	   Cursor c = getContentResolver().query(Biometrics.STATE_URI, new String[]{"sync_state", "tbr_state"}, null, null, null);
    	   if(c != null)
    	   {
    		   if(c.moveToLast())
    		   {
    			   SYNC = c.getInt(c.getColumnIndex("sync_state"));
    			   TBR = c.getInt(c.getColumnIndex("tbr_state"));
    			   
    			   //TODO: maybe update the wait detail if they have to wait
    		   }
    	   }
    	   c.close();
    	   
    	   checkInjectButton();
       }
	}
	
	class PumpObserver extends ContentObserver 
    {	
    	private int count;
    	
    	public PumpObserver(Handler handler) 
    	{
    		super(handler);
    		
    		final String FUNC_TAG = "Pump Observer";
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
    	   Debug.i(TAG, FUNC_TAG, "Pump Observer: "+count);
    	   
    	   Cursor c = getContentResolver().query(Biometrics.PUMP_DETAILS_URI, new String[]{"state", "service_state"}, null, null, null);
    	   if(c != null)
    	   {
    		   if(c.moveToLast())
    		   {
    			   PUMP_SERV_STATE = c.getInt(c.getColumnIndex("service_state"));
    		   }
    	   }
    	   c.close();
    	   
    	   checkInjectButton();
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

       @Override
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
    			   IOB = c.getDouble(c.getColumnIndex("iobValue"));
    			   if(IOB < 0.0)
    				   IOB = 0;
    			   
    			   PUMP_STATE = c.getInt(c.getColumnIndex("pumpState"));
    			   DIAS_STATE = c.getInt(c.getColumnIndex("diasState"));
    			   HYPO_LIGHT = c.getInt(c.getColumnIndex("hypoLight"));
    		   }
    		   c.close();
    	   }
    	   
    	   //Setup the UI
           int meal_activity_bolus_calculation_mode = Params.getInt(getContentResolver(), "meal_activity_bolus_calculation_mode", Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS);
           switch(meal_activity_bolus_calculation_mode) 
           {
           		case Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS:
           			mealScreenOpenLoop();
           			break;
	           	case Meal.MEAL_ACTIVITY_CALCULATES_BOLUS_PUMP_MODE:
                    switch(DIAS_STATE) 
                    {
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
           
           checkInjectButton();
       }		
    }
	
	public void readStartupValues()
	{
		Cursor c = getContentResolver().query(Biometrics.PUMP_DETAILS_URI, new String[]{"service_state"}, null, null, null);
		if(c != null)
		{
			if(c.moveToLast())
			{
				PUMP_SERV_STATE = c.getInt(c.getColumnIndex("service_state"));
			}
		}
		c.close();
		
		c = getContentResolver().query(Biometrics.SYSTEM_URI, null, null, null, null);
 	   	if(c!=null)
 	   	{
 	   		if(c.moveToLast())
 	   		{
 	   			IOB = c.getDouble(c.getColumnIndex("iobValue"));
 	   			if(IOB < 0.0)
 	   				IOB = 0;
 	   			
 	   			PUMP_STATE = c.getInt(c.getColumnIndex("pumpState"));
 	   			DIAS_STATE = c.getInt(c.getColumnIndex("diasState"));
 	   			HYPO_LIGHT = c.getInt(c.getColumnIndex("hypoLight"));
 	   		}
 	   		c.close();
 	   	}
 	   	
 	   c = getContentResolver().query(Biometrics.STATE_URI, new String[]{"sync_state", "tbr_state"}, null, null, null);
	   if(c != null)
	   {
		   if(c.moveToLast())
		   {
			   SYNC = c.getInt(c.getColumnIndex("sync_state"));
			   TBR = c.getInt(c.getColumnIndex("tbr_state"));
			   
			   //TODO: maybe update the wait detail if they have to wait
		   }
	   }
	   c.close();
 	   	
 	   checkInjectButton();
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
		return System.currentTimeMillis()/1000;			// Seconds since 1/1/1970 in UTC
	}
	
	/************************************************************************************
	* Log Messaging Functions
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
