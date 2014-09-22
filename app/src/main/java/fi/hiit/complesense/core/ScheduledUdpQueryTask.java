package fi.hiit.complesense.core;

import android.util.Log;
import java.net.SocketAddress;
import java.util.TimerTask;
import fi.hiit.complesense.connection.UdpConnectionRunnable;

/**
 * Created by hxguo on 8/5/14.
 */
public class ScheduledUdpQueryTask extends TimerTask
{

    private static final String TAG = "ScheduledQueryTask";
    private final UdpConnectionRunnable runnable;
    private final ServiceHandler serviceHandler;
    private final SocketAddress remoteSocketAddr;
    private int sensorType;
    private int counter;
    private final int counter2Switch;

    public ScheduledUdpQueryTask(UdpConnectionRunnable runnable,
                                 ServiceHandler serviceHandler,
                                 SocketAddress remoteSocketAddr, int sensorType)
    {
        this.runnable = runnable;
        this.serviceHandler = serviceHandler;
        this.remoteSocketAddr = remoteSocketAddr;
        this.sensorType = sensorType;
        counter = 0;
        counter2Switch = (int)(Math.random() * 10) + 1;
        Log.i(TAG, "counter2Switch: " + counter2Switch);
    }

    @Override
    public void run()
    {
        //Log.i(TAG, "run()");
        sendSensorQueryMessage();
        sendSwitchSensorRequest();
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
    protected void sendSensorQueryMessage()
    {
        //Log.i(TAG, "sType: " + sType);
        runnable.write(SystemMessage.makeSensorDataQueryMessage(sensorType), remoteSocketAddr);
        counter++;
    }

    protected void sendSwitchSensorRequest()
    {
        if(counter >= counter2Switch)
        {
            int selectedType = serviceHandler.sensorUtil.randomlySelectSensor(remoteSocketAddr.toString());
            serviceHandler.updateStatusTxt("Switch sensor from "+ sensorType +" to "
                    + selectedType + " on " + remoteSocketAddr.toString());
            sensorType = selectedType;
            if(runnable!=null)
                runnable.write(SystemMessage.makeSensorDataQueryMessage(selectedType), remoteSocketAddr);
            counter = 0;
        }

    }

}
