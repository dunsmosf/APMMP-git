//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.MCMservice;
import edu.virginia.dtc.Tvector.Tvector;
import android.util.Log;
import android.widget.Toast;
import Jama.*;

public class OptCORR_param {
	
	public static final boolean DEBUG = true;
	private static final String MYTAG = "model_construction";
	public OptCORR_model model;
	public double basal_avg;
	public double CR_avg;

	//public final double k_1 = 0.0173;
	
	public OptCORR_param(Tvector subject_basal, Tvector CR, int BW) {
		// TODO Auto-generated constructor stub
		
		model = new OptCORR_model();          
		basal_avg = mean_value(subject_basal)*1000/60; // from U/h to mU/min
		CR_avg = mean_value(CR);
		initOptCORR_param(BW);
	}

    public void initOptCORR_param(int BW) {
    	 	
    	// model
    	model.LBW = BW;
    	model.LVI = 0.060050826000000;
    	model.Lp1 = 0.006860613895034;
    	model.Lp2 = 4.797072116399615e-04;
    	model.Lp4 = 0.015527780977218;
    	model.Lp6 = 2.833223016962278e-05*76.33; // multiply by BW
    	model.Lkd = 0.020433693000000;
    	model.Lkcl = 0.253828977487143;
    	model.La1 = 0.01;
    	model.La2 = 0.01;
    	model.Lad = 0.02;
    	model.LGb = 110;
    	model.LIb = basal_avg/(model.Lkcl*model.LBW*model.LVI);//////////////////
    	
		debug_message(MYTAG, "model matrix construction start");

   // get multipliers
    	if (CR_avg>30) {
    		CR_avg=30; /////////////////////////////
    	}
    	double CR_mean = CR_avg;
    	double mtp1 = Math.exp(-0.8+2.5*(CR_mean-5)/(CR_mean+15));
    	double mtp2 = Math.exp(0.1);
    	double mtp3 = Math.exp(-0.0225*CR_mean+0.4973);
    	double mtp4 = Math.exp(-0.0305*CR_mean+0.2844);
   
   // for initial condition calculation
    	model.Lkd = model.Lkd*mtp3;

   // buffer
    	double[][] BufferA_ini={{0.3679, 0},{0.3679, 0.3679}}; // 5-minute
    	//double[][] BufferA_ini={{0.8187, 0},{0.1637, 0.8187}}; // 1-minute
    	model.Buffer_A=new Matrix(BufferA_ini);
    	
    	double[][] BufferB_ini={{0.6321},{0.2642}}; // 5-minute
    	//double[][] BufferB_ini={{0.1813},{0.0175}}; // 1-minute
    	model.Buffer_B=new Matrix(BufferB_ini);
    	
    	double[][] BufferC_ini={{0,1}};
    	model.Buffer_C=new Matrix(BufferC_ini);
    	
    	double[][] BufferD_ini={{0}};
    	model.Buffer_D=new Matrix(BufferD_ini);
    	
    	//**********************************************************
    	// Models
    	//**********************************************************	
    	
    	//gut model
    	double[][] AQ_ini={{-0.1309*mtp4+0.9921, 0},
		                   {0.0831*mtp4+0.007, -0.0478*mtp4+0.9991}};
        model.AQ=new Matrix(AQ_ini);

        double[][] BQ_ini={{-0.3426*mtp4+4.9865},{0.2213*mtp4+0.0119}};
        model.BQ=new Matrix(BQ_ini);

        double[][] CQall_ini={{1, 0},
                              {0, 1}};
        model.CQall=new Matrix(CQall_ini);

        double[][] DQ_ini={{0},{0}};
        model.DQ=new Matrix(DQ_ini);
    	
        double[][] CQ_ini={{model.La1*mtp4,model.La2*mtp4}};
        model.CQ=new Matrix(CQ_ini);

        //insulin model
    	double[][] AI_ini={{-0.09*mtp3+0.9931, 0, 0},
    			           {0.0788*mtp3+0.0132, -0.09*mtp3+0.9931, 0},
    			           {0.0076*mtp3-0.0043, 0.0497*mtp3+0.0047, 0.2811}};
    	model.AI=new Matrix(AI_ini);
    	
    	double[][] BI_ini={{-0.2347*mtp3+4.9882},{0.2154*mtp3+0.0228},{0.0144*mtp3-0.0082}};
        model.BI=new Matrix(BI_ini);
        
        double[][] CIall_ini={{1, 0, 0},
		                      {0, 1, 0},
		                      {0, 0, 1}};
        model.CIall=new Matrix(CIall_ini);
        
        double[][] DI_ini={{0},{0},{0}};
        model.DI=new Matrix(DI_ini);
        
        double[][] CI_ini={{0,0,1/(model.LBW*model.LVI)}};
        model.CI=new Matrix(CI_ini);
    	
        //X model
    	double[][] AX_ini={{0.9253}};
        model.AX=new Matrix(AX_ini);

        double[][] BX_ini={{0.0747}};
        model.BX=new Matrix(BX_ini);

        double[][] CX_ini={{1}};
        model.CX=new Matrix(CX_ini);

        double[][] DX_ini={{0}};
        model.DX=new Matrix(DX_ini);
    	
        //Kalman Filter
        double[][] Akf_ini={{-0.1932*mtp1+0.1891,-0.0023*mtp1},
        		            {-86+130*Math.pow(mtp1/0.55,4)/(1+Math.pow(mtp1/0.55,4)),0.9253}};
        model.Akf=new Matrix(Akf_ini);

        double[][] Bkf_ini={{-8.9711e-05*mtp1, 0.0117, 0.1932*mtp1+0.7772},
        		            {0.0747,0, 86-130*Math.pow(mtp1/0.55,4)/(1+Math.pow(mtp1/0.55,4))}};
        model.Bkf=new Matrix(Bkf_ini);

        double[][] Ckf_ini={{-0.0136*mtp1+0.0804,0},
        		            {-0.0136*mtp1+0.0804,0},
        		            {-93+145*Math.pow(mtp1/0.57,4)/(1+Math.pow(mtp1/0.57,4)),1}};
        model.Ckf=new Matrix(Ckf_ini);

        double[][] Dkf_ini={{0,0,0.0136*mtp1+0.9196},
        		            {0,0,0.0136*mtp1+0.9196},
        		            {0,0,93-145*Math.pow(mtp1/0.57,4)/(1+Math.pow(mtp1/0.57,4))}};
        model.Dkf=new Matrix(Dkf_ini);

        
        //core model in U* calculation
        double[][] AC_ini={{0.9663, -0.0023*mtp1, -0.0023*mtp1},
        		           {0, 0.9253,0},
        		           {0, 0, 0.9931}};
        model.AC=new Matrix(AC_ini);

        double[][] BCI_ini={{-8.9711e-05*mtp1},{0.0747},{0}};
        model.BCI=new Matrix(BCI_ini);

        double[][] BCQ_ini={{0.0117},{0},{0}};
        model.BCQ=new Matrix(BCQ_ini);

        double[][] BCI1_ini={{0},{0},{0}};
        model.BCI1=new Matrix(BCI1_ini);
        model.BCI1.set(1,0,model.BCI.get(1,0));
 
        
        double[][] BCI2_ini={{0},{0},{0}};
        model.BCI2=new Matrix(BCI2_ini);
        model.BCI2.set(1,0,-model.BCI.get(1,0)*model.LIb);     
   
		debug_message(MYTAG, "model matrix construction end");
		
		debug_message(MYTAG, "script matrix construction start");

    	//**********************************************************
    	// Hue Matrices
    	//**********************************************************
		
	    int N = 48; 
        
        //AQscript
        int coldim;
        coldim = model.AQ.getColumnDimension();
        int rowdim;
        rowdim = model.AQ.getRowDimension();

        double[][] AQscript_ini=new double[rowdim*N][coldim];
        model.AQscript = new Matrix(AQscript_ini);
        
        int ii;
        for (ii = 0; ii <= rowdim*N-rowdim; ii++){
        	 	    
        	 Matrix temporary;
        	 temporary = model.AQ.copy();
        	
        	 if ((ii+rowdim)/rowdim-1 == 0) {
        		 for (int i = 0; i < rowdim; i++) {
        			for(int j = 0; j < coldim; j++) {
        				int temp;
        				temp = (i == j) ? 1:0;
        				temporary.set(i, j, temp);
        			}
        		}
        	 }
        	 
        	 if ((ii+rowdim)/rowdim-1 == 1) {
           	     temporary = model.AQ.copy();
        	 }
        	
        	 if ((ii+rowdim)/rowdim-1 > 1) {
        	   	 int hh;
        	   	 for (hh = 0; hh < (ii+rowdim)/rowdim-2; hh++){
        	   	     temporary = temporary.times(model.AQ);
        	   	 }
        	 }
        	        	 
        	 model.AQscript.setMatrix(ii, ii + rowdim - 1, 0, coldim - 1, temporary);
        	 ii = ii + rowdim - 1;
        }

       // debug_message("matrix string",model.AQscript.);
        
        //CQscript
       
        rowdim = model.CQ.getRowDimension();
        coldim = model.CQ.getColumnDimension();

        double[][] CQscript_ini=new double[rowdim*N][coldim*N];
        model.CQscript = new Matrix(CQscript_ini);
     
        for (ii = 0; ii <= rowdim*N-rowdim; ii+=rowdim){
            int jj;
        	for (jj = 0; jj <= coldim*N-coldim; jj+=coldim){
        		if ( (ii+rowdim)/rowdim == (jj+coldim)/coldim ) {
        			Matrix temporary;
        			temporary = model.CQ.copy();
        			model.CQscript.setMatrix(ii, ii+rowdim-1, jj, jj+coldim-1, temporary);
        		}
        	}
        }
        
        //GammaQ
       
        rowdim = model.AQ.getRowDimension();
        coldim = model.BQ.getColumnDimension();

        Matrix zeroMGQ;
        double[][] zeroM_iniGQ = new double[rowdim][coldim];
        zeroMGQ = new Matrix(zeroM_iniGQ);

        double[][] GammaQ_ini=new double[rowdim*N][coldim];
        model.GammaQ = new Matrix(GammaQ_ini);

        for (ii = 0; ii <= rowdim*N-rowdim; ii++){
        	 Matrix temporary;
        	 temporary = model.AQ.copy();
        	 if ((ii+rowdim)/rowdim == 1) {
        	 	 temporary = zeroMGQ;
        	 }
        	 if ((ii+rowdim)/rowdim == 2) {
        		 temporary = model.BQ;
        	 }
        	 if ((ii+rowdim)/rowdim > 2) {
        	   	 int hh;
        	   	 for (hh = 0; hh < (ii+rowdim)/rowdim-3; hh++){
        	   	     temporary = temporary.times(model.AQ);
        	   	 }
        	   	 temporary = temporary.times(model.BQ);
        	 }
        	 model.GammaQ.setMatrix(ii, ii + rowdim - 1, 0, coldim - 1, temporary);
        	 ii = ii + rowdim - 1;
        }
    
        // insulin transport
        
        //AIscript
        
        coldim = model.AI.getColumnDimension();
        rowdim = model.AI.getRowDimension();

        double[][] AIscript_ini=new double[rowdim*N][coldim];
        model.AIscript = new Matrix(AIscript_ini);

       
        for (ii = 0; ii <= rowdim*N-rowdim; ii++){
        	 Matrix temporary;
        	 temporary = model.AI.copy();
        	 if ((ii+rowdim)/rowdim == 1) {
        		 for (int i = 0; i < rowdim; i++) {
        			for(int j = 0; j < coldim; j++) {
        				int temp;
        				temp = (i == j) ? 1:0;
        				temporary.set(i, j, temp);
        			}
        		}
        	 }
        	 if ((ii+rowdim)/rowdim > 1) {
        	   	 int hh;
        	   	 for (hh = 0; hh < (ii+rowdim)/rowdim-2; hh++){
        	   	     temporary = temporary.times(model.AI);
        	   	 }
        	 }
        	 model.AIscript.setMatrix(ii, ii + rowdim - 1, 0, coldim - 1, temporary);
        	 ii = ii + rowdim - 1;
        }
        
        //BIscript
       
        coldim = model.BI.getColumnDimension();
        rowdim = model.BI.getRowDimension();

        double[][] BIscript_ini=new double[rowdim*N][coldim];
        model.BIscript = new Matrix(BIscript_ini);

        for (ii = 0; ii <= rowdim*N-rowdim; ii+=rowdim){
        	 Matrix temporary;
        	 temporary = model.BI.copy();
        	 model.BIscript.setMatrix(ii, ii + rowdim - 1, 0, coldim - 1, temporary);
        }
        
        //GammaI1
       
        rowdim = model.AI.getRowDimension();
        coldim = model.AI.getColumnDimension();

        Matrix zeroMI1;
        double[][] zeroM_iniI1 = new double[rowdim][coldim];
        zeroMI1 = new Matrix(zeroM_iniI1);

        double[][] GammaI1_ini=new double[rowdim*N][coldim*N];
        model.GammaI1 = new Matrix(GammaI1_ini);
       
        for (ii = 0; ii <= rowdim*N-rowdim; ii+=rowdim){
            int jj;
        	for (jj = 0; jj <= coldim*N-coldim; jj+=coldim){
        	    Matrix temporary;
        	    temporary = model.AI.copy();
        		if ( (ii+rowdim)/rowdim <= (jj+coldim)/coldim ) {
        		    temporary = temporary.times(zeroMI1);
        		}
        		if ( (ii+rowdim)/rowdim == (jj+coldim)/coldim + 1 ){
        		    for (int i = 0; i < rowdim; i++) {
        				for(int j = 0; j < coldim; j++) {
        					int temp;
        					temp = (i == j) ? 1:0;
        					temporary.set(i, j, temp);
        				}
        			}
        	    }
        		if ( (ii+rowdim)/rowdim > (jj+coldim)/coldim + 1 ) {
        			int hh;
        		   	for (hh = 0; hh < (ii+rowdim)/rowdim-(jj+coldim)/coldim-2; hh++){
        		   		temporary = temporary.times(model.AI);
        		   	}
        		}	 
        	 model.GammaI1.setMatrix(ii, ii+rowdim-1, jj, jj+coldim-1, temporary); 		 
        	 }
        }
        
        //GammaI2
        
        rowdim = model.AI.getRowDimension();
        coldim = model.BI.getColumnDimension();

        Matrix zeroMI2;
        double[][] zeroM_iniI2 = new double[rowdim][coldim];
        zeroMI2 = new Matrix(zeroM_iniI2);

        double[][] GammaI2_ini=new double[rowdim*N][coldim];
        model.GammaI2 = new Matrix(GammaI2_ini);

        for (ii = 0; ii <= rowdim*N-rowdim; ii++){
        	 Matrix temporary;
        	 temporary = model.AI.copy();
        	 if ((ii+rowdim)/rowdim == 1) {
        	 	 temporary = zeroMI2;
        	 }
        	 if ((ii+rowdim)/rowdim == 2) {
        		 temporary = model.BI;
        	 }
        	 if ((ii+rowdim)/rowdim > 2) {
        	   	 int hh;
        	   	 for (hh = 0; hh < (ii+rowdim)/rowdim-3; hh++){
        	   	     temporary = temporary.times(model.AI);
        	   	 }
        	   	 temporary = temporary.times(model.BI);
        	 }
        	 model.GammaI2.setMatrix(ii, ii + rowdim - 1, 0, coldim - 1, temporary);
        	 ii = ii + rowdim - 1;
        }
        
        // core model    
        
        // ACscript
        
        coldim = model.AC.getColumnDimension();
        rowdim = model.AC.getRowDimension();

        double[][] ACscript_ini=new double[rowdim*N][coldim];
        model.ACscript = new Matrix(ACscript_ini);

        for (ii = 0; ii <= rowdim*N-rowdim; ii++){
        	 Matrix temporary;
        	 temporary = model.AC.copy();
        	 if ((ii+rowdim)/rowdim > 1) {
        	   	 int hh;
        	   	 for (hh = 0; hh < (ii+rowdim)/rowdim-1; hh++){
        	   	     temporary = temporary.times(model.AC);
        	   	 }
        	 }
        	 model.ACscript.setMatrix(ii, ii + rowdim - 1, 0, coldim - 1, temporary);
        	 ii = ii + rowdim - 1;
        }

        //BCI2script
        
        coldim = model.BCI2.getColumnDimension();
        rowdim = model.BCI2.getRowDimension();

        double[][] BCI2script_ini=new double[rowdim*N][coldim];
        model.BCI2script = new Matrix(BCI2script_ini);

        for (ii = 0; ii <= rowdim*N-rowdim; ii+=rowdim){
        	 Matrix temporary;
        	 temporary = model.BCI2.copy();
        	 model.BCI2script.setMatrix(ii, ii + rowdim - 1, 0, coldim - 1, temporary);
        }
                
        //GammaC1
        
        rowdim = model.AC.getRowDimension();
        coldim = model.BCQ.getColumnDimension();

        Matrix zeroMC1;
        double[][] zeroM_iniC1 = new double[rowdim][coldim];
        zeroMC1 = new Matrix(zeroM_iniC1);

        double[][] GammaC1_ini=new double[rowdim*N][coldim*N];
        model.GammaC1 = new Matrix(GammaC1_ini);
        
        for (ii = 0; ii <= rowdim*N-rowdim; ii+=rowdim){
            int jj;
        	for (jj = 0; jj <= coldim*N-coldim; jj+=coldim){
        	    Matrix temporary;
        		temporary = model.AC.copy();
        		if (ii < jj/coldim*rowdim) {
        		    temporary = temporary.times(zeroMC1);
        		}
        		if (ii == jj/coldim*rowdim) {
        		    temporary = model.BCQ.copy(); 
                }
        		if (ii > jj/coldim*rowdim) {
        			if ((ii+rowdim)/rowdim-jj/coldim-2 > 0){
        		  		int hh;
        			   	for (hh = 0; hh < (ii+rowdim)/rowdim-jj/coldim-2; hh++){
        			   		temporary = temporary.times(model.AC);
        			   	}
        		 	}
        		   	temporary = temporary.times(model.BCQ);
        		 }	 
        	 model.GammaC1.setMatrix(ii, ii+rowdim-1, jj, jj+coldim-1, temporary); 		 
        	 }
        }
        
        //GammaC2
        
        rowdim = model.AC.getRowDimension();
        coldim = model.CI.getColumnDimension();

        Matrix zeroMC2;
        double[][] zeroM_iniC2 = new double[rowdim][coldim];
        zeroMC2 = new Matrix(zeroM_iniC2);

        double[][] GammaC2_ini=new double[rowdim*N][coldim*N];
        model.GammaC2 = new Matrix(GammaC2_ini);
       
        for (ii = 0; ii <= rowdim*N-rowdim; ii+=rowdim){
            int jj;
        	for (jj = 0; jj <= coldim*N-coldim; jj+=coldim){
        	    Matrix temporary;
        		temporary = model.AC.copy();
        		if ( (ii+rowdim)/rowdim < (jj+coldim)/coldim ) {
        		    temporary = temporary.times(zeroMC2);
        		}
        		if ( (ii+rowdim)/rowdim == (jj+coldim)/coldim ) {
        		    temporary = model.BCI1.copy();
        		    temporary = temporary.times(model.CI);
                }
        		if ( (ii+rowdim)/rowdim > (jj+coldim)/coldim ) {
        			int hh;
        		   	for (hh = 0; hh < (ii+rowdim)/rowdim-(jj+coldim)/coldim-1; hh++){
        		   		temporary = temporary.times(model.AC);
        		   	}
        		 	temporary = temporary.times(model.BCI1);
        		   	temporary = temporary.times(model.CI);
        		 }	 
        	 model.GammaC2.setMatrix(ii, ii+rowdim-1, jj, jj+coldim-1, temporary); 		 
        	 }
        }
     
        //GammaC3
     
        rowdim = model.AC.getRowDimension();
        coldim = model.AC.getColumnDimension();

        Matrix zeroMC3;
        double[][] zeroM_iniC3 = new double[rowdim][coldim];
        zeroMC3 = new Matrix(zeroM_iniC3);

        double[][] GammaC3_ini=new double[rowdim*N][coldim*N];
        model.GammaC3 = new Matrix(GammaC3_ini);

        for (ii = 0; ii <= rowdim*N-rowdim; ii+=rowdim){
            int jj;
        	for (jj = 0; jj <= coldim*N-coldim; jj+=coldim){
        	    Matrix temporary;
        	    temporary = model.AC.copy();
        		if ( (ii+rowdim)/rowdim < (jj+coldim)/coldim ) {
        		    temporary = temporary.times(zeroMC3);
        		}
        		if ( (ii+rowdim)/rowdim == (jj+coldim)/coldim ){
        		    for (int i = 0; i < rowdim; i++) {
        				for(int j = 0; j < coldim; j++) {
        					int temp;
        					temp = (i == j) ? 1:0;
        					temporary.set(i, j, temp);
        				}
        			}
        	    }
        		if ( (ii+rowdim)/rowdim > (jj+coldim)/coldim ) {
        			int hh;
        		   	for (hh = 0; hh < (ii+rowdim)/rowdim-(jj+coldim)/coldim-1; hh++){
        		   		temporary = temporary.times(model.AC);
        		   	}
        		}	 
        	 model.GammaC3.setMatrix(ii, ii+rowdim-1, jj, jj+coldim-1, temporary); 		 
        	 }
        }

		debug_message(MYTAG, "script matrix construction end");

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

