package edu.virginia.dtc.MCMservice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import android.content.Context;

import edu.virginia.dtc.MCMservice.OptCORR_param;

import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.Tvector.Tvector;

import android.util.Log;
import android.widget.Toast;
import Jama.*;

public class OptCORR_processing {

	public static final String TAG = "MCMservice";
	public static final boolean DEBUG = true;
	
	// KF system storage
	double[][] KFinput_init_2x1 = {{0.0}, {0.0}};
	Matrix KFinput = new Matrix(KFinput_init_2x1);
	double[][] KFstate_init_8x1 = {{0.0}, {0.0}, {0.0}, {0.0}, {0.0}, {0.0}, {0.0}, {0.0}};
	Matrix KFstate = new Matrix(KFstate_init_8x1);
	double[][] KFoutput_init_9x1 = {{0.0}, {0.0}, {0.0}, {0.0}, {0.0}, {0.0}, {0.0}, {0.0}, {0.0}};
	Matrix KFoutput = new Matrix(KFoutput_init_9x1);
	
	// KF output storage
	public double[][] Est_states_init = new double[8][1];
	Matrix Est_states = new Matrix(Est_states_init);
	
	// other parameters
	public double delta = -115.1700;
	Matrix delta_tilde = new Matrix(72, 1, delta);
	public double Gref = 226.78;
	Matrix Ustartemp;
	double Ustar; 
	
	// VBR and u_LA generators 
	double[][] VBR_init_1440x1 = new double [1440][1];
	Matrix VBR = new Matrix(VBR_init_1440x1);
	
	double[][] VBR_gen_state_init_125x1 = new double [125][1];
	Matrix VBR_gen_state = new Matrix(VBR_gen_state_init_125x1);
	
	double[][] VBR_gen_out_init_125x1 = new double [125][1];
	Matrix VBR_gen_out = new Matrix(VBR_gen_out_init_125x1);
	
	double[][] u_LA_gen_init_125x1 = new double [125][1];
	Matrix u_LA_gen = new Matrix(u_LA_gen_init_125x1);
	
	double[][] VBR_tilde_gen_init_125x1 = new double [125][1];
	Matrix VBR_tilde_gen = new Matrix(VBR_tilde_gen_init_125x1);
		
	double[][] u_LA_tilde_gen_init_72x1 = new double [72][1];
	Matrix u_LA_tilde_gen = new Matrix(u_LA_tilde_gen_init_72x1);
	
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
	
    // average VBR calculation matrices, 1-min discretization
    double u_LA_FF_A = 9.997200391963417e-01;
    double u_LA_FF_B = 9.998600130657520e-01;
    double u_LA_FF_C = 1;
    double u_LA_FF_D = 0;
   
    // u_LA generation matrices
    double u_LA_Pred_A = 9.986009795428266e-01;
   
    // u_LA generation storage
    public Matrix u_LA_KF_2lastdays;
	public Matrix u_LA_2lastdays;
    
	// CGM vector from the Simulator (1st meal)
	
 	double[][] CGM_ini = {{228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00},
 						  {228.00}, {228.00}, {228.00}, {228.00}, {228.00}};
 	Matrix CGM = new Matrix(CGM_ini);
	  	
	public OptCORR_processing(OptCORR_param optcorr_param, Context context) {
		final String FUNC_TAG = "OptCORR_processing";
    	
//		Inputs inputs = new Inputs(620, 5, 110);
//		inputs.reconstruct(getCurrentTimeSeconds(), context);
//    	int ii;
//    	for (ii = 0; ii <= 124; ii+=5){
//        	Debug.i(TAG, FUNC_TAG, "CGM: "+inputs.CGM[ii]);
//		}
//    	
//    	Toast.makeText(context,	"SMBG: " + inputs.CGM.length, Toast.LENGTH_SHORT).show();
		
		// average VBR calculation
		cal_ave_VBR(optcorr_param); 
		
		// u_LA generation
	    cal_u_LA_generation(optcorr_param);
	    
	    // u_LA tilde generation
	    cal_u_LA_tilde_generation(optcorr_param);
		
	    // KF estimation
//	    cal_KF_estimation(optcorr_param, inputs.CGM);
	    cal_KF_estimation(optcorr_param, CGM);

        // advised bolus calculation    
	    cal_bolus_advised(optcorr_param);
   }

	

	private void cal_ave_VBR(OptCORR_param optcorr_param) {
		
		// U/min to pmol/kg/min
		double J = optcorr_param.u_LA_injection*6000/optcorr_param.model.BW;
		double input = 0;
		double n = 1440;
		double delta = 1440/n;
		// calculating initial condition
		optcorr_param.SC0_init_cond = (Math.pow(u_LA_FF_A,(n-1))*u_LA_FF_B*J)/(1-Math.pow(u_LA_FF_A,n));		
				
		// propagating the SC0 state for one day
		VBR.set(0, 0, optcorr_param.SC0_init_cond);
		int ii;
		for (ii = 0; ii < n-1; ii++){
			if (ii == 0) {
			        input = J;
			} else {
			        input = 0;
			}
	        VBR.set(ii+1, 0, u_LA_FF_A*VBR.get(ii,0) + u_LA_FF_B*input);
		}
		
		// calculating the average of the VBR
		double VBR_average = VBR.norm1()/1440;
		// mass preservation and to mU/min and 
		optcorr_param.VBR_mUperMin_average = VBR_average*0.00028*optcorr_param.model.BW/6;
				
	}

	public void  cal_u_LA_generation(OptCORR_param optcorr_param) {
		
		double J = optcorr_param.u_LA_injection*6000/optcorr_param.model.BW;
		double input = 0;
		
		// calculating u_LA up to the first meal
		VBR_gen_state.set(0, 0, optcorr_param.SC0_init_cond);
		int i;
		for (i = 0; i <= 123; i++){
			if (i == 1) {
			        input = J;
			} else {
			        input = 0;
			}
			VBR_gen_out.set(i, 0, (u_LA_FF_C*VBR_gen_state.get(i,0) + u_LA_FF_D*input));
			u_LA_gen.set(i, 0, VBR_gen_out.get(i,0)*0.00028*optcorr_param.model.BW/6 - optcorr_param.VBR_mUperMin_average);
			VBR_gen_state.set(i+1, 0, u_LA_FF_A*VBR_gen_state.get(i,0) + u_LA_FF_B*input);
		}
		VBR_gen_out.set(124, 0, (u_LA_FF_C*VBR_gen_state.get(i,0) + u_LA_FF_D*input));
		u_LA_gen.set(124, 0, VBR_gen_out.get(i,0)*0.00028*optcorr_param.model.BW/6 - optcorr_param.VBR_mUperMin_average);
	}
	
//	public void cal_KF_estimation(OptCORR_param optcorr_param, double[] CGM){
	public void cal_KF_estimation(OptCORR_param optcorr_param, Matrix CGM){
		// Kalman Filter Loop 
		int ii;
		for (ii = 0; ii <= 124; ii+=5){
        	KFinput.set(0, 0, u_LA_gen.get(ii,0));
//        	System.out.println(u_LA_gen.get(ii,0));
//			KFinput.set(1, 0, CGM[ii] - Gref);
			System.out.println(CGM.get(ii,0) - Gref);
			KFoutput = Ckf.times(KFstate).plus(Dkf.times(KFinput));
			KFstate = Akf.times(KFstate).plus(Bkf.times(KFinput));
			
		}
        
		Est_states.set(0,0, KFoutput.get(1,0));
		Est_states.set(1,0, KFoutput.get(2,0));
		Est_states.set(2,0, KFoutput.get(3,0));
		Est_states.set(3,0, KFoutput.get(4,0));
		Est_states.set(4,0, KFoutput.get(5,0));
		Est_states.set(5,0, KFoutput.get(6,0));
		Est_states.set(6,0, KFoutput.get(7,0));
		Est_states.set(7,0, KFoutput.get(8,0));
//		System.out.println(Est_states.getRowDimension());
//		System.out.println(Est_states.getColumnDimension());
		

	}
	
	private void cal_u_LA_tilde_generation(OptCORR_param optcorr_param) {

		// propagating u_LA for 72 steps
		VBR_tilde_gen.set(0, 0, VBR_gen_out.get(120,0));
		u_LA_tilde_gen.set(0, 0, VBR_tilde_gen.get(0, 0)*0.00028*optcorr_param.model.BW/6 - optcorr_param.VBR_mUperMin_average);
		int i;
		for (i = 0; i <= 70; i++){
			System.out.println(u_LA_tilde_gen.get(i,0));
			VBR_tilde_gen.set(i+1, 0, u_LA_Pred_A*VBR_tilde_gen.get(i,0));
			u_LA_tilde_gen.set(i+1, 0, VBR_tilde_gen.get(i+1,0)*0.00028*optcorr_param.model.BW/6 - optcorr_param.VBR_mUperMin_average);
		}
	}
	
	public void  cal_bolus_advised(OptCORR_param optcorr_param) {

		// u* formula initialization
		double[][] phi_init_1x1 = {{0.0}};
		Matrix phi = new Matrix(phi_init_1x1);
		
		double[][] K_init = new double[8][1];
		Matrix K = new Matrix(K_init);
		
		double[][] M_init = new double[1][72];
		Matrix M = new Matrix(M_init);
		
		double[][] N_init = new double[1][576];
		Matrix N = new Matrix(N_init);
		
		phi = optcorr_param.model.B0script.transpose().times(optcorr_param.model.Cscript.transpose()).times(optcorr_param.model.Qscript).times(optcorr_param.model.Cscript).times(optcorr_param.model.B0script).plus(optcorr_param.model.Rscript); 
	   			
		K = phi.inverse().uminus().times(optcorr_param.model.B0script.transpose()).times(optcorr_param.model.Cscript.transpose()).times(optcorr_param.model.Qscript).times(optcorr_param.model.Cscript).times(optcorr_param.model.Ascript);
        M = phi.inverse().uminus().times(optcorr_param.model.B0script.transpose()).times(optcorr_param.model.Cscript.transpose()).times(optcorr_param.model.Qscript).times(optcorr_param.model.Cscript).times(optcorr_param.model.Bscript);
	    N = phi.inverse().times(optcorr_param.model.B0script.transpose()).times(optcorr_param.model.Cscript.transpose()).times(optcorr_param.model.Qscript);
	    
	    Ustartemp = K.times(Est_states).plus(M.times(u_LA_tilde_gen)).plus(N.times(delta_tilde)); // U
	    Ustar = Ustartemp.trace();
	}
  	
	private void debug_message(String tag, String message) {
		if (DEBUG) {
			Log.i(tag, message);
		}
	}
	
	public long getCurrentTimeSeconds() 
	{
		return System.currentTimeMillis()/1000;			// Seconds since 1/1/1970 in UTC
	}
  
 }