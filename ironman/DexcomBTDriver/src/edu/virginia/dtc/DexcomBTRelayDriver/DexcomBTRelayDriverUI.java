package edu.virginia.dtc.DexcomBTRelayDriver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import edu.virginia.dtc.SysMan.Debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.format.DateFormat;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

public class DexcomBTRelayDriverUI extends Activity {
	
	private static final String TAG = "DexcomBTDriverUI";
	
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
	
	private ListView cgmListView;
	private ListView meterListView;
	private ListView cgmDevicesView;
	
	private static ArrayAdapter<String> cgmDevices;
	public static ArrayAdapter<String> meterList;
	public static ArrayAdapter<String> cgmList;
	
	public TextView statusView, statusLeft, statusRight;
	public static String status;	
	
    public Handler handler;
    
    public Thread updateUI = new Thread(){
    	public void run(){
    		updateUI();
    	}
    };
	
	private Driver thisDriver;
	private static long versionTime;
	private InterfaceData data;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";

		super.onCreate(savedInstanceState);
        //requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.main);
		
		setResult(Activity.RESULT_CANCELED);
		
		Debug.i(TAG, FUNC_TAG, "Created...");
		
		statusView = (TextView) findViewById(R.id.statusView);
		statusLeft = (TextView) findViewById(R.id.subStatus1);
		statusRight = (TextView) findViewById(R.id.subStatus2);

		//Setting up ListViews to ArrayAdapters
		cgmListView = (ListView) this.findViewById(R.id.listView1);
		if (cgmList == null)
			cgmList = new ArrayAdapter<String> (this,R.layout.listsettings);
		cgmListView.setAdapter(cgmList);
		cgmListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
		cgmListView.setStackFromBottom(true);
		
		meterListView = (ListView) findViewById(R.id.listView2);
		if (meterList == null)
			meterList = new ArrayAdapter<String> (this,R.layout.listsettings);
		meterListView.setAdapter(meterList);
		meterListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
		meterListView.setStackFromBottom(true);

		cgmDevicesView = (ListView) this.findViewById(R.id.listViewCGM);
		if (cgmDevices == null)
			cgmDevices = new ArrayAdapter<String>(this, R.layout.listsettings);
		cgmDevicesView.setAdapter(cgmDevices);
		
		registerForContextMenu(cgmDevicesView);
		
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
			Debug.i(TAG, FUNC_TAG,"unbinding UItoDriver...");
			unbindService(UItoDriver);
		}
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) 
	{
		if (v.getId()==R.id.listViewCGM)			// CGM menu
		{
			menu.setHeaderTitle("CGM Options");
			String[] options = {"Start", "Stop", "Disconnect"};
			for (int i = 0; i<options.length; i++) 
			{
				menu.add(0, i, i, options[i]);
			}
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final String FUNC_TAG = "onContextItemSelected";

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		Message msg;
		
		Intent cgmIntent = new Intent();
	    cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
	    
	    thisDriver = Driver.getInstance();
	    switch(item.getGroupId())
	    {
		    case 0:
				switch(item.getItemId())
				{
					case 0:		//Start
						msg = Message.obtain(null, UI2DRIVER_CGM_START, thisDriver.my_state_index, thisDriver.cgms.get(info.position).my_dev_index);
						try {
							toDriver.send(msg);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
						handler.postDelayed(updateUI, 500);
						break;
					case 1:		//Stop
						msg = Message.obtain(null, UI2DRIVER_CGM_STOP, thisDriver.my_state_index, thisDriver.cgms.get(info.position).my_dev_index);
						try {
							toDriver.send(msg);
						} catch (RemoteException e) {
							e.printStackTrace();
						}
						handler.postDelayed(updateUI, 500);
						break;
					case 2:		//Disconnect
						Debug.i(TAG, FUNC_TAG,"Disconnecting CGM");
						cgmIntent.putExtra("state", thisDriver.my_state_index);
					    cgmIntent.putExtra("dev", thisDriver.cgms.get(info.position).my_dev_index);
						cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_DISCONNECT);
						startService(cgmIntent);
						handler.postDelayed(updateUI, 500);				
						break;
			  	}
				break;
		    case 1:
		    	break;
	    }
		
		return true;
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
				
				//setProgressBarIndeterminateVisibility(msg.getData().getBoolean("progressbar", false));
				
				if (!status.equals(""))
					setStatus(status);
				
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
		cgmDevices.clear();	
		thisDriver = Driver.getInstance();
		data = InterfaceData.getInstance();
		
		statusLeft.setText(data.bt.getName() + " (G4: "+ ((InterfaceData.g4Battery > 0) ? InterfaceData.g4Battery : "???") +"%)");
		statusRight.setText(data.bt.getAddress());
		
		for(Device d:thisDriver.cgms)
			cgmDevices.add("CGM Index: "+d.my_dev_index+"\n"+d.status);
		
		if (thisDriver.cgms.size() > 0)
		{
			if (thisDriver.cgms.get(0).running)
			{
				//TODO: make sure to add some code to this for updating the UI
			} 
			else 
			{
				setStatus("CGM Service device required");
			}
		}
		else 
			setStatus("CGM Service device required");
		
		Intent intentResult = new Intent("edu.virginia.dtc.DEVICE_RESULT");
		intentResult.putExtra("started", true);
		intentResult.putExtra("name", "Dexcom BT");
		
		String cgm = "State:";
		
		if(InterfaceData.remoteCgmBt != null)
		{
			switch(InterfaceData.remoteCgmBt.getState())
			{
				case BluetoothConn.NONE:
					cgm+=" None";
					intentResult.putExtra("cgms", 0);
					sendBroadcast(intentResult);
					break;
				case BluetoothConn.LISTENING:
					cgm+=" Listening";
					intentResult.putExtra("cgms", 0);
					sendBroadcast(intentResult);
					break;
				case BluetoothConn.CONNECTING:
					cgm+=" Connecting";
					intentResult.putExtra("cgms", 0);
					sendBroadcast(intentResult);
					break;
				case BluetoothConn.CONNECTED:
					intentResult.putExtra("cgms", 1);
					sendBroadcast(intentResult);
					cgm+=" Running";
					break;
			}
		}
		
		setStatus("Registered - " + cgm);
	}
	
	public void initializeCGM() {
		if (thisDriver.cgms.size() > 0)
			return;
		Intent cgmIntent = new Intent();
		cgmIntent.setClassName("edu.virginia.dtc.CgmService", "edu.virginia.dtc.CgmService.CgmService");
		cgmIntent.putExtra("driver_intent", Driver.CGM_INTENT);
		cgmIntent.putExtra("driver_name", Driver.DRIVER_NAME);
		cgmIntent.putExtra("CGMCommand", CGM_SERVICE_CMD_INIT);
		startService(cgmIntent);	
	}
	
	public void refreshLog() {
		final String FUNC_TAG = "refreshLog";

		try 
		{
			Debug.i(TAG, FUNC_TAG, "Refreshing log at " + DateFormat.format("hh:mm:ss", new Date(System.currentTimeMillis())));
			Process process = Runtime.getRuntime().exec("logcat -v time -d CGMDriver:D USBDexcomLocalDriver:D USBDexcomLocalUI:D USBController:D CGMService:D ReceiverUpdateService:D *:S");
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
		} 
		catch (IOException e) 
		{
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
			case R.id.menuAddCGM:
				initializeCGM();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	public void setStatus(String status) {
		statusView.setText(status);
	}
}

