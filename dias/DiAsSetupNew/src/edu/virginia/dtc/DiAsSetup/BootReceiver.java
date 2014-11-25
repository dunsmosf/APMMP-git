//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.DiAsSetup;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;

public class BootReceiver extends BroadcastReceiver {

	private Context con;
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		con = context;
		
        // Do whatever you want on boot
		SharedPreferences prefs = context.getSharedPreferences(DiAsSetup1.PREFS_NAME, 0);
		// Clear certain saved data on reboot 
		prefs.edit().remove("connectedNames").apply();
		
		if(!subjectDataExists())
		{
			Debug.i("DiAsSetup1", "BootReceiver", "The subject database is empty after reboot, clearing local DB!");
			prefs.edit().putBoolean("clear", true).commit();
		}
	}
	
	private boolean subjectDataExists()
	{
		final String FUNC_TAG = "subjectDataExists";
		
		Cursor c = con.getContentResolver().query(Biometrics.SUBJECT_DATA_URI, null, null, null, null);
		if(c != null)
		{
			if(c.getCount() > 0)
			{
				return true;
			}
		}
		
		return false;
	}

}
