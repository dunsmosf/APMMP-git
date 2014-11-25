package edu.virginia.dtc.MCMservice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import edu.virginia.dtc.Tvector.Tvector;

import android.util.Log;
import android.widget.Toast;
import Jama.*;

public class OptCORR_processing {

	public static final String TAG = "MCMservice";
	public static final boolean DEBUG = true;
	
	public double[][] Mbuf_ini = {{0.0}, {0.0}};
	public double[][] Mstate_ini = {{0.0}, {0.0}};
	public double[][] Ibuf_ini = {{0.0}, {0.0}};
	public double[][] Istate_ini = {{0.0}, {0.0}, {0.0}};
	public double[][] KFstate_ini = {{0.0}, {0.0}};
	public double[][] Xstate_ini = {{0.0}};
	
	double[][] M_init_2x1 = {{0.0}, {0.0}};
	double[][] M_init_3x1 = {{0.0}, {0.0}, {0.0}};
	double[][] M_init_4x1 = {{0.0}, {0.0}, {0.0}, {0.0}};
	double[][] dTilde_init_48x1 = new double[48][1];
	
	double[][] phi_init_1x1={{0.0}};
	double[][] theta1_ini_1x3=new double[1][3];
	double[][] theta2_ini_1x48=new double[1][48];
	double[][] theta3_ini_1x3={{0.0,0.0,0.0}};
	double[][] theta4_ini_1x1= {{0.0}};
	double[][] theta5_ini_1x1= {{0.0}};
    //double[][] Q_ini_144x144=new double[144][144];
		
	// Gut system storage
    Matrix Mbuf = new Matrix(Mbuf_ini);
	double Mbuf_out = 0.0;
	Matrix Mstate = new Matrix(Mstate_ini);
	Matrix Mout = new Matrix(M_init_2x1);
	//Matrix Ms_out = new Matrix(M_init_2x1);
	double Rak = 0.0;  // into KF
	
	// Insulin system storage
	Matrix Ibuf = new Matrix(Ibuf_ini);
	double Ibuf_out = 0.0;
	Matrix Istate = new Matrix(Istate_ini);
	Matrix Iout = new Matrix(M_init_3x1);
	Matrix Is_out = new Matrix(M_init_3x1);
	double Ik = 0.0; // into KF
	Matrix Is_out_temp;
	
	// X system
	double Xout = 0.0;
	Matrix Xstate = new Matrix(Xstate_ini);
		
	// KF system storage
	Matrix KFstate = new Matrix(KFstate_ini);
	Matrix KFout = new Matrix(M_init_3x1);
	Matrix KFu = new Matrix(M_init_3x1);
	
	// kF output storage
	public double[][] Est_states=new double[144][2];
	public double[] Est_states_BG=new double[144];
	public double[] Est_states_logX=new double[144];
    
    // u* calculation
    public double Ustar;
    //public int CGM_data_counts;
	public double delta;
	Matrix startingstates=new Matrix(M_init_3x1);
	Matrix dTilde = new Matrix(dTilde_init_48x1);
	Matrix phi = new Matrix(phi_init_1x1);
	Matrix theta1 = new Matrix(theta1_ini_1x3);
	Matrix theta2 = new Matrix(theta2_ini_1x48);
	Matrix theta3 = new Matrix(theta3_ini_1x3);
	Matrix theta4 = new Matrix(theta4_ini_1x1);
	Matrix theta5 = new Matrix(theta5_ini_1x1);
//	Matrix Q = new Matrix(Q_ini_144x144);
	Matrix Q_script;
	
	// current time
	public int timesincelastmeal;
    
	public double[] meal_array, CGM_array_value, insulin_array, CGM_fix;
	public long[] CGM_array_time;
	public int a1,a2,a3;
	
	public OptCORR_processing(OptCORR_param optcorr_param, Tvector meal, Tvector CGM, Tvector insulin, Long currentTimeseconds,Tvector Tvec_calibration) {
    	
		
		
    	// KF estimation
	    cal_KF_estimation(optcorr_param,meal,CGM,insulin,currentTimeseconds,Tvec_calibration);

        // advised bolus calculation    
        cal_bolus_advised(optcorr_param,meal,currentTimeseconds);
   }

	public void cal_KF_estimation(OptCORR_param optcorr_param, Tvector meal, Tvector CGM, Tvector insulin, Long currentTimeseconds, Tvector Tvec_calibration){

	        double currentTimemin=(double)currentTimeseconds/60.0;

	        int yy;
			// construct CGM_array from Tvec_CGM and do BUMPLESS FIX
		  
	        CGM_array_time=new long[144];
		    CGM_array_value=new double[144];
	        if (CGM.count()>=144){
	        	for (yy=0;yy<144;yy++){
			    	CGM_array_time[yy]=CGM.get_time(CGM.count()-144+yy);
			    	CGM_array_value[yy]=CGM.get_value(CGM.count()-144+yy);
				} 
	        }
	        else if(CGM.count()>0){
	        	for (yy=144-CGM.count();yy<144;yy++){
			    	CGM_array_time[yy]=CGM.get_time(yy-144+CGM.count());
			    	CGM_array_value[yy]=CGM.get_value(yy-144+CGM.count());
				} 
	            for(yy=0;yy<144-CGM.count();yy++){
	            	CGM_array_time[yy]=CGM.get_time(0)-(144-CGM.count()-yy)*5;
			    	CGM_array_value[yy]=CGM.get_value(0);
	            }	        
	        }
	        else {
	        	for (yy=0;yy<144;yy++){
			    	CGM_array_time[yy]=(long) (currentTimemin-(143-yy)*5);
			    	CGM_array_value[yy]=110;
				} 
	        }
	       	
	        CGM_fix = CGM_array_value;
		    bumpless(Tvec_calibration); // return CGM_fix
		    		    
		    // construct insulin_array from Tvec_insulin
		    
		    insulin_array=new double[144];
		   		    
		    if (insulin.count()>=144){
	        	for (yy=0;yy<144;yy++){
			    	insulin_array[yy]=insulin.get_value(insulin.count()-144+yy);
				} 
	        }
	        else if(insulin.count()>0){
	        	for (yy=144-insulin.count();yy<144;yy++){
			    	insulin_array[yy]=insulin.get_value(yy-144+insulin.count());
				} 
	            for(yy=0;yy<144-insulin.count();yy++){
//			    	insulin_array[yy]=insulin.get_value(0);
			    	insulin_array[yy]=optcorr_param.basal_avg*5/1000;  // from mU/min to U/5min
	            }	        
	        }
	        else {
	        	for (yy=0;yy<144;yy++){
			    	insulin_array[yy]=optcorr_param.basal_avg*5/1000;  // from mU/min to U/5min
				} 
	        }
		 	    		    
		    // construct meal_array from Tvec_meal
		                  
		    meal_array=new double[144];
		    for (yy=0;yy<144;yy++){
			   meal_array[yy]=0;
			}
	    
		    int numofmeals=meal.count();
		    int jj=0;
		    
		    if (numofmeals>=1) {
		    for (jj=0;jj<numofmeals;jj++){
	    	long timetemp=meal.get_time(jj);    	
//	    	int a1=144-Math.round((currentTime-timetemp)/5);
//	    	int a2=144-Math.round((currentTime-timetemp)/5)+1;
//	    	int a3=144-Math.round((currentTime-timetemp)/5)+2;
	    	
	    	a1=144-Math.round((currentTimeseconds/60-timetemp)/5);
	    	a2=144-Math.round((currentTimeseconds/60-timetemp)/5)+1;
	    	a3=144-Math.round((currentTimeseconds/60-timetemp)/5)+2;
	    
	    	if (a1>=0 && a1<=141) {
	    	meal_array[a1]=meal.get_value(jj)/15;
	    	meal_array[a2]=meal.get_value(jj)/15;
	    	meal_array[a3]=meal.get_value(jj)/15;
	        }
	      }
	    }
	
	    
// initial states of insulin model
			   Istate.set(0,0,optcorr_param.basal_avg/optcorr_param.model.Lkd);
			   Istate.set(1,0,optcorr_param.basal_avg/optcorr_param.model.Lkd);
			   Istate.set(2,0,optcorr_param.basal_avg/optcorr_param.model.Lkcl);

			   Ibuf.set(0,0,optcorr_param.basal_avg);
			   Ibuf.set(1,0,optcorr_param.basal_avg);
   


	  
	   int ii=0;
	   while (ii<144){
	//********************
	// Kalman Filter Loop
	//********************
	// 1. gut model update for a step
	
		Mbuf_out = optcorr_param.model.Buffer_C.times(Mbuf).plus(optcorr_param.model.Buffer_D.times(meal_array[ii]*1000)).trace();
		Mbuf = optcorr_param.model.Buffer_A.times(Mbuf).plus(optcorr_param.model.Buffer_B.times(meal_array[ii]*1000));
		
		Mout = optcorr_param.model.CQall.times(Mstate).plus(optcorr_param.model.DQ.times(Mbuf_out));
		Mstate = optcorr_param.model.AQ.times(Mstate).plus(optcorr_param.model.BQ.times(Mbuf_out));
		
		Rak = Mout.get(0, 0)*optcorr_param.model.CQ.get(0,0)+Mout.get(1, 0)*optcorr_param.model.CQ.get(0,1);
		
		
	
	
	
	// 2. insulin model update for a step
	 		
		Ibuf_out = optcorr_param.model.Buffer_C.times(Ibuf).plus(optcorr_param.model.Buffer_D.times(insulin_array[ii]*1000/5)).trace();
		Ibuf = optcorr_param.model.Buffer_A.times(Ibuf).plus(optcorr_param.model.Buffer_B.times(insulin_array[ii]*1000/5));
		
		Iout = optcorr_param.model.CIall.times(Istate).plus(optcorr_param.model.DI.times(Ibuf_out));
		Istate = optcorr_param.model.AI.times(Istate).plus(optcorr_param.model.BI.times(Ibuf_out));
		Ik = Iout.get(2, 0)/(optcorr_param.model.LBW*optcorr_param.model.LVI)-optcorr_param.model.LIb;
	
	// 3. X  model update for a step
		Xout = optcorr_param.model.CX.times(Xstate).plus(optcorr_param.model.DX.times(Ik)).trace();
		Xstate = optcorr_param.model.AX.times(Xstate).plus(optcorr_param.model.BX.times(Ik));
	
	// 4. KF estimate update for a step
			
			KFu.set(0, 0, Ik);
			KFu.set(1, 0, Rak/optcorr_param.model.LBW);
			KFu.set(2, 0, Math.log(CGM_fix[ii]/optcorr_param.model.LGb));
			KFout = optcorr_param.model.Ckf.times(KFstate).plus(optcorr_param.model.Dkf.times(KFu));
			KFstate = optcorr_param.model.Akf.times(KFstate).plus(optcorr_param.model.Bkf.times(KFu));
		
		    Est_states[ii][0]=KFout.get(1,0);
		    Est_states[ii][1]=KFout.get(2,0);
		
		    Est_states_BG[ii]=Math.exp(KFout.get(1,0))*optcorr_param.model.LGb;
		    debug_message(TAG, "BG state estimate " + Est_states_BG[143]);
		    Est_states_logX[ii]=KFout.get(2,0);
		    
		ii++;
 	}
   
	// construct inputs into U* calculation block
		Is_out.set(0, 0, Iout.get(0, 0));
		Is_out.set(1, 0, Iout.get(1, 0));
		Is_out.set(2, 0, Iout.get(2, 0));
		
		Is_out_temp=Is_out.copy();

	    delta=KFout.get(2,0)-Xout;
	    
	    startingstates.set(0, 0, KFout.get(1,0));
	    startingstates.set(1, 0, Xout);
	    startingstates.set(2, 0, delta);

	    dTilde=optcorr_param.model.CQscript.times(optcorr_param.model.AQscript.times(Mout).plus(optcorr_param.model.GammaQ.times(Mbuf_out))).times(1/optcorr_param.model.LBW);
   
   }

	public void cal_bolus_advised(OptCORR_param optcorr_param, Tvector meal, Long currentTimeseconds){
	  // public void cal_bolus_advised(OptCORR_param optcorr_param){

       if (meal.count()==0){
    	   timesincelastmeal=264; //min  
       }
       else {
	   timesincelastmeal=(int)(Math.round(currentTimeseconds/60)-meal.get_last_time()); // min
        if (timesincelastmeal>264) {
        	timesincelastmeal=264;
        }
       
       }

	  
// Q_scirpt construction based on timesincelastmeal

       int N = 48;

       double[][] Q_pool_ini=new double[3*N][264];
       Matrix Q_pool = new Matrix(Q_pool_ini);

       double[][] Q_script_ini=new double[3*N][3*N];
       Matrix Q_script = new Matrix(Q_script_ini);

       int RowDim;
       RowDim = Q_pool.getRowDimension();
       int ColDim;
       ColDim = Q_pool.getColumnDimension();

       double[] StairSteps = {6, 6, 4, 6, 4, 4};
       double[] StairIndex = {25, 73, 121, 169, 217, 265}; // last element is 1 more in value than original because there is not filling with 1's after the end

       for (int ColInd = 0; ColInd <= ColDim-1; ColInd++) {
           for (int RowInd = RowDim-(int)StairSteps[0]*3+1-1; RowInd <= RowDim-1; RowInd+=3) {
               Q_pool.set(RowInd, ColInd, 1);
       	}
       }

       for (int index = 0; index <= 5-1; index++){
       	//int index = 0; 
           for (int ColInd = (int)StairIndex[index]+1-1; ColInd < (int)StairIndex[index+1]-1-1; ColInd++) {
           	
           	double sum1 = 0;
           	for (int i=0; i <= index+1; i++)
           		sum1+=StairSteps[i];
           	double sum2 = 0;
           	for (int i=0; i <= index; i++)
           		sum2+=StairSteps[i];
           	
           	for (int RowInd = RowDim-(int)sum1*3+1-1; RowInd <= RowDim-(int)sum2*3-1; RowInd+=3) {
//           		double temporary = Math.pow(((int)StairIndex[index+1]-1-(int)StairIndex[index]),-1);
//                temporary = (ColInd+1-(int)StairIndex[index])*temporary;
//           		Q_pool.set(RowInd, ColInd,  temporary);
           		Q_pool.set(RowInd, ColInd,  (double)(ColInd+1-(int)StairIndex[index])/(double)((int)StairIndex[index+1]-1-(int)StairIndex[index]));
           	}
           }
           
           for (int ColInd = (int)StairIndex[index+1]-1; ColInd <= ColDim-1; ColInd++) {
           	
           	double sum1 = 0;
           	for (int i=0; i <= index+1; i++)
           		sum1+=StairSteps[i];
           	double sum2 = 0;
           	for (int i=0; i <= index; i++)
           		sum2+=StairSteps[i];
           	
           	for (int RowInd = RowDim-(int)sum1*3+1-1; RowInd <= RowDim-(int)sum2*3-1; RowInd+=3) {
           		Q_pool.set(RowInd, ColInd, 1);
           	}
           }
           
       }

       for (int DiagInd = 0; DiagInd <=3*N-1; DiagInd+=3) {
       	Q_script.set(DiagInd, DiagInd, Q_pool.get(DiagInd, timesincelastmeal-1));
       }

       // Ustar calculation
	  	   	  
	   phi=optcorr_param.model.GammaI2.transpose().times(optcorr_param.model.GammaC2.transpose()).times(Q_script).times(optcorr_param.model.GammaC2).times(optcorr_param.model.GammaI2);	 
	   theta1=optcorr_param.model.GammaI2.transpose().times(optcorr_param.model.GammaC2.transpose()).times(Q_script).times(optcorr_param.model.ACscript);
	   theta2=optcorr_param.model.GammaI2.transpose().times(optcorr_param.model.GammaC2.transpose()).times(Q_script).times(optcorr_param.model.GammaC1);
	   theta3=optcorr_param.model.GammaI2.transpose().times(optcorr_param.model.GammaC2.transpose()).times(Q_script).times(optcorr_param.model.GammaC2).times(optcorr_param.model.AIscript);
	   theta4=optcorr_param.model.GammaI2.transpose().times(optcorr_param.model.GammaC2.transpose()).times(Q_script).times(optcorr_param.model.GammaC2).times(optcorr_param.model.GammaI1).times(optcorr_param.model.BIscript);
	   theta5=optcorr_param.model.GammaI2.transpose().times(optcorr_param.model.GammaC2.transpose()).times(Q_script).times(optcorr_param.model.GammaC3).times(optcorr_param.model.BCI2script);

	   double Ustartemp;
       Ustartemp=phi.inverse().times(theta1.times(startingstates).plus(theta2.times(dTilde)).plus(theta3.times(Is_out_temp)).plus(theta4.times(optcorr_param.basal_avg)).plus(theta5)).uminus().trace()*5/1000;   // U
     
       debug_message(TAG, "Phi " + phi.trace());
  
       debug_message(TAG, "core state component " + phi.inverse().times(theta1.times(startingstates)).uminus().trace()/200 
    		   		+ " d_tilde component " + phi.inverse().times(theta2.times(dTilde)).uminus().trace()/200
    		   		+ " insulin component " + phi.inverse().times(theta3.times(Is_out_temp)).uminus().trace()/200
    		   		+ " basal component " + phi.inverse().times(theta4.times(optcorr_param.basal_avg)).uminus().trace()/200
    		   		+ " constant component " + phi.inverse().times(theta5).uminus().trace()/200);
       
       if (Ustartemp>6) {
    	  Ustar= 6;
       }
       else
    	  Ustar=Ustartemp;
       
       debug_message(TAG, "Ustartemp " + Ustartemp + " Ustar " + Ustar);
              
   }
  
	public void bumpless(Tvector Tvec_calibration) {
  
	 int ncal=Tvec_calibration.count();
  
	 if (ncal==0) {
	  CGM_fix=CGM_array_value;
     }else{
     
		     CGM_fix=CGM_array_value;	
		     
		     long backwindow=240; // min
		     long forwardwindow=50;
		     long slopewindow=20;
		     double alpha0=1;
			 
		     int ii;	 
		     for(ii=0;ii<ncal;ii++){
		    	 
		    	long caltime = Tvec_calibration.get_time(ii);
		    	double calvalue = Tvec_calibration.get_value(ii);
		    	
		    	if ((caltime-CGM_array_time[0]>=30)) 
		    		{
					   /////// get CGM slope
			
			                                 //	find indslope	 
					     int n=0;
					     int tt;
					         for(tt=0;tt<144;tt++){
					        	 if (CGM_array_time[tt]>(caltime-slopewindow) && CGM_array_time[tt]<=caltime){
					        		n=n+1;
					        	 }
					         }
					     int[] indslope=new int[n];
					     int temp=0;
						     for(tt=0;tt<144;tt++){
					        	 if (CGM_array_time[tt]>(caltime-slopewindow) && CGM_array_time[tt]<=caltime){
					        		indslope[temp]=tt;
					        		temp=temp+1;
					        	 }
					         }
			                                 //	find indslope end	     
					     
					     double mstar;
					     double[][] A={{0,0},{0,0}};
					     double[][] b={{0},{0}};
					     for(tt=0;tt<indslope.length;tt++){
					    	A[0][0]=A[0][0]+Math.pow(CGM_array_time[indslope[tt]],2); 
					        A[0][1]=A[0][1]+CGM_array_time[indslope[tt]];
					        A[1][0]=A[0][1];
					        A[1][1]=A[1][1]+1;
					        b[0][0]=b[0][0]+CGM_array_time[indslope[tt]]*CGM_fix[indslope[tt]];
					        b[1][0]=b[1][0]+CGM_fix[indslope[tt]];
					     }
					     double det=A[0][0]*A[1][1]-A[0][1]*A[1][0];
					     double Ainv00=A[1][1]/det;
					     double Ainv01=-A[0][1]/det;
					     mstar=Ainv00*b[0][0]+Ainv01*b[1][0];
					     
					  /////// back window
					     double taustar=caltime-CGM_array_time[indslope[indslope.length-1]];
					     double Gammastar=calvalue-(CGM_array_value[indslope[indslope.length-1]]+mstar*taustar);
					     double Deltastar= alpha0*Gammastar; 
	
		                                    //	find indback	 
					     n=0;
					   	     for(tt=0;tt<144;tt++){
					        	 if (CGM_array_time[tt]>(caltime-backwindow) && CGM_array_time[tt]<=caltime){
					        		n=n+1;
					        	 }
					         }
					     int[] indback=new int[n];
					     temp=0;
						     for(tt=0;tt<144;tt++){
					        	 if (CGM_array_time[tt]>(caltime-backwindow) && CGM_array_time[tt]<=caltime){
					        		indback[temp]=tt;
					        		temp=temp+1;
					        	 }
					         }
			                                //	find indback end	 
	     
					         int jj;
						     for(jj=0;jj<indback.length;jj++) {
						    
						    	 double tau = caltime-CGM_array_time[indback[jj]];
						    	 CGM_fix[indback[jj]]=CGM_fix[indback[jj]]+Deltastar/(1.0+Math.exp((tau-backwindow/2)/(backwindow/20)));
						     }
	
						     
					   /////// forward window
					                            //	find indforward	 
						     n=0;
						   	     for(tt=0;tt<144;tt++){
						        	 if (CGM_array_time[tt]>caltime && CGM_array_time[tt]<=(caltime+forwardwindow)){
						        		n=n+1;
						        	 }
						         }
						   	     
						   	 if (n!= 0) {    
						     int[] indforward=new int[n];
						     temp=0;
							     for(tt=0;tt<144;tt++){
						        	 if (CGM_array_time[tt]>caltime && CGM_array_time[tt]<=(caltime+forwardwindow)){
						        		indforward[temp]=tt;
						        		temp=temp+1;
						        	 }
						         }
				                                //	find indforward end	 
					     
					         for(jj=0;jj<indforward.length;jj++) {
					     
					            double tau=CGM_array_time[indforward[jj]]-caltime;
					            double Gammatau= calvalue+tau*mstar-CGM_fix[indforward[jj]];
					            double Deltatau=alpha0*Gammatau;
					            CGM_fix[indforward[jj]]=CGM_fix[indforward[jj]]+Deltatau/(1.0+Math.exp((tau-forwardwindow/2)/(forwardwindow/20)));
					         }
		     
						   	 } // end of (n!=0)
		    	} // end of if
		     } // end of ncal for loop
          
        } // end of else
  
   }
	
	private void debug_message(String tag, String message) {
		if (DEBUG) {
			Log.i(tag, message);
		}
	}
  
 }