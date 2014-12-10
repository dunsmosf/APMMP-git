package edu.virginia.dtc.standaloneDriver;

import com.lamerman.FileDialog;
import com.lamerman.SelectionMode;

import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Pump;
import edu.virginia.dtc.standaloneDriver.Device.InputMode;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

public class StandaloneUI extends Activity {
	
	private static final String TAG = "StandaloneUI";
	
	// Messages from Driver
	private static final int DRIVER2UI_NULL = 0;
	private static final int DRIVER2UI_DEV_STATUS = 1;
	private static final int DRIVER2UI_FINISH = 5;
	
	// Messages to Driver
	private static final int UI2DRIVER_NULL = 0;
	private static final int UI2DRIVER_REGISTER = 1;
	
	private static final int UI2DRIVER_CGM_START = 2;
	private static final int UI2DRIVER_DATABASE_CGM_DATA = 9;
	
	private static final int UI2DRIVER_PUMP_START = 4;
	
	// CGM Service OnStartCommand messages
  	public static final int CGM_SERVICE_CMD_NULL = 0;
  	public static final int CGM_SERVICE_CMD_CALIBRATE = 1;
  	public static final int CGM_SERVICE_CMD_DISCONNECT = 2;
  	public static final int CGM_SERVICE_CMD_INIT = 3;
  	
  	// Pump Service OnStartCommand messages
  	public static final int PUMP_SERVICE_CMD_NULL = 0;
 	public static final int PUMP_SERVICE_CMD_INIT = 9;
 	public static final int PUMP_SERVICE_CMD_DISCONNECT = 10;
	
	private ListView cgmDevices;
	private ListView pumpDevices;
	
	private ServiceConnection UItoDriver = null;
	private final Messenger messengerFromDriver = new Messenger(new incomingDriverHandler());
	private Messenger toDriver = null;
	
	public static final int REQUEST_SELECT_DB_FILE = 1;
	public int cgmDeviceCurrentlyLoadingDBPosition;
	
	private Driver drv;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		setResult(Activity.RESULT_CANCELED);
		
		drv = Driver.getInstance();
		
		Debug.i(TAG, FUNC_TAG,"Created...");
		
		cgmDevices = (ListView) this.findViewById(R.id.listView1);
		pumpDevices = (ListView) this.findViewById(R.id.listView2);

		if(drv.cgmList == null)
			drv.cgmList = new ArrayAdapter<String> (this,R.layout.listsettings);
		
		if(drv.pumpList == null)
			drv.pumpList = new ArrayAdapter<String> (this,R.layout.listsettings);
		
		cgmDevices.setAdapter(drv.cgmList);
		pumpDevices.setAdapter(drv.pumpList);
		
		registerForContextMenu(cgmDevices);
		registerForContextMenu(pumpDevices);
		
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
		
		// Update the UI with any existing data from Driver object
		updateUI();
		
		// Start bound driver service
		Intent intent = new Intent(Driver.UI_INTENT);
		bindService(intent, UItoDriver, Context.BIND_AUTO_CREATE);
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
		
		// Unbind connection to driver
		if(UItoDriver!=null)
		{
			Debug.i(TAG, FUNC_TAG,"unbinding service...");
			unbindService(UItoDriver);
		}
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) 
	{
		if (v.getId()==R.id.listView1)			// CGM menu
		{
			menu.setHeaderTitle("CGM Options");
			String[] options = {"Start", "Input Value", "DB read", "CSV Read", "Warm-Up", "No Antenna", "Disconnect"};
			for (int i = 0; i<options.length; i++) 
			{
				menu.add(0, i, i, options[i]);
			}
		}
		else if(v.getId() == R.id.listView2)	// Pump menu
		{
			menu.setHeaderTitle("Pump Options");
			String[] options = {"Start", "Unresponsive", "Connecting", "Miss Bolus", "No Insulin", "Disconnect"};
			for (int i = 0;i<options.length;i++)
			{
				menu.add(1, i, i, options[i]);
			}
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final String FUNC_TAG = "onContextItemSelected";

		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		Message msg;
		
		Intent cgmIntent = new Intent();
	    cgmIntent.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.CgmService.CgmService");
	    
	    Intent pumpIntent = new Intent();
	    pumpIntent.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.PumpService.PumpService");
	    
	    drv = Driver.getInstance();
	    switch(item.getGroupId())
	    {
		    case 0:
				switch(item.getItemId())
				{
					case 0:		//Start
						drv.antenna = true;
						drv.cgm.state = CGM.CGM_NORMAL;
						drv.cgm.status = "Started";
						break;
					case 1:		//User Input
						drv.antenna = true;
						
						AlertDialog.Builder alert = new AlertDialog.Builder(this);
						final EditText input = new EditText(this);
				    	input.setInputType(InputType.TYPE_CLASS_NUMBER);
				    	
				    	alert.setTitle("User CGM Entry");
						alert.setMessage("Please enter a CGM value (39-400):");
						
						alert.setView(input);
						
						alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								double val = Double.parseDouble(input.getText().toString());
								
								if(val >=39 && val<=400)
								{
									drv.cgm.inputMode = InputMode.USER;
									drv.cgm.userValue = val;
									Toast.makeText(getApplicationContext(), "CGM set to: " +val+" mg/dL", Toast.LENGTH_SHORT).show();
								}
								else
									Toast.makeText(getApplicationContext(), "Invalid value!", Toast.LENGTH_SHORT).show();
							}
						});
						
						alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
							}
						});
						alert.show();
						break;
					case 2:		//Database IO
						drv.antenna = true;
						
						Intent intent = new Intent(getBaseContext(), FileDialog.class); //file dialog library from http://code.google.com/p/android-file-dialog/
		                intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());	
		                intent.putExtra(FileDialog.CAN_SELECT_DIR, true);		
		                intent.putExtra(FileDialog.FORMAT_FILTER, new String[] { "db" });
		                intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);
		                cgmDeviceCurrentlyLoadingDBPosition = info.position;
		                
		                startActivityForResult(intent, REQUEST_SELECT_DB_FILE);
						break;
					case 3:		//File IO
						drv.antenna = true;
						drv.cgm.inputMode = InputMode.CSV;
						Toast.makeText(getApplicationContext(), "Reading from internal file...", Toast.LENGTH_SHORT).show();
						break;
					case 4:		//Warmup
						drv.antenna = true;
						drv.cgm.state = CGM.CGM_WARMUP;
						break;
					case 5:
						drv.antenna = false;
						break;
					case 6:		//Disconnect
						Debug.i(TAG, FUNC_TAG,"Disconnecting CGM");
						drv.cgm.state = CGM.CGM_NONE;
						cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_DISCONNECT);
						startService(cgmIntent);
						drv.cgmList.clear();
						this.findViewById(R.id.button1).setEnabled(true);
						break;
			  	}
				updateUI();
				break;
		    case 1:
		    	switch(item.getItemId())
				{
					case 0:		//Start
						drv.pump.status = "Started";
						drv.updatePumpState(Pump.CONNECTED);
						break;
					case 1:	// Unresponsive
						drv.pump.state = Pump.RECONNECTING;
						drv.updatePumpState(Pump.RECONNECTING);
						break;
					case 2: // Connecting
						drv.pump.state = Pump.CONNECTING;
						drv.updatePumpState(Pump.CONNECTING);
						break;
					case 3:	// Miss Bolus
						drv.pump.status = "Missing Bolus";
						drv.updatePumpState(Pump.CONNECTED);
						break;
					case 4: // No Insulin
						drv.pump.status = "No Insulin";
						drv.updatePumpState(Pump.CONNECTED);
						break;
					case 5://Disconnect
						Debug.i(TAG, FUNC_TAG,"Disconnecting Pump");
						//drv.pump.state = Pump.DISCONNECTED;
						drv.updatePumpState(Pump.DISCONNECTED);
						//pumpIntent.putExtra("PumpCommand", PUMP_SERVICE_CMD_DISCONNECT);
						//startService(pumpIntent);
						drv.pumpList.clear();
						this.findViewById(R.id.button2).setEnabled(true);
						break;
			  	}
		    	updateUI();
		    	break;
	    }
		
		return true;
	}
	
    public void onActivityResult(final int requestCode, int resultCode, final Intent data) {
		final String FUNC_TAG = "onActivityResult";

        if (resultCode == Activity.RESULT_OK) {	
            if (requestCode == REQUEST_SELECT_DB_FILE) {
	            String selectedFilePath = data.getStringExtra(FileDialog.RESULT_PATH);
            	Debug.i(TAG, FUNC_TAG, "CGM #" + cgmDeviceCurrentlyLoadingDBPosition + " trying to load db file: " + selectedFilePath);
				
            	double[] cgmValues = null;
            	long[] cgmTimes = null;
            	String errorMessage = "";
            	boolean success = false;
            	try {
					SQLiteDatabase db = SQLiteDatabase.openDatabase(selectedFilePath, null, SQLiteDatabase.OPEN_READONLY);
					Cursor c = db.query("cgm", new String[]{ "cgm", "time" }, null, null, null, null, null);
					if (c != null) {
						if (c.moveToFirst()) {
							cgmValues = new double[c.getCount()];
							cgmTimes = new long[c.getCount()];
							int cgmIndex = c.getColumnIndexOrThrow("cgm");
							int timeIndex = c.getColumnIndexOrThrow("time");

							int i = 0;
							do {
								cgmValues[i] = c.getDouble(cgmIndex);
								cgmTimes[i] = c.getLong(timeIndex);
								i++;
							} while (c.moveToNext());
							success = true;
						} else {
							errorMessage = "no CGM entries";
						}
						c.close();
					} else {
						errorMessage = "no CGM table";
					}
					db.close();
				} catch (SQLiteException e) {
	            	Debug.i(TAG, FUNC_TAG, "CGM #" + cgmDeviceCurrentlyLoadingDBPosition + " unable to open database file: " + selectedFilePath);
	            	errorMessage = "unable to open";
				} catch (IllegalArgumentException e) {
	            	Debug.i(TAG, FUNC_TAG, "CGM #" + cgmDeviceCurrentlyLoadingDBPosition + " no cgm1 column in database file: " + selectedFilePath);
	            	errorMessage = "no cgm1 column";					
				}
				
				if (success) {

					Bundle bundle = new Bundle();
					bundle.putDoubleArray("cgmValues", cgmValues);
					bundle.putLongArray("cgmTimes", cgmTimes);
					Message msg = Message.obtain(null, UI2DRIVER_DATABASE_CGM_DATA, cgmDeviceCurrentlyLoadingDBPosition);
					msg.setData(bundle);
					try {
						toDriver.send(msg);
						drv.cgm.inputMode = InputMode.DATABASE;
						Toast.makeText(getApplicationContext(), "Successfully reading database file: " + selectedFilePath, Toast.LENGTH_SHORT).show();
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				} else {
					Toast.makeText(getApplicationContext(), "Bad database file - " + errorMessage + ": " + selectedFilePath, Toast.LENGTH_SHORT).show();					
				}
            }            
        } else if (resultCode == Activity.RESULT_CANCELED) {
        }
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
					updateUI();
					break;
				case DRIVER2UI_FINISH:
					finish();
			        android.os.Process.killProcess(android.os.Process.myPid());
					break;
			}
		}
	}
	
	public void updateUI()
	{
		drv = Driver.getInstance();

		Intent intent = new Intent("edu.virginia.dtc.DEVICE_RESULT");
		int pumps = 0, cgms = 0;
		if(!drv.pumpList.isEmpty())
		{
			drv.pumpList.clear();
			
			drv.pump.status = "Started";
			drv.updatePumpState(Pump.CONNECTED);
			
			drv.pumpList.add("PUMP \n"+drv.pump.status);
			pumps = 1;
			this.findViewById(R.id.button2).setEnabled(false);
		}
		if(!drv.cgmList.isEmpty())
		{
			drv.cgmList.clear();
			
			drv.antenna = true;
			drv.cgm.state = CGM.CGM_NORMAL;
			drv.cgm.status = "Started";
			
			drv.cgmList.add("CGM \n"+drv.cgm.status);
			cgms = 1;
			this.findViewById(R.id.button1).setEnabled(false);
		}
		
		intent.putExtra("cgms", cgms);
		intent.putExtra("pumps", pumps);
		intent.putExtra("started", cgms > 0 || pumps > 0);
		intent.putExtra("name", "Standalone");
		sendBroadcast(intent);
	}
	
	// If you press the "Add CGM" button the CGM Service is started
	// and begins the initialization process by binding to the driver
	// (if not already done so) and creating the necessary storage for
	// the incoming data
	public void cgmButton(View view)
    {
    	Intent cgmIntent = new Intent();
	    cgmIntent.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.CgmService.CgmService");
	    cgmIntent.putExtra("driver_intent", Driver.CGM_INTENT);
	    cgmIntent.putExtra("driver_name", Driver.DRIVER_NAME);
	    cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_INIT);
        startService(cgmIntent);
        
        drv.cgmList.add("CGM \n"+drv.cgm.status);
    }
	
	public void pumpButton(View view)
    {
		Intent pumpIntent = new Intent();
		pumpIntent.setClassName("edu.virginia.dtc.DiAsService", "edu.virginia.dtc.PumpService.PumpService");
		pumpIntent.putExtra("driver_intent", Driver.PUMP_INTENT);
		pumpIntent.putExtra("driver_name", Driver.DRIVER_NAME);
		pumpIntent.putExtra("PumpCommand", PUMP_SERVICE_CMD_INIT);
		startService(pumpIntent);
		
		drv.pumpList.add("PUMP \n"+drv.pump.status);
    }
}
