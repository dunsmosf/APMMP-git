package edu.virginia.dtc.biometricsContentProvider;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.IBinder;
import edu.virginia.dtc.SysMan.Debug;

public class biometricsCleanerService extends Service {
	
	public final String TAG = "BiometricsCleanerService";
	
	private long last_log_time = 0;
	
	public biometricsCleanerTask bCleanerTask = new biometricsCleanerTask();
	public biometricsWeeklyArchiveTask bArchiveTask = new biometricsWeeklyArchiveTask();
	
	public BroadcastReceiver ServiceReceiver = null;			// Listens for LOG_ACTION information broadcasts
	
	public static final String BIOMETRICS_PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
	public static final Uri LOG_CONTENT_URI = Uri.parse("content://" + BIOMETRICS_PROVIDER_NAME + "/log");
	
	/**
	 * onBind
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	
	/**
	 * onCreate
	 */
	@Override
	public void onCreate() {
		final String FUNC_TAG = "onCreate";

		super.onCreate();
		Debug.i(TAG, FUNC_TAG, "");
		
		last_log_time = 0;
		ServiceReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				Long i = 0L;
				Integer p = 0;
				Debug.i(TAG, FUNC_TAG, "ServiceReceiver > ");
				if (intent.getAction().equals("edu.virginia.dtc.intent.action.LOG_ACTION")) {
					i = intent.getLongExtra("time", 0);
					p = intent.getIntExtra("priority", 0);
					
					if (i <= last_log_time) {
						last_log_time = last_log_time + 1;
					} else {
						last_log_time = i;
					}
					Debug.i(TAG, FUNC_TAG, "ServiceReceiver > Service=" + intent.getStringExtra("Service") + ", Status=" + intent.getStringExtra("Status")
							+ ", time=" + last_log_time + ", priority=" + p.toString());
					try {
						ContentValues values = new ContentValues();
						values.put("status", intent.getStringExtra("Status"));
						values.put("service", intent.getStringExtra("Service"));
						values.put("time", last_log_time);
						values.put("priority", intent.getIntExtra("priority", 0));
						try {
							getContentResolver().insert(LOG_CONTENT_URI, values);
						} catch (Exception e) {
							Debug.e("Error", FUNC_TAG, e.getMessage());
						}
					} catch (Exception e) {
						Debug.e("DB", FUNC_TAG, e.toString());
					}
				}
			}
		};
		registerReceiver(ServiceReceiver, new IntentFilter("edu.virginia.dtc.intent.action.LOG_ACTION"));
		
		bCleanerTask.setTask(biometricsCleanerService.this);
		bArchiveTask.setTask(biometricsCleanerService.this);
		Debug.i(TAG, FUNC_TAG, "Cleaner and Archive tasks scheduled.");
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (ServiceReceiver != null) {
			unregisterReceiver(ServiceReceiver);
		}
		bCleanerTask.cancelTask(biometricsCleanerService.this);
		bArchiveTask.cancelTask(biometricsCleanerService.this);
		
	}
}
