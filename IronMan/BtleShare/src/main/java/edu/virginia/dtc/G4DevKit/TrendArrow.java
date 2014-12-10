/*    */ package edu.virginia.dtc.G4DevKit;
/*    */ 
/*    */ public enum TrendArrow
/*    */ {
/*  6 */   None(0), 
/*    */ 
/*  8 */   DoubleUp(1), 
/*    */ 
/* 10 */   SingleUp(2), 
/*    */ 
/* 12 */   FortyFiveUp(3), 
/*    */ 
/* 14 */   Flat(4), 
/*    */ 
/* 16 */   FortyFiveDown(5), 
/*    */ 
/* 18 */   SingleDown(6), 
/*    */ 
/* 20 */   DoubleDown(7), 
/*    */ 
/* 26 */   NotComputable(8), 
/*    */ 
/* 31 */   RateOutOfRange(9);
/*    */ 
/*    */   public final byte value;
/*    */ 
/*    */   private TrendArrow(int value)
/*    */   {
/* 38 */     this.value = (byte)value;
/*    */   }
/*    */ 
/*    */   private TrendArrow(byte value)
/*    */   {
/* 44 */     this.value = value;
/*    */   }
/*    */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.enums.TrendArrow
 * JD-Core Version:    0.6.0
 */