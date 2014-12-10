package edu.virginia.dtc.SSMservice;

import edu.virginia.dtc.SSMservice.Confirmations;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import edu.virginia.dtc.SysMan.Debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.View.OnFocusChangeListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

public class ConfirmationActivity extends Activity {

	public static final String TAG = "ConfirmationActivity";
	public static final boolean DEBUG = true;

    // Elements used in Interceptor Screens
	private Dialog bolusConfirmationDialog;
    private LayoutInflater inflater;
    private Context current_context;
    double bolusMax, insulinAmountToConfirm;
    boolean userChangeable;
    EditText bolusTotalText;
    TextView textView1, textView2, textView3;
    private Button increaseBolusButton, decreaseBolusButton;
    public static ScheduledExecutorService confirmationTimeoutScheduler = Executors.newScheduledThreadPool(2);
	public static ScheduledFuture<?> confirmationTimer;
	private long TIMEOUT_CONFIRMATION_MS = 30000;				// Constraint Service timeout is 30 seconds
	private MediaPlayer notificationMediaPlayer;
	private static final double AUDIO_VOLUME_MINIMUM_PERC = 1.00;

	//*********************************************
	// Button handlers
	//*********************************************	
	public void injectBolus(View view) {
    	final String FUNC_TAG = "injectBolus";
		if(confirmationTimer!= null)				//Cancel the Confirmation Service timeout routine if running
			confirmationTimer.cancel(true);
		confirmationComplete(Confirmations.CONFIRMATION_ACCEPT, insulinAmountToConfirm);
		finish();
	}
	
	public void cancelBolus(View view) {
    	final String FUNC_TAG = "cancelBolus";
		if(confirmationTimer!= null)				//Cancel the Confirmation Service timeout routine if running
			confirmationTimer.cancel(true);
		confirmationComplete(Confirmations.CONFIRMATION_CANCEL, 0.0);
		finish();
	}

	public void increaseBolus(View view) {
    	final String FUNC_TAG = "increaseBolus";
		EditText bolusTotalText = (EditText)bolusConfirmationDialog.findViewById(R.id.bolusTotal);
		insulinAmountToConfirm = insulinAmountToConfirm + 0.10;
		if (insulinAmountToConfirm > 10.0) {
			insulinAmountToConfirm = 10.0;
		}
		bolusTotalText.setText(String.format("%.2f U", (double)insulinAmountToConfirm));
		Debug.i(TAG, FUNC_TAG, "insulinAmountToConfirm="+insulinAmountToConfirm);
		startConstraintServiceTimer();
		bolusConfirmationDialog.show();
	}

	public void decreaseBolus(View view) {
    	final String FUNC_TAG = "decreaseBolus";
		EditText bolusTotalText = (EditText)bolusConfirmationDialog.findViewById(R.id.bolusTotal);
		insulinAmountToConfirm = insulinAmountToConfirm - 0.10;
		if (insulinAmountToConfirm < 0.0) {
			insulinAmountToConfirm = 0.0;
		}
		bolusTotalText.setText(String.format("%.2f U", (double)insulinAmountToConfirm));
		Debug.i(TAG, FUNC_TAG, "insulinAmountToConfirm="+insulinAmountToConfirm);
		startConstraintServiceTimer();
		bolusConfirmationDialog.show();
	}
	
	//*********************************************
	// Notify SSMservice of completion
	//*********************************************
	private void confirmationComplete(int status, double bolus) {
    	final String FUNC_TAG = "confirmationComplete";
    	Intent intentBroadcast = new Intent("edu.virginia.dtc.intent.action.CONFIRMATION_UPDATE_STATUS");
    	intentBroadcast.putExtra("ConfirmationStatus", status);
    	intentBroadcast.putExtra("bolus", bolus);
    	Debug.i(TAG, FUNC_TAG, "ConfirmationStatus=="+status+", bolus=="+bolus);
    	sendBroadcast(intentBroadcast);
	}
	
	//*********************************************
	// Activity handler overrides
	//*********************************************	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
    	final String FUNC_TAG = "onCreate";
		current_context = this;
		
		Intent intent = getIntent();
		Bundle paramBundle = intent.getExtras();
		insulinAmountToConfirm = (double)paramBundle.getDouble("insulinAmountToConfirm", 0.0);
		bolusMax = (double)paramBundle.getDouble("bolusMax", 0.0);
		userChangeable = (boolean)paramBundle.getBoolean("userChangeable", false);
		Debug.i(TAG, FUNC_TAG, "insulinAmountToConfirm="+insulinAmountToConfirm+", bolusMax="+bolusMax);


		// Set up the Confirmation display
		bolusConfirmationDialog = new Dialog(current_context); 
		bolusConfirmationDialog.setCanceledOnTouchOutside(false);
		bolusConfirmationDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE); 
		bolusConfirmationDialog.setContentView(getLayoutInflater().inflate(R.layout.bolus_dialog_layout , null));
		increaseBolusButton = (Button)bolusConfirmationDialog.findViewById(R.id.buttonIncrease);
		decreaseBolusButton = (Button)bolusConfirmationDialog.findViewById(R.id.buttonDecrease);
		if (!userChangeable) {
			increaseBolusButton.setEnabled(false);
			decreaseBolusButton.setEnabled(false);
		}
		textView1 = (TextView)bolusConfirmationDialog.findViewById(R.id.confirmationMsg1);
		textView2 = (TextView)bolusConfirmationDialog.findViewById(R.id.confirmationMsg2);
		textView3 = (TextView)bolusConfirmationDialog.findViewById(R.id.confirmationMsg3);
		textView1.setText(intent.getStringExtra("confirmationMsg1"));
		textView2.setText(intent.getStringExtra("confirmationMsg2"));
		textView3.setText(intent.getStringExtra("confirmationMsg3"));
		EditText bolusTotalText = (EditText)bolusConfirmationDialog.findViewById(R.id.bolusTotal);
		bolusTotalText.setText(String.format("%.2f U", insulinAmountToConfirm));
		startConstraintServiceTimer();
		bolusConfirmationDialog.show();
	}
	
	
	@Override
	public void onStart() {
		super.onStart();
    	final String FUNC_TAG = "onStart";
    	chime();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(confirmationTimer!= null)				//Cancel the Confirmation Service timeout routine if running
			confirmationTimer.cancel(true);
	}

	//*********************************************
	// Watchdog timer
	//*********************************************	
    private void startConstraintServiceTimer()
    {
		final String FUNC_TAG = "startConstraintServiceTimer";
    	Debug.i(TAG, FUNC_TAG,"Command Timer > Starting ConstraintService watchdog timer");
    	if(confirmationTimer!= null)				//Cancel the Constraint Service timeout routine if running
    		confirmationTimer.cancel(true);
    	confirmationTimer = confirmationTimeoutScheduler.schedule(confirmationTimeOut, TIMEOUT_CONFIRMATION_MS, TimeUnit.MILLISECONDS);  // 30 second timeout
    }
    
    private void haltConstraintServiceTimer()
    {
		final String FUNC_TAG = "haltConstraintServiceTimer";
    	Debug.i(TAG, FUNC_TAG,"Command Timer > Halting ConstraintService watchdog timer");
    	if(confirmationTimer!= null)				//Cancel the Constraint Service timeout routine if running
    		confirmationTimer.cancel(true);
    }
    
    public Runnable confirmationTimeOut = new Runnable()
	{
		public void run() 
		{	
			final String FUNC_TAG = "confirmationTimeOut";
    		Debug.i(TAG, FUNC_TAG, "Confirmation timed out");
    		confirmationComplete(Confirmations.CONFIRMATION_TIMED_OUT, 0.0);
    		finish();
		}
	};
	
	//*********************************************
	// Utility methods
	//*********************************************	
	private void chime() {
		final String FUNC_TAG = "chime";
		Debug.i(TAG, FUNC_TAG, "Prompt user to confirm bolus > chime.wav");
		setMinAudioVolume(AUDIO_VOLUME_MINIMUM_PERC);    						
		notificationMediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.chime);
		notificationMediaPlayer.setLooping(false);
		notificationMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		notificationMediaPlayer.start();
		notificationMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() 
		{
			public void onCompletion(MediaPlayer mp) 
			{
    			notificationMediaPlayer.release();
    			notificationMediaPlayer = null;
			}
		});
	}
	
	public void setMinAudioVolume(double minVolumePerc) {
		AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		double currentVolumePerc = max / (double)am.getStreamVolume(AudioManager.STREAM_MUSIC);
		int volume = (int)(Math.max(minVolumePerc, currentVolumePerc) * max);
		am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_VIBRATE); 
	}
	
	public long getCurrentTimeSeconds() {
		return (long) (System.currentTimeMillis() / 1000); // Seconds since
	}

	public void log_action(String tag, String message)
	{
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
        i.putExtra("Service", tag);
        i.putExtra("Status", message);
        i.putExtra("time", (long)getCurrentTimeSeconds());
        sendBroadcast(i);
	}
	
}