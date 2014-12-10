/*    */ package com.dexcom.G4DevKit;
/*    */ 
/*    */ import java.util.EnumSet;
/*    */ import java.util.HashMap;
/*    */ import java.util.Map;
/*    */ 
/*    */  enum ReceiverCommands
/*    */ {
/* 14 */   Null(0), 
/* 15 */   Ack(1), 
/* 16 */   Nak(2), 
/* 17 */   InvalidCommand(3), 
/* 18 */   InvalidParam(4), 
/* 19 */   IncompletePacketReceived(5), 
/* 20 */   ReceiverError(6), 
/* 21 */   InvalidMode(7), 
/*    */ 
/* 24 */   Ping(10), 
/* 25 */   ReadFirmwareHeader(11), 
/*    */ 
/* 28 */   ReadDatabaseParitionInfo(15), 
/* 29 */   ReadDatabasePageRange(16), 
/* 30 */   ReadDatabasePages(17), 
/* 31 */   ReadDatabasePageHeader(18), 
/*    */ 
/* 34 */   ReadTransmitterID(25), 
/* 35 */   WriteTransmitterID(26), 
/* 36 */   ReadLanguage(27), 
/* 37 */   WriteLanguage(28), 
/* 38 */   ReadDisplayTimeOffset(29), 
/* 39 */   WriteDisplayTimeOffset(30), 
/* 40 */   ReadRTC(31), 
/* 41 */   ResetReceiver(32), 
/* 42 */   ReadBatteryLevel(33), 
/* 43 */   ReadSystemTime(34), 
/* 44 */   ReadSystemTimeOffset(35), 
/* 45 */   WriteSystemTime(36), 
/* 46 */   ReadGlucoseUnit(37), 
/* 47 */   WriteGlucoseUnit(38), 
/* 48 */   ReadBlindedMode(39), 
/* 49 */   WriteBlindedMode(40), 
/* 50 */   ReadClockMode(41), 
/* 51 */   WriteClockMode(42), 
/* 52 */   ReadDeviceMode(43), 
/*    */ 
/* 54 */   EnterManufactureMode(44), 
/*    */ 
/* 56 */   EraseDatabase(45), 
/* 57 */   ShutdownReceiver(46), 
/* 58 */   WritePCParameters(47), 
/* 59 */   ReadBatteryState(48), 
/* 60 */   ReadHardwareBoardId(49), 
/* 61 */   EnterFirmwareUpgradeMode(50), 
/* 62 */   ReadFlashPage(51), 
/* 63 */   WriteFlashPage(52), 
/* 64 */   EnterSambaAccessMode(53), 
/* 65 */   ReadFirmwareSettings(54), 
/* 66 */   ReadEnableSetUpWizardFlag(55), 
/* 67 */   WriteEnableSetUpWizardFlag(56), 
/* 68 */   ReadSetUpWizardState(57), 
/* 69 */   WriteSetUpWizardState(58), 
/* 70 */   ReadChargerCurrentSetting(59), 
/* 71 */   WriteChargerCurrentSetting(60), 
/*    */ 
/* 73 */   MaxPossibleCommand(255);
/*    */ 
/*    */   final byte value;
/*    */   private static final Map<Byte, ReceiverCommands> lookup;
/*    */ 
/* 76 */   static { lookup = new HashMap();
/*    */   }
/*    */ 
/*    */   private ReceiverCommands(int value)
/*    */   {
/* 81 */     this.value = (byte)value;
/*    */   }
/*    */ 
/*    */   static ReceiverCommands getEnumFromValue(byte code)
/*    */   {
/* 88 */     for (ReceiverCommands rc : EnumSet.allOf(ReceiverCommands.class)) {
/* 89 */       lookup.put(Byte.valueOf(rc.value), rc);
/*    */     }
/* 91 */     return (ReceiverCommands)lookup.get(Byte.valueOf(code));
/*    */   }
/*    */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverCommands
 * JD-Core Version:    0.6.0
 */