/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */  enum UserAlertProfile
/*     */ {
/* 378 */   None(0), 
/*     */ 
/* 380 */   Vibrate(1), 
/*     */ 
/* 382 */   Soft(2), 
/*     */ 
/* 384 */   Normal(3), 
/*     */ 
/* 386 */   Attentive(4), 
/*     */ 
/* 388 */   Hyposafe(5);
/*     */ 
/*     */   final byte value;
/*     */ 
/*     */   private UserAlertProfile(int value)
/*     */   {
/* 395 */     this.value = (byte)value;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.UserAlertProfile
 * JD-Core Version:    0.6.0
 */