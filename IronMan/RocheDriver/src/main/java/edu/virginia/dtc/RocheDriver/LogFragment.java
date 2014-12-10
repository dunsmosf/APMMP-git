package edu.virginia.dtc.RocheDriver;

import edu.virginia.dtc.RocheDriver.Driver.Events;
import edu.virginia.dtc.SysMan.Debug;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class LogFragment extends Fragment {

	private static final String TAG = "LogFragment";
	private Driver drv = Driver.getInstance();
	private ActivityManager am;
	public static View view;
	public RocheUI main;
	
	private ListView responses;
	
	public LogFragment()
	{
		final String FUNC_TAG = "onCreate";
		
		Debug.i(TAG, FUNC_TAG, "LogFragment");
		
		this.main = drv.main;
	}
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		am = (ActivityManager) main.getSystemService(Context.ACTIVITY_SERVICE);
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final String FUNC_TAG = "onCreateView";
		
		view = inflater.inflate(R.layout.log, container, false);
		
		Debug.i(TAG, FUNC_TAG, "Building history view...");
		
		responses = (ListView)view.findViewById(R.id.respList);
		responses.setAdapter(drv.histList);
		
		return view;
	}
	
	public void updateUI() 
	{
		buildHistory();
	}
	
	private void buildHistory()
	{
		final String FUNC_TAG = "buildHistory";
		
		if(drv.histList.getCount() != drv.db.getTotalEvents())
		{
			Debug.i(TAG, FUNC_TAG, "Updating history! History count: "+drv.db.getTotalEvents());
			
			drv.histList.clear();
			for(Events e:drv.db.getHistory())
			{
				drv.histList.add(e.getEventString());
			}
		}
		else
		{
			Debug.i(TAG, FUNC_TAG, "History is up to date!");
		}
	}
}
