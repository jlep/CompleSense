package fi.hiit.complesense.connection;

import android.util.Log;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketAddress;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by rocsea0626 on 30.8.2014.
 */
public class AcceptorUDP extends AbstractUdpSocketHandler
{
    public static final String TAG = "AcceptorUDP";

    public final DatagramSocket socket;

    public AcceptorUDP(ServiceHandler serviceHandler) throws IOException
    {
        super(serviceHandler);

        socket = new DatagramSocket(Constants.SERVER_PORT);
        socket.setReuseAddress(true);
        Log.i(TAG, "listening on port: " + socket.getLocalPort());
    }


    @Override
    public void run()
    {
        Log.i(TAG,"run()");
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

    public SocketAddress getLocalSocketAddr() {
        return socket.getLocalSocketAddress();
    }
}
