/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */ import android.os.Parcel;
/*     */ import android.os.Parcelable;
/*     */ import android.os.Parcelable.Creator;
/*     */ import java.util.Date;
/*     */ 
/*     */ public class InsertionTimeRecord
/*     */   implements Parcelable
/*     */ {
/*     */   public Date SystemTime;
/*     */   public Date DisplayTime;
/*     */   public Date InsertionSystemTime;
/*     */   public Date InsertionDisplayTime;
/*     */   public boolean IsInserted;
/*     */   public SensorSessionState SessionState;
/*     */   public int RecordNumber;
/*  96 */   public static final Parcelable.Creator<InsertionTimeRecord> CREATOR = new Parcelable.Creator()
/*     */   {
/*     */     public InsertionTimeRecord createFromParcel(Parcel in)
/*     */     {
/* 102 */       return new InsertionTimeRecord(in);
/*     */     }
/*     */ 
/*     */     public InsertionTimeRecord[] newArray(int size)
/*     */     {
/* 109 */       return new InsertionTimeRecord[size];
/*     */     }
/*  96 */   };
/*     */ 
/*     */   public InsertionTimeRecord()
/*     */   {
/*     */   }
/*     */ 
/*     */   public InsertionTimeRecord(Parcel in)
/*     */   {
/*  41 */     readFromParcel(in);
/*     */   }
/*     */ 
/*     */   public String toString()
/*     */   {
/*  48 */     return "Inserted: " + (this.IsInserted ? "yes" : "no") + ", Time: " + 
/*  49 */       this.DisplayTime.toLocaleString() + ", Session State: " + 
/*  50 */       this.SessionState.name();
/*     */   }
/*     */ 
/*     */   public String toDetailedString()
/*     */   {
/*  55 */     return "Record # " + this.RecordNumber + "\nInserted: " + (
/*  56 */       this.IsInserted ? "yes" : "no") + ", Time: " + 
/*  57 */       this.DisplayTime.toLocaleString() + ", Session State: " + 
/*  58 */       this.SessionState.name();
/*     */   }
/*     */ 
/*     */   public int describeContents()
/*     */   {
/*  64 */     return 0;
/*     */   }
/*     */ 
/*     */   public void writeToParcel(Parcel dest, int flags)
/*     */   {
/*  72 */     dest.writeLong(this.SystemTime.getTime());
/*  73 */     dest.writeLong(this.DisplayTime.getTime());
/*  74 */     dest.writeLong(this.InsertionSystemTime.getTime());
/*  75 */     dest.writeLong(this.InsertionDisplayTime.getTime());
/*  76 */     dest.writeByte((byte)(this.IsInserted ? 1 : 0));
/*  77 */     dest.writeString(this.SessionState.name());
/*     */ 
/*  79 */     dest.writeInt(this.RecordNumber);
/*     */   }
/*     */ 
/*     */   private void readFromParcel(Parcel in)
/*     */   {
/*  85 */     this.SystemTime = new Date(in.readLong());
/*  86 */     this.DisplayTime = new Date(in.readLong());
/*  87 */     this.InsertionSystemTime = new Date(in.readLong());
/*  88 */     this.InsertionDisplayTime = new Date(in.readLong());
/*  89 */     this.IsInserted = (in.readByte() == 1);
/*  90 */     this.SessionState = SensorSessionState.valueOf(in
/*  91 */       .readString());
/*     */ 
/*  93 */     this.RecordNumber = in.readInt();
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.InsertionTimeRecord
 * JD-Core Version:    0.6.0
 */