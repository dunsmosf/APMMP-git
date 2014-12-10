package edu.virginia.dtc.ExerciseService;

import android.content.ServiceConnection;
import android.os.Messenger;

public class Exercise extends Object{
	
	//Parameters for DiAsService
	public double min_valid_HR;
	public double max_valid_HR;
		
	//Storage for values and timing for CGM device
	public boolean running;
	public boolean connected;
	public boolean bound;
	
	public String status;
	
	public long last_valid_HR_time;
	public long time;
	
	public int state;
	public int my_dev_index;
	public int my_state_index;
	
	public double value;
	public double instantspeed;
	
	public Messenger driverTransmitter, driverReceiver;
	public ServiceConnection driverConnection;
	
	public Exercise()
	{
		min_valid_HR = -1;
		max_valid_HR = -1;
		
		my_dev_index = -1;
		my_state_index = -1;
		
		bound = false;
		running = false;
		connected = false;
		
		state = 0;					//Zero signifies normal operation
		
		last_valid_HR_time = -1;
		time = 0;
		
		value = 0;
		instantspeed=-1;
		
		status = "Registered";
	}
}
