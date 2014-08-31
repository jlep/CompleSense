package fi.hiit.complesense.connection;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.local.ClientUdpConnectionRunnable;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by rocsea0626 on 30.8.2014.
 */
public class ConnectorUDP extends AbstractSystemThread
{
    public static final String TAG = "ConnectorUDP";

    private final InetAddress ownerAddr;
    private final Messenger serviceMessenger;
    private final int delay;
    private ClientConnectionRunnable clientConnectionRunnable;

    public ConnectorUDP(Messenger serviceMessenger,
                     ServiceHandler serviceHandler,
                     InetAddress ownerAddr, int delay) throws IOException
    {
        super(serviceHandler);
        this.ownerAddr = ownerAddr;

        //this.ownerAddr = ownerAddr;
        this.serviceMessenger = serviceMessenger;
        this.delay = delay;

    }

    @Override
    public void run()
    {
        if(delay>0)
            Log.i(TAG, "Starting client in " + delay + "ms");
        DatagramSocket socket = null;
        try
        {
            socket = new DatagramSocket();
            socket.connect(new InetSocketAddress(ownerAddr, Constants.SERVER_PORT) );
            Log.i(TAG, ownerAddr.toString());
            Log.i(TAG, "Launching the I/O handler at: " + socket.getLocalSocketAddress().toString());

            clientConnectionRunnable = new ClientConnectionRunnable(serviceHandler, socket);
            new Thread(clientConnectionRunnable).start();
        }
        catch (IOException e)
        {
            Log.i(TAG, e.toString() );
            AbstractUdpSocketHandler.closeSocket(socket);
            return;
        }
    }

    public UdpConnectionRunnable getConnectionRunnable()
    {
        return clientConnectionRunnable;
    }


    @Override
    public void stopThread() {

    }

    @Override
    public void pauseThread() {

    }
}
