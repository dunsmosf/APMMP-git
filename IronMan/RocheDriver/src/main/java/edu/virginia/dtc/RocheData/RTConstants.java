package edu.virginia.dtc.RocheData;

import edu.virginia.dtc.RocheDriver.Driver;
import edu.virginia.dtc.SysMan.Debug;

public class RTConstants {

	private static final String TAG = "RTConstants";
	
	static String[] tbr = 
	{
		"8888    8    8888   8   8           8888    8   88888 88888          8  88888 8888  8888   8    ",
		"8   8  8 8  8      8 8  8           8   8  8 8    8   8             8     8   8   8 8   8   8   ",
		"8   8 8   8 8     8   8 8           8   8 8   8   8   8            8      8   8   8 8   8    8  ",
		"8888  88888  888  88888 8           8888  88888   8   8888         8      8   8888  8888     8  ",
		"8   8 8   8     8 8   8 8           8 8   8   8   8   8            8      8   8   8 8 8      8  ",
		"8   8 8   8     8 8   8 8           8  8  8   8   8   8             8     8   8   8 8  8    8   ",
		"8888  8   8 8888  8   8 88888       8   8 8   8   8   88888          8    8   8888  8   8  8    ",
		"                                                                                                "
	};
	
	static String[] arrow =
	{
		"   8   ",
		"   88  ",
		"888888 ",
		"8888888",
		"888888 ",
		"   88  ",
		"   8   ",
		"       " 
	};
	
	static String[] zeroTbr = 
	{
		"                                            ",
		" 888         888   888   888  8  8    8 8   ",
		"8   8       8   8 8   8 8   8 8  8   8  8   ",
		"8  88       8  88 8  88 8  88 8  8   8  8 8 ",
		"8 8 8       8 8 8 8 8 8 8 8 8 8  8  8   88 8",
		"88  8       88  8 88  8 88  8 8  8  8   8  8",
		"8   8  88   8   8 8   8 8   8 8  8 8    8  8",
		" 888   88    888   888   888   88  8    8  8"                                                   
	};
	
	static String[] clock = 
	{
		"  888  ",
		" 8 8 8 ",
		"8  8  8",
		"8  88 8",
		"8     8",
		" 8   8 ",
		"  888  ",
		"       "
	};
	
	static String[] lowBatt =
	{
		"           ",
		"8888888888 ",
		"8        8 ",
		"888      88",
		"888       8",
		"888      88",
		"8        8 ",
		"8888888888 "
	};
	
	static String[] zero =
	{
		"  8888  ",            
		" 88  88 ",             
		"88    88",             
		"88    88",              
		"88    88",              
		"88    88",               
		"88    88",               
		"88    88"                
	};
	
	static String[] one =
	{
		"    88  ",            
		"   888  ",             
		"  8888  ",             
		"    88  ",              
		"    88  ",              
		"    88  ",               
		"    88  ",               
		"    88  "                
	};
	
	static String[] two =
	{
		"  8888  ",            
		" 88  88 ",             
		"88    88",             
		"88    88",              
		"      88",              
		"      88",               
		"     88 ",               
		"    88  "                
	};
	
	static String[] three =
	{
		" 88888  ",            
		"88   88 ",             
		"      88",             
		"      88",              
		"      88",              
		"     88 ",               
		"   888  ",               
		"     88 "                
	};
	
	static String[] four =
	{
		"     88 ",            
		"    888 ",             
		"    888 ",             
		"   8888 ",              
		"   8 88 ",              
		"  88 88 ",               
		"  8  88 ",               
		" 88  88 "               
	};
	
	static String[] five =
	{
		"8888888 ",            
		"88      ",             
		"88      ",             
		"88      ",              
		"88      ",              
		"888888  ",               
		"     88 ",               
		"      88"                
	};
	
	static String[] six =
	{
		"    888 ",            
		"   88   ",             
		"  88    ",             
		" 88     ",              
		" 88     ",              
		"88      ",               
		"888888  ",               
		"888  88 "                
	};
	
	static String[] seven =
	{
		"88888888",            
		"      88",             
		"      88",             
		"     88 ",              
		"     88 ",              
		"    88  ",               
		"    88  ",               
		"   88   "                
	};
	
	static String[] eight =
	{
		"  8888  ",            
		" 88  88 ",             
		"88    88",             
		"88    88",              
		"88    88",              
		" 88  88 ",               
		"  8888  ",               
		" 88  88 "                
	};
	
	static String[] nine =
	{
		"  8888  ",            
		" 88  88 ",             
		"88    88",             
		"88    88",              
		"88    88",              
		"88    88",               
		" 88  888",               
		"  888888"                
	};
	
	public static int getTbrPercent(String[] row, int start, int stop)
	{		
		String[] tbr =
		{
			row[0].substring(start, stop),
			row[1].substring(start, stop),
			row[2].substring(start, stop),
			row[3].substring(start, stop),
			row[4].substring(start, stop),
			row[5].substring(start, stop),
			row[6].substring(start, stop),
			row[7].substring(start, stop)
		};
		
		for(int i=0;i<=9;i++)
		{
			switch(i)
			{
				case 0:
					if(compare(tbr, zero))
						return i;
					break;
				case 1:
					if(compare(tbr, one))
						return i;
					break;
				case 2:
					if(compare(tbr, two))
						return i;
					break;
				case 3:
					if(compare(tbr, three))
						return i;
					break;
				case 4:
					if(compare(tbr, four))
						return i;
					break;
				case 5:
					if(compare(tbr, five))
						return i;
					break;
				case 6:
					if(compare(tbr, six))
						return i;
					break;
				case 7:
					if(compare(tbr, seven))
						return i;
					break;
				case 8:
					if(compare(tbr, eight))
						return i;
					break;
				case 9:
					if(compare(tbr, nine))
						return i;
					break;
				default:
					break;
			}
		}
		
		return -1;
	}
	
	public static boolean compare(String[] tbr, String[] num)
	{
		for(int i=0;i<tbr.length;i++)
		{
			Debug.i(TAG, "compare", tbr[i] + "| = |" + num[i]);
			
			if(!tbr[i].equalsIgnoreCase(num[i]))
				return false;
		}
		
		return true;
	}
}
