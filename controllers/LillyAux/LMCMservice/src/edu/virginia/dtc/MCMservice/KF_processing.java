package edu.virginia.dtc.MCMservice;

import Jama.Matrix;

public class KF_processing {
	
	LinearDiscModel buff_ins_model;
	LinearDiscModel buff_meal_model;
	LinearDiscModel ins_model;
	LinearDiscModel meal_model;
	LinearDiscModel pred_model,pred_model_1h,pred_model_light;
	KF kf;
	double Gpred,Risk=0.0, RiskEX=0.0, BrakeAction=1.0;
	double BW;
	double Gest;
	double Gpred_light;
  	double Gpred_1h;
  	double[][] PREDstate_init;
  	double delta_1h_window,delta_3h_window,Uma;
	

  public KF_processing(Inputs inputs, Subject subject_data,double Gop, SSM_param ssm_param, double exercise_level) {
    
	BW = subject_data.subjectWeight;  

	//buff model
	double[][] MEAL_BUFF_A_init={	{0.9048, 0.0905}, 
			                        {0.0, 0.9048}};
	double[][] MEAL_BUFF_B_init={	{4.679e-3}, 
			                        {9.516e-2}};
	double[][] INS_BUFF_A_init={	{0.6065, 0.3033}, 
			                        {0.0, 0.6065}};
	double[][] INS_BUFF_B_init={	{0.0902}, 
			                        {0.3935}};
	double[][] BUFF_C_init={	{1.0, 0.0}};
	double[][] BUFF_D_init={	{0.0}};
	double[][] BUFFstate_ins_ini={{0.0}, {0.0}};
	double[][] BUFFstate_meal_ini={{0.0}, {0.0}};

	//insulin model
	double[][] INS_A_init={	{0.9048, 6.256e-6, 2.996e-6/BW, 1.551e-6/BW}, 											// Now includes BW dependency
			                {0.0, 0.4107, 0.5301/BW, 0.2800/BW}, 
			                {0.0, 0.0, 0.9048, 0.0452}, 
			                {0.0, 0.0, 0.0, 0.9048}};
	double[][] INS_B_init={	{2.7923e-6/BW}, 																	// Now includes BW dependency
							{0.8035/BW}, 
							{0.1170},
							{4.7581}};
	double[][] INS_C_init={	{1.0, 0.0, 0.0, 0.0}, 
			                {0.0, 1.0, 0.0, 0.0}, 
			                {0.0, 0.0, 1.0, 0.0}, 
			                {0.0, 0.0, 0.0, 1.0}};
	double[][] INS_D_init={{0.0}, {0.0}, {0.0}, {0.0}};
	double[][] INSstate_ini={{0.0}, {0.0}, {0.0}, {0.0}};
	
	//meal model
	double[][] MEAL_A_init={{0.9048, 0.04524}, {0.0, 0.9048}};
	double[][] MEAL_B_init={{0.117}, {4.758}};
	double[][] MEAL_C_init={{0.02, 0.01}, {1.0, 0.0}, {0.0, 1.0}};
	double[][] MEAL_D_init={{0.0}, {0.0}, {0.0}};
	double[][] MEALstate_ini={{0.0}, {0.0}};
	
	//KF model
	double[][] KF_A_init={{0.4419, -417.53}, 
			              {2.569e-4, 0.9048}};
	double[][] KF_B_init={{-0.00214, 2.5669/BW, 0.5093}, {9.5163e-6, 0.0, -2.569e-4}};
	double[][] KF_C_init={{0.5892, 0.0}, {2.8397e-4, 1.0}};
	double[][] KF_D_init={{0.0, 0.0, 0.4108}, {0.0, 0.0, -2.8397e-4}};
	double[][] KFstate_init={{0.0}, {0.0}};
	
	//prediction model
	double[][] PRED_A_init={{0.7408, -2296, -1728, 0.2021/BW, 0.1299/BW, -0.0169, -0.0203/BW, -0.0347/BW}, // Now includes BW dependency
			{0.0, 0.9704, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
			{0.0, 0.0, 0.5488, 0.0, 0.0, 6.89e-6, 1.75e-5/BW, 2.794e-5/BW},
			{0.0, 0.0, 0.0, 0.5488, 0.1646, 0.0, 0.0, 0.0},
			{0.0, 0.0, 0.0, 0.0, 0.5488, 0.0, 0.0, 0.0},
			{0.0, 0.0, 0.0, 0.0, 0.0, 4.8e-3, 0.4315/BW, 0.5836/BW},
			{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.5488, 0.0},
			{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.1646, 0.5488},
                  };
	double[][] PRED_B_init={{0.0},{0.0},{0.0},{0.0},{0.0},{0.0},{0.0},{0.0}};
	double[][] PRED_C_init={{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}};
	double[][] PRED_D_init={{0}};
	
	//prediction model 1h	
	double[][] mat_1h_pred_A_init = {{0.30119, -3034.3, -1626.4, 0.19023/BW, 0.1522/BW, -0.018416, -0.058026/BW, -0.084929/BW}, // Now includes BW dependency
			{0.0, 0.94176, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
			{0.0, 0.0, 0.30119, 0.0, 0.0, 3.8123e-6, 2.6778e-5/BW, 3.4682e-5/BW},
			{0.0, 0.0, 0.0, 0.30119, 0.18072, 0.0, 0.0, 0.0},
			{0.0, 0.0, 0.0, 0.0, 0.30119, 0.0, 0.0, 0.0},
			{0.0, 0.0, 0.0, 0.0, 0.0, 2.3e-5, 0.33495/BW, 0.32308/BW},
			{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.30119, 0.0},
			{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.18072, 0.30119},
                                     };
    double[][] mat_1h_pred_C_init = {{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}};
    double[][] mat_1h_pred_B_init = {{0.0},{0.0},{0.0},{0.0},{0.0},{0.0},{0.0},{0.0}};
    double[][] mat_1h_pred_D_init = {{0}};
    
	//prediction model light
    double[][] CORE_pred_A_light_init = {{0.8607, -1244, -1079, 0.1262/BW, 0.0723/BW, -8.29e-3, -4.34e-3/BW, -8.034e-3/BW},  // Now includes BW dependency
			{0.0, 0.9851, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
			{0.0, 0.0, 0.7408, 0.0, 0.0, 8.5e-6, 8.217e-6/BW, 1.472e-5/BW},
			{0.0, 0.0, 0.0, 0.7408, 0.1111, 0.0, 0.0, 0.0},
			{0.0, 0.0, 0.0, 0.0, 0.7408, 0.0, 0.0, 0.0},
			{0.0, 0.0, 0.0, 0.0, 0.0, 0.0693, 0.4338/BW, 0.7204/BW},
			{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.7408, 0.0},
			{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.1111, 0.7408},
                                        };
    double[][] CORE_pred_B_light_init = {{0.9097/BW}, 									// Now includes BW dependency
			{0.0},
			{0.0},
			{1.4218},
			{11.726},
			{0.0},
			{0.0},
			{0.0}
             };
    double[][] CORE_pred_C_light_init = {{1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}};
    double[][] CORE_pred_D_light_init = {{0}};

	//feed-forward model
	buff_ins_model = new LinearDiscModel(INS_BUFF_A_init,INS_BUFF_B_init,BUFF_C_init,BUFF_D_init, BUFFstate_ins_ini);
	buff_meal_model = new LinearDiscModel(MEAL_BUFF_A_init,MEAL_BUFF_B_init,BUFF_C_init,BUFF_D_init, BUFFstate_meal_ini);
	ins_model = new LinearDiscModel(INS_A_init,INS_B_init,INS_C_init,INS_D_init, INSstate_ini);
	meal_model = new LinearDiscModel(MEAL_A_init,MEAL_B_init,MEAL_C_init,MEAL_D_init, MEALstate_ini);


	buff_ins_model.predict(inputs.J_dev);
	buff_meal_model.predict(inputs.meal);
	
	
	ins_model.predict(buff_ins_model.out);
	meal_model.predict(buff_meal_model.out);

	//construct KF inputs
	int i,N; // pay attention to those N's
	N=inputs.J_dev.length;
	double[][] kfinput = new double[2][N];
	for (i=0;i<N;i++){
		kfinput[0][i]=ins_model.out[1][i];  //Ik
		kfinput[1][i]=meal_model.out[0][i]; //Rak
	}

	
	//KF estimation	
	kf = new KF(KF_A_init,KF_B_init,KF_C_init,KF_D_init,KFstate_init);
	kf.estimate(kfinput,inputs.CGM_dev); //Gop
	
	Gest = kf.KFout[0][N-1]+Gop;
	
	// brake prediction
	PREDstate_init = new double[8][1];
	PREDstate_init[0][0]=kf.KFout[0][N-1];  //why 
	PREDstate_init[1][0]=kf.KFout[1][N-1]-ins_model.out[0][N-1]; //why
	PREDstate_init[2][0]=ins_model.out[0][N-1];
	PREDstate_init[3][0]=meal_model.out[1][N-1];
	PREDstate_init[4][0]=meal_model.out[2][N-1];
	PREDstate_init[5][0]=ins_model.out[1][N-1];
	PREDstate_init[6][0]=ins_model.out[2][N-1];
	PREDstate_init[7][0]=ins_model.out[3][N-1];
	
	// get delta moving average
	double[] window1h= new double[12];
	double[] window3h= new double[36];
	double[] dUVec= new double[3];

	for (int k=12;k>0;k--){
		window1h[12-k]=kf.KFout[1][N-k]-ins_model.out[0][N-k];
	}
	for (int k=36;k>0;k--){
		window3h[36-k]=kf.KFout[1][N-k]-ins_model.out[0][N-k];
	}	
	for (int k=3;k>0;k--){
		dUVec[3-k]=inputs.APC[N-k];
	}
	delta_1h_window = getmean(window1h);
	delta_3h_window = getmean(window3h);
	Uma = getmean(dUVec);
	
	
	// get Gpred
	pred_model= new LinearDiscModel(PRED_A_init,PRED_B_init,PRED_C_init,PRED_D_init, PREDstate_init);
    double[] pred_input = {0,0};  // why -->
	pred_model.predict(pred_input);
	Gpred = pred_model.out[0][1]+Gop; //  --> because in this case ,we have to predict 2 step (30min interval), 1st CX+DU is useless	
	
	
	//get Gpred_1h
	pred_model_1h= new LinearDiscModel(mat_1h_pred_A_init,mat_1h_pred_B_init, mat_1h_pred_C_init,mat_1h_pred_D_init ,PREDstate_init);
    double[] pred_input_1h = {0,0};  
	pred_model_1h.predict(pred_input_1h);
	Gpred_1h = pred_model_1h.out[0][1]+Gop;  	
	
	
	//get Gpred_light 
	pred_model_light= new LinearDiscModel(CORE_pred_A_light_init,CORE_pred_B_light_init,CORE_pred_C_light_init,CORE_pred_D_light_init ,PREDstate_init);
	double[] pred_input_light = {-inputs.basal[N-1],0};  
	pred_model_light.predict(pred_input_light);
	Gpred_light = pred_model_light.out[0][1]+Gop;  

	
  }
  
  
	  public double getmean(double[] input){
		  
		  double sum=0;
		  for (int i=0;i<input.length;i++){
			sum=sum+input[i];  
		  }
		  return sum/input.length;
	  }

}
