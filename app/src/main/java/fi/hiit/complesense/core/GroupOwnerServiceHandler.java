package fi.hiit.complesense.core;

import android.content.Context;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AsyncStreamServer;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SystemUtil;

import static fi.hiit.complesense.json.JsonSSI.COMMAND;

/**
 * Created by hxguo on 21.8.2014.
 */
public class GroupOwnerServiceHandler extends ServiceHandler
{
    private static final String TAG = GroupOwnerServiceHandler.class.getSimpleName();
    private SystemConfig sysConfig = null;
    //private Timer timer;

    private int clientCounter = 0;

    private final String cloudSocketAddrStr = "http://" + Constants.URL +
            ":" + Constants.CLOUD_SERVER_PORT + "/";
    private Map<String, ArrayList<Integer>> availableSensors = new HashMap<String, ArrayList<Integer>>();


    public GroupOwnerServiceHandler(Messenger serviceMessenger, String name,
                                    Context context) throws IOException, JSONException {
        super(serviceMessenger, name,context, true, null, 0);
        sysConfig = SystemUtil.loadConfigFile();
        if(sysConfig!=null)
        {
            List<SystemConfig.SensorConfig> reqSensors = sysConfig.reqSensors();
            Set<Integer> reqSensorTypes = new HashSet<Integer>();
            for(SystemConfig.SensorConfig sc : reqSensors){
                reqSensorTypes.add(sc.getType());
            }
            updateStatusTxt("Required sensors: " + reqSensorTypes.toString());
        }

//        timer = new Timer();
        //LocalRecThread localRecThread = new LocalRecThread(this);
        //eventHandlingThreads.put(LocalRecThread.TAG, localRecThread);

    }

    @Override
    public boolean handleMessage(Message msg)
    {
        if(!super.handleMessage(msg)){
            if(msg.what == JSON_RESPONSE_BYTES){
                try{
                    JSONObject jsonObject = (JSONObject)msg.obj;
                    WebSocket webSocket = (WebSocket)jsonObject.get(JsonSSI.WEB_SOCKET);

                    switch(jsonObject.getInt(COMMAND))
                    {
                        case JsonSSI.RTT_LAST:
                            ++clientCounter;
                            if(webSocket!=null)
                                webSocket.send(JsonSSI.makeSensorDiscvoeryReq().toString());
                            return true;
                        case JsonSSI.NEW_CONNECTION:
                            addNewConnection(webSocket);
                            JSONObject jsonRtt = JsonSSI.makeRttQuery(System.currentTimeMillis(),
                                    Constants.RTT_ROUNDS);
                            webSocket.send(jsonRtt.toString());
                            return true;

                        case JsonSSI.NEW_STREAM_SERVER:
                            sendStartStreamClientReq(webSocket, sysConfig.reqSensors(), jsonObject.getInt(JsonSSI.STREAM_PORT));
                            return true;

                        case JsonSSI.NEW_STREAM_CONNECTION:

                            return true;

                        case JsonSSI.N:
                            handleSensorTypesReply(jsonObject, webSocket);
                            startStreamingServer(webSocket);
                            return true;
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
        }

        return false;
    }

    private void handleSensorTypesReply(JSONObject jsonObject, WebSocket webSocket) throws JSONException
    {
        JSONArray jsonArray = jsonObject.getJSONArray(JsonSSI.SENSOR_TYPES);
        if(jsonArray!=null)
        {
            updateStatusTxt("Receives sensor list from " + webSocket.toString() +
                    ": " + jsonArray);
            ArrayList<Integer> sensorList = new ArrayList<Integer>();
            for(int i=0;i<jsonArray.length();i++)
            {
                sensorList.add(jsonArray.getInt(i));
            }
            availableSensors.put(webSocket.toString(), sensorList);

            //sensorUtil.initSensorValues(jsonArray, socketChannel.socket().toString());
        }
    }

    private void startStreamingServer(WebSocket webSocket) throws IOException {
        Log.i(TAG, "startStreamingServer()");
        /*
        if(workerThreads.get(AsyncStreamServer.TAG)==null){
            AsyncStreamServer asyncStreamServer = new AsyncStreamServer(this, socketChannel);
            workerThreads.put(AsyncStreamServer.TAG, asyncStreamServer);
            asyncStreamServer.start();
        }else{
            Log.i(TAG, "Streaming server is already running");
            AsyncStreamServer streamServer = (AsyncStreamServer)workerThreads.get(AsyncStreamServer.TAG);
            streamServer.notifyServerRunning(getHandler(), socketChannel);
        }
        */
    }



    private void sendStartStreamClientReq(WebSocket webSocket, List<SystemConfig.SensorConfig> requiredSensors, int recvPort) throws JSONException
    {
        String key = webSocket.toString();
        Set<Integer> sensorSet = new HashSet<Integer>(availableSensors.get(key));
        if(sensorSet==null)
        {
            Log.e(TAG, "no such client: " + key);
            return;
        }

        Set<Integer> availableSensorTypes = new HashSet<Integer>();
        for(SystemConfig.SensorConfig sc: requiredSensors)
            availableSensorTypes.add(sc.getType());

        if(sensorSet.containsAll(availableSensorTypes)){
            JSONObject jsonStartStream = JsonSSI.makeStartStreamReq(new JSONArray(requiredSensors),
                    peerList.get(key).getDelay(), recvPort);
            webSocket.send(jsonStartStream.toString().getBytes());
        }else{
            Log.e(TAG, "Client does not have all the required sensors");
        }

    }

}
