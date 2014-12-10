package edu.virginia.dtc.DiAsService;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import edu.virginia.dtc.DiAsService.R;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;

public class StartupActivity extends FragmentActivity {

	private static final String TAG = "StartupActivity";
	
	private TextView m, t;
	private String message, title;
	private int click;
	
	private static final double AUDIO_VOLUME_MINIMUM_PERC = 1.00;
	private static final int ALARM_WAKE_LOCK_FLAGS = PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE;
	private MediaPlayer alarmMediaPlayer;
	private WakeLock alarmWakeLock;
	private BroadcastReceiver AlertDismissReceiver;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";
	
		Debug.i(TAG, FUNC_TAG, "OnCreate");
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dias_startup_activity);
		
		this.setFinishOnTouchOutside(false);
		
		message = getIntent().getStringExtra("message");
		title = getIntent().getStringExtra("title");
		click = getIntent().getIntExtra("click", -1);
		
		Debug.i(TAG, FUNC_TAG, "Message: "+message+ " Title: "+title);
		
		alarmMediaPlayer = null;
		alarmWakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(ALARM_WAKE_LOCK_FLAGS, TAG);
		
		AlertDismissReceiver = new BroadcastReceiver() {
        	@Override
            public void onReceive(Context context, Intent intent) 
     		{
     			finish();
     		}
     	};
        registerReceiver(AlertDismissReceiver, new IntentFilter("edu.virginia.dtc.intent.action.DISMISS_STARTUP_ACTIVITY"));
		
		initScreen();
	}
	
	@Override
	public void onStart()
	{
		final String FUNC_TAG = "onStart";
		super.onStart();		
		Debug.i(TAG, FUNC_TAG, "OnStart");
	}
	
	@Override
	public void onDestroy()
	{
		final String FUNC_TAG = "onDestroy";	
		super.onDestroy();
		Debug.i(TAG, FUNC_TAG, "OnDestroy");
		stopAlarm();
		finish();
	}
	
	@Override
    protected void onRestart() 
    {
    	final String FUNC_TAG = "onRestart";
        super.onRestart();
        Debug.i(TAG, FUNC_TAG, "");
    }
    
    @Override
    protected void onResume() 
    {
    	final String FUNC_TAG = "onResume";
        super.onResume();        
        Debug.i(TAG, FUNC_TAG, "");
    }
    
    @Override
    protected void onPause() {
    	final String FUNC_TAG = "onPause";
        super.onPause();
        Debug.i(TAG, FUNC_TAG, "");
    }
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		final String FUNC_TAG = "dispatchKeyEvent";
	
	    int keyCode = event.getKeyCode();
	    switch (keyCode) {
	    
	        case KeyEvent.KEYCODE_VOLUME_UP:
	        case KeyEvent.KEYCODE_VOLUME_DOWN:
	        	Debug.i(TAG, FUNC_TAG, "Volume event! Setting volume to MAX!");
	        	
	        	AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
				am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_VIBRATE);
	        	
	            return false;
	        default:
	            return super.dispatchKeyEvent(event);
	    }
	}
	
	private void initScreen()
	{
		if(click == -1)
		{
			//Make Yes and No invisible and show OK button
			((LinearLayout)this.findViewById(R.id.yesNoLayout)).setVisibility(LinearLayout.GONE);
			((LinearLayout)this.findViewById(R.id.okLayout)).setVisibility(LinearLayout.VISIBLE);
		}
		
		m = (TextView)this.findViewById(R.id.alertText);
		t = (TextView)this.findViewById(R.id.titleText);
		
		m.setText(message);
		t.setText(title);
		
		startAlarm();
	}
	
	public void startAlarm(boolean loop)
	{	
		final String FUNC_TAG = "startAlarm";
    	Debug.i(TAG, FUNC_TAG, "Looping: "+loop);
    	
		alarmMediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.alertalarm);
		setMinAudioVolume(AUDIO_VOLUME_MINIMUM_PERC);    						
		alarmMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		alarmMediaPlayer.setLooping(loop);
		alarmMediaPlayer.start();
		updateWakeLock();
	}
	
	public void startAlarm()
	{
		startAlarm(false);
	}
	
	public void stopAlarm()
	{	
		final String FUNC_TAG = "stopAlarm";
    	Debug.i(TAG, FUNC_TAG, "");
		
		if (alarmMediaPlayer != null) {
			if (alarmMediaPlayer.isPlaying()) 
			{
				alarmMediaPlayer.stop();
	       	   	alarmMediaPlayer.release();
			}
	   	   	alarmMediaPlayer = null;
	   	   	updateWakeLock();
		}
	}
	
	private void clearFlag()
	{
		//Clear the flag
		ContentValues dv = new ContentValues();
		dv.put("ask_at_startup", 0);
		getContentResolver().update(Biometrics.HARDWARE_CONFIGURATION_URI, dv, null, null);
	}
	
	/************************************************************************************
	* Action Listeners
	************************************************************************************/
	
	public void yesClick(View view) 
	{	
		final Intent intent1 = new Intent();
		intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
		intent1.putExtra("DiAsCommand", click);
		startService(intent1);
		
		clearFlag();
		
		stopAlarm();
		finish();
	}
	
	public void noClick(View view) 
	{	
		clearFlag();
		
		stopAlarm();
		finish();
	}
	
	public void okClick(View view)
	{
		clearFlag();
		
		stopAlarm();
		finish();
	}
	
	/************************************************************************************
	* Auxillary Functions
	************************************************************************************/
   	
    public long getCurrentTimeSeconds()
    {
		long currentTimeSeconds = (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970 in UTC
		return currentTimeSeconds;
	}
    
    @Override
    public void onBackPressed() {
        // Prevent window for closing when hitting the back button.
    }
    
    public void setMinAudioVolume(double minVolumePerc)
    {	
    	final String FUNC_TAG = "SetMinAudioVolume";
    	Debug.i(TAG, FUNC_TAG, ""+ minVolumePerc);
    	
		AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		double currentVolumePerc = max / (double)am.getStreamVolume(AudioManager.STREAM_MUSIC);
		int volume = (int)(Math.max(minVolumePerc, currentVolumePerc) * max);
		am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_VIBRATE); 
	}
    
    public void updateWakeLock()
    {	
    	final String FUNC_TAG = "updateWakeLock";
    	Debug.i(TAG, FUNC_TAG, "");
    	
		boolean alarmPlaying = false;
		alarmPlaying |= alarmMediaPlayer != null && alarmMediaPlayer.isPlaying();
		if (alarmPlaying)
			alarmWakeLock.acquire();
		else if (alarmWakeLock.isHeld())
			alarmWakeLock.release();
	}
}
