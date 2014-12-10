package edu.virginia.dtc.SysMan;

import java.util.Set;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Broadcast 
{
	public static void sendBroadcast(Context context, Intent intent)
	{
		Set<String> keys = intent.getExtras().keySet();
		
		context.sendBroadcast(intent);
	}
	
	public static void startService(Context context, Intent intent)
	{		
		Set<String> keys = intent.getExtras().keySet();
		
		context.startActivity(intent);
	}
}
