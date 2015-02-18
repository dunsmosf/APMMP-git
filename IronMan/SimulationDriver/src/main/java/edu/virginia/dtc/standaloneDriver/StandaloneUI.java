package edu.virginia.dtc.standaloneDriver;

import com.lamerman.FileDialog;
import com.lamerman.SelectionMode;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.CGM;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Pump;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentValues;
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
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		setResult(Activity.RESULT_CANCELED);
		
		Debug.i(TAG, FUNC_TAG,"Created...");
		
		cgmDevices = (ListView) this.findViewById(R.id.listView1);
		pumpDevices = (ListView) this.findViewById(R.id.listView2);

		if(StandaloneDriver.cgmList == null)
			StandaloneDriver.cgmList = new ArrayAdapter<String> (this,R.layout.listsettings);
		
		if(StandaloneDriver.pumpList == null)
			StandaloneDriver.pumpList = new ArrayAdapter<String> (this,R.layout.listsettings);
		
		cgmDevices.setAdapter(StandaloneDriver.cgmList);
		pumpDevices.setAdapter(StandaloneDriver.pumpList);
		
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
		Intent intent = new Intent(StandaloneDriver.UI_INTENT);
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
			String[] options = {"Start", "Unresponsive", "Connecting", "Disconnect"};
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
	    cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
	    
	    Intent pumpIntent = new Intent();
	    pumpIntent.setClassName("edu.virginia.dtc.PumpService", "edu.virginia.dtc.PumpService.PumpService");
	    
	    switch(item.getGroupId())
	    {
		    case 0:
				switch(item.getItemId())
				{
					case 0:		//Start
						StandaloneDriver.cgmAntenna = true;
                        StandaloneDriver.cgmState = CGM.CGM_NORMAL;
                        StandaloneDriver.cgmDescriptor = "Started";
						break;
					case 1:		//User Input
                        StandaloneDriver.cgmAntenna = true;
						
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
                                    StandaloneDriver.inputMode = StandaloneDriver.InputMode.USER;
                                    StandaloneDriver.userValue = val;
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
                        StandaloneDriver.cgmAntenna = true;
						
						Intent intent = new Intent(getBaseContext(), FileDialog.class); //file dialog library from http://code.google.com/p/android-file-dialog/
		                intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());	
		                intent.putExtra(FileDialog.CAN_SELECT_DIR, true);		
		                intent.putExtra(FileDialog.FORMAT_FILTER, new String[] { "db" });
		                intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);
		                cgmDeviceCurrentlyLoadingDBPosition = info.position;
		                
		                startActivityForResult(intent, REQUEST_SELECT_DB_FILE);
						break;
					case 3:		//File IO
                        StandaloneDriver.cgmAntenna = true;
						StandaloneDriver.inputMode = StandaloneDriver.InputMode.CSV;
						Toast.makeText(getApplicationContext(), "Reading from internal file...", Toast.LENGTH_SHORT).show();
						break;
					case 4:		//Warmup
                        StandaloneDriver.cgmAntenna = true;
                        StandaloneDriver.cgmState = CGM.CGM_WARMUP;
						break;
					case 5:
                        StandaloneDriver.cgmAntenna = false;
						break;
					case 6:		//Disconnect
						Debug.i(TAG, FUNC_TAG,"Disconnecting CGM");
                        StandaloneDriver.cgmState = CGM.CGM_NONE;
						cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_DISCONNECT);
						startService(cgmIntent);

                        StandaloneDriver.cgmList.clear();
						this.findViewById(R.id.button1).setEnabled(true);
						break;
			  	}
				updateUI();
				break;
		    case 1:
		    	switch(item.getItemId())
				{
					case 0:		//Start
						StandaloneDriver.pumpDescriptor = "Started";
                        StandaloneDriver.pumpState = Pump.CONNECTED;
						break;
					case 1:	// Unresponsive
						StandaloneDriver.pumpState = Pump.RECONNECTING;
						break;
					case 2: // Connecting
						StandaloneDriver.pumpState = Pump.CONNECTING;
						break;
					case 3://Disconnect
                        StandaloneDriver.pumpState = Pump.DISCONNECTED;
						StandaloneDriver.pumpList.clear();
						this.findViewById(R.id.button2).setEnabled(true);
						break;
			  	}
                updatePumpState(StandaloneDriver.pumpState);
		    	updateUI();
		    	break;
	    }
		
		return true;
	}

    public void updatePumpState(int state){
        final String FUNC_TAG = "updatePumpState";

        Debug.i(TAG, FUNC_TAG, "State: "+state);

        ContentValues pv = new ContentValues();
        pv.put("state", state);

        getContentResolver().update(Biometrics.PUMP_DETAILS_URI, pv, null, null);
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
                        StandaloneDriver.inputMode = StandaloneDriver.InputMode.DATABASE;
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
		Intent intent = new Intent("edu.virginia.dtc.DEVICE_RESULT");
		int pumps = 0, cgms = 0;

		if(!StandaloneDriver.pumpList.isEmpty())
		{
			StandaloneDriver.pumpList.clear();
			StandaloneDriver.pumpList.add("PUMP \n"+StandaloneDriver.pumpDescriptor);

			pumps = 1;
			this.findViewById(R.id.button2).setEnabled(false);
		}
		if(!StandaloneDriver.cgmList.isEmpty())
		{
			StandaloneDriver.cgmList.clear();
			StandaloneDriver.cgmState = CGM.CGM_NORMAL;
			StandaloneDriver.cgmList.add("CGM \n"+StandaloneDriver.cgmDescriptor);

			cgms = 1;
			this.findViewById(R.id.button1).setEnabled(false);
		}
		
		intent.putExtra("cgms", cgms);
		intent.putExtra("pumps", pumps);
		intent.putExtra("started", cgms > 0 || pumps > 0);
		intent.putExtra("name", "Standalone");
		sendBroadcast(intent);
	}
	
	public void cgmButton(View view)
    {
    	Intent cgmIntent = new Intent();
	    cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
	    cgmIntent.putExtra("driver_intent", StandaloneDriver.CGM_INTENT);
	    cgmIntent.putExtra("driver_name", StandaloneDriver.DRIVER_NAME);
	    cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_INIT);
        startService(cgmIntent);
        
        StandaloneDriver.cgmList.add("CGM \n"+StandaloneDriver.cgmDescriptor);
    }
	
	public void pumpButton(View view)
    {
		Intent pumpIntent = new Intent();
		pumpIntent.setClassName("edu.virginia.dtc.PumpService", "edu.virginia.dtc.PumpService.PumpService");
		pumpIntent.putExtra("driver_intent", StandaloneDriver.PUMP_INTENT);
		pumpIntent.putExtra("driver_name", StandaloneDriver.DRIVER_NAME);
		pumpIntent.putExtra("PumpCommand", PUMP_SERVICE_CMD_INIT);
		startService(pumpIntent);
		
		StandaloneDriver.pumpList.add("PUMP \n"+StandaloneDriver.pumpDescriptor);
    }
}
