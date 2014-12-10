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
/*  19 */   private boolean isReceiverAttached = false;
/*     */   private final UsbManager m_usbManager;
/*     */   private UsbDevice m_receiver;
/*     */   private UsbInterface m_receiverInterface;
/*     */   private UsbEndpoint m_outEndpoint;
/*     */   private UsbEndpoint m_inEndpoint;
/*     */   private UsbDeviceConnection m_receiverConnection;
/*  28 */   private final int G4_RECEIVER_VENDOR_ID = 8867;
/*  29 */   private final int G4_RECEIVER_PRODUCT_ID = 71;
/*  30 */   private static int TIMEOUT = 5000;
/*     */ 
/*     */   ReceiverUsbComm(Context context)
/*     */   {
/*  35 */     this.m_usbManager = 
/*  36 */       ((UsbManager)context
/*  36 */       .getSystemService("usb"));
/*  37 */     doDetectReceivers(this.m_usbManager);
/*     */   }
/*     */ 
/*     */   boolean isCommPermissionGranted()
/*     */   {
/*  44 */     if (this.isReceiverAttached)
/*     */     {
/*  46 */       return this.m_usbManager.hasPermission(this.m_receiver);
/*     */     }
/*     */ 
/*  50 */     return false;
/*     */   }
/*     */ 
/*     */   private void doDetectReceivers(UsbManager manager)
/*     */   {
/*  60 */     UsbDevice device = null;
/*  61 */     HashMap deviceList = manager.getDeviceList();
/*  62 */     Iterator deviceIterator = deviceList.values().iterator();
/*     */ 
/*  64 */     this.m_receiver = null;
/*  65 */     this.isReceiverAttached = false;
/*     */ 
/*  67 */     if (deviceIterator.hasNext())
/*     */     {
/*  69 */       while (deviceIterator.hasNext())
/*     */       {
/*  71 */         device = (UsbDevice)deviceIterator.next();
/*  72 */         int currentDeviceVendorId = device.getVendorId();
/*  73 */         int currentDeviceProductId = device.getProductId();
/*     */ 
/*  76 */         if (((currentDeviceVendorId == 8867 ? 1 : 0) & (
/*  76 */           currentDeviceProductId == 71 ? 1 : 0)) != 0)
/*     */         {
/*  78 */           this.m_receiver = device;
/*  79 */           this.isReceiverAttached = true;
/*  80 */           break;
/*     */         }
/*     */ 
/*  84 */         Log.i("ReceiverUSBCommunication", "No G4 receivers connected");
/*     */       }
/*     */ 
/*     */     }
/*     */     else
/*     */     {
/*  90 */       Log.i("ReceiverUSBCommunication", "No USB devices connected");
/*     */     }
/*     */   }
/*     */ 
/*     */   public byte[] sendReceiverMessageForResponse(byte[] message)
/*     */     throws Exception
/*     */   {
/*  99 */     byte[] reply = new byte[0];
/*     */ 
/* 101 */     doDetectReceivers(this.m_usbManager);
/*     */ 
/* 103 */     if (this.isReceiverAttached)
/*     */     {
/* 106 */       setUpUsbComm();
/*     */ 
/* 109 */       int transferLength = doSendReceiverMessage(this.m_receiverConnection, 
/* 110 */         message);
/*     */ 
/* 112 */       if (transferLength >= 0)
/*     */       {
/* 116 */         reply = readReceiverMessage();
/*     */       }
/*     */       else
/*     */       {
/* 120 */         throw new Exception("No data has been sent to receiver");
/*     */       }
/*     */ 
/* 124 */       cleanUpUsbComm();
/*     */     }
/*     */     else
/*     */     {
/* 129 */       Log.i("ReceiverUSBCommunication", "No G4 receivers connected");
/*     */     }
/*     */ 
/* 132 */     return reply;
/*     */   }
/*     */ 
/*     */   private void setUpUsbComm()
/*     */   {
/* 138 */     this.m_receiverInterface = this.m_receiver.getInterface(1);
/* 139 */     this.m_outEndpoint = this.m_receiverInterface.getEndpoint(0);
/* 140 */     this.m_inEndpoint = this.m_receiverInterface.getEndpoint(1);
/* 141 */     Log.i("ReceiverUsbComm", this.m_receiver.toString());
/* 142 */     this.m_receiverConnection = this.m_usbManager.openDevice(this.m_receiver);
/* 143 */     boolean interfaceClaimSuccess = this.m_receiverConnection.claimInterface(
/* 144 */       this.m_receiverInterface, true);
/* 145 */     Log.i("ReceiverUsbComm", "Interface claim = " + interfaceClaimSuccess);
/*     */   }
/*     */ 
/*     */   private void cleanUpUsbComm()
/*     */   {
/* 151 */     boolean interfaceReleaseSuccess = this.m_receiverConnection
/* 152 */       .releaseInterface(this.m_receiverInterface);
/* 153 */     Log.i("ReceiverUsbComm", "Interface release = " + 
/* 154 */       interfaceReleaseSuccess);
/* 155 */     this.m_receiverConnection.close();
/*     */   }
/*     */ 
/*     */   private int doSendReceiverMessage(UsbDeviceConnection receiverConnection, byte[] message)
/*     */     throws Exception
/*     */   {
/* 164 */     int outTransferLength = receiverConnection.bulkTransfer(this.m_outEndpoint, 
/* 165 */       message, message.length, TIMEOUT);
/*     */ 
/* 167 */     return outTransferLength;
/*     */   }
/*     */ 
/*     */   private byte[] readReceiverMessage()
/*     */     throws Exception
/*     */   {
/* 174 */     byte[] packet = new byte[32773];
/*     */ 
/* 177 */     int inFullPacketTransferLength = this.m_receiverConnection.bulkTransfer(
/* 178 */       this.m_inEndpoint, packet, packet.length, TIMEOUT);
/* 179 */     if (inFullPacketTransferLength <= 0)
/*     */     {
/* 181 */       Log.e("ReceiverUSBCommunication", "No data has been read from receiver");
/*     */     }
/*     */ 
/* 184 */     return packet;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverUsbComm
 * JD-Core Version:    0.6.0
 */