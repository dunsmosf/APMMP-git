/*     */ package com.dexcom.G4DevKit;

import android.util.Log;

/*     */ 
/*     */ class PacketTools
/*     */ {
/*   9 */   byte[] m_Packet = new byte[1590];
/*  10 */   short PacketLength = GetPacketLength(this.m_Packet);
/*     */   static final short MaxPayloadLength = 1584;
/*     */   static final short MinPacketLength = 6;
/*     */   static final short MaxPacketLength = 1590;
/*     */   static final byte SOF = 1;
/*     */   static final int OffsetToPacketSOF = 0;
/*     */   static final int OffsetToPacketLength = 1;
/*     */   static final int OffsetToPacketCommand = 3;
/*     */   static final int OffsetToPacketPayload = 4;
/*     */   private static final int SizeOfByte = 1;
/*     */   private static final int SizeOfInt = 4;
/*     */   private static final int SizeOfShort = 2;
/*     */   private static final int SizeOfLong = 8;
/*     */ 
/*     */   short GetPacketLength(byte[] Packet)
/*     */   {
/*  30 */     return Packet[1];
/*     */   }
/*     */ 
/*     */   void ClearPacket()
/*     */   {
/*  36 */     for (int i = 0; i < this.m_Packet.length; i++)
/*     */     {
/*  38 */       this.m_Packet[i] = 0;
/*     */     }
/*     */   }
/*     */ 
/*     */   byte[] NewCopyOfPacket()
/*     */   {
/*  46 */     byte[] data = (byte[])null;
/*     */ 
/*  48 */     int length = this.PacketLength;
/*     */ 
/*  50 */     if ((length >= 6) && (length <= this.m_Packet.length))
/*     */     {
/*  52 */       data = new byte[length];
/*  53 */       System.arraycopy(this.m_Packet, 0, data, 0, length);
/*     */     }
/*     */ 
/*  56 */     return data;
/*     */   }
/*     */ 
/*     */   void OverwritePacketSOF(byte newValue)
/*     */   {
/*  62 */     this.m_Packet[0] = newValue;
/*     */   }
/*     */ 
/*     */   void OverwritePacketLength(short newLength)
/*     */   {
/*  68 */     StoreBytes(newLength, this.m_Packet, 1);
/*     */   }
/*     */ 
/*     */   void OverwritePacketCommand(byte newCommand)
/*     */   {
/*  74 */     this.m_Packet[3] = newCommand;
/*     */   }
/*     */ 
/*     */   void OverwritePacketCrc(short newCrc)
/*     */     throws Exception
/*     */   {
/*  80 */     int length = this.PacketLength;
/*     */ 
/*  82 */     if ((length >= 6) && (length <= this.m_Packet.length))
/*     */     {
/*  84 */       StoreBytes(newCrc, this.m_Packet, length - 2);
/*     */     }
/*     */     else
/*     */     {
/*  88 */       throw new Exception(
/*  89 */         "Failed to overwrite packet CRC because packet length not valid.");
/*     */     }
/*     */   }
/*     */ 
/*     */   void UpdatePacketCrc() throws Exception
/*     */   {
/*  95 */     int length = this.PacketLength;
/*     */ 
/*  97 */     if ((length >= 6) && (length <= this.m_Packet.length))
/*     */     {
/*  99 */       short crc = Crc.CalculateCrc16(this.m_Packet, 0, length - 2);
/* 100 */       StoreBytes(crc, this.m_Packet, length - 2);
/*     */     }
/*     */     else
/*     */     {
/* 104 */       throw new Exception(
/* 105 */         "Failed to overwrite packet CRC because packet length not valid.");
/*     */     }
/*     */   }
/*     */ 
/*     */   static byte GetStartOfFrameFromPacket(byte[] packet)
/*     */   {
/* 112 */     return packet[0];
/*     */   }
/*     */ 
/*     */   static byte GetCommandByteFromPacket(byte[] packet)
/*     */   {
/* 118 */     return packet[3];
/*     */   }
/*     */ 
/*     */   static short GetPayloadSizeFromPacket(byte[] packet)
/*     */   {
/* 138 */     return packet[1];
/*     */   }
/*     */ 
/*     */   static short GetCrcFromEndOfPacket(byte[] packet)
/*     */   {
/* 144 */     short crc = packet[(packet.length - 2)];
/* 145 */     return crc;
/*     */   }
/*     */ 
/*     */   static void StoreBytes(byte value, byte[] targetData, int targetOffset)
/*     */   {
/* 153 */     targetData[targetOffset] = value;
/*     */   }
/*     */ 
/*     */   static void StoreBytes(short value, byte[] targetData, int targetOffset)
/*     */   {
/* 162 */     targetData[(targetOffset + 0)] = (byte)(value >> 0 & 0xFF);
/* 163 */     targetData[(targetOffset + 1)] = (byte)(value >> 8 & 0xFF);
/*     */   }
/*     */ 
/*     */   static void StoreBytes(int value, byte[] targetData, int targetOffset)
/*     */   {
/* 172 */     targetData[(targetOffset + 0)] = (byte)(value >> 0 & 0xFF);
/* 173 */     targetData[(targetOffset + 1)] = (byte)(value >> 8 & 0xFF);
/* 174 */     targetData[(targetOffset + 2)] = (byte)(value >> 16 & 0xFF);
/* 175 */     targetData[(targetOffset + 3)] = (byte)(value >> 24 & 0xFF);
/*     */   }
/*     */ 
/*     */   static void StoreBytes(long value, byte[] targetData, int targetOffset)
/*     */   {
/* 184 */     targetData[(targetOffset + 0)] = (byte)(int)(value >> 0 & 0xFF);
/* 185 */     targetData[(targetOffset + 1)] = (byte)(int)(value >> 8 & 0xFF);
/* 186 */     targetData[(targetOffset + 2)] = (byte)(int)(value >> 16 & 0xFF);
/* 187 */     targetData[(targetOffset + 3)] = (byte)(int)(value >> 24 & 0xFF);
/* 188 */     targetData[(targetOffset + 4)] = (byte)(int)(value >> 32 & 0xFF);
/* 189 */     targetData[(targetOffset + 5)] = (byte)(int)(value >> 40 & 0xFF);
/* 190 */     targetData[(targetOffset + 6)] = (byte)(int)(value >> 48 & 0xFF);
/* 191 */     targetData[(targetOffset + 7)] = (byte)(int)(value >> 56 & 0xFF);
/*     */   }
/*     */ 
/*     */   byte[] ComposePacket(ReceiverCommands command)
/*     */   {	
/* 197 */     return DoComposePacket(this.m_Packet, command);
/*     */   }
/*     */ 
/*     */   byte[] ComposePacket(ReceiverCommands command, short payload)
/*     */   {
/* 204 */     return DoComposePacket(this.m_Packet, command, payload);
/*     */   }
/*     */ 
/*     */   byte[] ComposePacket(ReceiverCommands command, int payload)
/*     */   {
/* 211 */     return DoComposePacket(this.m_Packet, command, payload);
/*     */   }
/*     */ 
/*     */   byte[] ComposePacket(ReceiverCommands command, long payload)
/*     */   {
/* 218 */     return DoComposePacket(this.m_Packet, command, payload);
/*     */   }
/*     */ 
/*     */   byte[] ComposePacket(ReceiverCommands command, byte payload)
/*     */   {
/* 225 */     return DoComposePacket(this.m_Packet, command, payload);
/*     */   }
/*     */ 
/*     */   byte[] ComposePacket(ReceiverCommands command, byte payload1, int payload2)
/*     */   {
/* 233 */     return DoComposePacket(this.m_Packet, command, payload1, payload2);
/*     */   }
/*     */ 
/*     */   byte[] ComposePacket(ReceiverCommands command, byte payload1, int payload2, byte payload3)
/*     */   {
/* 242 */     return DoComposePacket(this.m_Packet, command, payload1, payload2, payload3);
/*     */   }
/*     */ 
/*     */   byte[] ComposePacket(ReceiverCommands command, byte[] payload, int payloadLength)
/*     */   {
/* 250 */     return DoComposePacket(this.m_Packet, command, payload, payloadLength);
/*     */   }
/*     */ 
/*     */   private static byte[] DoComposePacket(byte[] packet, ReceiverCommands command)
/*     */   {
				Log.i("DexcomPackets", "Packet type: "+command);
	
/* 258 */     int packetLength = 6;
/*     */ 
/* 260 */     packet[0] = 1;
/* 261 */     StoreBytes(packetLength, packet, 1);
/* 262 */     packet[3] = command.value;
/*     */ 
/* 264 */     short crc = Crc.CalculateCrc16(packet, 0, packetLength - 2);
/* 265 */     StoreBytes(crc, packet, packetLength - 2);
/*     */ 
/* 267 */     return packet;
/*     */   }
/*     */ 
/*     */   private static byte[] DoComposePacket(byte[] packet, ReceiverCommands command, byte payload)
/*     */   {
				Log.i("DexcomPackets", "Packet type: "+command);
	
/* 276 */     int packetLength = 7;
/*     */ 
/* 278 */     packet[0] = 1;
/* 279 */     StoreBytes(packetLength, packet, 1);
/* 280 */     packet[3] = command.value;
/* 281 */     StoreBytes(payload, packet, 4);
/* 282 */     short crc = Crc.CalculateCrc16(packet, 0, packetLength - 2);
/* 283 */     StoreBytes(crc, packet, packetLength - 2);
/*     */ 
/* 285 */     return packet;
/*     */   }
/*     */ 
/*     */   private static byte[] DoComposePacket(byte[] packet, ReceiverCommands command, short payload)
/*     */   {
				Log.i("DexcomPackets", "Packet type: "+command);
	
/* 294 */     int packetLength = 8;
/*     */ 
/* 296 */     packet[0] = 1;
/* 297 */     StoreBytes(packetLength, packet, 1);
/* 298 */     packet[3] = command.value;
/* 299 */     StoreBytes(payload, packet, 4);
/* 300 */     short crc = Crc.CalculateCrc16(packet, 0, packetLength - 2);
/* 301 */     StoreBytes(crc, packet, packetLength - 2);
/*     */ 
/* 303 */     return packet;
/*     */   }
/*     */ 
/*     */   private static byte[] DoComposePacket(byte[] packet, ReceiverCommands command, int payload)
/*     */   {
				Log.i("DexcomPackets", "Packet type: "+command);
	
/* 312 */     int packetLength = 10;
/*     */ 
/* 314 */     packet[0] = 1;
/* 315 */     StoreBytes(packetLength, packet, 1);
/* 316 */     packet[3] = command.value;
/* 317 */     StoreBytes(payload, packet, 4);
/* 318 */     short crc = Crc.CalculateCrc16(packet, 0, packetLength - 2);
/* 319 */     StoreBytes(crc, packet, packetLength - 2);
/*     */ 
/* 321 */     return packet;
/*     */   }
/*     */ 
/*     */   private static byte[] DoComposePacket(byte[] packet, ReceiverCommands command, long payload)
/*     */   {
				Log.i("DexcomPackets", "Packet type: "+command);
	
/* 330 */     int packetLength = 14;
/*     */ 
/* 332 */     packet[0] = 1;
/* 333 */     StoreBytes(packetLength, packet, 1);
/* 334 */     packet[3] = command.value;
/* 335 */     StoreBytes(payload, packet, 4);
/* 336 */     short crc = Crc.CalculateCrc16(packet, 0, packetLength - 2);
/* 337 */     StoreBytes(crc, packet, packetLength - 2);
/*     */ 
/* 339 */     return packet;
/*     */   }
/*     */ 
/*     */   private static byte[] DoComposePacket(byte[] packet, ReceiverCommands command, byte payload1, int payload2)
/*     */   {
				Log.i("DexcomPackets", "Packet type: "+command);
	
/* 349 */     int packetLength = 11;
/*     */ 
/* 351 */     packet[0] = 1;
/* 352 */     StoreBytes(packetLength, packet, 1);
/* 353 */     packet[3] = command.value;
/* 354 */     StoreBytes(payload1, packet, 4);
/* 355 */     StoreBytes(payload2, packet, 5);
/* 356 */     short crc = Crc.CalculateCrc16(packet, 0, packetLength - 2);
/* 357 */     StoreBytes(crc, packet, packetLength - 2);
/*     */ 
/* 359 */     return packet;
/*     */   }
/*     */ 
/*     */   private static byte[] DoComposePacket(byte[] packet, ReceiverCommands command, byte payload1, int payload2, byte payload3)
/*     */   {
				Log.i("DexcomPackets", "Packet type: "+command);
	
/* 370 */     int packetLength = 12;
/*     */ 
/* 373 */     packet[0] = 1;
/* 374 */     StoreBytes(packetLength, packet, 1);
/* 375 */     packet[3] = command.value;
/* 376 */     StoreBytes(payload1, packet, 4);
/* 377 */     StoreBytes(payload2, packet, 5);
/* 378 */     StoreBytes(payload3, packet, 9);
/*     */ 
/* 380 */     short crc = Crc.CalculateCrc16(packet, 0, packetLength - 2);
/* 381 */     StoreBytes(crc, packet, packetLength - 2);
/*     */ 
/* 383 */     return packet;
/*     */   }
/*     */ 
/*     */   private static byte[] DoComposePacket(byte[] packet, ReceiverCommands command, byte[] payload, int payloadLength)
/*     */   {
				Log.i("DexcomPackets", "Packet type: "+command);
	
/* 393 */     int packetLength = 6 + payloadLength;
/*     */ 
/* 395 */     packet[0] = 1;
/* 396 */     StoreBytes(packetLength, packet, 1);
/* 397 */     packet[3] = command.value;
/* 398 */     System.arraycopy(payload, 0, packet, 4, 
/* 399 */       payloadLength);
/* 400 */     short crc = Crc.CalculateCrc16(packet, 0, packetLength - 2);
/* 401 */     StoreBytes(crc, packet, packetLength - 2);
/*     */ 
/* 403 */     return packet;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.PacketTools
 * JD-Core Version:    0.6.0
 */