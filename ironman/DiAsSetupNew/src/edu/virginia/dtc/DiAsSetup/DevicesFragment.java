package edu.virginia.dtc.DiAsSetup;

import java.util.HashMap;
import java.util.List;

import edu.virginia.dtc.SysMan.Debug;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class DevicesFragment extends Fragment {
	public static String TAG = "DevicesFragment";
	public static View view;
	public DiAsSetup1 main;

	private static SwitchAdapter driverAdapter;

	// Manager to close apps
	private ActivityManager am;

	// ListView layouts
	private ListView driverList;

	// Device variables
	public DriverData hardware;
	public DriverInfo activeDevice;
	
	public BroadcastReceiver resultReceiver;

	public DevicesFragment(final DiAsSetup1 main) {
		final String FUNC_TAG = "DevicesFragment";
		
		this.main = main;

		hardware = DriverData.getInstance();
		
		resultReceiver = new BroadcastReceiver(){
			final String FUNC_TAG = "resultReceiver";
			
			public void onReceive(Context context, Intent intent) {
				String device = intent.getStringExtra("name");
				int cgms = intent.getIntExtra("cgms", -1);
				int pumps = intent.getIntExtra("pumps", -1);

				DriverInfo targetDevice = hardware.getByName(device);
				
				Debug.i(TAG, FUNC_TAG, "Target: "+device+" CGM: "+cgms+" PUMP: "+pumps);
				
				if(targetDevice != null)
				{
					driverAdapter.setCheckBoxes(targetDevice, cgms > 0, pumps > 0);
					driverAdapter.notifyDataSetChanged();
				}
				else
					Debug.i(TAG, FUNC_TAG,"Target device is null");
			}			
		};
		main.registerReceiver(resultReceiver, new IntentFilter("edu.virginia.dtc.DEVICE_RESULT"));

		SharedPreferences prefs = main.getSharedPreferences(DiAsSetup1.PREFS_NAME, 0);
		hardware.realTime = prefs.getBoolean("realtime", true);
		hardware.speedupMultiplier = prefs.getInt("speedupMultiplier", 1);
		driverAdapter = new SwitchAdapter(main, R.layout.listsettings);		
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		am = (ActivityManager) main.getSystemService(Context.ACTIVITY_SERVICE);
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final String FUNC_TAG = "onCreateView";
		
		view = inflater.inflate(R.layout.devices, container, false);

		driverList = (ListView) view.findViewById(R.id.driverList);
		driverList.setAdapter(driverAdapter);
		
		Debug.i(TAG, FUNC_TAG,"Updating drivers...");
		Intent update = new Intent("edu.virginia.dtc.DRIVER_UPDATE");
		main.sendBroadcast(update);
		
		return view;
	}

	public void startDriver(int position, boolean auto) {
		final String FUNC_TAG = "startDriver";
		
		DriverInfo d = hardware.availableDrivers.get(position);
		Debug.i(TAG, FUNC_TAG,"Starting: " + d.package_name);

		Intent intent = new Intent();
		
		if(!auto)
		{
			Debug.i(TAG, FUNC_TAG, "Starting service for driver!");
			
			intent.setClassName(d.package_name, d.service_name);
			main.startService(intent);			
		}

		activeDevice = d;
	}

	public void stopDriver(int position) {
		final String FUNC_TAG = "stopDriver";
		
		DriverInfo d = hardware.availableDrivers.get(position);
		Debug.i(TAG, FUNC_TAG,"Stopping Driver - " + d.package_name);

		Intent finishDriver = new Intent("edu.virginia.dtc.STOP_DRIVER");
		finishDriver.putExtra("package", d.package_name);
		main.sendBroadcast(finishDriver);

		//hardware.stopDriver(d);
		driverAdapter.notifyDataSetChanged();
	}

	public void updateDisplay() {
		Debug.i(TAG, "updateDisplay", "Checking for Drivers!");
		checkForDrivers();
	}

	public void onStart() {
		final String FUNC_TAG = "onStart";
		
		super.onStart();
		Debug.i(TAG, FUNC_TAG,"");
		checkForDrivers();
	}
	
	public void onDestry(){
		super.onDestroy();
		main.unregisterReceiver(resultReceiver);
	}

	public Boolean checkForDrivers() {
		final String FUNC_TAG = "checkForDrivers";
		
		Debug.i(TAG, FUNC_TAG,"Checking for device drivers...");
		hardware.updateDrivers(main);
		// Re-populate arrayLists since they get created each time
		if (hardware.availableDrivers.size() != driverAdapter.getCount()) {
			driverAdapter.clear();
			Debug.i(TAG, FUNC_TAG,"Repopulating available adapter list...");
			for (DriverInfo d : hardware.availableDrivers) {
				if (d.type == DriverInfo.CGM)
					driverAdapter.add(d.displayname + "\nCGM");
				else if (d.type == DriverInfo.PUMP)
					driverAdapter.add(d.displayname + "\nPump");
				else if (d.type == DriverInfo.CGM_PUMP)
					driverAdapter.add(d.displayname + "\nCGM and Pump");
				else
					driverAdapter.add(d.displayname + "\nMisc");
			}
		}
		driverAdapter.notifyDataSetChanged();

		// Check system for installed device drivers
		final PackageManager pm = main.getPackageManager();
		List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

		for (ApplicationInfo app : packages) {
			if (app.metaData != null) {
				Bundle meta = app.metaData;

				// Meta-data must contain these keys
				if (meta.containsKey("driver_name") && meta.containsKey("driver_cgm") && meta.containsKey("driver_pump") && meta.containsKey("driver_service")) {
					boolean cgm = meta.getBoolean("driver_cgm");
					boolean pump = meta.getBoolean("driver_pump");
					String name = meta.getString("driver_name");
					String displayname = meta.getString("driver_displayname", "");
					String serv = meta.getString("driver_service");
					boolean speedup = meta.getBoolean("supports_speedup");
					boolean multi = meta.getBoolean("supports_multi");

					Debug.i(TAG, FUNC_TAG,"Found " + name + " driver in " + app.packageName);
					Debug.i(TAG, FUNC_TAG,"Service: "+serv);
					
					//If there is no listed display name then just call it name
					if(displayname.equalsIgnoreCase(""))	
						displayname = name;

					if (cgm && pump) {
						DriverInfo d = new DriverInfo(name, displayname, app.packageName, serv, speedup, multi, DriverInfo.CGM_PUMP);
						if (!hardware.exists(d)) {
							hardware.addDriver(main, driverAdapter, hardware.availableDrivers, d);
						}
					} else if (cgm) {
						DriverInfo d = new DriverInfo(name, displayname, app.packageName, serv, speedup, multi, DriverInfo.CGM);
						if (!hardware.exists(d)) {
							hardware.addDriver(main, driverAdapter, hardware.availableDrivers, d);
						}
					} else if (pump) {
						DriverInfo d = new DriverInfo(name, displayname, app.packageName, serv, speedup, multi, DriverInfo.PUMP);
						if (!hardware.exists(d)) {
							hardware.addDriver(main, driverAdapter, hardware.availableDrivers, d);
						}
					}
					else {
						DriverInfo d = new DriverInfo(name, displayname, app.packageName, serv, speedup, multi, DriverInfo.MISC);
						if (!hardware.exists(d)) {
							hardware.addDriver(main, driverAdapter, hardware.availableDrivers, d);
						}
					}
				}
			}
		}
		return true;
	}

	private class SwitchAdapter extends ArrayAdapter<String> {
		HashMap<String, boolean[]> checkBoxes;

		public SwitchAdapter(Context context, int textViewResourceId) {
			super(context, textViewResourceId);
			checkBoxes = new HashMap<String, boolean[]>();
		}

		public View getView(final int position, View convertView, ViewGroup parent) {
			final String FUNC_TAG = "getView";
			
			if (convertView == null) {
				convertView = main.getLayoutInflater().inflate(R.layout.deviceitemlist, null);
			}
			final View v = convertView;
			String name = getItem(position).split("\\n")[0];
			String type = getItem(position).split("\\n")[1];
			
			Debug.i(TAG, FUNC_TAG,"NAME: "+name+" TYPE:"+type);
			
			((TextView) v.findViewById(R.id.drivername)).setText(name);
			((TextView) v.findViewById(R.id.drivertype)).setText(type);
			
			((Button) v.findViewById(R.id.driverSwitch)).setOnClickListener(new OnClickListener() {

				public void onClick(View v) {
					Debug.i(TAG, FUNC_TAG, hardware.availableDrivers.get(position).package_name);
					startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+hardware.availableDrivers.get(position).package_name)));
				}
			});
			
			((LinearLayout) v.findViewById(R.id.driverClickable)).setOnClickListener(new OnClickListener() {				//Label click, we want to open the UI here
				public void onClick(View v) {
					if (driverAdapter.getCount() > 0) {
						startDriver(position, false);
					}
				}
			});
			((LinearLayout) v.findViewById(R.id.driverCGMLayout)).setVisibility((type.contains("CGM")) ? CheckBox.VISIBLE : CheckBox.INVISIBLE);
			((LinearLayout) v.findViewById(R.id.driverPumpLayout)).setVisibility((type.contains("Pump")) ? CheckBox.VISIBLE : CheckBox.INVISIBLE);
			boolean[] checks = { false, false };
			if (checkBoxes.containsKey(hardware.availableDrivers.get(position).package_name))
				checks = checkBoxes.get(hardware.availableDrivers.get(position).package_name);
			((CheckBox) v.findViewById(R.id.driverCGMBox)).setChecked(checks[0]);
			((CheckBox) v.findViewById(R.id.driverPumpBox)).setChecked(checks[1]);
			return v;
		}

		public void setCheckBoxes(DriverInfo info, boolean cgmChecked, boolean pumpChecked) {
			checkBoxes.put(info.package_name, new boolean[] { cgmChecked, pumpChecked });
		}
	}
}
