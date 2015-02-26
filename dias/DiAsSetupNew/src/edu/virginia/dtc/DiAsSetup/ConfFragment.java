package edu.virginia.dtc.DiAsSetup;

import java.util.List;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import edu.virginia.dtc.SysMan.Debug;

public class ConfFragment extends Fragment {

	private final String TAG = "ConfFragment";
	public ParametersActivity main;
	Context context;
	
	public ConfFragment() 
	{
		return;
	}
	
	public static ConfFragment getInstance(ParametersActivity main) 
	{
		ConfFragment fragment = new ConfFragment();
		fragment.main = main;
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
		View view = inflater.inflate(R.layout.conf_fragment, container, false);
		context = inflater.getContext();
		
		LinearLayout currentList = (LinearLayout) view.findViewById(R.id.currentApps);
		
		final PackageManager pm = context.getPackageManager();
		List<ApplicationInfo> installedPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
		
		for (ApplicationInfo info : installedPackages) {
			
			if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 1) {
				
				LinearLayout newView = new LinearLayout(context);
	        	newView.setOrientation(LinearLayout.HORIZONTAL);
	        	
	        	String versionNumber;
	        	try {
	        		versionNumber = "v."+info.metaData.getString("Version").split(" ")[1];
				} catch (Exception e) {
					versionNumber = "Unknown Version";
				}
	        	String textContent = info.loadLabel(pm) + " - "+ versionNumber + " - (package: "+info.packageName+")";
	        	
	        	TextView versionStringView = new TextView(context);
	        	versionStringView.setText(textContent);
	        	
	        	newView.addView(versionStringView);
	        	
	        	currentList.addView(newView);
			}
        }
		
		return view;
	}
}