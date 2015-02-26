package edu.virginia.dtc.DiAsSetup;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;

public class ParamsFragment extends Fragment {

	private final String TAG = "ParamsFragment";
	public static View view;
	public ParametersActivity main;
	public boolean readOnly;
	
	public ParamsFragment() 
	{
		return;
	}
	
	public static ParamsFragment getInstance(ParametersActivity main) 
	{
		ParamsFragment fragment = new ParamsFragment();
		fragment.main = main;
		fragment.readOnly = true;
		return fragment;
	}
	
	public static ParamsFragment getInstance(ParametersActivity main, boolean readOnly) 
	{
		ParamsFragment fragment = new ParamsFragment();
		fragment.main = main;
		fragment.readOnly = readOnly;
		return fragment;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		Debug.i(TAG, "onCreateView", "");
		view = inflater.inflate(R.layout.params_fragment, container, false);
		Context context = inflater.getContext();
		
		LinearLayout currentList = (LinearLayout) view.findViewById(R.id.parametersList);
		
		Cursor c = context.getContentResolver().query(Biometrics.PARAM_URI, null, null, null, null);
		if (c.moveToFirst()) {
			while (!c.isAfterLast()) {
				LinearLayout newView = new LinearLayout(context);
	        	newView.setOrientation(LinearLayout.HORIZONTAL);
	        	
	        	TextView packageNameView = new TextView(context);
	        	packageNameView.setText("'"+c.getString(c.getColumnIndex("name"))+"' = "+c.getString(c.getColumnIndex("value")));
	        	
	        	newView.addView(packageNameView);
	        	currentList.addView(newView);
	        	
	        	c.moveToNext();
			}
		}
		c.close();
		
		return view;
	}
}
