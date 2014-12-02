package fi.hiit.complesense.service;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.ClientServiceHandler;
import fi.hiit.complesense.core.GroupOwnerServiceHandler;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 7/11/14.
 */
public class TestingService extends AbstractGroupService
{
    public static final int START_TESTING = 2;
    public static final int STOP_TESTING = 3;
    private ArrayList<ServiceHandler> clientsList;

    private static final String TAG = "TestingService";
    // Binder given to clients
    private InetAddress localHost;
    private Messenger uiMessenger;

    GroupOwnerServiceHandler ownerServiceHanlder;
    private boolean testRunning = false;

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {

    }

    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler
    {
        private boolean hasSent = false;

        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case START_TESTING:
                    startTesting(msg.replyTo, Constants.NUM_CLIENTS);
                    break;
                case STOP_TESTING:
                    stopSelf();
                    break;
                case Constants.SERVICE_MSG_STATUS_TXT_UPDATE:
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            (String) msg.obj);
                    break;
                case Constants.SERVICE_MSG_STEREO_IMG_REQ:
                    SystemUtil.sendTakeImageReq(uiMessenger);
                    break;
                case Constants.SERVICE_MSG_TAKEN_IMG:
                    if(msg.obj==null)
                        testSendImg2Server(null);
                    else
                        testSendImg2Server((ArrayList<String>) msg.obj);
                    hasSent = true;
                    break;
               default:
                    super.handleMessage(msg);
            }
        }

        private void testSendImg2Server(ArrayList<String> imageNames)
        {
            if(hasSent)
                return;
        /*
        * Send testing files
         */
            if(imageNames==null)
            {
                File testDir = new File(Constants.ROOT_DIR, "test_pic");
                if(testDir.exists())
                {
                    File[] files = testDir.listFiles();
                    for(File f:files)
                    {
                        if(f.getName().endsWith(".jpg"))
                        {
                            Log.i(TAG, "sendImg2Server(null" + ") @ thread id: " + Thread.currentThread().getId() );
                            for(ServiceHandler serviceHandler : clientsList)
                            {
                            }

                            return;
                        }
                    }
                }

            }
            /*
            * Use just captured files from camera
             */
            else
            {
                //Log.i(TAG, "imageNames: " + imageNames);
                for(ServiceHandler serviceHandler : clientsList)
                {
                    try{
                        JSONArray jsonArray = new JSONArray();
                        for(String s: imageNames)
                            jsonArray.put(s);

                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put(JsonSSI.COMMAND, JsonSSI.SEND_DATA);
                        jsonObject.put(JsonSSI.DATA_TO_SEND, jsonArray);

                        Message msg = Message.obtain(serviceHandler.getHandler(),
                                ServiceHandler.JSON_RESPONSE_BYTES, jsonObject);
                        msg.sendToTarget();
                    } catch (JSONException e) {
                        Log.e(TAG, e.toString());
                    }
                }

            }
        }
    }



    @Override
    public void onCreate()
    {
        super.onCreate();
        Log.v(TAG, "onCreate()");
        clientsList = new ArrayList<ServiceHandler>(Constants.NUM_CLIENTS);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    localHost = InetAddress.getLocalHost();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        };
        new Thread(runnable).start();

        mMessenger = new Messenger(new IncomingHandler());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.i(TAG, "Received start id " + startId + ": " + intent);

        // Don't restart the GroupOwnerService automatically if its
        // process is killed while it's running.
        return Service.START_NOT_STICKY;
    }


    public void startTesting(Messenger uiMessenger, int numClients)
    {
        Log.i(TAG,"startTesting(client="+numClients+")");
        if(!testRunning)
        {
            testRunning = true;
            if(localHost==null)
                return;

            this.uiMessenger = uiMessenger;

            //serverManager = new GroupOwnerManager(mMessenger,
            //        getApplication(), false);
            try {
                ownerServiceHanlder = new GroupOwnerServiceHandler(mMessenger, getApplicationContext());
                Log.i(TAG, "Starting GroupOwner from thread: " + Thread.currentThread().getId());
                ownerServiceHanlder.start();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            for(int i=0;i<Constants.NUM_CLIENTS;i++)
            {
                Log.i(TAG,"Creating client thread");
                int delay = (int)(1000 * Math.random());

                ClientServiceHandler client = new ClientServiceHandler(mMessenger,
                        getApplicationContext(), localHost, delay);
                clientsList.add(client);
            }

            Log.i(TAG, "Starting Client from thread: " + Thread.currentThread().getId());
            for(int i=0;i<Constants.NUM_CLIENTS;i++)
            {
                clientsList.get(i).start();
            }
            //killClients();
        }

    }

    private void killClients()
    {
        final long duration = 30000, interval = 5000;

        CountDownTimer countDownTimer = new CountDownTimer(duration, interval) {
            @Override
            public void onTick(long l) {
                Log.i(TAG, "onTick( "+ l+")");
                if(clientsList.size() > 0 && l < (duration - interval) )
                {
                    int index = (int)(Math.random() * clientsList.size());
                    Log.i(TAG, "kill client "+ index);
                    SystemUtil.sendStatusTextUpdate(uiMessenger,
                            "client " + index + " is killed");

                    ServiceHandler client =  clientsList.get(index);
                    client.stopServiceHandler();
                    clientsList.remove(client);
                }
            }

            @Override
            public void onFinish() {
                Log.i(TAG, "onFinnish(clientsList.size(): + " + clientsList.size() + ")");
                SystemUtil.sendStatusTextUpdate(uiMessenger, "onFinnish(clientsList.size(): + " + clientsList.size() + ")");
            }
        };
        countDownTimer.start();

    }


    @Override
    protected void start()
    {

    }

    @Override
    protected void stop()
    {
        Log.i(TAG, "stopTesting()");
        Log.e(TAG,"num of clients: " + clientsList.size());

        for(int i=0;i<clientsList.size();i++)
        {
            Log.i(TAG, "Stopping client thread " + i);
            clientsList.get(i).stopServiceHandler();
            try {
                clientsList.get(i).join();
            } catch (InterruptedException e) {
                Log.e(TAG, e.toString());
            }
        }
        Log.i(TAG, "All clients have been stoped");
        clientsList.clear();
        ownerServiceHanlder.stopServiceHandler();
    }

}
