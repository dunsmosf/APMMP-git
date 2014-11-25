package edu.virginia.dtc.DiAsSetup;

import java.util.ArrayList;
import java.util.List;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.DiAsSubjectData;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.Tvector.Tvector;

public class DiAsSetup1 extends FragmentActivity implements ActionBar.TabListener {

	/**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
	 * will keep every loaded fragment in memory. If this becomes too memory
	 * intensive, it may be best to switch to a
	 * {@link android.support.v4.app.FragmentStatePagerAdapter}.
	 */
	SectionsPagerAdapter mSectionsPagerAdapter;

	/**
	 * The {@link ViewPager} that will host the section contents.
	 */
	ViewPager mViewPager;
	public final String TAG = "DiAsSetup1";
	public final Handler handler = new Handler();

	// Fragments
	ProfileFragment currentProfileFragment;
	SubjectInfoFragment subjectInfoFragment;
	CorrectionFactorFragment correctionFactorFragment;
	CarbohydrateRatioFragment carbohydrateRatioFragment;
	BasalRateFragment basalRateFragment;
	USSBRMFragment ussbrmFragment;
	DevicesFragment devicesFragment;

	public static boolean speedupAllowed = false;

	// Hardware device settings file
	public static final String PREFS_NAME = "HardwareSettingsFile";
	
	public BroadcastReceiver SetupReceiver;
	public DriverData hardware;
	public boolean selectedFromPager = false;
	
	public static SetupDB db;
	
	// AlertDialog constants
	AlertDialog optionsAlert = null;
	public static final int DIALOG_SAVE = 0;

	public static boolean configurationMode = true;

	// Development mode allows user to access extra options
	public static boolean developmentMode = true;

	// Remote Monitoring URI dropdown list declarations
	public static ArrayAdapter<String> rmURIList;
	public ArrayList<String> rmURIHistory = new ArrayList<String>();
	public static final int RM_URI_HISTORY_MAX = 3;

	// Icon animation
	public MenuItem passwordIcon;
	public ValueAnimator whiteToRedAnimator = ValueAnimator.ofInt(255, 0);
	public static final long ANIMATOR_DURATION_MILLIS = 300;
	public static final long ANIMATION_START_DELAY_MILLIS = 500;
	public boolean animationStarted = false;
	public boolean noPassword = false;
	Context currentContext = this;

	// Active mode booleans
	public boolean activeSensorOnly;
	public boolean activeOpenLoop;
	public boolean activeClosedLoop;
	public boolean activeUSSBRM;
	
	//  DiAs Subject Databases for comparison
	public static DiAsSubjectData local_sd = new DiAsSubjectData();
	
    private boolean doesPackageExist(String targetPackage){
    	List<ApplicationInfo> packages;
        PackageManager pm;
        pm = getPackageManager();        
        packages = pm.getInstalledApplications(0);
        for (ApplicationInfo packageInfo : packages) {
            if(packageInfo.packageName.equals(targetPackage)) return true;
        }        
        return false;
    }
    
    @Override
	public void onCreate(Bundle savedInstanceState) 
	{
		final String FUNC_TAG = "onCreate";
		
		super.onCreate(null);
		setContentView(R.layout.main);
		
		currentContext = this;
		
		db = new SetupDB(this);
		
        // Create the adapter that will return a fragment for each of the three primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

		// Set up the action bar.
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setDisplayShowHomeEnabled(false);

		// Set up the ViewPager with the sections adapter.
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);

		// When swiping between different sections, select the corresponding tab.
		// We can also use ActionBar.Tab#select() to do this if we have a reference to the
		// Tab.
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() 
		{
			@Override
			public void onPageSelected(int position) 
			{
				int tabPosition = 0;
				switch (position) 
				{
					case 0:
						currentProfileFragment = null;
						tabPosition = 0;
						break;
					case 1:
						currentProfileFragment = correctionFactorFragment;
						tabPosition = 1;
						break;
					case 2:
						currentProfileFragment = carbohydrateRatioFragment;
						tabPosition = 1;
						break;
					case 3:
						currentProfileFragment = basalRateFragment;
						tabPosition = 1;
						break;
					case 4:
						currentProfileFragment = ussbrmFragment;
						tabPosition = 1;
						break;
					case 5:
						currentProfileFragment = null;
						tabPosition = 2;
						break;
				}
				selectedFromPager = true;
				actionBar.setSelectedNavigationItem(tabPosition);
				selectedFromPager = false;
			}
		});

		// For each of the sections in the app, add a tab to the action bar.
		actionBar.addTab(actionBar.newTab().setText(mSectionsPagerAdapter.getPageTitle(0)).setTabListener(this));
		actionBar.addTab(actionBar.newTab().setText(mSectionsPagerAdapter.getPageTitle(1)).setTabListener(this));
		actionBar.addTab(actionBar.newTab().setText(mSectionsPagerAdapter.getPageTitle(mSectionsPagerAdapter.getCount()-1)).setTabListener(this));

		if (rmURIList == null) 
		{
			rmURIList = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
			rmURIList.setDropDownViewResource(R.layout.spinnerdropdownlongstring);
		}

		Debug.i(TAG, FUNC_TAG, "Starting animation!");
		whiteToRedAnimator.setDuration(ANIMATOR_DURATION_MILLIS);
		whiteToRedAnimator.setRepeatCount(4);
		whiteToRedAnimator.setRepeatMode(ValueAnimator.REVERSE);
		whiteToRedAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
		whiteToRedAnimator.setStartDelay(ANIMATION_START_DELAY_MILLIS);
		whiteToRedAnimator.addListener(new AnimatorListenerAdapter() 
		{
			@Override
			public void onAnimationStart(Animator animation) {
				if (passwordIcon != null);
			}

			@Override
			public void onAnimationEnd(Animator animation) {
				animationStarted = true;
				if (passwordIcon != null);
			}
		});
		whiteToRedAnimator.addUpdateListener(new AnimatorUpdateListener() 
		{
			public void onAnimationUpdate(ValueAnimator anim) {
				int value = ((Integer) anim.getAnimatedValue()).intValue();
				if (passwordIcon != null) {
					passwordIcon.getIcon().setColorFilter(Color.rgb(255, value, value), PorterDuff.Mode.MULTIPLY);
					invalidateOptionsMenu();
				}
			}
		});
		
		SetupReceiver = new BroadcastReceiver() 
		{
			final String FUNC_TAG = "SetupReceiver";
			
			@Override
			public void onReceive(Context context, Intent intent) {
				Debug.i(TAG, FUNC_TAG, intent.getAction());

				String action = intent.getAction();
				if (action.equals("android.intent.action.KEYBOARD_SHOW")) 
				{
				} 
				else if (action.equals("android.intent.action.KEYBOARD_HIDE")) 
				{
					updateDisplay();
				} 
				else if (action.equals("android.intent.action.KEYBOARD_DISMISSED")) 
				{
					updateDisplay();
				}
			}
		};
		
		IntentFilter filter = new IntentFilter();
		filter.addAction("android.intent.action.KEYBOARD_SHOW");
		filter.addAction("android.intent.action.KEYBOARD_HIDE");
		filter.addAction("android.intent.action.KEYBOARD_DISMISSED");
		filter.addAction("android.intent.action.KEYBOARD_SHOW_H");
		filter.addAction("android.intent.action.KEYBOARD_HIDE_H");
		registerReceiver(SetupReceiver, filter);
		
		subjectInfoFragment = new SubjectInfoFragment(this);
		correctionFactorFragment = new CorrectionFactorFragment(this);
		carbohydrateRatioFragment = new CarbohydrateRatioFragment(this);
		basalRateFragment = new BasalRateFragment(this);
		ussbrmFragment = new USSBRMFragment(this);
		devicesFragment = new DevicesFragment(this);

		int screen = this.getIntent().getIntExtra("setupScreenIDNumber", 0);
		Debug.i(TAG, FUNC_TAG, "Screen = " + screen);
		mViewPager.setCurrentItem(screen);
		
		hardware = DriverData.getInstance();
		SharedPreferences prefs = getSharedPreferences(DiAsSetup1.PREFS_NAME, 0);
		hardware.realTime = prefs.getBoolean("realtime", true);
		hardware.speedupMultiplier = prefs.getInt("speedupMultiplier", 1);
		
		if(prefs.getBoolean("clear", false))
		{
			Debug.w(TAG, FUNC_TAG, "Need to clear local database...");
			prefs.edit().putBoolean("clear", true).commit();
			db.clearDb();
		}
	}
    
    @Override
    public void onStart() 
    {
    	super.onStart();
    	
    	final String FUNC_TAG = "onStart";
    	
    	Debug.i(TAG, FUNC_TAG, "");
    	
    	//Read the local DB on start so that we can update the display from what we previously entered
	    local_sd = db.readDb();
	    
	    validate();
	    
	    DiAsSubjectData.print(TAG, local_sd);
    }

	@Override
	public void onPause() 
	{
		super.onPause();
		Debug.i(TAG, "onPause", "");
	}

	@Override
	public void onResume() 
	{
		super.onResume();
		Debug.i(TAG, "onResume", "");
		updateDisplay();
	}
	
	@Override
	public void onDestroy() 
	{
		super.onDestroy();
		Debug.i(TAG, "onDestroy", "");
		unregisterReceiver(SetupReceiver);
	}
	
	//*********************************************************************************
	//*********************************************************************************
	//
	//	DISPLAY UPDATES
	//
	//*********************************************************************************
	//*********************************************************************************

	private void validate()
	{
		final String FUNC_TAG = "validate";
		
		Debug.i(TAG, FUNC_TAG, "Validating...");
		
		//Check subject name
		local_sd.subjectNameValid = false;
		if (local_sd.subjectName.length() > 0) 
		{
			local_sd.subjectNameValid = true;
		}
		
		//Check subject session
		local_sd.subjectSessionValid = false;
		if (local_sd.subjectSession.length() > 0 && local_sd.subjectSession.length() < 10) 
		{
			int subject_number = Integer.parseInt(local_sd.subjectSession);
			if (subject_number > 0 && subject_number < 1000000000) 
			{
				local_sd.subjectSessionValid = true;
			} 
		}
		
		//Check subject weight
		local_sd.weightValid = false;
		if (local_sd.subjectWeight >= 27 && local_sd.subjectWeight <= 136) 
		{
			local_sd.weightValid = true;
		}
		
		//Check subject height
		local_sd.heightValid = false;
		if (local_sd.subjectHeight >= 127 && local_sd.subjectWeight <= 221) 
		{
			local_sd.heightValid = true;
		}
		
		//Check subject age
		local_sd.ageValid = false;
		if (local_sd.subjectAge >= 1 && local_sd.subjectAge <= 100) 
		{
			local_sd.ageValid = true;
		}
		
		//Check TDI
		local_sd.TDIValid = false;
		if (local_sd.subjectTDI >= 10 && local_sd.subjectTDI <= 100) 
		{
			local_sd.TDIValid = true;
		} 
		
		local_sd.AITValid = true;
		
		if (readTvector(local_sd.subjectCF, Biometrics.CF_PROFILE_URI))
			local_sd.subjectCFValid = true;
		if (readTvector(local_sd.subjectCR, Biometrics.CR_PROFILE_URI))
			local_sd.subjectCRValid = true;
		if (readTvector(local_sd.subjectBasal, Biometrics.BASAL_PROFILE_URI))
			local_sd.subjectBasalValid = true;
		if (readTvector(local_sd.subjectSafety, Biometrics.USS_BRM_PROFILE_URI))
			local_sd.subjectSafetyValid = true;
		
		//If all these things are valid then we don't allow modification of the subject info
		if(subjectDataExists())
			configurationMode = true;
		else
			configurationMode = false;
		
		DiAsSubjectData.print(TAG, local_sd);
		
		Debug.i(TAG, FUNC_TAG, "Ending validation!");
	}
	
	private boolean subjectDataExists()
	{
		final String FUNC_TAG = "subjectDataExists";
		
		Cursor c = getContentResolver().query(Biometrics.SUBJECT_DATA_URI, null, null, null, null);
		if(c != null)
		{
			Debug.i(TAG, FUNC_TAG, "Count: "+c.getCount());
			if(c.getCount() > 0)
			{
				Debug.i(TAG, FUNC_TAG, "Subject data is in the table!");
				return true;
			}
		}
		
		Debug.w(TAG, FUNC_TAG, "Subject data does not exist in bioCP!");
		return false;
	}
	
	public void updateDisplay() 
	{
		final String FUNC_TAG = "updateDisplay";
		
		DiAsSubjectData subject_data = local_sd;
		
		DiAsSetup1.db.writeDb(DiAsSetup1.local_sd);
		
		Debug.i(TAG, FUNC_TAG, "Printing subject_data...");
		
		DiAsSubjectData.print(TAG, subject_data);
		
		activeSensorOnly = subject_data.subjectNameValid && subject_data.subjectSessionValid && subject_data.weightValid && subject_data.heightValid
				&& subject_data.ageValid && subject_data.TDIValid && subject_data.AITValid;
		activeOpenLoop = activeSensorOnly && subject_data.subjectCFValid && subject_data.subjectCRValid && subject_data.subjectBasalValid;
		activeClosedLoop = activeOpenLoop;
		activeUSSBRM = activeOpenLoop && subject_data.subjectSafetyValid;

		Debug.i(TAG, FUNC_TAG, "Sensor: "+activeSensorOnly+" Open Loop: "+activeOpenLoop+" Closed Loop: "+activeClosedLoop+" BRM: "+activeUSSBRM);
		
		//Set progress bar
		ProgressBar p = (ProgressBar)this.findViewById(R.id.progressBar1);
		TextView tv = (TextView)this.findViewById(R.id.availableModeText);
		
		p.setMax(100);
		int prog = 0;
		
		if(activeSensorOnly)
			prog += 25;
		if(subject_data.subjectCFValid)
			prog += 25;
		if(subject_data.subjectCRValid)
			prog += 25;
		if(subject_data.subjectBasalValid)
			prog += 25;
		
		p.setProgress(prog);
		
		String mode = "";
		if(activeSensorOnly)
			mode = "Sensor Mode";
		if(activeOpenLoop)
			mode = "Pump Mode";
		if(activeClosedLoop)
			mode = "Closed/Pump Mode";
		if(activeUSSBRM)
			mode = "Closed/Pump/BRM Mode";
		tv.setText(mode);
			
		toggleLight((TextView) findViewById(R.id.lightNone), !(activeSensorOnly || activeOpenLoop || activeClosedLoop));
		toggleLight((TextView) findViewById(R.id.lightSensorOnly), activeSensorOnly);
		toggleLight((TextView) findViewById(R.id.lightOpenLoop), activeOpenLoop);
		toggleLight((TextView) findViewById(R.id.lightClosedLoop), activeClosedLoop);
		toggleLight((TextView) findViewById(R.id.lightUSSBRM), activeUSSBRM);

		Cursor c = getContentResolver().query(Biometrics.PASSWORD_URI, null, null, null, null);
		if (c.moveToLast()) 
		{
			Debug.i(TAG, FUNC_TAG, "There is a password so no need to animate!");
			noPassword = false;
			whiteToRedAnimator.cancel();
			if (passwordIcon != null) 
			{
				passwordIcon.getIcon().clearColorFilter();
				invalidateOptionsMenu();
			}
		} else {
			Debug.i(TAG, FUNC_TAG, "There is no password!");
			if (!animationStarted)
			{
				Debug.i(TAG, FUNC_TAG, "Starting animation...");
				whiteToRedAnimator.start();
			}
			noPassword = true;
		}
		c.close();

		switch (mViewPager.getCurrentItem()) 
		{
			case 0:
				Debug.i(TAG, FUNC_TAG, "Subject Info");
				subjectInfoFragment.updateDisplay();
				break;
			case 1:
				Debug.i(TAG, FUNC_TAG, "Corr. Factor");
				correctionFactorFragment.updateDisplay();
				break;
			case 2:
				Debug.i(TAG, FUNC_TAG, "Carb Ratio");
				carbohydrateRatioFragment.updateDisplay();
				break;
			case 3:
				Debug.i(TAG, FUNC_TAG, "Basal Rate");
				basalRateFragment.updateDisplay();
				break;
			case 4:
				Debug.i(TAG, FUNC_TAG, "Night Profile");
				ussbrmFragment.updateDisplay();
				break;
			case 5:
				Debug.i(TAG, FUNC_TAG, "Device Manager");
				devicesFragment.updateDisplay();
				break;
		}
	}

	private void toggleLight(TextView light, boolean active) 
	{
		if (active)
			light.getBackground().clearColorFilter();
		else
			light.getBackground().setColorFilter(Color.rgb(85, 85, 85), PorterDuff.Mode.DARKEN);
	}
	
	//*********************************************************************************
	//*********************************************************************************
	//
	//	DATABASE ACCESSORS
	//
	//*********************************************************************************
	//*********************************************************************************

	public void saveDiAsSubjectData(DiAsSubjectData subject_data) 
	{
		final String FUNC_TAG = "saveDiAsSubjectData";		
		
		int diasState = getDiAsState();
		
		if (diasState == State.DIAS_STATE_CLOSED_LOOP || diasState == State.DIAS_STATE_OPEN_LOOP || diasState == State.DIAS_STATE_SAFETY_ONLY) 
		{
			if (subject_data.subjectCF.count()==0 || subject_data.subjectCR.count()==0 || subject_data.subjectBasal.count()==0) 
			{
    			Bundle b = new Bundle();
	    		b.putString("description", State.stateToString(diasState)+" mode requires that CR, CF and Basal profiles contain at least one entry.  The changes have not been saved.");
	    		Event.addEvent(getApplicationContext(), Event.EVENT_SYSTEM_ERROR, Event.makeJsonString(b), Event.SET_POPUP_AUDIBLE_VIBE);
	    		return;
			}
		}
		
		// Save Subject Information to biometricsContentProvider
		Debug.i(TAG, FUNC_TAG,"Subject ID: " + subject_data.subjectName);
		
		// Clear current subject information and overwrite with new
		getContentResolver().delete(Uri.parse("content://" + Biometrics.PROVIDER_NAME + "/info"), null, null);
		
		ContentValues values = new ContentValues();
		values.put("subjectid", subject_data.subjectName);
		values.put("session", subject_data.subjectSession);
		values.put("weight", subject_data.subjectWeight);
		values.put("height", subject_data.subjectHeight);
		values.put("age", subject_data.subjectAge);
		values.put("TDI", subject_data.subjectTDI);
		
		if (subject_data.subjectFemale) {
			values.put("isfemale", 1);
		} else {
			values.put("isfemale", 0);
		}
		if (subject_data.subjectSafetyValid) {
			values.put("SafetyOnlyModeIsEnabled", 1);
		} else {
			values.put("SafetyOnlyModeIsEnabled", 0);
		}
		if (subject_data.realTime) {
			values.put("realtime", 1);
		} else {
			values.put("realtime", 0);
		}
		
		values.put("AIT", subject_data.subjectAIT);
		values.put("insulinSetupComplete", (subject_data.subjectCFValid && subject_data.subjectCRValid && subject_data.subjectBasalValid) ? 1 : 0);

		try {
			getContentResolver().insert(Biometrics.SUBJECT_DATA_URI, values);
		} catch (Exception e) {
			Debug.i(TAG, FUNC_TAG,"saveDiAsSubjectData error =" + e.getMessage());
		}

		saveTvector(subject_data.subjectCF, Biometrics.CF_PROFILE_URI, true);
		saveTvector(subject_data.subjectCR, Biometrics.CR_PROFILE_URI, true);
		saveTvector(subject_data.subjectBasal, Biometrics.BASAL_PROFILE_URI, true);
		saveTvector(subject_data.subjectSafety, Biometrics.USS_BRM_PROFILE_URI, false);
		
		DiAsSubjectData.print(TAG, subject_data);
	}

	private void saveTvector(Tvector tvector, Uri uri, boolean value) 
	{
		final String FUNC_TAG = "saveTvector";
		
		int ii;
		ContentValues content_values = new ContentValues();
		for (ii = 0; ii < tvector.count(); ii++) 
		{
			content_values.put("time", tvector.get_time(ii).longValue());
			
			if (tvector.get_end_time(ii) != 0)
				content_values.put("endtime", tvector.get_end_time(ii).longValue());
			
			if (tvector.get_value(ii) >= 0 && value)
				content_values.put("value", (tvector.get_value(ii).doubleValue()));
			
			try {
				getContentResolver().insert(uri, content_values);
			} catch (Exception e) {
				Debug.i(TAG, FUNC_TAG,"getContentResolver().insert() error =" + e.getMessage());
			}
		}
	}

	public DiAsSubjectData readDiAsSubjectData() 
	{
		final String FUNC_TAG = "readDiAsSubjectData";
		
		DiAsSubjectData subject_data = new DiAsSubjectData();

		Cursor c = getContentResolver().query(Biometrics.SUBJECT_DATA_URI, null, null, null, null);
		Debug.i(TAG, FUNC_TAG,"Retrieved SUBJECT_DATA_URI with " + c.getCount() + " items");
		if (c.moveToLast()) 
		{
			// A database exists.  Initialize subject_data.
			subject_data.subjectName = new String(c.getString(c.getColumnIndex("subjectid")));
			subject_data.subjectSession = new String(c.getString(c.getColumnIndex("session")));
			subject_data.subjectWeight = (c.getInt(c.getColumnIndex("weight")));
			subject_data.subjectHeight = (c.getInt(c.getColumnIndex("height")));
			subject_data.subjectAge = (c.getInt(c.getColumnIndex("age")));
			subject_data.subjectTDI = (c.getInt(c.getColumnIndex("TDI")));
			
			// subject_data.subjectAIT = (c.getInt(c.getColumnIndex("AIT")));
			subject_data.subjectAIT = 4; // Force AIT == 4 for safety

			int isfemale = c.getInt(c.getColumnIndex("isfemale"));
			if (isfemale == 1)
				subject_data.subjectFemale = true;
			else
				subject_data.subjectFemale = false;

			int SafetyOnlyModeIsEnabled = c.getInt(c.getColumnIndex("SafetyOnlyModeIsEnabled"));
			if (SafetyOnlyModeIsEnabled == 1)
				subject_data.subjectSafetyValid = true;
			else
				subject_data.subjectSafetyValid = false;

			int realtime = c.getInt(c.getColumnIndex("realtime"));
			if (realtime == 1)
				subject_data.realTime = true;
			else
				subject_data.realTime = false;

//			// Set flags
//			subject_data.subjectNameValid = true;
//			subject_data.subjectSessionValid = true;
//			subject_data.weightValid = true;
//			subject_data.heightValid = true;
//			subject_data.ageValid = true;
//			subject_data.TDIValid = true;
//			subject_data.AITValid = true;
		}
		
		if (readTvector(subject_data.subjectCF, Biometrics.CF_PROFILE_URI))
			subject_data.subjectCFValid = true;
		if (readTvector(subject_data.subjectCR, Biometrics.CR_PROFILE_URI))
			subject_data.subjectCRValid = true;
		if (readTvector(subject_data.subjectBasal, Biometrics.BASAL_PROFILE_URI))
			subject_data.subjectBasalValid = true;
		if (readTvector(subject_data.subjectSafety, Biometrics.USS_BRM_PROFILE_URI))
			subject_data.subjectSafetyValid = true;
		c.close();
		
		DiAsSubjectData.print(TAG, subject_data);
		
		return subject_data;
	}

	public boolean readTvector(Tvector tvector, Uri uri) 
	{
		final String FUNC_TAG = "readTvector";
		
		boolean retvalue = false;
		Cursor c = getContentResolver().query(uri, null, null, null, null);
		long t, t2 = 0;
		double v;
		if (c.moveToFirst()) {
			do {
				t = c.getLong(c.getColumnIndex("time"));
				if (c.getColumnIndex("endtime") < 0){
					v = c.getDouble(c.getColumnIndex("value"));
					//Debug.i(TAG, FUNC_TAG,"readTvector: t=" + t + ", v=" + v);
					tvector.put(t, v);
				} else if (c.getColumnIndex("value") < 0){
					//Debug.i(TAG, FUNC_TAG,"readTvector: t=" + t + ", t2=" + t2);
					t2 = c.getLong(c.getColumnIndex("endtime"));
					tvector.put_range(t, t2);
				}
			} while (c.moveToNext());
			retvalue = true;
		}
		c.close();
		return retvalue;
	}

	public void putTvector(Bundle bundle, Tvector tvector, String startTimeKey, String endTimeKey, String valueKey) 
	{
		long[] times = new long[tvector.count()];
		long[] endTimes = new long[tvector.count()];
		double[] values = new double[tvector.count()];
		int ii;
		for (ii = 0; ii < tvector.count(); ii++) {
			times[ii] = tvector.get_time(ii).longValue();
			endTimes[ii] = tvector.get_end_time(ii).longValue();
			values[ii] = tvector.get_value(ii).doubleValue();
		}
		if (startTimeKey != null)
			bundle.putLongArray(startTimeKey, times);
		if (endTimeKey != null)
			bundle.putLongArray(endTimeKey, endTimes);
		if (valueKey != null)
			bundle.putDoubleArray(valueKey, values);
	}
	
	//*********************************************************************************
	//*********************************************************************************
	//
	//	MISC. FUNCTIONS
	//
	//*********************************************************************************
	//*********************************************************************************

	public void clickReceiver(View view) 
	{
		switch (view.getId()) 
		{
			case R.id.buttonPlus:
				currentProfileFragment.addItemToProfile(null);
				break;
			case R.id.buttonMinus:
				currentProfileFragment.removeItemFromProfile(null);
				break;
			case R.id.buttonClear:
				currentProfileFragment.clearProfile();
				break;
		}
	}

	public void finishClick(View view) 
	{
		final String FUNC_TAG = "finishClick";
		
		if (!(activeSensorOnly || activeOpenLoop || activeClosedLoop)) {
			Toast.makeText(this, "Complete setup before returning to main screen", Toast.LENGTH_LONG).show();
			return;
		}

		if (noPassword) {
			Debug.i(TAG, FUNC_TAG, "There is no password, starting animation!");
			whiteToRedAnimator.setRepeatCount(2);
			whiteToRedAnimator.setStartDelay(0);
			whiteToRedAnimator.start();
			Toast.makeText(this, "Set password before continuing", Toast.LENGTH_LONG).show();
			return;
		}
		
		if(DiAsSubjectData.isChanged(local_sd, readDiAsSubjectData()))
		{
			Debug.e(TAG, FUNC_TAG, "The values for the subject database are different!");
			
			// If there is no subject data then we simply commit the database to the bCP
			if(!subjectDataExists())
				finishConfirm(true);
			else
				showDialogSave();
		}
		else
		{
			Debug.e(TAG, FUNC_TAG, "The values for the subject database the same!");
			finishConfirm(false);
		}
	}
	
	private int getDiAsState() 
	{
		int diasState = State.DIAS_STATE_STOPPED;
    	Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, new String[]{"diasState"}, null, null, null);
    	if (c != null) 
    	{
    		if (c.moveToLast()) 
    		{
    	    	diasState = c.getInt(c.getColumnIndex("diasState"));
    			c.close();
    		}
    	}
    	return diasState;
	}

	public void finishConfirm(boolean saveSubjectData) 
	{
		final String FUNC_TAG = "finishConfirm";
		
		if(saveSubjectData)
		{
			// Save the local data to the biometrics Content Provider
			saveDiAsSubjectData(local_sd);
			Toast.makeText(this, "Subject information saved", Toast.LENGTH_SHORT).show();
			Debug.i(TAG, FUNC_TAG, "Saving data...");
			
			// Send broadcast notifying subject data is present
			Intent intentBroadcast = new Intent(DiAsSubjectData.PROFILE_CHANGE);
			sendBroadcast(intentBroadcast);
		}

		Debug.i(TAG, FUNC_TAG, "Starting DiAs UI...");
		
		// DiAsUI
		Intent uiIntent = new Intent();
		uiIntent.setClassName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.DiAsMain");
		uiIntent.setAction(Intent.ACTION_MAIN);
		uiIntent.putExtra("dataConfigured", true);
		startActivity(uiIntent);

		// Update drivers so they will report their status
		Debug.i(TAG, FUNC_TAG,"Updating drivers...");
		Intent update = new Intent("edu.virginia.dtc.DRIVER_UPDATE");
		sendBroadcast(update);
		
		finish();
	}

	private void showDialogSave() 
	{
		AlertDialog.Builder sBuild = new AlertDialog.Builder(this);
		sBuild.setMessage("Save information and go to DiAs UI?").setCancelable(false).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// Canceled
			}
		}).setNeutralButton("Discard Changes", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				finishConfirm(false);
			}
		}).setPositiveButton("Apply Changes", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				finishConfirm(true);
			}
		});
		sBuild.show();
	}

	private void showDialogPassword() 
	{
		Cursor c = getContentResolver().query(Biometrics.PASSWORD_URI, null, null, null, null);
		final String password;
		if (c.moveToLast()) {
			password = c.getString(c.getColumnIndex("password"));
		} else
			password = null;
		c.close();
		
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Set Password");
		FrameLayout frame = new FrameLayout(this);
		frame.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
		LinearLayout root = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.passwordlinear, frame, false);
		alert.setView(root);
		final TextView textOld = (TextView) root.findViewById(R.id.textOld);
		final TextView textNew = (TextView) root.findViewById(R.id.textNew);
		final TextView textConfirm = (TextView) root.findViewById(R.id.textConfirm);
		final EditText editOld = (EditText) root.findViewById(R.id.editOld);
		final EditText editNew = (EditText) root.findViewById(R.id.editNew);
		final EditText editConfirm = (EditText) root.findViewById(R.id.editConfirm);
		editNew.setHint("New password (4-16 characters)");
		editConfirm.setHint("Confirm new password");
		if (password == null) {
			editOld.setEnabled(false);
			textOld.setTextColor(Color.GRAY);
		} else {
			editOld.setEnabled(true);
			editOld.setHint("Enter password");
		}
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// Won't be called.
			}
		});
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// Canceled.
			}
		});
		final AlertDialog dialog = alert.create();
		dialog.show();
		dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				textOld.setTextColor((password == null) ? Color.GRAY : Color.WHITE);
				textConfirm.setTextColor(Color.WHITE);
				textNew.setTextColor(Color.WHITE);
				if (editOld.getText().toString().equals(password) || password == null) {
					if (password != null)
						textOld.setTextColor(Color.GREEN);
					String newString = editNew.getText().toString();
					if (newString.length() >= 4 && newString.length() <= 16) {
						textNew.setTextColor(Color.GREEN);
						if (editNew.getText().toString().equals(editConfirm.getText().toString())) {
							textConfirm.setTextColor(Color.GREEN);
							savePassword(newString);
							handler.postDelayed(new Thread(){
								@Override
								public void run(){
									dialog.dismiss();
								}
							}, 250);
						} else
							textConfirm.setTextColor(Color.RED);
					} else
						textNew.setTextColor(Color.RED);
				} else
					textOld.setTextColor(Color.RED);
			}
		});
	}

	public void savePassword(String password) 
	{
		ContentValues values = new ContentValues();
		values.put("password", password);
		getContentResolver().update(Biometrics.PASSWORD_URI, values, null, null);
		updateDisplay();
	}
	
	private void showDialogUrl() 
	{
		final String FUNC_TAG = "showDialogUrl";
		
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle("Monitoring server URL");
		FrameLayout frame = new FrameLayout(this);
		frame.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
		RadioGroup group = (RadioGroup) LayoutInflater.from(this).inflate(R.layout.monitoringurlchoices, frame, false);
		
		List<String> urls = new ArrayList<String>();
		urls.add(Params.getString(getContentResolver(), "dwm_address_default", ""));
		urls.add(Params.getString(getContentResolver(), "dwm_address_2", ""));
		urls.add(Params.getString(getContentResolver(), "dwm_address_3", ""));
		
		
		Cursor c = getContentResolver().query(Biometrics.SERVER_URI, null, null, null, null);
		final String server_address;
		Debug.i(TAG, FUNC_TAG, c.toString());
		if (c.moveToLast()) {
			server_address = c.getString(c.getColumnIndex("server_url"));
		} else
			server_address = "";
		c.close();
		
		Debug.i(TAG, FUNC_TAG, "Server Address: "+server_address);
		
		
		int i_urls = 0;
		int id_tocheck = -1;
		for (String url : urls) 
		{
			if (!url.isEmpty()) 
			{
				RadioButton button = new RadioButton(this);
				button.setId(i_urls);
				button.setText(url);
				if ( (!server_address.isEmpty()) && (url.equals(server_address)) ) 
				{
					id_tocheck = i_urls;
				}
				group.addView(button);
				i_urls ++;
			}
		}
		if (id_tocheck > -1) {
			group.check(id_tocheck);
		}
		
		alert.setView(group);
		
		final AlertDialog dialog = alert.create();
		
		group.setOnCheckedChangeListener(new OnCheckedChangeListener()
		{
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				RadioButton chosen = (RadioButton) group.getChildAt(checkedId);
				String url = (String) chosen.getText();
				if (!url.equals(server_address)) {
					
					//TODO: Add Validation Pop-Up for Server URL change
					ContentValues values = new ContentValues();
					values.put("server_url", url);
					getContentResolver().update(Biometrics.SERVER_URI, values, null, null);
					Debug.i(TAG, FUNC_TAG, "Server URL changed to: " + url);
					Toast.makeText(getBaseContext(), "Server URL changed to: " + url, Toast.LENGTH_SHORT).show();
					
					//TODO: Restart NetworkService after Server URL change
					Intent intentNetworkService = new Intent("DiAs.NetworkService");
		    		stopService(intentNetworkService);
		    		startService(intentNetworkService);
		    		
					dialog.dismiss();
				}
			}
		});
		
		dialog.setCanceledOnTouchOutside(false);
		dialog.show();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) 
	{
		final String FUNC_TAG = "onKeyDown";
		
		if (keyCode == KeyEvent.KEYCODE_HOME){
			AlertDialog.Builder alert = new AlertDialog.Builder(this);
			alert.setTitle("Launch screen");
			LinearLayout root = new LinearLayout(this);
			final TextView text = new TextView(this);
			text.setText("Enter password to go to launch screen");
			text.setTextAppearance(this, android.R.style.TextAppearance_DeviceDefault_Medium);
			final EditText edit = new EditText(this);
			edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
			edit.setHint("Enter password");
			root.setOrientation(LinearLayout.VERTICAL);
			root.addView(text);
			root.addView(edit);
			alert.setView(root);
			Cursor c = getContentResolver().query(Biometrics.PASSWORD_URI, null, null, null, null);
			alert.setPositiveButton("Ok", new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					// Won't be called
				}
			});
			alert.setNegativeButton("Cancel", new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					// Cancel
				}
			});
			final AlertDialog dialog = alert.create();
			dialog.show();

			if (c.moveToLast()) {
				final String PASSWORD = c.getString(c.getColumnIndex("password"));
				dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						if (edit.getText().toString().equals(PASSWORD)){
							dialog.dismiss();
							homeConfirm();
						} else {
							text.setText("Wrong password, try again");
						}
					}
				});
			} else {
				text.setText("No password found.\nCreate a password first.");
				//edit.setEnabled(true);
				dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						dialog.dismiss();
					}
				});
			}
			c.close();
			
			return true;
		}
		else if (keyCode == KeyEvent.KEYCODE_BACK) {
			
			Debug.i(TAG, FUNC_TAG, "Back key pressed!");
			
			finishClick(null);
			return true;
		} else
			return super.onKeyDown(keyCode, event);
	}
	
    public void homeConfirm()
    {
		Intent homeIntent =  new Intent(Intent.ACTION_MAIN, null);
		homeIntent.addCategory(Intent.CATEGORY_HOME);
		homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		startActivity(homeIntent);
		sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));    	
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		getMenuInflater().inflate(R.menu.main, menu);
		passwordIcon = menu.getItem(0);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		final String FUNC_TAG = "onPrepareOptionsMenu";
		
		if (menu.size() > 5) {
			int ii;
			int size = menu.size();
			for (ii=3; ii<size; ii++) {
				menu.removeItem(menu.getItem(ii).getItemId());		// Remove items 3 and greater if they exist, then add item 3 if necessary				
			}
		}	
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final String FUNC_TAG = "onOptionsItemSelected";
		
		switch (item.getItemId()) 
		{
			case R.id.menuPassword:
				showDialogPassword();
				return true;
			case R.id.menuTime:
				startActivity(new Intent(android.provider.Settings.ACTION_DATE_SETTINGS));
				return true;
			case R.id.menuWifi:
				startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
				return true;
			case R.id.menuBt:
				startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
				return true;
			case R.id.menuUrl:
				showDialogUrl();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
	}

	public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
		// When the given tab is selected, switch to the corresponding page in the ViewPager.
		int tabPosition = tab.getPosition();
		int pagerPosition = 0;
		switch (tabPosition) {
		case 0:
			pagerPosition = 0;
			break;
		case 1:
			pagerPosition = 1;
			break;
		case 2:
			pagerPosition = 5;
			break;
		}
		if (!selectedFromPager)
			mViewPager.setCurrentItem(pagerPosition);
	}

	public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
		updateDisplay();
	}

	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int i) {
			Fragment fragment = null;
			switch (i) {
			case 0:
				fragment = subjectInfoFragment;
				break;
			case 1:
				fragment = correctionFactorFragment;
				break;
			case 2:
				fragment = carbohydrateRatioFragment;
				break;
			case 3:
				fragment = basalRateFragment;
				break;
			case 4:
				fragment = ussbrmFragment;
				break;
			case 5:
				fragment = devicesFragment;
				break;
			}
			return fragment;
		}

		@Override
		public int getCount() {
			return 6;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
			case 0:
				return getString(R.string.titleInfo).toUpperCase();
			case 1:
			case 2:
			case 3:
			case 4:
				return getString(R.string.titleInsulin).toUpperCase();
			case 5:
				return getString(R.string.titleDevices).toUpperCase();
			}
			return null;
		}
	}
}
