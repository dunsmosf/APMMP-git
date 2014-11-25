/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */ import android.os.Parcel;
/*     */ import android.os.Parcelable;
/*     */ import android.os.Parcelable.Creator;
/*     */ import java.lang.reflect.Field;
/*     */ import java.util.Date;
/*     */ 
/*     */ public class SettingsRecord
/*     */   implements Parcelable
/*     */ {
/*     */   public Date SystemTime;
/*     */   public Date DisplayTime;
/*     */   public long DisplayTimeOffset;
/*     */   public long SystemTimeOffset;
/*     */   public boolean IsBlinded;
/*     */   public LanguageType Language;
/*     */   public short LanguageCode;
/*     */   public String TransmitterId;
/*     */   public boolean IsTwentyFourHourTime;
/*     */   public boolean IsSetUpWizardEnabled;
/*     */   public SetUpWizardState WizardState;
/*     */   public byte WizardStateCode;
/*     */   public boolean TimeLossOccurred;
/*     */   public UserAlertProfile AlertProfile;
/*     */   public short HighAlarmLevelValue;
/*     */   public short HighAlarmSnoozeTime;
/*     */   public boolean IsHighAlarmEnabled;
/*     */   public short LowAlarmLevelValue;
/*     */   public short LowAlarmSnoozeTime;
/*     */   public boolean IsLowAlarmEnabled;
/*     */   public short RiseRateValue;
/*     */   public boolean IsRiseRateAlarmEnabled;
/*     */   public short FallRateValue;
/*     */   public boolean IsFallRateAlarmEnabled;
/*     */   public short OutOfRangeAlarmSnoozeTime;
/*     */   public boolean IsOutOfRangeAlarmEnabled;
/*     */   public int RecordNumber;
/* 200 */   public static final Parcelable.Creator<SettingsRecord> CREATOR = new Parcelable.Creator()
/*     */   {
/*     */     public SettingsRecord createFromParcel(Parcel in)
/*     */     {
/* 206 */       return new SettingsRecord(in);
/*     */     }
/*     */ 
/*     */     public SettingsRecord[] newArray(int size)
/*     */     {
/* 213 */       return new SettingsRecord[size];
/*     */     }
/* 200 */   };
/*     */ 
/*     */   public SettingsRecord()
/*     */   {
/*     */   }
/*     */ 
/*     */   public SettingsRecord(Parcel in)
/*     */   {
/*  85 */     readFromParcel(in);
/*     */   }
/*     */ 
/*     */   public String toString()
/*     */   {
/*  92 */     return "Time: " + this.DisplayTime.toLocaleString();
/*     */   }
/*     */ 
/*     */   public String toDetailedString()
/*     */   {
/*  99 */     String description = "";
/*     */ 
/* 101 */     Field[] fields = getClass().getFields();
/* 102 */     for (Field f : fields) {
/*     */       try
/*     */       {
/* 105 */         description = description + f.getName() + ": " + f.get(this).toString() + 
/* 106 */           "\n";
/*     */       }
/*     */       catch (IllegalArgumentException e)
/*     */       {
/* 111 */         e.printStackTrace();
/*     */       }
/*     */       catch (IllegalAccessException e)
/*     */       {
/* 116 */         e.printStackTrace();
/*     */       }
/*     */     }
/* 119 */     return description;
/*     */   }
/*     */ 
/*     */   public int describeContents()
/*     */   {
/* 125 */     return 0;
/*     */   }
/*     */ 
/*     */   public void writeToParcel(Parcel dest, int flags)
/*     */   {
/* 133 */     dest.writeLong(this.SystemTime.getTime());
/* 134 */     dest.writeLong(this.DisplayTime.getTime());
/* 135 */     dest.writeLong(this.DisplayTimeOffset);
/* 136 */     dest.writeLong(this.SystemTimeOffset);
/* 137 */     dest.writeByte((byte)(this.IsBlinded ? 1 : 0));
/* 138 */     dest.writeString(this.Language.name());
/* 139 */     dest.writeInt(this.LanguageCode);
/* 140 */     dest.writeString(this.TransmitterId);
/* 141 */     dest.writeByte((byte)(this.IsTwentyFourHourTime ? 1 : 0));
/* 142 */     dest.writeByte((byte)(this.IsSetUpWizardEnabled ? 1 : 0));
/* 143 */     dest.writeString(this.WizardState.name());
/* 144 */     dest.writeByte(this.WizardStateCode);
/* 145 */     dest.writeByte((byte)(this.TimeLossOccurred ? 1 : 0));
/*     */ 
/* 147 */     dest.writeString(this.AlertProfile.name());
/* 148 */     dest.writeInt(this.HighAlarmLevelValue);
/* 149 */     dest.writeInt(this.HighAlarmSnoozeTime);
/* 150 */     dest.writeByte((byte)(this.IsHighAlarmEnabled ? 1 : 0));
/* 151 */     dest.writeInt(this.LowAlarmLevelValue);
/* 152 */     dest.writeInt(this.LowAlarmSnoozeTime);
/* 153 */     dest.writeByte((byte)(this.IsLowAlarmEnabled ? 1 : 0));
/* 154 */     dest.writeInt(this.RiseRateValue);
/* 155 */     dest.writeByte((byte)(this.IsRiseRateAlarmEnabled ? 1 : 0));
/* 156 */     dest.writeInt(this.FallRateValue);
/* 157 */     dest.writeByte((byte)(this.IsFallRateAlarmEnabled ? 1 : 0));
/* 158 */     dest.writeInt(this.OutOfRangeAlarmSnoozeTime);
/* 159 */     dest.writeByte((byte)(this.IsOutOfRangeAlarmEnabled ? 1 : 0));
/*     */ 
/* 161 */     dest.writeInt(this.RecordNumber);
/*     */   }
/*     */ 
/*     */   private void readFromParcel(Parcel in)
/*     */   {
/* 167 */     this.SystemTime = new Date(in.readLong());
/* 168 */     this.DisplayTime = new Date(in.readLong());
/* 169 */     this.DisplayTimeOffset = in.readLong();
/* 170 */     this.SystemTimeOffset = in.readLong();
/* 171 */     this.IsBlinded = (in.readByte() == 1);
/* 172 */     this.Language = LanguageType.valueOf(in.readString());
/* 173 */     this.LanguageCode = (short)in.readInt();
/* 174 */     this.TransmitterId = in.readString();
/* 175 */     this.IsTwentyFourHourTime = (in.readByte() == 1);
/* 176 */     this.IsSetUpWizardEnabled = (in.readByte() == 1);
/* 177 */     this.WizardState = SetUpWizardState.valueOf(in
/* 178 */       .readString());
/* 179 */     this.WizardStateCode = in.readByte();
/* 180 */     this.TimeLossOccurred = (in.readByte() == 1);
/*     */ 
/* 182 */     this.AlertProfile = UserAlertProfile.valueOf(in
/* 183 */       .readString());
/* 184 */     this.HighAlarmLevelValue = (short)in.readInt();
/* 185 */     this.HighAlarmSnoozeTime = (short)in.readInt();
/* 186 */     this.IsHighAlarmEnabled = (in.readByte() == 1);
/* 187 */     this.LowAlarmLevelValue = (short)in.readInt();
/* 188 */     this.LowAlarmSnoozeTime = (short)in.readInt();
/* 189 */     this.IsLowAlarmEnabled = (in.readByte() == 1);
/* 190 */     this.RiseRateValue = (short)in.readInt();
/* 191 */     this.IsRiseRateAlarmEnabled = (in.readByte() == 1);
/* 192 */     this.FallRateValue = (short)in.readInt();
/* 193 */     this.IsFallRateAlarmEnabled = (in.readByte() == 1);
/* 194 */     this.OutOfRangeAlarmSnoozeTime = (short)in.readInt();
/* 195 */     this.IsOutOfRangeAlarmEnabled = (in.readByte() == 1);
/*     */ 
/* 197 */     this.RecordNumber = in.readInt();
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.SettingsRecord
 * JD-Core Version:    0.6.0
 */