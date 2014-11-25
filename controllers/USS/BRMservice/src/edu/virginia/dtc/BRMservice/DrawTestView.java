//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.BRMservice;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Toast;
import android.content.ComponentName;
import android.app.Service;

import java.lang.Math;
import java.util.ArrayList;
import java.util.List;

import edu.virginia.dtc.Tvector.Tvector;
import android.util.Log;

public class DrawTestView extends View implements OnTouchListener {
    Paint paint = new Paint();
    Paint dimPaint = new Paint();
 	private Canvas myCanvas;
 	private Tvector tv;
	
    public DrawTestView(Context context) {
        super(context);
        System.exit(0);
    	/*
        setFocusable(true);
        setFocusableInTouchMode(true);
        this.setOnTouchListener(this);
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        dimPaint.setColor(Color.WHITE);
        dimPaint.setAntiAlias(true);
        dimPaint.setAlpha(40);

        // Test 1 - start up HMSservice
        Log.i("DrawTestView", "Start HMSservice"); 
        Intent intent2 = new Intent();
        intent2.setClassName("edu.virginia.dtc.HMSservice", "edu.virginia.dtc.HMSservice.HMSservice");
        intent2.putExtra("sysCommand", HMSservice.HMS_SERVICE_CMD_START_SERVICE);
        intent2.putExtra("IOB_curve_duration_hours", 8);
        intent2.putExtra("CR", 15.0);
        intent2.putExtra("CF", 1.0/45.0);
        intent2.putExtra("basal", 2.0);
        ComponentName cn2 = getContext().startService(intent2);
        // Test 2 - fetch all biometricsContentProvider data
        Log.i("DrawTestView", "Fetch all biometricsContentProvider data"); 
        intent2 = new Intent();
        intent2.setClassName("edu.virginia.dtc.HMSservice", "edu.virginia.dtc.HMSservice.HMSservice");
        intent2.putExtra("sysCommand", HMSservice.HMS_SERVICE_CMD_GET_ALL_BIOMETRIC_DATA);
        cn2 = getContext().startService(intent2);
*/

/*
        // Test 2 - test Tvector methods
        // Fetch Bitmaps
        // BackgroundImage1 = BitmapFactory.decodeResource(getResources(), R.drawable.introscreen);
        // Test Tvector methods
        // 1. Add and retrieve elements
        tv = new Tvector(5);
        tv.init();
        int ii;
        tv.put(4, 120*(1+(float)(Math.random()-0.5)));
        tv.put(7, 120*(1+(float)(Math.random()-0.5)));
        tv.put(35, 120*(1+(float)(Math.random()-0.5)));
        tv.put(1, 120*(1+(float)(Math.random()-0.5)));
        tv.put(90, 120*(1+(float)(Math.random()-0.5)));
        tv.put(32, 120*(1+(float)(Math.random()-0.5)));
        tv.put(189, 120*(1+(float)(Math.random()-0.5)));
        tv.put(16, 120*(1+(float)(Math.random()-0.5)));
        tv.put(2, 120*(1+(float)(Math.random()-0.5)));
        tv.put(87, 120*(1+(float)(Math.random()-0.5)));
        tv.put(4321, 120*(1+(float)(Math.random()-0.5)));
        tv.put(3, 120*(1+(float)(Math.random()-0.5)));
        tv.put(13, 120*(1+(float)(Math.random()-0.5)));
        tv.put(122, 120*(1+(float)(Math.random()-0.5)));
        tv.put(31, 120*(1+(float)(Math.random()-0.5)));
        tv.put(66, 120*(1+(float)(Math.random()-0.5)));
        tv.put(67, 120*(1+(float)(Math.random()-0.5)));
        tv.put(5, 120*(1+(float)(Math.random()-0.5)));
                
        Pair p = new Pair();
        for (ii=0; ii<tv.count(); ii++) { 
        	if ( (p = tv.get(ii)) != null) {
        		Toast.makeText(context, "ii="+ii+", time="+p.time()+", value="+p.value() , Toast.LENGTH_SHORT).show();
        	}
        	else {
        		Toast.makeText(context, "No Pair element for Tvector entry "+ii, Toast.LENGTH_SHORT).show();
        	}
        }
		Toast.makeText(context, "last value="+tv.get_last_value() , Toast.LENGTH_SHORT).show();
		
		// 2. find elements in various ranges
		List<Integer> indices1 = new ArrayList<Integer>();
		indices1 = tv.find(">", 15, "<=", 122);
		if (indices1 != null) {
			for (ii=0; ii<indices1.size(); ii++) {
				Toast.makeText(context, "time for index ["+ii+"]="+tv.get(indices1.get(ii)).time(), Toast.LENGTH_SHORT).show();
			}
		}
		*/
    }
 
    public boolean onTouch(View view, MotionEvent event) {
    	boolean retValue = true;
    	if (event.getAction() == MotionEvent.ACTION_DOWN) {
    		System.exit(0);
    	}
    	view.invalidate();
    	return retValue;
    }

    @Override
    public void onDraw(Canvas canvas) {
//        myCanvas = canvas;
//        canvas.drawColor(Color.CYAN);
     }

}
