/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */ import android.util.Log;
/*     */ 
/*     */  enum BlindedModeType
/*     */ {
/* 479 */   Unblinded(0), 
/* 480 */   Blinded(1);
/*     */ 
/*     */   final byte value;
/*     */ 
/*     */   private BlindedModeType(int value)
/*     */   {
/* 487 */     this.value = (byte)value;
/*     */   }
/*     */ 
/*     */   static BlindedModeType getEnumFromValue(byte code)
/*     */   {
/* 494 */     switch (code)
/*     */     {
/*     */     case 0:
/* 497 */       return Unblinded;
/*     */     case 1:
/* 499 */       return Blinded;
/*     */     }
/*     */ 
/* 502 */     Log.e("BlindedModeType", " Invalid value for BlindedModeType.");
/* 503 */     return Unblinded;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.BlindedModeType
 * JD-Core Version:    0.6.0
 */