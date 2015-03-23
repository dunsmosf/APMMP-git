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
import cern.colt.matrix.linalg.SeqBlas;
import cern.jet.math.Functions;
import cern.jet.math.Mult;

import com.joptimizer.functions.FunctionsUtils;
import com.joptimizer.solvers.BasicKKTSolver;
import com.joptimizer.solvers.KKTSolver;
import com.joptimizer.util.Utils;

/**
 * Primal-dual interior-point method.
 * 
 * @see "S.Boyd and L.Vandenberghe, Convex Optimization, p. 609"
 * @author alberto trivellato (alberto.trivellato@gmail.com)
 */
public class PrimalDualMethod extends OptimizationRequestHandler {

	private Algebra ALG = Algebra.DEFAULT;
	private DoubleFactory1D F1 = DoubleFactory1D.dense;
	private DoubleFactory2D F2 = DoubleFactory2D.dense; 
//	private DoubleMatrix2D myA = null;
//	private DoubleMatrix2D myAT = null;
//	private DoubleMatrix1D myB = null;
	private KKTSolver kktSolver;
	

	@Override
	public int optimize() throws Exception {
		Log.i(MainActivity.JOPTIMIZER_LOGTAG,"optimize");
		long tStart = System.currentTimeMillis();
		OptimizationResponse response = new OptimizationResponse();

		// @TODO: check assumptions!!!
//		if(getA()!=null){
//			if(ALG.rank(getA())>=getA().rows()){
//				throw new IllegalArgumentException("A-rank must be less than A-rows");
//			}
//		}
		
		DoubleMatrix1D X0 = getInitialPoint();
		if(X0==null){
			DoubleMatrix1D X0NF = getNotFeasibleInitialPoint();
			if(X0NF!=null){
				double rPriX0NFNorm = Math.sqrt(ALG.norm2(rPri(X0NF)));
				DoubleMatrix1D fiX0NF = getFi(X0NF);
				int maxIndex = Utils.getMaxIndex(fiX0NF.toArray());
				double maxValue = fiX0NF.get(maxIndex);
				if (Log.isLoggable(MainActivity.JOPTIMIZER_LOGTAG, Log.DEBUG)) {
					Log.d(MainActivity.JOPTIMIZER_LOGTAG,"rPriX0NFNorm :  " + rPriX0NFNorm);
					Log.d(MainActivity.JOPTIMIZER_LOGTAG,"X0NF         :  " + ArrayUtils.toString(X0NF.toArray()));
					Log.d(MainActivity.JOPTIMIZER_LOGTAG,"fiX0NF       :  " + ArrayUtils.toString(fiX0NF.toArray()));
				}
				if(maxValue<0 && rPriX0NFNorm<=getToleranceFeas()){
					//the provided not-feasible starting point is already feasible
					Log.d(MainActivity.JOPTIMIZER_LOGTAG,"the provided initial point is already feasible");
					X0 = X0NF;
				}
			}
			if(X0 == null){
				BasicPhaseIPDM bf1 = new BasicPhaseIPDM(this);
				X0 = bf1.findFeasibleInitialPoint();
			}
		}
		
		//check X0 feasibility
		DoubleMatrix1D fiX0 = getFi(X0);
		int maxIndex = Utils.getMaxIndex(fiX0.toArray());
		double maxValue = fiX0.get(maxIndex);
		double rPriX0Norm = Math.sqrt(ALG.norm2(rPri(X0)));
		if(maxValue >= 0 || rPriX0Norm > getToleranceFeas()){
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"rPriX0Norm  : " + rPriX0Norm);
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"ineqX0      : " + ArrayUtils.toString(fiX0.toArray()));
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"max ineq index: " + maxIndex);
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"max ineq value: " + maxValue);
			throw new Exception("initial point must be strictly feasible");
		}

		DoubleMatrix1D V0 = (getA()!=null)? F1.make(getA().rows()) : F1.make(0);
		
		DoubleMatrix1D L0 = getInitialLagrangian();
		if(L0!=null){
			for (int j = 0; j < L0.size(); j++) {
				// must be >0
				if(L0.get(j) <= 0){
					throw new IllegalArgumentException("initial lagrangian must be strictly > 0");
				}
			}
		}else{
			//L0 = F1.make(getFi().length, 1.);// must be >0
			L0 = F1.make(getFi().length, Math.min(1,(double)getDim()/getFi().length));// must be >0
		}
		if(Log.isLoggable(MainActivity.JOPTIMIZER_LOGTAG, Log.DEBUG)){
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"X0:  " + ArrayUtils.toString(X0.toArray()));
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"V0:  " + ArrayUtils.toString(V0.toArray()));
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"L0:  " + ArrayUtils.toString(L0.toArray()));
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"toleranceFeas:  " + getToleranceFeas());
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"tolerance    :  " + getTolerance());
		}

		DoubleMatrix1D X = X0;
		DoubleMatrix1D V = V0;
		DoubleMatrix1D L = L0;
		//double F0X;
		//DoubleMatrix1D gradF0X = null;
		//DoubleMatrix1D fiX = null;
		//DoubleMatrix2D GradFiX = null;
		//DoubleMatrix1D rPriX = null;
		//DoubleMatrix1D rCentXLt = null;
		//DoubleMatrix1D rDualXLV = null;
		//double rPriXNorm = Double.NaN;
		//double rCentXLtNorm = Double.NaN;
		//double rDualXLVNorm = Double.NaN;
		//double normRXLVt = Double.NaN;
		double previousF0X = Double.NaN;
		double previousRPriXNorm = Double.NaN;
		double previousRDualXLVNorm = Double.NaN;
		double previousSurrDG = Double.NaN;
		double t;
		int iteration = 0;
		while (true) {
			
			iteration++;
		    // iteration limit condition
			if (iteration == getMaxIteration()+1) {
				response.setReturnCode(OptimizationResponse.WARN);
				Log.w(MainActivity.JOPTIMIZER_LOGTAG,"Max iterations limit reached");
				break;
			}
			
		    double F0X = getF0(X);
			if(Log.isLoggable(MainActivity.JOPTIMIZER_LOGTAG, Log.DEBUG)){
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"iteration: " + iteration);
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"X=" + ArrayUtils.toString(X.toArray()));
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"L=" + ArrayUtils.toString(L.toArray()));
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"V=" + ArrayUtils.toString(V.toArray()));
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"f0(X)=" + F0X);
			}
			
//			if(!Double.isNaN(previousF0X)){
//				if (previousF0X < F0X) {
//					throw new Exception("critical minimization problem");
//				}
//			}
//			previousF0X = F0X;
			
			// determine functions evaluations
			DoubleMatrix1D gradF0X = getGradF0(X);
			DoubleMatrix1D fiX     = getFi(X);
			DoubleMatrix2D GradFiX = getGradFi(X);
			DoubleMatrix2D[] HessFiX = getHessFi(X);
			
			// determine t
			double surrDG = getSurrogateDualityGap(fiX, L);
			t = getMu() * getFi().length / surrDG;
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"t:  " + t);
						
			// determine residuals
			DoubleMatrix1D rPriX    = rPri(X);
			DoubleMatrix1D rCentXLt = rCent(fiX, L, t);
			DoubleMatrix1D rDualXLV = rDual(GradFiX, gradF0X, L, V);
			double rPriXNorm    = Math.sqrt(ALG.norm2(rPriX));
			double rCentXLtNorm = Math.sqrt(ALG.norm2(rCentXLt));
			double rDualXLVNorm = Math.sqrt(ALG.norm2(rDualXLV));
			double normRXLVt    = Math.sqrt(Math.pow(rPriXNorm, 2) + Math.pow(rCentXLtNorm, 2) + Math.pow(rDualXLVNorm, 2));
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"rPri  norm: " + rPriXNorm);
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"rCent norm: " + rCentXLtNorm);
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"rDual norm: " + rDualXLVNorm);
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"surrDG    : " + surrDG);
			
			// custom exit condition
			if(checkCustomExitConditions(X)){
				response.setReturnCode(OptimizationResponse.SUCCESS);
				break;
			}
			
			// exit condition
			if (rPriXNorm <= getToleranceFeas() && rDualXLVNorm <= getToleranceFeas() && surrDG <= getTolerance()) {
				response.setReturnCode(OptimizationResponse.SUCCESS);
				break;
			}
			
		  // progress conditions
			if(isCheckProgressConditions()){
				if (!Double.isNaN(previousRPriXNorm)
					&& !Double.isNaN(previousRDualXLVNorm)
					&& !Double.isNaN(previousSurrDG)) {
					if (  (previousRPriXNorm <= rPriXNorm && rPriXNorm >= getToleranceFeas())
						|| (previousRDualXLVNorm <= rDualXLVNorm && rDualXLVNorm >= getToleranceFeas())) {
						Log.w(MainActivity.JOPTIMIZER_LOGTAG,"No progress achieved, exit iterations loop without desired accuracy");
						response.setReturnCode(OptimizationResponse.WARN);
						break;
					}
				}
				previousRPriXNorm = rPriXNorm;
				previousRDualXLVNorm = rDualXLVNorm;
				previousSurrDG = surrDG;
			}

			// compute primal-dual search direction
			// a) prepare 11.55 system
			DoubleMatrix2D HessSum = getHessF0(X);
			for (int j = 0; j < getFi().length; j++) {
				if(HessFiX[j] != FunctionsUtils.ZEROES_MATRIX_PLACEHOLDER){
					HessSum.assign(HessFiX[j].copy().assign(Mult.mult(L.get(j))), Functions.plus);
				}
				//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"HessSum    : " + ArrayUtils.toString(HessSum.toArray()));
			}
			
			
			
			
//			DoubleMatrix2D GradSum = F2.make(getDim(), getDim());
//			for (int j = 0; j < getFi().length; j++) {
//				DoubleMatrix1D g = GradFiX.viewRow(j);
//				GradSum.assign(ALG.multOuter(g, g, null).assign(Mult.mult(-L.get(j) / fiX.get(j))), Functions.plus);
//				//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"GradSum    : " + ArrayUtils.toString(GradSum.toArray()));
//			}
			DoubleMatrix2D GradSum = F2.make(getDim(), getDim());
			for (int j = 0; j < getFi().length; j++) {
				final double c = -L.getQuick(j) / fiX.getQuick(j);
				DoubleMatrix1D g = GradFiX.viewRow(j);
				SeqBlas.seqBlas.dger(c, g, g, GradSum);
				//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"GradSum    : " + ArrayUtils.toString(GradSum.toArray()));
			}

			DoubleMatrix2D Hpd = HessSum.assign(GradSum, Functions.plus);
			//DoubleMatrix2D Hpd = getHessF0(X).assign(HessSum, Functions.plus).assign(GradSum, Functions.plus);

			DoubleMatrix1D gradSum = F1.make(getDim());
			for (int j = 0; j < getFi().length; j++) {
				gradSum.assign(GradFiX.viewRow(j).copy().assign(Mult.div(-t * fiX.get(j))), Functions.plus);
				//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"gradSum    : " + ArrayUtils.toString(gradSum.toArray()));
			}
			DoubleMatrix1D g = null;
			if(getAT()==null){
				g = gradF0X.copy().assign(gradSum, Functions.plus);
			}else{
				g = gradF0X.copy().assign(gradSum, Functions.plus).assign(ALG.mult(getAT(), V), Functions.plus);
			}
			
			// b) solving 11.55 system
			if(this.kktSolver==null){
				this.kktSolver = new BasicKKTSolver();
			}
			//KKTSolver solver = new DiagonalKKTSolver();
			if(isCheckKKTSolutionAccuracy()){
				kktSolver.setCheckKKTSolutionAccuracy(true);
				kktSolver.setToleranceKKT(getToleranceKKT());
			}
			kktSolver.setHMatrix(Hpd.toArray());
			kktSolver.setGVector(g.toArray());
			if(getA()!=null){
				kktSolver.setAMatrix(getA().toArray());
				kktSolver.setATMatrix(getAT().toArray());
				kktSolver.setHVector(rPriX.toArray());
			}
			double[][] sol = kktSolver.solve();
			DoubleMatrix1D stepX = F1.make(sol[0]);
			DoubleMatrix1D stepV = (sol[1]!=null)? F1.make(sol[1]) : F1.make(0);
			if(Log.isLoggable(MainActivity.JOPTIMIZER_LOGTAG, Log.DEBUG)){
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"stepX: " + ArrayUtils.toString(stepX.toArray()));
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"stepV: " + ArrayUtils.toString(stepV.toArray()));
			}

			// c) solving for L
			DoubleMatrix1D stepL = null;
			DoubleMatrix2D diagFInv = F2.diagonal(fiX.copy().assign(Functions.inv));
			DoubleMatrix2D diagL = F2.diagonal(L);
			stepL = ALG.mult(diagFInv, ALG.mult(diagL, ALG.mult(GradFiX, stepX))).assign(Mult.mult(-1)).assign(ALG.mult(diagFInv, rCentXLt), Functions.plus);
			if(Log.isLoggable(MainActivity.JOPTIMIZER_LOGTAG, Log.DEBUG)){
				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"stepL: " + ArrayUtils.toString(stepL.toArray()));
			}

			// line search and update
			// a) sMax computation 
			double sMax = Double.MAX_VALUE;
			for (int j = 0; j < getFi().length; j++) {
				if (stepL.get(j) < 0) {
					sMax = Math.min(-L.get(j) / stepL.get(j), sMax);
				}
			}
			sMax = Math.min(1, sMax);
			double s = 0.99 * sMax;
			// b) backtracking with f
			DoubleMatrix1D X1 = F1.make(X.size());
			DoubleMatrix1D L1 = F1.make(L.size());
			DoubleMatrix1D V1 = F1.make(V.size());
			DoubleMatrix1D fiX1 = null;
			DoubleMatrix1D gradF0X1 = null;
			DoubleMatrix2D GradFiX1 = null;
			DoubleMatrix1D rPriX1 = null;
			DoubleMatrix1D rCentX1L1t = null;
			DoubleMatrix1D rDualX1L1V1 = null;
			int cnt = 0;
			boolean areAllNegative = true;
			while (cnt < 500) {
				cnt++;
				// X1 = X + s*stepX
				X1 = stepX.copy().assign(Mult.mult(s)).assign(X, Functions.plus);
				DoubleMatrix1D ineqValueX1 = getFi(X1);
				areAllNegative = true;
				for (int j = 0; areAllNegative && j < getFi().length; j++) {
					areAllNegative = (Double.compare(ineqValueX1.get(j), 0.) < 0);
				}
				if (areAllNegative) {
					break;
				}
				s = getBeta() * s;
			}
			
			if(!areAllNegative){
				//exited from the feasible region
				throw new Exception("Optimization failed: impossible to remain within the faesible region");
			}
			
			Log.d(MainActivity.JOPTIMIZER_LOGTAG,"s: " + s);
			// c) backtracking with norm
			double previousNormRX1L1V1t = Double.NaN;
			cnt = 0;
			while (cnt < 500) {
				cnt++;
				X1 = stepX.copy().assign(Mult.mult(s)).assign(X, Functions.plus);
				L1 = stepL.copy().assign(Mult.mult(s)).assign(L, Functions.plus);
				V1 = stepV.copy().assign(Mult.mult(s)).assign(V, Functions.plus);
//				X1.assign(stepX.copy().assign(Mult.mult(s)).assign(X, Functions.plus));
//				L1.assign(stepL.copy().assign(Mult.mult(s)).assign(L, Functions.plus));
//				V1.assign(stepV.copy().assign(Mult.mult(s)).assign(V, Functions.plus));
				
				if (isInDomainF0(X1)) {
					fiX1 = getFi(X1);
					gradF0X1 = getGradF0(X1);
					GradFiX1 = getGradFi(X1);
					
					rPriX1 = rPri(X1);
					rCentX1L1t = rCent(fiX1, L1, t);
					rDualX1L1V1 = rDual(GradFiX1, gradF0X1, L1, V1);
					//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"rPriX1     : "+ArrayUtils.toString(rPriX1.toArray()));
					//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"rCentX1L1t : "+ArrayUtils.toString(rCentX1L1t.toArray()));
					//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"rDualX1L1V1: "+ArrayUtils.toString(rDualX1L1V1.toArray()));
					double normRX1L1V1t = Math.sqrt(ALG.norm2(rPriX1)
							                          + ALG.norm2(rCentX1L1t)
							                          + ALG.norm2(rDualX1L1V1));
					//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"normRX1L1V1t: "+normRX1L1V1t);
					if (normRX1L1V1t <= (1 - getAlpha() * s) * normRXLVt) {
						break;
					}
					
					if (!Double.isNaN(previousNormRX1L1V1t)) {
						if (previousNormRX1L1V1t <= normRX1L1V1t) {
							Log.w(MainActivity.JOPTIMIZER_LOGTAG,"No progress achieved in backtracking with norm");
							break;
						}
					}
					previousNormRX1L1V1t = normRX1L1V1t;
				}
				
				s = getBeta() * s;
				//Log.d(MainActivity.JOPTIMIZER_LOGTAG,"s: " + s);
			}

			// update
			X = X1;
			V = V1;
			L = L1;
			
//			fiX     = fiX1;
//			gradF0X = gradF0X1;
//			GradFiX  = GradFiX1;
//			
//			rPriX    = rPriX1;
//			rCentXLt = rCentX1L1t;
//			rDualXLV = rDualX1L1V1;
//			rPriXNorm    = Math.sqrt(ALG.norm2(rPriX));
//			rCentXLtNorm = Math.sqrt(ALG.norm2(rCentXLt));
//			rDualXLVNorm = Math.sqrt(ALG.norm2(rDualXLV));
//			normRXLVt = Math.sqrt(Math.pow(rPriXNorm, 2) + Math.pow(rCentXLtNorm, 2) + Math.pow(rDualXLVNorm, 2));
//			if(Log.isLoggable(MainActivity.JOPTIMIZER_LOGTAG, Log.DEBUG)){
//				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"rPri  norm: " + rPriXNorm);
//				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"rCent norm: " + rCentXLtNorm);
//				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"rDual norm: " + rDualXLVNorm);
//				Log.d(MainActivity.JOPTIMIZER_LOGTAG,"surrDG    : " + surrDG);
//			}
		}

		long tStop = System.currentTimeMillis();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"time: " + (tStop - tStart));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol : " + ArrayUtils.toString(X.toArray()));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"ret code: " + response.getReturnCode());
		response.setSolution(X.toArray());
		setOptimizationResponse(response);
		return response.getReturnCode();
	}

	/**
	 * Surrogate duality gap.
	 * 
	 * @see "Convex Optimization, 11.59"
	 */
	private double getSurrogateDualityGap(DoubleMatrix1D fiX, DoubleMatrix1D L) {
		return -ALG.mult(fiX, L);
	}

	/**
	 * @see "Convex Optimization, p. 610"
	 */
	private DoubleMatrix1D rDual(DoubleMatrix2D GradFiX, DoubleMatrix1D gradF0X, DoubleMatrix1D L, DoubleMatrix1D V) {
		if(getA()==null){
			return GradFiX.zMult(L, gradF0X.copy(), 1., 1., true);
		}
		return getA().zMult(V, GradFiX.zMult(L, gradF0X.copy(), 1., 1., true), 1., 1., true);
	}

	/**
	 * @see "Convex Optimization, p. 610"
	 */
//	private DoubleMatrix1D rPri(DoubleMatrix1D X) {
//		if(getA()==null){
//			return F1.make(0);
//		}
//		return getA().zMult(X, getB().copy(), 1., -1., false);
//	}

	/**
	 * @see "Convex Optimization, p. 610"
	 */
	private DoubleMatrix1D rCent(DoubleMatrix1D fiX, DoubleMatrix1D L, double t) {
		DoubleMatrix2D Diag = F2.diagonal(L);
		DoubleMatrix1D OnesOnT = F1.make(getFi().length, 1. / t);
		return Diag.zMult(fiX, OnesOnT, -1., -1., false);
	}
	
	public void setKKTSolver(KKTSolver kktSolver) {
		this.kktSolver = kktSolver;
	}

}
