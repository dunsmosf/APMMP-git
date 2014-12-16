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

public class BrmDB extends SQLiteOpenHelper 
{
	private static final String TAG = "BRM_DB";
	
	public static final int UNKNOWN = -1;
	public static final int PENDING = 0;
	public static final int DELIVERING = 1;
	public static final int DELIVERED = 2;
	public static final int CANCELLED = 3;
	public static final int INTERRUPTED = 4;
	public static final int INVALID_REQ = 5;
	
	private static final int DATABASE_VERSION = 2;
	private static final String DATABASE_NAME = "BRM";
	
    private static final String HISTORY_TABLE = "history";
    private static final String HISTORY_TABLE_CREATE =
    		"CREATE TABLE " + HISTORY_TABLE + "(" +
    		"_id integer primary key autoincrement, " +
    		"time long, starttime long, duration long, d1 double, d2 double, d3 double, TDI double);";
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
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + HISTORY_TABLE);
 
        onCreate(db);
	}
	



//	public void modifyBrmDB(long time,
//			int d0,
//			double d1,
//			double d2, 
//			double d3) {
//		final String FUNC_TAG = "modifyBrmDB";
//		
//		
//		
//		SQLiteDatabase db = this.getWritableDatabase();
//	
//		ContentValues values = new ContentValues();
//		values.put("time", time);
//		values.put("setting", d0);
//		values.put("d1", d1);
//		values.put("d2", d2);
//		values.put("d3", d3);
//		
//		int rows = db.update(HISTORY_TABLE, values, "eventCounter = '"+e.eventCounter+"'", null);
//		
//		Debug.i(TAG, FUNC_TAG, "Rows modified: "+rows);
//		
//		db.close();
//		
//		saveDatabase();
//    }	

	
	
    public void addtoBrmDB(long time,
    		            long starttime,
    		            long duration,
						double d1,
						double d2, 
						double d3) 
	{
		final String FUNC_TAG = "addtoBrmDB";
		
		SQLiteDatabase db = this.getWritableDatabase();
		
//		if(lookupEventCounterInHistory(e.eventCounter))
//		{
//			Debug.i(TAG, FUNC_TAG, "Event already exists, cannot add again!");
//			return;
//		}
//		else
//			Debug.i(TAG, FUNC_TAG, "Adding event...Event Counter: "+e.eventCounter);
		
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
	
    public void addTDItoBrmDB(long time,double TDI) 
	{
			final String FUNC_TAG = "TDItoBrmDB";
			
			SQLiteDatabase db = this.getWritableDatabase();
			
			//if(lookupEventCounterInHistory(e.eventCounter))
			//{
			//Debug.i(TAG, FUNC_TAG, "Event already exists, cannot add again!");
			//return;
			//}
			//else
			//Debug.i(TAG, FUNC_TAG, "Adding event...Event Counter: "+e.eventCounter);
			
			Settings st = IOMain.db.getLastBrmDB();
			
			ContentValues values = new ContentValues();
			values.put("time", time);
			values.put("starttime", st.starttime);
			values.put("duration", st.duration);
			values.put("d1", st.d1);
			values.put("d2", st.d2);
			values.put("d3", st.d3);
			values.put("TDI", TDI);
			
			db.insert(HISTORY_TABLE, null, values);
			db.close();
			
			saveDatabase();  // save to sd card
	
	}
	
	public Settings getBrmDB(int id)
	{
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
        st.TDI = c.getDouble(c.getColumnIndex("TDI"));
        
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

	public Settings getLastTDIBrmDB()
	{
		final String FUNC_TAG = "getLastTDIBrmDB";
		
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + HISTORY_TABLE+ " where TDI!=null";;

		Settings st = new Settings();
		
		Cursor c = db.rawQuery(query, null);
		Debug.i(TAG,FUNC_TAG,"Hello sweet   >>>> "+c.getCount());
		if (c.getCount() != 0) {
			Debug.i(TAG,FUNC_TAG,"Hello sweet");
			c.moveToLast();   //// need to verify
			st.time = c.getLong(c.getColumnIndex("time"));
	        st.TDI = c.getDouble(c.getColumnIndex("TDI"));
	        Debug.i(TAG,FUNC_TAG,"Hello sweet 1");
		}
		else {
			Debug.i(TAG,FUNC_TAG,"Hello sweet 1 1");
			st.time = getCurrentTimeSeconds();
	        st.TDI = 0;
	        Debug.i(TAG,FUNC_TAG,"Hello sweet 2");
		}
		Debug.i(TAG,FUNC_TAG,"Hello sweet 3");
		c.close();
		Debug.i(TAG,FUNC_TAG,"Hello sweet 4");
        //Fill in event data
		//st.id = c.getInt(c.getColumnIndex("id"));
 
        
        Debug.i(TAG,FUNC_TAG,"time =  "+st.time+"  TDI=   "+st.TDI);
        Debug.i(TAG,FUNC_TAG,"Hello sweet final");
        return st;
	}
	
	public long getCurrentTimeSeconds() {
		return (long)(System.currentTimeMillis()/1000);			// Seconds since 1/1/1970		
	}

//	public List<Events> getManualInsulinHistory()
//	{
//		SQLiteDatabase db = this.getReadableDatabase();
//		String query = "SELECT * FROM " + HISTORY_TABLE + " where bolusId='-1'";
//		List<Events> events = new ArrayList<Events>();
//		
//		Cursor c = db.rawQuery(query, null);
//		if(c != null)
//		{
//			if(c.moveToFirst())
//			{
//				do
//				{
//					//We should get each event if the ID exists
//					events.add(getHistoryEvent(c.getInt(c.getColumnIndex("_id"))));
//				} while (c.moveToNext());
//			}
//		}
//		c.close();
//		
//		//Return the manual history
//		return events;
//	}
//	
//	public List<Events> getHistory()
//	{
//		SQLiteDatabase db = this.getReadableDatabase();
//		String query = "SELECT * FROM " + HISTORY_TABLE;
//		List<Events> events = new ArrayList<Events>();
//		
//		Cursor c = db.rawQuery(query, null);
//		if(c != null)
//		{
//			if(c.moveToFirst())
//			{
//				do
//				{
//					//We should get each event if the ID exists
//					events.add(getHistoryEvent(c.getInt(c.getColumnIndex("_id"))));
//				} while (c.moveToNext());
//			}
//		}
//		c.close();
//		
//		//Return the completed history
//		return events;
//	}
	
	public int getTotalEvents()
	{
		String countQuery = "SELECT  * FROM " + HISTORY_TABLE;
        SQLiteDatabase db = this.getReadableDatabase();
        int count = 0;
        
        Cursor c = db.rawQuery(countQuery, null);
        count = c.getCount();
        c.close();
        
        return count;
	}
	
	public boolean lookupEventCounterInHistory(long eventCnt)
	{
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
	
//	public Events lookupBolusInHistory(int id)
//	{
//		final String FUNC_TAG = "lookupBolusInHistory";
//		
//		Events e = Driver.getInstance().new Events();
//		String idQuery = "SELECT * FROM " + HISTORY_TABLE + " where bolusId='"+id+"'";
//		SQLiteDatabase db = this.getReadableDatabase();
//		
//		double req = 0, deliv = 0;
//		
//		Cursor c = db.rawQuery(idQuery, null);
//		if(c != null)
//		{
//			Debug.i(TAG, FUNC_TAG, "Found "+c.getCount()+" rows with ID: "+id);
//			
//			if(c.moveToFirst())
//			{
//				do
//				{
//					e = getHistoryEvent(c.getInt(c.getColumnIndex("_id")));
//					switch(e.eventID)
//					{
//						case 15:
//							e.status = DELIVERED;
//							e.description = "Delivered";
//							deliv = e.bolus;
//							break;
//						case 14:
//							e.status = DELIVERING;
//							e.description = "Delivering";
//							req = e.bolus;
//							break;
//						default:
//							Debug.i(TAG, FUNC_TAG, "Unknown Event ID!");
//							break;
//					}
//					
//					Debug.i(TAG, FUNC_TAG, "Status "+ e.status + " found for ID: "+id);
//				} while (c.moveToNext());
//			}
//		}
//		
//		if(e.status != DELIVERED)
//		{
//			Debug.i(TAG, FUNC_TAG, "Bolus not delivered, marking failure in return status!");
//			e.description = "Invalid Request";
//			e.status = INVALID_REQ;
//			e.bolusId = id;
//		}
//		
//		Debug.i(TAG, FUNC_TAG, "Returned bolus status: "+e.status);
//		
//		if(e.status == DELIVERED && (req!=deliv))
//		{
//			e.status = CANCELLED;
//		}
//		
//		return e;
//	}
	
	public int saveDatabase()
	{
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

