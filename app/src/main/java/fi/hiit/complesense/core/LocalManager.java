package fi.hiit.complesense.core;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import fi.hiit.complesense.connection.AbstractUdpSocketHandler;
import fi.hiit.complesense.connection.remote.CloudSocketHandler;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 7/16/14.
 */
public abstract class LocalManager
{
    public static final String KEY_LOCAL_SOCKET = "/0.0.0.0";
    public static final String TAG = "LocalManager";

    private final Context context;
    protected boolean isServer;
    protected volatile boolean isRunning;

    protected WifiP2pDevice selfDevice = null;

    protected AbstractUdpSocketHandler abstractSocketHandler;
    protected CloudSocketHandler cloudSocketHandler;
    protected final Messenger remoteMessenger;
    protected boolean connect2Cloud;

    // All sensor values stored by local device, values can be retrieved from
    // server too
    protected Map<String, SensorValues> sensorValues;

    protected SensorUtil sensorUtil;

    public LocalManager(Messenger messenger, boolean isServer, boolean connect2Cloud, Context context)
    {
        remoteMessenger = messenger;
        this.isServer = isServer;
        this.context = context;
        sensorValues = new ConcurrentHashMap<String, SensorValues>();

        sensorUtil = new SensorUtil(context);
        abstractSocketHandler = null;
        cloudSocketHandler = null;
        this.connect2Cloud = connect2Cloud;
        isRunning = false;
    }


    public void stop()
    {
        Log.i(TAG, "stop()");
        if(abstractSocketHandler!=null)
        {
            abstractSocketHandler.stopHandler();
        }
        if(cloudSocketHandler !=null)
        {
            cloudSocketHandler.stopHandler();
        }

        sensorUtil.unregisterSensorListener();
        isRunning = false;
    }

    public void setSensorValues(float[] values, int sensorType, String srcSocketAddr)
    {
        String key = SensorValues.genKey(srcSocketAddr, sensorType);
        SensorValues sv = sensorValues.get(key);
        if(sv==null)
        {
            sensorValues.put(key, new SensorValues(srcSocketAddr, sensorType,values));
        }
        else
            sv.setValues(values);
    }



    public float[] getSensorValues(int sensorType)
    {
        if (null==sensorUtil.getLocalSensorValues(sensorType))
        {
            // Sensor Listener not installed yet
            sensorUtil.registerSensorListener(sensorType);
        }

        return sensorUtil.getLocalSensorValues(sensorType);
    }


    public abstract void start() throws IOException;
    public abstract void start(InetAddress ownerAddr, int delay) throws IOException;

    public boolean getIsServer()
    {
        return isServer;
    }

    public boolean getIsRunniing()
    {
        return isRunning;
    }

    public synchronized void setIsRunning(boolean isRunning)
    {
        this.isRunning = isRunning;
    }

    public List<Integer> getLocalSensorList()
    {
        return SensorUtil.getLocalSensorTypeList(context);
    }


}
