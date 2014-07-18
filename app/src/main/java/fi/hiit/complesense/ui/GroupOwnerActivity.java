package fi.hiit.complesense.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pGroup;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;

import java.io.Serializable;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.SystemUtil;
import fi.hiit.complesense.service.GroupOwnerService;

/**
 * Created by hxguo on 7/16/14.
 */
public class GroupOwnerActivity extends AbstractGroupActivity
{
    private static final String TAG = "GroupOwnerActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.group_owner_layout);
        statusTxtView = (TextView) findViewById(R.id.status_text);
        statusTxtView.setMovementMethod(new ScrollingMovementMethod());
        statusTxtView.setText("");
        selfInfoFragment = (SelfInfoFragment)getFragmentManager().
                findFragmentById(R.id.self_info_frag_group_owner);

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

        Intent intent = new Intent(this, GroupOwnerService.class);
        String serviceName = GroupOwnerService.class.getCanonicalName();
        if(!SystemUtil.isServiceRunning(serviceName, this))
        {
            Log.d(TAG,"service is not running");
            startService(intent);
        }
        else
            Log.d(TAG,"service is running");
    }

    @Override
    protected void onDestroy()
    {
        Log.i(TAG,"onDestroy()");

        doUnbindService();
        stopService(new Intent(this, GroupOwnerService.class));
        super.onDestroy();
    }

    @Override
    protected void doBindService()
    {
        Log.i(TAG, "doBindService()");
        bindService(new Intent(getApplicationContext(),
                GroupOwnerService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        appendStatus("Binding to GroupOwnerService");
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

                case Constants.MSG_CLIENTS_LISTS_UPDATE:
                    updateClientsListFragment(msg);
                    break;

                case Constants.MSG_SELF_INFO_UPDATE:
                    updateSelfInfoFragment(msg);
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void updateClientsListFragment(Message msg)
    {
        Log.i(TAG,"updateClientsListFragment()");
        ClientsListFragment fragment = (ClientsListFragment)
                getFragmentManager().findFragmentById(R.id.connected_peers_list);

        WifiP2pGroup group = (WifiP2pGroup)msg.obj;
        fragment.updateList(group);

    }
}
