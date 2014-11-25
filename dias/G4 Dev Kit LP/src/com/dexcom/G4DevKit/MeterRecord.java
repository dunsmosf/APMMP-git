/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import android.os.Parcel;
/*     */ import android.os.Parcelable;
/*     */ import android.os.Parcelable.Creator;
/*     */ import java.util.Date;
/*     */ 
/*     */ public class MeterRecord
/*     */   implements Parcelable
/*     */ {
/*     */   public short Value;
/*     */   public Date MeterSystemTime;
/*     */   public Date MeterDisplayTime;
/*     */   public Date SystemTime;
/*     */   public Date DisplayTime;
/*     */   public int RecordNumber;
/*  88 */   public static final Parcelable.Creator<MeterRecord> CREATOR = new Parcelable.Creator()
/*     */   {
/*     */     public MeterRecord createFromParcel(Parcel in)
/*     */     {
/*  94 */       return new MeterRecord(in);
/*     */     }
/*     */ 
/*     */     public MeterRecord[] newArray(int size)
/*     */     {
/* 101 */       return new MeterRecord[size];
/*     */     }
/*  88 */   };
/*     */ 
/*     */   public String toString()
/*     */   {
/*  34 */     return "Time: " + this.DisplayTime.toLocaleString() + ", SMBG: " + 
/*  35 */       this.Value;
/*     */   }
/*     */ 
/*     */   public String toDetailedString()
/*     */   {
/*  41 */     return "Record # " + this.RecordNumber + "\nTime: " + 
/*  42 */       this.DisplayTime.toLocaleString() + ", SMBG: " + this.Value + 
/*  43 */       "\nMeter Time: " + this.MeterDisplayTime.toLocaleString();
/*     */   }
/*     */ 
/*     */   public MeterRecord()
/*     */   {
/*     */   }
/*     */ 
/*     */   public MeterRecord(Parcel in)
/*     */   {
/*  54 */     readFromParcel(in);
/*     */   }
/*     */ 
/*     */   public int describeContents()
/*     */   {
/*  60 */     return 0;
/*     */   }
/*     */ 
/*     */   public void writeToParcel(Parcel dest, int flags)
/*     */   {
/*  68 */     dest.writeInt(this.Value);
/*  69 */     dest.writeLong(this.MeterSystemTime.getTime());
/*  70 */     dest.writeLong(this.MeterDisplayTime.getTime());
/*  71 */     dest.writeLong(this.SystemTime.getTime());
/*  72 */     dest.writeLong(this.DisplayTime.getTime());
/*  73 */     dest.writeInt(this.RecordNumber);
/*     */   }
/*     */ 
/*     */   private void readFromParcel(Parcel in)
/*     */   {
/*  79 */     this.Value = (short)in.readInt();
/*  80 */     this.MeterSystemTime = new Date(in.readLong());
/*  81 */     this.MeterDisplayTime = new Date(in.readLong());
/*  82 */     this.SystemTime = new Date(in.readLong());
/*  83 */     this.DisplayTime = new Date(in.readLong());
/*     */ 
/*  85 */     this.RecordNumber = in.readInt();
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.MeterRecord
 * JD-Core Version:    0.6.0
 */