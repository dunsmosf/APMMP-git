package edu.virginia.dtc.MealActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;

public class WeightToCarbRatioDialogFragment extends DialogFragment{
    
	private EditText ratio;
	int size;
	private final static int SIZE_SMALL = 0;
	private final static int SIZE_MEDIUM = 1;
	private final static int SIZE_LARGE = 2;
	private String Large_meal_ratio_key = "edu.virginia.dtc.MealService.User_weight_to_carb_ratio_large_meal";
	private String Medium_meal_ratio_key = "edu.virginia.dtc.MealService.User_weight_to_carb_ratio_medium_meal";
	private String Small_meal_ratio_key = "edu.virginia.dtc.MealService.User_weight_to_carb_ratio_small_meal";

	/**
     * Create a new instance of WeightToCarbRatioDialogFragment, providing "size"
     * as an argument.
     */
    static WeightToCarbRatioDialogFragment newInstance(int size) {
    	WeightToCarbRatioDialogFragment f = new WeightToCarbRatioDialogFragment();
        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putInt("size", size);
        f.setArguments(args);
        return f;
    }
    
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		size = getArguments().getInt("size");
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        ratio = new EditText(getActivity());
        ratio.setInputType(InputType.TYPE_CLASS_NUMBER+InputType.TYPE_NUMBER_FLAG_DECIMAL);
        switch(size) {
        case SIZE_SMALL:
            builder.setMessage(R.string.dialog_ratio).setPositiveButton(R.string.dialog_set, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
             	   String text = ratio.getText().toString();
             	   double ratio = 0;
             	   try {
             		  ratio = Double.parseDouble(text);                		   
             	   }
             	   catch (NumberFormatException e) {
             		   Log.e("MealActivity", "Weight to carb ratio > Error: "+e.getLocalizedMessage());
             	   }
             	   if (ratio < 1.0) {
                 	   Toast.makeText(getActivity().getApplicationContext(), "SIZE_SMALL ratio=" + ratio, Toast.LENGTH_SHORT).show();
                 	   MealActivity callingActivity = (MealActivity) getActivity();
                       callingActivity.onUserEntersRatio(SIZE_SMALL, ratio);
             	   }
             	   else {
                 	   Toast.makeText(getActivity().getApplicationContext(), "SIZE_SMALL ratio must be less than 1.0", Toast.LENGTH_SHORT).show();
             	   }
                   dialog.dismiss();
                }
            })
            .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog
                }
            })
            .setView(ratio);
        	break;
        case SIZE_MEDIUM:
            builder.setMessage(R.string.dialog_ratio).setPositiveButton(R.string.dialog_set, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
             	   String text = ratio.getText().toString();
             	   double ratio = 0;
             	   try {
             		  ratio = Double.parseDouble(text);                		   
             	   }
             	   catch (NumberFormatException e) {
             		   Log.e("MealActivity", "Weight to carb ratio > Error: "+e.getLocalizedMessage());
             	   }
             	   if (ratio < 1.0) {
                 	   Toast.makeText(getActivity().getApplicationContext(), "SIZE_MEDIUM ratio=" + ratio, Toast.LENGTH_SHORT).show();
                 	   MealActivity callingActivity = (MealActivity) getActivity();
                       callingActivity.onUserEntersRatio(SIZE_MEDIUM, ratio);
             	   }
             	   else {
                 	   Toast.makeText(getActivity().getApplicationContext(), "SIZE_MEDIUM ratio must be less than 1.0", Toast.LENGTH_SHORT).show();
             	   }
                   dialog.dismiss();
                }
            })
            .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog
                }
            })
            .setView(ratio);
        	break;
        case SIZE_LARGE:
            builder.setMessage(R.string.dialog_ratio).setPositiveButton(R.string.dialog_set, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
             	   String text = ratio.getText().toString();
             	   double ratio = 0;
             	   try {
             		  ratio = Double.parseDouble(text);                		   
             	   }
             	   catch (NumberFormatException e) {
             		   Log.e("MealActivity", "Weight to carb ratio > Error: "+e.getLocalizedMessage());
             	   }
             	   if (ratio < 1.0) {
                 	   Toast.makeText(getActivity().getApplicationContext(), "SIZE_LARGE ratio=" + ratio, Toast.LENGTH_SHORT).show();
                 	   MealActivity callingActivity = (MealActivity) getActivity();
                       callingActivity.onUserEntersRatio(SIZE_LARGE, ratio);
             	   }
             	   else {
                 	   Toast.makeText(getActivity().getApplicationContext(), "SIZE_LARGE ratio must be less than 1.0", Toast.LENGTH_SHORT).show();
             	   }
                   dialog.dismiss();
                }
            })
            .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog
                }
            })
            .setView(ratio);
        	break;
        default:
        	break;
        }
        
        // Create the AlertDialog object and return it
        return builder.create();
    }
}
