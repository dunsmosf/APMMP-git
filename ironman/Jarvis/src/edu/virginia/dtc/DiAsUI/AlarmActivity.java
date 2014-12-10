package edu.virginia.dtc.DiAsUI;

import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class AlarmActivity extends Activity{
	
	public static final String TAG = "AlarmActivity";
	public static final boolean DEBUG = true;
    
	public static final int DIAS_SERVICE_COMMAND_STOP_AUDIBLE_ALARM = 3;
	
	private static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
    public static final Uri CGM_URI = Uri.parse("content://"+ PROVIDER_NAME + "/cgm");
    public static final Uri SUBJECT_DATA_URI = Uri.parse("content://"+ PROVIDER_NAME + "/subjectdata");
    public static final Uri CF_PROFILE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/cfprofile");
    public static final Uri CR_PROFILE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/crprofile");
    public static final Uri BASAL_PROFILE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/basalprofile");
    public static final Uri MEAL_URI = Uri.parse("content://"+ PROVIDER_NAME + "/meal");
	
    // Confirmation dialogs
 	public static final int DIALOG_CLOSED_LOOP_NO_CGM = 254;	
 	public static final int DIALOG_CONFIRM_STOP = 256;
 	public static final int DIALOG_NEW_SUBJECT_CONFIRM = 257;	
 	public static final int DIALOG_PASSWORD = 258;
 	public static final int DIALOG_CONFIRM_CONFIG = 259;
 	public static final int DIALOG_CONFIRM_EXERCISE = 260;
 	public static final int DIALOG_CONFIRM_HYPO_TREATMENT = 261;
 	public static final int DIALOG_CONFIRM_CALIBRATION = 262;
 	public static final int DIALOG_BEGIN_CALIBRATION = 263;
    
	public static final int DIAS_STATE_STOPPED = 0;
	public static final int DIAS_STATE_OPEN_LOOP = 1;
	public static final int DIAS_STATE_CLOSED_LOOP = 2;
	public static final int DIAS_STATE_SAFETY_ONLY = 3;
	public static final int DIAS_STATE_SENSOR_ONLY = 4;
	
	// Alarm Activity IDs
    private static final int HYPO_ALARM = 0;
    private static final int NO_CGM_ALARM = 1;
	
	private int DIAS_STATE;
	
	private int type = HYPO_ALARM;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.ui_alarmscreen);
		setFinishOnTouchOutside(false);
		type = getIntent().getIntExtra("alarmType", HYPO_ALARM);
		
		Bundle b = new Bundle();
		
		switch(type)
		{
			case HYPO_ALARM:
				b.putString("description", "Hypo Alarm started!");
				Event.addEvent(this, Event.EVENT_SYSTEM_HYPO_ALARM, Event.makeJsonString(b), Event.SET_LOG);
				break;
			case NO_CGM_ALARM:
				b.putString("description", "No CGM Alarm started!");
				Event.addEvent(this, Event.EVENT_SYSTEM_NO_CGM_ALARM, Event.makeJsonString(b), Event.SET_LOG);
				break;
		}
		
		Debug.i(TAG, FUNC_TAG, "");
		
		setResult(RESULT_CANCELED);		//Set the result to cancelled unless we actually send a bolus with the UI
		
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
		final String FUNC_TAG = "onDestroy";
		
		super.onDestroy();
		Debug.i(TAG, FUNC_TAG, "");
		finish();
	}
	
	@Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
		final String FUNC_TAG = "onWindowFocusChanged";
		
    	super.onWindowFocusChanged(hasFocus);
    	
    	if(hasFocus)
    	{
    		Debug.i(TAG, FUNC_TAG, "M_HEIGHT: "+this.findViewById(R.id.alarmLayout).getHeight()+" M_WIDTH: "+this.findViewById(R.id.alarmLayout).getWidth());
    	}
    }
	
	@Override
    public boolean dispatchKeyEvent(KeyEvent event) {
		final String FUNC_TAG = "dispatchKeyEvent";
		
        int keyCode = event.getKeyCode();
        switch (keyCode) {
        
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            	//eat volume buttons
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

	private void initScreen()
	{
		Button alarmButton = (Button)(this.findViewById(R.id.alarmButton));
		
		switch(type)
		{
			case HYPO_ALARM:
				alarmButton.setText("STOP ALARM");
				alarmButton.setTextColor(0xFFFF0000);
				break;
			case NO_CGM_ALARM:
				alarmButton.setText("NO CGM SIGNAL\nTAP TO START OPEN LOOP");
				alarmButton.setTextColor(0xFF0000FF);
				break;
		}
	}
	
	/************************************************************************************
	* Action Listeners
	************************************************************************************/
	
	public void alarmClick(View view) 
	{
		final String FUNC_TAG = "alarmClick";
		
		Debug.i(TAG, FUNC_TAG, "Alarm Click!");

		Intent stopAlarmIntent = new Intent();
 		stopAlarmIntent.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
 		stopAlarmIntent.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_STOP_AUDIBLE_ALARM);
 		startService(stopAlarmIntent);
		
 		Bundle b = new Bundle();
 		
		switch(type)
		{
			case HYPO_ALARM:
				b.putString("description", "Hypo Alarm stopped!");
				Event.addEvent(this, Event.EVENT_SYSTEM_HYPO_ALARM, Event.makeJsonString(b), Event.SET_LOG);
				finish();
				break;
			case NO_CGM_ALARM:				
				b.putString("description", "No CGM Alarm stopped!");
				Event.addEvent(this, Event.EVENT_SYSTEM_NO_CGM_ALARM, Event.makeJsonString(b), Event.SET_LOG);
				//showDialog(DIALOG_CLOSED_LOOP_NO_CGM);
				finish();
				break;			
		}
    }
	
	// Dialog constructor function android uses when the showDialog(int id) function is called
    protected Dialog onCreateDialog(int id) 
    {
    	final String FUNC_TAG = "onCreateDialog";
    	
        Dialog dialog;
        switch(id) 
        {
	        case DIALOG_CLOSED_LOOP_NO_CGM:
				dialog = new Dialog(this);
	    		dialog.setContentView(R.layout.ui_smbg_dialog);
	        	dialog.setTitle("Start Open Loop Mode");
	        	
	    		TextView textViewSmbg = (TextView) dialog.findViewById(R.id.textViewSmbg);
	    		final EditText editTextSmbg = (EditText) dialog.findViewById(R.id.editTextSmbg);   
	    		textViewSmbg.setText("Enter BG to start Open Loop mode");
	    		
	    		// Listeners will set the determined loop mode and send it with the result when the activity is finished
	    		// DiAs Main will process this in onActivityResult() and set the mode accordingly
	    		((Button) dialog.findViewById(R.id.buttonSmbgOk)).setOnClickListener(new OnClickListener()
	    		{
					public void onClick(View v) 
					{
						if (Integer.parseInt(editTextSmbg.getText().toString()) > 80) 
						{
							Debug.i(TAG, FUNC_TAG, "BG in range, start Open Loop mode");
							
							Intent intent = new Intent();
							intent.putExtra("loopMode", DIAS_STATE_OPEN_LOOP);
							setResult(RESULT_OK, intent);
							finish();
						}
						else 
						{
							Debug.i(TAG, FUNC_TAG, "BG too low, do not start Open Loop mode");
							
							Intent intent = new Intent();
							intent.putExtra("loopMode", DIAS_STATE_STOPPED);
							setResult(RESULT_OK, intent);
							finish();					
						}
					}    	   		
	    	   	});
	    		
	    	   	((Button) dialog.findViewById(R.id.buttonSmbgCancel)).setOnClickListener(new OnClickListener(){
					public void onClick(View v) {
						Debug.i(TAG, FUNC_TAG, "BG not entered, do not start Open Loop mode");
						
						Intent intent = new Intent();
						intent.putExtra("loopMode", DIAS_STATE_STOPPED);
						setResult(RESULT_OK, intent);
						finish();
					}    	   		
	    	   	});
	    		break;   	
	        default:
	            dialog = null;
        }
        return dialog;
    }
	
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
	
	public void log_action(String tag, String message)
	{
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", tag);
        i.putExtra("Status", message);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
	}
}
