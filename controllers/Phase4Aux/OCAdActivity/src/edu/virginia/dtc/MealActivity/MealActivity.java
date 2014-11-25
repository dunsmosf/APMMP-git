package edu.virginia.dtc.MealActivity;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Meal;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.Tvector.Pair;
import edu.virginia.dtc.Tvector.PairComparator;
import edu.virginia.dtc.Tvector.Tvector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnFocusChangeListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

public class MealActivity extends FragmentActivity {
	
	public static final String TAG = "MealActivity";
	public static final boolean DEBUG = true;
	private boolean ENABLE_IO;
	private boolean enableIOtest = false;
	public static final String IO_TEST_TAG = "MealActivityIO";

    private long simulatedTime = -1; // Used in development to receive simulated
										// time from Application (-1 means not valid)
    
	public static final int DIAS_SERVICE_COMMAND_APC_CALCULATE_BOLUS = 23;
	public static final int DIAS_SERVICE_COMMAND_SEND_MEAL_BOLUS = 10;
	public static final int DIAS_SERVICE_COMMAND_SEND_CORRECTION_BOLUS = 1;

	public static final int DIASMAIN_UI_APC_RETURN_BOLUS = 13;

	private double user_weight_to_carb_ratio_large_meal;		// Weight (kg) x ratio (g/kg) = CHO (g)
	private double user_weight_to_carb_ratio_medium_meal;
	private double user_weight_to_carb_ratio_small_meal;
	private String Large_meal_ratio_key = "edu.virginia.dtc.MealService.User_weight_to_carb_ratio_large_meal";
	private String Medium_meal_ratio_key = "edu.virginia.dtc.MealService.User_weight_to_carb_ratio_medium_meal";
	private String Small_meal_ratio_key = "edu.virginia.dtc.MealService.User_weight_to_carb_ratio_small_meal";
	private final static int MEAL_SIZE_SMALL = 0;
	private final static int MEAL_SIZE_MEDIUM = 1;
	private final static int MEAL_SIZE_LARGE = 2;
	private final static int MEAL_SPEED_SLOW = 0;
	private final static int MEAL_SPEED_MEDIUM = 1;
	private final static int MEAL_SPEED_FAST = 2;
	private int USER_INPUTTED_MEAL_TYPE;
	private double USER_INPUTTED_MEAL_CARBS;
	private int USER_INPUTTED_MEAL_SIZE;
	private int USER_INPUTTED_MEAL_SMBG;
	private double USER_APPROVED_CLOSED_LOOP_BOLUS;
	private double USER_APPROVED_CLOSED_LOOP_BOLUS2;
	private double MEAL_BOLUS;

	public static final int APC_TYPE_HMS = 1;
	public static final int APC_TYPE_RCM = 2;
	public static final int APC_TYPE_AMYLIN = 3;

	// Define AP Controller behavior
	private int APC_MEAL_CONTROL;
	public static final int APC_NO_MEAL_CONTROL = 1;
	public static final int APC_WITH_MEAL_CONTROL = 2;

	private int DIAS_STATE, APC_TYPE;
	private double IOB;
	private boolean awaitingRcmResponse = false;

	// Values that are needed for the Meal Screen
 	private static final double MealScreenTargetBG = 110.0;    // Meal screen correction BG target
	private boolean MealScreenFetchingAPCBolusRecommendation = false;
	private double MealScreenCHO;
	private double MealScreenCHOInsulin;
	private double MealScreenCorrection;
	private double MealScreenBG;
	private double MealScreenTotalBolus;
	private boolean MealScreenCHOValid = false;
	private boolean MealScreenCorrectionValid = false;
	private boolean MealScreenTotalBolusValid = false;
	private boolean MealScreenBGValid = false;
	private boolean IOBvalid = true;
	private boolean RedLightOn = false;
	private boolean MealScreenUseIOBInsulin = false;
	private double latestIOB;
	private double latestCR, latestCF;
	private double MealScreenCorrectionBolus;
	private double MealScreenMealBolus;

	//Extended Bolus Parameters
	private double MealScreenSMBGbolus = 0.0;
	private boolean extendedBolusDurationSet = true;
	private long extendedBolusDurationSeconds = 0;
	private long extendedBolusDurationMins = 0;
	
	//UI Objects
	private EditText carbs;
	private EditText carbsTotal;
	private EditText bg;
	private EditText bgTotal;
	private EditText iobTotal;
	private EditText corrTotal;
	private EditText allTotal;
	private CheckBox iob;
	private Button injectMeal;
	private Dialog bolusConfirmationDialog;
	private Dialog bolusConfirmationDialog2;
	Context current_context;
	
	private int USER_WEIGHT;
		
	public Tvector Tvec_calibration;
	
	public Long currentTimeseconds,timesinceLastCalib;
	public double advised_bolus;
	//public double suggested_advice;
	//public double accepted_advice;
	public long TlastEventSec = 0;
	public long Tlast_sec = 0;
	public long Tbetminimum_sec = 60*60; 
	public long Tlast_hypo_sec;
	public long Test_time;
	
	private ServiceConnection MCMservice;
    final Messenger MCMmessenger = new Messenger(new IncomingMCMhandler());
    Messenger MCM = null;	
    boolean MCMbound;
    
    private static final int MCM_OCAD_MEAL_ACTIVITY_CALCULATE = 3000;
	private static final int ADVISED_BOLUS = 3001;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		enableIOtest = getIntent().getBooleanExtra("enableIOtest", false);
		Tvec_calibration = new Tvector();
					
		current_context = this;
		
		getSystem();
		if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP) {
			setContentView(R.layout.eat_or_not);
		}
		else if (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP) {
			setContentView(R.layout.defaultmealscreen);
			initMealScreen();
		}
		else {
			finish();
		}
		
		USER_INPUTTED_MEAL_TYPE = 0;
		USER_INPUTTED_MEAL_SIZE = 0;
		USER_APPROVED_CLOSED_LOOP_BOLUS = 0.0;
		extendedBolusDurationSet = true;

		// Fetch the user weight to meal carb ratios
		SharedPreferences prefs = this.getSharedPreferences("edu.virginia.dtc.MealService", Context.MODE_PRIVATE);
		user_weight_to_carb_ratio_large_meal = prefs.getFloat(Large_meal_ratio_key, (float)1.0);
		user_weight_to_carb_ratio_medium_meal = prefs.getFloat(Medium_meal_ratio_key, (float)0.6);
		user_weight_to_carb_ratio_small_meal = prefs.getFloat(Small_meal_ratio_key, (float)0.4);

		// Gather DiAs state and the APC type		
		APC_TYPE = getIntent().getIntExtra("apc", 0);
		APC_MEAL_CONTROL = getIntent().getIntExtra("apc_meal_control", APC_NO_MEAL_CONTROL);
		enableIOtest = getIntent().getBooleanExtra("enableIOtest", true);
		simulatedTime = getIntent().getLongExtra("simulatedTime", 0);
		
		debug_message(TAG, "onCreate > APC_TYPE=" + APC_TYPE);

		currentTimeseconds = getCurrentTimeSeconds();
		
		// calibration info		
		getSubjectWeight();
		getCalibration();
		
		if (Tvec_calibration.count()!=0){
			timesinceLastCalib=currentTimeseconds/60-Tvec_calibration.get_last_time();
		}
		else{
			timesinceLastCalib=(long) -99;
		}

		debug_message(TAG, "Ustar calculation start");
		
		startMCM();
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	//**********************************************************
	// Meal Button Closed Loop related actions
	//**********************************************************	
	
	// if replied 'NO' at eat_or_not	
	public void adviceonly(View view) {
		bolusConfirmationDialog2 = new Dialog(current_context); 
	    bolusConfirmationDialog2.getWindow().requestFeature(Window.FEATURE_NO_TITLE); 
	    bolusConfirmationDialog2.setContentView(getLayoutInflater().inflate(R.layout.corr_bolus_dialog_layout , null));
	    EditText bolusTotalText = (EditText)bolusConfirmationDialog2.findViewById(R.id.bolusTotal2);
	    bolusTotalText.setText(String.format("%.2f U", (double)USER_APPROVED_CLOSED_LOOP_BOLUS2));
	    bolusConfirmationDialog2.show();
	}
	
	// if replied 'YES' at eat_or_not	
	public void gotomealscreen(View view) {
			setContentView(R.layout.main);
		}

	// called when one of the 9 buttons of the meal screen is pressed
	public void injectBolusClick(View view) {
			switch (view.getId()) {
			case R.id.mostSlowButton:
				USER_INPUTTED_MEAL_SIZE = 2;
				USER_INPUTTED_MEAL_CARBS = USER_WEIGHT*user_weight_to_carb_ratio_large_meal;
				USER_INPUTTED_MEAL_TYPE = 0;
				Tbetminimum_sec = (long) 90*60;
				break;
			case R.id.mostRegButton:
				USER_INPUTTED_MEAL_SIZE = 2;
				USER_INPUTTED_MEAL_CARBS = USER_WEIGHT*user_weight_to_carb_ratio_large_meal;
				USER_INPUTTED_MEAL_TYPE = 1;
				Tbetminimum_sec = (long) 60*60;
				break;
			case R.id.mostFastButton:
				USER_INPUTTED_MEAL_SIZE = 2;
				USER_INPUTTED_MEAL_CARBS = USER_WEIGHT*user_weight_to_carb_ratio_large_meal;
				USER_INPUTTED_MEAL_TYPE = 2;
				Tbetminimum_sec = (long) 45*60;
				break;
			case R.id.moreSlowButton:
				USER_INPUTTED_MEAL_SIZE = 1;
				USER_INPUTTED_MEAL_CARBS = (USER_WEIGHT*user_weight_to_carb_ratio_medium_meal);
				USER_INPUTTED_MEAL_TYPE = 0;
				Tbetminimum_sec = (long) 90*60;
				break;
			case R.id.moreRegButton:
				debug_message(TAG, "button pressed" + USER_WEIGHT);
				USER_INPUTTED_MEAL_SIZE = 1;
				USER_INPUTTED_MEAL_CARBS = USER_WEIGHT*user_weight_to_carb_ratio_medium_meal;
				USER_INPUTTED_MEAL_TYPE = 1;
				Tbetminimum_sec = (long) 60*60;
				break;
			case R.id.moreFastButton:
				USER_INPUTTED_MEAL_SIZE = 1;
				USER_INPUTTED_MEAL_CARBS = USER_WEIGHT*user_weight_to_carb_ratio_medium_meal;
				USER_INPUTTED_MEAL_TYPE = 2;
				Tbetminimum_sec = (long) 45*60;
				break;
			case R.id.lowSlowButton:
				USER_INPUTTED_MEAL_SIZE = 0;
				USER_INPUTTED_MEAL_CARBS = USER_WEIGHT*user_weight_to_carb_ratio_small_meal;
				USER_INPUTTED_MEAL_TYPE = 0;
				Tbetminimum_sec = (long) 90*60;
				break;
			case R.id.lowRegButton:
				USER_INPUTTED_MEAL_SIZE = 0;
				USER_INPUTTED_MEAL_CARBS = USER_WEIGHT*user_weight_to_carb_ratio_small_meal;
				USER_INPUTTED_MEAL_TYPE = 1;
				Tbetminimum_sec = (long) 60*60;
				break;
			case R.id.lowFastButton:
				USER_INPUTTED_MEAL_SIZE = 0;
				USER_INPUTTED_MEAL_CARBS = USER_WEIGHT*user_weight_to_carb_ratio_small_meal;
				USER_INPUTTED_MEAL_TYPE = 2;
				Tbetminimum_sec = (long) 45*60;
				break;
			}
			USER_APPROVED_CLOSED_LOOP_BOLUS = 0.0;
			debug_message(TAG, "Opening SMBG Dialog");
			SMBGDialogFragment dialog = new SMBGDialogFragment();
			dialog.show(getSupportFragmentManager(), "SMBGDialogFragment");
			//Toast.makeText(getApplicationContext(),"End of Calculation", Toast.LENGTH_SHORT);
			// fetchAllBiometricData();
		}

	// SMBG check after it's been entered after the meal choice
	public void onUserSelectValue(int value) {
			// TODO Auto-generated method stub
			if (value >= 39 && value <= 400) {
				Toast.makeText(getApplicationContext(),"smbg="+value+", carbs="+USER_INPUTTED_MEAL_CARBS+", type="+USER_INPUTTED_MEAL_TYPE, Toast.LENGTH_SHORT).show();
				USER_INPUTTED_MEAL_SMBG = value;
				ProcessMeal(USER_INPUTTED_MEAL_SMBG, USER_INPUTTED_MEAL_CARBS, USER_INPUTTED_MEAL_TYPE);
			}
			else {
				SMBGDialogFragment dialog = new SMBGDialogFragment();
				dialog.show(getSupportFragmentManager(), "SMBGDialogFragment");
				Toast.makeText(getApplicationContext(), "The SMBG value must be between 39 and 400.", Toast.LENGTH_LONG).show();
			}
		}
	
	// called after the SMBG has been entered
	public void ProcessMeal(double smbg, double carbs, int type) {
		switch (DIAS_STATE) {
		case State.DIAS_STATE_OPEN_LOOP:
		case State.DIAS_STATE_CLOSED_LOOP:
			if (carbs != 0 && smbg != 0) {
				// 1. Update latestCR
				subject_parameters();
				// 2. Calculate meal bolus amount MealBolusA
				double MealASat;
				if (carbs >= 100.0)
					MealASat = 100.0;
				else if (carbs > 0.0)
					MealASat = carbs;
				else
					MealASat = 0.0;
				USER_APPROVED_CLOSED_LOOP_BOLUS = MealASat/latestCR + advised_bolus;
				MEAL_BOLUS = MealASat/latestCR;
				// Apply limits to MealBolusA
				if (USER_APPROVED_CLOSED_LOOP_BOLUS > 30.0) {
					USER_APPROVED_CLOSED_LOOP_BOLUS = 30.0;
				}
				else if (USER_APPROVED_CLOSED_LOOP_BOLUS < 0.0) {
					USER_APPROVED_CLOSED_LOOP_BOLUS = 0.0;
				}
				// 3. Display Bolus Dialog
				bolusConfirmationDialog = new Dialog(current_context); 
				bolusConfirmationDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE); 
				bolusConfirmationDialog.setContentView(getLayoutInflater().inflate(R.layout.meal_bolus_dialog_layout , null));
				EditText bolusTotalText = (EditText)bolusConfirmationDialog.findViewById(R.id.bolusTotal);
				bolusTotalText.setText(String.format("%.2f U", (double)USER_APPROVED_CLOSED_LOOP_BOLUS));
				bolusConfirmationDialog.show();
			}
			else {
				
			}
			/*
			if (!awaitingRcmResponse) {
				// Request bolus computation from RCM (NOTE: carbs and smbg
				// need to be input)
				if (carbs != 0 && smbg != 0) {
					Intent rcmCalc = new Intent();
					rcmCalc.setClassName("edu.virginia.dtc.DiAsService",
							"edu.virginia.dtc.DiAsService.DiAsService");
					rcmCalc.putExtra("DiAsCommand",
							DIAS_SERVICE_COMMAND_APC_CALCULATE_BOLUS);
					rcmCalc.putExtra("MealScreenCHO", carbs);
					rcmCalc.putExtra("MealScreenBG", smbg);
					rcmCalc.putExtra("simulatedTime",
							getCurrentTimeSeconds());
					rcmCalc.putExtra("type", type);
					awaitingRcmResponse = true;
					// Log the constraints for IO testing
					// Log the constraints for IO testing
					if (enableIOtest) {
						log_IO(IO_TEST_TAG,
								">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
						log_IO(IO_TEST_TAG,
								"> DIAS_SERVICE_COMMAND_APC_CALCULATE_BOLUS");
						log_IO(IO_TEST_TAG, "> MealScreenCHO=" + carbs);
						log_IO(IO_TEST_TAG, "> MealScreenBG=" + smbg);
						log_IO(IO_TEST_TAG, "> MealScreenType=" + type);
					}
					startService(rcmCalc);
				} else
					log(Log.ERROR,
							"Meals or SMBG value is zero, please correct this!");
			}
			*/
			break;
		default:
			Toast.makeText(getApplicationContext(),	"This state doesn't support meals!", Toast.LENGTH_SHORT).show();
			finish();
			break;
		}
	}
	
	// correction bolus with meal bolus confirmation dialog	
	public void injectBolusClosedLoop(View view) {
		FinalizeMeal(USER_INPUTTED_MEAL_SMBG,
					USER_APPROVED_CLOSED_LOOP_BOLUS,
					0.0,
					USER_APPROVED_CLOSED_LOOP_BOLUS, 
					true, 
					USER_INPUTTED_MEAL_CARBS, 
					0,
					0.0);
	
	}
	public void cancelBolusClosedLoop(View view) {
	    USER_APPROVED_CLOSED_LOOP_BOLUS = 0.0;
	    finish();
	}
	public void increaseBolusClosedLoop(View view) {
	    EditText bolusTotalText = (EditText)bolusConfirmationDialog.findViewById(R.id.bolusTotal);
	    USER_APPROVED_CLOSED_LOOP_BOLUS = USER_APPROVED_CLOSED_LOOP_BOLUS + 0.10;
	    if (USER_APPROVED_CLOSED_LOOP_BOLUS > 10.0) {
	        USER_APPROVED_CLOSED_LOOP_BOLUS = 10.0;
	    }
	    bolusTotalText.setText(String.format("%.2f U", (double)USER_APPROVED_CLOSED_LOOP_BOLUS));
	    bolusConfirmationDialog.show();
	}
	public void decreaseBolusClosedLoop(View view) {
	        EditText bolusTotalText = (EditText)bolusConfirmationDialog.findViewById(R.id.bolusTotal);
	        USER_APPROVED_CLOSED_LOOP_BOLUS = USER_APPROVED_CLOSED_LOOP_BOLUS - 0.10;
	        if (USER_APPROVED_CLOSED_LOOP_BOLUS < 0.0) {
	            USER_APPROVED_CLOSED_LOOP_BOLUS = 0.0;
	        }
	        bolusTotalText.setText(String.format("%.2f U", (double)USER_APPROVED_CLOSED_LOOP_BOLUS));
	        bolusConfirmationDialog.show();
	    }
	    
	// correction bolus only confirmation dialog    
	public void injectBolusClosedLoop2(View view) {
			// Log Advice Event
	    	Bundle bun = new Bundle();
			bun.putString("description", "Advice requested and injected");
			Event.addEvent(this, Event.EVENT_MEAL_ERROR, Event.makeJsonString(bun), Event.SET_POPUP);
			FinalizeMeal(-1,
		    			0.0, 
		    			USER_APPROVED_CLOSED_LOOP_BOLUS2, 
		    			USER_APPROVED_CLOSED_LOOP_BOLUS2,
		    			false, 
		    			0, 
		    			0, 
		    			0.0);
	        Tbetminimum_sec = (long) 60*60;
	
	    }
	public void cancelBolusClosedLoop2(View view) {
	    USER_APPROVED_CLOSED_LOOP_BOLUS2 = 0.0;
	 // Log Advice Event
    	Bundle bun = new Bundle();
		bun.putString("description", "Advice requested but canceled");
		Log.i(TAG, "preevent");
		Event.addEvent(this, Event.EVENT_MEAL_ERROR, Event.makeJsonString(bun), Event.SET_POPUP);
		Log.i(TAG, "postevent");
	    finish();
	}
	public void increaseBolusClosedLoop2(View view) {
	    EditText bolusTotalText = (EditText)bolusConfirmationDialog2.findViewById(R.id.bolusTotal2);
	    USER_APPROVED_CLOSED_LOOP_BOLUS2 = USER_APPROVED_CLOSED_LOOP_BOLUS2 + 0.10;
	    if (USER_APPROVED_CLOSED_LOOP_BOLUS2 > 10.0) {
	        USER_APPROVED_CLOSED_LOOP_BOLUS2 = 10.0;
	    }
	    bolusTotalText.setText(String.format("%.2f U", (double)USER_APPROVED_CLOSED_LOOP_BOLUS2));
	    bolusConfirmationDialog2.show();
	}
	public void decreaseBolusClosedLoop2(View view) {
		    EditText bolusTotalText = (EditText)bolusConfirmationDialog2.findViewById(R.id.bolusTotal2);
		    USER_APPROVED_CLOSED_LOOP_BOLUS2 = USER_APPROVED_CLOSED_LOOP_BOLUS2 - 0.10;
		    if (USER_APPROVED_CLOSED_LOOP_BOLUS2 < 0.0) {
		        USER_APPROVED_CLOSED_LOOP_BOLUS2 = 0.0;
		    }
		    bolusTotalText.setText(String.format("%.2f U", (double)USER_APPROVED_CLOSED_LOOP_BOLUS2));
		    bolusConfirmationDialog2.show();
		}
	
	// called when insulin is ready to be sent out to the rest of the system
	public void FinalizeMeal(double smbg, 
							double mealBolus,
							double corrBolus,
							double totalBolus,
							boolean carbsValid, 
							double carbs, 
							long extendedBolusDurationSeconds, 
							double MealScreenSMBGbolus) {
		Intent intent1 = new Intent();
		switch (DIAS_STATE) {
		case State.DIAS_STATE_CLOSED_LOOP:
			
			// store the advice values and blackout periods
			storeUserTable4Data(getCurrentTimeSeconds(),
								Tbetminimum_sec,
								getCurrentTimeSeconds(),
								advised_bolus,
								corrBolus,
								smbg);
			
			// send data to MMCM for the injection
			Message calcMessage = Message.obtain(null, Meal.MEAL_ACTIVITY_INJECT);
 			Bundle b = new Bundle();
 			b.putDouble("mealSize", carbs);
 			b.putDouble("smbg", smbg);
 			b.putDouble("mealPart", mealBolus);
 			b.putDouble("corrPart", corrBolus);
 			calcMessage.setData(b);
 			
 			try {
				MCM.send(calcMessage);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			
			// Log the constraints for IO testing
			if (enableIOtest) {
				log_IO(IO_TEST_TAG,
						">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
				log_IO(IO_TEST_TAG, "> DIAS_SERVICE_COMMAND_SEND_MEAL_BOLUS");
				//log_IO(IO_TEST_TAG, "> MealScreenMealBolus=" + mealBolus);
				//log_IO(IO_TEST_TAG, "> MealScreenCorrectionBolus=" + corrBolus);
				log_IO(IO_TEST_TAG, "> MealScreenMealBolus=" + MEAL_BOLUS);
				log_IO(IO_TEST_TAG, "> MealScreenCorrectionBolus=" + advised_bolus);
				log_IO(IO_TEST_TAG, "> MealScreenTotalBolus=" + totalBolus);
				log_IO(IO_TEST_TAG, "> MealScreenCHOValid=" + carbsValid);
				log_IO(IO_TEST_TAG, "> MealScreenCHO=" + carbs);
				log_IO(IO_TEST_TAG, "> SMBG=" + smbg);
				log_IO(IO_TEST_TAG, "> type=" + USER_INPUTTED_MEAL_TYPE);
				log_IO(IO_TEST_TAG, "> size=" + USER_INPUTTED_MEAL_SIZE);
				log_IO(IO_TEST_TAG, "> MealScreenSMBGbolus=" + MealScreenSMBGbolus);
				log_IO(IO_TEST_TAG, "> storeUserTable1Data()");
				storeUserTable1Data(); // Attempt to write unprotected table -
										// verify success message from
										// biometricsContentProvider
				log_IO(IO_TEST_TAG, "> storeUserTable4Data()");
				//storeUserTable4Data(); // Attempt to write protected table -
										// verify error message from
										// biometricsContentProvider
			}
			startService(intent1);
			finish();
			break;
		case State.DIAS_STATE_OPEN_LOOP:
			intent1.setClassName("edu.virginia.dtc.DiAsService",
					"edu.virginia.dtc.DiAsService.DiAsService");
			intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_SEND_MEAL_BOLUS);
			intent1.putExtra("MealScreenCHOValid", carbsValid);
			intent1.putExtra("MealScreenMealBolus", mealBolus);
			intent1.putExtra("MealScreenCorrectionBolus", corrBolus);
			intent1.putExtra("SMBG", smbg);
			intent1.putExtra("MealScreenCHO", carbs);
			intent1.putExtra("type", 0);
			intent1.putExtra("size", 0);
			intent1.putExtra("MealScreenTotalBolus", totalBolus);
			intent1.putExtra("MealScreenSMBGbolus", MealScreenSMBGbolus);
			intent1.putExtra("extendedBolusDurationSeconds", extendedBolusDurationSeconds);
			// Log the constraints for IO testing
			if (enableIOtest) {
				log_IO(IO_TEST_TAG,
						">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
				log_IO(IO_TEST_TAG, "> DIAS_SERVICE_COMMAND_SEND_MEAL_BOLUS");
				log_IO(IO_TEST_TAG, "> MealScreenMealBolus=" + mealBolus);
				log_IO(IO_TEST_TAG, "> MealScreenCorrectionBolus=" + corrBolus);
				log_IO(IO_TEST_TAG, "> MealScreenTotalBolus=" + totalBolus);
				log_IO(IO_TEST_TAG, "> MealScreenCHOValid=" + carbsValid);
				log_IO(IO_TEST_TAG, "> MealScreenCHO=" + carbs);
				log_IO(IO_TEST_TAG, "> SMBG=" + smbg);
				log_IO(IO_TEST_TAG, "> type=" + 0);
				log_IO(IO_TEST_TAG, "> size=" + 0);
				log_IO(IO_TEST_TAG, "> MealScreenSMBGbolus=" + MealScreenSMBGbolus);
				log_IO(IO_TEST_TAG, "> storeUserTable1Data()");
				storeUserTable1Data(); // Attempt to write unprotected table -
										// verify success message from
										// biometricsContentProvider
				log_IO(IO_TEST_TAG, "> storeUserTable4Data()");
				//storeUserTable4Data(); // Attempt to write protected table -
										// verify error message from
										// biometricsContentProvider
			}
//			Toast.makeText(getApplicationContext(),
//					"Meal bolus: " + totalBolus, Toast.LENGTH_SHORT).show();
			startService(intent1);
			finish();
			break;
		default:
			Toast.makeText(getApplicationContext(),
					"This state doesn't support meals!", Toast.LENGTH_SHORT)
					.show();
			break;
		}
	}

	//**********************************************************
	// Meal Button Open Loop related actions
	//**********************************************************
	
	// Open Loop Meal Screen and process entries/edits
	private void initMealScreen()
	{
		WindowManager.LayoutParams params = getWindow().getAttributes();
		params.height = getIntent().getIntExtra("height", 100);
		params.width = getIntent().getIntExtra("width", 100);
		
		Log.i(TAG, "HEIGHT: "+getIntent().getIntExtra("height", 100)+" WIDTH: "+getIntent().getIntExtra("width", 100));
		
		ViewGroup.LayoutParams lParams = this.findViewById(R.id.defaultMealLayout).getLayoutParams();
		
		lParams.height = params.height;
		lParams.height -= (0.07*lParams.height);
		
		lParams.width = params.width;
		lParams.width -= (0.07*lParams.width);
		
		(this.findViewById(R.id.defaultMealLayout)).setLayoutParams(lParams);
		
		this.getWindow().setAttributes(params);
		
		MealScreenCHOValid = false;
	 	MealScreenCorrectionValid = false;
	 	MealScreenTotalBolusValid = false;
	 	MealScreenBGValid = false;
	 	MealScreenUseIOBInsulin = true;			// Box is always checked on entry
		
	 	injectMeal = (Button)this.findViewById(R.id.injectMealBolusButton);
	 	
		carbs = (EditText)this.findViewById(R.id.editMealCarbs);
		bg = (EditText)this.findViewById(R.id.editBg);
		
		carbsTotal = (EditText)this.findViewById(R.id.editMealCarbsTotal);
		bgTotal = (EditText)this.findViewById(R.id.editBgTotal);
		iobTotal = (EditText)this.findViewById(R.id.editIobTotal);
		corrTotal = (EditText)this.findViewById(R.id.editCorrTotal);
		allTotal = (EditText)this.findViewById(R.id.editAllTotal);
		
		iob = (CheckBox)this.findViewById(R.id.iobCheckbox);
		latestIOB = fetchLatestIOBValue(getCurrentTimeSeconds());
		IOBvalid = true;
 		DecimalFormat iobFormat = new DecimalFormat();
 		iobFormat.setMaximumFractionDigits(2);
 		iobFormat.setMinimumFractionDigits(2);
		if (latestIOB > 0) {
			iobTotal.setText(iobFormat.format(latestIOB));
		}
		else {
			iobTotal.setText("");
		}
		
		injectMeal = (Button)this.findViewById(R.id.injectMealBolusButton);
		
 		subject_parameters();		// Get latestCR and latestCF
	 	debug_message(TAG, "mealClick > latestCR="+latestCR);
	 
		// If a selection has already been made then initialize the spinner accordingly.
		
		extendedBolusDurationMins = 0;
						
	 	// Carbohydrate Meal Input
	 	carbs.setOnEditorActionListener(new OnEditorActionListener() 
	 	{
	 		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
	 			MealScreenCHOValid=false;
    	 		MealScreenTotalBolusValid = false;
	 			allTotal.setText("");    	 			
	 			if (v.getText().toString().length() > 0) {
	 				MealScreenCHO = Double.parseDouble(v.getText().toString());
	 				debug_message(TAG, "MealScreenCHO="+MealScreenCHO);
	 				if (MealScreenCHO>=0.0 && MealScreenCHO<=200.0) {
	 					debug_message(TAG, "MealScreenCHO="+MealScreenCHO);
	 					MealScreenCHOValid=true;
	 					v.setTextColor(Color.BLACK);
	 					MealScreenCHOInsulin = MealScreenCHO/latestCR;
	 					if (	APC_TYPE == APC_TYPE_HMS 
	 						|| (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP)
	 						|| (APC_TYPE == APC_TYPE_RCM && DIAS_STATE != State.DIAS_STATE_CLOSED_LOOP)
	 						|| (APC_TYPE != APC_TYPE_HMS && APC_TYPE != APC_TYPE_RCM && APC_MEAL_CONTROL == APC_NO_MEAL_CONTROL)) 
	 					{
    	 			 		carbsTotal.setText(String.format("%.2f", (double)((int)(MealScreenCHOInsulin*100))/100));
	 					}
	 					else {
    	 			 		carbsTotal.setText("");
	 					}
	 					
	 					calculateTotalMealBolus();
	 					return false;
	 				}    					
    	 			v.setTextColor(Color.RED);
	 			}
	 			return true;
	 		}
	 	});
	 	
	 	carbs.setOnFocusChangeListener(new OnFocusChangeListener()
	 	{
            public void onFocusChange(View v, boolean hasFocus)
            {
        		if (!hasFocus) 
        		{
        	 		allTotal.setText("");    	 			
	 				MealScreenCHOValid=false;
	 	    	 	MealScreenTotalBolusValid = false;
    	 			if (((TextView)v).getText().toString().length() > 0) 
    	 			{
    	 				MealScreenCHO = Double.parseDouble(((TextView)v).getText().toString());
    	 				debug_message(TAG, "MealScreenCHO="+MealScreenCHO);
    	 				if (MealScreenCHO>=0.0 && MealScreenCHO<=200.0) 
    	 				{
    	 					debug_message(TAG, "MealScreenCHO="+MealScreenCHO);
    	 					MealScreenCHOValid=true;
    	 					((TextView)v).setTextColor(Color.BLACK);
    	 					MealScreenCHOInsulin = MealScreenCHO/latestCR;

    	 					if (	APC_TYPE == APC_TYPE_HMS 
		 						|| (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP)
		 						|| (APC_TYPE == APC_TYPE_RCM && DIAS_STATE != State.DIAS_STATE_CLOSED_LOOP)
		 						|| (APC_TYPE != APC_TYPE_HMS && APC_TYPE != APC_TYPE_RCM && APC_MEAL_CONTROL == APC_NO_MEAL_CONTROL)) 
    	 					{
    	 						carbsTotal.setText(String.format("%.2f", (double)((int)(MealScreenCHOInsulin*100))/100));
    	 					}
    	 					else {
        	 			 		carbsTotal.setText("");
    	 					}
    	 					
    	 					calculateTotalMealBolus();
    	 				}
    	 				else {
    	 					((TextView)v).setTextColor(Color.RED);
    	 				}
    	 			}
        		}
            }
	 	});
	 	
	 	//Set carb entry and totals to zero
	 	carbs.setText("");
	 	carbsTotal.setText("");
    	
	 	//SMBG Meal Input
	 	bg.setOnEditorActionListener(new OnEditorActionListener() 
	 	{
	 		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) 
	 		{
	 			MealScreenBGValid=false;
	 			MealScreenTotalBolusValid = false;
    	 		allTotal.setText("");    	 			
	 			if (v.getText().toString().length() > 0) 
	 			{
	 				MealScreenBG = Double.parseDouble(v.getText().toString());
	 				debug_message(TAG, "MealScreenBG="+MealScreenBG);
	 				if (MealScreenBG>=39.0 && MealScreenBG<=401.0) 
	 				{
	 					MealScreenBGValid=true;
	 					v.setTextColor(Color.BLACK);
	 					calculateTotalMealBolus();
	 					return false;
	 				}
    	 			v.setTextColor(Color.RED);
	 			}
	 			return true;
	 		}
	 	});
	 	
 		bg.setOnFocusChangeListener(new OnFocusChangeListener()
 		{
 			public void onFocusChange(View v, boolean hasFocus){
        		if (!hasFocus) 
        		{
	 				MealScreenBGValid=false;
    	 			MealScreenTotalBolusValid = false;
        	 		allTotal.setText("");    	 			
    	 			if (((TextView)v).getText().toString().length() > 0) 
    	 			{
    	 				MealScreenBG = Double.parseDouble(((TextView)v).getText().toString());
    	 				debug_message(TAG, "MealScreenBG="+MealScreenBG);
    	 				if (MealScreenBG>=39.0 && MealScreenBG<=401.0) 
    	 				{
    	 					MealScreenBGValid=true;
    	 					((TextView)v).setTextColor(Color.BLACK);
    	 					calculateTotalMealBolus();
    	 				}
    	 				else 
    	 				{
    	 					((TextView)v).setTextColor(Color.RED);
    	 				}
    	 			}
        		}
            }
        });
 		
 		//Set SMBG entry and totals to zero
	 	bg.setText("");
	 	bgTotal.setText("");
	
		corrTotal.setText("0.0");
		corrTotal.setTextColor(Color.BLACK);
	 	
	 	MealScreenCorrection = 0.0;
	 	MealScreenCorrectionValid = true;
	 	
	 	corrTotal.setOnEditorActionListener(new OnEditorActionListener() 
	 	{
	 		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) 
	 		{
	 			if (v.getText().toString().length() > 0) 
	 			{
	 				MealScreenCorrection = Double.parseDouble(v.getText().toString());
	 				debug_message(TAG, "MealScreenCorrection="+MealScreenCorrection);
	 				MealScreenCorrectionValid=false;
	 				if (MealScreenCorrection>=-20 && MealScreenCorrection<=20) 
	 				{
	 					debug_message(TAG, "MealScreenCorrection="+MealScreenCorrection);
	 					MealScreenCorrectionValid=true;
	 					v.setTextColor(Color.BLACK);
	 					calculateTotalMealBolus();
	 					return false;
	 				}    					
    	 			v.setTextColor(Color.RED);
	 			}
	 			return true;
	 		}
	 	});
	 	
	 	corrTotal.setOnFocusChangeListener(new OnFocusChangeListener()
	 	{
            public void onFocusChange(View v, boolean hasFocus)
            {
        		if (!hasFocus) 
        		{
    	 			if (((TextView)v).getText().toString().length() > 0) 
    	 			{
    	 				MealScreenCorrection = Double.parseDouble(((TextView)v).getText().toString());
    	 				debug_message(TAG, "MealScreenCorrection="+MealScreenCorrection);
    	 				MealScreenCorrectionValid=false;
    	 				if (MealScreenCorrection>=-20 && MealScreenCorrection<=20) 
    	 				{
    	 					debug_message(TAG, "MealScreenCorrection="+MealScreenCorrection);
    	 					MealScreenCorrectionValid=true;
    	 					((TextView)v).setTextColor(Color.BLACK);
    	 					calculateTotalMealBolus();
    	 				}
    	 				else 
    	 					((TextView)v).setTextColor(Color.RED);
    	 			}
        		}
            }
        });
	 	corrTotal.setText("0.0");
	}

	// called when 'Include IOB' box is checked
	public void checkboxIOBClick(View view) {
 		if (iob.isChecked()) 
 		{
 			DecimalFormat iobFormat = new DecimalFormat();
 			iobFormat.setMaximumFractionDigits(2);
 			iobFormat.setMinimumFractionDigits(2);
 			
 			if (latestIOB > 0)
 				iobTotal.setText(iobFormat.format(latestIOB));
 			else
 		 		iobTotal.setText("");
 			
	 		MealScreenUseIOBInsulin = true;
	 	}
	 	else 
	 	{
	 		iobTotal.setText("");
	 		MealScreenUseIOBInsulin = false;
	 	}
 		calculateTotalMealBolus();
	}
		
	// called every time new BG or CHO entered or IOB box checked on Default (Open Loop) Meal Screen
	
	public void calculateTotalMealBolus()
	{
		final String FUNC_TAG = "calculateTotalMealBolus";
		
	 	Debug.i(TAG, FUNC_TAG, "calculateTotalMealBolus");
	 	
	 	MealScreenMealBolus = 0.0;				// In Open Loop mode this is the portion of the bolus that is considered meal insulin
	 	MealScreenCorrectionBolus = 0.0;		// In Open Loop mode this is the portion of the bolus that is considered correction insulin
	 	
	 	double CHO_insulin;
	 	double IOB_insulin;
	 	double BG_insulin;
	 	double Correction_insulin;
	 	
	 	//Open loop, Safety mode or Closed Loop with MCM not calculating the bolus
		if (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY|| DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP) 
	 	{
	 		// HMS and RCM are both handled the same way in Open Loop mode
    	 	Debug.i(TAG, FUNC_TAG, "calculateTotalMealBolus 1");
    	 	
    	 	MealScreenSMBGbolus = 0.0;
    	 	
	 		MealScreenTotalBolusValid = false;
	 		DecimalFormat iobFormat = new DecimalFormat();
	 		iobFormat.setMaximumFractionDigits(2);
	 		iobFormat.setMinimumFractionDigits(2);
	 		if (MealScreenUseIOBInsulin) 
	 		{
	 			if (IOB > 0) 
	 			{
	 				iobTotal.setText(iobFormat.format(IOB));
	 				IOB_insulin = IOB;
	 			}
	 			else 
	 			{
	 				iobTotal.setText("");
	 				IOB_insulin = 0.0;
	 			}
	 			MealScreenTotalBolusValid = true;
	 		}
	 		else 
	 			IOB_insulin = 0;
	 		
	 		if (MealScreenCHOValid) 
	 		{
	 			CHO_insulin = MealScreenCHOInsulin;
	 			MealScreenTotalBolusValid = true;
	 		}
	 		else 
	 			CHO_insulin = 0;
	 		
	 		if (MealScreenCorrectionValid) 
	 		{
	 			Correction_insulin = MealScreenCorrection;
	 			MealScreenTotalBolusValid = true;
	 		}
	 		else 
	 			Correction_insulin = 0;
	 		
	 		if (MealScreenBGValid) 
	 		{
	 			subject_parameters();
	 			// Negative BG correction insulin is permitted
	 			BG_insulin = (MealScreenBG-MealScreenTargetBG)*(1/latestCF);
	 			bgTotal.setText(""+(((double)(int)(BG_insulin*100))/100));
	 		} 
	 		else 
	 			BG_insulin = 0;
	 		
	 		MealScreenSMBGbolus = BG_insulin;
	 		
	 		MealScreenTotalBolus = CHO_insulin + BG_insulin - IOB_insulin + Correction_insulin;
	 		
	 		//TODO: pull this value from pump details table
	 		double MealScreenTotalBolusMax = Math.min(20.0, 100.0/latestCR);
	 		Cursor c = getContentResolver().query(Biometrics.PUMP_DETAILS_URI, null, null, null, null);
				if (c.moveToLast())
				{
  				 MealScreenTotalBolusMax = c.getDouble(c.getColumnIndex("max_bolus_U"));
				}
				c.close();
				
				Debug.i(TAG, FUNC_TAG, "Maximum allowed bolus is "+MealScreenTotalBolusMax+"U via Pump Details Table!");
	 		
	 		if (MealScreenTotalBolus < 0) 
	 			MealScreenTotalBolus = 0;
	 		
	 		if (MealScreenTotalBolus > MealScreenTotalBolusMax) 
	 			MealScreenTotalBolus = MealScreenTotalBolusMax;
	 		
	 		if (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP || DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) 
	 		{
	 			// In Open Loop and Closed Loop modes distribute insulin between Meal and Correction
	 			if (MealScreenTotalBolus <= 0.0) 
	 			{
	 				MealScreenMealBolus = 0.0;
	 				MealScreenCorrectionBolus = 0.0;
	 			}
	 			else 
	 			{
	 				// Start with meal insulin from carbohydrates
	 				MealScreenMealBolus = CHO_insulin;
	 				// Correction bolus is everything else
	 				MealScreenCorrectionBolus = BG_insulin - IOB_insulin + Correction_insulin;
	 				if (MealScreenCorrectionBolus < 0.0) 
	 				{
	 					if (MealScreenMealBolus + MealScreenCorrectionBolus > 0.0) 
	 					{
	 						MealScreenMealBolus = MealScreenMealBolus + MealScreenCorrectionBolus;
	 						MealScreenCorrectionBolus = 0.0;
	 					}
	 					else 
	 					{
	 						MealScreenMealBolus = 0.0;
	 						MealScreenCorrectionBolus = 0.0;
	 					}
	 				}
	 				// Make sure the limit (min(20,100/CHI)) is enforced
	 				if((MealScreenMealBolus + MealScreenCorrectionBolus) > MealScreenTotalBolus)
	 				{
	 					Debug.i(TAG, FUNC_TAG, "Enforcing min(20,100/CHI) limit, Original boluses:  MealBolus: "+MealScreenMealBolus+" CorrectionBolus: "+MealScreenCorrectionBolus);
	 					double diff = (MealScreenMealBolus + MealScreenCorrectionBolus)-MealScreenTotalBolus;
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
	 			}
	 		}
	 		
	 		//Write the value to the EditText for the Total Bolus field
	 		allTotal.setText(String.format("%.2f", (double)MealScreenTotalBolus));
	 	}
	 	else 
	 	{
	 		// Handle Closed Loop RCM here
	 		if (MealScreenCHOValid && MealScreenBGValid && !MealScreenFetchingAPCBolusRecommendation && !MealScreenTotalBolusValid) 
	 		{
	 			MealScreenFetchingAPCBolusRecommendation = true;
	 			
	 			Message calcMessage = Message.obtain(null, Meal.MEAL_ACTIVITY_CALCULATE);
	 			Bundle b = new Bundle();
	 			b.putDouble("MealScreenCHO", MealScreenCHO);
	 			b.putDouble("MealScreenBG", MealScreenBG);
	 			calcMessage.setData(b);
	 			
	 			try {
					if (enableIOtest) {
                		Bundle b1 = new Bundle();
                		b.putString(	"description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
                						"MEAL_ACTIVITY_CALCULATE"+", "+
                						"MealScreenCHO="+MealScreenCHO+", "+
                						"MealScreenBG="+MealScreenBG
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b1), Event.SET_LOG);
					}
					MCM.send(calcMessage);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
	 		}
	 		else 
	 		{
    	 		Debug.i(TAG, FUNC_TAG, "Skip MEAL_ACTIVITY_CALCULATE, MealScreenCHOValid="+MealScreenCHOValid+
    	 				", MealScreenBGValid="+MealScreenBGValid+", MealScreenFetchingAPCBolusRecommendation="+MealScreenFetchingAPCBolusRecommendation+
    	 				", MealScreenTotalBolusValid="+MealScreenTotalBolusValid);
	 		}
	 	}
	}
	
	// called when 'Inject' is pressed
	public void injectMealBolusClick(View view) {
		final String FUNC_TAG = "injectMealBolusClick";
		
	 	if (MealScreenTotalBolusValid && MealScreenTotalBolus >= 0.05) 
	 	{
     	 	Debug.i(TAG, FUNC_TAG, "Inject "+MealScreenTotalBolus+" units of insulin.");
     	 	log_action(TAG, "MEAL INJECT CLICK = "+MealScreenTotalBolus+"U");
     	 
			if (DIAS_STATE == State.DIAS_STATE_OPEN_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY|| DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP ) 	// Confirmation dialog in OL or SAFETY
     	 	{
	     	 	AlertDialog.Builder alert = new AlertDialog.Builder(this);
		    	alert.setTitle("Confirm Injection");
		    	alert.setMessage("Do you want to inject "+String.format("%.2f",MealScreenTotalBolus)+" U?");
	
		    	alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String SMBGString = bg.getText().toString();
						double SMBG = (SMBGString.equals("")) ? 0 : Double.parseDouble(SMBGString);

						// Closed Loop sends bolus using credit/spend
						double credit = 0.0;
						double spend = 0.0;
						if (DIAS_STATE == State.DIAS_STATE_CLOSED_LOOP || DIAS_STATE == State.DIAS_STATE_SAFETY_ONLY) {
							credit = MealScreenCorrectionBolus + MealScreenMealBolus;
							spend = credit;
							MealScreenCorrectionBolus = 0.0;
							MealScreenMealBolus = 0.0;
						}
						
						Message calcMessage = Message.obtain(null, Meal.MEAL_ACTIVITY_INJECT);
			 			Bundle b = new Bundle();
			 			b.putDouble("mealSize", MealScreenCHO);
			 			b.putDouble("smbg", SMBG);
			 			b.putDouble("corrPart", MealScreenCorrectionBolus);
			 			b.putDouble("mealPart", MealScreenMealBolus);
			 			b.putDouble("credit", credit);
			 			b.putDouble("spend", spend);
			 			calcMessage.setData(b);
			 			
			 			try {
							if (enableIOtest) {
		                		Bundle b1 = new Bundle();
		                		b.putString(	"description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
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
     	 	else
     	 	{
     	 		String SMBGString = bg.getText().toString();
				double SMBG = (SMBGString.equals("")) ? 0 : Double.parseDouble(SMBGString);
				
				Message calcMessage = Message.obtain(null, Meal.MEAL_ACTIVITY_INJECT);
	 			Bundle b = new Bundle();
	 			b.putDouble("mealSize", MealScreenCHO);
	 			b.putDouble("smbg", SMBG);
	 			b.putDouble("credit", MealScreenTotalBolus);
	 			b.putDouble("spend", MealScreenTotalBolus);
	 			calcMessage.setData(b);
	 			
	 			try {
					if (enableIOtest) {
                		Bundle b1 = new Bundle();
                		b.putString(	"description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
                						"MEAL_ACTIVITY_INJECT"+", "+
                						"mealSize="+b.getDouble("mealSize")+", "+
                						"smbg="+b.getDouble("smbg")+", "+
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
	 	}
	}
	
	
	//**********************************************************
	// Utility methods
	//**********************************************************	
	
	// stores last advice time and the corresponding to the event blackout period
	public void storeUserTable4Data(long time,
			                        long Tbetminimum_sec,
			                        long Tlast_sec,
			                        double suggested_advice,
			                        double accepted_advice,
			                        double flag) {
		ContentValues values = new ContentValues();
		values.put("time", time);
		values.put("l0", Tbetminimum_sec);
		values.put("l1", Tlast_sec);
		values.put("d0", suggested_advice);
		values.put("d1", accepted_advice);
		values.put("d2", flag);
		values.put("d3", 0.0);
		values.put("d4", 0.0);
		values.put("d5", 0.0);
		values.put("d6", 0.0);
		values.put("d7", 0.0);
		values.put("d8", 0.0);
		values.put("d9", 0.0);
		values.put("d10", 0.0);
		values.put("d11", 0.0);
		values.put("d12", 0.0);
		values.put("d13", 0.0);
		values.put("d14", 0.0);
		values.put("d15", 0.0);
		
		Uri uri;
		try {
			uri = getContentResolver().insert(Biometrics.USER_TABLE_4_URI, values);
			Log.i(TAG, "storeUserTable4Data > l0=" + Tbetminimum_sec + ", l1=" + Tlast_sec);
			if (enableIOtest) {
				log_IO(IO_TEST_TAG, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
				if (uri != null) {
					log_IO(IO_TEST_TAG, "> USER_TABLE_4 Write SUCCESS");
				} else {
					log_IO(IO_TEST_TAG,
							"> USER_TABLE_4 Write FAIL (write protection)");
				}
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}

	// gets current time
	public long getCurrentTimeSeconds() {
		if (simulatedTime > 0) {
			// debug_message(TAG,
			// "getCurrentTimeSeconds > returning simulatedTime="+simulatedTime);
			return simulatedTime; // simulatedTime passed to us by Application
									// for development mode
		} else {
			return (long) (System.currentTimeMillis() / 1000); // Seconds since
																// 1/1/1970
		}
	}
	
	// gets latest CR and CF
	public void subject_parameters() {
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
 	  		error_message(TAG, "subject_parameters > CR_PROFILE_URI > c.getCount() == 0");
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
 	  		error_message(TAG, "subject_parameters > CF_PROFILE_URI > c.getCount() == 0");
 	  		log_action(TAG, "Error: subject_parameters > CF_PROFILE_URI > c.getCount() == 0");
 	  	}
 	  	c.close();
		// Get the offset in minutes into the current day in the current time zone (based on cell phone time zone setting)
		long timeSeconds = getCurrentTimeSeconds();
		TimeZone tz = TimeZone.getDefault();
		int UTC_offset_secs = tz.getOffset(timeSeconds*1000)/1000;
		int timeTodayMins = (int)((timeSeconds+UTC_offset_secs)/60)%1440;
		debug_message(TAG, "subject_parameters > UTC_offset_secs="+UTC_offset_secs+", timeSeconds="+timeSeconds+", timeSeconds/60="+timeSeconds/60+", timeTodayMins="+timeTodayMins);
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
			error_message(TAG, "subject_parameters > Missing CR daily profile");
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
			error_message(TAG, "subject_parameters > Missing CF daily profile");
 	  		log_action(TAG, "Error: subject_parameters > Missing CF daily profile");
		}
		else {
			latestCF = CF.get_value(indices.get(indices.size()-1));		// Return the last CF in this range						
		}
		debug_message(TAG, "subject_parameters > latestCR="+latestCR+", latestCF="+latestCF);
	}
	
	// gets last calibration time
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
				}
			} while (c.moveToNext());
	    }			
		c.close();
	}

	// gets last advice time and last blackout period value
	public void get_advice_data_from_DB() {
	    Cursor c=getContentResolver().query(Biometrics.USER_TABLE_4_URI, null, null, null, null);
	    if (c.moveToLast()) {
			Tbetminimum_sec = (long)c.getDouble(c.getColumnIndex("l0"));
			Tlast_sec = (long)c.getDouble(c.getColumnIndex("l1"));
	    }
	    if (c.getCount()==0){
	    	Tbetminimum_sec = (long) 60*60;
		    Tlast_sec = (long) 0;	
	    }
	    c.close();
	}

	// gets the time of the last hypotreatment
	public void get_hypotreatment_from_DB() {
		Tlast_hypo_sec = (long)0;
		long Ttemp;   
		Cursor c = getContentResolver().query(Biometrics.STATE_ESTIMATE_URI, null, null, null, null);
		if (c.getCount() == 0){
	    	Tlast_hypo_sec = (long)0;	
	    }
	    else {
	    	if (c.moveToFirst()) {
				do{
					Ttemp = (long)c.getDouble(c.getColumnIndex("hypoFlagTime"));

					if (Ttemp!=0) {Tlast_hypo_sec = Ttemp;}
					
				} while (c.moveToNext());
		    }			
	    }
	    c.close();
	}	

	// gets the last time when an event occured where "code" is the event's code from SysMan
	public void getLatestEvent(int code) {
		if ((code >= 1 && code <= 3) || (code >= 5 && code <= 10) || code == 100 || code == 200 || code == 201 || 
			(code >= 300 && code <= 302) || (code >= 304 && code <= 306) || code == 400 || code == 500 || code == 600) {
			TlastEventSec = 0;
			Tvector TvectorEvent = new Tvector();   
			TvectorEvent.init();
			Cursor c = getContentResolver().query(Biometrics.EVENT_URI, null, null, null, null);
			if (c.getCount() == 0){
				TlastEventSec = 0;	
		    }
		    else {
		    	if (c.moveToFirst()) {
					do{
						double tempcode = 0;
						tempcode = c.getDouble(c.getColumnIndex("code"));
						if (tempcode == code) {
							TvectorEvent.put(c.getLong(c.getColumnIndex("time")), c.getDouble(c.getColumnIndex("code")));
						}
					} while (c.moveToNext());
			    }
		    	
		    }
		    c.close();
			TlastEventSec = TvectorEvent.get_last_time();
			debug_message(TAG, "Tlast = " + TlastEventSec);
			
		}
	}	
	
	// gets subject's weight 
	public void getSubjectWeight() {
		Cursor c = getContentResolver().query(Biometrics.SUBJECT_DATA_URI, new String[]{"weight"}, null, null, null);
		if (c.moveToLast()) {
			USER_WEIGHT = (c.getInt(c.getColumnIndex("weight")));
		}
		c.close();
	}
	
	// gets DiAs state
	public void getSystem() {
		Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, new String[]{"diasState"}, null, null, null);
		if (c.moveToLast()) {
			DIAS_STATE = (c.getInt(c.getColumnIndex("diasState")));
		}
		c.close();
	}
	
	//**********************************************************
	// Misc
	//**********************************************************
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mainclosed, menu);
		return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
		SharedPreferences prefs = this.getSharedPreferences("edu.virginia.dtc.MealService", Context.MODE_PRIVATE);
		user_weight_to_carb_ratio_small_meal = prefs.getFloat(Small_meal_ratio_key, (float)0.4);
		user_weight_to_carb_ratio_medium_meal = prefs.getFloat(Medium_meal_ratio_key, (float)0.6);
		user_weight_to_carb_ratio_large_meal = prefs.getFloat(Large_meal_ratio_key, (float)1.0);
    	menu.clear();
        MenuInflater inflater = getMenuInflater();
		switch(DIAS_STATE)
		{
			case State.DIAS_STATE_CLOSED_LOOP:
				inflater.inflate(R.menu.mainclosed, menu);
				menu.clear();
				menu.add(0, R.id.menuClosedSmallMeal, 0, "Small Meal: "+String.format("%.2f g/kg", user_weight_to_carb_ratio_small_meal));
				menu.add(0, R.id.menuClosedMediumMeal, 0, "Medium Meal: "+String.format("%.2f g/kg", user_weight_to_carb_ratio_medium_meal));
				menu.add(0, R.id.menuClosedLargeMeal, 0, "Large Meal: "+String.format("%.2f g/kg", user_weight_to_carb_ratio_large_meal));
				break;
			default:
				break;
		}
		return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
    		case R.id.menuClosedSmallMeal:
    			WeightToCarbRatioDialogFragment newFragment = WeightToCarbRatioDialogFragment.newInstance(MEAL_SIZE_SMALL);
    			newFragment.show(getSupportFragmentManager(), "WeightToCarbRatioDialogFragment");
    			return true;
    		case R.id.menuClosedMediumMeal:
    			WeightToCarbRatioDialogFragment newFragment1 = WeightToCarbRatioDialogFragment.newInstance(MEAL_SIZE_MEDIUM);
    			newFragment1.show(getSupportFragmentManager(), "WeightToCarbRatioDialogFragment");
    			return true;
    		case R.id.menuClosedLargeMeal:
    			WeightToCarbRatioDialogFragment newFragment2 = WeightToCarbRatioDialogFragment.newInstance(MEAL_SIZE_LARGE);
    			newFragment2.show(getSupportFragmentManager(), "WeightToCarbRatioDialogFragment");
    			return true;
    		default:
    			return super.onOptionsItemSelected(item);
        }
    }
    
	public void onUserEntersRatio(int size, double ratio) {
		SharedPreferences prefs = getSharedPreferences("edu.virginia.dtc.MealService", Context.MODE_PRIVATE);
		switch (size) {
		case MEAL_SIZE_SMALL:
			user_weight_to_carb_ratio_small_meal = ratio;
			prefs.edit().putFloat(Small_meal_ratio_key, (float)user_weight_to_carb_ratio_small_meal).commit();
			break;
		case MEAL_SIZE_MEDIUM:
			user_weight_to_carb_ratio_medium_meal = ratio;
			prefs.edit().putFloat(Medium_meal_ratio_key, (float)user_weight_to_carb_ratio_medium_meal).commit();
			break;
		case MEAL_SIZE_LARGE:
			user_weight_to_carb_ratio_large_meal = ratio;
			prefs.edit().putFloat(Large_meal_ratio_key, (float)user_weight_to_carb_ratio_large_meal).commit();
			break;
		default:
			break;
		}
	}
	
	private static void error_message(String tag, String message) {
		Log.e(tag, "Error: "+message);
	}
	
	public void log_action(String tag, String message)
	{
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", tag);
        i.putExtra("Status", message);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
	}
	
	private void debug_message(String tag, String message) {
		if (DEBUG) {
			Log.i(tag, message);
		}
	}

	public void log_IO(String tag, String message) {
		Log.i(tag, message);
		/*
		 * Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
		 * i.putExtra("Service", tag); i.putExtra("Status", message);
		 * i.putExtra("time", (long)getCurrentTimeSeconds()); sendBroadcast(i);
		 */
	}

	private void log(int verbosity, String message) {
		if (DEBUG) {
			switch (verbosity) {
			case Log.VERBOSE:
				Log.v(TAG, message);
				break;
			case Log.DEBUG:
				Log.d(TAG, message);
				break;
			case Log.WARN:
				Log.w(TAG, message);
				break;
			case Log.ERROR:
				Log.e(TAG, message);
				break;
			}
		}

		// Add code to pipe this to file IO if necessary
	}
	
	public void storeUserTable1Data() {
		ContentValues values = new ContentValues();
		double d0 = 1.0;
		double d1 = 3.141592654;
		values.put("d0", d0);
		values.put("d1", d1);
		Uri uri;
		try {
			uri = getContentResolver().insert(Biometrics.USER_TABLE_1_URI, values);
			Log.i(TAG, "storeUserTable1Data > d0=" + d0 + ", d1=" + d1);
			if (enableIOtest) {
				log_IO(IO_TEST_TAG, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
				if (uri != null) {
					log_IO(IO_TEST_TAG, "> USER_TABLE_1 Write SUCCESS");
				} else {
					log_IO(IO_TEST_TAG,
							"> USER_TABLE_1 Write FAIL (write protection)");
				}
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		}
	}
	
	public double fetchLatestIOBValue(long time) {
		double return_value = 0.0;
		Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, null, null, null, null);
 	   	if(c!=null)
 	   	{
 	   		if(c.moveToLast())
 	   		{
 	   			IOB = c.getDouble(c.getColumnIndex("iobValue"));
 	   			if(IOB < 0.0)
 	   				IOB = 0; 	   			
 	   			return_value = IOB;
 	   		}
 	   		c.close();
 	   	}
		return return_value;
	}
		
/*
	public double fetchLatestIOBValue(long time) {
		double return_value = 0.0;
		// Fetch data from State Estimate data records
		Long Time = new Long(time);
		Cursor c=getContentResolver().query(Biometrics.STATE_ESTIMATE_URI, null, Time.toString(), null, null);
		long state_estimate_time;
		if (c.moveToFirst()) {
			do{
				if (c.getInt(c.getColumnIndex("asynchronous")) == 0) {
					state_estimate_time = c.getLong(c.getColumnIndex("time"));
					if (state_estimate_time >= time) {
						return_value = c.getDouble(c.getColumnIndex("IOB"));
					}
				}
			} while (c.moveToNext());
		}
		c.close();
		return return_value;
	}
*/
	
	//**********************************************************
	// Communication with MCMservice
	//**********************************************************	
	
	private void startMCM()
	{
		MCMservice = new ServiceConnection() {
        	final String FUNC_TAG = "MCMservice";
        	
            public void onServiceConnected(ComponentName className, IBinder service) {
                Debug.i(TAG, FUNC_TAG, "onServiceConnected Safety Service");
                MCM = new Messenger(service);
                MCMbound = true;
                Debug.i(TAG, FUNC_TAG, "MCM service");

                try {
            		// Send a register-client message to the service with the client message handler in replyTo
                	if (enableIOtest) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "(MealActivity) >> MCMservice, IO_TEST"+", "+FUNC_TAG+", "+
                						"MEAL_ACTIVITY_REGISTER"
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
            		Message msg = Message.obtain(null, Meal.MEAL_ACTIVITY_REGISTER, 0, 0);
            		msg.replyTo = MCMmessenger;
            		MCM.send(msg);
                }
                catch (RemoteException e) {
            		e.printStackTrace();
                }
                
                try {
            		// Send a register-client message to the service with the client message handler in replyTo
            		Message msg = Message.obtain(null, MCM_OCAD_MEAL_ACTIVITY_CALCULATE, 0, 0);
            		msg.replyTo = MCMmessenger;
            		MCM.send(msg);
                }
                catch (RemoteException e) {
            		e.printStackTrace();
                }
           }

           public void onServiceDisconnected(ComponentName className) {
        	   Debug.i(TAG, FUNC_TAG, "onServiceDisconnected");
        	   MCM = null;
        	   MCMbound = false;
           }
        };
        
        // Bind to the Safety Service
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
	            case Meal.MCM_CALCULATED_BOLUS:
	            	responseBundle = msg.getData();
	            	
	            	double bolus = responseBundle.getDouble("bolus");
	            	int status = responseBundle.getInt("MCM_status");
					String description = responseBundle.getString("MCM_description");
	            	
					if (enableIOtest) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "MCMservice >> (MealActivity), IO_TEST"+", "+FUNC_TAG+", "+
                						"MCM_CALCULATED_BOLUS"+", "+
                						"bolus="+bolus+", "+
                						"status="+status+", "+
                						"description="+description
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
					Debug.i(TAG, FUNC_TAG, "BOLUS: "+bolus);
					MealScreenTotalBolusValid = true;
					MealScreenTotalBolus = bolus;
					
					allTotal.setText(String.format("%.2f", (double)MealScreenTotalBolus));
	            	break;
	            case ADVISED_BOLUS:
	            	responseBundle = msg.getData();
	            	advised_bolus = responseBundle.getDouble("advised_bolus");
					
	    			Toast.makeText(getApplicationContext(),"Ustar="+advised_bolus, Toast.LENGTH_SHORT).show();
	            	
	    			debug_message(TAG, "Ustar calculation end");

	    			if (enableIOtest) {
                		Bundle b = new Bundle();
                		b.putString(	"description", "MCMservice >> (MealActivity), IO_TEST"+", "+FUNC_TAG+", "+
                						"ADVISED_BOLUS"+", "+
                						"advised_bolus="+advised_bolus
                					);
                		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_IO_TEST, Event.makeJsonString(b), Event.SET_LOG);
					}
					
	    			
	    			// last hypo treatment constraint		
	    			getLatestEvent(305);
	    			Tlast_hypo_sec = TlastEventSec;
	    			debug_message(TAG, "Tlast = " + Tlast_hypo_sec);
	    			if ((currentTimeseconds-Tlast_hypo_sec) < 3600) {
	    				advised_bolus=0;	
	    			}
	    			
	    			// last advice constraint		
	    			get_advice_data_from_DB(); // get Tlast_sec and Tbetminimum_sec
	    			
	    			if ((currentTimeseconds-Tlast_sec) < Tbetminimum_sec) {
	    				advised_bolus=0;	
	    			}
	    			
	    			// night constraint for Phase 4
	    			int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
	    			if (currentHour >= 23 || currentHour < 7) {
	    				advised_bolus=0;	
	    			}
	    		
	    			debug_message(TAG, "current time = " + currentTimeseconds
    						+ " Tlasthypo = " + Tlast_hypo_sec
    						+ " Tlast_sec " + Tlast_sec
    						+ " current hour = " + currentHour
    						+ " Ustar = " + advised_bolus);
	    			
	    		    USER_APPROVED_CLOSED_LOOP_BOLUS2 = advised_bolus ;
	    		    
	    		    if (USER_APPROVED_CLOSED_LOOP_BOLUS2 < 0){
	    		    	USER_APPROVED_CLOSED_LOOP_BOLUS2 = 0;	
	    		    	
	    		    }
	    			
	            	break;
	        	default:
					Debug.i(TAG, FUNC_TAG, "UNKNOWN_MESSAGE="+msg.what);
	        		super.handleMessage(msg);
            }
        }
    }

}