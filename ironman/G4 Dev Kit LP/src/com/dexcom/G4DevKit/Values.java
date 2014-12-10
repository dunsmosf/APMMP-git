/*    */ package com.dexcom.G4DevKit;
/*    */ 
/*    */ import java.util.Date;
/*    */ import java.util.GregorianCalendar;
/*    */ import java.util.UUID;
/*    */ 
/*    */ class Values
/*    */ {
/*    */   static final int SizeOfByte = 1;
/*    */   static final int SizeOfInt = 4;
/*    */   static final int SizeOfShort = 2;
/*    */   static final int SizeOfLong = 8;
/*    */   static final int DatabasePageSize = 528;
/*    */   static final int DatabaseBlockSize = 4224;
/*    */   static final int DatabasePageHeaderSize = 28;
/*    */   static final int DatabasePageDataSize = 500;
/*    */   static final int DatabaseRecordOverhead = 10;
/*    */   static final int DatabaseRecordMaxPayloadSize = 490;
/*    */   static final int MinimumCalculatedEgv = 39;
/*    */   static final int MaximumCalculatedEgv = 401;
/*    */   static final int DefaultCompressionLevel = 6;
/* 35 */   static final Date ReceiverBaseDate = new GregorianCalendar(2009, 0, 0, 23, 
/* 35 */     0, 0).getTime();
/* 36 */   static final Date MinDate = new Date(0, 0, 0, 0, 0, 0);
/* 37 */   static final Date MaxDate = new Date(9999, 11, 31, 23, 59, 59);
/*    */   static final String TransmitterIdValidChars = "0123456789ABCDEFGHJKLMNPQRSTUWXY";
/*    */   static final String TransmitterIdInvalidChars = "IOVZ";
/*    */   static final String ComPortNamePrefix = "COM";
/*    */   static final int MatchAnythingReceiverRecordTypeFlags = -1;
/* 54 */   static final UUID NoneId = UUID.fromString("00000000-0000-0000-0000-000000000000");
/*    */ 
/* 58 */   static final UUID AllId = UUID.fromString("11111111-1111-1111-1111-111111111111");
/*    */ 
/* 64 */   static final Date EmptySensorInsertionTime = Tools.convertReceiverTimeToDate(2147483647);
/*    */   static final int TransmitterIntervalMinutes = 5;
/*    */   static final int TransmitterIntervalMsec = 300000;
/*    */   static final int TransmitterMinimumGapMsec = 360000;
/*    */   static final byte TrendArrowMask = 15;
/*    */   static final byte NoiseMask = 112;
/*    */   static final byte ImmediateMatchMask = -128;
/*    */   static final short IsDisplayOnlyEgvMask = -32768;
/*    */   static final short EgvValueMask = 1023;
/*    */   static final byte FrequencyMask = 3;
/*    */   static final byte TransmitterIsBadStatusMask = -128;
/*    */   static final byte TransmitterIsLowBatteryMask = 64;
/*    */   static final int TransmitterBadStatusValueMask = -16777216;
/*    */   static final int TransmitterFilteredCountsMask = 16777215;
/*    */ 
/*    */   class FirmwareAppModeAddresses
/*    */   {
/*    */     static final int SpecialInfoAreaFirstPageIndex = 1272;
/*    */     static final int ProcessorErrorPageIndex = 1272;
/*    */     static final int ReservedPageIndex = 1273;
/*    */     static final int FirmWareParameterPageIndex = 1274;
/*    */     static final int PcUpdateStatePageIndex0 = 1275;
/*    */     static final int PcUpdateStatePageIndex1 = 1276;
/*    */     static final int PcUpdateStatePageIndex2 = 1277;
/*    */     static final int PcUpdateStatePageIndex3 = 1278;
/*    */     static final int BootModeStatusPageIndex = 1279;
/*    */ 
/*    */     FirmwareAppModeAddresses()
/*    */     {
/*    */     }
/*    */   }
/*    */ 
/*    */   class TinyBooterModeAddresses
/*    */   {
/*    */     static final int ProcessorErrorPageByteAddress = 4321152;
/*    */     static final int ReservedPageByteAddress = 4321680;
/*    */     static final int FirmwareParametersByteAddress = 4322208;
/*    */     static final int PcUpdateStatePageByteAddress0 = 4322736;
/*    */     static final int PcUpdateStatePageByteAddress1 = 4323264;
/*    */     static final int PcUpdateStatePageByteAddress2 = 4323792;
/*    */     static final int PcUpdateStatePageByteAddress3 = 4324320;
/*    */     static final int BootModeStatusByteAddress = 4324848;
/*    */     static final int FileSystemStartByteAddress = 3649536;
/*    */     static final int TotalDataFlashSizeBytes = 4325376;
/*    */ 
/*    */     TinyBooterModeAddresses()
/*    */     {
/*    */     }
/*    */   }
/*    */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.Values
 * JD-Core Version:    0.6.0
 */