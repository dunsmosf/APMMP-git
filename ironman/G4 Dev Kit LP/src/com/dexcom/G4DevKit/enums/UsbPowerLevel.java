/*    */ package com.dexcom.G4DevKit.enums;
/*    */ 
/*    */ import java.util.EnumSet;
/*    */ import java.util.HashMap;
/*    */ import java.util.Map;
/*    */ 
/*    */ public enum UsbPowerLevel
/*    */ {
/*  9 */   Pwr100mA(0), 
/* 10 */   Pwr500mA(1), 
/* 11 */   PwrMax(2), 
/* 12 */   PwrSuspend(3), 
/* 13 */   Unknown(4);
/*    */ 
/*    */   public final byte value;
/*    */   private static final Map<Byte, UsbPowerLevel> lookup;
/*    */ 
/* 16 */   static { lookup = new HashMap();
/*    */   }
/*    */ 
/*    */   private UsbPowerLevel(int value)
/*    */   {
/* 21 */     this.value = (byte)value;
/*    */   }
/*    */ 
/*    */   public static UsbPowerLevel getEnumFromValue(byte code)
/*    */   {
/* 28 */     for (UsbPowerLevel rc : EnumSet.allOf(UsbPowerLevel.class)) {
/* 29 */       lookup.put(Byte.valueOf(rc.value), rc);
/*    */     }
/* 31 */     return (UsbPowerLevel)lookup.get(Byte.valueOf(code));
/*    */   }
/*    */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.enums.UsbPowerLevel
 * JD-Core Version:    0.6.0
 */