package edu.virginia.dtc.SysMan;

public class TimeValue extends Object {

	private long time;
	private double value;
	
	public TimeValue(long t, double v) {
		time = t;
		value = v;
	}
	
	public long getTime() {
		return time;
	}
	
	public double getValue() {
		return value;
	}
}
