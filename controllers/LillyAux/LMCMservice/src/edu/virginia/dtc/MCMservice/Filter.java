//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.MCMservice;

import android.util.Log;


public class Filter {
	public double alpha;
	public int width;
	public double bolus_max;

	public Filter() {
		// TODO Auto-generated constructor stub
	}
	
	public void display(String tag1, String tag2) {
		Log.i(tag1, tag2+"alpha="+alpha+", width="+width+", bolus_max="+bolus_max);
	}

}
