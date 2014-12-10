//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class HMSData {
	// Interface definitions for the biometricsContentProvider
	public static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
    public static final Uri HMS_STATE_ESTIMATE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/hmsstateestimate");

    double MINIMUM_CORRECTION_BOLUS = 0.10;
    
	public final String TAG = "HMSservice";
    public double correction_in_units;
	public long correction_time_in_seconds;
	public boolean valid = false;
	
	public HMSData() {
		// Initialize needed values
		correction_in_units = 0.0;
		correction_time_in_seconds = -1;
		valid = false;
	}

   	private static HMSData instance = null;

   	public static HMSData getInstance() {
   		if(instance == null) {
   			instance = new HMSData();
   		}
   		return instance;
   	}

	public boolean read(Context calling_context) {
		boolean ret_value = false;
		Cursor c = calling_context.getContentResolver().query(HMS_STATE_ESTIMATE_URI, null, null, null, null);
		Log.i(TAG, "Retrieved HMS_STATE_ESTIMATE_URI with " + c.getCount() + " items");
		double correction_in_units_temp = 0.0;
		correction_in_units = 0.0;
		correction_time_in_seconds = -1;
		valid = false;
		if (c.moveToFirst()) {
			do{
				valid = true;
				// Fetch the correction_in_units value so that it can be screened for validity
				correction_in_units_temp = (double)c.getDouble(c.getColumnIndex("correction_in_units"));
				// If correction_in_units_temp > MINIMUM_CORRECTION_BOLUS then it corresponds to a correction
				if (correction_in_units_temp > MINIMUM_CORRECTION_BOLUS) {
					correction_time_in_seconds = c.getLong(c.getColumnIndex("time"));
					correction_in_units = correction_in_units_temp;
					ret_value = true;
				}
				else if (correction_in_units_temp < -1.0) {
					correction_time_in_seconds = c.getLong(c.getColumnIndex("time"));
					correction_in_units = 0.0;
					ret_value = true;
				}
			} while (c.moveToNext());
		}
		c.close();
		return ret_value;
	}
	
	
}
