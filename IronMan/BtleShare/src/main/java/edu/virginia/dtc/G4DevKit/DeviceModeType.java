/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */ import android.util.Log;
/*     */ 
/*     */  enum DeviceModeType
/*     */ {
/* 543 */   Normal(0), 
/* 544 */   Manufactuiring(1);
/*     */ 
/*     */   final byte value;
/*     */ 
/*     */   private DeviceModeType(int value)
/*     */   {
/* 551 */     this.value = (byte)value;
/*     */   }
/*     */ 
/*     */   static DeviceModeType getEnumFromValue(byte code)
/*     */   {
/* 558 */     switch (code)
/*     */     {
/*     */     case 0:
/* 561 */       return Normal;
/*     */     case 1:
/* 563 */       return Manufactuiring;
/*     */     }
/*     */ 
/* 566 */     Log.e("DeviceModeType", " Invalid value for DeviceModeType.");
/* 567 */     return Normal;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.DeviceModeType
 * JD-Core Version:    0.6.0
 */