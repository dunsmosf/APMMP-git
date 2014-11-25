package edu.virginia.dtc.supervisor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Xml;
import android.widget.Toast;
import edu.virginia.dtc.SysMan.Debug;

public class ConfigurationManager {
	public static final String TAG = "ConfigurationManager";
	public static ConfigurationManager instance;
	public Context context;

//	private static final String[] requiredPackages = { 
//			"edu.virginia.dtc.biometricsContentProvider", 
//			"edu.virginia.dtc.CgmService", 
//			"edu.virginia.dtc.PumpService", 
//			"edu.virginia.dtc.DiAsSetup",
//			"edu.virginia.dtc.DiAsService", 
//			"edu.virginia.dtc.DiAsUI", 
//			"edu.virginia.dtc.networkService", 
//			"edu.virginia.dtc.safetyService",
//			"edu.virginia.dtc.supervisor",
//			"edu.virginia.dtc.ConstraintService" 
//			};

	public static final String CONFIG_PATH = Environment.getExternalStorageDirectory().getPath() + "/configurations.xml";
	public static final String APP_VERSION_METADATA_TAG = "Version";

	public List<Config> configs = new ArrayList<Config>();

	private ConfigurationManager() {
	}

	public static ConfigurationManager getInstance(Context context) {
		if (instance == null)
			instance = new ConfigurationManager();
		instance.context = context;
		return instance;
	}

	public boolean checkForValidConfiguration() {
		final String FUNC_TAG = "validConfigurationExists";

		parseConfigurations();
		boolean foundValidConfig = false;
		for(Config config : configs){
			if (checkInstalledSoftwareAgainstConfig(config)){
				Toast.makeText(context, "Configuration " + config.name + " validated", Toast.LENGTH_LONG).show();
				Debug.i(TAG, FUNC_TAG, "Valid configuration - " + config.name);
				foundValidConfig = true;
			}
		}
		if (foundValidConfig)
			return true;
		else {
			Toast.makeText(context, "No valid configurations exist!", Toast.LENGTH_LONG).show();
			Debug.e(TAG, FUNC_TAG, "No valid configurations exist!");
			return false;
		}
	}

	public void parseConfigurations() {
		final String FUNC_TAG = "parseConfigurations";

		configs.clear();
		XmlPullParser parser = Xml.newPullParser();

		try {
			BufferedReader reader = new BufferedReader(new FileReader(new File(CONFIG_PATH)));
			parser.setInput(reader);
			int eventType = parser.getEventType();

			while (eventType != XmlPullParser.END_DOCUMENT) {
				String name = null;
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;
				case XmlPullParser.START_TAG:
					name = parser.getName();

					if (name.equalsIgnoreCase("config")) {
						configs.add(new Config(parser.getAttributeValue(0)));
					} else if (name.equalsIgnoreCase("package")) {
						try {
							Config c = configs.get(configs.size() - 1);
							int minV = Integer.parseInt(parser.getAttributeValue(1));
							int maxV = (parser.getAttributeCount() == 3) ? Integer.parseInt(parser.getAttributeValue(2)) : Integer.MAX_VALUE;
							c.packages.add(new Pack(parser.getAttributeValue(0), minV, maxV));
						} catch (NumberFormatException e) {
							Debug.e(TAG, FUNC_TAG, "Invalid config package declaration: name=" + parser.getAttributeValue(0) + " minVersion=" + parser.getAttributeValue(1) + " maxVersion=" + ((parser.getAttributeCount() == 3) ? Integer.parseInt(parser.getAttributeValue(2)) : null));
						}
					}
					break;
				case XmlPullParser.END_TAG:
					name = parser.getName();
					break;
				}

				eventType = parser.next();
			}
		} catch (FileNotFoundException e) {
			Debug.i(TAG, FUNC_TAG, "File Not Found: " + e.getMessage());
		} catch (IOException e) {
			Debug.i(TAG, FUNC_TAG, "IO Exception: " + e.getMessage());
		} catch (Exception e) {
			Debug.i(TAG, FUNC_TAG, "Exception: " + e.getMessage());
		}
	}

	public boolean checkInstalledSoftwareAgainstConfig(Config config) {
		final String FUNC_TAG = "checkInstalledSoftwareAgainstConfig";

		Debug.i(TAG, FUNC_TAG, "Checking config - " + config.name);
		
		boolean[] foundPackages = new boolean[config.packages.size()];

		final PackageManager pm = context.getPackageManager();
		List<ApplicationInfo> installedPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

		boolean allPackagesValid = true;
		for (int i = 0; i < config.packages.size(); i++){
			Pack requiredPackage = config.packages.get(i);
			InstalledApp app = new InstalledApp(requiredPackage.name);
			config.correspondingInstalledApps.add(app);
			for (ApplicationInfo info : installedPackages){
				if (!foundPackages[i] && requiredPackage.name.equalsIgnoreCase(info.packageName)){
					Integer version = null;
					String versionString = null;
					try {
						versionString = info.metaData.getString(APP_VERSION_METADATA_TAG); //format: $rev: # $ where # is the number
						version = Integer.parseInt(versionString.split(" ")[1]);
						if (version >= requiredPackage.minVersion && version <= requiredPackage.maxVersion)
							app.validVersion = true;
						app.versionString = "" + version;
					} catch (NullPointerException e) {
						app.versionString = "No version metadata";
					} catch (NumberFormatException e) {
						app.versionString = "Invalid version metadata: " + versionString;
					} catch (IndexOutOfBoundsException e) {
						app.versionString = "Invalid version metadata: " + versionString;
					}
					if (!app.validVersion){
						Debug.e(TAG, FUNC_TAG, "    Package " + requiredPackage.name + " has version " + version + ", requires " + requiredPackage.minVersion + ((requiredPackage.maxVersion == Integer.MAX_VALUE) ? " or higher" : ("-" + requiredPackage.maxVersion)));
					}
					app.found = true;
				}
			}
			if (!app.found){
				Debug.e(TAG, FUNC_TAG, "    Package " + requiredPackage.name + " is missing");
			}
			if (app.found && app.validVersion){
				Debug.i(TAG, FUNC_TAG, "    Package " + requiredPackage.name + " exists and is valid");
			}
			allPackagesValid &= app.found && app.validVersion;
		}
		
		if (allPackagesValid)
			Debug.i(TAG, FUNC_TAG, "    Config VALID - " + config.name);
		else
			Debug.e(TAG, FUNC_TAG, "    Config INVALID - " + config.name);
		
		config.allPackagesValid = allPackagesValid;
		return allPackagesValid;
	}
	
	class Config
	{
		public String name;
		public List<Pack> packages = new ArrayList<Pack>();
		public List<InstalledApp> correspondingInstalledApps = new ArrayList<InstalledApp>();
		public boolean allPackagesValid = false;
		
		public Config(String name)
		{
			this.name = name;
		}
	}
	
	class Pack
	{
		public String name;
		public int minVersion;
		public int maxVersion;
		
		public Pack(String name, int minVersion, int maxVersion)
		{
			this.name = name;
			this.minVersion = minVersion;
			this.maxVersion = maxVersion;
		}
	}
	
	class InstalledApp
	{
		public String name;
		public boolean found = false;
		public boolean validVersion = false;
		public String versionString = null;

		public InstalledApp(String name)
		{
			this.name = name;
		}
	}
}
