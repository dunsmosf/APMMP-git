package edu.virginia.dtc.DexcomBTRelayDriver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import edu.virginia.dtc.SysMan.Debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

public class DexcomBTRelayDriverUI extends Activity {
	
	private static final String TAG = "DexcomBTRelayDriverUI";
	
	// Messages from Driver
	private static final int DRIVER2UI_NULL = 0;
	private static final int DRIVER2UI_DEV_STATUS = 1;
	private static final int DRIVER2UI_UPDATE_STATUS = 4;
	private static final int DRIVER2UI_FINISH = 5;
	
	// Messages to Driver
	private static final int UI2DRIVER_NULL = 0;
	private static final int UI2DRIVER_REGISTER = 1;
	private static final int UI2DRIVER_CGM_START = 2;
	private static final int UI2DRIVER_CGM_STOP = 3;
	private static final int UI2DRIVER_CGM_NO_ANT = 6;
	private static final int UI2DRIVER_PUMP_START = 4;
	private static final int UI2DRIVER_PUMP_STOP = 5;
	private static final int UI2DRIVER_RESTART_RECEIVER_SERVICE = 9;
	
	// CGM Service OnStartCommand messages
  	public static final int CGM_SERVICE_CMD_NULL = 0;
  	public static final int CGM_SERVICE_CMD_CALIBRATE = 1;
  	public static final int CGM_SERVICE_CMD_DISCONNECT = 2;
  	public static final int CGM_SERVICE_CMD_INIT = 3;
  	
  	// Pump Service OnStartCommand messages
  	public static final int PUMP_SERVICE_CMD_NULL = 0;
 	public static final int PUMP_SERVICE_CMD_INIT = 9;
 	public static final int PUMP_SERVICE_CMD_DISCONNECT = 10;
	
	private ServiceConnection UItoDriver = null;
	private final Messenger messengerFromDriver = new Messenger(new incomingDriverHandler());
	private Messenger toDriver = null;
	private int previousLines = 0;

	CheckBox connectCheck;
	CheckBox btCheck;
	CheckBox completeCheck;
	
	private Button scan;
	private ProgressBar progress;
	
	private ListView cgmListView;
	private ListView pairDev, newDev;
	private ArrayAdapter<String> pairedDevices, newDevices;
	
	public static ArrayAdapter<String> meterList;
	public static ArrayAdapter<String> cgmList;
	
	public TextView statusView, subL, subR;
	
	public static String status;	
    public SharedPreferences ApplicationPreferences;
    public Handler handler;
    
    private InterfaceData data;
    
    public Thread updateUI = new Thread(){
    	public void run(){
    		updateUI();
    	}
    };
	
	private Driver thisDriver;
	private static long versionTime;
	
	private final BroadcastReceiver discoverReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already paired, skip it, because it's been listed already
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    newDevices.add(device.getName() + "\n" + device.getAddress());
                }
            // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (newDevices.getCount() == 0) {
                    String noDevices = "No devices!";
                    newDevices.add(noDevices);
                }
                scan.setEnabled(true);
                progress.setVisibility(View.INVISIBLE);
            }
        }
    };
    
    private OnItemClickListener deviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
        	data = InterfaceData.getInstance();
        	
            // Cancel discovery because it's costly and we're about to connect
            data.bt.cancelDiscovery();

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            final String address = info.substring(info.length() - 17);
            final BluetoothDevice connectDev = data.bt.getRemoteDevice(address);
            
            AlertDialog.Builder alert = new AlertDialog.Builder(v.getContext());
	    	
	    	alert.setTitle("Connect");
	    	alert.setMessage("Connect to " + connectDev.getName() + "?");
	    	
	    	alert.setPositiveButton("Connect", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					data = InterfaceData.getInstance();
					
					//TODO: post the name of the device
					//cgmDev = connectDev.getName();
					
					//Save the MAC address in shared preferences
					ApplicationPreferences.edit().putString("mac", address).commit();
					
					InterfaceData.remoteCgmBt.connect(connectDev, true);
				}
			});
			alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
				}
			});
	    	
	    	alert.show();
        }
    };
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";

		super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.main);
		
		setResult(Activity.RESULT_CANCELED);
		
		Debug.i(TAG, FUNC_TAG, "Created...");
		ApplicationPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		statusView = (TextView) findViewById(R.id.statusView);
		statusView.setText(ApplicationPreferences.getString("UIStatus", "none"));
		
		data = InterfaceData.getInstance();
		
		subL = (TextView)this.findViewById(R.id.subStatus1);
		subR = (TextView)this.findViewById(R.id.subStatus2);
		
		pairedDevices = new ArrayAdapter<String>(this, R.layout.list_name);
		newDevices = new ArrayAdapter<String>(this, R.layout.list_name);
		
		scan = (Button) this.findViewById(R.id.scan);
		
		progress = (ProgressBar) this.findViewById(R.id.progress);
		
		pairDev = (ListView)this.findViewById(R.id.pairedView);
		newDev = (ListView)this.findViewById(R.id.newView);
		
		pairDev.setAdapter(pairedDevices);
		pairDev.setOnItemClickListener(deviceClickListener);
		
		newDev.setAdapter(newDevices);
		newDev.setOnItemClickListener(deviceClickListener);
		
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(discoverReceiver, filter);
		
		Set<BluetoothDevice> alreadyPaired = data.bt.getBondedDevices();
		
		if(alreadyPaired.size()>0)
		{
			for(BluetoothDevice b:alreadyPaired)
				pairedDevices.add(b.getName() + "\n" + b.getAddress());
		}
		else
		{
			pairedDevices.add("No paired devices!");
		}

		cgmListView = (ListView) this.findViewById(R.id.listView1);
		if (cgmList == null)
			cgmList = new ArrayAdapter<String> (this,R.layout.listsettings);
		cgmListView.setAdapter(cgmList);
		cgmListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
		cgmListView.setStackFromBottom(true);
		
		/*
		meterListView = (ListView) findViewById(R.id.listView2);
		if (meterList == null)
			meterList = new ArrayAdapter<String> (this,R.layout.listsettings);
		meterListView.setAdapter(meterList);
		meterListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
		meterListView.setStackFromBottom(true);
		*/

		connectCheck = (CheckBox) findViewById(R.id.connectCheck);
		btCheck = (CheckBox) findViewById(R.id.btCheck);
		completeCheck = (CheckBox) findViewById(R.id.completeCheck);
		
		try
		{
			String rev = "$Rev: 333 $";
			((TextView)findViewById(R.id.textViewVersionNum)).setText("Version " + rev.split(" ")[1]);
			((TextView)findViewById(R.id.textViewVersionNum)).setTextColor(Color.rgb(120, 120, 120));
			ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), 0);
			ZipFile zf = new ZipFile(ai.sourceDir);
			ZipEntry ze = zf.getEntry("classes.dex");
			versionTime = ze.getTime();
			CharSequence versionDate = DateFormat.format("MM/dd hh:mm", new Date(versionTime));
			((TextView)findViewById(R.id.textViewVersionDate)).setText(versionDate);
			((TextView)findViewById(R.id.textViewVersionDate)).setTextColor(Color.rgb(120, 120, 120));
		} 
		catch (Exception e) 
		{
			Debug.e(TAG, FUNC_TAG, "Error getting version", e);
		}

		
		// Setup connection between UI and driver
		UItoDriver = new ServiceConnection(){
			public void onServiceConnected(ComponentName arg0, IBinder arg1) {
				Debug.i(TAG, FUNC_TAG,"Connecting to driver service!");
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
				Debug.i(TAG, FUNC_TAG,"Connection to driver terminated...");
			}
			
		};
		
		// Handler for posting delayed events
		handler = new Handler();
		
		// Update the UI with any existing data from Driver object
		updateUI();
		
		// Start bound driver service
		Intent intent = new Intent(Driver.UI_INTENT);
		bindService(intent, UItoDriver, Context.BIND_AUTO_CREATE);
		
		thisDriver.usbConnected = false;
	}
	
	@Override
	public void onStart()
	{
		super.onStart();
	}
	
	@Override
	public void onDestroy()
	{
		final String FUNC_TAG = "onDestroy";

		super.onDestroy();
		unregisterReceiver(discoverReceiver);
		
		// Unbind connection to driver
		if(UItoDriver!=null)
		{
			Debug.i(TAG, FUNC_TAG,"unbinding UItoDriver...");
			unbindService(UItoDriver);
		}
	}
	
	class incomingDriverHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case DRIVER2UI_NULL:
				break;
			case DRIVER2UI_DEV_STATUS:
				updateUI();
				break;
			case DRIVER2UI_UPDATE_STATUS:
				String status = msg.getData().getString("status");
				int checkbox = msg.getData().getInt("checkbox");
				boolean checked = msg.getData().getBoolean("checked");
				int color= msg.getData().getInt("color", Color.WHITE);
				
				setProgressBarIndeterminateVisibility(msg.getData().getBoolean("progressbar", false));
				
				if (!status.equals(""))
					setStatus(status);
			
				switch (checkbox) {
					case 1:
						connectCheck.setChecked(checked);
						connectCheck.setTextColor(color);
						break;
					case 2:
						btCheck.setChecked(checked);
						btCheck.setTextColor(color);
						break;
				}
				
				updateUI();
				break;
			case DRIVER2UI_FINISH:
				finish();
		        android.os.Process.killProcess(android.os.Process.myPid());
				break;
			}
		}
	}
	
	public void onScan(View view)
	{		
		newDevices.clear();
		
		if(data.bt.isDiscovering())
			data.bt.cancelDiscovery();
		
		data.bt.startDiscovery();
		
		scan.setEnabled(false);
		progress.setVisibility(View.VISIBLE);
	}
	
	public void updateUI()
	{
		thisDriver = Driver.getInstance();
		data = InterfaceData.getInstance();
		
		subL.setText("MAC: "+data.bt.getAddress());
		
		String cgm = "State:";
		
		if(InterfaceData.remoteCgmBt != null)
		{
			switch(InterfaceData.remoteCgmBt.getState())
			{
				case BluetoothConn.NONE:
					cgm+=" None";
					btCheck.setChecked(false);
					break;
				case BluetoothConn.LISTENING:
					cgm+=" Listening";
					btCheck.setChecked(false);
					break;
				case BluetoothConn.CONNECTING:
					cgm+=" Connecting";
					btCheck.setChecked(false);
					break;
				case BluetoothConn.CONNECTED:
					cgm+=" Running";
					btCheck.setChecked(true);
					break;
			}
		}
		
		completeCheck.setChecked(btCheck.isChecked() && connectCheck.isChecked());
		
		subR.setText(cgm);
		
		//TODO: add the check for when the device is actually connected and working
	}
	
	public void checkUsbDevices()
	{
		final String FUNC_TAG = "checkUsbDevices";

		thisDriver.manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = thisDriver.manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
		Debug.d(TAG, FUNC_TAG, "Checking for USB devices... " + deviceList.size());
        
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            
            if(device.getVendorId() == 8867)		//DexComGen4
            {
            	thisDriver.dexcom = device;
            	PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent("edu.virginia.dtc.ACTION.USE_PERMISSION"), 0);
            	thisDriver.manager.requestPermission(thisDriver.dexcom, usbPermissionIntent);
            }
        }
	}
	
	public void turnOnUSB(View view){
		DexcomBTRelayDriver.turnOnUSB();
	}
	
	public void turnOffUSB(View view){
		DexcomBTRelayDriver.turnOffUSB();
	}
	
	public void refreshLog(){
		final String FUNC_TAG = "refreshLog";

		try {
			Debug.i(TAG, FUNC_TAG, "Refreshing log at " + DateFormat.format("hh:mm:ss", new Date(System.currentTimeMillis())));
			Process process = Runtime.getRuntime().exec("logcat -v time -d CGMDriver:D DexcomBTRelayDriver:D DexcomBTRelayUI:D USBController:D CGMService:D ReceiverUpdateService:D *:S");
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			AlertDialog.Builder alert = new AlertDialog.Builder(this);
			String line;
			final HorizontalScrollView hsv = new HorizontalScrollView(this);
			final ScrollView vsv = new ScrollView(this);
			TextView tv = new TextView(this);
			int lines = 0;
			while ((line = bufferedReader.readLine()) != null) {
				lines++;
				if (lines < previousLines - 500)
					continue;
				tv.append(line + "\n");
			}
			hsv.addView(vsv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			vsv.addView(tv, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			alert.setView(hsv);
			alert.setPositiveButton("Refresh", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					handler.post(new Thread() {
						public void run() {
							refreshLog();
						}
					});
				}
			});
			alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
				}
			});
			alert.show();
			vsv.post(new Runnable() {            
			    public void run() {
			           vsv.fullScroll(View.FOCUS_DOWN);              
			    }
			});
			previousLines = lines;
		} catch (IOException e) {
		}
		updateUI();
	}
	
    public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.clear();
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menumain, menu);
        return true;
    }
    
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menuLog:
			refreshLog();
			return true;
		case R.id.menuPermission:
			checkUsbDevices();
			return true;
		case R.id.menuUSBOff:
			DexcomBTRelayDriver.turnOffUSB();
			return true;
		case R.id.menuUSBOn:
			DexcomBTRelayDriver.turnOnUSB();
			return true;
		case R.id.menuResetUSB:
			DexcomBTRelayDriver.resetUSB();
			return true;
		case R.id.menuRestartService:
			try {
				toDriver.send(Message.obtain(null, UI2DRIVER_RESTART_RECEIVER_SERVICE));
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	public void setStatus(String status){
		statusView.setText(status);
		ApplicationPreferences.edit().putString("UIStatus", status).commit();
	}
}

