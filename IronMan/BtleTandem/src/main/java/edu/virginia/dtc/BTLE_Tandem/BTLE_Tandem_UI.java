package edu.virginia.dtc.BTLE_Tandem;

import java.util.Timer;
import java.util.TimerTask;

import edu.virginia.dtc.BTLE_Tandem.BTLE_Tandem_Driver.listDevice;
import edu.virginia.dtc.SysMan.Debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class BTLE_Tandem_UI extends Activity {

	private static final String TAG = "BTLE_Tandem_UI";
	
	// Messages from UI
	public static final int UI2DRIVER_NULL = 0;
	public static final int UI2DRIVER_REGISTER = 1;
	public static final int UI2DRIVER_CONNECT = 2;
	public static final int UI2DRIVER_SCAN = 3;
	public static final int UI2DRIVER_STATUS = 4;
	public static final int UI2DRIVER_RECON = 5;
	public static final int UI2DRIVER_SERVICE = 6;
	public static final int UI2DRIVER_ERASE = 7;
	
	private ListView list;
	private Button scan;
	private ArrayAdapter<String> listAdapter;
	
	private Handler handler = new Handler();
    private Timer updateTimer;
    private Runnable update;
    
    private ServiceConnection UItoDriver = null;
	private final Messenger messengerFromDriver = new Messenger(new incomingDriverHandler());
	private Messenger toDriver = null;
	
    private OnItemClickListener clickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, final int arg2, final long arg3) {
         
        	final String FUNC_TAG = "clickListener";
        	
        	AlertDialog.Builder alert = new AlertDialog.Builder(v.getContext());
        	
        	if(BTLE_Tandem_Driver.settings.getBoolean("paired", false))
        	{
        		alert.setTitle("Pairing");
        		
        		alert.setMessage("The device is currently paired, you must unpair the current device before pairing to a new one!");
        		
        		alert.setNeutralButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
					}
				});
        	}
        	else
        	{
		    	alert.setTitle("Pairing");
	
		    	alert.setPositiveButton("Connect", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						
						Debug.i(TAG, FUNC_TAG, "Size: "+BTLE_Tandem_Driver.devices.size());
						Debug.i(TAG, FUNC_TAG, "Getting item: "+arg2+" > "+BTLE_Tandem_Driver.devices.get(arg2).address);
						
						Bundle data = new Bundle();
						data.putInt("index", arg2);
						sendDataMessage(toDriver, data,  UI2DRIVER_CONNECT);
					}
				});
		    	
				alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						
					}
				});
        	}
	    	
	    	alert.show();
        }
    };
    
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.main);
		
		list = (ListView)this.findViewById(R.id.listView1);
		listAdapter = new ArrayAdapter<String> (this, R.layout.list_name);
		list.setAdapter(listAdapter);
		list.setOnItemClickListener(clickListener);
		
		scan = (Button)this.findViewById(R.id.button1);
		
		update = new Runnable()
		{
			public void run()
			{
				update();
			}
		};
		
		updateTimer = new Timer();
		TimerTask ui = new TimerTask()
		{
			public void run()
			{
				handler.postDelayed(update, 0);
			}
		};
		updateTimer.scheduleAtFixedRate(ui, 0, 1000);
		
		// Setup connection between UI and driver
		UItoDriver = new ServiceConnection(){
			public void onServiceConnected(ComponentName arg0, IBinder arg1) {
				Log.i(TAG,"Connecting to driver service!");
				toDriver = new Messenger(arg1);
				
				Message msg = Message.obtain(null, UI2DRIVER_REGISTER);
				msg.replyTo = messengerFromDriver;
				
				try {
					toDriver.send(msg);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			public void onServiceDisconnected(ComponentName arg0) {
				Log.i(TAG,"Connection to driver terminated...");
			}
		};
		
		// Start bound driver service
		Intent intent = new Intent(BTLE_Tandem_Driver.UI_Intent);
		bindService(intent, UItoDriver, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}
	
	class incomingDriverHandler extends Handler {
		@Override
		public void handleMessage(Message msg) 
		{
			switch(msg.what)
			{
				
			}
		}
	}
	
	public void onScanClick(View v)
	{
		final String FUNC_TAG = "onScanClick";
		
		Debug.i(TAG, FUNC_TAG, "Scan press...");
		
		if(!BTLE_Tandem_Driver.scanning)
		{
			Debug.i(TAG, FUNC_TAG, "Scanning from UI!");
			sendDataMessage(toDriver, null,  UI2DRIVER_SCAN);
		}
	}
	
	public void onEraseClick(View v)
	{
		AlertDialog.Builder alert = new AlertDialog.Builder(v.getContext());
    	alert.setTitle("Erase Pairing");
    	alert.setMessage("Are you sure you want to erase the pairing data?  (If so, be sure to un-pair the pump too!)");
    	alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				sendDataMessage(toDriver, null, UI2DRIVER_ERASE);
			}
		});
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
			}
		});
    	alert.show();
	}
	
	public void onServiceClick(View v)
	{
		sendDataMessage(toDriver, null, UI2DRIVER_SERVICE);
	}
	
	public void onStatusClick(View v)
	{
		sendDataMessage(toDriver, null,  UI2DRIVER_STATUS);
		BTLE_Tandem_Driver.status = "Unknown";
		BTLE_Tandem_Driver.fluid = "Unknown";
		update();
	}
	
	public void onReconClick(View v)
	{
		AlertDialog.Builder alert = new AlertDialog.Builder(v.getContext());
    	
    	alert.setTitle("Cycle BT Radio");
    	alert.setMessage("Are you sure you want to cycle the BT radio?  This can take about 20 seconds.");
    	alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				sendDataMessage(toDriver, null,  UI2DRIVER_RECON);
			}
		});
    	
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				
			}
		});
    	
    	alert.show();
	}
	
	private void sendDataMessage(Messenger messenger, Bundle bundle, int what)
	{
		if(messenger != null)
		{
			Message msg = Message.obtain(null, what);
			msg.setData(bundle);
			
			try{
				messenger.send(msg);
			}
			catch(RemoteException e) {
				e.printStackTrace();
			}
		}
		else
			Log.i(TAG, "Messenger is not connected or is null!");
	}
	
	public void update()
	{
		final String FUNC_TAG = "update";
		
		listAdapter.clear();
		
		scan.setEnabled(!BTLE_Tandem_Driver.scanning);
		
		this.findViewById(R.id.Button01).setEnabled(BTLE_Tandem_Driver.buttons);
		this.findViewById(R.id.button2).setEnabled(BTLE_Tandem_Driver.buttons);
		this.findViewById(R.id.button3).setEnabled(BTLE_Tandem_Driver.buttons);
		
		TextView tv = (TextView)findViewById(R.id.textView4);
		tv.setText("Status: "+BTLE_Tandem_Driver.status);
		
		tv = (TextView)findViewById(R.id.textView2);
		if(BTLE_Tandem_Driver.devMac.equalsIgnoreCase(""))
			tv.setText("Connected to Pump: N/A");
		else
		{
			String name;
			name = "NULL";
			
			
			if(BTLE_Tandem_Driver.dev.getName() != null)
				name = BTLE_Tandem_Driver.dev.getName();
			
			tv.setText("Connected to: "+name+" - "+BTLE_Tandem_Driver.devMac);
		}
		
		tv = (TextView)findViewById(R.id.textView1);
		tv.setText("BTLE Status: "+BTLE_Tandem_Driver.btleStatus);
		
		tv = (TextView)findViewById(R.id.TextView01);
		tv.setText("Fluid: "+BTLE_Tandem_Driver.fluid);
		
		if(!BTLE_Tandem_Driver.devices.isEmpty())
		{
			for(listDevice d:BTLE_Tandem_Driver.devices)
			{
				String s = d.address;
				String sub = s.substring(0, 8);
				
				if(d.dev.getName() != null)
				{
					//After connection it should have the name of the device "tslim"
					listAdapter.add(d.dev.getName()+"\n"+s);
				}
				else
					listAdapter.add("Tandem\n"+s);
			}
		}
	}
}
