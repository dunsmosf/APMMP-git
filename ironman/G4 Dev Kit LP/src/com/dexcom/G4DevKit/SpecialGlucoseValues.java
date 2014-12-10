/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import java.util.EnumSet;
/*     */ import java.util.HashMap;
/*     */ import java.util.Iterator;
/*     */ import java.util.Map;
/*     */ 
/*     */  enum SpecialGlucoseValues
/*     */ {
/* 232 */   None(0), 
/* 233 */   SensorNotActive(1), 
/* 234 */   Aberation0(2), 
/* 235 */   NoAntenna(3), 
/*     */ 
/* 237 */   SensorOutOfCal(5), 
/* 238 */   Aberration1(6), 
/*     */ 
/* 241 */   Aberration2(9), 
/* 242 */   Aberration3(10), 
/*     */ 
/* 244 */   RFBadStatus(12);
/*     */ 
/*     */   final short value;
/*     */   private static final Map<Integer, SpecialGlucoseValues> lookup;
/*     */ 
/* 247 */   static { lookup = new HashMap();
/*     */   }
/*     */ 
/*     */   private SpecialGlucoseValues(int value)
/*     */   {
/* 252 */     this.value = (short)value;
/*     */   }
/*     */ 
/*     */   static SpecialGlucoseValues getEnumFromValue(int code)
/*     */   {
/* 260 */     Iterator localIterator = EnumSet.allOf(SpecialGlucoseValues.class).iterator();
/*     */ 
/* 259 */     while (localIterator.hasNext()) {
/* 260 */       SpecialGlucoseValues sgv = (SpecialGlucoseValues)localIterator.next();
/* 261 */       lookup.put(Integer.valueOf(sgv.value), sgv);
/*     */     }
/* 263 */     return (SpecialGlucoseValues)lookup.get(Integer.valueOf(code));
/*     */   }
/*     */ 
/*     */   static Boolean isDefined(int testValue)
/*     */   {
/* 269 */     for (SpecialGlucoseValues values : values())
/*     */     {
/* 271 */       if (values.value == testValue)
/*     */       {
/* 273 */         return Boolean.valueOf(true);
/*     */       }
/*     */     }
/*     */ 
/* 277 */     return Boolean.valueOf(false);
/*     */   }
/*     */ 
/*     */   static String toString(int value)
/*     */   {
/* 283 */     for (SpecialGlucoseValues values : values())
/*     */     {
/* 285 */       if (values.value == value)
/*     */       {
/* 287 */         return values.name();
/*     */       }
/*     */     }
/*     */ 
/* 291 */     return "";
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.SpecialGlucoseValues
 * JD-Core Version:    0.6.0
 */