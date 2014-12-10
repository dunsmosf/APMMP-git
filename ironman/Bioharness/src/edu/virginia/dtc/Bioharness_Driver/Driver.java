package edu.virginia.dtc.Bioharness_Driver;

import java.util.ArrayList;
import java.util.List;

public class Driver {
	
	// Required static information belonging to the driver, DRIVER_NAME must match meta-data string
	public static final String DRIVER_NAME = "Bioharness_Driver";
	public static final String DEVICE_CXN = "Bluetooth";
	
	// Intents for binding connections, PUMP_INTENT must be the same here as it is in the manifest
	public static final String DRIVER_INTENT = "Driver.BH." + DRIVER_NAME;
	public static final String UI_INTENT = "Driver.UI." + DRIVER_NAME;
	
	public int my_state_index;
	public Device zephyr;
	
	
	private static Driver instance = null;
	
	protected Driver()
	{
		zephyr = new Device(0);
	}
	
	public static Driver getInstance()
	{
		if(instance == null)
			instance = new Driver();
		return instance;
	}
}
