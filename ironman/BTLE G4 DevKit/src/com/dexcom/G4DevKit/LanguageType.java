/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import java.util.EnumSet;
/*     */ import java.util.HashMap;
/*     */ import java.util.Map;
/*     */ 
/*     */  enum LanguageType
/*     */ {
/* 298 */   None(0), 
/*     */ 
/* 300 */   English(1033), 
/*     */ 
/* 302 */   French(1036), 
/*     */ 
/* 304 */   German(1031), 
/*     */ 
/* 306 */   Dutch(1043), 
/*     */ 
/* 308 */   Spanish(1034), 
/*     */ 
/* 310 */   Swedish(1053), 
/*     */ 
/* 312 */   Italian(1040), 
/*     */ 
/* 314 */   Czech(1029), 
/*     */ 
/* 316 */   Finnish(1035), 
/*     */ 
/* 318 */   French_Canada(3084), 
/*     */ 
/* 320 */   Polish(1045), 
/*     */ 
/* 322 */   Portugese_Brazil(1046);
/*     */ 
/*     */   final short value;
/*     */   private static final Map<Integer, LanguageType> lookup;
/*     */ 
/* 325 */   static { lookup = new HashMap();
/*     */   }
/*     */ 
/*     */   private LanguageType(int value)
/*     */   {
/* 330 */     this.value = (short)value;
/*     */   }
/*     */ 
/*     */   static LanguageType getEnumFromValue(int code)
/*     */   {
/* 337 */     for (LanguageType sgv : EnumSet.allOf(LanguageType.class)) {
/* 338 */       lookup.put(Integer.valueOf(sgv.value), sgv);
/*     */     }
/* 340 */     return (LanguageType)lookup.get(Integer.valueOf(code));
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.LanguageType
 * JD-Core Version:    0.6.0
 */