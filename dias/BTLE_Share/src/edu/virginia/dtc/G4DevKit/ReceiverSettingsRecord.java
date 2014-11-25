/*     */ package edu.virginia.dtc.G4DevKit;
/*     */ 
/*     */ import java.nio.ByteBuffer;
/*     */ import java.nio.ByteOrder;
/*     */ import java.util.Date;
/*     */ 
/*     */ class ReceiverSettingsRecord
/*     */ {
/*     */   int SystemSeconds;
/*     */   int DisplaySeconds;
/*     */   int SystemTimeOffset;
/*     */   int DisplayTimeOffset;
/*     */   int TransmitterId;
/*     */   private final int EnableFlags;
/*     */   short HighAlarmLevelValue;
/*     */   short HighAlarmSnoozeTime;
/*     */   short LowAlarmLevelValue;
/*     */   short LowAlarmSnoozeTime;
/*     */   short RiseRateValue;
/*     */   short FallRateValue;
/*     */   short OutOfRangeAlarmSnoozeTime;
/*     */   LanguageType Language;
/*     */   UserAlertProfile CurrentUserProfile;
/*     */   SetUpWizardState CurrentSetUpWizardState;
/*     */   byte Reserved_0;
/*     */   byte Reserved_1;
/*     */   byte Reserved_2;
/*     */   byte Reserved_3;
/*     */   short Crc;
/*     */   static final int dataTypeByteLength = 48;
/*     */   Date SystemTimeStamp;
/*     */   Date DisplayTimeStamp;
/*     */   boolean IsBlinded;
/*     */   boolean IsOutOfRangeAlarmEnabled;
/*     */   boolean IsFallRateAlarmEnabled;
/*     */   boolean IsRiseRateAlarmEnabled;
/*     */   boolean IsHighAlarmEnabled;
/*     */   boolean IsLowAlarmEnabled;
/*     */   boolean IsAdvancedMode;
/*     */   boolean IsManufacturingMode;
/*     */   boolean IsTwentyFourHourTime;
/*     */   boolean TimeLossOccurred;
/*     */   int RecordNumber;
/*     */ 
/*     */   ReceiverSettingsRecord(byte[] dataStream)
/*     */   {
/* 179 */     ByteBuffer bb = ByteBuffer.wrap(dataStream);
/* 180 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/* 182 */     this.SystemSeconds = bb.getInt();
/* 183 */     this.DisplaySeconds = bb.getInt();
/* 184 */     this.SystemTimeOffset = bb.getInt();
/* 185 */     this.DisplayTimeOffset = bb.getInt();
/* 186 */     this.TransmitterId = bb.getInt();
/* 187 */     this.EnableFlags = bb.getInt();
/* 188 */     this.HighAlarmLevelValue = bb.getShort();
/* 189 */     this.HighAlarmSnoozeTime = bb.getShort();
/* 190 */     this.LowAlarmLevelValue = bb.getShort();
/* 191 */     this.LowAlarmSnoozeTime = bb.getShort();
/* 192 */     this.RiseRateValue = bb.getShort();
/* 193 */     this.FallRateValue = bb.getShort();
/* 194 */     this.OutOfRangeAlarmSnoozeTime = bb.getShort();
/* 195 */     this.Language = LanguageType.getEnumFromValue(bb
/* 196 */       .getShort());
/* 197 */     this.CurrentUserProfile = UserAlertProfile.values()[bb
/* 198 */       .get()];
/* 199 */     this.CurrentSetUpWizardState = 
/* 200 */       SetUpWizardState.values()[bb.get()];
/* 201 */     this.Reserved_0 = bb.get();
/* 202 */     this.Reserved_1 = bb.get();
/* 203 */     this.Reserved_2 = bb.get();
/* 204 */     this.Reserved_3 = bb.get();
/* 205 */     this.Crc = bb.getShort();
/*     */ 
/* 207 */     this.IsBlinded = ((this.EnableFlags & DataFlags.Blinded.value) != 0);
/* 208 */     this.IsOutOfRangeAlarmEnabled = ((this.EnableFlags & DataFlags.OutOfRangeEnabled.value) != 0);
/* 209 */     this.IsFallRateAlarmEnabled = ((this.EnableFlags & DataFlags.FallRateEnabled.value) != 0);
/* 210 */     this.IsRiseRateAlarmEnabled = ((this.EnableFlags & DataFlags.RiseRateEnabled.value) != 0);
/* 211 */     this.IsHighAlarmEnabled = ((this.EnableFlags & DataFlags.HighAlarmEnabled.value) != 0);
/* 212 */     this.IsLowAlarmEnabled = ((this.EnableFlags & DataFlags.LowAlarmEnabled.value) != 0);
/* 213 */     this.IsAdvancedMode = ((this.EnableFlags & DataFlags.AdvancedMode.value) != 0);
/* 214 */     this.IsManufacturingMode = ((this.EnableFlags & DataFlags.ManufacturingMode.value) != 0);
/* 215 */     this.IsTwentyFourHourTime = ((this.EnableFlags & DataFlags.TwentyFourHourTime.value) != 0);
/* 216 */     this.TimeLossOccurred = ((this.EnableFlags & DataFlags.TimeLossOccurred.value) != 0);
/*     */ 
/* 218 */     convertTimes();
/*     */ 
/* 220 */     Tools.evaluateCrc(dataStream, this.Crc, getClass().getName());
/*     */   }
/*     */ 
/*     */   SettingsRecord extractSettingsData()
/*     */   {
/* 227 */     SettingsRecord record = new SettingsRecord();
/*     */ 
/* 230 */     record.SystemTime = this.SystemTimeStamp;
/* 231 */     record.DisplayTime = this.DisplayTimeStamp;
/* 232 */     record.RecordNumber = this.RecordNumber;
/* 233 */     record.DisplayTimeOffset = this.DisplayTimeOffset;
/* 234 */     record.SystemTimeOffset = this.SystemTimeOffset;
/* 235 */     record.IsBlinded = this.IsBlinded;
/* 236 */     record.Language = this.Language;
/* 237 */     record.LanguageCode = this.Language.value;
/* 238 */     record.TransmitterId = Tools.convertTxIdToTxCode(this.TransmitterId);
/* 239 */     record.IsTwentyFourHourTime = this.IsTwentyFourHourTime;
/* 240 */     record.IsSetUpWizardEnabled = false;
/*     */ 
/* 242 */     record.WizardState = this.CurrentSetUpWizardState;
/* 243 */     record.TimeLossOccurred = this.TimeLossOccurred;
/*     */ 
/* 246 */     record.AlertProfile = this.CurrentUserProfile;
/* 247 */     record.HighAlarmLevelValue = this.HighAlarmLevelValue;
/* 248 */     record.HighAlarmSnoozeTime = this.HighAlarmSnoozeTime;
/* 249 */     record.IsHighAlarmEnabled = this.IsHighAlarmEnabled;
/* 250 */     record.LowAlarmLevelValue = this.LowAlarmLevelValue;
/* 251 */     record.LowAlarmSnoozeTime = this.LowAlarmSnoozeTime;
/* 252 */     record.IsLowAlarmEnabled = this.IsLowAlarmEnabled;
/* 253 */     record.RiseRateValue = this.RiseRateValue;
/* 254 */     record.IsRiseRateAlarmEnabled = this.IsRiseRateAlarmEnabled;
/* 255 */     record.FallRateValue = this.FallRateValue;
/* 256 */     record.IsFallRateAlarmEnabled = this.IsFallRateAlarmEnabled;
/* 257 */     record.OutOfRangeAlarmSnoozeTime = this.OutOfRangeAlarmSnoozeTime;
/* 258 */     record.IsOutOfRangeAlarmEnabled = this.IsOutOfRangeAlarmEnabled;
/*     */ 
/* 260 */     return record;
/*     */   }
/*     */ 
/*     */   private void convertTimes()
/*     */   {
/* 265 */     this.SystemTimeStamp = Tools.convertReceiverTimeToDate(this.SystemSeconds);
/* 266 */     this.DisplayTimeStamp = Tools.convertReceiverTimeToDate(this.DisplaySeconds);
/*     */   }
/*     */ 
/*     */   private static enum DataFlags
/*     */   {
/* 156 */     Blinded(1), 
/* 157 */     TwentyFourHourTime(2), 
/* 158 */     ManufacturingMode(4), 
/* 159 */     AdvancedMode(8), 
/* 160 */     LowAlarmEnabled(16), 
/* 161 */     HighAlarmEnabled(32), 
/* 162 */     RiseRateEnabled(64), 
/* 163 */     FallRateEnabled(128), 
/* 164 */     OutOfRangeEnabled(256), 
/* 165 */     TimeLossOccurred(512);
/*     */ 
/*     */     final int value;
/*     */ 
/*     */     private DataFlags(int value)
/*     */     {
/* 172 */       this.value = value;
/*     */     }
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverSettingsRecord
 * JD-Core Version:    0.6.0
 */