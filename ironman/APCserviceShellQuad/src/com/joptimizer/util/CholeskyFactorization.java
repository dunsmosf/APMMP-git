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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.joptimizer.MainActivity;
import android.util.Log;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;

/**
 * Cholesky factorization and inverse for symmetric and positive matrix.
 * 
 * @author alberto trivellato (alberto.trivellato@gmail.com)
 */
public class CholeskyFactorization {

	double[][] Q;
	double[][] L;
	double[][] LT;
	List<Double> eigenvalues;
	
	
	public CholeskyFactorization(double[][] Q) throws Exception{
		this.Q = Q;
		factorize();
	}
	
	/**
	 * Cholesky factorization L of psd matrix, Q = L.LT
	 */
	private void factorize() throws Exception{
		if (!MatrixUtils.isSymmetric(new Array2DRowRealMatrix(Q), Utils.getDoubleMachineEpsilon())) {
			throw new Exception("Matrix is not symmetric");
		}
		
		int N = Q.length;
		double[][] L = new double[N][N];
		this.eigenvalues = new ArrayList<Double>();

		for (int i = 0; i < N; i++) {
			for (int j = 0; j <= i; j++) {
				double sum = 0.0;
				for (int k = 0; k < j; k++) {
					sum += L[i][k] * L[j][k];
				}
				if (i == j){
					double d = Math.sqrt(Q[i][i] - sum); 
					if (Double.isNaN(d) || d*d<Utils.getDoubleMachineEpsilon()) {//d*d is a Q's eigenvalue
						Log.w(MainActivity.JOPTIMIZER_LOGTAG,"Not positive eigenvalues: "+d*d);
						throw new Exception("not positive definite matrix");
					}
					L[i][i] = d;
					this.eigenvalues.add(this.eigenvalues.size(), d*d);
				} else {
					L[i][j] = 1.0 / L[j][j] * (Q[i][j] - sum);
				}
			}
		}
		
		this.L = L;
	}
	
	public double[][] getInverse() {

		//QInv = LTInv * LInv, but for symmetry (QInv=QInvT)
		//QInv = LInvT * LTInvT = LInvT * LInv, so
		//LInvT = LTInv, and we calculate
		//QInv = LInvT * LInv

		double[][] lTData = getLT();
		int dim = lTData.length;

		// LTInv calculation (it will be x)
		double[] diag = new double[dim];
		Arrays.fill(diag, 1.);
		double[][] x = MatrixUtils.createRealDiagonalMatrix(diag).getData();
		for (int j = 0; j < dim; j++) {
			final double[] lTJ = lTData[j];
			final double lTJJ = lTJ[j];
			final double[] xJ = x[j];
			for (int k = 0; k < dim; ++k) {
				xJ[k] /= lTJJ;
			}
			for (int i = j + 1; i < dim; i++) {
				final double[] xI = x[i];
				final double lTJI = lTJ[i];
				for (int k = 0; k < dim; ++k) {
					xI[k] -= xJ[k] * lTJI;
				}
			}
		}

		// transposition (L is upper-triangular)
		double[][] LInvTData = new double[dim][dim];
		for (int i = 0; i < dim; i++) {
			double[] LInvTDatai = LInvTData[i];
			for (int j = i; j < dim; j++) {
				LInvTDatai[j] = x[j][i];
			}
		}

		// QInv
		// NB: LInvT is upper-triangular, so LInvT[i][j]=0 if i>j
		final double[][] QInvData = new double[dim][dim];
		for (int row = 0; row < dim; row++) {
			final double[] LInvTDataRow = LInvTData[row];
			final double[] QInvDataRow = QInvData[row];
			for (int col = row; col < dim; col++) {// symmetry of QInv
				final double[] LInvTDataCol = LInvTData[col];
				double sum = 0;
				for (int i = col; i < dim; i++) {// upper triangular
					sum += LInvTDataRow[i] * LInvTDataCol[i];
				}
				QInvDataRow[col] = sum;
				QInvData[col][row] = sum;// symmetry of QInv
			}
		}

		return QInvData;
	}
	
	public double[][] getL() {
		return L;
	}

	public double[][] getLT() {
		if(this.LT == null){
			this.LT = new Array2DRowRealMatrix(this.L).transpose().getData();
		}
		return this.LT;
	}

	public List<Double> getEigenvalues() {
		return eigenvalues;
	}
}
