/*    */ package edu.virginia.dtc.G4DevKit;
/*    */ 
/*    */ import java.nio.ByteBuffer;
/*    */ import java.nio.ByteOrder;
/*    */ 
/*    */ class DatabasePageHeader
/*    */ {
/*    */   int FirstRecordIndex;
/*    */   int NumberOfRecords;
/*    */   ReceiverRecordType RecordType;
/*    */   byte Revision;
/*    */   int PageNumber;
/*    */   int Reserved2;
/*    */   int Reserved3;
/*    */   int Reserved4;
/*    */   short Crc;
/*    */   static final int dataTypeByteLength = 28;
/*    */ 
/*    */   DatabasePageHeader(byte[] dataStream)
/*    */   {
/* 60 */     if (dataStream.length == 28)
/*    */     {
/* 62 */       ByteBuffer bb = ByteBuffer.wrap(dataStream);
/* 63 */       bb.order(ByteOrder.LITTLE_ENDIAN);
/*    */ 
/* 65 */       this.FirstRecordIndex = bb.getInt();
/* 66 */       this.NumberOfRecords = bb.getInt();
/*    */ 
/* 68 */       this.RecordType = ReceiverRecordType.values()[bb.get()];
/* 69 */       this.Revision = bb.get();
/*    */ 
/* 71 */       this.PageNumber = bb.getInt();
/* 72 */       this.Reserved2 = bb.getInt();
/* 73 */       this.Reserved3 = bb.getInt();
/* 74 */       this.Reserved4 = bb.getInt();
/*    */ 
/* 76 */       this.Crc = bb.getShort();
/*    */     }
/*    */   }
/*    */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.DatabasePageHeader
 * JD-Core Version:    0.6.0
 */