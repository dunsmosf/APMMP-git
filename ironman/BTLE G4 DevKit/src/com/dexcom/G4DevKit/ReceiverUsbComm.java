/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import android.content.Context;
/*     */ import android.hardware.usb.UsbDevice;
/*     */ import android.hardware.usb.UsbDeviceConnection;
/*     */ import android.hardware.usb.UsbEndpoint;
/*     */ import android.hardware.usb.UsbInterface;
/*     */ import android.hardware.usb.UsbManager;
/*     */ import android.util.Log;
/*     */ import java.util.Collection;
/*     */ import java.util.HashMap;
/*     */ import java.util.Iterator;
/*     */ 
/*     */ public class ReceiverUsbComm
/*     */   implements IReceiverComm
/*     */ {
/*     */   private static final String TAG = "ReceiverUSBCommunication";
/*  19 */   

			private int txTotal = 0, rxTotal = 0;

/*     */ 
/*     */   ReceiverUsbComm(Context context)
/*     */   {
	
/*     */   }
/*     */ 
/*     */   boolean isCommPermissionGranted()
/*     */   {
/*  44 */     return true;
/*     */   }
/*     */ 
/*     */   public byte[] sendReceiverMessageForResponse(byte[] message)
/*     */     throws Exception
/*     */   {
/*  99 */     byte[] reply = new byte[0];
/*     */ 
/* 103 */     if (ReceiverUpdateService.bleUsbConnected)
/*     */     {
/* 109 */       int transferLength = doSendReceiverMessage(message);
/*     */ 
/* 112 */       if (transferLength >= 0)
/*     */       {
/* 116 */         reply = readReceiverMessage();
/*     */       }
/*     */       else
/*     */       {
/* 120 */         throw new Exception("No data has been sent to receiver");
/*     */       }
/*     */     }
/*     */     else
/*     */     {
/* 129 */       Log.i("ReceiverUSBCommunication", "No G4 receivers connected");
/*     */     }
/*     */ 
/* 132 */     return reply;
/*     */   }
/*     */ 
/*     */   private int doSendReceiverMessage(byte[] message)
/*     */     throws Exception
/*     */   {
	
///* 164 */     int outTransferLength = receiverConnection.bulkTransfer(this.m_outEndpoint, message, message.length, TIMEOUT);
///*     */ 
///* 167 */     return outTransferLength;

				ReceiverUpdateService.btle.write(message);
	
				txTotal += message.length;
				Log.d("ReceiverUpdateService", "TX: "+message.length+" TX Total: "+txTotal);
				
				return message.length;
/*     */   }
/*     */ 
/*     */   private byte[] readReceiverMessage()
/*     */     throws Exception
/*     */   {
	
///* 174 */     byte[] packet = new byte[32773];
///*     */ 
///* 177 */     int inFullPacketTransferLength = this.m_receiverConnection.bulkTransfer(this.m_inEndpoint, packet, packet.length, TIMEOUT);
///* 179 */     if (inFullPacketTransferLength <= 0)
///*     */     {
///* 181 */       Log.e("ReceiverUSBCommunication", "No data has been read from receiver");
///*     */     }
///*     */ 
///* 184 */     return packet;

				byte[] packet = new byte[600];
	
				packet = ReceiverUpdateService.btle.read(packet.length);
	
				rxTotal += packet.length;
				Log.d("ReceiverUpdateService", "RX: "+packet.length+" RX Total: "+rxTotal);
				
				return packet;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverUsbComm
 * JD-Core Version:    0.6.0
 */