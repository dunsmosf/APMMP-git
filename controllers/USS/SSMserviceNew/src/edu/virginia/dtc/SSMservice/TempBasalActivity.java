package edu.virginia.dtc.SSMservice;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.TempBasal;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Spinner;
import android.widget.Toast;

public class TempBasalActivity extends Activity{
	
	public static final String TAG = "TempBasalActivity_SSM";
	public static final boolean DEBUG = true;
	
	Spinner percentSpinner, durationSpinner;
	int call_type;
    	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";
		
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.temporary_basal_screen);
		

		percentSpinner = (Spinner)findViewById(R.id.temporary_basal_percentage_spinner);
		//percentSpinner.setSelection(8);
		percentSpinner.setSelection(0);
		durationSpinner = (Spinner)findViewById(R.id.temporary_basal_duration_spinner);
		durationSpinner.setSelection(1);
		
		if (getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT){
			WindowManager.LayoutParams params = getWindow().getAttributes();
			params.gravity=Gravity.BOTTOM;			
		}		
		Debug.i(TAG, FUNC_TAG, "");		
		setResult(RESULT_CANCELED);		
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
		
	}
	
	/************************************************************************************
	* Action Listeners
	************************************************************************************/

	public void confirmTemporaryBasalClick(View view) 
	{
		final String FUNC_TAG = "confirmTemporaryBasalClick";		
		startTempBasal();
	 	finish();
    }

	public void cancelTemporaryBasalClick(View view) 
	{
		final String FUNC_TAG = "cancelTemporaryBasalClick";		
	 	finish();
    }
	
	/************************************************************************************
	* Database access methods
	************************************************************************************/
	
	private void startTempBasal() 
	{
		final String FUNC_TAG = "startTempBasal";
				
	    ContentValues values = new ContentValues();
	    	    
	    long time = getCurrentTimeSeconds();
	    values.put("start_time", time);
	    values.put("scheduled_end_time", time + (durationSpinner.getSelectedItemPosition()+1)*30*60);
	    values.put("actual_end_time", 0);
	    values.put("percent_of_profile_basal_rate", 10*percentSpinner.getSelectedItemPosition());
	    values.put("status_code", TempBasal.TEMP_BASAL_RUNNING);	 
	    values.put("owner", TempBasal.TEMP_BASAL_OWNER_SSMSERVICE);
		Bundle b = new Bundle();
		long length = (durationSpinner.getSelectedItemPosition()+1)*30;
		double percentage=10*percentSpinner.getSelectedItemPosition();
		try 
	    {
	    	Uri uri = getContentResolver().insert(Biometrics.TEMP_BASAL_URI, values);
 	    	b.putString("description", "SSM > startTempBasal, start_time= "+time+"   TBR > schedules length="+length+"minutes"+"  TBR > percentage="+percentage);
 	    	Event.addEvent(getApplicationContext(), Event.EVENT_TEMP_BASAL_STARTED, Event.makeJsonString(b), Event.SET_LOG);
	    }
	    catch (Exception e) 
	    {
 	    	b.putString("description", "SSM > startTempBasal failed, start_time= "+time+", "+e.getMessage());
 	    	Event.addEvent(getApplicationContext(), Event.EVENT_TEMP_BASAL_STARTED, Event.makeJsonString(b), Event.SET_LOG);
	    	Debug.e("Error", FUNC_TAG,(e.getMessage() == null) ? "null" : e.getMessage());
	    }
		Toast.makeText(getApplicationContext(), FUNC_TAG+", start_time="+values.getAsLong("start_time"), Toast.LENGTH_SHORT).show();
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
