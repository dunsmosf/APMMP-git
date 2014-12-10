package edu.virginia.dtc.RocheData;

import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;

import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.TwoFish.Twofish_Algorithm;


public class Security {

	private static final int ENCRYPT_BLOCKSIZE	= 16;
	
	private static final int BIT0    = 0x0001;
	private static final int BIT1    = 0x0002;
	private static final int BIT2    = 0x0004;
	private static final int BIT3    = 0x0008;
	private static final int BIT4    = 0x0010;
	private static final int BIT5    = 0x0020;
	private static final int BIT6    = 0x0040;
	private static final int BIT7    = 0x0080;
	private static final int BIT8    = 0x0100;
	private static final int BIT9    = 0x0200;
	private static final int BIT10   = 0x0400;
	private static final int BIT11   = 0x0800;
	private static final int BIT12   = 0x1000;
	private static final int BIT13   = 0x2000;
	private static final int BIT14   = 0x4000;
	private static final int BIT15   = 0x8000;
	private static final int BIT16   = 0x00010000;
	private static final int BIT17   = 0x00020000;
	private static final int BIT18   = 0x00040000;
	private static final int BIT19   = 0x00080000;
	private static final int BIT20   = 0x00100000;
	private static final int BIT21   = 0x00200000;
	private static final int BIT22   = 0x00400000;
	private static final int BIT23   = 0x00800000;
	private static final int BIT24   = 0x01000000;
	private static final int BIT25   = 0x02000000;
	private static final int BIT26   = 0x04000000;
	private static final int BIT27   = 0x08000000;
	private static final int BIT28   = 0x10000000;
	private static final int BIT29   = 0x20000000;
	private static final int BIT30   = 0x40000000;
	private static final int BIT31   = 0x80000000;
	
	// define the initial value for the BT crc generation
	public static final short BTCRCINIT = (short) 0xffff;

	// CRC bit masks
	private static final short CRC_MARK7    = (short) 0x8408;
	private static final short CRC_MARK6    = 0x4204;
	private static final short CRC_MARK5    = 0x2102;
	private static final short CRC_MARK4    = 0x1081;
	private static final short CRC_MARK3    = (short) 0x8C48;
	private static final short CRC_MARK2    = 0x4624;
	private static final short CRC_MARK1    = 0x2312;
	private static final short CRC_MARK0    = 0x1189;
	
	private static final int AUTHEN_SIZE  		= 8;   // # bytes in authentication code
	
	private static final String TAG = "Security";

	private static final boolean DEBUG = false;

	/***********************************************************************************/
	/***********************************************************************************/
	// TwoFish CCM Encryption Functions
	/***********************************************************************************/
	/***********************************************************************************/
	
	public Object makeKey(byte [] k)
	{
		Object sessionKey = null;
		
		try 
		{
			sessionKey = Twofish_Algorithm.makeKey(k);
		} 
		catch (InvalidKeyException e) 
		{
			e.printStackTrace();
		}
		
		return sessionKey;
	}
	
	public List<Byte> ccmAuthenticate(List<Byte> buffer, Object key, byte[] nonce)
	{
		List<Byte> output = new ArrayList<Byte>();
		int origLength = buffer.size();					//Hang on to the original length
		
		byte[] packet = new byte[buffer.size()];		//Create primitive array
		for(int i=0;i<packet.length;i++)				//Copy to byte array
			packet[i] = buffer.get(i);
		
		RochePacket p = new RochePacket();
		p.paddedPacket = padPacket(packet);				
		p.nonce = nonce;
		
		p.umac = ccmEncrypt(p,key);							//Generate U-MAC value
		
		p.setData(origLength);								//Organize data, don't send the padding we used to encrypt
		
		for(int i=0;i<p.packetBuffer.array().length;i++)	//Convert to List<Byte>
			output.add(p.packetBuffer.array()[i]);
		
		return output;
	}
	
	public byte[] ccmEncrypt(RochePacket p, Object key)
	{
		byte[] xi = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};		//Initialization vector
		byte[] u = new byte[8];											//Output array U-MAC
	
		//SETUP INITIALIZATION VECTOR
		
		xi[0] = 0x79;													//Set flags for IV
		
		for(int i=0;i<p.nonce.length;i++)								//Copy nonce
			xi[i+1] = p.nonce[i];										//TODO: check endianness
		
		xi[14] = 0;														//Length is zero
		xi[15] = 0;
		
		String dat = "";
		for(byte b:xi)
			dat += String.format("%02X ", b);
		//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> Initial XI: "+dat);
		
		xi = Twofish_Algorithm.blockEncrypt(xi, 0, key);				//Encrypt to generate XI from IV
		
		dat = "";
		for(byte b:xi)
			dat += String.format("%02X ", b);
		//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> Encrypt 1 XI: "+dat);
		
		//RUN CBC AND ENCRYPT PACKET
		
		//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> Length = "+p.paddedPacket.length);
		//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> Running loop "+p.paddedPacket.length/ENCRYPT_BLOCKSIZE+" iterations!");
		
		for(int i=0;i<(p.paddedPacket.length / ENCRYPT_BLOCKSIZE);i++)
		{
			//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> ==========================================================================================================");
			
			for(int n=0;n<ENCRYPT_BLOCKSIZE;n++)
			{
				//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> POSITION: "+ ((i * ENCRYPT_BLOCKSIZE)+n) +" xi[n]: "+String.format("%02X", xi[n])+" XOR'd with Data[n]: "+String.format("%02X",p.paddedPacket[(i * ENCRYPT_BLOCKSIZE)+n]));
				xi[n] ^= p.paddedPacket[(i * ENCRYPT_BLOCKSIZE)+n];		//Do the XOR chaining
				//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> POSITION: "+ ((i * ENCRYPT_BLOCKSIZE)+n) +" xi[n] RESULT: "+String.format("%02X", xi[n]));
			}
			
			xi = Twofish_Algorithm.blockEncrypt(xi, 0, key);			//Encrypt with TwoFish
			
			//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> ITERATION: "+i);
			
			dat = "";
			for(byte b:xi)
				dat += String.format("%02X ", b);
			//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> XI: "+dat);
			
			//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> ==========================================================================================================");
		}
		
		//PAD DATA IF IT ISN'T A MULTIPLE OF THE BLOCKSIZE
		
		if ((p.paddedPacket.length % ENCRYPT_BLOCKSIZE) != 0) 
	    {
			//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> Packet needs padding! Condition="+p.paddedPacket.length % ENCRYPT_BLOCKSIZE);
			
	        for (int i=0; i < ENCRYPT_BLOCKSIZE; i++) 
	        {
	        	//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> ==========================================================================================================");
	        	
	           if ( i < (p.paddedPacket.length % ENCRYPT_BLOCKSIZE) )
	           {
	               // Fill with trailing data 
	        	   //Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> I ="+i+" trailing data...");
	        	   //Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> POSITION: "+(((p.paddedPacket.length / ENCRYPT_BLOCKSIZE) * ENCRYPT_BLOCKSIZE) + i)+" xi[n]: "+String.format("%02X", xi[i])+" XOR'd with Data[n]: "+String.format("%02X",p.paddedPacket[((p.paddedPacket.length / ENCRYPT_BLOCKSIZE) * ENCRYPT_BLOCKSIZE) + i]));
	               xi[i] ^= p.paddedPacket[((p.paddedPacket.length / ENCRYPT_BLOCKSIZE) * ENCRYPT_BLOCKSIZE) + i];
	               //Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> POSITION: "+(((p.paddedPacket.length / ENCRYPT_BLOCKSIZE) * ENCRYPT_BLOCKSIZE) + i) +" xi[n] RESULT: "+String.format("%02X", xi[i]));
	           }
	           else
	           {
	               // Fill with bytes with value of bytes required to padding blocksize
	               // Difference to RFC 3610 section 2.2 (padding with zeroes)
	        	   //Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> I ="+i+" padding size...");
	        	   //Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> xi[n]: "+String.format("%02X", xi[i])+" XOR'd with size: "+String.format("%02X",ENCRYPT_BLOCKSIZE - (p.paddedPacket.length % ENCRYPT_BLOCKSIZE)));
	               xi[i] ^= ENCRYPT_BLOCKSIZE - (p.paddedPacket.length % ENCRYPT_BLOCKSIZE);
	               //Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> xi[n] RESULT: "+String.format("%02X", xi[i]));
	           }
	           
	           //Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> ==========================================================================================================");
	        }
	        
	        xi = Twofish_Algorithm.blockEncrypt(xi, 0, key);
	    }
		
		//COMPUTE U-MAC
		
		for(int i=0;i<u.length;i++)										//Copy XI to U
			u[i] = xi[i];
		
		dat = "";
		for(byte b:u)
			dat += String.format("%02X ", b);
		//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> Copy 8 Bytes to UMAC: "+dat);
		
		xi[0] = 65;														//Set flags
		
		dat = "";
		for(byte b:p.nonce)
			dat += String.format("%02X ", b);
		//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> Nonce: "+dat);
		
		for(int n=0;n<p.nonce.length;n++)
			xi[n+1] = p.nonce[n];
		
		xi[14] = 0;
		xi[15] = 0;
		
		dat = "";
		for(byte b:xi)
			dat += String.format("%02X ", b);
		//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> Encrypt A0: "+dat);
		
		xi = Twofish_Algorithm.blockEncrypt(xi, 0, key);				//Encrypt XI
		
		dat = "";
		for(byte b:xi)
			dat += String.format("%02X ", b);
		//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> Encrypt A0 after Encryption: "+dat);
		
		for(int i=0;i<u.length;i++)										//XOR to create U-MAC
			u[i] ^= xi[i];
		
		dat = "";
		for(byte b:u)
			dat += String.format("%02X ", b);
		//Debug.i(TAG, FUNC_TAG, "ccmEncrypt >>> U: "+dat);
		
		return u;
	}
	
	public boolean ccmVerify(RochePacket p, Object key, byte[] u)
	{
		final String FUNC_TAG = "ccmVerify";

		p.paddedPacket = p.packetNoUmac;							//Set the packet without the authentication code
		
		p.paddedPacket = padPacket(p.paddedPacket);
		
		boolean verified = true;
		byte[] u_prime = ccmEncrypt(p, key);						//Run encryption and check if U-MAC matches
		
		String dat = "";
		for(byte b:u)
			dat += String.format("%02X ", b);
		Debug.i(TAG, FUNC_TAG, "ccmVerify >>> U: "+dat);
		
		dat = "";
		for(byte b:u_prime)
			dat += String.format("%02X ", b);
		Debug.i(TAG, FUNC_TAG, "ccmVerify >>> U_Prime: "+dat);
		
		for(int i=0;i<u_prime.length;i++)
		{
			if(u_prime[i] != u[i])									//Compare U-MAC values
			{
				verified = false;
				break;
			}
		}
		
		Debug.i(TAG, FUNC_TAG, "ccmVerify >>> verify = "+verified);
		
		return verified;
	}
	
	public byte[] padPacket(byte[] packet)
	{
		byte pad;
		byte[] output;
		
		//Debug.i(TAG, FUNC_TAG, "padPacket >>> Packet Length: "+packet.length);
		
		String dat = "";
		for(byte b:packet)
			dat += String.format("%02X ", b);
		//Debug.i(TAG, FUNC_TAG, "padPacket >>> Input Packet: "+dat);
		
		pad = (byte) (ENCRYPT_BLOCKSIZE - (packet.length % ENCRYPT_BLOCKSIZE));	//(ENCRYPT_BLOCKSIZE - (packet.length % ENCRYPT_BLOCKSIZE));
		if(pad > 0)
		{
			output = new byte[pad+packet.length];
			//Debug.i(TAG, FUNC_TAG, "padPacket >>> Output Length: "+output.length+" Padding needed: "+pad);
			
			for(int n=0;n<packet.length;n++)		//Copy packet into output
				output[n] = packet[n];
			
			for(int i=0;i < pad;i++)
			{
				output[packet.length+i] = pad;
			}
		}
		else
			output =  packet;
		
		dat = "";
		for(byte b:output)
			dat += String.format("%02X ", b);
		//Debug.i(TAG, FUNC_TAG, "padPacket >>> Output Packet: "+dat);
		
		return output;
	}
	
	/***********************************************************************************/
	/***********************************************************************************/
	// CRC Functions
	/***********************************************************************************/
	/***********************************************************************************/
	
	public void addCrc(List<Byte> packet)
	{
		final String FUNC_TAG = "addCrc";

		short crc = BTCRCINIT;
		
		Debug.i(TAG, FUNC_TAG, "CRC: "+String.format("%X", crc));
		
		for(int i=0;i<packet.size();i++)
		{
			crc = updateCrc(crc, packet.get(i));
			Debug.i(TAG, FUNC_TAG, "CRC("+i+"): "+String.format("%X", crc));
		}
		
		packet.add((byte)(crc & 0xFF));					//Add CRC to packet
		packet.add((byte)(crc >> (short)8));
		
		for(int i=0;i<AUTHEN_SIZE;i++)					//Add zeroed authentication code
			packet.add((byte)0x00);
	}
	
	public short updateCrc(short crc, byte input)
	{
		final String FUNC_TAG = "updateCrc";

	    short crcTemp;

	    Debug.i(TAG, FUNC_TAG, "Input: "+String.format("%X", input));
	    
	    crcTemp = (short) ((short)input ^ crc);        		// Read next u16Temp byte.
	    crc = (short) (crc >> (short)0x0008);      			// Update u16crc.
	    crc &= 0xFF;
	    
	    Debug.i(TAG, FUNC_TAG, "updateCRC: "+String.format("%X", crc));
	    Debug.i(TAG, FUNC_TAG, "CRC Temp: "+String.format("%X", crcTemp));
	    
	    if ((crcTemp & (short)BIT7) > 0)
	    {
	    	Debug.i(TAG, FUNC_TAG, "BIT 7");
	        crc ^= CRC_MARK7;
	    }
	    Debug.i(TAG, FUNC_TAG, "updateCRC7: "+String.format("%X", crc));
	    
	    if ((crcTemp & (short)BIT6) > 0)
	    {
	    	Debug.i(TAG, FUNC_TAG, "BIT 6");
	        crc ^= CRC_MARK6;
	    }
	    Debug.i(TAG, FUNC_TAG, "updateCRC6: "+String.format("%X", crc));
	    
	    if ((crcTemp & (short)BIT5) > 0)
	    {
	    	Debug.i(TAG, FUNC_TAG, "BIT 5");
	        crc ^= CRC_MARK5;
	    }
	    Debug.i(TAG, FUNC_TAG, "updateCRC5: "+String.format("%X", crc));
	    
	    if ((crcTemp & (short)BIT4) > 0)
	    {
	    	Debug.i(TAG, FUNC_TAG, "BIT 4");
	        crc ^= CRC_MARK4;
	    }
	    Debug.i(TAG, FUNC_TAG, "updateCRC4: "+String.format("%X", crc));
	    
	    if ((crcTemp & (short)BIT3) > 0)
	    {
	    	Debug.i(TAG, FUNC_TAG, "BIT 3");
	        crc ^= CRC_MARK3;
	    }
	    Debug.i(TAG, FUNC_TAG, "updateCRC3: "+String.format("%X", crc));
	    
	    if ((crcTemp & (short)BIT2) > 0)
	    {
	    	Debug.i(TAG, FUNC_TAG, "BIT 2");
	        crc ^= CRC_MARK2;
	    }
	    Debug.i(TAG, FUNC_TAG, "updateCRC2: "+String.format("%X", crc));
	    
	    if ((crcTemp & (short)BIT1) > 0)
	    {
	    	Debug.i(TAG, FUNC_TAG, "BIT 1");
	        crc ^= CRC_MARK1;
	    }
	    Debug.i(TAG, FUNC_TAG, "updateCRC1: "+String.format("%X", crc));
	    
	    if ((crcTemp & (short)BIT0) > 0)
	    {
	    	Debug.i(TAG, FUNC_TAG, "BIT 0");
	        crc ^= CRC_MARK0;
	    }
	    Debug.i(TAG, FUNC_TAG, "updateCRC0: "+String.format("%X", crc));
	    
	    return(crc);
	}
	
	/***********************************************************************************/
	/***********************************************************************************/
	// Log Functions
	/***********************************************************************************/
	/***********************************************************************************/
	
//	public void debug_message(String tag, String message)
//	{
//		if(DEBUG)
//		{
//			Debug.i(tag, FUNC_TAG, message);
//		}
//	}
}
