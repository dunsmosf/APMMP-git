package edu.virginia.dtc.BRMservice;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MDI_Activity extends Activity{
	
	public static final String TAG = "MDI_Activity";
	public static final boolean DEBUG = false;
	
	private Timer time;
	private int status;
	EditText mdi_injection;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.mdiscreen);
		debug_message(TAG, "OnCreate");
		mdi_injection = (EditText)findViewById(R.id.insulin_injected);
		initScreen();
	}
	
	@Override
	public void onStart()
	{
		super.onStart();
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		debug_message(TAG, "OnDestroy");
		finish();
	}
	
	@Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
    	super.onWindowFocusChanged(hasFocus);
    	
    	if(hasFocus)
    	{
    		debug_message(TAG, "M_HEIGHT: "+this.findViewById(R.id.alarmLayout).getHeight()+" M_WIDTH: "+this.findViewById(R.id.alarmLayout).getWidth());
    	}
    }
	
	private void initScreen()
	{
		time = new Timer();
		TimerTask timeout = new TimerTask()
		{
			public void run()
			{
				// If the screen times out then cancel - don't assume a bolus of zero
//				Intent injection = new Intent("edu.virginia.dtc.intent.action.MDI_INJECTION");
//				double insulin_injected = 0.0;
//				injection.putExtra("insulin_injected", insulin_injected);
//				sendBroadcast(injection);
				finish();
			}
		};
		time.schedule(timeout, 55*1000);			// 55 second timeout - refreshes every minute
	}
	
	/************************************************************************************
	* Action Listeners
	************************************************************************************/
	
	public void okClick(View view) 
	{
		Intent injection = new Intent("edu.virginia.dtc.intent.action.MDI_INJECTION");
		Double insulin_injected;
		if (mdi_injection.getText().toString().equalsIgnoreCase("")) {
			insulin_injected = 0.0;
		}
		else {
			insulin_injected = Double.parseDouble(mdi_injection.getText().toString());
		}
		injection.putExtra("insulin_injected", insulin_injected);
		sendBroadcast(injection);
		time.cancel();	
		finish();
    }
	
//	public void cancelClick(View view)
//	{
//		time.cancel();
//		finish();
//	}
	
	/************************************************************************************
	* Auxillary Functions
	************************************************************************************/
	
	public long getCurrentTimeSeconds() {
		long currentTimeSeconds = (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970 in UTC
		return currentTimeSeconds;
	}
	
	/************************************************************************************
	* Log Messaging Functions
	************************************************************************************/
	
	private void debug_message(String tag, String message) {
		if (DEBUG) {
			Log.i(tag, message);
		}
	}
	
	public void log_IO(String tag, String message) {
		Log.i(tag, message);
		
		/*
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", tag);
        i.putExtra("Status", message);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
        */
	}
}
