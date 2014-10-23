package fi.hiit.complesense.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.Serializable;
import java.util.List;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.service.ClientOwnerService;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 7/25/14.
 */
public class ClientOwnerActivity extends AbstractGroupActivity
{

    private static final String TAG = "ClientOwnerActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        /*
        * Activity specific settings
        */
        setContentView(R.layout.demo_activity_main);

        selfInfoFragment = (SelfInfoFragment)getFragmentManager().
                findFragmentById(R.id.self_info_frag_client_owner);

        uiMessenger = new Messenger(new IncomingHandler());
        Intent intent = new Intent(this, ClientOwnerService.class);
        String serviceName = ClientOwnerService.class.getCanonicalName();
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
        Log.v(TAG, "onDestroy()");
        super.onDestroy();
        doUnbindService();
        stopService(new Intent(this, ClientOwnerService.class));
    }

    @Override
    protected void doBindService()
    {
        Log.v(TAG, "doBindService()");
        bindService(new Intent(getApplicationContext(),
                ClientOwnerService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    //    appendStatus("Binding to GroupOwnerService");
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
                case Constants.MSG_DNS_SERVICE_FOUND:
                    updateServersListFragment(msg);
                    break;


                case Constants.MSG_SELF_INFO_UPDATE:
                    updateSelfInfoFragment(msg);
                    break;

                case Constants.MSG_TAKE_IMAGE:
                    Uri fileUri = SystemUtil.getOutputMediaFileUri(Constants.MEDIA_TYPE_IMAGE);
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri); // set the image file name

                    startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }



    private void updateServersListFragment(Message msg)
    {
        Log.i(TAG,"updateServersListFragment()");

        String instanceName = msg.getData().getString(Constants.EXTENDED_DATA_INSTANCE_NAME);
        WifiP2pDevice srcDevice = (WifiP2pDevice)msg.obj;

        appendStatus("Find service instance: " + instanceName + " name: " +
                srcDevice.deviceName + " addr: " + srcDevice.deviceAddress);
    }

    private void updateClientsListFragment(Message msg)
    {
        Log.i(TAG,"updateClientsListFragment()");
        /*
        ClientsListFragment fragment = (ClientsListFragment)
                getFragmentManager().findFragmentById(R.id.connected_peers_list);

        WifiP2pGroup group = (WifiP2pGroup)msg.obj;
        fragment.updateList(group);
        */
        WifiP2pGroup group = (WifiP2pGroup)msg.obj;
        List<WifiP2pDevice> clientList = (List<WifiP2pDevice>)group.getClientList();
        for(WifiP2pDevice device:clientList)
            appendStatus(device.deviceName);


    }
}
