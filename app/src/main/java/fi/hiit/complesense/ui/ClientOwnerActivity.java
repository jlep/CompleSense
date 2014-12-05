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

import java.io.File;
import java.io.Serializable;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.service.ClientOwnerService;
import fi.hiit.complesense.service.TestingService;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 7/25/14.
 */
public class ClientOwnerActivity extends AbstractGroupActivity
{

    public static final String TAG = "ClientOwnerActivity";
    public static final int START_AS_UNKNOWN = 0;
    public static final int START_AS_MASTER = 2;
    public static final int START_AS_CIENT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        /*
        * Activity specific settings
        */
        //setContentView(R.layout.demo_activity_main);
        selfInfoFragment = (SelfInfoFragment)getFragmentManager().
                findFragmentById(R.id.self_info_frag_client_owner);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(serviceMessenger!=null) {
                    try {
                        Message msg = Message.obtain(null,
                                Constants.SERVICE_MSG_STOP);
                        msg.replyTo = uiMessenger;
                        serviceMessenger.send(msg);
                    }
                    catch (RemoteException e){
                    }
                    finish();
                }
            }
        });

        uiMessenger = new Messenger(new IncomingHandler());
        /**
         * Class for interacting with the main interface of the service.
         */
        mConnection = new ServiceConnection()
        {
            public void onServiceConnected(ComponentName className,
                                           IBinder service)
            {
                serviceMessenger = new Messenger(service);
                Log.i(TAG, "onServiceConnected()");
                try {
                    Message msg = Message.obtain(null,
                            Constants.SERVICE_MSG_INIT_SERVICE);
                    msg.replyTo = uiMessenger;
                    serviceMessenger.send(msg);

                    if(mImagesOrientationFile != null){
                        msg = Message.obtain(null, Constants.SERVICE_MSG_TAKEN_IMG);
                        msg.obj = mImagesOrientationFile;
                        try {
                            serviceMessenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
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
                serviceMessenger = null;
                mIsBound = false;
                appendStatus("Disconnected from GroupClientService");

                /**
                 // As part of the sample, tell the user what happened.
                 Toast.makeText(GroupClientActivity.this, R.string.remote_service_disconnected,
                 Toast.LENGTH_SHORT).show();
                 */
            }
        };


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
        private int startState = START_AS_UNKNOWN;

        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case Constants.MSG_SERVICE_INIT_DONE:
                    startServiceWork(startState);
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
                    appendStatus("Receive image taking request");
                    //imageUri = SystemUtil.getOutputMediaFileUri(Constants.MEDIA_TYPE_IMAGE);
                    //Log.i(TAG, "imageUri: " + imageUri.toString() );
                    Intent intent = new Intent(getApplicationContext(), TakePhotoActivity.class);
                    startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
                    break;

                case Constants.MSG_MASTER_DIES:
                    String txt = "Master is gone";
                    Log.e(TAG, txt);
                    appendStatus(txt);
                    boolean startAsMaster = (Boolean)msg.obj;
                    startState = (startAsMaster) ? START_AS_MASTER:START_AS_CIENT;
                    restartService();

                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    private void restartService() {
        RestartServiceRunnable runnable = new RestartServiceRunnable();
        new Thread(runnable).start();

    }

    class RestartServiceRunnable implements Runnable{

        @Override
        public void run() {
            doUnbindService();
            stopService(new Intent(getApplicationContext(), ClientOwnerService.class));

            try {
                for(int i=0;i<10;i++){
                    appendStatus(getString(R.string.restart_serivce));
                    Thread.sleep(10000);
                    String serviceName = ClientOwnerService.class.getCanonicalName();
                    if(!SystemUtil.isServiceRunning(serviceName, getApplicationContext()))
                    {
                        Log.i(TAG,"service is not running");
                        startService(new Intent(getApplicationContext(), ClientOwnerService.class));
                        Thread.sleep(1000);
                        doBindService();
                        break;
                    }
                    else
                        Log.i(TAG,"service is running");
                }
            } catch (InterruptedException e) {
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        Log.i(TAG, "onActivityResult(requestCode: "+ requestCode + ")");
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE)
        {
            if (resultCode == RESULT_OK) {
                // Image captured and saved to imageUri specified in the Intent
                int numImages = intent.getIntExtra(TakePhotoActivity.IMAGE_NUM, 0);

                String txt;
                if(numImages>0){
                    mImagesOrientationFile = intent.getStringExtra(TakePhotoActivity.IMAGE_ORIENTATION_FILE);
                    txt = String.format("%d images saved to %s", numImages, mImagesOrientationFile);
                }else{
                    txt = "no images are taken";
                }
                appendStatus(txt);

            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the image capture
                appendStatus("Image Capture canceled");
            } else {
                // Image capture failed, advise user
                appendStatus("Image Capture failed");
            }
        }

        if (requestCode == CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Video captured and saved to imageUri specified in the Intent
                appendStatus("Video saved to: " + intent.getData().toString());
            } else if (resultCode == RESULT_CANCELED) {
                // User cancelled the video capture
            } else {
                // Video capture failed, advise user
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
