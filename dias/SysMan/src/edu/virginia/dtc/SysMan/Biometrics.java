package edu.virginia.dtc.SysMan;

import java.util.List;

import android.content.UriMatcher;
import android.net.Uri;

public class Biometrics {
    
	public static final String PROVIDER_NAME = "edu.virginia.dtc.provider.biometrics";
	public static final String ARCHIVED_PROVIDER_NAME = "edu.virginia.dtc.provider.archived";
	
	public static final int QUERY = 0;
	public static final int UPDATE = 1;
	public static final int INSERT = 2;
	public static final int DELETE = 3;
	
	// Table Numbers
    public static final int MIN_TABLE_NUM = 1;
    public static final int MAX_TABLE_NUM = 40;
    
    public static final int ALL_URI = 1;
    public static final int INFO_URI = 2;
    
    public static final int CGM_TABLE = 3;
    public static final int INSULIN_TABLE = 4;
    public static final int STATE_ESTIMATE_TABLE = 5;
    public static final int MEAL_TABLE = 6;
    public static final int LOG_TABLE = 7;
    public static final int SUBJECT_DATA_TABLE = 8;
    public static final int CF_PROFILE_TABLE = 9;
    public static final int CR_PROFILE_TABLE = 10;
    public static final int BASAL_PROFILE_TABLE = 11;
    public static final int HARDWARE_CONFIGURATION_TABLE = 14;
    public static final int USER_TABLE_1 = 15;
    public static final int USER_TABLE_2 = 16;
    public static final int INSULIN_CREDIT_TABLE = 17;
    public static final int SMBG_TABLE = 18;
    public static final int HMS_STATE_ESTIMATE_TABLE = 19;
    public static final int PASSWORD_TABLE = 20;
    public static final int CGM_DETAILS_TABLE = 21;
    public static final int PUMP_DETAILS_TABLE = 22;
    public static final int SAFETY_PROFILE_TABLE = 23;
    public static final int DEVICE_DETAILS_TABLE = 24;
    public static final int CONSTRAINTS_TABLE = 25;
    public static final int GPS_TABLE = 26;
    public static final int EXERCISE_SENSOR_TABLE = 27;
    public static final int ACCELEROMETER_TABLE = 28;
    public static final int USER_TABLE_3 = 29;
    public static final int USER_TABLE_4 = 30;
    public static final int SYSTEM_TABLE = 31;
    public static final int EVENT_TABLE = 32;
    public static final int PARAMETER_TABLE = 33;
    public static final int SERVER_TABLE = 34;
    public static final int TEMP_BASAL_TABLE = 35;
    public static final int STATE_TABLE = 36;
    public static final int TIME_TABLE = 37;
    public static final int EXERCISE_STATE_TABLE = 38;
    public static final int CONTROLLER_PARAMETERS_TABLE = 39;
    public static final int SERVICE_OUTPUTS_TABLE = 40;
    
    // Table Names
    public static final String ALL_URI_NAME = "all";
    public static final String INFO_URI_NAME = "info";
    
    public static final String CGM_TABLE_NAME = "cgm";
    public static final String INSULIN_TABLE_NAME = "insulin";
    public static final String STATE_ESTIMATE_TABLE_NAME = "stateestimate";
    public static final String MEAL_TABLE_NAME = "meal";
    public static final String LOG_TABLE_NAME = "log";
    public static final String SUBJECT_DATA_TABLE_NAME = "subjectdata";
    public static final String CF_PROFILE_TABLE_NAME = "cfprofile";
    public static final String CR_PROFILE_TABLE_NAME = "crprofile";
    public static final String BASAL_PROFILE_TABLE_NAME = "basalprofile";
    public static final String HARDWARE_CONFIGURATION_TABLE_NAME = "hardwareconfiguration";
    public static final String USER_TABLE_1_NAME = "user1";
    public static final String USER_TABLE_2_NAME = "user2";
    public static final String INSULIN_CREDIT_TABLE_NAME = "insulincredit";
    public static final String SMBG_TABLE_NAME = "smbg";
    public static final String HMS_STATE_ESTIMATE_TABLE_NAME = "hmsstateestimate";
    public static final String PASSWORD_TABLE_NAME = "password";
    public static final String CGM_DETAILS_TABLE_NAME = "cgmdetails";
    public static final String PUMP_DETAILS_TABLE_NAME = "pumpdetails";
    public static final String SAFETY_PROFILE_TABLE_NAME = "safetyprofile";
    public static final String DEVICE_DETAILS_TABLE_NAME = "devicedetails";
    public static final String CONSTRAINTS_TABLE_NAME = "constraints";
    public static final String GPS_TABLE_NAME = "gps";
    public static final String EXERCISE_SENSOR_TABLE_NAME = "exercise_sensor";
    public static final String ACCELEROMETER_TABLE_NAME = "acc";
    public static final String USER_TABLE_3_NAME = "user3";
    public static final String USER_TABLE_4_NAME = "user4";
    public static final String SYSTEM_TABLE_NAME = "system";
    public static final String EVENT_TABLE_NAME = "events";
    public static final String PARAMETER_TABLE_NAME = "params";
    public static final String SERVER_TABLE_NAME = "server_url";
    public static final String TEMP_BASAL_TABLE_NAME = "temporary_basal_rate";
    public static final String STATE_TABLE_NAME = "state";
    public static final String TIME_TABLE_NAME = "time";
    public static final String EXERCISE_STATE_TABLE_NAME = "exercise_state";
    public static final String CONTROLLER_PARAMETERS_TABLE_NAME = "controller_parameters";
    public static final String SERVICE_OUTPUTS_TABLE_NAME = "service_outputs";
	
	// Interface definitions for the biometricsContentProvider
	
	public static final Uri BIOMETRICS_URI = Uri.parse("content://"+ PROVIDER_NAME + "/biometrics");
    public static final Uri CGM_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+CGM_TABLE_NAME);
    public static final Uri MEAL_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+MEAL_TABLE_NAME);
    public static final Uri SMBG_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+SMBG_TABLE_NAME);
    public static final Uri LOG_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+LOG_TABLE_NAME);
    public static final Uri STATE_ESTIMATE_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+STATE_ESTIMATE_TABLE_NAME);
    public static final Uri CGM_DETAILS_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+CGM_DETAILS_TABLE_NAME);
    public static final Uri PUMP_DETAILS_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+PUMP_DETAILS_TABLE_NAME);
    public static final Uri DEV_DETAILS_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+DEVICE_DETAILS_TABLE_NAME);
    public static final Uri INSULIN_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+INSULIN_TABLE_NAME);
    public static final Uri INSULIN_CREDIT_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+INSULIN_CREDIT_TABLE_NAME);
    public static final Uri SYSTEM_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+SYSTEM_TABLE_NAME);
    public static final Uri CONSTRAINTS_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+CONSTRAINTS_TABLE_NAME);
    public static final Uri PASSWORD_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+PASSWORD_TABLE_NAME);
    public static final Uri SUBJECT_DATA_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+SUBJECT_DATA_TABLE_NAME);
    public static final Uri CF_PROFILE_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+CF_PROFILE_TABLE_NAME);
    public static final Uri EVENT_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+EVENT_TABLE_NAME);
    public static final Uri CR_PROFILE_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+CR_PROFILE_TABLE_NAME);
    public static final Uri BASAL_PROFILE_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+BASAL_PROFILE_TABLE_NAME);
    public static final Uri SAFETY_PROFILE_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+SAFETY_PROFILE_TABLE_NAME);
    public static final Uri USS_BRM_PROFILE_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+SAFETY_PROFILE_TABLE_NAME);
    public static final Uri USER_TABLE_1_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+USER_TABLE_1_NAME);
    public static final Uri USER_TABLE_2_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+USER_TABLE_2_NAME);
    public static final Uri USER_TABLE_3_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+USER_TABLE_3_NAME);
    public static final Uri USER_TABLE_4_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+USER_TABLE_4_NAME);
    public static final Uri PARAM_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+PARAMETER_TABLE_NAME);
    public static final Uri SERVER_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+SERVER_TABLE_NAME);
	public static final Uri HARDWARE_CONFIGURATION_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+HARDWARE_CONFIGURATION_TABLE_NAME);
	public static final Uri HMS_STATE_ESTIMATE_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+HMS_STATE_ESTIMATE_TABLE_NAME);
	public static final Uri GPS_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+GPS_TABLE_NAME);
	public static final Uri EXERCISE_SENSOR_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+EXERCISE_SENSOR_TABLE_NAME);
	public static final Uri TEMP_BASAL_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+TEMP_BASAL_TABLE_NAME);
	public static final Uri ACC_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+ACCELEROMETER_TABLE_NAME);
	public static final Uri STATE_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+STATE_TABLE_NAME);
	public static final Uri TIME_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+TIME_TABLE_NAME);
	public static final Uri EXERCISE_STATE_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+EXERCISE_STATE_TABLE_NAME);
	public static final Uri CONTROLLER_PARAMETERS_URI = Uri.parse("content://"+ PROVIDER_NAME + "/"+CONTROLLER_PARAMETERS_TABLE_NAME);
	public static final Uri SERVICE_OUTPUTS_URI = Uri.parse("content://" + PROVIDER_NAME + "/"+SERVICE_OUTPUTS_TABLE_NAME);
	
	
	// List of the (19) time-related tables URIs
	public static final Uri[] TIME_BASED_DATA_URIS = {
		CGM_URI,	INSULIN_URI,	MEAL_URI,	STATE_ESTIMATE_URI,	LOG_URI,	DEV_DETAILS_URI,	SMBG_URI,	EVENT_URI,
		SYSTEM_URI,	INSULIN_CREDIT_URI,	HMS_STATE_ESTIMATE_URI,	CONSTRAINTS_URI,	GPS_URI,	EXERCISE_SENSOR_URI,
		ACC_URI,	USER_TABLE_1_URI,	USER_TABLE_2_URI,	USER_TABLE_3_URI,	USER_TABLE_4_URI,	EXERCISE_STATE_URI, SERVICE_OUTPUTS_URI
	};
	
	
	// List of the (4) profile tables URIs
	public static final Uri[] PROFILE_URIS = {
		CR_PROFILE_URI, CF_PROFILE_URI, SAFETY_PROFILE_URI, BASAL_PROFILE_URI
	};
	
	
	// List of the (6) single-row tables URIs
	public static final Uri[] SINGLE_ROW_TABLES_URIS = {
		HARDWARE_CONFIGURATION_URI,	PASSWORD_URI,	CGM_DETAILS_URI,	PUMP_DETAILS_URI,	SERVER_URI,		STATE_URI, TEMP_BASAL_URI,
		SUBJECT_DATA_URI
	};
	
	
	// List of the tables to send to the remote monitoring server via the Network Service. Profile tables are not part of this list but are sent as well.
	public static final Uri[] TABLES_URIS_TO_SEND = {
		CGM_URI,	INSULIN_URI,	MEAL_URI,	SMBG_URI,	
		SYSTEM_URI,	LOG_URI,	DEV_DETAILS_URI,	EVENT_URI,	
		STATE_ESTIMATE_URI,	USER_TABLE_3_URI,	USER_TABLE_4_URI,	
		PARAM_URI,	SUBJECT_DATA_URI,
		
		TEMP_BASAL_URI
	};
	
	
	public static final UriMatcher uriMatcher;
	static {
	 	
	 	uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        
	 	uriMatcher.addURI(PROVIDER_NAME, ALL_URI_NAME, ALL_URI);
	 	uriMatcher.addURI(PROVIDER_NAME, INFO_URI_NAME, INFO_URI);
	 	
		uriMatcher.addURI(PROVIDER_NAME, CGM_TABLE_NAME, CGM_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, INSULIN_TABLE_NAME, INSULIN_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, INSULIN_CREDIT_TABLE_NAME, INSULIN_CREDIT_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, STATE_ESTIMATE_TABLE_NAME, STATE_ESTIMATE_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, HMS_STATE_ESTIMATE_TABLE_NAME, HMS_STATE_ESTIMATE_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, MEAL_TABLE_NAME, MEAL_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, LOG_TABLE_NAME, LOG_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, SUBJECT_DATA_TABLE_NAME, SUBJECT_DATA_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, CF_PROFILE_TABLE_NAME, CF_PROFILE_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, CR_PROFILE_TABLE_NAME, CR_PROFILE_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, BASAL_PROFILE_TABLE_NAME, BASAL_PROFILE_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, HARDWARE_CONFIGURATION_TABLE_NAME, HARDWARE_CONFIGURATION_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, USER_TABLE_1_NAME, USER_TABLE_1);
		uriMatcher.addURI(PROVIDER_NAME, USER_TABLE_2_NAME, USER_TABLE_2);
		uriMatcher.addURI(PROVIDER_NAME, SMBG_TABLE_NAME, SMBG_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, PASSWORD_TABLE_NAME, PASSWORD_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, CGM_DETAILS_TABLE_NAME, CGM_DETAILS_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, PUMP_DETAILS_TABLE_NAME, PUMP_DETAILS_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, DEVICE_DETAILS_TABLE_NAME, DEVICE_DETAILS_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, SAFETY_PROFILE_TABLE_NAME, SAFETY_PROFILE_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, CONSTRAINTS_TABLE_NAME, CONSTRAINTS_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, GPS_TABLE_NAME, GPS_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, EXERCISE_SENSOR_TABLE_NAME, EXERCISE_SENSOR_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, ACCELEROMETER_TABLE_NAME, ACCELEROMETER_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, USER_TABLE_3_NAME, USER_TABLE_3);
		uriMatcher.addURI(PROVIDER_NAME, USER_TABLE_4_NAME, USER_TABLE_4);
		uriMatcher.addURI(PROVIDER_NAME, SYSTEM_TABLE_NAME, SYSTEM_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, EVENT_TABLE_NAME, EVENT_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, PARAMETER_TABLE_NAME, PARAMETER_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, SERVER_TABLE_NAME, SERVER_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, TEMP_BASAL_TABLE_NAME, TEMP_BASAL_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, STATE_TABLE_NAME, STATE_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, TIME_TABLE_NAME, TIME_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, EXERCISE_STATE_TABLE_NAME, EXERCISE_STATE_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, CONTROLLER_PARAMETERS_TABLE_NAME, CONTROLLER_PARAMETERS_TABLE);
		uriMatcher.addURI(PROVIDER_NAME, SERVICE_OUTPUTS_TABLE_NAME, SERVICE_OUTPUTS_TABLE);
	}
	
	public static String getTableName(Uri uri) {
		String table_name = "";
		if (uriMatcher.match(uri) != -1) {
			List<String> segments = uri.getPathSegments();
			table_name = segments.get(segments.size()-1);
		}
		else {
			table_name = null;
		}
		return table_name;
	}
	
	public static Uri getArchivedBiometricsUri(Uri uri){
		String s = uri.toString();
		s.replace(PROVIDER_NAME, ARCHIVED_PROVIDER_NAME);
		return Uri.parse(s);
	}
}
