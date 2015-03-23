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
package com.joptimizer.functions;

import cern.colt.matrix.DoubleFactory1D;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.colt.matrix.linalg.Property;

/**
 * 1/2 x.P.x + q.x + r
 * 
 * @author alberto trivellato (alberto.trivellato@gmail.com)
 */
public class QuadraticMultivariateRealFunction implements ConvexMultivariateRealFunction {

	/**
	 * Dimension of the function argument.
	 */
	protected int dim = -1;

	/**
	 * Quadratic factor.
	 */
	protected DoubleMatrix2D P = null;

	/**
	 * Linear factor.
	 */
	protected DoubleMatrix1D q = null;

	/**
	 * Constant factor.
	 */
	protected double r = 0;
	
	private Algebra ALG = Algebra.DEFAULT;

	public QuadraticMultivariateRealFunction(double[][] PMatrix, double[] qVector, double r) {
		
		this.P = (PMatrix!=null)? DoubleFactory2D.dense.make(PMatrix) : null;
		this.q = (qVector!=null)? DoubleFactory1D.dense.make(qVector) : null;
		this.r = r;
		
		if(P==null && q==null){
			throw new IllegalArgumentException("Impossible to create the function");
		}
		if (P != null && !Property.DEFAULT.isSquare(P)) {
			throw new IllegalArgumentException("Not quadratic argument");
		}
		
		this.dim = (P != null)? P.columns() : q.size();
		if (this.dim < 0) {
			throw new IllegalArgumentException("Impossible to create the function");
		}
	}

	public final double value(double[] X) {
		DoubleMatrix1D x = DoubleFactory1D.dense.make(X);
		double ret = r;
		if (P != null) {
			ret += 0.5 * ALG.mult(x, ALG.mult(P, x));
		}
		if (q != null) {
			ret += ALG.mult(q, x);
		}
		return ret;
	}

	public final double[] gradient(double[] X) {
		DoubleMatrix1D x = DoubleFactory1D.dense.make(X);
		DoubleMatrix1D ret = null;
		if(P!=null){
			if (q != null) {
				ret = P.zMult(x, q.copy(), 1, 1, false);
			} else {
				ret = ALG.mult(P, x);
			}
		}else{
			ret = q.copy();
		}
		return ret.toArray();
		
	}

	public final double[][] hessian(double[] X) {
		DoubleMatrix2D ret = null;
		if(P!=null){
			ret = P.copy();
		}else{
			//ret = DoubleFactory2D.dense.make(dim, dim);
			return FunctionsUtils.ZEROES_2D_ARRAY_PLACEHOLDER;
		}
		return ret.toArray();
	}

	public DoubleMatrix2D getP() {
		return P;
	}

	public DoubleMatrix1D getQ() {
		return q;
	}

	public double getR() {
		return r;
	}

	public int getDim() {
		return this.dim;
	}

}
