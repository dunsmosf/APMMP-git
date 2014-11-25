/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */ import android.os.Parcel;
/*     */ import android.os.Parcelable;
/*     */ import android.os.Parcelable.Creator;


import java.util.Date;
/*     */ 
/*     */ public class EstimatedGlucoseRecord
/*     */   implements Parcelable
/*     */ {
/*     */   public Date SystemTime;
/*     */   public Date DisplayTime;
/*     */   public short Value;
/*     */   public String SpecialValue;
/*     */   public TrendArrow TrendArrow;
/*     */   public boolean IsNoisy;
/*     */   public boolean IsImmediateMatch;
/*     */   public boolean IsDisplayOnly;
/*     */   public int RecordNumber;
/* 123 */   public static final Parcelable.Creator<EstimatedGlucoseRecord> CREATOR = new Parcelable.Creator()
/*     */   {
/*     */     public EstimatedGlucoseRecord createFromParcel(Parcel in)
/*     */     {
/* 129 */       return new EstimatedGlucoseRecord(in);
/*     */     }
/*     */ 
/*     */     public EstimatedGlucoseRecord[] newArray(int size)
/*     */     {
/* 136 */       return new EstimatedGlucoseRecord[size];
/*     */     }
/* 123 */   };
/*     */ 
/*     */   public String toString()
/*     */   {
/*  49 */     return "Time: " + this.DisplayTime.toLocaleString() + ", EGV: " + 
/*  50 */       this.Value;
/*     */   }
/*     */ 
/*     */   public String toDetailedString()
/*     */   {
/*  56 */     return "Record # " + this.RecordNumber + "\nTime: " + 
/*  57 */       this.DisplayTime.toLocaleString() + ", EGV: " + this.Value + 
/*  58 */       "\nSpecial Value: " + this.SpecialValue + "\nTrend Arrow: " + 
/*  59 */       this.TrendArrow.name() + "\nIs Noisy: " + this.IsNoisy + 
/*  60 */       "\nIs Immediate Match: " + this.IsImmediateMatch + 
/*  61 */       "\nDisplay Only: " + this.IsDisplayOnly;
/*     */   }
/*     */ 
/*     */   public int getTrendArrowCode()
/*     */   {
/*  66 */     return this.TrendArrow.value;
/*     */   }
/*     */ 
/*     */   public String getTrendArrowDescription()
/*     */   {
/*  71 */     return this.TrendArrow.name();
/*     */   }
/*     */ 
/*     */   public EstimatedGlucoseRecord()
/*     */   {
/*     */   }
/*     */ 
/*     */   public EstimatedGlucoseRecord(Parcel in)
/*     */   {
/*  82 */     readFromParcel(in);
/*     */   }
/*     */ 
/*     */   public int describeContents()
/*     */   {
/*  88 */     return 0;
/*     */   }
/*     */ 
/*     */   public void writeToParcel(Parcel dest, int flags)
/*     */   {
/*  96 */     dest.writeLong(this.SystemTime.getTime());
/*  97 */     dest.writeLong(this.DisplayTime.getTime());
/*  98 */     dest.writeInt(this.Value);
/*  99 */     dest.writeString(this.SpecialValue);
/* 100 */     dest.writeString(this.TrendArrow.name());
/* 101 */     dest.writeByte((byte)(this.IsNoisy ? 1 : 0));
/* 102 */     dest.writeByte((byte)(this.IsImmediateMatch ? 1 : 0));
/* 103 */     dest.writeByte((byte)(this.IsDisplayOnly ? 1 : 0));
/*     */ 
/* 105 */     dest.writeInt(this.RecordNumber);
/*     */   }
/*     */ 
/*     */   private void readFromParcel(Parcel in)
/*     */   {
/* 111 */     this.SystemTime = new Date(in.readLong());
/* 112 */     this.DisplayTime = new Date(in.readLong());
/* 113 */     this.Value = (short)in.readInt();
/* 114 */     this.SpecialValue = in.readString();
/* 115 */     this.TrendArrow = TrendArrow.valueOf(in.readString());
/* 116 */     this.IsNoisy = (in.readByte() == 1);
/* 117 */     this.IsImmediateMatch = (in.readByte() == 1);
/* 118 */     this.IsDisplayOnly = (in.readByte() == 1);
/*     */ 
/* 120 */     this.RecordNumber = in.readInt();
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.EstimatedGlucoseRecord
 * JD-Core Version:    0.6.0
 */