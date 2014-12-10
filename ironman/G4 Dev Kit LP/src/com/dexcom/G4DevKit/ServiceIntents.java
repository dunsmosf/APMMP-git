/*    */ package com.dexcom.G4DevKit;
/*    */ 
/*    */ public class ServiceIntents
/*    */ {
/*  5 */   private static final IntentFactory<EstimatedGlucoseRecord> m_egvIntents = new IntentFactory(
/*  6 */     EstimatedGlucoseRecord.class);
/*    */ 
/*  7 */   private static final IntentFactory<MeterRecord> m_meterIntents = new IntentFactory(
/*  8 */     MeterRecord.class);
/*    */ 
/*  9 */   private static final IntentFactory<SettingsRecord> m_settingsIntents = new IntentFactory(
/* 10 */     SettingsRecord.class);
/*    */ 
/* 11 */   private static final IntentFactory<InsertionTimeRecord> m_insertionIntents = new IntentFactory(
/* 12 */     InsertionTimeRecord.class);
/*    */   public static final String UPDATE_RECEIVER_DATA = "com.dexcom.g4devkit.action.UPDATE_RECEIVER_DATA";
/* 20 */   public static final String NEW_EGV_DATA = m_egvIntents
/* 20 */     .newDataAvailableIntent();
/*    */ 
/* 22 */   public static final String NEW_METER_DATA = m_meterIntents
/* 22 */     .newDataAvailableIntent();
/*    */ 
/* 24 */   public static final String NEW_SETTINGS_DATA = m_settingsIntents
/* 24 */     .newDataAvailableIntent();
/*    */ 
/* 26 */   public static final String NEW_INSERTION_DATA = m_insertionIntents
/* 26 */     .newDataAvailableIntent();
/*    */   public static final String UPDATE_CURRENT_RECEIVER_DATA = "com.dexcom.g4devkit.action.UPDATE_CURRENT_RECEIVER_DATA";

			public static final String UNKNOWN_ERROR = "com.dexcom.G4DevKit.UNKNOWN_ERROR"; 
			public static final String NO_DATA_ERROR = "com.dexcom.G4DevKit.NO_DATA_ERROR"; 
			public static final String RECEIVER_CONNECTED = "com.dexcom.G4DevKit.RECEIVER_CONNECTED"; 
/*    */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ServiceIntents
 * JD-Core Version:    0.6.0
 */