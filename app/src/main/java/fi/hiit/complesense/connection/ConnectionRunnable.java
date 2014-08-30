package fi.hiit.complesense.connection;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Date;

import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 20.8.2014.
 */
public class ConnectionRunnable implements Runnable, Handler.Callback
{
    private static final String TAG = "ConnectionRunnable";
    public static final String ID = "THREAD_ID";
    public final Messenger mMessenger;
    protected Socket socket;

    protected ObjectInputStream iStream;
    protected ObjectOutputStream oStream;
    public final long THREAD_ID;

    protected ServiceHandler serviceHandler;

    public ConnectionRunnable(ServiceHandler serviceHandler,
                              Socket socket) throws IOException
    {
        THREAD_ID = Thread.currentThread().getId();

        this.serviceHandler = serviceHandler;
        this.socket = socket;

        oStream = new ObjectOutputStream(socket.getOutputStream());
        write("");
        //Log.i(TAG,"Object output streams are initialized");
        iStream = new ObjectInputStream(socket.getInputStream());
        //Log.i(TAG,"Object input streams are initialized");
        Log.i(TAG,"Object streams are initialized");

        HandlerThread handlerThread = new HandlerThread("MyHandlerThread");
        handlerThread.start();

        // Get the looper from the handlerThread
        // Note: this may return null
        Looper looper = handlerThread.getLooper();
        // Create a new handler - passing in the looper to use and this class as
        // the message handler
        Handler handler = new Handler(looper, this);
        mMessenger = new Messenger(handler);

    }


    @Override
    public void run() {
        Log.i(TAG, "run()");
        while (!Thread.currentThread().isInterrupted())
        {
            try
            {
                Object o = iStream.readObject();
                if(o instanceof Date)
                    Log.i(TAG,"Date: " + ((Date)o).toString());

                if(o instanceof SystemMessage)
                {
                    //Log.i(TAG,"REC: " + o.toString() );
                    send2ServerHandler((SystemMessage) o);
                }
                if(o instanceof String)
                    Log.i(TAG, "recv String: " + o);

                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedException();

            } catch (IOException e)
            {
                Log.e(TAG, "disconnected" + e.toString());
                break;
            } catch (InterruptedException e)
            {
                Log.e(TAG, e.toString());
                break;
            } catch (ClassNotFoundException e) {
                Log.i(TAG,e.toString());
            }
        }
        Log.w(TAG,"Connection with "+ socket.getRemoteSocketAddress() +" Terminates!!!");
    }

    private void send2ServerHandler(SystemMessage sm)
    {
        Message msg = serviceHandler.getHandler().obtainMessage();
        msg.what = SystemMessage.ID_SYSTEM_MESSAGE;
        msg.obj = sm;
        msg.replyTo = mMessenger;
        msg.sendToTarget();
    }


    public void write(String msg)
    {
        //Log.i(TAG, "write()");
        try
        {
            //oStream.write(msg.getBytes());
            oStream.writeObject(msg);
            oStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        }
    }


    public void write(SystemMessage sm)
    {
        //Log.i(TAG, "write()");
        try {
            oStream.writeObject(sm);
            oStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        }
    }

    public void stopRunnable()
    {
        try {
            socket.close();
        } catch (IOException e) {
            Log.i(TAG,e.toString());
        }
    }

    public String getRemoteSocketAddr() {
        return socket.getRemoteSocketAddress().toString();
    }

    @Override
    public boolean handleMessage(Message message) {
        Log.i(TAG,"recv from service handler");
        return false;
    }
}
