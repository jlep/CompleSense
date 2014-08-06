package fi.hiit.complesense.service;

import android.app.Service;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import java.io.IOException;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.GroupOwnerManager;
import fi.hiit.complesense.core.WifiConnectionManager;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 7/16/14.
 */
public class GroupOwnerService extends AbstractGroupService
    implements WifiP2pManager.GroupInfoListener
{
    private static final String TAG = "GroupOwnerService";

    private GroupOwnerManager serverManager;
    private WifiP2pGroup clientsList;

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

                        serverManager = new GroupOwnerManager(mMessenger, getApplicationContext(), true);

                        isInitialized = true;
                        sendServiceInitComplete();
                    }
                    break;
                case Constants.SERVICE_MSG_START:
                    start();
                    break;
                case Constants.SERVICE_MSG_STOP:
                    stop();
                    break;
                case Constants.SERVICE_MSG_REGISTER_SERVICE:
                    mWifiConnManager.registerService();
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

        mWifiConnManager = new WifiConnectionManager(GroupOwnerService.this,
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
        Log.i(TAG,"onDestroy()");
        unregisterReceiver(receiver);
        stop();
        super.onDestroy();
    }

    @Override
    protected void start()
    {
        Log.i(TAG,"start()");
        mWifiConnManager.registerService();

        if(uiMessenger!=null && serverManager!=null)
        {
            if(!serverManager.getIsRunniing())
            {
                try {
                    serverManager.start();
                } catch (IOException e)
                {
                    Log.w(TAG, "Failed to create a server thread - "
                            + e.toString());
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            "Failed to create a server thread");
                    return;
                }
            }
            else
            {
                Log.i(TAG, "Server is already running");
                if(mDevice!=null)
                    SystemUtil.sendSelfInfoUpdate(uiMessenger, mDevice);
                if(clientsList!=null)
                    SystemUtil.sendClientsListUpdate(uiMessenger,clientsList);
            }


        }

    }

    @Override
    protected void stop()
    {
        Log.i(TAG, "stop()");
        serverManager.stop();

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

            manager.clearLocalServices(channel, new WifiP2pManager.ActionListener()
            {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "Local Service clearing completes");
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            "Service clearing completes");
                }

                @Override
                public void onFailure(int reasonCode)
                {
                    Log.e(TAG, "Service clearing failed. Reason :" + reasonCode);
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            "Service clearing failed. Reason :" + reasonCode);
                }
            });
        }
    }

    @Override
    public void resetData()
    {
        isInitialized = false;
        mDevice = null;
        clientsList = null;
    }



    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo p2pInfo)
    {
        Log.i(TAG,"onConnectionInfoAvailable()");
        /*
         * The group owner accepts connections using a server socket and then spawns a
         * client socket for every client. This is handled by {@code
         * GroupOwnerSocketHandler}
         */
        if (p2pInfo.isGroupOwner)
        {
            Log.i(TAG, "Device is connected as Group Owner in master mode");
            SystemUtil.sendStatusTextUpdate(uiMessenger,
                    "Device connected as Group Owner");

            manager.requestGroupInfo(channel,this);
        }
        else
        {
            SystemUtil.sendStatusTextUpdate(uiMessenger,
                    "Error - Device cannot be launched as Group Owner in client mode");
            Log.e(TAG, "Device cannot be launched as Group Owner in client mode");
        }
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group)
    {
        clientsList = group;
        Log.i(TAG,"onGroupInfoAvailable()");
        SystemUtil.sendClientsListUpdate(uiMessenger, group);
    }
}
