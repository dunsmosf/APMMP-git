//*********************************************************************************************************************
//  Copyright 2011-2013 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.APCservice;

import android.util.Log;

public class TherapyData {
	public double differential_basal_rate;
	public double spend_request;
	
	public TherapyData(long time) {
		// TODO Auto-generated constructor stub
		// Initialize needed values
		differential_basal_rate = 0.0;
		spend_request = 0.0;
	}

}
