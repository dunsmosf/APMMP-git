package edu.virginia.dtc.HR_Driver ;

import edu.virginia.dtc.SysMan.Debug;
import zephyr.android.HxMBT.BTClient;
import zephyr.android.HxMBT.ConnectListenerImpl;
import zephyr.android.HxMBT.ConnectedEvent;
import zephyr.android.HxMBT.ZephyrPacketArgs;
import zephyr.android.HxMBT.ZephyrPacketEvent;
import zephyr.android.HxMBT.ZephyrPacketListener;
import zephyr.android.HxMBT.ZephyrProtocol;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class NewConnectedListener extends ConnectListenerImpl
{
	private static final String TAG = "NewConnectedListener";
	
	private Handler _aNewHandler; 
	
	private int HR_SPD_DIST_PACKET =0x26;
	private final int HEART_RATE = 0x100;
	
	private HRSpeedDistPacketInfo HRSpeedDistPacket = new HRSpeedDistPacketInfo();
	
	public NewConnectedListener(Handler handler,Handler _NewHandler) 
	{
		super(handler, null);
		_aNewHandler = _NewHandler;
	}
	
	public void Connected(ConnectedEvent<BTClient> eventArgs) 
	{
		final String FUNC_TAG = "Connected";
		
		Debug.i(TAG, FUNC_TAG, String.format("Connected to BioHarness %s.", eventArgs.getSource().getDevice().getName()));

		//Creates a new ZephyrProtocol object and passes it the BTComms object
		ZephyrProtocol _protocol = new ZephyrProtocol(eventArgs.getSource().getComms());
		
		_protocol.addZephyrPacketEventListener(new ZephyrPacketListener() 
		{
			public void ReceivedPacket(ZephyrPacketEvent eventArgs) 
			{
				ZephyrPacketArgs msg = eventArgs.getPacket();
				msg.getCRCStatus();
				msg.getNumRvcdBytes();
				
				if (HR_SPD_DIST_PACKET==msg.getMsgID())
				{
					Long t= getCurrentTimeSeconds();
					byte [] DataArray = msg.getBytes();
					byte  TS = HRSpeedDistPacket.GetBatteryChargeInd(DataArray);
					String battery = String.valueOf(TS);
					
					//***************Displaying the Heart Rate********************************
					int i=DataArray[9];
					int HRate= byteToUnsignedInt(DataArray[9]);
					
					Debug.i(TAG, FUNC_TAG, "First method "+Integer.toString(i)+" second  "+Integer.toString(HRate));
					
					Message text1 = _aNewHandler.obtainMessage(HEART_RATE);
					Bundle b1 = new Bundle();
					b1.putString("HeartRate", String.valueOf(HRate));
					b1.putLong("time", t);
					b1.putString("battery", battery);
					
					Debug.i(TAG, FUNC_TAG, "Heart Rate is "+ HRate);

					//***************Displaying the Instant Speed********************************
					double InstantSpeed = HRSpeedDistPacket.GetInstantSpeed(DataArray);
					b1.putString("InstantSpeed", String.valueOf(InstantSpeed));
					text1.setData(b1);
					_aNewHandler.sendMessage(text1);
					
					Debug.i(TAG, FUNC_TAG, "Instant Speed is "+ InstantSpeed);
				}
			}
		});
	}
	
	public static int byteToUnsignedInt(byte b) {
	    return 0x00 << 24 | b & 0xff;
	  }
	
	public long getCurrentTimeSeconds() {
			long currentTimeSeconds = (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970
			return currentTimeSeconds;
	}
}