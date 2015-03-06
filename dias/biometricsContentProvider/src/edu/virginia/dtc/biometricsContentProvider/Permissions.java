package edu.virginia.dtc.biometricsContentProvider;

import java.util.ArrayList;
import java.util.List;

import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;


public class Permissions 
{
	private static final String TAG = "Permissions";
	
	public static String[] applications = 
	{
		//Main Applications
		"edu.virginia.dtc.biometricsContentProvider",		//0
		"edu.virginia.dtc.CgmService", 						//1
		"edu.virginia.dtc.PumpService", 					//2
		"edu.virginia.dtc.DiAsSetup", 						//3
		"edu.virginia.dtc.DiAsService", 					//4
		"edu.virginia.dtc.DiAsUI", 							//5
		"edu.virginia.dtc.networkService", 					//6
		"edu.virginia.dtc.supervisor", 						//7
		"edu.virginia.dtc.ConstraintService",				//8
		"edu.virginia.dtc.ExerciseService",					//9
			
		//Shell services
		"edu.virginia.dtc.APCserviceShell",					//10
		"edu.virginia.dtc.BRMserviceShell", 				//11
		"edu.virginia.dtc.SSMserviceShell",					//12
		
		//Services
		"edu.virginia.dtc.APCservice",						//13
		"edu.virginia.dtc.BRMservice",						//14
		"edu.virginia.dtc.MCMservice",						//15
		"edu.virginia.dtc.MealActivity",					//16
		"edu.virginia.dtc.SSMservice",						//17
   
		//Simulation driver
		"edu.virginia.dtc.standaloneDriver",				//18
		
		//Pump drivers
		"edu.virginia.dtc.TandemDriver",					//19
		"edu.virginia.dtc.BTLE_Tandem",						//20
		"edu.virginia.dtc.RocheDriver",						//21
   
		//CGM drivers
		"edu.virginia.dtc.USBDexcomLocalDriver",			//22
		"edu.virginia.dtc.DexcomBTRelayDriver",				//23
		"edu.virginia.dtc.BTLE_G4",							//24
					
		//Misc. drivers
		"edu.virginia.dtc.GlassDriver",						//25	
		"edu.virginia.dtc.HR_Driver",						//26
		"edu.virginia.dtc.Bioharness_Driver"				//27
	};
	
	public static class appPerm
	{
		public List<perm> tablePerm;
		public String app;
		
		public appPerm(String a)
		{
			app = a;
			tablePerm = new ArrayList<perm>();
		}
	}
   
	public static class perm
	{
		public int table;
		private boolean query = false;
		private boolean update = false;
		private boolean insert = false;
		private boolean delete = false;
		
		public perm(int t, boolean q, boolean u, boolean i, boolean d)
		{
			table = t;
			query = q;
			update = u;
			insert = i;
			delete = d;
		}
		
		public boolean canQuery()
		{return query;}
		
		public boolean canUpdate()
		{return update;}
		
		public boolean canInsert()
		{return insert;}
		
		public boolean canDelete()
		{return delete;}
	}
	
	public static void initPermissions(List<appPerm> l)
	{
		int j = 0;
		
	   for(String a:applications)
	   {
		   l.add(new appPerm(a));					//Add an application entry
		   appPerm aplEntry = l.get(l.size()-1);	//Grab the added application entry
		   
		   Debug.i(TAG, "", aplEntry.app + " Index: "+j);
		   j++;
		   
		   for(int i=Biometrics.MIN_TABLE_NUM;i<=Biometrics.MAX_TABLE_NUM;i++)			//Iterate through the tables and set permissions
		   {
			   perm p = getPermissions(aplEntry.app, i);
			   aplEntry.tablePerm.add(p);
			
			   Debug.i(TAG, ">>>", p.table + " Q: "+p.query+" U: "+p.update+" I: "+p.insert+" D: "+p.delete);
		   }
	   }
	}
	
	public static perm getCgmDriverPermissions(int table)
	{
		Debug.i(TAG, "", "GETTING CGM PERMISSION!");
		
		switch(table)
		{														//						Query	Update	Insert	Delete
			case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
			case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
			
	   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
	    	case Biometrics.HARDWARE_CONFIGURATION_TABLE:		return new perm(table, 	true, 	true, 	true, 	false);
	    	case Biometrics.CGM_DETAILS_TABLE:					return new perm(table, 	true, 	true, 	true, 	false);
	    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
	    	default:											return new perm(table,	true,	false,	false,	false);
		}
	}
	
	public static perm getPumpDriverPermissions(int table)
	{
		Debug.i(TAG, "", "GETTING PUMP PERMISSION!");
		
		switch(table)
		{														//						Query	Update	Insert	Delete
			case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
			case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
			
	   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
	    	case Biometrics.HARDWARE_CONFIGURATION_TABLE:		return new perm(table, 	true, 	true, 	true, 	false);
	    	case Biometrics.PUMP_DETAILS_TABLE:					return new perm(table, 	true, 	true, 	true, 	false);
	    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
	    	case Biometrics.STATE_TABLE:						return new perm(table,	true, 	true, 	true, 	false);
	    	default:											return new perm(table,	true,	false,	false,	false);
		}
	}
	
	public static perm getPermissions(String a, int table)
   {
		if(a.equalsIgnoreCase(applications[0]))						//Content Provider
		{
			return new perm(table, true, true, true, true);
		}
		else if(a.equalsIgnoreCase(applications[1]))				//CGM Service
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.CGM_TABLE:							return new perm(table,	true,	false,	true,	false);
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.SMBG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[2]))				//Pump Service
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.INSULIN_TABLE:						return new perm(table, 	true, 	true, 	true, 	false);
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.PUMP_DETAILS_TABLE:					return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
		    	case Biometrics.SERVICE_OUTPUTS_TABLE:				return new perm(table,  true,   false,	true,	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[3]))				//DiAs Setup
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	true);
				
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		   		case Biometrics.SUBJECT_DATA_TABLE:					return new perm(table, 	true, 	true, 	true, 	false);
		   		case Biometrics.CF_PROFILE_TABLE:					return new perm(table, 	true, 	true, 	true, 	false);
		   		case Biometrics.CR_PROFILE_TABLE:					return new perm(table, 	true, 	true, 	true, 	false);
		   		case Biometrics.BASAL_PROFILE_TABLE:				return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.HARDWARE_CONFIGURATION_TABLE:		return new perm(table, 	true, 	true, 	true, 	true);
		    	case Biometrics.PASSWORD_TABLE:						return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.SAFETY_PROFILE_TABLE:				return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
		    	case Biometrics.SERVER_TABLE:						return new perm(table,	true, 	true, 	false, 	false);
		    	case Biometrics.CONTROLLER_PARAMETERS_TABLE:		return new perm(table,	false, 	false, 	false, 	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[4]))				//DiAs Service
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.INSULIN_TABLE:						return new perm(table, 	true, 	false, 	true, 	false);
		   		case Biometrics.STATE_ESTIMATE_TABLE:				return new perm(table, 	true, 	false, 	true, 	false);
		   		case Biometrics.MEAL_TABLE:							return new perm(table, 	true, 	true, 	true, 	false);
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.HARDWARE_CONFIGURATION_TABLE:		return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.SMBG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.PUMP_DETAILS_TABLE:					return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.DEVICE_DETAILS_TABLE:				return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.CONSTRAINTS_TABLE:					return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.SYSTEM_TABLE:						return new perm(table,	true,	true, 	true,	true);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	true, 	true,	false);
		    	case Biometrics.TEMP_BASAL_TABLE:					return new perm(table,	true, 	true, 	true, 	false);
		    	case Biometrics.STATE_TABLE:						return new perm(table,	true, 	true, 	true, 	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[5]))				//DiAs UI
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	true);		//So far DiAs Main is the only call for deleting, when "New Subject" is pressed
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		   		case Biometrics.SMBG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
		    	case Biometrics.TEMP_BASAL_TABLE:					return new perm(table,	true, 	true, 	true, 	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[6]))				//Network Service
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	true,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	true,	false,	false);
				
		   		case Biometrics.CGM_TABLE:							return new perm(table,	true,	true,	false,	false);
		   		case Biometrics.INSULIN_TABLE:						return new perm(table, 	true, 	true, 	false, 	false);
		   		case Biometrics.STATE_ESTIMATE_TABLE:				return new perm(table, 	true, 	true, 	false, 	false);
		   		case Biometrics.MEAL_TABLE:							return new perm(table, 	true, 	true, 	false, 	false);
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	true, 	true, 	false);
		   		case Biometrics.SUBJECT_DATA_TABLE:					return new perm(table, 	true, 	true, 	false, 	false);
		   		case Biometrics.CF_PROFILE_TABLE:					return new perm(table, 	true, 	true, 	false, 	false);
		   		case Biometrics.CR_PROFILE_TABLE:					return new perm(table, 	true, 	true, 	false, 	false);
		   		case Biometrics.BASAL_PROFILE_TABLE:				return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.HARDWARE_CONFIGURATION_TABLE:		return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.USER_TABLE_1:						return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.USER_TABLE_2:						return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.INSULIN_CREDIT_TABLE:				return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.SMBG_TABLE:							return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.HMS_STATE_ESTIMATE_TABLE:			return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.PASSWORD_TABLE:						return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.CGM_DETAILS_TABLE:					return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.PUMP_DETAILS_TABLE:					return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.SAFETY_PROFILE_TABLE:				return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.DEVICE_DETAILS_TABLE:				return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.CONSTRAINTS_TABLE:					return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.GPS_TABLE:							return new perm(table, 	false, 	true, 	false, 	false);
		    	case Biometrics.EXERCISE_SENSOR_TABLE:				return new perm(table, 	false, 	true, 	false, 	false);
		    	case Biometrics.ACCELEROMETER_TABLE:				return new perm(table, 	false, 	true, 	false, 	false);
		    	case Biometrics.USER_TABLE_3:						return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.USER_TABLE_4:						return new perm(table, 	true, 	true, 	false, 	false);
		    	case Biometrics.SYSTEM_TABLE:						return new perm(table,	true,	true, 	false,	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	true, 	true,	false);
		    	case Biometrics.PARAMETER_TABLE:					return new perm(table,	true, 	true, 	false, 	false);
		    	case Biometrics.SERVER_TABLE:						return new perm(table,	true, 	true, 	false, 	false);
		    	case Biometrics.TEMP_BASAL_TABLE:					return new perm(table,	true, 	true, 	false, 	false);
		    	case Biometrics.CONTROLLER_PARAMETERS_TABLE:		return new perm(table,	true, 	true, 	false, 	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[7]))				//Supervisor
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.CGM_TABLE:							return new perm(table,	true,	false,	false,	false);
		    	case Biometrics.HARDWARE_CONFIGURATION_TABLE:		return new perm(table, 	true, 	true, 	true, 	true);
		    	case Biometrics.GPS_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.ACCELEROMETER_TABLE:				return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.SYSTEM_TABLE:						return new perm(table,	true,	false, 	false,	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
		    	case Biometrics.PARAMETER_TABLE:					return new perm(table,	true, 	false, 	true, 	true);
		    	case Biometrics.SERVER_TABLE:						return new perm(table,	true, 	true, 	true, 	false);
		    	case Biometrics.TIME_TABLE:							return new perm(table,	true, 	true, 	true, 	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[8]))				//Constraint Service
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.USER_TABLE_1:						return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.USER_TABLE_2:						return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.CONSTRAINTS_TABLE:					return new perm(table, 	true, 	true, 	true, 	true);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[9]))				//Exercise Service
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.EXERCISE_SENSOR_TABLE:				return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.SYSTEM_TABLE:						return new perm(table,	true,	false, 	false,	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
		    	case Biometrics.EXERCISE_STATE_TABLE:				return new perm(table,	true, 	true, 	true, 	false);
			}
		}
		
		//**************************************************************************************************************//
		//**************************************************************************************************************//
		//**************************************************************************************************************//
		
		else if(a.equalsIgnoreCase(applications[12]) || 			//SSM Service
				a.equalsIgnoreCase(applications[17]))				//SSM Service Shell
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.INSULIN_TABLE:						return new perm(table, 	true, 	true, 	false, 	false);
		   		case Biometrics.STATE_ESTIMATE_TABLE:				return new perm(table, 	true, 	true, 	true, 	false);
		   		case Biometrics.MEAL_TABLE:							return new perm(table, 	true, 	true, 	false, 	false);
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.USER_TABLE_1:						return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.USER_TABLE_2:						return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.INSULIN_CREDIT_TABLE:				return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.CONSTRAINTS_TABLE:					return new perm(table, 	true, 	true, 	true, 	true);
		    	case Biometrics.USER_TABLE_3:						return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.USER_TABLE_4:						return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
		    	case Biometrics.TEMP_BASAL_TABLE:					return new perm(table,	true, 	true, 	true, 	false);
		    	case Biometrics.CONTROLLER_PARAMETERS_TABLE:		return new perm(table,	true, 	true, 	true, 	false);
		    	case Biometrics.SERVICE_OUTPUTS_TABLE:				return new perm(table,  true,   false,	true,	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[10]) ||				//APC Service Shell
				a.equalsIgnoreCase(applications[13]) || 			//APC Service
				a.equalsIgnoreCase(applications[11]) ||				//BRM Service Shell
				a.equalsIgnoreCase(applications[14]))				//BRM Service
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.INSULIN_TABLE:						return new perm(table, 	true, 	true, 	true, 	false);
		   		case Biometrics.MEAL_TABLE:							return new perm(table, 	true, 	true, 	false, 	false);
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.USER_TABLE_1:						return new perm(table, 	true, 	true, 	true, 	true);
		    	case Biometrics.HMS_STATE_ESTIMATE_TABLE:			return new perm(table, 	true, 	true, 	true, 	true);
		    	case Biometrics.USER_TABLE_3:						return new perm(table, 	true, 	true, 	true, 	true);
		    	case Biometrics.USER_TABLE_4:						return new perm(table, 	true, 	true, 	true, 	true);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
		    	case Biometrics.TEMP_BASAL_TABLE:					return new perm(table,	true, 	true, 	true, 	false);
		    	case Biometrics.CONTROLLER_PARAMETERS_TABLE:		return new perm(table,	true, 	true, 	true, 	false);
		    	case Biometrics.SERVICE_OUTPUTS_TABLE:				return new perm(table,  true,   false,	true,	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[15]))				//MCM Service
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
				case Biometrics.MEAL_TABLE:							return new perm(table, 	true, 	true, 	true, 	false);
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.PUMP_DETAILS_TABLE:					return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.USER_TABLE_3:						return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.USER_TABLE_4:						return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.SYSTEM_TABLE:						return new perm(table,	true,	false, 	false,	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
		    	case Biometrics.CONTROLLER_PARAMETERS_TABLE:		return new perm(table,	true, 	true, 	true, 	false);
		    	case Biometrics.SERVICE_OUTPUTS_TABLE:				return new perm(table,  true,   false,	true,	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[16]))				//Meal Activity
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.USER_TABLE_1:						return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.USER_TABLE_4:						return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
		    	case Biometrics.CONTROLLER_PARAMETERS_TABLE:		return new perm(table,	true, 	true, 	true, 	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[18]))				//Standalone Driver
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.CGM_DETAILS_TABLE:					return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.PUMP_DETAILS_TABLE:					return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
		    	case Biometrics.CONTROLLER_PARAMETERS_TABLE:		return new perm(table,	false, 	false, 	false, 	false);
			}
		}
		
		else if(a.equalsIgnoreCase(applications[19]) ||				//Tandem Driver
				a.equalsIgnoreCase(applications[20]) ||				//BTLE Tandem Driver
				a.equalsIgnoreCase(applications[21]))				//Roche Driver
		{
			return getPumpDriverPermissions(table);
		}
		else if(a.equalsIgnoreCase(applications[22]) ||				//USB Dexcom Driver
				a.equalsIgnoreCase(applications[23]) ||				//BT Relay Dexcom Driver
				a.equalsIgnoreCase(applications[24]))				//BTLE Dexcom Driver
		{
			return getCgmDriverPermissions(table);
		}
		else if(a.equalsIgnoreCase(applications[25]))				//Glass Driver
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		   		case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
		    	case Biometrics.PUMP_DETAILS_TABLE:					return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.SYSTEM_TABLE:						return new perm(table,	true,	false, 	false,	false);
		    	case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
		    	case Biometrics.CONTROLLER_PARAMETERS_TABLE:		return new perm(table,	false, 	false, 	false, 	false);
			}
		}
		else if(a.equalsIgnoreCase(applications[26]))				// Heart Rate Driver
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		    	case Biometrics.HARDWARE_CONFIGURATION_TABLE:		return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.EXERCISE_SENSOR_TABLE:				return new perm(table, 	true, 	true, 	true, 	false); // Required
			}
		}
		else if(a.equalsIgnoreCase(applications[27]))				// Bioharness Driver
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		    	case Biometrics.HARDWARE_CONFIGURATION_TABLE:		return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.EXERCISE_SENSOR_TABLE:				return new perm(table, 	true, 	true, 	true, 	false); // Required
			}
		}
		else													//Unknown applications (these are defaults if a process isn't in the list)
		{
			Debug.i(TAG, "", "UNKNOWN APP");
			return new perm(table, true, false, false, false);
		}
		
		Debug.i(TAG, "", "UNKNOWN TABLE");
		return new perm(table, true, false, false, false);
   }
}
