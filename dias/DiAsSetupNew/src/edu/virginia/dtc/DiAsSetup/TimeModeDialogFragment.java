package edu.virginia.dtc.DiAsSetup;

import edu.virginia.dtc.SysMan.Debug;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.widget.Toast;

public class TimeModeDialogFragment extends DialogFragment {
	public static final String TAG = "TimeModeDialogFragment";

	public static final int[] SPEEDUP_MULTIPLIERS = new int[] { 1, 2, 3, 5, 10 };
	public int selectedItem = 0;

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final String FUNC_TAG = "onCreateDialog";
		
		Bundle args = getArguments();
		selectedItem = args.getInt("timeMode");
		// Use the Builder class for convenient dialog construction
		CharSequence[] items;

		if (DiAsSetup.speedupAllowed) {
			items = new CharSequence[SPEEDUP_MULTIPLIERS.length + 1];
			for (int i = 1; i < items.length; i++)
				items[i] = SPEEDUP_MULTIPLIERS[i - 1] + "x Simulated";
		} else {
			items = new CharSequence[1];
			selectedItem = 0;
		}
		items[0] = "Real-time";
		
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("Supervisor time mode")
			.setSingleChoiceItems(items, selectedItem, new OnClickListener() {				
				public void onClick(DialogInterface dialog, int which) {
					selectedItem = which;
				}
			})
		   	.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					DriverData hardware = DriverData.getInstance();
					if (selectedItem == 0) {
						Debug.i(TAG, FUNC_TAG, "Real-time selected");
						hardware.realTime = true;						
					} else {
						int speedupMultiplier = SPEEDUP_MULTIPLIERS[selectedItem - 1];
						Debug.i(TAG, FUNC_TAG, speedupMultiplier + "x Speed-up selected");
						hardware.realTime = false;		
						hardware.speedupMultiplier = speedupMultiplier;
					}
				}
			})
			.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					// User cancelled the dialog
				}
			});
		// Create the AlertDialog object and return it
		return builder.create();
	}
}
