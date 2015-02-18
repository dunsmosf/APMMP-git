package edu.virginia.dtc.G4DevKit;
 
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.widget.Toast;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Params;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
 
public class ReceiverUpdateService extends Service
{
	private static final String TAG = "ReceiverUpdateService";
	
	public Boolean isServiceStarted = Boolean.valueOf(false);
	public SharedPreferences applicationPreferences;

	public List<SettingsRecord> settingsRecords = Collections.synchronizedList(new ArrayList());
	public List<MeterRecord> meterRecords = Collections.synchronizedList(new ArrayList());
	public List<EstimatedGlucoseRecord> egvRecords = Collections.synchronizedList(new ArrayList());
	public List<InsertionTimeRecord> insertionRecords = Collections.synchronizedList(new ArrayList());

	public String currentTransmitterId;
	public SettingsRecord currentSettingsRecord;
	public MeterRecord currentMeterRecord;
	public EstimatedGlucoseRecord currentEstimatedGlucoseRecord;
	public InsertionTimeRecord currentInsertionRecord;
	private Context m_context;
	private final Handler m_handler = new Handler();
	
	Runnable m_toastMessage;
	private final IBinder m_binder = new ServiceBinder();
	private PowerManager m_powerManager;
	
	private static ReceiverApi api;
	private static ReceiverBleComm bleComm;
	private static receiverUpdate updater;
			
	public long systemOffset = -1;
	public boolean systemOffsetReady = false;

	public static final int EGV_PER_PAGE = 38;
	public static final int METER_PER_PAGE = 31;
	
	public static int EGV_PAGES = 1;
	public static int METER_PAGES = 1;
			
	private int lastEgvRecord = -1;
	private int lastMeterRecord = -1;
			
	public void onCreate()
	{
		final String FUNC_TAG = "onCreate";
		
		this.m_context = getApplicationContext();
		this.applicationPreferences = PreferenceManager.getDefaultSharedPreferences(this.m_context);
		this.m_powerManager = ((PowerManager)this.m_context.getSystemService(Context.POWER_SERVICE));
		
		BroadcastReceiver update = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent) 
			{
				if(updater != null);
					updater.run();
			}
		};
		this.registerReceiver(update, new IntentFilter("edu.virginia.dtc.sync_receiver"));
		
		Debug.i(TAG, FUNC_TAG, "finished...");
	}
	
	public IBinder onBind(Intent intent)
	{
		return this.m_binder;
	}
 
   	public int onStartCommand(Intent intent, int flags, int startId)
   	{
   		final String FUNC_TAG = "onStartCommand";

   		String mac = intent.getStringExtra("mac");
   		String code = intent.getStringExtra("code");
   		
   		Debug.i(TAG, FUNC_TAG, "MAC: "+mac+" Code: "+code);
   		
   		bleComm = new ReceiverBleComm(this, mac, code);
   		api = new ReceiverApi(bleComm);
   		
   		updater = new receiverUpdate();
   		
   		Debug.i(TAG, FUNC_TAG, "finished...");

   		return 1;
   	}
 
	public void onDestroy()
	{
		if (this.isServiceStarted.booleanValue())
		{
			Toast.makeText(this.m_context, "G4 Dev Kit Service destroyed", Toast.LENGTH_SHORT).show();
			this.isServiceStarted = Boolean.valueOf(false);
			this.applicationPreferences.edit().putBoolean("serviceStatus", this.isServiceStarted.booleanValue()).commit();
		}
	}
 
	private void syncTransmitterId(ReceiverApi api) throws Exception
	{
		final String FUNC_TAG = "syncTransmitterId";
		this.currentTransmitterId = ReceiverApiCommands.readTransmitterId(api);
		Debug.i(TAG, FUNC_TAG, "");
	}
 
	private void syncSettingsRecords(ReceiverApi api) throws Exception
   	{
	   final String FUNC_TAG = "syncSettingsRecords";
	   Debug.i(TAG, FUNC_TAG, "");
	
	   int mostRecentRecordNumber = 2147483647;
	   int numNewRecords = 0;
 
	   if (this.settingsRecords.size() > 0)
	   {
		   mostRecentRecordNumber = ((SettingsRecord)this.settingsRecords.get(this.settingsRecords.size() - 1)).RecordNumber;
	   }
 
	   ReceiverRecordType recordType = ReceiverRecordType.UserSettingData;
	   DatabasePageRange range = api.readDatabasePageRange(recordType);
 
	   int pageCount = range.LastPage - range.FirstPage + 1;
 
	   if (range.LastPage != -1)
	   {
		   int iPage = range.FirstPage;
		   
		   for (; iPage < range.FirstPage + pageCount; iPage++)
		   {
			   DatabasePageHeader header = api.readDatabasePageHeader(recordType, iPage);
 
			   int firstRecordNumber = header.FirstRecordIndex;
			   int lastRecordNumber = header.FirstRecordIndex + header.NumberOfRecords - 1;
 
			   if ((firstRecordNumber <= mostRecentRecordNumber) && (lastRecordNumber <= mostRecentRecordNumber) && (mostRecentRecordNumber != 2147483647))
			   {
				   continue;
			   }
			   
			   DatabasePage page = api.readDatabasePage(recordType, iPage);
			   ArrayList<ReceiverSettingsRecord> receiverRecordList = Tools.parseSettingsPage(page);
 
			   for (ReceiverSettingsRecord receiverRecord : receiverRecordList)
			   {
				   if ((receiverRecord.RecordNumber <= mostRecentRecordNumber) && (mostRecentRecordNumber != 2147483647))
					   continue;
				   
				   SettingsRecord newRecord = extractSettingsData(receiverRecord);
				   mostRecentRecordNumber = newRecord.RecordNumber;
 
				   this.currentSettingsRecord = newRecord;
				   this.settingsRecords.add(newRecord);
 
				   numNewRecords++;
			   }
 
		   }
	   }
 
	   if (numNewRecords > 0)
	   {
		   sendSettingsData();
		   notifyUserOfEvent(numNewRecords + " new settings records received", Boolean.valueOf(true), Boolean.valueOf(true));
	   }
   	}
 
	private void syncEgvRecords(ReceiverApi api) throws Exception
	{
		final String FUNC_TAG = "syncEgvRecords";
		
		int mostRecentRecordNumber = 2147483647;
		int numNewRecords = 0;

		if (this.egvRecords.size() > 0)
		{
			mostRecentRecordNumber = ((EstimatedGlucoseRecord)this.egvRecords.get(this.egvRecords.size() - 1)).RecordNumber;
		}
		
		ReceiverRecordType recordType = ReceiverRecordType.EGVData;
		DatabasePageRange range = api.readDatabasePageRange(recordType);

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
					//Debug.i(TAG, FUNC_TAG, "The time can't exceed 12 hours so the valid time is: "+last_valid_time);
				}
				//else
				//	Debug.i(TAG, FUNC_TAG, "The new valid CGM time is : "+last_valid_time);
			}
		}
		else if(last_valid_time < 0)
		{
			int hours = Params.getInt(getContentResolver(), "cgm_history_hrs", 12);
			long minutes = hours*60;
			long seconds = minutes*60;
			
			last_valid_time = (System.currentTimeMillis()/1000)-seconds; 
			//Debug.i(TAG, FUNC_TAG, "There is no data in the table, so the last valid time is "+last_valid_time);
		}
		c_time.close();

		//Debug.e(TAG, FUNC_TAG, "Last Valid Time: "+last_valid_time);
		
		long sec = (System.currentTimeMillis()/1000) - last_valid_time;
		long min = sec/60;
		
		int extraPage = 1;
		if(sec > 600)
			extraPage = 2;
		
		long pages = ((min/5)/ReceiverUpdateService.EGV_PER_PAGE) + extraPage;
		ReceiverUpdateService.EGV_PAGES = (int) pages;
		
		//Debug.e(TAG, FUNC_TAG, "Number of pages: "+ReceiverUpdateService.EGV_PAGES+" Records to pull: "+pages*ReceiverUpdateService.EGV_PER_PAGE);
		//Debug.i(TAG, FUNC_TAG, "Most recent Record is: "+mostRecentRecordNumber);
		
		range.FirstPage = range.LastPage - EGV_PAGES + 1;
		
		if(range.FirstPage < 0)
			range.FirstPage = 0;
		
		int pageCount = range.LastPage - range.FirstPage + 1;
 
		//Debug.i(TAG, FUNC_TAG, "Page Count: "+pageCount+" Last Page: "+range.LastPage+" First Page: "+range.FirstPage);

		if (range.LastPage != -1)
		{
			int iPage = range.FirstPage;

			//Debug.i(TAG, FUNC_TAG, "iPage = "+iPage+" Count = "+(range.FirstPage + pageCount)+" Total to run = "+(range.FirstPage + pageCount-iPage));
			
			for (; iPage < range.FirstPage + pageCount; iPage++)
			{
				DatabasePageHeader header = api.readDatabasePageHeader(recordType, iPage);

				int firstRecordNumber = header.FirstRecordIndex;
				int lastRecordNumber = header.FirstRecordIndex + header.NumberOfRecords - 1;
				
				//Debug.i(TAG, FUNC_TAG, "First: "+firstRecordNumber+" Last: "+lastRecordNumber+" Recent: "+mostRecentRecordNumber);
 
				if ((firstRecordNumber <= mostRecentRecordNumber) && (lastRecordNumber <= mostRecentRecordNumber) && (mostRecentRecordNumber != 2147483647))
				{
					continue;
				}

				DatabasePage page = api.readDatabasePage(recordType, iPage);
				ArrayList<ReceiverEgvRecord> receiverRecordList = Tools.parseEgvPage(page);
 
				for (ReceiverEgvRecord receiverRecord : receiverRecordList)
				{
					if ((receiverRecord.RecordNumber <= mostRecentRecordNumber) && (mostRecentRecordNumber != 2147483647))
					{
						continue;
					}

					EstimatedGlucoseRecord newRecord = extractEgvData(receiverRecord);
					mostRecentRecordNumber = newRecord.RecordNumber;

					this.currentEstimatedGlucoseRecord = newRecord;
					this.egvRecords.add(newRecord);
 
					numNewRecords++;
				}
			}
		}
 
		if (numNewRecords > 0)
		{
			sendEgvData();
			Debug.e(TAG, FUNC_TAG, "Number of new EGV records: "+numNewRecords);
			//notifyUserOfEvent(numNewRecords + " new EGV records received", Boolean.valueOf(true), Boolean.valueOf(true));
		}
	}
 
	private void syncMeterRecords(ReceiverApi api) throws Exception
	{
		final String FUNC_TAG = "syncMeterRecords";
		
		int mostRecentRecordNumber = 2147483647;
		int numNewRecords = 0;
 
		if (this.meterRecords.size() > 0)
		{
			mostRecentRecordNumber = ((MeterRecord)this.meterRecords.get(this.meterRecords.size() - 1)).RecordNumber;
		}
 
		ReceiverRecordType recordType = ReceiverRecordType.MeterData;
		DatabasePageRange range = api.readDatabasePageRange(recordType);

		//Debug.i(TAG, FUNC_TAG, "Most recent Record is: "+mostRecentRecordNumber);
			
		range.FirstPage = range.LastPage - ReceiverUpdateService.EGV_PAGES + 1;
			
		if(range.FirstPage < 0)
			range.FirstPage = 0;
		
		lastMeterRecord = mostRecentRecordNumber;
		//Debug.i(TAG, FUNC_TAG, "Last Record is: "+lastMeterRecord);

		int pageCount = range.LastPage - range.FirstPage + 1;

		//Debug.i(TAG, FUNC_TAG, "Page Count: "+pageCount+" Last Page: "+range.LastPage+" First Page: "+range.FirstPage);
 
		if (range.LastPage != -1)
		{
			int iPage = range.FirstPage;
			//Debug.i(TAG, FUNC_TAG, "iPage = "+iPage+" Count = "+(range.FirstPage + pageCount)+" Total to run = "+(range.FirstPage + pageCount-iPage));

			for (; iPage < range.FirstPage + pageCount; iPage++)
			{
				DatabasePageHeader header = api.readDatabasePageHeader(recordType, iPage);
 
				//Debug.i(TAG, FUNC_TAG, "Records on this page = "+header.NumberOfRecords);

				int firstRecordNumber = header.FirstRecordIndex;
				int lastRecordNumber = header.FirstRecordIndex + header.NumberOfRecords - 1;
 
				if ((firstRecordNumber <= mostRecentRecordNumber) && (lastRecordNumber <= mostRecentRecordNumber) && (mostRecentRecordNumber != 2147483647))
				{
					continue;
				}
				
				DatabasePage page = api.readDatabasePage(recordType, iPage);
				ArrayList<ReceiverMeterRecord> receiverRecordList = Tools.parseMeterPage(page);
 
				for (ReceiverMeterRecord receiverRecord : receiverRecordList)
				{
					if ((receiverRecord.RecordNumber <= mostRecentRecordNumber) && (mostRecentRecordNumber != 2147483647)) 
					{
						continue;
					}
					
					MeterRecord newRecord = extractMeterData(receiverRecord);
					mostRecentRecordNumber = newRecord.RecordNumber;
					
					this.currentMeterRecord = newRecord;
					this.meterRecords.add(newRecord);
 
					numNewRecords++;
				}
			}
		}
 
		if (numNewRecords > 0)
		{
			sendMeterData();
			Debug.e(TAG, FUNC_TAG, "Number of new meter records: "+numNewRecords);
			//notifyUserOfEvent(numNewRecords + " new meter records received", Boolean.valueOf(true), Boolean.valueOf(true));
		}
	}
 
	private void syncInsertionRecords(ReceiverApi api) throws Exception
	{
		final String FUNC_TAG = "syncInsertionRecords";
		
		Debug.i(TAG, FUNC_TAG, "");
	
		int mostRecentRecordNumber = 2147483647;
		int numNewRecords = 0;
 
		if (this.insertionRecords.size() > 0)
		{
			mostRecentRecordNumber = ((InsertionTimeRecord)this.insertionRecords.get(this.insertionRecords.size() - 1)).RecordNumber;
		}
 
		ReceiverRecordType recordType = ReceiverRecordType.InsertionTime;
		DatabasePageRange range = api.readDatabasePageRange(recordType);
 
		int pageCount = range.LastPage - range.FirstPage + 1;
 
		if (range.LastPage != -1)
		{
			int iPage = range.FirstPage;
			for (; iPage < range.FirstPage + pageCount; iPage++)
			{
				DatabasePageHeader header = api.readDatabasePageHeader(recordType, iPage);
 
				int firstRecordNumber = header.FirstRecordIndex;
				int lastRecordNumber = header.FirstRecordIndex + header.NumberOfRecords - 1;
 
				if ((firstRecordNumber <= mostRecentRecordNumber) && (lastRecordNumber <= mostRecentRecordNumber) && (mostRecentRecordNumber != 2147483647))
				{
					continue;
				}

				DatabasePage page = api.readDatabasePage(recordType, iPage);
				ArrayList<ReceiverInsertionTimeRecord> receiverRecordList = Tools.parseInsertionPage(page);
 
				for (ReceiverInsertionTimeRecord receiverRecord : receiverRecordList)
				{
					if ((receiverRecord.RecordNumber <= mostRecentRecordNumber) && (mostRecentRecordNumber != 2147483647)) 
					{
						continue;
					}
					
					InsertionTimeRecord newRecord = extractInsertionData(receiverRecord);
					mostRecentRecordNumber = newRecord.RecordNumber;
 
					this.currentInsertionRecord = newRecord;
					this.insertionRecords.add(newRecord);
 
					numNewRecords++;
				}
			}
		}
 
		if (numNewRecords > 0)
		{
			sendInsertionData();
 
			notifyUserOfEvent(numNewRecords + " new insertion records received", Boolean.valueOf(true), Boolean.valueOf(true));
		}
	}
 
	private SettingsRecord extractSettingsData(ReceiverSettingsRecord receiverRecord)
	{
		SettingsRecord record = new SettingsRecord();
 
		record.SystemTime = receiverRecord.SystemTimeStamp;
		record.DisplayTime = receiverRecord.DisplayTimeStamp;
		record.RecordNumber = receiverRecord.RecordNumber;
		record.DisplayTimeOffset = receiverRecord.DisplayTimeOffset;
		record.SystemTimeOffset = receiverRecord.SystemTimeOffset;
		record.IsBlinded = receiverRecord.IsBlinded;
		record.Language = receiverRecord.Language;
		record.LanguageCode = receiverRecord.Language.value;
		record.TransmitterId = Tools.convertTxIdToTxCode(receiverRecord.TransmitterId);
		record.IsTwentyFourHourTime = receiverRecord.IsTwentyFourHourTime;
		record.IsSetUpWizardEnabled = false;
		record.WizardState = receiverRecord.CurrentSetUpWizardState;
		record.TimeLossOccurred = receiverRecord.TimeLossOccurred;
		record.AlertProfile = receiverRecord.CurrentUserProfile;
		record.HighAlarmLevelValue = receiverRecord.HighAlarmLevelValue;
		record.HighAlarmSnoozeTime = receiverRecord.HighAlarmSnoozeTime;
		record.IsHighAlarmEnabled = receiverRecord.IsHighAlarmEnabled;
		record.LowAlarmLevelValue = receiverRecord.LowAlarmLevelValue;
		record.LowAlarmSnoozeTime = receiverRecord.LowAlarmSnoozeTime;
		record.IsLowAlarmEnabled = receiverRecord.IsLowAlarmEnabled;
		record.RiseRateValue = receiverRecord.RiseRateValue;
		record.IsRiseRateAlarmEnabled = receiverRecord.IsRiseRateAlarmEnabled;
		record.FallRateValue = receiverRecord.FallRateValue;
		record.IsFallRateAlarmEnabled = receiverRecord.IsFallRateAlarmEnabled;
		record.OutOfRangeAlarmSnoozeTime = receiverRecord.OutOfRangeAlarmSnoozeTime;
		record.IsOutOfRangeAlarmEnabled = receiverRecord.IsOutOfRangeAlarmEnabled;
 
		return record;
	}
 
	private EstimatedGlucoseRecord extractEgvData(ReceiverEgvRecord receiverRecord)
	{
		EstimatedGlucoseRecord record = new EstimatedGlucoseRecord();
 
		record.SystemTime = receiverRecord.SystemTimeStamp;
		record.DisplayTime = receiverRecord.DisplayTimeStamp;
		record.IsDisplayOnly = receiverRecord.IsDisplayOnly;
		record.IsImmediateMatch = receiverRecord.IsImmediateMatch;
		record.IsNoisy = ((receiverRecord.NoiseMode != NoiseMode.None) && (receiverRecord.NoiseMode != NoiseMode.NotComputed) && (receiverRecord.NoiseMode != NoiseMode.Clean));
		record.RecordNumber = receiverRecord.RecordNumber;
		record.TrendArrow = receiverRecord.TrendArrow;
 
		if (receiverRecord.SpecialValue.isEmpty())
		{
			record.Value = receiverRecord.GlucoseValue;
		}
		else
		{
			record.Value = 0;
 
			if ((receiverRecord.SpecialValue.equals("SensorNotActive")) || (receiverRecord.SpecialValue.equals("NoAntenna")) || 
					(receiverRecord.SpecialValue.equals("SensorOutOfCal")) || (receiverRecord.SpecialValue.equals("RFBadStatus")))
			{
				record.SpecialValue = receiverRecord.SpecialValue;
			}
			else if ((receiverRecord.SpecialValue.equals("Aberration0")) || (receiverRecord.SpecialValue.equals("Aberration1")) || 
					(receiverRecord.SpecialValue.equals("Aberration2")) || (receiverRecord.SpecialValue.equals("Aberration3")))
			{
				record.SpecialValue = "Aberration";
			}
			else
			{
				record.SpecialValue = "Unknown";
			}
		}
		return record;
	}
 
	private MeterRecord extractMeterData(ReceiverMeterRecord receiverRecord)
	{
		MeterRecord record = new MeterRecord();
		record.SystemTime = receiverRecord.SystemTimeStamp;
		record.DisplayTime = receiverRecord.DisplayTimeStamp;
		record.Value = receiverRecord.MeterValue;
		record.MeterDisplayTime = receiverRecord.MeterDisplayTime;
		record.MeterSystemTime = receiverRecord.MeterTimeStamp;
		record.RecordNumber = receiverRecord.RecordNumber;
 
		return record;
	}
 
	private InsertionTimeRecord extractInsertionData(ReceiverInsertionTimeRecord receiverRecord)
	{
		InsertionTimeRecord record = new InsertionTimeRecord();
		record.SystemTime = receiverRecord.SystemTimeStamp;
		record.DisplayTime = receiverRecord.DisplayTimeStamp;
		record.IsInserted = receiverRecord.IsInserted;
		record.InsertionSystemTime = receiverRecord.InsertionSystemTime;
		record.InsertionDisplayTime = receiverRecord.InsertionDisplayTime;
		record.SessionState = receiverRecord.SessionState;
		record.RecordNumber = receiverRecord.RecordNumber;
 
		return record;
	}
 
	private void sendSettingsData()
	{
		Intent settingsDataIntent = new Intent(ServiceIntents.NEW_SETTINGS_DATA);
		this.m_context.sendBroadcast(settingsDataIntent);
	}
 
	private void sendEgvData()
	{
		Intent egvDataIntent = new Intent(ServiceIntents.NEW_EGV_DATA);
		this.m_context.sendBroadcast(egvDataIntent);
	}
 
	private void sendMeterData()
	{
		Intent meterDataIntent = new Intent(ServiceIntents.NEW_METER_DATA);
		this.m_context.sendBroadcast(meterDataIntent);
	}
 
	private void sendInsertionData()
   	{
	   Intent insertionDataIntent = new Intent(ServiceIntents.NEW_INSERTION_DATA);
	   this.m_context.sendBroadcast(insertionDataIntent);
   	}
 
	private void notifyUserOfEvent(String message, Boolean isToast, Boolean isStatusbar)
	{
		if (isToast.booleanValue())
		{
			if (this.m_powerManager.isScreenOn())
			{
				this.m_handler.post(makeToastMessage(message));
			}
		}
		isStatusbar.booleanValue();
	}
 
	private Runnable makeToastMessage(final String message)
	{	
		Runnable toastMessage = new Runnable()
		{
			public void run()
			{
				Toast.makeText(ReceiverUpdateService.this.m_context, message, Toast.LENGTH_SHORT).show();
			}
		};
		return toastMessage;
	}
   
	public class ServiceBinder extends Binder 
	{
		public ServiceBinder() {}
 
		public ReceiverUpdateService getService() 
		{
			return ReceiverUpdateService.this;
		}
	}
 
	private class receiverUpdate implements Runnable
	{
		final String FUNC_TAG = "receiverUpdate";
		
		private receiverUpdate() {}
 
		public void run()
		{
			long start, stop;
			start = System.currentTimeMillis();
			Debug.v(TAG, FUNC_TAG, "START **********************************************************");
	
			boolean ping = ReceiverUpdateService.api.pingReceiver();
			Debug.i(TAG, FUNC_TAG, "Ping: "+ping);

			if (ping)
			{
			  	Debug.i(TAG, FUNC_TAG, "Receiver Updating...");
			  	Intent receiverConnected = new Intent(ServiceIntents.RECEIVER_CONNECTED);
			  	receiverConnected.putExtra("connected", true);
			  	ReceiverUpdateService.this.sendBroadcast(receiverConnected);
				
			  	try
				{
					long begin = System.currentTimeMillis();
					Date sysTime = ReceiverApiCommands.readSystemTime(ReceiverUpdateService.api);
					long end = (System.currentTimeMillis() - begin)/1000;
					
					long st, rt;
					st = System.currentTimeMillis()/1000;
					rt = sysTime.getTime()/1000;
					
					Debug.i(TAG, FUNC_TAG, "Time Offset: "+(st-rt-end));
					systemOffset = (st-rt-end);
					systemOffsetReady = true;
					
					ReceiverUpdateService.this.syncEgvRecords(ReceiverUpdateService.api);
      				ReceiverUpdateService.this.syncMeterRecords(ReceiverUpdateService.api);
				}
			  	catch (NegativeArraySizeException e)
			  	{
			  		Debug.e(TAG, FUNC_TAG, "Negative Array Size Exception!");
			  		e.printStackTrace();
			  		sendBroadcast(new Intent(ServiceIntents.NO_DATA_ERROR));
			  	}
			  	catch (ReceiverCommException e)
			  	{
			  		Debug.e(TAG, FUNC_TAG, "Receiver Communication Exception!");
			  		e.printStackTrace();
			  		sendBroadcast(new Intent(ServiceIntents.NO_DATA_ERROR));
			  	}
				catch (Exception e)
				{
					e.printStackTrace();
					Debug.e(TAG, FUNC_TAG, "Error");
					
					systemOffsetReady = false;
					
					if (ReceiverUpdateService.api.pingReceiver()) return; 
                		ReceiverUpdateService.this.sendBroadcast(new Intent(ServiceIntents.UNKNOWN_ERROR));
                		
                	Debug.e(TAG, FUNC_TAG, "Receiver Not Responding!");
				}
			}
			else
	       	{
	    	   Intent receiverConnected = new Intent(ServiceIntents.RECEIVER_CONNECTED);
	    	   receiverConnected.putExtra("connected", false);
	    	   ReceiverUpdateService.this.sendBroadcast(receiverConnected);
	       	}
			
			stop = System.currentTimeMillis() - start;
			Debug.v(TAG, FUNC_TAG, "STOP (elapsed time: "+stop+" ms) ************************************");
		}
	}
}