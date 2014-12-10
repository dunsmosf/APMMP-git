/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import android.util.Log;
/*     */ import java.io.ByteArrayInputStream;
/*     */ import java.io.IOException;
/*     */ import java.io.StringReader;
/*     */ import java.io.StringWriter;
/*     */ import java.nio.ByteBuffer;
/*     */ import java.nio.ByteOrder;
/*     */ import java.util.ArrayList;
/*     */ import java.util.Arrays;
/*     */ import java.util.Calendar;
/*     */ import java.util.Date;
/*     */ import java.util.GregorianCalendar;
/*     */ import javax.xml.parsers.DocumentBuilder;
/*     */ import javax.xml.parsers.DocumentBuilderFactory;
/*     */ import javax.xml.parsers.ParserConfigurationException;
/*     */ import javax.xml.transform.Transformer;
/*     */ import javax.xml.transform.TransformerException;
/*     */ import javax.xml.transform.TransformerFactory;
/*     */ import javax.xml.transform.TransformerFactoryConfigurationError;
/*     */ import javax.xml.transform.dom.DOMSource;
/*     */ import javax.xml.transform.stream.StreamResult;
/*     */ import org.w3c.dom.Document;
/*     */ import org.w3c.dom.Element;
/*     */ import org.xml.sax.InputSource;
/*     */ import org.xml.sax.SAXException;
/*     */ 
/*     */ class Tools
/*     */ {
/*     */   static Date convertPayloadToDate(byte[] dataStream)
/*     */   {
/*  40 */     ByteBuffer bb = ByteBuffer.wrap(dataStream);
/*  41 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/*  43 */     int receiverSeconds = bb.getInt();
/*  44 */     Date receiverDate = convertReceiverTimeToDate(receiverSeconds);
/*     */ 
/*  46 */     return receiverDate;
/*     */   }
/*     */ 
/*     */   static Date convertReceiverTimeToDate(int receiverSeconds)
/*     */   {
/*  55 */     Calendar receiverCalendarTimeStamp = new GregorianCalendar();
/*  56 */     receiverCalendarTimeStamp.setTime(Values.ReceiverBaseDate);
/*     */	  //TODO: Check if there is a better place in the code to add the offset:
/*     */	  receiverSeconds += 3600; // Because Sensor set to Standard Time
/*  57 */     receiverCalendarTimeStamp.add(13, receiverSeconds);
/*  58 */     Date receiverDate = receiverCalendarTimeStamp.getTime();
/*     */ 
/*  60 */     return receiverDate;
/*     */   }
/*     */ 
/*     */   static String convertTxIdToTxCode(int id)
/*     */   {
/*  68 */     StringBuilder code = new StringBuilder();
/*     */ 
/*  70 */     for (int i = 0; i < 5; i++)
/*     */     {
/*  72 */       int index = id & 0x1F;
/*  73 */       code.insert(0, "0123456789ABCDEFGHJKLMNPQRSTUWXY".toCharArray()[index]);
/*  74 */       id >>= 5;
/*     */     }
/*     */ 
/*  77 */     return code.toString();
/*     */   }
/*     */ 
/*     */   static void evaluateCrc(byte[] data, short crc, String tag)
/*     */   {
/*  87 */     byte[] dataNoCrc = Arrays.copyOf(data, data.length - 2);
/*  88 */     short calculatedCrc = Crc.CalculateCrc16(dataNoCrc, 0, data.length - 2);
/*  89 */     if (crc != calculatedCrc)
/*     */     {
/*  91 */       Log.e(tag, "Failed CRC check");
/*     */     }
/*     */   }
/*     */ 
/*     */   static Document convertStringToXml(String source)
/*     */   {
/*  99 */     DocumentBuilderFactory dBuilderFactory = 
/* 100 */       DocumentBuilderFactory.newInstance();
/* 101 */     DocumentBuilder dBuilder = null;
/* 102 */     Document xDoc = null;
/*     */     try
/*     */     {
/* 106 */       dBuilder = dBuilderFactory.newDocumentBuilder();
/* 107 */       xDoc = dBuilder.parse(new InputSource(new StringReader(source)));
/*     */     }
/*     */     catch (ParserConfigurationException e)
/*     */     {
/* 112 */       e.printStackTrace();
/*     */     }
/*     */     catch (SAXException e)
/*     */     {
/* 117 */       e.printStackTrace();
/*     */     }
/*     */     catch (IOException e)
/*     */     {
/* 122 */       e.printStackTrace();
/*     */     }
/*     */ 
/* 125 */     return xDoc;
/*     */   }
/*     */ 
/*     */   static String convertXmlDocumentToString(Document xDoc)
/*     */   {
/* 133 */     return convertXmlElementToString(xDoc.getDocumentElement(), false);
/*     */   }
/*     */ 
/*     */   static String convertXmlElementToString(Element xElement, boolean doOmitXmlDeclaration)
/*     */   {
/* 141 */     Transformer transformer = null;
/* 142 */     StreamResult result = null;
/*     */     try
/*     */     {
/* 146 */       transformer = TransformerFactory.newInstance().newTransformer();
/* 147 */       transformer.setOutputProperty("indent", "yes");
/* 148 */       transformer.setOutputProperty("omit-xml-declaration", 
/* 149 */         doOmitXmlDeclaration ? "yes" : "no");
/* 150 */       result = new StreamResult(new StringWriter());
/* 151 */       DOMSource source = new DOMSource(xElement);
/* 152 */       transformer.transform(source, result);
/*     */     }
/*     */     catch (TransformerException e)
/*     */     {
/* 157 */       e.printStackTrace();
/*     */     }
/*     */     catch (TransformerFactoryConfigurationError e1)
/*     */     {
/* 162 */       e1.printStackTrace();
/*     */     }
/*     */ 
/* 165 */     String xmlString = result.getWriter().toString();
/*     */ 
/* 167 */     return xmlString;
/*     */   }
/*     */ 
/*     */   static Document convertByteToXml(byte[] source)
/*     */   {
/* 174 */     DocumentBuilderFactory dBuilderFactory = 
/* 175 */       DocumentBuilderFactory.newInstance();
/* 176 */     DocumentBuilder dBuilder = null;
/* 177 */     Document xDoc = null;
/*     */     try
/*     */     {
/* 181 */       dBuilder = dBuilderFactory.newDocumentBuilder();
/* 182 */       xDoc = dBuilder.parse(new ByteArrayInputStream(source));
/*     */     }
/*     */     catch (ParserConfigurationException e)
/*     */     {
/* 187 */       e.printStackTrace();
/*     */     }
/*     */     catch (SAXException e)
/*     */     {
/* 192 */       e.printStackTrace();
/*     */     }
/*     */     catch (IOException e)
/*     */     {
/* 197 */       e.printStackTrace();
/*     */     }
/*     */ 
/* 200 */     return xDoc;
/*     */   }
/*     */ 
/*     */   static int convertByteToInt(byte[] dataStream)
/*     */   {
/* 208 */     ByteBuffer bb = ByteBuffer.wrap(dataStream);
/* 209 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/* 210 */     int value = bb.getInt();
/*     */ 
/* 212 */     return value;
/*     */   }
/*     */ 
/*     */   static short convertByteToShort(byte[] dataStream)
/*     */   {
/* 220 */     ByteBuffer bb = ByteBuffer.wrap(dataStream);
/* 221 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/* 222 */     short value = bb.getShort();
/*     */ 
/* 224 */     return value;
/*     */   }
/*     */ 
/*     */   static ArrayList<ReceiverSettingsRecord> parseSettingsPage(DatabasePage page)
/*     */   {
/* 231 */     ArrayList recordList = new ArrayList();
/* 232 */     int firstRecordIndex = page.PageHeader.FirstRecordIndex;
/*     */ 
/* 234 */     ByteBuffer bb = ByteBuffer.wrap(page.PageData);
/* 235 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/* 237 */     for (int iRecord = 0; iRecord < page.PageHeader.NumberOfRecords; iRecord++)
/*     */     {
/* 239 */       byte[] recordData = new byte[48];
/* 240 */       bb.get(recordData);
/*     */ 
/* 242 */       ReceiverSettingsRecord record = new ReceiverSettingsRecord(
/* 243 */         recordData);
/* 244 */       record.RecordNumber = (iRecord + firstRecordIndex);
/*     */ 
/* 246 */       recordList.add(record);
/*     */     }
/*     */ 
/* 249 */     return recordList;
/*     */   }
/*     */ 
/*     */   static ArrayList<ReceiverEgvRecord> parseEgvPage(DatabasePage page)
/*     */   {
/* 256 */     ArrayList recordList = new ArrayList();
/* 257 */     int firstRecordIndex = page.PageHeader.FirstRecordIndex;
/*     */ 
/* 259 */     ByteBuffer bb = ByteBuffer.wrap(page.PageData);
/* 260 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/* 262 */     for (int iRecord = 0; iRecord < page.PageHeader.NumberOfRecords; iRecord++)
/*     */     {
/* 264 */       byte[] recordData = new byte[13];
/* 265 */       bb.get(recordData);
/*     */ 
/* 267 */       ReceiverEgvRecord record = new ReceiverEgvRecord(recordData);
/* 268 */       record.RecordNumber = (iRecord + firstRecordIndex);
/*     */ 
/* 270 */       recordList.add(record);
/*     */     }
/*     */ 
/* 273 */     return recordList;
/*     */   }
/*     */ 
/*     */   static ArrayList<ReceiverMeterRecord> parseMeterPage(DatabasePage page)
/*     */   {
/* 280 */     ArrayList recordList = new ArrayList();
/* 281 */     int firstRecordIndex = page.PageHeader.FirstRecordIndex;
/*     */ 
/* 283 */     ByteBuffer bb = ByteBuffer.wrap(page.PageData);
/* 284 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/* 286 */     for (int iRecord = 0; iRecord < page.PageHeader.NumberOfRecords; iRecord++)
/*     */     {
/* 288 */       byte[] recordData = new byte[16];
/* 289 */       bb.get(recordData);
/*     */ 
/* 291 */       ReceiverMeterRecord record = new ReceiverMeterRecord(recordData);
/* 292 */       record.RecordNumber = (iRecord + firstRecordIndex);
/*     */ 
/* 294 */       recordList.add(record);
/*     */     }
/*     */ 
/* 297 */     return recordList;
/*     */   }
/*     */ 
/*     */   static ArrayList<ReceiverInsertionTimeRecord> parseInsertionPage(DatabasePage page)
/*     */   {
/* 305 */     ArrayList recordList = new ArrayList();
/* 306 */     int firstRecordIndex = page.PageHeader.FirstRecordIndex;
/*     */ 
/* 308 */     ByteBuffer bb = ByteBuffer.wrap(page.PageData);
/* 309 */     bb.order(ByteOrder.LITTLE_ENDIAN);
/*     */ 
/* 311 */     for (int iRecord = 0; iRecord < page.PageHeader.NumberOfRecords; iRecord++)
/*     */     {
/* 313 */       byte[] recordData = new byte[15];
/* 314 */       bb.get(recordData);
/*     */ 
/* 316 */       ReceiverInsertionTimeRecord record = new ReceiverInsertionTimeRecord(
/* 317 */         recordData);
/* 318 */       record.RecordNumber = (iRecord + firstRecordIndex);
/*     */ 
/* 320 */       recordList.add(record);
/*     */     }
/*     */ 
/* 323 */     return recordList;
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.Tools
 * JD-Core Version:    0.6.0
 */