package fi.hiit.complesense.core;

import android.content.Context;
import android.hardware.Sensor;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.channels.Pipe;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AsyncStreamServer;
import fi.hiit.complesense.connection.DataProcessingThread;
import fi.hiit.complesense.json.JsonSSI;

import static fi.hiit.complesense.json.JsonSSI.COMMAND;

/**
 * Created by hxguo on 21.8.2014.
 */
public class GroupOwnerServiceHandler extends ServiceHandler
{
    private static final String TAG = GroupOwnerServiceHandler.class.getSimpleName();
    //private Timer timer;

    private int clientCounter = 0;

    private final String cloudSocketAddrStr = "http://" + Constants.URL +
            ":" + Constants.CLOUD_SERVER_PORT + "/";
    private Map<String, ArrayList<Integer>> availableSensors = new HashMap<String, ArrayList<Integer>>();


    public GroupOwnerServiceHandler(Messenger serviceMessenger, String name,
                                    Context context)
    {
        super(serviceMessenger, name,context, true, null, 0);
//        timer = new Timer();
        //LocalRecThread localRecThread = new LocalRecThread(this);
        //eventHandlingThreads.put(LocalRecThread.TAG, localRecThread);

    }

    @Override
    public void onReceiveLastRttReply(long startTimeMillis, SocketChannel socketChannel)
    {
        super.onReceiveLastRttReply(startTimeMillis, socketChannel);

        ++clientCounter;
        try
        {
            absAsyncIO.send(socketChannel, JsonSSI.makeSensorDiscvoeryReq().toString().getBytes());
        } catch (JSONException e) {
            Log.i(TAG, e.toString());
        }
    }

    @Override
    public boolean handleMessage(Message msg)
    {
        super.handleMessage(msg);
        if(msg.what == JSON_RESPONSE_BYTES)
        {
            try
            {
                JSONObject jsonObject = (JSONObject)msg.obj;

                SocketChannel socketChannel = (SocketChannel)jsonObject.get(JsonSSI.SOCKET_CHANNEL);
                Socket socket = socketChannel.socket();

                switch(jsonObject.getInt(COMMAND))
                {
                    case JsonSSI.NEW_CONNECTION:
                        addNewConnection(socket.getRemoteSocketAddress());
                        JSONObject jsonRtt = JsonSSI.makeRttQuery(System.currentTimeMillis(),
                                Constants.RTT_ROUNDS, socket.getLocalAddress().toString(), socket.getLocalPort());
                        absAsyncIO.send(socketChannel, jsonRtt.toString().getBytes());
                        break;

                    case JsonSSI.NEW_STREAM_SERVER:
                        JSONArray testJson = new JSONArray();
                        testJson.put(Sensor.TYPE_ACCELEROMETER);
                        testJson.put(Sensor.TYPE_GYROSCOPE);
                        testJson.put(Sensor.TYPE_MAGNETIC_FIELD);
                        sendStartStreamClientReq(socketChannel, testJson, 8000, jsonObject.getInt(JsonSSI.STREAM_PORT));
                        break;

                    case JsonSSI.NEW_STREAM_CONNECTION:

                        break;

                    case JsonSSI.N:
                        handleSensorTypesReply(jsonObject, socketChannel);
                        Pipe pipe = Pipe.open();

                        AsyncStreamServer asyncStreamServer = new AsyncStreamServer(this, socketChannel, pipe.sink());
                        workerThreads.put(AsyncStreamServer.TAG, asyncStreamServer);
                        asyncStreamServer.start();

                        DataProcessingThread dataProcessingThread = new DataProcessingThread(this, pipe.source());
                        workerThreads.put(DataProcessingThread.TAG, dataProcessingThread);
                        dataProcessingThread.start();
                        break;
                    default:
                        Log.i(TAG, "Unknown command...");
                        break;
                }

            } catch (JSONException e) {
                Log.i(TAG, e.toString());
            }catch (IOException e) {
                Log.i(TAG, e.toString());
            }
        }

        return false;
    }

    private void handleSensorTypesReply(JSONObject jsonObject, SocketChannel socketChannel) throws JSONException
    {
        JSONArray jsonArray = jsonObject.getJSONArray(JsonSSI.SENSOR_TYPES);
        if(jsonArray!=null)
        {
            updateStatusTxt("Receives sensor list from " + socketChannel.socket() +
                    ": " + jsonArray);
            ArrayList<Integer> sensorList = new ArrayList<Integer>();
            for(int i=0;i<jsonArray.length();i++)
            {
                sensorList.add(jsonArray.getInt(i));
            }
            String key = socketChannel.socket().getRemoteSocketAddress().toString();
            availableSensors.put(key, sensorList);

            //sensorUtil.initSensorValues(jsonArray, socketChannel.socket().toString());
        }
    }

    private void sendStartStreamClientReq(SocketChannel socketChannel, JSONArray reqSensorTypes, int samplesPerSecond, int recvPort) throws JSONException
    {
        Set<Integer> requiredSensors = new HashSet<Integer>();
        for(int i=0;i<reqSensorTypes.length();i++)
            requiredSensors.add(reqSensorTypes.getInt(i));

        String key = socketChannel.socket().getRemoteSocketAddress().toString();
        Set<Integer> sensorSet = new HashSet<Integer>(availableSensors.get(key));
        if(sensorSet==null)
        {
            Log.e(TAG, "no such client: " + key);
            return;
        }

        if(sensorSet.containsAll(requiredSensors))
        {
            JSONObject jsonStartStream = JsonSSI.makeStartStreamReq(reqSensorTypes, samplesPerSecond,
                    peerList.get(key).getDelay(), recvPort);
            absAsyncIO.send(socketChannel, jsonStartStream.toString().getBytes());
        }
        else
        {
            Log.e(TAG, "Client does not have all the required sensors");
        }

    }

    @Override
    public void stopServiceHandler()
    {
//        timer.cancel();
        super.stopServiceHandler();
    }
}
