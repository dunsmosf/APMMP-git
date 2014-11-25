//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.app.Service;
import android.content.Intent;
import android.content.ComponentName;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import java.util.List;

public class HMSServiceShutdown extends Activity {
	private static final String TAG = "Shutdown HMSservice";
	public static final int DIAS_SERVICE_COMMAND_EXIT = -978;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "myUid="+android.os.Process.myUid());
        
        // Stop SafetyService
		Intent intent = new Intent();
		intent.setClassName("edu.virginia.dtc.HMSservice", "edu.virginia.dtc.HMSservice.HMSservice");
        stopService(intent);
        
        // Get currently running application processes
        ActivityManager manager = (ActivityManager)this.getSystemService(ACTIVITY_SERVICE );
        List<ActivityManager.RunningAppProcessInfo> list = manager.getRunningAppProcesses();
        boolean killedPID;
        int pid = -1;
        if(list != null){
        		for(int i=0;i<list.size();++i){
        			killedPID = false;
        			if("edu.virginia.dtc.HMSservice".matches(list.get(i).processName)){
        				pid = list.get(i).pid;
        				android.os.Process.killProcess(pid);
            			killedPID = true;
        			}
       			if (killedPID)
        				Log.i(TAG, "Killed "+ list.get(i).processName +", pid="+pid);
        			else
        				Log.i(TAG, list.get(i).processName+" not killed.");
        		}
        	}
        finish();
        System.exit(0);
    }

}
