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
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Params;

public class CorrectionFactorFragment extends ProfileFragment {
	public static String TAG = "CorrectionFactorFragment";

	// Temporary storage locations for CF
	public int subjectCFHour, subjectCFMinute;
	public double subjectCFValue;
	public boolean subjectCFHourValid = false;
	public boolean subjectCFMinuteValid = false;
	public boolean subjectCFValueValid = false;
    public boolean mmolL = false;
	
	public int mgdlUpperLimit = 200;
	public int mgdlLowerLimit = 10;
	
	public double mmollUpperLimit = 11.0;
	public double mmollLowerLimit = 0.5;

	public CorrectionFactorFragment(DiAsSetup main) {
		this.main = main;
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final String FUNC_TAG = "onCreateView";
		
        int glucose_unit = Params.getInt(getActivity().getContentResolver(), "blood_glucose_display_units", 0);
		mmolL = (glucose_unit == 1);
        
		view = super.onCreateView(inflater, container, savedInstanceState, R.array.CF);

        if (mmolL) {
			((TextView) view.findViewById(R.id.textViewUnits)).setText("CF (mmol/L/U)");
		}
        
		// Build a list of Strings from the CF Tvector
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
						subjectCFHourValid = false;
						if (Integer.parseInt(((TextView) v).getText().toString()) >= 0 && Integer.parseInt(((TextView) v).getText().toString()) <= 23) {
							Debug.i(TAG, FUNC_TAG,"valid subjectCFTimeHour=" + ((TextView) v).getText().toString());
							subjectCFHour = Integer.parseInt(((TextView) v).getText().toString());
							subjectCFHourValid = true;
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
				subjectCFHourValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Integer.parseInt(v.getText().toString()) >= 0 && Integer.parseInt(v.getText().toString()) <= 23) {
						Debug.i(TAG, FUNC_TAG,"valid subjectCFTimeHour=" + v.getText().toString());
						subjectCFHour = Integer.parseInt(v.getText().toString());
						subjectCFHourValid = true;
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
						subjectCFMinuteValid = false;
						if (Integer.parseInt(((TextView) v).getText().toString()) >= 0 && Integer.parseInt(((TextView) v).getText().toString()) <= 59) {
							Debug.i(TAG, FUNC_TAG,"valid subjectCFTimeMinute=" + ((TextView) v).getText().toString());
							subjectCFMinute = Integer.parseInt(((TextView) v).getText().toString());
							subjectCFMinuteValid = true;
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
				subjectCFMinuteValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (Integer.parseInt(v.getText().toString()) >= 0 && Integer.parseInt(v.getText().toString()) <= 59) {
						Debug.i(TAG, FUNC_TAG,"valid subjectCFTimeMinute=" + v.getText().toString());
						subjectCFMinute = Integer.parseInt(v.getText().toString());
						subjectCFMinuteValid = true;
						v.setTextColor(Color.WHITE);
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});

		int length_filter = 3;
		if (mmolL) {
			length_filter +=2;
		}
		editTextValue.setFilters(new InputFilter[] {new InputFilter.LengthFilter(length_filter)});
		
		editTextValue.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
                String lower, upper, unit;
				((TextView) v).setTextColor(Color.WHITE);
				if (hasFocus) {
					if (mmolL){
						lower = String.valueOf(mmollLowerLimit);
						upper = String.valueOf(mmollUpperLimit);
						unit = "mmol/L/U";
					}
					else {
						lower = String.valueOf(mgdlLowerLimit);
						upper = String.valueOf(mgdlUpperLimit);
						unit = "mg/dL/U";
					}
					instructions.setText("Enter a correction factor in the range of "+ lower +" to "+ upper+" "+unit);
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						subjectCFValueValid = false;
						if (cfIsInRange(Double.parseDouble(((TextView) v).getText().toString()))) {
							Debug.i(TAG, FUNC_TAG,"valid subjectCF=" + ((TextView) v).getText().toString());
							if(mmolL){
                                subjectCFValue = Double.parseDouble(((TextView) v).getText().toString())*CGM.MGDL_PER_MMOLL;
                            }
                            else {
                                subjectCFValue = Double.parseDouble(((TextView) v).getText().toString());
                            }
							subjectCFValueValid = true;
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
				subjectCFValueValid = false;
				if (((TextView) v).getText().toString().length() > 0) {
					if (cfIsInRange(Double.parseDouble(((TextView) v).getText().toString()))) {
						Debug.i(TAG, FUNC_TAG,"valid subjectCF=" + ((TextView) v).getText().toString());
						if(mmolL){
                            subjectCFValue = Double.parseDouble(((TextView) v).getText().toString())*CGM.MGDL_PER_MMOLL;
                        }
                        else {
                            subjectCFValue = Double.parseDouble(((TextView) v).getText().toString());
                        }
						subjectCFValueValid = true;
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

	// Build the profile list from the CF Tvector
	public void buildProfile() {
		int ii, jj, t;
		profileList.clear();
		if (DiAsSetup.local_sd.subjectCFValid) {
			for (ii = 0; ii < DiAsSetup.local_sd.subjectCF.count(); ii++) {
				t = DiAsSetup.local_sd.subjectCF.get_time(ii).intValue();
				String s;
                if (mmolL) {
					s = new String(pad(t / 60) + ":" + pad(t % 60) + "  " + DiAsSetup.local_sd.subjectCF.get_value(ii)/CGM.MGDL_PER_MMOLL + " mmol/L/U");
				}
				else {
					s = new String(pad(t / 60) + ":" + pad(t % 60) + "  " + DiAsSetup.local_sd.subjectCF.get_value(ii) + " " + strings[UNIT]);
				}
				profileList.add(new SelectBox(main, this, ii, s));
			}
		}
		
		DiAsSetup.db.writeDb(DiAsSetup.local_sd);
	}

	// Add item to profile
	public void addItemToProfile(View view) {
		final String FUNC_TAG = "addItemToProfile";
		
		if (DiAsSetup.local_sd.subjectCF.count() < DiAsSetup.local_sd.subjectCF.capacity() && subjectCFHourValid && subjectCFMinuteValid && subjectCFValueValid) {
			int minutes = subjectCFHour * 60 + subjectCFMinute;
			DiAsSetup.local_sd.subjectCF.put_with_replace(minutes, subjectCFValue);
			DiAsSetup.local_sd.subjectCFValid = true;
			Debug.i(TAG, FUNC_TAG,"addItemToProfile, minutes=" + minutes + ", CF=" + subjectCFValue);
			buildProfile();
			displayProfile();
		} else {
			Debug.i(TAG, FUNC_TAG,"addItemToProfile failed: fields not valid " + subjectCFHourValid + " " + subjectCFMinuteValid + " " + subjectCFValueValid);
		}
		main.updateDisplay();
	}

	// Remove item from profile
	public void removeItemFromProfile(View view) {
		final String FUNC_TAG = "removeItemFromProfile";
		
		if (DiAsSetup.local_sd.subjectCF.count() > 0 && profileLineSelected < DiAsSetup.local_sd.subjectCF.count()) {
			DiAsSetup.local_sd.subjectCF.remove(profileLineSelected);
			if (DiAsSetup.local_sd.subjectCF.count() == 0) {
				DiAsSetup.local_sd.subjectCFValid = false;
			}
			Debug.i(TAG, FUNC_TAG, "Remove CF item...");
			buildProfile();
			displayProfile();
		} else {
			Debug.i(TAG, FUNC_TAG,"removeItemFromProfile failed");
		}
		main.updateDisplay();
	}

	public void clearConfirm() {
		DiAsSetup.local_sd.subjectCF.init();
		profileLineSelected = 0;
		DiAsSetup.local_sd.subjectCFValid = false;
		buildProfile();
		displayProfile();
		main.updateDisplay();
	}
    
    public boolean cfIsInRange(Double n) {
		boolean result;
		if(mmolL){
			result = ((n >= mmollLowerLimit) && (n <= mmollUpperLimit));
		}
		else {
			result = ((n >= mgdlLowerLimit) && (n <= mgdlUpperLimit));
		}
		return result;
	}
}
