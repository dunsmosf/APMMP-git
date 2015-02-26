package edu.virginia.dtc.DiAsSetup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.virginia.dtc.SysMan.Debug;

import android.content.SharedPreferences;
import android.widget.ArrayAdapter;

// Singleton class used to store information about available drivers
// as well as connected drivers.  It primarily serves as an interface
// to the interfaces of proprietary devices drivers.
public class DriverData {

	private static final String TAG = "DiAsDriversData";

	// Bool for passing to other applications
	public boolean realTime;
	public int speedupMultiplier;

	public List<DriverInfo> availableDrivers;
	public Set<String> connectedDriverNames;
	public SharedPreferences prefs;

	protected DriverData() {
		realTime = true;
		speedupMultiplier = 1;

		availableDrivers = new ArrayList<DriverInfo>();
	}

	public void addDriver(DiAsSetup main, ArrayAdapter<String> a, List<DriverInfo> l, DriverInfo d) {
		l.add(d);

		if (d.type == DriverInfo.CGM)
			a.add(d.displayname + "\nCGM");
		else if (d.type == DriverInfo.PUMP)
			a.add(d.displayname + "\nPump");
		else if (d.type == DriverInfo.CGM_PUMP)
			a.add(d.displayname + "\nCGM and Pump");
		else
			a.add(d.displayname + "\nMisc");
	}

	public void updateDrivers(DiAsSetup main){
		prefs = main.getSharedPreferences(DiAsSetup.PREFS_NAME, 0);
		connectedDriverNames = prefs.getStringSet("connectedNames", new HashSet<String>());
	}
	
	public DriverInfo getByName(String name)
	{
		final String FUNC_TAG = "getByName";
		
		for(DriverInfo d : availableDrivers)
		{
			Debug.i(TAG, FUNC_TAG, d.name + " Test name: "+name);
			if(d.name.equalsIgnoreCase(name))
			{
				Debug.i(TAG, FUNC_TAG, "Found!");
				return d;
			}
		}
		return null;
	}

	public boolean exists(DriverInfo d) {
		for (DriverInfo driver : availableDrivers)
			if (driver.name.equals(d.name) && driver.type == d.type)
				return true;
		return false;
	}
	
	public List<DriverInfo> getConnectedDrivers(){
		ArrayList<DriverInfo> list = new ArrayList<DriverInfo>();
		for (DriverInfo d : availableDrivers){
			if (connectedDriverNames.contains(d.name))
				list.add(d);
		}
		return list;
	}

	private static DriverData instance = null;

	// Singleton declaration
	public static DriverData getInstance() {
		if (instance == null) {
			instance = new DriverData();
		}
		return instance;
	}

	public void clear() {
		availableDrivers.clear();
	}
}
