package edu.virginia.dtc.DiAsService;

import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import edu.virginia.dtc.DiAsService.R;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;

public class HypoFragment extends DialogFragment implements OnClickListener {
	
	private static final String TAG = "HypoFragment";
	
	private BroadcastReceiver HypoAlertDismissReceiver;
	
	public static final int DIAS_SERVICE_COMMAND_SET_HYPO_MUTE_DURATION = 6;
	
	private boolean triggeredByHypoAlert;
	
	public View mainView;
	public EditText smbg_input;
	public EditText carbs_input;
	
	public RadioGroup radio_treatment;
	public RadioButton treatment;
	public RadioButton no_treatment;
	
	public LinearLayout carbs_layout;
	
	public boolean mmolL;
	
	public HypoFragment() {
		
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
	
		HypoAlertDismissReceiver = new BroadcastReceiver() {
        	//final String FUNC_TAG = "AlertDismissReceiver";        	
     		@Override
            public void onReceive(Context context, Intent intent) 
     		{
     			Bundle b = new Bundle();
     			b.putString("description", "Hypo Alert Dialog dismissed");
     			Event.addEvent(context, Event.EVENT_AUTOMATICALLY_DISMISSED, Event.makeJsonString(b), Event.SET_LOG);
     			dismiss();
     		}
     	};		
     	getActivity().registerReceiver(HypoAlertDismissReceiver, new IntentFilter("edu.virginia.dtc.intent.action.DISMISS_HYPO_DIALOG"));
		
		//setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Light);
		
	}
	
	@Override
	public void onDestroy()
	{
		final String FUNC_TAG = "onDestroy";	
		super.onDestroy();
		
		getActivity().unregisterReceiver(HypoAlertDismissReceiver);
		
		Debug.i(TAG, FUNC_TAG, "OnDestroy");
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		
		mainView = inflater.inflate(R.layout.dias_hypo_dialog, container, false);
        
        int glucose_unit = Params.getInt(getActivity().getContentResolver(), "blood_glucose_display_units", 0);
		mmolL = (glucose_unit == 1);
		
		Bundle args = getArguments();
		triggeredByHypoAlert = args.getBoolean("triggeredByHypoAlert");
		
		Button validate_hypo = (Button) mainView.findViewById(R.id.validate_hypo);
		Button cancel_hypo = (Button) mainView.findViewById(R.id.cancel_hypo);
		
		radio_treatment = (RadioGroup) mainView.findViewById(R.id.radio_treated);
		TextView treatment_text = (TextView) mainView.findViewById(R.id.treat_text);
		treatment = (RadioButton) mainView.findViewById(R.id.treatment);
		no_treatment = (RadioButton) mainView.findViewById(R.id.no_treatment);
		
		smbg_input = (EditText) mainView.findViewById(R.id.smbg_input);
        if (mmolL){
			smbg_input.setHint("BG in mmol/L");
			smbg_input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
		}
		carbs_input = (EditText) mainView.findViewById(R.id.carbs_input);
		carbs_layout = (LinearLayout) mainView.findViewById(R.id.carbs_layout);
		
		validate_hypo.setOnClickListener(this);
		
		if (triggeredByHypoAlert) {
			treatment.setOnClickListener(this);
			no_treatment.setOnClickListener(this);
			
			cancel_hypo.setVisibility(View.GONE);
			carbs_layout.setVisibility(View.GONE);
		}
		else {
			cancel_hypo.setOnClickListener(this);
			
			treatment_text.setVisibility(View.GONE);
			radio_treatment.setVisibility(View.GONE);
		}
		
		getDialog().setTitle("Hypo Treatment");
		getDialog().setCanceledOnTouchOutside(false);
		//getDialog().setCancelable(false);
		setCancelable(false);
		
		return mainView;
	}
	
	
	public void onClick(View v) {
		final String FUNC_TAG = "onClick";
		
		int carbs_value = -1;
		double smbg_value = -1;
		boolean didTreat;
		
		switch (v.getId()) {
			case R.id.validate_hypo:
				if (!treatment.isChecked() && !no_treatment.isChecked() && triggeredByHypoAlert) {
					Toast.makeText(getActivity(), "Have you treated yourself for hypoglycemia?", Toast.LENGTH_SHORT).show();
				}
				else {
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
					// Get Carbs value if any.
					if (!carbs_input.getText().toString().equals("")) {
						try {
							carbs_value = Integer.parseInt(carbs_input.getText().toString());
						} catch(NumberFormatException e){
							Debug.i(TAG, FUNC_TAG, "Error in Carbs value: "+e.getMessage());
						}
					}
					// Get treatment info.
					if (triggeredByHypoAlert)  {
						didTreat = treatment.isChecked();
					}
					else {
						didTreat = true;
					}
					
					
					if (didTreat) {
						Debug.i(TAG, FUNC_TAG, "Adding Hypo event...");
				   		Bundle b = new Bundle();
						b.putString("description", "Hypo treatment button pressed");
						Event.addEvent(getActivity().getApplicationContext(), Event.EVENT_SYSTEM_HYPO_TREATMENT, Event.makeJsonString(b), Event.SET_LOG);
			   		}
					
					Debug.i(TAG, FUNC_TAG, "New SMBG row to add with 'treatment'="+didTreat+", 'carbs'="+carbs_value+", 'SMBG'="+smbg_value);
					
					ContentValues values = new ContentValues();
					values.put("time", getCurrentTimeSeconds());
					values.put("smbg", smbg_value);
					values.put("isCalibration", false);
					values.put("isHypo", true);
					values.put("didTreat", didTreat);
					if (carbs_value > -1) {
						values.put("carbs", carbs_value);
					}
					try {
						getActivity().getContentResolver().insert(Biometrics.SMBG_URI, values);
					} catch (Exception e) {
						Debug.i(TAG, FUNC_TAG,"Save SMBG error:" + e.getMessage());
					}
					
					if (triggeredByHypoAlert) {
						muteHypoAlarm(smbg_value, didTreat);
					}
					
					dismiss();
					getActivity().finish();
				}
				break;
			case R.id.treatment:
				//Toast.makeText(getActivity(), "Yes - Treatment", Toast.LENGTH_SHORT).show();
				carbs_layout.setVisibility(View.VISIBLE);
				break;
			
			case R.id.no_treatment:
				//Toast.makeText(getActivity(), "No - No Treatment", Toast.LENGTH_SHORT).show();
				carbs_layout.setVisibility(View.GONE);
				carbs_input.setText("");
				
				break;
			case R.id.cancel_hypo:
				dismiss();
				getActivity().finish();
				break;
			default:break;
		}
	}
	
	
	public int getHypoMuteDuration(double bg_value, boolean treated) {
		final String FUNC_TAG = "getHypoMuteDuration";
		
		int bgThreshold1 = Params.getInt(getActivity().getContentResolver(), "bg_threshold_1", 70);
		int bgThreshold2 = Params.getInt(getActivity().getContentResolver(), "bg_threshold_2", 90);
		
		int muteDurationTreated = Params.getInt(getActivity().getContentResolver(), "hypo_mute_treated", 15);
		int muteDurationNoBg = Params.getInt(getActivity().getContentResolver(), "hypo_mute_no_bg", 5);
		int muteDurationLowBg = Params.getInt(getActivity().getContentResolver(), "hypo_mute_low_bg", 5);
		int muteDurationMiddleBg = Params.getInt(getActivity().getContentResolver(), "hypo_mute_middle_bg", 15);
		int muteDurationHighBg = Params.getInt(getActivity().getContentResolver(), "hypo_mute_high_bg", 30);
		
		// Switch BG threshold values if 1>2
		if (bgThreshold1 > bgThreshold2) {
			int tmp;
			tmp = bgThreshold1;
			bgThreshold1 = bgThreshold2;
			bgThreshold2 = tmp;
		}
		
		int muteDuration = 0; // in minutes
		
		// Mute duration definition 
		if (treated) {
			muteDuration = muteDurationTreated;
		}
		else {
			if (bg_value <= -1) {
				muteDuration = muteDurationNoBg;
			}
			else if ((-1 < bg_value) && (bg_value < bgThreshold1)){
				muteDuration = muteDurationLowBg;
			}
			else if ((bgThreshold1 <= bg_value) && (bg_value < bgThreshold2)) {
				muteDuration = muteDurationMiddleBg;
			}
			else if (bgThreshold2 <= bg_value) {
				muteDuration = muteDurationHighBg;
			}
		}
		
		Debug.i(TAG, FUNC_TAG, "Treated= "+treated+", BG value="+bg_value+", mute duration="+Integer.toString(muteDuration));
		
		return muteDuration;
	}
	
	
	public void muteHypoAlarm(double bg_value, boolean treated) {
		
		final String FUNC_TAG = "muteHypoAlarm";
		
		int muteDuration = getHypoMuteDuration(bg_value, treated);
		
		Debug.i(TAG, FUNC_TAG, "Hypo Alarm Mute duration="+Integer.toString(muteDuration));
		
		Intent intent1 = new Intent();
 	   	intent1.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.DiAsService.DiAsService");
 	   	intent1.putExtra("DiAsCommand", DIAS_SERVICE_COMMAND_SET_HYPO_MUTE_DURATION);
 	   	intent1.putExtra("muteDuration", muteDuration);
 	   	getActivity().startService(intent1);
 	   	
	}
	
	public long getCurrentTimeSeconds() {
		long currentTimeSeconds = (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970 in UTC
		return currentTimeSeconds;
	}
}
