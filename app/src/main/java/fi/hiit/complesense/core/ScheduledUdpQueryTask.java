package fi.hiit.complesense.core;

import android.util.Log;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.TimerTask;

import fi.hiit.complesense.connection.UdpConnectionRunnable;
import fi.hiit.complesense.connection.local.GroupOwnerUdpConnectionRunnable;

/**
 * Created by hxguo on 8/5/14.
 */
public class ScheduledUdpQueryTask extends TimerTask
{

    private static final String TAG = "ScheduledQueryTask";
    private final UdpConnectionRunnable runnable;
    private final ServiceHandler serviceHandler;
    private final SocketAddress remoteSocketAddr;
    private final int sensorType;

    public ScheduledUdpQueryTask(UdpConnectionRunnable runnable,
                                 ServiceHandler serviceHandler,
                                 SocketAddress remoteSocketAddr, int sensorType)
    {
        this.runnable = runnable;
        this.serviceHandler = serviceHandler;
        this.remoteSocketAddr = remoteSocketAddr;
        this.sensorType = sensorType;
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

    /*
    private ArrayDeque<String> selectHopsMeasureRtt()
    {
        Object[] hopsStr = groupOwnerManager.getConnectedClients().toArray();
        ArrayDeque<String> hops = new ArrayDeque<String>();
        hops.add((String)hopsStr[0]);
        hops.add((String)hopsStr[1]);
        hops.add((String)hopsStr[0]);

        return hops;
    }
    */
    private void sendSensorQueryMessage()
    {
        //Log.i(TAG, "sType: " + sType);
        runnable.write(SystemMessage.makeSensorDataQueryMessage(sensorType), remoteSocketAddr);
    }
}
