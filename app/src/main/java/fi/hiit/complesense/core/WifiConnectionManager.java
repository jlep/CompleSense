package fi.hiit.complesense.core;

import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.service.AbstractGroupService;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 7/30/14.
 */
public class WifiConnectionManager
{
    private static final String TAG = "WifiConnectionManager";
    private final AbstractGroupService abstractGroupService;
    protected WifiP2pManager manager;
    protected WifiP2pManager.Channel channel;
    public boolean isWifiP2pEnabled = false;

    public static final String SERVICE_INSTANCE = "_complesense";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";
    private Messenger uiMessenger;
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private WifiP2pDnsSdServiceInfo mServiceInfo;


    public WifiConnectionManager( AbstractGroupService abstractGroupService,
                                  WifiP2pManager manager,
                                  WifiP2pManager.Channel channel)
    {
        this.manager = manager;
        this.channel = channel;
        this.uiMessenger = null;
        this.abstractGroupService = abstractGroupService;
        serviceRequest = null;
    }

    /**
     * Register a local service, waiting for service discovery initiated by other nearby devices
     */
    public void advertiseLocalService()
    {
        Log.i(TAG,"advertiseLocalService()");
        //-------- put own TxtRecord into advertisement
        Map<String, String> record = abstractGroupService.getDiscoveredDevices().
                get(abstractGroupService.getDevice().deviceAddress).getTxtRecord();
        mServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE, SERVICE_REG_TYPE, record);

        manager.addLocalService(channel, mServiceInfo, new WifiP2pManager.ActionListener()
        {
            @Override
            public void onSuccess() {
                Log.i(TAG, abstractGroupService.getApplicationContext().getString(R.string.added_local_service));
                SystemUtil.sendStatusTextUpdate(uiMessenger, abstractGroupService.getApplicationContext().getString(R.string.added_local_service));
            }

            @Override
            public void onFailure(int error) {
                Log.i(TAG, "Failed to add a service");
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Adding Service failed: " + SystemUtil.parseErrorCode(error));
            }
        });
    }

    public void findService(WifiP2pManager.DnsSdServiceResponseListener servListener,
                            WifiP2pManager.DnsSdTxtRecordListener txtListener)
    {
        Log.i(TAG,"findService() without known master");
        if (!isWifiP2pEnabled)
        {
            Toast.makeText(abstractGroupService,
                    R.string.p2p_off_warning, Toast.LENGTH_SHORT).show();
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
            manager.addServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener()
            {
                @Override
                public void onSuccess()
                {
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            "Added service discovery request");

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

                @Override
                public void onFailure(int code) {
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            "Failed adding service discovery request - " +
                                    SystemUtil.parseErrorCode(code));
                }
            });
        }
    }

    /**
     * Find the nearby CompleSense service with default DnsSdServiceResponseListener &
     * DnsSdTxtRecordListener
     */
    public void findService()
    {
        Log.i(TAG,"findService() with known master");
        if (!isWifiP2pEnabled) {
            Toast.makeText(abstractGroupService,
                    R.string.p2p_off_warning, Toast.LENGTH_SHORT).show();
        }
        else
        {
            manager.setDnsSdResponseListeners(channel,
                    new WifiP2pManager.DnsSdServiceResponseListener()
                    {
                        @Override
                        public void onDnsSdServiceAvailable(String instanceName,
                                                            String registrationType, WifiP2pDevice srcDevice)
                        {
                            // A service has been discovered. Is this our app?
                            if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {
                                Log.i(TAG, "onDnsSdServiceAvailable()");
                                // update the UI and add the item the discovered device.
                                if(uiMessenger!=null)
                                    SystemUtil.sendDnsFoundUpdate(uiMessenger, srcDevice,
                                            instanceName);
                            }
                        }
                    }, new WifiP2pManager.DnsSdTxtRecordListener() {
                        @Override
                        public void onDnsSdTxtRecordAvailable(String fullDomainName,
                                                              Map<String, String> record, WifiP2pDevice device) {
                            String txt = "DnsSdTxtRecord available from " + device.deviceAddress +": " + record.toString();
                            SystemUtil.sendStatusTextUpdate(uiMessenger, txt);
                            Log.i(TAG, txt);
                            //Log.i(TAG, device.deviceName + " is "+ record.get(TXTRECORD_PROP_AVAILABLE));
                            //SystemUtil.sendStatusTextUpdate(uiMessenger, "from "+ device.deviceAddress +" recv TxtRecord_sensors: "+ (String)record.get(
                            //        Constants.TXTRECORD_SENSOR_TYPE_LIST));
                            SystemUtil.sendStatusTextUpdate(uiMessenger, "from "+ device.deviceAddress +" recv TxtRecord_connection: "+ (String)record.get(
                                    Constants.TXTRECORD_NETWORK_INFO));
                            abstractGroupService.getDiscoveredDevices().put(device.deviceAddress, new CompleSenseDevice(device, record) );
                            abstractGroupService.setGroupOwner(device);

                            stopFindingService();
                            connectP2p(device, 2);
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

    public void connectP2p(WifiP2pDevice groupOwner, int groupOwnerIntent)
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

    public void startRegistrationAndDiscovery(WifiP2pManager.DnsSdServiceResponseListener servListener,
                                              WifiP2pManager.DnsSdTxtRecordListener txtListener)
    {
        Log.i(TAG,"startRegistrationAndDiscovery()");
        advertiseLocalService();
        findService(servListener, txtListener);
    }

    /**
     * update the TxtRecord attached to the registered service
     */
    public void updateLocalService(final WifiP2pDnsSdServiceInfo serviceInfo)
    {
        if(manager!=null && channel !=null && mServiceInfo !=null) {

            manager.removeLocalService(channel, mServiceInfo, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    mServiceInfo = serviceInfo;
                    String str = abstractGroupService.getString(R.string.old_service_removed);
                    Log.i(TAG, str);
                    SystemUtil.sendStatusTextUpdate(uiMessenger, str);
                    advertiseLocalService();
                }

                @Override
                public void onFailure(int i) {

                }
            });
        }
    }

    public void removeLocalService(){
        if(manager!=null && channel !=null && mServiceInfo !=null) {

            manager.removeLocalService(channel, mServiceInfo, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    mServiceInfo = null;
                    String str = abstractGroupService.getString(R.string.old_service_removed);
                    Log.i(TAG, str);
                    SystemUtil.sendStatusTextUpdate(uiMessenger, str);
                    advertiseLocalService();
                }

                @Override
                public void onFailure(int i) {

                }
            });
        }
    }

    public WifiP2pDevice decideGroupOnwer(Map<String, CompleSenseDevice> compleSenseDevices)
    {
        Log.i(TAG, "decideGroupOnwer");
        float maxBatteryLevel = 0;
        WifiP2pDevice groupOwner = null;
        for(Map.Entry<String, CompleSenseDevice> entry : compleSenseDevices.entrySet())
        {
            CompleSenseDevice compleSenseDevice = entry.getValue();
            //-------- decide group owner using battery level
            if(compleSenseDevice.getTxtRecord().get(Constants.TXTRECORD_BATTERY_LEVEL)!=null)
            {
                float batteryLevel = Float.parseFloat(
                        compleSenseDevice.getTxtRecord().get(Constants.TXTRECORD_BATTERY_LEVEL));
                Log.d(TAG,"battery " + entry.getKey()+": " + batteryLevel);

                if(batteryLevel > maxBatteryLevel)
                {
                    maxBatteryLevel = batteryLevel;
                    groupOwner = compleSenseDevice.getDevice();
                }
            }

            /*
            if(compleSenseDevice.getTxtRecord().get(Constants.TXTRECORD_NETWORK_INFO) != null)
            {
                String networkInfo = compleSenseDevice.getTxtRecord().get(Constants.TXTRECORD_NETWORK_INFO);
                Log.i(TAG, "networkInfo: " + networkInfo);
                if(networkInfo!=null)
                {
                    networkInfo = networkInfo.substring(1,2);
                    Log.i(TAG,networkInfo + ":" + Integer.toString(ConnectivityManager.TYPE_MOBILE));
                    if(networkInfo.equals(Integer.toString(ConnectivityManager.TYPE_MOBILE)))
                        return compleSenseDevice.getDevice();
                }
            }
            */
        }
        return groupOwner;
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
            WifiP2pDevice device = abstractGroupService.getDevice();
            if (device == null || device.status == WifiP2pDevice.CONNECTED) {
                disconnect();
            }
            else if (device.status == WifiP2pDevice.AVAILABLE || device.status == WifiP2pDevice.INVITED)
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

    private void disconnect()
    {
        Log.i(TAG,"disconnect()");
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" +
                        SystemUtil.parseErrorCode(reasonCode));
            }

            @Override
            public void onSuccess()
            {
                Log.d(TAG, abstractGroupService.getString(R.string.disconnect_succeed));
                SystemUtil.sendStatusTextUpdate(uiMessenger,abstractGroupService.getString(R.string.disconnect_succeed));
            }

        });
    }

    public void clearServiceAdvertisement()
    {
        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, abstractGroupService.getString(R.string.local_dns_service_stops));
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        abstractGroupService.getString(R.string.local_dns_service_stops));
            }

            @Override
            public void onFailure(int reason) {
                Log.i(TAG,"Stopping service DNS fails");
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Stopping service DNS fails");
            }
        });
    }


    public void stopFindingService()
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


    public void setUiMessenger(Messenger uiMessenger) {
        this.uiMessenger = uiMessenger;
    }
}
