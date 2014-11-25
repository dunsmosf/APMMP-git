//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes, Najib Ben Brahim and Antoine Robert
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.networkService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.DiAsSubjectData;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.Pump;
//import android.content.SharedPreferences;

public class networkService extends Service {
	public final String TAG = "NetworkService";
	private static final boolean DEBUG_MODE = true;
//	private static final boolean LOGGING = false;
	private static final boolean MESSAGE_LOGGING_ENABLED = false;
	private static final int LOG_ACTION_SERIOUS = 5;
	public static final String PREFS_NAME = "HardwareSettingsFile";


	public static final String PING_URL_PATH = "/diabetesassistant/webservices/ping.php";

	public static final String SSM_PROVIDER_NAME = "edu.virginia.dtc.provider.SSM";
	public static final Uri SSM_CONTENT_URI = Uri.parse("content://" + SSM_PROVIDER_NAME + "/SSM");

	public static final String HMS_PROVIDER_NAME = "edu.virginia.dtc.provider.SSM";
	public static final Uri HMS_CONTENT_URI = Uri.parse("content://" + HMS_PROVIDER_NAME + "/HMS");

	// Power management
	private PowerManager pm;
	private PowerManager.WakeLock wl;

	// Current remote monitoring database
	public BroadcastReceiver TickReceiver;
	public BroadcastReceiver ProfileReceiver;
	
	private boolean remoteMonitoringURIValid = true;
	private String remoteMonitoringURI;
	
	public static final int REMOTE_MONITORING_ICON_ID = 0x10000000;
	public static final int NO_REMOTE_MONITORING_ICON_ID = 0x10000001;
	public static final int WEAK_REMOTE_MONITORING_ICON_ID = 0x10000010;

	private String DeviceID;
	private String subject_number;

	String returnString;
	Integer battery_level = 100;
	private long last_log_time = 0;

	public long lastCGMTime = 0;
	public long lastInsulinTime = 0;
	public long lastMealTime = 0;
	public long lastStateEstimateTime = 0;
	public long lastLogTime = 0;
	public long lastDeviceDataTime = 0;

	public static final int SEND_BIOMETRICS_SLEEP_SECS = 30;

	private int timeoutConnection = 10000; // In milliseconds
	private int timeoutSocket = 10000; // In milliseconds
	
	private boolean hasToSendProfiles = false;
	private boolean hasNewSMBG = false;
	
	private final int MAX_SENDING_ATTEMPTS = 20;
	private int consecutivePingFailures = 0;
	
	private int hourlyPingAttempts = 0;
	private int hourlyPingSuccess = 0;
	
	public static final int DATA_BUNDLE_SIZE = 15; //Number of rows to include in one "Send Request"
	
	private boolean logReady = false;
    private String logFile;
    
    public static String[] PARAMS_PARAMS = {"id", "name", "value", "type"};
    private SmbgObserver smbgObserver;
    

	// **************************************************************************************************************
	// Thread Constructors
	// **************************************************************************************************************
	
	private Thread SendDiasdata = new Thread() {
		@Override
		public void run() {
			final String FUNC_TAG = "Send_biometrics";

			int ping_result = 0;
			try {
				ping_result = Send_Ping();
				Debug.i(TAG, FUNC_TAG, "Sending_biometrics : Ping = "+ping_result);
			}
			catch (Exception e) {
				Debug.e(TAG, FUNC_TAG, "Sending_biometrics > error in Ping=" + e.getMessage());
				log_action(TAG, "Sending_biometrics, Error in Ping: " + e.getMessage(), LOG_ACTION_SERIOUS);
			}
			
			if (hourlyPingAttempts >= 120 && isSubjectReady()) {
				updateConnectivityInfo();
			}
			
			//DiAsSubjectData subject_data = readDiAsSubjectData();
			
			if (ping_result > 0 && isSubjectReady())
			{
				sendData(Biometrics.CGM_URI);
				
				sendData(Biometrics.INSULIN_URI);
				
				sendData(Biometrics.MEAL_URI);
				
				sendData(Biometrics.STATE_ESTIMATE_URI);
				
				sendData(Biometrics.LOG_URI);
				
				sendData(Biometrics.DEV_DETAILS_URI);
				
				sendData(Biometrics.SMBG_URI);
				
				sendData(Biometrics.EVENT_URI);
				
				sendData(Biometrics.SYSTEM_URI);
				
				sendData(Biometrics.USER_TABLE_3_URI);
				
				sendData(Biometrics.USER_TABLE_4_URI);
				
				Send_Request_Params(Biometrics.PARAM_URI, PARAMS_PARAMS);
				
				sendData(Biometrics.SUBJECT_DATA_URI);
					
				// Profile data transmission
				for(int i=0;i<4;i++)
				{
					// Sets each URI with the correct string to cycle through the similar profiles
					Uri content_uri = null;
					switch(i)
					{
						case 0:
							content_uri = Biometrics.CR_PROFILE_URI;
							break;
						case 1:
							content_uri = Biometrics.CF_PROFILE_URI;
							break;
						case 2:
							content_uri = Biometrics.BASAL_PROFILE_URI;
							break;
						case 3:
							content_uri = Biometrics.SAFETY_PROFILE_URI;
							break;
					}
					
					if(content_uri != null)
					{
						Cursor profiles_to_send = getContentResolver().query(content_uri, null, "received_server = 0", null, null);
						if (profiles_to_send.getCount() > 0) {
							Debug.i(TAG, FUNC_TAG, profiles_to_send.getCount()+" new profile info to send");
							try {
								
								Cursor sub = getContentResolver().query(content_uri, null, null, null, null);
								Integer cursor_count = sub.getCount();
								Debug.i(TAG, FUNC_TAG, "Sending_subjectData: " + cursor_count.toString());
								sub.moveToFirst();
								JSONArray profile = new JSONArray();
								Integer index = 0;
								while (sub.getCount() != 0 && sub.isAfterLast() == false) {
									JSONObject object = new JSONObject();
									try {
										object.put("time", sub.getString(1));
										object.put("value", sub.getString(2));
										profile.put(index, object);
										
									} catch (JSONException e) {
										Debug.i(TAG, FUNC_TAG, "JSON Encode for profile error: "+e.toString());
									}
									index +=1;
									sub.moveToNext();
								}
								if ((DeviceID != null) && (profile.length() > 0)) {
									Send_Request_timeValue(subject_number, DeviceID, content_uri, profile);
								}
								else {
									Debug.e(TAG, FUNC_TAG, "Sending_diasdata > Send_Request_timeValue: Aborted because of null DeviceID");
								}
								sub.close();
							}
							catch (Exception e) {
								Debug.e(TAG, FUNC_TAG, "Sending_profiledata > error=" + e.getMessage());
								log_action(TAG, "Sending_profiledata, Error: " + e.getMessage(), LOG_ACTION_SERIOUS);
							}
						}
						else {
							Debug.i(TAG, FUNC_TAG, "Profile info already sent");
						}
						profiles_to_send.close();
					}
				}
				
			}
			else {
				Debug.e(TAG, FUNC_TAG, "Sending_diasdata > Server unreachable, Diasdata not sent");
				log_action(TAG, "Sending_diasdata, Error: Server unreachable, Diasdata not sent", LOG_ACTION_SERIOUS);
			}
		}	
	};
	

	
	// **************************************************************************************************************
	// Scheduled Executor Service Setup
	// **************************************************************************************************************

	public static ScheduledExecutorService systemScheduler = Executors.newSingleThreadScheduledExecutor();
	public static ScheduledFuture<?> futureDiasdata;

	
	// **************************************************************************************************************
	// Send Requests Functions
	// **************************************************************************************************************

    public void sendData(Uri content_uri) {
    	
    	String tableName = Biometrics.getTableName(content_uri);
    	
        final String FUNC_TAG = "Send_Request(" + tableName + ")";
        
        if (DeviceID != null) {
        
	        try {
	            
	        	Cursor cursor = getContentResolver().query(content_uri, null, null, null, "_id ASC LIMIT 1");
	        	List<String> columns_list = new LinkedList<String>(Arrays.asList(cursor.getColumnNames()));
	        	columns_list.remove("received_server");
	        	columns_list.remove("send_attempts_server");
	        	Debug.i(TAG,  FUNC_TAG, "Columns: "+columns_list);
	        	cursor.close();
	        	
	            String order_by = new String("");
	            String selection = "received_server = 0 AND send_attempts_server < "+ MAX_SENDING_ATTEMPTS;
	            
	            if(columns_list.contains("time")) {
	            	order_by = "time DESC";
	            }
	            else if (columns_list.contains("deliv_time")){
	            	order_by = "deliv_time DESC";
	            }
	            else {
	            	order_by = null;
	            }
	            if (order_by!=null){
	            	order_by +=" LIMIT "+DATA_BUNDLE_SIZE;
	            }
	            //ArrayList<ArrayList<NameValuePair>> dataBundle = new ArrayList<ArrayList<NameValuePair>>();
	            Cursor c = getContentResolver().query(content_uri, null, selection, null, order_by);
	            Integer cursor_count = c.getCount();
	            InputStream is = null;
	            Debug.i(TAG, FUNC_TAG, FUNC_TAG+" > rows=" + cursor_count.toString());
	            JSONObject post = new JSONObject();
	            JSONArray data = new JSONArray();
	            int index = 0;
	            
	            c.moveToFirst();
	            while (c.isAfterLast() == false) {
	                Debug.i("Hello", FUNC_TAG, "Hello "+tableName+" 1");
	
	                //long time = c.getLong(c.getColumnIndex("time"));
	                Integer attempts = Integer.parseInt(c.getString(c.getColumnIndex("send_attempts_server")));
	
	                attempts += 1;
	
	                ContentValues values = new ContentValues();
	                values.put("send_attempts_server", attempts);
	
	                Integer row = getContentResolver().update(content_uri, values, "_id = "+c.getString(c.getColumnIndex("_id")), null);
	                Debug.i(TAG, FUNC_TAG, tableName+" attempts incremented: "+row+" row, attempts = "+attempts);
	
	                JSONObject object = new JSONObject();
	                
	                for(int i=0; i<columns_list.size(); i++) {
	                	if (columns_list.get(i).equals("_id")) {
	                		object.put("id", c.getString(c.getColumnIndex(columns_list.get(i))));
	                	} else {
	                		object.put(columns_list.get(i), c.getString(c.getColumnIndex(columns_list.get(i))));
	                	}
	                }
	                
	                // Specific to INSULIN table
	                if (columns_list.contains("deliv_time")) {
	                	object.put("time", c.getString(c.getColumnIndex("deliv_time")));
	                }
	                
	                // Specific to SUBJECTDATA table
	                if (tableName.equals("subjectdata")) {
	                	TimeZone tz = TimeZone.getDefault();
						String tz_id = tz.getID();
						Debug.i(TAG, FUNC_TAG, "Getting default TimeZone: " +tz_id);
						object.put("timezone", tz_id);
						
						String center_name = Params.getString(getContentResolver(), "center", "");
						String protocol_code = Params.getString(getContentResolver(), "protocol", "");
						
						object.put("center_name", center_name);
						object.put("protocol_code", protocol_code);
						
	                }
	                
	                //Debug.i(TAG, FUNC_TAG, "Row added: "+object.toString());
	                
	                data.put(index, object);
	                index += 1;
	                
	                c.moveToNext();
	            }
	            
	            c.close();
	            
	            if (data.length() > 0) {
	            	
	            	post.put("subjectnumber", subject_number);
	                post.put("macaddress", DeviceID);
	            	post.put("data", data);
	            	
	            	//Debug.i(TAG, FUNC_TAG, "Data to send: "+data.toString());
	                //HTTP post
	                try {
	                    DefaultHttpClient httpclient = new DefaultHttpClient();
	                    HttpParams httpParameters = new BasicHttpParams();
	        			HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
	        			HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
	        			httpclient.setParams(httpParameters);
	        			
	                    String PostString;
	                    Debug.i(TAG, FUNC_TAG, tableName+" > remoteMonitoringURIValid=" + remoteMonitoringURIValid + ", remoteMonitoringURI=" + remoteMonitoringURI);
	                    if (remoteMonitoringURIValid) {
	                        PostString = new String(remoteMonitoringURI);
	                        PostString = PostString.concat(getUrlPath(tableName));
	                    }
	                    else {
	                        PostString = new String(getUrlPath(tableName));
	                    }
	                    HttpPost httppost = new HttpPost(PostString);
	                    httppost.setEntity(new StringEntity(post.toString()));
	                    httppost.setHeader("Accept", "application/json");
	                    httppost.setHeader("Content-type","application/json");
	                    
	                    //Debug.i(TAG, FUNC_TAG, "POSTED entity: "+EntityUtils.toString(httppost.getEntity()));
	                    
	                    HttpResponse response = httpclient.execute(httppost);
	                    HttpEntity entity = response.getEntity();
	
	                    is = entity.getContent();
	
	                } catch (Exception e) {
	                    Debug.i(TAG, FUNC_TAG, "Send_Request_device > 1 > Error in http connection " + e.toString());
	                    log_action(TAG, "Send_Request_device, Connection error: " + e.toString(), LOG_ACTION_SERIOUS);
	                }
	
	                //convert response to string
	                try {
	                	String resultString = "";
	                    String status = "";
	                	
	                    BufferedReader reader = new BufferedReader(new InputStreamReader(is,"iso-8859-1"),8);
	                    StringBuilder sb = new StringBuilder();
	                    String line = null;
	                    while ((line = reader.readLine()) != null) {
	                        sb.append(line + "\n");
	                    }
	                    is.close();
	                    resultString=sb.toString();
	                    Debug.i(TAG, FUNC_TAG, "result = "+resultString);
	                    
	                    JSONArray result = new JSONArray(resultString);
	                    status = result.getJSONObject(0).getString("Status");;
	                    Debug.i(TAG, FUNC_TAG, "JSON_Parser_Result = "+status);
	                    if (status.equals("Received"))
	                    {
	                    	//JSONObject savedIds = result.getJSONObject(1);
	                    	JSONArray savedIds = result.getJSONObject(1).getJSONArray("saved_ids");
	                    	int savedIdsLength = savedIds.length();
	                    	
	                    	for (int i=0; i<savedIdsLength; i++) {
	                    		String id = savedIds.get(i).toString();
	                    		ContentValues new_values = new ContentValues();
		                        new_values.put("received_server", true);
		                        
		                        // Set "received_server" to false to re-send data when status is not final
		                        // For Insulin ("pending" or "delivering")
		                        if (tableName == "insulin") {
		                        	Cursor insulinRow = getContentResolver().query(content_uri, new String[] {"status"}, "_id = "+id, null, null);
		                        	insulinRow.moveToFirst();
		                        	if (insulinRow.getInt(insulinRow.getColumnIndex("status")) == Pump.PENDING || insulinRow.getInt(insulinRow.getColumnIndex("status")) == Pump.DELIVERING) {
		                        		new_values.put("received_server", false);
		                        	}
		                        	insulinRow.close();
		                        }
		                        // For Meal ("pending")
//		                        if (diasdata == "meal") {
//		                        	Cursor mealRow = getContentResolver().query(content_uri, new String[] {"meal_status"}, "_id = "+id, null, null);
//		                        	mealRow.moveToFirst();
//		                        	if (mealRow.getInt(mealRow.getColumnIndex("meal_status")) == Meal.MEAL_STATUS_PENDING) {
//		                        		new_values.put("received_server", false);
//		                        	}
//		                        	mealRow.close();
//		                        }
		                        
		                        int new_row = getContentResolver().update(content_uri, new_values, "_id = "+id, null);
		                        Debug.i(TAG, FUNC_TAG, tableName+" data received: "+new_row+" row");
	                    	}
	                    }
	                    else if (status.equals("Error")) {
	                        Debug.i(TAG, FUNC_TAG, status+" in "+tableName+" data reception by server");
	                    }
	                    else {
	                        Debug.i(TAG, FUNC_TAG, "Unknown error in "+tableName+" data reception: status = "+status );
	                    }
	                }
	                catch(Exception e) {
	                    Debug.i(TAG, FUNC_TAG, "JSON Parse Reception "+tableName+": Error converting result "+e.toString());
	                    log_action(TAG, "Send_"+tableName+", Response conversion error: "+e.toString(), LOG_ACTION_SERIOUS);
	                }
	
	                Debug.i("Hello", FUNC_TAG, "Hello "+tableName+" 2");
	            }
	            else {
	            	Debug.i(TAG, FUNC_TAG, "JSON Array empty, no data to send.");
	            }
	            
	        } catch (Exception e) {
	            Debug.e(TAG, FUNC_TAG, FUNC_TAG+" > error=" + e.getMessage());
	            log_action(TAG, FUNC_TAG+", Error: " + e.getMessage(), LOG_ACTION_SERIOUS);
	        }
	        
        }
        else {
        	Debug.e(TAG, FUNC_TAG, "Send Request Aborted because of null DeviceID");
        }

    }
    
    public void Send_Request_Params(Uri content_uri, String[] diasdataParams) {
    	String diasdata = "params";
        final String FUNC_TAG = "Send_Request(" + diasdata + ")";
        
        if (DeviceID != null) {
        
	        try {
	            //DiAsSubjectData subject_data = readDiAsSubjectData();
	            
	            String order_by = null;
	            String selection = "received_server = 0 AND send_attempts_server < " + MAX_SENDING_ATTEMPTS;
	            
	            JSONObject post = new JSONObject();
	            JSONArray data = new JSONArray();
	            
	            Cursor c = getContentResolver().query(content_uri, null, selection, null, order_by);
	            Integer cursor_count = c.getCount();
	            Debug.i(TAG, FUNC_TAG, FUNC_TAG+" > rows=" + cursor_count.toString());
	            
	            JSONArray parameters = new JSONArray();
	            c.moveToFirst();
	            int index = 0;
	            while (c.isAfterLast() == false) {
	                Debug.i("Hello", FUNC_TAG, "Hello "+diasdata+" 1");
	
	                Integer attempts = Integer.parseInt(c.getString(c.getColumnIndex("send_attempts_server")));
	
	                attempts += 1;
	
	                ContentValues values = new ContentValues();
	                values.put("send_attempts_server", attempts);
	
	                Integer row = getContentResolver().update(content_uri, values, "_id = "+c.getString(c.getColumnIndex("_id")), null);
	                Debug.i(TAG, FUNC_TAG, diasdata+" attempts incremented: "+row+" row, attempts = "+attempts);
	                
	                JSONObject object = new JSONObject();
					try {
						object.put(c.getString(c.getColumnIndex("name")), c.getString(c.getColumnIndex("value")));
						parameters.put(index, object);
						
					} catch (JSONException e) {
						Debug.i(TAG, FUNC_TAG, "JSON Encode for parameters error: "+e.toString());
					}
					index +=1;
					c.moveToNext();
	            }
	            c.close();
	            
	            if (parameters.length() > 0) {
		            JSONObject parametersObject = new JSONObject();
		            parametersObject.put("parameters", parameters);
		            data.put(0, parametersObject);
		            
		            InputStream is = null;
		        	
	                post.put("subjectnumber", subject_number);
	                post.put("macaddress", DeviceID);
	                post.put("data", data);
	                
	                //HTTP post
	                try {
	                    DefaultHttpClient httpclient = new DefaultHttpClient();
	                    HttpParams httpParameters = new BasicHttpParams();
	        			HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
	        			HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
	        			httpclient.setParams(httpParameters);
	        			
	                    String PostString;
	                    Debug.i(TAG, FUNC_TAG, diasdata+" > remoteMonitoringURIValid=" + remoteMonitoringURIValid + ", remoteMonitoringURI=" + remoteMonitoringURI);
	                    if (remoteMonitoringURIValid) {
	                        PostString = new String(remoteMonitoringURI);
	                        PostString = PostString.concat(getUrlPath(diasdata));
	                    }
	                    else {
	                        PostString = new String(getUrlPath(diasdata));
	                    }
	                    HttpPost httppost = new HttpPost(PostString);
	                    httppost.setEntity(new StringEntity(post.toString()));
	                    httppost.setHeader("Accept", "application/json");
	                    httppost.setHeader("Content-type", "application/json");
	                    HttpResponse response = httpclient.execute(httppost);
	                    HttpEntity entity = response.getEntity();
	
	                    is = entity.getContent();
	
	                } catch (Exception e) {
	                    Debug.i(TAG, FUNC_TAG, "Send_Request_device > 1 > Error in http connection " + e.toString());
	                    log_action(TAG, "Send_Request_device, Connection error: " + e.toString(), LOG_ACTION_SERIOUS);
	                }
	
	                //convert response to string
	                try {
	                	String result = "";
	                    String status = "";
	                	
	                    BufferedReader reader = new BufferedReader(new InputStreamReader(is,"iso-8859-1"),8);
	                    StringBuilder sb = new StringBuilder();
	                    String line = null;
	                    while ((line = reader.readLine()) != null) {
	                        sb.append(line + "\n");
	                    }
	                    is.close();
	                    result=sb.toString();
	                    Debug.i(TAG, FUNC_TAG, "result = "+result);
	                    status = JSON_Parser_Reception(result);
	                    
	                    if (status.equals("Received"))
	                    {
	                        ContentValues new_values = new ContentValues();
	                        new_values.put("received_server", true);
                        
	                        int new_row = getContentResolver().update(content_uri, new_values, "received_server = 0 AND send_attempts_server < " + MAX_SENDING_ATTEMPTS, null);
		                    Debug.i(TAG, FUNC_TAG, diasdata+" data received: "+new_row+" row");
		                     
	                    }
	                    else if (status.equals("Error")) {
	                        Debug.i(TAG, FUNC_TAG, status+" in "+diasdata+" data reception by server");
	                    }
	                    else {
	                        Debug.i(TAG, FUNC_TAG, "Unknown error in "+diasdata+" data reception: status = "+status );
	                    }
	                }
	                catch(Exception e) {
	                    Debug.i(TAG, FUNC_TAG, "JSON Parse Reception "+diasdata+": Error converting result "+e.toString());
	                    log_action(TAG, "Send_"+diasdata+", Response conversion error: "+e.toString(), LOG_ACTION_SERIOUS);
	                }
	
	                Debug.i("Hello", FUNC_TAG, "Hello "+diasdata+" 2");
	            }
	            
	        } catch (Exception e) {
	            Debug.e(TAG, FUNC_TAG, FUNC_TAG+" > error=" + e.getMessage());
	            log_action(TAG, FUNC_TAG+", Error: " + e.getMessage(), LOG_ACTION_SERIOUS);
	        }
	        
        }
        else {
        	Debug.e(TAG, FUNC_TAG, "Send Request Aborted because of null DeviceID");
        }
    }
    
	
	public void Send_Request_timeValue(String subjectnumber, String macaddress, Uri uri, JSONArray profile)
	{
		final String FUNC_TAG = "Send_Request_timeValue";
		
		String tableName = Biometrics.getTableName(uri);
		
		JSONObject post = new JSONObject();
		JSONArray data = new JSONArray();
		
		JSONObject profiles = new JSONObject();
		
		InputStream is = null;
		
		try {
			profiles.put("profiles", profile);
			data.put(0, profiles);
			
			post.put("subjectnumber", subjectnumber);
			post.put("macaddress", macaddress);
			post.put("data", data);
			
			//HTTP post
			try {
				DefaultHttpClient httpclient = new DefaultHttpClient();
				HttpParams httpParameters = new BasicHttpParams();
				HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
				HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
				httpclient.setParams(httpParameters);
				
				String PostString;
				Debug.i(TAG, FUNC_TAG, "Send_Request_timeValue > remoteMonitoringURIValid=" + remoteMonitoringURIValid + ", remoteMonitoringURI=" + remoteMonitoringURI);
				
				if (remoteMonitoringURIValid) {
					PostString = new String(remoteMonitoringURI);
					PostString = PostString.concat(getUrlPath(tableName));
				}
				else {
					PostString = new String(getUrlPath(tableName));
				}
				
				HttpPost httppost = new HttpPost(PostString);
				httppost.setEntity(new StringEntity(post.toString()));
				HttpResponse response = httpclient.execute(httppost);
				HttpEntity entity = response.getEntity();
				is = entity.getContent();
	
			} catch (Exception e) {
				Debug.i(TAG, FUNC_TAG, "Error in http connection " + e.toString());
				log_action(TAG, "Send_Request_timeValue, Connection error: " + e.toString(), LOG_ACTION_SERIOUS);
			}
			
			//convert response to string
            try {
            	String result = "";
                String status = "";
            	
                BufferedReader reader = new BufferedReader(new InputStreamReader(is,"iso-8859-1"),8);
                StringBuilder sb = new StringBuilder();
                String line = null;
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
                is.close();
                result=sb.toString();
                Debug.i(TAG, FUNC_TAG, "result = "+result);
                status = JSON_Parser_Reception(result);
                
                if (status.equals("Received"))
                {
                    Debug.i(TAG, FUNC_TAG, "Profile data received");
                    
                    ContentValues new_values = new ContentValues();
                    new_values.put("received_server", true);
                    getContentResolver().update(uri, new_values, null, null);

                }
                else if (status.equals("Error")) {
                    Debug.i(TAG, FUNC_TAG, status+" in profile data reception by server");
                }
                else {
                    Debug.i(TAG, FUNC_TAG, "Unknown error in profile data reception: status = "+status );
                }
            }
            catch(Exception e) {
                Debug.i(TAG, FUNC_TAG, "JSON Parse Reception profile: Error converting result "+e.toString());
                log_action(TAG, "Send_profile, Response conversion error: "+e.toString(), LOG_ACTION_SERIOUS);
            }
		}
		catch (JSONException e) {
			Debug.i(TAG, FUNC_TAG, "JSONException when building post content: "+e.getMessage());
		}
	}
	
	
	/***
	 * Send the data bundled as 'data' for the table 'tableName' to the server through and HTTP POST request.
	 * 
	 * @param tableName, the name of the table used to build the POST URL. 
	 * @param data, the bundle of data enclosed with the POST request.
	 * @return the response from the server, or null if an issue occurred.
	 */
	public HttpResponse postRequest(Uri content_uri, JSONArray data) {
		
		final String FUNC_TAG = "postRequest";
		
		String tableName = Biometrics.getTableName(content_uri);
		HttpResponse response = null;
		
		JSONObject post = new JSONObject();
		
		try {
			post.put("subjectnumber", subject_number);
			post.put("macaddress", DeviceID);
			post.put("data", data);
			
			//HTTP post
			try {
				DefaultHttpClient httpclient = new DefaultHttpClient();
				HttpParams httpParameters = new BasicHttpParams();
				HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
				HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
				httpclient.setParams(httpParameters);
				
				String PostString;
				Debug.i(TAG, FUNC_TAG, "Send_Request_timeValue > remoteMonitoringURIValid=" + remoteMonitoringURIValid + ", remoteMonitoringURI=" + remoteMonitoringURI);
				
				if (remoteMonitoringURIValid) {
					PostString = new String(remoteMonitoringURI);
					PostString = PostString.concat(getUrlPath(tableName));
				}
				else {
					PostString = new String(getUrlPath(tableName));
				}
				
				HttpPost httppost = new HttpPost(PostString);
				httppost.setEntity(new StringEntity(post.toString()));
				response = httpclient.execute(httppost);
				
			} catch (Exception e) {
				Debug.i(TAG, FUNC_TAG, "Error in http connection " + e.toString());
				log_action(TAG, FUNC_TAG+", Connection error: " + e.toString(), LOG_ACTION_SERIOUS);
			}
		}
		catch (JSONException e) {
			Debug.i(TAG, FUNC_TAG, "JSONException when building post content: "+e.getMessage());
		}
		
		return response;
	}
	
	
	/***
	 * Handles the response from the server after sending data.
	 * 
	 * @param content_uri
	 * @param response
	 * @return whether the data has been received properly by the server, even partially.
	 */
	public boolean handleResponse(Uri content_uri, HttpResponse response) {
		
		final String FUNC_TAG = "handleResponse";
		
		InputStream is = null;
		
		boolean received = false;
		
		try {
			is = response.getEntity().getContent();
		}
		catch(IOException e) {
			Debug.i(TAG, FUNC_TAG, "Error getting response content: "+e.toString());
			return received;
		}
		
		String tableName = Biometrics.getTableName(content_uri);
		
		//convert response to string
    	String resultString = "", status = "";
    	StringBuilder sb = new StringBuilder();
        String line = null;
        BufferedReader reader;
        JSONArray result =null;
        
		try {
			reader = new BufferedReader(new InputStreamReader(is,"iso-8859-1"),8);
		} catch (UnsupportedEncodingException e) {
			Debug.i(TAG, FUNC_TAG, "Error encoding response content: "+e.toString());
			return received;
		}
        
        try {
			while ((line = reader.readLine()) != null) {
			    sb.append(line + "\n");
			}
		} catch (IOException e) {
			Debug.i(TAG, FUNC_TAG, "Error reading buffer of response content: "+e.toString());
			return received;
		}
        try {
			is.close();
		} catch (IOException e) {
			Debug.i(TAG, FUNC_TAG, "Error encoding response content: "+e.toString());
			return received;
		}
        resultString=sb.toString();
        Debug.i(TAG, FUNC_TAG, "result = "+resultString);
        
        // Gets status from parsed response
        try {
        	result = new JSONArray(resultString);
			status = result.getJSONObject(0).getString("Status");
		} catch (JSONException e) {
			Debug.i(TAG, FUNC_TAG, "Error parsing response status (JSON): "+e.toString());
			return received;
		};
        Debug.i(TAG, FUNC_TAG, "JSON_Parser_Result = "+status);
        
        if (status.equals("Received"))
        {
        	if(Arrays.asList(Biometrics.TIME_BASED_DATA_URIS).contains(content_uri))
        	{
        		
	        	JSONArray savedIds = null;
				try {
					savedIds = result.getJSONObject(1).getJSONArray("saved_ids");
					
					for (int i=0; i<savedIds.length(); i++) {
		        		String id = savedIds.get(i).toString();
		        		ContentValues new_values = new ContentValues();
		                new_values.put("received_server", true);
		                
		                // Set "received_server" to false to re-send data when status is not final
		                // For Insulin ("pending" or "delivering")
		                if (content_uri == Biometrics.INSULIN_URI) {
		                	Cursor insulinRow = getContentResolver().query(content_uri, new String[] {"status"}, "_id = "+id, null, null);
		                	insulinRow.moveToFirst();
		                	if (insulinRow.getInt(insulinRow.getColumnIndex("status")) == Pump.PENDING || insulinRow.getInt(insulinRow.getColumnIndex("status")) == Pump.DELIVERING) {
		                		new_values.put("received_server", false);
		                	}
		                	insulinRow.close();
		                }
		                // For Meal ("pending")
		//                    if (diasdata == "meal") {
		//                    	Cursor mealRow = getContentResolver().query(content_uri, new String[] {"meal_status"}, "_id = "+id, null, null);
		//                    	mealRow.moveToFirst();
		//                    	if (mealRow.getInt(mealRow.getColumnIndex("meal_status")) == Meal.MEAL_STATUS_PENDING) {
		//                    		new_values.put("received_server", false);
		//                    	}
		//                    	mealRow.close();
		//                    }
		                
		                int new_row = getContentResolver().update(content_uri, new_values, "_id = "+id, null);
		                Debug.i(TAG, FUNC_TAG, tableName+" data received: "+new_row+" row");
		        	}
		        	
					received = true;
		        	
				} catch (JSONException e) {
					Debug.i(TAG, FUNC_TAG, "Error parsing response 'saved ids' (JSON): "+e.toString());
					return received;
				}
        	}
        	else if(	Arrays.asList(Biometrics.SINGLE_ROW_TABLES_URIS).contains(content_uri)
        			 || Arrays.asList(Biometrics.PROFILE_URIS).contains(content_uri)
        			 || content_uri==Biometrics.PARAM_URI )
        	{
        		ContentValues new_values = new ContentValues();
                new_values.put("received_server", true);
        		int new_row = getContentResolver().update(content_uri, new_values, null, null);
                Debug.i(TAG, FUNC_TAG, tableName+" data received: "+new_row+" rows");
                
                received = true;
        	}
        }
        else if (status.equals("Error")) {
            Debug.i(TAG, FUNC_TAG, status+" in "+tableName+" data reception by server");
        }
        else {
            Debug.i(TAG, FUNC_TAG, "Unknown error in "+tableName+" data reception: status = "+status );
        }
        
        return received;
	}
	
	
	public int Send_Ping() {
		final String FUNC_TAG = "Send_Ping";

		int output = 0;
		InputStream is = null;
        String result = "", res = "";
        
        hourlyPingAttempts +=1;
        
        ArrayList<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
		nameValuePairs.add(new BasicNameValuePair("request", "ping"));
		
		//HTTP post
		try {
			DefaultHttpClient httpclient = new DefaultHttpClient();
			HttpParams httpParameters = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
			HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
			httpclient.setParams(httpParameters);
			
			String PostString;
			Debug.i(TAG, FUNC_TAG, "Send_Request_ping > remoteMonitoringURIValid=" + remoteMonitoringURIValid + ", remoteMonitoringURI=" + remoteMonitoringURI);
			if (remoteMonitoringURIValid) {
				PostString = new String(remoteMonitoringURI);
				PostString = PostString.concat(PING_URL_PATH);
			}
			else {
				PostString = new String(PING_URL_PATH);
			}
			HttpPost httppost = new HttpPost(PostString);
			httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
			HttpResponse response = httpclient.execute(httppost);
			HttpEntity entity = response.getEntity();
			is = entity.getContent();

		} catch (Exception e) {
			Debug.i(TAG, FUNC_TAG, "Error in http connection " + e.toString());
			log_action(TAG, "Send_Request_ping, Connection error: " + e.toString(), LOG_ACTION_SERIOUS);
		}
		
        //convert response to string
        try{
            BufferedReader reader = new BufferedReader(new InputStreamReader(is,"iso-8859-1"),8);
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
            }
            is.close();
            result=sb.toString();
            res = JSON_Parser_Reception(result);
            Debug.i(TAG, FUNC_TAG, "result "+res);
            if (res.equals("Received"))
            {
            	output += 1;
            }
            Debug.i("HERE FOCUS state", FUNC_TAG,returnString+lastCGMTime);
        }catch(Exception e){
                Debug.i(TAG, FUNC_TAG, "Error converting result "+e.toString());
				log_action(TAG, "Send_Ping, Response conversion error: "+e.toString(), LOG_ACTION_SERIOUS);
        }
        Debug.i(TAG, FUNC_TAG, "Send_Request_Ping result = "+ output);

        // Set up icons indicating state of DWM connectivity
        Intent rmIconIntent = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
		rmIconIntent.putExtra("id", REMOTE_MONITORING_ICON_ID);
		rmIconIntent.putExtra("resourcePackage", "edu.virginia.dtc.networkService");	
		rmIconIntent.putExtra("resourceID", R.drawable.remote_monitoring_cloud);
		
		Intent weakRmIconIntent = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
		weakRmIconIntent.putExtra("id", WEAK_REMOTE_MONITORING_ICON_ID);
		weakRmIconIntent.putExtra("resourcePackage", "edu.virginia.dtc.networkService");	
		weakRmIconIntent.putExtra("resourceID", R.drawable.weak_remote_monitoring_cloud);
		
		Intent noRmIconIntent = new Intent("edu.virginia.dtc.intent.CUSTOM_ICON");
		noRmIconIntent.putExtra("id", NO_REMOTE_MONITORING_ICON_ID);
		noRmIconIntent.putExtra("resourcePackage", "edu.virginia.dtc.networkService");	
		noRmIconIntent.putExtra("resourceID", R.drawable.no_remote_monitoring_cloud);
		
        if (output > 0) {
        	hourlyPingSuccess +=1;
			rmIconIntent.putExtra("remove", false);
			weakRmIconIntent.putExtra("remove", true);
			noRmIconIntent.putExtra("remove", true);
			consecutivePingFailures = 0;
			Debug.i(TAG, FUNC_TAG, "== Cloud Icon: GREEN");
        }
        else {
        	consecutivePingFailures += 1;
        	
        	if (consecutivePingFailures > 9) {
        		// Icon switches and stays to "NO_REMOTE" above 9 failed ping attempts.
        		rmIconIntent.putExtra("remove", true);
    			weakRmIconIntent.putExtra("remove", true);
    			noRmIconIntent.putExtra("remove", false);
    			Debug.i(TAG, FUNC_TAG, "== Cloud Icon: RED");
        		
        		if (consecutivePingFailures % 10 == 0) {
        			// Only generate an Event once every 10 failed ping attempts
        			// (i.e. once every 10xSEND_BIOMETRICS_SLEEP_SECS seconds of disconnection)
                	Bundle bun = new Bundle();
                	bun.putString("description", "Network Service, DWM Server unreachable: 10 consecutive connection attempts failed.");
            		Event.addEvent(this, Event.EVENT_NETWORK_SERVER_UNREACHABLE, Event.makeJsonString(bun), Event.SET_POPUP);
                }
        	}
        	else {
        		// Icon switches to "WEAK_REMOTE" for intermittent 
        		rmIconIntent.putExtra("remove", true);
    			weakRmIconIntent.putExtra("remove", false);
    			noRmIconIntent.putExtra("remove", true);
    			Debug.i(TAG, FUNC_TAG, "== Cloud Icon: YELLOW");
        	}
        }
        Debug.i(TAG, FUNC_TAG, "Ping: consecutivePingFailures: " + consecutivePingFailures);
        
        sendBroadcast(rmIconIntent);
        sendBroadcast(weakRmIconIntent);
        sendBroadcast(noRmIconIntent);
        
        return output;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		final String FUNC_TAG = "onCreate";

		super.onCreate();

		SimpleDateFormat sdfDate = new SimpleDateFormat("MM-dd-yyyy");
	    Date now = new Date();
	    String strDate = sdfDate.format(now);
		String path = "NetworkServiceLogFile_"+strDate+".txt";
		
		logFile = path;
		
		// Get the unique device identifier (IMEI)
		TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		DeviceID = tm.getDeviceId();
		Debug.i(TAG, FUNC_TAG, "DeviceID=" + DeviceID);

		remoteMonitoringURIValid = false;
		remoteMonitoringURI = null;
		
		Cursor c = getContentResolver().query(Biometrics.SERVER_URI, null, null, null, null);
		if (c.moveToLast()) {
			remoteMonitoringURI = c.getString(c.getColumnIndex("server_url"));
		}
		else {
			remoteMonitoringURI = "";
		}
		c.close();
		
		if(remoteMonitoringURI!=null && !remoteMonitoringURI.equalsIgnoreCase(""))
			remoteMonitoringURIValid = true;
		
		Debug.i(TAG, FUNC_TAG, "onCreate()");
		Debug.i(TAG, FUNC_TAG, "Remote Monitoring URI: "+remoteMonitoringURI);
		
		ProfileReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent)
			{
				hasToSendProfiles = true;
				Debug.i(TAG, FUNC_TAG, "HasToSendProfiles = "+ hasToSendProfiles);
			}
		};
		registerReceiver(ProfileReceiver, new IntentFilter(DiAsSubjectData.PROFILE_CHANGE));
		
		last_log_time = 0;

		// Tracking System
		Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
		i.putExtra("Service", "Network");
		i.putExtra("Status", "Created");
		i.putExtra("time", getCurrentTimeSeconds());
		sendBroadcast(i);

		// Startup executors
		futureDiasdata = systemScheduler.scheduleAtFixedRate(SendDiasdata, 0, SEND_BIOMETRICS_SLEEP_SECS, TimeUnit.SECONDS);

		// Set up a Notification for this Service
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		int icon = R.drawable.icon;
		CharSequence tickerText = "";
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, tickerText, when);
		Context context = getApplicationContext();
		CharSequence contentTitle = "NetworkService v1.0";
		CharSequence contentText = "Remote Monitoring";
		Intent notificationIntent = new Intent(this, networkService.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		final int NETWORKSERVICE_ID = 14;
//		mNotificationManager.notify(NETWORKSERVICE_ID, notification);
		// Make this a Foreground Service
		startForeground(NETWORKSERVICE_ID, notification);
		// Keep the CPU running even after the screen dims
		pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		wl.acquire();
		
		smbgObserver = new SmbgObserver(new Handler());
		getContentResolver().registerContentObserver(Biometrics.SMBG_URI, true, smbgObserver);
	}

	
	@Override
	public void onDestroy() {	   
		Toast.makeText(this, "Data sending Service Stopped", Toast.LENGTH_LONG).show();
		
		//Tracking System
		if (futureDiasdata != null)
			futureDiasdata.cancel(true);
		unregisterReceiver(ProfileReceiver);
		//unregisterReceiver(TickReceiver);
		wl.release();
		if(smbgObserver != null)
			getContentResolver().unregisterContentObserver(smbgObserver);
	}

	
	public long getCurrentTimeSeconds() {
		return System.currentTimeMillis() / 1000;	  // Seconds since 1/1/1970		
	}

	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String FUNC_TAG = "onStartCommand";

		Debug.i(TAG, FUNC_TAG, "onStartCommand()");
		
		//Tracking System
		Intent i = new Intent("edu.virginia.dtc.LOG_ACTION");
		i.putExtra("Service", "Network");
		i.putExtra("Status", "Started");
		i.putExtra("time", getCurrentTimeSeconds());
		sendBroadcast(i);

		return 0;
	}
	
	
	/***
	 * Checks whether subject data has been initialized.
	 * Updates the Network Service's subject_number variable when available.
	 * @return whether subject number is stored in database.
	 */
	public boolean isSubjectReady() {
		
		String FUNC_TAG = "isSubjectReady";
		boolean result = false;
		
		Cursor c = getContentResolver().query(Biometrics.SUBJECT_DATA_URI, null, null, null, null);
		if (c.moveToFirst()) {
			try {
				subject_number = c.getString(c.getColumnIndex("session"));
				result = true;
			}
			catch (Exception e){
				Debug.i(TAG, FUNC_TAG, "unable to retrieve subject 'number'");
				subject_number = null;
			}
		}
		c.close();
		
		Debug.i(TAG, FUNC_TAG, ""+result+", subject number = "+subject_number );
		
		return result;
	}
	
	
	/***
	 * 
	 */
	public void updateConnectivityInfo() {
		final String FUNC_TAG = "updateConnectivityInfo";
		
		Debug.i(TAG, FUNC_TAG, "Connectivity data gathered: "+ hourlyPingAttempts +" attempted connections, "+ hourlyPingSuccess +" successful connections on the last hour.");
		
		String order_by = "time DESC LIMIT 1";
        String selection = "received_server = 0 and send_attempts_server < " + MAX_SENDING_ATTEMPTS;
        
        Cursor c = getContentResolver().query(Biometrics.DEV_DETAILS_URI, new String[]{"_id"}, selection, null, order_by);
        if(c.moveToFirst()) {
        	ContentValues values = new ContentValues();
    		
    		values.put("ping_attempts_last_hour", hourlyPingAttempts);
    		values.put("ping_success_last_hour", hourlyPingSuccess);
    		
    		Integer row = getContentResolver().update(Biometrics.DEV_DETAILS_URI, values, "_id = "+c.getString(c.getColumnIndex("_id")), null);
    		Debug.i(TAG, FUNC_TAG, row+" row of Device Details updated.");
    		
    		hourlyPingAttempts = 0;
    		hourlyPingSuccess = 0;
        }
        c.close();
	}

	
	public boolean readTvector(Tvector tvector, Uri uri) {
		boolean retvalue = false;
		Cursor c = getContentResolver().query(uri, null, null, null, null);
		long t, t2 = 0;
		double v;
		if (c.moveToFirst()) {
			do {
				t = c.getLong(c.getColumnIndex("time"));
				if (c.getColumnIndex("endtime") < 0){
					v = c.getDouble(c.getColumnIndex("value"));
					//Debug.i(TAG, FUNC_TAG, "readTvector: t=" + t + ", v=" + v);
					tvector.put(t, v);
				} else if (c.getColumnIndex("value") < 0){
					//Debug.i(TAG, FUNC_TAG, "readTvector: t=" + t + ", t2=" + t2);
					t2 = c.getLong(c.getColumnIndex("endtime"));
					tvector.put_range(t, t2);
				}
			} while (c.moveToNext());
			retvalue = true;
		}
		c.close();
		return retvalue;
	}

	public void log_action(String service, String action, int priority) {
		if (MESSAGE_LOGGING_ENABLED) {
			Intent i = new Intent("edu.virginia.dtc.intent.action.LOG_ACTION");
			i.putExtra("Service", service);
			i.putExtra("Status", action);
			i.putExtra("priority", priority);
			i.putExtra("time", getCurrentTimeSeconds());
			sendBroadcast(i);
		}
	}
	
	
	public String JSON_Parser_Reception (String response)
	{
		final String FUNC_TAG = "JSON_Parser_Reception";
		Debug.i(TAG, FUNC_TAG, "Response: "+response);
		try {
			JSONArray jArray = new JSONArray(response);
			return jArray.getJSONObject(0).getString("Status");
		}
		catch (JSONException e) {
			Debug.i(TAG, FUNC_TAG, "JSON Parser for reception error, "+e.toString());
			return "Not_parsed";
		}
	}
	
	
	public String getUrlPath(String uri) {
		//TODO: Remove those exceptions when the server's webservice is updated with those new values
		if (uri.equals(Biometrics.DEV_DETAILS_URI)) {
			uri = "devices";
		}
		else if (uri.equals(Biometrics.CR_PROFILE_URI) || uri.equals(Biometrics.CF_PROFILE_URI) || uri.equals(Biometrics.BASAL_PROFILE_URI) || uri.equals(Biometrics.SAFETY_PROFILE_URI)) {
			uri = uri.substring(0, uri.length() - 6);
		}
		
		String urlPath = "/diabetesassistant/androidservices/"+ uri +"_transfer.php";
		return urlPath;
	}
	
	
	class SmbgObserver extends ContentObserver
	{
		private int count;
		
		public SmbgObserver(Handler handler)
		{
			super(handler);
			
			final String FUNC_TAG = "SMBG Observer";
			Debug.i(TAG, FUNC_TAG, "Constructor");
			
			count = 0;
		}
		
		@Override
		public void onChange(boolean selfChange)
		{
			this.onChange(selfChange, null);
		}
		
		@Override
		public void onChange(boolean selfChange, Uri uri)
		{
			final String FUNC_TAG = "onChange";
			
			count++;
			Debug.i(TAG, FUNC_TAG, "SMBG Observer: "+count);
			
			Cursor c = getContentResolver().query(Biometrics.SMBG_URI, null, null, null, "time DESC limit 5");
			
			if (c!=null)
			{
				if (c.moveToLast())
				{
					hasNewSMBG = true;
					Debug.i(TAG, FUNC_TAG, "HasNewSMBG");
					//futureSmbg = systemScheduler.schedule(Send_smbg, 10, TimeUnit.SECONDS);
				}
			}
			c.close();
		}
	}
}