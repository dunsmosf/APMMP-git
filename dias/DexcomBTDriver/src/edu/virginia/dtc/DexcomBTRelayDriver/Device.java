package edu.virginia.dtc.DexcomBTRelayDriver;

public class Device extends Object {
	public int my_dev_index;
	public int data_index;
	public int meter_index;
	public int state;
	
	public boolean running;
	public boolean connected;
	public boolean cgm_ant;
	
	public String status;
	
	public Device(int index)
	{
		my_dev_index = index;
		status = "Registered";
		
		running = false;
		connected = false;
		cgm_ant = true;
		
		state = 0;				//This is CGM_NORMAL state
		data_index = 0;
		meter_index = 0;
	}
}
