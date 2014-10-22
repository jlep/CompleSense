package fi.hiit.complesense.core;

import android.content.Context;
import android.os.CountDownTimer;
import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import fi.hiit.complesense.Constants;
import fi.hiit.complesense.audio.LocalRecThread;
import fi.hiit.complesense.audio.RelayThread;
import fi.hiit.complesense.audio.SendAudioThread;
import fi.hiit.complesense.connection.AcceptorUDP;
import fi.hiit.complesense.connection.UdpConnectionRunnable;

/**
 * Created by hxguo on 21.8.2014.
 */
public class GroupOwnerServiceHandler extends ServiceHandler
    implements UdpConnectionRunnable.UdpConnectionListner {
    private static final String TAG = "GroupOwnerServiceHandler";
    private final AcceptorUDP acceptorUDP;
    //private Timer timer;

    private int clientCounter = 0;

    private final String cloudSocketAddrStr = "http://" + Constants.URL +
            ":" + Constants.CLOUD_SERVER_PORT + "/";


    public GroupOwnerServiceHandler(Messenger serviceMessenger, String name,
                                    Context context)
    {
        super(serviceMessenger, name,context, true, null, 0);
//        timer = new Timer();
        acceptorUDP = (AcceptorUDP)eventHandlingThreads.get(AcceptorUDP.TAG);

        //LocalRecThread localRecThread = new LocalRecThread(this);
        //eventHandlingThreads.put(LocalRecThread.TAG, localRecThread);

    }

    @Override
    public void onReceiveLastRttReply(long startTimeMillis, SocketAddress fromAddr)
    {
        super.onReceiveLastRttReply(startTimeMillis, fromAddr);

        ++clientCounter;
        if(clientCounter >= 1)
        {
            Log.e(TAG, "enough clients have joined");

            for(String key: peerList.keySet() )
            {
                acceptorUDP.write(SystemMessage.makeStereoImageReq(),
                        peerList.get(key).socketAddress);
            }
        }
    }


    @Override
    protected void handleSystemMessage(SystemMessage sm, SocketAddress fromAddr)
    {

        super.handleSystemMessage(sm, fromAddr);
        float[] values;
        int type;
        Log.i(TAG,sm.toString());
        switch (sm.getCmd())
        {
            case SystemMessage.Y:

                break;
            case SystemMessage.R:
                break;

            case SystemMessage.J:
                updateStatusTxt(fromAddr.toString() + "->" + sm.toString());                //create a relay listener
                /*
                SocketAddress senderAddr = groupOwnerManager.selectAudioStreamSender();
                Log.i(TAG,"Relay sender Addr: " + senderAddr.toString());

                if(senderAddr!=null)
                {
                    audioStreamThread = AudioShareManager.getRelayAudioThread(senderAddr, remoteSocketAddr,
                            GroupOwnerUdpConnectionRunnable.this);
                    audioStreamThread.start();
                }
                */
                break;

            case SystemMessage.V:
                renewPeerList(fromAddr.toString());
                type = SystemMessage.parseSensorType(sm);
                if(type <= 0)
                {
                    Log.e(TAG, "cannot parse sensor type");
                    break;
                }
                values = SystemMessage.parseSensorValues(sm);
                sensorUtil.setSensorValue(values, type, fromAddr.toString());
                //send2Cloud(fromAddr.toString(), values);

                //updateStatusTxt(fromAddr + "->: " + sm.toString());
                //SystemUtil.writeLogFile(startTime, fromAddr.toString());
                break;

            case SystemMessage.N:
                List<Integer> typeList = SystemMessage.parseSensorTypeList(sm);
                sensorUtil.initSensorValues(typeList, fromAddr.toString());
                addNewConnection(fromAddr);

                updateStatusTxt("sensor list from " + fromAddr + ": " + typeList.toString());
                //Log.e(TAG, "from "+fromAddr.toString()+" receive typeList: " + typeList.toString());

                // make RTT measurement request
                acceptorUDP.write(SystemMessage.makeRttQuery(System.currentTimeMillis(),
                        Constants.RTT_ROUNDS, acceptorUDP.getLocalSocketAddr()),fromAddr);

                /*
                AudioShareManager.RelayAudioHttpThread httpThread =
                        AudioShareManager.getHttpRelayAudioThread(fromAddr, this, acceptorUDP.getConnectionRunnable());
                eventHandlingThreads.put(AudioShareManager.StreamRelayAudioThread.TAG,
                        httpThread);
                */

                /*
                ConnectorCloud connectorCloud = (ConnectorCloud)eventHandlingThreads.get(ConnectorCloud.TAG);
                if(connectorCloud!=null)
                {

                    AudioShareManager.RelayAudioHttpThread relayAudioHttpThread =
                            AudioShareManager.getHttpRelayAudioThread(fromAddr, connectorCloud,this);

                    int recvPort = relayAudioHttpThread.getLocalPort();

                    eventHandlingThreads.put(AudioShareManager.RelayAudioHttpThread.TAG,
                            relayAudioHttpThread);
                    relayAudioHttpThread.start();

                    acceptorUDP.getConnectionRunnable().write(
                            SystemMessage.makeAudioStreamingRequest(recvPort), fromAddr);
                }
                else
                {
                    Log.e(TAG,"connectorCloud is null");
                }
*/
                /*
                AudioShareManager.StreamRelayAudioThread streamRelayThread =
                        AudioShareManager.getStreamRelayAudioThread(fromAddr, this, acceptorUDP.getConnectionRunnable());
                if(streamRelayThread!=null)
                {
                    eventHandlingThreads.put(AudioShareManager.StreamRelayAudioThread.TAG +"-"+fromAddr,
                            streamRelayThread);
                    streamRelayThread.start();

                }
                */
                break;

            default:
                break;
        }
    }

    @Override
    public void stopServiceHandler()
    {
//        timer.cancel();
        super.stopServiceHandler();
    }



}
