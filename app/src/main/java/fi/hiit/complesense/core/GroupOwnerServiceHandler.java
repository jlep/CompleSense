package fi.hiit.complesense.core;

import android.content.Context;
import android.os.Messenger;
import android.util.Log;

import java.net.SocketAddress;
import java.util.List;
import java.util.Timer;

import fi.hiit.complesense.connection.AcceptorUDP;
import fi.hiit.complesense.connection.ConnectorCloud;

/**
 * Created by hxguo on 21.8.2014.
 */
public class GroupOwnerServiceHandler extends ServiceHandler
{
    private static final String TAG = "GroupOwnerServiceHandler";
    private final AcceptorUDP acceptorUDP;
    private Timer timer;

    public GroupOwnerServiceHandler(Messenger serviceMessenger, String name,
                                    Context context)
    {
        super(serviceMessenger, name,context, true, null, 0);
        timer = new Timer();
        acceptorUDP = (AcceptorUDP)eventHandlingThreads.get(AcceptorUDP.TAG);

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
                type = SystemMessage.parseSensorType(sm);
                values = SystemMessage.parseSensorValues(sm);

                sensorUtil.setSensorValue(values, type, fromAddr.toString());
                //send2Cloud(fromAddr.toString(), values);

                updateStatusTxt(fromAddr + "->: " + sm.toString());
                break;

            case SystemMessage.N:
                List<Integer> typeList = SystemMessage.parseSensorTypeList(sm);
                updateStatusTxt("sensor list from " + fromAddr + ": " + typeList.toString());

                int sType = randomlySelectSensor(typeList, fromAddr.toString());
                Log.i(TAG,"sType: " + sType);

                if(acceptorUDP!=null)
                {
                    if(acceptorUDP.getConnectionRunnable()!=null)
                    {
                        ScheduledUdpQueryTask sTask = new ScheduledUdpQueryTask(
                                acceptorUDP.getConnectionRunnable(),this, fromAddr,sType);
                        timer.schedule(sTask, 0, 3000);
                    }
                }

                /*
                // create streaming recv thread
                try {
                    AudioShareManager.ReceiveAudioThread recvAudioThread =
                            new AudioShareManager.ReceiveAudioThread(this);

                    int recvPort = recvAudioThread.getLocalPort();
                    eventHandlingThreads.put(AudioShareManager.ReceiveAudioThread.TAG
                                    + Integer.toString(recvPort), recvAudioThread);
                    recvAudioThread.start();

                    acceptorUDP.getConnectionRunnable().write(
                            SystemMessage.makeAudioStreamingRequest(recvPort), fromAddr);

                } catch (SocketException e) {
                    e.printStackTrace();
                }
*/

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


                break;
            case SystemMessage.RTT:
                //forwardRttQuery(sm.getPayload(),remoteSocketAddr);
                break;

            default:
                break;
        }
    }


    /**
     * Randomly select one sensor from a connected client
     */
    public synchronized int randomlySelectSensor(List<Integer> typeList, String remoteSocketAddr)
    {
        int sType = typeList.get((int)(Math.random()*typeList.size()) );
        float[] dummyValues = {-1.0f,-1.0f,-1.0f};
        sensorUtil.setSensorValue(dummyValues, sType, remoteSocketAddr);

        //sensorValues.put(remoteSocketAddr, dummyValues);
        return sType;
    }

    @Override
    public void stopServiceHandler()
    {
        timer.cancel();
        super.stopServiceHandler();
    }
}
