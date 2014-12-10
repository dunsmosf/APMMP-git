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
package com.joptimizer.solvers;

import com.joptimizer.MainActivity;
import android.util.Log;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;

/**
 * H.v + [A]T.w = -g, <br>
 * A.v = -h, <br>
 * 
 * (H is square)
 * 
 * @see "S.Boyd and L.Vandenberghe, Convex Optimization, p. 542"
 * @author alberto trivellato (alberto.trivellato@gmail.com)
 */
public abstract class KKTSolver {

	protected RealMatrix H;
	protected RealMatrix A;
	protected RealMatrix AT;
	protected RealVector g;
	protected RealVector h;
	protected double toleranceKKT;
	protected boolean checkKKTSolutionAccuracy;
	private DoubleFactory2D F2 = DoubleFactory2D.dense;
	

	/**
	 * Returns two vectors v and w.
	 */
	public abstract double[][] solve() throws Exception;

	public void setHMatrix(double[][] HMatrix) {
		this.H = new Array2DRowRealMatrix(HMatrix, false);
	}

	public void setAMatrix(double[][] AMatrix) {
		if (AMatrix != null && AMatrix.length > 0) {
			this.A = new Array2DRowRealMatrix(AMatrix, false);
		}
	}

	public void setATMatrix(double[][] ATMatrix) {
		if (ATMatrix != null && ATMatrix.length > 0) {
			this.AT = new Array2DRowRealMatrix(ATMatrix, false);
		}
	}

	public void setGVector(double[] gVector) {
		this.g = new ArrayRealVector(gVector);
	}

	public void setHVector(double[] hVector) {
		if (hVector != null && hVector.length > 0) {
			this.h = new ArrayRealVector(hVector);
		}
	}

	/**
	 * Acceptable tolerance for system resolution.
	 */
	public void setToleranceKKT(double tolerance) {
		this.toleranceKKT = tolerance;
	}

	public void setCheckKKTSolutionAccuracy(boolean b) {
		this.checkKKTSolutionAccuracy = b;
	}
	
	/**
	 * Solve the KKT system as a whole.
	 * Useful only if A not null.
	 * @see "S.Boyd and L.Vandenberghe, Convex Optimization, p. 547"
	 */
	protected double[][] solveFullKKT(KKTSolver kktSolver) throws Exception{
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"solveFullKKT");
		
		//if the KKT matrix is nonsingular, then H + [A]T.A > 0.
		RealMatrix HATA = H.add(AT.multiply(A));
		try{
			CholeskyDecomposition cFact = new CholeskyDecomposition(HATA);
			cFact.getSolver().getInverse();
		}catch(Exception e){
			throw new Exception("singular KKT system");
		}
		
		kktSolver.setHMatrix(HATA.getData());//this is positive
		kktSolver.setAMatrix(A.getData());
		kktSolver.setATMatrix(AT.getData());
		kktSolver.setGVector(g.toArray());
		
		if(h!=null){
			RealVector ATQh = AT.operate(MatrixUtils.createRealIdentityMatrix(A.getRowDimension()).operate(h));
			RealVector gATQh = g.add(ATQh);
			kktSolver.setGVector(gATQh.toArray());
			kktSolver.setHVector(h.toArray());
		}

		return kktSolver.solve();
	}

	protected boolean checkKKTSolutionAccuracy(RealVector v, RealVector w) {
		// build the full KKT matrix
		double norm;
		
		DoubleMatrix2D M = F2.make(this.H.getData());
		if (this.A != null) {
			if(h!=null){
				DoubleMatrix2D[][] parts = { { F2.make(this.H.getData()), F2.make(this.AT.getData()) },
						 { F2.make(this.A.getData()), null } };
				M = F2.compose(parts);
				RealMatrix KKT = new Array2DRowRealMatrix(M.toArray());
				RealVector X = v.append(w);
				RealVector Y = g.append(h);
				// check ||KKT.X+Y||<tolerance
				norm = KKT.operate(X).add(Y).getNorm();
			}else{
				//H.v + [A]T.w = -g
				norm = H.operate(v).add(AT.operate(w)).add(g).getNorm();
			}
		}else{
			// check ||H.X+h||<tolerance
			norm = H.operate(v).add(g).getNorm(); 
		}
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"KKT solution error: " + norm);
		return norm < toleranceKKT;
	}
}
