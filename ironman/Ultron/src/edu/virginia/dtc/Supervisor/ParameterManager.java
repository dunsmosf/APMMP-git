package edu.virginia.dtc.Supervisor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Xml;
import android.widget.Toast;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;

public class ParameterManager {
	public static final String TAG = "ParameterManager";
	public static ParameterManager instance;
	public Context context;
	
	public String SETUP_PACKAGE_NAME = "edu.virginia.dtc.DiAsSetup";

	public static final String PARAM_PATH = Environment.getExternalStorageDirectory().getPath() + "/parameters.xml";

	public List<Parameter> params = new ArrayList<Parameter>();

	// Hardware device settings file
	public static final String PREFS_NAME = "HardwareSettingsFile";
	
	private ParameterManager() {}

	public static ParameterManager getInstance(Context context) {
		if (instance == null)
			instance = new ParameterManager();
		instance.context = context;
		return instance;
	}

	public boolean readParameters() {
		final String FUNC_TAG = "readParameters";
		
		// Delete existing parameters
		int deleted = context.getContentResolver().delete(Uri.parse("content://"+ Biometrics.PROVIDER_NAME + "/params"), null, null);
		//Toast.makeText(context, "Deleting previous parameters ("+ deleted +" rows).", Toast.LENGTH_LONG).show();
		Debug.i(TAG, FUNC_TAG, "Deleting previous parameters ("+ deleted +" rows).");
		
		parseParameters();
		
		/*
		if(parametersExistInDb())
		{
			
			if (isSubjectReady()) {
				Debug.i(TAG, FUNC_TAG, "Parameters already exist, not overwriting existing permissions!");
				return;
			}
			else {
				Debug.i(TAG, FUNC_TAG, "No existing subject, overwrite previous parameters");
			}
		}
		*/
		
		
		if(params.isEmpty())
		{
			Toast.makeText(context, "No valid parameters exist!", Toast.LENGTH_LONG).show();
			Debug.e(TAG, FUNC_TAG, "No valid parameters exist!");
			return false;
		}
		else
		{
			Toast.makeText(context, "Valid parameters found!", Toast.LENGTH_LONG).show();
			Debug.i(TAG, FUNC_TAG, "Valid parameters found!");
			
			//Parameters are added to the table
			for(Parameter p:params)
			{
				ContentValues cv = new ContentValues();
				cv.put("name", p.name);
				cv.put("value", p.value);
				cv.put("type", p.type);
				context.getContentResolver().insert(Biometrics.PARAM_URI, cv);
				Debug.i(TAG, FUNC_TAG, "Adding row..."+p.name);
				
				if (p.name.equals("dwm_address_default")) {
					Cursor c = context.getContentResolver().query(Biometrics.SERVER_URI, null, null, null, null);
					if (!c.moveToLast()) {
						ContentValues values = new ContentValues();
						values.put("server_url", p.value);
						context.getContentResolver().insert(Biometrics.SERVER_URI, values);
						Debug.i(TAG, FUNC_TAG, "Server URL saved in database: "+p.value);
					}
					c.close();
				}
				
			}
			return parametersExistInDb();
			
			//Use the Params.getAllParameters call to return a bundle with all parameters
			//You can also use Params.getParameter with a parameter name input to return a bundle with a single parameter
		}
	}
	
	
	public boolean parametersExistInDb() {
		Cursor c = context.getContentResolver().query(Biometrics.PARAM_URI, null, null, null, null);
		if(c != null)
		{
			if(c.getCount() > 0)
			{
				return true;
			}
			c.close();
		}
		return false;
	}

	
	public void parseParameters() {
		final String FUNC_TAG = "parseParameters";

		params.clear();
		XmlPullParser parser = Xml.newPullParser();
		boolean start_read = false, end_read = false;
		
		try {
			BufferedReader reader = new BufferedReader(new FileReader(new File(PARAM_PATH)));
			parser.setInput(reader);
			int eventType = parser.getEventType();

			while (eventType != XmlPullParser.END_DOCUMENT) 
			{
				String name = null;
				switch (eventType) 
				{
					case XmlPullParser.START_DOCUMENT:
						break;
					case XmlPullParser.START_TAG:
						name = parser.getName();
	
						if (name.equalsIgnoreCase("parameter")) {
							try {
								String value = parser.getAttributeValue(1);
								String type = parser.getAttributeValue(2);
								params.add(new Parameter(parser.getAttributeValue(0), value, type));
							} catch (NumberFormatException e) {
								Debug.e(TAG, FUNC_TAG, "Invalid parameter declaration: name=" + parser.getAttributeValue(0) + " value=" + parser.getAttributeValue(1));
							}
						} else if ((name.equalsIgnoreCase("parameters_list"))) {
							start_read = true;
						}
						break;
					case XmlPullParser.END_TAG:
						name = parser.getName();
						if (name.equalsIgnoreCase("parameters_list")) {
							end_read = true;
						}
						break;
				}

				eventType = parser.next();
			}
		} catch (FileNotFoundException e) {
			Debug.i(TAG, FUNC_TAG, "File Not Found: " + e.getMessage());
		} catch (IOException e) {
			Debug.i(TAG, FUNC_TAG, "IO Exception: " + e.getMessage());
		} catch (Exception e) {
			Debug.i(TAG, FUNC_TAG, "Exception: " + e.getMessage());
		}
		
		if (!end_read || !start_read){
			params.clear();
			Debug.i(TAG, FUNC_TAG, "Error in parameters.xml reading, missing start/end tags.");
			//Toast.makeText(context, "Error in parameters.xml reading, missing start/end tags.", Toast.LENGTH_LONG).show();
		}
	}
	
	public boolean isSubjectReady() {
		
		String FUNC_TAG = "isSubjectReady";
		boolean result = false;
		
		Cursor c = context.getContentResolver().query(Biometrics.SUBJECT_DATA_URI, null, null, null, null);
		if (c.moveToFirst()) {
			try {
				result = true;
			}
			catch (Exception e){
				Debug.i(TAG, FUNC_TAG, "unable to retrieve subject 'number'");
			}
		}
		c.close();
		
		Debug.i(TAG, FUNC_TAG, ""+result);
		
		return result;
	}
	
	class Parameter
	{
		public String name, value, type;
		
		public Parameter(String name, String value, String type)
		{
			final String FUNC_TAG = "Parameter";
			
			this.name = name;
			this.value = value;
			this.type = type;
			
			Debug.i(TAG, FUNC_TAG, "Parameter Name: "+this.name+" Value: "+this.value+" Type: "+this.type);
		}
	}
}
