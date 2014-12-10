package edu.virginia.dtc.DexcomBTRelayDriver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.dexcom.G4DevKit.ServiceIntents;

// Tools to handle one-time and repeating receiver data updates using the ReceiverUpdateService
class ReceiverUpdateTools
{
    private static Intent updateReceiverIntent;
    private static PendingIntent m_updateReceiverPi;
    private static AlarmManager m_alarmManager;

    public ReceiverUpdateTools(
        Context context)
    {
        updateReceiverIntent = new Intent(ServiceIntents.UPDATE_RECEIVER_DATA);
        m_updateReceiverPi = PendingIntent.getBroadcast(context, 0, updateReceiverIntent, 0);
    }

    // Immediate one-time update
    void performUpdate(Context context)
    {
        context.sendBroadcast(updateReceiverIntent);
    }

    // Repeating update. Set up using an Alarm - this is better than using a
    // timer inside the service because the update will still happen even if the
    // service gets killed by the system.
    void setPeriodicUpdate(Context context, int alarmInterval)
    {
        AlarmManager m_alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

        m_alarmManager.cancel(m_updateReceiverPi); // cancel the previous alarm
        m_alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), alarmInterval * 1000, m_updateReceiverPi);
    }

    void cancelPeriodicUpdate(Context context)
    {
        m_alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        m_alarmManager.cancel(m_updateReceiverPi);
    }
}
