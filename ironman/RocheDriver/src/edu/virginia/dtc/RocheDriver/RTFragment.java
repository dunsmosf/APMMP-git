package edu.virginia.dtc.RocheDriver;

import java.util.Timer;
import java.util.TimerTask;

import edu.virginia.dtc.RocheData.Application;
import edu.virginia.dtc.SysMan.Debug;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;


public class RTFragment extends Fragment {
	
	private static final String TAG = "RTFragment";
	
	private Driver drv = Driver.getInstance();
	private ActivityManager am;
	public static View view;
	public RocheUI main;
	public static Context context;
	
	public TextView r1s1, r1s2, r1s3, r1s4, r1s5, r1s6, r1s7, r1s8;
	public TextView r2s1, r2s2, r2s3, r2s4, r2s5, r2s6, r2s7, r2s8;
	public TextView r3s1, r3s2, r3s3, r3s4, r3s5, r3s6, r3s7, r3s8;
	public TextView r4s1, r4s2, r4s3, r4s4, r4s5, r4s6, r4s7, r4s8;
	
	public TextView rx;
	
	public Button menu, check, up, down, nokey, run, refresh;
	
	private InterfaceData data;
	
	public RTFragment(final RocheUI main)
	{
		this.main = main;
		
		data = InterfaceData.getInstance();
	}
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		am = (ActivityManager) main.getSystemService(Context.ACTIVITY_SERVICE);
	}
	
	public void onDestroy()
	{
		super.onDestroy();
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final String FUNC_TAG = "onCreateView";

		view = inflater.inflate(R.layout.rt, container, false);
		
		Debug.i(TAG, FUNC_TAG, "Creating view...");
		
		RTFragment.context = this.getActivity();
		
		menu = (Button)view.findViewById(R.id.menu);
		menu.setOnClickListener(new OnClickListener(){
			public void onClick(View v) 
			{
				//drv.a.rtSendKey(Application.MENU, true);
			}
		});
		
		check = (Button)view.findViewById(R.id.check);
		check.setOnClickListener(new OnClickListener(){
			public void onClick(View v) 
			{
				//drv.a.rtSendKey(Application.CHECK, true);
			}
		});
		
		up = (Button)view.findViewById(R.id.up);
		up.setOnClickListener(new OnClickListener(){
			public void onClick(View v) 
			{
				//drv.a.rtSendKey(Application.UP, true);
			}
		});
		
		down = (Button)view.findViewById(R.id.down);
		down.setOnClickListener(new OnClickListener(){
			public void onClick(View v) 
			{
				//drv.a.rtSendKey(Application.DOWN, true);
			}
		});
		
		nokey = (Button)view.findViewById(R.id.nokey);
		nokey.setOnClickListener(new OnClickListener(){
			public void onClick(View v) 
			{
				//drv.a.rtSendKey(Application.NO_KEY, true);
			}
		});
		
		run = (Button)view.findViewById(R.id.run);
		run.setOnClickListener(new OnClickListener(){
			public void onClick(View v) 
			{
				//if(Driver.getMode() == Driver.COMMAND)				//We have to transition states to RT
					//drv.a.startMode(Driver.RT);
				//else
					//Debug.i(TAG, FUNC_TAG, "Driver is in mode: "+Driver.getMode());
				
				drv.a.setTbr();
			}
		});
		
		rx = (TextView)view.findViewById(R.id.rxQueue);
		
		r1s1 = (TextView)view.findViewById(R.id.textView1);
		r1s2 = (TextView)view.findViewById(R.id.infoText);
		r1s3 = (TextView)view.findViewById(R.id.textView3);
		r1s4 = (TextView)view.findViewById(R.id.textView4);
		r1s5 = (TextView)view.findViewById(R.id.textView5);
		r1s6 = (TextView)view.findViewById(R.id.textView6);
		r1s7 = (TextView)view.findViewById(R.id.textView7);
		r1s8 = (TextView)view.findViewById(R.id.textView8);
		
		r2s1 = (TextView)view.findViewById(R.id.TextView01);
		r2s2 = (TextView)view.findViewById(R.id.TextView02);
		r2s3 = (TextView)view.findViewById(R.id.TextView03);
		r2s4 = (TextView)view.findViewById(R.id.TextView04);
		r2s5 = (TextView)view.findViewById(R.id.TextView05);
		r2s6 = (TextView)view.findViewById(R.id.TextView06);
		r2s7 = (TextView)view.findViewById(R.id.TextView07);
		r2s8 = (TextView)view.findViewById(R.id.TextView08);
		
		r3s1 = (TextView)view.findViewById(R.id.TextView09);
		r3s2 = (TextView)view.findViewById(R.id.TextView10);
		r3s3 = (TextView)view.findViewById(R.id.TextView11);
		r3s4 = (TextView)view.findViewById(R.id.TextView12);
		r3s5 = (TextView)view.findViewById(R.id.TextView13);
		r3s6 = (TextView)view.findViewById(R.id.TextView14);
		r3s7 = (TextView)view.findViewById(R.id.TextView15);
		r3s8 = (TextView)view.findViewById(R.id.TextView16);
		
		r4s1 = (TextView)view.findViewById(R.id.TextView17);
		r4s2 = (TextView)view.findViewById(R.id.TextView18);
		r4s3 = (TextView)view.findViewById(R.id.TextView19);
		r4s4 = (TextView)view.findViewById(R.id.TextView20);
		r4s5 = (TextView)view.findViewById(R.id.TextView21);
		r4s6 = (TextView)view.findViewById(R.id.TextView22);
		r4s7 = (TextView)view.findViewById(R.id.TextView23);
		r4s8 = (TextView)view.findViewById(R.id.TextView24);
		
		return view;
	}
	
	public void updateUI()
	{
//		if(r4s8!=null)
//		{
//			r1s1.setText(Driver.r1s1);
//			r1s2.setText(Driver.r1s2);
//			r1s3.setText(Driver.r1s3);
//			r1s4.setText(Driver.r1s4);
//			r1s5.setText(Driver.r1s5);
//			r1s6.setText(Driver.r1s6);
//			r1s7.setText(Driver.r1s7);
//			r1s8.setText(Driver.r1s8);
//			
//			r2s1.setText(Driver.r2s1);
//			r2s2.setText(Driver.r2s2);
//			r2s3.setText(Driver.r2s3);
//			r2s4.setText(Driver.r2s4);
//			r2s5.setText(Driver.r2s5);
//			r2s6.setText(Driver.r2s6);
//			r2s7.setText(Driver.r2s7);
//			r2s8.setText(Driver.r2s8);
//			
//			r3s1.setText(Driver.r3s1);
//			r3s2.setText(Driver.r3s2);
//			r3s3.setText(Driver.r3s3);
//			r3s4.setText(Driver.r3s4);
//			r3s5.setText(Driver.r3s5);
//			r3s6.setText(Driver.r3s6);
//			r3s7.setText(Driver.r3s7);
//			r3s8.setText(Driver.r3s8);
//			
//			r4s1.setText(Driver.r4s1);
//			r4s2.setText(Driver.r4s2);
//			r4s3.setText(Driver.r4s3);
//			r4s4.setText(Driver.r4s4);
//			r4s5.setText(Driver.r4s5);
//			r4s6.setText(Driver.r4s6);
//			r4s7.setText(Driver.r4s7);
//			r4s8.setText(Driver.r4s8);
//			
//			rx.setText("RX Queue: "+InterfaceData.pumpMessages.size());
//		}
	}
}
