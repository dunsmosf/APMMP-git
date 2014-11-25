package edu.virginia.dtc.MealActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.EditText;

public class SMBGDialogFragment extends DialogFragment{
    
	private EditText smbg;
    
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        smbg = new EditText(getActivity());
        smbg.setInputType(InputType.TYPE_CLASS_NUMBER);
        
        builder.setMessage(R.string.dialog_smbg).setPositiveButton(R.string.dialog_set, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                	   String text = smbg.getText().toString();
                	   int value = 0;
                	   try {
                           value = Integer.parseInt(text);                		   
                	   }
                	   catch (NumberFormatException e) {
                		   Log.e("MealActivity", "SMBG > Error: "+e.getLocalizedMessage());
                	   }
                       Log.i("MealActivity", "SMBG="+text);
                       MealActivity callingActivity = (MealActivity) getActivity();
                       callingActivity.onUserSelectValue(value);
                       dialog.dismiss();
                   }
               })
               .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       // User cancelled the dialog
                   }
               })
               .setView(smbg);
        // Create the AlertDialog object and return it
        return builder.create();
    }
}
