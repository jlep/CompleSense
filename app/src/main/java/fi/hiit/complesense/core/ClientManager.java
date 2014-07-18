package fi.hiit.complesense.core;

import android.content.Context;
import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Created by hxguo on 7/16/14.
 */
public class ClientManager extends LocalManager
        implements LocalManager.startInterface
{
    private static final String TAG = "ClientManager";

    public ClientManager(Messenger messenger, Context context)
    {
        super(messenger, false, context);
    }

    @Override
    public void start(InetAddress ownerAddr, int delay) throws IOException
    {
        if(!isServer && ownerAddr!=null)
        {
            abstractSocketHandler = new ClientSocketHandler(remoteMessenger, this, ownerAddr, delay);
            abstractSocketHandler.start();
        }
    }

    @Override
    public void start() throws IOException{
        Log.e(TAG,"This is client cannot be run as Server!!");
    }
}
