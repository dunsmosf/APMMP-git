package edu.virginia.dtc.RocheData;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import edu.virginia.dtc.RocheDriver.Driver;
import edu.virginia.dtc.SysMan.Debug;


public class Packet {

	private static final int NONCE_SIZE = 13;
	
	// frame delimiter and escape values
	private static final byte FRAME_DELIMITER         = (byte) 0xCC;
	private static final byte ESCAPE_CHARACTER        = (byte) 0x77;
	private static final byte ESCAPE_DELIMITER        = (byte) 0xDD;
	private static final byte ESCAPE_ESCAPE_CHARACTER = (byte) 0xEE;

	private static final String TAG = "Packet";
	
	public static byte[] nonceTx = new byte[NONCE_SIZE];
	public static byte[] nonceRx = new byte[NONCE_SIZE];
	
	private static boolean start = false, stop = false, escaped = false;
	private static List<Byte> packet = new ArrayList<Byte>();
	
	public void resetPacketLayer()
	{
		start = stop = escaped = false;
		packet = new ArrayList<Byte>();
		
		resetTxNonce();
		resetRxNonce();
	}
	
	public List<Byte> buildPacket(byte[] command, ByteBuffer payload, boolean address)
	{
		List<Byte> output = new ArrayList<Byte>();
		
		for(int i=0; i < command.length; i++)
			output.add(command[i]);
		
		if(address)											//Replace the default address with the real one
		{
			output.remove(command.length-1);				//Remove the last byte (address)
			output.add(Driver.getInstance().addresses);		//Add the real address byte
		}
		
		addNonce(output, nonceTx);
		
		if(payload!=null)
		{
			payload.rewind();
			for(int i=0;i<payload.capacity();i++)
				output.add(payload.get());
		}
		
		return output;
	}
	
	public List<List<Byte>> frameDeEscaping(List<Byte> buffer)
	{
		final String FUNC_TAG = "frameDeEscaping";

		List<List<Byte>> complete = new ArrayList<List<Byte>>();
		
		if(start)
		{
			//This is a scenario where a packet is so big it isn't complete in a buffer
			//So we don't want to erase the data or reset the flags
			Debug.i(TAG, FUNC_TAG, "Start is true, so we have an incomplete packet waiting...");
		}
		else
		{
			//Reset flags and clear data for starting a new packet
			start = stop = escaped = false;
			packet.clear();
		}
		
		for(int i=0;i<buffer.size();i++)
		{
			if(escaped == true)
			{
				escaped = false;
				if(buffer.get(i) == ESCAPE_DELIMITER)
				{
					packet.add(FRAME_DELIMITER);
				}
				else if(buffer.get(i) == ESCAPE_ESCAPE_CHARACTER)
				{
					packet.add(ESCAPE_CHARACTER);
				}
			}
			else if(buffer.get(i) == ESCAPE_CHARACTER)
			{
				if(i+1 >= buffer.size())
				{
					escaped = true;				//If we are at the end of the buffer and find an escape character
				}
				else
				{
					Byte next = buffer.get(i+1);
					if(next == ESCAPE_DELIMITER)
					{
						Debug.i(TAG, FUNC_TAG, "Escape delimiter de-escaping!");
						packet.add(FRAME_DELIMITER);
						i++;								//Skip the next byte
					}
					else if(next == ESCAPE_ESCAPE_CHARACTER)
					{
						Debug.i(TAG, FUNC_TAG, "Escape escape delimiter de-escaping!");
						packet.add(ESCAPE_CHARACTER);			//Skip the next byte
						i++;
					}
				}
			}
			else if(buffer.get(i) == FRAME_DELIMITER)	//We need to cover the chance that there are multiple packets in the buffer
			{
				Debug.i(TAG, FUNC_TAG, "Frame delimiter, skipping byte");
				
				if(!start)
				{
					Debug.i(TAG, FUNC_TAG, "Iteration: "+i+" Start is found!");
					start = true;
				}
				else
				{
					Debug.i(TAG, FUNC_TAG, "Iteration: "+i+" Stop is found!");
					stop = true;
				}
				
				if(start && stop)
				{
					Debug.i(TAG, FUNC_TAG, "Adding packet!");
					start = false;
					stop = false;
					
					if(packet.size() == 0)
					{
						Debug.e(TAG, FUNC_TAG, "We just tried to add an empty packet so our delimiters got screwed up due to random traffic, treat this as a start, not stop!");
						start = true;
						stop = false;
					}
					else if(i == 0)
					{
						Debug.e(TAG, FUNC_TAG, "This really doesn't happen unless its an error case!  We shouldn't end up with a packet that ends at the start of a new set of data.");
						start = true;
						stop = false;
					}
					else
					{
						complete.add(packet);
						packet = new ArrayList<Byte>();
					}
				}
			}
			else
			{
				//Debug.i(TAG, FUNC_TAG, "Adding byte to packet...");
				if(start)
					packet.add(buffer.get(i));
				else
					Debug.e(TAG, FUNC_TAG, "Cannot add packet data to a packet without a start delimiter!");
			}
		}
		
		return complete;
	}
	
	public void frameEscaping(List<Byte> packet)
	{
		final String FUNC_TAG = "frameEscaping";

		List<Byte> temp = new ArrayList<Byte>();
		
		temp.add(FRAME_DELIMITER);							//Add delimiter
		
		for(int i=0;i<packet.size();i++)
		{
			if(packet.get(i) == FRAME_DELIMITER)			//Escape necessary bytes
			{
				Debug.i(TAG, FUNC_TAG, "Frame Delimiter byte escaped!");
				temp.add(ESCAPE_CHARACTER);
				temp.add(ESCAPE_DELIMITER);
			}
			else if(packet.get(i) == ESCAPE_CHARACTER)
			{
				Debug.i(TAG, FUNC_TAG, "Escape Character byte escaped!");
				temp.add(ESCAPE_CHARACTER);
				temp.add(ESCAPE_ESCAPE_CHARACTER);
			}
			else
			{
				temp.add(packet.get(i));
			}
			
		}
		
		temp.add(FRAME_DELIMITER);							//Add delimiter
		
		packet.clear();
		packet.addAll(temp);
	}
	
	public void resetTxNonce()
	{
		for(int i=0;i<nonceTx.length;i++)
			nonceTx[i] = 0;
	}
	
	public void resetRxNonce()
	{
		for(int i=0;i<nonceRx.length;i++)
			nonceRx[i] = 0;
	}
	
	public int incrementArray(byte[] array)
	{
		int i=0, carry=0;
		
		array[i]++;
		if(array[i] == 0)
			carry =1;
		
		for(i=1;i<array.length;i++)
		{
			if(carry==1)
			{
				array[i] += carry;
				if(array[i] > 0)
				{
					carry = 0;
					return carry;
				}
				else
					carry = 1;
			}
		}
		
		return carry;
	}
	
	public void addNonce(List<Byte> packet, byte[] nonce)
	{
		for(int i=0;i<nonce.length;i++)
			packet.add(nonce[i]);
	}
}
