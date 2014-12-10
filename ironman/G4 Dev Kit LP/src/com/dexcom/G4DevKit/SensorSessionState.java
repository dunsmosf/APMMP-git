/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */  enum SensorSessionState
/*     */ {
/* 403 */   None(0), 
/*     */ 
/* 406 */   SensorRemoved(1), 
/*     */ 
/* 408 */   SessionExpired(2), 
/*     */ 
/* 410 */   Aberration1(3), 
/*     */ 
/* 412 */   Aberration2(4), 
/*     */ 
/* 415 */   TryingToStartSecondSession(5), 
/*     */ 
/* 418 */   SensorShutOffDueToTimeLoss(6), 
/*     */ 
/* 425 */   SessionStarted(7), 
/*     */ 
/* 428 */   BadTransmitter(8), 
/*     */ 
/* 430 */   Internal1(9);
/*     */ 
/*     */   final byte value;
/*     */ 
/*     */   private SensorSessionState(int value)
/*     */   {
/* 437 */     this.value = (byte)value;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.SensorSessionState
 * JD-Core Version:    0.6.0
 */