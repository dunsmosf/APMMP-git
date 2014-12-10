/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import java.nio.ByteBuffer;
/*     */ import java.nio.ByteOrder;
/*     */ import java.util.Date;
/*     */ 
/*     */ class ParameterRecord
/*     */ {
/*     */   int SystemSeconds;
/*     */   int DisplaySeconds;
/*     */   String xmlData;
/*     */   short Crc;
/*     */   Date SystemTimeStamp;
/*     */   Date DisplayTimeStamp;
/*     */   int PageNumber;
/*     */   int RecordRevision;
/*     */   int RecordNumber;
/*     */ 
/*     */   public ParameterRecord()
/*     */   {
/*     */   }
/*     */ 
/*     */   ParameterRecord(byte[] dataStream)
/*     */   {
/* 507 */     ByteBuffer bb = ByteBuffer.wrap(dataStream);
/* 508 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/* 510 */     this.SystemSeconds = bb.getInt();
/* 511 */     this.DisplaySeconds = bb.getInt();
/*     */ 
/* 513 */     byte[] xmlDataArray = new byte[dataStream.length - 10];
/* 514 */     bb.get(xmlDataArray);
/* 515 */     this.xmlData = new String(xmlDataArray);
/*     */ 
/* 517 */     this.Crc = bb.getShort();
/*     */ 
/* 519 */     convertTimes();
/*     */ 
/* 521 */     Tools.evaluateCrc(dataStream, this.Crc, getClass().getName());
/*     */   }
/*     */ 
/*     */   private void convertTimes()
/*     */   {
/* 526 */     this.SystemTimeStamp = Tools.convertReceiverTimeToDate(this.SystemSeconds);
/* 527 */     this.DisplayTimeStamp = Tools.convertReceiverTimeToDate(this.DisplaySeconds);
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ParameterRecord
 * JD-Core Version:    0.6.0
 */