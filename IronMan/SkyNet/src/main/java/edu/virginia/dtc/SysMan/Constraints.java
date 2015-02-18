package edu.virginia.dtc.SysMan;

public class Constraints {

	// Values used in the "status" field of the Constraints table
	public static final int CONSTRAINT_TIMED_OUT = -2;
	public static final int CONSTRAINT_REQUESTED = -1;
	public static final int CONSTRAINT_WRITTEN = 0;
	public static final int CONSTRAINT_READ = 1;
	
	// We limit on boluses above this range but under the "too high" value
	public static final double MAX_MEAL = 18.0;
	public static final double MAX_CORR = 6.0;
	public static final double MAX_BASAL = 1.0;
	public static final double MAX_TOTAL = MAX_MEAL + MAX_CORR + MAX_BASAL;
	
	// We exit on boluses above this range
	public static final double TOO_HIGH_MEAL = 30;
	public static final double TOO_HIGH_CORR = 30;
	public static final double TOO_HIGH_BASAL = 2.0;
	public static final double TOO_HIGH_TOTAL = TOO_HIGH_MEAL + TOO_HIGH_CORR + TOO_HIGH_BASAL;
}
