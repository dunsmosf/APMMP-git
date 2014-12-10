/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import android.util.Log;
/*     */ 
/*     */  enum ClockModeType
/*     */ {
/* 511 */   ClockMode24Hour(0), 
/* 512 */   ClockMode12Hour(1);
/*     */ 
/*     */   final byte value;
/*     */ 
/*     */   private ClockModeType(int value)
/*     */   {
/* 519 */     this.value = (byte)value;
/*     */   }
/*     */ 
/*     */   static ClockModeType getEnumFromValue(byte code)
/*     */   {
/* 526 */     switch (code)
/*     */     {
/*     */     case 0:
/* 529 */       return ClockMode24Hour;
/*     */     case 1:
/* 531 */       return ClockMode12Hour;
/*     */     }
/*     */ 
/* 534 */     Log.e("ClockModeType", " Invalid value for ClockModeType.");
/* 535 */     return ClockMode12Hour;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ClockModeType
 * JD-Core Version:    0.6.0
 */