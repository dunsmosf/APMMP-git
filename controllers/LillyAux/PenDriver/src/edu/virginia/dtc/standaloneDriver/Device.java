package edu.virginia.dtc.standaloneDriver;

public class Device extends Object {
	public int my_dev_index;
	public int csv_index;
	public int db_index;
	
	int state;
	
	public enum InputMode { USER, CSV, DATABASE }; 
	public InputMode inputMode;
	public double userValue;
	
	public String status;
	
	public Device()
	{
		status = "Registered";
		
		csv_index = 0;
		db_index = 0;
		
		inputMode = InputMode.CSV;	
		userValue = 0.0;
	}
}
