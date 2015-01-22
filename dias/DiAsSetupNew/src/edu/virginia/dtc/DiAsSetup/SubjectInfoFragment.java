package edu.virginia.dtc.DiAsSetup;

import java.util.ArrayList;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.DiAsSubjectData;
import edu.virginia.dtc.SysMan.Params;

public class SubjectInfoFragment extends Fragment {
	public static String TAG = "SubjectInfoFragment";
	public static View view;
	public DiAsSetup1 main;
	public static int COLOR_DISABLED = Color.rgb(130, 130, 130);

	// Remote Monitoring URI dropdown list declarations
	public static ArrayAdapter<String> rmURIList;
	public ArrayList<String> rmURIHistory = new ArrayList<String>();
	public static final int RM_URI_HISTORY_MAX = 3;

	//	public static boolean expandScrollView = true;

	public SubjectInfoFragment(DiAsSetup1 main) 
	{
		this.main = main;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
	{
		final String FUNC_TAG = "onCreateView";
		
		view = inflater.inflate(R.layout.setup1, container, false);

		if (rmURIList == null) {
			rmURIList = new ArrayAdapter<String>(main, android.R.layout.simple_spinner_item);
			rmURIList.setDropDownViewResource(R.layout.spinnerdropdownlongstring);
		}
		
		// Find all GUI components
		Spinner spinner = (Spinner) view.findViewById(R.id.spinner);
		EditText editTextSubjectID = (EditText) view.findViewById(R.id.editTextSubjectID);
		editTextSubjectID.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		EditText editTextSessionNumber = (EditText) view.findViewById(R.id.editTextSessionNumber);
		EditText editTextWeight = (EditText) view.findViewById(R.id.editTextWeight);
		EditText editTextHeight = (EditText) view.findViewById(R.id.editTextHeight);
		EditText editTextAge = (EditText) view.findViewById(R.id.editTextAge);
		EditText editTextTDI = (EditText) view.findViewById(R.id.editTextTDI);
		RadioGroup editGender = (RadioGroup) view.findViewById(R.id.RadioGroupGender);

		int size = Params.getInt(main.getContentResolver(), "setup_screen_font_size", 16);
		
		if(size > 0 && size < 20)
		{
			((TextView)view.findViewById(R.id.TextView01)).setTextSize(size);
			((TextView)view.findViewById(R.id.TextView02)).setTextSize(size);
			((TextView)view.findViewById(R.id.TextView03)).setTextSize(size);
			((TextView)view.findViewById(R.id.TextView04)).setTextSize(size);
			((TextView)view.findViewById(R.id.TextView05)).setTextSize(size);
			((TextView)view.findViewById(R.id.TextView06)).setTextSize(size);
			((TextView)view.findViewById(R.id.TextView07)).setTextSize(size);
			((TextView)view.findViewById(R.id.TextView07)).setVisibility(View.GONE);
		}
		
		// Set up the spinner here so you can select active insulin time (2, 4, 6 or 8 hours)
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(main, R.array.AIT_array, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		
		// The AIT is set at 4 hours. The spinner will display the 4 hour value and cannot be clicked
		spinner.setEnabled(false);
		spinner.setClickable(false);
		spinner.setAdapter(adapter);
		
		// If a selection has already been made then initialize the spinner accordingly
		if (DiAsSetup1.local_sd != null) {
			if (DiAsSetup1.local_sd.AITValid) {
				switch (DiAsSetup1.local_sd.subjectAIT) 
				{
					case 2:
						spinner.setSelection(0);
						break;
					case 4:
						spinner.setSelection(1);
						break;
					case 6:
						spinner.setSelection(2);
						break;
					case 8:
						spinner.setSelection(3);
						break;
					default:
						break;
				}
			}
		}
		// Force AIT==4 hours
		spinner.setSelection(1);
		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
				switch (pos) 
				{
					case 0:
						DiAsSetup1.local_sd.subjectAIT = 2;
						DiAsSetup1.local_sd.AITValid = true;
						break;
					case 1:
						DiAsSetup1.local_sd.subjectAIT = 4;
						DiAsSetup1.local_sd.AITValid = true;
						break;
					case 2:
						DiAsSetup1.local_sd.subjectAIT = 6;
						DiAsSetup1.local_sd.AITValid = true;
						break;
					case 3:
						DiAsSetup1.local_sd.subjectAIT = 8;
						DiAsSetup1.local_sd.AITValid = true;
						break;
					default:
						DiAsSetup1.local_sd.AITValid = false;
						break;
				}
				DiAsSetup1.local_sd.subjectAIT = 4;
				DiAsSetup1.local_sd.AITValid = true;
				main.updateDisplay();
			}

			public void onNothingSelected(AdapterView<?> parent) 
			{
			}
		});
		spinner.setVisibility(View.INVISIBLE);

		// Enter subjectName
		editTextSubjectID.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) 
			{
				TextView textViewInstructions = (TextView) view.findViewById(R.id.textViewInstructions);
				if (hasFocus) 
				{
					textViewInstructions.setText("Enter the subject ID using alphanumeric characters");
				} 
				else 
				{
					DiAsSetup1.local_sd.subjectNameValid = false;
					if (((TextView) v).getText().toString().length() > 0) 
					{
						Debug.w(TAG, FUNC_TAG, "ID!");
						DiAsSetup1.local_sd.subjectName = ((EditText) view.findViewById(R.id.editTextSubjectID)).getText().toString();
						DiAsSetup1.local_sd.subjectNameValid = true;
						if (!DiAsSetup1.configurationMode)
							((TextView) v).setTextColor(Color.WHITE);
					}
				}
				main.updateDisplay();
			}
		});
		editTextSubjectID.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				DiAsSetup1.local_sd.subjectNameValid = false;
				if (v.getText().toString().length() > 0) {
					Debug.i(TAG, FUNC_TAG, "valid subjectID=" + v.getText().toString());
					DiAsSetup1.local_sd.subjectName = ((EditText) view.findViewById(R.id.editTextSubjectID)).getText().toString();
					DiAsSetup1.local_sd.subjectNameValid = true;
					if (!DiAsSetup1.configurationMode)
						v.setTextColor(Color.WHITE);
					main.updateDisplay();
					return false;
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});

		// Enter sessionNumber (Subject Number)
		editTextSessionNumber.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				TextView textViewInstructions = (TextView) view.findViewById(R.id.textViewInstructions);
				if (hasFocus) {
					textViewInstructions.setText("Enter a Subject Number from 1-999999999");
				} else {
					DiAsSetup1.local_sd.subjectSessionValid = false;
					if (((TextView) v).getText().toString().length() > 0) {
						if (((TextView) v).getText().toString().length() < 10) {
							int subject_number = Integer.parseInt(((TextView) v).getText().toString());
							if (subject_number > 0 && subject_number < 1000000000) {
								Debug.i(TAG, FUNC_TAG, "valid Subject Number=" + subject_number);
								DiAsSetup1.local_sd.subjectSession = ((EditText) view.findViewById(R.id.editTextSessionNumber)).getText().toString();
								DiAsSetup1.local_sd.subjectSessionValid = true;
								((TextView) v).setTextColor(Color.WHITE);
							} 
							else {
								((TextView) v).setTextColor(Color.RED);
							}
						}
						else {
							((TextView) v).setTextColor(Color.RED);
						}
					}
				}
				main.updateDisplay();
			}
		});
		editTextSessionNumber.setOnEditorActionListener(new OnEditorActionListener() {	
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (v.getText().toString().length() > 0) {
					int subject_number = Integer.parseInt(v.getText().toString());
					Debug.i(TAG, FUNC_TAG, "subject number=" + subject_number);
					DiAsSetup1.local_sd.subjectSessionValid = false;
					if (subject_number > 0 && subject_number < 1000000000) {
						Debug.i(TAG, FUNC_TAG, "valid Subject Number=" + subject_number);
						DiAsSetup1.local_sd.subjectSession = ((EditText) view.findViewById(R.id.editTextSessionNumber)).getText().toString();
						DiAsSetup1.local_sd.subjectSessionValid = true;
						v.setTextColor(Color.WHITE);
						v.setTextColor(Color.WHITE);
						main.updateDisplay();
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});

		// Enter weight
		editTextWeight.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				TextView textViewInstructions = (TextView) view.findViewById(R.id.textViewInstructions);
				if (hasFocus) {
					//					expandScrollView = false;
					//					((ScrollView) view.findViewById(R.id.scrollWindow)).setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 4));
					textViewInstructions.setText("Enter subject weight from 27-136 kg");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						DiAsSetup1.local_sd.subjectWeight = Integer.parseInt(((TextView) v).getText().toString());
						Debug.i(TAG, FUNC_TAG, "OnFocusChangeListener weight=" + DiAsSetup1.local_sd.subjectWeight);
						DiAsSetup1.local_sd.weightValid = false;
						if (DiAsSetup1.local_sd.subjectWeight >= 27 && DiAsSetup1.local_sd.subjectWeight <= 136) {
							Debug.i(TAG, FUNC_TAG, "valid weight=" + DiAsSetup1.local_sd.subjectWeight);
							DiAsSetup1.local_sd.weightValid = true;
							((TextView) v).setTextColor(Color.WHITE);
						} else {
							((TextView) v).setTextColor(Color.RED);
						}
					} else {
						((TextView) v).setTextColor(Color.RED);
					}
				}
				main.updateDisplay();
			}
		});
		editTextWeight.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (v.getText().toString().length() > 0) {
					DiAsSetup1.local_sd.subjectWeight = Integer.parseInt(v.getText().toString());
					Debug.i(TAG, FUNC_TAG, "weight=" + DiAsSetup1.local_sd.subjectWeight);
					DiAsSetup1.local_sd.weightValid = false;
					if (DiAsSetup1.local_sd.subjectWeight >= 27 && DiAsSetup1.local_sd.subjectWeight <= 136) {
						Debug.i(TAG, FUNC_TAG, "valid weight=" + DiAsSetup1.local_sd.subjectWeight);
						DiAsSetup1.local_sd.weightValid = true;
						v.setTextColor(Color.WHITE);
						main.updateDisplay();
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});

		// Enter height
		editTextHeight.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				TextView textViewInstructions = (TextView) view.findViewById(R.id.textViewInstructions);
				if (hasFocus) {
					//					expandScrollView = false;
					//					((ScrollView) view.findViewById(R.id.scrollWindow)).setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 4));
					textViewInstructions.setText("Enter subject height from 127-221 cm");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						DiAsSetup1.local_sd.subjectHeight = Integer.parseInt(((TextView) v).getText().toString());
						Debug.i(TAG, FUNC_TAG, "OnFocusChangeListener height=" + DiAsSetup1.local_sd.subjectHeight);
						DiAsSetup1.local_sd.heightValid = false;
						if (DiAsSetup1.local_sd.subjectHeight >= 127 && DiAsSetup1.local_sd.subjectWeight <= 221) {
							Debug.i(TAG, FUNC_TAG, "valid height=" + DiAsSetup1.local_sd.subjectHeight);
							DiAsSetup1.local_sd.heightValid = true;
							((TextView) v).setTextColor(Color.WHITE);
						} else {
							((TextView) v).setTextColor(Color.RED);
						}
					} else {
						((TextView) v).setTextColor(Color.RED);
					}
				}
				main.updateDisplay();
			}
		});
		editTextHeight.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (v.getText().toString().length() > 0) {
					DiAsSetup1.local_sd.subjectHeight = Integer.parseInt(v.getText().toString());
					Debug.i(TAG, FUNC_TAG, "height=" + DiAsSetup1.local_sd.subjectHeight);
					DiAsSetup1.local_sd.heightValid = false;
					if (DiAsSetup1.local_sd.subjectHeight >= 127 && DiAsSetup1.local_sd.subjectHeight <= 221) {
						Debug.i(TAG, FUNC_TAG, "valid height=" + DiAsSetup1.local_sd.subjectHeight);
						DiAsSetup1.local_sd.heightValid = true;
						v.setTextColor(Color.WHITE);
						main.updateDisplay();
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});

		// Enter age
		editTextAge.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				TextView textViewInstructions = (TextView) view.findViewById(R.id.textViewInstructions);
				if (hasFocus) {
					//					expandScrollView = false;
					//					((ScrollView) view.findViewById(R.id.scrollWindow)).setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 4));
					textViewInstructions.setText("Enter subject age from 1-100 years");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						DiAsSetup1.local_sd.subjectAge = Integer.parseInt(((TextView) v).getText().toString());
						Debug.i(TAG, FUNC_TAG, "age=" + DiAsSetup1.local_sd.subjectAge);
						DiAsSetup1.local_sd.ageValid = false;
						if (DiAsSetup1.local_sd.subjectAge >= 1 && DiAsSetup1.local_sd.subjectAge <= 100) {
							Debug.i(TAG, FUNC_TAG, "valid age=" + DiAsSetup1.local_sd.subjectAge);
							DiAsSetup1.local_sd.ageValid = true;
							((TextView) v).setTextColor(Color.WHITE);
						} else {
							((TextView) v).setTextColor(Color.RED);
						}
					} else {
						((TextView) v).setTextColor(Color.RED);
					}
				}
				main.updateDisplay();
			}
		});
		editTextAge.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (v.getText().toString().length() > 0) {
					DiAsSetup1.local_sd.subjectAge = Integer.parseInt(v.getText().toString());
					Debug.i(TAG, FUNC_TAG, "age=" + DiAsSetup1.local_sd.subjectAge);
					DiAsSetup1.local_sd.ageValid = false;
					if (DiAsSetup1.local_sd.subjectAge >= 1 && DiAsSetup1.local_sd.subjectAge <= 100) {
						Debug.i(TAG, FUNC_TAG, "valid age=" + DiAsSetup1.local_sd.subjectAge);
						DiAsSetup1.local_sd.ageValid = true;
						v.setTextColor(Color.WHITE);
						main.updateDisplay();
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});

		// Enter TDI
		editTextTDI.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				TextView textViewInstructions = (TextView) view.findViewById(R.id.textViewInstructions);
				if (hasFocus) {
					//					expandScrollView = false;
					//					((ScrollView) view.findViewById(R.id.scrollWindow)).setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 4));
					textViewInstructions.setText("Enter subject total daily insulin from 10-100 units");
				} else {
					if (((TextView) v).getText().toString().length() > 0) {
						DiAsSetup1.local_sd.subjectTDI = Double.parseDouble(((TextView) v).getText().toString());
						Debug.i(TAG, FUNC_TAG, "TDI=" + DiAsSetup1.local_sd.subjectTDI);
						DiAsSetup1.local_sd.TDIValid = false;
						if (DiAsSetup1.local_sd.subjectTDI >= 10 && DiAsSetup1.local_sd.subjectTDI <= 100) {
							Debug.i(TAG, FUNC_TAG, "valid TDI=" + DiAsSetup1.local_sd.subjectTDI);
							DiAsSetup1.local_sd.TDIValid = true;
							((TextView) v).setTextColor(Color.WHITE);
						} else {
							((TextView) v).setTextColor(Color.RED);
						}
					} else {
						((TextView) v).setTextColor(Color.RED);
					}
				}
				main.updateDisplay();
			}
		});
		editTextTDI.setOnEditorActionListener(new OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (v.getText().toString().length() > 0) {
					DiAsSetup1.local_sd.subjectTDI = Double.parseDouble(v.getText().toString());
					Debug.i(TAG, FUNC_TAG, "TDI=" + DiAsSetup1.local_sd.subjectTDI);
					DiAsSetup1.local_sd.TDIValid = false;
					if (DiAsSetup1.local_sd.subjectTDI >= 10 && DiAsSetup1.local_sd.subjectTDI <= 100) {
						Debug.i(TAG, FUNC_TAG, "valid TDI=" + DiAsSetup1.local_sd.subjectTDI);
						DiAsSetup1.local_sd.TDIValid = true;
						v.setTextColor(Color.WHITE);
						main.updateDisplay();
						return false;
					}
				}
				v.setTextColor(Color.RED);
				return true;
			}
		});
		
		editGender.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			public void onCheckedChanged(final RadioGroup group, final int checkedId) {
				switch (checkedId) {
					case R.id.RadioButtonMale:
						DiAsSetup1.local_sd.subjectFemale = false;
						break;
					case R.id.RadioButtonFemale:
						DiAsSetup1.local_sd.subjectFemale = true;
						break;
					default:break;
				}
			}
		});
		
		
		// fill in fields
		if (DiAsSetup1.local_sd.subjectNameValid)
			editTextSubjectID.setText(DiAsSetup1.local_sd.subjectName);
		if (DiAsSetup1.local_sd.subjectSessionValid)
			editTextSessionNumber.setText(DiAsSetup1.local_sd.subjectSession);
		if (DiAsSetup1.local_sd.weightValid)
			editTextWeight.setText(Integer.toString(DiAsSetup1.local_sd.subjectWeight));
		if (DiAsSetup1.local_sd.heightValid)
			editTextHeight.setText(Integer.toString(DiAsSetup1.local_sd.subjectHeight));
		if (DiAsSetup1.local_sd.ageValid)
			editTextAge.setText(Integer.toString(DiAsSetup1.local_sd.subjectAge));
		if (DiAsSetup1.local_sd.TDIValid)
			editTextTDI.setText(Double.toString(DiAsSetup1.local_sd.subjectTDI));
		if (DiAsSetup1.local_sd.subjectFemale) {
			((RadioButton) view.findViewById(R.id.RadioButtonFemale)).setChecked(true);
		} else {
			((RadioButton) view.findViewById(R.id.RadioButtonMale)).setChecked(true);
		}
		
		((ScrollView) view.findViewById(R.id.scrollWindow)).setVerticalFadingEdgeEnabled(true);
		((ScrollView) view.findViewById(R.id.scrollWindow)).setFadingEdgeLength(50);		

		Debug.w(TAG, FUNC_TAG, "OnCreate Subject Info Fragment");
		DiAsSubjectData.print(TAG, DiAsSetup1.local_sd);
		
		return view;
	}

	public void updateDisplay() 
	{
		final String FUNC_TAG = "updateDisplay";
		
		if (view == null)
			return;
		if (DiAsSetup1.configurationMode) 
		{
			Debug.w(TAG, FUNC_TAG, "Configuration Mode");
			
			Spinner spinner = (Spinner) view.findViewById(R.id.spinner);
			EditText editTextSubjectID = (EditText) view.findViewById(R.id.editTextSubjectID);
			editTextSubjectID.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
			EditText editTextSessionNumber = (EditText) view.findViewById(R.id.editTextSessionNumber);
			
			EditText editTextWeight = (EditText) view.findViewById(R.id.editTextWeight);
			EditText editTextHeight = (EditText) view.findViewById(R.id.editTextHeight);
			EditText editTextAge = (EditText) view.findViewById(R.id.editTextAge);
			EditText editTextTDI = (EditText) view.findViewById(R.id.editTextTDI);
			RadioButton RadioButtonFemale = (RadioButton) view.findViewById(R.id.RadioButtonFemale);
			RadioButton RadioButtonMale = (RadioButton) view.findViewById(R.id.RadioButtonMale);
			
			// Make page view-only
			spinner.setEnabled(false);
			editTextSubjectID.setEnabled(false);
			editTextSessionNumber.setEnabled(false);
//			editTextWeight.setEnabled(false);
//			editTextHeight.setEnabled(false);
//			editTextAge.setEnabled(false);
//			editTextTDI.setEnabled(false);
			RadioButtonFemale.setEnabled(false);
			RadioButtonMale.setEnabled(false);
			spinner.setFocusable(false);
			editTextSubjectID.setFocusable(false);
			editTextSessionNumber.setFocusable(false);
//			editTextWeight.setFocusable(false);
//			editTextHeight.setFocusable(false);
//			editTextAge.setFocusable(false);
//			editTextTDI.setFocusable(false);
			RadioButtonFemale.setFocusable(false);
			RadioButtonMale.setFocusable(false);
			editTextSubjectID.setTextColor(COLOR_DISABLED);
			editTextSessionNumber.setTextColor(COLOR_DISABLED);
//			editTextWeight.setTextColor(COLOR_DISABLED);
//			editTextHeight.setTextColor(COLOR_DISABLED);
//			editTextAge.setTextColor(COLOR_DISABLED);
//			editTextTDI.setTextColor(COLOR_DISABLED);
			RadioButtonFemale.setTextColor(COLOR_DISABLED);
			RadioButtonMale.setTextColor(COLOR_DISABLED);
			
			((TextView) view.findViewById(R.id.textViewInstructions)).setText("View Only: Fixed ID and Number");
		}
	}
}
