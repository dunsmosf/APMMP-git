package edu.virginia.dtc.DiAsService;

import java.util.Date;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.app.AlertDialog;
import android.os.Bundle;

public class Alerts extends Activity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	
	    Context ctx = this;
	    final AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
	    Bundle params = getIntent().getExtras();
	    if (params != null) {
	    	if (params.getBoolean("extended_bolus")) {
	    	    alert.setTitle("Extended bolus delivery stopped.");
	    	}
	    	else {
	    	    alert.setTitle("Priming insulin delivery stopped.");
	    	}
	    }
	    else {
		    alert.setTitle("Bolus delivery stopped.");
	    }
	    double undelivered_insulin = 0.0;
	    if (params != null) {
	    	undelivered_insulin = params.getDouble("undelivered_insulin", 0.0);
	    	String undelivered_insulin_string = new Double(undelivered_insulin).toString();
	    	alert.setMessage(String.format("%.2f", undelivered_insulin) +" U will not be delivered.");
	    }
	    else {
		    alert.setMessage("Some insulin was not delivered.");	    	
	    }
	    alert.setPositiveButton("OK",new DialogInterface.OnClickListener() {
	    public void onClick(DialogInterface dialog,int whichButton) {
	    	finish();
		}});
	    alert.create();
	    alert.show();

	}
	

}
