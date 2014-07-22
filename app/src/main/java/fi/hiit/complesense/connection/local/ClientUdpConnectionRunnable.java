package fi.hiit.complesense.connection.local;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import fi.hiit.complesense.connection.AbstractUdpConnectionRunnable;
import fi.hiit.complesense.connection.AbstractUdpSocketHandler;
import fi.hiit.complesense.core.ClientManager;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 7/22/14.
 */
public class ClientUdpConnectionRunnable extends AbstractUdpConnectionRunnable
{
    private static final String TAG = "ClientUdpConnectionRunnable";
    private final ClientManager clientManager;
    private final InetSocketAddress remoteSocketAddr;

    public ClientUdpConnectionRunnable(DatagramSocket socket,
                                       ClientManager clientManager,
                                       InetSocketAddress remoteSocketAddr,
                                       Messenger messenger)
            throws IOException
    {
        super(socket, messenger);
        this.remoteSocketAddr = remoteSocketAddr;
        this.clientManager = clientManager;
    }

    @Override
    public void run()
    {
        Log.i(TAG, "run()");

        clientManager.setIsRunning(true);

        write(SystemMessage.makeSensorsListReplyMessage(
                clientManager.getLocalSensorList()), remoteSocketAddr);

        while (!Thread.currentThread().isInterrupted())
        {
            try
            {
                socket.receive(recPacket);
                parseSystemMessage(SystemMessage.getFromBytes(
                        recPacket.getData()));
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

        clientManager.setIsRunning(false);
        AbstractUdpSocketHandler.closeSocket(socket);
        Log.w(TAG,"Terminates!!!");
    }

    @Override
    protected void parseSystemMessage(SystemMessage sm)
    {
        float[] values;
        Log.i(TAG,sm.toString());
        switch (sm.getCmd())
        {
            case SystemMessage.R:
                // Sensor data request
                int sensorType = SystemMessage.parseSensorType(sm);
                values = clientManager.getSensorValues(sensorType);
                if(null!=values)
                {
                    SystemMessage reply = SystemMessage.makeSensorValuesReplyMessage(sensorType, values);
                    write(reply, remoteSocketAddr);
                }

                break;

            case SystemMessage.V:
                break;

            case SystemMessage.C:
                write(SystemMessage.makeSensorsListReplyMessage(
                        clientManager.getLocalSensorList()), remoteSocketAddr);
                break;
            default:
                break;
        }

    }
}
