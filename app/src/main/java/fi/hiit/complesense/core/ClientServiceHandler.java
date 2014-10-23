package fi.hiit.complesense.core;

import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.Messenger;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.audio.SendAudioThread;
import fi.hiit.complesense.connection.ConnectorUDP;
import fi.hiit.complesense.connection.UdpConnectionRunnable;
import fi.hiit.complesense.img.ImageWebSocketClient;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by rocsea0626 on 31.8.2014.
 */
public class ClientServiceHandler extends ServiceHandler
{
    private static final String TAG = "ClientServiceHandler";
    private final ConnectorUDP connectorUDP;
    private final InetAddress ownerAddr;
    private int serverWebSocketPort;


    public ClientServiceHandler(Messenger serviceMessenger,
                                String name, Context context,
                                InetAddress ownerAddr, int delay)

    {
        super(serviceMessenger, name, context, false, ownerAddr, delay);
        connectorUDP = (ConnectorUDP)eventHandlingThreads.get(ConnectorUDP.TAG);
        this.ownerAddr = ownerAddr;
    }

    @Override
    protected void handleSystemMessage(SystemMessage sm, SocketAddress remoteSocketAddr)
    {
        super.handleSystemMessage(sm, remoteSocketAddr);
        float[] values;
        ByteBuffer bb;
        Log.i(TAG, sm.toString());

        switch (sm.getCmd())
        {
            case SystemMessage.O:
                // receive Audio Streaming request
                updateStatusTxt("From " + remoteSocketAddr.toString() + " receive " + sm.toString());

                String socketAddrStr = remoteSocketAddr.toString();
                String host = SystemUtil.getHost(socketAddrStr);
                Log.i(TAG,"remote host is " + host);

                bb = ByteBuffer.wrap(sm.getPayload());

                serverWebSocketPort = bb.getInt();
                Log.i(TAG,"streaming recv port is " + serverWebSocketPort);
                long threadId = bb.getLong();
                Log.i(TAG, "threadId: " + threadId);

                int toStart = bb.getInt();
                Log.i(TAG, "toStart: " + toStart);

                if(toStart == 1)
                {
                    SystemUtil.writeAlivenessFile(threadId);
                    SendAudioThread sendAudioThread = SendAudioThread.getInstancce(
                            new InetSocketAddress(host, serverWebSocketPort), this, threadId,true);

                    eventHandlingThreads.put(SendAudioThread.TAG, sendAudioThread);
                }
                else
                {
                    SendAudioThread sendAudioThread = (SendAudioThread) eventHandlingThreads.remove(SendAudioThread.TAG);
                    if(sendAudioThread != null)
                        sendAudioThread.stopThread();
                }


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

            case SystemMessage.STEREO_IMG:
                bb = ByteBuffer.wrap(sm.getPayload());

                serverWebSocketPort = bb.getInt();
                Log.i(TAG,"webSocketPort recv port is " + serverWebSocketPort);
                startImageCapture(remoteSocketAddr);
                break;

            default:
                break;
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        super.handleMessage(msg);
        if(msg.what == Constants.THREAD_MSG_SEND_IMG)
        {
            File imgFile = (File)msg.obj;
            sendImg2Server(imgFile);
        }

        return false;
    }

    public void sendImg2Server(File imgFile)
    {
        Log.i(TAG, "sendImg2Server(imgFile: "+ imgFile +") @ thread id: " + Thread.currentThread().getId() );
        SocketAddress serverSocketAddr = new InetSocketAddress(ownerAddr.getHostAddress(), serverWebSocketPort);
        ImageWebSocketClient socketClient = new ImageWebSocketClient(imgFile, serverSocketAddr, this);
        socketClient.connect();
    }
}
