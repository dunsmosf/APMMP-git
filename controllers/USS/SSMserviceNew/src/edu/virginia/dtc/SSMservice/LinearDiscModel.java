package edu.virginia.dtc.SSMservice;

import Jama.Matrix;

public class LinearDiscModel {

	Matrix A;
	Matrix B;
	Matrix C;
	Matrix D;	
	Matrix state;
	
	double[][] out;	
	int steps,Nu,No;
	
	
    public LinearDiscModel(double[][] A_init,double[][] B_init,double[][] C_init,double[][] D_init,double[][] state_init){
		
		A= new Matrix(A_init);
		B= new Matrix(B_init);
		C= new Matrix(C_init);
		D= new Matrix(D_init);
		state = new Matrix(state_init);
		
	}
    
   
    
    // (a) multiple inputs
    public void predict(double[][] u) {
    	
		int i,j;

    	steps =u[0].length;
        Nu = u.length;
    	No=C.getRowDimension();
    	
    	out = new double[No][steps];

    	double[][] utemp_init= new double[Nu][1];
		double[][] otemp_init= new double[No][1];
		Matrix otemp = new Matrix(otemp_init);
        
    	for (i=0;i<steps;i++) {
			
			for (j=0;j<Nu;j++){
				utemp_init[j][0]=u[j][i];
			}
			Matrix utemp = new Matrix(utemp_init);
		
		otemp = C.times(state).plus(D.times(utemp));
		state = A.times(state).plus(B.times(utemp));
		
		    for (j=0;j<No;j++){
			   out[j][i]=otemp.get(j,0);
			}
		}
    	
    }
    
    // (b) single input
    public void predict(double[] u) {
    	
		int i,j;

    	steps =u.length;
    	No=C.getRowDimension();
    	
    	out = new double[No][steps];

		double[][] otemp_init= new double[No][1];
		Matrix otemp = new Matrix(otemp_init);
        
    	for (i=0;i<steps;i++) {
		
		otemp = C.times(state).plus(D.times(u[i]));
		state = A.times(state).plus(B.times(u[i]));
		
		    for (j=0;j<No;j++){
			   out[j][i]=otemp.get(j,0);
			}
		}
    	
    }
	
    
 
    
}
