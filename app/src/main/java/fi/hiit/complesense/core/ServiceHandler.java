package fi.hiit.complesense.core;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AbsAsyncIO;
import fi.hiit.complesense.connection.AcceptorUDP;
import fi.hiit.complesense.connection.AsyncServer;
import fi.hiit.complesense.connection.ConnectorUDP;
import fi.hiit.complesense.connection.EchoWorker;
import fi.hiit.complesense.connection.RspHandler;
import fi.hiit.complesense.connection.UdpConnectionRunnable;
import fi.hiit.complesense.connection.AsyncClient;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SensorUtil;
import fi.hiit.complesense.util.SystemUtil;

import static fi.hiit.complesense.json.JsonSSI.*;

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
    private final Context context;
    private Handler handler;
    protected Map<String, AbstractSystemThread> eventHandlingThreads;
    protected Map<String, AliveConnection> peerList; // Store all the alive connections

    public final SensorUtil sensorUtil;
    public final long delay;
    private boolean isGroupOwner;
    protected AbsAsyncIO absAsyncIO;

    public ServiceHandler(Messenger serviceMessenger, String name,
                          Context context, boolean isGroupOwner, InetAddress ownerAddr,
                          long delay)
    {
        super(name);
        this.serviceMessenger = serviceMessenger;
        this.context = context;
        sensorUtil = new SensorUtil(context);
        eventHandlingThreads =  new TreeMap<String, AbstractSystemThread>();
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
                EchoWorker worker = new EchoWorker();
                absAsyncIO = AsyncServer.getInstance(this, worker);
                new Thread(worker).start();
            }
            else
                absAsyncIO = new AsyncClient(this, ownerAddr);
        } catch (IOException e)
        {
            Log.e(TAG, e.toString());
            absAsyncIO = null;
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
                Log.i(TAG, "Receive: " + jsonObject.toString());

                SocketChannel socketChannel = (SocketChannel)jsonObject.get(JsonSSI.SOCKET_CHANNEL);
                Socket socket = socketChannel.socket();

                switch(jsonObject.getInt(COMMAND))
                {
                    case JsonSSI.NEW_CONNECTION:

                        JSONObject jsonRtt = JsonSSI.makeRttQuery(System.currentTimeMillis(),
                                Constants.RTT_ROUNDS, socket.getLocalAddress().toString(), socket.getLocalPort());
                        absAsyncIO.send(socketChannel, jsonRtt.toString().getBytes());
                        break;
                    case JsonSSI.C:
                        absAsyncIO.send(socketChannel,
                                JsonSSI.makeSensorDiscvoeryRep(sensorUtil.getLocalSensorTypeList()).toString().getBytes());
                        break;

                    case JsonSSI.N:
                        handleSensorTypesReply(jsonObject, socket);
                        updateStatusTxt("sensor list from " + socket +
                                ": " + jsonObject.getJSONArray(JsonSSI.SENSOR_TYPES));
                        break;
                    case JsonSSI.RTT_QUERY:
                        forwarRttQuery(jsonObject, socketChannel);
                        break;
                    default:
                        Log.i(TAG, "Unknown command...");
                        break;
                }

            } catch (JSONException e) {
                Log.i(TAG, e.toString());
            }
            return false;
        }

        return false;
    }

    private void handleSensorTypesReply(JSONObject jsonObject, Socket socket) throws JSONException
    {
        JSONArray jsonArray = jsonObject.getJSONArray(JsonSSI.SENSOR_TYPES);
        List<Integer> arrayList = new ArrayList<Integer>(jsonArray.length());
        for(int i=0;i<jsonArray.length();i++)
        {
            arrayList.add(jsonArray.getInt(i));
        }
        sensorUtil.initSensorValues(arrayList, socket.toString());
    }

    protected void handleSystemMessage(SystemMessage sm, SocketAddress fromAddr)
    {
        Log.d(TAG, "recv: " + sm.toString() + " from " + fromAddr);
        //reply(msg.replyTo);
        if(sm.getCmd()==SystemMessage.RTT)
        {
            UdpConnectionRunnable runnable = null;
            if(eventHandlingThreads.get(AcceptorUDP.TAG)!=null){
                runnable = ((AcceptorUDP)
                        eventHandlingThreads.get(AcceptorUDP.TAG)).getConnectionRunnable();
            }

            if(eventHandlingThreads.get(ConnectorUDP.TAG)!=null){
                runnable = ((ConnectorUDP)
                        eventHandlingThreads.get(ConnectorUDP.TAG)).getConnectionRunnable();
            }

            if(runnable==null)
                Log.e(TAG,"runnable is null");
            //runnable.replyRttQuery(sm.getPayload(), fromAddr, this);
        }
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
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }
        absAsyncIO.start();
        Iterator<Map.Entry<String, AbstractSystemThread>> iterator
                = eventHandlingThreads.entrySet().iterator();

        while(iterator.hasNext())
        {
            iterator.next().getValue().start();
        }
    }

    public void stopServiceHandler()
    {
        Log.i(TAG,"stopServiceHandler()");

        Iterator<Map.Entry<String, AbstractSystemThread>> iterator
                = eventHandlingThreads.entrySet().iterator();
        while(iterator.hasNext())
        {
            Map.Entry<String, AbstractSystemThread> entry = iterator.next();

            Log.e(TAG, "Stop thread: " + entry.getKey());
            entry.getValue().stopThread();
        }
        absAsyncIO.stopAsyncIO();

        quit();
    }

    protected void addNewConnection(SocketAddress socketAddress)
    {
        String str = "addNewConnection("+ socketAddress +")";
        Log.i(TAG,str);
        updateStatusTxt(str);
        AliveConnection aliveConnection = new AliveConnection(socketAddress, this);
        if(!peerList.containsKey(socketAddress.toString()))
            this.peerList.put(socketAddress.toString(), aliveConnection);
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
     * @param fromAddr: peer's address
     */
    public void onReceiveLastRttReply(long startTimeMillis, SocketAddress fromAddr)
    {
        //Log.i(TAG, "startTimeMillis: " + startTimeMillis + "currentTime: " + System.currentTimeMillis());
        long rttMeasurement = (System.currentTimeMillis() - startTimeMillis) / Constants.RTT_ROUNDS;
        peerList.get(fromAddr.toString()).setTimeDiff(rttMeasurement / 2);
        Log.i(TAG,"RTT between "+ fromAddr.toString() +" : " + rttMeasurement+ " ms");
    }

    private void forwarRttQuery(JSONObject jsonObject, SocketChannel socketChannel) throws JSONException {

        long startTime = jsonObject.getLong(JsonSSI.TIMESTAMP);
        int rounds = jsonObject.getInt(JsonSSI.ROUNDS);
        String originHost = (String)jsonObject.get(JsonSSI.ORIGIN_HOST);
        int originPort = jsonObject.getInt(JsonSSI.ORIGIN_PORT);
        String senderSocketAddrStr = socketChannel.socket().getRemoteSocketAddress().toString();

        Log.i(TAG, "replyRttQuery(rounds: " + rounds + " senderSocketAddrStr: " + senderSocketAddrStr + ")");
        String localSocketAddrStr = socketChannel.socket().getLocalSocketAddress().toString();
        Log.i(TAG, "replyRttQuery(localSocketAddrStr: " + localSocketAddrStr + ")");

        if(rounds <=0 && isOrigin(originHost, originPort, socketChannel.socket()))
        {
            onReceiveLastRttReply(startTime, socketChannel.socket().getLocalSocketAddress() );
        }
        else
        {
            if(isOrigin(originHost,originPort, socketChannel.socket())){
                --rounds;
            }
            JSONObject jsonForward = JsonSSI.makeRttQuery(startTime, rounds, originHost, originPort);
            absAsyncIO.send(socketChannel,jsonForward.toString().getBytes());
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
