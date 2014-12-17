package edu.virginia.dtc.DiAsService;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.util.TimeZone;

import edu.virginia.dtc.Supervisor.SupervisorService;
import edu.virginia.dtc.SysMan.Biometrics;
import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.Event;
import edu.virginia.dtc.SysMan.FSM;
import edu.virginia.dtc.SysMan.Messages;
import edu.virginia.dtc.SysMan.Params;
import edu.virginia.dtc.SysMan.State;

public class Controllers
{
    private static final String TAG = "Controllers";

    private DiAsService service;
    private StateMachine machine;
    private Controller Apc, Brm, Ssm, Mcm;
    private long cycle;

    private ServiceObserver serviceObserver;

    public Controllers(DiAsService s)
    {
        service = s;

        cycle = getCycle();

        Ssm = new Controller("DiAs.SSMservice");
        Apc = new Controller("DiAs.APCservice");
        Brm = new Controller("DiAs.BRMservice");
        Mcm = new Controller("DiAs.MCMservice");

        startController(Ssm);
        startController(Apc);
        startController(Brm);
        startController(Mcm);

        machine = new StateMachine(this);

        serviceObserver = new ServiceObserver(new Handler());
        service.getContentResolver().registerContentObserver(Biometrics.SERVICE_OUTPUTS_URI, false, serviceObserver);
    }

    public void runAlgorithm()
    {
        final String FUNC_TAG = "run";

        Debug.i(TAG, FUNC_TAG, "-----------------------------");
        Debug.i(TAG, FUNC_TAG, "Running system...Cycle: "+cycle);

        Debug.i(TAG, FUNC_TAG, "Starting scan...");
        machine.startScan();

        //Run algorithm machine

        //Run TBR machine

        cycle++;

        Debug.i(TAG, FUNC_TAG, "-----------------------------");
    }

    public void runMeal()
    {
        //Run scan machine

        //Run meal machine
    }

    private long getCycle()
    {
        return 0;
    }

    private void startController(final Controller c)
    {
        final String FUNC_TAG = "startController";

        c.cxn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Debug.i(TAG, FUNC_TAG, c.intent+" connected!");
                c.bound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Debug.i(TAG, FUNC_TAG, c.intent+" disconnected!");
                c.bound = false;
            }
        };

        Intent intent = new Intent(c.intent);
        service.bindService(intent, c.cxn, Context.BIND_AUTO_CREATE);
    }

    public void sendCommand(int dest, int machine, int mess)
    {
        ContentValues c = new ContentValues();
        c.put("time", SupervisorService.getSystemSeconds());
        c.put("cycle", cycle);
        c.put("source", Messages.DIAS);
        c.put("destination", dest);
        c.put("machine", machine);
        c.put("type", Messages.COMMAND);
        c.put("message", mess);
        c.put("data", "");

        service.getContentResolver().insert(Biometrics.SERVICE_OUTPUTS_URI, c);
    }

    // Internal Classes
    // ------------------------------------------------------------------

    class ServiceObserver extends ContentObserver
    {
        final String FUNC_TAG = "ServiceObserver";

        public ServiceObserver(Handler handler)
        {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange)
        {
            this.onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri)
        {
            Cursor c = service.getContentResolver().query(Biometrics.SERVICE_OUTPUTS_URI, null, null, null, "_id DESC LIMIT 1");
            if(c.moveToFirst())
            {
                int type = c.getInt(c.getColumnIndex("type"));
                int machine = c.getInt(c.getColumnIndex("machine"));
                int source = c.getInt(c.getColumnIndex("source"));
                int message = c.getInt(c.getColumnIndex("message"));

                if(type == Messages.COMMAND)
                {

                }
                else if(type == Messages.RESPONSE)
                {
                    Debug.i(TAG, FUNC_TAG, "Processing response...");
                    processResponse(machine, source, message);
                }
            }
            c.close();
        }

        private void processResponse(int machine_type, int source, int message)
        {
            switch(machine_type)
            {
                case FSM.SCAN:
                    machine.processScanResponse(source);
                    break;
                case FSM.ALGORITHM:
                    machine.processAlgorithmResponse(source, message);
                    break;
                case FSM.MEAL:
                    break;
                case FSM.TBR:
                    break;
            }
        }
    }

    class Controller
    {
        ServiceConnection cxn;
        boolean bound;
        String intent;

        public Controller(String i)
        {
            intent = i;
            bound = false;
        }
    }
}