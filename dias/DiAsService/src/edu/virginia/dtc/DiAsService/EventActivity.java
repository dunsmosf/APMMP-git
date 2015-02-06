package edu.virginia.dtc.DiAsService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;

public class EventActivity extends FragmentActivity {

	private static final String TAG = "EventActivity";
	
	private TextView m, t;
	
	private String message, title;
	private boolean chime, vibe, alarm;
	private int settings = Event.SET_LOG;
	int id, code;
	private BroadcastReceiver AlertDismissReceiver;
	public static final int DIAS_SERVICE_COMMAND_SET_HYPO_MUTE_DURATION = 6;
	public static final int DIAS_SERVICE_COMMAND_SET_HYPER_MUTE_DURATION = 7;
	
	public static final int defaultHypoMuteDuration = 5;
	public static final int defaultHyperMuteDuration = 30;
	
	// ALARM
	private static final int ALARM_WAKE_LOCK_FLAGS = PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE;
	private MediaPlayer alarmMediaPlayer;
	private Vibrator vibrator;
	private WakeLock alarmWakeLock;
	
	//TODO: Add SET_HIDDEN_AUDIBLE, SET_HIDDEN_VIBE and SET_HIDDEN_AUDIBLE_VIBE handling???
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";
	
		Debug.i(TAG, FUNC_TAG, "OnCreate");
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.event_activity);
		
		this.setFinishOnTouchOutside(false);
		
		message = getIntent().getStringExtra("message");
		title = getIntent().getStringExtra("title");
		settings = getIntent().getIntExtra("settings", Event.SET_LOG);
		code = getIntent().getIntExtra("code", -1);
		id = getIntent().getIntExtra("id", -1);
		
		alarmMediaPlayer = null;
		vibrator = null;
		alarmWakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(ALARM_WAKE_LOCK_FLAGS, TAG);
		chime = false;
		vibe = false;
		alarm = false;
		
		AudioManager am=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
		am.setStreamVolume(AudioManager.STREAM_MUSIC, (am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/Params.getInt(getContentResolver(), "hypo_alarm_volume", 1)), AudioManager.FLAG_VIBRATE);
		
		switch(settings)
		{
			case Event.SET_LOG:
				chime = vibe = alarm = false;
				break;
			case Event.SET_POPUP:
				chime = vibe = alarm = false;
				break;
			case Event.SET_POPUP_AUDIBLE:
				chime = true;
				vibe = alarm = false;
				break;
			case Event.SET_POPUP_AUDIBLE_VIBE:
				alarm = false;
				chime = vibe = true;
				break;
			case Event.SET_POPUP_VIBE:
				chime = alarm = false;
				vibe = true;
				break;
			case Event.SET_POPUP_AUDIBLE_ALARM:
			case Event.SET_POPUP_AUDIBLE_HYPO:
				chime = false;
				alarm = vibe = true;
				break;
			case Event.SET_CUSTOM:
				break;
		}
		
		if (code == Event.EVENT_SYSTEM_HYPO_ALARM) {
			title = "Hypo Alert";
		}
		
		Debug.i(TAG, FUNC_TAG, "Message: "+message+ " Title: "+title);
		
		initScreen();
		
		// Triggering alarm
		Debug.i(TAG, FUNC_TAG, "Event Code '"+code+"' in IMPORTANT list: "+ isImportant(code));
		AlertDismissReceiver = new BroadcastReceiver() {
        	//final String FUNC_TAG = "AlertDismissReceiver";        	
     		@Override
            public void onReceive(Context context, Intent intent) 
     		{
     			int prev_code = 0;
     			
     			Cursor c = getContentResolver().query(Biometrics.EVENT_URI, null, "popup_displayed=1", null, null);
     			if(c != null) {
     				if(c.moveToLast()) {
     					prev_code = c.getInt(c.getColumnIndex("code"));
     					Debug.i(TAG, FUNC_TAG, "Previous Event code: "+prev_code);      
     				}
     			}
     			c.close();
     			
     			if (! isImportant(code) || (prev_code == Event.EVENT_SYSTEM_HYPO_ALARM &&  code == Event.EVENT_SYSTEM_HYPO_ALARM)) {
         			// Dismiss Alert Activity unless it is an important Event
     				// Log a new Event marking popup dismissal
         			Bundle b = new Bundle();
         			b.putString("description", "Event "+id+" dismissed");
         			Event.addEvent(getApplicationContext(), Event.EVENT_AUTOMATICALLY_DISMISSED, Event.makeJsonString(b), Event.SET_LOG);
         			finish();
                }
     		}
     	};
        registerReceiver(AlertDismissReceiver, new IntentFilter("edu.virginia.dtc.intent.action.DISMISS_EVENT_ACTIVITY"));
        
        if (code == Event.EVENT_SYSTEM_HYPO_ALARM) {
	        Intent dismissActivityBroadcast = new Intent("edu.virginia.dtc.intent.action.DISMISS_HYPO_DIALOG");
			sendBroadcast(dismissActivityBroadcast);
        }
        
        if(code == Event.EVENT_SYSTEM_HYPER_ALARM) {
        	Intent dismissHyper = new Intent("edu.virginia.dtc.intent.action.DISMISS_HYPER_DIALOG");
        	sendBroadcast(dismissHyper);
        }
        
        if (code == Event.EVENT_UI_HYPO_BUTTON_PRESSED) {
        	HypoFragment dialog = new HypoFragment();
        	Bundle b = new Bundle();
        	b.putBoolean("triggeredByHypoAlert", false);
        	dialog.setArguments(b);
			dialog.show(getFragmentManager(), "HypoFragment Tag");
        }
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
		stopAlarm();
		unregisterReceiver(AlertDismissReceiver);
		Debug.i(TAG, FUNC_TAG, "OnDestroy");
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
		m = (TextView)this.findViewById(R.id.alertText);
		t = (TextView)this.findViewById(R.id.titleText);
		
		m.setText(message);
		t.setText(title);
		
		boolean params_sound_alarms = Params.getBoolean(getContentResolver(), "audible_alarms", true);
		int params_sound_alarm_threshold = Params.getInt(getContentResolver(), "audible_alarms_threshold", Event.AUDIBLE_ALARM_ALL_EVENTS);
		boolean params_vibrate_alarms = Params.getBoolean(getContentResolver(), "vibrate_alarms", true);
		
		if (params_sound_alarm_threshold > Event.AUDIBLE_ALARM_HYPO_ONLY || code == Event.EVENT_SYSTEM_HYPO_ALARM) {
			
			if (alarm && params_sound_alarms)
				startAlarm();
			
			if (chime && params_sound_alarms)
				chime();
		}
		
		if (vibe && params_vibrate_alarms)
			startVibe();
	}
	
	/************************************************************************************
	* CHIME, VIBE and ALARM functions
	************************************************************************************/
	
	private void chime()
	{
		MediaPlayer mMediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.chime);
   	 	mMediaPlayer.start();
	}
	
	private void startVibe()
	{
		vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		long[] pattern = {0,500,500,500,500,500};
		if (alarm) {
			pattern[1] = pattern[3] = pattern[5] = 2000;	
		}
		vibrator.vibrate(pattern, -1);
	}
	
	public void stopVibe() {
		final String FUNC_TAG = "stopVibe";
    	Debug.i(TAG, FUNC_TAG, "");
		
		if (vibrator != null) {
			vibrator.cancel();
		}
	}
	
	public void startAlarm(boolean loop)
	{	
		final String FUNC_TAG = "startAlarm";
    	Debug.i(TAG, FUNC_TAG, "Looping: "+loop);
    	
		alarmMediaPlayer = MediaPlayer.create(getApplicationContext(), getAlertSoundFileId());
		//setMinAudioVolume(Params.getDouble(getContentResolver(), "hypo_alarm_volume", 1.00));    						
		alarmMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		alarmMediaPlayer.setLooping(loop);
		alarmMediaPlayer.start();
		updateWakeLock();
	}
	
	public void startAlarm()
	{
		startAlarm(false);
	}
	
	public void stopAlarm() {	
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
	
	public int getAlertSoundFileId() {
		int soundFileId;
		switch(settings){
			case Event.SET_POPUP_AUDIBLE_HYPO:
				soundFileId = R.raw.hypoalarm;
				break;
			case Event.SET_POPUP_AUDIBLE_ALARM:
				soundFileId = R.raw.alertalarm;
				break;
			default:
				soundFileId = R.raw.alertalarm;
				break;
		}
		return soundFileId;
	}
	
	/************************************************************************************
	* Action Listeners
	************************************************************************************/
	
	public void dismissClick(View view) 
	{	
		// Stop audible alarm if playing
		if (alarm) {
			stopAlarm();
		}
		if (vibe) {
			stopVibe();
		}
		
		// Triggers the Hypo Alert fragment
		if (code == Event.EVENT_SYSTEM_HYPO_ALARM) {
			HypoFragment dialog = new HypoFragment();
			Bundle b = new Bundle();
        	b.putBoolean("triggeredByHypoAlert", true);
        	dialog.setArguments(b);
			dialog.show(getFragmentManager(), "HypoFragment Tag");
			muteHypoAlarm(defaultHypoMuteDuration);
		}
		else if(code == Event.EVENT_SYSTEM_HYPER_ALARM) {
			HyperFragment dialog = new HyperFragment();
			dialog.show(getFragmentManager(), "HyperFragment Tag");
			muteHyperAlarm(defaultHyperMuteDuration);
		}
		else {
			// Log a new Event marking popup dismissal
			Bundle b = new Bundle();
			b.putString("description", "Event "+id+" dismissed");
			Event.addEvent(getApplicationContext(), Event.EVENT_USER_RESPONSE, Event.makeJsonString(b), Event.SET_LOG);
			finish();
		}
	}
	
	/************************************************************************************
	* Auxillary Functions
	************************************************************************************/
	
	public boolean isImportant(int code)
	{
		boolean result = false;
		for(int i : Event.IMPORTANT_EVENT_CODES){
			if (code == i) {
				result = true;
			}
		}
		return result;
	}
	
   	public void muteHypoAlarm(int muteDuration)
    {
   		final String FUNC_TAG = "hypoTreatmentConfirm";
    	Debug.i(TAG, FUNC_TAG, "hypoFlagTime");
 	   	
 	   	Intent intent1 = new Intent();
 	   	intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
 	   	intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_SET_HYPO_MUTE_DURATION);
 	   	intent1.putExtra("muteDuration", muteDuration);
 	   	startService(intent1);    	
		//finish();
    }
   	
   	public void muteHyperAlarm(int muteDuration)
   	{
   		final String FUNC_TAG = "hyperConfirm";
   		Debug.i(TAG, FUNC_TAG, "hyperFlagTime");
   		
   		Intent intent1 = new Intent();
 	   	intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
 	   	intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_SET_HYPER_MUTE_DURATION);
 	   	intent1.putExtra("muteDuration", muteDuration);
 	   	startService(intent1); 
   	}
   	
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
