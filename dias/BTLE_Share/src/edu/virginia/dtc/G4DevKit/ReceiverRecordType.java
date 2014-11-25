/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */ import java.util.EnumSet;
/*     */ import java.util.HashMap;
/*     */ import java.util.Map;
/*     */ 
/*     */  enum ReceiverRecordType
/*     */ {
/*  98 */   ManufacturingData(0), 
/*  99 */   FirmwareParameterData(1), 
/* 100 */   PCSoftwareParameter(2), 
/* 101 */   SensorData(3), 
/* 102 */   EGVData(4), 
/* 103 */   CalSet(5), 
/* 104 */   Aberration(6), 
/* 105 */   InsertionTime(7), 
/* 106 */   ReceiverLogData(8), 
/* 107 */   ReceiverErrorData(9), 
/* 108 */   MeterData(10), 
/* 109 */   UserEventData(11), 
/* 110 */   UserSettingData(12), 
/*     */ 
/* 112 */   MaxValue(13);
/*     */ 
/*     */   final byte value;
/*     */   private static final Map<Byte, ReceiverRecordType> lookup;
/*     */ 
/* 115 */   static { lookup = new HashMap();
/*     */   }
/*     */ 
/*     */   private ReceiverRecordType(int value)
/*     */   {
/* 120 */     this.value = (byte)value;
/*     */   }
/*     */ 
/*     */   static ReceiverRecordType getEnumFromValue(byte code)
/*     */   {
/* 127 */     for (ReceiverRecordType rc : EnumSet.allOf(ReceiverRecordType.class)) {
/* 128 */       lookup.put(Byte.valueOf(rc.value), rc);
/*     */     }
/* 130 */     return (ReceiverRecordType)lookup.get(Byte.valueOf(code));
/*     */   }
/*     */ 
/*     */   static ReceiverRecordTypeFlags getTypeFlag(ReceiverRecordType type)
/*     */   {
/* 136 */     ReceiverRecordTypeFlags flag = 
/* 137 */       ReceiverRecordTypeFlags.getEnumFromValue(type);
/*     */ 
/* 159 */     return flag;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverRecordType
 * JD-Core Version:    0.6.0
 */