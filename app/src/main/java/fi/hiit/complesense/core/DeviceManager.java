package fi.hiit.complesense.core;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Messenger;
import android.util.Log;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fi.hiit.complesense.connection.AbstractSocketHandler;
import fi.hiit.complesense.connection.remote.CloudSocketHandler;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 7/25/14.
 */
public class DeviceManager
{
    private static final String TAG = "DeviceManager";

    // Sensors selected by the group owner
    private Map<String, Integer> selectedSensorsDict;
    private Map<String, List<Integer>> availabeSensors;

    public static final String KEY_LOCAL_SOCKET = "/0.0.0.0";
    private final Context context;
    protected volatile boolean isRunning;

    protected WifiP2pDevice selfDevice = null;

    protected AbstractSocketHandler abstractSocketHandler;
    protected CloudSocketHandler cloudSocketHandler;
    protected final Messenger remoteMessenger;
    protected boolean connect2Cloud;

    // All sensor values stored by local device, values can be retrieved from
    // server too
    protected Map<String, SensorValues> sensorValues;

    protected SensorUtil sensorUtil;


    public DeviceManager(Messenger messenger,
                         boolean connect2Cloud,
                         Context context)
    {
        this.context = context;
        this.connect2Cloud = connect2Cloud;
        remoteMessenger = messenger;

        sensorValues = new ConcurrentHashMap<String, SensorValues>();
        sensorUtil = new SensorUtil(context);
        abstractSocketHandler = null;
        cloudSocketHandler = null;
        this.connect2Cloud = connect2Cloud;
        isRunning = false;
    }

    public void start()
    {

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



}
