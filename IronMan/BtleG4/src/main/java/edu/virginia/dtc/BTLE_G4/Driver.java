package edu.virginia.dtc.BTLE_G4;

public class Driver {
	
	// Required static information belonging to the driver, DRIVER_NAME must match meta-data string
	public static final String DRIVER_NAME = "BTLE_G4";
	public static final String DEVICE_CXN = "BTLE";
	
	// Intents for binding connections, PUMP_INTENT must be the same here as it is in the manifest
	public static final String CGM_INTENT = "Driver.Cgm." + DRIVER_NAME;
	public static final String UI_INTENT = "Driver.UI." + DRIVER_NAME;

	// All the USB variables stored here for access by UI and driver
	public int my_state_index;
	public Device cgm;
	
	public boolean registered = false, connected = false, progress = false, lowPower = false;
	public String status = "";
	public int battery = -1;
	public String deviceMac = "";
	
	private static Driver instance = null;
	
	protected Driver()
	{
		cgm = new Device(0);
		cgm.data_index = 0;
	}
	
	public static Driver getInstance()
	{
		if (instance == null)
			instance = new Driver();
		return instance;
	}
}
