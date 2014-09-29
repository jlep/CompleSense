package fi.hiit.complesense.core;

import android.content.Context;
import android.os.CountDownTimer;
import android.os.Messenger;
import android.util.Log;

import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import fi.hiit.complesense.Constants;
import fi.hiit.complesense.audio.RelayThread;
import fi.hiit.complesense.connection.AcceptorUDP;
import fi.hiit.complesense.connection.UdpConnectionRunnable;

/**
 * Created by hxguo on 21.8.2014.
 */
public class GroupOwnerServiceHandler extends ServiceHandler
    implements UdpConnectionRunnable.UdpConnectionListner {
    private static final String TAG = "GroupOwnerServiceHandler";
    private final AcceptorUDP acceptorUDP;
    private Timer timer;

    private int clientCounter = 0;

    private final String cloudSocketAddrStr = "http://" + Constants.URL +
            ":" + Constants.CLOUD_SERVER_PORT + "/";


    public GroupOwnerServiceHandler(Messenger serviceMessenger, String name,
                                    Context context)
    {
        super(serviceMessenger, name,context, true, null, 0);
        timer = new Timer();
        acceptorUDP = (AcceptorUDP)eventHandlingThreads.get(AcceptorUDP.TAG);
    }

    @Override
    public void onReceiveLastRttReply(long startTimeMillis, SocketAddress fromAddr)
    {
        super.onReceiveLastRttReply(startTimeMillis, fromAddr);

        int sType = sensorUtil.randomlySelectSensor(fromAddr.toString() );
        Log.i(TAG,"sType: " + sType);

        if(acceptorUDP.getConnectionRunnable()!=null)
        {
            ScheduledUdpQueryTask sTask = new ScheduledUdpQueryTask(
                    acceptorUDP.getConnectionRunnable(),this, fromAddr,sType);
            timer.schedule(sTask, 0, 3000);
        }

        //AudioShareManager.WebSocketConnection webSocketConnection=
        //        AudioShareManager.getWebSocketAudioRelayThread(fromAddr, this, acceptorUDP.getConnectionRunnable());

        try {
            RelayThread relayThread = new RelayThread(this, fromAddr,
                    acceptorUDP.getConnectionRunnable(), clientCounter);

            ++clientCounter;
            if(relayThread!=null)
            {
                eventHandlingThreads.put(RelayThread.TAG +"-"+fromAddr.toString(), relayThread);
            }
        } catch (SocketException e) {
            Log.i(TAG, e.toString() );
        }

        if(clientCounter >= 1)
        {
            Log.e(TAG, "enough clients have joined");
            CountDownTimer countDownTimer = new CountDownTimer(20000,1000) {
                @Override
                public void onTick(long l) {

                }

                @Override
                public void onFinish() {
                    // send stop recording message
                    Iterator<Map.Entry<String, AbstractSystemThread> > iter = eventHandlingThreads.entrySet().iterator();
                    while(iter.hasNext())
                    {
                        Map.Entry<String, AbstractSystemThread> entry = iter.next();
                        if(entry.getKey().contains(RelayThread.TAG))
                        {
                            Log.i(TAG, "stop rec on: " + entry.getKey());
                            SocketAddress clientAddr = ((RelayThread)entry.getValue()).senderSocketAddr;
                            acceptorUDP.write(SystemMessage.makeAudioStreamingRequest(0,0, false), clientAddr);
                        }
                    }
                }
            };
            countDownTimer.start();
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

                updateStatusTxt(fromAddr + "->: " + sm.toString());
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
        timer.cancel();
        super.stopServiceHandler();
    }

    @Override
    public void onValidTimeExpires(SocketAddress socketAddress)
    {
        super.onValidTimeExpires(socketAddress);
        timer.cancel();
    }



}
