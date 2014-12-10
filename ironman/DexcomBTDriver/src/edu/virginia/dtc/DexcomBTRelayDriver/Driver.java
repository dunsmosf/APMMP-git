package edu.virginia.dtc.DexcomBTRelayDriver;

import java.util.ArrayList;
import java.util.List;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

public class Driver {
	
	// Required static information belonging to the driver, DRIVER_NAME must match meta-data string
	public static final String DRIVER_NAME = "DexcomBT";
	public static final String DEVICE_CXN = "BT";
	
	// Intents for binding connections, PUMP_INTENT must be the same here as it is in the manifest
	public static final String PUMP_INTENT = "Driver.Pump." + DRIVER_NAME;
	public static final String CGM_INTENT = "Driver.Cgm." + DRIVER_NAME;
	public static final String UI_INTENT = "Driver.UI." + DRIVER_NAME;
	
	public int my_state_index;
	public List<Device> cgms;
	
	private static Driver instance = null;
	
	protected Driver()
	{
		cgms = new ArrayList<Device>();
	}
	
	public int getDeviceArrayIndex(int dev)
	{
		for(Device d:cgms)
		{
			if(d.my_dev_index == dev)
				return cgms.indexOf(d);
		}
		return -1;
	}
	
	public static Driver getInstance()
	{
		if (instance == null)
			instance = new Driver();
		return instance;
	}
}
