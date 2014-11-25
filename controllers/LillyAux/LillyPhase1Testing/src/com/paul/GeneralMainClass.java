package com.paul;

import java.text.DecimalFormat;

import com.paul.OptCORR_param;
import com.paul.OptCORR_processing_Phase11;
import com.paul.OptCORR_processing_Phase12;
import com.paul.OptCORR_processing_Phase13;
import com.paul.OptCORR_processing_Phase14;
import com.paul.OptCORR_processing_Phase15;
import edu.virginia.dtc.Tvector.Tvector;

public class GeneralMainClass {
	
	public static void main(String[] args) {
		Tvector USER_CR;
//		OptCORR_processing_Phase11 optcorr_bolus_calculation;
//		OptCORR_processing_Phase12 optcorr_bolus_calculation;
//		OptCORR_processing_Phase13 optcorr_bolus_calculation;
//		OptCORR_processing_Phase14 optcorr_bolus_calculation;
		OptCORR_processing_Phase15 optcorr_bolus_calculation;
		USER_CR = new Tvector();
		USER_CR.put(1, 0.0555);
		
		double USER_WEIGHT;
		USER_WEIGHT = 88.0024;
		OptCORR_param optcorr_param;
		
		// OptCORR_param returns hue matrices: Ascript, Bscript, B0script, Cscript, Rscript, Qscript
		optcorr_param = new OptCORR_param(USER_CR, USER_WEIGHT);
		optcorr_bolus_calculation = new OptCORR_processing_Phase15(optcorr_param);
		
		

			
//		int ii = 0;
//		int jj = 0;
//		double temp;
//		String elStr;
//		String rowStr = " ";
//		
//		for (ii = 0; ii <= optcorr_param.model.B.getRowDimension()-1; ii++) {
//			for (jj = 0; jj <= optcorr_param.model.B.getColumnDimension()-1; jj++){
//				temp = optcorr_param.model.B.get(ii,jj);
//				rowStr = rowStr.concat(Double.toString(temp));
//				rowStr = rowStr.concat("           ");
//			}
//			System.out.println(rowStr);
//			rowStr = " ";
//		}
		

		
//		int ii = 0;
//		int jj = 0;
//		double temp;
//		String elStr;
//		String rowStr = " ";
//		
//		for (ii = 0; ii <= optcorr_param.model.Bscript.getRowDimension()-1; ii++) {
//			for (jj = 0; jj <= optcorr_param.model.Bscript.getColumnDimension()-1; jj++){
//				temp = optcorr_param.model.Bscript.get(ii,jj);
//				rowStr = rowStr.concat(Double.toString(temp));
//				rowStr = rowStr.concat("           ");
//			}
//			System.out.println(rowStr);
//			rowStr = " ";
//		}
		
		
		int ii = 0;
		int jj = 0;
		double temp;
		String elStr;
		String rowStr = " ";
		
		for (ii = 0; ii <= optcorr_bolus_calculation.Est_states.getRowDimension()-1; ii++) {
			for (jj = 0; jj <= optcorr_bolus_calculation.Est_states.getColumnDimension()-1; jj++){
				temp = optcorr_bolus_calculation.Est_states.get(ii,jj);
				DecimalFormat df = new DecimalFormat("#.####");
				elStr = df.format(temp);
				rowStr = rowStr.concat(elStr);
				rowStr = rowStr.concat("           ");
			}
			System.out.println(rowStr);
			rowStr = " ";
		}

//		System.out.println(optcorr_bolus_calculation.u_LA_KF_2lastdays.getRowDimension());
//		System.out.println(optcorr_bolus_calculation.u_LA_KF_2lastdays.getColumnDimension());
//	
		
		
//		int ii = 0;
//		int jj = 0;
//		double temp;
//		String elStr;
//		String rowStr = " ";
//		
//		for (ii = 0; ii <= optcorr_bolus_calculation.Est_states.getRowDimension()-1; ii++) {
//			for (jj = 0; jj <= optcorr_bolus_calculation.Est_states.getColumnDimension()-1; jj++){
//				temp = optcorr_bolus_calculation.Est_states.get(ii,jj);
//				DecimalFormat df = new DecimalFormat("#.####");
//				elStr = df.format(temp);
//				rowStr = rowStr.concat(elStr);
//				rowStr = rowStr.concat("           ");
//			}
//			System.out.println(rowStr);
//			rowStr = " ";
//		}

		
//		System.out.println(optcorr_bolus_calculation.Ustartemp.getRowDimension());
//		System.out.println(optcorr_bolus_calculation.Ustartemp.getColumnDimension());	
		System.out.println(optcorr_bolus_calculation.Ustartemp.trace());
		
//		int ii = 0;
//		int jj = 0;
//		double temp;	
//		String elStr;
//		String rowStr = " ";
//		
//		for (ii = 0; ii <= optcorr_bolus_calculation.Est_states.getRowDimension()-1; ii++) {
//			for (jj = 0; jj <= optcorr_bolus_calculation.Est_states.getColumnDimension()-1; jj++){
//				temp = optcorr_bolus_calculation.Est_states.get(ii,jj);
//				DecimalFormat df = new DecimalFormat("#.####");
//				elStr = df.format(temp);
//				rowStr = rowStr.concat(elStr);
//				rowStr = rowStr.concat("           ");
//			}
//			System.out.println(rowStr);
//			rowStr = " ";
//		}



	}

}
