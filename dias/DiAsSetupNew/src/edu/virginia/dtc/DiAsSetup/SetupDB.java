package edu.virginia.dtc.DiAsSetup;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.DiAsSubjectData;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.State;
import edu.virginia.dtc.Tvector.Tvector;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;

public class SetupDB extends SQLiteOpenHelper 
{
	private static final String TAG = "DiAsSetup1";
	
	private static final int DATABASE_VERSION = 2;
	private static final String DATABASE_NAME = "SETUP";
	
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
	    
    private static final String DATABASE_TABLE_SAFETY_PROFILE_CREATE =
	    "create table " + Biometrics.SAFETY_PROFILE_TABLE_NAME +
	    " (_id integer primary key autoincrement, "
	    + "time long not null, endtime long not null,"
	    + "send_attempts_server int, received_server boolean);";
    
    
	SetupDB(Context context){
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		final String FUNC_TAG = "onCreate";
		
		Debug.i(TAG, FUNC_TAG, "");
		
		db.execSQL(DATABASE_TABLE_SUBJECT_DATA_CREATE);
        db.execSQL(DATABASE_TABLE_CF_PROFILE_CREATE);
        db.execSQL(DATABASE_TABLE_CR_PROFILE_CREATE);
        db.execSQL(DATABASE_TABLE_BASAL_PROFILE_CREATE);
        db.execSQL(DATABASE_TABLE_SAFETY_PROFILE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) 
	{
	}
	
	public DiAsSubjectData readDb()
	{
		final String FUNC_TAG = "readDb";
		
		Debug.i(TAG, FUNC_TAG, "Reading setup DB...");
		
		DiAsSubjectData subject_data = new DiAsSubjectData();

		SQLiteDatabase db = this.getReadableDatabase();
		
		String query = "SELECT * FROM "+ Biometrics.SUBJECT_DATA_TABLE_NAME;
		Cursor c = db.rawQuery(query, null);
		Debug.i(TAG, FUNC_TAG,"Retrieved SUBJECT_DATA_URI with " + c.getCount() + " items");
		if (c.moveToLast()) 
		{
			// A database exists.  Initialize subject_data.
			subject_data.subjectName = new String(c.getString(c.getColumnIndex("subjectid")));
			subject_data.subjectSession = new String(c.getString(c.getColumnIndex("session")));
			subject_data.subjectWeight = (c.getInt(c.getColumnIndex("weight")));
			subject_data.subjectHeight = (c.getInt(c.getColumnIndex("height")));
			subject_data.subjectAge = (c.getInt(c.getColumnIndex("age")));
			subject_data.subjectTDI = (c.getInt(c.getColumnIndex("TDI")));
			
			// subject_data.subjectAIT = (c.getInt(c.getColumnIndex("AIT")));
			subject_data.subjectAIT = 4; // Force AIT == 4 for safety

			int isfemale = c.getInt(c.getColumnIndex("isfemale"));
			if (isfemale == 1)
				subject_data.subjectFemale = true;
			else
				subject_data.subjectFemale = false;

			int SafetyOnlyModeIsEnabled = c.getInt(c.getColumnIndex("SafetyOnlyModeIsEnabled"));
			if (SafetyOnlyModeIsEnabled == 1)
				subject_data.subjectSafetyValid = true;
			else
				subject_data.subjectSafetyValid = false;

			int realtime = c.getInt(c.getColumnIndex("realtime"));
			if (realtime == 1)
				subject_data.realTime = true;
			else
				subject_data.realTime = false;
		}
		c.close();
		
		if (readTvector(subject_data.subjectCF, Biometrics.CF_PROFILE_TABLE_NAME))
			subject_data.subjectCFValid = true;
		if (readTvector(subject_data.subjectCR, Biometrics.CR_PROFILE_TABLE_NAME))
			subject_data.subjectCRValid = true;
		if (readTvector(subject_data.subjectBasal, Biometrics.BASAL_PROFILE_TABLE_NAME))
			subject_data.subjectBasalValid = true;
		if (readTvector(subject_data.subjectSafety, Biometrics.SAFETY_PROFILE_TABLE_NAME))
			subject_data.subjectSafetyValid = true;
		c.close();
		
		return subject_data;
	}
	
	public void clearDb()
	{
		SQLiteDatabase db = this.getWritableDatabase();

		db.delete(Biometrics.CF_PROFILE_TABLE_NAME, null, null);
		db.delete(Biometrics.CR_PROFILE_TABLE_NAME, null, null);
		db.delete(Biometrics.BASAL_PROFILE_TABLE_NAME, null, null);
		db.delete(Biometrics.SAFETY_PROFILE_TABLE_NAME, null, null);
		db.delete(Biometrics.SUBJECT_DATA_TABLE_NAME, null, null);
	}
	
	public void writeDb(DiAsSubjectData subject_data)
	{
		final String FUNC_TAG = "writeDb";		
		
		Debug.i("DiAsSetup1", FUNC_TAG, "Saving database...");
		
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put("subjectid", subject_data.subjectName);
		values.put("session", subject_data.subjectSession);
		values.put("weight", subject_data.subjectWeight);
		values.put("height", subject_data.subjectHeight);
		values.put("age", subject_data.subjectAge);
		values.put("TDI", subject_data.subjectTDI);
		
		if (subject_data.subjectFemale) {
			values.put("isfemale", 1);
		} else {
			values.put("isfemale", 0);
		}
		
		if (subject_data.subjectSafetyValid) {
			values.put("SafetyOnlyModeIsEnabled", 1);
		} else {
			values.put("SafetyOnlyModeIsEnabled", 0);
		}
		
		if (subject_data.realTime) {
			values.put("realtime", 1);
		} else {
			values.put("realtime", 0);
		}
		
		values.put("AIT", subject_data.subjectAIT);
		values.put("insulinSetupComplete", (subject_data.subjectCFValid && subject_data.subjectCRValid && subject_data.subjectBasalValid) ? 1 : 0);

		db.insert(Biometrics.SUBJECT_DATA_TABLE_NAME, null, values);
		db.close();

		saveTvector(subject_data.subjectCF, Biometrics.CF_PROFILE_TABLE_NAME, true);
		saveTvector(subject_data.subjectCR, Biometrics.CR_PROFILE_TABLE_NAME, true);
		saveTvector(subject_data.subjectBasal, Biometrics.BASAL_PROFILE_TABLE_NAME, true);
		saveTvector(subject_data.subjectSafety, Biometrics.SAFETY_PROFILE_TABLE_NAME, false);
	}
	
	private boolean readTvector(Tvector tvector, String table) 
	{
		final String FUNC_TAG = "readTvector";
		
		boolean retvalue = false;
		
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + table;
		Cursor c = db.rawQuery(query, null);
		
		long t, t2 = 0;
		double v;
		if (c.moveToFirst()) {
			do {
				t = c.getLong(c.getColumnIndex("time"));
				if (c.getColumnIndex("endtime") < 0){
					v = c.getDouble(c.getColumnIndex("value"));
					//Debug.i(TAG, FUNC_TAG,"readTvector: t=" + t + ", v=" + v);
					tvector.put(t, v);
				} else if (c.getColumnIndex("value") < 0){
					//Debug.i(TAG, FUNC_TAG,"readTvector: t=" + t + ", t2=" + t2);
					t2 = c.getLong(c.getColumnIndex("endtime"));
					tvector.put_range(t, t2);
				}
			} while (c.moveToNext());
			retvalue = true;
		}
		c.close();
		return retvalue;
	}
	
	private void saveTvector(Tvector tvector, String table, boolean value) 
	{
		final String FUNC_TAG = "saveTvector";
		
		SQLiteDatabase db = this.getWritableDatabase();
		
		int ii;
		ContentValues content_values = new ContentValues();
		for (ii = 0; ii < tvector.count(); ii++) 
		{
			content_values.put("time", tvector.get_time(ii).longValue());
			
			if (tvector.get_end_time(ii) != 0)
				content_values.put("endtime", tvector.get_end_time(ii).longValue());
			
			if (tvector.get_value(ii) >= 0 && value)
				content_values.put("value", (tvector.get_value(ii).doubleValue()));
			
			db.insert(table, null, content_values);
		}
		db.close();
	}
}
