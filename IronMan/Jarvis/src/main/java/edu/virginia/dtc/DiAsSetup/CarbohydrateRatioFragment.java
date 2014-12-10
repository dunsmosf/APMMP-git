package edu.virginia.dtc.DiAsSetup;

import java.util.ArrayList;

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
import edu.virginia.dtc.DiAsUI.R;
import edu.virginia.dtc.SysMan.Debug;

public class CarbohydrateRatioFragment extends ProfileFragment {
	public static String TAG = "CarbohydrateRatioFragment";

	// Temporary storage locations for CR
	public int subjectCRHour, subjectCRMinute;
	public double subjectCRValue;
	public boolean subjectCRHourValid = false;
	public boolean subjectCRMinuteValid = false;
	public boolean subjectCRValueValid = false;

	public CarbohydrateRatioFragment(DiAsSetup1 main) {
		this.main = main;
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final String FUNC_TAG = "onCreateView";
		
		view = super.onCreateView(inflater, container, savedInstanceState, R.array.CR);

		// Build a list of Strings from the CR Tvector
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
						subjectCRHourValid = false;
						if (Integer.parseInt(((TextView) v).getText().toString()) >= 0 && Integer.parseInt(((TextView) v).getText().toString()) <= 23) {
							Debug.i(TAG, FUNC_TAG,"valid subjectCRTimeHour=" + ((TextView) v).getText().toString());
							subjectCRHour = Integer.parseInt(((TextView) v).getText().toString());
							subjectCRHourValid = true;
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
				boolean retValue = false;
				subjectCRHourValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Integer.parseInt(v.getText().toString()) >= 0 && Integer.parseInt(v.getText().toString()) <= 23) {
						Debug.i(TAG, FUNC_TAG,"valid subjectCRTimeHour=" + v.getText().toString());
						subjectCRHour = Integer.parseInt(v.getText().toString());
						subjectCRHourValid = true;
						retValue = false;
					}
				}
//				else {
//					v.setTextColor(Color.RED);
//					retValue = true;
//				}
				return retValue;
			}
		});

		editTextTimeMinute.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				((TextView) v).setTextColor(Color.WHITE);
				if (hasFocus) {
					instructions.setText("Enter the minute in the range 0 to 59");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						subjectCRMinuteValid = false;
						if (Integer.parseInt(((TextView) v).getText().toString()) >= 0 && Integer.parseInt(((TextView) v).getText().toString()) <= 59) {
							Debug.i(TAG, FUNC_TAG,"valid subjectCRTimeMinute=" + ((TextView) v).getText().toString());
							subjectCRMinute = Integer.parseInt(((TextView) v).getText().toString());
							subjectCRMinuteValid = true;
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
				boolean retValue = false;
				subjectCRMinuteValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Integer.parseInt(v.getText().toString()) >= 0 && Integer.parseInt(v.getText().toString()) <= 59) {
						Debug.i(TAG, FUNC_TAG,"valid subjectCRTimeMinute=" + v.getText().toString());
						subjectCRMinute = Integer.parseInt(v.getText().toString());
						subjectCRMinuteValid = true;
						v.setTextColor(Color.WHITE);
						retValue = false;
					}
				}
//				else {
//					v.setTextColor(Color.RED);
//					retValue = true;
//				}
				return retValue;
			}
		});
		
		editTextValue.setFilters(new InputFilter[] {new InputFilter.LengthFilter(3)});
		
		editTextValue.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				((TextView) v).setTextColor(Color.WHITE);
				if (hasFocus) {
					instructions.setText("Enter a carbohydrate to insulin ratio in the range of 1 to 100 g/U");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						subjectCRValueValid = false;
						if (Double.parseDouble(((TextView) v).getText().toString()) >= 1 && Double.parseDouble(((TextView) v).getText().toString()) <= 100) {
							Debug.i(TAG, FUNC_TAG,"valid subjectCR=" + ((TextView) v).getText().toString());
							subjectCRValue = Double.parseDouble(((TextView) v).getText().toString());
							subjectCRValueValid = true;
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
				subjectCRValueValid = false;
				boolean retValue = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Double.parseDouble(v.getText().toString()) >= 1 && Double.parseDouble(v.getText().toString()) <= 100) {
						Debug.i(TAG, FUNC_TAG,"valid subjectCR=" + v.getText().toString());
						subjectCRValue = Double.parseDouble(v.getText().toString());
						subjectCRValueValid = true;
						v.setTextColor(Color.WHITE);
						retValue = false;
					}
				}
//				else {
//					v.setTextColor(Color.RED);
//					retValue = true;
//				}
				return retValue;
			}
		});
		main.updateDisplay();
		return view;
	}

	public void updateDisplay() {
	}

	// Build the profile list from the CR Tvector
	public void buildProfile() {
		int ii, jj, t;
		profileList.clear();
		if (DiAsSetup1.local_sd.subjectCRValid) {
			for (ii = 0; ii < DiAsSetup1.local_sd.subjectCR.count(); ii++) {
				t = DiAsSetup1.local_sd.subjectCR.get_time(ii).intValue();
				String s = new String(pad(t / 60) + ":" + pad(t % 60) + "  " + DiAsSetup1.local_sd.subjectCR.get_value(ii) + " " + strings[UNIT]);
				profileList.add(new SelectBox(main, this, ii, s));
			}
		}
		
		DiAsSetup1.db.writeDb(DiAsSetup1.local_sd);
	}

	// Add item to profile
	public void addItemToProfile(View view) {
		final String FUNC_TAG = "addItemToProfile";
		
		if (DiAsSetup1.local_sd.subjectCR.count() < DiAsSetup1.local_sd.subjectCR.capacity() && subjectCRHourValid && subjectCRMinuteValid && subjectCRValueValid) {
			int minutes = subjectCRHour * 60 + subjectCRMinute;
			DiAsSetup1.local_sd.subjectCR.put_with_replace(minutes, subjectCRValue);
			DiAsSetup1.local_sd.subjectCRValid = true;
			Debug.i(TAG, FUNC_TAG,"addItemToProfile, minutes=" + minutes + ", CR=" + subjectCRValue);
			buildProfile();
			displayProfile();
		} else {
			Debug.i(TAG, FUNC_TAG,"addItemToProfile failed: fields not valid " + subjectCRHourValid + " " + subjectCRMinuteValid + " " + subjectCRValueValid);
		}
		main.updateDisplay();
	}

	// Remove item from profile
	public void removeItemFromProfile(View view) {
		final String FUNC_TAG = "removeItemFromProfile";
		
		if (DiAsSetup1.local_sd.subjectCR.count() > 0 && profileLineSelected < DiAsSetup1.local_sd.subjectCR.count()) {
			DiAsSetup1.local_sd.subjectCR.remove(profileLineSelected);
			if (DiAsSetup1.local_sd.subjectCR.count() == 0) {
				DiAsSetup1.local_sd.subjectCRValid = false;
			}
			buildProfile();
			displayProfile();
		} else {
			Debug.i(TAG, FUNC_TAG,"removeItemFromProfile failed");
		}
		main.updateDisplay();
	}

	public void clearConfirm() {
		DiAsSetup1.local_sd.subjectCR.init();
		profileLineSelected = 0;
		DiAsSetup1.local_sd.subjectCRValid = false;
		buildProfile();
		displayProfile();
		main.updateDisplay();
	}
}
