package com.joptimizer;

import junit.framework.Test;
import junit.framework.TestSuite;

import com.joptimizer.optimizers.JOptimizerTest;

public class AllTests {

	public static Test suite() {
		TestSuite suite = new TestSuite("JOptimizer test suite");

		suite.addTestSuite(JOptimizerTest.class);

		return suite;
	}
}
