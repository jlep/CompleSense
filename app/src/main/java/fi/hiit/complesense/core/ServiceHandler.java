package fi.hiit.complesense.core;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AcceptorUDP;
import fi.hiit.complesense.connection.ConnectorCloud;
import fi.hiit.complesense.connection.ConnectorUDP;
import fi.hiit.complesense.connection.UdpConnectionRunnable;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 20.8.2014.
 */
public class ServiceHandler extends HandlerThread implements Handler.Callback
{
    private static final String TAG = "ServiceHandler";
    protected final Messenger serviceMessenger;
    private final Context context;
    private Handler handler;
    protected Map<String, AbstractSystemThread> eventHandlingThreads;


    public final SensorUtil sensorUtil;
    private boolean isGroupOwner;

    public ServiceHandler(Messenger serviceMessenger, String name,
                          Context context, boolean isGroupOwner, InetAddress ownerAddr, int delay)
    {
        super(name);
        this.serviceMessenger = serviceMessenger;
        this.context = context;
        sensorUtil = new SensorUtil(context);
        eventHandlingThreads =  new TreeMap<String, AbstractSystemThread>();
        this.isGroupOwner = isGroupOwner;
        init(ownerAddr, delay);
    }

    protected void init(InetAddress ownerAddr, int delay)
    {
        if(isGroupOwner)
        {
            AcceptorUDP acceptor = null;
            ConnectorCloud connectorCloud = new ConnectorCloud(this);
            try {
                acceptor = new AcceptorUDP(serviceMessenger, this);
                eventHandlingThreads.put(AcceptorUDP.TAG, acceptor);
                eventHandlingThreads.put(ConnectorCloud.TAG, connectorCloud);

            } catch (IOException e) {
                Log.e(TAG, e.toString());

            }
        }
        else
        {
            ConnectorUDP connector = null;
            try {
                connector = new ConnectorUDP(serviceMessenger, this, ownerAddr, delay);
                eventHandlingThreads.put(ConnectorUDP.TAG, connector);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    @Override
    protected void onLooperPrepared() {
        handler = new Handler(getLooper(), this);
    }

    @Override
    public boolean handleMessage(Message msg)
    {
        if(msg.what == UdpConnectionRunnable.ID_DATAGRAM_PACKET)
        {
            DatagramPacket packet = (DatagramPacket)msg.obj;
            SocketAddress fromAddr = packet.getSocketAddress();
            Log.i(TAG,"recv UDP packet from " +  fromAddr );

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
        Log.i(TAG, "recv: " + sm.toString() + " from " + fromAddr);
        //reply(msg.replyTo);
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

    public void startServiveHandler()
    {
        Log.i(TAG,"startServiceHandler()");
        this.start();
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
            iterator.next().getValue().stopThread();

        quit();
    }
    public void send2Cloud(String srcAddr, float[] values)
    {
        ConnectorCloud cloudConnector = (ConnectorCloud)eventHandlingThreads.get(ConnectorCloud.TAG);
        if(cloudConnector!=null)
        {
            if(cloudConnector.getConnectionRunnable()!=null)
            {
                String str = srcAddr + "->";
                for(float f:values)
                {
                    str += Float.toString(f);
                    str +=", ";
                }
                //cloudConnector.getConnectionRunnable().write(str, cloudConnector.getCloudSocketAddr() );
                //cloudConnector.getConnectionRunnable().write(str.getBytes());
            }
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

}
