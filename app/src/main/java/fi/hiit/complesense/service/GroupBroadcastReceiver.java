package fi.hiit.complesense.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

/**
 * Created by hxguo on 7/17/14.
 */
public class GroupBroadcastReceiver extends BroadcastReceiver
{
    private static final String TAG = "GroupBroadcastReceiver";
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final AbstractGroupService service;

    public GroupBroadcastReceiver(WifiP2pManager manager,
                                  WifiP2pManager.Channel channel,
                                  AbstractGroupService service)
    {
        super();
        this.manager = manager;
        this.channel = channel;
        this.service = service;
    }

    /*
     * (non-Javadoc)
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
     * android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent)
    {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action))
        {
            Log.i(TAG, "WIFI_P2P_STATE_CHANGED_ACTION");
            // UI update to indicate wifi p2p status.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
            {
                // Wifi Direct mode is enabled
                service.setIsWifiP2pEnabled(true);
            }
            else
            {
                service.setIsWifiP2pEnabled(false);
                //service.resetData();
            }
            switch (state)
            {
                case WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED:
                    Log.d(TAG, "P2P state changed - WIFI_P2P_DISCOVERY_STARTED");
                    break;
                case WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED:
                    Log.d(TAG, "P2P state changed - WIFI_P2P_DISCOVERY_STOPPED");
                    break;
                case WifiP2pManager.NO_SERVICE_REQUESTS:
                    Log.d(TAG, "P2P state changed - NO_SERVICE_REQUESTS");
                    break;
                default:
                    Log.d(TAG, "P2P state changed - " + state);
                    break;
            }
        }
        else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action))
        {
            Log.d(TAG, "WIFI_P2P_PEERS_CHANGED_ACTION");
            // request available peers from the wifi p2p manager. This is an
            // asynchronous call and the calling activity is notified with a
            // callback on PeerListListener.onPeersAvailable()
            if (manager != null)
            {

            }
        }
        else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action))
        {
            Log.i(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION");
            if (manager == null) {
                return;
            }

            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            WifiP2pDevice device = (WifiP2pDevice) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);

            if (networkInfo.isConnected())
            {
                Log.i(TAG,"Network is connected");
                // Device is connected with the other device as Group Owner
                manager.requestConnectionInfo(channel, service);
            } else {
                // It's a disconnect
                Log.i(TAG,"Network is disconnected");
                //service.resetData();

            }
        }
        else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action))
        {
            Log.i(TAG,"WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");
            WifiP2pDevice device = (WifiP2pDevice) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            service.updateSelfInfo(device);
        }
    }
}
