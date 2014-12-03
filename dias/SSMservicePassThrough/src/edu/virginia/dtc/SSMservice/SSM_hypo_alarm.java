//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.SSMservice;

import android.util.Log;

public class SSM_hypo_alarm {
	public int width;
    public int calibration_window_width; 
	public double thresG;
	public double predH;
	public double eA[][];

	public SSM_hypo_alarm() {
		// TODO Auto-generated constructor stub
	}

	public void display(String tag1, String tag2) {
		Log.i(tag1, tag2+"width="+width+"thresG="+thresG+"predH="+predH);
		for (int ii=0; ii<8; ii++)
			Log.i(tag1, tag2+"eA: "+eA[ii][0]+" "+eA[ii][1]+" "+eA[ii][2]+" "+eA[ii][3]+" "+eA[ii][4]+" "+eA[ii][5]+" "+eA[ii][6]+" "+eA[ii][7]);
	}
}
