package fi.hiit.complesense.core;

import android.content.Context;
import android.os.Messenger;
import android.util.Log;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import fi.hiit.complesense.connection.ConnectorUDP;
import fi.hiit.complesense.connection.UdpConnectionRunnable;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by rocsea0626 on 31.8.2014.
 */
public class ClientServiceHandler extends ServiceHandler
{
    private static final String TAG = "ClientServiceHandler";
    private final ConnectorUDP connectorUDP;
    //private String foreignSocketAddrStr = null;


    public ClientServiceHandler(Messenger serviceMessenger,
                                String name, Context context,
                                InetAddress ownerAddr, int delay)

    {
        super(serviceMessenger, name, context, false, ownerAddr, delay);
        connectorUDP = (ConnectorUDP)eventHandlingThreads.get(ConnectorUDP.TAG);
    }

    @Override
    protected void handleSystemMessage(SystemMessage sm, SocketAddress remoteSocketAddr)
    {
        super.handleSystemMessage(sm, remoteSocketAddr);
        float[] values;
        Log.i(TAG, sm.toString());

        switch (sm.getCmd())
        {
            case SystemMessage.O:
                // receive Audio Streaming request
                //String rootDir = Environment.getExternalStorageDirectory().getAbsolutePath();
                //Log.i(TAG,rootDir);
                //String audioFilePath = rootDir + "/Music/romance.wav";
                //Log.i(TAG,audioFilePath);
                updateStatusTxt("From " + remoteSocketAddr.toString() + " receive " + sm.toString());

                String socketAddrStr = remoteSocketAddr.toString();
                String host = SystemUtil.getHost(socketAddrStr);
                Log.i(TAG,"remote host is " + host);

                ByteBuffer bb = ByteBuffer.wrap(sm.getPayload());

                int port = bb.getInt();
                Log.i(TAG,"streaming recv port is " + port);
                byte[] strByte = new byte[sm.getPayload().length - Integer.SIZE / 8];
                bb.get(strByte);
                String threadId = new String(strByte);

                //foreignSocketAddrStr = new String(sm.getPayload(),
                //        Integer.SIZE/8, sm.getPayload().length - Integer.SIZE/8);

                SystemUtil.writeLogFile(threadId);

                AudioShareManager.SendMicAudioThread audioStreamThread = AudioShareManager.getSendMicAudioThread(
                        new InetSocketAddress(host, port), this);
                eventHandlingThreads.put(AudioShareManager.SendMicAudioThread.TAG, audioStreamThread);
                audioStreamThread.start();

                // request send audio streaming
                //audioStreamThread = AudioShareManager.sendAudioThread(audioFilePath, remoteSocketAddr.getAddress() );

                break;

            case SystemMessage.L:
                // relay sender is ready
                updateStatusTxt("from " + remoteSocketAddr.toString() + " recv " + sm.toString());

                //audioStreamThread = AudioShareManager.getReceiveAudioThread();
                //audioStreamThread.start();
                //write(SystemMessage.makeRelayListenerReply(), remoteSocketAddr);

                break;
            case SystemMessage.R:
                // Sensor data request
                int sensorType = SystemMessage.parseSensorType(sm);
                Log.i(TAG,"sensorType " + sensorType);
                values = sensorUtil.getLocalSensorValue(sensorType);

                if(null!=values)
                {
                    //if(foreignSocketAddrStr!=null)
                    //    SystemUtil.writeLogFile(startTime, foreignSocketAddrStr);

                    SystemMessage reply = SystemMessage.makeSensorValuesReplyMessage(sensorType, values);
                    if(connectorUDP!=null)
                        connectorUDP.write(reply, remoteSocketAddr);
                }
                break;

            case SystemMessage.V:
                break;

            case SystemMessage.C:
                //write(SystemMessage.makeSensorsListReplyMessage(
                //        clientManager.getLocalSensorList()), remoteSocketAddr);
                break;

            default:
                break;
        }
    }
}
