/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import android.util.Log;
/*     */ 
/*     */  enum GlucoseUnitType
/*     */ {
/* 444 */   None(0), 
/* 445 */   mgPerDL(1), 
/* 446 */   mmolPerL(2);
/*     */ 
/*     */   final byte value;
/*     */ 
/*     */   private GlucoseUnitType(int value)
/*     */   {
/* 453 */     this.value = (byte)value;
/*     */   }
/*     */ 
/*     */   static GlucoseUnitType getEnumFromValue(int code)
/*     */   {
/* 460 */     switch (code)
/*     */     {
/*     */     case 0:
/* 463 */       return None;
/*     */     case 1:
/* 465 */       return mgPerDL;
/*     */     case 2:
/* 467 */       return mmolPerL;
/*     */     }
/*     */ 
/* 470 */     Log.e("GlucoseUnitType", " Invalid value for GlucoseUnitType.");
/* 471 */     return None;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.GlucoseUnitType
 * JD-Core Version:    0.6.0
 */