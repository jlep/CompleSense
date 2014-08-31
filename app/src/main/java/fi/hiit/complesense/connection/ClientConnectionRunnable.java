package fi.hiit.complesense.connection;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketAddress;

import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by rocsea0626 on 30.8.2014.
 */
public class ClientConnectionRunnable extends UdpConnectionRunnable
{
    private static final String TAG = "ClientConnectionRunnable";
    private final SocketAddress groupOwnerSocketAddr;

    public ClientConnectionRunnable(ServiceHandler serviceHandler, DatagramSocket socket)
            throws IOException
    {
        super(serviceHandler, socket);
        groupOwnerSocketAddr = socket.getRemoteSocketAddress();
    }

    @Override
    public void run()
    {
        write(SystemMessage.makeSensorsListReplyMessage(
                serviceHandler.sensorUtil.getLocalSensorTypeList()), groupOwnerSocketAddr);

        super.run();
    }


}
