package fi.hiit.complesense.util;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by hxguo on 7/30/14.
 * Handles the Wifi-Direct Connection procedures
 */
public class ConnectionUtil
{
    protected WifiP2pManager manager;
    protected WifiP2pManager.Channel channel;
    private Context context;


    public ConnectionUtil(Context context, WifiP2pManager manager, WifiP2pManager.Channel channel)
    {
        this.context = context;
        this.manager = manager;
        this.channel = channel;
    }

    /**
     * Register a local service, waiting for service discovery initiated by other nearby devices
     */
    protected void registerService()
    {
        Log.i(TAG, "registerService()");
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
}
