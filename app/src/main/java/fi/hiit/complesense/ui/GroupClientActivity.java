package fi.hiit.complesense.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.Serializable;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.util.SystemUtil;
import fi.hiit.complesense.service.GroupClientService;

/**
 * Created by hxguo on 7/16/14.
 */
public class GroupClientActivity extends AbstractGroupActivity
    implements ServersListFragment.ServerOnClickListener,
        ConnectedClientFragment.ClientActionListener
{
    public static final String TAG = "GroupClientActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.group_client_layout);
        statusTxtView = (TextView) findViewById(R.id.status_text);
        statusTxtView.setMovementMethod(new ScrollingMovementMethod());
        statusTxtView.setText("");
        selfInfoFragment = (SelfInfoFragment)getFragmentManager().
                findFragmentById(R.id.self_info_frag_group_client);

        uiMessenger = new Messenger(new IncomingHandler());

        /**
         * Class for interacting with the main interface of the service.
         */
        mConnection = new ServiceConnection()
        {
            public void onServiceConnected(ComponentName className,
                                           IBinder service)
            {
                mService = new Messenger(service);
                Log.i(TAG, "onServiceConnected()");
                try
                {
                    Message msg = Message.obtain(null,
                            Constants.SERVICE_MSG_INIT_SERVICE);
                    msg.replyTo = uiMessenger;
                    mService.send(msg);
                }
                catch (RemoteException e)
                {
                    // In this case the service has crashed before we could even
                    // do anything with it; we can count on soon being
                    // disconnected (and then reconnected if it can be restarted)
                    // so there is no need to do anything here.
                }
            }

            public void onServiceDisconnected(ComponentName className)
            {
                // This is called when the connection with the service has been
                // unexpectedly disconnected -- that is, its process crashed.
                mService = null;
                mIsBound = false;
                appendStatus("Disconnected from GroupClientService");

                /**
                 // As part of the sample, tell the user what happened.
                 Toast.makeText(GroupClientActivity.this, R.string.remote_service_disconnected,
                 Toast.LENGTH_SHORT).show();
                 */
            }
        };


        Intent intent = new Intent(this, GroupClientService.class);
        String serviceName = GroupClientService.class.getCanonicalName();
        if(!SystemUtil.isServiceRunning(serviceName, this))
        {
            Log.d(TAG,"service is not running");
            startService(intent);
        }
        else
            Log.d(TAG,"service is running");
    }


    protected void doBindService()
    {
        Log.i(TAG, "doBindService()");
        Intent intent =new Intent(getApplicationContext(),
                GroupClientService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        appendStatus("Binding to GroupOwnerService");
    }


    @Override
    public void cancelConnect()
    {
        Message msg = Message.obtain(null,
                Constants.SERVICE_MSG_CANCEL_CONNECT);
        msg.replyTo = uiMessenger;
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connectP2p(WifiP2pDevice device)
    {
        Message msg = Message.obtain(null,
                Constants.SERVICE_MSG_CONNECT);
        //msg.replyTo = mMessenger;
        msg.obj = device;
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    /**
     * only called when a client is looking for servers
     */
    @Override
    public void stopServiceDiscovery()
    {
        Message msg = Message.obtain(null,
                Constants.SERVICE_MSG_STOP_CLIENT_SERVICE);
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        doUnbindService();
        //stopService(new Intent(this, GroupClientService.class));
        finish();
    }

    @Override
    public void disconnect()
    {
        Message msg = Message.obtain(null,
                Constants.SERVICE_MSG_CANCEL_CONNECT);
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        doUnbindService();
        finish();
    }

    class IncomingHandler extends Handler implements Serializable
    {
        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case Constants.MSG_SERVICE_INIT_DONE:
                    startServiceWork();
                    break;

                case Constants.MSG_UPDATE_STATUS_TXT:
                    appendStatus((String)msg.obj);
                    break;

                case Constants.MSG_DNS_SERVICE_FOUND:
                    updateServersListFragment(msg);
                    break;

                case Constants.MSG_SERVER_INFO:
                    updateConnectedClientFragment(msg);
                    break;

                case Constants.MSG_SELF_INFO_UPDATE:
                    updateSelfInfoFragment(msg);
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void updateConnectedClientFragment(Message msg)
    {

        WifiP2pDevice server = (WifiP2pDevice)msg.obj;
        ServersListFragment fragment = (ServersListFragment)
                getFragmentManager().findFragmentById(R.id.available_servers_list);

        fragment.dimissDialog();
        fragment.getView().setVisibility(View.GONE);

        ConnectedClientFragment clientFragment = (ConnectedClientFragment)
                getFragmentManager().findFragmentById(R.id.connected_clients_list);
        clientFragment.getView().setVisibility(View.VISIBLE);

        clientFragment.updateServerUI(server);
    }


    private void updateServersListFragment(Message msg)
    {
        Log.i(TAG,"updateServersListFragment()");

        String instanceName = msg.getData().getString(Constants.EXTENDED_DATA_INSTANCE_NAME);
        WifiP2pDevice srcDevice = (WifiP2pDevice)msg.obj;

        ServersListFragment fragment = (ServersListFragment) getFragmentManager()
                .findFragmentById(R.id.available_servers_list);
        if (fragment != null)
        {
            PeerListAdapter adapter = (PeerListAdapter) fragment.getListAdapter();
            boolean newServer = true;
            for(int i=0;i<adapter.getCount();i++)
            {
                WifiP2pDevice device = (WifiP2pDevice)adapter.getItem(i);
                if(device.deviceAddress.equals(srcDevice.deviceAddress))
                {
                    newServer = false;
                    break;
                }
            }
            if(newServer)
            {
                adapter.add(srcDevice);
                adapter.notifyDataSetChanged();
            }
            Log.d(TAG, "onBonjourServiceAvailable " + instanceName);
        }

    }
}
