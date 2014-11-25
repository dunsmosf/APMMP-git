/*    */ package edu.virginia.dtc.G4DevKit;
/*    */ 
/*    */ import android.util.Log;
/*    */ 
/*    */ public class IntentFactory<T>
/*    */ {
/*    */   private static final String TAG = "IntentFactory";
/*  9 */   private final String m_prefix = "com.dexcom.g4devkit.";
/*    */   private final Class<T> m_recordClass;
/*    */   private final String m_dataTypeParam;
/*    */ 
/*    */   public IntentFactory(Class<T> recordClass)
/*    */   {
/* 16 */     this.m_recordClass = recordClass;
/* 17 */     this.m_dataTypeParam = generateDataTypeParam();
/*    */   }
/*    */ 
/*    */   public String newDataAvailableIntent()
/*    */   {
/* 22 */     return "com.dexcom.g4devkit.action.NEW_" + this.m_dataTypeParam + "_DATA";
/*    */   }
/*    */ 
/*    */   public String dataNameInParcel()
/*    */   {
/* 27 */     return "com.dexcom.g4devkit." + this.m_dataTypeParam + "_DATA";
/*    */   }
/*    */ 
/*    */   private final String generateDataTypeParam()
/*    */   {
/* 32 */     String className = this.m_recordClass.getName();
/* 33 */     String dataTypeParam = "";
/*    */ 
/* 35 */     if (className.equals("edu.virginia.dtc.G4DevKit.EstimatedGlucoseRecord"))
/*    */     {
/* 37 */       dataTypeParam = "EGV";
/*    */     }
/* 39 */     else if (className.equals("edu.virginia.dtc.G4DevKit.MeterRecord"))
/*    */     {
/* 41 */       dataTypeParam = "METER";
/*    */     }
/* 43 */     else if (className.equals("edu.virginia.dtc.G4DevKit.SettingsRecord"))
/*    */     {
/* 45 */       dataTypeParam = "SETTINGS";
/*    */     }
/* 47 */     else if (className.equals("edu.virginia.dtc.G4DevKit.InsertionTimeRecord"))
/*    */     {
/* 49 */       dataTypeParam = "INSERTION";
/*    */     }
/*    */     else
/*    */     {
/* 53 */       Log.e("IntentFactory", "Cannot generate intent for data type " + className);
/*    */     }
/*    */ 
/* 56 */     return dataTypeParam;
/*    */   }
/*    */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.IntentFactory
 * JD-Core Version:    0.6.0
 */