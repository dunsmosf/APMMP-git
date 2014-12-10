package edu.virginia.dtc.DiAsSetup;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class MonitoringUrlFragment extends DialogFragment {
	public static final String TAG = "MonitoringUrlFragment";
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
        
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		
		LayoutInflater inflater = getActivity().getLayoutInflater();
		RadioGroup layout = (RadioGroup) inflater.inflate(R.layout.monitoringurlchoices, null);
        
		builder.setTitle("Select Monitoring Server URL")
		   		.setView(layout);
		
		
		Dialog dialog = builder.create();
		dialog.setCanceledOnTouchOutside(false);
        
        return dialog;
		
	}
}
