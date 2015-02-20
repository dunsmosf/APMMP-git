package edu.virginia.dtc.BRMservice;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.Tvector.Tvector;

public class BrmDB extends SQLiteOpenHelper 
{
	private static final String TAG = "BRM_DB";
	
	private static final int DATABASE_VERSION = 2;
	private static final String DATABASE_NAME = "BRM";
	
    private static final String HISTORY_TABLE = "history";
    private static final String HISTORY_TABLE_CREATE =
    		"CREATE TABLE " + HISTORY_TABLE + "(" +
    		"_id integer primary key autoincrement, " +
    		"time long, starttime long, duration long, d1 double, d2 double, d3 double);";
    private static final String TDI_HISTORY_TABLE = "TDIhistory";
    private static final String TDI_HISTORY_TABLE_CREATE =
    		"CREATE TABLE " + TDI_HISTORY_TABLE + "(" +
    		"_id integer primary key autoincrement, " +
    		"time long, subjectID text, TDIc double, TDIest double);";
    
    public Context context;

    BrmDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context=context;
    }

	@Override
	public void onCreate(SQLiteDatabase db) {
		final String FUNC_TAG = "onCreate";
		
		Debug.i(TAG, FUNC_TAG, "");
		
		db.execSQL(HISTORY_TABLE_CREATE);
		db.execSQL(TDI_HISTORY_TABLE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + HISTORY_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + TDI_HISTORY_TABLE);
        onCreate(db);
	}
	
	public void UpdateTDIest(String subjectID, long time,double TDIest) {
		final String FUNC_TAG = "modifyBrmDB";
	
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put("time", time);
		values.put("TDIest", TDIest);
		values.put("subjectID", subjectID);
		
		int rows = db.update(TDI_HISTORY_TABLE, values, "time="+time, null);
		
		Debug.i(TAG, FUNC_TAG, "Rows modified: "+rows);
		
		db.close();
		
		saveDatabase();
	}
	
	public void UpdateTDIc(String subjectID, long time,double TDIc) {
		final String FUNC_TAG = "UpdateTDIc";
		
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put("time", time);
		values.put("TDIc", TDIc);
		values.put("subjectID", subjectID);
		
		int rows = db.update(TDI_HISTORY_TABLE, values, "time="+time, null);
		
		Debug.i(TAG, FUNC_TAG, "Rows modified: "+rows);
		
		db.close();
		
		saveDatabase();
	}
	
    public void addtoBrmDB(long time,
    		            long starttime,
    		            long duration,
						double d1,
						double d2, 
						double d3) 
	{
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put("time", time);
		values.put("starttime", starttime);
		values.put("duration", duration);
		values.put("d1", d1);
		values.put("d2", d2);
		values.put("d3", d3);
		
		db.insert(HISTORY_TABLE, null, values);
		db.close();
		
	    saveDatabase();  // save to sd card
		
	}
	
    public void addTDItoBrmDB(String subjectID, long time,double TDIc, double TDIest) 
	{
		final String FUNC_TAG = "TDItoBrmDB";
		
		SQLiteDatabase db = this.getWritableDatabase();
		
		Debug.i(TAG, FUNC_TAG, "Adding TDI to database");
		
		ContentValues values = new ContentValues();
		values.put("time", time);
		values.put("subjectID", subjectID);
		
		if (TDIc !=0){
			values.put("TDIc", TDIc);
		}
		if (TDIest !=0){
			values.put("TDIest", TDIest);
		}
		db.insert(TDI_HISTORY_TABLE, null, values);
		db.close();
		
		saveDatabase();  // save to sd card
	}
	
	public Settings getBrmDB(int id) {
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + HISTORY_TABLE + " where _id='"+id+"'";

		Settings st = new Settings();
		
		Cursor c = db.rawQuery(query, null);
        
		if (c != null) {
			c.moveToFirst();
		}
		
        //Fill in event data
        st.time = c.getLong(c.getColumnIndex("time"));
        st.starttime = c.getLong(c.getColumnIndex("starttime"));
        st.duration = c.getLong(c.getColumnIndex("duration"));
        st.d1 = c.getDouble(c.getColumnIndex("d1"));
        st.d2 = c.getDouble(c.getColumnIndex("d2"));
        st.d3 = c.getDouble(c.getColumnIndex("d3"));
        c.close();
        
		return st;
	}
	
	public Settings getTDI_DB(int id) {
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + TDI_HISTORY_TABLE + " where _id='"+id+"'";

		Settings st = new Settings();
		
		Cursor c = db.rawQuery(query, null);
        
		if (c != null) {
			c.moveToFirst();
		}
		
        //Fill in event data
		st.subjectID = c.getString(c.getColumnIndex("subjectID"));
        st.time = c.getLong(c.getColumnIndex("time"));
        st.TDIc = c.getDouble(c.getColumnIndex("TDIc"));
        st.TDIest = c.getDouble(c.getColumnIndex("TDIest"));
        c.close();
        
		return st;
	}	

	public Settings getLastBrmDB()
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + HISTORY_TABLE;

		Settings st = new Settings();
		
		Cursor c = db.rawQuery(query, null);
        
		if (c != null) {
			c.moveToLast();   //// need to verify
		}
		
        //Fill in event data
		//st.id = c.getInt(c.getColumnIndex("id"));
		st.time = c.getLong(c.getColumnIndex("time"));
        st.starttime = c.getLong(c.getColumnIndex("starttime"));
        st.duration = c.getLong(c.getColumnIndex("duration"));
        st.d1 = c.getDouble(c.getColumnIndex("d1"));
        st.d2 = c.getDouble(c.getColumnIndex("d2"));
        st.d3 = c.getDouble(c.getColumnIndex("d3"));
        
        c.close();
        
		return st;
	}	

	public Settings getLastTDIestBrmDB(String subjectID)
	{
		final String FUNC_TAG = "getLastTDIestBrmDB";
		
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + TDI_HISTORY_TABLE+ " where TDIest IS NOT NULL AND subjectID="+"'"+subjectID+"'";

		Settings st = new Settings();
		
		Cursor c = db.rawQuery(query, null);
		Debug.i(TAG,FUNC_TAG,"last tdi est query >>>>"+c.getCount());
		if (c.getCount() != 0) {
			
			c.moveToLast();   //// need to verify
			st.time = c.getLong(c.getColumnIndex("time"));
	        st.TDIest = c.getDouble(c.getColumnIndex("TDIest"));
	        
		}
		else {
			st.time = getCurrentTimeSeconds();
	        st.TDIest = 0;
		}
		c.close();
        
        return st;
	}
	
	//getTDIcHistory == gets history of TDIc since time t
	public Tvector getTDIcHistory(String subjectID, long time)
	{
		final String FUNC_TAG = "getTDIcHistory";
		Tvector temp= new Tvector();
		
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + TDI_HISTORY_TABLE+ " where TDIc IS NOT NULL AND time > "+time+" AND subjectID="+"'"+subjectID+"'";

		Cursor c = db.rawQuery(query, null);
		Debug.i(TAG,FUNC_TAG,"TDIc History >>>>"+c.getCount()+" since >>>  "+time);	
		if((c.moveToFirst())&&(c.getCount() != 0))
		{
			do {
				temp.put(c.getLong(c.getColumnIndex("time")), c.getDouble(c.getColumnIndex("TDIc")));
			} while (c.moveToNext());
		}
		
		c.close();
		
        return temp;
	}
	
	public long getCurrentTimeSeconds() {
		return (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970		
	}
	
	public int getTotalEvents() {
		String countQuery = "SELECT  * FROM " + HISTORY_TABLE;
        SQLiteDatabase db = this.getReadableDatabase();
        int count = 0;
        
        Cursor c = db.rawQuery(countQuery, null);
        count = c.getCount();
        c.close();
        
        return count;
	}
	
	public boolean lookupEventCounterInHistory(long eventCnt) {
		final String FUNC_TAG = "lookupEventCounterInHistory";
		
		String idQuery = "SELECT * FROM " + HISTORY_TABLE + " where eventCounter='"+eventCnt+"'";
		SQLiteDatabase db = this.getReadableDatabase();
		
		Cursor c = db.rawQuery(idQuery, null);
		if(c != null)
		{
			Debug.i(TAG, FUNC_TAG, "Found "+c.getCount()+" rows with eventCounter: "+eventCnt);
			
			if(c.getCount() > 0)
				return true;
			else
				return false;
		}
		
		return false;
	}
	
	public int saveDatabase() {
		final String FUNC_TAG = "saveDatabase";

	 	SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd");
	 	String EXPORT_FILE_NAME = "BrmDB-"+sdf.format(new Date(System.currentTimeMillis()));

	 	try
	 	{		 		
	 		File f = new File("/sdcard/"+EXPORT_FILE_NAME+".db");
	 		if (!f.createNewFile())
	 		{
	 			/*
	 			for (int i = 1;!f.createNewFile(); i++)
	 			{
	 				String filename ="/sdcard/"+EXPORT_FILE_NAME;
	 				filename += "("+i+").db";;
	 				f = new File(filename);
	 			}
	 			*/
	 		}

	 		FileInputStream s = new FileInputStream(new File(context.getFilesDir().toString().split("/files")[0]+"/databases/"+DATABASE_NAME));

	 		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
	 		byte[] buffer = new byte[1024];
	 		int bytesRead = 0;
	 		while( (bytesRead = s.read(buffer)) > 0)
	 		{
 			out.write(buffer, 0, bytesRead);
	 		}
	 		out.close();		        
	 		Debug.i(TAG, FUNC_TAG,  "Saved database to " + f.getAbsolutePath());
	 	} 
	 	catch (IOException ioe)
	 	{
	 		ioe.printStackTrace();
	 		Debug.i(TAG, FUNC_TAG,  "IOException in saving database");
	 		return 0;
	 	} 
	 	catch (CursorIndexOutOfBoundsException ce)
	 	{
	 		ce.printStackTrace();
	 		Debug.i(TAG, FUNC_TAG,  "Cursor error in saving database");
	 	}
	 	
	 	return 1;
    }
}

