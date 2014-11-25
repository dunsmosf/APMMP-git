/*    */ package edu.virginia.dtc.G4DevKit;
/*    */ 
/*    */ public class DexComTimeSpan
/*    */ {
/*    */   int m_days;
/*    */   int m_hours;
/*    */   int m_minutes;
/*    */   int m_seconds;
/*    */   int m_milliseconds;
/*    */   long m_timeSpanDays;
/*    */   long m_timeSpanHours;
/*    */   long m_timeSpanMinutes;
/*    */   long m_timeSpanSecs;
/*    */   long m_timeSpanMilliseconds;
/*    */   boolean m_isFuture;
/*    */ 
/*    */   public DexComTimeSpan(long hours, long minutes, long seconds)
/*    */   {
/* 25 */     this.m_timeSpanSecs = (hours * 3600L);
/* 26 */     this.m_timeSpanSecs += minutes * 60L;
/* 27 */     this.m_timeSpanSecs += seconds;
/*    */   }
/*    */ 
/*    */   public long getTimeSpanInSecs()
/*    */   {
/* 32 */     return this.m_timeSpanSecs;
/*    */   }
/*    */ 
/*    */   public DexComTimeSpan()
/*    */   {
/*    */   }
/*    */ 
/*    */   public DexComTimeSpan(long milliseconds)
/*    */   {
/* 44 */     this.m_timeSpanDays = (milliseconds / 86400000L);
/* 45 */     this.m_timeSpanHours = (milliseconds / 3600000L);
/* 46 */     this.m_timeSpanMinutes = (milliseconds / 60000L);
/* 47 */     this.m_timeSpanSecs = (milliseconds / 1000L);
/* 48 */     this.m_timeSpanMilliseconds = milliseconds;
/*    */ 
/* 50 */     this.m_isFuture = (milliseconds >= 0L);
/* 51 */     long millisecondsMag = Math.abs(milliseconds);
/*    */ 
/* 53 */     this.m_days = Math.abs((int)(millisecondsMag / 86400000L));
/* 54 */     millisecondsMag -= 86400000 * this.m_days;
/*    */ 
/* 56 */     this.m_hours = Math.abs((int)(millisecondsMag / 3600000L));
/* 57 */     millisecondsMag -= 3600000 * this.m_hours;
/*    */ 
/* 59 */     this.m_minutes = Math.abs((int)(millisecondsMag / 60000L));
/* 60 */     millisecondsMag -= 60000 * this.m_minutes;
/*    */ 
/* 62 */     this.m_seconds = Math.abs((int)(millisecondsMag / 1000L));
/* 63 */     millisecondsMag -= 1000 * this.m_seconds;
/*    */ 
/* 65 */     this.m_milliseconds = Math.abs((int)millisecondsMag);
/*    */   }
/*    */ 
/*    */   public static DexComTimeSpan fromDays(int days)
/*    */   {
/* 71 */     DexComTimeSpan result = new DexComTimeSpan();
/* 72 */     result.m_timeSpanSecs = (days * 24 * 3600);
/* 73 */     return result;
/*    */   }
/*    */ 
/*    */   public String toString()
/*    */   {
/* 80 */     String stringRep = "";
/*    */ 
/* 82 */     stringRep = stringRep + (this.m_days != 0 ? this.m_days + "." : "");
/* 83 */     stringRep = stringRep + (this.m_hours != 0 ? this.m_hours + ":" : "00:");
/* 84 */     stringRep = stringRep + (this.m_minutes != 0 ? this.m_minutes + ":" : "00:");
/* 85 */     stringRep = stringRep + (this.m_seconds != 0 ? Integer.valueOf(this.m_seconds) : "00");
/* 86 */     stringRep = stringRep + (this.m_milliseconds != 0 ? "." + this.m_milliseconds : "");
/*    */ 
/* 88 */     if (this.m_isFuture)
/*    */     {
/* 90 */       return stringRep;
/*    */     }
/*    */ 
/* 94 */     return "-" + stringRep;
/*    */   }
/*    */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.DexComTimeSpan
 * JD-Core Version:    0.6.0
 */