package edu.virginia.dtc.PumpService;

import edu.virginia.dtc.SysMan.Pump;
import android.content.ServiceConnection;
import android.os.Messenger;

public class PumpSystem extends Object{

    //Insulin storage values
    public double acc_basal_U;
    public double acc_corr_U;
    public double acc_meal_U;
    
    public double sent_basal_U;
    public double sent_corr_U;
    public double sent_meal_U;
    
    public int retryId;
    public int max_retries;
    
	//Parameters
	public double min_bolus;
	public double max_bolus;
	public double min_quanta;
	public double infusion_rate;
	public double voltage;
	public double reservoir_size;
	public double low_reservoir_level;
	
	public double remaining_U;
	public double delivered_U;
	
	public boolean confidence;		//Insulin delivery confidence boolean
	
	public boolean low_reservoir;
	public boolean queryable;
	public boolean tempBasal;
	public boolean retries;
	
	public long time_delivered;
	
	public int tempBasalDuration;
	public int device_status;
	public int state;
	
	public String status;
	
	public ServiceConnection driverConnection = null;
    
    public Messenger driverTransmitter = null;
    public Messenger driverReceiver = null;
	
	private static PumpSystem instance = null;
	
	protected PumpSystem()
	{
		min_bolus = -1;
		max_bolus = -1;
		infusion_rate = -1;
		voltage = -1;
		reservoir_size = -1;
		low_reservoir_level = -1;
		
		remaining_U = 0;
		delivered_U = 0;
		
		state = Pump.PUMP_STATE_IDLE;
		
		low_reservoir = false;
		
		confidence = false;
		
		queryable = false;
		tempBasal = false;
		retries = false;
		
		status = "Registered";
	}
	
	public static PumpSystem getInstance()
	{
		if(instance == null)
			instance = new PumpSystem();
		return instance;
	}
}
