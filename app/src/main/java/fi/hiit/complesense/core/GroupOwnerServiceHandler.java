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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AcceptorWebSocket;
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

    private final String cloudSocketAddrStr = "http://" + Constants.URL_CLOUD +
            ":" + Constants.CLOUD_SERVER_PORT + "/";
    private Map<String, ArrayList<Integer>> availableSensors = new HashMap<String, ArrayList<Integer>>();


    public GroupOwnerServiceHandler(Messenger serviceMessenger, Context context) throws IOException, JSONException {
        super(serviceMessenger, TAG, context, true, null, 0);
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
        if(super.handleMessage(msg)){
            try{
                JSONObject jsonObject = (JSONObject)msg.obj;
                Log.i(TAG, "jsonObject: " + jsonObject.toString());
                String webSocketKey = jsonObject.getString(JsonSSI.WEB_SOCKET_KEY);
                WebSocket webSocket = ((AcceptorWebSocket)workerThreads.get(AcceptorWebSocket.TAG)).getSocket(webSocketKey);

                if(webSocket!=null)
                {
                    switch(jsonObject.getInt(COMMAND))
                    {
                        case JsonSSI.RTT_LAST:
                            ++clientCounter;
                            webSocket.send(JsonSSI.makeSensorDiscvoeryReq().toString());
                            return true;
                        case JsonSSI.NEW_CONNECTION:
                            addNewConnection(webSocket);
                            JSONObject jsonRtt = JsonSSI.makeRttQuery(System.currentTimeMillis(),
                                    Constants.RTT_ROUNDS);
                            webSocket.ping(jsonRtt.toString());
                            return true;

                        case JsonSSI.NEW_STREAM_SERVER:
                            sendStartStreamClientReq(webSocket, sysConfig.reqSensors() );
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
                }
            } catch (JSONException e) {
                Log.i(TAG, e.toString());
            }catch (IOException e) {
                Log.i(TAG, e.toString());
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
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JsonSSI.COMMAND, JsonSSI.NEW_STREAM_SERVER);
            jsonObject.put(JsonSSI.WEB_SOCKET_KEY, webSocket.toString());
            jsonObject.put(JsonSSI.DESC, "New Stream Server running at thread: "+ Thread.currentThread().getId());
            send2Handler(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }



    private void sendStartStreamClientReq(WebSocket webSocket, List<SystemConfig.SensorConfig> requiredSensors) throws JSONException
    {
        String key = webSocket.toString();
        Set<Integer> sensorSet = new HashSet<Integer>(availableSensors.get(key));

        if(sensorSet==null){
            Log.e(TAG, "no such client: " + key);
            return;
        }

        Set<Integer> availableSensorTypes = new HashSet<Integer>();
        for(SystemConfig.SensorConfig sc: requiredSensors)
            availableSensorTypes.add(sc.getType());

        if(sensorSet.containsAll(availableSensorTypes)){
            JSONArray jsonArray = new JSONArray(requiredSensors.toString());
            Log.i(TAG, "sendStartStreamClientReq()-requiredSensors: " + jsonArray.toString());
            JSONObject jsonStartStream = JsonSSI.makeStartStreamReq(jsonArray,
                    peerList.get(key).getDelay() );


            webSocket.send(jsonStartStream.toString());
        }else{
            sensorSet.removeAll(availableSensorTypes);
            Log.e(TAG, "Client does not have such sensors: " + sensorSet.toString());
        }

    }

}
