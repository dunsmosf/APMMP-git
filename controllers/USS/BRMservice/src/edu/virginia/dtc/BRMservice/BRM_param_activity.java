package edu.virginia.dtc.BRMservice;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.SysMan.TempBasal;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

public class BRM_param_activity extends Activity{
	
	public static final String TAG = "BRM_param_activity";
	public static final boolean DEBUG = true;
	
	Spinner percentSpinnerleft, percentSpinnerright,durationSpinner;
	int call_type;
   
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";
		
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.brm_param_screen);
		

		percentSpinnerleft = (Spinner)findViewById(R.id.basal_rate_modulator_params_spinner_left);
		percentSpinnerleft.setSelection(2);
		
		percentSpinnerright = (Spinner)findViewById(R.id.basal_rate_modulator_params_spinner_right);
		percentSpinnerright.setSelection(1);
		
		
		//durationSpinner = (Spinner)findViewById(R.id.basal_rate_modulator_duration_spinner);
		//durationSpinner.setSelection(1);
		
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

	public void confirmBRMparamClick(View view) 
	{
		final String FUNC_TAG = "confirmBRMparamClick";		
		setBRMparam();
	 	finish();
	    Toast.makeText(getApplicationContext(), "BRM saturation paramaters changed!", Toast.LENGTH_LONG).show();

    }

	public void cancelBRMparamClick(View view) 
	{
		final String FUNC_TAG = "cancelBRMparamClick";		
	 	finish();
	    Toast.makeText(getApplicationContext(), "BRM setting quit", Toast.LENGTH_LONG).show();

    }
	
	/************************************************************************************
	* Database access methods
	************************************************************************************/
	
	private void setBRMparam() 
	{
		final String FUNC_TAG = "setBRMparam";
				
	    ContentValues values = new ContentValues();
	    	    
	    long time = getCurrentTimeSeconds();
	    
	    double paramhigh = percentSpinnerleft.getSelectedItemPosition()+1;
	    double paramlow = percentSpinnerright.getSelectedItemPosition()+1;
	  //  long duration = (durationSpinner.getSelectedItemPosition()+1)*30*60;
	   
		Bundle b = new Bundle();
		try 
	    {
		    IOMain.db.addtoBrmDB(time, time, 0, paramhigh, paramlow, 0);

			b.putString("description", "BRM > parameter set, time= "+time);
 	    	Event.addEvent(getApplicationContext(), Event.EVENT_BRM_PARAM_CHANGED, Event.makeJsonString(b), Event.SET_LOG);
	    }
	    catch (Exception e) 
	    {
 	    	b.putString("description", "BRM > parameter set failed, time= "+time+", "+e.getMessage());
 	    	Event.addEvent(getApplicationContext(), Event.EVENT_BRM_PARAM_CHANGED, Event.makeJsonString(b), Event.SET_LOG);
	    	Debug.e("Error", FUNC_TAG,(e.getMessage() == null) ? "null" : e.getMessage());
	    }
		Debug.i(TAG, FUNC_TAG, "BG>180, the parameter="+paramhigh);
		Debug.i(TAG, FUNC_TAG, "BG<180, the parameter="+paramlow);
		//Debug.i(TAG, FUNC_TAG, "duration="+duration);

		//Toast.makeText(getApplicationContext(), "BG>180, the parameter="+paramhigh, Toast.LENGTH_SHORT).show();
		//Toast.makeText(getApplicationContext(), "BG<180, the parameter="+paramlow, Toast.LENGTH_SHORT).show();

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
