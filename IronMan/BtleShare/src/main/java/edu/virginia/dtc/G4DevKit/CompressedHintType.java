/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */ import android.util.Log;
/*     */ 
/*     */  enum CompressedHintType
/*     */ {
/* 576 */   Unknown(0), 
/* 577 */   Binary(1), 
/* 578 */   String(2), 
/* 579 */   XmlElement(3), 
/* 580 */   XmlFragment(4);
/*     */ 
/*     */   final byte value;
/*     */ 
/*     */   private CompressedHintType(int value)
/*     */   {
/* 587 */     this.value = (byte)value;
/*     */   }
/*     */ 
/*     */   static CompressedHintType getEnumFromValue(byte code)
/*     */   {
/* 594 */     switch (code)
/*     */     {
/*     */     case 0:
/* 597 */       return Unknown;
/*     */     case 1:
/* 599 */       return Binary;
/*     */     case 2:
/* 601 */       return String;
/*     */     case 3:
/* 603 */       return XmlElement;
/*     */     case 4:
/* 605 */       return XmlFragment;
/*     */     }
/*     */ 
/* 608 */     Log.e("CompressedHintType", 
/* 609 */       " Invalid value for CompressedHintType.");
/* 610 */     return Unknown;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.CompressedHintType
 * JD-Core Version:    0.6.0
 */