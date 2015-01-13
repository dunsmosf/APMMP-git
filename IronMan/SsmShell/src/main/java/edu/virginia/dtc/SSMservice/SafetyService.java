package edu.virginia.dtc.SSMservice;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Messages;

public class SafetyService extends Service {
	private PowerManager pm;
	private PowerManager.WakeLock wl;

    public static final String TAG = "SSMservice";

    private ServiceObserver serviceObserver;
    
    @Override
	public void onCreate() {
        // Set up a Notification for this Service
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
        int icon = R.drawable.ic_launcher;
        CharSequence tickerText = "";
        long when = System.currentTimeMillis();
        Notification notification = new Notification(icon, tickerText, when);
        CharSequence contentTitle = "Safety Service v1.0";
        CharSequence contentText = "Monitoring Insulin Dosing";
        Intent notificationIntent = new Intent(this, SafetyService.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		Context context = getApplicationContext();
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        final int SAFETY_ID = 1;

        startForeground(SAFETY_ID, notification);

		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();

        serviceObserver = new ServiceObserver(new Handler());
        getContentResolver().registerContentObserver(Biometrics.SERVICE_OUTPUTS_URI, false, serviceObserver);
	}

	@Override
	public void onDestroy() {
        if(serviceObserver != null)
            getContentResolver().unregisterContentObserver(serviceObserver);
	}
	
    /* When binding to the service, we return an interface to our messenger for sending messages to the service. */
    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    class ServiceObserver extends ContentObserver
    {
        final String FUNC_TAG = "ServiceObserver";

        public ServiceObserver(Handler handler)
        {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange)
        {
            this.onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri)
        {
            Cursor c = getContentResolver().query(Biometrics.SERVICE_OUTPUTS_URI, null, null, null, "_id DESC LIMIT 1");
            if(c.moveToFirst())
            {
                int type = c.getInt(c.getColumnIndex("type"));
                int machine = c.getInt(c.getColumnIndex("machine"));
                int source = c.getInt(c.getColumnIndex("source"));
                int message = c.getInt(c.getColumnIndex("message"));
                int destination = c.getInt(c.getColumnIndex("destination"));
                long cycle = c.getLong(c.getColumnIndex("cycle"));

                if(destination == Messages.SSM) {
                    if (type == Messages.COMMAND) {
                        switch(message) {
                            case Messages.PING:
                                Debug.i(TAG, FUNC_TAG, "Sending PING response...");
                                sendResponse(source, message, machine, cycle);
                                break;
                            case Messages.UPDATE:
                                break;
                            case Messages.PROCESS:
                                break;
                        }
                    } else if (type == Messages.RESPONSE) {

                    }
                }
            }
            c.close();
        }

        public void sendResponse(int dest, int message, int machine, long cycle)
        {
            ContentValues c = new ContentValues();
            c.put("time", System.currentTimeMillis() / 1000);
            c.put("cycle", cycle);
            c.put("source", Messages.SSM);
            c.put("destination", dest);
            c.put("machine", machine);
            c.put("type", Messages.RESPONSE);
            c.put("message", message);
            c.put("data", "");

            getContentResolver().insert(Biometrics.SERVICE_OUTPUTS_URI, c);
        }
    }
}