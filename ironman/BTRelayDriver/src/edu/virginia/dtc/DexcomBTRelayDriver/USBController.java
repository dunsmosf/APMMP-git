package edu.virginia.dtc.DexcomBTRelayDriver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import edu.virginia.dtc.SysMan.Debug;


public class USBController {

	public static final String UNBOUND = "unbound";
	public static final String NOT_ATTACHED = "0000";
	public static final String WALL_CHARGER = "0040";
	public static final String ACCESSORY = "0004";
	public static final String OTG = "0080";

	private static final String DEVICE_FILE = "/sys/devices/platform/omap/omap_i2c.4/i2c-4/4-0025/device_type";

	private static final String BIND_COMMAND = "echo -n \"4-0025\" > " + "/sys/bus/i2c/drivers/fsa9480/bind";
	private static final String UNBIND_COMMAND = "echo -n \"4-0025\" > " + "/sys/bus/i2c/drivers/fsa9480/unbind";

	private static final String TAG = "USBController";

	public static String getStatus() {
		String retVal = null;
		File f = new File(DEVICE_FILE);
		if (!f.exists()) {
			retVal = UNBOUND;
		} else {
			try {
				BufferedReader br = new BufferedReader(new FileReader(f));
				retVal = br.readLine();
				br.close();
			} catch (IOException e) {
				retVal = UNBOUND;
			}
		}

		return retVal;
	}

	private static void runCommand(String command) {		
		final String FUNC_TAG = "runCommand";

		try {
			Process process = Runtime.getRuntime().exec("su");
			DataOutputStream os = new DataOutputStream(process.getOutputStream());
			os.writeBytes(command + "\n");
			os.flush();
			os.writeBytes("exit \n");
			os.flush();
			os.close();
			process.waitFor();
		} catch (IOException e) {
			Debug.e(TAG, FUNC_TAG, e.getMessage());
		} catch (InterruptedException e) {
			Debug.e(TAG, FUNC_TAG, e.getMessage());
		}
	}

	public static void turnOnUSB() {
		turnOnUSB(false);
	}

	public static void turnOnUSB(boolean longwait) {		
		final String FUNC_TAG = "turnOnUSB";

		if (getStatus().equalsIgnoreCase(UNBOUND)) {
			runCommand(BIND_COMMAND);
			try {
				if (longwait == true)
					Thread.sleep(20000);
				else
					Thread.sleep(5000);
			} catch (Exception e) {
			}
			Debug.i(TAG, FUNC_TAG, "turned USB on, status now " + getStatus());
		}
	}

	public static void turnOffUSB() {
		turnOffUSB(false);
	}

	public static void turnOffUSB(boolean longwait) {		
		final String FUNC_TAG = "turnOffUSB";

		if (!getStatus().equalsIgnoreCase(WALL_CHARGER) && !getStatus().equalsIgnoreCase(ACCESSORY)) {
			runCommand(UNBIND_COMMAND);
			try {
				if (longwait == true)
					Thread.sleep(10000);
				else
					Thread.sleep(3500);
			} catch (Exception e) {
			}
			Debug.i(TAG, FUNC_TAG, "turned USB off, status now " + getStatus());
		}
	}

	// sometimes the Dex/USB intersection gets jammed up. Resetting the driver
	// seems to help.
	public static void resetUSB() {
		resetUSB(false);
	}

	public static void resetUSB(boolean longwait) {
		turnOffUSB(longwait);
		turnOnUSB(longwait);
	}

}
