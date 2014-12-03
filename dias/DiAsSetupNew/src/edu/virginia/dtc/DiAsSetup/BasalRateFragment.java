package edu.virginia.dtc.DiAsSetup;

import java.util.ArrayList;

import edu.virginia.dtc.SysMan.Debug;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class BasalRateFragment extends ProfileFragment {
	public static String TAG = "BasalRateFragment";

	// Temporary storage locations for Basal
	public int subjectBasalHour, subjectBasalMinute;
	public double subjectBasalValue;
	public boolean subjectBasalHourValid = false;
	public boolean subjectBasalMinuteValid = false;
	public boolean subjectBasalValueValid = false;

	public BasalRateFragment(DiAsSetup1 main) {
		this.main = main;
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final String FUNC_TAG = "onCreateView";
		
		view = super.onCreateView(inflater, container, savedInstanceState, R.array.Basal);

		// Build a list of Strings from the Basal Tvector
		profileList = new ArrayList<SelectBox>();
		buildProfile();
		displayProfile();
		profileLineSelected = 0;			// No profile line is currently selected

		// Edit Hour
		editTextTimeHour.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				((TextView) v).setTextColor(Color.WHITE);
				if (hasFocus) {
					instructions.setText("Enter the hour in the range 0 to 23");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						subjectBasalHourValid = false;
						if (Integer.parseInt(((TextView) v).getText().toString()) >= 0 && Integer.parseInt(((TextView) v).getText().toString()) <= 23) {
							Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeHour=" + ((TextView) v).getText().toString());
							subjectBasalHour = Integer.parseInt(((TextView) v).getText().toString());
							subjectBasalHourValid = true;
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
		editTextTimeHour.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				((TextView) v).setTextColor(Color.WHITE);
				subjectBasalHourValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Integer.parseInt(v.getText().toString()) >= 0 && Integer.parseInt(v.getText().toString()) <= 23) {
						Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeHour=" + v.getText().toString());
						subjectBasalHour = Integer.parseInt(v.getText().toString());
						subjectBasalHourValid = true;
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});

		editTextTimeMinute.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				((TextView) v).setTextColor(Color.WHITE);
				if (hasFocus) {
					instructions.setText("Enter the minute in the range 0 to 59");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						subjectBasalMinuteValid = false;
						if (Integer.parseInt(((TextView) v).getText().toString()) >= 0 && Integer.parseInt(((TextView) v).getText().toString()) <= 59) {
							Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeMinute=" + ((TextView) v).getText().toString());
							subjectBasalMinute = Integer.parseInt(((TextView) v).getText().toString());
							subjectBasalMinuteValid = true;
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
		editTextTimeMinute.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				((TextView) v).setTextColor(Color.WHITE);
				subjectBasalMinuteValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Integer.parseInt(v.getText().toString()) >= 0 && Integer.parseInt(v.getText().toString()) <= 59) {
						Debug.i(TAG, FUNC_TAG,"valid subjectBasalTimeMinute=" + v.getText().toString());
						subjectBasalMinute = Integer.parseInt(v.getText().toString());
						subjectBasalMinuteValid = true;
						v.setTextColor(Color.WHITE);
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});

		editTextValue.setFilters(new InputFilter[] {new InputFilter.LengthFilter(5)});
		
		editTextValue.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				((TextView) v).setTextColor(Color.WHITE);
				if (hasFocus) {
					instructions.setText("Enter a basal rate in the range of 0 to 3 U/hour");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						subjectBasalValueValid = false;
						if (Double.parseDouble(((TextView) v).getText().toString()) >= 0 && Double.parseDouble(((TextView) v).getText().toString()) <= 3) {
							Debug.i(TAG, FUNC_TAG,"valid subjectBasal=" + ((TextView) v).getText().toString());
							subjectBasalValue = Double.parseDouble(((TextView) v).getText().toString());
							subjectBasalValueValid = true;
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
		editTextValue.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				((TextView) v).setTextColor(Color.WHITE);
				subjectBasalValueValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Double.parseDouble(v.getText().toString()) >= 0 && Double.parseDouble(v.getText().toString()) <= 3) {
						Debug.i(TAG, FUNC_TAG,"valid subjectBasal=" + v.getText().toString());
						subjectBasalValue = Double.parseDouble(v.getText().toString());
						subjectBasalValueValid = true;
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

	public void updateDisplay() {
	}

	// Build the profile list from the Basal Tvector
	public void buildProfile() {
		int ii, jj, t;
		profileList.clear();
		if (DiAsSetup1.local_sd.subjectBasalValid) {
			for (ii = 0; ii < DiAsSetup1.local_sd.subjectBasal.count(); ii++) {
				t = DiAsSetup1.local_sd.subjectBasal.get_time(ii).intValue();
				String s = new String(pad(t / 60) + ":" + pad(t % 60) + "  " + DiAsSetup1.local_sd.subjectBasal.get_value(ii) + " " + strings[UNIT]);
				profileList.add(new SelectBox(main, this, ii, s));
			}
		}
		
		DiAsSetup1.db.writeDb(DiAsSetup1.local_sd);
	}

	// Add item to profile
	public void addItemToProfile(View view) {
		final String FUNC_TAG = "addItemToProfile";
		
		if (DiAsSetup1.local_sd.subjectBasal.count() < DiAsSetup1.local_sd.subjectBasal.capacity() && subjectBasalHourValid && subjectBasalMinuteValid && subjectBasalValueValid) {
			int minutes = subjectBasalHour * 60 + subjectBasalMinute;
			DiAsSetup1.local_sd.subjectBasal.put_with_replace(minutes, subjectBasalValue);
			DiAsSetup1.local_sd.subjectBasalValid = true;
			Debug.i(TAG, FUNC_TAG,"addItemToProfile, minutes=" + minutes + ", Basal=" + subjectBasalValue);
			buildProfile();
			displayProfile();
		} else {
			Debug.i(TAG, FUNC_TAG,"addItemToProfile failed: fields not valid " + subjectBasalHourValid + " " + subjectBasalMinuteValid + " " + subjectBasalValueValid);
		}
		main.updateDisplay();
	}

	// Remove item from profile
	public void removeItemFromProfile(View view) {
		final String FUNC_TAG = "removeItemFromProfile";
		
		if (DiAsSetup1.local_sd.subjectBasal.count() > 0 && profileLineSelected < DiAsSetup1.local_sd.subjectBasal.count()) {
			DiAsSetup1.local_sd.subjectBasal.remove(profileLineSelected);
			if (DiAsSetup1.local_sd.subjectBasal.count() == 0) {
				DiAsSetup1.local_sd.subjectBasalValid = false;
			}
			buildProfile();
			displayProfile();
		} else {
			Debug.i(TAG, FUNC_TAG,"removeItemFromProfile failed");
		}
		main.updateDisplay();
	}

	public void clearConfirm() {
		DiAsSetup1.local_sd.subjectBasal.init();
		profileLineSelected = 0;
		DiAsSetup1.local_sd.subjectBasalValid = false;
		buildProfile();
		displayProfile();
		main.updateDisplay();
	}
}
