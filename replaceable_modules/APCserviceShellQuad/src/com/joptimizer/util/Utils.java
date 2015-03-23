/*
 * Copyright 2011-2013 JOptimizer
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.joptimizer.util;

import java.util.Random;

import com.joptimizer.MainActivity;
import android.util.Log;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;

/**
 * @author alberto trivellato (alberto.trivellato@gmail.com)
 */
public class Utils {
	
	private static Double RELATIVE_MACHINE_PRECISION = Double.NaN;
	
	
	public static DoubleMatrix2D randomValuesMatrix(int rows, int cols, double min, double max) {
		return randomValuesMatrix(rows, cols, min, max, null);
	}

	public static DoubleMatrix2D randomValuesMatrix(int rows, int cols, double min, double max, Long seed) {
		Random random = (seed != null) ? new Random(seed) : new Random();

		double[][] matrix = new double[rows][cols];
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols; j++) {
				matrix[i][j] = min + random.nextDouble() * (max - min);
			}
		}
		return DoubleFactory2D.dense.make(matrix);
	}
	
	/**
	 * @TODO: check this!!!
	 * @see "http://mathworld.wolfram.com/PositiveDefiniteMatrix.html"
	 */
	public static DoubleMatrix2D randomValuesPositiveMatrix(int rows, int cols, double min, double max, Long seed) {
		DoubleMatrix2D Q = Utils.randomValuesMatrix(rows, cols, min, max, seed);
		DoubleMatrix2D P = Algebra.DEFAULT.mult(Q, Algebra.DEFAULT.transpose(Q.copy()));
    return Algebra.DEFAULT.mult(P, P);
	}
	
	/**
	 * Residual conditions check after resolution of A.x=b.
	 * 
	 * eps := The relative machine precision
	 * N   := matrix dimension
	 * 
     * Checking the residual of the solution. 
     * Inversion pass if scaled residuals are less than 10:
	 * ||Ax-b||_oo/( (||A||_oo . ||x||_oo + ||b||_oo) . N . eps ) < 10.
	 * 
	 * @param A not-null matrix
	 * @param x not-null vector
	 * @param b not-null vector
	 */
//	public static boolean checkScaledResiduals(DoubleMatrix2D A, DoubleMatrix1D x, DoubleMatrix1D b, Algebra ALG) {
//	  //The relative machine precision
//		double eps = RELATIVE_MACHINE_PRECISION;
//		int N = A.rows();//matrix dimension
//		double residual = -Double.MAX_VALUE;
//		if(Double.compare(ALG.normInfinity(x), 0.)==0 && Double.compare(ALG.normInfinity(b), 0.)==0){
//			return true;
//		}else{
//			residual = ALG.normInfinity(ALG.mult(A, x).assign(b,	Functions.minus)) / 
//	          ((ALG.normInfinity(A)*ALG.normInfinity(x) + ALG.normInfinity(b)) * N * eps);
//			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"scaled residual: " + residual);
//			return residual < 10;
//		}
//	}
	
	/**
	 * The smallest positive (epsilon) such that 1.0 + epsilon != 1.0.
	 * @see http://en.wikipedia.org/wiki/Machine_epsilon#Approximation_using_Java
	 */
	public static final double getDoubleMachineEpsilon() {
		
		if(!Double.isNaN(RELATIVE_MACHINE_PRECISION)){
			return RELATIVE_MACHINE_PRECISION;
		}
		
		synchronized(RELATIVE_MACHINE_PRECISION){
			
			if(!Double.isNaN(RELATIVE_MACHINE_PRECISION)){
				return RELATIVE_MACHINE_PRECISION;
			}
			
			double eps = 1.;
			do {
				eps /= 2.;
			} while ((double) (1. + (eps / 2.)) != 1.);
			
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"Calculated double machine epsilon: " + eps);
			RELATIVE_MACHINE_PRECISION = eps;
		}
		
		return RELATIVE_MACHINE_PRECISION;
	}
	
	/**
	 * @see http://en.wikipedia.org/wiki/Machine_epsilon#Approximation_using_Java
	 */
//	public static final float calculateFloatMachineEpsilon() {
//		float eps = 1.0f;
//		do {
//			eps /= 2.0f;
//		} while ((float) (1.0 + (eps / 2.0)) != 1.0);
//		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"Calculated float machine epsilon: " + eps);
//		return eps;
//	}
	
	public static RealMatrix squareMatrixInverse(RealMatrix M) throws SingularMatrixException{
		if(!M.isSquare()){
			throw new IllegalArgumentException("Not square matrix!");
		}
		
		// try commons-math cholesky
		try {
			CholeskyDecomposition cd = new CholeskyDecomposition(M);
			return cd.getSolver().getInverse();
		} catch (SingularMatrixException e) {
			throw e;
		} catch (Exception e) {
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,e.getMessage());
		}
		
		// try joptimizer cholesky
		try {
			CholeskyFactorization cd = new CholeskyFactorization(M.getData());
			double[][] MInv = cd.getInverse();
			return MatrixUtils.createRealMatrix(MInv);
		} catch (Exception e) {
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,e.getMessage());
		}
		
		// try LU
		try{
			LUDecomposition ld = new LUDecomposition(M);
			return ld.getSolver().getInverse();
		} catch (SingularMatrixException e) {
			throw e;
		} catch (Exception e) {
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,e.getMessage());
		}

		// try QR
		try {
			QRDecomposition qr = new org.apache.commons.math3.linear.QRDecomposition(M);
			return qr.getSolver().getInverse();
		} catch (SingularMatrixException e) {
			throw e;
		} catch (Exception e) {
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,e.getMessage());
		}
		
		return null;
	}
	 
	/**
	   * Get the index of the maximum entry.
	   */
	  public static int getMaxIndex(double[] v){
	  	int maxIndex = -1;
	  	double maxValue = -Double.MAX_VALUE;
	  	for(int i=0; i<v.length; i++){
	  		if(v[i]>maxValue){
	  			maxIndex = i;
	  			maxValue = v[i]; 
	  		}
	  	}
	  	return maxIndex; 
	  } 
	  
	  
	  
	  /**
	   * Get the index of the minimum entry.
	   */
	  public static int getMinIndex(double[] v){
	  	int minIndex = -1;
	  	double minValue = Double.MAX_VALUE;
	  	for(int i=0; i<v.length; i++){
	  		if(v[i]<minValue){
	  			minIndex = i;
	  			minValue = v[i]; 
	  		}
	  	}
	  	return minIndex; 
	  }
	  
	public static final double[][] createConstantDiagonalMatrix(int dim, double c) {
		double[][] matrix = new double[dim][dim];
		for (int i = 0; i < dim; i++) {
			matrix[i][i] = c;
		}
		return matrix;
	}
	
	public static final double[][] upperTriangularMatrixUnverse(double[][] L) throws Exception {
		
		// Solve L*X = Id
		int dim = L.length;
		double[][] x = Utils.createConstantDiagonalMatrix(dim, 1.);
		for (int j = 0; j < dim; j++) {
			final double[] LJ = L[j];
			final double LJJ = LJ[j];
			final double[] xJ = x[j];
			for (int k = 0; k < dim; ++k) {
				xJ[k] /= LJJ;
			}
			for (int i = j + 1; i < dim; i++) {
				final double[] xI = x[i];
				final double LJI = LJ[i];
				for (int k = 0; k < dim; ++k) {
					xI[k] -= xJ[k] * LJI;
				}
			}
		}
        
		return new Array2DRowRealMatrix(x).transpose().getData();
	}
	
	public static final double[][] lowerTriangularMatrixUnverse(double[][] L) throws Exception {
		
		double[][] LT = new Array2DRowRealMatrix(L).transpose().getData();
		double[][] x = upperTriangularMatrixUnverse(LT);
		return new Array2DRowRealMatrix(x).transpose().getData();
	}
	
	/**
	 * Brute-force determinant calculation.
	 */
	public static final double calculateDeterminant(double[][] ai, int dim) {
		double det = 0;
		if (dim == 1) {
			det = ai[0][0];
		} else if (dim == 2) {
			det = ai[0][0] * ai[1][1] - ai[0][1] * ai[1][0];
		} else {
			double ai1[][] = new double[dim - 1][dim - 1];
			for (int k = 0; k < dim; k++) {
				for (int i1 = 1; i1 < dim; i1++) {
					int j = 0;
					for (int j1 = 0; j1 < dim; j1++) {
						if (j1 != k) {
							ai1[i1 - 1][j] = ai[i1][j1];
							j++;
						}
					}
				}
				if (k % 2 == 0) {
					det += ai[0][k] * calculateDeterminant(ai1, dim - 1);
				} else {
					det -= ai[0][k] * calculateDeterminant(ai1, dim - 1);
				}
			}
		}
		return det;
	}
}
