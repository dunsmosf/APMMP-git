package edu.virginia.dtc.MCMservice;

import java.util.List;
import java.util.TimeZone;


import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.Tvector.Pair;
import edu.virginia.dtc.Tvector.Tvector;
import edu.virginia.dtc.SysMan.Meal;


import Jama.Matrix;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

public class Inputs {
	
  private static final boolean DEBUG_MODE = true;
  public final String TAG = "SSMservice_inputs";

  
  double[] CGM;  // mg/dl
  double[] J; // pmol/min
  double[] meal; // mg/min
  double[] basal;  // pmol/min
  double[] J_dev;  // pmol/min
  double[] CGM_dev; // mg/dl
  double[] mealandhypo; //mg/min
  double[] hypo;
  double[] APC; // U/min
  
  long length,interval;
  int steps;
  double Gop;
  
  Tvector Tvec_meal, Tvec_CGM, Tvec_insulin, Tvec_calibration, Tvec_basal, Tvec_hypo,Tvec_APC;
  Long currentTimeseconds;
  Context callingContext;
  long currentTimemins;

  public long[] timestamp;
  public double[] CGM_array;
  public double[] CGM_clean;
  public double[] meal_array; 
  public double[] hypo_array; 
  public double[] insulin_array;
  public double[] basal_array;
  public double[] APC_array;
  
  public Inputs(long length, long interval, double Gop) {
     
	 this.length=length;
     this.interval=interval;
     steps=(int) Math.floor(length/interval)+1; // must be integer multiplication
     timestamp = new long[steps];
    
     Tvec_CGM = new Tvector();
 	 Tvec_insulin = new Tvector();
 	 Tvec_meal = new Tvector();
 	 Tvec_basal = new Tvector();
 	 Tvec_calibration = new Tvector();
 	 Tvec_hypo = new Tvector();
 	 Tvec_APC = new Tvector();

     
     CGM_array = new double[steps];
     meal_array = new double[steps];
     insulin_array = new double[steps];   
     basal_array = new double[steps];
     CGM_clean = new double[steps];
     hypo_array = new double[steps];
     APC_array = new double[steps];
     
     
     CGM = new double[steps];
     J = new double[steps];
     meal = new double[steps];
     basal = new double[steps];
     hypo = new double[steps];
     mealandhypo = new double[steps];
     APC = new double[steps];

     CGM_dev = new double[steps];
     J_dev = new double[steps];
     
     this.Gop=Gop;
     
          
  }
  
  public void reconstruct (long time,Context callingContext) {
	  
	  
	  this.callingContext=callingContext;

	  debug_message(TAG, "beginfetch");

	  fetchAllBiometricData();  // get Tvec_meal, Tvec_CGM, Tvec_insulin, Tvec_calibration, currentTimesecond, Tvec_basal;
	 
	  debug_message(TAG, "endinfetch");

	  currentTimeseconds = time;  
	  long startTimesecs = currentTimeseconds-length;
	  int i,j;
	  
	  //*****************************construct timestamp*******************************//
	  timestamp[0]=startTimesecs;
	  for(i=1;i<steps;i++){
		timestamp[i]=timestamp[i-1]+interval;  
	  }
	  
	  debug_message(TAG, "timestamp[0]:"+timestamp[0]);
	  	  
	  
	  //*****************************construct CGM**************************************//
	  List<Integer> indicesCGM1 = null;

	  indicesCGM1 = Tvec_CGM.find(">", startTimesecs, "<", currentTimeseconds);
	  

	  if (indicesCGM1 == null) {
		  for(i=0;i<steps;i++){
			  CGM_array[i]=Gop;// don't need other operations?
		  } 	  
	  }
	  else{
			 
		  debug_message(TAG, "indicesCGM1.size():"+indicesCGM1.size());

		  //find empty's before and after indiced CGM
		  int nCGM=indicesCGM1.size();
		  int nbegin=0,nend=0;
		  i=0;	  
		  while (Tvec_CGM.get_time(indicesCGM1.get(0))>timestamp[i]) {
			  i = i+1;
			 };	 
		  nbegin=i-1;
		  i=steps-1;
		  while (Tvec_CGM.get_time(indicesCGM1.get(nCGM-1))<timestamp[i]) {
			  i = i-1;
		  }
		  nend=i+1;
		  
		  debug_message(TAG, "nbegin:"+nbegin+"nend:"+nend);

		  //fill in empty's before and after indiced CGM
		  for(i=0;i<=nbegin;i++){
			  CGM_array[i]=Tvec_CGM.get_value(indicesCGM1.get(0));
		  }
		  for(i=nend;i<steps;i++){
			  CGM_array[i]=Tvec_CGM.get_value(indicesCGM1.get(nCGM-1));
		  }
		  
		  debug_message(TAG, "A2");

		  //interpolate the indiced CGM
		  
		  if (nend-nbegin>1) {
			  
		    if (indicesCGM1.size()==1) {
		    	CGM_array[nbegin+1]=Tvec_CGM.get_value(indicesCGM1.get(0));
		    }
		    else {
				  double[] x = new double[nCGM];
				  double[] xintp= new double[nend-nbegin-1];
				  double[] y = new double[nCGM];
				  for (i=0;i<nCGM;i++){
					  x[i]=(double) Tvec_CGM.get_time(indicesCGM1.get(i));
					  y[i]=Tvec_CGM.get_value(indicesCGM1.get(i));
				  }
				  for (i=nbegin+1;i<=nend-1;i++) {
					  xintp[i-(nbegin+1)]=(double) timestamp[i];
				  }
				  double[] interpolatedCGM = Interpolator.interpLinear(x, y, xintp);
				  for(i=nbegin+1;i<=nend-1;i++){
					  CGM_array[i]=interpolatedCGM[i-(nbegin+1)];
				  }
		    }
		  }	  
	  }
	  
	  for(i=0;i<CGM_array.length;i++){debug_message(TAG, "CGM_array:"+CGM_array[i]+"\n");}

	      debug_message(TAG, "bumpless_wil_strat");
      
	  CGM_clean=bumpless(Tvec_calibration,CGM_array); // CGM_array ---> CGM_clean
	  
          debug_message(TAG, "bumpless_end");

	  CGM = CGM_clean;    
	  
	  //for(i=0;i<CGM_array.length;i++){debug_message(TAG, "CGM_array:"+CGM_array[i]+"\n");}// why is it different from above?
	  for(i=0;i<CGM.length;i++){debug_message(TAG, "CGM:"+CGM[i]+"\n");}

	  
	  //********************************construct meal******************************//
	  //Tvec_meal.put(23079099,50000);
	  
	  List<Integer> indicesmeal1 = null;

	  indicesmeal1 = Tvec_meal.find(">", startTimesecs, "<", currentTimeseconds);

	  if (indicesmeal1 == null) {
		  for(i=0;i<steps;i++){
			  meal_array[i]=0;
		  }
	  }else{
		  
		  debug_message(TAG, "indicesmeal1.size():"+indicesmeal1.size());

		  
		//find empty's before and after indiced meal
		  int nmeal=indicesmeal1.size();
		  int nbegin=0,nend=0;   // duplicate variable?
		  i=0;	  
		  while (Tvec_meal.get_time(indicesmeal1.get(0))>timestamp[i]) {
			  i = i+1;
			 };	 
		  nbegin=i-1;
		  i=steps-1;
		  while (Tvec_meal.get_time(indicesmeal1.get(nmeal-1))<timestamp[i]) {
			  i = i-1;
		  }
		  nend=i+1;
		  
		  //fill meal into stamps
		  //(a)
		  for(i=0;i<=nbegin;i++){
			  meal_array[i]=0;
		  }
		  
		  //(b)		  
		  for(i=nbegin+1;i<=nend;i++){
			 List<Integer> indicesmeal2 = null;
			 indicesmeal2 = Tvec_meal.find(">=", timestamp[i-1], "<", timestamp[i]); //>= need to fix
             
			 if (indicesmeal2 != null) {
				 double totalspaninterval=0;
				 for (j=0;j<indicesmeal2.size();j++){
					 totalspaninterval=totalspaninterval+Tvec_meal.get_value(indicesmeal2.get(j));
	             }
				 meal_array[i] = (double) totalspaninterval/5;

			 } else {
				 meal_array[i]=0;
			 }
		  } 
		  
		  //(c)	  
		  if (nend<steps-1){
			  
			  for(i=nend+1;i<steps;i++){
				  meal_array[i]=0;
			  }
		  
		  }
		
		  
	  }
	  
	  
	  meal = meal_array;
	  
	  for(i=0;i<meal.length;i++){debug_message(TAG, "meal:"+meal[i]+"\n");}
	  
	  
	  
	  
	  
	//********************************construct hypo******************************//
	  
//	  List<Integer> indiceshypo1 = null;
//
//	  indiceshypo1 = Tvec_hypo.find(">", startTimesecs, "<", currentTimeseconds);
//
//	  if (indiceshypo1 == null) {
//		  for(i=0;i<steps;i++){
//			  hypo_array[i]=0;
//		  }
//	  }else{
//		  
//		  debug_message(TAG, "indiceshypo1.size():"+indiceshypo1.size());
//
//		  
//		//find empty's before and after indiced hypo
//		  int nhypo=indiceshypo1.size();
//		  int nbegin=0,nend=0;   // duplicate variable?
//		  i=0;	  
//		  while (Tvec_hypo.get_time(indiceshypo1.get(0))>timestamp[i]) {
//			  i = i+1;
//			 };	 
//		  nbegin=i-1;
//		  i=steps-1;
//		  while (Tvec_hypo.get_time(indiceshypo1.get(nhypo-1))<timestamp[i]) {
//			  i = i-1;
//		  }
//		  nend=i+1;
//		  
//		  //fill hypo into stamps
//		  //(a)
//		  for(i=0;i<=nbegin;i++){
//			  hypo_array[i]=0;
//		  }
//		  
//		  //(b)		  
//		  for(i=nbegin+1;i<=nend;i++){
//			 List<Integer> indiceshypo2 = null;
//			 indiceshypo2 = Tvec_hypo.find(">=", timestamp[i-1], "<", timestamp[i]); //>= need to fix
//             
//			 if (indiceshypo2 != null) {
//				 double totalspaninterval=0;
//				 for (j=0;j<indiceshypo2.size();j++){
//					 totalspaninterval=totalspaninterval+Tvec_hypo.get_value(indiceshypo2.get(j));
//	             }
//				 hypo_array[i] = (double) totalspaninterval/5;
//
//			 } else {
//				 hypo_array[i]=0;
//			 }
//		  } 
//		  
//		  //(c)	  
//		  if (nend<steps-1){
//			  
//			  for(i=nend+1;i<steps;i++){
//				  hypo_array[i]=0;
//			  }
//		  
//		  }
//		
//		  
//	  }
//	  
//	  
//	  hypo = hypo_array;
//	  
//	  for(i=0;i<hypo.length;i++){debug_message(TAG, "hypo:"+hypo[i]+"\n");}
	  
	  
	  

	  
	  //***************************construct basal profile*****************************//
	  TimeZone tz = TimeZone.getDefault();
	  int UTC_offset_secs = tz.getOffset(currentTimeseconds*1000)/1000;
		
	  Pair p; 
	  
	  for(i=0;i<steps;i++) {
	  
		  long tlocal = (timestamp[i]/60+UTC_offset_secs/60)%1440;
		  if ((p = Tvec_basal.getLastInRange(">", -1, "<=", tlocal)) == null) {
		  		p = Tvec_basal.getLastInRange(">", -1, "<", -1);
		  }	  
		  basal_array[i]= p.value();//  
	 	  
      }
	  
      basal = basal_array;
      
	  for(i=0;i<basal.length;i++){debug_message(TAG, "basal:"+basal[i]+"\n");}

	  
	  //********************************construct J***************************//
	  	  
	  List<Integer> indicesinsulin1 = null;

	  indicesinsulin1 = Tvec_insulin.find(">", startTimesecs, "<", currentTimeseconds);
	  
	  if (indicesinsulin1 == null) {
		  for(i=0;i<steps;i++){
			  insulin_array[i]=basal_array[i];
		  }
		  
	  }else{
		  debug_message(TAG, "indicesinsulin1.size():"+indicesinsulin1.size());
	      
		  int ol;
		  for(ol=0;ol<=indicesinsulin1.size()-1;ol++){  
		  debug_message(TAG, "injected:"+Tvec_insulin.get_value(indicesinsulin1.get(ol))+"\n");
	      }
		  
		  //find empty's before and after indiced insulin
		  int ninsulin=indicesinsulin1.size();
		  int nbegin=0,nend=0;   // duplicate variable?
		  i=0;	  
		  while (Tvec_insulin.get_time(indicesinsulin1.get(0))>timestamp[i]) {
			  i = i+1;
			 };	 
		  nbegin=i-1;
		  i=steps-1;
		  while (Tvec_insulin.get_time(indicesinsulin1.get(ninsulin-1))<timestamp[i]) {
			  i = i-1;
		  }
		  nend=i+1;
		  
		  debug_message(TAG, "nbegin:"+nbegin+"nend:"+nend);

		  
		  //fill insulin into stamps
		  //(a)
		  for(i=0;i<=nbegin;i++){
			  insulin_array[i]=basal_array[i];
		  }
		  
	      debug_message(TAG, "A4");

		  
		  //(b)		  
		  for(i=nbegin+1;i<=nend;i++){
			 List<Integer> indicesinsulin2 = null;
			 indicesinsulin2 = Tvec_insulin.find(">=", timestamp[i-1], "<", timestamp[i]); //>= need to fix
             
			 if(indicesinsulin2!=null){
			     double totalspaninterval=0;
				 for (j=0;j<indicesinsulin2.size();j++){
					 totalspaninterval=totalspaninterval+Tvec_insulin.get_value(indicesinsulin2.get(j));
			        // debug_message(TAG, "injected:"+Tvec_insulin.get_value(indicesinsulin2.get(j))+"\n");
				 }			  
				 insulin_array[i] = (double) totalspaninterval/5;
			 } else {
				 insulin_array[i] = 0;
			 }
		  }
          
		  if (Tvec_insulin.get_time(0)==timestamp[nbegin+1]) {
			  insulin_array[nbegin+1]=basal_array[nbegin+1];
		  }
	      
		  debug_message(TAG, "A5");

		  
		  //(c)	  
		  if (nend<steps-1){
			  
			  for(i=nend+1;i<steps;i++){
				  insulin_array[i]=basal_array[i]; // or 0?
			  }
		  
		  }
		  
	      debug_message(TAG, "A6");

		  }
	  
	  J = insulin_array;
	  
	  for(i=0;i<J.length;i++){debug_message(TAG, "J:"+J[i]+"\n");}
	  
	  
	  //*******************************construct APC*********************************//
	  List<Integer> indicesapc1 = null;

	  indicesapc1 = Tvec_APC.find(">", startTimesecs, "<", currentTimeseconds);

	  if (indicesapc1 == null) {
		  for(i=0;i<steps;i++){
			  APC_array[i]=0;
		  }
	  }else{
		  
		  debug_message(TAG, "indicesapc1.size():"+indicesapc1.size());

		  
		//find empty's before and after indiced meal
		  int napc=indicesapc1.size();
		  int nbegin=0,nend=0;   // duplicate variable?
		  i=0;	  
		  while (Tvec_APC.get_time(indicesapc1.get(0))>timestamp[i]) {
			  i = i+1;
			 };	 
		  nbegin=i-1;
		  i=steps-1;
		  while (Tvec_APC.get_time(indicesapc1.get(napc-1))<timestamp[i]) {
			  i = i-1;
		  }
		  nend=i+1;
		  
		  //fill meal into stamps
		  //(a)
		  for(i=0;i<=nbegin;i++){
			  APC_array[i]=0;
		  }
		  
		  //(b)		  
		  for(i=nbegin+1;i<=nend;i++){
			 List<Integer> indicesapc2 = null;
			 indicesapc2 = Tvec_APC.find(">=", timestamp[i-1], "<", timestamp[i]); //>= need to fix
             
			 if (indicesapc2 != null) {
				 double totalspaninterval=0;
				 for (j=0;j<indicesapc2.size();j++){
					 totalspaninterval=totalspaninterval+Tvec_APC.get_value(indicesapc2.get(j));
	             }
				 APC_array[i] = (double) totalspaninterval/5;

			 } else {
				 APC_array[i]=0;
			 }
		  } 
		  
		  //(c)	  
		  if (nend<steps-1){
			  
			  for(i=nend+1;i<steps;i++){
				  APC_array[i]=0;
			  }
		  
		  }
		
		  
	  }
	  
	  
	  APC = APC_array;
	  
	  for(i=0;i<APC.length;i++){debug_message(TAG, "APC:"+APC[i]+"\n");}
	  
	 
	  //**************************construct J_dev and CGM_dev*****************************//
	  
	  for (i=0;i<steps;i++) {
		  J_dev[i] = J[i] - basal[i];
		  CGM_dev[i] = CGM[i] - Gop;
	  }
	    
	  for(i=0;i<J_dev.length;i++){debug_message(TAG, "J_dev:"+J_dev[i]+"\n");}
	  
	  
	  //************************************mealandhypo***********************************//
//	  for (i=0;i<steps;i++) {
//		  mealandhypo[i] = meal[i] + hypo[i];
//	  }
//	    
//	  for(i=0;i<steps;i++){debug_message(TAG, "meal and hypo:"+mealandhypo[i]+"\n");}
  }
  
  public double[] bumpless(Tvector Tvec_calibration, double[] CGM_array) {
	  
	     double[][] X_ini= {{1,-15},{1,-10},{1,-5},{1, 0}};
	     Matrix X = new Matrix(X_ini);
	     Matrix X_t = X.transpose();
	     Matrix Btemp1 = X_t.times(X);
	     Matrix Btemp2 = Btemp1.inverse();
	     
		 int ncal=Tvec_calibration.count();
		 
		 double[] CGM_clean=CGM_array;
		 

		 if (ncal!=0) {
		
	     			     
			     long backwindow=48; // step
			     long forwardwindow=6;
			     long slopewindow=4;
			     double alpha=1;
				 
			     int ii;	 
			     for(ii=0;ii<ncal;ii++){
			    	 
			    	long caltime = Tvec_calibration.get_time(ii);
			    	int Calind = (int) Math.floor((caltime-(currentTimeseconds-length))/300);
			    	
			    	
			    	if (Calind>1 && Calind<72) 
			    		{
			    		   double Mstar = 0, Gammastar =0, Deltastar=0, Gammatau=0,Deltatau=0;
						   
					       double calvalue = CGM_array[Calind+1];

			    		   //*****************************get slope************************//
				           if (Calind>4){
				        	   double[][] Y_ini = {{CGM_array[Calind-3]},{CGM_array[Calind-2]},{CGM_array[Calind-1]},{CGM_array[Calind]}};
				               Matrix Y = new Matrix(Y_ini);
				               Matrix B = Btemp2.times(X_t).times(Y);
				               Mstar = B.get(1,0);
				           }else{
				        	   Mstar = 0;
				           }
				           
				           Gammastar= calvalue-(CGM_array[Calind]+5*Mstar);
				           Deltastar= alpha*Gammastar;
						     
						  //*****************************back window************************//
						  for (int k=0;k<Math.min(Calind, backwindow);k++){
							  CGM_clean[Calind-k+1] = CGM_clean[Calind-k+1]+Deltastar/(1+Math.exp(((double)k-(double)backwindow/2)/((double)backwindow/20)));
						  }
				           
						  
						  //*****************************forward window*********************//
						  for (int k=0;k<Math.min(72-Calind, forwardwindow);k++){
							  Gammatau=calvalue+5*Mstar-CGM_clean[Calind+k+1];
                              Deltatau=alpha*Gammatau;
							  CGM_clean[Calind+k+1] = CGM_clean[Calind+k+1]+Deltatau/(1+Math.exp(((double)k-(double)forwardwindow/2)/((double)forwardwindow/20)));
						  }
				           
			     
			    	} // end of if (Calind>1 && Calind<72) 
			     } // end of ncal for loop
	          
	     } // end of if (ncal!=0)
		 
		 return CGM_clean;
	  
  }
    
  public void fetchAllBiometricData() {
       
	//	boolean return_value = false;		
	Tvec_CGM.init();
	Tvec_insulin.init();
	Tvec_meal.init();
	Tvec_basal.init();
	Tvec_calibration.init();
	Tvec_hypo.init();
	Tvec_APC.init();

	
	try {
	
		// (1) Fetch CGM data
	    Cursor c=callingContext.getContentResolver().query(Biometrics.CGM_URI, null, null, null, null);

        //debug_message(TAG,"CGM > c.getCount="+c.getCount());
		long last_time_temp_secs = 0;
		double cgm1_value;
		
		if (c.moveToFirst()) {
			do{
				// Fetch the cgm1 and cgm2 values so that they can be screened for validity
				cgm1_value = (double)c.getDouble(c.getColumnIndex("cgm"));
				// Make sure that cgm1_value is in the range of validity
				if (cgm1_value>=39.0 && cgm1_value<=401.0) {
					// Save the latest timestamp from the retrieved data
					if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
						last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
					}
					// time in seconds
					Tvec_CGM.put(c.getLong(c.getColumnIndex("time")), cgm1_value);  // sec
					//return_value = true;
				}
			} while (c.moveToNext());
	    }						    
		c.close();
		//last_Tvec_cgm1_time_secs = last_time_temp_secs;			
		
		  debug_message(TAG, "Tvec_CGM(0)"+Tvec_CGM.get_value(0)+"Tvec_CGM(end)"+Tvec_CGM.get_last_value());

		
		
		// (2) Fetch INSULIN data
		//c=getContentResolver().query(INSULIN_URI, null, last_Tvec_insulin_rate1_time_secs.toString(), null, null);
		c=callingContext.getContentResolver().query(Biometrics.INSULIN_URI, null, null, null, null);

		//debug_message(TAG,"INSULIN > c.getCount="+c.getCount());
		last_time_temp_secs = 0;
		double insulin_value;
		
		if (c.moveToFirst()) {
			do {
				insulin_value=(double)c.getDouble(c.getColumnIndex("deliv_total"));
				if	(insulin_value>=0) {		
				// Save the latest timestamp from the retrieved data
					if (c.getLong(c.getColumnIndex("deliv_time")) > last_time_temp_secs) {
						last_time_temp_secs = c.getLong(c.getColumnIndex("deliv_time"));
					}
				// Round incoming time in seconds down to the nearest minute
				Tvec_insulin.put(c.getLong(c.getColumnIndex("deliv_time")), insulin_value*1000*6); // sec pmol
				}
			 } while(c.moveToNext());		    
		}	
		c.close();
		//last_Tvec_insulin_rate1_time_secs = last_time_temp_secs;			

		debug_message(TAG, "Tvec_insulin(0)"+Tvec_insulin.get_value(0)+"Tvec_insulin(end)"+Tvec_insulin.get_last_value());


		
		// (3) Fetch MEAL data
		//c=getContentResolver().query(MEAL_URI, null, Time.toString(), null, null);
		c=callingContext.getContentResolver().query(Biometrics.MEAL_URI, null, null, null, null);

        //debug_message(TAG,"MEAL > c.getCount="+c.getCount());
		double meal_value;
		int meal_status;
		last_time_temp_secs = 0;
		if (c.moveToFirst()) {
			do{
				meal_value = (double)c.getDouble(c.getColumnIndex("meal_size_grams"));
				meal_status = (int)c.getDouble(c.getColumnIndex("meal_status"));
                if (meal_value>=0 && meal_status==Meal.MEAL_STATUS_APPROVED) {
                	if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
						last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
					}
				// Round incoming time in seconds down to the nearest minute
				Tvec_meal.put(c.getLong(c.getColumnIndex("time")), meal_value*1000); // sec mg      	
                }				
			} while (c.moveToNext());

	    }
		c.close();
		//last_Tvec_meal_time_secs = last_time_temp_secs;
		
		  debug_message(TAG, "Tvec_meal(0)"+Tvec_meal.get_value(0)+"Tvec_meal(end)"+Tvec_meal.get_last_value());


		
		// (4) Fetch basal profile data		
	    c = callingContext.getContentResolver().query(Biometrics.BASAL_PROFILE_URI, null, null, null, null);
	    
	    double basal_value;
		last_time_temp_secs = 0;
		//Log.i(TAG, "Retrieved BASAL_URI with " + c.getCount() + " items");
	    if (c.moveToFirst()) {
		   do{
			  basal_value = (double)c.getDouble(c.getColumnIndex("value")); 
			  
			  if(basal_value>=0){
				  if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
						last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
					}				  				  
			  Tvec_basal.put(c.getLong(c.getColumnIndex("time")), basal_value*1000*6/60);  //min pmol/min
			  }      
		      //return_value = true;
		   }while (c.moveToNext());
	    }	
	    c.close();
	    
		 debug_message(TAG, "Tvec_basal(0)"+Tvec_basal.get_value(0)+"Tvec_basal(end)"+Tvec_basal.get_last_value());

	     
	     
	    // (5) Fetch calibration data
	    c=callingContext.getContentResolver().query(Biometrics.SMBG_URI, null, null, null, null);

		double calibration_value;
		last_time_temp_secs = 0;
	    if (c.moveToFirst()) {
			do{
				calibration_value = (double)c.getDouble(c.getColumnIndex("smbg"));

				if (calibration_value>=0) {
					
					if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
						last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
					}
					   
					int calval = c.getInt(c.getColumnIndex("isCalibration"));
					if (calval==1){
					Tvec_calibration.put(c.getLong(c.getColumnIndex("time")), calibration_value); //sec
					}
				}
			} while (c.moveToNext());
	    }			
		c.close();
		
		 debug_message(TAG, "Tvec_cal(0)"+Tvec_calibration.get_value(0)+"Tvec_cal(end)"+Tvec_calibration.get_last_value());

		
		 
		// (6) Fetch hypotreatment data
		   
		c=callingContext.getContentResolver().query(Biometrics.EVENT_URI, null, null, null, null);

        //debug_message(TAG,"MEAL > c.getCount="+c.getCount());
		double code_value;
		last_time_temp_secs = 0;
			if (c.moveToFirst()) {
				do{
					code_value = (double)c.getDouble(c.getColumnIndex("code"));
	                
					if (code_value == 305 && c.getLong(c.getColumnIndex("time"))-last_time_temp_secs >= 1200) {	
					Tvec_hypo.put(c.getLong(c.getColumnIndex("time")), 10*1000); // sec mg      	
					last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
	                }				
				
				} while (c.moveToNext());
	
		    }
		c.close();
			//last_Tvec_meal_time_secs = last_time_temp_secs;
			
		debug_message(TAG, "Tvec_hypo(0)"+Tvec_hypo.get_value(0)+"Tvec_hypo(end)"+Tvec_hypo.get_last_value());

		
		// (7) Fetch APC data		
	    c = callingContext.getContentResolver().query(Biometrics.USER_TABLE_2_URI, null, null, null, null);
	    
	    double APC_value;
		last_time_temp_secs = 0;
	    if (c.moveToFirst()) {
		   do{
			  APC_value = (double)c.getDouble(c.getColumnIndex("d0")); 
			  
			  if(APC_value>-15.0 && APC_value<15.0){
				  if (c.getLong(c.getColumnIndex("time")) > last_time_temp_secs) {
						last_time_temp_secs = c.getLong(c.getColumnIndex("time"));
					}				  				  
			  Tvec_APC.put(c.getLong(c.getColumnIndex("time")), APC_value*5);  //sec U/5min
			  }      
		      //return_value = true;
		   }while (c.moveToNext());
	    }	
	    c.close();
	    
		debug_message(TAG, "Tvec_APC(0)"+Tvec_APC.get_value(0)+"Tvec_APC(end)"+Tvec_APC.get_last_value());

	     
	  }
       
	  catch (Exception e) {
  		 Log.e("Error Fetch data", e.getMessage());
      }
  }

  private static void debug_message(String tag, String message) {
		final String FUNC_TAG = "debug_message";
		if (DEBUG_MODE) {
			Log.i(tag, message);
		}
  }
   


	
}
