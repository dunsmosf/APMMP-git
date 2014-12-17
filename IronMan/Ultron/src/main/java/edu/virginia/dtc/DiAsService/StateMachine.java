package edu.virginia.dtc.DiAsService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import edu.virginia.dtc.SysMan.Debug;
import edu.virginia.dtc.SysMan.FSM;
import edu.virginia.dtc.SysMan.Messages;

public class StateMachine
{
    private static final String TAG = "StateMachine";

    private Controllers controllers;
    public State scan, algorithm, meal, tbr;
    public boolean apc, brm, ssm, mcm;

    private static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scanExpire, algorithmExpire;

    private static final int ALG_TIMEOUT_S      = 10;
    private static final int SCAN_TIMEOUT_S     = 3;

    public StateMachine(Controllers c)
    {
        controllers = c;

        algorithm = new State();
        meal = new State();
        tbr = new State();
        scan = new State();
    }

    /*****************************************
    // SCAN MACHINE
    *****************************************/

    private Runnable stimeout = new Runnable()
    {
        @Override
        public void run()
        {
            final String FUNC_TAG = "stimeout";

            Debug.i(TAG, FUNC_TAG, "Scan timeout...");

            switch (scan.getState())
            {
                case FSM.PING_APC:
                    scan.setState(FSM.PING_BRM);
                    break;
                case FSM.PING_BRM:
                    scan.setState(FSM.PING_SSM);
                    break;
                case FSM.PING_SSM:
                    scan.setState(FSM.PING_MCM);
                    break;
                case FSM.PING_MCM:
                    scan.setState(FSM.IDLE);
                    break;
            }

            runScanMachine();
        }
    };

    private void setScanTimer()
    {
        if(scanExpire != null)
            scanExpire.cancel(true);

        scanExpire = scheduler.schedule(stimeout, SCAN_TIMEOUT_S, TimeUnit.SECONDS);
    }

    public void startScan()
    {
        apc = brm = ssm = mcm = false;

        scan.setState(FSM.PING_APC);
        runScanMachine();
    }

    private void runScanMachine()
    {
        final String FUNC_TAG = "runScanMachine";

        switch(scan.getState())
        {
            case FSM.IDLE:
                Debug.i(TAG, FUNC_TAG, "Current Scanned Services");
                Debug.i(TAG, FUNC_TAG, "------------------------");
                Debug.i(TAG, FUNC_TAG, "APC - "+apc);
                Debug.i(TAG, FUNC_TAG, "BRM - "+brm);
                Debug.i(TAG, FUNC_TAG, "SSM - "+ssm);
                Debug.i(TAG, FUNC_TAG, "MCM - "+mcm);
                Debug.i(TAG, FUNC_TAG, "------------------------");

                if(scanExpire != null)
                    scanExpire.cancel(true);
                break;
            case FSM.PING_APC:
                Debug.i(TAG, FUNC_TAG, "Pinging APC...");
                controllers.sendCommand(Messages.APC, FSM.SCAN, Messages.PING);
                setScanTimer();
                break;
            case FSM.PING_BRM:
                Debug.i(TAG, FUNC_TAG, "Pinging BRM...");
                controllers.sendCommand(Messages.BRM, FSM.SCAN, Messages.PING);
                setScanTimer();
                break;
            case FSM.PING_SSM:
                Debug.i(TAG, FUNC_TAG, "Pinging SSM...");
                controllers.sendCommand(Messages.SSM, FSM.SCAN, Messages.PING);
                setScanTimer();
                break;
            case FSM.PING_MCM:
                Debug.i(TAG, FUNC_TAG, "Pinging MCM...");
                controllers.sendCommand(Messages.MCM, FSM.SCAN, Messages.PING);
                setScanTimer();
                break;
        }
    }

    public void processScanResponse(int source)
    {
        final String FUNC_TAG = "processScanResponse";

        switch(scan.getState())
        {
            case FSM.PING_APC:
                if(source == Messages.APC) {
                    Debug.i(TAG, FUNC_TAG, "Received APC response...");
                    apc = true;
                    scan.setState(FSM.PING_BRM);
                }
                break;
            case FSM.PING_BRM:
                if(source == Messages.BRM) {
                    Debug.i(TAG, FUNC_TAG, "Received BRM response...");
                    brm = true;
                    scan.setState(FSM.PING_SSM);
                }
                break;
            case FSM.PING_SSM:
                if(source == Messages.SSM) {
                    Debug.i(TAG, FUNC_TAG, "Received SSM response...");
                    ssm = true;
                    scan.setState(FSM.PING_MCM);
                }
                break;
            case FSM.PING_MCM:
                if(source == Messages.MCM) {
                    Debug.i(TAG, FUNC_TAG, "Received MCM response...");
                    mcm = true;
                    scan.setState(FSM.IDLE);
                }
                break;
        }

        runScanMachine();
    }

    /*****************************************
    // ALGORITHM MACHINE
    *****************************************/

    private Runnable atimeout = new Runnable()
    {
        @Override
        public void run()
        {
            algorithm.setState(FSM.IDLE);

            runAlgorithmMachine();
        }
    };

    private void setAlgorithmTimer()
    {
        if(algorithmExpire != null)
            algorithmExpire.cancel(true);

        algorithmExpire = scheduler.schedule(atimeout, ALG_TIMEOUT_S, TimeUnit.SECONDS);
    }

    public void startAlgorithm()
    {
        if(ssm) {
            algorithm.setState(FSM.SSM_UPDATE);
            runAlgorithmMachine();
        }
    }

    private void runAlgorithmMachine()
    {
        switch (algorithm.getState()) {
            case FSM.IDLE:
                if(algorithmExpire != null)
                    algorithmExpire.cancel(true);
                break;
            case FSM.SSM_UPDATE:
                controllers.sendCommand(Messages.SSM, FSM.ALGORITHM, Messages.UPDATE);
                setAlgorithmTimer();
                break;
            case FSM.APC_PROCESS:
                controllers.sendCommand(Messages.APC, FSM.ALGORITHM, Messages.PROCESS);
                setAlgorithmTimer();
                break;
            case FSM.BRM_PROCESS:
                controllers.sendCommand(Messages.BRM, FSM.ALGORITHM, Messages.PROCESS);
                setAlgorithmTimer();
                break;
            case FSM.SSM_PROCESS:
                controllers.sendCommand(Messages.SSM, FSM.ALGORITHM, Messages.PROCESS);
                setAlgorithmTimer();
                break;
        }
    }

    public void processAlgorithmResponse(int source, int message)
    {
        int nextState = algorithm.getState();

        switch(algorithm.getState()) {
            case FSM.SSM_UPDATE:
                if(source == Messages.SSM && message == Messages.UPDATE) {
                    if (apc)
                        nextState = FSM.APC_PROCESS;
                    else if (brm)
                        nextState = FSM.BRM_PROCESS;
                    else if (ssm)
                        nextState = FSM.SSM_PROCESS;
                }
                break;
            case FSM.APC_PROCESS:
                if(source == Messages.APC && message == Messages.PROCESS) {
                    if (brm)
                        nextState = FSM.BRM_PROCESS;
                    else if (ssm)
                        nextState = FSM.SSM_PROCESS;
                }
                break;
            case FSM.BRM_PROCESS:
                if(source == Messages.BRM && message == Messages.PROCESS) {
                    if (ssm)
                        nextState = FSM.SSM_PROCESS;
                }
                break;
            case FSM.SSM_PROCESS:
                if(source == Messages.SSM && message == Messages.PROCESS) {
                    nextState = FSM.IDLE;
                }
                break;
        }

        algorithm.setState(nextState);

        runAlgorithmMachine();
    }

    /*****************************************
    // MEAL MACHINE
    *****************************************/

    private void mealMachine()
    {
        switch (meal.getState())
        {
            case FSM.IDLE:
                break;
        }
    }

    /*****************************************
    // TBR MACHINE
    *****************************************/

    private void tbrMachine()
    {
        switch (tbr.getState())
        {
            case FSM.IDLE:
                break;
        }
    }

    /*****************************************
    // MISC. CLASSES
    *****************************************/

    class State
    {
        private int state, prevState;

        public State()
        {
            state = prevState = FSM.IDLE;
        }

        public void setState(int s)
        {
            prevState = state;
            state = s;
        }

        public int getState()
        {
            return state;
        }
    }
}
