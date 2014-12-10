package edu.virginia.dtc.SysMan;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;

public class Params {

	private static final String TAG = "Params";

	public static double getDouble(ContentResolver resolver, String name, Double defValue)
	{
		Bundle b = getParameter(resolver, name);
		
		if(!b.isEmpty())
			return b.getDouble(name);
		else
			return defValue;
	}
	
	public static int getInt(ContentResolver resolver, String name, int defValue)
	{
		Bundle b = getParameter(resolver, name);
		
		if(!b.isEmpty())
			return b.getInt(name);
		else
			return defValue;
	}
	
	public static long getLong(ContentResolver resolver, String name, long defValue)
	{
		Bundle b = getParameter(resolver, name);
		
		if(!b.isEmpty())
			return b.getLong(name);
		else
			return defValue;
	}
	
	public static String getString(ContentResolver resolver, String name, String defValue)
	{
		Bundle b = getParameter(resolver, name);
		
		if(!b.isEmpty())
			return b.getString(name);
		else
			return defValue;
	}
	
	public static boolean getBoolean(ContentResolver resolver, String name, boolean defValue)
	{
		Bundle b = getParameter(resolver, name);
		
		if(!b.isEmpty())
			return b.getBoolean(name);
		else
			return defValue;
	}
	
	public static Bundle getParameter(ContentResolver resolver, String name)
	{
		final String FUNC_TAG = "getParameter";
		
		Cursor c = resolver.query(Biometrics.PARAM_URI, null, "name = '"+name+"'", null, null);
		
		Bundle out = new Bundle();
		
		if(c!=null)
		{
			if(c.getCount() > 0)
			{
				Debug.i(TAG, FUNC_TAG, "Rows: "+c.getCount()+" Columns: "+c.getColumnCount());
				
				c.moveToFirst();
				String value = c.getString(c.getColumnIndex("value"));
				String type = c.getString(c.getColumnIndex("type"));
				
				Debug.i(TAG, FUNC_TAG, "Name: "+name+" Value: "+value+" Type: "+type);
				
				out = getParamType(out, type, name, value);
			}
		}
		c.close();
		
		return out;
	}
	
	public static Bundle getAllParameters(ContentResolver resolver)
	{
		final String FUNC_TAG = "getAllParameters";
		
		Cursor c = resolver.query(Biometrics.PARAM_URI, null, null, null, null);
		
		Bundle out = new Bundle();
		
		if(c!=null)
		{
			if(c.getCount() > 0)
			{
				Debug.i(TAG, FUNC_TAG, "Rows: "+c.getCount()+" Columns: "+c.getColumnCount());
				
				c.moveToFirst();
				
				do
				{
					String value = c.getString(c.getColumnIndex("value"));
					String type = c.getString(c.getColumnIndex("type"));
					String name = c.getString(c.getColumnIndex("name"));
					
					Debug.i(TAG, FUNC_TAG, "Name: "+name+" Value: "+value+" Type: "+type);
					
					out = getParamType(out, type, name, value);
				}
				while(c.moveToNext());
			}
		}
		c.close();
		
		return out;
	}
	
	private static Bundle getParamType(Bundle b, String type, String name, String value)
	{
		final String FUNC_TAG = "getParamType";
		
		Debug.i(TAG, FUNC_TAG, "Type: "+type+" Name: "+name+" Value: "+value);
		
		if(type.equalsIgnoreCase("int"))
		{
			b.putInt(name, Integer.parseInt(value));
		}
		else if(type.equalsIgnoreCase("double"))
		{
			b.putDouble(name, Double.parseDouble(value));
		}
		else if(type.equalsIgnoreCase("string"))
		{
			b.putString(name, value);
		}
		else if(type.equalsIgnoreCase("long"))
		{
			b.putLong(name, Long.parseLong(value));
		}
		else if(type.equalsIgnoreCase("boolean"))
		{
			b.putBoolean(name, Boolean.parseBoolean(value));
		}
		
		return b;
	}
}
