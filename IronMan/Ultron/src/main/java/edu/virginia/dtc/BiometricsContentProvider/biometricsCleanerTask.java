package edu.virginia.dtc.BiometricsContentProvider;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.widget.Toast;

public class biometricsCleanerTask extends BroadcastReceiver {
	public static final String TAG = "biometricsCleanerTask";
	
	private static final int CLEANING_ROUTINE_INTERVAL = 60; //Minutes
	private static final int DATA_RANGE_HOURS = 25; //Hours
	
	@Override
	public void onReceive(Context context, Intent intent){
		
		final String FUNC_TAG = "onReceive";
		
		Debug.i(TAG, FUNC_TAG, "");
		
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire();
        
        deleteOldItems(context, DATA_RANGE_HOURS);

        wl.release();
	}
	
	public void setTask(Context context)
    {
        AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, biometricsCleanerTask.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), 1000 * 60 * CLEANING_ROUTINE_INTERVAL, pi); // Millisec * Second * Minute
    }

    public void cancelTask(Context context)
    {
        Intent intent = new Intent(context, biometricsCleanerTask.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }
    
    
    private void deleteOldItems(Context context, int range) {
    	
    	String FUNC_TAG = "deleteOldItems";
    	Long now_time = System.currentTimeMillis() / 1000;
    	
    	Debug.i(TAG, FUNC_TAG, "Deleting old data...");
    	
    	int count = 0;
    	for (Uri data_uri : Biometrics.TIME_BASED_DATA_URIS) {
        	
        	ContentResolver c = context.getContentResolver();
        	String time_field_name = "time";
        	if (data_uri.equals(Biometrics.INSULIN_URI)) {
        		time_field_name = "req_time";
        	}
        	
        	int counter = c.delete(data_uri, time_field_name +" < "+ String.valueOf(now_time - range*3600) + " AND received_server = 1", null);
        	
        	count += counter;
        	//Debug.i(TAG, FUNC_TAG, ""+counter+" rows deleted from "+data_uri.toString());
        }
    	Debug.i(TAG, FUNC_TAG, count+" old data rows deleted!!");
        Toast.makeText(context, "Old Biometrics Cleaned", Toast.LENGTH_LONG).show(); // For example
    }
}