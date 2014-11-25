//*********************************************************************************************************************
//  Copyright 2011 by the University of Virginia
//	All Rights Reserved
//
//  Created by Patrick Keith-Hynes
//  Center for Diabetes Technology
//  University of Virginia
//*********************************************************************************************************************
package edu.virginia.dtc.BRMservice;

import edu.virginia.dtc.Tvector.Tvector;

public class DiAsSubjectData extends Object	{
	
		// Default remote monitoring URI
		public static final String REMOTE_MONITORING_URI = "https://";
		// Storage for subject session parameters
		// - DiAsSetup1
		public boolean realTime;
		public String remoteMonitoringURI;
	   	public String subjectName, subjectSession;
	   	public int subjectAIT, subjectWeight, subjectHeight, subjectAge;
	   	public double subjectTDI;
	   	public boolean subjectFemale;
		// - DiAsSetup2-4
	   	public Tvector subjectCR, subjectCF, subjectBasal, subjectSafety;
	   	// Field validity flags
		// - DiAsSetup1
	   	public boolean subjectNameValid;
	   	public boolean subjectSessionValid;
	   	public boolean weightValid;
	   	public boolean heightValid;
	   	public boolean ageValid;
	   	public boolean TDIValid;
	   	public boolean sexIsFemale;
	   	public boolean AITValid;
		// - DiAsSetup2-4
	   	public boolean subjectCFValid;
	   	public boolean subjectCRValid;
	   	public boolean subjectBasalValid;
		public boolean subjectSafetyValid;

	   	protected DiAsSubjectData() {
	   		// Instantiation happens  exactly once so initialize all fields to known values here.
	   		remoteMonitoringURI = new String(REMOTE_MONITORING_URI);
	   		realTime = true;
	   		subjectName = new String("");
	   		subjectSession = new String("");
	   		subjectAIT=0;
	   		subjectWeight=0;
	   		subjectHeight=0;
	   		subjectAge=0;
	   		subjectTDI=0.0;
	   		subjectFemale=false;
	   		subjectNameValid = false;
	   		subjectSessionValid = false;
	   		weightValid = false;
	   		heightValid = false;
	   		ageValid = false;
	   		TDIValid = false;
	   		sexIsFemale = true;
	   		AITValid = false;
	   		subjectCR = new Tvector(12);
	   		subjectCF = new Tvector(12);
	   		subjectBasal = new Tvector(12);
	   		subjectSafety = new Tvector(12);
	   		subjectCFValid = false;
	   		subjectCRValid = false;
	   		subjectBasalValid = false;
	   		subjectSafetyValid = false;
	   	}

	   	private static DiAsSubjectData instance = null;

	   	public static DiAsSubjectData getInstance() {
	   		if(instance == null) {
	   			instance = new DiAsSubjectData();
	   		}
	   		return instance;
	   	}
	   
}
