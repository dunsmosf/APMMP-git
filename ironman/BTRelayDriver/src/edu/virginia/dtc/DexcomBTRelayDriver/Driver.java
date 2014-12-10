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
	public static final String DRIVER_NAME = "DexcomBTRelay";
	public static final String DEVICE_CXN = "USB";
	
	// Intents for binding connections, PUMP_INTENT must be the same here as it is in the manifest
	public static final String PUMP_INTENT = "Driver.Pump." + DRIVER_NAME;
	public static final String CGM_INTENT = "Driver.Cgm." + DRIVER_NAME;
	public static final String UI_INTENT = "Driver.UI." + DRIVER_NAME;

	// All the USB variables stored here for access by UI and driver
	public UsbManager manager;
	public UsbDevice dexcom;
	
	public boolean usbConnected;
	public boolean cdcFound;
	public byte endpointsFound;
	
	Device cgm = null;
	
	private static Driver instance = null;
	
	protected Driver()
	{
		cgm = new Device(0);
	}
	
	public static Driver getInstance()
	{
		if (instance == null)
			instance = new Driver();
		return instance;
	}

	public void resetUsb() {
		usbConnected = false;
		cdcFound = false;
		endpointsFound = 0;
	}
	
}
