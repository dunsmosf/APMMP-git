/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import java.nio.ByteBuffer;
/*     */ import java.nio.ByteOrder;
/*     */ 
/*     */ class DatabasePage
/*     */ {
/*     */   DatabasePageHeader PageHeader;
/*     */   byte[] PageData;
/*     */   static final int dataTypeByteLength = 528;
/*     */ 
/*     */   DatabasePage(byte[] dataStream)
/*     */   {
/*  93 */     ByteBuffer bb = ByteBuffer.wrap(dataStream);
/*  94 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/*  96 */     int streamLength = dataStream.length;
/*  97 */     byte[] pageHeader = new byte[28];
/*  98 */     this.PageData = 
/*  99 */       new byte[streamLength - 
/*  99 */       28];
/*     */ 
/* 101 */     System.arraycopy(dataStream, 0, pageHeader, 0, 
/* 102 */       28);
/* 103 */     System.arraycopy(dataStream, 28, 
/* 104 */       this.PageData, 0, streamLength - 
/* 105 */       28);
/*     */ 
/* 107 */     this.PageHeader = new DatabasePageHeader(pageHeader);
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.DatabasePage
 * JD-Core Version:    0.6.0
 */