package fi.hiit.complesense.connection;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.SystemMessage;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by rocsea0626 on 30.8.2014.
 */
public class UdpConnectionRunnable implements Runnable, Handler.Callback
{
    private static final String TAG = "UdpConnectionRunnable";
    public static final int ID_DATAGRAM_PACKET = 10348;

    public final Messenger mMessenger;
    protected final ServiceHandler serviceHandler;
    protected DatagramSocket socket;

    protected byte[] sendBuf = new byte[Constants.MAX_BUF];
    protected byte[] recBuf = new byte[Constants.MAX_BUF];
    protected DatagramPacket recPacket = new DatagramPacket(recBuf, recBuf.length);

    public interface UdpConnectionListner
    {
        public void onReceiveLastRttReply(long currentTimeMillis, SocketAddress fromAddr);
    }
    public UdpConnectionRunnable(ServiceHandler serviceHandler,
                              DatagramSocket socket) throws IOException
    {
        this.serviceHandler = serviceHandler;
        this.socket = socket;

        HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
        handlerThread.start();

        // Get the looper from the handlerThread
        // Note: this may return null
        Looper looper = handlerThread.getLooper();
        // Create a new handler - passing in the looper to use and this class as
        // the message handler
        Handler handler = new Handler(looper, this);
        mMessenger = new Messenger(handler);

    }

    @Override
    public boolean handleMessage(Message message) {
        return false;
    }

    @Override
    public void run()
    {
        Log.i(TAG, "run()");

        //Log.i(TAG,"Query available sensors on the connected client");
        while (!Thread.currentThread().isInterrupted())
        {
            try
            {
                socket.receive(recPacket);
                //remoteSocketAddr = recPacket.getSocketAddress();//todo: remoteSocketAddr is not synchronized
                send2ServiceHandler(recPacket);

                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedException();

            } catch (IOException e)
            {
                Log.e(TAG, "disconnected" + e.toString());
                break;
            } catch (InterruptedException e)
            {
                Log.e(TAG, e.toString());
                break;
            }
        }
        Log.w(TAG,"Group Owner UDP connection terminates..");
    }

    protected void send2ServiceHandler(DatagramPacket packet) throws SocketException
    {
        Message msg = serviceHandler.getHandler().obtainMessage();
        msg.what = UdpConnectionRunnable.ID_DATAGRAM_PACKET;
        msg.arg1 = SystemMessage.ID_SYSTEM_MESSAGE;
        DatagramPacket packetCopy = new DatagramPacket(packet.getData(),
                0 , packet.getLength(),packet.getSocketAddress());
        msg.obj = packetCopy;
        msg.replyTo = mMessenger;
        msg.sendToTarget();
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
        Log.d(TAG,"write()" + socket.getLocalSocketAddress().toString()
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

    public void stopRunnable()
    {
        Log.i(TAG,"stopRunnable()");
        socket.close();
    }

    public void replyRttQuery(byte[] payload, SocketAddress remoteSocketAddr,
                              UdpConnectionRunnable.UdpConnectionListner listener)
    {
        ByteBuffer bb = ByteBuffer.wrap(payload);
        long startTime = bb.getLong();
        int rounds = bb.getInt();
        int strLen = payload.length - Long.SIZE/8 - Integer.SIZE/8;
        byte strBuf[] = new byte[strLen];
        bb.get(strBuf);
        String senderSocketAddrStr = new String(strBuf);
        Log.i(TAG,"replyRttQuery(rounds: "+rounds+" senderSocketAddrStr: " + senderSocketAddrStr + ")");
        String localSocketAddrStr = socket.getLocalSocketAddress().toString();
        Log.i(TAG,"replyRttQuery(localSocketAddrStr: " + localSocketAddrStr + ")");

        if(rounds <= 0 && senderSocketAddrStr.equalsIgnoreCase(localSocketAddrStr) )
        {
            listener.onReceiveLastRttReply(startTime, remoteSocketAddr);
        }
        else{
            if(senderSocketAddrStr.equalsIgnoreCase(localSocketAddrStr) )
                --rounds;
            String host = SystemUtil.getHost(senderSocketAddrStr);
            int port = SystemUtil.getPort(senderSocketAddrStr);

            write(SystemMessage.makeRttQuery(startTime, rounds,
                    new InetSocketAddress(host, port)), remoteSocketAddr);
        }
    }
}
