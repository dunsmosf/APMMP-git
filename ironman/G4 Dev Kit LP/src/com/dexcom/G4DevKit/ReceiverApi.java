/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import java.nio.ByteBuffer;
/*     */ import java.nio.ByteOrder;
/*     */ 
/*     */ class ReceiverApi
/*     */ {
/*     */   private final IReceiverComm m_usbComm;
/*  10 */   private final PacketTools m_ptools = new PacketTools();
/*     */ 
/*     */   ReceiverApi(IReceiverComm usbCommInterface)
/*     */   {
/*  15 */     this.m_usbComm = usbCommInterface;
/*     */   }
/*     */ 
/*     */   boolean pingReceiver()
/*     */   {
/*  21 */     byte[] message = this.m_ptools.ComposePacket(ReceiverCommands.Ping);
/*     */ 
/*  24 */     int messageLength = this.m_ptools.GetPacketLength(message);
/*  25 */     byte[] truncatedMessage = new byte[messageLength];
/*  26 */     System.arraycopy(message, 0, truncatedMessage, 0, messageLength);
/*     */     try
/*     */     {
/*  32 */       byte[] reply = this.m_usbComm
/*  33 */         .sendReceiverMessageForResponse(truncatedMessage);
/*     */ 
/*  36 */       byte commandId = reply[3];
/*     */ 
/*  38 */       return commandId == ReceiverCommands.Ack.value;
/*     */     }
/*     */     catch (Exception e)
/*     */     {
/*     */     }
/*     */ 
/*  44 */     return false;
/*     */   }
/*     */ 
/*     */   DatabasePageRange readDatabasePageRange(ReceiverRecordType recordType)
/*     */     throws Exception
/*     */   {
/*  52 */     byte[] message = this.m_ptools.ComposePacket(
/*  53 */       ReceiverCommands.ReadDatabasePageRange, recordType.value);
/*  54 */     byte[] pageRangeData = sendReceiverMessage(message);
/*  55 */     DatabasePageRange range = new DatabasePageRange(pageRangeData);
/*     */ 
/*  57 */     return range;
/*     */   }
/*     */ 
/*     */   DatabasePageHeader readDatabasePageHeader(ReceiverRecordType recordType, int pageNumber)
/*     */     throws Exception
/*     */   {
/*  65 */     byte[] message = this.m_ptools.ComposePacket(
/*  66 */       ReceiverCommands.ReadDatabasePageHeader, recordType.value, 
/*  67 */       pageNumber);
/*  68 */     byte[] pageHeaderData = sendReceiverMessage(message);
/*  69 */     DatabasePageHeader header = new DatabasePageHeader(pageHeaderData);
/*     */ 
/*  71 */     return header;
/*     */   }
/*     */ 
/*     */   DatabasePage readDatabasePage(ReceiverRecordType recordType, int pageNumber)
/*     */     throws Exception
/*     */   {
/*  79 */     byte[] message = this.m_ptools.ComposePacket(
/*  80 */       ReceiverCommands.ReadDatabasePages, recordType.value, 
/*  81 */       pageNumber, (byte)1);
/*  82 */     byte[] payload = sendReceiverMessage(message);
/*  83 */     DatabasePage page = new DatabasePage(payload);
/*     */ 
/*  85 */     return page;
/*     */   }
/*     */ 
/*     */   byte[] sendReceiverMessage(byte[] message)
/*     */     throws Exception
/*     */   {
/*  93 */     int messageLength = this.m_ptools.GetPacketLength(message);
/*  94 */     byte[] truncatedMessage = new byte[messageLength];
/*  95 */     System.arraycopy(message, 0, truncatedMessage, 0, messageLength);
/*     */ 
/*  99 */     byte[] reply = this.m_usbComm
/* 100 */       .sendReceiverMessageForResponse(truncatedMessage);
/* 101 */     byte[] payload = processReceiverReply(reply);
/*     */ 
/* 103 */     return payload;
/*     */   }
/*     */ 
/*     */   private byte[] processReceiverReply(byte[] reply)
/*     */     throws Exception
/*     */   {
/* 110 */     byte[] payload = new byte[0];
/*     */ 
/* 112 */     ByteBuffer bb = ByteBuffer.wrap(reply);
/* 113 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/* 116 */     short totalPacketLength = bb.getShort(1);
/* 117 */     int payloadSize = totalPacketLength - 6;
/* 118 */     byte commandId = reply[3];
/* 119 */     verifyResponseCommand(commandId, reply);
/*     */ 
/* 123 */     byte[] packetStart = new byte[4];
/* 124 */     byte[] crc = new byte[2];
/* 125 */     payload = new byte[payloadSize];
/*     */ 
/* 127 */     bb.get(packetStart, 0, 4);
/* 128 */     bb.get(payload, 0, payloadSize);
/* 129 */     bb.get(crc, 0, 2);
/*     */ 
/* 132 */     bb.rewind();
/* 133 */     byte[] packetNoCrc = new byte[totalPacketLength - 2];
/* 134 */     bb.get(packetNoCrc, 0, totalPacketLength - 2);
/* 135 */     short calculatedCrc = Crc.CalculateCrc16(packetNoCrc, 0, 
/* 136 */       totalPacketLength - 2);
/*     */ 
/* 138 */     if (bb.getShort(totalPacketLength - 2) != calculatedCrc)
/*     */     {
/* 140 */       throw new Exception("Failed CRC check in packet");
/*     */     }
/*     */ 
/* 143 */     return payload;
/*     */   }
/*     */ 
/*     */   private void verifyResponseCommand(byte commandId, byte[] payload)
/*     */     throws Exception
/*     */   {
/* 151 */     ReceiverCommands command = ReceiverCommands.getEnumFromValue(commandId);
/*     */ 
/* 153 */     switch (command)
/*     */     {
/*     */     case EnterFirmwareUpgradeMode:
/* 156 */       break;
/*     */     case EnterManufactureMode:
/* 158 */       throw new Exception(
/* 159 */         "Receiver reported NAK or an invalid CRC error.");
/*     */     case EnterSambaAccessMode:
/* 162 */       throw new Exception(
/* 163 */         "Receiver reported an invalid command error.");
/*     */     case EraseDatabase:
/* 166 */       if ((payload != null) && (payload.length >= 1))
/*     */       {
/* 168 */         throw new Exception(
/* 169 */           "Receiver reported an invalid parameter error for parameter " + 
/* 170 */           payload[0]);
/*     */       }
/*     */ 
/* 174 */       throw new Exception(
/* 175 */         "Receiver reported an invalid parameter error.");
/*     */     case InvalidCommand:
/* 179 */       throw new Exception("Receiver reported an internal error.");
/*     */     case Ack:
				break;
/*     */     case IncompletePacketReceived:
/*     */     case InvalidMode:
/*     */     default:
/* 185 */       throw new Exception("Unknown or invalid receiver command " + 
/* 186 */         commandId + "=" + command);
/*     */     }
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverApi
 * JD-Core Version:    0.6.0
 */