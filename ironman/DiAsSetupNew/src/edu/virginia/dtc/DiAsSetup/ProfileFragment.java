package edu.virginia.dtc.DiAsSetup;

import java.util.List;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public abstract class ProfileFragment extends Fragment {
	public static String TAG;
	public static View view;
	public DiAsSetup1 main;

	// Display-specific constants
	public static final int PROFILE_TEXT_DISPLAY_WIDTH = 30;
	// Profile box objects
	public List<SelectBox> profileList;
	public int profileLineSelected;
	public int profileToConfigure;
	public static final int COLOR_HIGHLIGHT = Color.argb(200, 255, 185, 75);
	public LinearLayout profileLinear;
	// UI objects
	EditText editTextTimeHour;
	EditText editTextTimeMinute;
	EditText editTextValue;
	SelectBox profileBox;
	Button buttonSave;	
	TextView instructions;
    // String array indices
    public static final int TITLE = 0;
    public static final int HINT = 1;
    public static final int UNIT = 2;
    public static final int INSTRUCTIONS = 3;
    public static final int SHORTNAME = 4;	
	public String[] strings;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState, int stringArrayID) {
		view = inflater.inflate(R.layout.setup2, container, false);
		strings = main.getResources().getStringArray(stringArrayID);
		((TextView) view.findViewById(R.id.textViewTitle)).setText(strings[TITLE]);
		((TextView) view.findViewById(R.id.textViewInstructions)).setText(strings[INSTRUCTIONS]);
		((TextView) view.findViewById(R.id.editTextValue)).setHint(strings[HINT]);
		((TextView) view.findViewById(R.id.textViewUnits)).setText(strings[SHORTNAME] + " (" + strings[UNIT] + ")");

		editTextTimeHour = (EditText) view.findViewById(R.id.editTextHour);
		editTextTimeMinute = (EditText) view.findViewById(R.id.editTextMinute);
		editTextValue = (EditText) view.findViewById(R.id.editTextValue);
		instructions = (TextView) view.findViewById(R.id.textViewInstructions);
		
		profileLinear = (LinearLayout) view.findViewById(R.id.profileLinear);

		return view;
	}

	// Decrement profile item selected
	public void decrementProfileSelection(View view) {
		if (profileLineSelected > 0) {
			profileLineSelected--;
		}
		displayProfile();
	}

	// Increment profile item selected
	public void incrementProfileSelection(View view) {
		if (profileLineSelected < profileList.size() - 1) {
			profileLineSelected++;
		}
		displayProfile();
	}

	// SelectListener selects a profile item
	public void selectProfile(int profile) {
		if (profile + 1 > 0 && profile < profileList.size()) {
			profileLineSelected = profile;
		}
		displayProfile();
	}

	public int getProfileCount() {
		return profileList.size();
	}

	public static String pad(int c) {
		if (c >= 10)
			return String.valueOf(c);
		else
			return "0" + String.valueOf(c);
	}

	// Display all current profile items
	public void displayProfile() {
		int ii;
		profileLinear.removeAllViews();
		LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		for (ii = 0; ii < profileList.size(); ii++) {
			profileLinear.addView(profileList.get(ii), params);
			if (profileLineSelected == ii)
				profileList.get(ii).setBackgroundColor(COLOR_HIGHLIGHT);
			else profileList.get(ii).setBackgroundColor(Color.TRANSPARENT);
		}
	}

	// Clear profile
	public void clearProfile() {
		AlertDialog.Builder cBuild = new AlertDialog.Builder(main);
		cBuild.setMessage("Clear all entries?").setCancelable(false).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				// Canceled
			}
		}).setPositiveButton("Accept", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				clearConfirm();
			}
		});
		cBuild.show();
	}

	public abstract void buildProfile();

	public abstract void addItemToProfile(View view);

	public abstract void removeItemFromProfile(View view);

	public abstract void clearConfirm();

}
