package fi.hiit.complesense.connection;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by rocsea0626 on 30.8.2014.
 */
public class ConnectorUDP extends AbstractUdpSocketHandler
{
    public static final String TAG = "ConnectorUDP";

    private final InetAddress ownerAddr;

    public ConnectorUDP(ServiceHandler serviceHandler,
                     InetAddress ownerAddr) throws IOException
    {
        super(serviceHandler);
        this.ownerAddr = ownerAddr;
    }

    @Override
    public void run()
    {
        DatagramSocket socket = null;
        try
        {
            socket = new DatagramSocket();
            socket.connect(new InetSocketAddress(ownerAddr, Constants.SERVER_PORT) );
            Log.i(TAG, ownerAddr.toString());
            Log.i(TAG, "Launching the I/O handler at: " + socket.getLocalSocketAddress().toString());

            udpConnRunnable = new ClientConnectionRunnable(serviceHandler, socket);
            new Thread(udpConnRunnable).start();
        }
        catch (IOException e)
        {
            Log.i(TAG, e.toString() );
            socket.close();
            return;
        }
    }

}
