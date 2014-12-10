package edu.virginia.dtc.RocheDriver;

import java.lang.reflect.Method;
import java.security.InvalidKeyException;
import java.util.Set;

import edu.virginia.dtc.RocheData.Application;
import edu.virginia.dtc.RocheData.Transport;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.TwoFish.Twofish_Algorithm;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class DiscoveryFragment extends Fragment {

	private static final String TAG = "DiscoveryFragment";
	
	public static final int STARTUP = 0;
	public static final int ROCHE_SEARCH = 1;
	public static final int ROCHE_CONNECTING = 2;
	public static final int ROCHE_CONNECTED = 3;
	public static final int ROCHE_AUTHENTICATED = 4;
	
	public static final int DISCOVER_TIME = 10;
	
	private Driver drv = Driver.getInstance();
	public static View view;
	public static Context context;
	
	private TextView info;
	private Button button;
	private ProgressBar progress;
	private CheckBox history, time, started;
	private LinearLayout checkLayout;
	
	private InterfaceData data;
	
	public DiscoveryFragment()
	{
		final String FUNC_TAG = "onCreate";
		
		Debug.i(TAG, FUNC_TAG, "DiscoveryFragment");
		
		data = InterfaceData.getInstance();
	}
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	public void onDestroy()
	{
		super.onDestroy();
		Debug.i(TAG, "onDestroy", "...");
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final String FUNC_TAG = "onCreateView";

		view = inflater.inflate(R.layout.discovery, container, false);
		
		Debug.i(TAG, FUNC_TAG, "Creating view...");
		
		if(view == null)
			Debug.i(TAG, FUNC_TAG, "View is null!");
		
		DiscoveryFragment.context = this.getActivity();
		
		button = (Button)view.findViewById(R.id.button);
		button.setOnClickListener(new OnClickListener(){
			public void onClick(View v) 
			{
				switch(Driver.uiState)
				{
					case STARTUP:
						drv.sdpFound = false;
						
						Set<BluetoothDevice> bondedDevices = data.bt.getBondedDevices();
					    for(BluetoothDevice b:bondedDevices)
					    	Debug.i(TAG, FUNC_TAG, "Device: "+b.getName()+" "+b.getBondState()+" "+b.getAddress());
						
					    if(!pumpBonded(bondedDevices))
					    {
				        	Debug.i(TAG, FUNC_TAG, "No bonded pump devices!");
					    
							if (data.bt.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) 
							{
					            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
					            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVER_TIME);
					            startActivity(discoverableIntent);
					            
					            Driver.uiState = ROCHE_SEARCH;
							}
							else
							{
								Debug.i(TAG, FUNC_TAG, "Cancel Discovery "+data.bt.cancelDiscovery());
							}
					    }
						break;
					case ROCHE_SEARCH:
						Driver.uiState = ROCHE_CONNECTING;
						break;
					case ROCHE_CONNECTED:
					case ROCHE_CONNECTING:
						drv.sdpFound = false;
						
						if(Driver.timeoutTimer != null)
							Driver.timeoutTimer.cancel(true);
						
						if(data.bt.isDiscovering())
							data.bt.cancelDiscovery();
						
						InterfaceData.remotePumpBt.stop();
						InterfaceData.remotePumpBt.listen();
						
						Driver.uiState = STARTUP;
						break;
					case ROCHE_AUTHENTICATED:
						
						/*
						if(Driver.getMode() == Driver.RT)
							drv.a.startMode(Driver.COMMAND);
						else
							drv.a.startMode(Driver.RT);
						*/
						
						//drv.a.cancelBolus();
						
						//drv.a.setTbr();
						
						AlertDialog.Builder alert = new AlertDialog.Builder(context);
				    	
				    	alert.setTitle("Erase Pairing Data");
						alert.setMessage("Are you sure you want to erase the pairing data?");
						
						alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								
								Bundle b = new Bundle();
					    		b.putString("description", "The pump has been unpaired manually by the user");
								Event.addEvent(drv.serv, Event.EVENT_PUMP_PAIR, Event.makeJsonString(b), Event.SET_LOG);
								
								ContentValues dv = new ContentValues();

								Debug.e("Application", FUNC_TAG, "Removing running pump DB value!");
								dv.put("running_pump", "");					
								
								drv.serv.getContentResolver().update(Biometrics.HARDWARE_CONFIGURATION_URI, dv, null, null);
								
//								Debug.i(TAG, FUNC_TAG, "Stopping Roche Driver");
//								Intent intent = new Intent("edu.virginia.dtc.STOP_DRIVER");
//								intent.putExtra("package", "edu.virginia.dtc.RocheDriver");
//								drv.main.sendBroadcast(intent);
								
								drv.resetDriver();
							}
						});
						
						alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								
							}
						});
						alert.show();
						break;
				}
				
				updateUI();
			}
		});
		
		checkLayout = (LinearLayout)view.findViewById(R.id.layoutCheck);
		checkLayout.setVisibility(View.GONE);
		
		history = (CheckBox)view.findViewById(R.id.checkBox1);
		time = (CheckBox)view.findViewById(R.id.checkBox2);
		started = (CheckBox)view.findViewById(R.id.checkBox3);
		
		history.setText("History");
		time.setText("Time");
		started.setText("Start");
		
		progress = (ProgressBar)view.findViewById(R.id.progressBar);
		info = (TextView)view.findViewById(R.id.infoText);
		
		return view;
	}
	
	public boolean pumpBonded(Set<BluetoothDevice> devs)
	{
		for (BluetoothDevice bluetoothDevice : devs) 
        {
			if(bluetoothDevice.getName() == null)
				continue;
			
        	if(bluetoothDevice.getName().equalsIgnoreCase("SpiritCombo")) 
                return true;
        }
		
		return false;
	}
	
	public void updateUI()
	{
		final String FUNC_TAG = "updateUI";
		String stat = "None";
		
		//Debug.i(TAG, FUNC_TAG, "Driver UI State: " + Driver.uiState);
		
		if(InterfaceData.remotePumpBt!=null)
		{
			switch(InterfaceData.remotePumpBt.getState())
			{
				case BluetoothConn.NONE:
					stat = "None";
					break;
				case BluetoothConn.LISTENING:
					stat = "Listening";
					break;
				case BluetoothConn.CONNECTING:
					stat = "Connecting";
					break;
				case BluetoothConn.CONNECTED:
					stat = "Connected";
					switch(Driver.uiState)
					{
						case ROCHE_CONNECTING:
							Driver.uiState = ROCHE_CONNECTED;
							break;
						case ROCHE_AUTHENTICATED:
//							if(Driver.sendCommand && Driver.getMode() != Driver.PAIRING_AUTH)
//							{
//								Debug.i(TAG, FUNC_TAG, "Sending Start Mode command!");
//								drv.a.startMode(Driver.COMMAND, false);
//								Driver.sendCommand = false;
//							}
							break;
					}
					break;
			}
		}
		
		if(info != null)
		{
			switch(Driver.uiState)
			{
				case STARTUP:
					button.setEnabled(false);
					Set<BluetoothDevice> bondedDevices = data.bt.getBondedDevices();
					if(pumpBonded(bondedDevices))
					{
					    try {
					        Class<?> btDeviceInstance =  Class.forName(BluetoothDevice.class.getCanonicalName());
					        Method removeBondMethod = btDeviceInstance.getMethod("removeBond");
					        
					        if(bondedDevices.isEmpty())
					        	Debug.i(TAG, FUNC_TAG, "No bonded devices!");
					        
					        for (BluetoothDevice bluetoothDevice : bondedDevices) 
					        {
					        	Debug.i(TAG, FUNC_TAG, "DEVICE: "+bluetoothDevice.getName()+" MAC: "+bluetoothDevice.getAddress());
					        	if(bluetoothDevice.getName().equalsIgnoreCase("SpiritCombo")) 
					            {
					                removeBondMethod.invoke(bluetoothDevice);
					                Debug.i(TAG,FUNC_TAG,"Cleared Pairing");
					            }
					        }
					    } 
					    catch (Throwable th) 
					    {
					        Debug.e(TAG,FUNC_TAG,"Error pairing", th);
					    }
					}
					else
						button.setEnabled(true);
					
					checkLayout.setVisibility(View.GONE);
					button.setText("Discoverable");
					info.setText("Please press the Discoverable button below and select Yes when prompted to start discovery for "+DISCOVER_TIME+" seconds.");
					info.setTextSize(18);
					progress.setVisibility(View.GONE);
					break;
				case ROCHE_SEARCH:
					button.setText("OK");
					info.setText("On the pump navigate to \"Bluetooth Settings\" and initiate pairing.  Press OK when complete.");
					info.setTextSize(18);
					break;
				case ROCHE_CONNECTING:
					button.setText("Cancel");
					button.setVisibility(View.VISIBLE);
					info.setText("Bluetooth status: "+stat+"\n\nWhen prompted on the pump screen, select the BT Device Name: "+data.bt.getName());
					info.setTextSize(18);
					progress.setVisibility(View.VISIBLE);
					break;
				case ROCHE_CONNECTED:
					info.setText("Connection complete!");
					info.setTextSize(18);
					
					if(data.bt.isDiscovering())
						data.bt.cancelDiscovery();
					
					if(Driver.sendConnect)
						drv.t.sendConnect();
					Driver.sendConnect = false;
					
					progress.setVisibility(View.GONE);
					
					button.setText("Cancel");
					
					if(drv.txFsm.equalsIgnoreCase("P_A_COMPLETE"))
						Driver.uiState = ROCHE_AUTHENTICATED;
					break;
				case ROCHE_AUTHENTICATED:
					checkLayout.setVisibility(View.VISIBLE);
					button.setText("Reset Pump");
					//button.setText("Cancel Bolus");
					button.setVisibility(View.VISIBLE);
					progress.setVisibility(View.GONE);
					info.setTextSize(9);
					info.setText("Bluetooth Status:\n"+stat+
							"\n\nMode:\n"+getModeString()+
							"\n\nApplication Layer:\n"+drv.appFsm+
							"\n\nTransport Layer:\n"+drv.txFsm+
							"\n\nCommand:\n"+drv.command
							);
					
					history.setChecked(drv.histRead);
					time.setChecked(drv.timeSync);
					started.setChecked(drv.startMode);
					break;
			}
		}
	}
	
	private String getModeString()
	{
		String mode = "Unknown";
		switch(Driver.getMode())
		{
			case Driver.NONE:
				mode = "None";
				break;
			case Driver.COMMAND:
				mode = "Command";
				break;
			case Driver.PAIRING_AUTH:
				mode = "P&A";
				break;
			case Driver.RT:
				mode = "RT";
				break;
			default:
				mode = "Unknown";
				break;
		}
		return mode;
	}
}
