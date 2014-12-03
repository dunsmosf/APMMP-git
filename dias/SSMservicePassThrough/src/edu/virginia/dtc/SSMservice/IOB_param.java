//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.SSMservice;

public class IOB_param {
	public double alpha;
	public double beta;
	public double[] curves;
	public double[] curves_nonrecursive;
	public double target;
	public int hist_length;
	public double[] fourcurves;
	public double[] sixcurves;
	public double[] eightcurves;
	public double[] fourcurves_nonrecursive;
	public double[] sixcurves_nonrecursive;
	public double[] eightcurves_nonrecursive;


	public IOB_param(int number_of_insulin_curve_values, int number_of_nonrecursive_insulin_curve_values) {
		// TODO Auto-generated constructor stub
		curves = new double[number_of_insulin_curve_values];
		curves_nonrecursive = new double[number_of_nonrecursive_insulin_curve_values];
	}

}
