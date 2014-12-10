/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */  enum NoiseMode
/*     */ {
/* 213 */   None(0), 
/* 214 */   Clean(1), 
/* 215 */   Noise2(2), 
/* 216 */   Noise3(3), 
/* 217 */   Noise4(4), 
/* 218 */   NotComputed(5), 
/* 219 */   Max(6);
/*     */ 
/*     */   final byte value;
/*     */ 
/*     */   private NoiseMode(int value)
/*     */   {
/* 226 */     this.value = (byte)value;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.NoiseMode
 * JD-Core Version:    0.6.0
 */