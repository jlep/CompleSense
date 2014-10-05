package fi.hiit.complesense.core;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.service.AbstractGroupService;
import fi.hiit.complesense.util.SensorUtil;
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

    public static final String SERVICE_INSTANCE = "_wifidemotest";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";
    private Messenger uiMessenger;
    private WifiP2pDnsSdServiceRequest serviceRequest;


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
    public void registerService()
    {
        Log.i(TAG, "registerService()");
        Map<String, String> record = new HashMap<String, String>();
        record.put(Constants.TXTRECORD_PROP_VISIBILITY, "visible");

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

    public void findService(WifiP2pManager.DnsSdServiceResponseListener servListener,
                            WifiP2pManager.DnsSdTxtRecordListener txtListener)
    {
        Log.i(TAG,"findService()");
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
        Log.i(TAG,"findService()");
        if (!isWifiP2pEnabled)
        {
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
                        public void onDnsSdTxtRecordAvailable(
                                String fullDomainName, Map<String, String> record,
                                WifiP2pDevice device) {
                            Log.i(TAG, device.deviceName + " is "+ record.get(Constants.TXTRECORD_PROP_VISIBILITY));
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
        //-------- put own TxtRecord into advertisement
        Map<String, String> record = abstractGroupService.getDiscoveredDevices().
                get(abstractGroupService.getDevice().deviceAddress).getTxtRecord();
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

        findService(servListener, txtListener);
    }

    /**
     * update the TxtRecord attached to the registered service
     */
    public void updateTxtRecord()
    {
        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess()
            {
                String str = "Old Registered service removed";
                Log.i(TAG, str);
                SystemUtil.sendStatusTextUpdate(uiMessenger, str);

                Map<String, String> record = abstractGroupService.getDiscoveredDevices().
                        get(abstractGroupService.getDevice().deviceAddress).getTxtRecord();
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
            }

            @Override
            public void onFailure(int reason) {
                Log.i(TAG,"Stopping service DNS fails");
                SystemUtil.sendStatusTextUpdate(uiMessenger,
                        "Stopping service DNS fails");
            }
        });

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
            WifiP2pDevice mDevice = abstractGroupService.getDevice();
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

    public void stopGroupOwner()
    {
        Log.i(TAG,"stopGroupOwner()");
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
    }

    public void clearServiceAdvertisement()
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
