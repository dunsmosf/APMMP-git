package edu.virginia.dtc.RocheDriver;

import java.security.InvalidKeyException;

import edu.virginia.dtc.RocheData.Application;
import edu.virginia.dtc.RocheData.Transport;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.TwoFish.Twofish_Algorithm;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.ListView;
import android.widget.TextView;

public class SetupFragment extends Fragment {

	private static final String TAG = "SetupFragment";
	
	// Messages from UI
	private static final int UI2DRIVER_NULL = 0;
	private static final int UI2DRIVER_REGISTER = 1;
	private static final int UI2DRIVER_USB_CONNECT = 2;
	private static final int UI2DRIVER_START_SCAN = 3;
	private static final int UI2DRIVER_STOP_SCAN = 4;
	private static final int UI2DRIVER_SET_TIME = 5;
	private static final int UI2DRIVER_CONNECT = 6;
	private static final int UI2DRIVER_DISCONNECT = 7;
	private static final int UI2DRIVER_KEY= 8;
	private static final int UI2DRIVER_PUMPSTATUS = 9;
	private static final int UI2DRIVER_CLEAR_HISTORY = 10;
	private static final int UI2DRIVER_SET_ID = 11;
	
	private Driver drv = Driver.getInstance();
	private ActivityManager am;
	public static View view;
	public RocheUI main;
	public static Context context;
	
	private TextView btStatus, sent, recv, paired, aFsm, tFsm, command;
	private TextView lastBolus, lastTime, currentBolus, currentStatus;
	private TextView histEvents, histRemEvents;
	
	private InterfaceData data;
	private CheckBox connect, bt;
	private Button discover, auth, comm, rt, bolus, history, confirm;
	
	public SetupFragment(final RocheUI main)
	{
		final String FUNC_TAG = "constructor";
		
		this.main = main;
		
		data = InterfaceData.getInstance();
		
		Debug.i(TAG, FUNC_TAG, "onCreate");
	}
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		am = (ActivityManager) main.getSystemService(Context.ACTIVITY_SERVICE);
	}
	
	public void onDestroy()
	{
		super.onDestroy();
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final String FUNC_TAG = "onCreateView";

		view = inflater.inflate(R.layout.setup, container, false);
		
		Debug.i(TAG, FUNC_TAG, "Creating view...");
		
		SetupFragment.context = this.getActivity();
		
		discover = (Button)view.findViewById(R.id.button);
		discover.setOnClickListener(new OnClickListener(){
			public void onClick(View v) 
			{
				if (data.bt.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) 
				{
		            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60);
		            startActivity(discoverableIntent);
				}
			}
		});
		
		auth = (Button)view.findViewById(R.id.auth);
		auth.setOnClickListener(new OnClickListener(){
			public void onClick(View v)
			{
				if(data.bt.isDiscovering())
					data.bt.cancelDiscovery();
				
				Debug.i(TAG, FUNC_TAG, "Sending Authenticate");
				
				drv.t.sendConnect();
			}
		});
		
		comm = (Button)view.findViewById(R.id.comm);
		comm.setOnClickListener(new OnClickListener(){
			public void onClick(View v)
			{
				//drv.a.startMode(Driver.COMMAND);
			}
		});
		
		rt = (Button)view.findViewById(R.id.rt);
		rt.setOnClickListener(new OnClickListener(){
			public void onClick(View v)
			{
				//drv.a.startMode(Driver.RT);
			}
		}); 
		
		bolus = (Button)view.findViewById(R.id.bolus);
		bolus.setOnClickListener(new OnClickListener(){
			public void onClick(View v)
			{
				//drv.a.cmdErrStatus();
				
				/*
				AlertDialog.Builder alert = new AlertDialog.Builder(main);
				final EditText input = new EditText(main);
		    	input.setInputType(InputType.TYPE_CLASS_NUMBER);
		    	
		    	alert.setTitle("Bolus");
		    	alert.setView(input);
		    	alert.setMessage("Please enter a bolus amount in tenths of a unit:");

		    	alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						int id = Integer.parseInt(input.getText().toString());
						if(id > 0 && id < 50)
						{
							drv.a.deliverBolus(id, 0);
						}
					}
				});
		    	
				alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
					}
				});
		    	
		    	alert.show();
		    	*/
			}
		});
		
		history = (Button)view.findViewById(R.id.history);
		history.setOnClickListener(new OnClickListener(){
			public void onClick(View v)
			{
				//drv.a.historyRead();
				
				//drv.a.cmdOpStatus();
			}
		});
		
		confirm = (Button)view.findViewById(R.id.confirm);
		confirm.setOnClickListener(new OnClickListener(){
			public void onClick(View v)
			{
				//drv.a.confirmHistoryBlock();
				//drv.a.cmdReadTime();
			}
		});
		
		sent = (TextView)view.findViewById(R.id.sent);
		recv = (TextView)view.findViewById(R.id.recv);
		btStatus = (TextView)view.findViewById(R.id.btStatus);
		paired = (TextView)view.findViewById(R.id.paired);
		aFsm = (TextView)view.findViewById(R.id.appFsm);
		tFsm = (TextView)view.findViewById(R.id.txFsm);
		command = (TextView)view.findViewById(R.id.command);
		
		histEvents = (TextView)view.findViewById(R.id.events);
		histRemEvents = (TextView)view.findViewById(R.id.remEvents);
		
		lastBolus = (TextView)view.findViewById(R.id.lastBolus);
		lastTime = (TextView)view.findViewById(R.id.lastTime);
		currentBolus = (TextView)view.findViewById(R.id.currentBolus);
		currentStatus = (TextView)view.findViewById(R.id.currentStatus);
		
		connect = (CheckBox) view.findViewById(R.id.connectCheck);
		bt = (CheckBox) view.findViewById(R.id.btCheck);
		
		return view;
	}
	
	public void updateUI()
	{
		if(bt != null)		//Just check to make sure the last UI element is not null, then we know the others are ready
		{
			String stat = "Unknown";
				
			if(InterfaceData.remotePumpBt!=null)
			{
				switch(InterfaceData.remotePumpBt.getState())
				{
					case BluetoothConn.NONE:
						stat = "None";
						bt.setChecked(false);
						break;
					case BluetoothConn.LISTENING:
						stat = "Listening";
						bt.setChecked(false);
						break;
					case BluetoothConn.CONNECTING:
						stat = "Connecting";
						bt.setChecked(false);
						break;
					case BluetoothConn.CONNECTED:
						stat = "Connected";
						bt.setChecked(true);
						break;
				}
			}
			histRemEvents.setText("Rem. Events: "+drv.histRemEvents);
			histEvents.setText("Events: "+drv.histEvents);
			
			//lastBolus.setText("Amount: "+drv.lastBolus);
			//lastTime.setText("Time: "+drv.lastTime);
			
			//currentBolus.setText("Amount: "+drv.currentBolus);
			//currentStatus.setText("Status: "+drv.currentStatus);
			
//			connect.setChecked(drv.pump.running);
			
			if(Driver.getMode() == Driver.COMMAND)
				connect.setText("Command Mode");
			else if(Driver.getMode() == Driver.RT)
				connect.setText("RT Mode");
			
			//sent.setText("Sent: "+drv.sentPackets);
			//recv.setText("Received: "+drv.recvPackets);
			btStatus.setText("BT Status: "+stat);
			
			aFsm.setText("App FSM: "+drv.appFsm);
			tFsm.setText("Tx FSM: "+drv.txFsm);
			
			command.setText("Command: "+drv.command);
			
			if(drv.settings.getBoolean("paired", false))
				paired.setText("Paired: Yes");
			else
				paired.setText("Paired: No");
			
//			if(drv.pump.running)
//			{
//				auth.setEnabled(false);
//				discover.setEnabled(false);
//				//comm.setEnabled(false);
//				//history.setEnabled(false);
//			}
//			else
//			{
//				auth.setEnabled(true);
//				discover.setEnabled(true);
//				//comm.setEnabled(true);
//				//history.setEnabled(true);
//			}
		}
	}
	
//	private void debug_message(String tag, String message)
//	{
//		Debug.i(tag, FUNC_TAG, message);
//		
//		if(drv.responses!=null)
//		{
//			drv.responses.add(tag + " > " + message);
//		}
//	}
}
