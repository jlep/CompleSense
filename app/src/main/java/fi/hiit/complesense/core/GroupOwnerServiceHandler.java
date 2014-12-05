package fi.hiit.complesense.core;

import android.content.Context;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.connection.AcceptorStreaming;
import fi.hiit.complesense.connection.AcceptorWebSocket;
import fi.hiit.complesense.connection.AliveConnection;
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
    private final String cloudSocketAddrStr = "http://" + Constants.URL_CLOUD +
            ":" + Constants.CLOUD_SERVER_PORT + "/";
    private Map<String, ArrayList<Integer>> availableSensors = new HashMap<String, ArrayList<Integer>>();
    private int indexStreamServer = 0;


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
    }

    @Override
    public boolean handleMessage(Message msg)
    {
        if(super.handleMessage(msg))
        {
            try{
                JSONObject jsonObject = (JSONObject)msg.obj;
                Log.i(TAG, "jsonObject: " + jsonObject.toString());
                String webSocketKey = jsonObject.getString(JsonSSI.WEB_SOCKET_KEY);
                WebSocket webSocket = ((AcceptorWebSocket)workerThreads.get(AcceptorWebSocket.TAG)).getSocket(webSocketKey);

                if(msg.what == JSON_RESPONSE_BYTES)
                {
                    if(webSocket!=null)
                    {
                        switch(jsonObject.getInt(COMMAND))
                        {
                            case JsonSSI.RTT_LAST:
                                webSocket.send(JsonSSI.makeSensorDiscvoeryReq().toString());
                                return true;
                            case JsonSSI.NEW_CONNECTION:
                                addNewConnection(webSocket);
                                requestBatteryLevel(webSocket);
                                JSONObject jsonRtt = JsonSSI.makeRttQuery(System.currentTimeMillis(),
                                        Constants.RTT_ROUNDS);
                                webSocket.ping(jsonRtt.toString());
                                return true;
                            case JsonSSI.A:
                                reassignSecondaryMaster(jsonObject, webSocket);
                                return true;
                            case JsonSSI.NEW_STREAM_CONNECTION:
                                return true;

                            case JsonSSI.N:
                                handleSensorTypesReply(jsonObject, webSocket);
                                startStreamingServer(webSocket, indexStreamServer++);
                                return true;
                            default:
                                Log.i(TAG, "Unknown command...");
                                return false;
                        }
                    }
                }

                if(msg.what == JSON_SYSTEM_STATUS){
                    int status = jsonObject.getInt(JsonSSI.SYSTEM_STATUS);
                    switch (status){
                        case JsonSSI.DISCONNECT:
                            removeFromPeerList(webSocketKey);
                            String key = stopStreamingAcceptor(webSocketKey);

                            String txt = "Stop " + key;
                            Log.i(TAG, txt);
                            updateStatusTxt(txt);
                        default:
                            Log.i(TAG, context.getString(R.string.unknown_status));
                            return false;
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

    private void requestBatteryLevel(WebSocket webSocket) throws JSONException {
        JSONObject jsonObject = JsonSSI.makeJsonBatteryQuery();
        webSocket.send(jsonObject.toString());
    }

    private void reassignSecondaryMaster(JSONObject jsonObject, WebSocket webSocket) throws JSONException {
        double battery = jsonObject.getDouble(JsonSSI.BATTERY_LEVEL);
        boolean isClientLocal = jsonObject.getBoolean(JsonSSI.IS_CLIENT_LOCAL);

        AliveConnection currentSecondaryMaster = findCurrentSecondaryMaster();
        AliveConnection potentialNewSecondaryMaster = peerList.get(webSocket.toString());
        potentialNewSecondaryMaster.setBatteryLevel(battery);
        potentialNewSecondaryMaster.setLocal(isClientLocal);

        if(currentSecondaryMaster == null){
            if(!potentialNewSecondaryMaster.isLocal())
                potentialNewSecondaryMaster.getWebSocket().send(JsonSSI.makeJsonAssignSecondaryMaster(true).toString());
        }


        if(currentSecondaryMaster != null && potentialNewSecondaryMaster != null){
            if(currentSecondaryMaster.getBatteryLevel() < potentialNewSecondaryMaster.getBatteryLevel()){
                currentSecondaryMaster.getWebSocket().send(JsonSSI.makeJsonAssignSecondaryMaster(false).toString());
                potentialNewSecondaryMaster.getWebSocket().send(JsonSSI.makeJsonAssignSecondaryMaster(true).toString());
            }

        }

    }

    private AliveConnection findCurrentSecondaryMaster() {
        double maxBattery = Double.MIN_VALUE;
        AliveConnection secondaryMaster = null;
        for(AliveConnection ac : peerList.values()){
            if(ac.isLocal())
                continue;
            if(ac.getBatteryLevel() > maxBattery){
                maxBattery = ac.getBatteryLevel();
                secondaryMaster = ac;
            }
        }
        return secondaryMaster;
    }


    private String stopStreamingAcceptor(String webSocketKey) {
        String str = AcceptorStreaming.TAG + ":" +webSocketKey;
        for(String key : workerThreads.keySet()){
            if(key.equals(str))
                return key;
        }
        return null;
    }

    private void handleSensorTypesReply(JSONObject jsonObject, WebSocket webSocket) throws JSONException
    {
        long clientLocalTime = jsonObject.getLong(JsonSSI.LOCAL_TIME);
        long rtt = peerList.get(webSocket.toString()).getRtt();
        long delay = System.currentTimeMillis() - (long)(rtt/2) - clientLocalTime;
        peerList.get(webSocket.toString()).setDelay(delay);

        JSONArray jsonArray = jsonObject.getJSONArray(JsonSSI.SENSOR_TYPES);
        if(jsonArray!=null)
        {
            updateStatusTxt("Receives sensor list from " + SystemUtil.formatWebSocketStr(webSocket) +
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

    private void startStreamingServer(WebSocket webSocket, int serverIndex) throws IOException {
        Log.i(TAG, "startStreamingServer()");
        CountDownLatch latch = new CountDownLatch(1);
        AcceptorStreaming streamingServer = new AcceptorStreaming(this,
                sysConfig.reqSensorTypes(), serverIndex, webSocket, latch);
        streamingServer.start();

        try {
            latch.await();
            sendStartStreamClientReq(webSocket, sysConfig.reqSensors(), streamingServer.getmStreamPort() );

        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
    }



    private void sendStartStreamClientReq(WebSocket webSocket,
                                          List<SystemConfig.SensorConfig> requiredSensors, int streamPort) throws JSONException
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
                    peerList.get(key).getDelay(), streamPort );

            webSocket.send(jsonStartStream.toString());
        }else{
            sensorSet.removeAll(availableSensorTypes);
            Log.e(TAG, "Client does not have such sensors: " + sensorSet.toString());
            updateStatusTxt("Client does not have such sensors: " + sensorSet.toString());
        }

    }

}
