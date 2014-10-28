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
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Iterator;
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

import static fi.hiit.complesense.json.JsonSSI.*;

/**
 * Created by hxguo on 20.8.2014.
 */
public class ServiceHandler extends HandlerThread
        implements Handler.Callback,AliveConnection.AliveConnectionListener, UdpConnectionRunnable.UdpConnectionListner
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
                JSONObject jsonObject = new JSONObject(new String((byte[]) msg.obj));
                Log.i(TAG, "Receive: " + jsonObject.toString());

                switch(jsonObject.getInt(COMMAND))
                {
                    case JsonSSI.C:
                        //absAsyncIO.sensorUtil.getLocalSensorTypeList();
                        break;
                }

            } catch (JSONException e) {
                Log.i(TAG, e.toString());
            }
            return false;
        }



        if(msg.what == UdpConnectionRunnable.ID_DATAGRAM_PACKET)
        {
            DatagramPacket packet = (DatagramPacket)msg.obj;
            SocketAddress fromAddr = packet.getSocketAddress();
            Log.d(TAG,"recv UDP packet from " +  fromAddr );

            if(msg.arg1 == SystemMessage.ID_SYSTEM_MESSAGE)
            {
                // received a SystemMessage
                SystemMessage sm = SystemMessage.getFromBytes(packet.getData());
                handleSystemMessage(sm, fromAddr);
            }
        }

        return false;
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
            runnable.replyRttQuery(sm.getPayload(), fromAddr, this);
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
    @Override
    public void onReceiveLastRttReply(long startTimeMillis, SocketAddress fromAddr)
    {
        //Log.i(TAG, "startTimeMillis: " + startTimeMillis + "currentTime: " + System.currentTimeMillis());
        long rttMeasurement = (System.currentTimeMillis() - startTimeMillis) / Constants.RTT_ROUNDS;
        peerList.get(fromAddr.toString()).setTimeDiff(rttMeasurement / 2);
        Log.i(TAG,"RTT between "+ fromAddr.toString() +" : " + rttMeasurement+ " ms");
    }

}
