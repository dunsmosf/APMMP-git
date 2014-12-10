//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.supervisor;

import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

//Boot receiver for the supervisor service
public class SupervisorReceiver extends BroadcastReceiver {
	
	final String TAG = "SupervisorReceiver";
	final String FUNC_TAG = "SupervisorReceiver";
	
	@Override
	public void onReceive(Context context, Intent intent) {
		Debug.i(TAG, FUNC_TAG, "onReceive for boot receiver!");
        Intent startActivityIntent = new Intent(context, Supervisor.class);
        startActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(startActivityIntent);
        
        Bundle b = new Bundle();
		b.putString("description", "Device has just completed boot cycle ("+intent.getAction()+")");
		Event.addEvent(context, Event.EVENT_SYSTEM_BOOT_COMPLETE, Event.makeJsonString(b), Event.SET_LOG);
	}
}
