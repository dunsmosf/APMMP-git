//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.DiAsSetup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
        // Do whatever you want on boot
		SharedPreferences prefs = context.getSharedPreferences(DiAsSetup1.PREFS_NAME, 0);
		// Clear certain saved data on reboot 
		prefs.edit()
			.remove("connectedNames") // Make sure all drivers are set as available but not connected, to reflect reality
			.apply();
	}

}
