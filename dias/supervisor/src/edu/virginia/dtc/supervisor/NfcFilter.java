package edu.virginia.dtc.supervisor;

import edu.virginia.dtc.SysMan.Debug;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;

public class NfcFilter extends Activity {

	private final String TAG = "NfcFilter"; 
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";

		super.onCreate(savedInstanceState);
		
		Debug.i(TAG, FUNC_TAG, getIntent().getAction());
		String action = getIntent().getAction();
		
		ActivityManager am = (ActivityManager)getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
		PackageManager pm = getApplicationContext().getPackageManager();
		
		boolean inSetup = false;
		
		for(RunningAppProcessInfo r:am.getRunningAppProcesses())
		{
			if(r.processName.equals("edu.virginia.dtc.DiAsSetup"))
			{
				Debug.i(TAG, FUNC_TAG, "Importance: "+r.importance);
				Debug.i(TAG, FUNC_TAG, "Reason: "+r.importanceReasonCode);
				Debug.i(TAG, FUNC_TAG, "Pid: "+r.importanceReasonPid);
				
				if(r.importance == RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE)
				{
					Debug.i(TAG, FUNC_TAG, "DiAs Setup is in the foreground!");
					inSetup = true;
				}
			}
		}
		
		if(inSetup)
		{
			if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) 
			{
		        Parcelable[] rawMsgs = getIntent().getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
		        if (rawMsgs != null) 
		        {
		            NdefMessage[] msgs = new NdefMessage[rawMsgs.length];
		            
		            for (int i = 0; i < rawMsgs.length; i++) 
		            {
		                msgs[i] = (NdefMessage) rawMsgs[i];
		                for(NdefRecord r:msgs[i].getRecords())
		                {
		                	byte[] payload = r.getPayload();
		                	String textEncoding = ((payload[0] & 0200) == 0) ? "UTF-8" : "UTF-16";
		                    int languageCodeLength = payload[0] & 0077;
		                    String text = "Unknown";
		                    
		                    try 
		                    {
								String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
								text = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
		                    }
		                    catch(Exception e)
		                    {
		                    	Debug.e(TAG, FUNC_TAG, e.getMessage());
		                    }
		                    
		                	Debug.i(TAG, FUNC_TAG, text);
		                	
		                	parseText(text);
		                }
		            }
		        }
		    }
		}
		else
			Debug.w(TAG, FUNC_TAG, "User is not at the setup screen...");
		
		finish();
	}
	
	private void parseText(String msg)
	{
		final String FUNC_TAG = "parseText";
		
		String type = msg.split("-")[0];
		Intent driver = new Intent();
		
		Debug.i(TAG, FUNC_TAG, "Type: "+type);
		
		if(type.equalsIgnoreCase("share"))
		{
			String mac = msg.split("-")[1];
			String code = msg.split("-")[2];
			
			Debug.i(TAG, FUNC_TAG, "MAC: "+mac+" Code: "+code);
			
			driver.setClassName("edu.virginia.dtc.BTLE_G4", "edu.virginia.dtc.BTLE_G4.BTLE_G4_Driver");
			driver.putExtra("auto", true);
			driver.putExtra("nfc", true);
			driver.putExtra("mac", mac);
			driver.putExtra("code", code);
			startService(driver);
		}
		else
		{
			Debug.i(TAG, FUNC_TAG, "Unknown...");
		}
	}
	
}
