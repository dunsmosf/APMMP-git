//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.MealActivity;
import edu.virginia.dtc.Tvector.Tvector;
import android.util.Log;
import android.widget.Toast;
import Jama.*;

public class OptCORR_param {
	
	public static final boolean DEBUG = true;
	private static final String MYTAG = "model_construction";
	public OptCORR_model model;
	public double CR_avg;
	public double q = 3.316121719646661e-02;
	public double u_LA_injection = 3.180281401500506e+00; // U/min 
	public double VBR_mUperMin_average;
	public double SC0_init_cond;
	
    int coldim_Ascript;
    int rowdim_Ascript;
    int coldim_Bscript;
    int rowdim_Bscript;
    int coldim_B0script;
    int rowdim_B0script;
    int coldim_Cscript;
    int rowdim_Cscript;
    int coldim_Qscript;
    int rowdim_Qscript;
    public Matrix zeroM_Bscript;
    public Matrix zeroM_B0script;

	
    
	public OptCORR_param(Tvector LA_injection, Tvector CR, double BW) {
		
		// TODO Auto-generated constructor stub
		
		model = new OptCORR_model();          
		CR_avg = mean_value(CR);
		initOptCORR_param(BW);
	}

    public void initOptCORR_param(double BW) {
    	 	
    	// model parameters
    	model.BW = 8.800243669426772e+01;
		model.GEZI = 0.0230;
		model.Gb = 112.5000;
		model.Ib = 22.4202;
		model.SG = 0.0113;
		model.SI = 3.2841e-04;
		model.Vg = 2.9111;
		model.Vi = 0.0601;
		model.f = 0.9000;
		model.id = 50.5000;
		model.kabs = 0.0119;
		model.kcl = 0.2538;
		model.kd = 0.0204;
		model.ksc = 0.0909;
		model.ktau = 0.0839;
		model.p2 = 0.1387;
		model.taumeal = 0.0050;
		model.kd0 = 0.00028;
    	
	    	
    	//**********************************************************
    	// Model Matrices, discretized in MATLAB
    	//**********************************************************	
    	
		double[][] A_ini = {{9.450625168590742e-01, -3.932106526246453e+02, -7.931581578066036e-06, -2.866957717455132e-04, -7.348918768960021e-03, 0, 4.255396964262459e-05, 2.279502953866225e-04},
					        {0,  4.999425665018947e-01,  5.142947033681595e-08,  1.311530662832043e-06, 1.887657073303963e-05, 0, 0, 0},
					        {0, 0,  9.028774356089306e-01, 0, 0, 0, 0, 0},
					        {0, 0,  9.224560167930070e-02,  9.028774356089306e-01, 0, 0, 0, 0},
					        {0, 0,  3.309965762112265e-03,  5.443890659530898e-02,  2.810718671303809e-01, 0, 0, 0},
					        {3.542997284574726e-01, -8.629861229613891e+01, -7.160217307587960e-07, -3.280882992652074e-05, -1.162027504517129e-03, 6.348179684777104e-01, 6.016467161444669e-06, 4.557739223514646e-05},
					        {0, 0, 0, 0, 0, 0, 6.572515813544529e-01, 0},
					        {0, 0, 0, 0, 0, 0, 3.320343716583045e-01, 9.420938782596230e-01}};
		model.A = new Matrix(A_ini);
    	
    	double[][] B_ini={{-8.513292415681497e-06},
			    	      {7.135812382740888e-08},
			    	      {4.753059781756988e+00},
			    	      {2.386726037123345e-01},
			    	      {6.173436008643281e-03},
			    	      {-6.348266629236438e-07},
			    	      {0},
			    	      {0}};
        model.B = new Matrix(B_ini);
        
        double[][] C_ini={{1, 0, 0, 0, 0, 0, 0, 0}};
        model.C = new Matrix(C_ini);
    	
     // KF matrices, obtained in MATLAB (with all operating points and initial conditions done)
        
        double[][] Akf_ini = {{9.450625168590742e-01, -3.932106526246453e+02, -7.931581578066036e-06, -2.866957717455132e-04, -7.348918768960021e-03, -8.993386378712529e-01, 4.255396964262459e-05, 2.279502953866225e-04},
      				          {0, 4.999425665018947e-01,  5.142947033681595e-08,  1.311530662832043e-06,  1.887657073303963e-05, 0, 0, 0},
    				          {0, 0, 9.028774356089306e-01, 0, 0, 0, 0, 0},
    				          {0, 0, 9.224560167930070e-02, 9.028774356089306e-01, 0, 0, 0, 0},
    				          {0, 0, 3.309965762112265e-03, 5.443890659530898e-02, 2.810718671303809e-01, 0, 0, 0},
    				          {3.542997284574726e-01, -8.629861229613891e+01, -7.160217307587960e-07, -3.280882992652074e-05, -1.162027504517129e-03, 4.437457796247490e-02, 6.016467161444669e-06, 4.557739223514646e-05},
    				          {0, 0, 0, 0, 0, -9.655223662082925e+01, 6.572515813544529e-01, 0}, 
    				          {0, 0, 0, 0, 0, -8.154272603553949e+02, 3.320343716583045e-01, 9.420938782596230e-01}};
    	Matrix Akf = new Matrix(Akf_ini);

    	double[][] Bkf_ini = {{-8.513292415681497e-06, 8.993386378712529e-01},
     					      {7.135812382740888e-08, 0},
    					      {4.753059781756988e+00, 0},
    					      {2.386726037123345e-01, 0},
    					      {6.173436008643281e-03, 0},
    					      {-6.348266629236438e-07, 5.904433905152355e-01},
    					      {0, 9.655223662082925e+01},
    					      {0, 8.154272603553949e+02}};
    	Matrix Bkf = new Matrix(Bkf_ini);

        double[][] Ckf_ini={{0, 0, 0, 0, 0, 5.475892392572868e-01, 0, 0},
        					 {1, 0, 0, 0, 0, -7.487203858048873e-01, 0, 0},
        					 {0, 1, 0, 0, 0, 0, 0, 0},
        					 {0, 0, 1, 0, 0, 0, 0, 0},
        					 {0, 0, 0, 1, 0, 0, 0, 0},
        					 {0, 0, 0, 0, 1, 0, 0, 0},
        					 {0, 0, 0, 0, 0, 5.475892392572868e-01, 0, 0},
        					 {0, 0, 0, 0, 0, -1.469030115102287e+02, 1, 0},
        					 {0, 0, 0, 0, 0, -8.137728403990428e+02, 0, 1}};
        Matrix Ckf = new Matrix(Ckf_ini);

        double[][] Dkf_ini={{0, 4.524107607427133e-01},
    		        	     {0, 7.487203858048873e-01},
    		        	     {0, 0},
    		        	     {0, 0},
    		        	     {0, 0},
    		        	     {0, 0},
    		        	     {0, 4.524107607427133e-01},
    		        	     {0, 1.469030115102287e+02},
    		        	     {0, 8.137728403990428e+02}};
        Matrix Dkf = new Matrix(Dkf_ini);
           
		debug_message(MYTAG, "model matrix construction end");
		
		debug_message(MYTAG, "script matrix construction start");

		//**********************************************************
    	// Hue Matrices
    	//**********************************************************
		
	    int N = 72;
        int ii;
        int jj;
  

        //Ascript
        
        rowdim_Ascript = model.A.getRowDimension();
        coldim_Ascript = model.A.getColumnDimension();
        
        double[][] Ascript_ini=new double[rowdim_Ascript*N][coldim_Ascript];
        model.Ascript = new Matrix(Ascript_ini);
       
        for (ii = 0; ii <= rowdim_Ascript*N-rowdim_Ascript; ii+=rowdim_Ascript){
        	 Matrix temporary;
        	 temporary = model.A.copy();
        	 if ((ii+rowdim_Ascript)/rowdim_Ascript == 1) {
        		 for (int i = 0; i < rowdim_Ascript; i++) {
        			for(int j = 0; j < coldim_Ascript; j++) {
        				int temp;
        				temp = (i == j) ? 1:0;
        				temporary.set(i, j, temp);
        			}
        		}
        	 }
        	 if ((ii+rowdim_Ascript)/rowdim_Ascript > 1) {
        	   	 int hh;
        	   	 for (hh = 0; hh < (ii+rowdim_Ascript)/rowdim_Ascript-2; hh++){
        	   	     temporary = temporary.times(model.A);
        	   	 }
        	 }
        	 model.Ascript.setMatrix(ii, ii + rowdim_Ascript - 1, 0, coldim_Ascript - 1, temporary);
        }
        
        //Bscript
        
        rowdim_Bscript = model.A.getRowDimension();
        coldim_Bscript = model.B.getColumnDimension();

        double[][] zeroM_ini_Bscript = new double[rowdim_Bscript][coldim_Bscript];
        zeroM_Bscript = new Matrix(zeroM_ini_Bscript);

        double[][] Bscript_ini=new double[rowdim_Bscript*N][coldim_Bscript*N];
        model.Bscript = new Matrix(Bscript_ini);
        
        for (ii = 0; ii <= rowdim_Bscript*N-rowdim_Bscript; ii+=rowdim_Bscript){
            for (jj = 0; jj <= coldim_Bscript*N-coldim_Bscript; jj+=coldim_Bscript){
        	    Matrix temporary;
        		temporary = model.A.copy();
        		
        		if (ii/rowdim_Bscript <= jj) { // on the diagonal or above it - put zeros
        		    temporary = temporary.times(zeroM_Bscript);
        		}
        		if (ii/rowdim_Bscript - 1 == jj) { //right below the diagonal - put B
        		    temporary = model.B.copy(); 
                }
        		if (ii/rowdim_Bscript - 1 > jj) { // below the first "sub-diagonal" - put power of A multiplied by B 
        			int hh;
    			   	for (hh = 0; hh < ii/rowdim_Bscript - jj - 2; hh++){
    			   		temporary = temporary.times(model.A);
    			   	}
        		temporary = temporary.times(model.B);
        		 }
        	model.Bscript.setMatrix(ii, ii+rowdim_Bscript-1, jj, jj+coldim_Bscript-1, temporary); 		 
            }
        }

        
        //B0script
        
        rowdim_B0script = model.A.getRowDimension();
        coldim_B0script = model.B.getColumnDimension();

        double[][] zeroM_ini_B0script = new double[rowdim_B0script][coldim_B0script];
        zeroM_B0script = new Matrix(zeroM_ini_B0script);

        double[][] B0script_ini=new double[rowdim_B0script*N][coldim_B0script];
        model.B0script = new Matrix(B0script_ini);

        for (ii = 0; ii <= rowdim_B0script*N-rowdim_B0script; ii++){
        	 Matrix temporary;
        	 temporary = model.A.copy();
        	 if ((ii+rowdim_B0script)/rowdim_B0script == 1) {
        	 	 temporary = zeroM_B0script;
        	 }
        	 if ((ii+rowdim_B0script)/rowdim_B0script == 2) {
        		 temporary = model.B;
        	 }
        	 if ((ii+rowdim_B0script)/rowdim_B0script > 2) {
        	   	 int hh;
        	   	 for (hh = 0; hh < (ii+rowdim_B0script)/rowdim_B0script-3; hh++){
        	   	     temporary = temporary.times(model.A);
        	   	 }
        	   	 temporary = temporary.times(model.B);
        	 }
        	 model.B0script.setMatrix(ii, ii + rowdim_B0script - 1, 0, coldim_B0script - 1, temporary);
        	 ii = ii + rowdim_B0script - 1;
        }
    
        //Cscript
        
        rowdim_Cscript = model.C.getRowDimension();
        coldim_Cscript = model.C.getColumnDimension();

        double[][] Cscript_ini=new double[rowdim_Cscript*N][coldim_Cscript*N];
        model.Cscript = new Matrix(Cscript_ini);
     
        for (ii = 0; ii <= rowdim_Cscript*N-rowdim_Cscript; ii+=rowdim_Cscript){
            for (jj = 0; jj <= coldim_Cscript*N-coldim_Cscript; jj+=coldim_Cscript){
        		if ( (ii+rowdim_Cscript)/rowdim_Cscript == (jj+coldim_Cscript)/coldim_Cscript ) {
        			Matrix temporary;
        			temporary = model.C.copy();
        			model.Cscript.setMatrix(ii, ii+rowdim_Cscript-1, jj, jj+coldim_Cscript-1, temporary);
        		}
        	}
        }
        
        // Rscript
        model.Rscript = new Matrix(1, 1, 1);
        
        // Q_scirpt 

        rowdim_Qscript = 1;
        coldim_Qscript = 1;
        
    	double[][] Qscript_ini = new double[rowdim_Qscript*N][coldim_Qscript*N];
    	model.Qscript = new Matrix(Qscript_ini);
    	
    	for (ii = 0; ii <= rowdim_Qscript*N-rowdim_Qscript; ii+=rowdim_Qscript){
            for (jj = 0; jj <= coldim_Qscript*N-coldim_Qscript; jj+=coldim_Qscript){
            	 if (ii == jj) {
            		 model.Qscript.set(ii, jj, q);
            	 }  
            }
    	}

}  	
    
    private void debug_message(String tag, String message) {
		if (DEBUG) {
			Log.i(tag, message);
		}
	}
       
    public double mean_value(Tvector p) {
    	int ii;
    	double mean = 0;
    	for (ii=0; ii<p.count(); ii++) {
    		mean = mean+p.get(ii).value();
    	}
    	mean = mean/p.count();
    	return mean;
    }

}

