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
import edu.virginia.dtc.Tvector.Tvector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.util.Log;
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
import android.widget.Toast;

public class MealActivity extends Activity{
	
	public static final String TAG = "MealActivity";
	public static final boolean DEBUG = true;
    public static final String IO_TEST_TAG = "MealActivityIO";
    private boolean enableIOtest = false;
	
    
    private Tvector USER_BASAL;
	private Tvector USER_CR;
	private Tvector Tvec_basal;
	private int USER_WEIGHT;
	
	// Meal screen correction BG target
 	private static final double MealScreenTargetBG = 110.0;
	
	public static final int DIAS_SERVICE_COMMAND_SEND_MEAL_BOLUS = 10;
	
	private static final int ACT_TIMEOUT = 300;		//Number of seconds until we close due to inactivity
 	
	private int DIAS_STATE, PUMP_STATE, PUMP_SERV_STATE, HYPO_LIGHT;
	private int TBR, SYNC;
	private double IOB;
	
	//TODO: Remove those hard-coded values and use the constraints from SysMan / SSMservice
	private static final double MEAL_MAX_CONSTRAINT = 20.0;
	private static final double CORRECTION_MAX_CONSTRAINT = 10.0;
	
	private double latestCR, latestCF;
	
	// Values that are needed for the Meal Screen
	private boolean apcProcessing = false;
	
	
	// long-acting insulin screen
	private double LAinputValue = 0.0;
	
	// Data input and advice screens
	private double bg1 = 0.0;
	private double mealCarbs = 0.0;
	private double mealInsulin = 0.0;
	private double algBolus;
	private double algBolusShow;
	private double corrRatio = 0.0;
	private double approvedBolus1 = 0.0;
	
	// Conventional correction calculation screen
	private double bgInsulin1;
	private double IOBinsulin1;
	private double IOBinsulin1show;
	private double convCorr1;
	
	// SMBG input and correction only screens
	private double bg2 = 0.0;				//SMBG input
	private double bgInsulin2;
	private double IOBinsulin2;
	private double IOBinsulin2show;
	private double convCorr2 = 0.0;		//Total insulin bolus
	private double approvedBolus2;
	
	
	
	private boolean LAvalid = false;
	private boolean mealCarbsValid = false;
	private boolean mealInsulinValid = false;
	private boolean bgValid1 = false;
	private boolean bgValid2 = false;
	private boolean corrValid = false;
	private boolean allValid = false;
	private boolean iob1status = false;
	private boolean iob2status = false;
	
	//UI Objects
	private EditText LAinputEditText;
	private EditText LAoutputEditText;
	private EditText SMBG1input;
	private EditText mealCarbsEditText;
	private EditText mealInsulinEditText;
	private TextView RatioPart1;
	private EditText ShowCorrRatio;
	private TextView RatioPart2;
	private EditText ShowAlgCorrInsulin;
	private EditText ShowMealInsulin; 
	private EditText CFcorr1;
	private EditText IOBcorr1;
	private EditText TotalConvCorr1;
	private EditText ApprovedInsulin1;
	private EditText ApprovedInsulin1Output;
    private EditText SMBG2input;
	private EditText CFcorr2;
	private EditText IOBcorr2;
	private EditText TotalConvCorr2;
	private EditText ApprovedInsulin2;
	private EditText ApprovedInsulin2Output; 
	private CheckBox iob1;
	private CheckBox iob2;
	
	private TextView waitDetail;
	
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
    

	private Dialog bolusConfirmationDialog;
	Context current_context;
    
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
		
		current_context = this;
		
		USER_BASAL = new Tvector();
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.a_long_or_rapid);
//		setContentView(R.layout.defaultmealscreen);
		
		Debug.i(TAG, FUNC_TAG, "Orientation: "+getResources().getConfiguration().orientation);
		
//		if (getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE)
//		{
//			WindowManager.LayoutParams params = getWindow().getAttributes();
//			params.height = getIntent().getIntExtra("height", 100);
//			params.width = getIntent().getIntExtra("width", 100);
//			
//			Debug.i(TAG, FUNC_TAG, "HEIGHT: "+getIntent().getIntExtra("height", 100)+" WIDTH: "+getIntent().getIntExtra("width", 100));
//			
//			ViewGroup.LayoutParams lParams = this.findViewById(R.id.defaultMealLayout).getLayoutParams();
//			
//			lParams.height = params.height;
//			lParams.height -= (0.07*lParams.height);
//			
//			lParams.width = params.width;
//			lParams.width -= (0.07*lParams.width);
//
//			params.gravity=Gravity.TOP;
//			
//			(this.findViewById(R.id.defaultMealLayout)).setLayoutParams(lParams);
//			
//			this.getWindow().setAttributes(params);
//		}
		
		Debug.i(TAG, FUNC_TAG, "OnCreate");
		
//		if(true)	//Params.getBoolean(getContentResolver(), "connection_scheduling", false))
//		{
//			((LinearLayout)this.findViewById(R.id.progressLayout)).setVisibility(View.VISIBLE);
//			((LinearLayout)this.findViewById(R.id.mealLayout)).setVisibility(View.GONE);
//			waitDetail = (TextView)this.findViewById(R.id.waitDetail);
//		}
//		else
//		{
//			((LinearLayout)this.findViewById(R.id.progressLayout)).setVisibility(View.GONE);
//			((LinearLayout)this.findViewById(R.id.mealLayout)).setVisibility(View.VISIBLE);
//		}
		
		setResult(RESULT_CANCELED);		//Set the result to cancelled unless we actually send a bolus with the UI

//		injectMeal = (Button)this.findViewById(R.id.injectMealBolusButton);
		
		//Read startup values
//		readStartupValues();
		
//		initMealScreen();
		
//		sysObserver = new SystemObserver(new Handler());
//		getContentResolver().registerContentObserver(Biometrics.SYSTEM_URI, true, sysObserver);
//
//		pumpObserver = new PumpObserver(new Handler());
//        getContentResolver().registerContentObserver(Biometrics.PUMP_DETAILS_URI, true, pumpObserver);
//        
//        stateObserver = new StateObserver(new Handler());
//        getContentResolver().registerContentObserver(Biometrics.STATE_URI, true, stateObserver);
        
		//Setup the UI
        int meal_activity_bolus_calculation_mode = Params.getInt(getContentResolver(), "meal_activity_bolus_calculation_mode", Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS);
//        switch(meal_activity_bolus_calculation_mode) 
//        {
//        	case Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS:
//				mealScreenOpenLoop();
//        		break;
//        	case Meal.MEAL_ACTIVITY_CALCULATES_BOLUS_PUMP_MODE:
//                switch(DIAS_STATE) {
//    				case State.DIAS_STATE_OPEN_LOOP:
//    					mealScreenOpenLoop();
//    					break;
//    				case State.DIAS_STATE_SAFETY_ONLY:
//    				case State.DIAS_STATE_CLOSED_LOOP:
//    					mealScreenClosedLoop();
//    					break;
//                }
//        		break;
//        	case Meal.MEAL_ACTIVITY_NEVER_CALCULATES_BOLUS:
//				mealScreenClosedLoop();
//        		break;
//        	default:
//				mealScreenOpenLoop();
//        		break;
//        }
        
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
		
//		Message uiMessage = Message.obtain(null, Meal.MCM_UI);
//		Bundle b = new Bundle();
//		b.putBoolean("end", true);
//		uiMessage.setData(b);
//		
//		try {
//			MCM.send(uiMessage);
//		} catch (RemoteException e) {
//			e.printStackTrace();
//		}
//		
		finish();
    }
	
	
	private void startMCM()
	{
		
		MCMservice = new ServiceConnection() 		
		{
        	final String FUNC_TAG = "MCMservice";
        	
            public void onServiceConnected(ComponentName className, IBinder service)             
            {
            	Debug.i(TAG, FUNC_TAG, "onServiceConnected LillyActvity");
                MCM = new Messenger(service);
                MCMbound = true;
                Debug.i(TAG, FUNC_TAG, "MCM service");

                try {
            		// Send a register-client message to the service with the client message handler in replyTo
					if (enableIOtest) 
					{
                		Bundle b1 = new Bundle();
                		b1.putString("description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+"MEAL_ACTIVITY_REGISTER");
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
					}
            		Message msg = Message.obtain(null, Meal.MEAL_ACTIVITY_REGISTER, 0, 0);
            		msg.replyTo = MCMmessenger;
            		MCM.send(msg);
                }
                catch (RemoteException e) {
            		e.printStackTrace();
                }
          
	            try {
	            	Debug.i(TAG, FUNC_TAG, "sending LILLY_MEAL");

	            	// Send a register-client message to the service with the client message handler in replyTo
	        		Message msg = Message.obtain(null, Meal.LILLY_MEAL_ACTIVITY_CALCULATE, 0, 0);
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
        
        // Bind
        Intent intent = new Intent("DiAs.MCMservice");
        bindService(intent, MCMservice, Context.BIND_AUTO_CREATE);
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
	            	
//	            	if(responseBundle.getBoolean("connected",false))
//	            	{
//	            		((LinearLayout)MealActivity.this.findViewById(R.id.progressLayout)).setVisibility(View.GONE);
//	        			((LinearLayout)MealActivity.this.findViewById(R.id.mealLayout)).setVisibility(View.VISIBLE);
//	            	}
//	            	else
//	            	{
//	            		Debug.i(TAG, FUNC_TAG, "Connection has failed or ignoring because another loop is busy");
//	            		finish();
//	            	}
	            	break;
	            case Meal.LMCM_CALCULATED_BOLUS:
	            	responseBundle = msg.getData();
	            	
	            	algBolus = responseBundle.getDouble("advised_bolus");
	            	int status = responseBundle.getInt("MCM_status");
					String description = responseBundle.getString("MCM_description");

					if (true) {
                		Bundle b1 = new Bundle();
                		b1.putString(	"description", "MCMservice >> (MealActivity), IO_TEST"+", "+FUNC_TAG+", "+
                						"MCM_CALCULATED_BOLUS"+", "+
                						"bolus="+algBolus+", "+
                						"status="+status+", "+
                						"description="+description
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
					}
	            	
					Debug.i(TAG, FUNC_TAG, "BOLUS: "+algBolus);
					
					algBolusShow = Math.floor(algBolus*2)/2;
					
					if(algBolus > (MEAL_MAX_CONSTRAINT + CORRECTION_MAX_CONSTRAINT))
					{
						Debug.w(TAG, FUNC_TAG, "Limiting bolus to maximum: "+(MEAL_MAX_CONSTRAINT + CORRECTION_MAX_CONSTRAINT));
						algBolus = (MEAL_MAX_CONSTRAINT + CORRECTION_MAX_CONSTRAINT);
					}
					
					allValid = true;
//					allInsulin = bolus;
//					allTotal.setText(String.format("%.2f", allInsulin));
	            	break;
	        	default:
					Debug.i(TAG, FUNC_TAG, "UNKNOWN_MESSAGE="+msg.what);
	        		super.handleMessage(msg);
            }
        }
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
		//Getting most recent subject information
 		subject_parameters();
	 	Debug.i(TAG, FUNC_TAG, "latest CR:"+latestCR+" CF: "+latestCF);

	 	mealCarbsListeners();
	 	mealInsulinListeners();
	 	smbg1Listeners();
	 	smbg2Listeners();	 	
	}
	 	
	
 	/***********************************************************************
	  ____  _  __  _______   ___________ ______
	 / __ \/ |/ / / ___/ /  /  _/ ___/ //_/ __/
	/ /_/ /    / / /__/ /___/ // /__/ ,< _\ \  
	\____/_/|_/  \___/____/___/\___/_/|_/___/  
	                                                   
	***********************************************************************/

	// if replied 'Long' at long_or_rapid screen	
	public void GoToLAinputScreen(View view) {
		setContentView(R.layout.b_long_insulin_screen);
		
		
//	   Cursor c = current_context.getContentResolver().query(Biometrics.BASAL_PROFILE_URI, null, null, null, null);
//	    
//	    double basal_value;
//	    long last_time_temp_secs = 0;
//		Log.i(TAG, "Retrieved BASAL_URI with " + c.getCount() + " items");
		
		getSubjectBasal();
		
		
//	    if (c.moveToFirst()) {
//		   do{
//			  basal_value = (double)c.getDouble(c.getColumnIndex("value")); 
//			  
//			  if(basal_value>=0){
//				  if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
//						last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
//					}				  				  
//			  Tvec_basal.put(c.getLong(c.getColumnIndex("time")), basal_value);  //min pmol/min
//			  }      
//		      //return_value = true;
//		   }while (c.moveToNext());
//	    }	
//	    c.close();
//	    
		 debug_message(TAG, "Tvec_basal(0)"+USER_BASAL.get_value(0)+"Tvec_basal(end)"+USER_BASAL.get_last_value());

	     
		
		
		LAinputEditText = (EditText)this.findViewById(R.id.editLAinput);
		LAinputEditText.setText(String.format("%.0f", USER_BASAL.get_value(0)));
		LAvalid = true;
		
		
		LAlisteners();
		
		refreshActivity();
	}
	
	public void CancelLAinputClick(View view) {
		finish();
		refreshActivity();
	}
	
	public void ApproveLAinputClick(View view) {
		if (LAvalid) {
			setContentView(R.layout.c_la_confirm_screen);
			LAoutputEditText = (EditText)this.findViewById(R.id.editLAoutput);
			LAoutputEditText.setText(String.format("%.0f", (double)LAinputValue));
			
			refreshActivity();
		}
		else
		{
			Toast.makeText(this, "Please complete data entry correctly", Toast.LENGTH_LONG).show();
		}
	}
	
	public void BackLAinputClick(View view) {
		setContentView(R.layout.b_long_insulin_screen);
		LAinputEditText = (EditText)this.findViewById(R.id.editLAinput);
		Debug.i(TAG, "test", "value"+ LAinputValue);
 		
		LAinputEditText.setText(String.format("%.0f", (double)LAinputValue));
		
		LAlisteners();
		
		refreshActivity();
	}
	
	public void ConfirmLAinputClick(View view) {
		finish();
		refreshActivity();
	}
	
	
	// if replied 'Rapid' at long_or_rapid screen	
	public void GoToEatOrNotScreen(View view) {
		setContentView(R.layout.d_eat_or_not);
		refreshActivity();
	}
	
	// if replied 'Yes' at eat_or_not screen
	public void GoToDataInputScreen(View view) {
		setContentView(R.layout.e_data_input_screen);
		SMBG1input = (EditText)this.findViewById(R.id.editSMBGmeal);
		mealCarbsEditText = (EditText)this.findViewById(R.id.editMealCarbs);
		mealInsulinEditText = (EditText)this.findViewById(R.id.editMealInsulin);
                
		smbg1Listeners();
		mealCarbsListeners();
		mealInsulinListeners();
		
		refreshActivity();
	}
	
	// if pressed 'Cancel' at data_input_screen
	public void CancelDataInputClick(View view) {
		finish();
		refreshActivity();
	}
	
	// if pressed 'Done' at data_input_screen
	public void DoneDataInputClick(View view) {
		
		if (bgValid1 && (mealCarbsValid || mealInsulinValid)) {
			
			setContentView(R.layout.f_meal_and_advice_screen);
					
			// show algorithm correction insulin
			ShowAlgCorrInsulin = (EditText)this.findViewById(R.id.editShowAlgCorrInsulin);
			ShowAlgCorrInsulin.setText(String.format("%.1f U", (double)algBolusShow));
			
			// show meal insulin
			ShowMealInsulin = (EditText)this.findViewById(R.id.editShowMealInsulin);
			if(mealInsulin != 0.0) {
				ShowMealInsulin.setText(String.format("%.1f U", (double)mealInsulin));
			}
			else if(mealCarbs != 0.0) {
				subject_parameters();
				mealInsulin = mealCarbs/latestCR;
				mealInsulin = Math.floor(mealInsulin*2)/2;
				ShowMealInsulin.setText(String.format("%.1f U", (double)mealInsulin));
			}
			
			// show total advised insulin
			approvedBolus1 = algBolusShow + mealInsulin;
			ApprovedInsulin1 = (EditText)this.findViewById(R.id.editApprovedInsulin1);
			ApprovedInsulin1.setText(String.format("%.1f U", (double)approvedBolus1));
			
			// show corrections ratio
			RatioPart1 = (TextView)this.findViewById(R.id.textRatioPart1);
			ShowCorrRatio = (EditText)this.findViewById(R.id.editShowCorrRatio);
			RatioPart2 = (TextView)this.findViewById(R.id.textRatioPart2);
			bgInsulin1 = (bg1-MealScreenTargetBG)*(1/latestCF);
			IOBinsulin1 = 1;
			IOBinsulin1show = 0;
			convCorr1 = bgInsulin1 - IOBinsulin1show;
			corrRatio = algBolusShow/convCorr1;
			ShowCorrRatio.setText(String.format("%.2f", (double)corrRatio));
			if (corrRatio > 1.25) {
				ShowCorrRatio.setTextColor(Color.RED);
				RatioPart1.setTextColor(Color.RED);
				RatioPart2.setTextColor(Color.RED);
			}
								
			refreshActivity();
		}
		else
		{
			Toast.makeText(this, "Please complete data entry correctly", Toast.LENGTH_LONG).show();
		}
	}

	// if pressed 'View Conventional Correction Calculation' at meal_and_advice_screen
	public void ViewConvCorrCalcClick1(View view) {
		setContentView(R.layout.g_conv_corr_calc_screen);
		
		// enable editText fields
		CFcorr1 = (EditText)this.findViewById(R.id.editCFcorr1);
		iob1 = (CheckBox)this.findViewById(R.id.iobCheckbox1);
		IOBcorr1 = (EditText)this.findViewById(R.id.editIOBcorr1);
		TotalConvCorr1 = (EditText)this.findViewById(R.id.editTotalConvCorr1);
		
		// get latest CF
		subject_parameters();
		
		bgInsulin1 = (bg1-MealScreenTargetBG)*(1/latestCF);
		CFcorr1.setText(String.format("%.1f U", (double)bgInsulin1));
		if (iob1status)
		{
			iob1.setChecked(true);
		}
		
		if (iob1.isChecked()) 
 		{
 			if (IOBinsulin1 > 0) {
 				IOBinsulin1show = IOBinsulin1;
 				convCorr1 = bgInsulin1 - IOBinsulin1show;
 				IOBcorr1.setText(String.format("%.1f U", (double)IOBinsulin1show));
 				TotalConvCorr1.setText(String.format("%.1f U", (double)convCorr1));
 			}
 			else
 				IOBcorr1.setText("");
	 	}
	 	else 
	 	{
	 		IOBcorr1.setText("");
	 		IOBinsulin1show = 0;
	 		convCorr1 = bgInsulin1 - IOBinsulin1show;
			TotalConvCorr1.setText(String.format("%.1f U", (double)convCorr1));
	 	}
   		refreshActivity();
	}

	public void checkboxIOBClick1(View view) 
	{
		refreshActivity();
		
		if (iob1.isChecked()) 
 		{
 			if (IOBinsulin1 > 0) {
 				IOBinsulin1show = IOBinsulin1;
 				convCorr1 = bgInsulin1 - IOBinsulin1show;
 				IOBcorr1.setText(String.format("%.1f U", (double)IOBinsulin1show));
 				TotalConvCorr1.setText(String.format("%.1f U", (double)convCorr1));
 			}
 			else
 				IOBcorr1.setText("");
 				iob1status = true;
	 	}
	 	else 
	 	{
	 		IOBcorr1.setText("");
	 		IOBinsulin1show = 0;
	 		convCorr1 = bgInsulin1 - IOBinsulin1show;
			TotalConvCorr1.setText(String.format("%.1f U", (double)convCorr1));
			iob1status = false;
	 	}
   	}
	
	public void BackFromConvCorrCalc1(View view) {
		setContentView(R.layout.f_meal_and_advice_screen);
		
		// show algorithm correction insulin
		ShowAlgCorrInsulin = (EditText)this.findViewById(R.id.editShowAlgCorrInsulin);
		ShowAlgCorrInsulin.setText(String.format("%.1f U", (double)algBolusShow));
		
		// show meal insulin
		ShowMealInsulin = (EditText)this.findViewById(R.id.editShowMealInsulin);
		ShowMealInsulin.setText(String.format("%.1f U", (double)mealInsulin));
		
		// show total advised insulin
		ApprovedInsulin1 = (EditText)this.findViewById(R.id.editApprovedInsulin1);
		ApprovedInsulin1.setText(String.format("%.1f U", (double)approvedBolus1));
		
		// show corrections ratio
		corrRatio = algBolusShow/convCorr1;
		RatioPart1 = (TextView)this.findViewById(R.id.textRatioPart1);
		ShowCorrRatio = (EditText)this.findViewById(R.id.editShowCorrRatio);
		RatioPart2 = (TextView)this.findViewById(R.id.textRatioPart2);
		ShowCorrRatio.setText(String.format("%.2f", (double)corrRatio));
		if (corrRatio > 1.25) {
			ShowCorrRatio.setTextColor(Color.RED);
			RatioPart1.setTextColor(Color.RED);
			RatioPart2.setTextColor(Color.RED);
		}
		
		refreshActivity();
	}
	
	public void MinusClick1(View view) {
	    ApprovedInsulin1 = (EditText)this.findViewById(R.id.editApprovedInsulin1);
		approvedBolus1 = approvedBolus1 - 0.50;
		if (approvedBolus1 < 0.0) {
	    	approvedBolus1 = 0.0;
	    }
	    
	    ApprovedInsulin1.setText(String.format("%.1f U", (double)approvedBolus1));
    }
	
	public void PlusClick1(View view) {
		ApprovedInsulin1 = (EditText)this.findViewById(R.id.editApprovedInsulin1);
		approvedBolus1 = approvedBolus1 + 0.50;
		if (approvedBolus1 > 30.0) {
	    	approvedBolus1 = 30.0;
	    }
	    ApprovedInsulin1.setText(String.format("%.1f U", (double)approvedBolus1));
	}
	
	// if pressed 'Cancel' at meal_and_advice_screen
	public void CancelMealAndAdviceClick(View view) {
		finish();
	}
	
	public void ApproveMealAndAdviceClick(View view) {
		setContentView(R.layout.h_meal_confirm_screen);
		ApprovedInsulin1Output = (EditText)this.findViewById(R.id.editApprovedInsulin1output);
		ApprovedInsulin1Output.setText(String.format("%.1f", (double)approvedBolus1));
	}
	
	public void BackMealAndAdviceClick(View view) {
		setContentView(R.layout.f_meal_and_advice_screen);
		
		// show algorithm correction insulin
		ShowAlgCorrInsulin = (EditText)this.findViewById(R.id.editShowAlgCorrInsulin);
		ShowAlgCorrInsulin.setText(String.format("%.1f U", (double)algBolusShow));
		
		// show meal insulin
		ShowMealInsulin = (EditText)this.findViewById(R.id.editShowMealInsulin);
		ShowMealInsulin.setText(String.format("%.1f U", (double)mealInsulin));
		
		// show total advised insulin
		ApprovedInsulin1 = (EditText)this.findViewById(R.id.editApprovedInsulin1);
		ApprovedInsulin1.setText(String.format("%.1f U", (double)approvedBolus1));
		
		// show corrections ratio
		RatioPart1 = (TextView)this.findViewById(R.id.textRatioPart1);
		ShowCorrRatio = (EditText)this.findViewById(R.id.editShowCorrRatio);
		RatioPart2 = (TextView)this.findViewById(R.id.textRatioPart2);
		ShowCorrRatio.setText(String.format("%.2f", (double)corrRatio));
		if (corrRatio > 1.25) {
			ShowCorrRatio.setTextColor(Color.RED);
			RatioPart1.setTextColor(Color.RED);
			RatioPart2.setTextColor(Color.RED);
		}
		
		refreshActivity();
	}
	
	public void ConfirmMealAndAdviceClick(View view) {
		finish();
		refreshActivity();
	}
	
	
	// if replied 'No'at eat_or_not screen
	public void GoToSMBGinputScreen(View view) {
		setContentView(R.layout.i_smbg_input_screen);
		SMBG2input = (EditText)this.findViewById(R.id.editSMBGcorrOnly);
		smbg2Listeners();
		refreshActivity();
	}

	// if pressed 'Cancel' at smbg_input_screen
	public void CancelSMBGinputClick(View view) {
		finish();
	}

	// if pressed 'Done' at smbg_input_screen
	public void DoneSMBGinputClick(View view) {
		
		if (bgValid2) {
			
			setContentView(R.layout.j_corr_only_screen);
			
			// enable editText fields
			CFcorr2 = (EditText)this.findViewById(R.id.editCFcorr2);
			iob2 = (CheckBox)this.findViewById(R.id.iobCheckbox2);
			IOBcorr2 = (EditText)this.findViewById(R.id.editIOBcorr2);
			TotalConvCorr2 = (EditText)this.findViewById(R.id.editTotalConvCorr2);
			ApprovedInsulin2 = (EditText)this.findViewById(R.id.editApprovedInsulin2);
					
			// get latest CF
			subject_parameters();
			
			bgInsulin2 = (bg2-MealScreenTargetBG)*(1/latestCF);
			bgInsulin2 = Math.floor(bgInsulin2*2)/2;
			if (bgInsulin2 < 0) {
				bgInsulin2 = 0.0;
			}
			IOBinsulin2 = 1;
			IOBinsulin2show = 0;
			convCorr2 = bgInsulin2 - IOBinsulin2show;
			if (convCorr2 < 0) {
				convCorr2 = 0.0;
			}
			approvedBolus2 = convCorr2;
			CFcorr2.setText(String.format("%.1f U", (double)bgInsulin2));
			IOBcorr2.setText(String.format("%.1f U", (double)IOBinsulin2show));
			TotalConvCorr2.setText(String.format("%.1f U", (double)convCorr2));
			ApprovedInsulin2.setText(String.format("%.1f U", (double)approvedBolus2));
			refreshActivity();
		}
		else
		{
			Toast.makeText(this, "Please complete data entry correctly", Toast.LENGTH_LONG).show();
		}
	}
		
	
	
	public void checkboxIOBClick2(View view) 
	{
		refreshActivity();
		
		if (iob2.isChecked()) 
 		{
 			if (IOBinsulin2 > 0) {
 				IOBinsulin2show = IOBinsulin2;
 				convCorr2 = bgInsulin2 - IOBinsulin2show;
 				if (convCorr2 < 0) {
 					convCorr2 = 0.0;
 				}
 				approvedBolus2 = convCorr2;
 				IOBcorr2.setText(String.format("%.1f U", (double)IOBinsulin2show));
 				TotalConvCorr2.setText(String.format("%.1f U", (double)convCorr2));
 				ApprovedInsulin2.setText(String.format("%.1f U", (double)approvedBolus2));
 				
 			}
 			else
 				IOBcorr2.setText("");
 				iob2status = true;
	 	}
	 	else 
	 	{
	 		IOBcorr2.setText("");
	 		IOBinsulin2show = 0;
	 		convCorr2 = bgInsulin2 - IOBinsulin2show;
	 		approvedBolus2 = convCorr2;
			TotalConvCorr2.setText(String.format("%.1f U", (double)convCorr2));
			ApprovedInsulin2.setText(String.format("%.1f U", (double)approvedBolus2));
			iob2status = false;
	 	}
   	}
	
	public void MinusClick2(View view) {
	    ApprovedInsulin2 = (EditText)this.findViewById(R.id.editApprovedInsulin2);
	    approvedBolus2 = approvedBolus2 - 0.50;
	    if (approvedBolus2 < 0.0) {
	        approvedBolus2 = 0.0;
	    }
	    ApprovedInsulin2.setText(String.format("%.1f U", (double)approvedBolus2));
    }
	
	public void PlusClick2(View view) {
		ApprovedInsulin2 = (EditText)this.findViewById(R.id.editApprovedInsulin2);
		approvedBolus2 = approvedBolus2 + 0.50;
		if (approvedBolus2 > 30.0) {
	    	approvedBolus2 = 30.0;
	    }
	    
	    ApprovedInsulin2.setText(String.format("%.1f U", (double)approvedBolus2));
	}
	
	// if pressed 'Cancel' at corr_only_screen
	public void CancelCorrOnlyClick(View view) {
		finish();
	}
	
	
	public void ApproveCorrOnlyClick(View view) {
		setContentView(R.layout.k_corr_only_confirm_screen);
		ApprovedInsulin2Output = (EditText)this.findViewById(R.id.editApprovedInsulin2output);
		ApprovedInsulin2Output.setText(String.format("%.1f", (double)approvedBolus2));
	}
	
	public void BackCorrOnlyClick(View view) {
		setContentView(R.layout.j_corr_only_screen);
		
		// enable editText fields
		CFcorr2 = (EditText)this.findViewById(R.id.editCFcorr2);
		CFcorr2.setText(String.format("%.1f U", (double)bgInsulin2));
		
		iob2 = (CheckBox)this.findViewById(R.id.iobCheckbox2);
		if (iob2status)
		{
			iob2.setChecked(true);
		}
		
		
		IOBcorr2 = (EditText)this.findViewById(R.id.editIOBcorr2);
		IOBcorr2.setText(String.format("%.1f U", (double)IOBinsulin2show));
		
		TotalConvCorr2 = (EditText)this.findViewById(R.id.editTotalConvCorr2);
		TotalConvCorr2.setText(String.format("%.1f U", (double)convCorr2));
		
		ApprovedInsulin2 = (EditText)this.findViewById(R.id.editApprovedInsulin2);
		ApprovedInsulin2.setText(String.format("%.1f U", (double)approvedBolus2));
				
		refreshActivity();
	}
	
	public void ConfirmCorrOnlyClick(View view) {
		finish();
	}
	

 	/*************************************************************
	   __   ___     _____  _____  __  ________
	  / /  / _ |   /  _/ |/ / _ \/ / / /_  __/
	 / /__/ __ |  _/ //    / ___/ /_/ / / /   
	/____/_/ |_| /___/_/|_/_/   \____/ /_/    
	                                          
 	*************************************************************/
 	private boolean LAinput(String input)
 	{
 		final String FUNC_TAG = "LAinput";
 		
 		LAinputValue = Double.parseDouble(input);
 		LAinputValue = Math.round(LAinputValue);
 		
		if(LAinputValue >= 5.0 && LAinputValue <= 50.0)
		{
			LAinputEditText.setTextColor(Color.BLACK);
			LAvalid = true;
			Debug.i(TAG, FUNC_TAG, "validity"+ LAvalid);
	 		
			return false;
		}
		else
		{
			LAinputEditText.setTextColor(Color.RED);	//Set input to red text
			LAvalid = false;
			Debug.i(TAG, FUNC_TAG, "validity"+ LAvalid);
	 		return true;
		}
 	}
 	
 	
 	private void LAlisteners()
 	{
 		final String FUNC_TAG = "LAlisteners";
 		
 		LAinputEditText.setOnEditorActionListener(new OnEditorActionListener() 
	 	{
	 		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) 
	 		{
	 			refreshActivity();
	 			
	 			LAvalid = allValid = false;
    	 		
	 			if (v.getText().toString().length() > 0) 
	 			{
	 				return LAinput(v.getText().toString());
	 			}
	 			else
	 				return true;
	 		}
	 	});
 		LAinputEditText.setOnFocusChangeListener(new OnFocusChangeListener()
 		{
 			public void onFocusChange(View v, boolean hasFocus){
 				refreshActivity();
	 			
        		if (!hasFocus) 
        		{
        			LAvalid = allValid = false;
        	 		
    	 			if (((TextView)v).getText().toString().length() > 0) 
    	 			{
    	 				LAinput(((TextView)v).getText().toString());
    	 			}
        		}
            }
        });
 		
 	}
	
	
 	/*************************************************************
	   ______  ______  ________  _____  _____  __  ________
	  / __/  |/  / _ )/ ___<  / /  _/ |/ / _ \/ / / /_  __/
	 _\ \/ /|_/ / _  / (_ // / _/ //    / ___/ /_/ / / /   
	/___/_/  /_/____/\___//_/ /___/_/|_/_/   \____/ /_/    
                                                       
 	*************************************************************/
 	private boolean smbg1Input(String input)
 	{
 		final String FUNC_TAG = "smbg1Input";
 		
 		bg1 = Double.parseDouble(input);
 		Debug.i(TAG, FUNC_TAG, "Input: "+input+" SMBG: "+bg1);
 		
 		int blood_glucose_display_units = Params.getInt(getContentResolver(), "blood_glucose_display_units", CGM.BG_UNITS_MG_PER_DL);
		if (blood_glucose_display_units == CGM.BG_UNITS_MMOL_PER_L) 
			bg1 = bg1 * CGM.MGDL_PER_MMOLL;
		
		Debug.i(TAG, FUNC_TAG, "Post Conversion - SMBG: "+bg1);
		
		Toast.makeText(getApplicationContext(),	"SMBG: " + bg1, Toast.LENGTH_SHORT).show();
		
		double limit = (CORRECTION_MAX_CONSTRAINT * latestCF) + MealScreenTargetBG;
		Debug.i(TAG, FUNC_TAG, "Limit of "+limit+" mg/dL can be entered");
		
		if(bg1 >= 39.0 && bg1 <= 401.0)
		{
			SMBG1input.setTextColor(Color.BLACK);
			bgValid1 = true;
			return false;
		}
		else
		{
			SMBG1input.setTextColor(Color.RED);	//Set input to red text
			bgValid1 = false;
			return true;
		}
 	}
 	
 	
 	private void smbg1Listeners()
 	{
 		final String FUNC_TAG = "smbg1Listeners";
 		
 		SMBG1input.setOnEditorActionListener(new OnEditorActionListener() 
	 	{
	 		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) 
	 		{
	 			refreshActivity();
	 			
	 			bgValid1 = allValid = false;
    	 		
	 			if (v.getText().toString().length() > 0) 
	 			{
	 				return smbg1Input(v.getText().toString());
	 			}
	 			else
	 				return true;
	 		}
	 	});
 		SMBG1input.setOnFocusChangeListener(new OnFocusChangeListener()
 		{
 			public void onFocusChange(View v, boolean hasFocus){
 				refreshActivity();
	 			
        		if (!hasFocus) 
        		{
        			bgValid1 = allValid = false;
        	 		
    	 			if (((TextView)v).getText().toString().length() > 0) 
    	 			{
    	 				smbg1Input(((TextView)v).getText().toString());
    	 			}
        		}
            }
        });
 		
 		SMBG1input.setText("");
 	}
	
 	/***********************************************************************
	   __  __________   __     ________   ___  ___  ____  _____  _____  __  ________
	  /  |/  / __/ _ | / /    / ___/ _ | / _ \/ _ )/ __/ /  _/ |/ / _ \/ / / /_  __/
	 / /|_/ / _// __ |/ /__  / /__/ __ |/ , _/ _  |\ \  _/ //    / ___/ /_/ / / /   
	/_/  /_/___/_/ |_/____/  \___/_/ |_/_/|_/____/___/ /___/_/|_/_/   \____/ /_/    
	                                                                                                                                                       
	***********************************************************************/
	
 	private boolean mealCarbsInput(String input)
	{
		final String FUNC_TAG = "mealCarbsInput";
		
		mealCarbs = Double.parseDouble(input);
		Debug.i(TAG, FUNC_TAG, "Input: "+input+" Carbs: " + mealCarbs);
		
		mealInsulin = 0;
	 	mealInsulinEditText.setText("");

	 	if(mealCarbs >= 10.0 && mealCarbs <= 200.0)
		{
			mealCarbsEditText.setTextColor(Color.BLACK);
			mealCarbsValid = true;
			return false;
		}
		else
		{
			mealCarbsEditText.setTextColor(Color.RED);		//Set input to red text
			mealCarbsValid = false;
			return true;
		}
	}
	
 	private void mealCarbsListeners()
 	{
 		final String FUNC_TAG = "mealCarbsListeners";
 		
	 	mealCarbsEditText.setOnEditorActionListener(new OnEditorActionListener() 
	 	{
	 		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) 
	 		{
	 			refreshActivity();
	 			
	 			mealCarbsValid = allValid = false;
	 			
	 			if (v.getText().toString().length() > 0) 
	 			{
	 				return mealCarbsInput(v.getText().toString());
	 			}
	 			else
	 				return true;
	 		}
	 	});
	 	mealCarbsEditText.setOnFocusChangeListener(new OnFocusChangeListener()
	 	{
            public void onFocusChange(View v, boolean hasFocus)
            {
            	refreshActivity();
	 			
        		if (!hasFocus) 
        		{
        			mealCarbsValid = allValid = false;

    	 			if (((TextView)v).getText().toString().length() > 0) 
    	 			{
    	 				mealCarbsInput(((TextView)v).getText().toString());
    	 			}
        		}
            }
	 	});
	 	
	 	mealCarbsEditText.setText("");
 	}

 	/***********************************************************************
	   __  __________   __     _____  ________  ____   _____  __  _____  _____  __  ________
	  /  |/  / __/ _ | / /    /  _/ |/ / __/ / / / /  /  _/ |/ / /  _/ |/ / _ \/ / / /_  __/
	 / /|_/ / _// __ |/ /__  _/ //    /\ \/ /_/ / /___/ //    / _/ //    / ___/ /_/ / / /   
	/_/  /_/___/_/ |_/____/ /___/_/|_/___/\____/____/___/_/|_/ /___/_/|_/_/   \____/ /_/    
	                                                                                       	                                                                                                                                                       
	***********************************************************************/
	private boolean mealInsulinInput(String input)
	{
		final String FUNC_TAG = "mealInsulinInput";
		
		mealInsulin = Double.parseDouble(input);
		mealInsulin = Math.floor(mealInsulin*2)/2;
		mealInsulinEditText.setText(String.format("%.1f", (double)mealInsulin));
		
		
		Debug.i(TAG, FUNC_TAG, "Input: "+input+" Carbs: " + mealCarbs);
		
		mealCarbs = 0;
	 	mealCarbsEditText.setText("");

		if(mealInsulin >= 0.5 && mealInsulin <= 20.0)
		{
			mealInsulinEditText.setTextColor(Color.BLACK);
			mealInsulinValid = true;
			return false;
		}
		else
		{
			mealInsulinEditText.setTextColor(Color.RED);		//Set input to red text
			mealInsulinValid = false;
			return true;
		}
	}
	
 	private void mealInsulinListeners()
 	{
 		final String FUNC_TAG = "mealInsulinListeners";
 		
	 	mealInsulinEditText.setOnEditorActionListener(new OnEditorActionListener() 
	 	{
	 		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) 
	 		{
	 			refreshActivity();
	 			
	 			mealInsulinValid = allValid = false;
	 			
	 			if (v.getText().toString().length() > 0) 
	 			{
	 				return mealInsulinInput(v.getText().toString());
	 			}
	 			else
	 				return true;
	 		}
	 	});
	 	mealInsulinEditText.setOnFocusChangeListener(new OnFocusChangeListener()
	 	{
            public void onFocusChange(View v, boolean hasFocus)
            {
            	refreshActivity();
	 			
        		if (!hasFocus) 
        		{
        			mealInsulinValid = allValid = false;

    	 			if (((TextView)v).getText().toString().length() > 0) 
    	 			{
    	 				mealInsulinInput(((TextView)v).getText().toString());
    	 			}
        		}
            }
	 	});
	 	
	 	mealInsulinEditText.setText("");
 	}
 	
 	/*************************************************************
	   ______  ______  ________    _____  _____  __  ________
	  / __/  |/  / _ )/ ___/_  |  /  _/ |/ / _ \/ / / /_  __/
	 _\ \/ /|_/ / _  / (_ / __/  _/ //    / ___/ /_/ / / /   
	/___/_/  /_/____/\___/____/ /___/_/|_/_/   \____/ /_/    
	                                                        
	*************************************************************/
	private boolean smbg2Input(String input)
	{
		final String FUNC_TAG = "smbgInput";
		
		bg2 = Double.parseDouble(input);
		Debug.i(TAG, FUNC_TAG, "Input: "+input+" SMBG: "+bg2);
		
		int blood_glucose_display_units = Params.getInt(getContentResolver(), "blood_glucose_display_units", CGM.BG_UNITS_MG_PER_DL);
		if (blood_glucose_display_units == CGM.BG_UNITS_MMOL_PER_L) 
			bg2 = bg2 * CGM.MGDL_PER_MMOLL;
		
		Debug.i(TAG, FUNC_TAG, "Post Conversion - SMBG: "+bg2);
		
		Toast.makeText(getApplicationContext(),	"SMBG: " + bg2, Toast.LENGTH_SHORT).show();
		
		double limit = (CORRECTION_MAX_CONSTRAINT * latestCF) + MealScreenTargetBG;
		Debug.i(TAG, FUNC_TAG, "Limit of "+limit+" mg/dL can be entered");
		
		if(bg2 >= 39.0 && bg2 <= 401.0)
		{
			SMBG2input.setTextColor(Color.BLACK);
			bgValid2 = true;
			return false;
		}
		else
		{
			SMBG2input.setTextColor(Color.RED);	//Set input to red text
			bgValid2 = false;
			return true;
		}
	}
	
	private void smbg2Listeners()
	{
		final String FUNC_TAG = "smbgListeners";
		
		SMBG2input.setOnEditorActionListener(new OnEditorActionListener() 
	 	{
	 		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) 
	 		{
	 			refreshActivity();
	 			
	 			bgValid2 = allValid = false;
 	 		
	 			if (v.getText().toString().length() > 0) 
	 			{
	 				return smbg2Input(v.getText().toString());
	 			}
	 			else
	 				return true;
	 		}
	 	});
		SMBG2input.setOnFocusChangeListener(new OnFocusChangeListener()
		{
			public void onFocusChange(View v, boolean hasFocus){
				refreshActivity();
	 			
     		if (!hasFocus) 
     		{
     			bgValid2 = allValid = false;
     	 		
 	 			if (((TextView)v).getText().toString().length() > 0) 
 	 			{
 	 				smbg2Input(((TextView)v).getText().toString());
 	 			}
     		}
         }
     });
		
		SMBG2input.setText("");
//	 	bgTotal.setText("");
	}
	
 	
 	/***********************************************************************************
 	   __ ________   ___  _______    ______  ___  _____________________  _  ______
  	  / // / __/ /  / _ \/ __/ _ \  / __/ / / / |/ / ___/_  __/  _/ __ \/ |/ / __/
 	 / _  / _// /__/ ___/ _// , _/ / _// /_/ /    / /__  / / _/ // /_/ /    /\ \  
	/_//_/___/____/_/  /___/_/|_| /_/  \____/_/|_/\___/ /_/ /___/\____/_/|_/___/  
                                                                              
 	***********************************************************************************/
//	public void calculateBolus()
//	{
//		final String FUNC_TAG = "calculateBolus";
//		
//		Debug.i(TAG, FUNC_TAG, "Starting bolus calculation...");
//		
//	 	double carb_insulin;
//	 	double iob_insulin;
//	 	double bg_insulin;
//	 	double corr_insulin;
//	 	
//	    int calc_mode = Params.getInt(getContentResolver(), "meal_activity_bolus_calculation_mode", Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS);
//	    
//	    //OPEN LOOP MEAL ACTIVITY CALCULATION MODE
//	    //----------------------------------------------------------------------------------------------------------------------------------------------------------------
//	    if (calc_mode == Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS || calc_mode == Meal.MEAL_ACTIVITY_CALCULATES_BOLUS_PUMP_MODE && DIAS_STATE == State.DIAS_STATE_OPEN_LOOP)
//	 	{
//	    	Debug.d(TAG, FUNC_TAG, "Meal Activity is calculating bolus...");
//	    	
//	 		DecimalFormat iobFormat = new DecimalFormat();
//	 		iobFormat.setMaximumFractionDigits(2);
//	 		iobFormat.setMinimumFractionDigits(2);
//	 		
//	 		if (iob.isChecked()) 
//	 		{
//	 			if (IOB > 0) 
//	 			{
//	 				iobTotal.setText(iobFormat.format(IOB));
//	 				iob_insulin = IOB;
//	 			}
//	 			else 
//	 			{
//	 				iobTotal.setText("");
//	 				iob_insulin = 0.0;
//	 			}
//	 		}
//	 		else 
//	 			iob_insulin = 0.0;
//	 		
////	 		if (carbsValid) 
////	 			carb_insulin = carbsInsulin;
////	 		else 
////	 			carb_insulin = 0;
//	 		
//	 		if (corrValid) 
//	 			corr_insulin = corrInsulin;
//	 		else 
//	 			corr_insulin = 0;
//	 		
//	 		if (bgValid) 
//	 		{
//	 			subject_parameters();
//	 			
//	 			// Negative BG correction insulin is permitted
//	 			bg_insulin = (bg2-MealScreenTargetBG)*(1/latestCF);
//	 			bgTotal.setText(""+(((double)(int)(bg_insulin*100))/100));
//	 		} 
//	 		else 
//	 			bg_insulin = 0;
//	 		
//	 		// Added on 07-25-14
////	 		double MealScreenTotalBolusMax = Math.min(20.0, 100.0/latestCR);
////	 		Cursor c = getContentResolver().query(Biometrics.PUMP_DETAILS_URI, null, null, null, null);
////			if (c.moveToLast())
////			{
////				MealScreenTotalBolusMax = 2 * c.getDouble(c.getColumnIndex("max_bolus_U"));
////			}
////			c.close();
//			double maxBolus = MEAL_MAX_CONSTRAINT + CORRECTION_MAX_CONSTRAINT;
//			// End of Added on 07-25-14
//			
//			Debug.i(TAG, FUNC_TAG, "Maximum allowed bolus is "+maxBolus+" U!");
//	 		
//			mealBolus = carb_insulin;
//			corrBolus = bg_insulin - iob_insulin + corr_insulin;
//			
//			allInsulin = mealBolus + corrBolus;
//			
//			allValid = (carbsValid && corrValid && bgValid);
//			if(allValid)
//			{
//	 			if (allInsulin <= 0.0) 						//Insulin is less than zero
//	 			{
//	 				Debug.w(TAG, FUNC_TAG, "Insulin is zero or less...");
//	 				
//	 				mealBolus = 0.0;
//	 				corrBolus = 0.0;
//	 				
//	 				allInsulin = 0.0;
//	 			}
//	 			else 	//if(allInsulin > maxBolus)				//Insulin is over the maximum
//	 			{
//	 				if(mealBolus > MEAL_MAX_CONSTRAINT)
//	 				{
//	 					Debug.w(TAG, FUNC_TAG, "Meal bolus is being capped at: "+MEAL_MAX_CONSTRAINT+" U");
//	 					mealBolus = MEAL_MAX_CONSTRAINT;
//	 				}
//	 				
//	 				if(corrBolus > CORRECTION_MAX_CONSTRAINT)
//	 				{
//	 					Debug.w(TAG, FUNC_TAG, "Correction bolus is being capped at: "+CORRECTION_MAX_CONSTRAINT+" U");
//	 					corrBolus = CORRECTION_MAX_CONSTRAINT;
//	 				}
//	 				
//	 				if (corrBolus < 0.0) 
// 	 				{
//	 					Debug.w(TAG, FUNC_TAG, "Correction is negative, modifying meal!");
// 	 					if (mealBolus + corrBolus > 0.0) 
// 	 					{
// 	 						mealBolus += corrBolus;
// 	 						corrBolus = 0.0;
// 	 					}
// 	 					else 
// 	 					{
// 	 						mealBolus = 0.0;
// 	 						corrBolus = 0.0;
// 	 					}
// 	 				}
//	 				
//	 				Debug.i(TAG, FUNC_TAG, "Meal Bolus: "+mealBolus);
//	 				Debug.i(TAG, FUNC_TAG, "Corr Bolus: "+corrBolus);
//	 				
//	 				/*
//	 	 				// Make sure the limit (min(20,100/CHI)) is enforced
//	 	 				if((MealScreenMealBolus + MealScreenCorrectionBolus) > allInsulin)
//	 	 				{
//	 	 					Debug.i(TAG, FUNC_TAG, "Enforcing min(20,100/CHI) limit, Original boluses:  MealBolus: "+MealScreenMealBolus+" CorrectionBolus: "+MealScreenCorrectionBolus);
//	 	 					double diff = (MealScreenMealBolus + MealScreenCorrectionBolus)-allInsulin;
//	 	 					if(MealScreenCorrectionBolus < diff)		//If the difference is greater than the correction bolus we need to zero correction and subtract the remainder from meal bolus
//	 	 					{
//	 	 						diff -= MealScreenCorrectionBolus;		//Remove the size of correction from the difference
//	 	 						MealScreenCorrectionBolus = 0.0;		//Zero out the correction bolus
//	 	 						MealScreenMealBolus -= diff;			//Take the remaining difference from the meal bolus
//	 	 					}
//	 	 					else										//If the correction is greater than or equal to the difference, then we can just subtract it from the correction bolus and we're done
//	 	 					{
//	 	 						MealScreenCorrectionBolus -= diff;
//	 	 					}
//	 	 					Debug.i(TAG, FUNC_TAG, "Limit enforced:  MealBolus: "+MealScreenMealBolus+" CorrectionBolus: "+MealScreenCorrectionBolus);
//	 	 				}
//	 	 			*/
//	 				
//	 				allInsulin = mealBolus + corrBolus;
//	 			}
//			}
//			else
//			{
//				Debug.w(TAG, FUNC_TAG, "Not all inputs are valid > Carb: "+carbsValid+" BG: "+bgValid+" Corr: "+corrValid);
//				allInsulin = 0.0;
//			}
// 			
// 			Debug.i(TAG, FUNC_TAG, "Total: "+allInsulin+" U");
// 			
//	 		allTotal.setText(String.format("%.2f", allInsulin));
//	 	}
//	    //CLOSED LOOP/MCM ACTIVITY CALCULATION MODE
//	    //----------------------------------------------------------------------------------------------------------------------------------------------------------------
//	 	else 
//	 	{
//	 		// MCM calculates bolus
//	 		Debug.d(TAG, FUNC_TAG, "MCM is calculating bolus...");
//	 		
//	 		if (carbsValid && bgValid && !apcProcessing && allValid) 
//	 		{
//	 			apcProcessing = true;
//	 			
//	 			Message calcMessage = Message.obtain(null, Meal.MEAL_ACTIVITY_CALCULATE);
//	 			Bundle b = new Bundle();
//	 			b.putDouble("MealScreenCHO", carbs);
//	 			b.putDouble("MealScreenBG", bg2);
//	 			calcMessage.setData(b);
//	 			
//	 			try {
//					if (true) {
//                		Bundle b1 = new Bundle();
//                		b1.putString(	"description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
//                						"MEAL_ACTIVITY_CALCULATE"+", "+
//                						"MealScreenCHO="+carbs+", "+
//                						"MealScreenBG="+bg2
//                					);
//                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
//					}
//					MCM.send(calcMessage);
//				} catch (RemoteException e) {
//					e.printStackTrace();
//				}
//	 		}
//	 		else 
//    	 		Debug.i(TAG, FUNC_TAG, "Skiping MEAL_ACTIVITY_CALCULATE > Carb: "+carbsValid+" BG: "+bgValid+" Corr: "+corrValid+" APC Processing: "+apcProcessing);
//	 	}
//	}
	
	/************************************************************************************
	* Listener Functions
	************************************************************************************/
	
	
//	public void injectMealBolusClick(View view) {
//		final String FUNC_TAG = "injectMealBolusClick";
//		
//		refreshActivity();
//		
//	 	if (allValid && allInsulin >= 0.1) 
//	 	{
//     	 	Debug.i(TAG, FUNC_TAG, "Inject "+allInsulin+" U of insulin.");
//     	 	log_action(TAG, "MEAL INJECT CLICK = "+allInsulin+"U");
//     	 
//    	    int calc_mode = Params.getInt(getContentResolver(), "meal_activity_bolus_calculation_mode", Meal.MEAL_ACTIVITY_ALWAYS_CALCULATES_BOLUS);
//    	    //CLOSED LOOP/MCM ACTIVITY CALCULATION MODE
//    	    //----------------------------------------------------------------------------------------------------------------------------------------------------------------
//    	    if (calc_mode == Meal.MEAL_ACTIVITY_NEVER_CALCULATES_BOLUS || calc_mode == Meal.MEAL_ACTIVITY_CALCULATES_BOLUS_PUMP_MODE && DIAS_STATE != State.DIAS_STATE_OPEN_LOOP) 
//    	    {
//				Message calcMessage = Message.obtain(null, Meal.MEAL_ACTIVITY_INJECT);
//	 			Bundle b = new Bundle();
//	 			b.putDouble("mealSize", carbs);
//	 			b.putDouble("smbg", bg2);
//	 			b.putDouble("corr", 0.0);
//	 			b.putDouble("meal", 0.0);
//	 			b.putDouble("credit", allInsulin);
//	 			b.putDouble("spend", allInsulin);
//	 			calcMessage.setData(b);
//	 			
//	 			try 
//	 			{
//					if (true) 
//					{
//                		Bundle b1 = new Bundle();
//                		b1.putString(	"description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
//                						"MEAL_ACTIVITY_INJECT"+", "+
//                						"mealSize="+b.getDouble("mealSize")+", "+
//                						"smbg="+b.getDouble("smbg")+", "+
//                						"corr="+b.getDouble("corr")+", "+
//                						"meal="+b.getDouble("meal")+", "+
//                						"credit="+b.getDouble("credit")+", "+
//                						"spend="+b.getDouble("spend")
//                					);
//                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
//					}
//					MCM.send(calcMessage);
//				} catch (RemoteException e) {
//					e.printStackTrace();
//				}				
//				setResult(RESULT_OK);
//				finish();
//     	 	}
//    	    //OPEN LOOP MEAL ACTIVITY CALCULATION MODE
//    	    //----------------------------------------------------------------------------------------------------------------------------------------------------------------
//     	 	else
//     	 	{
//	     	 	AlertDialog.Builder alert = new AlertDialog.Builder(this);
//		    	alert.setTitle("Confirm Injection");
//		    	alert.setMessage("Do you want to inject "+String.format("%.2f",allInsulin)+" U?");
//	
//		    	alert.setNegativeButton("Yes", new DialogInterface.OnClickListener() 
//		    	{
//					public void onClick(DialogInterface dialog, int whichButton) 
//					{
//						// Added on 07-25-14
//						if (corrBolus > CORRECTION_MAX_CONSTRAINT)
//				 		{
//							corrBolus = CORRECTION_MAX_CONSTRAINT;
//				 			
//				 			Bundle b1 = new Bundle();
//			        		b1.putString(	"description", "Meal bolus is being capped at the maximum "+corrBolus+" U!");
//			        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_POPUP_AUDIBLE);
//				 		}
//						if (mealBolus > MEAL_MAX_CONSTRAINT)
//				 		{
//							mealBolus = MEAL_MAX_CONSTRAINT;
//				 			
//				 			Bundle b1 = new Bundle();
//			        		b1.putString(	"description", "Meal bolus is being capped at the maximum "+mealBolus+" U!");
//			        		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_POPUP_AUDIBLE);
//				 		}
//						// End of Added on 07-25-14
//						
//						Message calcMessage = Message.obtain(null, Meal.MEAL_ACTIVITY_INJECT);
//			 			Bundle b = new Bundle();
//			 			b.putDouble("mealSize", carbs);
//			 			b.putDouble("smbg", bg2);
//			 			b.putDouble("corr", corrBolus);
//			 			b.putDouble("meal", mealBolus);
//			 			b.putDouble("credit", 0.0);
//			 			b.putDouble("spend", 0.0);
//			 			calcMessage.setData(b);
//			 			
//			 			try {
//							if (true) {
//		                		Bundle b1 = new Bundle();
//		                		b1.putString(	"description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
//		                						"MEAL_ACTIVITY_INJECT"+", "+
//		                						"mealSize="+b.getDouble("mealSize")+", "+
//		                						"smbg="+b.getDouble("smbg")+", "+
//		                						"corr="+b.getDouble("corr")+", "+
//		                						"meal="+b.getDouble("meal")+", "+
//		                						"credit="+b.getDouble("credit")+", "+
//		                						"spend="+b.getDouble("spend")
//		                					);
//		                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
//							}
//							MCM.send(calcMessage);
//						} catch (RemoteException e) {
//							e.printStackTrace();
//						}
//						setResult(RESULT_OK);
//						finish();
//					}
//				});
//		    	
//				alert.setPositiveButton("Cancel", new DialogInterface.OnClickListener() {
//					public void onClick(DialogInterface dialog, int whichButton) {
//					}
//				});
//		    	
//		    	alert.show();
//     	 	}
//	 	}
//	}
//	
	
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
//           			mealScreenOpenLoop();
           			break;
	           	case Meal.MEAL_ACTIVITY_CALCULATES_BOLUS_PUMP_MODE:
                    switch(DIAS_STATE) 
                    {
	       				case State.DIAS_STATE_OPEN_LOOP:
//	       					mealScreenOpenLoop();
	       					break;
	       				case State.DIAS_STATE_SAFETY_ONLY:
	       				case State.DIAS_STATE_CLOSED_LOOP:
//	       					mealScreenClosedLoop();
	       					break;
                    }
	           		break;
	           	case Meal.MEAL_ACTIVITY_NEVER_CALCULATES_BOLUS:
//	   				mealScreenClosedLoop();
	           		break;
	           	default:
//	   				mealScreenOpenLoop();
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

	private void debug_message(String tag, String message) {
		if (DEBUG) {
			Log.i(tag, message);
		}
	}

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
	
		    
}