package edu.virginia.dtc.SSMservice;

import java.util.ArrayList;
import java.util.List;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.TimeValue;
import android.content.Context;
import android.database.Cursor;

public class DataAccessor {

	/**
	 * Gets the most recent calibration SMBG value, returns -1 if 
	 * there is no calibration data
	 * @param ctx Context for the content resolver
	 * @return
	 */
	public static long getMostRecentCalibrationTime(Context ctx) {
		long time = -1;
		
		Cursor c = ctx.getContentResolver().query(Biometrics.SMBG_URI, 
				new String[]{"time", "isCalibration"}, 
				"isCalibration = 1", 
				null, 
				"time DESC LIMIT 1");
		
		if (c.moveToFirst()) {
			return c.getLong(c.getColumnIndex("time"));
		}
		
		return time;
	}
	
	/**
	 * Outputs a list of time-value pairs of CGM values in descending
	 * order from now back to the duration specified, returns an empty
	 * list if there is no data in the range
	 * @param ctx Context for the content resolver
	 * @param timeFrameSec Duration, in seconds, of data you want 
	 * in the past (i.e. 300 would return all CGM data in the last
	 * 300 seconds)
	 */
	public static void getCgmArray(Context ctx, long timeFrameSec) {
		List<TimeValue> cgm = new ArrayList<TimeValue>();
		
		long time = System.currentTimeMillis()/1000 - timeFrameSec;
		
		Cursor c = ctx.getContentResolver().query(Biometrics.CGM_URI, 
				new String[]{"time", "cgm"}, 
				"time > "+time, 
				null, 
				"time DESC");
		
		if (c.moveToFirst()) {
			do {
				long t = c.getLong(c.getColumnIndex("time"));
				double v = c.getDouble(c.getColumnIndex("cgm"));
				
				cgm.add(new TimeValue(t, v));
			} while (c.moveToNext());
		}
	}
}
