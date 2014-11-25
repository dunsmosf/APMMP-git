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
package com.joptimizer.optimizers;

import org.apache.commons.lang3.ArrayUtils;
import com.joptimizer.MainActivity;
import android.util.Log;

import cern.colt.matrix.DoubleFactory1D;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.Functions;
import cern.jet.math.Mult;

import com.joptimizer.solvers.BasicKKTSolver;
import com.joptimizer.solvers.KKTSolver;
import com.joptimizer.util.Utils;

/**
 * Linear equality constrained newton optimizer, with feasible starting point.
 * 
 * @see "S.Boyd and L.Vandenberghe, Convex Optimization, p. 521"
 * @author alberto trivellato (alberto.trivellato@gmail.com)
 */
public class NewtonLEConstrainedFSP extends OptimizationRequestHandler {

	private Algebra ALG = Algebra.DEFAULT;
	private DoubleFactory1D F1 = DoubleFactory1D.dense;
	private DoubleFactory2D F2 = DoubleFactory2D.dense;
	private KKTSolver kktSolver;
	
	
	public NewtonLEConstrainedFSP(boolean activateChain){
		if(activateChain){
			this.successor = new NewtonLEConstrainedISP(true);
		}
	}
	
	public NewtonLEConstrainedFSP(){
		this(false);
	}

	@Override
	public int optimize() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"optimize");
		OptimizationResponse response = new OptimizationResponse();
		
	    //checking responsibility
		if (getFi() != null) {
			// forward to the chain
			return forwardOptimizationRequest();
		}
		
		long tStart = System.currentTimeMillis();
		
		//initial point must be feasible (i.e., satisfy x in domF and Ax = b).
		DoubleMatrix1D X0 = getInitialPoint();
		double rPriX0Norm = (X0 != null)? Math.sqrt(ALG.norm2(rPri(X0))) : 0d;
		//if (X0 == null	|| (getA()!=null && Double.compare(ALG.norm2(getA().zMult(X0, getB().copy(), 1., -1., false)), 0d) != 0)) {
		//if (X0 == null	|| rPriX0Norm > Utils.getDoubleMachineEpsilon()) {	
		if (X0 == null	|| rPriX0Norm > getTolerance()) {	
			// infeasible starting point, forward to the chain
			return forwardOptimizationRequest();
		}
		
		if(Log.isLoggable(MainActivity.JOPTIMIZER_LOGTAG, Log.DEBUG)){
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"X0:  " + ArrayUtils.toString(X0.toArray()));
		}
		DoubleMatrix1D X = X0;
		double F0X;
		//double previousF0X = Double.NaN;
		double previousLambda = Double.NaN;
		int iteration = 0;
		while (true) {
			iteration++;
			F0X = getF0(X);
			if(Log.isLoggable(MainActivity.JOPTIMIZER_LOGTAG, Log.DEBUG)){
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"iteration " + iteration);
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"X=" + ArrayUtils.toString(X.toArray()));
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"f(X)=" + F0X);
			}
			
//			if(!Double.isNaN(previousF0X)){
//				if (previousF0X < F0X) {
//					throw new Exception("critical minimization problem");
//				}
//			}
//			previousF0X = F0X;
			
			// custom exit condition
			if(checkCustomExitConditions(X)){
				response.setReturnCode(OptimizationResponse.SUCCESS);
				break;
			}
			
			DoubleMatrix1D gradX = getGradF0(X);
			DoubleMatrix2D hessX = getHessF0(X);
			
			double gradXNorm = Math.sqrt(ALG.norm2(gradX));
			if(gradXNorm < Utils.getDoubleMachineEpsilon()){
				response.setReturnCode(OptimizationResponse.SUCCESS);
				break;
			}

			// Newton step and decrement
			if(this.kktSolver==null){
				this.kktSolver = new BasicKKTSolver();
			}
			if(isCheckKKTSolutionAccuracy()){
				kktSolver.setCheckKKTSolutionAccuracy(isCheckKKTSolutionAccuracy());
				kktSolver.setToleranceKKT(getToleranceKKT());
			}
			kktSolver.setHMatrix(hessX.toArray());
			kktSolver.setGVector(gradX.toArray());
			if(getA()!=null){
				kktSolver.setAMatrix(getA().toArray());
				kktSolver.setATMatrix(getAT().toArray());
			}
			double[][] sol = kktSolver.solve();
			DoubleMatrix1D step = F1.make(sol[0]);
			DoubleMatrix1D w = (sol[1]!=null)? F1.make(sol[1]) : F1.make(0);
			if(Log.isLoggable(MainActivity.JOPTIMIZER_LOGTAG, Log.DEBUG)){
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"stepX: " + ArrayUtils.toString(step.toArray()));
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"w    : " + ArrayUtils.toString(w.toArray()));
			}

			// exit condition: check the Newton decrement
			double lambda = Math.sqrt(ALG.mult(step, ALG.mult(hessX, step)));
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"lambda: " + lambda);
			if (lambda / 2. <= getTolerance()) {
				response.setReturnCode(OptimizationResponse.SUCCESS);
				break;
			}
			
			// iteration limit condition
			if (iteration == getMaxIteration()) {
				response.setReturnCode(OptimizationResponse.WARN);
				Log.w(MainActivity.JOPTIMIZER_LOGTAG,"Max iterations limit reached");
				break;
			}
			
		    // progress conditions
			if(isCheckProgressConditions()){
				if (!Double.isNaN(previousLambda) && previousLambda <= lambda) {
					Log.w(MainActivity.JOPTIMIZER_LOGTAG,"No progress achieved, exit iterations loop without desired accuracy");
					response.setReturnCode(OptimizationResponse.WARN);
					break;
				}
			}
			previousLambda = lambda;

			// backtracking line search
			double s = 1d;
			DoubleMatrix1D X1 = null;
			int cnt = 0;
			while (cnt < 250) {
				cnt++;
				// @TODO: can we use simplification 9.7.1 ??
				X1 = X.copy().assign(step.copy().assign(Mult.mult(s)), Functions.plus);// x + t*step
				//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"X1: "+ArrayUtils.toString(X1.toArray()));
				
				if (isInDomainF0(X1)) {
					double condSX = getF0(X1);
					//NB: this will also check !Double.isNaN(getF0(X1))
					double condDX = F0X + getAlpha() * s * ALG.mult(gradX, step);
					//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"condSX: "+condSX);
					//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"condDX: "+condDX);
					if (condSX <= condDX) {
						break;
					}
				}
				s = getBeta() * s;
			}
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"s: " + s);

			// update
			X = X1;
		}

		long tStop = System.currentTimeMillis();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"time: " + (tStop - tStart));
		response.setSolution(X.toArray());
		setOptimizationResponse(response);
		return response.getReturnCode();
	}
	
	public void setKKTSolver(KKTSolver kktSolver) {
		this.kktSolver = kktSolver;
	}
}
