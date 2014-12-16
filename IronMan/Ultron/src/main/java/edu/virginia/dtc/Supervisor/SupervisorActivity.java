package edu.virginia.dtc.Supervisor;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTitleStrip;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import edu.virginia.dtc.DiAsService.R;
import edu.virginia.dtc.Supervisor.ConfigurationManager.Config;
import edu.virginia.dtc.Supervisor.ConfigurationManager.InstalledApp;
import edu.virginia.dtc.Supervisor.ConfigurationManager.Pack;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;

public class SupervisorActivity extends FragmentActivity
{
	private static final String TAG = "Supervisor";

	// DiAsService Commands
	public static final int DIAS_SERVICE_COMMAND_NULL = 0;
	public static final int DIAS_SERVICE_COMMAND_INIT = 1;
	
	private static ParameterManager parameterManager;
	private boolean validParametersExist = false;
	
	private static ConfigurationManager configurationManager;
	private boolean validConfigurationExists = false;
	
	// Viewpager stuff
	SectionsPagerAdapter mSectionsPagerAdapter;
	ViewPager mViewPager;
	public static final int COLOR_VALID = Color.rgb(0, 200, 0);
	public static final int COLOR_INVALID = Color.rgb(255, 20, 20);
	public static final int COLOR_DEFAULT = Color.BLACK;

    @Override
    public void onStop()
    {
    	final String FUNC_TAG = "onStop";
		super.onStop();
		Debug.i(TAG, FUNC_TAG, "");
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
    	final String FUNC_TAG = "onCreate";
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.super_main);
		
		// Initialization happens in onResume
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.super_menu, menu);
		menu.findItem(R.id.menuLaunch).setVisible(validConfigurationExists && validParametersExist);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		final String FUNC_TAG = "onOptionsItemSelected";
		switch (item.getItemId()) 
		{
			case R.id.menuRefresh:
				validConfigurationExists = configurationManager.checkForValidConfiguration();
				validParametersExist = parameterManager.readParameters();
				
				invalidateOptionsMenu();
				
				int pagerTitleColor = (!configurationManager.configs.isEmpty() && configurationManager.configs.get(0).allPackagesValid) ? COLOR_VALID : COLOR_INVALID;
				((PagerTitleStrip) findViewById(R.id.pager_title_strip)).setBackgroundColor(pagerTitleColor);
				
				int titleColor = validParametersExist ? COLOR_DEFAULT : COLOR_INVALID;
				String text = validParametersExist ? "Install the proper apps to match one of the configurations below" : "Invalid or missing parameters. Check the 'parameters.xml' file";
				((TextView) findViewById(R.id.currentText)).setBackgroundColor(titleColor);
				((TextView) findViewById(R.id.currentText)).setText(text);
				
				mViewPager.setAdapter(mSectionsPagerAdapter);
				return true;
			case R.id.menuLaunch:
				int selectedConfigID = mViewPager.getCurrentItem(); 	//not currently used, but could be useful for specialized actions depending on config
				startDiAsServices();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}	
	
	@Override 
	public boolean onKeyDown(int keyCode, KeyEvent event) 
	{
		boolean retCode;
		switch (keyCode)
		{
			//eat these inputs
			case KeyEvent.KEYCODE_BACK:
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
	public boolean onKeyUp(int keyCode, KeyEvent event) 
    {
		boolean retCode;
		switch (keyCode)
		{
			//eat these inputs
			case KeyEvent.KEYCODE_BACK:
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
    public void onPause()
    {
    	super.onPause();
    }
    
    @Override
    public void onResume()
    {
    	super.onResume();
    	
    	init();
    	
    	mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
		
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);
    }
    
    @Override
    public void onStart()
    {
    	super.onStart();
    }
    
    public void init()
    {
    	final String FUNC_TAG = "init";
    	
    	parameterManager = ParameterManager.getInstance(this);
		validParametersExist = parameterManager.readParameters();
		
        configurationManager = ConfigurationManager.getInstance(this);
        validConfigurationExists = configurationManager.checkForValidConfiguration();
		
        Debug.i(TAG, FUNC_TAG, "CONFIG: "+validConfigurationExists+" PARAMS: "+validParametersExist);
        
        invalidateOptionsMenu();

		if (!configurationManager.configs.isEmpty())
        {
			int pagerTitleColor = configurationManager.configs.get(0).allPackagesValid ? COLOR_VALID : COLOR_INVALID;
			((PagerTitleStrip) findViewById(R.id.pager_title_strip)).setBackgroundColor(pagerTitleColor);
		}

		if (!validParametersExist) {
			int titleColor = COLOR_INVALID;
			((TextView) findViewById(R.id.currentText)).setBackgroundColor(titleColor);
			((TextView) findViewById(R.id.currentText)).setText("Invalid or missing parameters.Check the 'parameters.xml' file.");
		}
		
		if (validConfigurationExists)
        {
			if(validParametersExist)
            {
				startDiAsServices();
			}
			else
            {
				Toast.makeText(this.getBaseContext(), "No parameters in database, system not starting. Check the 'parameters.xml' file.", Toast.LENGTH_LONG).show();
			}
		}
        else
        {
        }
    }
    
	private void startDiAsServices()
    {
    	final String FUNC_TAG = "startDiAsServices";
    	
		Intent startApp;
        
        // Start DiAs system services
        try 
        {			
			// SupervisorService
    		Debug.i(TAG, FUNC_TAG, "Starting supervisorService..."); 
    		startApp = new Intent();
    		startApp.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.Supervisor.SupervisorService");
    		startService(startApp);
			
    		// DiAsService
    		Debug.i(TAG, FUNC_TAG, "Starting DiAsService...");
			startApp = new Intent();
			startApp.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
			startApp.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_NULL);
			startService(startApp);
    		
			// NetworkService
    		Debug.i(TAG, FUNC_TAG, "Starting networkService...");
    		startApp = new Intent("DiAs.NetworkService");
    		startService(startApp);
    		
    		// biometricsCleanerService
    		Debug.i(TAG, FUNC_TAG, "Starting biometricsCleanerService...");
    		startApp = new Intent("DiAs.BiometricsCleanerService");
    		startService(startApp);
    		
    		// ConstraintService
    		Debug.i(TAG, FUNC_TAG, "Starting ConstraintService...");
    		startApp = new Intent("DiAs.ConstraintService");
    		startService(startApp);

			// Start any previously running drivers
			startDrivers();

			// Determine if we need to start DiAs Setup or not
			Cursor subjectInfo = this.getContentResolver().query(Biometrics.SUBJECT_DATA_URI, null, null, null, null);
			if(subjectInfo!=null)
			{
				if(subjectInfo.getCount() > 0)
				{
					Debug.i(TAG, FUNC_TAG, "Subject data exists in the database...");
					
					// DiAsService
		    		Debug.i(TAG, FUNC_TAG, "Starting DiAsService...");
					startApp = new Intent();
					startApp.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
					startApp.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_INIT);
					startService(startApp);
					
					// Start DiAs UI
					startApp = new Intent();
					startApp.setClassName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsUI.DiAsMain");
					startApp.setAction(Intent.ACTION_MAIN);
					startActivity(startApp);
					
					subjectInfo.close();
					return;
				}
			}
			
			Debug.i(TAG, FUNC_TAG, "There is no subject data, so launch DiAs Setup...");

			// DiAs Setup
    		startApp = new Intent();
    		startApp.setComponent(new ComponentName("edu.virginia.dtc.DiAsUI", "edu.virginia.dtc.DiAsSetup.DiAsSetup1"));
    		startActivity(startApp);
    		
    		return;
        }        
        catch (Exception e) 
        {
        	Debug.e(TAG, FUNC_TAG, "ERROR: "+e.getMessage());
        	e.printStackTrace();
        }
	}
	
	public void startDrivers()
	{
		final String FUNC_TAG = "startDrivers";
		
		Debug.i(TAG, FUNC_TAG, "Starting drivers...");
		
		Cursor c = getContentResolver().query(Biometrics.HARDWARE_CONFIGURATION_URI, null, null, null, null);
		if(c!=null)
		{
			if(c.moveToFirst())
			{
				String pump = c.getString(c.getColumnIndex("running_pump"));
				String cgm = c.getString(c.getColumnIndex("running_cgm"));
				String misc = c.getString(c.getColumnIndex("running_misc"));
				
				Intent driver = new Intent();
				if(pump != null && !pump.equalsIgnoreCase(""))
				{
					Debug.w(TAG, FUNC_TAG, "Previously running pump was found: "+pump);
					String p_pump = pump.substring(0, pump.lastIndexOf('.'));
					Debug.w(TAG, FUNC_TAG, "Pump Package name: "+p_pump);
					
					driver.setClassName(p_pump, pump);
					driver.putExtra("auto", true);
					this.startService(driver);
				}
				else
					Debug.i(TAG, FUNC_TAG, "No previously running Pump!");
				
				if(cgm != null && !cgm.equalsIgnoreCase(""))
				{
					Debug.w(TAG, FUNC_TAG, "Previously running CGM was found:  "+cgm);
					String p_cgm = cgm.substring(0, cgm.lastIndexOf('.'));
					Debug.w(TAG, FUNC_TAG, "CGM Package name: "+p_cgm);
					
					driver.setClassName(p_cgm, cgm);
					driver.putExtra("auto", true);
					this.startService(driver);
				}
				else
					Debug.i(TAG, FUNC_TAG, "No previously running CGM!");
				
				if(misc != null && !misc.equalsIgnoreCase(""))
				{
					Debug.w(TAG, FUNC_TAG, "Previously running Misc. Driver was found:  "+misc);
					String p_misc = misc.substring(0, misc.lastIndexOf('.'));
					Debug.w(TAG, FUNC_TAG, "Misc Package name: "+p_misc);
					
					driver.setClassName(p_misc, misc);
					driver.putExtra("auto", true);
					this.startService(driver);
				}
				else
					Debug.i(TAG, FUNC_TAG, "No previously running Misc. Driver!");
			}
		}
		else
			Debug.i(TAG, FUNC_TAG, "Cursor is null!");
		
		c.close();
	}
	
	public class SectionsPagerAdapter extends FragmentPagerAdapter
    {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			Fragment fragment = new ConfigurationFragment();
			Bundle args = new Bundle();
			args.putInt(ConfigurationFragment.CONFIG_ID, position);
			fragment.setArguments(args);
			return fragment;
		}

		@Override
		public int getCount() {
			return configurationManager.configs.size();
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return configurationManager.configs.get(position).name;
		}
	}

	public static class ConfigurationFragment extends Fragment
    {
		public static final String TAG = "ConfigurationFragment";
		
		public static final String CONFIG_ID = "config_id";
		public int configID;

		public ConfigurationFragment()
        {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    	final String FUNC_TAG = "onCreateView";
	    	
			View rootView = inflater.inflate(R.layout.super_frag_config, container, false);
			Context context = inflater.getContext();
			configID = getArguments().getInt(CONFIG_ID);
			
			Config config = configurationManager.configs.get(configID);
            Debug.i(TAG, FUNC_TAG, "Setting up new config tab with id=" + configID);
		    
            LinearLayout requiredList = (LinearLayout) rootView.findViewById(R.id.requiredAppsViews);
            LinearLayout currentList = (LinearLayout) rootView.findViewById(R.id.currentAppsView);
            
            for	(int i = 0; i < config.packages.size(); i++){
            	Pack pack = config.packages.get(i);
            	InstalledApp app = config.correspondingInstalledApps.get(i);
            	
            	LinearLayout newView = new LinearLayout(context);
            	newView.setOrientation(LinearLayout.VERTICAL);
            	
            	TextView packageNameView = new TextView(context);
            	packageNameView.setText(pack.name);
            	packageNameView.setTextColor(Color.WHITE);
            	TextView versionStringView = new TextView(context);
            	versionStringView.setText(pack.minVersion + ((pack.maxVersion != Integer.MAX_VALUE) ? " - " + pack.maxVersion : " or higher"));
            	versionStringView.setTextColor(Color.WHITE);
            	
            	newView.addView(packageNameView);
            	newView.addView(versionStringView);
            	requiredList.addView(newView);

            	newView = new LinearLayout(context);
            	newView.setOrientation(LinearLayout.VERTICAL);
            	
            	int installedPackageColor = (app.found && app.validVersion) ? COLOR_VALID : COLOR_INVALID;
            	packageNameView = new TextView(context);
            	packageNameView.setText(pack.name);
            	packageNameView.setTextColor(installedPackageColor);
            	versionStringView = new TextView(context);
            	versionStringView.setText((app.found) ? app.versionString : "MISSING");
            	versionStringView.setTextColor(installedPackageColor);
            	
            	newView.addView(packageNameView);
            	newView.addView(versionStringView);
            	currentList.addView(newView);
            }
			
			return rootView;
		}
		
		@Override
		public void setUserVisibleHint(boolean isVisibleToUser) {
		    super.setUserVisibleHint(isVisibleToUser);
		    
			if (isVisibleToUser) {
				int pagerTitleColor = configurationManager.configs.get(configID).allPackagesValid ? COLOR_VALID : COLOR_INVALID;
				((PagerTitleStrip) this.getActivity().findViewById(R.id.pager_title_strip)).setBackgroundColor(pagerTitleColor);
		    }
		}
	}
}