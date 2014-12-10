package edu.virginia.dtc.RocheDriver;

import java.util.Timer;
import java.util.TimerTask;

import edu.virginia.dtc.SysMan.Debug;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

public class RocheUI extends FragmentActivity implements ActionBar.TabListener {
	private static final String TAG = "RocheUI";
	
	// Messages from Driver
	private static final int DRIVER2UI_NULL = 0;
	private static final int DRIVER2UI_DEV_STATUS = 1;
  	
  	// Pump Service OnStartCommand messages
  	public static final int PUMP_SERVICE_CMD_NULL = 0;
 	public static final int PUMP_SERVICE_CMD_INIT = 9;
 	public static final int PUMP_SERVICE_CMD_DISCONNECT = 10;
	
	private ServiceConnection UItoDriver = null;
	
	private final Messenger messengerFromDriver = new Messenger(new incomingDriverHandler());
	private Messenger toDriver = null;
	
	Driver drv = Driver.getInstance();
    
	public static int pageCount = 1;
	
    SectionsPagerAdapter mSectionsPagerAdapter;
    ViewPager mViewPager;
    
    public boolean selectedFromPager = false;
    
    static DiscoveryFragment discoveryFragment;
    static SetupFragment setupFragment;
    static RTFragment rtFragment;
    static LogFragment logFragment;
    
    private Handler handler = new Handler();
    private Timer updateTimer;
    private Runnable update;
    
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		setResult(Activity.RESULT_CANCELED);
		
		Debug.i(TAG, FUNC_TAG,"OnCreate");
		
		drv.main = RocheUI.this;
		drv.ui = this;
        
		drv.histList = new ArrayAdapter<String>(this, R.layout.list_name);
		
		// Setup the pager and fragments 
		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

		final ActionBar actionBar = this.getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setDisplayShowHomeEnabled(false);

		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);

		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				int tabPosition = 0;
				switch (position) 
				{
					case 0:
						tabPosition = 0;
						break;
					case 1:
						tabPosition = 1;
						break;
					case 2:
						tabPosition = 2;
						break;
					case 3:
						tabPosition = 3;
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
//		actionBar.addTab(actionBar.newTab().setText(mSectionsPagerAdapter.getPageTitle(2)).setTabListener(this));
//		actionBar.addTab(actionBar.newTab().setText(mSectionsPagerAdapter.getPageTitle(3)).setTabListener(this));
		
		// Initialize fragments
		//setupFragment = new SetupFragment(this);
		//rtFragment = new RTFragment(this);
		logFragment = new LogFragment();
		discoveryFragment = new DiscoveryFragment();
		
		update = new Runnable()
		{
			public void run()
			{
				updateUI();
			}
		};
		
		updateTimer = new Timer();
		TimerTask ui = new TimerTask()
		{
			public void run()
			{
				handler.postDelayed(update, 0);
			}
		};
		
		updateTimer.scheduleAtFixedRate(ui, 0, 500);
		
	}
	
	@Override
	public void onStart()
	{
		final String FUNC_TAG = "onStart";

		Debug.i(TAG, FUNC_TAG, "OnStart");
		super.onStart();
	}
	
	@Override
	public void onStop()
	{
		final String FUNC_TAG = "onStop";

		Debug.i(TAG, FUNC_TAG, "OnStop");
		super.onStop();
	}
	
	@Override
	public void onDestroy()
	{
		final String FUNC_TAG = "onDestroy";

		Debug.i(TAG, FUNC_TAG, "OnDestroy");
		super.onDestroy();
		
		// Unbind connection to driver
		if(UItoDriver!=null)
		{
			Debug.i(TAG, FUNC_TAG,"unbinding service...");
			unbindService(UItoDriver);
		}
		
		handler.removeCallbacks(update);
		updateTimer.cancel();
	}

	/******************************************************************************
	 * Driver Message Handler
	 ******************************************************************************/
	class incomingDriverHandler extends Handler {
		@Override
		public void handleMessage(Message msg) 
		{
			switch(msg.what)
			{
				case DRIVER2UI_NULL:
					break;
				case DRIVER2UI_DEV_STATUS:
					updateUI();
					break;
			}
		}
	}
	
	public void sendDriverMessage(Bundle data, int what)
	{
		sendDataMessage(toDriver, data,  what);
	}
	
	private void sendDataMessage(Messenger messenger, Bundle bundle, int what)
	{
		final String FUNC_TAG = "sendDataMessage";

		if(messenger != null)
		{
			Message msg = Message.obtain(null, what);
			msg.setData(bundle);
			
			try{
				messenger.send(msg);
			}
			catch(RemoteException e) {
				e.printStackTrace();
			}
		}
		else
			Debug.i(TAG, FUNC_TAG, "Messenger is not connected or is null!");
	}
	
	/******************************************************************************
	 * Helper/Update Functions
	 ******************************************************************************/
	
	public void updateUI()
	{
		//Debug.i(TAG, FUNC_TAG,"Updating UI!");
		
		switch (mViewPager.getCurrentItem()) {
			
			case 0:
				discoveryFragment.updateUI();
				break;
			case 1:
				logFragment.updateUI();
				break;
		}
	}

	/******************************************************************************
	 * Fragment and Tabs Functions and Classes
	 ******************************************************************************/
	
	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int i) {
			Fragment fragment = null;
			switch (i) 
			{
				case 0:
					fragment = discoveryFragment;
					break;
				case 1:
					fragment = logFragment;
					break;
			}
			return fragment;
		}

		@Override
		public int getCount() {
			//TODO:  Change this to the number of pages if you want to unlock the other modes
			return 2;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) 
			{
				case 0:
					return getString(R.string.titleDiscovery).toUpperCase();
				case 1:
					return getString(R.string.titleLog).toUpperCase();
			}
			return null;
		}
	}
	
	/******************************************************************************
	 * Context Menu Functions
	 ******************************************************************************/	
	
	@Override
    public boolean onPrepareOptionsMenu(Menu menu) {
		final String FUNC_TAG = "onPrepareOptionsMenu";

    	menu.clear();
        MenuInflater inflater = getMenuInflater();

		Debug.i(TAG, FUNC_TAG,"Device Config Menu");
		
        return true;
    }
		
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        updateUI();
        return true;
    }


	public void onTabReselected(Tab tab, android.app.FragmentTransaction ft) {
		updateUI();
	}

	public void onTabSelected(Tab tab, android.app.FragmentTransaction ft) {
		// When the given tab is selected, switch to the corresponding page in the ViewPager.
		int tabPosition = tab.getPosition();
		int pagerPosition = 0;
		switch (tabPosition) 
		{
			case 0:
				pagerPosition = 0;
				break;
			case 1:
				pagerPosition = 1;
				break;
			case 2:
				pagerPosition = 2;
				break;
		}
		
		if (!selectedFromPager)
			mViewPager.setCurrentItem(pagerPosition);
		
	}

	public void onTabUnselected(Tab tab, android.app.FragmentTransaction ft) {
	}
}
