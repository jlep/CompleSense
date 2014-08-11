package fi.hiit.complesense.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.ClientManager;
import fi.hiit.complesense.core.WifiConnectionManager;
import fi.hiit.complesense.util.SystemUtil;

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
                        mWifiConnManager.setUiMessenger(uiMessenger);

                        clientManager = new ClientManager(mMessenger, context,false);
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
                    mWifiConnManager.connectP2p((WifiP2pDevice)msg.obj,1);
                    break;
                case Constants.SERVICE_MSG_FIND_SERVICE:
                    mWifiConnManager.findService();
                    break;
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
        context = getApplicationContext();
        registerReceiver(receiver, intentFilter);

        mWifiConnManager = new WifiConnectionManager(GroupClientService.this,
                manager, channel);
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
        super.onDestroy();
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



    @Override
    protected void start()
    {
        Log.i(TAG,"start()");
        mWifiConnManager.findService();
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
