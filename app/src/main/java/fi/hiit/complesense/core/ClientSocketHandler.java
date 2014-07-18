package fi.hiit.complesense.core;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import fi.hiit.complesense.Constants;

/**
 * Created by hxguo on 7/14/14.
 */
public class ClientSocketHandler extends AbstractSocketHandler
{

    private static final String TAG = "ClientSocketHandler";
    private final InetAddress ownerAddr;
    private final int delay;
    private final ClientManager clientManager;
    private ClientConnectionRunnable clientConnectionRunnable;
    private Thread mThread;


    public ClientSocketHandler(Messenger remoteMessenger,
                               ClientManager clientManager, InetAddress ownerAddr, int delay)
    {
        super(remoteMessenger);
        this.clientManager = clientManager;
        this.ownerAddr = ownerAddr;
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

            clientConnectionRunnable = new ClientConnectionRunnable(socket,
                    clientManager, remoteMessenger);
            mThread = new Thread(clientConnectionRunnable);
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
        clientConnectionRunnable.signalStop();
        mThread.interrupt();

    }
}
