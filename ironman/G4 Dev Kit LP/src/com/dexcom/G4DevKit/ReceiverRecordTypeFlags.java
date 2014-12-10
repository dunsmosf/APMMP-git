/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import java.util.EnumSet;
/*     */ import java.util.HashMap;
/*     */ import java.util.Iterator;
/*     */ import java.util.Map;
/*     */ 
/*     */  enum ReceiverRecordTypeFlags
/*     */ {
/* 165 */   None(0), 
/* 166 */   ManufacturingData(1), 
/* 167 */   FirmwareParameterData(2), 
/* 168 */   PCSoftwareParameter(4), 
/* 169 */   SensorData(8), 
/* 170 */   EGVData(16), 
/* 171 */   CalSet(32), 
/* 172 */   Aberration(64), 
/* 173 */   InsertionTime(128), 
/* 174 */   ReceiverLogData(256), 
/* 175 */   ReceiverErrorData(512), 
/* 176 */   MeterData(1024), 
/* 177 */   UserEventData(2048), 
/* 178 */   UserSettingData(4096), 
/* 179 */   ProcessorErrors(8192);
/*     */ 
/*     */   final int value;
/*     */   private static final Map<String, ReceiverRecordTypeFlags> lookup;
/*     */ 
/* 183 */   static { lookup = new HashMap();
/*     */   }
/*     */ 
/*     */   private ReceiverRecordTypeFlags(int value)
/*     */   {
/* 188 */     this.value = value;
/*     */   }
/*     */ 
/*     */   static ReceiverRecordTypeFlags getEnumFromValue(ReceiverRecordType type)
/*     */   {
/* 198 */     Iterator localIterator = EnumSet.allOf(ReceiverRecordTypeFlags.class).iterator();
/*     */ 
/* 197 */     while (localIterator.hasNext()) {
/* 198 */       ReceiverRecordTypeFlags rc = (ReceiverRecordTypeFlags)localIterator.next();
/* 199 */       lookup.put(rc.name(), rc);
/*     */     }
/* 201 */     ReceiverRecordTypeFlags matchingFlag = (ReceiverRecordTypeFlags)lookup.get(type.name());
/*     */ 
/* 204 */     ReceiverRecordTypeFlags flag = matchingFlag != null ? matchingFlag : 
/* 205 */       None;
/*     */ 
/* 207 */     return flag;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverRecordTypeFlags
 * JD-Core Version:    0.6.0
 */