/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import android.util.Log;

import com.dexcom.G4DevKit.enums.UsbPowerLevel;

import edu.virginia.dtc.SysMan.Debug;

import java.util.Date;
import java.util.TimeZone;
/*     */ 
/*     */ class ReceiverApiCommands
/*     */ {
/*  10 */   private static final PacketTools m_ptools = new PacketTools();
/*     */ 
/*     */   static boolean hasManufacturingParameters(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/*  17 */     DatabasePageRange range = api
/*  18 */       .readDatabasePageRange(ReceiverRecordType.ManufacturingData);
/*  19 */     return range.LastPage != -1;
/*     */   }
/*     */ 
/*     */   static ParameterRecord readManufacturingParameters(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/*  26 */     if (hasManufacturingParameters(api))
/*     */     {
/*  28 */       DatabasePageRange range = api
/*  29 */         .readDatabasePageRange(ReceiverRecordType.ManufacturingData);
/*  30 */       DatabasePage page = api.readDatabasePage(
/*  31 */         ReceiverRecordType.ManufacturingData, range.LastPage);
/*     */ 
/*  33 */       ParameterRecord record = new ParameterRecord(page.PageData);
/*     */ 
/*  35 */       record.PageNumber = page.PageHeader.PageNumber;
/*  36 */       record.RecordNumber = page.PageHeader.FirstRecordIndex;
/*  37 */       record.RecordRevision = page.PageHeader.Revision;
/*     */ 
/*  39 */       return record;
/*     */     }
/*     */ 
/*  43 */     throw new ReceiverCommException(
/*  44 */       "Manufacturing Parameters Partition is empty");
/*     */   }
/*     */ 
/*     */   static boolean hasPcParameters(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/*  52 */     DatabasePageRange range = api
/*  53 */       .readDatabasePageRange(ReceiverRecordType.PCSoftwareParameter);
/*  54 */     return range.LastPage != -1;
/*     */   }
/*     */ 
/*     */   static ParameterRecord readPcParameters(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/*  61 */     if (hasPcParameters(api))
/*     */     {
/*  63 */       DatabasePageRange range = api
/*  64 */         .readDatabasePageRange(ReceiverRecordType.PCSoftwareParameter);
/*  65 */       DatabasePage page = api.readDatabasePage(
/*  66 */         ReceiverRecordType.PCSoftwareParameter, range.LastPage);
/*  67 */       ParameterRecord record = new ParameterRecord(page.PageData);
/*     */ 
/*  69 */       record.PageNumber = page.PageHeader.PageNumber;
/*  70 */       record.RecordNumber = page.PageHeader.FirstRecordIndex;
/*  71 */       record.RecordRevision = page.PageHeader.Revision;
/*     */ 
/*  73 */       return record;
/*     */     }
/*     */ 
/*  77 */     throw new ReceiverCommException(
/*  78 */       "Manufacturing Parameters Partition is empty");
/*     */   }
/*     */ 
/*     */   static byte[] ReadFlashPage(ReceiverApi api, int pageIndex)
/*     */     throws Exception
/*     */   {
/*  87 */     byte[] message = m_ptools.ComposePacket(ReceiverCommands.ReadFlashPage, 
/*  88 */       pageIndex);
/*  89 */     byte[] payload = api.sendReceiverMessage(message);
/*     */ 
/*  91 */     return payload;
/*     */   }
/*     */ 
/*     */   static String readTransmitterId(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/*  98 */     byte[] message = m_ptools
/*  99 */       .ComposePacket(ReceiverCommands.ReadTransmitterID);
/* 100 */     byte[] payload = api.sendReceiverMessage(message);

/* 101 */     String transmitterCode = new String(payload);

				for(byte b:message)
					Log.e("ReceiverUpdateService", String.format("%02X", b));

/*     */ 
/* 103 */     return transmitterCode;
/*     */   }
/*     */ 
/*     */   static Date readSystemTime(ReceiverApi api)
/*     */     throws Exception
/*     */   {
				
/* 110 */     byte[] message = m_ptools
/* 111 */       .ComposePacket(ReceiverCommands.ReadSystemTime);
/*     */ 
/* 113 */     byte[] payload = api.sendReceiverMessage(message);
/*     */ 
/* 115 */     Date systemTime = Tools.convertPayloadToDate(payload);

				Debug.i("ReceiverUpdateService", "readSystemTime", systemTime.toString()+" Seconds: "+systemTime.getTime()/1000);
				
/* 117 */     return systemTime;
/*     */   }
/*     */ 
/*     */   static int readSystemTimeOffset(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/* 124 */     byte[] message = m_ptools
/* 125 */       .ComposePacket(ReceiverCommands.ReadSystemTimeOffset);
/* 126 */     byte[] payload = api.sendReceiverMessage(message);
/* 127 */     int timeOffsetSec = Tools.convertByteToInt(payload);
/*     */ 

			Debug.i("ReceiverUpdateService", "readSystemTimeOffset", "Offset: "+timeOffsetSec+" seconds");

/* 129 */     return timeOffsetSec;
/*     */   }
/*     */ 
/*     */   static Date readDisplayTime(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/* 136 */     byte[] message = m_ptools
/* 137 */       .ComposePacket(ReceiverCommands.ReadSystemTime);
/* 138 */     byte[] payload = api.sendReceiverMessage(message);
/* 139 */     int systemTimeSec = Tools.convertByteToInt(payload);
/*     */ 
/* 141 */     int displayTimeOffset = readDisplayTimeOffset(api);
/*     */ 
/* 143 */     Date displayTime = Tools.convertReceiverTimeToDate(systemTimeSec + 
/* 144 */       displayTimeOffset);
/*     */ 
/* 146 */     return displayTime;
/*     */   }
/*     */ 
/*     */   static int readDisplayTimeOffset(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/* 153 */     byte[] message = m_ptools
/* 154 */       .ComposePacket(ReceiverCommands.ReadDisplayTimeOffset);
/* 155 */     byte[] payload = api.sendReceiverMessage(message);
/* 156 */     int timeOffsetSec = Tools.convertByteToInt(payload);
/*     */ 
/* 158 */     return timeOffsetSec;
/*     */   }
/*     */ 
/*     */   static Date readRtc(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/* 165 */     byte[] message = m_ptools.ComposePacket(ReceiverCommands.ReadRTC);
/* 166 */     byte[] payload = api.sendReceiverMessage(message);
/* 167 */     Date systemTime = Tools.convertPayloadToDate(payload);
/*     */ 

Debug.i("ReceiverUpdateService", "readRtc", systemTime.toString()+" Seconds: "+systemTime.getTime()/1000);

/* 169 */     return systemTime;
/*     */   }
/*     */ 
/*     */   static LanguageType readLanguageType(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/* 176 */     byte[] message = m_ptools.ComposePacket(ReceiverCommands.ReadLanguage);
/* 177 */     byte[] payload = api.sendReceiverMessage(message);
/* 178 */     short languageCode = Tools.convertByteToShort(payload);
/*     */ 
/* 180 */     LanguageType lType = LanguageType.getEnumFromValue(languageCode);
/*     */ 
/* 182 */     return lType;
/*     */   }
/*     */ 
/*     */   static GlucoseUnitType readGlucoseUnit(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/* 189 */     byte[] message = m_ptools
/* 190 */       .ComposePacket(ReceiverCommands.ReadGlucoseUnit);
/* 191 */     byte[] payload = api.sendReceiverMessage(message);
/* 192 */     byte unitCode = payload[0];
/*     */ 
/* 194 */     GlucoseUnitType gUnitType = GlucoseUnitType.getEnumFromValue(unitCode);
/*     */ 
/* 196 */     return gUnitType;
/*     */   }
/*     */ 
/*     */   static BlindedModeType readBlindMode(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/* 203 */     byte[] message = m_ptools
/* 204 */       .ComposePacket(ReceiverCommands.ReadBlindedMode);
/* 205 */     byte[] payload = api.sendReceiverMessage(message);
/* 206 */     byte blindCode = payload[0];
/*     */ 
/* 208 */     BlindedModeType blindType = BlindedModeType.getEnumFromValue(blindCode);
/*     */ 
/* 210 */     return blindType;
/*     */   }
/*     */ 
/*     */   static ClockModeType readClockMode(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/* 217 */     byte[] message = m_ptools.ComposePacket(ReceiverCommands.ReadClockMode);
/* 218 */     byte[] payload = api.sendReceiverMessage(message);
/* 219 */     byte clockCode = payload[0];
/*     */ 
/* 221 */     ClockModeType clockType = ClockModeType.getEnumFromValue(clockCode);
/*     */ 
/* 223 */     return clockType;
/*     */   }
/*     */ 
/*     */   static DeviceModeType readDeviceMode(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/* 230 */     byte[] message = m_ptools
/* 231 */       .ComposePacket(ReceiverCommands.ReadDeviceMode);
/* 232 */     byte[] payload = api.sendReceiverMessage(message);
/* 233 */     byte deviceModeCode = payload[0];
/*     */ 
/* 235 */     DeviceModeType deviceModeType = 
/* 236 */       DeviceModeType.getEnumFromValue(deviceModeCode);
/*     */ 
/* 238 */     return deviceModeType;
/*     */   }
/*     */ 
/*     */   static boolean setUsbPowerlevel(ReceiverApi api, UsbPowerLevel level)
/*     */     throws Exception
/*     */   {
/* 246 */     byte[] message = m_ptools.ComposePacket(
/* 247 */       ReceiverCommands.WriteChargerCurrentSetting, level.value);
/* 248 */     api.sendReceiverMessage(message);
/*     */ 
/* 250 */     return readUsbPowerLevel(api) == level;
/*     */   }
/*     */ 
/*     */   static UsbPowerLevel readUsbPowerLevel(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/* 257 */     byte[] message = m_ptools
/* 258 */       .ComposePacket(ReceiverCommands.ReadChargerCurrentSetting);
/* 259 */     byte[] payload = api.sendReceiverMessage(message);
/*     */ 
/* 261 */     return UsbPowerLevel.getEnumFromValue(payload[0]);
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverApiCommands
 * JD-Core Version:    0.6.0
 */