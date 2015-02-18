package edu.virginia.dtc.RocheDriver;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.bluetooth.BluetoothAdapter;
import android.net.wifi.WifiManager;

public class InterfaceData {

private static InterfaceData instance = null;

	private static final String TAG = "InterfaceData";
	
	//This UUID was gathered from the SDP fetching process (It's the UUID for SPP)
	public static final String PUMP_UUID = "00001101-0000-1000-8000-00805f9b34fb";

	public int commType = -1;
	public static final int NONE = 0;
	public static final int BLUETOOTH = 1;
	public static final int WIFI = 2;

	public BluetoothAdapter bt;
	
	public static BluetoothConn remotePumpBt = null;
	public static Queue<byte[]> pumpMessages;
	public static Lock pumpLock;
	
	public String btString;
	
	protected InterfaceData()
	{			
		pumpLock = new ReentrantLock();
		pumpMessages = new ConcurrentLinkedQueue<byte[]>();
	}
	
	public static InterfaceData getInstance()
	{
		if(instance == null)
		{
			synchronized(InterfaceData.class)
			{
				if(instance == null)
					instance = new InterfaceData();
			}
		}
		return instance;
	}
}
