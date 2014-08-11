package fi.hiit.complesense.connection;

import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.SystemMessage;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 7/22/14.
 */
public abstract class AbstractUdpConnectionRunnable implements Runnable
{
    private static final String TAG = "AbstractUdpConnectionRunnable";
    protected final Timer timer;
    protected final DatagramSocket socket;
    protected byte[] sendBuf = new byte[Constants.MAX_BUF];
    protected byte[] recBuf = new byte[Constants.MAX_BUF];
    protected DatagramPacket recPacket = new DatagramPacket(recBuf, recBuf.length);
    protected List<Long> rttMeasurements;

    protected Thread audioStreamThread;


    // Messenger points to Remote service
    protected Messenger remoteMessenger;

    public AbstractUdpConnectionRunnable(DatagramSocket socket,
                                         Messenger messenger) throws IOException
    {
        timer = new Timer();
        this.socket = socket;
        remoteMessenger = messenger;
        rttMeasurements = new ArrayList<Long>();
    }
    public void write(String msg
            , SocketAddress remoteSocketAddr)
    {
        write(msg.getBytes(),remoteSocketAddr);
    }


    public void write(SystemMessage sm,
                      SocketAddress remoteSocketAddr)
    {
        write(sm.toBytes(),remoteSocketAddr);
    }

    public void write(byte[] bytes, SocketAddress remoteSocketAddr)
    {
        Log.i(TAG,"write()" + socket.getLocalSocketAddress().toString()
                + "->" +remoteSocketAddr.toString());

        //sendBuf = bytes;
        try
        {
            DatagramPacket out = new DatagramPacket(bytes,
                    bytes.length, remoteSocketAddr);
            socket.send(out);
        } catch (IOException e)
        {
            Log.e(TAG, "Exception during write", e);
        }
    }


    public void write(byte[] bytes, int bytesLen, SocketAddress remoteSocketAddr)
    {
        Log.i(TAG,"write()" + socket.getLocalSocketAddress().toString()
                + "->" +remoteSocketAddr.toString());
        try
        {
            DatagramPacket out = new DatagramPacket(bytes,
                    bytesLen, remoteSocketAddr);
            socket.send(out);
        } catch (IOException e)
        {
            Log.e(TAG, "Exception during write", e);
        }
    }

    protected void updateStatusTxt(String str)
    {
        //Log.i(TAG,"updateStatusTxt()");
        Message msg = Message.obtain();
        msg.what = Constants.SERVICE_MSG_STATUS_TXT_UPDATE;
        msg.obj = str;
        try {
            remoteMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void signalStop()
    {
        Log.i(TAG, "signalStop()");
        if(audioStreamThread!=null)
            audioStreamThread.interrupt();
        timer.cancel();
        socket.close();
    }

    public void forwardRttQuery(byte[] payload, SocketAddress remoteSocketAddr)
    {
        ByteBuffer bb = ByteBuffer.wrap(payload);
        long timeStamp = bb.getLong();

        ArrayDeque<String> hops = SystemMessage.parseRttQuery(payload);
        Log.i(TAG, "RTT hops: " + hops.toString());

        if(socket.getLocalSocketAddress().toString().contains("/:::"))
        {
            //must be the group owner socket
            String nextHost = SystemUtil.getHost(hops.peek());
            int nextPort = SystemUtil.getPort(hops.peek());

            Log.i(TAG,"nextSocketAddr: " + nextHost+":"+nextPort);
            write(SystemMessage.makeRttQuery(timeStamp, hops), new InetSocketAddress(nextHost, nextPort));

        }
        else
        {
            String socketAddrStr = hops.poll();
            Log.i(TAG,"poll socketAddr: " + socketAddrStr);
            if(!socketAddrStr.equals(socket.getLocalSocketAddress().toString()))
            {
                Log.e(TAG,"packet wrong host: " + socketAddrStr + "/" +
                        socket.getLocalSocketAddress().toString());
                return;
            }
            if(hops.size()==0)
            {
                //Log.i(TAG,"RTT: " + Long.toString(System.currentTimeMillis()- timeStamp));
                long rtt = System.currentTimeMillis()-timeStamp;
                Log.i(TAG,"pacekt has reached its destination: " + rtt);

                rttMeasurements.add(rtt);
                Iterator<Long> iter = rttMeasurements.iterator();
                long sum = 0;
                while(iter.hasNext())
                    sum += (Long)iter.next();

                updateStatusTxt("RTT: " + Float.toString((float)(sum/rttMeasurements.size())));
                return;
            }

            if(timeStamp==0)
                timeStamp = System.currentTimeMillis();
            write(SystemMessage.makeRttQuery(timeStamp, hops), remoteSocketAddr);
        }

    }

    @Override
    public abstract void run();

    protected abstract void parseSystemMessage(SystemMessage sm, SocketAddress remoteSocketAddr);

}
