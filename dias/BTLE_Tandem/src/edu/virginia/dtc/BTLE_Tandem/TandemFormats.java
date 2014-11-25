package edu.virginia.dtc.BTLE_Tandem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TandemFormats {

	//TODO: add checksums in compare for extraction of data
	
	public static final String TAG = "TandemFormats";
	
	// Packet FSM
	public static final int NULL = 0;
	public static final int GET_DATA = 1;
	public static final int COMPLETE = 2;
	
	// Tandem Messaging Statics
	public static final int DELIMITER = 0x55;
	public static final int HDR_SIZE = 3;					//3 byte header (delimiter, type, and length)
	public static final int TAIL_SIZE = 6;					//Size of tail of packet (4 byte timestamp and 2 byte checksum)
	public static final long JAN12008 = 1199145600;			//Epoch time for January 1, 2008
	
	public static final int DELIM_POS = 0;
	public static final int TYPE_POS = 1;
	public static final int LEN_POS = 2;
	
	// Tandem Pump Messages
	public static final int GET_INTERNAL_TIME		 = 1;
	public static final int GET_TIME_DATE			 = 2;
	public static final int SET_TIME_DATE			 = 3;
	public static final int CANCEL_DELIVERY			 = 10;
	public static final int REQUEST_BOLUS			 = 11;
	public static final int BOLUS_STATUS			 = 12;
	public static final int BOLUS_END_TIME			 = 13;
	public static final int SET_FLUID_NAME			 = 20;
	public static final int GET_FLUID_REMAINING		 = 21;
	public static final int GET_FLUID_NAME			 = 22;
	public static final int CLEAR_HISTORY			 = 30;
	public static final int MARK_NEW_PATIENT		 = 31;
	public static final int GET_PUMP_STATUS			 = 40;
	public static final int REQUEST_CONFIRM			 = 50;
	public static final int CONFIRM_REQUEST			 = 51;
	public static final int NACK					 = 200;
	public static final int ACK						 = 201;
	public static final int END_CONNECTION			 = 202;
	
	//New Tandem Messages
	public static final int REQUEST_BOLUS_NO_TRACK	 = 14;			//DONE
	public static final int GET_PREV_BOLUS_ID		 = 15;			//DONE
	public static final int GET_FLUID_PUMPED		 = 23;
	public static final int GET_IOB					 = 24;			//DONE
	public static final int GET_TDD					 = 25;			//DONE
	public static final int GET_LOG_SIZE			 = 32;			//DONE
	public static final int GET_LOG_ENTRY			 = 33;			//DONE
	public static final int GET_FEAT_LOCK			 = 41;			//DONE
	public static final int SET_FEAT_LOCK			 = 42;			//DONE
	public static final int DECLARE_ALERT_ALARM		 = 43;			//DONE
	public static final int CLEAR_ALERT_ALARM		 = 44;			//DONE
	public static final int SET_TEMP_BASAL			 = 60;
	public static final int GET_TEMP_BASAL			 = 61;
	public static final int GET_PRO_SEG				 = 62;
	public static final int GET_CURRENT_PRO_SEG		 = 63;
	public static final int GET_CURRENT_BASAL_RATE	 = 64;
	public static final int GET_LAST_BASAL_TIME		 = 65;
	
	public TandemFormats() {}
	
	//TODO:  Check this for the message types...
	public static boolean isValidType(int type)
	{
		return true;
	}
	
	public class Header
	{
		private byte delimiter, type, length;
		private ByteBuffer buffer = ByteBuffer.allocate(3);
		
		public Header(int mess_type, int mess_length)
		{
			delimiter = DELIMITER;
			type = (byte)mess_type;
			length = (byte)mess_length;
			
			buffer.put(delimiter);
			buffer.put(type);
			buffer.put(length);
		}
		
		public byte[] getHeaderArray()
		{
			return buffer.array();
		}
		
		public ByteBuffer getHeaderBuffer()
		{
			return buffer;
		}
		
		public int getType()
		{
			return (int)(type & 0xFF);
		}
		
		public int getLength()
		{
			return (int)(length & 0xFF);
		}
	}

	public class Packet
	{
		private ByteBuffer buffer, payload;
		private Header header;
		
		public int timestamp;
		public short checksum;
		
		public Packet(Header hdr, byte[] cargo)
		{
			header = hdr;
			
			buffer = ByteBuffer.allocate(HDR_SIZE + TAIL_SIZE + hdr.getLength());
			buffer.put(hdr.getHeaderArray());
			
			if(cargo!=null)
			{
				payload = ByteBuffer.wrap(cargo);
				buffer.put(cargo);
			}
			
			timestamp = (int)((System.currentTimeMillis()/1000) - JAN12008);
			buffer.putInt(timestamp);
			
			int chk = 0;
			for(byte b:buffer.array())
				chk += (int)(b & 0xFF);
			chk -= DELIMITER;
			
			checksum = (short)chk;
			
			buffer.put((byte)((chk>>8) & 0xFF));
			buffer.put((byte)(chk & 0xFF));
		}
		
		public Packet(byte[] packet)
		{
			header = new Header(packet[1], packet[2]);
			
			buffer = ByteBuffer.allocate(HDR_SIZE + TAIL_SIZE + header.getLength());
			buffer.put(packet);
			
			if(header.getLength() > 0)
				payload = ByteBuffer.wrap(packet, 3, header.getLength());
			
			buffer.order(ByteOrder.BIG_ENDIAN);
			timestamp = buffer.getInt(packet.length-6);
			checksum = buffer.getShort(packet.length-2);
		}
		
		public int getType()
		{
			return header.getType();
		}
		
		public int getCargoLength()
		{
			return header.getLength();
		}
		
		public ByteBuffer getCargo()
		{
			return payload;
		}
		
		public byte[] getPacket()
		{
			return buffer.array();
		}
	}
	
	/**********************************************************************************
	 * Classes for extracting data and building packets for Tandem commands
	 **********************************************************************************/
	
	public class BolusEndTime
	{
		public static final String TYPE = "BolusEndTime";
		public static final int RESP_CNT = 1;
		
		public BolusEndTime() {}
		
		short year;
		byte month, day, hour, min, sec;
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == BOLUS_END_TIME)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				year = buffer.getShort();
				month = buffer.get();
				day = buffer.get();
				hour = buffer.get();
				min = buffer.get();
				sec = buffer.get();
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build(int id)
		{
			ByteBuffer cargo = ByteBuffer.allocate(4);
			cargo.order(ByteOrder.LITTLE_ENDIAN);
			cargo.putInt(id);
			
			Packet pkt = new Packet(new Header(BOLUS_END_TIME, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
	
	public class GetInternalTime
	{
		public static final String TYPE = "GetInternalTime";
		public static final int RESP_CNT = 1;
		
		public GetInternalTime() {}
		
		public int milliseconds;
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == GET_INTERNAL_TIME)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				milliseconds = buffer.getInt();
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(GET_INTERNAL_TIME, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class GetTimeDate
	{
		public static final String TYPE = "GetTimeDate";
		public static final int RESP_CNT = 1;
		
		public GetTimeDate() {}
		
		public short year;
		public byte month, day, hour, minute, second;
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == GET_TIME_DATE)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				year = buffer.getShort();
				month = buffer.get();
				day = buffer.get();
				hour = buffer.get();
				minute = buffer.get();
				second = buffer.get();
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(GET_TIME_DATE, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class SetTimeDate			//Only generated by the external device
	{
		public static final String TYPE = "SetTimeDate";
		public static final int RESP_CNT = 1;
		
		public SetTimeDate() {}
		
		public byte[] Build(int year, int month, int day, int hour, int min, int sec)
		{
			ByteBuffer cargo = ByteBuffer.allocate(7);
			cargo.order(ByteOrder.LITTLE_ENDIAN);
			cargo.putShort((short)year);
			cargo.put((byte)month);
			cargo.put((byte)day);
			cargo.put((byte)hour);
			cargo.put((byte)min);
			cargo.put((byte)sec);
			
			Packet pkt = new Packet(new Header(SET_TIME_DATE, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
	
	public class CancelDelivery			//Only generated by the external device
	{
		public static final String TYPE = "CancelDelivery";
		public static final int RESP_CNT = 1;
		
		public CancelDelivery() {}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(CANCEL_DELIVERY, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class RequestBolus			//Only generated by the external device
	{
		public static final String TYPE = "RequestBolus";
		public static final int RESP_CNT = 2;
		
		public RequestBolus() {}
		
		public byte[] Build(int id, float bolus)
		{
			ByteBuffer cargo = ByteBuffer.allocate(13);
			cargo.order(ByteOrder.LITTLE_ENDIAN);
			cargo.putInt(id);
			cargo.putFloat(bolus);
			cargo.putFloat((float)0);
			cargo.put((byte)0);
			
			Packet pkt = new Packet(new Header(REQUEST_BOLUS, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
	
	public class BolusStatus
	{
		public static final String TYPE = "BolusStatus";
		public static final int RESP_CNT = 2;
		
		public BolusStatus() {}
		
		public int id;
		public float requested, delivered;
		public byte status;
		public String statusString;
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == BOLUS_STATUS)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				id = buffer.getInt();
				requested = buffer.getFloat();
				delivered = buffer.getFloat();
				status = buffer.get();
				
				switch(status)
				{
					case 0: statusString = "Bolus pending, pump is preparing to deliver"; break;
					case 1: statusString = "Bolus is being delivered"; break;
					case 2: statusString = "Bolus delivered successfully"; break;
					case 3: statusString = "Bolus was cancelled"; break;
					case 4: statusString = "Bolus was interrupted by alarm"; break;
					case 5: statusString = "Invalid request or bolus ID"; break;
				}
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build(int id)
		{
			ByteBuffer cargo = ByteBuffer.allocate(13);
			cargo.order(ByteOrder.LITTLE_ENDIAN);
			cargo.putInt(id);
			cargo.putFloat((float)0);
			cargo.putFloat((float)0);
			cargo.put((byte)0);
			
			Packet pkt = new Packet(new Header(BOLUS_STATUS, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
	
	public class SetFluidName
	{
		public static final String TYPE = "SetFluidName";
		public static final int RESP_CNT = 1;
		
		public SetFluidName() {}
		
		public byte[] Build(byte[] name)
		{
			if(name.length < 10)
				return null;
			
			ByteBuffer cargo = ByteBuffer.allocate(10);
			cargo.put(name);
			
			Packet pkt = new Packet(new Header(SET_FLUID_NAME, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
	
	public class GetFluidRemaining
	{
		public static final String TYPE = "GetFluidRemaining";
		public static final int RESP_CNT = 1;
		
		public GetFluidRemaining() {}
		
		public float remaining;
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == GET_FLUID_REMAINING)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				remaining = buffer.getFloat();
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(GET_FLUID_REMAINING, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class GetFluidName
	{
		public static final String TYPE = "GetFluidName";
		public static final int RESP_CNT = 1;
		
		public GetFluidName() {}
		
		public byte[] name;
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == GET_FLUID_NAME)
			{
				//TODO: fix the byte[] to char[] thing
				
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				buffer.get(name);
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(GET_FLUID_NAME, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class ClearHistory		//Only generated by the external device
	{
		public static final String TYPE = "ClearHistory";
		public static final int RESP_CNT = 1;
		
		public ClearHistory() {}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(CLEAR_HISTORY, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class MarkNewPatient		//Only generated by the external device
	{
		public static final String TYPE = "MarkNewPatient";
		public static final int RESP_CNT = 1;
		
		public MarkNewPatient() {}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(MARK_NEW_PATIENT, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class GetPumpStatus
	{
		public static final String TYPE = "GetPumpStatus";
		public static final int RESP_CNT = 1;
		
		public GetPumpStatus() {}
		
		public int status;
		public String statusString;
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == GET_PUMP_STATUS)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				status = buffer.getInt();
				switch(status)
				{
					case 0: statusString = "Cartridge not loaded"; break;
					case 1: statusString = "Pump is ready to resume"; break;
					case 2: statusString = "Pump is ready to accept a bolus"; break;
					case 3: statusString = "Pump is in an alarm state"; break;
					case 4: statusString = "Pump is delivering a bolus"; break;
				}
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(GET_PUMP_STATUS, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class RequestConfirm		//Only generated by the pump
	{
		public static final String TYPE = "RequestConfirm";
		public static final int RESP_CNT = 1;
		
		public RequestConfirm() {}
		
		public byte[] cargo;
		public byte token;
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == REQUEST_CONFIRM)
			{
				ByteBuffer buffer = pkt.getCargo();			//I don't think I want little endian when reading in the whole array)
				cargo = new byte[pkt.getCargoLength()-1];
				buffer.get(cargo);
				token = buffer.get();
				
				return true;
			}
			else
				return false;
		}
	}
	
	public class ConfirmRequest		//Only generated by the external device
	{
		public static final String TYPE = "ConfirmRequest";
		public static final int RESP_CNT = 1;
		
		public ConfirmRequest() {}
		
		public byte[] Build(int type, int token)
		{
			ByteBuffer cargo = ByteBuffer.allocate(2);
			cargo.put((byte)type);
			cargo.put((byte)token);
			
			Packet pkt = new Packet(new Header(CONFIRM_REQUEST, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
	
	public class Nack		//Only generated by the pump
	{
		public static final String TYPE = "Nack";
		
		public Nack() {}
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == NACK)
				return true;
			else
				return false;
		}
	}
	
	public class Ack		//Only generated by the pump
	{
		public static final String TYPE = "Ack";
		
		public Ack() {}
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == ACK)
				return true;
			else
				return false;
		}
	}
	
	public class EndConnection
	{
		public static final String TYPE = "EndConnection";
		public static final int RESP_CNT = 1;
		
		public EndConnection() {}
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == END_CONNECTION)
				return true;
			else
				return false;
		}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(END_CONNECTION, 0), null);
			return pkt.getPacket();
		}
	}

	//NEW SET OF COMMANDS -----------------------------------------------
	
	public class RequestBolusNoTrack
	{
		public static final String TYPE = "RequestBolusNoTrack";
		
		public RequestBolusNoTrack() {}
		
		public byte[] Build(int id, float bolus)
		{
			ByteBuffer cargo = ByteBuffer.allocate(13);
			cargo.order(ByteOrder.LITTLE_ENDIAN);
			cargo.putInt(id);
			cargo.putFloat(bolus);
			cargo.putFloat((float)0);
			cargo.put((byte)0);
			
			Packet pkt = new Packet(new Header(REQUEST_BOLUS_NO_TRACK, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
	
	public class GetPrevBolusId
	{
		public static final String TYPE = "GetPrevBolusId";
		
		public int reqPrevId, id;
		
		public GetPrevBolusId(){}
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == GET_PREV_BOLUS_ID)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				reqPrevId = buffer.getInt();
				id = buffer.getInt();
				
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build(int id)
		{
			ByteBuffer cargo = ByteBuffer.allocate(4);
			cargo.order(ByteOrder.LITTLE_ENDIAN);
			cargo.putInt(id);
			
			Packet pkt = new Packet(new Header(GET_PREV_BOLUS_ID, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
	
	public class GetIob
	{
		public static final String TYPE = "GetIob";
		
		public float iob;
		public int timeRem;
		
		public GetIob(){}
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == GET_IOB)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				iob = buffer.getFloat();
				timeRem = buffer.getInt();
				
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(GET_IOB, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class GetTdd
	{
		public static final String TYPE = "GetTdd";
		
		public short year;
		public byte month, day;
		public float basalU, bolusU, tddU;
		
		public GetTdd(){}
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == GET_TDD)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				year = buffer.getShort();
				month = buffer.get();
				day = buffer.get();
				basalU = buffer.getFloat();
				bolusU = buffer.getFloat();
				tddU = buffer.getFloat();
				
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(GET_TDD, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class GetLogSize
	{
		public static final String TYPE = "GetLogSize";
		
		public int numEntries, firstSeq, lastSeq;
		
		public GetLogSize(){}
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == GET_LOG_SIZE)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				numEntries = buffer.getInt();
				firstSeq = buffer.getInt();
				lastSeq = buffer.getInt();
				
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(GET_LOG_SIZE, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class GetLogEntry
	{
		public static final String TYPE = "GetLogEntry";
		
		public short id;
		public int timestamp, fieldA, fieldB, fieldC, fieldD;
		
		public GetLogEntry(){}
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == GET_LOG_ENTRY)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				id = buffer.getShort();
				timestamp = buffer.getInt();
				fieldA = buffer.getInt();
				fieldB = buffer.getInt();
				fieldC = buffer.getInt();
				fieldD = buffer.getInt();
				
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build(int seq)
		{
			ByteBuffer cargo = ByteBuffer.allocate(4);
			cargo.order(ByteOrder.LITTLE_ENDIAN);
			cargo.putInt(seq);
			
			Packet pkt = new Packet(new Header(GET_LOG_ENTRY, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
	
	public class GetFeatLock
	{
		public static final String TYPE = "GetFeatLock";
		
		public byte lockState;
		
		public GetFeatLock(){}
		
		public boolean Extract(Packet pkt)
		{
			if(pkt.getType() == GET_FEAT_LOCK)
			{
				ByteBuffer buffer = pkt.getCargo();
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				lockState = buffer.get();
				
				return true;
			}
			else
				return false;
		}
		
		public byte[] Build()
		{
			Packet pkt = new Packet(new Header(GET_FEAT_LOCK, 0), null);
			return pkt.getPacket();
		}
	}
	
	public class SetFeatLock
	{
		public static final String TYPE = "SetFeatLock";
		
		public SetFeatLock(){}
		
		public byte[] Build(byte featLock)
		{
			ByteBuffer cargo = ByteBuffer.allocate(1);
			cargo.order(ByteOrder.LITTLE_ENDIAN);
			cargo.put(featLock);
			
			Packet pkt = new Packet(new Header(SET_FEAT_LOCK, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
	
	public class DeclareAlertAlarm
	{
		public static final String TYPE = "DeclareAlertAlarm";
		
		public DeclareAlertAlarm(){};
		
		public byte[] Build(short type, short delay, byte[] title, byte[] line1, byte[] line2, byte[] line3)
		{
			ByteBuffer cargo = ByteBuffer.allocate(164);
			cargo.order(ByteOrder.LITTLE_ENDIAN);
			cargo.putShort(type);
			cargo.putShort(delay);
			cargo.put(title);
			cargo.put(line1);
			cargo.put(line2);
			cargo.put(line3);
			
			Packet pkt = new Packet(new Header(DECLARE_ALERT_ALARM, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
	
	public class ClearAlertAlarm
	{
		public static final String TYPE = "ClearAlertAlarm";
		
		public ClearAlertAlarm(){};
		
		public byte[] Build(short type)
		{
			ByteBuffer cargo = ByteBuffer.allocate(2);
			cargo.order(ByteOrder.LITTLE_ENDIAN);
			cargo.putShort(type);
			
			Packet pkt = new Packet(new Header(CLEAR_ALERT_ALARM, cargo.capacity()), cargo.array());
			return pkt.getPacket();
		}
	}
}
