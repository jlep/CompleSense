package fi.hiit.complesense.connection.remote;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketAddress;

import fi.hiit.complesense.connection.AbstractUdpConnectionRunnable;
import fi.hiit.complesense.connection.AbstractUdpSocketHandler;
import fi.hiit.complesense.core.LocalManager;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 7/22/14.
 */
public class CloudUdpConnectionRunnable extends AbstractUdpConnectionRunnable
{

    private static final String TAG = "CloudUdpConnectionRunnable";
    private final LocalManager localManager;

    public CloudUdpConnectionRunnable(DatagramSocket socket, LocalManager localManager,
                                   Messenger uiMessenger) throws IOException
    {
        super(socket, uiMessenger);
        this.localManager = localManager;
    }


    @Override
    public void run()
    {
        Log.i(TAG, "run()");

        write("Hello from Android...", socket.getRemoteSocketAddress());
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        while (!Thread.currentThread().isInterrupted())
        {
            try
            {
                socket.receive(recPacket);
                Log.i(TAG,"Rec- " +
                        new String(recPacket.getData(),0,recPacket.getLength()) );

                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedException();
            }
            catch (IOException e)
            {
                Log.e(TAG, "disconnected" + e.toString());
                break;
            }
            catch (InterruptedException e)
            {
                Log.e(TAG, e.toString());
                break;
            }
        }
        AbstractUdpSocketHandler.closeSocket(socket);
        Log.w(TAG,"Cloud connection closed!!!");

    }

    @Override
    protected void parseSystemMessage(SystemMessage sm, SocketAddress remoteSocketAddr)
    {

    }
}
