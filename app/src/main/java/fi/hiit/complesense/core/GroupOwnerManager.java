package fi.hiit.complesense.core;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pGroup;
import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import fi.hiit.complesense.connection.local.GroupOwnerUdpSocketHandler;
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
    private Map<String, Integer> relayReceivers; // relay data receiver


    private WifiP2pGroup clientsGroup = null;


    public GroupOwnerManager(Messenger messenger, Context context, boolean connect2Cloud)
    {
        super(messenger, true, connect2Cloud, context);

        selectedSensorsDict = new TreeMap<String, Integer>();
        availabeSensors = new TreeMap<String, List<Integer>>();
        relayReceivers = new TreeMap<String, Integer>();

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
            abstractSocketHandler = new GroupOwnerUdpSocketHandler(remoteMessenger, this);
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

    public synchronized int addRelayListenor(String remoteSocketAddrStr, int sensorType)
    {
        if(relayReceivers==null)
            return -1;
        relayReceivers.put(remoteSocketAddrStr, sensorType);
        return relayReceivers.size();
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

    public Integer getSelectedSensor(String socketAddrStr)
    {
        return selectedSensorsDict.get(socketAddrStr);
    }
    public Integer getRelayReceiver(String socketAddrStr)
    {
        return selectedSensorsDict.get(socketAddrStr);
    }

    public void sendSensorVals2Cloud(String srcAddr, float[] values)
    {
        if(cloudSocketHandler!=null)
        {
            if(cloudSocketHandler.getCloudConnection()!=null)
            {
                String str = srcAddr + "->";
                for(float f:values)
                {
                    str += Float.toString(f);
                    str +=", ";
                }
                cloudSocketHandler.getCloudConnection().write(str);
            }

        }

    }

    public synchronized SocketAddress selectAudioStreamSender()
    {
        Log.i(TAG,"selectAudioStreamSender()");
        String sender;
        int count = 0, maxCount = 100;

        while(count<maxCount)
        {
            int idx = (int)(Math.random() * selectedSensorsDict.size());

            sender = (String)(selectedSensorsDict.keySet().toArray()[idx]);
            //Log.i(TAG,"sender: "+ sender);
            String senderAddrStr = sender.substring(sender.indexOf("/")+1, sender.indexOf(":"));
            //Log.i(TAG,"sender addr: "+ senderAddrStr);

            String portStr = sender.substring(sender.indexOf(":")+1);
            //Log.i(TAG,"sender port: "+ portStr);
            if(relayReceivers.get(sender)==null)
                return new InetSocketAddress(senderAddrStr, Integer.parseInt(portStr));
        }
        return null;
    }

    public synchronized Set<String> getConnectedClients()
    {
        return selectedSensorsDict.keySet();
    }
}
