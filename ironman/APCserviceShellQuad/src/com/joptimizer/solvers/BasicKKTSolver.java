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

import org.apache.commons.lang3.ArrayUtils;
import com.joptimizer.MainActivity;
import android.util.Log;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularMatrixException;

import com.joptimizer.util.Utils;

/**
 * H.v + [A]T.w = -g, <br>
 * A.v = -h
 * 
 * @see "S.Boyd and L.Vandenberghe, Convex Optimization, p. 542"
 * @author alberto trivellato (alberto.trivellato@gmail.com)
 */
public class BasicKKTSolver extends KKTSolver {

	

	/**
	 * Returns the two vectors v and w.
	 */
	@Override
	public double[][] solve() throws Exception {

		RealVector v = null;// dim equals cols of A
		RealVector w = null;// dim equals rank of A

		if (Log.isLoggable(MainActivity.JOPTIMIZER_LOGTAG, Log.DEBUG)) {
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"H: " + ArrayUtils.toString(H.getData()));
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"g: " + ArrayUtils.toString(g.toArray()));
		}
		RealMatrix HInv;
		try{
			HInv = Utils.squareMatrixInverse(H);
		}catch(SingularMatrixException e){
			HInv = null;
		}

		if (HInv != null) {
			// Solving KKT system via elimination
			if (A != null) {
				if (Log.isLoggable(MainActivity.JOPTIMIZER_LOGTAG, Log.DEBUG)) {
					Log.d(MainActivity.JOPTIMIZER_LOGTAG,"A: " + ArrayUtils.toString(A.getData()));
					if (h != null) {
						Log.d(MainActivity.JOPTIMIZER_LOGTAG,"h: " + ArrayUtils.toString(h.toArray()));
					}
				}
				RealMatrix AHInv = A.multiply(HInv);
				RealMatrix MenoS = AHInv.multiply(AT);
				RealMatrix MenoSInv = Utils.squareMatrixInverse(MenoS);
				if (h == null || Double.compare(h.getNorm(), 0.) == 0) {
					w = MenoSInv.operate(AHInv.operate(g)).mapMultiply(-1.);
				} else {
					w = MenoSInv.operate(h.subtract(AHInv.operate(g)));
				}
				v = HInv.operate(g.add(AT.operate(w)).mapMultiply(-1.));
			} else {
				w = null;
				v = HInv.operate(g).mapMultiply(-1.);
			}
		} else {
			// Solving the full KKT system
			if(A!=null){
				KKTSolver kktSolver = new BasicKKTSolver();
				kktSolver.setCheckKKTSolutionAccuracy(false);
				double[][] fullSol =  this.solveFullKKT(new BasicKKTSolver());
				v = new ArrayRealVector(fullSol[0]);
				w = new ArrayRealVector(fullSol[1]);
			}else{
				//@TODO: try with rescaled H
				throw new Exception("KKT solution failed");
			}
		}

		// solution checking
		if (this.checkKKTSolutionAccuracy && !this.checkKKTSolutionAccuracy(v, w)) {
			Log.e(MainActivity.JOPTIMIZER_LOGTAG,"KKT solution failed");
			throw new Exception("KKT solution failed");
		}

		double[][] ret = new double[2][];
		ret[0] = v.toArray();
		ret[1] = (w != null) ? w.toArray() : null;
		return ret;
	}
}
