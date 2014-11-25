package edu.virginia.dtc.standaloneDriver;

import java.util.ArrayList;
import java.util.List;

import edu.virginia.dtc.SysMan.Biometrics;

import android.content.ContentValues;
import android.content.Context;
import android.widget.ArrayAdapter;

public class Driver {
	
	// Required static information belonging to the driver, DRIVER_NAME must match meta-data string
	public static final String DRIVER_NAME = "Standalone";
	public static final String DEVICE_CXN = "Bluetooth";
	
	// Intents for binding connections, PUMP_INTENT must be the same here as it is in the manifest
	public static final String PUMP_INTENT = "Driver.Pump." + DRIVER_NAME;
	public static final String CGM_INTENT = "Driver.Cgm." + DRIVER_NAME;
	public static final String UI_INTENT = "Driver.UI." + DRIVER_NAME;
	
	public Device pump;
	
	public ArrayAdapter<String> pumpList;
	
	public Context context;
	
	boolean antenna = true;
	
	private static Driver instance = null;
	
	protected Driver()
	{
		pump = new Device();
		antenna = true;
	}
	
	public static Driver getInstance()
	{
		if(instance == null)
			instance = new Driver();
		return instance;
	}
	
	public void updatePumpState(int state)
	{
		Driver.getInstance().pump.state = state;
		
		ContentValues pv = new ContentValues();
		pv.put("state", state);
		
		context.getContentResolver().update(Biometrics.PUMP_DETAILS_URI, pv, null, null);
	}
}
