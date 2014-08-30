package fi.hiit.complesense.core;

import android.util.Log;

import java.net.SocketAddress;
import java.util.ArrayDeque;
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
    private final SocketAddress remoteSocketAddr;

    public ScheduledUdpQueryTask(GroupOwnerUdpConnectionRunnable runnable,
                              GroupOwnerManager groupOwnerManager,
                              SocketAddress remoteSocketAddr)
    {
        this.runnable = runnable;
        this.groupOwnerManager = groupOwnerManager;
        this.remoteSocketAddr = remoteSocketAddr;
    }

    @Override
    public void run()
    {
        //Log.i(TAG, "run()");
        sendSensorQueryMessage();
        //ArrayDeque<String> hops = selectHopsMeasureRtt();
        //Log.i(TAG, "Selected hops: " + hops.toString());
        //groupOwnerManager.sendMeasureRTTRequest(hops);
    }

    private ArrayDeque<String> selectHopsMeasureRtt()
    {
        Object[] hopsStr = groupOwnerManager.getConnectedClients().toArray();
        ArrayDeque<String> hops = new ArrayDeque<String>();
        hops.add((String)hopsStr[0]);
        hops.add((String)hopsStr[1]);
        hops.add((String)hopsStr[0]);

        return hops;
    }

    private void sendSensorQueryMessage()
    {
        int sType = groupOwnerManager.getSelectedSensor(remoteSocketAddr.toString());
        //Log.i(TAG, "sType: " + sType);
        runnable.write(SystemMessage.makeSensorDataQueryMessage(sType), remoteSocketAddr);
    }
}
