package com.paul;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import edu.virginia.dtc.Tvector.Tvector;
import Jama.*;

public class OptCORR_processing_Phase15 {

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
    
    // average VBR calculation matrices
    
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
 	double[][] CGM_ini = {{228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}, {228.00}};
 	Matrix CGM = new Matrix(CGM_ini);
  	
	public OptCORR_processing_Phase15(OptCORR_param optcorr_param) {
    
		// average VBR calculation
		cal_ave_VBR(optcorr_param); 
		
		// u_LA generation
	    cal_u_LA_generation(optcorr_param);
	    
	    // u_LA tilde generation
	    cal_u_LA_tilde_generation(optcorr_param);
		
	 	// u_LA vector from the Simulator (1st meal)
//	 	double[][] u_LA_2lastdays_ini = {{2.261112556636594e+01}, {2.240549095179500e+01}, {2.220014402425656e+01}, {2.199508438127054e+01}, {2.179031162092002e+01}, {2.158582534185031e+01}, {2.138162514326822e+01}, {2.117771062494128e+01}, {2.097408138719702e+01}, {2.077073703092204e+01}, {2.056767715756132e+01}, {2.036490136911747e+01}, {2.016240926814989e+01}, {1.996020045777396e+01}, {1.975827454166039e+01}, {1.955663112403428e+01}, {1.935526980967449e+01}, {1.915419020391278e+01}, {1.895339191263304e+01}, {1.875287454227058e+01}, {1.855263769981125e+01}, {1.835268099279078e+01}, {1.815300402929404e+01}, {1.795360641795405e+01}, {1.775448776795145e+01}, {1.755564768901353e+01}, {1.735708579141383e+01}, {1.715880168597085e+01}, {1.696079498404773e+01}, {1.676306529755125e+01}, {1.656561223893117e+01}, {1.636843542117944e+01}, {1.617153445782943e+01}, {1.597490896295515e+01}, {1.577855855117061e+01}, {1.558248283762897e+01}, {1.538668143802170e+01}, {1.519115396857801e+01}, {1.499590004606404e+01}, {1.480091928778201e+01}, {1.460621131156952e+01}, {1.441177573579894e+01}, {1.421761217937650e+01}, {1.402372026174152e+01}, {1.383009960286580e+01}, {1.363674982325280e+01}, {1.344367054393686e+01}, {1.325086138648249e+01}, {1.305832197298379e+01}, {1.286605192606334e+01}, {1.267405086887187e+01}, {1.248231842508718e+01}, {1.229085421891366e+01}, {1.209965787508139e+01}, {1.190872901884546e+01}, {1.171806727598529e+01}, {1.152767227280378e+01}, {1.133754363612667e+01}, {1.114768099330178e+01}, {1.095808397219821e+01}, {1.076875220120581e+01}, {1.057968530923422e+01}, {1.039088292571226e+01}, {1.020234468058721e+01}, {1.001407020432405e+01}, {9.826059127904774e+00}, {9.638311082827560e+00}, {9.450825701106211e+00}, {9.263602615269306e+00}, {9.076641458359536e+00}, {8.889941863932993e+00}, {8.703503466058429e+00}};
//	 	u_LA_2lastdays = new Matrix(u_LA_2lastdays_ini);
	 	
	 	// u_LA KF vector from the Simulator (1st meal)
//	 	double[][] u_LA_KF_2lastdays_ini = {{-4.564142824253864e+00}, {-4.592561994164544e+00}, {-4.620942995078739e+00}, {-4.649284290392060e+00}, {-4.677585935653450e+00}, {5.318948198924357e+00}, {5.276700792488558e+00}, {5.234512491038611e+00}, {5.192383211885460e+00}, {5.150312872455675e+00}, {5.108301390291405e+00}, {5.066348683050116e+00}, {5.024454668504493e+00}, {4.982619264542247e+00}, {4.940842389165983e+00}, {4.899123960493007e+00}, {4.857463896755181e+00}, {4.815862116298781e+00}, {4.774318537584289e+00}, {4.732833079186285e+00}, {4.691405659793244e+00}, {4.650036198207427e+00}, {4.608724613344673e+00}, {4.567470824234260e+00}, {4.526274750018754e+00}};
//	 	u_LA_KF_2lastdays = new Matrix(u_LA_KF_2lastdays_ini);
	 		 	
		// KF estimation
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
		System.out.println(optcorr_param.SC0_init_cond);
		
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
		System.out.println(optcorr_param.VBR_mUperMin_average);
		
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
//			System.out.println(u_LA_gen.get(i,0));
		}
		VBR_gen_out.set(124, 0, (u_LA_FF_C*VBR_gen_state.get(i,0) + u_LA_FF_D*input));
		u_LA_gen.set(124, 0, VBR_gen_out.get(i,0)*0.00028*optcorr_param.model.BW/6 - optcorr_param.VBR_mUperMin_average);
//		System.out.println(u_LA_gen.get(124,0));
		
//		
//		int ii = 0;
//		int jj = 0;
//		double temp;
//		String elStr;
//		String rowStr = " ";
//		
//		for (ii = 0; ii <= u_LA_gen.getRowDimension()-1; ii++) {
//			for (jj = 0; jj <= u_LA_gen.getColumnDimension()-1; jj++){
//				temp = u_LA_gen.get(ii,jj);
//				DecimalFormat df = new DecimalFormat("#.#########");
//				elStr = df.format(temp);
//				rowStr = rowStr.concat(elStr);
//				rowStr = rowStr.concat("           ");
//			}
//			System.out.println(rowStr);
//			rowStr = " ";
//		}
//	
	}
	
	public void cal_KF_estimation(OptCORR_param optcorr_param, Matrix CGM){
		
		// Kalman Filter Loop 
		int ii;
		for (ii = 0; ii <= 124; ii+=5){
        	KFinput.set(0, 0, u_LA_gen.get(ii,0));
//        	System.out.println(u_LA_gen.get(ii,0));
			KFinput.set(1, 0, CGM.get(ii,0) - Gref);
//			System.out.println(CGM.get(ii,0) - Gref);
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
//		System.out.println(u_LA_gen.get(124,0));
//		System.out.println(u_LA_tilde_gen.get(0,0));
		int i;
		for (i = 0; i <= 70; i++){
			System.out.println(u_LA_tilde_gen.get(i,0));
			VBR_tilde_gen.set(i+1, 0, u_LA_Pred_A*VBR_tilde_gen.get(i,0));
			u_LA_tilde_gen.set(i+1, 0, VBR_tilde_gen.get(i+1,0)*0.00028*optcorr_param.model.BW/6 - optcorr_param.VBR_mUperMin_average);
		}
//		System.out.println(u_LA_tilde_gen.get(71,0));
				
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
	    
	}
	
	
}
	