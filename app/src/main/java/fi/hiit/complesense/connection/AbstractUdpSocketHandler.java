package fi.hiit.complesense.connection;

import android.os.Messenger;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Timer;

/**
 * Created by hxguo on 7/22/14.
 */
public abstract class AbstractUdpSocketHandler extends Thread
{
    private static final String TAG = "AbstractUDPSocketHandler";
    protected final Timer timer;
    protected Messenger remoteMessenger;
    protected DatagramPacket initPacket;

    protected AbstractUdpSocketHandler(Messenger messenger)
    {
        remoteMessenger = messenger;
        timer = new Timer();
    }


    @Override
    public abstract void run();
    public abstract void stopHandler();

    public static void closeSocket(DatagramSocket socket)
    {
        Log.i(TAG, "closeSocket(DatagramSocket)");
        if (socket != null && !socket.isClosed())
            socket.close();
    }

}
