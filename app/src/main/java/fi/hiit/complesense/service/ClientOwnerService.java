package fi.hiit.complesense.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.core.ClientServiceHandler;
import fi.hiit.complesense.core.CompleSenseDevice;
import fi.hiit.complesense.core.GroupBroadcastReceiver;
import fi.hiit.complesense.core.GroupOwnerServiceHandler;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.WifiConnectionManager;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.ui.ClientOwnerActivity;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 7/28/14.
 */
public class ClientOwnerService extends AbstractGroupService
{
    private static final String TAG = "ClientOwnerService";

    private boolean retryChannel = false;
    private ClientServiceHandler localClientServiceHandler = null;

    WifiP2pManager.DnsSdTxtRecordListener txtListener =
            new WifiP2pManager.DnsSdTxtRecordListener()
            {
                @Override
                public void onDnsSdTxtRecordAvailable(
                        String fullDomain, Map record, WifiP2pDevice device)
                {
                    Log.i(TAG, "DnsSdTxtRecord available from " + device.deviceAddress +": " + record.toString());
                    String txt = device.deviceName + " is "+ record.get(Constants.TXTRECORD_PROP_VISIBILITY);
                    SystemUtil.sendStatusTextUpdate(uiMessenger, txt);
                    Log.i(TAG, txt);
                    //SystemUtil.sendStatusTextUpdate(uiMessenger, "from "+ device.deviceAddress +" recv TxtRecord_sensors: "+ (String)record.get(
                    //        Constants.TXTRECORD_SENSOR_TYPE_LIST));
                    //SystemUtil.sendStatusTextUpdate(uiMessenger, "from "+ device.deviceAddress +" recv TxtRecord_connection: "+ (String)record.get(
                    //        Constants.TXTRECORD_NETWORK_INFO));
                    float batteryDiff = SystemUtil.getBatteryLevel(getApplicationContext())
                            - Float.parseFloat((String)record.get(Constants.TXTRECORD_BATTERY_LEVEL));
                    SystemUtil.sendStatusTextUpdate(uiMessenger, "Battery diff with " + device.deviceAddress + " is :" +batteryDiff);
                    discoveredDevices.put(device.deviceAddress, new CompleSenseDevice(device, record) );
                    //Log.i(TAG,"compleSenseDevices.size():" + nearbyDevices.size() );

                    if(discoveredDevices.size()>1)
                    {
                        mWifiConnManager.stopFindingService();
                        groupOwner = mWifiConnManager.decideGroupOnwer(discoveredDevices);
                        if(groupOwner == null)
                        {
                            Log.i(TAG,"groupOwner is null");
                            SystemUtil.sendStatusTextUpdate(uiMessenger, "Cannot find valid group owner");
                            return;
                        }
                        txt = "Group Owner Addr: " + groupOwner.deviceAddress + " own addr: " + getDevice().deviceAddress;
                        Log.i(TAG,txt);
                        SystemUtil.sendStatusTextUpdate(uiMessenger, txt);

                        if(groupOwner.deviceAddress.equals(mDevice.deviceAddress) ) {
                            txt = "Try to connect as group owner with highest priority";
                            Log.i(TAG, txt);
                            SystemUtil.sendStatusTextUpdate(uiMessenger, txt);

                            mWifiConnManager.connectP2p(device, 10);
                        }
                        else
                        {
                            txt = "Try to connect as group client";
                            Log.i(TAG, txt);
                            SystemUtil.sendStatusTextUpdate(uiMessenger, txt);

                            mWifiConnManager.connectP2p(groupOwner, 2);
                            mWifiConnManager.clearServiceAdvertisement();
                        }
                    }
                }
            };

    WifiP2pManager.DnsSdServiceResponseListener servListener
            = new WifiP2pManager.DnsSdServiceResponseListener()
    {
        @Override
        public void onDnsSdServiceAvailable(String instanceName, String registrationType,
                                            WifiP2pDevice srcDevice)
        {
            // A service has been discovered. Is this our app?
            if (instanceName.equalsIgnoreCase(WifiConnectionManager.SERVICE_INSTANCE))
            {
                Log.i(TAG, "onDnsSdServiceAvailable()");
                // update the UI and add the item the discovered device.
                if(uiMessenger!=null)
                    SystemUtil.sendDnsFoundUpdate(uiMessenger, srcDevice, instanceName);
            }
        }
    };


    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler
    {
        @Override
        public void handleMessage(Message msg)
        {
            String txt;
            switch (msg.what)
            {
                case Constants.SERVICE_MSG_INIT_SERVICE:
                    if(!isInitialized) {
                        uiMessenger = msg.replyTo;
                        SystemUtil.sendSelfInfoUpdate(uiMessenger, mDevice);
                        mWifiConnManager.setUiMessenger(uiMessenger);

                        sendServiceInitComplete();
                    }                        //clientManager = new ClientManager(mMessenger, context,false);
                    isInitialized = true;

                    break;
                case Constants.SERVICE_MSG_START:
                    int state = msg.arg1;
                    start(state);
                    break;
                case Constants.SERVICE_MSG_STOP:
                    stopSelf();
                    break;
                case Constants.SERVICE_MSG_CONNECT:
                    mWifiConnManager.connectP2p((WifiP2pDevice)msg.obj, 1);
                    break;
                //case Constants.SERVICE_MSG_FIND_SERVICE:
                //    mWifiConnManager.findService(servListener, txtListener);
                //    break;
                case Constants.SERVICE_MSG_STOP_CLIENT_SERVICE:
                    mWifiConnManager.stopFindingService();
                    stopSelf();
                    break;
                case Constants.SERVICE_MSG_CANCEL_CONNECT:
                    mWifiConnManager.cancelConnect();
                    stopSelf();
                    break;
                case Constants.SERVICE_MSG_STATUS_TXT_UPDATE:
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            (String) msg.obj);
                    break;
                case Constants.SERVICE_MSG_STEREO_IMG_REQ:
                    SystemUtil.sendTakeImageReq(uiMessenger);
                    break;
                case Constants.SERVICE_MSG_TAKEN_IMG:
                    send2Handler((String) msg.obj);
                    break;
                case Constants.SERVICE_MSG_SECONDARY_MASTER:
                    mIsSecondaryMaster = (msg.arg1==1) ? true:false;
                    if(mIsSecondaryMaster)
                        txt = getString(R.string.new_secondary_master);
                    else
                        txt = getString(R.string.get_new_secondary_master);
                    Log.e(TAG, txt);
                    SystemUtil.sendStatusTextUpdate(uiMessenger, txt);
                    break;
                case Constants.SERVICE_MSG_MASTER_DISCONNECT:
                    //SystemUtil.sendMasterDies(uiMessenger, mIsSecondaryMaster);
                    restartLocalService();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void restartLocalService() {
        Log.i(TAG, "restartLocalService()");
        if(serviceHandler!=null)
            serviceHandler.stopServiceHandler();

        if(localClientServiceHandler !=null)
            localClientServiceHandler.stopServiceHandler();

        mDevice = null;
        groupOwner = null;
        serviceHandler = null;
        localClientServiceHandler = null;

        if(mIsSecondaryMaster){
            mIsSecondaryMaster = false;
            Map<String, String> record =  SystemUtil.generateTxtRecord(this);
            WifiP2pDnsSdServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(WifiConnectionManager.SERVICE_INSTANCE,
                    WifiConnectionManager.SERVICE_REG_TYPE, record);
            mWifiConnManager.updateLocalService(serviceInfo);
            mWifiConnManager.findService(servListener, txtListener);
        }else{
            new FindServiceThread().start();
        }
    }

    class FindServiceThread extends Thread{

        @Override
        public void run() {
            long sleepTime = 2000;
            try {
                String txt = "FindServiceThread() in " + sleepTime + " ms";
                Log.i(TAG, txt);
                SystemUtil.sendStatusTextUpdate(uiMessenger, txt);
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mWifiConnManager.findService();
        }
    }


    public void send2Handler(String imageOrientationsFile){
        //Log.i(TAG, "send2Handler()" + data);
        try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(JsonSSI.COMMAND, JsonSSI.SEND_DATA);
            jsonObject.put(JsonSSI.DATA_TO_SEND, imageOrientationsFile);

            if(localClientServiceHandler!=null)
                localClientServiceHandler.send2Handler(jsonObject.toString(), ServiceHandler.JSON_RESPONSE_BYTES);
            else
                serviceHandler.send2Handler(jsonObject.toString(), ServiceHandler.JSON_RESPONSE_BYTES);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
    }

    @Override
    public void onCreate()
    {
        Log.i(TAG, "onCreate()");
        super.onCreate();
        mMessenger = new Messenger(new IncomingHandler());
        receiver = new GroupBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver,intentFilter);

        mWifiConnManager = new WifiConnectionManager(ClientOwnerService.this,
                manager, channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand(): id " + startId + ": " + intent);

        CharSequence text = getText(R.string.remote_service_started);

        Notification notification = new Notification(R.drawable.ic_launcher, text,
                System.currentTimeMillis());

        Intent i = new Intent(this, ClientOwnerActivity.class);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,i, 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.remote_service_client),
                text, contentIntent);
        notification.flags|=Notification.FLAG_NO_CLEAR;
        startForeground(ID_NOTIFICATION, notification);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG, "onDestroy()");
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override
    protected void start(int state)
    {
        Log.i(TAG,"start()");
        if(mWifiConnManager!=null){
            String txt = "rebuilding group: ";
            switch (state){
                case ClientOwnerActivity.START_AS_UNKNOWN:
                    mWifiConnManager.startRegistrationAndDiscovery(servListener, txtListener);
                    break;
                case ClientOwnerActivity.START_AS_CIENT:
                    txt += context.getString(R.string.finding_new_master);
                    Log.e(TAG, txt);
                    SystemUtil.sendStatusTextUpdate(uiMessenger, txt);
                    mWifiConnManager.findService();
                    break;
                case ClientOwnerActivity.START_AS_MASTER:
                    txt += context.getString(R.string.myself_is_new_master);
                    Log.e(TAG, txt);
                    SystemUtil.sendStatusTextUpdate(uiMessenger, txt);
                    mWifiConnManager.advertiseLocalService();
                    break;
            }
        }

        else
            Log.e(TAG,"mWifiConnManager is null");

    }


    @Override
    protected void stop()
    {
        Log.i(TAG, "stop()");
        if(serviceHandler!=null)
            serviceHandler.stopServiceHandler();

        if(localClientServiceHandler !=null)
            localClientServiceHandler.stopServiceHandler();

        if (mWifiConnManager != null){
            mWifiConnManager.cancelConnect();
            mWifiConnManager.clearServiceAdvertisement();
        }

    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo)
    {
        Log.i(TAG,"onConnectionInfoAvailable("+ p2pInfo.groupFormed +", "+ p2pInfo.groupOwnerAddress + ", "+ p2pInfo.isGroupOwner +")");
        if(mMessenger==null)
            Log.e(TAG,"mMessenger is null");

        if(p2pInfo.groupFormed)
        {
            if (p2pInfo.isGroupOwner)
            {
                if(groupOwner==null)
                    groupOwner = mDevice;

                if(groupOwner.deviceAddress.equals(mDevice.deviceAddress) )
                {
                    Log.i(TAG, "Device is connected as Group Owner in master mode");
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            "Device connected as Group Owner " + mDevice.isGroupOwner());
                    if(serviceHandler == null)
                    {
                        try {
                            serviceHandler = new GroupOwnerServiceHandler(mMessenger, context);
                            serviceHandler.start();

                            Callable<InetAddress> callable = new Callable<InetAddress>() {
                                @Override
                                public InetAddress call() throws Exception {
                                    return InetAddress.getLocalHost();
                                }
                            };
                            ExecutorService executor = Executors.newFixedThreadPool(1);
                            Future<InetAddress> future = executor.submit(callable);
                            InetAddress localHost = future.get();

                            if(localHost!=null){
                                Log.i(TAG, "localhost: " + localHost.toString());
                                localClientServiceHandler = new ClientServiceHandler(mMessenger, context, localHost, 0, true);
                                localClientServiceHandler.start();
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            Log.i(TAG, e.toString());
                        } catch (ExecutionException e) {
                            e.printStackTrace();
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
                if(serviceHandler == null)
                {
                    mWifiConnManager.clearServiceAdvertisement();
                    //mWifiConnManager.removeLocalService();
                    //serviceHandler = new ClientManager(mMessenger, context, false);
                    serviceHandler = new ClientServiceHandler(mMessenger, context, p2pInfo.groupOwnerAddress, 0, false);

                    Log.i(TAG,"GroupOwner InetAddress: " + p2pInfo.groupOwnerAddress.toString());
                    serviceHandler.start();
                }
            }
        }
    }

}
