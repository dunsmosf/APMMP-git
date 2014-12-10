package edu.virginia.dtc.RocheDriver;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import edu.virginia.dtc.RocheDriver.Driver.Events;
import edu.virginia.dtc.RocheDriver.Driver.History;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Pump;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class RocheDB extends SQLiteOpenHelper 
{
	private static final String TAG = "Application_DB";
	
	public static final int UNKNOWN = -1;
	public static final int PENDING = 0;
	public static final int DELIVERING = 1;
	public static final int DELIVERED = 2;
	public static final int CANCELLED = 3;
	public static final int INTERRUPTED = 4;
	public static final int INVALID_REQ = 5;
	
	public static final int MANUAL = 0;
	public static final int SYSTEM = 1;
	
	private static final int DATABASE_VERSION = 2;
	private static final String DATABASE_NAME = "ROCHE";
	
    private static final String HISTORY_TABLE = "history";
    private static final String HISTORY_TABLE_CREATE =
    		"CREATE TABLE " + HISTORY_TABLE + "(" +
    		"_id integer primary key autoincrement, " +
    		"bolusId long, time long, eventId int, eventCounter long, bolus double, description text, isProcessed int);";

    RocheDB(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
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
	
	public void modifyHistoryEvent(Events e)
	{
		final String FUNC_TAG = "modifyHistoryEvent";
		
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues c = new ContentValues();
		c.put("bolusId", e.eventCounter);
		c.put("time", e.timestamp);
		c.put("eventId", e.eventID);
		c.put("eventCounter", e.eventCounter);
		c.put("bolus", e.bolus);
		c.put("description", e.description);
		c.put("isProcessed", Events.PROCESSED);
		
		int rows = db.update(HISTORY_TABLE, c, "eventCounter = '"+e.eventCounter+"'", null);
		
		Debug.i(TAG, FUNC_TAG, "Rows modified: "+rows);
		
		db.close();
		
		saveDatabase();
	}
	
	public void addHistoryEvent(Events e)
	{
		final String FUNC_TAG = "addHistoryEvent";
		
		SQLiteDatabase db = this.getWritableDatabase();
		
		if(lookupEventCounterInHistory(e.eventCounter))
		{
			Debug.i(TAG, FUNC_TAG, "Event already exists, cannot add again!");
			return;
		}
		else
			Debug.i(TAG, FUNC_TAG, "Adding event...Event Counter: "+e.eventCounter);
		
		ContentValues c = new ContentValues();
		c.put("bolusId", e.bolusId);
		c.put("time", e.timestamp);
		c.put("eventId", e.eventID);
		c.put("eventCounter", e.eventCounter);
		c.put("bolus", e.bolus);
		c.put("description", e.description);
		c.put("isProcessed", e.isProcessed);
		
		db.insert(HISTORY_TABLE, null, c);
		db.close();
		
		saveDatabase();
	}
	
	public Events getHistoryEvent(int id)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + HISTORY_TABLE + " where _id='"+id+"'";
		Events e = Driver.getInstance().new Events();
		
		Cursor c = db.rawQuery(query, null);
        if (c != null)
            c.moveToFirst();
		
        //Fill in event data
        e.timestamp = c.getLong(c.getColumnIndex("time"));
        e.bolusId = c.getLong(c.getColumnIndex("bolusId"));
        e.eventID = (short) c.getInt(c.getColumnIndex("eventId"));
        e.eventCounter = c.getLong(c.getColumnIndex("eventCounter"));
        e.bolus = c.getDouble(c.getColumnIndex("bolus"));
        e.description = c.getString(c.getColumnIndex("description"));
        e.isProcessed = c.getInt(c.getColumnIndex("isProcessed"));
        
        c.close();
        
		return e;
	}
	
	public List<Events> getUnprocessedCommandEvents()
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + HISTORY_TABLE + " where isProcessed = "+Events.UNPROCESSED+" AND ( eventId = 14 OR eventId = 15 )";
		List<Events> events = new ArrayList<Events>();
		
		Cursor c = db.rawQuery(query, null);
		if(c != null)
		{
			if(c.moveToFirst())
			{
				do
				{
					//We should get each event if the ID exists
					events.add(getHistoryEvent(c.getInt(c.getColumnIndex("_id"))));
				} while (c.moveToNext());
			}
		}
		c.close();
		
		//Return the unprocessed history
		return events;
	}
	
	public List<Events> getManualInsulinHistory()
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + HISTORY_TABLE + " where bolusId='-1'";
		List<Events> events = new ArrayList<Events>();
		
		Cursor c = db.rawQuery(query, null);
		if(c != null)
		{
			if(c.moveToFirst())
			{
				do
				{
					//We should get each event if the ID exists
					events.add(getHistoryEvent(c.getInt(c.getColumnIndex("_id"))));
				} while (c.moveToNext());
			}
		}
		c.close();
		
		//Return the manual history
		return events;
	}
	
	public int getBolusStatus(Events e)
	{
		final String FUNC_TAG = "getBolusStatus";
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + HISTORY_TABLE + " where eventCounter='"+e.eventCounter+"'";
		int status = -1;
		
		Cursor c = db.rawQuery(query, null);
        if (c != null && c.moveToFirst() && c.getCount() == 1)
        {
        	int id = c.getInt(c.getColumnIndex("_id"));
        	id --;
        	
        	String prev = "SELECT * FROM " + HISTORY_TABLE + " where _id='"+id+"'";
        	Cursor r = db.rawQuery(prev, null);
        	if(r != null && r.moveToFirst() && r.getCount() == 1)
        	{
        		if(r.getInt(r.getColumnIndex("eventId")) == 14)
        		{
        			Debug.i(TAG, FUNC_TAG, "Comparing request to infusion!");
        			return Pump.DELIVERED;
        		}
        		else
        			Debug.e(TAG, FUNC_TAG, "Previous entry is not requested insulin!");
        		r.close();
        	}
        	else
        		Debug.e(TAG, FUNC_TAG, "Error getting previous requested insulin!");
        	
        	c.close();
        }
        else
        {
        	Debug.e(TAG, FUNC_TAG, "There is no event for this bolus!");
        }
        	
        
		return status;
	}
	
	public List<Events> getHistory()
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String query = "SELECT * FROM " + HISTORY_TABLE;
		List<Events> events = new ArrayList<Events>();
		
		Cursor c = db.rawQuery(query, null);
		if(c != null)
		{
			if(c.moveToFirst())
			{
				do
				{
					//We should get each event if the ID exists
					events.add(getHistoryEvent(c.getInt(c.getColumnIndex("_id"))));
				} while (c.moveToNext());
			}
		}
		c.close();
		
		//Return the completed history
		return events;
	}
	
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
	
	public Events lookupBolusInHistory(int id)
	{
		final String FUNC_TAG = "lookupBolusInHistory";
		
		Events e = Driver.getInstance().new Events();
		String idQuery = "SELECT * FROM " + HISTORY_TABLE + " where bolusId='"+id+"'";
		SQLiteDatabase db = this.getReadableDatabase();
		
		double req = 0, deliv = 0;
		
		Cursor c = db.rawQuery(idQuery, null);
		if(c != null)
		{
			Debug.i(TAG, FUNC_TAG, "Found "+c.getCount()+" rows with ID: "+id);
			
			if(c.getCount() > 2)
			{
				Debug.e(TAG, FUNC_TAG, "Found more than 2 rows with the same ID, this is not good...");
				return e; 
			}
			
			if(c.moveToFirst())
			{
				do
				{
					e = getHistoryEvent(c.getInt(c.getColumnIndex("_id")));
					switch(e.eventID)
					{
						case 15:
							e.status = DELIVERED;
							e.description = "Delivered";
							deliv = e.bolus;
							break;
						case 14:
							e.status = DELIVERING;
							e.description = "Delivering";
							req = e.bolus;
							break;
						default:
							Debug.i(TAG, FUNC_TAG, "Unknown Event ID!");
							break;
					}
					
					Debug.i(TAG, FUNC_TAG, "Status "+ e.status + " found for ID: "+id);
				} while (c.moveToNext());
			}
		}
		
		if(e.status != DELIVERED)
		{
			Debug.i(TAG, FUNC_TAG, "Bolus not delivered, marking failure in return status!");
			e.description = "Invalid Request";
			e.status = INVALID_REQ;
			e.bolusId = id;
		}
		
		Debug.i(TAG, FUNC_TAG, "Returned bolus status: "+e.status);
		
		if(e.status == DELIVERED && (req!=deliv))
		{
			e.status = CANCELLED;
		}
		
		return e;
	}
	
	public int saveDatabase()
	{
		final String FUNC_TAG = "saveDatabase";

	 	SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd");
	 	String EXPORT_FILE_NAME = "RocheDB-"+sdf.format(new Date(System.currentTimeMillis()));
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
	 		
	 		FileInputStream s = new FileInputStream(new File(Driver.getInstance().serv.getFilesDir().toString().split("/files")[0]+"/databases/"+DATABASE_NAME));
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
