package fi.hiit.complesense.core;

import java.util.TimerTask;

import fi.hiit.complesense.SystemMessage;

/**
 * Created by hxguo on 7/11/14.
 */
public class ScheduledQueryTask extends TimerTask
{

    private static final String TAG = "ScheduledQueryTask";
    private final GroupOwnerConnectionRunnable runnable;
    private final GroupOwnerManager groupOwnerManager;

    public ScheduledQueryTask(GroupOwnerConnectionRunnable runnable,
                              GroupOwnerManager groupOwnerManager)
    {
        this.runnable = runnable;
        this.groupOwnerManager = groupOwnerManager;
    }

    @Override
    public void run()
    {
        //Log.i(TAG, "run()");
        sendSensorQueryMessage();
    }

    private void sendSensorQueryMessage()
    {
        int sType = groupOwnerManager.getSelectedSensor(runnable.getRemoteSocketAddr());
        //Log.i(TAG, "sType: " + sType);
        runnable.write(SystemMessage.makeSensorDataQueryMessage(sType));
    }
}
