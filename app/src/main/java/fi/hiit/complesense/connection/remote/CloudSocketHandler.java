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
 * Created by hxguo on 7/21/14.
 */
public class CloudSocketHandler extends AbstractSocketHandler
{
    private static final String TAG = "CloudSocketHandler";
    private final LocalManager localManager;
    private boolean running;

    private CloudConnectionRunnable cloudConnectionRunnable;
    private Thread mThread;


    public CloudSocketHandler(Messenger remoteMessenger, LocalManager localManager)
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
        Socket socket = new Socket();
        try
        {
            socket.bind(null);
            socket.connect(new InetSocketAddress(Constants.URL,
                    Constants.CLOUD_SERVER_PORT), 5000);
            Log.i(TAG, "Connecting to cloud...");

            cloudConnectionRunnable = new CloudConnectionRunnable(socket, localManager,
                    remoteMessenger);
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
        cloudConnectionRunnable.signalStop();
        mThread.interrupt();
    }

}
