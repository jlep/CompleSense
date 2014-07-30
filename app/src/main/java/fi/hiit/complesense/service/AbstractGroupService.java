package fi.hiit.complesense.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.core.ComleSenseDevice;
import fi.hiit.complesense.core.LocalManager;
import fi.hiit.complesense.ui.DemoActivity;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 7/17/14.
 * Class to handle the DNS-SD connection me steps
 */
public abstract class AbstractGroupService extends Service
    implements WifiP2pManager.ConnectionInfoListener
{
    private static final String TAG = "AbstractGroupService";


    protected NotificationManager mNM;
    protected boolean isWifiP2pEnabled = false, isInitialized = false;
    protected WifiP2pManager manager;
    protected WifiP2pManager.Channel channel;
    protected WifiP2pDnsSdServiceRequest serviceRequest;
    protected GroupBroadcastReceiver receiver;
    protected LocalManager localManager = null;

    protected WifiP2pDevice mDevice, groupOwner;

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    protected Messenger mMessenger, uiMessenger;
    protected final IntentFilter intentFilter = new IntentFilter();

    // TXT RECORD properties
    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    public static final String TXTRECORD_SENSOR_TYPE_LIST = "types";
    public static final String TXTRECORD_NETWORK_INFO = "conns";

    public static final String SERVICE_INSTANCE = "_wifidemotest";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";
    protected Map<String, ComleSenseDevice> compleSenseDevices;

    WifiP2pManager.DnsSdTxtRecordListener txtListener =
            new WifiP2pManager.DnsSdTxtRecordListener()
            {
                @Override
                public void onDnsSdTxtRecordAvailable(
                        String fullDomain, Map record, WifiP2pDevice device)
                {
                    Log.i(TAG, "DnsSdTxtRecord available -" + record.toString());
                    //Log.i(TAG, device.deviceName + " is "+ record.get(TXTRECORD_PROP_AVAILABLE));
                    SystemUtil.sendStatusTextUpdate(uiMessenger, (String)record.get(TXTRECORD_SENSOR_TYPE_LIST));
                    SystemUtil.sendStatusTextUpdate(uiMessenger, (String)record.get(TXTRECORD_NETWORK_INFO));
                    //Log.i(TAG,"available sensors: " + record.get(TXTRECORD_SENSOR_TYPE_LIST));
                    //Log.i(TAG, "available networks: " + record.get(TXTRECORD_NETWORK_INFO));

                    compleSenseDevices.put(device.deviceAddress, new ComleSenseDevice(device, record) );
                    Log.i(TAG,"compleSenseDevices.size():" + compleSenseDevices.size() );

                    if(compleSenseDevices.size()>1)
                    {
                        stopFindingService();

                        groupOwner = decideGroupOnwer(compleSenseDevices);
                        if(groupOwner == null)
                        {
                            Log.i(TAG,"groupOwner is null");
                            return;
                        }
                        Log.i(TAG,"Group Owner Addr: " + groupOwner.deviceAddress );

                        if(groupOwner.deviceAddress.equals(mDevice.deviceAddress) )
                            connectP2p(device, 14);
                        else
                        {
                            connectP2p(groupOwner, 1);
                            clearServiceAdvertisement();
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
                    if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE))
                    {
                        Log.i(TAG, "onDnsSdServiceAvailable()");
                        // update the UI and add the item the discovered device.
                        if(uiMessenger!=null)
                            SystemUtil.sendDnsFoundUpdate(uiMessenger, srcDevice,
                                    instanceName);
                    }
                    }
            };

    @Override
    public void onCreate()
    {
        Log.i(TAG, "onCreate()");
        super.onCreate();
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        groupOwner = null;

        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        // Display a notification about us starting.
        showNotification();
        // add necessary intent values to be matched.
        compleSenseDevices = new TreeMap<String, ComleSenseDevice>();

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // Register BroadcastReceiver to track connection changes.
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);

    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.i(TAG, "onBInd()");
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG,"onDestroy()");
        super.onDestroy();
        // Cancel the persistent notification.
        mNM.cancel(R.string.remote_service_started);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.remote_service_stopped, Toast.LENGTH_SHORT).show();
    }

    protected abstract void start();

    protected abstract void stop();

    protected void resetData()
    {
        Log.i(TAG,"resetData()");
        mDevice = null;
        mMessenger = null;
        uiMessenger = null;
        groupOwner = null;
        localManager = null;
        isInitialized = false;
    }


    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    public void updateSelfInfo(WifiP2pDevice device)
    {
        Log.i(TAG,"updateSelfInfo()");
        mDevice = device;
        SystemUtil.sendSelfInfoUpdate(uiMessenger, mDevice);
    }

    protected void showNotification()
    {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.remote_service_started);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_launcher, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, DemoActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.remote_service_client),
                text, contentIntent);

        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNM.notify(R.string.remote_service_started, notification);

    }

    protected void sendServiceInitComplete()
    {
        Message msg = Message.obtain();
        msg.what = Constants.MSG_SERVICE_INIT_DONE;
        try {
            uiMessenger.send(msg);
        } catch (RemoteException e) {
            Log.i(TAG,e.toString());
        }
    }

     /**
     * Register a local service, waiting for service discovery initiated by other nearby devices
     */
    protected void registerService()
    {
        Log.i(TAG,"registerService()");
        Map<String, String> record = new HashMap<String, String>();
        record.put(TXTRECORD_PROP_AVAILABLE, "visible");

        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
        manager.addLocalService(channel, service, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.i(TAG,"Added Local Service");
                SystemUtil.sendStatusTextUpdate(uiMessenger, "Added Local Service");

                manager.createGroup(channel, new WifiP2pManager.ActionListener()
                {
                    @Override
                    public void onSuccess(){
                        SystemUtil.sendStatusTextUpdate(uiMessenger,
                                "Group creation succeed");
                    }

                    @Override
                    public void onFailure(int reason){
                        SystemUtil.sendStatusTextUpdate(uiMessenger,
                                "Group creation failed: " + SystemUtil.parseErrorCode(reason));
                    }
                });
            }

            @Override
            public void onFailure(int error) {
                Log.i(TAG,"Failed to add a service");
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Adding Service failed: " + SystemUtil.parseErrorCode(error));
            }
        });
    }


    protected void findService()
    {
        Log.i(TAG,"findService()");
        if (!isWifiP2pEnabled)
        {
            Toast.makeText(this, R.string.p2p_off_warning, Toast.LENGTH_SHORT).show();
        }
        else
        {
            /**
             * Register listeners for DNS-SD services. These are callbacks invoked
             * by the system when a service is actually discovered.
             */
            manager.setDnsSdResponseListeners(channel, servListener, txtListener);


            // After attaching listeners, create a service request and initiate
            // discovery.
            serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
            manager.addServiceRequest(channel, serviceRequest,
                    new WifiP2pManager.ActionListener()
                    {
                        @Override
                        public void onSuccess() {
                            SystemUtil.sendStatusTextUpdate(uiMessenger,
                                    "Added service discovery request");
                        }

                        @Override
                        public void onFailure(int code) {
                            SystemUtil.sendStatusTextUpdate(uiMessenger,
                                    "Failed adding service discovery request - " +
                                            SystemUtil.parseErrorCode(code));
                        }
                    });
            manager.discoverServices(channel, new WifiP2pManager.ActionListener()
            {
                @Override
                public void onSuccess() {
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            "Service discovery initiated");
                }

                @Override
                public void onFailure(int code)
                {
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            "Service discovery failed: " + SystemUtil.parseErrorCode(code));

                }
            });
        }

    }

    private WifiP2pDevice decideGroupOnwer(Map<String, ComleSenseDevice> compleSenseDevices)
    {
        Log.i(TAG, "decideGroupOnwer");
        for(Map.Entry<String, ComleSenseDevice> entry : compleSenseDevices.entrySet())
        {
            Log.i(TAG,entry.getKey());
            ComleSenseDevice compleSenseDevice = entry.getValue();

            if(compleSenseDevice.getTxtRecord().get(TXTRECORD_NETWORK_INFO) != null)
            {
                String networkInfo = compleSenseDevice.getTxtRecord().get(TXTRECORD_NETWORK_INFO).toString();
                if(networkInfo!=null)
                {
                    networkInfo = networkInfo.substring(1,2);
                    Log.i(TAG,networkInfo + ":" + Integer.toString(ConnectivityManager.TYPE_MOBILE));
                    if(networkInfo.equals(Integer.toString(ConnectivityManager.TYPE_MOBILE)))
                        return compleSenseDevice.getDevice();
                }
            }

        }

        return null;
    }

    protected void clearServiceAdvertisement()
    {
        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Service DNS stops");
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Service DNS stops");
            }

            @Override
            public void onFailure(int reason) {
                Log.i(TAG,"Stopping service DNS fails");
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Stopping service DNS fails");
            }
        });
    }


    protected void stopFindingService()
    {
        manager.clearServiceRequests(channel,new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Service discovery stops");
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Service discovery stops");
            }

            @Override
            public void onFailure(int reason) {
                Log.i(TAG,"Stopping service discovery fails");
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Stopping service discovery fails");
            }
        });
    }

    /**
     * Called when a client initiates a connection to group owner
     * @param device
     */
    protected void connectP2p(final WifiP2pDevice device)
    {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        if (serviceRequest != null)
        {
            manager.removeServiceRequest(channel, serviceRequest,
                    new WifiP2pManager.ActionListener()
                    {
                        @Override
                        public void onSuccess() {
                        }

                        @Override
                        public void onFailure(int arg0) {
                        }
                    });
        }

        manager.connect(channel, config, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess() {
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Connected to service");
            }

            @Override
            public void onFailure(int errorCode) {
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Failed connecting to service, Reason: " +
                                SystemUtil.parseErrorCode(errorCode));
            }
        });
    }

    private void connectP2p(WifiP2pDevice groupOwner, int groupOwnerIntent)
    {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = groupOwner.deviceAddress;
        //config.wps.setup = WpsInfo.PBC;
        config.groupOwnerIntent = groupOwnerIntent;

        if (serviceRequest != null)
        {
            manager.removeServiceRequest(channel, serviceRequest,
                    new WifiP2pManager.ActionListener()
                    {
                        @Override
                        public void onSuccess() {
                        }

                        @Override
                        public void onFailure(int arg0) {
                        }
                    });
        }

        manager.connect(channel, config, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess() {
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Connected to service");
            }

            @Override
            public void onFailure(int errorCode) {
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Failed connecting to service, Reason: " +
                                SystemUtil.parseErrorCode(errorCode));
            }
        });


    }



    /**
     * A cancel abort request by user. Disconnect i.e. removeGroup if
     * already connected. Else, request WifiP2pManager to abort the ongoing
     * request
     */
    public void cancelConnect()
    {
        Log.i(TAG,"cancelConnect()");

        if (manager != null)
        {
            if (mDevice == null
                    || mDevice.status == WifiP2pDevice.CONNECTED) {
                disconnect();
            }
            else if (mDevice.status == WifiP2pDevice.AVAILABLE
                    || mDevice.status == WifiP2pDevice.INVITED)
            {
                manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        SystemUtil.sendStatusTextUpdate(uiMessenger,
                                "Aborting connection");
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        SystemUtil.sendStatusTextUpdate(uiMessenger,
                                "Connect abort request failed. Reason Code: " +
                                        SystemUtil.parseErrorCode(reasonCode));
                    }
                });
            }
        }
    }

    public void disconnect()
    {
        Log.i(TAG,"disconnect()");
        manager.removeGroup(channel, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" +
                        SystemUtil.parseErrorCode(reasonCode));
            }

            @Override
            public void onSuccess()
            {
                Log.d(TAG, "Disconnect succeed");
                SystemUtil.sendStatusTextUpdate(uiMessenger,"Disconnect succeed");
            }

        });
    }




}
