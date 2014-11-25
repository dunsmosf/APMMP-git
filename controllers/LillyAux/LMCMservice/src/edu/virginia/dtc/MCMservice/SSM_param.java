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

public class SSM_param {
	public IOB_param iob_param;
	public Filter filter;
	public SSM_model model;
	public double target;
	public Tvector basal;
	public SSM_flag_param flag_param;
	public SSM_hyper_alarm hyper_alarm;
	public SSM_hypo_alarm hypo_alarm;
	public final double k_1 = 0.0173;
	public final double k_2 = 0.0116;
	public final double k_3 = 6.75;

	public SSM_param(int AIT, Tvector subject_basal, Tvector CR, Tvector CF, double TDI, double BW) {
		// TODO Auto-generated constructor stub
		iob_param = new IOB_param(AIT*12, 96);
		filter = new Filter();
		model = new SSM_model();
		basal = subject_basal;				// Use the basal profile passed to us
		flag_param = new SSM_flag_param();
		hyper_alarm = new SSM_hyper_alarm();
		hypo_alarm = new SSM_hypo_alarm();
		initSSM_param(AIT, basal, CR, CF, TDI, BW);
	}

    public void initSSM_param(int AIT, Tvector basal, Tvector CR, Tvector CF, double TDI, double BW) {
    	double insulin_curve_mins[];
		int ii;
    	switch(AIT) {
    	case 2:
        	insulin_curve_mins = new double[120];
    		for (ii=0; ii<120; ii++) {
    			insulin_curve_mins[ii] = 1-.25*((-k_3/(k_2*(k_1-k_2))*(Math.exp(-k_2*(ii+1)/.25)-1)+k_3/(k_1*(k_1-k_2))*(Math.exp(-k_1*(ii+1)/.25)-1))/(8.3158e3));
    		}
    		for (ii=0; ii<24; ii++) {
    			iob_param.curves[ii] =insulin_curve_mins[(ii+1)*5-1];
    		}
    		iob_param.alpha=1.5187;
    		iob_param.beta=-0.5781;
    		for (ii=0; ii<24; ii++) {
    			iob_param.curves_nonrecursive[95-ii] = iob_param.curves[ii];
    		}
    		for (ii=24; ii<96; ii++) {
    			iob_param.curves_nonrecursive[95-ii] = 0;
    		}
    		break;
    	case 4:
        	insulin_curve_mins = new double[240];
    		for (ii=0; ii<240; ii++) {
    			insulin_curve_mins[ii] = 1-.5*((-k_3/(k_2*(k_1-k_2))*(Math.exp(-k_2*(ii+1)/.5)-1)+k_3/(k_1*(k_1-k_2))*(Math.exp(-k_1*(ii+1)/.5)-1))/(1.6631e4));
    				}
    		for (ii=0; ii<48; ii++) {
    			iob_param.curves[ii] =insulin_curve_mins[(ii+1)*5-1];
    		}
    		iob_param.alpha=1.7394;
    		iob_param.beta=-0.7566;
    		for (ii=0; ii<48; ii++) {
    			iob_param.curves_nonrecursive[95-ii] = iob_param.curves[ii];
    		}
    		for (ii=48; ii<96; ii++) {
    			iob_param.curves_nonrecursive[95-ii] = 0;
    		}
    		break;
    	case 6:
        	insulin_curve_mins = new double[360];
    		for (ii=0; ii<360; ii++) {
    			insulin_curve_mins[ii] = 1-.75*((-k_3/(k_2*(k_1-k_2))*(Math.exp(-k_2*(ii+1)/.75)-1)+k_3/(k_1*(k_1-k_2))*(Math.exp(-k_1*(ii+1)/.75)-1))/(2.4947e4));
    		}
    		for (ii=0; ii<72; ii++) {
    			iob_param.curves[ii] =insulin_curve_mins[(ii+1)*5-1];
    		}
    		iob_param.alpha=1.8215;
    		iob_param.beta=-0.8296;
    		for (ii=0; ii<72; ii++) {
    			iob_param.curves_nonrecursive[95-ii] = iob_param.curves[ii];
    		}
    		for (ii=72; ii<96; ii++) {
    			iob_param.curves_nonrecursive[95-ii] = 0;
    		}
    		break;
    	case 8:
        	insulin_curve_mins = new double[480];
    		for (ii=0; ii<480; ii++) {
    			insulin_curve_mins[ii] = 1-((-k_3/(k_2*(k_1-k_2))*(Math.exp(-k_2*(ii+1))-1)+k_3/ (k_1*(k_1-k_2))*(Math.exp(-k_1*(ii+1))-1))/(3.3263e4));
    		}
    		for (ii=0; ii<96; ii++) {
    			iob_param.curves[ii] =insulin_curve_mins[(ii+1)*5-1];
    		}
    		iob_param.alpha=1.8644;
    		iob_param.beta=-0.8690;
    		for (ii=0; ii<96; ii++) {
    			iob_param.curves_nonrecursive[95-ii] = iob_param.curves[ii];
    		}
    		break;
    	default:
    		break;
    	}
    	
    	iob_param.fourcurves = new double[48];
    	iob_param.sixcurves = new double[72];
    	iob_param.eightcurves = new double[96];
    	iob_param.fourcurves_nonrecursive = new double[96];
    	iob_param.sixcurves_nonrecursive = new double[96];
    	iob_param.eightcurves_nonrecursive = new double[96];
    	double insulin_4curve_mins[];
    	insulin_4curve_mins = new double[240];
    	    		for (ii=0; ii<240; ii++) {
    	    			insulin_4curve_mins[ii] = 1-.5*((-k_3/(k_2*(k_1-k_2))*(Math.exp(-k_2*(ii+1)/.5)-1)+k_3/(k_1*(k_1-k_2))*(Math.exp(-k_1*(ii+1)/.5)-1))/(1.6631e4));
    	    				}
    	    		for (ii=0; ii<48; ii++) {
    	    			iob_param.fourcurves[ii]=insulin_4curve_mins[(ii+1)*5-1];
    	    		}
    	    		
    	    		for (ii=0; ii<48; ii++) {
    	iob_param.fourcurves_nonrecursive[95-ii] = iob_param.fourcurves[ii];
    	    		}
    	for (ii=48; ii<96; ii++) {
    	    			iob_param.fourcurves_nonrecursive[95-ii] = 0;
    	    		}

    	double insulin_6curve_mins[];
    	insulin_6curve_mins = new double[360];
    	    		for (ii=0; ii<360; ii++) {
    	    			insulin_6curve_mins[ii] = 1-.75*((-k_3/(k_2*(k_1-k_2))*(Math.exp(-k_2*(ii+1)/.75)-1)+k_3/(k_1*(k_1-k_2))*(Math.exp(-k_1*(ii+1)/.75)-1))/(2.4947e4));
    	    		}
    	    		for (ii=0; ii<72; ii++) {
    	    			iob_param.sixcurves[ii] =insulin_6curve_mins[(ii+1)*5-1];
    	    		}
    	    		
    	    		for (ii=0; ii<72; ii++) {
    	    			iob_param.sixcurves_nonrecursive[95-ii] = iob_param.sixcurves[ii];
    	    		}
    	    		for (ii=72; ii<96; ii++) {
    	    			iob_param.sixcurves_nonrecursive[95-ii] = 0;
    	    		}

    	double insulin_8curve_mins[];
    	insulin_8curve_mins = new double[480];
    	    		for (ii=0; ii<480; ii++) {
    	    			insulin_8curve_mins[ii] = 1-((-k_3/(k_2*(k_1-k_2))*(Math.exp(-k_2*(ii+1))-1)+k_3/ (k_1*(k_1-k_2))*(Math.exp(-k_1*(ii+1))-1))/(3.3263e4));
    	    		}
    	    		for (ii=0; ii<96; ii++) {
    	    			iob_param.eightcurves[ii] =insulin_8curve_mins[(ii+1)*5-1];
    	    		}
    	    		
    	    		for (ii=0; ii<96; ii++) {
    	    			iob_param.eightcurves_nonrecursive[95-ii] = iob_param.eightcurves[ii];
    	    		}
    

    	// target glucose level
    	target = 112.5;
    	// Additional iob_param elements
    	iob_param.target=112.5;
    	iob_param.hist_length=8*60;
    	// filter
    	filter.alpha = 0.5;
    	filter.width=45;
    	filter.bolus_max=6;
    	// model
    	model.KBW =  79.675011695299970;
    	model.KGEZI = 0.022952300000000;
    	model.KGb = 112.50000;
    	model.KIb = 23.241662330704127;
    	model.KSG = 0.008734715834658;
    	model.KSI = 2.292679226469605e-04;
    	model.KVg = 2.853542413752443;
    	model.KVi = 0.060050826000000;
    	model.Kf = 0.900000000000001;
    	model.Kid = 50.5;
    	model.Kkabs = 0.010966264413841;
    	model.Kkcl = 0.237947091272969;
    	model.Kkd = 0.020433693000000;
    	model.Kksc = 0.090883397000000;
    	model.Kktau = 0.096139935268239;
    	model.Kp2 = 0.175545038809223;
    	model.Ktaumeal = 0.0050000000;
    	// Set up some working variables
    	double opG = target;
    	double opGsc = opG;
    	double opX = model.KSG*model.KGb/opG-model.KSG;
    	double opIp=(opX/model.KSI+model.KIb)*model.KVi*model.KBW;
    	double opIsc2=model.Kkcl*opIp/model.Kkd;
    	double opIsc1=opIsc2;
    	double opbasal=(model.Kkd*opIsc1);
    	double opQ1=0;
    	double opQ2=0;
    	model.Ac = new double[][] {
    		{-(model.KSG+opX), -opG, 0, 0, 0, 0, 0, model.Kkabs*model.Kf/(model.KVg*model.KBW)},
    		{0, -model.Kp2, 0, 0, model.Kp2*model.KSI/(model.KVi*model.KBW), 0, 0, 0},
    		{0, 0, -model.Kkd, 0, 0, 0, 0, 0},
    		{0, 0, model.Kkd, -model.Kkd, 0, 0, 0, 0},
    		{0, 0, 0, model.Kkd, -model.Kkcl, 0, 0, 0},
    		{model.Kksc, 0, 0, 0, 0, -model.Kksc, 0, 0},
    		{0, 0, 0, 0, 0, 0, -model.Kktau, 0},
    		{0, 0, 0, 0, 0, 0, model.Kktau, -model.Kkabs}
    	};
    	model.Bc = new double[] {0, 0, 1, 0, 0, 0, 0, 0};
    	model.Cc = new double[] {0, 0, 0, 0, 0, 1, 0, 0};
    	model.Dc = 0;
    	model.Gc = new double[] {0, 0, 0, 0, 0, 0, 1, 1};
    	model.Gref = opG;
    	model.Jref = opbasal;
    	model.xref = new double [] {opG, opX, opIsc1, opIsc2, opIp, opGsc, opQ1, opQ2};
    	model.A = new double [][] {
    			{0.991303320968245, -1.027164705141324e+02, -1.501859484551175e-08, -2.888016796204953e-06, -4.115295919213864e-04, 0.0, 2.008114252228304e-06, 4.298499012064700e-05},
    			{0.0, 0.838999608779181, 5.229968568385989e-10, 7.444695554831070e-08, 6.841833823689807e-06, 0.0, 0.0, 0.0},
    			{0, 0, 0.979773660172805, 0, 0, 0, 0, 0},
    			{0, 0, 0.020020394181457, 0.979773660172805, 0, 0, 0, 0},
    			{0, 0, 1.904874525230007e-04, 0.017992685674403, 0.788244394998039, 0, 0, 0},
    			{0.086491785540854, -4.666860891521209, -2.730084344815173e-10, -6.585728552974108e-08, -1.261803976209045e-05, 0.913124177710464, 6.004311848978277e-08, 1.901601693891575e-06},
    			{0, 0, 0, 0, 0, 0, 0.908336898807234, 0},
    			{0, 0, 0, 0, 0, 0, 0.091154324533205, 0.989093645866446}
    	};
    	model.AhypoAlarm = new double [][] {
    			{0.916358918760358, -5.014502167302258e+02, -7.005645001335162e-05, -0.001171634978039, -0.013094517864297, 0, 1.446768800648856e-04, 3.933902852410761e-04},
    			{0, 0.172829384463164, 2.055221390250846e-07, 2.256554908158791e-06, 1.081502964614245e-05, 0, 0, 0},
    			{0, 0, 0.815187663731760, 0, 0, 0, 0, 0},
    			{0, 0, 0.166572944580820, 0.815187663731760, 0, 0, 0, 0},
    			{0, 0, 0.009271290714475, 0.067881535755103, 0.092599557799045, 0, 0, 0},
    			{0.567950214249191, -2.164014255888083e+02, -1.251742298115281e-05, -2.684350506347080e-04, -0.004285617728904, 0.402993853102167, 3.836897919832754e-05, 1.388643990437360e-04},
    			{0, 0, 0, 0, 0, 0, 0.382357458501364, 0},
    			{0, 0, 0, 0, 0, 0, 0.579928912249696, 0.896136401175804}
    	};
    	model.Abrakes = new double [][] {
    			{0.769479108869124, -5.154694670379711e+02, -0.001459290647954, -0.006782113460724, -0.018628125459046, 0, 6.940601168086614e-04, 9.692966294859702e-04},
    			{0, 0.005162413045742, 1.080101478929748e-06, 2.678200944763238e-06, 5.888632103693559e-07, 0, 0, 0},
    			{0, 0, 0.541717413940511, 0, 0, 0, 0, 0},
    			{0, 0, 0.332078619876430, 0.541717413940511, 0, 0, 0, 0},
    			{0, 0, 0.026422479206073, 0.050815548981225, 7.940114007531313e-04, 0, 0, 0},
    			{0.778889607457411, -4.816521833660736e+02, -6.940849329838669e-04, -0.004097507641758, -0.016037889462784, 0.065447832111091, 4.225651298635188e-04, 6.792158818450082e-04},
    			{0, 0, 0, 0, 0, 0, 0.055899599800695, 0},
    			{0, 0, 0, 0, 0, 0, 0.749211386781904, 0.719651701152595}
    	};
    	model.B = new double [][] {
    			{-3.050044100934195e-09, 2.223906490535622e-05},
    			{1.337354321245918e-10, 0},
    			{0.989852388757865, 0},
    			{0.010078728585060, 0},
    			{6.496483370203719e-05, 0},
    			{-4.610230855690027e-11, 6.548715638445916e-07},
    			{0, 0.953434188789687},
    			{0, 1.040931566332443}
    	};
    	model.C = new double [] {0, 0, 0, 0, 0, 1, 0, 0};
    	model.D = new double [] {0, 0};
    	model.L = new double [] {0.147300357782068, 0, 0, 0, 0, 0.097339692798794, 9.750693774688585, 1.388669006625623e+02};
    	model.M = new double [] {0.142525815371869, 0, 0, 0, 0, 0.092809541130748, 10.734666606071528, 139.4088314642911};
    	
    	// SSM_flag_param
    	flag_param.g_thres = 112.5;
    	flag_param.cho_thres = 16;
    	flag_param.target = 112.5;
    	flag_param.low_target = 80;
    	// SSM_hyper_alarm
    	hyper_alarm.thresG = 200;
    	hyper_alarm.thresDG = 0.5;
    	hyper_alarm.thresI = 2;
    	hyper_alarm.width = 60;
    	// SSM_hypo_alarm
    	hypo_alarm.width = 20;
        hypo_alarm.calibration_window_width = 60; 
    	hypo_alarm.thresG = 70;
    	hypo_alarm.predH = 10;
    	hypo_alarm.eA = new double [][] {
    			{9.617588614485465, -3.021325208703737e+03, -1.282152090783033e-04, -0.003024361198024, -0.054472274576435, 0, 4.579336532647565e-04, 0.001835779958348},
    			{0, 5.137693202262679, 5.359900572292229e-07, 9.579447138765691e-06, 1.149259472051007e-04, 0, 0, 0},
    			{0, 0, 9.137211074628347, 0, 0, 0, 0, 0},
    			{0, 0, 0.808728766161856, 9.137211074628347, 0, 0, 0, 0},
    			{0, 0, 0.033153482910775, 0.455815235920034, 4.285130692019000, 0, 0, 0},
    			{3.037579278795897, -7.930317558346135e+02, -1.781413474595091e-05, -5.088474818640511e-04, -0.011697154545338, 6.871948157315345, 8.772301326658969e-05, 4.376920503238983e-04},
    			{0, 0, 0, 0, 0, 0, 6.738180723339734, 0},
    			{0, 0, 0, 0, 0, 0, 3.143616991449147, 9.523218992555623}
    	};	
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

