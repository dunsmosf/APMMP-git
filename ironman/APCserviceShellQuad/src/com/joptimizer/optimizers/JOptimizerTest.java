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

import junit.framework.TestCase;

import org.apache.commons.lang3.ArrayUtils;
import com.joptimizer.MainActivity;
import android.util.Log;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import cern.colt.matrix.DoubleFactory1D;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.jet.math.Functions;
import cern.jet.math.Mult;

import com.joptimizer.functions.ConvexMultivariateRealFunction;
import com.joptimizer.functions.FunctionsUtils;
import com.joptimizer.functions.LinearMultivariateRealFunction;
import com.joptimizer.functions.LogTransformedPosynomial;
import com.joptimizer.functions.PDQuadraticMultivariateRealFunction;
import com.joptimizer.functions.QuadraticMultivariateRealFunction;
import com.joptimizer.functions.StrictlyConvexMultivariateRealFunction;
import com.joptimizer.util.Utils;

/**
 * @author alberto trivellato (alberto.trivellato@gmail.com)
 */
public class JOptimizerTest extends TestCase {
	private Algebra ALG = Algebra.DEFAULT;
	private DoubleFactory1D F1 = DoubleFactory1D.dense;
	private DoubleFactory2D F2 = DoubleFactory2D.dense;
	

	/**
	 * The simplest test.
	 */
	public void testSimplest() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testSimplest");
		
		// Objective function
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(new double[] { 1. }, 0);
		
		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[1];
		inequalities[0] = new LinearMultivariateRealFunction(new double[]{-1}, 0.);
		
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setFi(inequalities);
		or.setInitialPoint(new double[] { 1 });
		or.setToleranceFeas(1.E-8);
		or.setTolerance(1.E-9);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : "	+ objectiveFunction.value(sol));

		assertEquals(0., sol[0], 0.000000001);
	}	
	
	/**
	 * Quadratic objective, no constraints.
	 */
	public void testNewtownUnconstrained() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testNewtownUnconstrained");
		RealMatrix PMatrix = new Array2DRowRealMatrix(new double[][] { 
				{ 1.68, 0.34, 0.38 },
				{ 0.34, 3.09, -1.59 }, 
				{ 0.38, -1.59, 1.54 } });
		RealVector qVector = new ArrayRealVector(new double[] { 0.018, 0.025, 0.01 });

		// Objective function.
		double theta = 0.01522;
		RealMatrix P = PMatrix.scalarMultiply(theta);
		RealVector q = qVector.mapMultiply(-1);
		PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P.getData(), q.toArray(), 0);
		
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { 0.04, 0.50, 0.46 });
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : "	+ objectiveFunction.value(sol));

		// we already know the analytic solution of the problem
		// sol = -invQ * C
		RealMatrix QInv = Utils.squareMatrixInverse(P);
		RealVector benchSol = QInv.operate(q).mapMultiply(-1);
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"benchSol   : " + ArrayUtils.toString(benchSol.toArray()));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"benchValue : " + objectiveFunction.value(benchSol.toArray()));

		assertEquals(benchSol.getEntry(0), sol[0], 0.000000000000001);
		assertEquals(benchSol.getEntry(1), sol[1], 0.000000000000001);
		assertEquals(benchSol.getEntry(2), sol[2], 0.000000000000001);
	}
	
	/**
	 * Quadratic objective with linear equality constraints and feasible starting point.
	 */
	public void testNewtonLEConstrainedFSP() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testNewtonLEConstrainedFSP");
		DoubleMatrix2D PMatrix = F2.make(new double[][] { 
				{ 1.68, 0.34, 0.38 },
				{ 0.34, 3.09, -1.59 }, 
				{ 0.38, -1.59, 1.54 } });
		DoubleMatrix1D qVector = F1.make(new double[] { 0.018, 0.025, 0.01 });
		
		// Objective function.
		double theta = 0.01522;
		DoubleMatrix2D P = PMatrix.assign(Mult.mult(theta));
		DoubleMatrix1D q = qVector.assign(Mult.mult(-1));
		PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P.toArray(), q.toArray(), 0);
		
		//equalities
		double[][] A = new double[][]{{1,1,1}};
		double[] b = new double[]{1};
		
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { 0.04, 0.50, 0.46 });
		or.setA(A);
		or.setB(b);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals(0.04632311555988555, sol[0], 0.000000000000001);
		assertEquals(0.5086308460954377,  sol[1], 0.000000000000001);
		assertEquals(0.44504603834467693, sol[2], 0.000000000000001);
	}
	
	/**
	 * Quadratic objective with linear equality constraints and infeasible starting point.
	 */
	public void testNewtonLEConstrainedISP() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testNewtonLEConstrainedISP");
		DoubleMatrix2D PMatrix = F2.make(new double[][] { 
				{ 1.68, 0.34, 0.38 },
				{ 0.34, 3.09, -1.59 }, 
				{ 0.38, -1.59, 1.54 } });
		DoubleMatrix1D qVector = F1.make(new double[] { 0.018, 0.025, 0.01 });

		// Objective function (Risk-Aversion).
		double theta = 0.01522;
		DoubleMatrix2D P = PMatrix.assign(Mult.mult(theta));
		DoubleMatrix1D q = qVector.assign(Mult.mult(-1));
		PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P.toArray(), q.toArray(), 0);

		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { 1, 1, 1 });
		or.setA(new double[][] { { 1, 1, 1 } });
		or.setB(new double[] { 1 });
		
	  	//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals(0.04632311555988555, sol[0], 0.000000000000001);
		assertEquals(0.5086308460954377,  sol[1], 0.000000000000001);
		assertEquals(0.44504603834467693, sol[2], 0.000000000000001);
	}
	
	/**
	 * Quadratic objective with linear eq and ineq.
	 */
	public void testPrimalDualMethod() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testPrimalDualMethod");
		DoubleMatrix2D PMatrix = F2.make(new double[][] { 
    		{ 1.68, 0.34, 0.38 },
				{ 0.34, 3.09, -1.59 }, 
				{ 0.38, -1.59, 1.54 } });
		DoubleMatrix1D qVector = F1.make(new double[] { 0.018, 0.025, 0.01 });
		
		// Objective function.
		double theta = 0.01522;
		DoubleMatrix2D P = PMatrix.assign(Mult.mult(theta));
		DoubleMatrix1D q = qVector.assign(Mult.mult(-1));
    	PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P.toArray(), q.toArray(), 0);

    	//equalities
    	double[][] A = new double[][]{{1,1,1}};
		double[] b = new double[]{1};

		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[3];
		inequalities[0] = new LinearMultivariateRealFunction(new double[]{-1, 0, 0}, 0);
		inequalities[1] = new LinearMultivariateRealFunction(new double[]{0, -1, 0}, 0);
		inequalities[2] = new LinearMultivariateRealFunction(new double[]{0, 0, -1}, 0);

		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { 0.2, 0.2, 0.6 });
		or.setFi(inequalities);
		or.setA(A);
		or.setB(b);
		or.setToleranceFeas(1.E-12);
		or.setTolerance(1.E-12);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals(0.04632311555988555, sol[0], 0.000000001);
		assertEquals(0.5086308460954377,  sol[1], 0.000000001);
		assertEquals(0.44504603834467693, sol[2], 0.000000001);
  }
	
	/**
	 * The same as testPrimalDualMethod, but with barrier-method.
	 * Quadratic objective with linear eq and ineq.
	 */
	public void testBarrierMethod() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testBarrierMethod");
		DoubleMatrix2D PMatrix = F2.make(new double[][] { 
    		{ 1.68, 0.34, 0.38 },
				{ 0.34, 3.09, -1.59 }, 
				{ 0.38, -1.59, 1.54 } });
		DoubleMatrix1D qVector = F1.make(new double[] { 0.018, 0.025, 0.01 });
		
		// Objective function (Risk-Aversion).
		double theta = 0.01522;
		DoubleMatrix2D P = PMatrix.assign(Mult.mult(theta));
		DoubleMatrix1D q = qVector.assign(Mult.mult(-1));
    	PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P.toArray(), q.toArray(), 0);

    	//equalities
    	double[][] A = new double[][]{{1,1,1}};
    	double[] b = new double[]{1};

		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[3];
		inequalities[0] = new LinearMultivariateRealFunction(new double[]{-1, 0, 0}, 0);
		inequalities[1] = new LinearMultivariateRealFunction(new double[]{0, -1, 0}, 0);
		inequalities[2] = new LinearMultivariateRealFunction(new double[]{0, 0, -1}, 0);

		OptimizationRequest or = new OptimizationRequest();
		or.setInteriorPointMethod(JOptimizer.BARRIER_METHOD);
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { 0.3, 0.3, 0.4 });
		or.setFi(inequalities);
		or.setA(A);
		or.setB(b);
		or.setTolerance(1.E-12);
		or.setToleranceInnerStep(1.E-5); 
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals(0.04632311555988555, sol[0], 0.00000001);
		assertEquals(0.5086308460954377,  sol[1], 0.00000001);
		assertEquals(0.44504603834467693, sol[2], 0.00000001);
  }
	
	/**
	 * Linear programming in 2D.
	 */
	public void testLinearProgramming2D() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testLinearProgramming2D");
		
	  // START SNIPPET: LinearProgramming-1
		
		// Objective function (plane)
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(new double[] { -1., -1. }, 4);

		//inequalities (polyhedral feasible set G.X<H )
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[4];
		double[][] G = new double[][] {{4./3., -1}, {-1./2., 1.}, {-2., -1.}, {1./3., 1.}};
		double[] H = new double[] {2., 1./2., 2., 1./2.};
		inequalities[0] = new LinearMultivariateRealFunction(G[0], -H[0]);
		inequalities[1] = new LinearMultivariateRealFunction(G[1], -H[1]);
		inequalities[2] = new LinearMultivariateRealFunction(G[2], -H[2]);
		inequalities[3] = new LinearMultivariateRealFunction(G[3], -H[3]);
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setFi(inequalities);
		//or.setInitialPoint(new double[] {0.0, 0.0});//initial feasible point, not mandatory
		or.setToleranceFeas(1.E-9);
		or.setTolerance(1.E-9);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
	  // END SNIPPET: LinearProgramming-1
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol: " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value  : " + objectiveFunction.value(sol));
		assertEquals(1.5, sol[0], 0.000000001);
		assertEquals(0.0, sol[1], 0.000000001);
  }
	
	/**
	 * Very simple linear.
	 */
	public void testSimpleLinear() throws Exception{
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testSimpleLinear");
	  // Objective function (plane)
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(new double[] { 1., 1. }, 0.);

	  //equalities
		//DoubleMatrix2D AMatrix = F2.make(new double[][]{{1,-1}});
		//DoubleMatrix1D BVector = F1.make(new double[]{0});
		
		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[4];
		inequalities[0] = new LinearMultivariateRealFunction(new double[]{ 1., 0.}, -3.);
		inequalities[1] = new LinearMultivariateRealFunction(new double[]{-1., 0.},  0.);
		inequalities[2] = new LinearMultivariateRealFunction(new double[]{ 0., 1.}, -3.);
		inequalities[3] = new LinearMultivariateRealFunction(new double[]{ 0.,-1.},  0.);
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setFi(inequalities);
//		or.setInitialPoint(new double[] {1., 1.});//initial feasible point, not mandatory
		or.setToleranceFeas(1.E-12);
		or.setTolerance(1.E-12);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol: " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value  : " + objectiveFunction.value(sol));
		assertEquals(0.0, sol[0], 0.000000000001);
		assertEquals(0.0, sol[1], 0.000000000001);
	}
	
	/**
	 * Quadratic programming in 2D.
	 */
	public void testQuadraticProgramming2D() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testQuadraticProgramming2D");
		
		// START SNIPPET: QuadraticProgramming-1
		
		// Objective function
		double[][] P = new double[][] {{ 1., 0.4 }, { 0.4, 1. }};
		PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P, null, 0);

		//equalities
		double[][] A = new double[][]{{1,1}};
		double[] b = new double[]{1};

		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[2];
		inequalities[0] = new LinearMultivariateRealFunction(new double[]{-1, 0}, 0);
		inequalities[1] = new LinearMultivariateRealFunction(new double[]{0, -1}, 0);
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { 0.1, 0.9});
		//or.setFi(inequalities); //if you want x>0 and y>0
		or.setA(A);
		or.setB(b);
		or.setToleranceFeas(1.E-12);
		or.setTolerance(1.E-12);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
	  // END SNIPPET: QuadraticProgramming-1
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol: " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value  : " + objectiveFunction.value(sol));
		assertEquals(0.5, sol[0], 0.0000000000001);
		assertEquals(0.5, sol[1], 0.0000000000001);
    }
	
	/**
	 * Minimize -x-y s.t. 
	 * x^2 + y^2 <= 4 (1/2 [x y] [I] [x y]^T - 2 <= 0)
	 */
	public void testLinearCostQuadraticInequalityOptimizationProblem() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testLinearCostQuadraticInequalityOptimizationProblem");

		double[] minimizeF = new double[] { -1.0, -1.0 };
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(minimizeF, 0.0);

		// inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[1];
		double[][] PMatrix = new double[][] { { 1.0, 0.0 }, { 0.0, 1.0 } };
		double[] qVector = new double[] { 0.0, 0.0 };
		double r = -2;

		inequalities[0] = new QuadraticMultivariateRealFunction(PMatrix, qVector, r); // x^2+y^2 <=4
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setFi(inequalities);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}

		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol: " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value  : " + objectiveFunction.value(sol));
		assertEquals(Math.sqrt(2.0), sol[0], 1e-6);
		assertEquals(Math.sqrt(2.0), sol[1], 1e-6);
	}
	
	/**
	 * Minimize x s.t. 
	 * x+y=4
	 * y >= x^2. 
	 */
	public void testLinearCostLinearEqualityQuadraticInequalityOptimizationProblem()	throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testLinearCostLinearEqualityQuadraticInequalityOptimizationProblem");
		double[] minimizeF = new double[] { 1.0, 0.0 };
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(minimizeF, 0.0);

		// Equalities:
		double[][] equalityAMatrix = new double[][] { { 1.0, 1.0 } };
		double[] equalityBVector = new double[] { 4.0 };

		// inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[1];
		double[][] PMatrix = new double[][] { { 2.0, 0.0 }, { 0.0, 0.0 } };
		double[] qVector = new double[] { 0.0, -1.0 };
		double r = 0.0;

		inequalities[0] = new QuadraticMultivariateRealFunction(PMatrix, qVector, r); // x^2 - y < 0
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setFi(inequalities);
		or.setA(equalityAMatrix);
		or.setB(equalityBVector);
		//or.setInitialPoint(new double[]{-0.5,4.5});
		//or.setNotFeasibleInitialPoint(new double[]{4,0});
		//or.setInteriorPointMethod(JOptimizer.BARRIER_METHOD);
		or.setCheckKKTSolutionAccuracy(true);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}

		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol: " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value  : " + objectiveFunction.value(sol));
		assertEquals(1.0 / 2.0 * (-1.0 - Math.sqrt(17.0)), sol[0], 1e-5);
		assertEquals(1.0 / 2.0 * ( 9.0 + Math.sqrt(17.0)), sol[1], 1e-5);
	}
	
	/**
	 * Quadratically constrained quadratic programming in 2D.
	 */
	public void testSimpleQCQuadraticProgramming() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testSimpleQCQuadraticProgramming");
		
		// Objective function
		double[][] P = new double[][] { { 2., 0. },{ 0., 2. }};
		PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P, null, 0);

		//equalities
		double[][] A = new double[][]{{1,1}};
		double[] b = new double[]{1};

		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[1];
		inequalities[0] = FunctionsUtils.createCircle(2, 2, new double[]{0., 0.});
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { 0.2, 0.8});
		or.setA(A);
		or.setB(b);
		or.setFi(inequalities);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol: " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value  : " + objectiveFunction.value(sol));
		assertEquals(0.5, sol[0], 0.0000000001);//NB: this is driven by the equality constraint
		assertEquals(0.5, sol[1], 0.0000000001);//NB: this is driven by the equality constraint
  }
	
	/**
	 * Linear objective, quadratically constrained.
	 * It simulates the type of optimization occurring in feasibility searching
	 * in a problem with constraints:
	 * x^2 < 1
	 */
	public void testQCQuadraticProgramming() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testQCQuadraticProgramming");
		
		// Objective function (linear (x,s)->s)
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(new double[] { 0, 1 }, 0);
		
		//inequalities x^2 < 1 + s
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[1];
		double[][] P1 = new double[][] { { 2., 0. },{ 0., 0. }};
		double[] c1 = new double[] { 0, -1 };
		inequalities[0] = new QuadraticMultivariateRealFunction(P1, c1, -1);
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		//or.setInitialPoint(new double[] { 2, 5});//@FIXME: why this shows a poor convergence?
		//or.setInitialPoint(new double[] {-0.1,-0.989});
		or.setInitialPoint(new double[] {1.2, 2.});
		or.setFi(inequalities);
		or.setCheckKKTSolutionAccuracy(true);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals( 0., sol[0], 0.0000001);
		assertEquals(-1., sol[1], 0.0000001);
  }
	
	/**
	 * Linear objective, linear constrained.
	 * It simulates the type of optimization occurring in feasibility searching
	 * in a problem with constraints:
	 * -x < 0
	 *  x -1 < 0
	 */
	public void testLinearProgramming() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testLinearProgramming");
		
		// Objective function (linear (x,s)->s)
		double[] c0 = new double[] { 0, 1 };
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(c0, 0);

		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[2];
		//-x -s < 0
		double[] c1 = new double[] { -1, -1 };
		inequalities[0] = new LinearMultivariateRealFunction(c1, 0);
		// x -s -1 < 0
		double[] c2 = new double[] { 1, -1 };
		inequalities[1] = new LinearMultivariateRealFunction(c2, -1);
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { 1.4, 0.5});
		//or.setInitialPoint(new double[] {-0.1,-0.989});
		//or.setInitialPoint(new double[] {1.2, 2.});
		or.setFi(inequalities);
		//or.setInitialLagrangian(new double[]{0.005263, 0.1});
		or.setMu(100d); 
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals( 0.5, sol[0], 0.000001);
		assertEquals(-0.5, sol[1], 0.000001);
  }
	
	/**
	 * Quadratic objective, no constraints.
	 */
	public void testQCQuadraticProgramming2() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testQCQuadraticProgramming2");
		
		// Objective function
		double[][] P = new double[][] { { 1., 0. },{ 0., 1. }};
		PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P, null, 0);

		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { 2., 2.});
		or.setToleranceFeas(1.E-12);
		or.setTolerance(1.E-12);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol: " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value  : " + objectiveFunction.value(sol));
		assertEquals(0., sol[0], 0.000000000000001);
		assertEquals(0., sol[1], 0.000000000000001);
  }
	
	/**
	 * Quadratically constrained quadratic programming in 2D.
	 */
	public void testQCQuadraticProgramming2D() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testQCQuadraticProgramming2D");
		
		// START SNIPPET: QCQuadraticProgramming-1
		
		// Objective function
		double[][] P = new double[][] { { 1., 0.4 },{ 0.4, 1. }};
		PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P, null, 0);

		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[1];
		inequalities[0] = FunctionsUtils.createCircle(2, 1.75, new double[]{-2, -2});
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { -2., -2.});
		or.setFi(inequalities);
		or.setCheckKKTSolutionAccuracy(true);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
	  // END SNIPPET: QCQuadraticProgramming-1
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals(-2 + 1.75/Math.sqrt(2), sol[0], 0.0000001);//-0.762563132923542
		assertEquals(-2 + 1.75/Math.sqrt(2), sol[1], 0.0000001);//-0.762563132923542
  }
	
	/**
	 * The same as testQCQuadraticProgramming2D, but without initial point.
	 */
	public void testQCQuadraticProgramming2DNoInitialPoint() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testQCQuadraticProgramming2DNoInitialPoint");
		
		// Objective function
		double[][] P = new double[][] { { 1., 0.4 },{ 0.4, 1. }};
		PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P, null, 0);

		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[1];
		inequalities[0] = FunctionsUtils.createCircle(2, 1.75, new double[]{-2, -2});
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setFi(inequalities);
		or.setCheckKKTSolutionAccuracy(true);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals(-2 + 1.75/Math.sqrt(2), sol[0], 0.0000001);//-0.762563132923542
		assertEquals(-2 + 1.75/Math.sqrt(2), sol[1], 0.0000001);//-0.762563132923542
  }
	
	/**
	 * The basic PhaseI problem relative to testQCQuadraticProgramming2DNoInitialPoint. 
	 * min(s) s.t.
	 * (x+2)^2 + (y+2)^2 -1.75 < s 
	 * This problem can't be solved without an initial point, because the relative PhaseI problem
	 * min(r) s.t.
	 * (x+2)^2 + (y+2)^2 -1.75 -s < r
	 * is unbounded.
	 */
	public void testQCQuadraticProgramming2DNoInitialPointPhaseI() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testQCQuadraticProgramming2DNoInitialPointPhaseI");
		
		// Objective function
		double[] f0 = new double[]{0, 0, 1};//s
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(f0, 0);

		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[1];
		inequalities[0] = new ConvexMultivariateRealFunction() {
			
			public double value(double[] X) {
				double x = X[0];
				double y = X[1];
				double s = X[2];
				return Math.pow(x+2, 2)+Math.pow(y+2, 2)-1.75-s;
			}
			
			public double[] gradient(double[] X) {
				double x = X[0];
				double y = X[1];
				double s = X[2];
				return new double[]{2*(x+2), 2*(y+2), -1 };
			}
			
			public double[][] hessian(double[] X) {
				double x = X[0];
				double y = X[1];
				double s = X[2];
				double[][] ret = new double[3][3];
				ret[0][0] = 2;
				ret[1][1] = 2;
				return ret;
			}
			
			public int getDim() {
				return 3;
			}
		};
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		//or.setInitialPoint(new double[] {0.5,0.5,94375.0});
		or.setInitialPoint(new double[] {-2, -2, 10});
		or.setFi(inequalities);
		or.setCheckKKTSolutionAccuracy(true);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals(-2.  , sol[0], 0.0000001);
		assertEquals(-2.  , sol[1], 0.0000001);
		assertEquals(-1.75, sol[2], 0.0000001);
  }
	
	/**
	 * Second-order cone programming in 2D.
	 */
	public void testSOConeProgramming2D() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testSOConeProgramming2D");
		
		// START SNIPPET: SOConeProgramming-1
		
		// Objective function (plane)
		double[] c = new double[] { -1., -1. };
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(c, 6);
		
		//equalities
		double[][] A = new double[][]{{1./4.,-1.}};
		double[] b = new double[]{0};

		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[4];

//      constraint in the form ||A1x+b1||<=c1.x+d1,
//		double[][] A1 = new double[][] {{ 0, 1. }};
//		double[] b1 = new double[] { 0 };
//		double[] c1 = new double[] { 1./3., 0. };
//		double d1 = 1./3.;
        //quadratic parametrization of the conic problem
		double[][] P1 = new double[][] {{ -2./9., 0. },{ 0., 2}};
		double[] Q1 = new double[] { -2./9, 0. };
		double r1 = -1./9.; 
		inequalities[0] = new QuadraticMultivariateRealFunction(P1, Q1, r1);
		//-x<1
		inequalities[1] = new LinearMultivariateRealFunction(new double[] { -1., 0. }, -1.);

//      constraint in the form ||A2x+b2||<=c2.x+d2,
//		double[][] A2 = new double[][] {{ 0, 1. }};
//		double[] b2 = new double[] { 0};
//		double[] c2 = new double[] { -1./2., 0};
//		double d2 = 1;
        //quadratic parametrization of the conic problem
		double[][] P2 = new double[][] {{ -1./2., 0. },{ 0., 2. }};
		double[] Q2 = new double[] { 1., 0. };
		double r2 = -1.;
		inequalities[2] = new QuadraticMultivariateRealFunction(P2, Q2, r2);
		//x<2
		inequalities[3] = new LinearMultivariateRealFunction(new double[] { 1., 0. }, -2.);
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { 0., 0.});
		or.setFi(inequalities);
		or.setA(A);
		or.setB(b);
		or.setToleranceFeas(1.E-6);
		or.setTolerance(2.E-6);
		or.setMaxIteration(500);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
	  // END SNIPPET: SOConeProgramming-1
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol: " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value  : " + objectiveFunction.value(sol));
		assertEquals(4./3., sol[0], 0.000001);
		assertEquals(1./3., sol[1], 0.000001);
  }
	
	/**
	 * Semidefinite programming.
	 * dim=2 QCQP viewed as a dim=3 SDP.
	 */
	public void testSemidefiniteProgramming() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testSemidefiniteProgramming");
		
		// START SNIPPET: SDProgramming-1
		
		// Objective function (variables (x,y,t), dim = 3)
		double[] c = new double[]{0,0,1};
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(c, 0);
		
		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[2];
		
		//constraint in the form (A0.x+b0)T.(A0.x+b0) - c0.x - d0 - t < 0
//		double[][] A0 = new double[][]{{-Math.sqrt(21./50.),0.},{-Math.sqrt(2)/5., -1./Math.sqrt(2)}};
//		double[] b0 = new double[] { 0, 0 };
//		double[] c0 = new double[] { 0, 0 };
//		double d0 = 0;
		//quadratic parameterization of the constraint
		double[][] P0 = new double[][] {{ 1, 0.4, 0 },{ 0.4, 1, 0},{ 0, 0, 0}};//P0 comes from 2.A0T.A0
		double[] q0 = new double[] { 0, 0., -1};//q0 comes from 2.A0.b0 - c0
		double r0 = 0;//r0 comes from b0.b0 - d0
		inequalities[0] = new QuadraticMultivariateRealFunction(P0, q0, r0);
		
		//constraint in the form (A1.x+b1)T.(A1.x+b1) - c1.x - d1 < 0
//		double[][] A1 = new double[][]{{1,0},{0,1}};
//		double[] b1 = new double[] { 2, 2 };
//		double[] c1 = new double[] { 0, 0 };
//		double d1 = 1.75^2;
		//quadratic parameterization of the constraint
		double[][] P1 = new double[][] {{2,0,0},{0,2,0},{0,0,0}};//P1 comes from 2.A1T.A1
		double[] q1 = new double[] {4,4,0};//q1 comes from 2.A1.b1 - c1
		double r1 = 8 - Math.pow(1.75, 2);//r1 comes from b1.b1 - d1 
		inequalities[1] = new QuadraticMultivariateRealFunction(P1, q1, r1);
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { -2, -2, 10});
		or.setFi(inequalities);
		or.setTolerance(1.E-10);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		// END SNIPPET: SDProgramming-1
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
        
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals(-2 + 1.75/Math.sqrt(2), sol[0], 0.00000000001);//-0,7625631329235418
		assertEquals(-2 + 1.75/Math.sqrt(2), sol[1], 0.00000000001);//-0,7625631329235418
  }
	
	/**
	 * Simple geometric programming.
	 * Solve the following LP
	 * 	min x s.t.
	 * 	 2<x<3
	 * as a GP.	
	 */
	public void testGeometricProgramming1() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testGeometricProgramming1");
		
		// Objective function (variables y, dim = 1)
		double[] a01 = new double[]{1};
		double b01 = 0;
		double[] a11 = new double[]{-1};
		double b11 = Math.log(2);
		double[] a21 = new double[]{1};
		double b21 = Math.log(1./3.);
		ConvexMultivariateRealFunction objectiveFunction = new LogTransformedPosynomial(new double[][]{a01}, new double[]{b01});
		
		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[2];
		inequalities[0] = new LogTransformedPosynomial(new double[][]{a11}, new double[]{b11});
		inequalities[1] = new LogTransformedPosynomial(new double[][]{a21}, new double[]{b21});
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setFi(inequalities);
		or.setInitialPoint(new double[]{Math.log(2.5)});
		or.setTolerance(1.E-12);
		//or.setInteriorPointMethod(JOptimizer.BARRIER_METHOD);//if you prefer the barrier-method
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
        
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals(Math.log(2), sol[0], 0.00000000001);
  }
	
	/**
	 * Geometric programming with dim=2.
	 */
	public void testGeometricProgramming2D() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testGeometricProgramming2D");
		
		// START SNIPPET: GeometricProgramming-1
		
		// Objective function (variables (x,y), dim = 2)
		double[] a01 = new double[]{2,1};
		double b01 = 0;
		double[] a02 = new double[]{3,1};
		double b02 = 0;
		ConvexMultivariateRealFunction objectiveFunction = new LogTransformedPosynomial(new double[][]{a01, a02}, new double[]{b01, b02});
		
		//constraints
		double[] a11 = new double[]{1,0};
		double b11 = Math.log(1);
		double[] a21 = new double[]{0,1};
		double b21 = Math.log(1);
		double[] a31 = new double[]{-1,-1.};
		double b31 = Math.log(0.7);
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[3];
		inequalities[0] = new LogTransformedPosynomial(new double[][]{a11}, new double[]{b11});
		inequalities[1] = new LogTransformedPosynomial(new double[][]{a21}, new double[]{b21});
		inequalities[2] = new LogTransformedPosynomial(new double[][]{a31}, new double[]{b31});
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setFi(inequalities);
		or.setInitialPoint(new double[]{Math.log(0.9), Math.log(0.9)});
		//or.setInteriorPointMethod(JOptimizer.BARRIER_METHOD);//if you prefer the barrier-method
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		// END SNIPPET: GeometricProgramming-1
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
        
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		assertEquals(Math.log(0.7), sol[0], 0.00000001);//-0,35667494
		assertEquals(Math.log(1),   sol[1], 0.00000001);// 0.0
  }
	
	/**
	 * Exponential objective with quadratic ineq. 
	 * f0 = exp[z^2], z=(x-1, y-2) 
	 * f1 = x^2+y^2 < 3^2
	 */
	public void testOptimize7() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testOptimize7");
		// START SNIPPET: JOptimizer-1
		
		//you can implement the function definition using whatever linear algebra library you want, you are not tied to Colt
		StrictlyConvexMultivariateRealFunction objectiveFunction = new StrictlyConvexMultivariateRealFunction() {

			public double value(double[] X) {
				DoubleMatrix1D Z = F1.make(new double[] { X[0] - 1, X[1] - 2, });
				return Math.exp(Z.zDotProduct(Z));
			}

			public double[] gradient(double[] X) {
				DoubleMatrix1D Z = F1.make(new double[] { X[0] - 1, X[1] - 2, });
				return Z.assign(Mult.mult(2 * Math.exp(Z.zDotProduct(Z)))).toArray();
			}

			public double[][] hessian(double[] X) {
				DoubleMatrix1D Z = F1.make(new double[] { X[0] - 1, X[1] - 2, });
				double d = Math.exp(Z.zDotProduct(Z));
				DoubleMatrix2D ID = F2.identity(2);
				DoubleMatrix2D ret = ALG.multOuter(Z, Z, null).assign(ID, Functions.plus).assign(Mult.mult(2 * d));
				return ret.toArray();
			}

			public int getDim() {
				return 2;
			}
		};

		// Inquality constraints
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[1];
		inequalities[0] = FunctionsUtils.createCircle(2, 3);//dim=2, radius=3, center=(0,0)

		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setInitialPoint(new double[] { 0.2, 0.2 });
		or.setFi(inequalities);

		// optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		// END SNIPPET: JOptimizer-1
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		double value = objectiveFunction.value(sol);
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + value);
		assertEquals(1., sol[0], 0.0000001);
		assertEquals(2., sol[1], 0.0000001);
	}
	
	/**
	 * Test QP in 3-dim
	 * Min( 1/2 * xT.x) s.t.
   * 	x1 <= -10
   * This problem can't be solved without an initial point, 
   * because the relative PhaseI problem is undetermined.
	 * Submitted 01/06/2012 by Klaas De Craemer
	 */
	public void testQP() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testQP");
		
		// Objective function
        double[][] pMatrix = new double[][] {
        		{ 1, 0, 0 },
        		{ 0, 1, 0 },
        		{ 0, 0, 1 }};
        PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(pMatrix, null, 0);

        //inequalities
        ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[1];
        inequalities[0] = new LinearMultivariateRealFunction(new double[]{1, 0, 0}, 10);// x1 <= -10
        
        //optimization problem
        OptimizationRequest or = new OptimizationRequest();
        or.setF0(objectiveFunction);
        or.setInitialPoint(new double[]{-11, 1, 1});
        or.setFi(inequalities);
//        or.setToleranceFeas(1.E-12);
//        or.setTolerance(1.E-12);
        
        //optimization
        JOptimizer opt = new JOptimizer();
        opt.setOptimizationRequest(or);
        int returnCode = opt.optimize();
        
        if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
        
        OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		double value = objectiveFunction.value(sol);
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + value);
		assertEquals(-10., sol[0], 1.E-6);
		assertEquals(  0., sol[1], 1.E-6);
		assertEquals(  0., sol[2], 1.E-6);
		assertEquals( 50.,  value, 1.E-6);
	}	
	
	/**
	 * Test QP.
	 * Submitted 12/07/2012 by Katharina Schwaiger.
	 */
	public void testQPScala() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testQPScala");
		
		double[][] P = new double[2][2];
	    P[0] = new double[]{1.0, 0.4};
	    P[1] = new double[]{0.4, 1.0};

	    PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P, null, 0);

	    double[][] A = new double[1][2];
	    A[0] = new double[]{1.0, 1.0};
	    double[] b = new double[]{1.0};

	    ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[4];
	    inequalities[0] = new LinearMultivariateRealFunction(new double[]{-1, 0}, -0.2);// -x1 -0.2 < 0
	    inequalities[1] = new LinearMultivariateRealFunction(new double[]{0, -1}, -0.2);// -x2 -0.2 < 0
	    
	    inequalities[2] = new LinearMultivariateRealFunction(new double[]{-1, -1}, 0.9);// -x1 -x2 +0.9 < 0
	    inequalities[3] = new LinearMultivariateRealFunction(new double[]{1, 1},   -1.1);//   x1 +x2 -1.1 < 0

	    OptimizationRequest OR = new OptimizationRequest();
	    OR.setF0(objectiveFunction);
	    OR.setA(A);
	    OR.setB(b);
	    OR.setFi(inequalities);
	    OR.setToleranceFeas(1.E-12);
	    OR.setTolerance(1.E-12);

	    JOptimizer opt = new JOptimizer();
	    opt.setOptimizationRequest(OR);
	    int returnCode = opt.optimize();
	    if(returnCode==OptimizationResponse.FAILED){
	 			fail();
	 		}

	    double[] res = opt.getOptimizationResponse().getSolution();

	    int status = opt.getOptimizationResponse().getReturnCode();
	    Log.d(MainActivity.JOPTIMIZER_LOGTAG,"status : " + status);
	    Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(res));
	}
	
	/**
	 * Test QP.
	 * Submitted 12/07/2012 by Katharina Schwaiger.
	 */
	public void testQPScala2() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testQPScala2");
		
		double[][] P = new double[4][4];
	    P[0] = new double[]{0.08, -0.05, -0.05, -0.05};
	    P[1] = new double[]{-0.05, 0.16, -0.02, -0.02};
	    P[2] = new double[]{-0.05, -0.02, 0.35, 0.06};
	    P[3] = new double[]{-0.05, -0.02, 0.06, 0.35};
	    
	    PDQuadraticMultivariateRealFunction objectiveFunction = new PDQuadraticMultivariateRealFunction(P, null, 0);

	    double[][] A = new double[1][2];
	    A[0] = new double[]{1.0, 1.0};
	    double[] b = new double[]{1.0};

	    ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[6];
	    inequalities[0] = new LinearMultivariateRealFunction(new double[]{1, 1, 1, 1}, -10000);// x1+x2+x3+x4+1000 < 0
	    inequalities[1] = new LinearMultivariateRealFunction(new double[]{-0.05, 0.2, -0.15, -0.3}, -1000);// -0.05x1+0.2x2-0.15x3-0.3x4-1000 < 0
	    inequalities[2] = new LinearMultivariateRealFunction(new double[]{-1, 0, 0, 0}, 0);// -x1 < 0
	    inequalities[3] = new LinearMultivariateRealFunction(new double[]{0, -1, 0, 0}, 0);// -x2 < 0
	    inequalities[4] = new LinearMultivariateRealFunction(new double[]{0, 0, -1, 0}, 0);// -x3 < 0
	    inequalities[5] = new LinearMultivariateRealFunction(new double[]{0, 0, 0, -1}, 0);// -x4 < 0
	    
	    OptimizationRequest OR = new OptimizationRequest();
	    OR.setF0(objectiveFunction);
	    OR.setFi(inequalities);
	    OR.setToleranceFeas(1.E-12);
	    OR.setTolerance(1.E-12);

	    JOptimizer opt = new JOptimizer();
	    opt.setOptimizationRequest(OR);
	    int returnCode = opt.optimize();
	    if(returnCode==OptimizationResponse.FAILED){
	 			fail();
	 		}

	    double[] res = opt.getOptimizationResponse().getSolution();

	    int status = opt.getOptimizationResponse().getReturnCode();
	    Log.d(MainActivity.JOPTIMIZER_LOGTAG,"status : " + status);
	    Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(res));
	}
	
	/**
	 * min(100 * y) s.t.
	 *   x -y = 1
	 *	-x < 0 
	 * Submitted 19/10/2012 by Noreen Jamil.
	 */
	public void testLP() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testLP");
		
		// Objective function
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(new double[] { 0., 100. }, 0);
		
		double[][] A = new double[1][2];
		A[0] = new double[]{1.0, -1.0};
		double[] b = new double[]{1.0};
    
		//inequalities
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[1];
		inequalities[0] = new LinearMultivariateRealFunction(new double[]{-1, 0}, 0.);
		
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setA(A);
		or.setB(b);
		or.setFi(inequalities);
		//or.setInitialPoint(new double[] { 3, 2 });
		//or.setNotFeasibleInitialPoint(new double[] { 3, 2 });
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode == OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : "	+ objectiveFunction.value(sol));

		assertEquals( 0., sol[0], 0.000000001);
		assertEquals(-1., sol[1], 0.000000001);
	}	
	
	/**
	 * Linear fractional programming in 2D.
	 * Original problem is:
	 * <br>min (c.X/e.X) s.t.
	 * <br>	G.X < h
	 * <br>with
	 * <br> X = {x,y}
	 * <br> c = {2,4}
	 * <br> e = {2,3}
	 * <br> G = {{-1,1},{3,1},{1/5,-1}}
	 * <br> h = {0,3.2,-0.32}
	 */
	public void testLFProgramming2D() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testLFProgramming2D");
		
		// START SNIPPET: LFP-1
		
		// Objective function (variables y0, y1, z)
		double[] n = new double[] { 2., 4., 0.};
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(n, 0);

		//inequalities (G.y-h.z<0, z>0)
		double[][] Gmh = new double[][]{{-1.0, 1., 0.},
										{ 3.0, 1.,-3.2},
										{ 0.2,-1., 0.32},
										{ 0.0, 0.,-1.0}};
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[4];
		for(int i=0; i<4;i++){
			inequalities[i] = new LinearMultivariateRealFunction(Gmh[i], 0);
		}
		
		//equalities (e.y+f.z=1)
		double[][] Amb = new double[][]{{ 2.,  3.,  0.}};
		double[] bm= new double[]{1};
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setA(Amb);
		or.setB(bm);
		or.setFi(inequalities);
		or.setTolerance(1.E-6);
		or.setToleranceFeas(1.E-6);
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
	  // END SNIPPET: LFP-1
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		double x = sol[0]/sol[2];
		double y = sol[1]/sol[2];
		assertEquals(0.9, x, 0.00001);
		assertEquals(0.5, y, 0.00001);
	}
	
	/**
	 * Convex-concave fractional programming in 2D.
	 * Original problem is:
	 * <br>min (c.X/e.X) s.t.
	 * <br>	(x-c0)^2 + (y-c1)^2 < R^2
	 * <br>with
	 * <br> X = {x,y}
	 * <br> c = {2,4}
	 * <br> e = {2,3}
	 * <br> c0 = 0.65
	 * <br> c1 = 0.65
	 * <br> R = 0.25
	 */
	public void testCCFProgramming2D() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testCCFProgramming2D");
		
		// START SNIPPET: CCFP-1
		
		// Objective function (variables y0, y1, t)
		double[] n = new double[] { 2., 4., 0.};
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(n, 0);

		//inequalities (perspective of (x-c0)^2 + (y-c1)^2 -R^2 < 0, and t>0)
		double c0 = 0.65;
		double c1 = 0.65;
		double R = 0.25;
		double[][] PMatrix = new double[][]{
				{ 1.0, 0.0, -c0},
				{ 0.0, 1.0, -c1},
				{ -c0, -c1, c0*c0+c1*c1-R*R}};
		double[][] Gmh = new double[][]{{0.0, 0.0,-1.0}};//t>0
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[2];
		inequalities[0] = new QuadraticMultivariateRealFunction(PMatrix, null, 0.);
		inequalities[1] = new LinearMultivariateRealFunction(Gmh[0], 0);
		
		//equalities (e.y+f.t=1)
		double[][] Amb = new double[][]{{ 2.,  3.,  0.}};
		double[] bm= new double[]{1};
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setA(Amb);
		or.setB(bm);
		or.setFi(inequalities);
		or.setTolerance(1.E-6);
		or.setToleranceFeas(1.E-6);
		or.setNotFeasibleInitialPoint(new double[] { 0.6, -0.2/3., 0.1 });
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
	  // END SNIPPET: CCFP-1
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		double x = sol[0]/sol[2];
		double y = sol[1]/sol[2];
		assertEquals(0.772036, x, 0.000001);
		assertEquals(0.431810, y, 0.000001);
  }
	
	public void testLinearProgramming7D() throws Exception {
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"testLinearProgramming7D");
		
		double[] CVector = new double[]{0.0, 0.0, 0.0, 1.0, 0.833, 0.833, 0.833};
	    double[][] AMatrix = new double[][]{{1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0}};
	    double[] BVector = new double[]{1.0};
	    double[][] GMatrix = new double[][]{
	    		  {0.014,  0.009,  0.021, 1.0, 1.0, 0.0, 0.0},
			      {0.001,  0.002, -0.002, 1.0, 0.0, 1.0, 0.0},
			      {0.003, -0.005,  0.002, 1.0, 0.0, 0.0, 1.0},
			      {0.006,  0.002,  0.007, 0.0, 0.0, 0.0, 0.0},
			      {1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
			      {0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0},
			      {0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0},
			      {0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0},
			      {0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0},
			      {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0},
			      {0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0}};
	    double[] HVector = new double[]{0.0, 0.0, 0.0, 0.0010, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
		
		// Objective function (plane)
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(CVector, 0.0);

		//inequalities (polyhedral feasible set -G.X-H<0 )
		ConvexMultivariateRealFunction[] inequalities = new ConvexMultivariateRealFunction[GMatrix.length];
		for(int i=0; i<GMatrix.length; i++){
			inequalities[i] = new LinearMultivariateRealFunction(new ArrayRealVector(GMatrix[i]).mapMultiply(-1.).toArray(), -HVector[i]);
		}
		
		//optimization problem
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setFi(inequalities);
		or.setA(AMatrix);
		or.setB(BVector);
		or.setInitialPoint(new double[] {0.25, 0.25, 0.5, 0.01, 0.01, 0.01, 0.01});
		
		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		int returnCode = opt.optimize();
		
		if(returnCode==OptimizationResponse.FAILED){
			fail();
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol: " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value  : " + objectiveFunction.value(sol));
		assertTrue(sol[0] > 0);
		assertTrue(sol[1] > 0);
		assertTrue(sol[2] > 0);
		assertTrue(sol[4] > 0);
		assertTrue(sol[5] > 0);
		assertTrue(sol[6] > 0);
		assertEquals(sol[0]+sol[1]+sol[2], 1., 0.00000001);
		assertTrue(0.006*sol[0]+0.002*sol[1]+0.007*sol[2] > 0.0010);
  }
}
