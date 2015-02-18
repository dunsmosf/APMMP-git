/*    */ package edu.virginia.dtc.G4DevKit;
/*    */ 
/*    */ import android.util.Log;
/*    */ 
/*    */ public class ReceiverCommException extends Exception
/*    */ {
/*    */   private static final long serialVersionUID = 3293776130594222308L;
/*    */ 
/*    */   public ReceiverCommException()
/*    */   {
/*    */   }
/*    */ 
/*    */   public ReceiverCommException(String message)
/*    */   {
/* 17 */     super(message);
/* 18 */     Log.e("G4 DevKit", message);
/*    */   }
/*    */ 
/*    */   public ReceiverCommException(String tag, String message)
/*    */   {
/* 25 */     super(message);
/* 26 */     Log.e(tag, message);
/*    */   }
/*    */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverCommException
 * JD-Core Version:    0.6.0
 */