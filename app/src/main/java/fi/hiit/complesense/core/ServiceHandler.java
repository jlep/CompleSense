package fi.hiit.complesense.core;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fi.hiit.complesense.connection.ConnectionRunnable;

/**
 * Created by hxguo on 20.8.2014.
 */
public class ServiceHandler extends HandlerThread implements Handler.Callback
{
    private static final String TAG = "ServiceHandler";
    protected final Messenger serviceMessenger;
    private Handler handler;

    public ServiceHandler(Messenger serviceMessenger, String name) {
        super(name);
        this.serviceMessenger = serviceMessenger;
    }

    @Override
    protected void onLooperPrepared() {
        handler = new Handler(getLooper(), this);
    }

    @Override
    public boolean handleMessage(Message msg)
    {
        if(msg.what == SystemMessage.ID_SYSTEM_MESSAGE)
        {
            // received a SystemMessage
            Log.i(TAG,"recv: " + ((SystemMessage)msg.obj).toString());
            reply(msg.replyTo);
        }

        return false;
    }


    protected void reply(Messenger messenger)
    {
        Message msg = new Message();
        msg.obj = SystemMessage.makeRelayListenerReply();
        try {
            messenger.send(msg);
        } catch (RemoteException e) {
            Log.i(TAG,e.toString());
        }
    }


    public Handler getHandler(){
        if(handler==null)
            Log.i(TAG,"handler is null");
        return handler;
    }

}
