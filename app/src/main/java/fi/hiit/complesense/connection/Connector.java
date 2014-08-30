package fi.hiit.complesense.connection;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 20.8.2014.
 */
public class Connector extends AbstractSystemThread
{
    public static final String TAG = "Connector";

    private final InetAddress ownerAddr;
    private final Messenger serviceMessenger;
    private final int delay;
    private ConnectionRunnable clientConnectionRunnable;

    public Connector(Messenger serviceMessenger,
                     ServiceHandler serviceHandler,
                     InetAddress ownerAddr, int delay) throws IOException
    {
        super(serviceHandler);
        this.ownerAddr = ownerAddr;

        //this.ownerAddr = ownerAddr;
        this.serviceMessenger = serviceMessenger;
        this.delay = delay;

    }

    @Override
    public void run()
    {
        if(delay>0)
            Log.i(TAG,"Starting client in "+ delay +"ms");

        Socket socket = new Socket();
        try
        {
            socket.bind(null);
            socket.connect(new InetSocketAddress(ownerAddr.getHostAddress(),
                    Constants.SERVER_PORT), 5000);
            Log.i(TAG, "Launching the I/O handler");

            clientConnectionRunnable = new ConnectionRunnable(serviceHandler, socket);
            new Thread(clientConnectionRunnable).start();
        }
        catch (IOException e)
        {
            Log.i(TAG, e.toString() );
            try {
                socket.close();
            } catch (IOException e1) {
                Log.i(TAG,e1.toString());
            }
            return;
        }

    }

    @Override
    public void stopThread() {
        clientConnectionRunnable.stopRunnable();
    }

    @Override
    public void pauseThread() {

    }
}
