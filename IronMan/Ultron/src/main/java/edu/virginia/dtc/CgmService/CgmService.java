//*********************************************************************************************************************
//  Copyright 2011-2012 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************


package edu.virginia.dtc.CgmService;

import edu.virginia.dtc.DiAsService.R;
import edu.virginia.dtc.Supervisor.Supervisor;
import edu.virginia.dtc.Supervisor.SupervisorService;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.State;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;

/**
 * This is the main Activity that displays the current chat session.
 */
public class CgmService extends Service {	
	
	// CGM Service commands
	public static final int CGM_SERVICE_CMD_NULL = 0;
	public static final int CGM_SERVICE_CMD_CALIBRATE = 1;
	public static final int CGM_SERVICE_CMD_DISCONNECT = 2;
	public static final int CGM_SERVICE_CMD_INIT = 3;
	
	// Commands for CGM Driver
	private static final int CGM_SERVICE2DRIVER_REGISTER = 1;
	private static final int CGM_SERVICE2DRIVER_CALIBRATE = 2;
	private static final int CGM_SERVICE2DRIVER_DISCONNECT = 4;
	
	// Commands for CGM Service from Driver
	private static final int DRIVER2CGM_SERVICE_NEW_EGV = 0;
	private static final int DRIVER2CGM_SERVICE_NEW_METER = 3;
    
	private static final String TAG = "CGMService";

	public int DIAS_STATE;
    
	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;
	
	private Messenger driverReceiver = new Messenger(new driverMessageHandler());
	private Messenger driverTransmitter;
	private ServiceConnection driverConnection;
	
	private Cgm receiver;
	
	private SystemObserver sysObserver;
	
	private Context main;
	
    @Override
    public void onCreate()
    {
    	final String FUNC_TAG = "onCreate";

        super.onCreate();

        receiver = new Cgm();

        main = this;
        DIAS_STATE = State.DIAS_STATE_STOPPED;

    	String ns = Context.NOTIFICATION_SERVICE;
    	NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
    	int icon = R.drawable.ic_launcher;
    	CharSequence tickerText = "CGM Service v1.0";
    	long when = System.currentTimeMillis();
    	Notification notification = new Notification(icon, tickerText, when);
    	Context context = getApplicationContext();
    	CharSequence contentTitle = "CGM Service v1.0";
    	CharSequence contentText = "CGM";
    	Intent notificationIntent = new Intent(this, CgmService.class);
    	PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
    	notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
    	final int CGM_ID = 1;
    	
    	// Make this a Foreground Service
    	startForeground(CGM_ID, notification);
    	
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();  
		
		sysObserver = new SystemObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.SYSTEM_URI, true, sysObserver);
        sysObserver.getCurrent();
    }

    @Override
	public int onStartCommand(Intent intent, int flags, int startId)
    {
    	final String FUNC_TAG = "onStartCommand";

		int command = intent.getIntExtra("CGMCommand", 0);
        
        Message msg;
        Bundle transmit;
        
        switch(command)
        {
    		case CGM_SERVICE_CMD_NULL:
    			Debug.i(TAG, FUNC_TAG, "CGM_SERVICE_CMD_NULL");
    			break;
    		case CGM_SERVICE_CMD_INIT:
    			Debug.i(TAG, FUNC_TAG, "CGM_SERVICE_CMD_INIT");
    			String intentName = intent.getStringExtra("driver_intent");
    			String driverName = intent.getStringExtra("driver_name");
    			
    			Debug.i(TAG, FUNC_TAG, "Intent: "+intentName+" Driver: "+driverName);
    			
    			bindToNewDriver(intentName, driverName);
    			break;
    		case CGM_SERVICE_CMD_CALIBRATE:
    			double BG = intent.getDoubleExtra("BG", 0);		
				int BGint = (int)BG;
				
				Debug.i(TAG, FUNC_TAG, "CGM_SERVICE_CMD_CALIBRATE > BG="+BG);

                //TODO:  should this just call sendMeterData directly

				msg = Message.obtain(null, CGM_SERVICE2DRIVER_CALIBRATE);
				transmit = new Bundle();
				
				transmit.putInt("calibration_value", BGint);
				msg.setData(transmit);
				
				msg.replyTo = driverReceiver;
				
				try {
					driverTransmitter.send(msg);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
    			break;
    		case CGM_SERVICE_CMD_DISCONNECT:
    			Message msg1 = Message.obtain(null, CGM_SERVICE2DRIVER_DISCONNECT, 0, 0);
    			
				try {
					driverTransmitter.send(msg1);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
    			break;
        	default:
       	        break;
        }
        return 0;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    } 
    
    @Override
	public IBinder onBind(Intent arg0)
    {
		return null;
	}

    /*****************************************************************************************************
     * Driver Message Handler
     ****************************************************************************************************/
    class driverMessageHandler extends Handler
    {
    	@Override
    	public void handleMessage(Message msg)
    	{
        	final String FUNC_TAG = "handleMessage";
    		
    		Bundle response;
    		switch(msg.what)
    		{
	    		case DRIVER2CGM_SERVICE_NEW_EGV:
	    			response = msg.getData();

	    			receiver.lastEgv.time = response.getLong("time", 0L);
					receiver.lastEgv.value = response.getDouble("cgmValue", 0.0);
					receiver.lastEgv.trend = response.getInt("trend", 0);
					receiver.lastEgv.state = response.getInt("cgm_state", 0);

					if(receiver != null)
						sendEgvData(receiver.lastEgv);
					
	    			break;
	    		case DRIVER2CGM_SERVICE_NEW_METER:
	    			response = msg.getData();

	    			receiver.lastMeter.value = response.getInt("cal_value");
	    			receiver.lastMeter.time = response.getLong("cal_time", 0L);

                    if(receiver != null)
                        sendMeterData(receiver.lastMeter);
	    			break;
    		}
    	}
    }

    private void sendEgvData(Record r)
    {
        long time = 0;
        double min = 40, max = 400;
        Cursor c = getContentResolver().query(Biometrics.CGM_URI, new String[]{"time"}, null, null, "time DESC LIMIT 1");
        if(c.moveToLast())
        {
            time = c.getLong(c.getColumnIndex("time"));
        }
        else
        {
            time = Supervisor.getSystemSeconds() - SupervisorService.IterationTimeSeconds;
        }
        c.close();

        if(r.time > time)
        {
            Cursor p = getContentResolver().query(Biometrics.CGM_DETAILS_URI, null, null, null, null);
            if (c.moveToLast())
            {
                min = p.getDouble(p.getColumnIndex("min_cgm"));
                max = p.getDouble(p.getColumnIndex("max_cgm"));
            }
            p.close();

            if((r.value < min || r.value > max) && (r.state == CGM.CGM_NORMAL))
            {
                r.state = CGM.CGM_DATA_ERROR;
            }

            ContentValues values = new ContentValues();
            values.put("time", r.time);
            values.put("diasState", DIAS_STATE);
            values.put("state", r.state);
            values.put("cgm", r.value);
            values.put("trend", r.trend);

            getContentResolver().insert(Biometrics.CGM_URI, values);
        }
    }

    private void sendMeterData(Record r)
    {
        long time = 0;
        Cursor c = getContentResolver().query(Biometrics.SMBG_URI, new String[]{"time"}, null, null, "time DESC LIMIT 1");
        if(c.moveToLast())
        {
            time = c.getLong(c.getColumnIndex("time"));
        }
        else
        {
            time = Supervisor.getSystemSeconds() - SupervisorService.IterationTimeSeconds;
        }
        c.close();

        if(r.time > time)
        {
            ContentValues values = new ContentValues();
            values.put("time", r.time);
            values.put("smbg", r.value);
            values.put("type", CGM.CALIBRATION);

            getContentResolver().insert(Biometrics.SMBG_URI, values);
        }
    }

    private void bindToNewDriver(String intentName, String driverName)
    {	
    	final String FUNC_TAG = "bindToNewDriver";
    	
    	// Setup device connection for this driver
    	driverConnection = new ServiceConnection()
        {
    		public void onServiceConnected(ComponentName className, IBinder service)
            {
    			driverTransmitter = new Messenger(service);
    			
				Message msg = Message.obtain(null, CGM_SERVICE2DRIVER_REGISTER);

				msg.replyTo = driverReceiver;

				try {
					driverTransmitter.send(msg);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
    		}
    		
    		public void onServiceDisconnected(ComponentName className)
            {
    		}
    	};
    	
    	Intent intent = new Intent(intentName);
    	bindService(intent, driverConnection, Context.BIND_AUTO_CREATE);
    }

    class SystemObserver extends ContentObserver
    {
        public SystemObserver(Handler handler)
        {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange)
        {
            this.onChange(selfChange, null);
        }

        public void onChange(boolean selfChange, Uri uri)
        {
            Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, new String[]{"diasState"}, null, null, "time DESC LIMIT 1");
            if(c.moveToLast())
            {
                DIAS_STATE = c.getInt(c.getColumnIndex("diasState"));
            }
            c.close();
        }

        public void getCurrent()
        {
            Cursor c = getContentResolver().query(Biometrics.SYSTEM_URI, new String[]{"diasState"}, null, null, "time DESC LIMIT 1");
            if(c.moveToLast())
            {
                DIAS_STATE = c.getInt(c.getColumnIndex("diasState"));
            }
            c.close();
        }
    }

    class Cgm
    {
        private Record lastEgv, lastMeter;

        public Cgm()
        {
            lastEgv = new Record();
            lastMeter = new Record();
        }
    }

    class Record
    {
        private long time;
        private double value;
        private int state, trend;

        public Record()
        {

        }
    }
}
