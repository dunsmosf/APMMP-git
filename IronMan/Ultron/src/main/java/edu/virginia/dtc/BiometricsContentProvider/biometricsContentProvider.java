package edu.virginia.dtc.BiometricsContentProvider;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.Permissions;
import edu.virginia.dtc.SysMan.Permissions.appPerm;
import edu.virginia.dtc.SysMan.Permissions.perm;

public class biometricsContentProvider extends ContentProvider {
	public static final String TAG = "biometricsContentProvider";
    
    private static Context context;
    public static List<appPerm> appPerms = new ArrayList<appPerm>();
    private static final UriMatcher uriMatcher = Biometrics.uriMatcher;
    
    //---for database use---
    private SQLiteDatabase biometricDB;
    private SQLiteDatabase archiveDB;
    
    private static final String DATABASE_NAME = "biometrics";
    private static final String DATABASE_NAME_ARCHIVE = "archived";
    private static final int DATABASE_VERSION = 1;
    
    private static final int QUERY_TIME_LIMIT = 24;
    
    private static final String DATABASE_TABLE_CGM_CREATE =
    "create table " + Biometrics.CGM_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "diasState int, cgm double not null, trend int, state int, time long not null,"
    + "recv_time long, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_INSULIN_CREATE =
    "create table " + Biometrics.INSULIN_TABLE_NAME
    + "(_id integer primary key autoincrement,"
    + "req_time long, req_total double, req_basal double, req_meal double, req_corr double,"
    + "deliv_time long, deliv_total double, deliv_basal double, deliv_meal double, deliv_corr double,"
    + "recv_time long,"
    + "running_total double, identifier long, status int, num_retries int, type int,"
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_STATE_ESTIMATE_CREATE =
    "create table " + Biometrics.STATE_ESTIMATE_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "enough_data boolean, CGM double, IOB double, IOBlast double, IOBlast2 double, Gpred double, " +
    "Gbrakes double, Gpred_light double, " +
    "Xi00 double, Xi01 double, Xi02 double, Xi03 double, Xi04 double, Xi05 double, Xi06 double, Xi07 double, " +
    "isMealBolus boolean, Gpred_bolus double, CHOpred double, " +
    "Abrakes double, CGM_corr double, IOB_controller_rate double, SSM_amount double, State int, " +
    "stoplight int, stoplight2 int, SSM_state double, SSM_state_timestamp long," +
    "exerciseFlagTimeStart long, exerciseFlagTimeStop long, hypoFlagTime long, calFlagTime long,  corrFlagTime long, " +
    "DIAS_state int, UTC_offset_seconds int, time long not null, Umax_IOB double, brakes_coeff double, asynchronous int, Gpred_1h double, "+
    "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_MEAL_CREATE =
    "create table " + Biometrics.MEAL_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "meal_size_grams double, time_announce long not null, SMBG double, meal_screen_bolus double, time long, type int, size int, treated int, active int, approved int,"
    + "extended_bolus int, extended_bolus_duration_seconds long, meal_screen_meal_bolus double, meal_screen_corr_bolus double, meal_screen_smbg_bolus double, extended_bolus_insulin_rem double, "
    + "meal_status int, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_LOG_CREATE =
    "create table " + Biometrics.LOG_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "status text not null, service text not null, time long not null, priority int,"
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_SUBJECT_DATA_CREATE =
    "create table " + Biometrics.SUBJECT_DATA_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "subjectid text not null, session text not null, weight int not null, height int not null, age int not null, TDI int not null, " +
    "isfemale int not null, AIT int not null, realtime int not null, " +
    "SafetyOnlyModeIsEnabled int not null, insulinSetupComplete int not null,"
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_CF_PROFILE_CREATE =
    "create table " + Biometrics.CF_PROFILE_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "time long not null, value double not null,"
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_CR_PROFILE_CREATE =
    "create table " + Biometrics.CR_PROFILE_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "time long not null, value double not null,"
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_BASAL_PROFILE_CREATE =
    "create table " + Biometrics.BASAL_PROFILE_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "time long not null, value double not null,"
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_HARDWARE_CONFIGURATION_CREATE =
    "create table " + Biometrics.HARDWARE_CONFIGURATION_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "running_pump text, running_cgm text, running_misc text, last_state int, ask_at_startup int,"
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_INSULIN_CREDIT_CREATE =
    "create table " + Biometrics.INSULIN_CREDIT_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "credit double not null, spent double not null, time long not null, net double not null, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_SMBG_CREATE =
    "create table " + Biometrics.SMBG_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "time long not null, smbg double not null, type int not null, didTreat boolean, carbs int,"
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_PASSWORD_CREATE =
    "create table IF NOT EXISTS " + Biometrics.PASSWORD_TABLE_NAME +
    " (password text, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_CGM_DETAILS_CREATE =
    "create table IF NOT EXISTS " + Biometrics.CGM_DETAILS_TABLE_NAME +
    " (details text, min_cgm double, max_cgm double, phone_calibration int, state int, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_PUMP_DETAILS_CREATE =
    "create table IF NOT EXISTS " + Biometrics.PUMP_DETAILS_TABLE_NAME +
    " (details text, low_reservoir_threshold_U double, reservoir_size_U double, infusion_rate_U_sec double, "
    + "unit_conversion double, unit_name text, min_bolus_U double, max_bolus_U double, min_quanta_U double, "
    + "queryable int, temp_basal int, temp_basal_time int, retries int, max_retries int, state int, service_state int,"
    + "set_temp_basal_time int, set_temp_basal_target int, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_SAFETY_PROFILE_CREATE =
    "create table " + Biometrics.SAFETY_PROFILE_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "time long not null, endtime long not null,"
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_DEVICE_DETAILS_CREATE =
    "create table IF NOT EXISTS " + Biometrics.DEVICE_DETAILS_TABLE_NAME +
    " (_id integer primary key autoincrement, " +
    " time long not null, battery text, plugged int, network_type text, network_strength text," +
    " ping_attempts_last_hour int, ping_success_last_hour int," +
    " battery_stats text,"
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_CONSTRAINTS_CREATE =
    "create table " + Biometrics.CONSTRAINTS_TABLE_NAME +
    " (_id integer primary key autoincrement, " +
    " time long, status int, " +
    " constraint1 double, constraint2 double, constraint3 double, constraint4 double, constraint5 double, " +
    " constraint6 double, constraint7 double, constraint8 double, constraint9 double, constraint10 double, " +
    " constraint11 double, constraint12 double, constraint13 double, constraint14 double, constraint15 double, " +
    " constraint16 double, constraint17 double, constraint18 double, constraint19 double, constraint20 double, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_GPS_CREATE =
    "create table " + Biometrics.GPS_TABLE_NAME +
    " (_id integer primary key autoincrement, " +
    " time long, gpsLat double, gpsLong double, gpsAlt double, gpsBearing float, gpsSpeed float, gpsSysTime long, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_EXERCISE_SENSOR_CREATE =
    "create table " + Biometrics.EXERCISE_SENSOR_TABLE_NAME +
    " (_id integer primary key autoincrement, " +
    " time long, json_data text, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_ACC_CREATE =
    "create table " + Biometrics.ACCELEROMETER_TABLE_NAME +
    " (_id integer primary key autoincrement, " +
    " time long, x float, y float, z float, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_SYSTEM_TABLE_CREATE =
    "create table " + Biometrics.SYSTEM_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "time long, sysTime long, safetyMode int, diasState int, enableIOTest int, battery int, "
    + "cgmValue double, cgmTrend int, cgmLastTime long, cgmState int, cgmStatus text, "
    + "pumpLastBolus double, pumpLastBolusTime long, pumpState int, pumpStatus text, "
    + "iobValue double, "
    + "hypoLight int, hyperLight int, "
    + "apcBolus double, apcStatus int, apcType int, apcString string, "
    + "exercising int, alarmNoCgm int, alarmHypo int, "
    + "send_attempts_server int, received_server boolean "
    + ");";
    
    private static final String DATABASE_EVENT_TABLE_CREATE =
    "create table " + Biometrics.EVENT_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "time long not null, code int, json text, settings int, popup_displayed int, "
    + "send_attempts_server int, received_server boolean "
    + ");";
    
    private static final String DATABASE_PARAMETER_TABLE_CREATE =
    "create table " + Biometrics.PARAMETER_TABLE_NAME +
    " (_id integer primary key autoincrement, "
    + "name text, value text, type text, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_SERVER_CREATE =
    "create table IF NOT EXISTS " + Biometrics.SERVER_TABLE_NAME +
    " (server_url text, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_TEMPORARY_BASAL_CREATE =
    "create table IF NOT EXISTS " + Biometrics.TEMP_BASAL_TABLE_NAME
    + "(start_time long, scheduled_end_time long, actual_end_time long, percent_of_profile_basal_rate int, status_code int, owner int,"
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_EXERCISE_STATE_CREATE =
    "create table " + Biometrics.EXERCISE_STATE_TABLE_NAME
    + "(_id integer primary key autoincrement,"
    + "time long not null, currentlyExercising int, json_data text, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_CONTROLLER_PARAMETERS_CREATE =
    "create table " + Biometrics.CONTROLLER_PARAMETERS_TABLE_NAME
    + "(_id integer primary key autoincrement,"
    + "time long not null, json_data text, "
    + "send_attempts_server int, received_server boolean);";
    
    private static final String DATABASE_TABLE_SERVICE_OUTPUTS =
    "create table " + Biometrics.SERVICE_OUTPUTS_TABLE_NAME
    + "(_id integer primary key autoincrement,"
    + "time long not null, cycle long, source int, destination int, "
    + "machine int, type int, message int, data text, "
    + "send_attempts_server int, received_server boolean);";
    
    private static class DatabaseHelper extends SQLiteOpenHelper
    {
        final String FUNC_TAG = "DatabaseHelper";
        
        DatabaseHelper(Context context) {
	 	 	super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        
        DatabaseHelper(Context context, String dbname) {
            super(context, dbname, null, DATABASE_VERSION);
        }
        
        @Override
        public void onCreate(SQLiteDatabase db)
        {
            final String FUNC_TAG = "onCreate";
		  	
            db.execSQL(DATABASE_TABLE_HARDWARE_CONFIGURATION_CREATE );			// Create hardware configuration table
            db.execSQL(DATABASE_TABLE_CGM_CREATE );								// Create cgm table
            db.execSQL(DATABASE_TABLE_INSULIN_CREATE );							// Create insulin table
            db.execSQL(DATABASE_TABLE_INSULIN_CREDIT_CREATE );					// Create insulin credit pool table
            db.execSQL(DATABASE_TABLE_STATE_ESTIMATE_CREATE);					// Create state estimate table
            db.execSQL(DATABASE_TABLE_MEAL_CREATE);								// Create meal table
            db.execSQL(DATABASE_TABLE_SMBG_CREATE);								// Create smbg table
            db.execSQL(DATABASE_TABLE_LOG_CREATE);								// Create log table
            db.execSQL(DATABASE_TABLE_SUBJECT_DATA_CREATE);						// Create subject data table
            db.execSQL(DATABASE_TABLE_CF_PROFILE_CREATE);						// Create cf profile data table
            db.execSQL(DATABASE_TABLE_CR_PROFILE_CREATE);						// Create cr profile data table
            db.execSQL(DATABASE_TABLE_BASAL_PROFILE_CREATE);					// Create basal profile data table
            db.execSQL(DATABASE_TABLE_SAFETY_PROFILE_CREATE);					// Create safety profile data table
            db.execSQL(DATABASE_TABLE_PASSWORD_CREATE);							// Create password table
            db.execSQL(DATABASE_TABLE_CGM_DETAILS_CREATE);						// Create cgmdetails table
            db.execSQL(DATABASE_TABLE_PUMP_DETAILS_CREATE);						// Create pumpdetails table
            db.execSQL(DATABASE_TABLE_DEVICE_DETAILS_CREATE);					// Create devicedetails table
            db.execSQL(DATABASE_TABLE_CONSTRAINTS_CREATE);						// Create the constraint table for the ConstraintService
            db.execSQL(DATABASE_TABLE_GPS_CREATE);								// Create the gps data table
            db.execSQL(DATABASE_TABLE_EXERCISE_SENSOR_CREATE);					// Create the exercise sensor table
            db.execSQL(DATABASE_TABLE_ACC_CREATE);								// Create the accelerometer data table
            db.execSQL(DATABASE_SYSTEM_TABLE_CREATE);							// Create system table
            db.execSQL(DATABASE_EVENT_TABLE_CREATE);							// Create event table
            db.execSQL(DATABASE_PARAMETER_TABLE_CREATE);						// Create parameter table
            db.execSQL(DATABASE_TABLE_SERVER_CREATE);							// Create server_url table
            db.execSQL(DATABASE_TABLE_TEMPORARY_BASAL_CREATE);					// Create temporary_basal_rate table
            db.execSQL(DATABASE_TABLE_EXERCISE_STATE_CREATE);					// Create the exercise state table
            db.execSQL(DATABASE_TABLE_CONTROLLER_PARAMETERS_CREATE);			// Create the controller parameters table
            db.execSQL(DATABASE_TABLE_SERVICE_OUTPUTS);							
        }
        
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
			Debug.i(TAG, FUNC_TAG, "Upgrading database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS "+Biometrics.HARDWARE_CONFIGURATION_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS "+Biometrics.CGM_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS "+Biometrics.INSULIN_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS "+Biometrics.INSULIN_CREDIT_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS "+Biometrics.STATE_ESTIMATE_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS "+Biometrics.MEAL_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS "+Biometrics.SMBG_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS "+Biometrics.LOG_TABLE_NAME);
			db.execSQL("DROP TABLE IF EXISTS subjectdata");
			db.execSQL("DROP TABLE IF EXISTS cfprofile");
			db.execSQL("DROP TABLE IF EXISTS crprofile");
			db.execSQL("DROP TABLE IF EXISTS basalprofile");
			db.execSQL("DROP TABLE IF EXISTS password");
			db.execSQL("DROP TABLE IF EXISTS devicedetails");
			db.execSQL("DROP TABLE IF EXISTS constraints");
			db.execSQL("DROP TABLE IF EXISTS gps");
			db.execSQL("DROP TABLE IF EXISTS exercise_sensor");
			db.execSQL("DROP TABLE IF EXISTS acc");
			db.execSQL("DROP TABLE IF EXISTS system");
			db.execSQL("DROP TABLE IF EXISTS events");
			db.execSQL("DROP TABLE IF EXISTS params");
			db.execSQL("DROP TABLE IF EXISTS server_url");
			db.execSQL("DROP TABLE IF EXISTS temporary_basal_rate");
			db.execSQL("DROP TABLE IF EXISTS exercise_state");
			db.execSQL("DROP TABLE IF EXISTS controller_parameters");
			db.execSQL("DROP TABLE IF EXISTS "+Biometrics.SERVICE_OUTPUTS_TABLE_NAME);
			onCreate(db);
        }
    }
    
    
    public int saveDatabases() 
    {
        return saveDatabase(biometricDB, DATABASE_NAME) + saveDatabase(archiveDB, DATABASE_NAME_ARCHIVE);
    }
    
    
    public int saveDatabase(SQLiteDatabase db, String dbname){
	 	final String FUNC_TAG = "saveDatabase";
	 	
	 	String folder = Environment.getExternalStorageDirectory().getPath()+"/";
	 	
	 	// File name
	 	String EXPORT_FILE_NAME;
	 	String SUBJECT_ID_STRING ="";
	 	
	 	Cursor c = biometricDB.query("subjectdata", null, null, null, null, null, null);
	 	if (c.moveToFirst()){
	 		SUBJECT_ID_STRING = c.getString(c.getColumnIndex("subjectid")).replace(' ', '_') + "-" + c.getString(c.getColumnIndex("session")).replace(' ', '_');
	 	}
	 	c.close();
	 	
	 	if (db == archiveDB) {
	 		folder += SUBJECT_ID_STRING+"_weekly_archive/";
	 		
	 		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
	    	String today = format.format(new Date());
	 		
	 		if (!new File(folder).exists()) {
	 			new File(folder).mkdir();
	 		}
	 		EXPORT_FILE_NAME = today + ".db";
	 	}
	 	else {
	 		EXPORT_FILE_NAME = SUBJECT_ID_STRING+"_working.db";
	 	}
	 	
	 	try {
	 		File f = new File(folder + EXPORT_FILE_NAME);
	 		Debug.i(TAG, FUNC_TAG, "Trying file: " + folder + EXPORT_FILE_NAME);
	 		if (!f.createNewFile()) {
	 			for (int i = 1;!f.createNewFile(); i++) {
	 				String filename = folder + EXPORT_FILE_NAME;
	 				filename = filename.substring(0, filename.length()-3);
	 				filename += "("+i+").db";
	 				f = new File(filename);
	 			}
	 		}
	 		FileInputStream s = new FileInputStream(new File(this.getContext().getFilesDir().toString().split("/files")[0]+"/databases/"+dbname));
	 		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
	 		byte[] buffer = new byte[1024];
	 		int bytesRead = 0;
	 		while( (bytesRead = s.read(buffer)) > 0){
	 			out.write(buffer, 0, bytesRead);
	 		}
	 		out.close();
	 		s.close();
            MediaScannerConnection.scanFile(getContext(), new String[] { f.toString() }, null,
                                            new MediaScannerConnection.OnScanCompletedListener() {
                public void onScanCompleted(String path, Uri uri) {
                    //Log.i(TAG, "Scanned " + path + ":");
                    //Log.i(TAG, "-> uri=" + uri);
                }
            }
                                            );
	 		Debug.i(TAG, FUNC_TAG,  "Saved database to " + f.getAbsolutePath());
	 	}
	 	catch (IOException ioe) {
	 		ioe.printStackTrace();
	 		Debug.i(TAG, FUNC_TAG,  "IOException in saving database");
	 		return 0;
	 	}
	 	catch (CursorIndexOutOfBoundsException ce) {
	 		ce.printStackTrace();
	 		Debug.i(TAG, FUNC_TAG,  "Cursor error in saving database");
	 	}
	 	
	 	c.close();
	 	return 1;
    }
    
    @Override
    public boolean onCreate() {
        context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        biometricDB = dbHelper.getWritableDatabase();
        
        DatabaseHelper archiveDBHelper = new DatabaseHelper(context, DATABASE_NAME_ARCHIVE);
        archiveDB = archiveDBHelper.getWritableDatabase();
        
        Permissions.initPermissions(appPerms);
        
        return (biometricDB == null)? false:true;
    }

	@Override
    public String getType(Uri uri)
    {
	    return null;
	}
	
	/*********************************************************
	 * Query Command
	 *********************************************************/
    
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		final String FUNC_TAG = "query";
        
		SQLiteQueryBuilder sqlBuilder = new SQLiteQueryBuilder();
		Cursor c;
		
		if(!checkPermissions(Biometrics.QUERY, uri))		//Check if it has query permissions
			return null;
        
		SQLiteDatabase db = null;
		String dbname = "unknown";
		if (uri.getAuthority().equals(Biometrics.PROVIDER_NAME)) {
			db = biometricDB;
			dbname = DATABASE_NAME;
		}
		if (uri.getAuthority().equals(Biometrics.ARCHIVED_PROVIDER_NAME)) {
			db = archiveDB;
			dbname = DATABASE_NAME_ARCHIVE;
		}
		String table = uri.getPath().substring(1);
		sqlBuilder.setTables(table);
		
		if (Arrays.asList(Biometrics.TIME_BASED_DATA_URIS).contains(uri)) {
			selection = limitQueryTime(uri, selection);
		}
		
		c = sqlBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
		
		Debug.i(TAG, FUNC_TAG, "Querying '"+dbname+"' for table '"+table+"', proj: "+Arrays.toString(projection)+", select: "+selection+", selectArgs: "+Arrays.toString(selectionArgs)+", order: "+sortOrder);
		
		c.setNotificationUri(getContext().getContentResolver(), uri);
        
		return c;
    }

    /*********************************************************
     * Update Command
     *********************************************************/
    
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
	 	final String FUNC_TAG = "update";
	 	
	 	int count = 0;
	 	int count2 = 0;
        
	 	Debug.i(TAG, FUNC_TAG, "Updating for "+uri.toString());
	 	
		if(!checkPermissions(Biometrics.UPDATE, uri))		//Check if it has update permissions
 			return count;
		
		String table = Biometrics.getTableName(uri);
	 	
		count = biometricDB.update(table, values, selection, selectionArgs);
		count2 = archiveDB.update(table, values, selection, selectionArgs);
		
		Debug.i(TAG, FUNC_TAG, "Update table "+table.toString()+" with values "+values+". Result: "+count);
		
		if (Arrays.asList(Biometrics.SINGLE_ROW_TABLES_URIS).contains(uri)) {
			if (count <=0 ){
				long result = biometricDB.insert(table, "", values);
				long result2 = archiveDB.insert(table, "", values);
				if ((result > -1) && (result2 > -1)) {
					count+=1;
				}
			}
		}
		
        if(count > 0)
        {
	 		//Make sure a change actually occured before notifying
	 		getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }
    
    /*********************************************************
     * Insert Command
     *********************************************************/
    
    @Override
    public Uri insert(Uri uri, ContentValues values) {
	 	final String FUNC_TAG = "insert";
		
	 	Debug.i(TAG, FUNC_TAG,  "insert into "+uri);
	 	// Add a new row to the appropriate table
	 	long rowID=-1;
	 	
	 	if(!checkPermissions(Biometrics.INSERT, uri))		//Check if it has insert permissions
	 		return null;
	 	
	 	String table = Biometrics.getTableName(uri);
	 	
	 	if (Arrays.asList(Biometrics.SINGLE_ROW_TABLES_URIS).contains(uri)) {
			biometricDB.delete(table, null, null);
			archiveDB.delete(table, null, null);
		}
	 	
	 	values.put("send_attempts_server", 0);
 		values.put("received_server", 0);
 		Debug.i(TAG, FUNC_TAG,  "inserting into "+table+", values="+values+"...");
	 	rowID = biometricDB.insert(table, "", values);
        if (rowID > -1) {
            values.put("_id", rowID);
            archiveDB.insert(table, "", values);
        }
	 	
		if (rowID>0)
		{
            Uri _uri = ContentUris.withAppendedId(Biometrics.BIOMETRICS_URI, rowID);
            getContext().getContentResolver().notifyChange(uri, null);	//Make sure we notify on the URI itself not the appended ID URI
            return _uri;
		}
		throw new SQLException("Failed to insert row into " + uri);
	}
	
	/*********************************************************
	 * Delete Command
	 *********************************************************/
	
	@Override
	public int delete(Uri URI, String selection, String[] selectionArgs) {
		final String FUNC_TAG = "delete";
        
		String table = URI.toString().split("/")[URI.toString().split("/").length-1];
  	  	int count=0;
  	 	
  	 	if (table.equals("save"))				//For now we allow anything to save the DB (seems okay)
  	 	{
  	 		//return saveDatabase(selection, biometricDB, DATABASE_NAME); // use the "where" clause as an optionally-provided nickname for the DB file... kinda dumb but it's easy
  	 		return saveDatabases();
  	 	}
  	 	else if (table.equals("archive_weekly"))
  	 	{
  	 		saveDatabase(archiveDB, DATABASE_NAME_ARCHIVE);
  	 		delete_time_based(archiveDB);
  	 	}
  	 	else if (table.equals("all"))
  	 	{
  	 		if(!checkPermissions(Biometrics.DELETE, URI))		//Check if it has delete permissions for "all" URI
 				return count;
  	 		
  	 		saveDatabases();
  	 		delete_all(biometricDB);
  	 		delete_all(archiveDB);
  	 	}
  	 	else if (table.equals("info"))
  	 	{
  	 		if(!checkPermissions(Biometrics.DELETE, URI))		//Check if it has delete permissions for "info" URI
 				return count;
  	 		
  	 		delete_info(biometricDB);
  	 	}
  	 	else
  	 	{
  	 		if(!checkPermissions(Biometrics.DELETE, URI))		//Check if it has delete permissions for a specific URI (most don't)
 				return count;
  	 		
  	 		count += biometricDB.delete(table, selection, selectionArgs);
  	 	}
  	 	
  	 	getContext().getContentResolver().notifyChange(URI, null);
  	 	
  	 	return count;
	}
	
	private void delete_all(SQLiteDatabase db){
		
		final String FUNC_TAG = "DeleteAll";
		Debug.i(TAG, FUNC_TAG,  "Delete all tables from "+db.toString()+".");
		
		db.execSQL("DROP TABLE IF EXISTS user1");
		db.execSQL("DROP TABLE IF EXISTS user2");
		db.execSQL("DROP TABLE IF EXISTS hardwareconfiguration");
		db.execSQL("DROP TABLE IF EXISTS cgm");
		db.execSQL("DROP TABLE IF EXISTS insulin");
		db.execSQL("DROP TABLE IF EXISTS insulincredit");
		db.execSQL("DROP TABLE IF EXISTS stateestimate");
		db.execSQL("DROP TABLE IF EXISTS hmsstateestimate");
		db.execSQL("DROP TABLE IF EXISTS meal");
		db.execSQL("DROP TABLE IF EXISTS smbg");
		db.execSQL("DROP TABLE IF EXISTS log");
		db.execSQL("DROP TABLE IF EXISTS subjectdata");
		db.execSQL("DROP TABLE IF EXISTS cfprofile");
		db.execSQL("DROP TABLE IF EXISTS crprofile");
		db.execSQL("DROP TABLE IF EXISTS basalprofile");
		db.execSQL("DROP TABLE IF EXISTS safetyprofile");
		db.execSQL("DROP TABLE IF EXISTS password");
		db.execSQL("DROP TABLE IF EXISTS cgmdetails");
		db.execSQL("DROP TABLE IF EXISTS pumpdetails");
		db.execSQL("DROP TABLE IF EXISTS devicedetails");
		db.execSQL("DROP TABLE IF EXISTS constraints");
		db.execSQL("DROP TABLE IF EXISTS gps");
		db.execSQL("DROP TABLE IF EXISTS exercise_sensor");
		db.execSQL("DROP TABLE IF EXISTS acc");
		db.execSQL("DROP TABLE IF EXISTS user3");
		db.execSQL("DROP TABLE IF EXISTS user4");
		db.execSQL("DROP TABLE IF EXISTS system");
		db.execSQL("DROP TABLE IF EXISTS events");
		db.execSQL("DROP TABLE IF EXISTS params");
		db.execSQL("DROP TABLE IF EXISTS server_url");
		db.execSQL("DROP TABLE IF EXISTS temporary_basal_rate");
		db.execSQL("DROP TABLE IF EXISTS state");
		db.execSQL("DROP TABLE IF EXISTS time");
		db.execSQL("DROP TABLE IF EXISTS exercise_state");
		db.execSQL("DROP TABLE IF EXISTS controller_parameters");

		db.execSQL(DATABASE_TABLE_HARDWARE_CONFIGURATION_CREATE );				// Create hardwareconfiguration table
		db.execSQL(DATABASE_TABLE_CGM_CREATE );								// Create cgm table
		db.execSQL(DATABASE_TABLE_INSULIN_CREATE );							// Create insulin table
		db.execSQL(DATABASE_TABLE_INSULIN_CREDIT_CREATE );						// Create insulin credit pool table
		db.execSQL(DATABASE_TABLE_STATE_ESTIMATE_CREATE);						// Create state estimate table
		db.execSQL(DATABASE_TABLE_MEAL_CREATE);								// Create meal table
		db.execSQL(DATABASE_TABLE_SMBG_CREATE);								// Create smbg table
		db.execSQL(DATABASE_TABLE_LOG_CREATE);									// Create log table
		db.execSQL(DATABASE_TABLE_SUBJECT_DATA_CREATE);						// Create subject data table
		db.execSQL(DATABASE_TABLE_CF_PROFILE_CREATE);							// Create cf profile data table
		db.execSQL(DATABASE_TABLE_CR_PROFILE_CREATE);							// Create cr profile data table
		db.execSQL(DATABASE_TABLE_BASAL_PROFILE_CREATE);						// Create basal profile data table
		db.execSQL(DATABASE_TABLE_SAFETY_PROFILE_CREATE);						// Create basal profile data table
		db.execSQL(DATABASE_TABLE_PASSWORD_CREATE);							// Create password table
		db.execSQL(DATABASE_TABLE_CGM_DETAILS_CREATE);							// Create cgmdetails table
		db.execSQL(DATABASE_TABLE_PUMP_DETAILS_CREATE);						// Create pumpdetails table
		db.execSQL(DATABASE_TABLE_DEVICE_DETAILS_CREATE);
		db.execSQL(DATABASE_TABLE_CONSTRAINTS_CREATE);
		db.execSQL(DATABASE_TABLE_GPS_CREATE);
		db.execSQL(DATABASE_TABLE_EXERCISE_SENSOR_CREATE);
		db.execSQL(DATABASE_TABLE_ACC_CREATE);
		db.execSQL(DATABASE_SYSTEM_TABLE_CREATE);
		db.execSQL(DATABASE_EVENT_TABLE_CREATE);
		db.execSQL(DATABASE_PARAMETER_TABLE_CREATE);
		db.execSQL(DATABASE_TABLE_SERVER_CREATE);
		db.execSQL(DATABASE_TABLE_TEMPORARY_BASAL_CREATE);
		db.execSQL(DATABASE_TABLE_EXERCISE_STATE_CREATE);
		db.execSQL(DATABASE_TABLE_CONTROLLER_PARAMETERS_CREATE);
	}
	
    private void delete_time_based(SQLiteDatabase db){
		
		final String FUNC_TAG = "DeleteTimeBased";
		Debug.i(TAG, FUNC_TAG,  "Delete time based tables from "+db.toString()+".");
		
		db.execSQL("DROP TABLE IF EXISTS user1");
		db.execSQL("DROP TABLE IF EXISTS user2");
		db.execSQL("DROP TABLE IF EXISTS cgm");
		db.execSQL("DROP TABLE IF EXISTS insulin");
		db.execSQL("DROP TABLE IF EXISTS insulincredit");
		db.execSQL("DROP TABLE IF EXISTS stateestimate");
		db.execSQL("DROP TABLE IF EXISTS hmsstateestimate");
		db.execSQL("DROP TABLE IF EXISTS meal");
		db.execSQL("DROP TABLE IF EXISTS smbg");
		db.execSQL("DROP TABLE IF EXISTS log");
		db.execSQL("DROP TABLE IF EXISTS devicedetails");
		db.execSQL("DROP TABLE IF EXISTS constraints");
		db.execSQL("DROP TABLE IF EXISTS gps");
		db.execSQL("DROP TABLE IF EXISTS exercise_sensor");
		db.execSQL("DROP TABLE IF EXISTS acc");
		db.execSQL("DROP TABLE IF EXISTS user3");
		db.execSQL("DROP TABLE IF EXISTS user4");
		db.execSQL("DROP TABLE IF EXISTS system");
		db.execSQL("DROP TABLE IF EXISTS events");
		db.execSQL("DROP TABLE IF EXISTS time");
		db.execSQL("DROP TABLE IF EXISTS exercise_state");
		db.execSQL("DROP TABLE IF EXISTS controller_parameters");

		db.execSQL(DATABASE_TABLE_CGM_CREATE );								// Create cgm table
		db.execSQL(DATABASE_TABLE_INSULIN_CREATE );							// Create insulin table
		db.execSQL(DATABASE_TABLE_INSULIN_CREDIT_CREATE );						// Create insulin credit pool table
		db.execSQL(DATABASE_TABLE_STATE_ESTIMATE_CREATE);						// Create state estimate table
		db.execSQL(DATABASE_TABLE_MEAL_CREATE);								// Create meal table
		db.execSQL(DATABASE_TABLE_SMBG_CREATE);								// Create smbg table
		db.execSQL(DATABASE_TABLE_LOG_CREATE);									// Create log table
		db.execSQL(DATABASE_TABLE_DEVICE_DETAILS_CREATE);
		db.execSQL(DATABASE_TABLE_CONSTRAINTS_CREATE);
		db.execSQL(DATABASE_TABLE_GPS_CREATE);
		db.execSQL(DATABASE_TABLE_EXERCISE_SENSOR_CREATE);
		db.execSQL(DATABASE_TABLE_ACC_CREATE);
		db.execSQL(DATABASE_SYSTEM_TABLE_CREATE);
		db.execSQL(DATABASE_EVENT_TABLE_CREATE);
		db.execSQL(DATABASE_TABLE_EXERCISE_STATE_CREATE);
		db.execSQL(DATABASE_TABLE_CONTROLLER_PARAMETERS_CREATE);
	}
	
	private void delete_info(SQLiteDatabase db) {
		
		final String FUNC_TAG = "DeleteInfo";
        
		Debug.i(TAG, FUNC_TAG,  "Delete subject info tables from "+db.toString()+".");
		
		db.execSQL("DROP TABLE IF EXISTS subjectdata");
		db.execSQL("DROP TABLE IF EXISTS cfprofile");
		db.execSQL("DROP TABLE IF EXISTS crprofile");
		db.execSQL("DROP TABLE IF EXISTS basalprofile");
		db.execSQL("DROP TABLE IF EXISTS safetyprofile");
		
		db.execSQL(DATABASE_TABLE_SUBJECT_DATA_CREATE);								// Create subject data table
		db.execSQL(DATABASE_TABLE_CF_PROFILE_CREATE);								// Create cf profile data table
		db.execSQL(DATABASE_TABLE_CR_PROFILE_CREATE);								// Create cr profile data table
		db.execSQL(DATABASE_TABLE_BASAL_PROFILE_CREATE);							// Create basal profile data table
		db.execSQL(DATABASE_TABLE_SAFETY_PROFILE_CREATE);							// Create basal profile data table
	}
	
	
	/***************************************************************************************************************
     * Permission Functions
     ***************************************************************************************************************/
	
	private boolean checkPermissions(int callType, Uri uri)
	{
		final String FUNC_TAG = "checkPermissions";
		
		int pid = Binder.getCallingPid();						//Get the calling process ID
		String callingProcess = "";
		
		ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
		
		for(RunningAppProcessInfo pi:am.getRunningAppProcesses())
		{
			if(pi.pid == pid)									//Look for a match in the running processes and gather the name
				callingProcess = pi.processName;
		}
		if (callingProcess.endsWith(":remote")) {
			callingProcess = callingProcess.substring(0, callingProcess.length() - 7);
		}
		
		Debug.i(TAG, FUNC_TAG,  "Calling Process: "+callingProcess+" URI: "+uri.toString());
		
		if(callingProcess!="")									//Check that the process isn't blank
		{
			for(appPerm a:appPerms)
			{
				if(a.app.equalsIgnoreCase(callingProcess))		//Find the application permission that matches the calling process
				{
					Debug.i(TAG, FUNC_TAG,  "Application: "+a.app+" Table: "+uriMatcher.match(uri));
					
					for(perm p:a.tablePerm)						//Find the table permission that matches the URI trying to be accessed
					{
						if(p.table == uriMatcher.match(uri))
						{
							Debug.i(TAG, FUNC_TAG,  "P.Table: "+p.table+" URI Matcher: "+uriMatcher.match(uri));
							
							switch(callType)						//Identify and return whether it has permission based on the call type (QUID)
							{
                                case Biometrics.QUERY:
                                    Debug.i(TAG, FUNC_TAG,  callingProcess+">  Table:  "+uri.toString()+">  Query: "+p.canQuery());
                                    if(!p.canQuery()) {
                                        Bundle b = new Bundle();
                                        b.putString("description", "Query error: "+callingProcess+">  Table:  "+uri.toString()+">  Query: "+p.canQuery()+" in "+FUNC_TAG);
                                        addEvent(Event.EVENT_SYSTEM_DATABASE_ACCESS_ERROR, Event.makeJsonString(b), Event.SET_LOG);
                                        Debug.e(TAG, FUNC_TAG, "DBPermFail"+ callingProcess+">  Table:  "+uri.toString()+">  Query: "+p.canQuery());
                                    }
                                    return p.canQuery();
                                    
                                case Biometrics.UPDATE:
                                    Debug.i(TAG, FUNC_TAG,  callingProcess+">  Table:  "+uri.toString()+">  Update: "+p.canUpdate());
                                    if(!p.canUpdate()) {
                                        Bundle b = new Bundle();
                                        b.putString("description", "Update error: "+callingProcess+">  Table:  "+uri.toString()+">  Update: "+p.canUpdate()+" in "+FUNC_TAG);
                                        addEvent(Event.EVENT_SYSTEM_DATABASE_ACCESS_ERROR, Event.makeJsonString(b), Event.SET_LOG);
                                        Debug.e(TAG, FUNC_TAG, "DBPermFail"+ callingProcess+">  Table:  "+uri.toString()+">  Update: "+p.canUpdate());
                                    }
                                    return p.canUpdate();
                                    
                                case Biometrics.INSERT:
                                    Debug.i(TAG, FUNC_TAG,  callingProcess+">  Table:  "+uri.toString()+">  Insert: "+p.canInsert());
                                    if(!p.canInsert()) {
                                        Bundle b = new Bundle();
                                        b.putString("description", "Insert error: "+callingProcess+">  Table:  "+uri.toString()+">  Insert: "+p.canInsert()+" in "+FUNC_TAG);
                                        addEvent(Event.EVENT_SYSTEM_DATABASE_ACCESS_ERROR, Event.makeJsonString(b), Event.SET_LOG);
                                        Debug.e(TAG, FUNC_TAG, "DBPermFail"+ callingProcess+">  Table:  "+uri.toString()+">  Insert: "+p.canInsert());
                                    }
                                    return p.canInsert();
                                    
                                case Biometrics.DELETE:
                                    Debug.i(TAG, FUNC_TAG,  callingProcess+">  Table:  "+uri.toString()+">  Delete: "+p.canDelete());
                                    if(!p.canDelete()) {
                                        Bundle b = new Bundle();
                                        b.putString("description", "Delete error: "+callingProcess+">  Table:  "+uri.toString()+">  Delete: "+p.canDelete()+" in "+FUNC_TAG);
                                        addEvent(Event.EVENT_SYSTEM_DATABASE_ACCESS_ERROR, Event.makeJsonString(b), Event.SET_LOG);
                                        Debug.e(TAG, FUNC_TAG, "DBPermFail"+ callingProcess+">  Table:  "+uri.toString()+">  Delete: "+p.canDelete());
                                    }
                                    return p.canDelete();
                                    
                                default:						//The default is always to reject permission
                                    Bundle b = new Bundle();
                                    b.putString("description", "Unknown error: "+callingProcess+">  Table:  "+uri.toString()+">  Unknown call: "+callType+" in "+FUNC_TAG);
                                    addEvent(Event.EVENT_SYSTEM_DATABASE_ACCESS_ERROR, Event.makeJsonString(b), Event.SET_LOG);
                                    Debug.i(TAG, FUNC_TAG, "DBPermFail"+ callingProcess+">  Table:  "+uri.toString()+">  Unknown permission!");
                                    return false;
							}
						}
					}
				}
			}
		}
		else
		{
			Debug.i(TAG, FUNC_TAG, "DBPermFail: Calling process is blank!");
		}
		
		Debug.i(TAG, FUNC_TAG, "DBPermFail: Unknown calling process ("+callingProcess+"> "+uri.toString()+"> "+callType+")!");
		return false;		//Again, default is always to reject permission
	}
	
	/***************************************************************************************
     * Table for Application Permissions to Content Provider
     ***************************************************************************************/
	
	private String limitQueryTime(Uri uri, String selection) {
		String FUNC_TAG = "limitQueryTime";
		
		if (uri.getAuthority().equals(Biometrics.PROVIDER_NAME)) {
			
			String timeIndex = "time";
			Debug.i(TAG, FUNC_TAG, "QueryPath: "+uri.getPath());
			if (uri.getPath().equals("/insulin")) {
				timeIndex = "req_time";
			}
			
			long minTime = System.currentTimeMillis()/1000 - QUERY_TIME_LIMIT*3600;
			
			if (selection == null || selection =="") {
				selection = timeIndex + " > "+ minTime;
			}
			else {
				if (!selection.contains(timeIndex)) {
					selection += " and " + timeIndex +" > "+ minTime;
				}
			}
		}
		return selection;
	}
	
	
	/***************************************************************************************
	 * Function to add Event when no context is available (to use in checkPermissions only)
	 ***************************************************************************************/
	
	private void addEvent(int code, String json, int settings)
	{
		ContentValues cv = new ContentValues();
		cv.put("time", System.currentTimeMillis()/1000);
		cv.put("code", code);
		cv.put("json", json);
		cv.put("settings", settings);
		cv.put("popup_displayed", 0);
		
		insert(Biometrics.EVENT_URI, cv);
	}
}
