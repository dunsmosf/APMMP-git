//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.SSMservice;

public class SSM_brakes_param {
	public double alpha;
	public int filterwidth;
	public double k;
	public SSM_risk risk;
	
	public SSM_brakes_param() {
		risk = new SSM_risk();
	}

}
