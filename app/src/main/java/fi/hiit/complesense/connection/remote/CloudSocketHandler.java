package fi.hiit.complesense.connection.remote;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AbstractSocketHandler;
import fi.hiit.complesense.core.LocalManager;

/**
 * Created by hxguo on 7/22/14.
 */
public class CloudSocketHandler extends AbstractSocketHandler
{
    private static final String TAG = "CloudSocketHandler";
    private final LocalManager localManager;
    private CloudConnectionRunnable cloudConnectionRunnable;
    private Thread mThread;


    public CloudSocketHandler(Messenger remoteMessenger,
                              LocalManager localManager)
    {
        super(remoteMessenger);
        this.localManager = localManager;
    }

    @Override
    public void run()
    {
        Socket socket = new Socket();
        final InetSocketAddress cloudSocketAddr = new InetSocketAddress(Constants.URL,
                Constants.CLOUD_SERVER_PORT);
        try
        {
            socket.bind(null);
            socket.connect(cloudSocketAddr,5000);
            Log.i(TAG, "Connecting to cloud...");

            cloudConnectionRunnable = new CloudConnectionRunnable(socket,
                    localManager, remoteMessenger);
            mThread = new Thread(cloudConnectionRunnable);
            mThread.start();
        }
        catch (IOException e)
        {
            Log.i(TAG, e.toString() );
            AbstractSocketHandler.closeSocket(socket);
            return;
        }

    }

    @Override
    public void stopHandler()
    {
        Log.i(TAG, "stopHandler()");
        if(cloudConnectionRunnable!=null)
        {
            cloudConnectionRunnable.signalStop();
            mThread.interrupt();
        }
    }

    public CloudConnectionRunnable getCloudConnection()
    {
        return cloudConnectionRunnable;
    }
}
