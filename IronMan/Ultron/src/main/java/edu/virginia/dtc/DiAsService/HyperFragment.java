package edu.virginia.dtc.DiAsService;

import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;

public class HyperFragment extends DialogFragment implements OnClickListener {
	
	private static final String TAG = "HyperFragment";
	
	private BroadcastReceiver HyperAlertDismissReceiver;
	
	public static final int DIAS_SERVICE_COMMAND_SET_HYPER_MUTE_DURATION = 7;
	
	public View mainView;
	public EditText smbg_input;
	
	public boolean mmolL;
	
	public HyperFragment() {
		
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	
		HyperAlertDismissReceiver = new BroadcastReceiver() {
     		@Override
            public void onReceive(Context context, Intent intent) 
     		{
     			Bundle b = new Bundle();
     			b.putString("description", "Hyper Alert Dialog dismissed");
     			Event.addEvent(context, Event.EVENT_AUTOMATICALLY_DISMISSED, Event.makeJsonString(b), Event.SET_LOG);
     			dismiss();
     		}
     	};		
     	getActivity().registerReceiver(HyperAlertDismissReceiver, new IntentFilter("edu.virginia.dtc.intent.action.DISMISS_HYPER_DIALOG"));
		
	}
	
	@Override
	public void onDestroy()
	{
		final String FUNC_TAG = "onDestroy";	
		super.onDestroy();
		
		getActivity().unregisterReceiver(HyperAlertDismissReceiver);
		
		Debug.i(TAG, FUNC_TAG, "OnDestroy");
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		
		mainView = inflater.inflate(R.layout.dias_hyper_dialog, container, false);
        
        int glucose_unit = Params.getInt(getActivity().getContentResolver(), "blood_glucose_display_units", 0);
		mmolL = (glucose_unit == 1);
		
		Button validate_hyper = (Button) mainView.findViewById(R.id.validate_hypo);
		
		smbg_input = (EditText) mainView.findViewById(R.id.smbg_input);
        if (mmolL){
			smbg_input.setHint("BG in mmol/L");
			smbg_input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		}
		
		validate_hyper.setOnClickListener(this);
		
		getDialog().setTitle("Hyper Alarm");
		getDialog().setCanceledOnTouchOutside(false);
		setCancelable(false);
		
		return mainView;
	}
	
	
	public void onClick(View v) {
		final String FUNC_TAG = "onClick";
		
		double smbg_value = -1;
		
		switch (v.getId()) {
			case R.id.validate_hypo:
				// Get SMBG value if any.
				if (!smbg_input.getText().toString().equals("")) {
					try {
						if (mmolL) {
							smbg_value = (double) Double.parseDouble(smbg_input.getText().toString())*CGM.MGDL_PER_MMOLL;
						}
						else {
							smbg_value = (double) Double.parseDouble(smbg_input.getText().toString());
						}
					} catch(NumberFormatException e){
						Debug.i(TAG, FUNC_TAG, "Error in SMBG value: "+e.getMessage());
					}
				}
				
				Debug.i(TAG, FUNC_TAG, "SMBG: "+smbg_value);

				long mealTime = -1;
				
				Cursor c = getActivity().getContentResolver().query(Biometrics.INSULIN_URI, new String[]{"deliv_time", "deliv_meal"}, "deliv_meal > 0.0", null, null);
				if(c != null)
				{
					if(c.moveToFirst())
					{
						//The first meal with deliv_meal insulin greater than zero
						mealTime = c.getLong(c.getColumnIndex("deliv_time"));
					}
						
					c.close();
				}
				
				Debug.i(TAG, FUNC_TAG, "Meal Time: "+mealTime);
				
				int muteDuration = 120;
				
				//Logic
				if(smbg_value > 250)
				{
					Debug.i(TAG, FUNC_TAG, "BG is greater than 250 mg/dL!");
					if((mealTime == -1) || itsTwoHoursPostMeal(mealTime))
					{
						// 30 Minute Mute (since we want to alert them more if they're higher)
						// Since they are higher than 250 and its 2 hours after their last meal
						muteDuration = 30;
						Debug.i(TAG, FUNC_TAG, "Subject ate more than 2 hours ago...");
					}
					else
						Debug.i(TAG, FUNC_TAG, "There is a recent meal!");
				}
				else
					Debug.i(TAG, FUNC_TAG, "BG is less than 250 mg/dL...");
				
				Debug.i(TAG, FUNC_TAG, "Mute Duration: "+muteDuration);
				
				muteHyperAlarm(muteDuration);
				
				ContentValues values = new ContentValues();
				values.put("time", getCurrentTimeSeconds());
				values.put("smbg", smbg_value);
				values.put("isCalibration", false);
				values.put("isHypo", false);
				try {
					getActivity().getContentResolver().insert(Biometrics.SMBG_URI, values);
				} catch (Exception e) {
					Debug.i(TAG, FUNC_TAG,"Save SMBG error:" + e.getMessage());
				}
				
				dismiss();
				getActivity().finish();
				break;
			case R.id.cancel_hypo:
				Debug.i(TAG, FUNC_TAG, "Cancel!");
				dismiss();
				getActivity().finish();
				break;
			default:break;
		}
	}
	
	private boolean itsTwoHoursPostMeal(long mealTime)
	{
		final String FUNC_TAG = "itsTwoHoursPostMeal";
		
		long twoHourPostMeal = mealTime + (120*60);
		
		Debug.i(TAG, FUNC_TAG, "2 Hour Post: "+twoHourPostMeal);
		Debug.i(TAG, FUNC_TAG, "System Time: "+System.currentTimeMillis()/1000);
		
		if((System.currentTimeMillis()/1000) > twoHourPostMeal)
			return true;
		else
			return false;
	}
	
	public void muteHyperAlarm(int muteDuration) {
		
		final String FUNC_TAG = "muteHyperAlarm";
		
		Intent intent1 = new Intent();
 	   	intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
 	   	intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_SET_HYPER_MUTE_DURATION);
 	   	intent1.putExtra("muteDuration", muteDuration);
 	   	getActivity().startService(intent1);
 	   	
	}
	
	public long getCurrentTimeSeconds() {
		long currentTimeSeconds = (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970 in UTC
		return currentTimeSeconds;
	}
}
