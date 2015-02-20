package edu.virginia.dtc.DiAsSetup;

import java.util.ArrayList;

import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.Tvector.Tvector;

import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnFocusChangeListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

public class USSBRMFragment extends ProfileFragment {
    public static final String TAG = "SafetyFragment";
	
	public static final int TITLE = 0;
    public static final int INSTRUCTIONS = 1;
    public static final int STARTTIME = 2;
    public static final int ENDTIME = 3;
    
    public static final int MIN_TIME_INTERVAL = 10; // mins
    
    public EditText editTextStartHour;
    public EditText editTextStartMinute;
    public EditText editTextEndHour;
    public EditText editTextEndMinute;
    public boolean subjectSafetyStartHourValid;
    public boolean subjectSafetyStartMinuteValid;
    public boolean subjectSafetyEndHourValid;
    public boolean subjectSafetyEndMinuteValid;
    public int subjectSafetyStartHour;
    public int subjectSafetyStartMinute;
    public int subjectSafetyEndHour;
    public int subjectSafetyEndMinute;
    
    public USSBRMFragment(DiAsSetup1 main){
    	this.main = main;
    }

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final String FUNC_TAG = "onCreateView";
		
		view = inflater.inflate(R.layout.setupussbrm, container, false);
		strings = main.getResources().getStringArray(R.array.USSBRM);
		((TextView) view.findViewById(R.id.textViewTitle)).setText(strings[TITLE]);
		((TextView) view.findViewById(R.id.textViewInstructions)).setText(strings[INSTRUCTIONS]);
		((TextView) view.findViewById(R.id.safetyStartTime)).setText(strings[STARTTIME]);
		((TextView) view.findViewById(R.id.safetyEndTime)).setText(strings[ENDTIME]);

		editTextStartHour = (EditText) view.findViewById(R.id.editTextStartHour);
		editTextStartMinute = (EditText) view.findViewById(R.id.editTextStartMinute);
		editTextEndHour = (EditText) view.findViewById(R.id.editTextEndHour);
		editTextEndMinute = (EditText) view.findViewById(R.id.editTextEndMinute);
		instructions = (TextView) view.findViewById(R.id.textViewInstructions);
		
		profileLinear = (LinearLayout) view.findViewById(R.id.profileLinear);
		
		// Build a list of Strings from the Basal Tvector
		profileList = new ArrayList<SelectBox>();
		buildProfile();
		displayProfile();
		profileLineSelected = 0;			// No profile line is currently selected

		// Edit Hour
		editTextStartHour.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				((TextView) v).setTextColor(Color.WHITE);
				if (hasFocus) {
					instructions.setText("Enter the start hour in the range 0 to 23");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						subjectSafetyStartHourValid = false;
						if (Integer.parseInt(((TextView) v).getText().toString()) >= 0 && Integer.parseInt(((TextView) v).getText().toString()) <= 23) {
							Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeHour=" + ((TextView) v).getText().toString());
							subjectSafetyStartHour = Integer.parseInt(((TextView) v).getText().toString());
							subjectSafetyStartHourValid = true;
							((TextView) v).setTextColor(Color.WHITE);
						} else {
							((TextView) v).setTextColor(Color.RED);
						}
					} else {
						((TextView) v).setTextColor(Color.RED);
					}
				}
			}
		});
		editTextStartHour.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				((TextView) v).setTextColor(Color.WHITE);
				subjectSafetyStartHourValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Integer.parseInt(v.getText().toString()) >= 0 && Integer.parseInt(v.getText().toString()) <= 23) {
						Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeHour=" + v.getText().toString());
						subjectSafetyStartHour = Integer.parseInt(v.getText().toString());
						subjectSafetyStartHourValid = true;
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});

		editTextStartMinute.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				((TextView) v).setTextColor(Color.WHITE);
				if (hasFocus) {
					instructions.setText("Enter the start minute in the range 0 to 59");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						subjectSafetyStartMinuteValid = false;
						if (Integer.parseInt(((TextView) v).getText().toString()) >= 0 && Integer.parseInt(((TextView) v).getText().toString()) <= 59) {
							Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeMinute=" + ((TextView) v).getText().toString());
							subjectSafetyStartMinute = Integer.parseInt(((TextView) v).getText().toString());
							subjectSafetyStartMinuteValid = true;
							((TextView) v).setTextColor(Color.WHITE);
						} else {
							((TextView) v).setTextColor(Color.RED);
						}
					} else {
						((TextView) v).setTextColor(Color.RED);
					}
				}
			}
		});
		editTextStartMinute.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				((TextView) v).setTextColor(Color.WHITE);
				subjectSafetyStartMinuteValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Integer.parseInt(v.getText().toString()) >= 0 && Integer.parseInt(v.getText().toString()) <= 59) {
						Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeMinute=" + v.getText().toString());
						subjectSafetyStartMinute = Integer.parseInt(v.getText().toString());
						subjectSafetyStartMinuteValid = true;
						v.setTextColor(Color.WHITE);
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});		
		// Edit Hour
		editTextEndHour.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				((TextView) v).setTextColor(Color.WHITE);
				if (hasFocus) {
					instructions.setText("Enter the end hour in the range 0 to 23");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						subjectSafetyEndHourValid = false;
						if (Integer.parseInt(((TextView) v).getText().toString()) >= 0 && Integer.parseInt(((TextView) v).getText().toString()) <= 23) {
							Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeHour=" + ((TextView) v).getText().toString());
							subjectSafetyEndHour = Integer.parseInt(((TextView) v).getText().toString());
							subjectSafetyEndHourValid = true;
							((TextView) v).setTextColor(Color.WHITE);
						} else {
							((TextView) v).setTextColor(Color.RED);
						}
					} else {
						((TextView) v).setTextColor(Color.RED);
					}
				}
			}
		});
		editTextEndHour.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				((TextView) v).setTextColor(Color.WHITE);
				subjectSafetyEndHourValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Integer.parseInt(v.getText().toString()) >= 0 && Integer.parseInt(v.getText().toString()) <= 23) {
						Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeHour=" + v.getText().toString());
						subjectSafetyEndHour = Integer.parseInt(v.getText().toString());
						subjectSafetyEndHourValid = true;
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});

		editTextEndMinute.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				((TextView) v).setTextColor(Color.WHITE);
				if (hasFocus) {
					instructions.setText("Enter the end minute in the range 0 to 59");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						subjectSafetyEndMinuteValid = false;
						if (Integer.parseInt(((TextView) v).getText().toString()) >= 0 && Integer.parseInt(((TextView) v).getText().toString()) <= 59) {
							Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeMinute=" + ((TextView) v).getText().toString());
							subjectSafetyEndMinute = Integer.parseInt(((TextView) v).getText().toString());
							subjectSafetyEndMinuteValid = true;
							((TextView) v).setTextColor(Color.WHITE);
						} else {
							((TextView) v).setTextColor(Color.RED);
						}
					} else {
						((TextView) v).setTextColor(Color.RED);
					}
				}
			}
		});
		editTextEndMinute.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				((TextView) v).setTextColor(Color.WHITE);
				subjectSafetyEndMinuteValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Integer.parseInt(v.getText().toString()) >= 0 && Integer.parseInt(v.getText().toString()) <= 59) {
						Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeMinute=" + v.getText().toString());
						subjectSafetyEndMinute = Integer.parseInt(v.getText().toString());
						subjectSafetyEndMinuteValid = true;
						v.setTextColor(Color.WHITE);
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});		
		main.updateDisplay();
		return view;
	}
	
	public void updateDisplay(){
		
	}

	@Override
	public void buildProfile() {
		int ii, t, t2;
		profileList.clear();
		if (DiAsSetup1.local_sd.subjectTimeRangeValid) {
			for (ii = 0; ii < DiAsSetup1.local_sd.subjectSafety.count(); ii++) {
				t = DiAsSetup1.local_sd.subjectSafety.get_time(ii).intValue();
				t2 = DiAsSetup1.local_sd.subjectSafety.get_end_time(ii).intValue();
				String s = new String(pad(t / 60) + ":" + pad(t % 60) + "  to  " + pad(t2 / 60) + ":" + pad(t2 % 60));
				profileList.add(new SelectBox(main, this, ii, s));
			}
		}
		
		DiAsSetup1.db.writeDb(DiAsSetup1.local_sd);
	}

	@Override
	public void addItemToProfile(View view) {
		final String FUNC_TAG = "addItemToProfile";
		
		if (DiAsSetup1.local_sd.subjectSafety.count() < DiAsSetup1.local_sd.subjectSafety.capacity() && subjectSafetyStartHourValid && subjectSafetyStartMinuteValid && subjectSafetyEndHourValid && subjectSafetyEndMinuteValid) {
			int startMinutes = subjectSafetyStartHour * 60 + subjectSafetyStartMinute;
			int endMinutes = subjectSafetyEndHour * 60 + subjectSafetyEndMinute;
			int start = startMinutes, end = endMinutes;
			if (Math.abs(end - start) < 10){
				Toast.makeText(main, "Start and end times must be at least " + MIN_TIME_INTERVAL + " minutes apart", Toast.LENGTH_LONG).show();
				return;
			}
			// check overlaps and gaps between ranges
			if (subjectSafetyStartHour > subjectSafetyEndHour){ // handle case of range over midnight
				end += 24*60;
			}
			for (int i = 0; i < DiAsSetup1.local_sd.subjectSafety.count(); i++){
				int t = DiAsSetup1.local_sd.subjectSafety.get_time(i).intValue();
				int t2 = DiAsSetup1.local_sd.subjectSafety.get_end_time(i).intValue();
				if (subjectSafetyStartHour > subjectSafetyEndHour){ // handle case of range over midnight
					t2 += 24*60;
				}
				if ((t <= start && start <= t2) || (t <= end && end <= t2)){
					Toast.makeText(main, "Ranges cannot overlap", Toast.LENGTH_LONG).show();
					return;			
				}
				if (Math.abs(start - t2) < MIN_TIME_INTERVAL || Math.abs(t - end) < MIN_TIME_INTERVAL){
					Toast.makeText(main, "Ranges must have a gap of at least " + MIN_TIME_INTERVAL + " minutes between other ranges", Toast.LENGTH_LONG).show();
					return;
				}
			}
			DiAsSetup1.local_sd.subjectSafety.put_time_range_with_replace(startMinutes, endMinutes);
			DiAsSetup1.local_sd.subjectTimeRangeValid = true;
			Debug.i(TAG, FUNC_TAG,"addItemToProfile, startMinutes=" + startMinutes + ", endMinutes=" + endMinutes);
			buildProfile();
			displayProfile();
		} else {
			Debug.i(TAG, FUNC_TAG,"addItemToProfile failed: fields not valid " + subjectSafetyStartHourValid + " " + subjectSafetyStartMinuteValid + " " + subjectSafetyEndHourValid + " " + subjectSafetyEndMinuteValid);
		}
		main.updateDisplay();
	}
	
	public boolean existsOverlap(Tvector data, int start, int end){
		for (int i = 0; i < data.count(); i++){
			int t = data.get_time(i).intValue();
			int t2 = data.get_end_time(i).intValue();
			if ((t <= start && start <= t2) || (t <= end && end <= t2)){
				return true;			
			}
		}
		return false;
	}

	public boolean checkGapsBetweenRanges(Tvector data, int start, int end){
		for (int i = 0; i < data.count(); i++){
			int t = data.get_time(i).intValue();
			int t2 = data.get_end_time(i).intValue();
			if ((t <= start && start <= t2) || (t <= end && end <= t2)){
				return true;			
			}
		}		
		return false;
	}

	@Override
	public void removeItemFromProfile(View view) {
		final String FUNC_TAG = "removeItemFromProfile";
		
		if (DiAsSetup1.local_sd.subjectSafety.count() > 0 && profileLineSelected < DiAsSetup1.local_sd.subjectSafety.count()) {
			DiAsSetup1.local_sd.subjectSafety.remove(profileLineSelected);
			if (DiAsSetup1.local_sd.subjectSafety.count() == 0) {
				DiAsSetup1.local_sd.subjectTimeRangeValid = false;
			}
			buildProfile();
			displayProfile();
		} else {
			Debug.i(TAG, FUNC_TAG,"removeItemFromProfile failed");
		}
		main.updateDisplay();
	}

	@Override
	public void clearConfirm() {
		DiAsSetup1.local_sd.subjectSafety.init();
		profileLineSelected = 0;
		DiAsSetup1.local_sd.subjectTimeRangeValid = false;
		buildProfile();
		displayProfile();
		main.updateDisplay();
	}
}
