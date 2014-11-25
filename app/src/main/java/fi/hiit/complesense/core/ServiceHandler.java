package fi.hiit.complesense.core;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AcceptorWebSocket;
import fi.hiit.complesense.connection.AliveConnection;
import fi.hiit.complesense.connection.ConnectorWebSocket;
import fi.hiit.complesense.json.JsonSSI;

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
    public Map<String, AbsSystemThread> workerThreads;


    public ServiceHandler(Messenger serviceMessenger, String name,
                          Context context, boolean isGroupOwner, InetAddress ownerInetAddr,
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
        init(ownerInetAddr);
    }

    protected void init(InetAddress ownerInetAddr)
    {
        try{
            if(isGroupOwner){
                AcceptorWebSocket acceptor = new AcceptorWebSocket(this);
                workerThreads.put(AcceptorWebSocket.TAG, acceptor);
            }else{
                ConnectorWebSocket connector = new ConnectorWebSocket(this, ownerInetAddr);
                workerThreads.put(ConnectorWebSocket.TAG, connector);
            }
        } catch (IOException e) {
            Log.e(TAG, "Web connection creation failed: " + e.toString());
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
            return true;
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

    protected void addNewConnection(WebSocket webSocket)
    {
        String str = "addNewConnection("+ webSocket.toString() +")";
        Log.i(TAG,str);
        updateStatusTxt(str);
        AliveConnection aliveConnection = new AliveConnection(webSocket, this);
        peerList.put(webSocket.toString(), aliveConnection);

    }


    protected void removeFromPeerList(String socketAddrStr)
    {
        String str = "removeFromPeerList("+ socketAddrStr +")";
        Log.i(TAG,str);
        updateStatusTxt(str);
        peerList.remove(socketAddrStr);
    }

    public void send2Handler(String data){
        //Log.i(TAG, "send2Handler()" + data);
        try{
            JSONObject jsonObject = new JSONObject(data);
            Message msg = Message.obtain(handler, JSON_RESPONSE_BYTES, jsonObject);
            msg.sendToTarget();
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
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
    public void onConnectionTimeout(WebSocket webSocket)
    {
        Log.i(TAG, "onConnectionTimeout(" + webSocket.toString() + ")");
        removeFromPeerList(webSocket.toString());
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

    /*
    private void forwarRttQuery(JSONObject jsonObject, WebSocket webSocket) throws JSONException {

        long startTime = jsonObject.getLong(JsonSSI.TIMESTAMP);
        int rounds = jsonObject.getInt(JsonSSI.ROUNDS);
        String originHost = (String)jsonObject.get(JsonSSI.ORIGIN_HOST);
        int originPort = jsonObject.getInt(JsonSSI.ORIGIN_PORT);

        Log.v(TAG, "replyRttQuery(rounds: " + rounds + " senderSocketAddrStr: " + senderSocketAddrStr + ")");
        String localSocketAddrStr = socketChannel.socket().getLocalSocketAddress().toString();
        Log.v(TAG, "replyRttQuery(localSocketAddrStr: " + localSocketAddrStr + ")");

        if(rounds <=0 && isOrigin(originHost, originPort, webSocket.getSocket().))
        {
            onReceiveLastRttReply(startTime, socketChannel);
        }
        else
        {
            if(isOrigin(originHost,originPort, socketChannel.socket())){
                --rounds;
            }
            JSONObject jsonForward = JsonSSI.makeRttQuery(startTime, rounds, originHost, originPort);
            webConnection.send(socketChannel, jsonForward.toString().getBytes());
        }
    }
    */
    private boolean isOrigin(String host, int port, Socket localSocket)
    {
        if(host.equalsIgnoreCase(localSocket.getLocalAddress().toString()) &&
                port == localSocket.getLocalPort())
            return true;

        return false;
    }

}
