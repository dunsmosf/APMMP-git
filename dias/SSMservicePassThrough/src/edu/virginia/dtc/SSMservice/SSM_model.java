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

public class SSM_model {
	public double KBW;
	public double KGEZI;
	public double KGb;
	public double KIb;
	public double KSG;
	public double KSI;
	public double KVg;
	public double KVi;
	public double Kf;
	public double Kid;
	public double Kkabs;
	public double Kkcl;
	public double Kkd;
	public double Kksc;
	public double Kktau;
	public double Kp2;
	public double Ktaumeal;
	public double Ac[][];
	public double Bc[];
	public double Cc[];
	public double Dc;
	public double Gc[];
	public double Gref;
	public double Jref;
	public double xref[];
	public double A[][];
	public double B[][];
	public double C[];
	public double D[];
	public double L[];
	public double M[];
	public double AhypoAlarm[][];
	public double Abrakes[][];
	
	public SSM_model() {
	}
	
	public void display(String tag1, String tag2) {
		Log.i(tag1, tag2+"KBW="+KBW+", KGEZI="+KGEZI+", KGb="+KGb+", KIb="+KIb+", KSG="+KSG+", KSI="+KSI+", KVg="+KVg);
		Log.i(tag1, tag2+"KVi="+KVi+", Kf="+Kf+", Kid="+Kid+", Kkabs="+Kkabs+", Kkcl="+Kkcl+", Kkd="+Kkd+", Kksc="+Kksc);
		Log.i(tag1, tag2+"Kktau="+Kktau+", Kp2="+Kp2+", Ktaumeal="+Ktaumeal);
		for (int ii=0; ii<8; ii++)
			Log.i(tag1, tag2+"Ac: "+Ac[ii][0]+" "+Ac[ii][1]+" "+Ac[ii][2]+" "+Ac[ii][3]+" "+Ac[ii][4]+" "+Ac[ii][5]+" "+Ac[ii][6]+" "+Ac[ii][7]);
		Log.i(tag1, tag2+"Bc: "+Bc[0]+" "+Bc[1]+" "+Bc[2]+" "+Bc[3]+" "+Bc[4]+" "+Bc[5]+" "+Bc[6]+" "+Bc[7]);
		Log.i(tag1, tag2+"Cc: "+Cc[0]+" "+Cc[1]+" "+Cc[2]+" "+Cc[3]+" "+Cc[4]+" "+Cc[5]+" "+Cc[6]+" "+Cc[7]);
		Log.i(tag1, tag2+"Dc="+Dc);
		Log.i(tag1, tag2+"Gc: "+Gc[0]+" "+Gc[1]+" "+Gc[2]+" "+Gc[3]+" "+Gc[4]+" "+Gc[5]+" "+Gc[6]+" "+Gc[7]);
		Log.i(tag1, tag2+"Gref="+Gref);
		Log.i(tag1, tag2+"Jref="+Jref);
		Log.i(tag1, tag2+"xref: "+xref[0]+" "+xref[1]+" "+xref[2]+" "+xref[3]+" "+xref[4]+" "+xref[5]+" "+xref[6]+" "+xref[7]);
		for (int ii=0; ii<8; ii++)
			Log.i(tag1, tag2+"A: "+A[ii][0]+" "+A[ii][1]+" "+A[ii][2]+" "+A[ii][3]+" "+A[ii][4]+" "+A[ii][5]+" "+A[ii][6]+" "+A[ii][7]);
		for (int ii=0; ii<8; ii++)
			Log.i(tag1, tag2+"B: "+B[ii][0]+" "+B[ii][1]);
		Log.i(tag1, tag2+"C: "+C[0]+" "+C[1]+" "+C[2]+" "+C[3]+" "+C[4]+" "+C[5]+" "+C[6]+" "+C[7]);
		Log.i(tag1, tag2+"D0="+D[0]+", D1="+D[1]);
		Log.i(tag1, tag2+"L: "+L[0]+" "+L[1]+" "+L[2]+" "+L[3]+" "+L[4]+" "+L[5]+" "+L[6]+" "+L[7]);
		Log.i(tag1, tag2+"M: "+M[0]+" "+M[1]+" "+M[2]+" "+M[3]+" "+M[4]+" "+M[5]+" "+M[6]+" "+M[7]);
	}

}
