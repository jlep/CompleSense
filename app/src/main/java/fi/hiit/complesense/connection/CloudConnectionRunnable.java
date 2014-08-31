package fi.hiit.complesense.connection;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Timer;

import fi.hiit.complesense.connection.AbstractSocketHandler;
import fi.hiit.complesense.core.LocalManager;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 7/22/14.
 */
public class CloudConnectionRunnable extends ConnectionRunnable
{
    private static final String TAG = "CloudConnectionRunnable";

    public CloudConnectionRunnable(ServiceHandler serviceHandler, Socket socket) throws IOException
    {
        super(serviceHandler, socket);
    }

    @Override
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

        Log.w(TAG,"Terminates!!!");
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

}
