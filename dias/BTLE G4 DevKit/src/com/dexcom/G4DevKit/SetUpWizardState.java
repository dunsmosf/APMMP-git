/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */  enum SetUpWizardState
/*     */ {
/* 347 */   None(0), 
/*     */ 
/* 350 */   TimeNotSet(1), 
/*     */ 
/* 352 */   TimeSet(2), 
/*     */ 
/* 354 */   TransmitterIDSet(3), 
/*     */ 
/* 356 */   UserLowLevelSet(4), 
/*     */ 
/* 358 */   UserHighLevelSet(5), 
/*     */ 
/* 360 */   TimeFormatSet(6), 
/*     */ 
/* 362 */   LanguageNotSet(7), 
/*     */ 
/* 364 */   LanguageSet(8);
/*     */ 
/*     */   final byte value;
/*     */ 
/*     */   private SetUpWizardState(int value)
/*     */   {
/* 371 */     this.value = (byte)value;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.SetUpWizardState
 * JD-Core Version:    0.6.0
 */