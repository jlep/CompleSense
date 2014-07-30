package fi.hiit.complesense.connection.remote;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Timer;

import fi.hiit.complesense.connection.AbstractSocketHandler;
import fi.hiit.complesense.core.LocalManager;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 7/22/14.
 */
public class CloudConnectionRunnable implements Runnable
{
    private static final String TAG = "CloudConnectionRunnable";
    private final LocalManager localManager;
    private final OutputStream oStream;
    private final InputStream iStream;
    private final Messenger remoteMessenger;
    private final Socket socket;
    private final Timer timer;

    public CloudConnectionRunnable(Socket socket, LocalManager localManager,
                                    Messenger  messenger) throws IOException
    {
        this.localManager = localManager;
        timer = new Timer();
        this.socket = socket;
        remoteMessenger = messenger;

        oStream = socket.getOutputStream();
        //Log.i(TAG,"Object output streams are initialized");
        iStream = socket.getInputStream();
        //Log.i(TAG,"Object input streams are initialized");
        Log.i(TAG,"Input/output streams are initialized");
    }


    public void run()
    {
        Log.i(TAG, "run()");

        byte[] buffer = new byte[1024];
        int bytes;
        write("Hello from android...");

        while (!Thread.currentThread().isInterrupted())
        {
            try
            {
                // Read from the InputStream
                bytes = iStream.read(buffer);
                if (bytes == -1) {
                    break;
                }

                Log.i(TAG, "Rec:" + String.valueOf(buffer));

                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedException();

            }
            catch (IOException e)
            {
                Log.e(TAG, "disconnected" + e.toString());
                break;
            }
            catch (InterruptedException e)
            {
                Log.e(TAG, e.toString());
                break;
            }
        }

        AbstractSocketHandler.closeSocket(socket);
        Log.w(TAG,"Terminates!!!");
    }

    protected void parseSystemMessage(SystemMessage sm)
    {
        float[] values;
        switch (sm.getCmd())
        {
            default:
                break;
        }

    }

    public void write(String msg)
    {
        Log.i(TAG, "write("+msg+")");
        try
        {
            oStream.write(msg.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        }
    }

    public void signalStop()
    {
        Log.i(TAG,"signalStop()");
        try {
            timer.cancel();
            iStream.close();
            oStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
