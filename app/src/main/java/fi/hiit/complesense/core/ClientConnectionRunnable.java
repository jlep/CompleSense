package fi.hiit.complesense.core;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;
import java.util.Date;

import fi.hiit.complesense.SystemMessage;

/**
 * Created by hxguo on 7/14/14.
 */
public class ClientConnectionRunnable extends AbstractConnectionRunnable
{
    private static final String TAG = "ClientConnectionRunnable";
    private final ClientManager clientManager;

    public ClientConnectionRunnable(Socket socket, ClientManager clientManager,
                                    Messenger remoteHandler) throws IOException
    {
        super(socket, remoteHandler);
        this.clientManager = clientManager;
    }

    @Override
    public void run()
    {
        Log.i(TAG, "run()");

        clientManager.setIsRunning(true);

        while (!Thread.currentThread().isInterrupted())
        {
            try
            {
                Object o = iStream.readObject();
                if(o instanceof Date)
                    Log.i(TAG,"Date: " + ((Date)o).toString());

                if(o instanceof SystemMessage)
                {
                    Log.i(TAG,"REC: " + o.toString() );
                    parseSystemMessage((SystemMessage) o);
                }

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
            catch (ClassNotFoundException e) {
                Log.i(TAG,e.toString());
            }
        }

        clientManager.setIsRunning(false);
        AbstractSocketHandler.closeSocket(socket);
        Log.w(TAG,"Terminates!!!");
    }

    @Override
    protected void parseSystemMessage(SystemMessage sm)
    {
        float[] values;
        switch (sm.getCmd())
        {
            case SystemMessage.R:
                // Sensor data request
                int sensorType = SystemMessage.parseSensorType(sm);
                values = clientManager.getSensorValues(sensorType);
                if(null!=values)
                {
                    SystemMessage reply = SystemMessage.makeSensorValuesReplyMessage(sensorType, values);
                    write(reply);
                }

                break;

            case SystemMessage.V:
                break;

            case SystemMessage.C:
                write(SystemMessage.makeSensorsListReplyMessage(
                        clientManager.getLocalSensorList() ));
                break;
            default:
                break;
        }

    }
}
