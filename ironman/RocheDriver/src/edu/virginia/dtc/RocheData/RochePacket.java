package edu.virginia.dtc.RocheData;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import edu.virginia.dtc.RocheDriver.Driver;
import edu.virginia.dtc.SysMan.Debug;

public class RochePacket 
{	
	private static final String TAG = "RochePacket";

	public ByteBuffer packetBuffer, nBuf, pBuf, uBuf;
	
	public byte[] nonce, payload, umac, paddedPacket, packetNoUmac;
	
	public Byte version, command, addresses;
	
	public short length;
	
	public RochePacket(){}
	
	public RochePacket(ByteBuffer packet)
	{		
		final String FUNC_TAG = "RochePacket";

		packetBuffer = packet;							//Copy entire packet
		
		try
		{
			version = packetBuffer.get();					//Get version and other data
			command = packetBuffer.get();
			
			length = packetBuffer.getShort();
			
			addresses = packetBuffer.get();
			
			nonce = new byte[13];							//Copy buffers for nonce
			packetBuffer.get(nonce, 0, nonce.length);
			nBuf = ByteBuffer.wrap(nonce);					//Copy to ByteBuffers too for extracting data
			
			payload = new byte[length];						//Payload
			packetBuffer.get(payload, 0, payload.length);
			pBuf = ByteBuffer.wrap(payload);
			
			umac = new byte[8];								//U-MAC
			packetBuffer.get(umac, 0, umac.length);
			uBuf = ByteBuffer.wrap(umac);
			
			packetNoUmac = new byte[packetBuffer.capacity()-umac.length];
			packetBuffer.rewind();
			for(int i = 0; i<packetNoUmac.length;i++)
				packetNoUmac[i] = packetBuffer.get();
			
			packetBuffer.rewind();
		}
		catch(BufferUnderflowException e)
		{
			Driver.log("ROCHE", FUNC_TAG, "Packet underflow exception!");
		}
		catch(IndexOutOfBoundsException b)
		{
			Driver.log("ROCHE", FUNC_TAG, "Packet index out of bounds exception!");
		}
		
		//Display output from parsing
		Debug.i(TAG, FUNC_TAG, String.format("Version: %02X", this.version));
		Debug.i(TAG, FUNC_TAG, String.format("Command: %02X", this.command));
		Debug.i(TAG, FUNC_TAG, String.format("Length: %04X", this.length));
		Debug.i(TAG, FUNC_TAG, String.format("Address: %02X", this.addresses));
		
		String dat = "";
		for(byte b:this.nonce)
			dat += String.format("%02X ", b);
		Debug.i(TAG, FUNC_TAG, "Nonce: "+dat);
		
		dat = "";
		for(byte b:this.payload)
			dat += String.format("%02X ", b);
		Debug.i(TAG, FUNC_TAG, "Payload: "+dat);
		
		dat = "";
		for(byte b:this.umac)
			dat += String.format("%02X ", b);
		Debug.i(TAG, FUNC_TAG, "UMAC: "+dat);
		
		dat = "";
		for(byte b:this.packetNoUmac)
			dat += String.format("%02X ", b);
		Debug.i(TAG, FUNC_TAG, "Packet No UMAC: "+dat);
	}
	
	public void setData(int length)
	{
		final String FUNC_TAG = "setData";

		packetBuffer = ByteBuffer.allocate(length + umac.length);
		packetBuffer.order(ByteOrder.LITTLE_ENDIAN);
		
		packetBuffer.put(paddedPacket, 0, length);
		packetBuffer.put(umac);
		
		String dat = "";
		for(byte b:packetBuffer.array())
			dat += String.format("%02X ", b);
		Debug.i(TAG, FUNC_TAG, "SetData(): "+dat);
	}
}
