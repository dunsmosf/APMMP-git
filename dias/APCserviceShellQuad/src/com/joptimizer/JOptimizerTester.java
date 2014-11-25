package com.joptimizer;

import org.apache.commons.lang3.ArrayUtils;

import android.util.Log;

import com.joptimizer.functions.ConvexMultivariateRealFunction;
import com.joptimizer.functions.LinearMultivariateRealFunction;
import com.joptimizer.functions.QuadraticMultivariateRealFunction;
import com.joptimizer.optimizers.JOptimizer;
import com.joptimizer.optimizers.OptimizationRequest;
import com.joptimizer.optimizers.OptimizationResponse;

public class JOptimizerTester {
	public void doTest() throws Exception {
		testSimplest();
	}
	
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
			throw new RuntimeException("test failed");
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : "	+ objectiveFunction.value(sol));

		boolean ok = Math.abs(sol[0])<0.000000001;
		if(!ok){
			throw new RuntimeException("test failed");
		}
	}	
	
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
			throw new RuntimeException("test failed");
		}
		
		OptimizationResponse response = opt.getOptimizationResponse();
		double[] sol = response.getSolution();
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"sol   : " + ArrayUtils.toString(sol));
		Log.d(MainActivity.JOPTIMIZER_LOGTAG,"value : " + objectiveFunction.value(sol));
		boolean ok = Math.abs(sol[0])<0.0000001;
		ok &= Math.abs(-1.-sol[1])<0.0000001;
		if(!ok){
			throw new RuntimeException("test failed");
		}
  }
}
