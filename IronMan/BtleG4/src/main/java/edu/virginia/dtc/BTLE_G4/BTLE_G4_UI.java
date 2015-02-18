package edu.virginia.dtc.BTLE_G4;

import java.util.Date;
import java.util.Timer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


import edu.virginia.dtc.BTLE_G4.BTLE_G4_Driver.listDevice;
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
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class BTLE_G4_UI extends Activity {
	
	private static final String TAG = "BTLE_G4";
	
	// Messages from Driver
	private static final int DRIVER2UI_NULL = 0;
	private static final int DRIVER2UI_UPDATE = 1;
	private static final int DRIVER2UI_FINISH = 5;
	
	// Messages to Driver
	private static final int UI2DRIVER_NULL = 0;
	private static final int UI2DRIVER_REGISTER = 1;
	private static final int UI2DRIVER_CGM_START = 2;
	private static final int UI2DRIVER_CGM_STOP = 3;
	private static final int UI2DRIVER_CGM_NO_ANT = 6;
	private static final int UI2DRIVER_SCAN = 9;
	private static final int UI2DRIVER_CONNECT = 10;
	
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

	CheckBox checkBox2;
	CheckBox checkBox3;
	CheckBox checkBox4;
	RadioButton radio;

	private ListView cgmListView;
	private ListView meterListView;
	private ListView g4ListView;
	
	public static ArrayAdapter<String> meterList;
	public static ArrayAdapter<String> cgmList;
	public static ArrayAdapter<String> g4List;
	
	public TextView statusView;	
    
    public Timer updateTimer;
    public Handler handler;
	
	private Driver drv;
	private static long versionTime;
	
	private Context main = this;
	
	private OnItemClickListener clickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, final int arg2, final long arg3) {
         
        	final String FUNC_TAG = "clickListener";
        	
            AlertDialog.Builder alert = new AlertDialog.Builder(main);
	    	
	    	alert.setTitle("Pairing");

	    	alert.setPositiveButton("Connect", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					
					Debug.i(TAG, FUNC_TAG, "Getting item: "+arg2+" > "+BTLE_G4_Driver.devices.get(arg2).address);
					
					Bundle data = new Bundle();
					data.putInt("index", arg2);
					sendDataMessage(toDriver, data,  UI2DRIVER_CONNECT);
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
		statusView = (TextView) findViewById(R.id.statusView);

		g4ListView = (ListView)this.findViewById(R.id.ListG4s);
		if(g4List == null)
			g4List = new ArrayAdapter<String> (this, R.layout.listsettings);
		g4ListView.setAdapter(g4List);
		g4ListView.setOnItemClickListener(clickListener);
		
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
		
		checkBox2 = (CheckBox) findViewById(R.id.checkBox2);
		checkBox3 = (CheckBox) findViewById(R.id.checkBox3);
		checkBox4 = (CheckBox) findViewById(R.id.checkBox4);
		
		radio = (RadioButton) findViewById(R.id.radioButton1);
		
		try 
		{
			String rev = "$Rev: 1155 $"; //auto-updated by SVN
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
		
		drv = Driver.getInstance();
		
		// Handler for posting delayed events
		handler = new Handler();
		
		// Start bound driver service
		Intent intent = new Intent(Driver.UI_INTENT);
		bindService(intent, UItoDriver, Context.BIND_AUTO_CREATE);
		
		updateUI();
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
	
	class incomingDriverHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case DRIVER2UI_NULL:
				break;
			case DRIVER2UI_UPDATE:
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
		final String FUNC_TAG = "updateUI";
		
		Debug.i(TAG, FUNC_TAG, "Updating UI");
		
		drv = Driver.getInstance();
		
		TextView name = (TextView)this.findViewById(R.id.connectedName);
		TextView batt = (TextView)this.findViewById(R.id.battery);
		
		if(drv.battery != -1)
			batt.setText("Battery: "+drv.battery+"%");
		else
			batt.setText("Battery: N/A");
		
		if(drv.deviceMac.equalsIgnoreCase(""))
			name.setText("Connected to: None");
		else
			name.setText("Connected to: "+drv.deviceMac);
		
		checkBox2.setChecked(drv.registered);
		checkBox3.setChecked(drv.connected);
		checkBox4.setChecked(drv.registered && drv.connected);

		radio.setChecked(drv.lowPower);
		
		setProgressBarIndeterminateVisibility(drv.progress);
		
		statusView.setText(drv.status);
		
		g4List.clear();
		
		if(!BTLE_G4_Driver.devices.isEmpty())
		{
			for(listDevice d:BTLE_G4_Driver.devices)
			{
				String s = d.address;
				g4List.add(d.dev.getName()+"\n"+s);
			}
		}
	}
	
	public void scanClick(View v)
	{
		final String FUNC_TAG = "scanClick";
		
		Debug.i(TAG, FUNC_TAG, "Pressed scan!");
		
		BTLE_G4_Driver.devices.clear();
		
		sendDataMessage(toDriver, null,  UI2DRIVER_SCAN);
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
	
    public boolean onPrepareOptionsMenu(Menu menu) {
    	menu.clear();
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menumain, menu);
        return true;
    }
    
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) 
		{
			case R.id.menuLowPower:
//				boolean set = USBDexcomLocalDriver.m_receiverService.setCurrentUsbPowerLevel(UsbPowerLevel.PwrSuspend);
//				Toast.makeText(this, "Power Set: "+set, Toast.LENGTH_SHORT).show();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
}

