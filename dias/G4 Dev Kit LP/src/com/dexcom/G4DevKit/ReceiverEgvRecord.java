/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import com.dexcom.G4DevKit.enums.TrendArrow;

/*     */ import java.nio.ByteBuffer;
/*     */ import java.nio.ByteOrder;
/*     */ import java.util.Date;
/*     */ 
/*     */ class ReceiverEgvRecord
/*     */ {
/*     */   int SystemSeconds;
/*     */   int DisplaySeconds;
/*     */   short GlucoseValueWithFlags;
/*     */   byte TrendArrowAndNoise;
/*     */   short Crc;
/*     */   static final int dataTypeByteLength = 13;
/*     */   boolean IsImmediateMatch;
/*     */   TrendArrow TrendArrow;
/*     */   NoiseMode NoiseMode;
/*     */   boolean IsDisplayOnly;
/*     */   short GlucoseValue;
/*     */   String SpecialValue;
/*     */   Date SystemTimeStamp;
/*     */   Date DisplayTimeStamp;
/*     */   int RecordNumber;
/*     */ 
/*     */   ReceiverEgvRecord(byte[] dataStream)
/*     */   {
/* 297 */     ByteBuffer bb = ByteBuffer.wrap(dataStream);
/* 298 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/* 300 */     this.SystemSeconds = bb.getInt();
/* 301 */     this.DisplaySeconds = bb.getInt();
/* 302 */     this.GlucoseValueWithFlags = bb.getShort();
/* 303 */     this.TrendArrowAndNoise = bb.get();
/* 304 */     this.Crc = bb.getShort();
/*     */ 
/* 306 */     convertTimesAndValues();
/*     */ 
/* 308 */     Tools.evaluateCrc(dataStream, this.Crc, getClass().getName());
/*     */   }
/*     */ 
/*     */   EstimatedGlucoseRecord extractEgvData()
/*     */   {
/* 314 */     EstimatedGlucoseRecord record = new EstimatedGlucoseRecord();
/*     */ 
/* 316 */     record.SystemTime = this.SystemTimeStamp;
/* 317 */     record.DisplayTime = this.DisplayTimeStamp;
/* 318 */     record.IsDisplayOnly = this.IsDisplayOnly;
/* 319 */     record.IsImmediateMatch = this.IsImmediateMatch;
/* 320 */     record.IsNoisy = ((this.NoiseMode != NoiseMode.None) && 
/* 321 */       (this.NoiseMode != NoiseMode.NotComputed) && 
/* 322 */       (this.NoiseMode != NoiseMode.Clean));
/* 323 */     record.RecordNumber = this.RecordNumber;
/* 324 */     record.TrendArrow = this.TrendArrow;
/*     */ 
/* 328 */     if (this.SpecialValue.isEmpty())
/*     */     {
/* 330 */       record.Value = this.GlucoseValue;
/*     */     }
/*     */     else
/*     */     {
/* 334 */       record.Value = 0;
/*     */ 
/* 336 */       if ((this.SpecialValue.equals("SensorNotActive")) || 
/* 337 */         (this.SpecialValue.equals("NoAntenna")) || 
/* 338 */         (this.SpecialValue.equals("SensorOutOfCal")) || 
/* 339 */         (this.SpecialValue.equals("RFBadStatus")))
/*     */       {
/* 341 */         record.SpecialValue = this.SpecialValue;
/*     */       }
/* 343 */       else if ((this.SpecialValue.equals("Aberration0")) || 
/* 344 */         (this.SpecialValue.equals("Aberration1")) || 
/* 345 */         (this.SpecialValue.equals("Aberration2")) || 
/* 346 */         (this.SpecialValue.equals("Aberration3")))
/*     */       {
/* 348 */         record.SpecialValue = "Aberration";
/*     */       }
/*     */       else
/*     */       {
/* 352 */         record.SpecialValue = "Unknown";
/*     */       }
/*     */     }
/*     */ 
/* 356 */     return record;
/*     */   }
/*     */ 
/*     */   private void convertTimesAndValues()
/*     */   {
/* 361 */     this.IsImmediateMatch = ((this.TrendArrowAndNoise & 0xFFFFFF80) != 0);
/* 362 */     this.TrendArrow = TrendArrow.values()[(this.TrendArrowAndNoise & 0xF)];
/* 363 */     this.NoiseMode = NoiseMode.values()[((this.TrendArrowAndNoise & 0x70) >> 4)];
/* 364 */     this.IsDisplayOnly = ((this.GlucoseValueWithFlags & 0xFFFF8000) != 0);
/* 365 */     this.GlucoseValue = (short)(this.GlucoseValueWithFlags & 0x3FF);
/*     */ 
/* 367 */     this.SystemTimeStamp = Tools.convertReceiverTimeToDate(this.SystemSeconds);
/* 368 */     this.DisplayTimeStamp = Tools.convertReceiverTimeToDate(this.DisplaySeconds);
/*     */ 
/* 370 */     this.SpecialValue = "";
/*     */ 
/* 372 */     if (SpecialGlucoseValues.isDefined(this.GlucoseValue).booleanValue())
/*     */     {
/* 374 */       this.SpecialValue = SpecialGlucoseValues.toString(this.GlucoseValue);
/*     */     }
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverEgvRecord
 * JD-Core Version:    0.6.0
 */