package edu.virginia.dtc.MCMservice;

import Jama.*;

public class OptCORR_model {
	
	// population average EMMGK model parameters
	public double BW;
	public double GEZI;
	public double Gb;
	public double Ib;
	public double SG;
	public double SI;
	public double Vg;
	public double Vi;
	public double f;
	public double id;
	public double kabs;
	public double kcl;
	public double kd;
	public double ksc;
	public double ktau;
	public double p2;
	public double taumeal;
	public double kd0;
	
	// System matrices
	public Matrix A;
	public Matrix B;
	public Matrix C;
	
	// script matrices
	public Matrix Ascript;
	public Matrix Bscript;
	public Matrix B0script;
	public Matrix Cscript;
	public Matrix Rscript;
	public Matrix Qscript;
		
	public OptCORR_model() {
	}
		
}
