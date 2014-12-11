package edu.virginia.dtc.DiAsService;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.EditText;

public class MdiActivity extends Activity{
	
	public static final String TAG = "MDI_Activity";

	public static final int DIAS_UI_START_SAFETY_CLICK = 5;
	public static final int MDI_ACTIVITY_STATUS_SUCCESS = 0;

	private int state_change_command = DIAS_UI_START_SAFETY_CLICK;
	EditText mdi_injection;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		state_change_command = getIntent().getIntExtra("state_change_command", DIAS_UI_START_SAFETY_CLICK);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.dias_mdiscreen);
		mdi_injection = (EditText)findViewById(R.id.insulin_injected);
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
	}
	
	public void okClick(View view) 
	{
		Intent injection = new Intent("edu.virginia.dtc.intent.action.MDI_INJECTION");
		Double insulin_injected;

		if (mdi_injection.getText().toString().equalsIgnoreCase(""))
        {
			insulin_injected = 0.0;
		}
		else
        {
			insulin_injected = Double.parseDouble(mdi_injection.getText().toString());
		}

		injection.putExtra("insulin_injected", insulin_injected);
		injection.putExtra("state_change_command", state_change_command);
		injection.putExtra("status", MDI_ACTIVITY_STATUS_SUCCESS);
		sendBroadcast(injection);
		finish();
    }
}
