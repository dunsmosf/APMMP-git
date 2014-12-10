package edu.virginia.dtc.DexcomBTRelayDriver;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.dexcom.G4DevKit.ReceiverUpdateService;

public class AppPreferences extends PreferenceActivity implements
        OnSharedPreferenceChangeListener
{
    private SharedPreferences ApplicationPreferences;
    private ReceiverUpdateTools m_receiverUpdateTools;

    private Preference updateIntervalPref;

    private int serviceUpdateInterval;
    private boolean serviceStatus;

    @Override
    public void onCreate(
        Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        m_receiverUpdateTools = new ReceiverUpdateTools(getApplicationContext());

        // Get the default application preferences
        addPreferencesFromResource(R.xml.app_prefs);
        ApplicationPreferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        serviceUpdateInterval = ApplicationPreferences.getInt(
                "serviceUpdateInterval", DexcomBTRelayDriver.RECEIVER_CHECK_INTERVAL);
        serviceStatus = ApplicationPreferences.getBoolean("serviceStatus",
                false);

        // Update the Summary fields in the preferences UI according to the
        // current preference values
        updateIntervalPref = findPreference("serviceUpdateInterval");
        updateIntervalPref.setSummary("Update every "
                + Integer.toString(serviceUpdateInterval) + " seconds");

        // Listen to changes to preferences
        ApplicationPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    // Handle preference changes and update the UI
    public void onSharedPreferenceChanged(
        SharedPreferences sharedPreferences,
        String key)
    {
        if (key.equals("serviceUpdateInterval"))
        {
            // If user changes the data update interval, set a new Alarm with
            // the appropriate timing
            int newUpdateInterval = sharedPreferences.getInt(
                    "serviceUpdateInterval", DexcomBTRelayDriver.RECEIVER_CHECK_INTERVAL);
            m_receiverUpdateTools.setPeriodicUpdate(getApplicationContext(),
                    newUpdateInterval);

            updateIntervalPref.setSummary("Update every "
                    + Integer.toString(newUpdateInterval) + " seconds");
            getListView().invalidate();
        }
        else if (key.equals("serviceStatus"))
        {
            // If user flips the service status switch, start/stop the service
            serviceStatus = sharedPreferences
                    .getBoolean("serviceStatus", false);

            if (serviceStatus)
            {
                int serviceUpdateInterval = sharedPreferences.getInt(
                        "serviceUpdateInterval", DexcomBTRelayDriver.RECEIVER_CHECK_INTERVAL);
                m_receiverUpdateTools.setPeriodicUpdate(
                        getApplicationContext(), serviceUpdateInterval);
                startService(new Intent(getApplicationContext(),
                        ReceiverUpdateService.class));
            }
            else
            {
                m_receiverUpdateTools
                        .cancelPeriodicUpdate(getApplicationContext());
                stopService(new Intent(getApplicationContext(),
                        ReceiverUpdateService.class));
            }
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        ApplicationPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }
}
