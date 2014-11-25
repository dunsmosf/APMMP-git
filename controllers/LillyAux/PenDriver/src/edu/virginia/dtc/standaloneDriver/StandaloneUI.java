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
	
	private static final int UI2DRIVER_PUMP_START = 4;
	
  	// Pump Service OnStartCommand messages
  	public static final int PUMP_SERVICE_CMD_NULL = 0;
 	public static final int PUMP_SERVICE_CMD_INIT = 9;
 	public static final int PUMP_SERVICE_CMD_DISCONNECT = 10;
	
	private ListView pumpDevices;
	
	private ServiceConnection UItoDriver = null;
	private final Messenger messengerFromDriver = new Messenger(new incomingDriverHandler());
	private Messenger toDriver = null;
	
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
		
		pumpDevices = (ListView) this.findViewById(R.id.listView2);

		if(drv.pumpList == null)
			drv.pumpList = new ArrayAdapter<String> (this,R.layout.listsettings);
		
		pumpDevices.setAdapter(drv.pumpList);
		
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
		
		if(v.getId() == R.id.listView2)	// Pump menu
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
	    cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
	    
	    Intent pumpIntent = new Intent();
	    pumpIntent.setClassName("edu.virginia.dtc.PumpService", "edu.virginia.dtc.PumpService.PumpService");
	    
	    drv = Driver.getInstance();
	    switch(item.getGroupId())
	    {
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
				}
		    	updateUI();
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
		}
		
		
		intent.putExtra("cgms", cgms);
		intent.putExtra("pumps", pumps);
		intent.putExtra("started", cgms > 0 || pumps > 0);
		intent.putExtra("name", "Standalone");
		sendBroadcast(intent);
	}
	
}
