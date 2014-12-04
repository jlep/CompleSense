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
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.connection.AcceptorWebSocket;
import fi.hiit.complesense.connection.AliveConnection;
import fi.hiit.complesense.connection.ConnectorWebSocket;
import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 20.8.2014.
 */
public class ServiceHandler extends HandlerThread
        implements Handler.Callback
{
    private static final String TAG = "ServiceHandler";
    public static final int JSON_RESPONSE_BYTES = 2697337;
    public static final int JSON_SYSTEM_STATUS = 5762346;

    public final String startTime = Long.toString(System.currentTimeMillis());

    protected final Messenger serviceMessenger;
    protected final Context context;
    private Handler handler;
    protected Map<String, AliveConnection> peerList; // Store all the alive connections

    //public final SensorUtil sensorUtil;
    public final long delay;
    private boolean isGroupOwner;
    public ConcurrentMap<String, AbsSystemThread> workerThreads;


    public ServiceHandler(Messenger serviceMessenger, String name,
                          Context context, boolean isGroupOwner, InetAddress ownerInetAddr,
                          long delay)
    {
        super(name);
        this.serviceMessenger = serviceMessenger;
        this.context = context;
        //sensorUtil = new SensorUtil(context);
        workerThreads = new ConcurrentHashMap<String, AbsSystemThread>();
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
        boolean ifSendToSubClass = false;
        if(msg.what == JSON_RESPONSE_BYTES)
            ifSendToSubClass = true;
        if(msg.what == JSON_SYSTEM_STATUS){
            try {
                handleSystemStatus((JSONObject)msg.obj);
            } catch (JSONException e) {
                Log.i(TAG, e.toString());
            }
        }
        return ifSendToSubClass;
    }

    protected void handleSystemStatus(JSONObject jsonObject) throws JSONException {
        int status = jsonObject.getInt(JsonSSI.SYSTEM_STATUS);

        switch (status){
            case JsonSSI.DISCONNECT:
                break;
            default:
                Log.i(TAG, context.getString(R.string.unknown_status));
                break;
        }
    }

    public Map<String, AliveConnection> getPeerList()
    {
        return peerList;
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
                workerThreads.get(key).start();
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }
    }

    public void stopServiceHandler()
    {
        Log.i(TAG,"stopServiceHandler(): " + workerThreads.keySet().toString());

        for(String key : workerThreads.keySet()){
            Log.e(TAG, "Stop thread: " + key);
            workerThreads.get(key).stopThread();
            /*
            try {
                workerThreads.get(key).join();
                Log.i(TAG, "Thread " + TAG + " has stopped");
            } catch (InterruptedException e) {
                Log.i(TAG, e.toString());
            }
            */
        }
        Log.e(TAG, "All threads have been stopped");
        quit();
    }

    public void addNewConnection(WebSocket webSocket)
    {
        String str = "addNewConnection("+ webSocket.toString() +")";
        Log.i(TAG,str);
        updateStatusTxt(str);
        AliveConnection aliveConnection = new AliveConnection(webSocket);
        peerList.put(webSocket.toString(), aliveConnection);
    }

    public void removeFromPeerList(WebSocket webSocket)
    {
        String str = "removeFromPeerList("+ webSocket.toString() +")";
        Log.i(TAG,str);
        updateStatusTxt(str);
        peerList.remove(webSocket.toString());
    }

    public void send2Handler(String data, int messageType){
        //Log.i(TAG, "send2Handler()" + data);
        try{
            JSONObject jsonObject = new JSONObject(data);
            Message msg = Message.obtain(handler, messageType, jsonObject);
            msg.sendToTarget();
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
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

    protected void startImageCapture(long delay)
    {
        //Log.i(TAG,"updateStatusTxt()");
        Message msg = Message.obtain();
        msg.what = Constants.SERVICE_MSG_STEREO_IMG_REQ;
        try {
            serviceMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
