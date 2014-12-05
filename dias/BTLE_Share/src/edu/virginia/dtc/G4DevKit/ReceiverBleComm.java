package edu.virginia.dtc.G4DevKit;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;

public class ReceiverBleComm  implements IReceiverComm
{
	private static final String TAG = "ReceiverBleComm";
	
	public static final UUID CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	
	public static final UUID AUTH_CHAR = 		UUID.fromString("f0acacac-ebfa-f96f-28da-076c35a521db");
	public static final UUID CRADLE_CHAR = 		UUID.fromString("f0acb0cd-ebfa-f96f-28da-076c35a521db");
	public static final UUID HBT_CHAR = 		UUID.fromString("f0ac2b18-ebfa-f96f-28da-076c35a521db");
	public static final UUID SRV_CHAR = 		UUID.fromString("f0acb20a-ebfa-f96f-28da-076c35a521db");
    public static final UUID CLT_CHAR = 		UUID.fromString("f0acb20b-ebfa-f96f-28da-076c35a521db");
	
    private static BluetoothManager btleManager;
	private static BluetoothAdapter btleAdapter; 
    private static BluetoothGatt btleGatt;
    private static BluetoothGattCharacteristic hrtChar, authChar, cltChar, srvChar;
    
    private Context service;
    private static String code, mac;
    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> setNotify, setAuth, rxTimer;
    private CountDownLatch rx;
    private long time, beats;
    
    private static boolean connected = false, reconnecting = true;
    
    private List<Byte> receive = new ArrayList<Byte>();
    
    private void cancelTimers()
    {
    	if(setNotify != null)
    		setNotify.cancel(true);
    	
    	if(setAuth != null)
    		setAuth.cancel(true);
    }
    
    private Runnable release = new Runnable()
    {
    	public void run()
    	{
    		Debug.w(TAG, "release", "Releasing lock because of expiration of timer...");
    		rx.countDown();
    	}
    };
    
    private Runnable heartbeat = new Runnable()
    {
		public void run() 
		{
			BluetoothGattCharacteristic c = hrtChar;
			btleGatt.setCharacteristicNotification(c, true);
            BluetoothGattDescriptor descriptor = c.getDescriptor(CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
            boolean success1 = descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            boolean success = btleGatt.writeDescriptor(descriptor);
            Debug.i(TAG, "heartbeat", "Set Notifications: "+success1+" "+success);
		}
    };
    
    private Runnable client = new Runnable()
    {
		public void run() 
		{
			BluetoothGattCharacteristic c = cltChar;
			btleGatt.setCharacteristicNotification(c, true);
            BluetoothGattDescriptor descriptor = c.getDescriptor(CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
            boolean success1 = descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            boolean success = btleGatt.writeDescriptor(descriptor);
            Debug.i(TAG, "client", "Set Indications: "+success1+" "+success);
		}
    };
    
    private Runnable authenticate = new Runnable()
    {
    	public void run()
    	{
    		String Serial = ReceiverBleComm.code + "000000";
			byte[] b = null;
			
			try 
			{
				b = Serial.getBytes("US-ASCII");
			} 
			catch (UnsupportedEncodingException e) 
			{
				e.printStackTrace();
			}
			
			write(authChar, b);
    	}
    };
    
	public ReceiverBleComm(Context c, String mac, String code)
	{
		final String FUNC_TAG = "Constructor";
		
		btleManager = (BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);
		btleAdapter = btleManager.getAdapter();
		
		this.service = c;
		ReceiverBleComm.code = code;
		ReceiverBleComm.mac = mac;
		
		beats = 0;
		
		Debug.i(TAG, FUNC_TAG, "MAC: ("+ReceiverBleComm.mac+") Code: ("+ReceiverBleComm.code+")");

		cancelTimers();
		
		new Handler().postDelayed(new Runnable()
		{
			public void run() 
			{
				connect(btleAdapter.getRemoteDevice(ReceiverBleComm.mac.toUpperCase()));				
			}
		}, 3000);
	}
	
	public byte[] sendReceiverMessageForResponse(byte[] paramArrayOfByte) throws Exception 
	{
		final String FUNC_TAG = "sendReceiverMessageForResponse";
		
		Debug.v(TAG, FUNC_TAG, "Data to be sent: "+paramArrayOfByte.length);
		
		byte[] reply = new byte[0];		//We will return a zero length array if no response/timeout
		
		final StringBuilder s1 = new StringBuilder(paramArrayOfByte.length);
        for(byte byteChar : paramArrayOfByte)
            s1.append(String.format("%02X ", byteChar));
        Debug.i(TAG, FUNC_TAG, s1.toString());
		
		//Segment and send message
        receive.clear();
        rx = new CountDownLatch(1);
        
        List<byte[]> segments = segmentMessage(paramArrayOfByte);
        for(byte[] s:segments)
        {
        	final StringBuilder s2 = new StringBuilder(s.length);
            for(byte byteChar : s)
                s2.append(String.format("%02X ", byteChar));
            Debug.i(TAG, FUNC_TAG, "Segment ["+segments.indexOf(s)+"] "+s2.toString());
            
            write(srvChar, s);
        }
		
		//Receive or wait for response message
        Debug.v(TAG, FUNC_TAG, "Waiting on reception of data...");
        rx.await(15, TimeUnit.SECONDS);
		
		//Return the message or null if times out
        Debug.v(TAG, FUNC_TAG, "Receive Buffer: "+receive.size());
        reply = new byte[receive.size()];
        for(int i = 0; i < reply.length; i++)
        	reply[i] = (byte)(receive.get(i) & 0x00FF);
		
        final StringBuilder s3 = new StringBuilder(reply.length);
        for(byte byteChar : reply)
            s3.append(String.format("%02X ", byteChar));
        Debug.v(TAG, FUNC_TAG, "Reply "+s3.toString());
        
		return reply;
	}
	
	private List<byte[]> segmentMessage(byte[] input)
	{
		final String FUNC_TAG = "segmentMessage";
		
		List<byte[]> output = new ArrayList<byte[]>();
		Debug.i(TAG, FUNC_TAG, "Input length: "+input.length);
		
		if(input.length > 18)
		{
			Debug.e(TAG, FUNC_TAG, "Not implemented as there are no commands yet greater than 18 bytes!");
		}
		else
		{
			byte[] out = new byte[input.length + 2];
			out[0] = 0x01;		//Segment number
			out[1] = 0x01;		//Number of segments
			System.arraycopy(input, 0, out, 2, input.length);
			
			output.add(out);
		}
		
		return output;
	}
	
	private void write(BluetoothGattCharacteristic c, byte[] b)
	{
		final String FUNC_TAG = "write";
		
		c.setValue(b);
		
		if(btleGatt != null)
			Debug.i(TAG, FUNC_TAG, "Writing: "+btleGatt.writeCharacteristic(c));
		else
			Debug.e(TAG, FUNC_TAG, "BTLE Gatt Connection is null or closed!");
	}
	
	public static boolean isConnected()
	{
		return connected;
	}
	
	public void reconnect()
	{
		final String FUNC_TAG = "reconnect";
		
		reconnecting = true;
		
		Debug.i(TAG, FUNC_TAG, "Attempting reconnect...");
		
		if(ReceiverBleComm.mac != null && !ReceiverBleComm.mac.equals(""))
			connect(btleAdapter.getRemoteDevice(ReceiverBleComm.mac.toUpperCase()));
		else
			Debug.e(TAG, FUNC_TAG, "Unable to reconnect, invalid MAC address!");
	}
	
	private void connect(BluetoothDevice d)
	{
		final String FUNC_TAG = "connect";
		
		if(btleGatt != null)
			btleGatt.close();
		
		if(d != null)
			btleGatt = d.connectGatt(service, false, gattCallback);
		else
			Debug.e(TAG, FUNC_TAG, "The connection or device is null!");
	}

	private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() 
	{
		final String FUNC_TAG = "gattCallback";
		
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) 
        {
        	Debug.i(TAG, FUNC_TAG, "Connection state change: "+newState+" Status: "+checkStatus(status));
        	Debug.i(TAG, FUNC_TAG, "Device MAC: "+gatt.getDevice().getAddress());
        	
            switch(newState)
            {
	            case BluetoothProfile.STATE_CONNECTED: 
	            	Debug.v(TAG, FUNC_TAG, "Connected!");
	            	if(status != BluetoothGatt.GATT_SUCCESS)
	            	{
	            		Debug.e(TAG, FUNC_TAG, "Error connecting...");
	            		if(reconnecting)
	            			reconnect();
	            		return;
	            	}
	            	
	            	connected = true;
	            	reconnecting = false;
	            	
	            	Debug.i(TAG, FUNC_TAG, "Discovering services...");
	                btleGatt.discoverServices();
	            	break;
	            case BluetoothProfile.STATE_CONNECTING:
	            	Debug.v(TAG, FUNC_TAG, "Connecting!");
	            	break;
	            case BluetoothProfile.STATE_DISCONNECTED:
	            	Debug.v(TAG, FUNC_TAG, "Disconnected!");
	            	
	            	if(connected)
	            		reconnect();
	            	
	            	connected = false;
	            	break;
	            case BluetoothProfile.STATE_DISCONNECTING:
	            	Debug.v(TAG, FUNC_TAG, "Disconnecting!");
	            	break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) 
        {
        	Debug.i(TAG, FUNC_TAG, "Services Discovered: " + checkStatus(status));
        	
            if (status == BluetoothGatt.GATT_SUCCESS) 
            {
            	List<BluetoothGattService> services = gatt.getServices();
            	
            	for(BluetoothGattService s:services)
            	{
            		Debug.i(TAG, FUNC_TAG, "Service >>> "+s.getUuid().toString());
            		
            		for(BluetoothGattCharacteristic c:s.getCharacteristics())
            		{
            			Debug.i(TAG, FUNC_TAG, 	"	Char >>> "+c.getUuid().toString());
            			
            			if(c.getUuid().equals(AUTH_CHAR))
            			{
            				Debug.i(TAG, FUNC_TAG, "Authentication characteristic found!");
            				authChar = c;
            				
            				setAuth = scheduler.schedule(authenticate, 15, TimeUnit.SECONDS);
            			}
            			else if(c.getUuid().equals(HBT_CHAR))
            			{
            				Debug.i(TAG, FUNC_TAG, "Heartbeat characteristic found!");
            				hrtChar = c;
            				
            				setNotify = scheduler.schedule(heartbeat, 5, TimeUnit.SECONDS);
            			}
            			else if(c.getUuid().equals(SRV_CHAR))
            			{
            				Debug.i(TAG, FUNC_TAG, "Server characteristic found!");
            				srvChar = c;
            			}
            			else if(c.getUuid().equals(CLT_CHAR))
            			{
            				Debug.i(TAG, FUNC_TAG, "Client characteristic found!");
            				cltChar = c;
            				
            				setNotify = scheduler.schedule(client, 10, TimeUnit.SECONDS);
            			}
            		}
            	}
            } 
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic c)
        {
        	Debug.i(TAG, FUNC_TAG, "Characteristic Change received: "+c.getValue().length+" bytes!");
        	byte[] data = c.getValue();
        	
        	if(c.getUuid().equals(HBT_CHAR))		//Heartbeat
        	{
        		Debug.i(TAG, FUNC_TAG, "Hearbeats: "+beats);
        		
        		if(beats > 0)
        		{
	        		Intent i = new Intent("edu.virginia.dtc.sync_receiver");
	        		service.sendBroadcast(i);
        		}
        		beats++;
        	}
        	else if(c.getUuid().equals(CLT_CHAR))	//Client (data to be read)
        	{
        		Debug.i(TAG, FUNC_TAG, "Client");
        		
        		Debug.w(TAG, FUNC_TAG, "Time to RX: "+(System.currentTimeMillis() - time)+" ms");
        		
        		time = System.currentTimeMillis();
        		
        		if(rxTimer != null)
        			rxTimer.cancel(true);
        		
        		Debug.v(TAG, FUNC_TAG, "Setting timer!");
        		rxTimer = scheduler.schedule(release, 500, TimeUnit.MILLISECONDS);
        		
        		for(byte b:data)
        			receive.add(b);
        		
        		Debug.i(TAG, FUNC_TAG, "Receive Length: "+receive.size()+" CountDown: "+rx.getCount());
        	}
        	
        	if(data != null)
        	{
        		final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                Debug.i(TAG, FUNC_TAG, stringBuilder.toString());
        	}
        }
        
        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) 
        {
        	Debug.i(TAG, FUNC_TAG, "Characteristic Read received: "+ checkStatus(status));
        	byte[] data = characteristic.getValue();
        	if(data != null)
        	{
        		final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                Debug.i(TAG, FUNC_TAG, stringBuilder.toString());
        	}
        }
        
        @Override
        public void onCharacteristicWrite (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) 
        {
        	Debug.i(TAG, FUNC_TAG, "Characteristic Write received: "+ checkStatus(status));
        }
    };
    
    private String checkStatus(int status)
	{
		String s = "";
		switch(status)
		{
			case    0x0000:	s = "GATT_SUCCESS"; break;
			case	0x0001:	s = "GATT_INVALID_HANDLE"; break;
			case    0x0002: s = "GATT_READ_NOT_PERMIT"; break;
			case    0x0003: s = "GATT_WRITE_NOT_PERMIT"; break;
			case    0x0004: s = "GATT_INVALID_PDU"; break;
			case    0x0005: s = "GATT_INSUF_AUTHENTICATION"; break;
			case    0x0006: s = "GATT_REQ_NOT_SUPPORTED"; break;
			case    0x0007: s = "GATT_INVALID_OFFSET"; break;
			case    0x0008: s = "GATT_INSUF_AUTHORIZATION"; break;
			case    0x0009: s = "GATT_PREPARE_Q_FULL"; break;
			case    0x000a: s = "GATT_NOT_FOUND"; break;
			case    0x000b: s = "GATT_NOT_LONG"; break;
			case    0x000c: s = "GATT_INSUF_KEY_SIZE"; break;
			case    0x000d: s = "GATT_INVALID_ATTR_LEN"; break;
			case    0x000e: s = "GATT_ERR_UNLIKELY"; break;
			case    0x000f: s = "GATT_INSUF_ENCRYPTION"; break;
			case    0x0010: s = "GATT_UNSUPPORT_GRP_TYPE"; break;
			case    0x0011: s = "GATT_INSUF_RESOURCE"; break;
			case    0x0087: s = "GATT_ILLEGAL_PARAMETER"; break;
			case    0x0080: s = "GATT_NO_RESOURCES"; break;
			case    0x0081: s = "GATT_INTERNAL_ERROR"; break;
			case    0x0082: s = "GATT_WRONG_STATE"; break;
			case    0x0083: s = "GATT_DB_FULL"; break;
			case    0x0084: s = "GATT_BUSY"; break;
			case    0x0085: s = "GATT_ERROR"; break;
			case    0x0086: s = "GATT_CMD_STARTED"; break;
			case    0x0088: s = "GATT_PENDING"; break;
			case    0x0089: s = "GATT_AUTH_FAIL"; break;
			case    0x008a: s = "GATT_MORE"; break;
			case    0x008b: s = "GATT_INVALID_CFG"; break;
			case    0x008c: s = "GATT_SERVICE_STARTED"; break;
			case    0x008d: s = "GATT_ENCRYPED_NO_MITM"; break;
			case	0x008e: s = "GATT_NOT_ENCRYPTED"; break;
			default: s = "Unknown"; break;
		}
		return s;
	}
}
