package edu.virginia.dtc.HR_Driver;

public class Device extends Object {
	public int my_dev_index;
	public int data_index;
	
	public boolean running;
	public boolean connected;
	
	public boolean cgm_ant;
	public boolean unresponsive;
	public boolean totaling;
	
	public String status;
	
	public Device(int index)
	{
		my_dev_index = index;
		status = "Registered";
		running = false;
		connected = false;
		cgm_ant = true;
		data_index = 0;
		
		unresponsive = false;
		totaling = true;
	}
}
