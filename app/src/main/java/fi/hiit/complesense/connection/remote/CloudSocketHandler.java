package fi.hiit.complesense.connection.remote;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.ConnectionRunnable;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 7/22/14.
 */
public class CloudSocketHandler extends AbstractSystemThread
{
    private static final String TAG = "CloudSocketHandler";
    private ConnectionRunnable cloudConnectionRunnable;
    private Thread mThread;


    public CloudSocketHandler(Messenger remoteMessenger,
                              ServiceHandler serviceHandler)
    {
        super(serviceHandler);
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

            cloudConnectionRunnable = new ConnectionRunnable(serviceHandler, socket);
            mThread = new Thread(cloudConnectionRunnable);
            mThread.start();
        }
        catch (IOException e)
        {
            Log.i(TAG, e.toString() );
            try {
                socket.close();
            } catch (IOException e1) {
                Log.i(TAG,e.toString());
            }
            return;
        }

    }

    public ConnectionRunnable getCloudConnection()
    {
        return cloudConnectionRunnable;
    }

    @Override
    public void stopThread() {
        Log.i(TAG, "stopThread()");
        if(cloudConnectionRunnable!=null)
        {
            cloudConnectionRunnable.stopRunnable();
            mThread.interrupt();
        }
    }

    @Override
    public void pauseThread() {

    }
}
