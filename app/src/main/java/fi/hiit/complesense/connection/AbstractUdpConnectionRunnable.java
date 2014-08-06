package fi.hiit.complesense.connection;

import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.Timer;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.SystemMessage;

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


    // Messenger points to Remote service
    protected Messenger remoteMessenger;

    public AbstractUdpConnectionRunnable(DatagramSocket socket,
                                         Messenger messenger) throws IOException
    {
        timer = new Timer();
        this.socket = socket;
        remoteMessenger = messenger;
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
        Log.i(TAG,"write()" + remoteSocketAddr.toString());
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
        Log.i(TAG,"write()" + remoteSocketAddr.toString());
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

    protected void updateStatusTxt(String str) throws RemoteException
    {
        //Log.i(TAG,"updateStatusTxt()");
        Message msg = Message.obtain();
        msg.what = Constants.SERVICE_MSG_STATUS_TXT_UPDATE;
        msg.obj = str;
        remoteMessenger.send(msg);
    }

    public void signalStop()
    {
        Log.i(TAG, "signalStop()");
        timer.cancel();
        socket.close();
    }



    @Override
    public abstract void run();

    protected abstract void parseSystemMessage(SystemMessage sm);

}
