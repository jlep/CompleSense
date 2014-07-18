package fi.hiit.complesense.core;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;

/**
 * Created by hxguo on 7/14/14.
 * Abstract Class for handling socket connections
 */
public abstract class AbstractSocketHandler extends Thread
{
    private static final String TAG = AbstractSocketHandler.class.getSimpleName();
    protected final Timer timer;
    protected Messenger remoteMessenger;

    protected ObjectInputStream iStream;
    protected ObjectOutputStream oStream;

    protected AbstractSocketHandler(Messenger messenger)
    {
        remoteMessenger = messenger;
        timer = new Timer();
    }


    @Override
    public abstract void run();
    public abstract void stopHandler();

    public static void closeSocket(ServerSocket socket)
    {
        Log.i(TAG, "closeSocket(ServerSocket)");
        try
        {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e)
        {
            Log.e(TAG, e.toString());
        }
    }

    public static void closeSocket(Socket socket)
    {
        Log.i(TAG,"closeSocket(Socket)");
        try
        {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e)
        {
            Log.e(TAG, e.toString());
        }
    }
}
