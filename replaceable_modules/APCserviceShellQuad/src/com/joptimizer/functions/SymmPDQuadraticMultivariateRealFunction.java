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

import cern.colt.matrix.linalg.CholeskyDecomposition;

/**
 * 1/2 * x.P.x + q.x + r, 
 * P positive definite and symmetric.
 * 
 * @author alberto trivellato (alberto.trivellato@gmail.com)
 */
public abstract class SymmPDQuadraticMultivariateRealFunction extends PDQuadraticMultivariateRealFunction implements StrictlyConvexMultivariateRealFunction {

	public SymmPDQuadraticMultivariateRealFunction(double[][] PMatrix, double[] qVector, double r) {
		this(PMatrix, qVector, r, true);
	}
	
	public SymmPDQuadraticMultivariateRealFunction(double[][] PMatrix, double[] qVector, double r, boolean checkSPD) {
		super(PMatrix, qVector, r, false);
		if(checkSPD){
			try{
				CholeskyDecomposition cDecomp = new CholeskyDecomposition(P);
				if(!cDecomp.isSymmetricPositiveDefinite()){
					throw new Exception();
				}
			}catch(Exception e){
				throw new IllegalArgumentException("P not symmetric positive definite");
			}
		}
	}

}
