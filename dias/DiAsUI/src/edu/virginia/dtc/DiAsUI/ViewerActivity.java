package edu.virginia.dtc.DiAsUI;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Pump;

public class ViewerActivity extends Activity{
	
	public static final String TAG = "ViewerActivity";
	
	public static final int MAX_ENTRIES = 30;
	
	private Spinner spin;
	private ListView list;
	private ArrayAdapter<String> listAdapter;
	private int spinnerPosition = 0;
	private List<listItem> items = new ArrayList<listItem>();
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		final String FUNC_TAG = "onCreate";
		
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.viewerscreen);
		
		if (getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT){
			WindowManager.LayoutParams params = getWindow().getAttributes();
			params.gravity=Gravity.BOTTOM;
		}
		
		Debug.i(TAG, FUNC_TAG, "");
	
		initScreen();
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
		Debug.i(TAG, FUNC_TAG, "OnDestroy");
		finish();
	}

	private void initScreen()
	{
		final String FUNC_TAG = "initScreen";
		
		list = (ListView)this.findViewById(R.id.listView1);
		spin = (Spinner)this.findViewById(R.id.spinner1);
		
		items.clear();
		
		List<String> spinList = new ArrayList<String>();
		spinList.add("Events");
		spinList.add("Insulin");
		spinList.add("System");
		spinList.add("CGM");
		spinList.add("SMBG");
		spinList.add("Device");
		spinList.add("HW Config");
		spinList.add("Exercise");	
		spinList.add("CGM Details");
		spinList.add("Pump Details");
		spinList.add("Temp Basal");
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, spinList);
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spin.setAdapter(dataAdapter);
		
		spin.setOnItemSelectedListener(new eventOnItemSelectedListener());
		
		listAdapter = new ArrayAdapter<String> (this, R.layout.listsettings);
		list.setAdapter(listAdapter);
		
		list.setOnItemClickListener(new eventOnItemClickListener());
	}
	
	class eventOnItemSelectedListener implements OnItemSelectedListener {
		 
		  public void onItemSelected(AdapterView<?> parent, View view, int pos,long id) {
			  spinnerPosition = pos;
			  
			  listAdapter.clear();
			  items.clear();
			  
			  switch(pos)
			  {
				  case 0: showEventTable(); break;
				  case 1: showInsulinTable(); break;
				  case 2: showGenericTable(Biometrics.SYSTEM_URI); break;
				  case 3: showGenericTable(Biometrics.CGM_URI); break;
				  case 4: showGenericTable(Biometrics.SMBG_URI); break;
				  case 5: showGenericTable(Biometrics.DEV_DETAILS_URI); break;
				  case 6: showGenericTable(Biometrics.HARDWARE_CONFIGURATION_URI); break;
				  case 7: showGenericTable(Biometrics.EXERCISE_SENSOR_URI); break;
				  case 8: showGenericTable(Biometrics.CGM_DETAILS_URI); break;
				  case 9: showGenericTable(Biometrics.PUMP_DETAILS_URI); break;
				  case 10: showGenericTable(Biometrics.TEMP_BASAL_URI); break;
			  }
		  }
		 
		  public void onNothingSelected(AdapterView<?> arg0) {
			// TODO Auto-generated method stub
		  }
		 
	}
	
	class eventOnItemClickListener implements OnItemClickListener{
		final String FUNC_TAG = "eventOnItemClickListener";
		
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
			int index = arg2;
			
			Debug.i(TAG, FUNC_TAG, "Item index: "+index+" selected!");
			
			switch(spinnerPosition)
			{
				case 0: showEventDetails(index); break;
				case 1: showInsulinDetails(index); break;
				case 2: showGenericDetails(Biometrics.SYSTEM_URI, index); break;
				case 3: showGenericDetails(Biometrics.CGM_URI, index); break;
				case 4: showGenericDetails(Biometrics.SMBG_URI, index); break;
				case 5: showGenericDetails(Biometrics.DEV_DETAILS_URI, index); break;
				case 6: showGenericDetails(Biometrics.HARDWARE_CONFIGURATION_URI, index); break;
				case 7: showGenericDetails(Biometrics.EXERCISE_SENSOR_URI, index); break;
				case 8: showGenericDetails(Biometrics.CGM_DETAILS_URI, index); break;
				case 9: showGenericDetails(Biometrics.PUMP_DETAILS_URI, index); break;
				case 10: showGenericTable(Biometrics.TEMP_BASAL_URI); break;
			}
		}
	}
	
	class listItem{
		
		int index, code, id;
		long time;
		String json, detail;
		
		public listItem() {}
		
		public listItem(int index, int id, int code, long time, String json)
		{
			this.index = index;
			this.code = code;
			this.time = time;
			this.json = json;
			this.id = id;
		}
		
		public listItem(int index, int id)
		{
			this.index = index;
			this.id = id;
		}
	}
	
	/************************************************************************************
	* Table Display functions
	************************************************************************************/
	
	private void showGenericTable(Uri uri_bio)
	{
		final String FUNC_TAG = "showGenericTable";
		
		Cursor c = getContentResolver().query(uri_bio, null, null, null, null);
		
		if(c!=null)
		{
			if(c.moveToLast())
    		{
    			int k = 0;
	    		do
	    		{
	    			int idColumn = c.getColumnIndex("_id");
	    			int id = -1;
	    			
	    			if(idColumn != -1)
	    				id = c.getInt(c.getColumnIndex("_id"));
	    		
	    			String[] cNames = c.getColumnNames();
	    			for(String s:cNames)
	    			{
	    				Debug.i(TAG, FUNC_TAG, "Column: "+s);
		    			int t = c.getType(c.getColumnIndex(s));
						String strType = "";
						switch(t)
						{
		    				case Cursor.FIELD_TYPE_BLOB: strType="BLOB"; break;
		    				case Cursor.FIELD_TYPE_FLOAT: strType="FLOAT"; break;
		    				case Cursor.FIELD_TYPE_INTEGER: strType="INTEGER"; break;
		    				case Cursor.FIELD_TYPE_NULL: strType="NULL"; break;
		    				case Cursor.FIELD_TYPE_STRING: strType="STRING"; break;
						}
						Debug.i(TAG, FUNC_TAG, "Type: "+strType);
	    			}
	    			
	    			if(id == -1)
	    				listAdapter.add(c.getColumnNames()[0]+": "+c.getString(0));
	    			else
	    			{
	    				long time = -1;
	    				for(String s:c.getColumnNames())
	    				{
	    					if(s.equalsIgnoreCase("time"))
	    						time = c.getLong(c.getColumnIndex(s));
	    				}
	    				if(time != -1)
	    				{
	    					Calendar cal = Calendar.getInstance();
	    	    			TimeZone tz = cal.getTimeZone();
	    	    			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd hh:mm");
	    	    			sdf.setTimeZone(tz);
	    	    			String localTime = sdf.format(new Date(time * 1000));
	    					
	    					listAdapter.add("ID: "+id+" | Time: "+localTime);
	    				}
	    				else
	    					listAdapter.add("ID: "+id+" | "+cNames[1]+":"+c.getLong(c.getColumnIndex(cNames[1])));
	    			}
	    			
	    			items.add(new listItem((listAdapter.getCount() - 1), id));
	    			
	    			Debug.i(TAG, FUNC_TAG, "Adding item index: "+(listAdapter.getCount()-1)+" ID: "+id);
	    			k++;
	    		}
	    		while(c.moveToPrevious() && k < MAX_ENTRIES);
    		}
    		
    		c.close();
    	}
    	else
    		return;
	}
	
	private void showGenericDetails(Uri uri, int index)
	{
		final String FUNC_TAG = "showGenericDetails";
		
		if(items.isEmpty())
			return;
		
		listItem l = items.get(index);
		
		Debug.i(TAG, FUNC_TAG, "Details - ID: "+l.id+" Index: "+(index));
		
		Cursor c;
		if(l.id == -1)
			c = getContentResolver().query(uri, null, null, null, null);
		else
			c = getContentResolver().query(uri, null, "_id = "+l.id, null, null);
    	
    	if(c!=null)
    	{
    		if(c.getCount() == 1 && c.moveToFirst())
    		{
    			String details;
    			int id = -1;
    			
    			if(l.id != -1)
    				id = c.getInt(c.getColumnIndex("_id"));
    			
    			details = "";
    			
    			for(String s:c.getColumnNames())
    			{
    				Debug.i(TAG, FUNC_TAG, "Column: "+s);
    				details += s+": ";
	    			int t = c.getType(c.getColumnIndex(s));
					String strType = "";
					switch(t)
					{
	    				case Cursor.FIELD_TYPE_BLOB: strType="BLOB"; 
	    					details += "Array";
	    					break;
	    				case Cursor.FIELD_TYPE_FLOAT: strType="FLOAT"; 
	    					details += c.getDouble(c.getColumnIndex(s));
	    					break;
	    				case Cursor.FIELD_TYPE_INTEGER: strType="INTEGER"; 
	    					details += c.getLong(c.getColumnIndex(s));
	    					break;
	    				case Cursor.FIELD_TYPE_NULL: strType="NULL"; 
	    					details += "Null";
	    					break;
	    				case Cursor.FIELD_TYPE_STRING: strType="STRING"; 
	    					details += "\""+c.getString(c.getColumnIndex(s))+"\"";
	    					break;
					}
					details += "\n";
					Debug.i(TAG, FUNC_TAG, "DETAILS >>> "+details);
    			}	
    			
    			if(l.id == -1)
    				showDialog("Details", details);
    			else
    				showDialog("ID: "+id, details);
    		}
    		c.close();
    	}
    	else
    		return;
	}
	
	private void showInsulinTable()
	{
		final String FUNC_TAG = "showInsulinTable";
				
		Cursor c = getContentResolver().query(Biometrics.INSULIN_URI, null, null, null, "_id DESC LIMIT 30");
    	
    	if(c!=null)
    	{
    		Debug.i(TAG, FUNC_TAG, "Row count: "+c.getCount());
    		
    		if(c.moveToFirst())
    		{
    			do
	    		{
	    			int id = c.getInt(c.getColumnIndex("_id"));
	    			long req_time = c.getLong(c.getColumnIndex("req_time"));
	    			long deliv_time = c.getLong(c.getColumnIndex("deliv_time"));
	    			double req_total = c.getDouble(c.getColumnIndex("req_total"));
	    			double deliv_total = c.getDouble(c.getColumnIndex("deliv_total"));
	    			int status = c.getInt(c.getColumnIndex("status"));
	    		
	    			Calendar cal = Calendar.getInstance();
	    			TimeZone tz = cal.getTimeZone();
	    			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.US);
	    			sdf.setTimeZone(tz);
	    			String localReqTime;
	    			if (req_time > 0)
	    				localReqTime = sdf.format(new Date(req_time * 1000));
	    			else
	    				localReqTime = "-";
	    			
	    			String localDelivTime;
	    			if (deliv_time > 0)
	    				localDelivTime = sdf.format(new Date(deliv_time * 1000));
	    			else
	    				localDelivTime = "-";
	    			
	    			listAdapter.add("ID: "+id+" | Requested: "+String.format("%.2f",req_total)+"U at "+localReqTime+" | Delivered: "+String.format("%.2f",deliv_total)+"U at "+localDelivTime+" | Status: "+getStatus(status));
	    			
	    			items.add(new listItem((listAdapter.getCount() - 1), id));
	    			
	    			Debug.i(TAG, FUNC_TAG, "Adding item index: "+(listAdapter.getCount()-1)+" ID: "+id);
	    		}
	    		while(c.moveToNext());
    		}
    		
    		c.close();
    	}
    	else
    		return;
	}
	
	private void showInsulinDetails(int index)
	{
		final String FUNC_TAG = "showInsulinDetails";
		
		if(items.isEmpty())
			return;
		
		listItem l = items.get(index);
		
		Debug.i(TAG, FUNC_TAG, "Details - ID: "+l.id+" Index: "+(index));
		
		Cursor c = getContentResolver().query(Biometrics.INSULIN_URI, null, "_id = "+l.id, null, null);
    	
    	if(c!=null)
    	{
    		if(c.getCount() == 1 && c.moveToFirst())
    		{
    			String details;
    			
    			int id = c.getInt(c.getColumnIndex("_id"));
    			long req_time = c.getLong(c.getColumnIndex("req_time"));
    			long deliv_time = c.getLong(c.getColumnIndex("deliv_time"));
    			
    			String status = getStatus(c.getInt(c.getColumnIndex("status")));

    			String dtotal = String.format("%.2f",c.getDouble(c.getColumnIndex("deliv_total")));
				String dbasal = String.format("%.2f",c.getDouble(c.getColumnIndex("deliv_basal")));
				String dmeal = String.format("%.2f",c.getDouble(c.getColumnIndex("deliv_meal")));
				String dcorr = String.format("%.2f",c.getDouble(c.getColumnIndex("deliv_corr")));
    			
				String rtotal = String.format("%.2f",c.getDouble(c.getColumnIndex("req_total")));
				String rbasal = String.format("%.2f",c.getDouble(c.getColumnIndex("req_basal")));
				String rmeal = String.format("%.2f",c.getDouble(c.getColumnIndex("req_meal")));
				String rcorr = String.format("%.2f",c.getDouble(c.getColumnIndex("req_corr")));
				
    			Calendar cal = Calendar.getInstance();
    			TimeZone tz = cal.getTimeZone();
    			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    			sdf.setTimeZone(tz);
    			String reqTime = "-";
    			if(req_time >0)
    				reqTime = sdf.format(new Date(req_time * 1000));
    			
    			String delivTime = "-";
    			if(deliv_time>0)
    				delivTime = sdf.format(new Date(deliv_time * 1000));
    			
    			details = 	"Status: "+ status +
    						"\n\nRequested:"+
    						"\n"+reqTime+
    						"\nTotal: "+rtotal+"U"+
    						"\nB: "+rbasal+"U | M: "+rmeal+"U | C: "+rcorr+"U"+
    						
    						"\n\n"+
    						
    						"Delivered:"+
    						"\n"+delivTime+
    						"\nTotal: "+dtotal+"U"+
    						"\nB: "+dbasal+"U | M: "+dmeal+"U | C: "+dcorr+"U";
    			
    			showDialog("ID: "+id, details);
    		}
    		c.close();
    	}
    	else
    		return;
	}
	
	private void showEventTable()
	{
		final String FUNC_TAG = "showEventTable";
		
		Cursor c = getContentResolver().query(Biometrics.EVENT_URI, null, null, null, null);
    	
    	if(c!=null)
    	{
    		if(c.moveToLast())
    		{
    			int k = 0;
	    		do
	    		{
	    			long time = c.getLong(c.getColumnIndex("time"));
	    			int id = c.getInt(c.getColumnIndex("_id"));
	    			int code = c.getInt(c.getColumnIndex("code"));
	    			String json = c.getString(c.getColumnIndex("json"));
	    		
	    			listAdapter.add("ID: "+id+" | Code: "+Event.getCodeString(code));
	    			
	    			Debug.i(TAG, FUNC_TAG, "Adding item index: "+(listAdapter.getCount()-1)+" ID: "+id);
	    			
	    			items.add(new listItem((listAdapter.getCount() - 1), id, code, time, json));
	    			k++;
	    		}
	    		while(c.moveToPrevious() && k < MAX_ENTRIES);
    		}
    		
    		c.close();
    	}
    	else
    		return;
	}
	
	private void showEventDetails(int index)
	{
		final String FUNC_TAG = "showEventDetails";
		
		listItem l = items.get(index);
		
		Debug.i(TAG, FUNC_TAG, "Details - ID: "+l.id+" Code: "+l.code);
		
		String jsonDescrip = null;
		
		try {
			JSONObject j = new JSONObject(l.json);
			jsonDescrip = j.getString("description");
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		Calendar cal = Calendar.getInstance();
		TimeZone tz = cal.getTimeZone();

		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		sdf.setTimeZone(tz);

		String localTime = sdf.format(new Date(l.time * 1000));
		
		String details = "Time: "+localTime;
		
		if(jsonDescrip != null)
			details += "\n\nDescription: "+jsonDescrip;
		
		showDialog("Details", details);
	}
	
	private void showDialog(String title, String details)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setIcon(android.R.drawable.ic_dialog_info);
		builder.setTitle(title);
		
		builder.setMessage(details);

		AlertDialog dialog = builder.create();
		dialog.show();
		
		dialog.getButton(Dialog.BUTTON_POSITIVE).setEnabled(false);
		dialog.getButton(Dialog.BUTTON_NEGATIVE).setEnabled(false);
	}
	
	/************************************************************************************
	* Auxillary Functions
	************************************************************************************/
	
	public String getStatus(int status)
	{
		switch(status)
		{
			case Pump.PENDING:
				return "Pending";
			case Pump.DELIVERING:
				return "Delivering";
			case Pump.DELIVERED:
				return "Delivered";
			case Pump.CANCELLED:
				return "Cancelled";
			case Pump.INTERRUPTED:
				return "Interrupted";
			case Pump.INVALID_REQ:
				return "Invalid Request";
			case Pump.MANUAL:
			case Pump.PRE_MANUAL:
				return "Manual";
			default:
				return "N/A";
		}
	}
	
	public long getCurrentTimeSeconds() {
		long currentTimeSeconds = (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970 in UTC
		return currentTimeSeconds;
	}
}
