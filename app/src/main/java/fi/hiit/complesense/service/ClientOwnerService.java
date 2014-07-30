package fi.hiit.complesense.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.ClientManager;
import fi.hiit.complesense.core.ComleSenseDevice;
import fi.hiit.complesense.core.GroupOwnerManager;
import fi.hiit.complesense.util.SensorUtil;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 7/28/14.
 */
public class ClientOwnerService extends AbstractGroupService
{
    private static final String TAG = "ClientOwnerService";

    private boolean retryChannel = false;
    private Context context;

    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case Constants.SERVICE_MSG_INIT_SERVICE:
                    if(!isInitialized)
                    {
                        uiMessenger = msg.replyTo;
                        SystemUtil.sendSelfInfoUpdate(uiMessenger, mDevice);
                        //clientManager = new ClientManager(mMessenger, context,false);
                        isInitialized = true;
                        sendServiceInitComplete();
                    }
                    break;
                case Constants.SERVICE_MSG_START:
                    start();
                    break;
                case Constants.SERVICE_MSG_STOP:
                    stopSelf();
                    break;
                case Constants.SERVICE_MSG_CONNECT:
                    connectP2p((WifiP2pDevice)msg.obj);
                    break;
                case Constants.SERVICE_MSG_FIND_SERVICE:
                    findService();
                    break;
                case Constants.SERVICE_MSG_STOP_CLIENT_SERVICE:
                    stopFindingService();
                    stopSelf();
                    break;
                case Constants.SERVICE_MSG_CANCEL_CONNECT:
                    cancelConnect();
                    stopSelf();
                    break;
                case Constants.SERVICE_MSG_STATUS_TXT_UPDATE:
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            (String) msg.obj);
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    public void onCreate()
    {
        Log.i(TAG, "onCreate()");
        super.onCreate();
        mMessenger = new Messenger(new IncomingHandler());
        receiver = new GroupBroadcastReceiver(manager, channel, this);
        context = getApplicationContext();
        registerReceiver(receiver,intentFilter);
    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG, "onDestroy()");
        unregisterReceiver(receiver);
        stop();
        super.onDestroy();
    }

    @Override
    protected void start()
    {
        Log.i(TAG,"start()");
        if(context!=null)
            startRegistrationAndDiscovery();
        else
            Log.e(TAG,"context is null");

    }

    private void startRegistrationAndDiscovery()
    {
        Log.i(TAG,"startRegistrationAndDiscovery()");

        Map<String, String> record = generateTxtRecord();
        compleSenseDevices.put(mDevice.deviceAddress, new ComleSenseDevice(mDevice, record));

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE, SERVICE_REG_TYPE, record);

        manager.addLocalService(channel, service, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Added Local Service");
                SystemUtil.sendStatusTextUpdate(uiMessenger, "Added Local Service");
            }

            @Override
            public void onFailure(int error) {
                Log.i(TAG, "Failed to add a service");
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Adding Service failed: " + SystemUtil.parseErrorCode(error));
            }
        });


        findService();
    }

    private Map<String, String> generateTxtRecord()
    {
        Log.i(TAG,"generateTxtRecord()");

        Map<String, String> record = new HashMap<String, String>();
        record.put(TXTRECORD_PROP_AVAILABLE, "visible");
        record.put(TXTRECORD_SENSOR_TYPE_LIST,
                SensorUtil.getLocalSensorTypeList(context).toString());
        Log.i(TAG,SensorUtil.getLocalSensorTypeList(context).toString());

        // network connections
        List<Integer> availableConns = new ArrayList<Integer>();
        ConnectivityManager connMgr =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] networkInfos = connMgr.getAllNetworkInfo();
        for(NetworkInfo ni:networkInfos)
        {
            if(ni !=null)
            {
                //Log.i(TAG, ni.getTypeName());
                if(ni.isConnectedOrConnecting())
                    availableConns.add(ni.getType());
            }

        }
        if(availableConns.size()>0)
            record.put(TXTRECORD_NETWORK_INFO, availableConns.toString() );
        //Log.i(TAG, availableConns.toString());

        return record;
    }

    @Override
    protected void stop()
    {
        Log.i(TAG, "stop()");
        if(localManager!=null)
            localManager.stop();

        if (manager != null && channel != null)
        {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener()
            {
                @Override
                public void onFailure(int reasonCode)
                {
                    Log.e(TAG, "Server stop failed. Reason :" + reasonCode);
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            "Group removal stop failed. Reason :" + reasonCode);
                }

                @Override
                public void onSuccess() {
                    Log.i(TAG, "Group removal completes");
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            "Group removal completes");
                }

            });
            clearServiceAdvertisement();
        }

    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo)
    {
        Log.i(TAG,"onConnectionInfoAvailable()");
        if(mMessenger==null)
            Log.e(TAG,"mMessenger is null");
        if (p2pInfo.isGroupOwner)
        {
            if(groupOwner == null)
                groupOwner = mDevice;

            if(groupOwner.deviceAddress.equals(mDevice.deviceAddress) )
            {
                Log.i(TAG, "Device is connected as Group Owner in master mode");
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Device connected as Group Owner");
                if(localManager == null)
                {
                    localManager = new GroupOwnerManager(mMessenger, context, true);
                    try {
                        localManager.start();
                    } catch (IOException e) {
                        Log.i(TAG,e.toString());
                    }
                }

            }
            else
            {
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Error - Device cannot be launched as Group Owner in client mode");
                Log.e(TAG, "Device cannot be launched as Group Owner in client mode");
            }
            //manager.requestGroupInfo(channel,this);
        }
        else
        {
            Log.i(TAG, "Device is connected as Group Client in client mode");
            SystemUtil.sendStatusTextUpdate(uiMessenger,
                    "Device connected as Client");
            if(localManager == null)
            {
                clearServiceAdvertisement();

                localManager = new ClientManager(mMessenger, context, false);
                try {
                    Log.i(TAG,"GroupOwner InetAddress: " + p2pInfo.groupOwnerAddress.toString());
                    localManager.start(p2pInfo.groupOwnerAddress, 0);
                } catch (IOException e) {
                    Log.i(TAG,e.toString());

                }
            }

        }

    }

    /**
     *
     * This BroadcastReceiver intercepts the android.net.ConnectivityManager.CONNECTIVITY_ACTION,
     * which indicates a connection change. It checks whether the type is TYPE_WIFI.
     * If it is, it checks whether Wi-Fi is connected and sets the wifiConnected flag in the
     * main activity accordingly.
     *
     */
    class NetworkReceiver extends BroadcastReceiver
    {

        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.i("NetworkReceiver", "onReceive()");
            ConnectivityManager connMgr =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo[] networkInfos = connMgr.getAllNetworkInfo();
            for(NetworkInfo ni:networkInfos)
            {
                Log.i("NetworkReceiver", ni.getTypeName());
                //if(ni.isAvailable())
                //    availableConns.add(ni.getType() );
            }

        }
    }
}
