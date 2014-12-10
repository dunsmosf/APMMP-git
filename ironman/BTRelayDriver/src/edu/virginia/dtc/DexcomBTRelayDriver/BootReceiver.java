package edu.virginia.dtc.DexcomBTRelayDriver;

import edu.virginia.dtc.SysMan.Debug;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

	private static final String TAG = "USBDexcomLocalDriver";

	@Override
	public void onReceive(Context context, Intent intent) {
		final String FUNC_TAG = "onReceive";

		Debug.i(TAG, FUNC_TAG, "Starting Dexcom Driver via Boot Receiver!");
		Intent startActivityIntent = new Intent(context, DexcomBTRelayDriverUI.class);
        startActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(startActivityIntent);
	}
}
