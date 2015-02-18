package edu.virginia.dtc.BiometricsContentProvider;

import java.util.Calendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.widget.Toast;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;

public class biometricsWeeklyArchiveTask extends BroadcastReceiver {
	public static final String TAG = "biometricsWeeklyArchiveTask";
	
	private static final int ARCHIVE_ROUTINE_INTERVAL = 7; //Days
	
	@Override
	public void onReceive(Context context, Intent intent){
		
		final String FUNC_TAG = "onReceive";
		
		Debug.i(TAG, FUNC_TAG, "");
		
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();
        
        archiveWeekDb(context);

        wl.release();
	}
	
	public void setTask(Context context)
    {
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, biometricsWeeklyArchiveTask.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        
        Calendar nextSundayNight = Calendar.getInstance();
        nextSundayNight.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        nextSundayNight.set(Calendar.HOUR_OF_DAY, 23);
        nextSundayNight.set(Calendar.MINUTE, 59);
        nextSundayNight.set(Calendar.SECOND, 59);
        nextSundayNight.set(Calendar.MILLISECOND, 0);
        
        if (nextSundayNight.getTimeInMillis() < System.currentTimeMillis()) {
        	nextSundayNight.add(Calendar.WEEK_OF_MONTH, 1);
        }
        
        am.setRepeating(AlarmManager.RTC_WAKEUP, nextSundayNight.getTimeInMillis(), AlarmManager.INTERVAL_DAY * ARCHIVE_ROUTINE_INTERVAL, pi);
    }

    public void cancelTask(Context context)
    {
        Intent intent = new Intent(context, biometricsCleanerTask.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }
    
    
    private void archiveWeekDb(Context context) {
    	
    	String FUNC_TAG = "archiveWeekDb";
    	
    	Debug.i(TAG, FUNC_TAG, "Archiving old data...");
    	
    	context.getContentResolver().delete(Uri.parse("content://"+ Biometrics.PROVIDER_NAME + "/archive_weekly"), null, null);
    	
    	Toast.makeText(context, "Data archived to db file", Toast.LENGTH_LONG).show();
    }
}