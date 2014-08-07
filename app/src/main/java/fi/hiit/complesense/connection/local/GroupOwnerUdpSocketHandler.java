package fi.hiit.complesense.connection.local;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AbstractUdpSocketHandler;
import fi.hiit.complesense.core.AudioShareManager;
import fi.hiit.complesense.core.GroupOwnerManager;

/**
 * Created by hxguo on 7/22/14.
 */
public class GroupOwnerUdpSocketHandler extends AbstractUdpSocketHandler
{

    private static final String TAG = "GroupOwnerSocketHandler";

    private final DatagramSocket socket;
    private final GroupOwnerManager groupOwnerManager;
    GroupOwnerUdpConnectionRunnable cr = null;
    private volatile boolean running = false;

    private final Messenger remoteMessenger;

    public GroupOwnerUdpSocketHandler(Messenger remoteMessenger,
                                   GroupOwnerManager groupOwnerMananger) throws IOException
    {
        super(remoteMessenger);
        this.groupOwnerManager = groupOwnerMananger;

        socket = new DatagramSocket(Constants.SERVER_PORT);
        socket.setReuseAddress(true);
        this.remoteMessenger = remoteMessenger;
        Log.i(TAG, "listening on port: " + socket.getLocalPort());

    }


    @Override
    public void run()
    {
        Log.i(TAG,"run()");
        running = true;

        if(running)
        {
            try
            {
                groupOwnerManager.setIsRunning(running);

                cr= new GroupOwnerUdpConnectionRunnable(socket,
                        groupOwnerManager, remoteMessenger);
                new Thread(cr).start();
            }
            catch (IOException e)
            {
                Log.w(TAG,e.toString());
            }
        }
        Log.w(TAG, "Server Terminates!!!");
    }

    @Override
    public void stopHandler()
    {
        Log.i(TAG, "stopHandler()");
        running = false;
        if(cr != null)
            cr.signalStop();

        AbstractUdpSocketHandler.closeSocket(socket);
    }

}
