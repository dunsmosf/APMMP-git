package edu.virginia.dtc.CgmService;

public class Cgm extends Object{
	
	//Parameters for DiAsService
	public double min_valid_BG;
	public double max_valid_BG;
	public boolean phone_calibration;
	
	//Storage for values and timing for CGM device
	public boolean calibrated;
	
	public String status;
	
	public long last_valid_CGM_time;
	public long last_valid_calib_time;
	public long time;
	
	public int trend;
	public double value;
	public int cal_value;
	public int state;
	
	protected Cgm()
	{
		min_valid_BG = -1;
		max_valid_BG = -1;
		
		calibrated = false;

		state = 0;					//Zero signifies normal operation
		
		last_valid_CGM_time = -1;
		last_valid_calib_time = -1;
		time = 0;

		trend = 0;		
		value = 0;
		status = "Registered";
	}
}
