//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

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

public class IOMain extends Service {
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	public static final String TAG = "APCservice";

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
        Context context = getApplicationContext();
        CharSequence contentTitle = "APCservice";
        CharSequence contentText = "Mitigating Hyperglycemia";
        Intent notificationIntent = new Intent(this, IOMain.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        final int APC_ID = 3;

        // Make this a Foreground Service
        startForeground(APC_ID, notification);

		// Keep the CPU running even after the screen dims
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

                if(destination == Messages.APC) {
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
            c.put("source", Messages.APC);
            c.put("destination", dest);
            c.put("machine", machine);
            c.put("type", Messages.RESPONSE);
            c.put("message", message);
            c.put("data", "");

            getContentResolver().insert(Biometrics.SERVICE_OUTPUTS_URI, c);
        }
    }
}