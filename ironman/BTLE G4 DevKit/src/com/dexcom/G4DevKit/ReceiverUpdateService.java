/*     */ package com.dexcom.G4DevKit;
/*     */ 
/*     */ import android.app.Service;
/*     */ import android.content.BroadcastReceiver;
/*     */ import android.content.Context;
/*     */ import android.content.Intent;
/*     */ import android.content.IntentFilter;
/*     */ import android.content.SharedPreferences;
/*     */ import android.content.SharedPreferences.Editor;
import android.database.Cursor;
/*     */ import android.os.Binder;
/*     */ import android.os.Handler;
/*     */ import android.os.IBinder;
/*     */ import android.os.PowerManager;
/*     */ import android.preference.PreferenceManager;
/*     */ import android.util.Log;
/*     */ import android.widget.Toast;

import com.dexcom.G4DevKit.enums.UsbPowerLevel;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.btleUsb.BLEUsbPort;
import edu.virginia.dtc.btleUsbDebug.DebugLog;

import java.io.IOException;
/*     */ import java.util.ArrayList;
/*     */ import java.util.Collections;
import java.util.Date;
/*     */ import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
/*     */ 
/*     */ public class ReceiverUpdateService extends Service
/*     */ {
/*     */   private static final String TAG = "ReceiverUpdateService";
/*  27 */   public Boolean isServiceStarted = Boolean.valueOf(false);
/*     */   public SharedPreferences applicationPreferences;
/*  32 */   public List<SettingsRecord> settingsRecords = Collections.synchronizedList(new ArrayList());
/*     */ 
/*  34 */   public List<MeterRecord> meterRecords = Collections.synchronizedList(new ArrayList());
/*     */ 
/*  36 */   public List<EstimatedGlucoseRecord> egvRecords = Collections.synchronizedList(new ArrayList());
/*     */ 
/*  38 */   public List<InsertionTimeRecord> insertionRecords = Collections.synchronizedList(new ArrayList());
/*     */   public String currentTransmitterId;
/*     */   public SettingsRecord currentSettingsRecord;
/*     */   public MeterRecord currentMeterRecord;
/*     */   public EstimatedGlucoseRecord currentEstimatedGlucoseRecord;
/*     */   public InsertionTimeRecord currentInsertionRecord;
/*     */   private Context m_context;
/*     */   private BroadcastReceiver m_checkForDataBReceiver;
			private BroadcastReceiver m_macAddressReceiver;
/*  49 */   private final Handler m_handler = new Handler();
/*     */   Runnable m_toastMessage;
/*  51 */   private final IBinder m_binder = new ServiceBinder();
/*     */   private PowerManager m_powerManager;
/*     */   private ReceiverUsbComm m_usbComm;
/*     */   private ReceiverApi m_api;
			private String macAddress = null;
			
			public boolean systemOffsetReady = false;
			public long systemOffset = -1;

			public static BLEUsbPort btle;
			public static boolean bleUsbConnected = false;

			public static final int EGV_PER_PAGE = 38;
			public static final int METER_PER_PAGE = 31;
			
			public static int EGV_PAGES = 1;
			public static int METER_PAGES = 1;
			
			private int lastEgvRecord = -1;
			private int lastMeterRecord = -1;
			
			private ScheduledExecutorService scheduler;
			private ScheduledFuture<?> receiver;
			private boolean receiverIsNew;
			
/*     */ 
/*     */   public void onCreate()
/*     */   {
/*  60 */     this.m_context = getApplicationContext();
/*     */ 
				scheduler = Executors.newSingleThreadScheduledExecutor();
				receiverIsNew = true;
				
/*  64 */     this.applicationPreferences = 
/*  65 */       PreferenceManager.getDefaultSharedPreferences(this.m_context);
/*     */ 
/*  68 */     this.m_powerManager = 
/*  69 */       ((PowerManager)this.m_context
/*  69 */       .getSystemService("power"));
/*     */ 

			try {
				ReceiverUpdateService.btle = new BLEUsbPort(m_context, 2048, 4000);
				ReceiverUpdateService.bleUsbConnected = false;
				DebugLog.setDebugLevel(DebugLog.DebugLevelNone);
			} catch (Exception e) {
				e.printStackTrace();
			}

/*  71 */     this.m_usbComm = new ReceiverUsbComm(this.m_context);
/*  72 */     this.m_api = new ReceiverApi(this.m_usbComm);
 
				this.m_macAddressReceiver = new BroadcastReceiver()
				{
					public void onReceive(Context context, Intent intent)
					{
						macAddress = intent.getStringExtra("mac");
						Log.e(TAG, "MAC: "+macAddress);
					}
				};
				IntentFilter filt = new IntentFilter("com.dexcom.g4devkit.action.UPDATE_MAC");
				registerReceiver(this.m_macAddressReceiver, filt);

/*  75 */     this.m_checkForDataBReceiver = new BroadcastReceiver()
/*     */     {
/*     */       public void onReceive(Context context, Intent intent)
/*     */       {
/*  83 */         if (intent.getAction().equals(
/*  84 */           "com.dexcom.g4devkit.action.UPDATE_RECEIVER_DATA"))
/*     */         {
					
						Log.w(TAG, "Received update data command...");
						
						
						if(receiverIsNew || receiver.isDone())
						{
							Log.w(TAG, "Running update service!");
							receiverIsNew = false;
							receiver = scheduler.schedule(new ReceiverUpdateService.receiverUpdate(), 0, TimeUnit.SECONDS);
						}
						else
							Log.w(TAG, "Update is already running!");
					
///*  87 */           new Thread()
///*     */           {
///*     */             public void run()
///*     */             {
///*  92 */               ReceiverUpdateService.this.m_handler.post(new ReceiverUpdateService.receiverUpdate());
///*     */             }
///*     */           }
///*  94 */           .start();

/*     */         }
/*     */       }
/*     */     };
/* 100 */     IntentFilter m_checkDataFilter = new IntentFilter(
/* 101 */       "com.dexcom.g4devkit.action.UPDATE_RECEIVER_DATA");
/* 102 */     registerReceiver(this.m_checkForDataBReceiver, m_checkDataFilter);

				Log.i(TAG, "onCreate is finished...");
/*     */   }
/*     */ 
/*     */   public IBinder onBind(Intent intent)
/*     */   {
/* 110 */     return this.m_binder;
/*     */   }
/*     */ 
/*     */   public int onStartCommand(Intent intent, int flags, int startId)
/*     */   {
/* 120 */     if (!this.isServiceStarted.booleanValue())
/*     */     {
/* 122 */       Toast.makeText(this.m_context, "G4 Dev Kit Service started", 
/* 123 */         0).show();
/* 124 */       this.isServiceStarted = Boolean.valueOf(true);
/* 125 */       this.applicationPreferences.edit()
/* 126 */         .putBoolean("serviceStatus", this.isServiceStarted.booleanValue()).commit();
/*     */     }

				Log.i(TAG, "Start command finished!");

/* 128 */     return 1;
/*     */   }
/*     */ 
/*     */   public void onDestroy()
/*     */   {
/* 134 */     if (this.isServiceStarted.booleanValue())
/*     */     {
/* 137 */       Toast.makeText(this.m_context, "G4 Dev Kit Service destroyed", 
/* 138 */         0).show();
/* 139 */       this.isServiceStarted = Boolean.valueOf(false);
/* 140 */       this.applicationPreferences.edit()
/* 141 */         .putBoolean("serviceStatus", this.isServiceStarted.booleanValue()).commit();
/*     */     }
/* 143 */     unregisterReceiver(this.m_checkForDataBReceiver);
/*     */   }
/*     */ 
/*     */   public UsbPowerLevel readCurrentUsbPowerLevel()
/*     */   {
/* 186 */     UsbPowerLevel level = UsbPowerLevel.Unknown;
/*     */ 
/* 188 */     if (this.m_api.pingReceiver())
/*     */     {
/*     */       try
/*     */       {
/* 192 */         level = ReceiverApiCommands.readUsbPowerLevel(this.m_api);
/*     */       }
/*     */       catch (Exception e)
/*     */       {
/* 196 */         e.printStackTrace();
/*     */ 
/* 198 */         if (!this.m_api.pingReceiver())
/*     */         {
/* 200 */           Log.e("ReceiverUpdateService", "Receiver not responding");
/*     */         }
/*     */       }
/*     */     }
/*     */ 
/* 205 */     return level;
/*     */   }
/*     */ 
/*     */   public boolean setCurrentUsbPowerLevel(UsbPowerLevel level)
/*     */   {
/* 211 */     boolean success = false;
/*     */ 
/* 213 */     if (this.m_api.pingReceiver())
/*     */     {
/*     */       try
/*     */       {
/* 217 */         success = ReceiverApiCommands.setUsbPowerlevel(this.m_api, level);
/*     */       }
/*     */       catch (Exception e)
/*     */       {
/* 221 */         e.printStackTrace();
/*     */ 
/* 223 */         if (!this.m_api.pingReceiver())
/*     */         {
/* 225 */           Log.e("ReceiverUpdateService", "Receiver not responding");
/*     */         }
/*     */       }
/*     */     }
/*     */ 
/* 230 */     return success;
/*     */   }
/*     */ 
/*     */   private void syncTransmitterId(ReceiverApi api)
/*     */     throws Exception
/*     */   {
/* 237 */     this.currentTransmitterId = ReceiverApiCommands.readTransmitterId(api);

				Log.i(TAG, "syncTransmitterID");
/*     */   }
/*     */ 
/*     */   private void syncSettingsRecords(ReceiverApi api)
/*     */     throws Exception
/*     */   {
				Log.i(TAG, "syncSettingsRecords");
	
/* 244 */     int mostRecentRecordNumber = 2147483647;
/* 245 */     int numNewRecords = 0;
/*     */ 
/* 249 */     if (this.settingsRecords.size() > 0)
/*     */     {
/* 251 */       mostRecentRecordNumber = 
/* 252 */         ((SettingsRecord)this.settingsRecords
/* 252 */         .get(this.settingsRecords.size() - 1)).RecordNumber;
/*     */     }
/*     */ 
/* 256 */     ReceiverRecordType recordType = ReceiverRecordType.UserSettingData;
/* 257 */     DatabasePageRange range = api.readDatabasePageRange(recordType);
/*     */ 
/* 259 */     int pageCount = range.LastPage - range.FirstPage + 1;
/*     */ 
/* 262 */     if (range.LastPage != -1)
/*     */     {
/* 264 */       int iPage = range.FirstPage;
/* 265 */       for (; iPage < range.FirstPage + 
/* 265 */         pageCount; iPage++)
/*     */       {
/* 269 */         DatabasePageHeader header = api.readDatabasePageHeader(
/* 270 */           recordType, iPage);
/*     */ 
/* 272 */         int firstRecordNumber = header.FirstRecordIndex;
/* 273 */         int lastRecordNumber = header.FirstRecordIndex + 
/* 274 */           header.NumberOfRecords - 1;
/*     */ 
/* 279 */         if ((firstRecordNumber <= mostRecentRecordNumber) && 
/* 280 */           (lastRecordNumber <= mostRecentRecordNumber) && 
/* 281 */           (mostRecentRecordNumber != 2147483647))
/*     */         {
/*     */           continue;
/*     */         }
/* 285 */         DatabasePage page = api.readDatabasePage(recordType, iPage);
/* 286 */         ArrayList<ReceiverSettingsRecord> receiverRecordList = 
/* 287 */           Tools.parseSettingsPage(page);
/*     */ 
/* 291 */         for (ReceiverSettingsRecord receiverRecord : receiverRecordList)
/*     */         {
/* 293 */           if ((receiverRecord.RecordNumber <= mostRecentRecordNumber) && 
/* 294 */             (mostRecentRecordNumber != 2147483647))
/*     */             continue;
/* 296 */           SettingsRecord newRecord = extractSettingsData(receiverRecord);
/*     */ 
/* 298 */           mostRecentRecordNumber = newRecord.RecordNumber;
/*     */ 
/* 300 */           this.currentSettingsRecord = newRecord;
/* 301 */           this.settingsRecords.add(newRecord);
/*     */ 
/* 303 */           numNewRecords++;
/*     */         }
/*     */ 
/*     */       }
/*     */ 
/*     */     }
/*     */ 
/* 312 */     if (numNewRecords > 0)
/*     */     {
/* 314 */       sendSettingsData();
/*     */ 
/* 316 */       notifyUserOfEvent(numNewRecords + " new settings records received", 
/* 317 */         Boolean.valueOf(true), Boolean.valueOf(true));
/*     */     }
/*     */   }
/*     */ 
/*     */   private void syncEgvRecords(ReceiverApi api)
/*     */     throws Exception
/*     */   {
	
			Log.e(TAG, "syncEgvRecords -------------------------------------------");
			
/* 325 */     int mostRecentRecordNumber = 2147483647;
/* 326 */     int numNewRecords = 0;

/* 330 */     if (this.egvRecords.size() > 0)
/*     */     {
/* 332 */       mostRecentRecordNumber = ((EstimatedGlucoseRecord)this.egvRecords.get(this.egvRecords.size() - 1)).RecordNumber;
/*     */     }
/*     */ 
/* 336 */     ReceiverRecordType recordType = ReceiverRecordType.EGVData;
/* 337 */     DatabasePageRange range = api.readDatabasePageRange(recordType);

			long last_valid_time = -1;
			Cursor c_time = getContentResolver().query(Biometrics.CGM_URI, null, null, null, null);
			
			if(c_time.getCount() > 0)
			{
				if(c_time.moveToLast())
				{
					//Get the latest CGM time from the database
					last_valid_time = c_time.getLong(c_time.getColumnIndex("time"));
					if(((System.currentTimeMillis()/1000)-last_valid_time) > (12*60*60))
					{
						last_valid_time = ((System.currentTimeMillis()/1000)-(12*60*60));
						Log.i(TAG, "The time can't exceed 12 hours so the valid time is: "+last_valid_time);
					}
					else
						Log.i(TAG, "The new valid CGM time is : "+last_valid_time);
				}
			}
			else if(last_valid_time < 0)
			{
				int hours = Params.getInt(getContentResolver(), "cgm_history_hrs", 12);
				long minutes = hours*60;
				long seconds = minutes*60;
				
				last_valid_time = (System.currentTimeMillis()/1000)-seconds; 
				Log.i(TAG, "There is no data in the table, so the last valid time is "+last_valid_time);
			}
			c_time.close();

			Log.e(TAG, "Last Valid Time: "+last_valid_time);
			
			long sec = (System.currentTimeMillis()/1000) - last_valid_time;
			Log.e(TAG, "Seconds: "+sec);
			long min = sec/60;
			
			int extraPage = 1;
			if(sec > 600)
				extraPage = 2;
			
			long pages = ((min/5)/ReceiverUpdateService.EGV_PER_PAGE) + extraPage;
			ReceiverUpdateService.EGV_PAGES = (int) pages;
			
			Log.e(TAG, "Number of pages: "+ReceiverUpdateService.EGV_PAGES+" Records to pull: "+pages*ReceiverUpdateService.EGV_PER_PAGE);
			Log.i(TAG, "Most recent Record is: "+mostRecentRecordNumber);
			
			range.FirstPage = range.LastPage - EGV_PAGES + 1;
			
			if(range.FirstPage < 0)
				range.FirstPage = 0;
			
			lastEgvRecord = mostRecentRecordNumber;
			Log.i(TAG, "Last Record is: "+lastEgvRecord);

/* 339 */  	int pageCount = range.LastPage - range.FirstPage + 1;
/*     */ 
			Log.i(TAG, "Page Count: "+pageCount+" Last Page: "+range.LastPage+" First Page: "+range.FirstPage);

/* 342 */     if (range.LastPage != -1)
/*     */     {
/* 344 */       int iPage = range.FirstPage;

				Log.i(TAG, "iPage = "+iPage+" Count = "+(range.FirstPage + pageCount)+" Total to run = "+(range.FirstPage + pageCount-iPage));

/* 345 */       for (; iPage < range.FirstPage + pageCount; iPage++)
/*     */       {
/* 349 */         DatabasePageHeader header = api.readDatabasePageHeader(recordType, iPage);

					Log.i(TAG, "Records on this page = "+header.NumberOfRecords);

/* 352 */         int firstRecordNumber = header.FirstRecordIndex;
/* 353 */         int lastRecordNumber = header.FirstRecordIndex + header.NumberOfRecords - 1;
/*     */ 
/* 359 */         if ((firstRecordNumber <= mostRecentRecordNumber) && (lastRecordNumber <= mostRecentRecordNumber) && (mostRecentRecordNumber != 2147483647))
/*     */         {
/*     */           continue;
/*     */         }

/* 365 */         DatabasePage page = api.readDatabasePage(recordType, iPage);
/* 366 */         ArrayList<ReceiverEgvRecord> receiverRecordList = Tools.parseEgvPage(page);
/*     */ 
/* 371 */         for (ReceiverEgvRecord receiverRecord : receiverRecordList)
/*     */         {
/* 373 */           if ((receiverRecord.RecordNumber <= mostRecentRecordNumber) && (mostRecentRecordNumber != 2147483647))
/*     */             continue;

/* 376 */           EstimatedGlucoseRecord newRecord = extractEgvData(receiverRecord);
/* 378 */           mostRecentRecordNumber = newRecord.RecordNumber;

/* 380 */           this.currentEstimatedGlucoseRecord = newRecord;
/* 381 */           this.egvRecords.add(newRecord);
/*     */ 
/* 383 */           numNewRecords++;
/*     */         }
/*     */       }
/*     */     }
/*     */ 
/* 392 */     if (numNewRecords > 0)
/*     */     {
/* 394 */       sendEgvData();
				Log.e(TAG, "Number of new records: "+numNewRecords);
/* 396 */       notifyUserOfEvent(numNewRecords + " new EGV records received", Boolean.valueOf(true), Boolean.valueOf(true));
/*     */     }
/*     */   }
/*     */ 
/*     */   private void syncMeterRecords(ReceiverApi api)
/*     */     throws Exception
/*     */   {
	
			Log.e(TAG, "syncMeterRecords -------------------------------------------");
	
/* 405 */     int mostRecentRecordNumber = 2147483647;
/* 406 */     int numNewRecords = 0;
/*     */ 
/* 410 */     if (this.meterRecords.size() > 0)
/*     */     {
/* 412 */       mostRecentRecordNumber = ((MeterRecord)this.meterRecords.get(this.meterRecords.size() - 1)).RecordNumber;
/*     */     }
/*     */ 
/* 416 */     ReceiverRecordType recordType = ReceiverRecordType.MeterData;
/* 417 */     DatabasePageRange range = api.readDatabasePageRange(recordType);

			Log.i(TAG, "Most recent Record is: "+mostRecentRecordNumber);
			
			range.FirstPage = range.LastPage - ReceiverUpdateService.EGV_PAGES + 1;
			
			lastMeterRecord = mostRecentRecordNumber;
			Log.i(TAG, "Last Record is: "+lastMeterRecord);

/*     */ 
/* 419 */     int pageCount = range.LastPage - range.FirstPage + 1;

			Log.i(TAG, "Page Count: "+pageCount+" Last Page: "+range.LastPage+" First Page: "+range.FirstPage);
/*     */ 
/* 422 */     if (range.LastPage != -1)
/*     */     {
/* 424 */       int iPage = range.FirstPage;

			Log.i(TAG, "iPage = "+iPage+" Count = "+(range.FirstPage + pageCount)+" Total to run = "+(range.FirstPage + pageCount-iPage));

/* 425 */       for (; iPage < range.FirstPage + 
/* 425 */         pageCount; iPage++)
/*     */       {
/* 429 */         DatabasePageHeader header = api.readDatabasePageHeader(
/* 430 */           recordType, iPage);
/*     */ 
				Log.i(TAG, "Records on this page = "+header.NumberOfRecords);

/* 432 */         int firstRecordNumber = header.FirstRecordIndex;
/* 433 */         int lastRecordNumber = header.FirstRecordIndex + 
/* 434 */           header.NumberOfRecords - 1;
/*     */ 
/* 439 */         if ((firstRecordNumber <= mostRecentRecordNumber) && 
/* 440 */           (lastRecordNumber <= mostRecentRecordNumber) && 
/* 441 */           (mostRecentRecordNumber != 2147483647))
/*     */         {
/*     */           continue;
/*     */         }
/* 445 */         DatabasePage page = api.readDatabasePage(recordType, iPage);
/* 446 */         ArrayList<ReceiverMeterRecord> receiverRecordList = 
/* 447 */           Tools.parseMeterPage(page);
/*     */ 
/* 451 */         for (ReceiverMeterRecord receiverRecord : receiverRecordList)
/*     */         {
/* 453 */           if ((receiverRecord.RecordNumber <= mostRecentRecordNumber) && 
/* 454 */             (mostRecentRecordNumber != 2147483647)) {
/*     */             continue;
/*     */           }
/* 457 */           MeterRecord newRecord = extractMeterData(receiverRecord);
/*     */ 
/* 459 */           mostRecentRecordNumber = newRecord.RecordNumber;
/*     */ 
/* 461 */           this.currentMeterRecord = newRecord;
/* 462 */           this.meterRecords.add(newRecord);
/*     */ 
/* 464 */           numNewRecords++;
/*     */         }
/*     */ 
/*     */       }
/*     */ 
/*     */     }
/*     */ 
/* 473 */     if (numNewRecords > 0)
/*     */     {
/* 475 */       sendMeterData();
/*     */ 
/* 477 */       notifyUserOfEvent(numNewRecords + " new meter records received", 
/* 478 */         Boolean.valueOf(true), Boolean.valueOf(true));
/*     */     }
/*     */   }
/*     */ 
/*     */   private void syncInsertionRecords(ReceiverApi api)
/*     */     throws Exception
/*     */   {
	Log.i(TAG, "syncInsertionRecords");
	
/* 486 */     int mostRecentRecordNumber = 2147483647;
/* 487 */     int numNewRecords = 0;
/*     */ 
/* 491 */     if (this.insertionRecords.size() > 0)
/*     */     {
/* 493 */       mostRecentRecordNumber = ((InsertionTimeRecord)this.insertionRecords.get(this.insertionRecords
/* 494 */         .size() - 1)).RecordNumber;
/*     */     }
/*     */ 
/* 498 */     ReceiverRecordType recordType = ReceiverRecordType.InsertionTime;
/* 499 */     DatabasePageRange range = api.readDatabasePageRange(recordType);
/*     */ 
/* 501 */     int pageCount = range.LastPage - range.FirstPage + 1;
/*     */ 
/* 504 */     if (range.LastPage != -1)
/*     */     {
/* 506 */       int iPage = range.FirstPage;
/* 507 */       for (; iPage < range.FirstPage + 
/* 507 */         pageCount; iPage++)
/*     */       {
/* 511 */         DatabasePageHeader header = api.readDatabasePageHeader(
/* 512 */           recordType, iPage);
/*     */ 
/* 514 */         int firstRecordNumber = header.FirstRecordIndex;
/* 515 */         int lastRecordNumber = header.FirstRecordIndex + 
/* 516 */           header.NumberOfRecords - 1;
/*     */ 
/* 521 */         if ((firstRecordNumber <= mostRecentRecordNumber) && 
/* 522 */           (lastRecordNumber <= mostRecentRecordNumber) && 
/* 523 */           (mostRecentRecordNumber != 2147483647))
/*     */         {
/*     */           continue;
/*     */         }
/* 527 */         DatabasePage page = api.readDatabasePage(recordType, iPage);
/* 528 */         ArrayList<ReceiverInsertionTimeRecord> receiverRecordList = 
/* 529 */           Tools.parseInsertionPage(page);
/*     */ 
/* 533 */         for (ReceiverInsertionTimeRecord receiverRecord : receiverRecordList)
/*     */         {
/* 535 */           if ((receiverRecord.RecordNumber <= mostRecentRecordNumber) && 
/* 536 */             (mostRecentRecordNumber != 2147483647)) {
/*     */             continue;
/*     */           }
/* 539 */           InsertionTimeRecord newRecord = extractInsertionData(receiverRecord);
/*     */ 
/* 541 */           mostRecentRecordNumber = newRecord.RecordNumber;
/*     */ 
/* 543 */           this.currentInsertionRecord = newRecord;
/* 544 */           this.insertionRecords.add(newRecord);
/*     */ 
/* 546 */           numNewRecords++;
/*     */         }
/*     */ 
/*     */       }
/*     */ 
/*     */     }
/*     */ 
/* 555 */     if (numNewRecords > 0)
/*     */     {
/* 557 */       sendInsertionData();
/*     */ 
/* 559 */       notifyUserOfEvent(
/* 560 */         numNewRecords + " new insertion records received", Boolean.valueOf(true), 
/* 561 */         Boolean.valueOf(true));
/*     */     }
/*     */   }
/*     */ 
/*     */   private SettingsRecord extractSettingsData(ReceiverSettingsRecord receiverRecord)
/*     */   {
/* 570 */     SettingsRecord record = new SettingsRecord();
/*     */ 
/* 573 */     record.SystemTime = receiverRecord.SystemTimeStamp;
/* 574 */     record.DisplayTime = receiverRecord.DisplayTimeStamp;
/* 575 */     record.RecordNumber = receiverRecord.RecordNumber;
/* 576 */     record.DisplayTimeOffset = receiverRecord.DisplayTimeOffset;
/* 577 */     record.SystemTimeOffset = receiverRecord.SystemTimeOffset;
/* 578 */     record.IsBlinded = receiverRecord.IsBlinded;
/* 579 */     record.Language = receiverRecord.Language;
/* 580 */     record.LanguageCode = receiverRecord.Language.value;
/* 581 */     record.TransmitterId = 
/* 582 */       Tools.convertTxIdToTxCode(receiverRecord.TransmitterId);
/* 583 */     record.IsTwentyFourHourTime = receiverRecord.IsTwentyFourHourTime;
/* 584 */     record.IsSetUpWizardEnabled = false;
/*     */ 
/* 586 */     record.WizardState = receiverRecord.CurrentSetUpWizardState;
/* 587 */     record.TimeLossOccurred = receiverRecord.TimeLossOccurred;
/*     */ 
/* 590 */     record.AlertProfile = receiverRecord.CurrentUserProfile;
/* 591 */     record.HighAlarmLevelValue = receiverRecord.HighAlarmLevelValue;
/* 592 */     record.HighAlarmSnoozeTime = receiverRecord.HighAlarmSnoozeTime;
/* 593 */     record.IsHighAlarmEnabled = receiverRecord.IsHighAlarmEnabled;
/* 594 */     record.LowAlarmLevelValue = receiverRecord.LowAlarmLevelValue;
/* 595 */     record.LowAlarmSnoozeTime = receiverRecord.LowAlarmSnoozeTime;
/* 596 */     record.IsLowAlarmEnabled = receiverRecord.IsLowAlarmEnabled;
/* 597 */     record.RiseRateValue = receiverRecord.RiseRateValue;
/* 598 */     record.IsRiseRateAlarmEnabled = receiverRecord.IsRiseRateAlarmEnabled;
/* 599 */     record.FallRateValue = receiverRecord.FallRateValue;
/* 600 */     record.IsFallRateAlarmEnabled = receiverRecord.IsFallRateAlarmEnabled;
/* 601 */     record.OutOfRangeAlarmSnoozeTime = receiverRecord.OutOfRangeAlarmSnoozeTime;
/* 602 */     record.IsOutOfRangeAlarmEnabled = receiverRecord.IsOutOfRangeAlarmEnabled;
/*     */ 
/* 604 */     return record;
/*     */   }
/*     */ 
/*     */   private EstimatedGlucoseRecord extractEgvData(ReceiverEgvRecord receiverRecord)
/*     */   {
/* 612 */     EstimatedGlucoseRecord record = new EstimatedGlucoseRecord();
/*     */ 
/* 614 */     record.SystemTime = receiverRecord.SystemTimeStamp;
/* 615 */     record.DisplayTime = receiverRecord.DisplayTimeStamp;
/* 616 */     record.IsDisplayOnly = receiverRecord.IsDisplayOnly;
/* 617 */     record.IsImmediateMatch = receiverRecord.IsImmediateMatch;
/* 618 */     record.IsNoisy = ((receiverRecord.NoiseMode != NoiseMode.None) && 
/* 619 */       (receiverRecord.NoiseMode != NoiseMode.NotComputed) && 
/* 620 */       (receiverRecord.NoiseMode != NoiseMode.Clean));
/* 621 */     record.RecordNumber = receiverRecord.RecordNumber;
/* 622 */     record.TrendArrow = receiverRecord.TrendArrow;
/*     */ 
/* 626 */     if (receiverRecord.SpecialValue.isEmpty())
/*     */     {
/* 628 */       record.Value = receiverRecord.GlucoseValue;
/*     */     }
/*     */     else
/*     */     {
/* 632 */       record.Value = 0;
/*     */ 
/* 634 */       if ((receiverRecord.SpecialValue.equals("SensorNotActive")) || 
/* 635 */         (receiverRecord.SpecialValue.equals("NoAntenna")) || 
/* 636 */         (receiverRecord.SpecialValue.equals("SensorOutOfCal")) || 
/* 637 */         (receiverRecord.SpecialValue.equals("RFBadStatus")))
/*     */       {
/* 639 */         record.SpecialValue = receiverRecord.SpecialValue;
/*     */       }
/* 641 */       else if ((receiverRecord.SpecialValue.equals("Aberration0")) || 
/* 642 */         (receiverRecord.SpecialValue.equals("Aberration1")) || 
/* 643 */         (receiverRecord.SpecialValue.equals("Aberration2")) || 
/* 644 */         (receiverRecord.SpecialValue.equals("Aberration3")))
/*     */       {
/* 646 */         record.SpecialValue = "Aberration";
/*     */       }
/*     */       else
/*     */       {
/* 650 */         record.SpecialValue = "Unknown";
/*     */       }
/*     */     }
/*     */ 
/* 654 */     return record;
/*     */   }
/*     */ 
/*     */   private MeterRecord extractMeterData(ReceiverMeterRecord receiverRecord)
/*     */   {
/* 662 */     MeterRecord record = new MeterRecord();
/* 663 */     record.SystemTime = receiverRecord.SystemTimeStamp;
/* 664 */     record.DisplayTime = receiverRecord.DisplayTimeStamp;
/* 665 */     record.Value = receiverRecord.MeterValue;
/* 666 */     record.MeterDisplayTime = receiverRecord.MeterDisplayTime;
/* 667 */     record.MeterSystemTime = receiverRecord.MeterTimeStamp;
/* 668 */     record.RecordNumber = receiverRecord.RecordNumber;
/*     */ 
/* 670 */     return record;
/*     */   }
/*     */ 
/*     */   private InsertionTimeRecord extractInsertionData(ReceiverInsertionTimeRecord receiverRecord)
/*     */   {
/* 678 */     InsertionTimeRecord record = new InsertionTimeRecord();
/* 679 */     record.SystemTime = receiverRecord.SystemTimeStamp;
/* 680 */     record.DisplayTime = receiverRecord.DisplayTimeStamp;
/* 681 */     record.IsInserted = receiverRecord.IsInserted;
/* 682 */     record.InsertionSystemTime = receiverRecord.InsertionSystemTime;
/* 683 */     record.InsertionDisplayTime = receiverRecord.InsertionDisplayTime;
/* 684 */     record.SessionState = receiverRecord.SessionState;
/* 685 */     record.RecordNumber = receiverRecord.RecordNumber;
/*     */ 
/* 687 */     return record;
/*     */   }
/*     */ 
/*     */   private void sendSettingsData()
/*     */   {
/* 693 */     Intent settingsDataIntent = new Intent(ServiceIntents.NEW_SETTINGS_DATA);
/* 694 */     this.m_context.sendBroadcast(settingsDataIntent);
/*     */   }
/*     */ 
/*     */   private void sendEgvData()
/*     */   {
/* 700 */     Intent egvDataIntent = new Intent(ServiceIntents.NEW_EGV_DATA);
/* 701 */     this.m_context.sendBroadcast(egvDataIntent);
/*     */   }
/*     */ 
/*     */   private void sendMeterData()
/*     */   {
/* 707 */     Intent meterDataIntent = new Intent(ServiceIntents.NEW_METER_DATA);
/* 708 */     this.m_context.sendBroadcast(meterDataIntent);
/*     */   }
/*     */ 
/*     */   private void sendInsertionData()
/*     */   {
/* 714 */     Intent insertionDataIntent = new Intent(
/* 715 */       ServiceIntents.NEW_INSERTION_DATA);
/* 716 */     this.m_context.sendBroadcast(insertionDataIntent);
/*     */   }
/*     */ 
/*     */   private void notifyUserOfEvent(String message, Boolean isToast, Boolean isStatusbar)
/*     */   {
/* 726 */     if (isToast.booleanValue())
/*     */     {
/* 728 */       if (this.m_powerManager.isScreenOn())
/*     */       {
/* 730 */         this.m_handler.post(makeToastMessage(message));
/*     */       }
/*     */     }
/*     */ 
/* 734 */     isStatusbar.booleanValue();
/*     */   }
/*     */ 
/*     */   private Runnable makeToastMessage(final String message)
/*     */   {
/* 755 */     Runnable toastMessage = new Runnable()
/*     */     {
/*     */       public void run()
/*     */       {
/* 760 */         Toast.makeText(ReceiverUpdateService.this.m_context, message, 0).show();
/*     */       }
/*     */     };
/* 764 */     return toastMessage;
/*     */   }
/*     */   public class ServiceBinder extends Binder {
/*     */     public ServiceBinder() {
/*     */     }
/*     */ 
/*     */     public ReceiverUpdateService getService() {
/* 771 */       return ReceiverUpdateService.this;
/*     */     }
/*     */   }
/*     */ 
/*     */   private class receiverUpdate
/*     */     implements Runnable
/*     */   {
/*     */     private receiverUpdate()
/*     */     {
/*     */     }
/*     */ 
/*     */     public void run()
/*     */     {
	
				long start, stop;
				start = System.currentTimeMillis();
				Log.e(TAG, "****** START **********************************************************");
	
				if(macAddress != null)
				{
					ReceiverUpdateService.bleUsbConnected = ReceiverUpdateService.btle.robustConnect("Bluesb", macAddress, 5);		//.robustConnect("Bluesb", 5);
					boolean ping = ReceiverUpdateService.this.m_api.pingReceiver();
					
					Log.i(TAG, "Ping: "+ping+" BLE USB: "+ReceiverUpdateService.bleUsbConnected);
					
					if (ReceiverUpdateService.bleUsbConnected && ping)
					{
					  	Log.e("ReceiverUpdateService", "Receiver Updating...");
					  	Intent receiverConnected = new Intent(ServiceIntents.RECEIVER_CONNECTED);
					  	receiverConnected.putExtra("connected", true);
					  	ReceiverUpdateService.this.sendBroadcast(receiverConnected);
						
					  	try
						{
							long begin = System.currentTimeMillis();
							Log.w("ReceiverUpdateService", "System Time --------------------");
							Date sysTime = ReceiverApiCommands.readSystemTime(ReceiverUpdateService.this.m_api);
							Log.w("ReceiverUpdateService", "Offset --------------------");

							long end = (System.currentTimeMillis() - begin)/1000;
							
							long st, rt;
							st = System.currentTimeMillis()/1000;
							rt = sysTime.getTime()/1000;
							
							//Log.e(TAG, "sendEGVData >>> REC_T: "+rt);
							//Log.e(TAG, "sendEGVData >>> SYS_T: "+st);
							//Log.e(TAG, "sendEGVData >>> DIFFT: "+(st-rt));
							//Log.e(TAG, "sendEGVData >>> END_T: "+end);
							
							Log.w(TAG, "Offset: "+(st-rt-end));
							systemOffset = (st-rt-end);
							systemOffsetReady = true;
							
							//int sysOff = ReceiverApiCommands.readSystemTimeOffset(ReceiverUpdateService.this.m_api);
							//Log.e(TAG, "sendEGVData >>> SysOff: "+sysOff);
			
							//Removed all synchronizations except the EGV records
							//ReceiverUpdateService.this.syncTransmitterId(ReceiverUpdateService.this.m_api);
							//ReceiverUpdateService.this.syncSettingsRecords(ReceiverUpdateService.this.m_api);
							
							ReceiverUpdateService.this.syncEgvRecords(ReceiverUpdateService.this.m_api);
	          				ReceiverUpdateService.this.syncMeterRecords(ReceiverUpdateService.this.m_api);
	          				
	          				//ReceiverUpdateService.this.syncInsertionRecords(ReceiverUpdateService.this.m_api);

						}
					  	catch (NegativeArraySizeException e){
					  		Log.e(TAG, "NegativeArraySizeException - this is fine");
					  		e.printStackTrace();
					  		sendBroadcast(new Intent(ServiceIntents.NO_DATA_ERROR));
					  	}
					  	catch (ReceiverCommException e){
					  		Log.e(TAG, "ReceiverCommException - this is fine");
					  		e.printStackTrace();
					  		sendBroadcast(new Intent(ServiceIntents.NO_DATA_ERROR));
					  	}
						catch (Exception e)
						{
							systemOffsetReady = false;
							
							e.printStackTrace();
							Log.e("ReceiverUpdateService", "Error");
							
							if (ReceiverUpdateService.this.m_api.pingReceiver()) return; 
	                    		ReceiverUpdateService.this.sendBroadcast(new Intent(ServiceIntents.UNKNOWN_ERROR));
	                    		
	                    	Log.e("ReceiverUpdateService", "Receiver not responding - this is bad");
						}
	
						try {
							int battery = ReceiverUpdateService.btle.getBatteryLevel();
							Intent i = new Intent("edu.virginia.dtc.BLEG4_BATTERY");
							i.putExtra("battery", battery);
							sendBroadcast(i);
						} catch (InterruptedException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
	/*     */       }
	/*     */       else
	/*     */       {
					  Intent receiverConnected = new Intent(ServiceIntents.RECEIVER_CONNECTED);
					  receiverConnected.putExtra("connected", false);
					  ReceiverUpdateService.this.sendBroadcast(receiverConnected);
					  
	/* 179 */         //ReceiverUpdateService.this.notifyUserOfEvent("Receiver not responding", Boolean.valueOf(true), Boolean.valueOf(false));
	/*     */       }
				}

				stop = System.currentTimeMillis() - start;
				Log.e(TAG, "****** STOP >>> Diff: "+stop+" **********************************************************");

				ReceiverUpdateService.btle.endCommunications();

/*     */     }
/*     */   }
/*     */ }

/* Location:           C:\Documents and Settings\Boris-III\Desktop\G4 DevKit LP.jar
 * Qualified Name:     com.dexcom.G4DevKit.ReceiverUpdateService
 * JD-Core Version:    0.6.0
 */
