package fi.hiit.complesense.core;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pGroup;
import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import fi.hiit.complesense.connection.local.GroupOwnerSocketHandler;
import fi.hiit.complesense.connection.remote.CloudSocketHandler;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 7/16/14.
 */
public class GroupOwnerManager extends LocalManager
{
    private static final String TAG = "GroupOwnerManager";

    // Sensors selected by the server
    private Map<String, Integer> selectedSensorsDict;
    private Map<String, List<Integer>> availabeSensors;

    private WifiP2pGroup clientsGroup = null;


    public GroupOwnerManager(Messenger messenger, Context context, boolean connect2Cloud)
    {
        super(messenger, true, connect2Cloud, context);

        selectedSensorsDict = new TreeMap<String, Integer>();
        availabeSensors = new TreeMap<String, List<Integer>>();

        // Register local sensors
        registerSensors(LocalManager.KEY_LOCAL_SOCKET,
                SensorUtil.getLocalSensorTypeList(context));
    }

    @Override
    public void start(InetAddress ownerAddr, int delay)
            throws IOException
    {
        Log.e(TAG,"This is server, cannot be started as client!! ");
    }

    @Override
    public void start() throws IOException {
        if(isServer)
        {
            abstractSocketHandler = new GroupOwnerSocketHandler(remoteMessenger, this);
            abstractSocketHandler.start();
        }

        if(connect2Cloud)
        {
            cloudSocketHandler = new CloudSocketHandler(remoteMessenger, this);
            cloudSocketHandler.start();
        }
    }

    /**
     * Randomly select one sensor from a connected client
     */
    public synchronized int randomlySelectSensor(List<Integer> typeList, String remoteSocketAddr)
    {
        int sType = typeList.get((int)(Math.random()*typeList.size()) );
        selectedSensorsDict.put(remoteSocketAddr, sType);
        float[] dummyValues = {-1.0f,-1.0f,-1.0f};
        //sensorValues.put(remoteSocketAddr, dummyValues);
        return sType;
    }

    public synchronized void registerSensors(String socketAddr, List<Integer> typeList)
    {
        availabeSensors.put(socketAddr, typeList);
    }

    /**
     * Get a list of available sensors on a device
     * @param socketAddr
     * @return
     */
    public List<Integer> getSensorsList(String socketAddr)
    {
        //Log.i(TAG, allSensorsDict.get(socketAddr).toString() );
        return availabeSensors.get(socketAddr);
    }

    public int getSelectedSensor(String socketAddr)
    {
        return selectedSensorsDict.get(socketAddr);
    }

    public void sendSensorVals2Cloud(float[] values)
    {
        if(cloudSocketHandler!=null)
        {
            String str = "";
            for(float f:values)
            {
                str += Float.toString(f);
                str +=", ";
            }

            if(cloudSocketHandler.getCloudConnection()!=null)
                cloudSocketHandler.getCloudConnection().write(str);
        }

    }
}
