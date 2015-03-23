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

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import cern.colt.matrix.DoubleFactory1D;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;

import com.joptimizer.functions.ConvexMultivariateRealFunction;
import com.joptimizer.functions.FunctionsUtils;
import com.joptimizer.util.Utils;


public abstract class OptimizationRequestHandler {
	protected OptimizationRequestHandler successor = null;
	private OptimizationRequest request;
	private OptimizationResponse response;
	private Algebra ALG = Algebra.DEFAULT;
	private DoubleFactory1D F1 = DoubleFactory1D.dense;
	private DoubleFactory2D F2 = DoubleFactory2D.dense; 

	public void setOptimizationRequest(OptimizationRequest request) {
		this.request = request;
	}
	
	protected OptimizationRequest getOptimizationRequest() {
		return this.request;
	}
	
	protected void setOptimizationResponse(OptimizationResponse response) {
		this.response = response;
	}

	public OptimizationResponse getOptimizationResponse() {
		return this.response;
	}

	public int optimize() throws Exception {
		return forwardOptimizationRequest();
	}

	protected int forwardOptimizationRequest() throws Exception {
		if (successor != null) {
			successor.setOptimizationRequest(request);
			int retCode = successor.optimize();
			this.response = successor.getOptimizationResponse();
			return retCode;
		}
		throw new Exception("Failed to solve the problem");
	}
	
	private int dim = -1;
	protected final int getDim(){
		if(dim < 0){
			dim = this.request.getF0().getDim();
		}
		return dim;
	}
	
	protected final DoubleMatrix1D getInitialPoint(){
		return request.getInitialPoint();
	}
	
	protected final DoubleMatrix1D getNotFeasibleInitialPoint(){
		return request.getNotFeasibleInitialPoint();
	}
	
	protected final DoubleMatrix1D getInitialLagrangian(){
		return request.getInitialLagrangian();
	}
	
	protected final DoubleMatrix2D getA() {
		return request.getA();
	}
	
	private DoubleMatrix2D AT = null;
	protected final DoubleMatrix2D getAT() {
		if(AT==null && getA()!=null){
			AT = ALG.transpose(getA().copy());
		}
		return AT;
	}
	
	protected final DoubleMatrix1D getB() {
//		if(request.getB()==null){
//			request.setB(new double[1]);
//		}
		return request.getB();
	}
	
	protected final int getMaxIteration(){
		return request.getMaxIteration();
	}
	protected final double getTolerance(){
		return request.getTolerance();
	}
	protected final double getToleranceFeas(){
		return request.getToleranceFeas();
	}
	protected final double getToleranceInnerStep(){
		return request.getToleranceInnerStep();
	}
	protected final double getAlpha(){
		return request.getAlpha();
	}
	protected final double getBeta(){
		return request.getBeta();
	}
	protected final double getMu(){
		return request.getMu();
	}
	protected final boolean isCheckProgressConditions(){
		return request.isCheckProgressConditions();
	}
	protected final boolean isCheckKKTSolutionAccuracy(){
		return request.isCheckKKTSolutionAccuracy();
	}
	protected final double getToleranceKKT(){
		return request.getToleranceKKT();
	}
	
	/**
	 * Objective function.
	 */
	protected final ConvexMultivariateRealFunction getF0() {
		return request.getF0();
	}
	
	/**
	 * Objective function value at X.
	 */
	protected final boolean isInDomainF0(DoubleMatrix1D X) {
		double F0X = request.getF0().value(X.toArray());
		return !Double.isInfinite(F0X) && !Double.isNaN(F0X);
	}
	
	/**
	 * Objective function value at X.
	 */
	protected final double getF0(DoubleMatrix1D X) {
		return request.getF0().value(X.toArray());
	}

	/**
	 * Objective function gradient at X.
	 */
	protected final DoubleMatrix1D getGradF0(DoubleMatrix1D X) {
		return F1.make(request.getF0().gradient(X.toArray()));
	}

	/**
	 * Objective function hessian at X.
	 */
	protected final DoubleMatrix2D getHessF0(DoubleMatrix1D X) {
		double[][] hess = request.getF0().hessian(X.toArray());
		if(hess == FunctionsUtils.ZEROES_2D_ARRAY_PLACEHOLDER){
			return F2.make(X.size(), X.size());
		}else{
			return F2.make(hess);
		}
	}
	
	/**
	 * Inequality functions.
	 */
	protected final ConvexMultivariateRealFunction[] getFi() {
		return request.getFi();
	}
	
	/**
	 * The chose interior point method.
	 */
	protected final String getInteriorPointMethod() {
		return request.getInteriorPointMethod();
	}

	/**
	 * Inequality functions values at X.
	 */
	protected DoubleMatrix1D getFi(DoubleMatrix1D X){
		if(request.getFi()==null){
			return null;
		}
		double[] ret = new double[request.getFi().length];
		double[] x = X.toArray();
		for(int i=0; i<request.getFi().length; i++){
			ret[i] = request.getFi()[i].value(x);
		}
		return F1.make(ret);
	}
	
	/**
	 * Inequality functions gradients values at X.
	 */
//	protected DoubleMatrix2D getGradFi(DoubleMatrix1D X){
//		DoubleMatrix2D ret = F2.make(0, X.size());
//		double[] x = X.toArray();
//		for(int i=0; i<request.getFi().length; i++){
//			ret = F2.appendRows(ret, ALG.multOuter(F1.make(1, 1.), F1.make(request.getFi()[i].gradient(x)), null));
//		}
//		return ret;
//	}
	
	protected DoubleMatrix2D getGradFi(DoubleMatrix1D X) {
		DoubleMatrix2D ret = F2.make(request.getFi().length, X.size());
		double[] x = X.toArray();
		for(int i=0; i<request.getFi().length; i++){
			ret.viewRow(i).assign(request.getFi()[i].gradient(x));
		}
		return ret;
	}
	
	/**
	 * Inequality functions hessians values at X.
	 */
	protected DoubleMatrix2D[] getHessFi(DoubleMatrix1D X){
		DoubleMatrix2D[] ret = new DoubleMatrix2D[request.getFi().length];
		double[] x = X.toArray();
		for(int i=0; i<request.getFi().length; i++){
			double[][] hess = request.getFi()[i].hessian(x);
			if(hess == FunctionsUtils.ZEROES_2D_ARRAY_PLACEHOLDER){
				ret[i] = FunctionsUtils.ZEROES_MATRIX_PLACEHOLDER;
			}else{
				ret[i] = F2.make(hess);
			}
		}
		return ret;
	}
	
	/**
	 * Overriding this, a subclass can define some extra condition for exiting the iteration loop. 
	 */
	protected boolean checkCustomExitConditions(DoubleMatrix1D Y) {
		return false;
	}
	
	/**
	 * Find a solution of the linear (equalities) system A.x = b.
	 * @see "S.Boyd and L.Vandenberghe, Convex Optimization, p. 682"
	 */
	protected double[] findEqFeasiblePoint(double[][] A, double[] b) throws Exception {
		RealMatrix AT = new Array2DRowRealMatrix(A).transpose();
		int p = A.length;
		
		SingularValueDecomposition dFact1 = new SingularValueDecomposition(AT);
		int  rangoAT = dFact1.getRank();
		if(rangoAT!=p){
			throw new RuntimeException("Equalities matrix A must have full rank");
		}
		
		QRDecomposition dFact = new QRDecomposition(AT);
		//A = QR
		RealMatrix Q1Q2 = dFact.getQ();
		RealMatrix R0 = dFact.getR();
		RealMatrix Q1 = Q1Q2.getSubMatrix(0, AT.getRowDimension()-1, 0, p-1); 
		RealMatrix R = R0.getSubMatrix(0, p-1, 0, p-1);
		double[][] rData = R.copy().getData();
		//inversion
		double[][] rInvData = Utils.upperTriangularMatrixUnverse(rData);
		RealMatrix RInv = new Array2DRowRealMatrix(rInvData);
		
		//w = Q1 *	Inv([R]T) . b
		double[] w = Q1.operate(RInv.transpose().operate(new ArrayRealVector(b))).toArray();
		return w;
	}
	
	/**
	 * rPri := Ax - b
	 */
	protected DoubleMatrix1D rPri(DoubleMatrix1D X) {
		if(getA()==null){
			return F1.make(0);
		}
		return getA().zMult(X, getB().copy(), 1., -1., false);
	}
}
