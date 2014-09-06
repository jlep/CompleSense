package fi.hiit.complesense.connection;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;
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

    private InetSocketAddress cloudSocketAddr = null;
    private ConnectionRunnable cloudConnectionRunnable;
    //private HttpConnectionRunnable cloudConnectionRunnable;
    //private HttpStreamingConnection cloudConnectionRunnable;

    public ConnectorCloud(ServiceHandler serviceHandler)
    {
        super(serviceHandler);

    }

    @Override
    public void run()
    {
        cloudSocketAddr = new InetSocketAddress(Constants.URL,
                Constants.CLOUD_SERVER_PORT);

        /*
        Socket socket = null;
        try
        {
            socket = new Socket();
            socket.connect(cloudSocketAddr);
            Log.i(TAG, "Connecting to cloud...");

            //cloudConnectionRunnable = new HttpStreamingConnection(serviceHandler);
            //cloudConnectionRunnable = new ConnectionRunnable(serviceHandler, socket);
            //new Thread(cloudConnectionRunnable).start();
        }
        catch (IOException e)
        {
            Log.e(TAG, e.toString());
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        */

    }

    public ConnectionRunnable getConnectionRunnable()
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

    public InetSocketAddress getCloudSocketAddr() {
        return cloudSocketAddr;
    }
}
