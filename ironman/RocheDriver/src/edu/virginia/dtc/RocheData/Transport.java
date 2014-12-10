package edu.virginia.dtc.RocheData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import edu.virginia.dtc.RocheDriver.Driver;
import edu.virginia.dtc.RocheDriver.Driver.OutPacket;
import edu.virginia.dtc.RocheDriver.InterfaceData;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.TwoFish.Twofish_Algorithm;

public class Transport {	
	// TX/RX  packet buffer sizes
	private static final short NUM_DELIMITERS                = 2;           	// two delimter bytes one at the start of packet, and one at the end
	private static final byte MIN_PACKET_SIZE               = 28;      			// PACKET_HEADER_SIZE + AUTHEN_SIZE + NUM_DELIMITERS

	// Packets field sizes
	private static final byte DM_CLIENT_ID_SIZE       = 4;    			// # bytes in DM client ID
	private static final byte DM_DEVICE_ID_SIZE       = 13;   			// # bytes in DM device ID
	private static final byte ID_REQ_PAYLOAD_SIZE     = (byte) (DM_DEVICE_ID_SIZE +  DM_CLIENT_ID_SIZE);   		// # bytes in ID request payload
	private static final byte CRC_SIZE                = 2;       	// # bytes in CRC
	private static final byte VERSION                 = 0x10;    	// frame version:  major- bits 7 -4, minor bits 3 -0

	// Pump commands/responses
	public static final byte A_CON_REQ               	= 0x09;
	public static final byte A_KEY_REQ               	= 0x0C;
	public static final byte A_ID_REQ                	= 0x12;
	public static final byte A_KEY_AVA               	= 0x0F;
	public static final byte SYN                     	= 0x17;
	public static final byte CONNECT_ACCEPTED         	= 0x0A;
	public static final byte KEY_RESPONSE             	= 0x11;
	public static final byte ID_RESPONSE              	= 0x14;
	public static final byte SYN_ACK                  	= 0x18;
	public static final byte DISCONNECT               	= 0x1B;
	public static final byte ACK_RESPONSE             	= 0x05;
	public static final byte UNRELIABLE_DATA          	= 0x03;
	public static final byte RELIABLE_DATA            	= 0x23;
	public static final byte ERROR_RESPONSE           	= 0x06;

	// Failure incrementing nonce
	private static final byte NONCE_INC_FAILED  = (byte) 0xFF;
	
	// Security and Transport Layer commands
	private byte[] connect_pair_authenticate =
	{    
	// VERSION: 8 bits \ Seq#: 1 bit Res1: 1 bit(unused) REL: 1 bit  Command: 5 bits\ length: 16 bits LSB MSB\Source addr: 4 bits Dest addr: 4 bits\ Nonce: 104 bits\ Payload: \ Authentication Code
	        VERSION    ,                   A_CON_REQ                                ,       CRC_SIZE,0,                     (byte) 0xF0
	};
	private byte[] key_request =
	{
	// VERSION: 8 bits \ Seq#: 1 bit Res1: 1 bit(unused) REL: 1 bit  Command: 5 bits\ length: 16 bits LSB MSB\Source addr: 4 bits Dest addr: 4 bits\ Nonce: 104 bits\ Payload: \ Authentication Code
	        VERSION    ,                   A_KEY_REQ                                ,       CRC_SIZE,0,                     (byte) 0xF0
	};
	private byte[] key_available =
	{
	// VERSION: 8 bits \ Seq#: 1 bit Res1: 1 bit(unused) REL: 1 bit  Command: 5 bits\ length: 16 bits LSB MSB\Source addr: 4 bits Dest addr: 4 bits\ Nonce: 104 bits\ Payload: \ Authentication Code
	        VERSION    ,                   A_KEY_AVA                                ,       CRC_SIZE,0,                     (byte) 0xF0
	};
	private byte[] device_id =
	{
	// VERSION: 8 bits \ Seq#: 1 bit Res1: 1 bit(unused) REL: 1 bit  Command: 5 bits\ length: 16 bits LSB MSB\Source addr: 4 bits Dest addr: 4 bits\ Nonce: 104 bits\ Payload: \ Authentication Code
	        VERSION    ,                   A_ID_REQ                                 ,       ID_REQ_PAYLOAD_SIZE,0,           0
	};
	private byte[] disconnect =
	{
	// VERSION: 8 bits \ Seq#: 1 bit Res1: 1 bit(unused) REL: 1 bit  Command: 5 bits\ length: 16 bits MSB LSB\Source addr: 4 bits Dest addr: 4 bits\ Nonce: 104 bits\ Payload: \ Authentication Code
	        VERSION    ,                   DISCONNECT                               ,       0,0,                             0
	};
	private byte[] connect_normal =
	{
	// VERSION: 8 bits \ Seq#: 1 bit Res1: 1 bit(unused) REL: 1 bit  Command: 5 bits\ length: 16 bits LSB MSB\Source addr: 4 bits Dest addr: 4 bits\ Nonce: 104 bits\ Payload: \ Authentication Code
	        VERSION    ,                   SYN                                      ,       0,0,                             0
	};
	private byte[] ack =
	{
	// VERSION: 8 bits \ Seq#: 1 bit Res1: 1 bit(unused) REL: 1 bit  Command: 5 bits\ length: 16 bits LSB MSB\Source addr: 4 bits Dest addr: 4 bits\ Nonce: 104 bits\ Payload: \ Authentication Code
	        VERSION    ,                   ACK_RESPONSE                             ,       0,0,                             0
	};
	private byte[] data_packet =
	{
	// VERSION: 8 bits \ Seq#: 1 bit Res1: 1 bit(unused) REL: 1 bit  Command: 5 bits\ length: 16 bits LSB MSB\Source addr: 4 bits Dest addr: 4 bits\ Nonce: 104 bits\ Payload: \ Authentication Code
	        VERSION    ,                   UNRELIABLE_DATA                          ,       0,0,                             0
	};
	private byte[] error_message =
	{
	// VERSION: 8 bits \ Seq#: 1 bit Res1: 1 bit(unused) REL: 1 bit  Command: 5 bits\ length: 16 bits LSB MSB\Source addr: 4 bits Dest addr: 4 bits\ Nonce: 104 bits\ Payload: \ Authentication Code
	        VERSION    ,                   ERROR_RESPONSE                           ,       1,0,                             0
	};
	
	public static final int SEND_CONNECT_PAIR_AUTHENTICATE = 0;
	public static final int SEND_KEY_REQUEST = 1;
	public static final int SEND_KEY_AVAILABLE = 2;
	public static final int SEND_ID_REQUEST = 3;
	public static final int SEND_SYN = 4;
	public static final int SEND_RELIABLE_DATA = 5;
	public static final int SEND_ACK = 6;
	public static final int SEND_UNRELIABLE_DATA = 7;
		
	private static final String TAG = "Transport";
	
	private static int state, prevState;
	
	public Security s = new Security();
	public Packet p = new Packet();
	public Key k = new Key();
	
	private Driver drv;
	private Handler handler;
	
	private static final int TX_THREAD_SLEEP = 50;
	public static Queue<OutPacket> txMessages;
	private static volatile Thread transmit;
	public static boolean txRunning, txStop;
	
	private static final int RX_THREAD_SLEEP = 5;
	private static volatile Thread receive;
	public static boolean rxRunning, rxStop;
	
	public Transport(Driver inst)
	{
		drv = inst;
		InterfaceData.getInstance();
		
		state = Driver.NONE;
		prevState = Driver.NONE;
		
		handler = new Handler();
		
		Debug.i(TAG, "resetDriver", "Cancelling response timer...");
				
		p.resetPacketLayer();
		
		Debug.i(TAG, "resetDriver", "Starting to stop transmit thread!");
		txStop = true;
		//Close transmit thread
		if(transmit != null)
		{
			if(transmit.isAlive())
			{
				try {
					transmit.join();
					txRunning = false;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		Debug.i(TAG, "resetDriver", "Starting to stop receive thread!");
		rxStop = true;
		//Close transmit thread
		if(receive != null)
		{
			if(receive.isAlive())
			{
				try {
					receive.join();
					rxRunning = false;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		Debug.i(TAG, "resetDriver", "Starting communication threads!");
		
		startTransmitThread();
		startReceiveThread();
	}
	
	public void incrementTxNonce()
	{
		p.incrementArray(Packet.nonceTx);			//TODO: use the output to check overflow and errors
	}
	
	public void incrementRxNonce()
	{
		p.incrementArray(Packet.nonceRx);
	}
	
	/******************************************************************************************************************************************************************************/
	/******************************************************************************************************************************************************************************/
	//	   _______                            _ _     ______                _   _                 
	//	  |__   __|                          (_) |   |  ____|              | | (_)                
	//	     | |_ __ __ _ _ __  ___ _ __ ___  _| |_  | |__ _   _ _ __   ___| |_ _  ___  _ __  ___ 
	//	     | | '__/ _` | '_ \/ __| '_ ` _ \| | __| |  __| | | | '_ \ / __| __| |/ _ \| '_ \/ __|
	//	     | | | | (_| | | | \__ \ | | | | | | |_  | |  | |_| | | | | (__| |_| | (_) | | | \__ \
	//	     |_|_|  \__,_|_| |_|___/_| |_| |_|_|\__| |_|   \__,_|_| |_|\___|\__|_|\___/|_| |_|___/
	//	                                                                                          
	/******************************************************************************************************************************************************************************/
	/******************************************************************************************************************************************************************************/
	
	private void startTransmitThread()
	{
		final String FUNC_TAG = "startTransmitThread";

		txMessages = new ConcurrentLinkedQueue<OutPacket>();				//Initialize the TX queue
		
		if(!txRunning)
		{
			txRunning = true;
			
			transmit = new Thread ()
			{
				public void run ()
				{
					Debug.i("Thread", FUNC_TAG, "TX Thread starting!");
					txStop = false;
				
					while(!txStop)
					{
						OutPacket op = txMessages.poll();
						if(op != null)
						{
							Debug.i("Thread", FUNC_TAG, "Size of TxBuffer: "+txMessages.size());
							
							drv.command = op.descrip;
							Debug.i(TAG, FUNC_TAG, "Transmitting: " + op.descrip + " | Seq No: "+String.format("%X",op.sequence) + " | Size of buffer: "+op.packet.length);
							InterfaceData.remotePumpBt.write(op.packet);		//Write it out
							
							Driver.stats.txPackets++;
						}
						
						try {
							Thread.sleep(TX_THREAD_SLEEP);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			};
			
			transmit.start();
		}
	}
	
	private void sendTransportLayerCommand(int command, ByteBuffer payload, String commDescrip)
	{	
		final String FUNC_TAG = "sendTransportLayerCommand";

		List<Byte> packet = null;
		boolean reliable = true;
		String descrip = "Unknown";
		byte seq = (byte) 0xFF;
		
		switch(command)
		{
			case SEND_CONNECT_PAIR_AUTHENTICATE:			/************************************************************************/
				descrip = "CONNECT_PAIR_AUTH";
				packet = p.buildPacket(connect_pair_authenticate, null, false);
				s.addCrc(packet);
				p.frameEscaping(packet);
				break;
				
			case SEND_KEY_REQUEST:							/************************************************************************/
				descrip = "KEY_REQUEST";
				packet = p.buildPacket(key_request, null, false);
				s.addCrc(packet);
				p.frameEscaping(packet);
				break;
				
			case SEND_KEY_AVAILABLE:						/************************************************************************/
				descrip = "KEY_AVAIL";
				packet = p.buildPacket(key_available, null, false);
				s.addCrc(packet);
				p.frameEscaping(packet);
				break;
				
			case SEND_ID_REQUEST:							/************************************************************************/
				descrip = "ID_REQ";
				p.resetTxNonce();														//Reset TX Nonce (previous to this the nonce is not used and is zero)
				incrementTxNonce();														//Increment it to 1
				
				ByteBuffer ids = ByteBuffer.allocate(17);								//Allocate payload
				
				String btName = InterfaceData.getInstance().bt.getName();				//Get the Device ID
				
				Debug.i(TAG, FUNC_TAG, "BT Friendly Name: "+btName);
				
				byte[] deviceId = new byte[13];
				for(int i=0;i<deviceId.length;i++)
				{
					if(i < btName.length())
						deviceId[i] = (byte)btName.charAt(i);
					else
						deviceId[i] = (byte)0x00;
				}
				
				String dat = "";
				for(byte b:deviceId)
					dat += String.format("%02X ", b);
				Debug.i(TAG, FUNC_TAG, "Device ID: "+dat);
				
				String swver = "5.04";													//Get the SW Version
				int clientId = 0;
				
				clientId += (((byte)swver.charAt(3)) - 0x30);			
				clientId += (((byte)swver.charAt(2)) - 0x30)*10;
				clientId += (((byte)swver.charAt(0)) - 0x30)*100;
				clientId += (10000);
				
				Debug.i(TAG, FUNC_TAG, "Client ID: "+String.format("%X", clientId));
				
				ids.order(ByteOrder.LITTLE_ENDIAN);
				ids.putInt(clientId);
				ids.put(deviceId);
				
				dat = "";																//Print payload
				for(byte b:ids.array())
					dat += String.format("%02X ", b);
				Debug.i(TAG, FUNC_TAG, "Payload: "+dat);
				
				packet = p.buildPacket(device_id, ids, true);							//Use real address (gathered in Key Response)
				packet = s.ccmAuthenticate(packet, drv.dp_key, Packet.nonceTx);			//Add U-MAC (Use D->P key)
				
				p.frameEscaping(packet);												//Escape packet
				break;
			case SEND_SYN:									/************************************************************************/
				descrip = "SYN";
				incrementTxNonce();
				
				packet = p.buildPacket(connect_normal, null, true);
				packet = s.ccmAuthenticate(packet, drv.dp_key, Packet.nonceTx);
				
				p.frameEscaping(packet);
				break;
			case SEND_RELIABLE_DATA:						/************************************************************************/
				descrip = "RELIABLE_DATA";
				incrementTxNonce();
				
				packet = p.buildPacket(data_packet, payload, true);					//Add the payload, set the address if valid
				
				seq = drv.seqNo;
				packet.set(1, setSeqRel(packet.get(1), true));						//Set the sequence and reliable bits
				
				adjustLength(packet, payload.capacity());							//Set the payload length
				
				packet = s.ccmAuthenticate(packet, drv.dp_key, Packet.nonceTx);		//Authenticate packet
				
				p.frameEscaping(packet);
				break;
			case SEND_UNRELIABLE_DATA:
				descrip = "UNRELIABLE_DATA";
				incrementTxNonce();
				
				packet = p.buildPacket(data_packet, payload, true);					//Add the payload, set the address if valid
				
				seq = drv.seqNo;
				
				adjustLength(packet, payload.capacity());							//Set the payload length
				
				packet = s.ccmAuthenticate(packet, drv.dp_key, Packet.nonceTx);		//Authenticate packet
				
				p.frameEscaping(packet);
				break;
			case SEND_ACK:									/************************************************************************/
				descrip = "ACK";
				incrementTxNonce();
				
				packet = p.buildPacket(ack, null, true);
				
				//drv.recvSeqNo ^= 0x80;
				
				seq = drv.recvSeqNo;
				packet.set(1, (byte) (packet.get(1) | drv.recvSeqNo));				//OR the received sequence number
				
				packet = s.ccmAuthenticate(packet, drv.dp_key, Packet.nonceTx);
				
				p.frameEscaping(packet);
				//reliable = false;													//Use this to make transmission asynchronous (not threaded)
				break;
		}
		
		if(packet != null)
		{
//			Debug.i(TAG, FUNC_TAG, "TX Packet Start ------------------------------------");
//			String dat = "";
//			for(byte b:packet)
//			{
//				dat += String.format("%02X ", b);
//			}
//			Debug.i(TAG, FUNC_TAG, dat);
//			Debug.i(TAG, FUNC_TAG, "TX Packet End --------------------------------------");
		}
		else
			Debug.i(TAG, FUNC_TAG, "Packet is null!");
		
		saveNonce();
		
		addTxPacket(packet, reliable, seq, descrip + " " + commDescrip);
	}
	
	private void adjustLength(List<Byte> packet, int length)
	{
		packet.set(2, (byte) (length & 0xFF));
		packet.set(3, (byte) (length >> 8));
		
		//Debug.i(TAG, FUNC_TAG, "Length bytes > "+String.format("%02X", packet.get(2))+" | "+String.format("%02X", packet.get(3)));
	}
	
	private Byte setSeqRel(Byte b, boolean rel)
	{
		b = (byte) (b | drv.seqNo);			//Set the sequence bit
		
		if(rel)
			b = (byte) (b |0x20);			//Set the reliable bit
				
		//Debug.i(TAG, FUNC_TAG, "Byte for Seq and Rel: "+String.format("%02X", b));
		
		drv.seqNo ^= 0x80;
		
		return b;
	}
	
	public void sendConnect()
	{
		//Basically the entry point to system start up, pairing and authentication begins here!
		Driver.setMode(Driver.PAIRING_AUTH);
		setState(Transport.P1_CONNECT);
		runFSM();
	}
	
	public void sendReliableData(ByteBuffer payload, String commDescrip)
	{
		sendTransportLayerCommand(Transport.SEND_RELIABLE_DATA, payload, commDescrip);
	}
	
	public void sendUnreliableData(ByteBuffer payload, String commDescrip)
	{
		sendTransportLayerCommand(Transport.SEND_UNRELIABLE_DATA, payload, commDescrip);
	}
	
	private void addTxPacket(List<Byte> in, boolean reliable, byte seq, String s)
	{
		byte[] out = new byte[in.size()];
		
		for(int i=0;i<out.length;i++)		//Stupid conversion to byte[]
			out[i] = in.get(i);
		
		OutPacket op = drv.new OutPacket(out, reliable, s, seq);
		
		txMessages.offer(op);
	}

	
	/******************************************************************************************************************************************************************************/
	/******************************************************************************************************************************************************************************/
	//	  _____               _             ______                _   _                 
	//	 |  __ \             (_)           |  ____|              | | (_)                
	//	 | |__) |___  ___ ___ ___   _____  | |__ _   _ _ __   ___| |_ _  ___  _ __  ___ 
	//	 |  _  // _ \/ __/ _ \ \ \ / / _ \ |  __| | | | '_ \ / __| __| |/ _ \| '_ \/ __|
	//	 | | \ \  __/ (_|  __/ |\ V /  __/ | |  | |_| | | | | (__| |_| | (_) | | | \__ \
	//	 |_|  \_\___|\___\___|_| \_/ \___| |_|   \__,_|_| |_|\___|\__|_|\___/|_| |_|___/
	//	                                                                                 
	/******************************************************************************************************************************************************************************/
	/******************************************************************************************************************************************************************************/
	
	private void startReceiveThread()
	{
		final String FUNC_TAG = "startReceiveThread";
		
		if(!rxRunning)
		{
			rxRunning = true;
			
			receive = new Thread ()
			{
				public void run ()
				{
					Debug.i("Thread", FUNC_TAG, "RX Thread starting!");
					rxStop = false;
				
					while(!rxStop)
					{
						// Pump message parsing
						byte[] ip = InterfaceData.pumpMessages.poll();		//Pulls data directly from BluetoothConn
						if(ip != null)
						{
							Debug.i("Thread", FUNC_TAG, "RX Data Received, remaining messages: "+InterfaceData.pumpMessages.size());
							
//							String s = "";
//							for(byte b:ip)
//								s += String.format("%02X ", b);
//							Debug.i(TAG, FUNC_TAG, "RX Buffer: "+s);
							
							//Process buffer
							drv.t.processRx(ip);
						}
						
						try {
							Thread.sleep(RX_THREAD_SLEEP);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			};
			
			receive.start();
		}
	}
	
	public void processRx(byte[] buffer)
	{
		final String FUNC_TAG = "processRx";
		
		boolean rel = false;
		
		List<Byte> bufList = new ArrayList<Byte>();
		for(int i=0;i<buffer.length;i++)
			bufList.add(buffer[i]);
		
		List<List<Byte>> complete = p.frameDeEscaping(bufList);
		
		for(List<Byte> p:complete)
		{
			if(p != null && p.size() >= (MIN_PACKET_SIZE-NUM_DELIMITERS))
			{
				
//				Debug.i(TAG, FUNC_TAG, "RX Packet Start ------------------------------------");
//				String dat = "";
//				for(byte b:p)
//				{
//					dat += String.format("%02X ", b);
//				}
//				Debug.i(TAG, FUNC_TAG, "Packet data: " + dat);
//				Debug.i(TAG, FUNC_TAG, "RX Packet End --------------------------------------");
								
				if((p.get(1) & 0x20) == 0x20)
				{
					rel = true;
					
					byte seq = 0x00;
					if((p.get(1) & 0x80)==0x80)
						seq = (byte) 0x80;
					
					drv.recvSeqNo = seq;
					sendTransportLayerCommand(Transport.SEND_ACK, null, "");				//Send ACK right away
				}
				else
				{
					rel = false;
				}
				
				Driver.stats.rxPackets++;
				parseRx(p, rel);
			}
			else
				Driver.stats.skippedPackets++;
		}
	}
	
	public void parseRx(List<Byte> packet, boolean rel)
	{
		final String FUNC_TAG = "parseRx";

		Byte command = (byte) (packet.get(1) & 0x1F);
		boolean expected = false;
		String descrip = "";
		
		RochePacket p = drv.t.parsePacket(packet);
		
		byte seq = 0x00;
		if((packet.get(1) & 0x80)==0x80)
			seq = (byte) 0x80;
		else
			seq = (byte) 0x00;
		
		switch(command)
		{
			case Transport.CONNECT_ACCEPTED:	/****************************************************************************************/
				
				descrip = "CONNECT_ACCEPTED";
				
				sendTransportLayerCommand(Transport.SEND_KEY_REQUEST, null, "");
				
				//POST ALERT FOR ENTERING 10-DIGIT KEY
				
				//Turn off timeout timer for connection start
				if(Driver.timeoutTimer != null)
					Debug.i(TAG, FUNC_TAG, "Timeout timer: "+Driver.timeoutTimer.cancel(true));
				
				handler.post(new Runnable()														
				{
					public void run()
					{
						AlertDialog.Builder alert = new AlertDialog.Builder(drv.ui);
						final EditText input = new EditText(drv.ui);
				    	input.setInputType(InputType.TYPE_CLASS_NUMBER);
				    	
				    	alert.setTitle("Passkey Entry");
						alert.setMessage("Please enter 10-digit key from the pump screen:");
						alert.setCancelable(false);
						alert.setView(input);
						
						alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
							
							}
						});
						final AlertDialog dialog = alert.create();
						dialog.show();
						
						dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
						{            
					          
					          public void onClick(View v) {
						        	String key = input.getText().toString();
									
									if((!TextUtils.isEmpty(key))&&(key.length() == 10))
									{
										Debug.i(TAG, FUNC_TAG, "parseRx >>> "+key);
										
										drv.k_10 = k.generateKey(key);
										
										String d = "";
										for(byte b:drv.k_10)
											d += String.format("%02X ", b);
										Debug.i(TAG, FUNC_TAG, "parseRx >>> K_10: "+d);
										
										if(getState() == Transport.P1_AWAIT_CONNECT_RESP)
										{
											setState(Transport.P1_KEY_AVA);
											runFSM();
										}
										dialog.dismiss();
									}
						            else
						            {
						            	Toast.makeText(input.getContext(), "The key must be 10-digit long.", Toast.LENGTH_SHORT).show();
									}
							  }
					      });
					}
				});
				
				break;
			case Transport.KEY_RESPONSE:		/****************************************************************************************/
				
				descrip = "KEY_RESPONSE";
				
				if(p != null)
				{
					Object weak_session = null;
					
					try 
					{
						//CREATE THE WEAK KEY OBJECT WITH THE 10 DIGIT WEAK KEY	
						weak_session = Twofish_Algorithm.makeKey(drv.k_10);
					} 
					catch (InvalidKeyException e) 
					{
						e.printStackTrace();
					}
						
					if(drv.t.s.ccmVerify(p, weak_session, p.umac) && weak_session != null)
					{
						//DECRYPT THE 128-BIT KEYS WITH THE WEAK KEY
						
						drv.addresses = (byte)((p.addresses << 4) & 0xF0);		//Get the address and reverse it since source and destination are reversed from the RX packet
						
						byte[] key_pd = new byte[16];							//Get the bytes for the keys
						byte[] key_dp = new byte[16];
						
						p.pBuf.rewind();
						p.pBuf.get(key_pd, 0, key_pd.length);
						p.pBuf.get(key_dp, 0, key_dp.length);
						
						String d = "";
						for(byte b:key_pd)
							d += String.format("%02X ", b);
						Debug.i(TAG, FUNC_TAG, "parseRx >>> Key_PD: "+d);
						
						d = "";
						for(byte b:key_dp)
							d += String.format("%02X ", b);
						Debug.i(TAG, FUNC_TAG, "parseRx >>> Key_DP: "+d);
						
						byte[] key_pd_de = Twofish_Algorithm.blockDecrypt(key_pd, 0, weak_session);
						byte[] key_dp_de = Twofish_Algorithm.blockDecrypt(key_dp, 0, weak_session);
						
						saveKeysToPrefs(key_pd_de, key_dp_de);
						
						d = "";
						for(byte b:key_pd_de)
							d += String.format("%02X ", b);
						Debug.i(TAG, FUNC_TAG, "parseRx >>> Decrytped PD: "+d);
						
						d = "";
						for(byte b:key_dp_de)
							d += String.format("%02X ", b);
						Debug.i(TAG, FUNC_TAG, "parseRx >>> Decrytped DP: "+d);
						
						//CREATE THE KEY OBJECTS (WHITENING SUBKEYS, ROUND KEYS, S-BOXES)
						try 
						{
							drv.pd_key = Twofish_Algorithm.makeKey(key_pd_de);
							drv.dp_key = Twofish_Algorithm.makeKey(key_dp_de);
						} 
						catch (InvalidKeyException e) 
						{
							e.printStackTrace();
						}
						
						if(getState() == Transport.P1_AWAIT_KEY_RESP)
						{
							setState(Transport.P2_ID_REQ);
						}
					}
					else
					{
						Debug.i(TAG, FUNC_TAG, "parseRx >>> KEY_RESPONSE Verification Failed!");	
						
						handler.post(new Runnable(){
							public void run() {
								setState(Transport.P1_AWAIT_CONNECT_RESP);
								
								//drv.resetDriver();
								AlertDialog.Builder alert = new AlertDialog.Builder(drv.ui);
								final EditText input = new EditText(drv.ui);
						    	input.setInputType(InputType.TYPE_CLASS_NUMBER);
						    	
						    	alert.setTitle("Passkey Entry");
								alert.setMessage("Please enter 10-digit key from the pump screen:");
								alert.setCancelable(false);
								alert.setView(input);
								
								alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int whichButton) {
									
									}
								});
								final AlertDialog dialog = alert.create();
								dialog.show();
								
								dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
								{            
							          
							          public void onClick(View v) {
								        	String key = input.getText().toString();
											
											if((!TextUtils.isEmpty(key))&&(key.length() == 10))
											{
												Debug.i(TAG, FUNC_TAG, "parseRx >>> "+key);
												
												drv.k_10 = k.generateKey(key);
												
												String d = "";
												for(byte b:drv.k_10)
													d += String.format("%02X ", b);
												Debug.i(TAG, FUNC_TAG, "parseRx >>> K_10: "+d);
												
												if(getState() == Transport.P1_AWAIT_CONNECT_RESP)
												{
													setState(Transport.P1_KEY_AVA);
													runFSM();
												}
												dialog.dismiss();
											}
								            else
								            {
								            	Toast.makeText(input.getContext(), "The key must be 10-digit long.", Toast.LENGTH_SHORT).show();
											}
									  }
							      });
							}
						});
						return;
					}
				}
				break;
			case Transport.ID_RESPONSE:			/****************************************************************************************/
				
				descrip = "ID_RESPONSE";
				
				if(p != null)
				{
					if(drv.t.s.ccmVerify(p, drv.pd_key, p.umac))
					{
						byte[] device = new byte[13];
						
						p.pBuf.order(ByteOrder.LITTLE_ENDIAN);
						drv.serverId = p.pBuf.getInt();
						p.pBuf.get(device);
						drv.deviceId = new String(device);
						
						Debug.i(TAG, FUNC_TAG, "Server ID: "+String.format("%X", drv.serverId)+" Device ID: "+drv.deviceId);
						
						if(getState() == Transport.P2_AWAIT_ID_RESP)
							setState(Transport.P3_SYN);
					}
					else
					{
						Debug.i(TAG, FUNC_TAG, "parseRx >>> ID_RESPONSE Verification Failed!");
					}
				}
				break;
				
			case Transport.SYN_ACK:				/****************************************************************************************/
				
				descrip = "SYN_ACK";
				
				if(p != null)
				{
					if(drv.t.s.ccmVerify(p, drv.pd_key, p.umac))
					{
						if(getState() == Transport.P3_SYN_ACK)
						{
							drv.seqNo = 0x00;
							
							Debug.i(TAG, FUNC_TAG, "Sequence Number reset!");
							Debug.i(TAG, FUNC_TAG, "parseRx >>> Sending APP_SEND_CONNECT!");
							
							if(Driver.getMode() == Driver.PAIRING_AUTH)
								setState(Transport.P3_APP_CONNECT);
						}
						else if(getState() == Transport.P3_SYN_DIS_RESP)
						{
							Debug.i(TAG, FUNC_TAG, "Resetting TX layer of pump after binding...");
							setState(Transport.P3_APP_DISCONNECT);
						}
						else if(getState() == Transport.CM_SYN_RESP)
						{
							setState(Transport.CM_SYN_ACKD);
						}
					}
					else
					{
						Debug.i(TAG, FUNC_TAG, "parseRx >>> SYN_ACK Verification Failed!");
					}
				}
				break;
				
			case Transport.DISCONNECT:			/****************************************************************************************/
				
				descrip = "DISCONNECT";
				
				if(p != null)
				{
					if(drv.t.s.ccmVerify(p, drv.pd_key, p.umac))
					{
					}
				}
				
				break;
				
			case Transport.ACK_RESPONSE:		/****************************************************************************************/
				
				descrip = "ACK_RESPONSE";
				
				if(p != null)
				{
					if(drv.t.s.ccmVerify(p, drv.pd_key, p.umac))
					{
					}
				}
				
				break;
				
			case Transport.RELIABLE_DATA:		/****************************************************************************************/
			case Transport.UNRELIABLE_DATA:		/****************************************************************************************/
				
				if(rel)
				{
					//if(seq != expectedSeq)
					//	expected = false;
					
					descrip = "RELIABLE_DATA";
				}
				else
					descrip = "UNRELIABLE_DATA";
				
				if(p != null)
				{
					if(drv.t.s.ccmVerify(p, drv.pd_key, p.umac))
					{
						Application.appMessages.offer(drv.new InPacket(p.payload, rel));
					}
				}
				
				break;
				
			case Transport.ERROR_RESPONSE:		/****************************************************************************************/
				
				descrip = "ERROR_RESPONSE";
				
				if(p != null)
				{
					if(drv.t.s.ccmVerify(p, drv.pd_key, p.umac))
					{
						byte error = 0;
						String err = "";
						
						if(p.payload.length > 0)
							error = p.payload[0]; 
						
						switch(error)
						{
							case 0x00:
								err = "Undefined";
								break;
							case 0x0F:
								err = "Wrong state";
								drv.a.startMode(Driver.COMMAND, true);
								Debug.e(TAG, FUNC_TAG, "Forcing starting of command mode, since the transport layer broke!");
								break;
							case 0x33:
								err = "Invalid service primitive";
								break;
							case 0x3C:
								err = "Invalid payload length";
								break;
							case 0x55:
								err = "Invalid source address";
								break;
							case 0x66:
								err = "Invalid destination address";
								break;
						}
						
						Debug.e(TAG, FUNC_TAG, "Error in Transport Layer! ("+err+")");
						Driver.log("ROCHE", FUNC_TAG, "Error in Transport Layer! ("+err+")");
					}
				}
				
				break;
				
			default:							/****************************************************************************************/
				
				descrip = "UNKNOWN TRANSPORT PACKET!  ID: "+String.format("%X", command);
				
				break;
		}
		
		Debug.i(TAG, FUNC_TAG, "Receiving: "+descrip+" | Seq No: "+String.format("%X", seq) +" | Length: "+ packet.size() + ((expected) ? " | IGNORED" : ""));
		
		runFSM();		
	}
	
	public RochePacket parsePacket(List<Byte> packet)
	{
		byte[] buf = new byte[packet.size()];
		
		for(int i=0;i<buf.length;i++)
			buf[i] = packet.get(i);
			
		ByteBuffer buffer = ByteBuffer.wrap(buf);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		
		RochePacket p = new RochePacket(buffer);
		
		return p;
	}
	
	private void saveKeysToPrefs(byte[] pd, byte[] dp)
	{
		Editor edit = drv.settings.edit();
		
		for(int i=0;i<pd.length;i++)
		{
			edit.putInt("pd"+i, pd[i]);
		}
		
		for(int i=0;i<dp.length;i++)
		{
			edit.putInt("dp"+i, dp[i]);
		}
		
		edit.commit();
	}
	
	private void saveNonce()
	{
		Editor edit = drv.settings.edit();
		
		for(int i=0;i<Packet.nonceTx.length;i++)
		{
			edit.putInt("nonce"+i, Packet.nonceTx[i]);
		}
		
		edit.commit();
	}
	
	/******************************************************************************************************************************************************************************/
	/******************************************************************************************************************************************************************************/
	//	   __  __                           _               ______ _____ __  __ 
	//	  |  \/  |                         (_)             |  ____/ ____|  \/  |
	//	  | \  / | ___  ___ ___  __ _  __ _ _ _ __   __ _  | |__ | (___ | \  / |
	//	  | |\/| |/ _ \/ __/ __|/ _` |/ _` | | '_ \ / _` | |  __| \___ \| |\/| |
	//	  | |  | |  __/\__ \__ \ (_| | (_| | | | | | (_| | | |    ____) | |  | |
	//	  |_|  |_|\___||___/___/\__,_|\__, |_|_| |_|\__, | |_|   |_____/|_|  |_|
	//	                               __/ |         __/ |                      
	//	                              |___/         |___/                       
	/******************************************************************************************************************************************************************************/
	/******************************************************************************************************************************************************************************/
	
	public static final int P1_CONNECT = 1;
	public static final int P1_AWAIT_CONNECT_RESP = 2;
	public static final int P1_KEY_AVA = 3;
	public static final int P1_AWAIT_KEY_RESP = 4;
	public static final int P2_ID_REQ = 5;
	public static final int P2_AWAIT_ID_RESP = 6;
	public static final int P3_SYN = 7;
	public static final int P3_SYN_ACK = 8;
	public static final int P3_APP_CONNECT = 9;
	public static final int P3_BINDING = 10;
	public static final int P3_SYN_DIS = 11;
	public static final int P3_SYN_DIS_RESP = 12;
	public static final int P3_APP_DISCONNECT = 13;

	public static final int P_A_COMPLETE = 20;
	public static final int CONNECTED = 21;
	public static final int DISCONNECTED = 22;
	
	public static final int SERVICE_CON = 23;
	public static final int CM_SYN = 24;
	public static final int CM_SYN_RESP = 25;
	public static final int CM_SYN_ACKD = 26;

	private String getStateString(int st)
	{
		switch(st)
		{
			case P1_CONNECT: return "P1_CONNECT";
			case P1_AWAIT_CONNECT_RESP: return "P1_AWAIT_CONNECT_RESP";
			case P1_KEY_AVA: return "P1_KEY_AVA";
			case P1_AWAIT_KEY_RESP: return "P1_AWAIT_KEY_RESP";
			case P2_ID_REQ: return "P2_ID_REQ";
			case P2_AWAIT_ID_RESP: return "P2_AWAIT_ID_RESP";
			case P3_SYN: return "P3_SYN";
			case P3_SYN_ACK: return "P3_SYN_ACK";
			case P3_APP_CONNECT: return "P3_APP_CONNECT";
			case P3_BINDING: return "P3_BINDING";
			case P3_SYN_DIS: return "P3_SYN_DIS";
			case P3_SYN_DIS_RESP: return "P3_SYN_DIS_RESP";
			case P3_APP_DISCONNECT: return "P3_APP_DISCONNECT";
	
			case P_A_COMPLETE: return "P_A_COMPLETE";
			case CONNECTED: return "CONNECTED";
			case DISCONNECTED: return "DISCONNECTED";
			
			case SERVICE_CON: return "SERVICE_CON";
			case CM_SYN: return "CM_SYN";
			case CM_SYN_RESP: return "CM_SYN_RESP";
			case CM_SYN_ACKD: return "CM_SYN_ACKD";
			
			default: return "UNKNOWN";
		}
	}
	
	public void synAck()
	{
		if(Driver.getMode() == Driver.COMMAND || Driver.getMode() == Driver.RT)
		{
			setState(Transport.CM_SYN);
			runFSM();
		}
	}
	
	public void finishPairingAuthentication()
	{
		final String FUNC_TAG = "finishPairingAuthentication";

		if(getState() == Transport.P3_BINDING)
			setState(Transport.P3_SYN_DIS);
		else
			Debug.i(TAG, FUNC_TAG, "Not in binding state, cannot complete P&A!");
		
		runFSM();
	}
	
	private int getState()
	{
		return state;
	}
	
	public void setState(int st)
	{		
		final String FUNC_TAG = "setState";

		drv.txFsm = getStateString(st);
		
		prevState = state;			//Save the previous state
		state = st;					//Set new state
		
		Debug.i(TAG, FUNC_TAG, "setState >>> State: "+state+" Prev: "+prevState);
	}
	
	private void runFSM()
	{
		switch(Driver.getMode())
		{
			case Driver.PAIRING_AUTH:
				runPairingAuthFSM();
				break;
			case Driver.COMMAND:
			case Driver.RT:
				runModeFSM();
				break;
			case Driver.IDLE:
				break;
			case Driver.NONE:
			default:
				break;
		}
	}
	
	private void runModeFSM()
	{
		final String FUNC_TAG = "runCommandFSM";

		switch(getState())
		{
			case CM_SYN:
				Debug.i(TAG, FUNC_TAG, "Sending SYN command!");
				sendTransportLayerCommand(Transport.SEND_SYN, null, "");
				setState(CM_SYN_RESP);
				break;
			case CM_SYN_ACKD:
				Debug.i(TAG, FUNC_TAG, "Sending SYN ACK signal!");
				setState(SERVICE_CON);
				drv.a.synAckd();
				break;
		}
	}
	
	private void runPairingAuthFSM()
	{
		final String FUNC_TAG = "runPairingAuthFSM";

		switch(getState())
		{
			case P1_CONNECT:
				sendTransportLayerCommand(Transport.SEND_CONNECT_PAIR_AUTHENTICATE, null, "");
				setState(Transport.P1_AWAIT_CONNECT_RESP);
				break;
			case P1_KEY_AVA:
				sendTransportLayerCommand(Transport.SEND_KEY_AVAILABLE, null, "");
				setState(Transport.P1_AWAIT_KEY_RESP);
				break;
			case P2_ID_REQ:
				sendTransportLayerCommand(Transport.SEND_ID_REQUEST, null, "");
				setState(Transport.P2_AWAIT_ID_RESP);
				break;
			case P3_SYN:
				sendTransportLayerCommand(Transport.SEND_SYN, null, "");
				setState(Transport.P3_SYN_ACK);
				break;
			case P3_APP_CONNECT:
				drv.a.startP3();				
				setState(Transport.P3_BINDING);
				break;
			case P3_SYN_DIS:
				sendTransportLayerCommand(Transport.SEND_SYN, null, "");
				setState(Transport.P3_SYN_DIS_RESP);
				break;
			case P3_APP_DISCONNECT:
				Debug.i(TAG, FUNC_TAG, "Telling Application layer to disconnect...");
				drv.a.disconnectAppLayer();
				if(Driver.getMode() == Driver.PAIRING_AUTH)
				{	
					Debug.i(TAG, FUNC_TAG, "Pairing and authentication complete!  Overall state set to IDLE!");
					setState(Transport.P_A_COMPLETE);
					
					//Delay this execution so the UI doesn't prematurely send the start for Command mode
					handler.postDelayed(new Runnable(){
						public void run() {
							Debug.e(TAG, FUNC_TAG, "Setting mode to idle...");
							Driver.setMode(Driver.IDLE);
							drv.a.startMode(Driver.COMMAND, false);
						}
					}, 5000);
					
					ContentValues dv = new ContentValues();

					Debug.e("Application", FUNC_TAG, "Adding to running pump DB value!");
					dv.put("running_pump", "edu.virginia.dtc.RocheDriver.RocheDriver");					
					
					Debug.w("Application", FUNC_TAG, "Running pump updated in Hardware Configuration table!");
					drv.serv.getContentResolver().update(Biometrics.HARDWARE_CONFIGURATION_URI, dv, null, null);
					
					Debug.i(TAG, FUNC_TAG, "Writing keys and MAC address to shared storage!");
					
					Editor edit = drv.settings.edit();
					
					edit.putInt("address", drv.addresses);
					edit.putString("mac", drv.deviceMac);
					edit.putBoolean("paired", true);
					
					edit.commit();
				}
				break;
		}
	}
}
