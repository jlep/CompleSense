package fi.hiit.complesense.service;

import android.app.Service;
import android.content.Intent;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.SystemUtil;
import fi.hiit.complesense.core.ClientManager;

/**
 * Created by hxguo on 7/16/14.
 */
public class GroupClientService extends AbstractGroupService
        implements WifiP2pManager.ChannelListener
{

    private static final String TAG = "GroupClientService";

    private ClientManager clientManager;
    private WifiP2pDevice server;
    private boolean retryChannel = false;

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
                        clientManager = new ClientManager(mMessenger, context);
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
                            (String)msg.obj);
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
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.i(TAG, "Received start id " + startId + ": " + intent);

        // Don't restart the GroupOwnerService automatically if its
        // process is killed while it's running.
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG, "onDestroy()");
        unregisterReceiver(receiver);
        stop();
        super.onDestroy();
    }


    private void findService()
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
            manager.setDnsSdResponseListeners(channel,
                    new WifiP2pManager.DnsSdServiceResponseListener()
                    {
                        @Override
                        public void onDnsSdServiceAvailable(String instanceName,
                                                            String registrationType, WifiP2pDevice srcDevice)
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
                    }, new WifiP2pManager.DnsSdTxtRecordListener()
                    {
                        @Override
                        /** Callback includes:
                         * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
                         * record: TXT record dta as a map of key/value pairs.
                         * device: The device running the advertised service.
                         */
                        public void onDnsSdTxtRecordAvailable(
                                String fullDomainName, Map<String, String> record,
                                WifiP2pDevice device) {
                            Log.d(TAG, device.deviceName + " is "+ record.get(TXTRECORD_PROP_AVAILABLE));
                        }
                    });

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
                            switch (code)
                            {
                                case WifiP2pManager.P2P_UNSUPPORTED:
                                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                                            "Failed adding service discovery request - P2P_UNSUPPORTED");
                                    break;
                                case WifiP2pManager.BUSY:
                                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                                            "Failed adding service discovery request - BUSY");
                                    break;
                                case WifiP2pManager.ERROR:
                                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                                            "Failed adding service discovery request - ERROR");
                                    break;
                            }

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

    private void stopFindingService()
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
    private void connectP2p(final WifiP2pDevice device)
    {
        server = device;
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

    @Override
    public void onChannelDisconnected()
    {
        // we will try once more
        if (manager != null && !retryChannel)
        {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            resetData();
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

    public void resetData()
    {
        mDevice=null;
        server = null;
        isInitialized = false;
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo)
    {
        Log.i(TAG, "onConnectionInfoAvailable()");

        /**
         * The group owner accepts connections using a server socket and then spawns a
         * client socket for every client. This is handled by {@code
         * GroupOwnerSocketHandler}
         */
        if (p2pInfo.isGroupOwner)
        {
            SystemUtil.sendStatusTextUpdate(uiMessenger,
                    "Error, device cannot be Group Owner in client mode");
            Log.e(TAG, "This is client mode, cannot be the Group Owner");
        }
        else
        {
            if(p2pInfo.groupFormed)
            {
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Group is formed, Connected as peer");
                Log.d(TAG, "Group is formed, Connected as peer");

                SystemUtil.sendServerInfoUpdate(uiMessenger, server);

                try
                {
                    if(!clientManager.getIsRunniing())
                        clientManager.start(p2pInfo.groupOwnerAddress, 0);
                } catch (IOException e)
                {
                    Log.e(TAG,e.toString());
                    stopSelf();
                }


            }
        }
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


    @Override
    protected void start()
    {
        Log.i(TAG,"start()");
        findService();
    }

    @Override
    protected void stop()
    {
        Log.i(TAG,"stop()");
        if(clientManager!=null)
            clientManager.stop();
        resetData();
    }

}
