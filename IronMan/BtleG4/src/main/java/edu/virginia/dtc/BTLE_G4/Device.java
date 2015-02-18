package edu.virginia.dtc.BTLE_G4;

import edu.virginia.dtc.SysMan.CGM;

public class Device extends Object {
	public int my_dev_index;
	public int data_index;
	public int meter_index;
	public int state;
	
	public Device(int index)
	{
		my_dev_index = index;
		
		state = CGM.CGM_NONE;				//This is CGM_NORMAL state
		
		data_index = 0;
		meter_index = 0;
	}
}
