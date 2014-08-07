package fi.hiit.complesense.core;

import android.content.Context;
import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;

import fi.hiit.complesense.connection.local.ClientUdpConnectionRunnable;
import fi.hiit.complesense.connection.local.ClientUdpSocketHandler;
import fi.hiit.complesense.connection.remote.CloudSocketHandler;

/**
 * Created by hxguo on 7/16/14.
 */
public class ClientManager extends LocalManager
{
    private static final String TAG = "ClientManager";

    public ClientManager(Messenger messenger, Context context, boolean connect2Cloud)
    {
        super(messenger, false, connect2Cloud, context);
    }

    @Override
    public void start(InetAddress ownerAddr, int delay) throws IOException
    {
        if(!isServer && ownerAddr!=null)
        {
            abstractSocketHandler = new ClientUdpSocketHandler(remoteMessenger, this, ownerAddr, delay);
            abstractSocketHandler.start();
        }

        if(connect2Cloud)
        {
            cloudSocketHandler = new CloudSocketHandler(remoteMessenger, this);
            cloudSocketHandler.start();
        }
    }

    @Override
    public void start() throws IOException{
        Log.e(TAG,"This is client cannot be run as Server!!");
    }


}
