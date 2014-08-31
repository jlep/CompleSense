package fi.hiit.complesense.connection;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by rocsea0626 on 31.8.2014.
 */
public class ConnectorCloud extends AbstractSystemThread
{
    public static final String TAG = "ConnectorCloud";

    private CloudConnectionRunnable cloudConnectionRunnable;

    protected ConnectorCloud(ServiceHandler serviceHandler)
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

            cloudConnectionRunnable = new CloudConnectionRunnable(serviceHandler, socket);
            new Thread(cloudConnectionRunnable).start();
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

    public CloudConnectionRunnable getConnectionRunnable()
    {
        return cloudConnectionRunnable;
    }


    @Override
    public void stopThread() {
        Log.i(TAG, "stopThread()");
        if(cloudConnectionRunnable!=null)
        {
            cloudConnectionRunnable.stopRunnable();
        }
    }

    @Override
    public void pauseThread() {

    }
}
