/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */ import java.nio.ByteBuffer;
/*     */ import java.nio.ByteOrder;
/*     */ import java.util.Date;
/*     */ 
/*     */ class ReceiverInsertionTimeRecord
/*     */ {
/*     */   int SystemSeconds;
/*     */   int DisplaySeconds;
/*     */   int InsertionTimeSeconds;
/*     */   SensorSessionState SessionState;
/*     */   short Crc;
/*     */   static final int dataTypeByteLength = 15;
/*     */   Date SystemTimeStamp;
/*     */   Date DisplayTimeStamp;
/*     */   Date InsertionSystemTime;
/*     */   Date InsertionDisplayTime;
/*     */   boolean IsInserted;
/*     */   int RecordNumber;
/*     */   private Date InsertionTimeStamp;
/*     */ 
/*     */   ReceiverInsertionTimeRecord(byte[] dataStream)
/*     */   {
/* 450 */     ByteBuffer bb = ByteBuffer.wrap(dataStream);
/* 451 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/* 453 */     this.SystemSeconds = bb.getInt();
/* 454 */     this.DisplaySeconds = bb.getInt();
/* 455 */     this.InsertionTimeSeconds = bb.getInt();
/* 456 */     this.SessionState = SensorSessionState.values()[bb
/* 457 */       .get()];
/* 458 */     this.Crc = bb.getShort();
/*     */ 
/* 460 */     convertTimesAndValues();
/*     */ 
/* 462 */     Tools.evaluateCrc(dataStream, this.Crc, getClass().getName());
/*     */   }
/*     */ 
/*     */   private void convertTimesAndValues()
/*     */   {
/* 467 */     this.SystemTimeStamp = Tools.convertReceiverTimeToDate(this.SystemSeconds);
/* 468 */     this.DisplayTimeStamp = Tools.convertReceiverTimeToDate(this.DisplaySeconds);
/* 469 */     this.InsertionTimeStamp = 
/* 470 */       Tools.convertReceiverTimeToDate(this.InsertionTimeSeconds);
/*     */ 
/* 472 */     this.IsInserted = (this.InsertionTimeSeconds != -1);
/*     */ 
/* 474 */     this.InsertionSystemTime = (this.IsInserted ? this.InsertionTimeStamp : 
/* 475 */       Values.MinDate);
/*     */ 
/* 477 */     this.InsertionDisplayTime = (this.IsInserted ? 
/* 478 */       Tools.convertReceiverTimeToDate(this.InsertionTimeSeconds + (
/* 479 */       this.DisplaySeconds - this.SystemSeconds)) : 
/* 480 */       Values.MinDate);
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverInsertionTimeRecord
 * JD-Core Version:    0.6.0
 */