package fi.hiit.complesense.connection;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.local.GroupOwnerUdpConnectionRunnable;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.GroupOwnerManager;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by rocsea0626 on 30.8.2014.
 */
public class AcceptorUDP extends AbstractSystemThread
{
    public static final String TAG = "AcceptorUDP";

    private final DatagramSocket socket;
    private volatile boolean running = false;

    private final Messenger serviceMessenger;
    private UdpConnectionRunnable udpConnRunnable;

    public AcceptorUDP(Messenger serviceMessenger,
                       ServiceHandler serviceHandler) throws IOException
    {
        super(serviceHandler);
        this.serviceMessenger = serviceMessenger;

        socket = new DatagramSocket(Constants.SERVER_PORT);
        socket.setReuseAddress(true);
        Log.i(TAG, "listening on port: " + socket.getLocalPort());
    }


    @Override
    public void run()
    {
        Log.i(TAG,"run()");
        running = true;

        if(running)
        {
            try
            {
                udpConnRunnable= new UdpConnectionRunnable(serviceHandler, socket);
                new Thread(udpConnRunnable).start();
            }
            catch (IOException e)
            {
                Log.w(TAG,e.toString());
            }
        }
        Log.w(TAG, "Server Terminates!!!");
    }

    @Override
    public void stopThread()
    {
        Log.i(TAG, "stopThread()");
        running = false;
        if(udpConnRunnable != null)
            udpConnRunnable.stopRunnable();
    }

    @Override
    public void pauseThread() {

    }

    public UdpConnectionRunnable getConnectionRunnable() {
        return udpConnRunnable;
    }
}
