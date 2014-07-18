package fi.hiit.complesense.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import fi.hiit.complesense.SensorUtil;
import fi.hiit.complesense.SystemMessage;
import fi.hiit.complesense.core.ClientManager;
import fi.hiit.complesense.core.GroupOwnerManager;

/**
 * Created by hxguo on 7/11/14.
 */
public class TestingService extends Service
{
    public static final int NUM_CLIENTS = 1;
    public static String UI_HANDLER ="fi.hiit.complesense.service.TestingService.UI_HANDLER";
    private ArrayList<ClientManager> clientsList;
    private GroupOwnerManager serverManager;

    private static final String TAG = "TestingService";
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private Messenger uiMessenger;
    private InetAddress localHost;



    @Override
    public void onCreate()
    {
        Log.i(TAG,"onCreate()");
        clientsList = new ArrayList<ClientManager>(NUM_CLIENTS);
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
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.i(TAG, "Received start id " + startId + ": " + intent);

        // Don't restart the GroupOwnerService automatically if its
        // process is killed while it's running.
        return Service.START_NOT_STICKY;
    }


    public void testSensorListParsing()
    {
        Log.i(TAG,"testSensorListParsing()");
        List<Integer> sList= SensorUtil.getLocalSensorTypeList(getApplicationContext());
        Log.i(TAG,sList.toString());

        SystemMessage sm = SystemMessage.makeSensorsListReplyMessage(sList);

        SystemMessage.parseSensorTypeList(sm);

        int a = 13;
        byte[] bArray = SystemMessage.int2Bytes(a);
        Log.i(TAG, Integer.toString(SystemMessage.byteArray2Int(bArray)) );

    }

    public void startTesting(Messenger uiMessenger, int numClients)
    {
        Log.i(TAG,"startTesting(client="+numClients+")");


        if(localHost==null)
            return;

        this.uiMessenger = uiMessenger;
        Log.i(TAG,"Creating GroupOwner thread");

        serverManager = new GroupOwnerManager(uiMessenger,
                getApplication() );
        try {
            serverManager.start();
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        }

        for(int i=0;i<NUM_CLIENTS;i++)
        {
            Log.i(TAG,"Creating client thread");
            clientsList.add(new ClientManager(uiMessenger,
                    getApplication() ));
        }

        for(int i=0;i<NUM_CLIENTS;i++)
        {
            Log.i(TAG, "Starting client thread");
            try {
                clientsList.get(i).start(localHost, (int)(Math.random()*1000) );
            } catch (IOException e) {
                Log.i(TAG,e.toString());
            }
        }

    }


    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder
    {
        public TestingService getService() {
            // Return this instance of LocalService so clients can call public methods
            return TestingService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.i(TAG,"onBInd()");
        return mBinder;
    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG,"onDestroy()");

    }

    public void stopTesting()
    {
        Log.i(TAG, "stopTesting()");
        Log.e(TAG,"num of clients: " + clientsList.size());

        for(int i=0;i<NUM_CLIENTS;i++)
        {
            Log.i(TAG, "Stopping client thread");
            clientsList.get(i).stop();
        }
        clientsList.clear();
        serverManager.stop();
    }


}
