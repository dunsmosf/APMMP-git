package edu.virginia.dtc.SysMan;

import java.util.ArrayList;
import java.util.List;

//TODO: fix permissions
public class Permissions 
{
	private static final String TAG = "Permissions";
	
	public static String[] applications = 
	{
		//Main Applications
		"edu.virginia.dtc.DiAsService", 					//0
		"edu.virginia.dtc.DiAsUI",

        //Services
		"edu.virginia.dtc.ConstraintService",               //2
		"edu.virginia.dtc.ExerciseService",
		"edu.virginia.dtc.APCservice",                      //4
		"edu.virginia.dtc.BRMservice",
		"edu.virginia.dtc.MCMservice",
		"edu.virginia.dtc.SSMservice",
   
		//Simulation driver
		"edu.virginia.dtc.standaloneDriver",				//8

        //Pump Drivers
		"edu.virginia.dtc.BTLE_Tandem",						//9
		"edu.virginia.dtc.RocheDriver",
   
		//CGM drivers
		"edu.virginia.dtc.BTLE_G4",							//11
					
		//Misc. drivers
		"edu.virginia.dtc.GlassDriver",						//12
		"edu.virginia.dtc.HR_Driver",
		"edu.virginia.dtc.Bioharness_Driver"
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
	    	default:											return new perm(table,	true,	false,	false,	false);
		}
	}
	
	public static perm getPermissions(String a, int table)
   {
        if(a.equalsIgnoreCase(applications[0]))				        //DiAs Service
		{
			return new perm(table, true, true, true, true);
		}
		else if(a.equalsIgnoreCase(applications[1]))				//DiAs UI
        {
            return new perm(table, true, true, true, true);
        }
        else if(a.equalsIgnoreCase(applications[4]) || a.equalsIgnoreCase(applications[5])
                || a.equalsIgnoreCase(applications[6]) || a.equalsIgnoreCase(applications[7]))
        {
            switch(table)
            {														//						Query	Update	Insert	Delete
                case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
                case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);

                case Biometrics.MEAL_TABLE:							return new perm(table, 	true, 	true, 	false, 	false);
                case Biometrics.LOG_TABLE:							return new perm(table, 	true, 	false, 	true, 	false);
                case Biometrics.EVENT_TABLE:						return new perm(table,	true,	false, 	true,	false);
                case Biometrics.TEMP_BASAL_TABLE:					return new perm(table,	true, 	true, 	true, 	false);
                case Biometrics.CONTROLLER_PARAMETERS_TABLE:		return new perm(table,	true, 	true, 	true, 	false);
                case Biometrics.SERVICE_OUTPUTS_TABLE:				return new perm(table,  true,   false,	true,	false);
            }
        }
		else if(a.equalsIgnoreCase(applications[8]))				//Standalone Driver
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
		
		else if(a.equalsIgnoreCase(applications[9]) ||				//Tandem Driver
				a.equalsIgnoreCase(applications[10]))				//Roche Driver
		{
			return getPumpDriverPermissions(table);
		}
		else if(a.equalsIgnoreCase(applications[11]))				//BTLE G4 Dexcom Driver
		{
			return getCgmDriverPermissions(table);
		}
		else if(a.equalsIgnoreCase(applications[12]))				//Glass Driver
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
		else if(a.equalsIgnoreCase(applications[13]) ||             // Heart Rate Driver
                a.equalsIgnoreCase(applications[14]))			    // Bioharness
		{
			switch(table)
			{														//						Query	Update	Insert	Delete
				case Biometrics.ALL_URI:							return new perm(table,	false,	false,	false,	false);
				case Biometrics.INFO_URI:							return new perm(table,	false,	false,	false,	false);
				
		    	case Biometrics.HARDWARE_CONFIGURATION_TABLE:		return new perm(table, 	true, 	true, 	true, 	false);
		    	case Biometrics.EXERCISE_SENSOR_TABLE:				return new perm(table, 	true, 	true, 	true, 	false); // Required
			}
		}
		else												    	//Unknown applications (these are defaults if a process isn't in the list)
		{
			Debug.i(TAG, "", "UNKNOWN APP");
			return new perm(table, true, false, false, false);
		}
		
		Debug.i(TAG, "", "UNKNOWN TABLE");
		return new perm(table, true, false, false, false);
   }
}
