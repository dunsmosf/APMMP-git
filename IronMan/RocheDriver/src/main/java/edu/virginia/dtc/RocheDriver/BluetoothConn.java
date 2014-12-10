package edu.virginia.dtc.RocheDriver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
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

public class BluetoothConn extends Object{

	private static final String TAG = "BluetoothConn";
	
	private String NAME;
	private UUID UUID_SECURE;
	
	public static final int NONE = 0;
	public static final int LISTENING = 1;
	public static final int CONNECTING =2;
	public static final int CONNECTED = 3;
	
	private static final int RETRY_S = 1;
	
	private BluetoothAdapter adapter;
	
	private int state;
	private ListenThread listen;
	private ConnectThread connect;
	private RunningThread running;
	private LinkedList<byte[]> rxData;
	private boolean autoRetry = false;
	private boolean connectRetry = false;
	private boolean stopping = false;
	private BluetoothDevice prevDev = null;
	
	private int count = 0;
	
	public String device = "";
	
	private InterfaceData data;
	
	public ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	public ScheduledFuture<?> retrySchedule;
	
	public BluetoothConn(BluetoothAdapter bt, String uuid, String name, boolean retry)
	{
		final String FUNC_TAG = "BluetoothConn";

		Debug.i(TAG, FUNC_TAG, name +" Starting BT connection!");
		
		stopping = false;
		
		adapter = bt;
		UUID_SECURE = UUID.fromString(uuid);
		NAME = name;
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
	
	public void connect(BluetoothDevice dev, boolean async, boolean retry)
	{
		final String FUNC_TAG = "connect";

		stopping = false;
		
		connectRetry = retry;
		autoRetry = retry;
		
		count++;
		
		if(retrySchedule != null)
			retrySchedule.cancel(true);
		
		if(async)			//Basically a flag to say whether it comes from the UI or internally
			prevDev = dev;	//We only want to overwrite the device from the UI, not the retry
		
		Debug.i(TAG, FUNC_TAG, NAME +"Connect()");
		
//		if(listen != null)
//		{
//			listen.cancel();
//			listen = null;
//		}
		
		if(connect != null)
		{
			connect.cancel();
			connect = null;
			Debug.i(TAG, FUNC_TAG, "Nullifying connecting thread!");
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
		
		stopping = true;
		
		Debug.i("RocheDriver", "resetDriver", "Canceling retry schedule");
		
		if(retrySchedule!=null)
			retrySchedule.cancel(true);
		
		Debug.i("RocheDriver", "resetDriver", "Canceling scheduler");
		
		if(scheduler != null)
		{
			scheduler.shutdown();
			scheduler = Executors.newSingleThreadScheduledExecutor();
		}
		
		Debug.i("RocheDriver", "resetDriver", "Canceling listen");
		
		if(listen != null)
		{
			listen.cancel();
			listen = null;
		}
		
		Debug.i("RocheDriver", "resetDriver", "Canceling connect");
		
		if(connect != null)
		{
			connect.cancel();
			connect = null;
		}
		
		Debug.i("RocheDriver", "resetDriver", "Canceling running");
		
		if(running != null)
		{
			running.cancel();
			running = null;
		}
		
		Debug.i("RocheDriver", "resetDriver", "Resetting variables");
		
		prevDev = null;
		
		connectRetry = false;
		autoRetry = false;
		
		setState(NONE);
	}
	
	public boolean write(byte[] out)
	{
		final String FUNC_TAG = "write";

		RunningThread r;
		synchronized(this)
		{
			if(state != CONNECTED)
			{
				Debug.e(TAG, FUNC_TAG, "Cannot execute write, not in connected state!");
				return false;
			}
			r = running;
		}
		r.write(out);
		return true;
	}
	
	private void failed()
	{
		final String FUNC_TAG = "failed";

		if(!stopping)
		{
			Debug.i(TAG, FUNC_TAG, "Connection failed...");
			listen();												
			
			Runnable retry = new Runnable()
			{
				public void run() 
				{
					if(prevDev != null && state != CONNECTED)
						connect(prevDev, false, true);
				}	
			};
			
			if(retrySchedule != null)
				retrySchedule.cancel(true);
			
			if(connectRetry)
			{
				Debug.i(TAG, FUNC_TAG, "Retrying previous device...");
				retrySchedule = scheduler.schedule(retry, RETRY_S, TimeUnit.SECONDS);
			}
		}
		else
			Debug.i(TAG, FUNC_TAG, "Stopping!");
	}
	
	private void lost()
	{
		final String FUNC_TAG = "lost";

		if(!stopping)
		{
			Debug.i(TAG, FUNC_TAG, "Connection lost...retrying previous device");
			listen();
			
			Runnable retry = new Runnable()
			{
				public void run() 
				{
					if(prevDev != null && state != CONNECTED)
					{
						Debug.i(TAG, FUNC_TAG, "Sending connect to previous device...");
						connect(prevDev, false, true);
					}
					else
						Debug.i(TAG, FUNC_TAG, "Problem with retry! State: "+state);
				}	
			};
			
			if(retrySchedule != null)
				retrySchedule.cancel(true);
			
			if(autoRetry)
				retrySchedule = scheduler.schedule(retry, RETRY_S, TimeUnit.SECONDS);
			else
				Debug.i(TAG, FUNC_TAG, "AutoRetry is off");
		}
		else
			Debug.i(TAG, FUNC_TAG, "Stopping!");
	}
	
	private synchronized void setState(int st)
	{
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
				tmpSock = adapter.listenUsingInsecureRfcommWithServiceRecord(NAME, UUID_SECURE);
				//tmpSock = adapter.listenUsingRfcommWithServiceRecord(NAME, UUID_SECURE);
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
					if(servSock != null)
						socket = servSock.accept();
				}
				catch(IOException e)
				{
					Debug.e(TAG, FUNC_TAG, NAME + ": ListenThread: socket accept() failed");
					break;
				}
				
				if(socket != null)
				{
					Debug.i(TAG, FUNC_TAG, NAME + ": Socket Accepted!");
					synchronized(this)
					{
						switch(state)
						{
							case LISTENING:
							case CONNECTING:
								//Start connected thread
								running(socket);
								break;
							case NONE:
							case CONNECTED:
								try
								{
									Debug.e(TAG, FUNC_TAG, "Closing Socket from Listening thread!");
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
				if(servSock!=null)
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
				tmp = device.createInsecureRfcommSocketToServiceRecord(UUID_SECURE);
				//tmp = device.createRfcommSocketToServiceRecord(UUID_SECURE);
			}
			catch(IOException e)
			{
				Debug.e(TAG, FUNC_TAG,NAME + ": ConnectThread: socket create() failed");
			}
			
			Debug.i(TAG, FUNC_TAG, "Setting socket value...");
			socket = tmp;
		}
		
		public void run()
		{
			final String FUNC_TAG = "run";

			if(adapter.isDiscovering())
				adapter.cancelDiscovery();
			
			try{
//				if(count % 2 == 0)
//					socket.close();
				
				Debug.i(TAG, FUNC_TAG, "Attempting to connect to socket...");
				socket.connect();
			}
			catch(IOException e)
			{
				Debug.e(TAG, FUNC_TAG, e.getMessage());
				Debug.i(TAG, FUNC_TAG, "Socket isConnected: "+socket.isConnected());
				
				if(socket.isConnected())
				{
					try
					{
						Debug.i(TAG, FUNC_TAG, "Attempting to close socket...");
						socket.close();
					}
					catch(IOException e2)
					{
						Debug.e(TAG, FUNC_TAG, NAME + ": ConnectThread: unable to close() socket");
					}
				}
				
				failed();
				return;
			}
			
			synchronized(this)
			{
				Debug.i(TAG, FUNC_TAG, "Connect thread set to null...");
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
					String p = "";
					
					byte[] out = new byte[bytes];
					for(int i=0;i<out.length;i++)
						out[i] = buffer[i];
					
					InterfaceData.pumpMessages.offer(out);
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
				Debug.i(TAG, FUNC_TAG, NAME + ": RunningThread: Writing "+buffer.length+" bytes!");
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