package fi.hiit.complesense.core;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AbsAsyncIO;
import fi.hiit.complesense.connection.AcceptorUDP;
import fi.hiit.complesense.connection.AsyncClient;
import fi.hiit.complesense.connection.AsyncServer;
import fi.hiit.complesense.connection.ConnectorUDP;
import fi.hiit.complesense.connection.UdpConnectionRunnable;
import fi.hiit.complesense.json.JsonSSI;

import static fi.hiit.complesense.json.JsonSSI.COMMAND;

/**
 * Created by hxguo on 20.8.2014.
 */
public class ServiceHandler extends HandlerThread
        implements Handler.Callback,AliveConnection.AliveConnectionListener
{
    private static final String TAG = "ServiceHandler";
    public static final int JSON_RESPONSE_BYTES = 2697337;

    public final String startTime = Long.toString(System.currentTimeMillis());

    protected final Messenger serviceMessenger;
    protected final Context context;
    private Handler handler;
    protected Map<String, AliveConnection> peerList; // Store all the alive connections

    //public final SensorUtil sensorUtil;
    public final long delay;
    private boolean isGroupOwner;
    protected AbsAsyncIO absAsyncIO;
    public Map<String, AbsSystemThread> workerThreads;


    public ServiceHandler(Messenger serviceMessenger, String name,
                          Context context, boolean isGroupOwner, InetAddress ownerAddr,
                          long delay)
    {
        super(name);
        this.serviceMessenger = serviceMessenger;
        this.context = context;
        //sensorUtil = new SensorUtil(context);
        workerThreads = new HashMap<String, AbsSystemThread>();
        peerList = new HashMap<String, AliveConnection>();
        this.isGroupOwner = isGroupOwner;

        this.delay = delay;
        init(ownerAddr);
    }

    protected void init(InetAddress ownerAddr)
    {
        try
        {
            if(isGroupOwner)
            {
                absAsyncIO = AsyncServer.getInstance(this);
                workerThreads.put(AsyncServer.TAG, absAsyncIO);
            }else{
                absAsyncIO = new AsyncClient(this, ownerAddr, Constants.SERVER_PORT);
                workerThreads.put(AsyncClient.TAG, absAsyncIO);
            }

        } catch (IOException e)
        {
            Log.e(TAG, e.toString());
        }

    }

    @Override
    protected void onLooperPrepared()
    {
        Log.i(TAG, "onLooperPrepared() ");
        handler = new Handler(getLooper(), this);
        startWorkerThreads();
    }

    @Override
    public boolean handleMessage(Message msg)
    {
        if(msg.what == JSON_RESPONSE_BYTES)
        {
            try
            {
                JSONObject jsonObject = (JSONObject)msg.obj;
                Log.v(TAG, "Receive: " + jsonObject.toString());
                SocketChannel socketChannel = (SocketChannel)jsonObject.get(JsonSSI.SOCKET_CHANNEL);

                switch(jsonObject.getInt(COMMAND))
                {
                    case JsonSSI.RTT_QUERY:
                        forwarRttQuery(jsonObject, socketChannel);
                        return true;
                }
            } catch (JSONException e) {
                Log.i(TAG, e.toString());
            }
        }
        return false;
    }

    public Map<String, AliveConnection> getPeerList()
    {
        return peerList;
    }

    protected void reply(Messenger messenger)
    {
        Message msg = new Message();
        msg.obj = SystemMessage.makeRelayListenerReply();
        try {
            messenger.send(msg);
        } catch (RemoteException e) {
            Log.i(TAG,e.toString());
        }
    }


    public Handler getHandler(){
        if(handler==null)
            Log.i(TAG,"handler is null");
        return handler;
    }

    private void startWorkerThreads()
    {
        Log.i(TAG,"startWorkerThreads(delay: " + delay +")");
        try
        {
            Thread.sleep(delay);
            for(String key : workerThreads.keySet())
            {
                workerThreads.get(key).start();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }
    }

    public void stopServiceHandler()
    {
        Log.i(TAG,"stopServiceHandler()");
        for(String key : workerThreads.keySet())
        {
            Log.e(TAG, "Stop thread: " + key);
            AbsSystemThread absSystemThread = workerThreads.get(key);
            absSystemThread.stopThread();
            try {
                absSystemThread.join();
            } catch (InterruptedException e) {
                Log.i(TAG, e.toString());
            }
        }
        Log.e(TAG, "All threads have been stopped");
        quit();
    }

    protected void addNewConnection(SocketAddress socketAddress)
    {
        String str = "addNewConnection("+ socketAddress +")";
        Log.i(TAG,str);
        updateStatusTxt(str);
        AliveConnection aliveConnection = new AliveConnection(socketAddress, this);
        peerList.put(socketAddress.toString(), aliveConnection);

    }


    protected void removeFromPeerList(String socketAddrStr)
    {
        String str = "removeFromPeerList("+ socketAddrStr +")";
        Log.i(TAG,str);
        updateStatusTxt(str);
        peerList.remove(socketAddrStr);
    }


    protected void renewPeerList(String socketAddrStr)
    {
        String str = "renewPeerList("+ socketAddrStr +")";
        Log.d(TAG,str);
        //updateStatusTxt(str);
        ((AliveConnection)(peerList.get(socketAddrStr)) ).resetCheckTime();
    }

    public void updateStatusTxt(String str)
    {
        //Log.i(TAG,"updateStatusTxt()");
        Message msg = Message.obtain();
        msg.what = Constants.SERVICE_MSG_STATUS_TXT_UPDATE;
        msg.obj = str;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    protected void startImageCapture(SocketAddress remoteSocketAddr)
    {
        //Log.i(TAG,"updateStatusTxt()");
        Message msg = Message.obtain();
        msg.what = Constants.SERVICE_MSG_STEREO_IMG_REQ;
        msg.obj = remoteSocketAddr;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionTimeout(SocketAddress socketAddress)
    {
        Log.i(TAG, "onConnectionTimeout(" + socketAddress.toString() + ")");
        removeFromPeerList(socketAddress.toString());
    }

    /**
     * Event is fired when the RTT query sender receives enough RTT reply from a peer
     * @param startTimeMillis: requester local time, when RTT query was sent
     * @param socketChannel: peer's socketChannel
     */
    public void onReceiveLastRttReply(long startTimeMillis, SocketChannel socketChannel)
    {
        //Log.i(TAG, "startTimeMillis: " + startTimeMillis + "currentTime: " + System.currentTimeMillis());
        long rttMeasurement = (System.currentTimeMillis() - startTimeMillis) / Constants.RTT_ROUNDS;
        String remoteSocketAddr = socketChannel.socket().getRemoteSocketAddress().toString();
        peerList.get(remoteSocketAddr).setDelay(rttMeasurement / 2);
        Log.i(TAG,"RTT between "+ remoteSocketAddr +" : " + rttMeasurement+ " ms");
    }

    private void forwarRttQuery(JSONObject jsonObject, SocketChannel socketChannel) throws JSONException {

        long startTime = jsonObject.getLong(JsonSSI.TIMESTAMP);
        int rounds = jsonObject.getInt(JsonSSI.ROUNDS);
        String originHost = (String)jsonObject.get(JsonSSI.ORIGIN_HOST);
        int originPort = jsonObject.getInt(JsonSSI.ORIGIN_PORT);
        String senderSocketAddrStr = socketChannel.socket().getRemoteSocketAddress().toString();

        Log.v(TAG, "replyRttQuery(rounds: " + rounds + " senderSocketAddrStr: " + senderSocketAddrStr + ")");
        String localSocketAddrStr = socketChannel.socket().getLocalSocketAddress().toString();
        Log.v(TAG, "replyRttQuery(localSocketAddrStr: " + localSocketAddrStr + ")");

        if(rounds <=0 && isOrigin(originHost, originPort, socketChannel.socket()))
        {
            onReceiveLastRttReply(startTime, socketChannel);
        }
        else
        {
            if(isOrigin(originHost,originPort, socketChannel.socket())){
                --rounds;
            }
            JSONObject jsonForward = JsonSSI.makeRttQuery(startTime, rounds, originHost, originPort);
            absAsyncIO.send(socketChannel, jsonForward.toString().getBytes());
        }
    }

    private boolean isOrigin(String host, int port, Socket localSocket)
    {
        if(host.equalsIgnoreCase(localSocket.getLocalAddress().toString()) &&
                port == localSocket.getLocalPort())
            return true;

        return false;
    }

}
