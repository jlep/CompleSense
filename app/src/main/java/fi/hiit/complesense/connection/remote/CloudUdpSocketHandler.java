package fi.hiit.complesense.connection.remote;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AbstractUdpSocketHandler;
import fi.hiit.complesense.core.LocalManager;

/**
 * Created by hxguo on 7/21/14.
 */
public class CloudUdpSocketHandler extends AbstractUdpSocketHandler
{
    private static final String TAG = "CloudUdpSocketHandler";
    private final LocalManager localManager;
    private boolean running;

    private CloudUdpConnectionRunnable cloudConnectionRunnable;
    private Thread mThread;


    public CloudUdpSocketHandler(Messenger remoteMessenger, LocalManager localManager)
            throws IOException
    {
        super(remoteMessenger);
        this.localManager = localManager;

        running = false;
    }

    @Override
    public void run()
    {
        Log.i(TAG,"run()");

        DatagramSocket socket = null;
        try
        {
            socket = new DatagramSocket();
            socket.connect(InetAddress.getByName(Constants.URL),
                    Constants.CLOUD_SERVER_PORT);
            Log.i(TAG, "Connecting to cloud..." + socket.getRemoteSocketAddress().toString());

            cloudConnectionRunnable = new CloudUdpConnectionRunnable(socket, localManager,
                    remoteMessenger);
            mThread = new Thread(cloudConnectionRunnable);
            mThread.start();
        }
        catch (IOException e)
        {
            Log.i(TAG, e.toString() );
            AbstractUdpSocketHandler.closeSocket(socket);
            return;
        }
    }

    @Override
    public void stopHandler()
    {
        Log.i(TAG, "stopHandler()");
        cloudConnectionRunnable.signalStop();
        mThread.interrupt();
    }

}
