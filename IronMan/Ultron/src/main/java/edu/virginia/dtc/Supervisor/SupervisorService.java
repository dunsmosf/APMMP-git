package edu.virginia.dtc.Supervisor;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import edu.virginia.dtc.DiAsService.R;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Params;

public class SupervisorService extends Service
{
	private static final String TAG = "SupervisorService";

    public static final long IterationTimeSeconds = 300;
    public static final long TickMax = IterationTimeSeconds/60;

	private PowerManager pm;
	private PowerManager.WakeLock wl;
	private long tickCounter;
	private boolean batteryCollectionStarted = false;
	
    public Handler handler;
	public static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	public static ScheduledFuture<?> timer;

	public Runnable tick = new Runnable()
	{
		final String FUNC_TAG = "tick";
		
		public void run() 
		{
   			Intent tickBroadcast = new Intent("edu.virginia.dtc.intent.action.SUPERVISOR_TIME_TICK");
   			tickBroadcast.putExtra("tick", tickCounter);
   			sendBroadcast(tickBroadcast);

   			if(tickCounter == 2)
    		{
				Intent algTickBroadcast = new Intent("edu.virginia.dtc.intent.action.SUPERVISOR_CONTROL_ALGORITHM_TICK");
                algTickBroadcast.putExtra("tick", tickCounter);
				sendBroadcast(algTickBroadcast);
    		}

    		if (tickCounter % TickMax == 0)
    			tickCounter = 0;
    		
    		tickCounter++;
		}	
	};

    //TODO: check time stuff, i've removed it for now
	@Override
	public void onCreate()
    {
		final String FUNC_TAG = "onCreate";
		
		handler = new Handler();

		BluetoothAdapter bt = ((android.bluetooth.BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();

		if(!bt.isEnabled())
			bt.enable();
		
		// Start the Battery Data Collection if specified by the Parameters
		boolean collectBatteryStats = Params.getBoolean(getContentResolver(), "collectBatteryStats", false);

		if(collectBatteryStats && !batteryCollectionStarted)
        {
			int collectBatteryDataInterval = Params.getInt(getContentResolver(), "collectBatteryStatsInterval", 15);

			Intent startApp = new Intent();
			startApp.setAction("edu.virginia.dtc.intent.action.START_COLLECT_BATTERY_STATS");
			startApp.putExtra("collectBatteryDataInterval", collectBatteryDataInterval);
			sendBroadcast(startApp);
			
			batteryCollectionStarted = true;
		}
		else
        {
			Debug.i(TAG, FUNC_TAG, "No Battery Data Collection");
		}

        final int SUPERVISOR_ID = 4;
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
		builder.setSmallIcon(R.drawable.ic_launcher);
		builder.setContentText("Overseeing DiAs");
		builder.setContentTitle("Supervisor v1.0");
        startForeground(SUPERVISOR_ID, builder.getNotification());

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wl.acquire();

        timer = scheduler.scheduleAtFixedRate(tick, 0, 1, TimeUnit.MINUTES);
	}

	@Override
	public void onDestroy()
    {
        if(timer != null)
            timer.cancel(true);

        wl.release();
	}
	 
	@Override
	public IBinder onBind(Intent arg0)
    {
		return null;
	}
}
