/*    */ package com.dexcom.G4DevKit;
/*    */ 
/*    */ import java.nio.ByteBuffer;
/*    */ import java.nio.ByteOrder;
/*    */ 
/*    */ class DatabasePageRange
/*    */ {
/*    */   int FirstPage;
/*    */   int LastPage;
/*    */   static final int dataTypeByteLength = 8;
/*    */ 
/*    */   DatabasePageRange(int fp, int lp)
/*    */   {
/* 24 */     this.FirstPage = fp;
/* 25 */     this.LastPage = lp;
/*    */   }
/*    */ 
/*    */   DatabasePageRange(byte[] dataStream)
/*    */   {
/* 31 */     if (dataStream.length == 8)
/*    */     {
/* 33 */       ByteBuffer bb = ByteBuffer.wrap(dataStream);
/* 34 */       bb.order(ByteOrder.LITTLE_ENDIAN);
/*    */ 
/* 36 */       this.FirstPage = bb.getInt();
/* 37 */       this.LastPage = bb.getInt();
/*    */     }
/*    */   }
/*    */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.DatabasePageRange
 * JD-Core Version:    0.6.0
 */