package edu.virginia.dtc.Bioharness_Driver;

import edu.virginia.dtc.Bioharness_Driver.Bioharness_Driver.listDevice;
import edu.virginia.dtc.SysMan.Debug;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
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
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.AdapterView.OnItemClickListener;

public class Bioharness_DriverUI extends Activity {
	
	private static final String TAG = "Bioharness_Driver_UI";
	
	// Messages from Driver
	private static final int DRIVER2UI_NULL = 0;
	private static final int DRIVER2UI_DEV_STATUS = 1;
	private static final int DRIVER2UI_FINISH = 2;
	private static final int DRIVER2UI_NEW_HR = 3;
	private static final int DRIVER2UI_RESTING_HR=4;
	
	// Messages to Driver
	private static final int UI2DRIVER_NULL = 0;
	private static final int UI2DRIVER_REGISTER = 1;
	private static final int UI2DRIVER_HR_START = 2;
	private static final int UI2DRIVER_HR_STOP = 3;
	
	// CGM Service OnStartCommand messages
	public static final int HR_SERVICE_CMD_NULL = 0;
	public static final int HR_SERVICE_CMD_DISCONNECT = 1;
	public int HR_SERVICE_CMD_INIT = 2;
  	
	private ListView list;
	private ArrayAdapter<String> listAdapter;
	private ListView hr_values;
	private TextView batterylife, connection_status;
	public static ArrayAdapter<String> hr_val_List;
	private ToggleButton controller_button;
	public static Button scan, disconnect_button;
	
	public static int battery;
	
	private ServiceConnection UItoDriver = null;
	private final Messenger messengerFromDriver = new Messenger(new incomingDriverHandler());
	private Messenger toDriver = null;
	
	private Driver thisDriver;
	public SharedPreferences exercise_ctrl;
	private boolean Ex_ctr_button_checked=true;
	
	private OnItemClickListener clickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, final int arg2, final long arg3) {
         
        	final String FUNC_TAG = "clickListener";
        	
        	final BluetoothDevice d = Bioharness_Driver.devices.get(arg2).dev;
			Debug.i(TAG, FUNC_TAG, "Device selected: "+d.getName()+" "+d.getAddress());
        	
        	AlertDialog.Builder alert = new AlertDialog.Builder(v.getContext());
        	
        	if(Bioharness_Driver.bh.getDevice() != null && Bioharness_Driver.bh.getDevice().getAddress().equalsIgnoreCase(d.getAddress()))
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
						//Connect Bioharness
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
		setContentView(R.layout.main);
		
		setResult(Activity.RESULT_CANCELED);
		
		Log.i(TAG,"Created...");
		
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
		
		controller_button.setOnClickListener( new View.OnClickListener() {
			public void onClick(View v) {
				Log.i("Ex_Ctrl", "Toggle value  "+controller_button.isChecked());
				
				exercise_ctrl = getSharedPreferences(Bioharness_Driver.PREFS, Context.MODE_WORLD_READABLE);
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
				Bioharness_Driver.devices.clear();
				
				if(Bioharness_Driver.bt.isDiscovering())
					Bioharness_Driver.bt.cancelDiscovery();
				
				Bioharness_Driver.bt.startDiscovery();
				
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
		UItoDriver = new ServiceConnection(){
			public void onServiceConnected(ComponentName arg0, IBinder arg1) 
			{
				Log.e(TAG,"Connecting to driver service!");
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
				Log.e(TAG,"Connection to driver terminated...");
			}
		};
		
		// Update the UI with any existing data from Driver object
		updateUI();

		// Start bound driver service
		Intent intent = new Intent(Driver.UI_INTENT);
		bindService(intent, UItoDriver, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	public void onStart()
	{
		final String FUNC_TAG = "onStart";
		
		super.onStart();
		
		Debug.i(TAG, FUNC_TAG, "");
		
		exercise_ctrl = getSharedPreferences(Bioharness_Driver.PREFS, Context.MODE_WORLD_READABLE);
		Ex_ctr_button_checked=exercise_ctrl.getBoolean("Control", true);
		controller_button.setChecked(Ex_ctr_button_checked);
	}
		
	@Override
	public void onDestroy()
	{
		final String FUNC_TAG = "onDestroy";
		
		// Unbind connection to driver
		if(UItoDriver != null)
		{
			Log.i(TAG,"unbinding service...");
			unbindService(UItoDriver);
		}
		
		Debug.i(TAG, FUNC_TAG, "");
		
		super.onDestroy();
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		Message msg;
		
		Intent cgmIntent = new Intent();
	    cgmIntent.setClassName("edu.virginia.dtc.ExerciseService", "edu.virginia.dtc.ExerciseService.ExerciseService");
	    
	    thisDriver = Driver.getInstance();
	    switch(item.getGroupId())
	    {
		    case 0:
				switch(item.getItemId())
				{
					case 0:		//Start
						msg = Message.obtain(null, UI2DRIVER_HR_START, thisDriver.my_state_index, 0);
						try {
							toDriver.send(msg);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
						break;
					case 1:		//Stop
						msg = Message.obtain(null, UI2DRIVER_HR_STOP, thisDriver.my_state_index, 0);
						
						try {
							toDriver.send(msg);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
						break;
					case 2:		//Disconnect
						Log.i(TAG,"Disconnecting Zephyr HxM");
						cgmIntent.putExtra("state", thisDriver.my_state_index);
					    cgmIntent.putExtra("dev", 0);
						cgmIntent.putExtra("HRCommand", HR_SERVICE_CMD_DISCONNECT);
						startService(cgmIntent);
						break;
			  	}
				break;
		    case 1:
		    	// To add other sensors
		    	break;
	    }
		
		return true;
	}
	
	class incomingDriverHandler extends Handler {
		@Override
		public void handleMessage(Message msg) 
		{
			switch(msg.what)
			{
				case DRIVER2UI_NULL:
					break;
				case DRIVER2UI_DEV_STATUS:
					break;
				case DRIVER2UI_FINISH:
					finish();
			        android.os.Process.killProcess(android.os.Process.myPid());
					break;
				case DRIVER2UI_NEW_HR:
					battery = msg.arg2;
					hr_val_List.insert("Activity: "+msg.obj.toString() + " || Date: "+msg.arg1, 0);		
					break;
				case DRIVER2UI_RESTING_HR:
					break;
			}
			
			updateUI();
		}
	}
	
	public void GET_FROM_EX_SERVICE(String param){
		//Get HR rest
		Intent HRIntent = new Intent("edu.virginia.dtc.HR_Driver.intent.action.GET");
 		HRIntent.putExtra("REQUEST", param);
	 	sendBroadcast(HRIntent);
	}
	
	public void updateUI()
	{
		thisDriver = Driver.getInstance();
		
		listAdapter.clear();
		
		String batt = "Battery: N/A";
		String status = "Status: N/A";
		
		if(Bioharness_Driver.bh.getDevice() != null)
		{
			if(Bioharness_Driver.bh.isConnected())
			{
				status = "Status: Connected to "+Bioharness_Driver.bh.getDevice().getName();
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
		
		if(Bioharness_Driver.devices != null && !Bioharness_Driver.devices.isEmpty())
		{
			for(listDevice d:Bioharness_Driver.devices)
				listAdapter.add(d.dev.getName()+"\n"+d.address);
		}
		
		Debug.i(TAG, "UpdateUI", "Updating UI");
		
		Intent intent = new Intent("edu.virginia.dtc.DEVICE_RESULT");
		intent.putExtra("hr", 1);
		intent.putExtra("started", true);
		intent.putExtra("name", "HR_Driver");
		sendBroadcast(intent);
	}
	
	// If you press the "Add CGM" button the CGM Service is started
	// and begins the initialization process by binding to the driver
	// (if not already done so) and creating the necessary storage for
	// the incoming data
	public void hrButton(View view)
    {
    	Intent cgmIntent = new Intent();
	    cgmIntent.setClassName("edu.virginia.dtc.ExerciseService", "edu.virginia.dtc.ExerciseService.ExerciseService");
	    cgmIntent.putExtra("driver_intent", Driver.DRIVER_INTENT);
	    cgmIntent.putExtra("driver_name", Driver.DRIVER_NAME);
	    cgmIntent.putExtra("HRCommand", HR_SERVICE_CMD_INIT);
        startService(cgmIntent);
    }
	
	public void sensorClick(View view)
	{
		final String FUNC_TAG = "sensorClick";
		
		Debug.i(TAG, FUNC_TAG, "Sensor control button clicked!");
		
		Intent i = new Intent("edu.virginia.dtc.sensorcontrol");
		sendBroadcast(i);
	}
}
