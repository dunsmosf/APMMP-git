/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */ import java.nio.ByteBuffer;
/*     */ import java.nio.ByteOrder;
/*     */ import java.util.Date;
/*     */ 
/*     */ class ReceiverMeterRecord
/*     */ {
/*     */   int SystemSeconds;
/*     */   int DisplaySeconds;
/*     */   short MeterValue;
/*     */   int MeterTime;
/*     */   short Crc;
/*     */   static final int dataTypeByteLength = 16;
/*     */   Date SystemTimeStamp;
/*     */   Date DisplayTimeStamp;
/*     */   Date MeterTimeStamp;
/*     */   Date MeterDisplayTime;
/*     */   int RecordNumber;
/*     */ 
/*     */   ReceiverMeterRecord(byte[] dataStream)
/*     */   {
/* 401 */     ByteBuffer bb = ByteBuffer.wrap(dataStream);
/* 402 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/* 404 */     this.SystemSeconds = bb.getInt();
/* 405 */     this.DisplaySeconds = bb.getInt();
/* 406 */     this.MeterValue = bb.getShort();
/* 407 */     this.MeterTime = bb.getInt();
/* 408 */     this.Crc = bb.getShort();
/*     */ 
/* 410 */     convertTimes();
/*     */ 
/* 412 */     Tools.evaluateCrc(dataStream, this.Crc, getClass().getName());
/*     */   }
/*     */ 
/*     */   private void convertTimes()
/*     */   {
/* 417 */     this.SystemTimeStamp = Tools.convertReceiverTimeToDate(this.SystemSeconds);
/* 418 */     this.DisplayTimeStamp = Tools.convertReceiverTimeToDate(this.DisplaySeconds);
/* 419 */     this.MeterTimeStamp = Tools.convertReceiverTimeToDate(this.MeterTime);
/* 420 */     this.MeterDisplayTime = 
/* 421 */       Tools.convertReceiverTimeToDate(this.DisplaySeconds - (
/* 422 */       this.SystemSeconds - this.MeterTime));
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverMeterRecord
 * JD-Core Version:    0.6.0
 */