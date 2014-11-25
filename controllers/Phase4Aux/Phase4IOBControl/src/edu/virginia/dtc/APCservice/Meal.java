//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.app.AlertDialog;

public class Meal {
	// Interface definitions for the biometricsContentProvider
	public static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
    public static final Uri HMS_STATE_ESTIMATE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/hmsstateestimate");
    public static final Uri MEAL_URI = Uri.parse("content://"+ PROVIDER_NAME + "/meal");
	// DiAs State Variable and Definitions - state for the system as a whole
	public static final int DIAS_STATE_STOPPED = 0;
	public static final int DIAS_STATE_OPEN_LOOP = 1;
	public static final int DIAS_STATE_CLOSED_LOOP = 2;
	public static final int DIAS_STATE_SAFETY_ONLY = 3;
	public final static int MEAL_SIZE_SMALL = 0;
	public final static int MEAL_SIZE_MEDIUM = 1;
	public final static int MEAL_SIZE_LARGE = 2;
	public final static int MEAL_SPEED_SLOW = 0;
	public final static int MEAL_SPEED_MEDIUM = 1;
	public final static int MEAL_SPEED_FAST = 2;
    public int _id = -1;
	public long time_announce = 0;
	public long time = 0;
	public double meal_size_grams = 0.0;
	public double SMBG = 0.0;
	public double meal_screen_bolus = 0.0;
	public int type = 0;
	public int size = 0;
	public boolean treated = false;
	public boolean active = false;
	public boolean approved = false;
	public boolean extended_bolus = false;
	public long extended_bolus_duration_seconds = 0;
	public double meal_screen_meal_bolus = 0.0;
	public double meal_screen_corr_bolus = 0.0;
	public double meal_screen_smbg_bolus = 0.0;
	public double extended_bolus_insulin_rem = 0.0;
	public boolean empty = true;
	private Context context;
	public static final String TAG = "MealIOBControl";
	private static final boolean DEBUG_MODE = true;

	public Meal(Context calling_context) {
		context = calling_context;
		read();
	}

	public void dump() {
		debug_message(TAG, "Meal> time_announce="+time_announce+", time="+time+", meal_size_grams="+meal_size_grams+", SMBG="+SMBG);
		debug_message(TAG, "Meal> meal_screen_bolus="+meal_screen_bolus+", type="+type+", treated="+treated+", active="+active+", approved="+approved);
	}
	
	public boolean markAllMealsTreated(AlertDialog alertDialog) {
		boolean return_value = false;
		int treated_int = 0;
		int active_int = 0;
		int approved_int = 0;
		int extended_bolus_int = 0;
        try {
			// Fetch meal data
			Cursor creq=context.getContentResolver().query(MEAL_URI, null, null, null, null);
			if (creq.moveToFirst()) {
				do{
					// Retrieve the current meal bolus entry
					_id = creq.getInt(creq.getColumnIndex("_id"));
					time = creq.getInt(creq.getColumnIndex("time"));
					time_announce = creq.getInt(creq.getColumnIndex("time_announce"));
					meal_size_grams = creq.getInt(creq.getColumnIndex("meal_size_grams"));
					SMBG = creq.getInt(creq.getColumnIndex("SMBG"));
					meal_screen_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_bolus"));
					type = creq.getInt(creq.getColumnIndex("type"));
					size = creq.getInt(creq.getColumnIndex("size"));
					extended_bolus_duration_seconds = creq.getInt(creq.getColumnIndex("extended_bolus_duration_seconds"));
					meal_screen_meal_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_meal_bolus"));
					meal_screen_corr_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_corr_bolus"));
					meal_screen_smbg_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_smbg_bolus"));
					extended_bolus_insulin_rem = creq.getDouble(creq.getColumnIndex("extended_bolus_insulin_rem"));
					treated_int = creq.getInt(creq.getColumnIndex("treated"));
					if (treated_int == 0)
						treated=false;
					else
						treated=true;
					active_int = creq.getInt(creq.getColumnIndex("active"));
					if (active_int == 0)
						active=false;
					else
						active=true;
					approved_int = creq.getInt(creq.getColumnIndex("approved"));
					if (approved_int == 0)
						approved=false;
					else
						approved=true;
					extended_bolus_int = creq.getInt(creq.getColumnIndex("extended_bolus"));
					if (extended_bolus_int == 0)
						extended_bolus=false;
					else
						extended_bolus=true;
					// If active then fire off an Alert
					alertDialog = new AlertDialog.Builder(context).create();
					alertDialog.setTitle("Bolus ended");
					alertDialog.setMessage(extended_bolus_insulin_rem+" U of insulin not delivered.");
					alertDialog.show();
					// Set the treated field true and update
					treated = true;
					active=false;
					approved=false;
					update();
					return_value = true;
				} while (creq.moveToNext());
			}
			creq.close();
        }
        catch (Exception e) {
        	debug_message("updateMeal > Error ", e.getMessage());
        }
        return return_value;
	}
	
	// Update the local copy of the meal record with the first row marked active (there should only be one active row!)
	// If there is no row marked active then fill local copy with contents of last row
	public boolean readActive() {
		boolean return_value = false;
		int treated_int = 0;
		int active_int = 0;
		int approved_int = 0;
		int extended_bolus_int = 0;
        try {
			// Fetch meal data
			Cursor creq=context.getContentResolver().query(MEAL_URI, null, null, null, null);
			if (creq.moveToFirst()) {
				do{
					// Retrieve a  meal bolus entry
					_id = creq.getInt(creq.getColumnIndex("_id"));
					time = creq.getInt(creq.getColumnIndex("time"));
					time_announce = creq.getInt(creq.getColumnIndex("time_announce"));
					meal_size_grams = creq.getInt(creq.getColumnIndex("meal_size_grams"));
					SMBG = creq.getInt(creq.getColumnIndex("SMBG"));
					meal_screen_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_bolus"));
					type = creq.getInt(creq.getColumnIndex("type"));
					size = creq.getInt(creq.getColumnIndex("size"));
					extended_bolus_duration_seconds = creq.getInt(creq.getColumnIndex("extended_bolus_duration_seconds"));
					meal_screen_meal_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_meal_bolus"));
					meal_screen_corr_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_corr_bolus"));
					meal_screen_smbg_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_smbg_bolus"));
					extended_bolus_insulin_rem = creq.getDouble(creq.getColumnIndex("extended_bolus_insulin_rem"));
					treated_int = creq.getInt(creq.getColumnIndex("treated"));
					if (treated_int == 0)
						treated=false;
					else
						treated=true;
					active_int = creq.getInt(creq.getColumnIndex("active"));
					if (active_int == 0)
						active=false;
					else
						active=true;
					approved_int = creq.getInt(creq.getColumnIndex("approved"));
					if (approved_int == 0)
						approved=false;
					else
						approved=true;
					extended_bolus_int = creq.getInt(creq.getColumnIndex("extended_bolus"));
					if (extended_bolus_int == 0)
						extended_bolus=false;
					else
						extended_bolus=true;
					empty = false;
					return_value = true;
					// Check for matching time stamp (this happens when a meal is active)
					if (active) {
						creq.close();
						return true;
					}
				} while (creq.moveToNext());
			}
			else {
				time_announce = 0;
				time = 0;
				meal_size_grams = 0.0;
				SMBG = 0.0;
				meal_screen_bolus = 0.0;
				type = 0;
				treated = false;
				active = false;
				approved = false;
				extended_bolus = false;
				extended_bolus_duration_seconds = 0;
				meal_screen_meal_bolus = 0.0;
				meal_screen_corr_bolus = 0.0;
				meal_screen_smbg_bolus = 0.0;
				extended_bolus_insulin_rem = 0.0;
				empty = true;
			}
			creq.close();
        }
        catch (Exception e) {
        	debug_message("updateMeal > Error ", e.getMessage());
        }
        return return_value;
	}

	// Update the local copy of the meal record with the first row marked active after ignoring the rows with ids in the list.
	// If there is no row marked active then fill local copy with contents of last row
	public boolean readActive(List <Integer> ids_to_ignore) {
		boolean return_value = false;
		int treated_int = 0;
		int active_int = 0;
		int approved_int = 0;
		int extended_bolus_int = 0;
		Integer id_buffer;
		boolean read_this_id;
        try {
			// Fetch meal data
			Cursor creq=context.getContentResolver().query(MEAL_URI, null, null, null, null);
			if (creq.moveToFirst()) {
				do{
					id_buffer = creq.getInt(creq.getColumnIndex("_id"));
					if (ids_to_ignore==null) {
						read_this_id = true;
					}
					else if (ids_to_ignore.contains(id_buffer)) {
						read_this_id = false;
					}
					else {
						read_this_id = true;
					}
					if (read_this_id) {
						// Retrieve a  meal bolus entry
						_id = creq.getInt(creq.getColumnIndex("_id"));
						time = creq.getInt(creq.getColumnIndex("time"));
						time_announce = creq.getInt(creq.getColumnIndex("time_announce"));
						meal_size_grams = creq.getInt(creq.getColumnIndex("meal_size_grams"));
						SMBG = creq.getInt(creq.getColumnIndex("SMBG"));
						meal_screen_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_bolus"));
						type = creq.getInt(creq.getColumnIndex("type"));
						size = creq.getInt(creq.getColumnIndex("size"));
						extended_bolus_duration_seconds = creq.getInt(creq.getColumnIndex("extended_bolus_duration_seconds"));
						meal_screen_meal_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_meal_bolus"));
						meal_screen_corr_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_corr_bolus"));
						meal_screen_smbg_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_smbg_bolus"));
						extended_bolus_insulin_rem = creq.getDouble(creq.getColumnIndex("extended_bolus_insulin_rem"));
						treated_int = creq.getInt(creq.getColumnIndex("treated"));
						if (treated_int == 0)
							treated=false;
						else
							treated=true;
						active_int = creq.getInt(creq.getColumnIndex("active"));
						if (active_int == 0)
							active=false;
						else
							active=true;
						approved_int = creq.getInt(creq.getColumnIndex("approved"));
						if (approved_int == 0)
							approved=false;
						else
							approved=true;
						extended_bolus_int = creq.getInt(creq.getColumnIndex("extended_bolus"));
						if (extended_bolus_int == 0)
							extended_bolus=false;
						else
							extended_bolus=true;
						empty = false;
						return_value = true;
						// Check for matching time stamp (this happens when a meal is active)
						if (active) {
							creq.close();
							return true;
						}
					}
				} while (creq.moveToNext());
			}
			else {
				time_announce = 0;
				time = 0;
				meal_size_grams = 0.0;
				SMBG = 0.0;
				meal_screen_bolus = 0.0;
				type = 0;
				treated = false;
				active = false;
				approved = false;
				extended_bolus = false;
				extended_bolus_duration_seconds = 0;
				meal_screen_meal_bolus = 0.0;
				meal_screen_corr_bolus = 0.0;
				meal_screen_smbg_bolus = 0.0;
				extended_bolus_insulin_rem = 0.0;
				empty = true;
			}
			creq.close();
        }
        catch (Exception e) {
        	debug_message("updateMeal > Error ", e.getMessage());
        }
        return return_value;
	}

	// Update the local copy of the Meal data with the last record from the "meal" table 
	public boolean read() {
		boolean return_value = false;
		int treated_int = 0;
		int active_int = 0;
		int approved_int = 0;
		int extended_bolus_int = 0;
        try {
			// Fetch meal data
			Cursor creq=context.getContentResolver().query(MEAL_URI, null, null, null, null);
			if (creq.moveToLast()) {
				// Save the latest timestamp from the retrieved data
				_id = creq.getInt(creq.getColumnIndex("_id"));
				time = creq.getInt(creq.getColumnIndex("time"));
				time_announce = creq.getInt(creq.getColumnIndex("time_announce"));
				meal_size_grams = creq.getInt(creq.getColumnIndex("meal_size_grams"));
				SMBG = creq.getInt(creq.getColumnIndex("SMBG"));
				meal_screen_bolus = creq.getInt(creq.getColumnIndex("meal_screen_bolus"));
				type = creq.getInt(creq.getColumnIndex("type"));
				size = creq.getInt(creq.getColumnIndex("size"));
				extended_bolus_duration_seconds = creq.getInt(creq.getColumnIndex("extended_bolus_duration_seconds"));
				meal_screen_meal_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_meal_bolus"));
				meal_screen_corr_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_corr_bolus"));
				meal_screen_smbg_bolus = creq.getDouble(creq.getColumnIndex("meal_screen_smbg_bolus"));
				extended_bolus_insulin_rem = creq.getDouble(creq.getColumnIndex("extended_bolus_insulin_rem"));
				treated_int = creq.getInt(creq.getColumnIndex("treated"));
				if (treated_int == 0)
					treated=false;
				else
					treated=true;
				active_int = creq.getInt(creq.getColumnIndex("active"));
				if (active_int == 0)
					active=false;
				else
					active=true;
				approved_int = creq.getInt(creq.getColumnIndex("approved"));
				if (approved_int == 0)
					approved=false;
				else
					approved=true;
				extended_bolus_int = creq.getInt(creq.getColumnIndex("extended_bolus"));
				if (extended_bolus_int == 0)
					extended_bolus=false;
				else
					extended_bolus=true;
				empty = false;
				return_value = true;
			}
			else {
				_id = -1;
				time_announce = 0;
				time = 0;
				meal_size_grams = 0.0;
				SMBG = 0.0;
				meal_screen_bolus = 0.0;
				type = 0;
				treated = false;
				active = false;
				approved = false;
				extended_bolus = false;
				extended_bolus_duration_seconds = 0;
				meal_screen_meal_bolus = 0.0;
				meal_screen_corr_bolus = 0.0;
				meal_screen_smbg_bolus = 0.0;
				extended_bolus_insulin_rem = 0.0;
				empty = true;
			}
			creq.close();
        }
        catch (Exception e) {
        	debug_message("read > Error ", e.getMessage());
        }
        return return_value;
	}

	// Update the current row in the table based on the local values
	public boolean update() {
		boolean return_value = false;
	    ContentValues values = new ContentValues();
	    values.put("_id", _id);
	    values.put("time", time);
	    values.put("time_announce", time_announce);
	    values.put("meal_size_grams", meal_size_grams);
	    values.put("SMBG", SMBG);
	    values.put("meal_screen_bolus", meal_screen_bolus);
	    values.put("type", type);
	    values.put("size", size);
	    values.put("extended_bolus_duration_seconds", extended_bolus_duration_seconds);
	    values.put("meal_screen_meal_bolus", meal_screen_meal_bolus);
	    values.put("meal_screen_corr_bolus", meal_screen_corr_bolus);
	    values.put("meal_screen_smbg_bolus", meal_screen_smbg_bolus);
	    values.put("extended_bolus_insulin_rem", extended_bolus_insulin_rem);
	    if (active)
	    	values.put("active", 1);
	    else
	    	values.put("active", 0);
	    if (approved)
	    	values.put("approved", 1);
	    else
	    	values.put("approved", 0);
	    if (treated)
	    	values.put("treated", 1);
	    else
	    	values.put("treated", 0);
	    if (extended_bolus)
	    	values.put("extended_bolus", 1);
	    else
	    	values.put("extended_bolus", 0);
	    if (_id != -1) {		// Valid _id field
		    try {
		    	context.getContentResolver().update(MEAL_URI, values, "_id="+_id, null);
		    	return_value = true;
		    }
		    catch (Exception e) {
	    			Log.e(TAG, e.getMessage());
		    }
	    }
	    else {
			log_IO(TAG, "Meal > update > meal record not updated");
	    }
	    return return_value;
	}
    
	// Insert a new row in the table based on the local values
	public void save() {
	    ContentValues values = new ContentValues();
	    values.put("time", time);
	    values.put("time_announce", time_announce);
	    values.put("meal_size_grams", meal_size_grams);
	    values.put("SMBG", SMBG);
	    values.put("meal_screen_bolus", meal_screen_bolus);
	    values.put("type", type);
	    values.put("size", size);
	    values.put("extended_bolus_duration_seconds", extended_bolus_duration_seconds);
	    values.put("meal_screen_meal_bolus", meal_screen_meal_bolus);
	    values.put("meal_screen_corr_bolus", meal_screen_corr_bolus);
	    values.put("meal_screen_smbg_bolus", meal_screen_smbg_bolus);
	    values.put("extended_bolus_insulin_rem", extended_bolus_insulin_rem);
	    if (active)
	    	values.put("active", 1);
	    else
	    	values.put("active", 0);
	    if (approved)
	    	values.put("approved", 1);
	    else
	    	values.put("approved", 0);
	    if (treated)
	    	values.put("treated", 1);
	    else
	    	values.put("treated", 0);
	    if (extended_bolus)
	    	values.put("extended_bolus", 1);
	    else
	    	values.put("extended_bolus", 0);
	    try {
	    	context.getContentResolver().insert(MEAL_URI, values);
	    }
	    catch (Exception e) {
    			Log.e(TAG, e.getMessage());
	    }
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
