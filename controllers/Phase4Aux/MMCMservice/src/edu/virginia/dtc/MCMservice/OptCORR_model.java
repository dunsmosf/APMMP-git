package edu.virginia.dtc.MCMservice;

import Jama.*;

public class OptCORR_model {
	public double LBW;
	public double LVI;
	public double Lp1;
	public double Lp2;
	public double Lp4;
	public double Lp6;
	public double Lkd;
	public double Lkcl;
	public double La1;
	public double La2;
	public double Lad;
	public double LGb;
	public double LIb;
	
	public Matrix Buffer_A;
	public Matrix Buffer_B;
	public Matrix Buffer_C;
	public Matrix Buffer_D;

	public Matrix AQ;
	public Matrix BQ;
	public Matrix CQall;
	public Matrix DQ;
	public Matrix CQ;

	public Matrix AI;
	public Matrix BI;
	public Matrix CIall;
	public Matrix DI;
	public Matrix CI;

	public Matrix AX;
	public Matrix BX;
	public Matrix CX;
	public Matrix DX;
	
	public Matrix Akf;
	public Matrix Bkf;
	public Matrix Ckf;
	public Matrix Dkf;
	
	public Matrix AC;
	public Matrix BCI;
	public Matrix BCQ;
	public Matrix BCI1;
	public Matrix BCI2;

// script matrix
	// d~ calculation
	public Matrix AQscript;
	public Matrix CQscript;
	public Matrix GammaQ;

	// insulin transport
	public Matrix AIscript;
	public Matrix BIscript;
	public Matrix GammaI1;
	public Matrix GammaI2;
	
	//core model
	public Matrix ACscript;
	public Matrix BCI2script;
	public Matrix GammaC1;
	public Matrix GammaC2;
	public Matrix GammaC3;

		
	public OptCORR_model() {
	}
		
}
