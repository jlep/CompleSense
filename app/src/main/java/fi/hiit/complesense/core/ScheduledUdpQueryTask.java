package fi.hiit.complesense.core;

import java.util.TimerTask;

import fi.hiit.complesense.connection.local.GroupOwnerUdpConnectionRunnable;

/**
 * Created by hxguo on 8/5/14.
 */
public class ScheduledUdpQueryTask extends TimerTask
{

    private static final String TAG = "ScheduledQueryTask";
    private final GroupOwnerUdpConnectionRunnable runnable;
    private final GroupOwnerManager groupOwnerManager;

    public ScheduledUdpQueryTask(GroupOwnerUdpConnectionRunnable runnable,
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
        int sType = groupOwnerManager.getSelectedSensor(runnable.getRemoteSocketAddr().toString());
        //Log.i(TAG, "sType: " + sType);
        runnable.write(SystemMessage.makeSensorDataQueryMessage(sType),
                runnable.getRemoteSocketAddr());
    }
}
