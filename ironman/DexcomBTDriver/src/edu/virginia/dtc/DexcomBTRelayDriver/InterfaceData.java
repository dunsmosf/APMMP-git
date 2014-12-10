package edu.virginia.dtc.DexcomBTRelayDriver;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import edu.virginia.dtc.SysMan.Debug;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.WifiManager;

public class InterfaceData {

private static InterfaceData instance = null;

	private static final String TAG = "InterfaceData";
	private static final int TIMEOUT = 2;
	
	public static final String PUMP_UUID = "fa87c0d0-afac-11de-8a39-0800200c9a66";
	public static final String CGM_UUID = "fa87c0d0-afac-11de-8a39-0800200c9a67";

	public String commMethod;
	public String commDescrip;

	public int commType = -1;
	public static final int NONE = 0;
	public static final int BLUETOOTH = 1;
	public static final int WIFI = 2;

	public WifiManager wifiMan;
	public BluetoothAdapter bt;
	
	public static BluetoothConn remotePumpBt = null;
	public static LinkedList<String> pumpMessages = new LinkedList<String>();
	public static Lock pumpLock = new ReentrantLock();
	
	public static BluetoothConn remoteCgmBt = null;
	public static LinkedList<String> cgmMessages = new LinkedList<String>();
	public static Lock cgmLock = new ReentrantLock();
	
	public String btString;
	public static int g4Battery = -1;
	
	protected InterfaceData()
	{	
		commMethod = "";
		commDescrip = "";
		
		bt = BluetoothAdapter.getDefaultAdapter();
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
	
	public String getMessage(LinkedList<String> rx, Lock lock)
	{		
		final String FUNC_TAG = "getMessage";

		try 
		{
			if(lock.tryLock(TIMEOUT, TimeUnit.SECONDS))
			{
				String out = null;
				out = rx.getFirst();
				
				lock.unlock();
				return out;
			}
			else
				Debug.i(TAG, FUNC_TAG, "Thread lock timeout");
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
		
		return null;
	}
	
	public void AddMessage(LinkedList<String> rx, Lock lock, String in)
	{
		final String FUNC_TAG = "AddMessage";

		try
		{
			if(lock.tryLock(TIMEOUT, TimeUnit.SECONDS))
			{
				rx.add(in);
				lock.unlock();
			}
			else 
				Debug.i(TAG, FUNC_TAG, "Thread lock timeout");
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
		}
	}
	
	public void removeMessage(LinkedList<String> rx, Lock lock)
	{
		final String FUNC_TAG = "removeMessage";

		try
		{
			if(lock.tryLock(TIMEOUT, TimeUnit.SECONDS))
			{
				rx.removeFirst();
				lock.unlock();
			}
			else
				Debug.i(TAG, FUNC_TAG, "Thread lock timeout");
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
		}
	}
}
