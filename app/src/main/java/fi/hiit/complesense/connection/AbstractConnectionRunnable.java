package fi.hiit.complesense.connection;

import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Timer;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AudioShareManager;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 7/14/14.
 */
public abstract class AbstractConnectionRunnable implements Runnable
{
    private static final String TAG = "AbstractConnectionRunnable";
    protected final Timer timer;
    protected final Socket socket;

    protected ObjectInputStream iStream;
    protected ObjectOutputStream oStream;

    // Messenger points to Remote service
    protected Messenger remoteMessenger;
    private String remoteSocketAddr;
    protected AudioShareManager audioShareManager; // initialized in sub-classes

    public AbstractConnectionRunnable(Socket socket, Messenger messenger) throws IOException
    {
        timer = new Timer();
        this.socket = socket;
        remoteMessenger = messenger;
        audioShareManager = null;

        oStream = new ObjectOutputStream(socket.getOutputStream());
        write("");
        //Log.i(TAG,"Object output streams are initialized");
        iStream = new ObjectInputStream(socket.getInputStream());
        //Log.i(TAG,"Object input streams are initialized");
        Log.i(TAG,"Object streams are initialized");

    }


    public void write(String msg)
    {
        //Log.i(TAG, "write()");
        try
        {
            oStream.write(msg.getBytes());
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

    protected void updateStatusTxt(String str)
    {
        //Log.i(TAG,"updateStatusTxt()");
        Message msg = Message.obtain();
        msg.what = Constants.SERVICE_MSG_STATUS_TXT_UPDATE;
        msg.obj = str;
        try {
            remoteMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
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

    @Override
    public abstract void run();

    protected abstract void parseSystemMessage(SystemMessage sm);

}
