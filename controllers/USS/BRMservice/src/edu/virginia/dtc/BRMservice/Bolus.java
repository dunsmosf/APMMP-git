//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.BRMservice;

import android.content.ContentValues;
import android.database.Cursor;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

public class Bolus {
	// Interface definitions for the biometricsContentProvider
	public static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
    public static final Uri HMS_STATE_ESTIMATE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/hmsstateestimate");
    public static final Uri MEAL_URI = Uri.parse("content://"+ PROVIDER_NAME + "/meal");
    public int _id = -1;
    public double MealBolusA = 0.0;
	public double MealBolusArem = 0.0;
	public double CORRA = 0.0;
	public double IOBrem = 0.0;
	public double d = 0.0;
	public double hmin = 0.0;
	public double Hmax = 0.0;
	public long time = 0;
	private Context context;
	public static final String TAG = "BRMservice";
	private static final boolean DEBUG_MODE = true;

	public Bolus(Context calling_context) {
		context = calling_context;
		read();
	}
	
	public void dump() {
		debug_message(TAG, "Bolus> MealBolusA="+MealBolusA+", MealBolusArem="+MealBolusArem+", CORRA="+CORRA+", IOBrem="+IOBrem);
		debug_message(TAG, "Bolus> d="+d+", hmin="+hmin+", Hmax="+Hmax+", time="+time);
	}
	
	// Fetch data from the last row in the hmsstateestimate table
	public boolean read() {
		boolean return_value = false;
        try {
			// Fetch meal bolus data
			Cursor creq=context.getContentResolver().query(HMS_STATE_ESTIMATE_URI, null, null, null, null);
			if (creq.moveToLast()) {
				// Retrieve the last meal bolus entry
				_id = creq.getInt(creq.getColumnIndex("_id"));
				time = creq.getLong(creq.getColumnIndex("time"));
				MealBolusA = creq.getDouble(creq.getColumnIndex("MealBolusA"));
				MealBolusArem = creq.getDouble(creq.getColumnIndex("MealBolusArem"));
				CORRA = creq.getDouble(creq.getColumnIndex("CORRA"));
				IOBrem = creq.getDouble(creq.getColumnIndex("IOBrem"));
				d = creq.getDouble(creq.getColumnIndex("d"));
				hmin = creq.getDouble(creq.getColumnIndex("hmin"));
				Hmax = creq.getDouble(creq.getColumnIndex("Hmax"));
				return_value = true;
				creq.close();
			}
			// There is no data to retrieve
			else {
				_id = -1;
				MealBolusA = 0.0;
				MealBolusArem = 0.0;
				CORRA = 0.0;
				IOBrem = 0.0;
				d = 0.0;
				hmin = 0.0;
				Hmax = 0.0;
				time = 0;
				return_value = false;
			}
        }
        catch (Exception e) {
        	debug_message("fetchNewBiometricData > Error APController", e.getMessage());
        }
        return return_value;
	}

	// Fetch row in the hmsstateestimate table with time equal to time_match or fetch last row before time_match and return false if no match
	public boolean read(long time_match) {
		boolean return_value = false;
        try {
			// Fetch meal bolus data that matches time_match or else return last row if no match
        	// Use case:  i) Subject enters CHO and SMBG but does not press INJECT.  A bolus record is created.
        	//            ii) Subject enter different CHO and SMBG and presses INJECT.  A new bolus record is created as well as a meal record.  The second
        	//                bolus record is the one that matches the meal record and must be used.  It will have a time earlier than the meal time but later
        	//                than the first bolus.
			Cursor creq=context.getContentResolver().query(HMS_STATE_ESTIMATE_URI, null, null, null, null);
			if (creq.moveToFirst()) {
				long time_buffer;
				// Pre-fill fields with valid blank data
				_id = -1;
				MealBolusA = 0.0;
				MealBolusArem = 0.0;
				CORRA = 0.0;
				IOBrem = 0.0;
				d = 0.0;
				hmin = 0.0;
				Hmax = 0.0;
				time = 0;
				do{
					// Check to see if this row has a time stamp later than time_match (this happens when we are matching a recently approved meal with its corresponding bolus record)
					time_buffer = creq.getLong(creq.getColumnIndex("time"));
					if (time_buffer > time_match) {
						creq.close();
						return false;
					}
					// Retrieve a  meal bolus entry
					_id = creq.getInt(creq.getColumnIndex("_id"));
					time = creq.getLong(creq.getColumnIndex("time"));
					MealBolusA = creq.getDouble(creq.getColumnIndex("MealBolusA"));
					MealBolusArem = creq.getDouble(creq.getColumnIndex("MealBolusArem"));
					CORRA = creq.getDouble(creq.getColumnIndex("CORRA"));
					IOBrem = creq.getDouble(creq.getColumnIndex("IOBrem"));
					d = creq.getDouble(creq.getColumnIndex("d"));
					hmin = creq.getDouble(creq.getColumnIndex("hmin"));
					Hmax = creq.getDouble(creq.getColumnIndex("Hmax"));
					// Check for matching time stamp (this happens when a meal is active)
					if (time == time_match) {
						creq.close();
						return true;
					}
				} while (creq.moveToNext());
			}
			// There is no data to retrieve
			else {
				_id = -1;
				MealBolusA = 0.0;
				MealBolusArem = 0.0;
				CORRA = 0.0;
				IOBrem = 0.0;
				d = 0.0;
				hmin = 0.0;
				Hmax = 0.0;
				time = 0;
				return_value = false;
			}
			creq.close();
        }
        catch (Exception e) {
        	debug_message("fetchNewBiometricData > Error APController", e.getMessage());
        }
        return return_value;
	}
	
    public void save(long time) {
	    ContentValues values = new ContentValues();
	    values.put("time", time);
	    values.put("MealBolusA", MealBolusA);
	    values.put("MealBolusArem", MealBolusArem);
	    values.put("CORRA", CORRA);
	    values.put("IOBrem", IOBrem);
	    values.put("d", d);
	    values.put("hmin", hmin);
	    values.put("Hmax", Hmax);
	    try {
	    	context.getContentResolver().insert(HMS_STATE_ESTIMATE_URI, values);
	    }
	    catch (Exception e) {
    			Log.e(TAG, e.getMessage());
	    }
	}
    
	// Update the row in the hmsstateestimate table that matches the meal that has just been started
	public boolean update() {
		boolean return_value = false;
		ContentValues values = new ContentValues();
        try {
			// Fetch meal bolus data
			Cursor creq=context.getContentResolver().query(HMS_STATE_ESTIMATE_URI, null, null, null, null);
			if (creq.moveToLast()) {
				// Retrieve the last meal bolus entry, substituting "start_time" for the "time" field
				values.put("_id", _id);
				values.put("time", time);
				values.put("MealBolusA", MealBolusA);
				values.put("MealBolusArem", MealBolusArem);
				values.put("CORRA", CORRA);
				values.put("IOBrem", IOBrem);
				values.put("d", d);
				values.put("hmin", hmin);
				values.put("Hmax", Hmax);
			}
			creq.close();
       }
        catch (Exception e) {
        	debug_message("updateMealBolusData > Error: ", e.getMessage());
        }
		if (_id != -1) {			// valid _id field
		    try {
		    	context.getContentResolver().update(HMS_STATE_ESTIMATE_URI, values, "_id="+_id, null);
				return_value = true;
		    }
		    catch (Exception e) {
		    	debug_message(TAG, e.getMessage());
		    }
		}
		else {
			log_IO(TAG, "Bolus > update > bolus record not updated");
		}
        return return_value;
	}

	public void log_IO(String tag, String message) {
		debug_message(tag, message);
	}
	
	private static void debug_message(String tag, String message) {
		if (DEBUG_MODE) {
			Log.i(tag, message);
		}
	}
	
}
