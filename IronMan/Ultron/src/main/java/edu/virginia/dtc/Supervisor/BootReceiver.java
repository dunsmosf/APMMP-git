package edu.virginia.dtc.Supervisor;

import edu.virginia.dtc.SysMan.Event;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class BootReceiver extends BroadcastReceiver
{
	@Override
	public void onReceive(Context context, Intent intent)
    {
        Intent startActivityIntent = new Intent(context, SupervisorActivity.class);
        startActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(startActivityIntent);
        
        Bundle b = new Bundle();
		b.putString("description", "Device has just completed boot cycle ("+intent.getAction()+")");
		Event.addEvent(context, Event.EVENT_SYSTEM_BOOT_COMPLETE, Event.makeJsonString(b), Event.SET_LOG);
	}
}
