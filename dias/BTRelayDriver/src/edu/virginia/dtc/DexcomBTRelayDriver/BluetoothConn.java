package edu.virginia.dtc.DexcomBTRelayDriver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import edu.virginia.dtc.SysMan.Debug;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.provider.ContactsContract.Contacts.Data;

public class BluetoothConn extends Object{

	private static final String TAG = "BluetoothConn";
	
	private String NAME;
	private UUID UUID_SECURE;
	
	private static final int RETRY_WAIT = 5;
	
	public static final int NONE = 0;
	public static final int LISTENING = 1;
	public static final int CONNECTING =2;
	public static final int CONNECTED = 3;
	
	private BluetoothAdapter adapter;
	
	private int state, prevState;
	
	private ListenThread listen;
	private ConnectThread connect;
	private RunningThread running;
	private LinkedList<String> rxData;
	private Lock rxLock;
	private boolean autoRetry = true;
	private BluetoothDevice prevDev = null;
	
	public String device = "";
	
	private InterfaceData data;
	
	public ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	public ScheduledFuture<?> retrySchedule;
	
	public BluetoothConn(BluetoothAdapter bt, String uuid, String name, LinkedList<String> rx, Lock lock, boolean retry)
	{
		final String FUNC_TAG = "BluetoothConn";

		Debug.i(TAG, FUNC_TAG, name +"Starting BT connection!");
		
		adapter = bt;
		UUID_SECURE = UUID.fromString(uuid);
		NAME = name;
		rxData = rx;
		rxLock = lock;
		autoRetry = retry;
		
		data = InterfaceData.getInstance();
	}
	
	public void listen()
	{
		final String FUNC_TAG = "listen";

		Debug.i(TAG, FUNC_TAG, NAME +"Listen()");
		
		if(connect!=null)
		{
			connect.cancel();
			connect = null;
		}
		
		if(running != null)
		{
			running.cancel();
			running = null;
		}
		
		setState(LISTENING);
		
		if(listen == null)
		{
			listen = new ListenThread();
			listen.start();
		}
	}
	
	public void connect(BluetoothDevice dev, boolean async)
	{
		final String FUNC_TAG = "connect";

		if(async)			//Basically a flag to say whether it comes from the UI or internally
		{					//We only want to overwrite the device from the UI, not the retry
			prevDev = dev;	
		}
		
		Debug.i(TAG, FUNC_TAG, NAME +"Connect()");
		
		if(state == CONNECTING)
		{
			if(connect != null)
			{
				connect.cancel();
				connect = null;
			}
		}

		if(running != null)
		{
			running.cancel();
			running = null;
		}
		
		connect = new ConnectThread(dev);
		connect.start();
		
		setState(CONNECTING);
	}
	
	private void running(BluetoothSocket sock)
	{
		final String FUNC_TAG = "running";

		Debug.i(TAG, FUNC_TAG, NAME +"Running()");
		
		if(connect != null)
		{
			connect.cancel();
			connect = null;
		}
		
		if(running != null)
		{
			running.cancel();
			running = null;
		}
		
		if(listen != null)
		{
			listen.cancel();
			listen = null;
		}
		
		running = new RunningThread(sock);
		running.start();
		
		setState(CONNECTED);
	}
	
	public synchronized void stop()
	{
		final String FUNC_TAG = "stop";

		Debug.i(TAG, FUNC_TAG, NAME + "Stopping Bluetooth");
		
		if(connect != null)
		{
			connect.cancel();
			connect = null;
		}
		
		if(running != null)
		{
			running.cancel();
			running = null;
		}
		
		if(listen != null)
		{
			listen.cancel();
			listen = null;
		}
		
		setState(NONE);
	}
	
	public boolean write(byte[] b)
	{
		RunningThread r;
		synchronized(this)
		{
			if(state != CONNECTED)
				return false;
			r = running;
		}
		r.write(b);
		return true;
	}
	
	private void failed()
	{
		final String FUNC_TAG = "failed";

		Debug.i(TAG, FUNC_TAG, "Connection failed...retrying previous device");
		listen();
		
		Runnable retry = new Runnable()
		{
			public void run() 
			{
				if(prevDev != null && state != CONNECTED)
					connect(prevDev, false);
			}	
		};
		
		if(retrySchedule != null)
			retrySchedule.cancel(true);
		retrySchedule = scheduler.schedule(retry, RETRY_WAIT, TimeUnit.SECONDS);
	}
	
	private void lost()
	{
		final String FUNC_TAG = "lost";

		Debug.i(TAG, FUNC_TAG, "Connection lost...retrying previous device");
		listen();
		
		Runnable retry = new Runnable()
		{
			public void run() 
			{
				if(prevDev != null && state != CONNECTED)
					connect(prevDev, false);
			}	
		};
		
		if(retrySchedule != null)
			retrySchedule.cancel(true);
		retrySchedule = scheduler.schedule(retry, RETRY_WAIT, TimeUnit.SECONDS);
	}
	
	private synchronized void setState(int st)
	{
		prevState = state;
		state = st;
	}
	
	public synchronized int getState()
	{
		return state;
	}
	
	/***************************************************************************************
	 * Thread classes (Derived from BluetoothChat example developer.android.com)
	 ***************************************************************************************/
	
	private class ListenThread extends Thread
	{
		private final BluetoothServerSocket servSock;
		
		public ListenThread()
		{
			final String FUNC_TAG = "ListenThread";

			Debug.i(TAG, FUNC_TAG,NAME +"ListenThread starting...");
			BluetoothServerSocket tmpSock = null;
			
			try
			{
				tmpSock = adapter.listenUsingRfcommWithServiceRecord(NAME, UUID_SECURE);
			}
			catch (IOException e)
			{
				Debug.e(TAG, FUNC_TAG, NAME + ": ListenThread: socket listen() failed");
			}
				
			servSock = tmpSock;
		}
		
		public void run()
		{
			final String FUNC_TAG = "run";

			BluetoothSocket socket = null;
			while(state != CONNECTED)
			{
				try
				{
					socket = servSock.accept();
				}
				catch(IOException e)
				{
					Debug.e(TAG, FUNC_TAG, NAME + ": ListenThread: socket accept() failed");
					break;
				}
				
				if(socket != null)
				{
					synchronized(this)
					{
						switch(state)
						{
							case LISTENING:
							case CONNECTING:
								//Start connected thread
								
								break;
							case NONE:
							case CONNECTED:
								try
								{
									socket.close();
								}
								catch(IOException e)
								{
									Debug.e(TAG, FUNC_TAG, NAME + ": ListenThread: couldn't close unwanted socket");
								}
								break;
						}
					}
				}
			}
		}
		
		public void cancel()
		{
			final String FUNC_TAG = "cancel";

			try{
				servSock.close();
			}
			catch(IOException e)
			{
				Debug.e(TAG, FUNC_TAG, NAME + ": ListenThread: unable to close() server socket");
			}
		}
	}
	
	private class ConnectThread extends Thread
	{
		private final BluetoothSocket socket;
		private final BluetoothDevice device;
		
		public ConnectThread(BluetoothDevice dev)
		{
			final String FUNC_TAG = "ConnectThread";

			Debug.i(TAG, FUNC_TAG,NAME + ": ConnectThread starting...");
			
			device = dev;
			BluetoothSocket tmp = null;
			try
			{
				tmp = device.createRfcommSocketToServiceRecord(UUID_SECURE);
			}
			catch(IOException e)
			{
				Debug.e(TAG, FUNC_TAG,NAME + ": ConnectThread: socket create() failed");
			}
			
			socket = tmp;
		}
		
		public void run()
		{
			final String FUNC_TAG = "run";

			adapter.cancelDiscovery();
			
			try{
				socket.connect();
			}
			catch(IOException e)
			{
				Debug.e(TAG, FUNC_TAG, e.getMessage());
				
				try
				{
					socket.close();
				}
				catch(IOException e2)
				{
					Debug.e(TAG, FUNC_TAG, NAME + ": ConnectThread: unable to close() socket");
				}
				failed();
				return;
			}
			
			synchronized(this)
			{
				connect = null;
			}
			
			running(socket);
		}
		
		public void cancel()
		{
			final String FUNC_TAG = "cancel";

			try
			{
				socket.close();
			}
			catch(IOException e)
			{
				Debug.e(TAG, FUNC_TAG, NAME + ": ConnectThread: unable to close() during cancel call");
			}
		}
	}
	
	private class RunningThread extends Thread
	{
		private final BluetoothSocket socket;
		private final InputStream input;
		private final OutputStream output;
		
		public RunningThread(BluetoothSocket sock)
		{
			final String FUNC_TAG = "RunningThread";

			Debug.i(TAG, FUNC_TAG,NAME + ": RunningThread starting...");
			
			socket = sock;
			InputStream tmpInput = null;
			OutputStream tmpOutput = null;
			
			try
			{
				tmpInput = socket.getInputStream();
				tmpOutput = socket.getOutputStream();
			}
			catch(IOException e)
			{
				Debug.e(TAG, FUNC_TAG, NAME + ": RunningThread: IO sockets not created");
			}
			
			input = tmpInput;
			output = tmpOutput;
		}
		
		public void run()
		{
			final String FUNC_TAG = "run";

			byte[] buffer = new byte[1024];
			int bytes;
			
			while(true)
			{
				try
				{
					bytes = input.read(buffer);
					Debug.i(TAG, FUNC_TAG, NAME + ": RunningThread: read "+bytes+" bytes");
					
					if(bytes>0)
					{
						String out = "";
						
						for(int i=0;i<bytes;i++)
							out += String.format("%c", buffer[i]);
						
						Debug.i(TAG, FUNC_TAG, NAME + "Output: " + out);
						
						data.addMessage(rxData, rxLock, out);
					}
				}
				catch(IOException e)
				{
					Debug.e(TAG, FUNC_TAG,NAME + ": RunningThread: connection lost, unable to read");
					lost();
					break;
				}
			}
		}
		
		public void write(byte[] buffer)
		{
			final String FUNC_TAG = "write";

			try
			{
				output.write(buffer);
			}
			catch(IOException e)
			{
				Debug.e(TAG, FUNC_TAG,NAME + ": RunningThread: Exception during write process");
			}
		}
		
		public void cancel()
		{
			final String FUNC_TAG = "cancel";

			try
			{
				socket.close();
			}
			catch(IOException e)
			{
				Debug.e(TAG, FUNC_TAG, NAME + ": RunningThread: cancelling of socket failed close()");
			}
		}
	}
}
