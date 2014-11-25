package edu.virginia.dtc.HR_Driver;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.AdapterView.OnItemClickListener;
import edu.virginia.dtc.HR_Driver.HR_Driver.listDevice;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Params;

public class HR_DriverUI extends Activity {
	
	private static final String TAG = "HR_Driver";

	// Messages from Driver
	private static final int DRIVER2UI_NULL = 0;
	private static final int DRIVER2UI_DEV_STATUS = 1;
	private static final int DRIVER2UI_NEW_HR = 3;
	private static final int DRIVER2UI_RESTING_HR=4;
	
	// Messages to Driver
	private static final int UI2DRIVER_NULL = 0;
	private static final int UI2DRIVER_REGISTER = 1;
	private static final int UI2DRIVER_HR_START = 2;
	private static final int UI2DRIVER_HR_STOP = 3;
	private static final int UI2DRIVER_HR_FINISH = 4;
	private int DRIVER_STATUS;
	
	// CGM Service OnStartCommand messages
	public static final int HR_SERVICE_CMD_NULL = 0;
	public static final int HR_SERVICE_CMD_DISCONNECT = 1;
	public int HR_SERVICE_CMD_INIT = 2;
  	
	private int battery;
	
	private ListView list;
	private ArrayAdapter<String> listAdapter;
	private ListView hr_values;
	private TextView batterylife, connection_status, hr_rest;
	private ArrayAdapter<String> hr_val_List;
	private ToggleButton controller_button;
	public static Button scan, disconnect_button;
	
	private ServiceConnection UItoDriver = null;
	private final Messenger messengerFromDriver = new Messenger(new incomingDriverHandler());
	private Messenger toDriver = null;
	
	private Driver thisDriver;
	SharedPreferences exercise_ctrl;
	
	private OnItemClickListener clickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, final int arg2, final long arg3) {
         
        	final String FUNC_TAG = "clickListener";
        	
        	final BluetoothDevice d = HR_Driver.devices.get(arg2).dev;
			Debug.i(TAG, FUNC_TAG, "Device selected: "+d.getName()+" "+d.getAddress());
        	
        	AlertDialog.Builder alert = new AlertDialog.Builder(v.getContext());
        	

        	if(HR_Driver.hxm.getDevice() != null && HR_Driver.hxm.getDevice().getAddress().equalsIgnoreCase(d.getAddress()))
        	{
        		alert.setTitle("Connection");
        		alert.setMessage("This device is currently connected!");
		    	alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) 
					{
					}
				});
        	}
        	else
        	{
		    	alert.setTitle("Connect to "+d.getName()+" "+d.getAddress()+"?");
		    	alert.setPositiveButton("Connect", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) 
					{
						//Connect HxM
						Message connect_msg = Message.obtain(null, UI2DRIVER_HR_START, thisDriver.my_state_index, 0);
						Bundle b = new Bundle();
						b.putString("mac", d.getAddress());
						connect_msg.setData(b);
						try {
							toDriver.send(connect_msg);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				});
				alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) 
					{
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
		
		final String FUNC_TAG = "onCreate";
		
		setContentView(R.layout.main);
		setResult(Activity.RESULT_CANCELED);
		
		Debug.i(TAG, FUNC_TAG, "");
		
		//GET_FROM_EX_SERVICE("HR_REST");
		
		list = (ListView)this.findViewById(R.id.ListView01);
		listAdapter = new ArrayAdapter<String> (this, R.layout.devsettings);
		list.setAdapter(listAdapter);
		list.setOnItemClickListener(clickListener);
		
		hr_values = (ListView) this.findViewById(R.id.ListView2);
		hr_val_List = new ArrayAdapter<String> (this,R.layout.listsettings);
		hr_values.setAdapter(hr_val_List);
		
		batterylife = (TextView) this.findViewById(R.id.textView3);
		connection_status = (TextView) this.findViewById(R.id.TextView01);
		
		scan= (Button)this.findViewById(R.id.button1);
		disconnect_button=(Button)this.findViewById(R.id.button2);
		controller_button= (ToggleButton)this.findViewById(R.id.togglebutton);
		hr_rest = (TextView)this.findViewById(R.id.hr_rest);
		
		controller_button= (ToggleButton)this.findViewById(R.id.togglebutton);
		
		//Initialize Exercise Controller indicator
		controller_button.setOnClickListener( new View.OnClickListener() {
			
			public void onClick(View v) {
				
				Debug.i(TAG, FUNC_TAG, "Toggle value  "+controller_button.isChecked());
				exercise_ctrl = getSharedPreferences(HR_Driver.PREFS, Context.MODE_WORLD_READABLE);
				Editor editor = exercise_ctrl.edit();
				editor.putBoolean("Control", controller_button.isChecked());
				editor.commit();
				
				Intent i = new Intent();
				i.putExtra("toggle_value", controller_button.isChecked());
				i.setAction("edu.virginia.dtc.intent.EXERCISE_TOGGLE_BUTTON");
				sendBroadcast(i);
			}
		});
		
		scan.setOnClickListener( new View.OnClickListener() {
			public void onClick(View v) {
				HR_Driver.devices.clear();
				
				if(HR_Driver.bt.isDiscovering())
					HR_Driver.bt.cancelDiscovery();
				
				HR_Driver.bt.startDiscovery();
				
				scan.setEnabled(false);
			}
		});
		
		disconnect_button.setOnClickListener( new View.OnClickListener() {
			public void onClick(View v) {				
				Message disconnect_msg = Message.obtain(null, UI2DRIVER_HR_STOP, thisDriver.my_state_index, 0);
				try {
					toDriver.send(disconnect_msg);
					
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		});
				
		// Setup connection between UI and driver
		UItoDriver = new ServiceConnection()
		{
			public void onServiceConnected(ComponentName arg0, IBinder arg1) 
			{
				Debug.i(TAG, FUNC_TAG, "Connecting to driver service!");
				toDriver = new Messenger(arg1);
				
				Message msg = Message.obtain(null, UI2DRIVER_REGISTER);
				msg.replyTo = messengerFromDriver;
				
				try {
					toDriver.send(msg);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			public void onServiceDisconnected(ComponentName arg0) 
			{
				Debug.i(TAG, FUNC_TAG, "Connection to driver terminated...");
			}
		};
		
		Debug.i(TAG, FUNC_TAG, "Ex detection param =   "+Params.getInt(getContentResolver(), "exercise_detection_mode", 0));
		
		if (Params.getInt(getContentResolver(), "exercise_detection_mode", 0) == 2)
		{
			Debug.i(TAG, FUNC_TAG, "Set Invisible");
			controller_button.setVisibility(View.GONE);
		}
		
		// Start bound driver service
		Intent intent = new Intent(Driver.UI_INTENT);
		bindService(intent, UItoDriver, Context.BIND_AUTO_CREATE);
		
		updateUI();
	}
	
	@Override
	public void onStart()
	{
		super.onStart();
		exercise_ctrl = getSharedPreferences(HR_Driver.PREFS, Context.MODE_WORLD_READABLE);		
		controller_button.setChecked(exercise_ctrl.getBoolean("Control", true));
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		final String FUNC_TAG = "onDestroy";
		
		// Unbind connection to driver
		if(UItoDriver!=null)
		{
			Debug.i(TAG, FUNC_TAG, "unbinding service...");
			unbindService(UItoDriver);
		}
	}
	
	class incomingDriverHandler extends Handler 
	{
		final String FUNC_TAG = "incomingDriverHandler";
		private SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm:ss.SSS");
		
		@Override
		public void handleMessage(Message msg) 
		{
			switch(msg.what)
			{
				case DRIVER2UI_NULL:
					break;
				case DRIVER2UI_DEV_STATUS:
					break;
				case DRIVER2UI_NEW_HR:
					battery = msg.arg2;
					
					hr_val_List.insert("HR: "+Integer.toString(msg.arg1) + " | Date: "+ sdf.format(new Date((Long)msg.obj)), 0);		
					
					if (hr_rest.getText().equals("Resting HR"))
					{
						Debug.i(TAG, FUNC_TAG, "Getting resting heartrate...");
						GET_FROM_EX_SERVICE("HR_REST");
					}
					break;
				case DRIVER2UI_RESTING_HR:
					hr_rest.setText("Resting HR: "+Integer.toString(msg.arg1));
					break;
			}
			
			updateUI();
		}
	}
	
	public void GET_FROM_EX_SERVICE(String param)
	{
		//Get HR rest
		Intent HRIntent = new Intent("edu.virginia.dtc.HR_Driver.intent.action.GET");
 		HRIntent.putExtra("REQUEST", param);
	 	sendBroadcast(HRIntent);
	}
	
	public void updateUI()
	{
		thisDriver = Driver.getInstance();

		listAdapter.clear();
		
		String status = "Status: N/A";
		String batt = "Battery: N/A";
		
		if(HR_Driver.hxm.getDevice() != null)
		{
			if(HR_Driver.hxm.isConnected())
			{
				status = "Status: Connected to "+HR_Driver.hxm.getDevice().getName();
				batt = "Battery: "+battery+"%";
			}
			else
			{
				status = "Status: Disconnected";
				batt = "Battery: N/A";
			}
		}
		
		connection_status.setText(status);
		batterylife.setText(batt);
		
		if(HR_Driver.devices != null && !HR_Driver.devices.isEmpty())
		{
			for(listDevice d:HR_Driver.devices)
				listAdapter.add(d.dev.getName()+"\n"+d.address);
		}
		
		Intent intent = new Intent("edu.virginia.dtc.DEVICE_RESULT");
		intent.putExtra("hr", 1);
		intent.putExtra("started", true);
		intent.putExtra("name", "HR_Driver");
		sendBroadcast(intent);
	}
}
