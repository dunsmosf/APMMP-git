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

        machine = new StateMachine(this);


    }

    public void run()
    {
        //need to get system status
        //initialize connections if unavailable
        //run if necessary
    }

    public void initialize()
    {
        //Analyze system and run correct controllers
    }

    private long getCycle()
    {
        return 0;
    }

    private void startController(final Controller c)
    {
        c.cxn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                c.bound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
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
    }

    // Internal Classes
    // ------------------------------------------------------------------

    class ServiceObserver extends ContentObserver
    {
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
