package com.paul;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import edu.virginia.dtc.Tvector.Tvector;
import Jama.*;

public class OptCORR_processing_Phase12 {

    // other parameters
	public double delta = -115.1700;
	Matrix delta_tilde = new Matrix(72, 1, delta);
	Matrix Ustartemp;

    
	// u_LA generation storage
	public Matrix u_LA_2lastdays;
    	
 // KF estimate from the Simulator (1st meal)
 	double[][] x_ini = {{9.666672961387889e-01},
					    {5.395372645858502e-04},
					    {1.890807473827476e+02},
					    {1.125582688460508e+02},
					    {8.543562871959665e+00},
					    {1.123329427067773e+00},
					    {6.880088106873392e+01},
					    {9.733851922892014e+02}};
 	Matrix x = new Matrix(x_ini);
 	
			
	public OptCORR_processing_Phase12(OptCORR_param optcorr_param) {
    	
	 	// u_LA vector from the Simulator (1st meal)
	 	double[][] u_LA_2lastdays_ini = {{2.261112556636594e+01, 2.240549095179500e+01, 2.220014402425656e+01, 2.199508438127054e+01, 2.179031162092002e+01, 2.158582534185031e+01, 2.138162514326822e+01, 2.117771062494128e+01, 2.097408138719702e+01, 2.077073703092204e+01, 2.056767715756132e+01, 2.036490136911747e+01, 2.016240926814989e+01, 1.996020045777396e+01, 1.975827454166039e+01, 1.955663112403428e+01, 1.935526980967449e+01, 1.915419020391278e+01, 1.895339191263304e+01, 1.875287454227058e+01, 1.855263769981125e+01, 1.835268099279078e+01, 1.815300402929404e+01, 1.795360641795405e+01, 1.775448776795145e+01, 1.755564768901353e+01, 1.735708579141383e+01, 1.715880168597085e+01, 1.696079498404773e+01, 1.676306529755125e+01, 1.656561223893117e+01, 1.636843542117944e+01, 1.617153445782943e+01, 1.597490896295515e+01, 1.577855855117061e+01, 1.558248283762897e+01, 1.538668143802170e+01, 1.519115396857801e+01, 1.499590004606404e+01, 1.480091928778201e+01, 1.460621131156952e+01, 1.441177573579894e+01, 1.421761217937650e+01, 1.402372026174152e+01, 1.383009960286580e+01, 1.363674982325280e+01, 1.344367054393686e+01, 1.325086138648249e+01, 1.305832197298379e+01, 1.286605192606334e+01, 1.267405086887187e+01, 1.248231842508718e+01, 1.229085421891366e+01, 1.209965787508139e+01, 1.190872901884546e+01, 1.171806727598529e+01, 1.152767227280378e+01, 1.133754363612667e+01, 1.114768099330178e+01, 1.095808397219821e+01, 1.076875220120581e+01, 1.057968530923422e+01, 1.039088292571226e+01, 1.020234468058721e+01, 1.001407020432405e+01, 9.826059127904774e+00, 9.638311082827560e+00, 9.450825701106211e+00, 9.263602615269306e+00, 9.076641458359536e+00, 8.889941863932993e+00, 8.703503466058429e+00}};
	 	Matrix pre_u_LA_2lastdays = new Matrix(u_LA_2lastdays_ini);
	 	u_LA_2lastdays = pre_u_LA_2lastdays.transpose();

        // advised bolus calculation    
        cal_bolus_advised(optcorr_param);
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
	    
//		int ii = 0;
//		int jj = 0;
//		double temp;
//		String elStr;
//		String rowStr = " ";
//		
//		for (ii = 0; ii <= M.getRowDimension()-1; ii++) {
//			for (jj = 0; jj <= M.getColumnDimension()-1; jj++){
//				temp = M.get(ii,jj);
//				rowStr = rowStr.concat(Double.toString(temp));
//				rowStr = rowStr.concat("           ");
//			}
//			System.out.println(rowStr);
//			rowStr = " ";
//		}
	    
	    Ustartemp = K.times(x).plus(M.times(u_LA_2lastdays)).plus(N.times(delta_tilde)); // U
	}
	
}
	