package edu.virginia.dtc.DiAsUI;

import java.text.DecimalFormat;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Params;

public class SmbgActivity extends Activity{
	
	public static final String TAG = "SmbgActivity";
	public static final boolean DEBUG = true;
	
    public static final int CGM_SERVICE_CMD_CALIBRATE = 1;
    
	public static final int DIAS_STATE_STOPPED = 0;
	public static final int DIAS_STATE_OPEN_LOOP = 1;
	public static final int DIAS_STATE_CLOSED_LOOP = 2;
	public static final int DIAS_STATE_SAFETY_ONLY = 3;
	public static final int DIAS_STATE_SENSOR_ONLY = 4;
	
	private int DIAS_STATE;
	
	private EditText smbgEntry;
	private CheckBox calibrationBox;
	private double smbgValue;
	private boolean smbgValid;
	private boolean standaloneInstalled;
	
	public boolean mmolL;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";
		
		Intent i = getIntent();
		standaloneInstalled = i.getBooleanExtra("standaloneInstalled", false);
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.smbgscreen);
		
		if (getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT){
			WindowManager.LayoutParams params = getWindow().getAttributes();
			params.gravity=Gravity.BOTTOM;
			
		}
		
		Debug.i(TAG, FUNC_TAG, "Standalone installed: "+standaloneInstalled);
		
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
		Debug.i(TAG, FUNC_TAG, "OnDestroy");
		finish();
	}
	
	@Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
		final String FUNC_TAG = "onWindowFocusChanged";
		
    	super.onWindowFocusChanged(hasFocus);
    	
    	if(hasFocus)
    	{
    		Debug.i(TAG, FUNC_TAG, "M_HEIGHT: "+this.findViewById(R.id.viewerLayout).getHeight()+" M_WIDTH: "+this.findViewById(R.id.viewerLayout).getWidth());
    	}
    }

	private void initScreen()
	{
		final String FUNC_TAG = "initScreen";
		
		mmolL = Params.getInt(getContentResolver(), "blood_glucose_display_units", 0) == 1;
		
		String glucose_unit = mmolL ? "mmol/L" : "mg/dL";
		Debug.i(TAG, FUNC_TAG, "Glucose Unit: "+ glucose_unit);
		
		calibrationBox = (CheckBox)this.findViewById(R.id.smbgCalibrationCheckbox);
		
		smbgEntry = (EditText) this.findViewById(R.id.editTextSmbgEntry);
		
		TextView smbgEntryUnitText = (TextView) this.findViewById(R.id.textViewSmbgScreenMessage1a);
		
		if (mmolL){
			
			double min_value = 40.0/CGM.MGDL_PER_MMOLL;
			double max_value = 400.0/CGM.MGDL_PER_MMOLL;
			
			DecimalFormat format = new DecimalFormat();
			format.setMaximumFractionDigits(1);
			format.setMinimumFractionDigits(1);
			
			smbgEntry.setHint(format.format(min_value)+"-"+format.format(max_value));
			smbgEntry.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
			
			smbgEntryUnitText.setText("mmol/L");
		}
		
		if (!standaloneInstalled) {
			this.findViewById(R.id.smbgIsCalibration).setVisibility(View.GONE);
			LinearLayout viewerLayout = (LinearLayout)this.findViewById(R.id.viewerLayout);
			viewerLayout.setWeightSum(4);
		}
		
		smbgEntry.setOnFocusChangeListener(new OnFocusChangeListener()
		{
			public void onFocusChange(View v, boolean hasFocus)
			{
				((TextView)v).setTextColor(Color.BLACK);
				
				if (((TextView)v).getText().toString().length() > 0) 
				{
					if (mmolL) {
						smbgValue = (double) Double.parseDouble(((TextView)v).getText().toString())*CGM.MGDL_PER_MMOLL;
					}
					else {
						smbgValue = (double) Double.parseDouble(((TextView)v).getText().toString());
					}
			    	Debug.i(TAG, FUNC_TAG, "BG="+smbgValue);
			    	
			    	smbgValid=false;
			    	if (smbgValue >= 40 && smbgValue <= 400) 
			    	{
			        	Debug.i(TAG, FUNC_TAG, "valid BG="+smbgValue);
			        	smbgValid=true;
			        	((TextView)v).setTextColor(Color.BLACK);
			    	}
			    	else 
			    	{                   			
						((TextView)v).setTextColor(Color.RED);            			
			    	}
				}
				else 
				{                   			
					((TextView)v).setTextColor(Color.RED);            			
				}
			}
		});
			    
		smbgEntry.setOnEditorActionListener(new OnEditorActionListener() 
		{
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) 
			{
				((TextView)v).setTextColor(Color.BLACK);
				
				if (v.getText().toString().length() > 0) 
				{
					if (mmolL) {
						smbgValue = (double) Double.parseDouble(((TextView)v).getText().toString())*CGM.MGDL_PER_MMOLL;
					}
					else {
						smbgValue = (double) Double.parseDouble(((TextView)v).getText().toString());
					}
					Debug.i(TAG, FUNC_TAG, "BG="+smbgValue);
					smbgValid=false;
					if (smbgValue >= 40 && smbgValue <= 400) 
					{
			    		Debug.i(TAG, FUNC_TAG, "valid BG="+smbgValue);
			    		smbgValid=true;
			    		v.setTextColor(Color.BLACK);
						return false;
					}    					
				}
				v.setTextColor(Color.RED);
				return true;
			}      
		});
	}
	
	/************************************************************************************
	* Action Listeners
	************************************************************************************/

	public void validateClick(View view) 
	{
		final String FUNC_TAG = "validateClick";
		boolean isCalibration = false;
		
		if (standaloneInstalled && calibrationBox.isChecked()) 
		{
			isCalibration = true;
			
	 		Intent intent1 = new Intent();
		    intent1.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
		    intent1.putExtra("CGMCommand", CGM_SERVICE_CMD_CALIBRATE);
		    intent1.putExtra("BG", (double)smbgValue);
	        startService(intent1);
	        
	        Debug.i(TAG, FUNC_TAG, "Sent Calibration intent to CgmService");
		}
		
		ContentValues values = new ContentValues();
		values.put("time", getCurrentTimeSeconds());
		values.put("smbg", smbgValue);
		values.put("isCalibration", isCalibration);
		values.put("isHypo", false);
		Debug.i(TAG, "IO_TEST", "SMBG: "+values.toString());
		try {
			getContentResolver().insert(Biometrics.SMBG_URI, values);
		} catch (Exception e) {
			Debug.e(TAG, "IO_TEST","Save SMBG error:" + e.getMessage());
		}
		
		finish();
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
