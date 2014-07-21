package fi.hiit.complesense.connection.remote;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;

import fi.hiit.complesense.connection.AbstractConnectionRunnable;
import fi.hiit.complesense.connection.AbstractSocketHandler;
import fi.hiit.complesense.core.LocalManager;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 7/21/14.
 */
public class CloudConnectionRunnable extends AbstractConnectionRunnable
{
    private static final String TAG = "CloudConnectionRunnable";
    private final LocalManager localManager;

    public CloudConnectionRunnable(Socket socket, LocalManager localManager,
                                    Messenger remoteHandler) throws IOException
    {
        super(socket, remoteHandler);
        this.localManager = localManager;
    }

    @Override
    public void run()
    {
        Log.i(TAG,"run()");

        //write("Hello from Android...");

        byte[] buffer = new byte[1024];
        int bytes;

        while (!Thread.currentThread().isInterrupted())
        {
            try
            {
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
        Log.w(TAG,"Cloud connection closed!!!");

    }

    @Override
    protected void parseSystemMessage(SystemMessage sm) {

    }
}
