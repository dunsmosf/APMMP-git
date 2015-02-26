package edu.virginia.dtc.DiAsSetup;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.Toast;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.DiAsSubjectData;

public class ParametersActivity extends FragmentActivity implements ActionBar.TabListener {
	
	private final String TAG = "ParametersActivity";
	
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
	
	Context currentContext;
	public boolean selectedFromPager = false;
	
	Fragment currentFragment;
	ConfFragment confFragment;
	ParamsFragment paramsFragment;
	
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		final String FUNC_TAG = "onCreate";
		
		super.onCreate(savedInstanceState);
		Debug.i(TAG, FUNC_TAG, "");
		setContentView(R.layout.params_act);
		
		currentContext = this;
		
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
						currentFragment = confFragment;
						tabPosition = 0;
						break;
					case 1:
						currentFragment = paramsFragment;
						tabPosition = 1;
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

				
		confFragment = ConfFragment.getInstance(this);
		paramsFragment = ParamsFragment.getInstance(this);
		
		int screen = this.getIntent().getIntExtra("setupScreenIDNumber", 0);
		Debug.i(TAG, FUNC_TAG, "Screen = " + screen);
		mViewPager.setCurrentItem(screen);
		
	}
	
	 @Override
    public void onStart() 
    {
    	super.onStart();
    	Debug.i(TAG, "onStart", "");
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
	}
	
	@Override
	public void onDestroy() 
	{
		super.onDestroy();
		Debug.i(TAG, "onDestroy", "");
	}
	
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
		// TODO Auto-generated method stub
		
	}

	public void onTabSelected(Tab tab, FragmentTransaction ft) {
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
		}
		if (!selectedFromPager)
			mViewPager.setCurrentItem(pagerPosition);
		
	}

	public void onTabUnselected(Tab tab, FragmentTransaction ft) {
		// TODO Auto-generated method stub
		
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
				fragment = confFragment;
				break;
			case 1:
				fragment = paramsFragment;
				break;
			}
			return fragment;
		}

		@Override
		public int getCount() {
			return 2;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
			case 0:
				return getString(R.string.title_conf).toUpperCase();
			case 1:
				return getString(R.string.title_params).toUpperCase();
			}
			return null;
		}
	}
	
	public void doneClick(View view) 
	{
		final String FUNC_TAG = "finishClick";
		Debug.i(TAG, FUNC_TAG, "Done");
		finish();
	}

}
