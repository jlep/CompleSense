package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 29.9.2014.
 */
public class AcceptorWebSocket extends AbsSystemThread
    implements CompletedCallback, AsyncHttpServer.WebSocketRequestCallback
{
    public static final String TAG = AcceptorWebSocket.class.getSimpleName();
    private AsyncHttpServer httpServer = new AsyncHttpServer();
    private Map<String, WebSocket> _sockets = new HashMap<String, WebSocket>();

    public AcceptorWebSocket(ServiceHandler serviceHandler) throws IOException {
        super(TAG, serviceHandler);

        httpServer.setErrorCallback(this);
        httpServer.websocket("/command",Constants.WEB_PROTOCOL, this);
    }

    @Override
    public void run()
    {
        String txt = "Start AcceptorWebSocket at thread id: " + Thread.currentThread().getId();
        Log.e(TAG, txt);
        serviceHandler.updateStatusTxt(txt);
        //com.koushikdutta.async.AsyncServer asyncServer =
        //        new com.koushikdutta.async.AsyncServer(TAG + ": " + Long.toString(Thread.currentThread().getId() ));
        //connect();
        httpServer.listen(Constants.SERVER_PORT);
    }

    @Override
    public void stopThread()
    {
        if(httpServer!=null)
            httpServer.stop();
    }

    @Override
    public void onCompleted(Exception e) {
        Log.e(TAG, "AcceptorWebSocket setup fails: " + e.toString());
        stopThread();
    }

    @Override
    public void onConnected(final WebSocket webSocket, RequestHeaders requestHeaders)
    {
        String txt = "onConnected():";
        Log.i(TAG, txt);
        serviceHandler.updateStatusTxt(txt);

        _sockets.put(webSocket.toString(), webSocket);
        try {
            notifyServiceHandlerNewConnection(webSocket);
        } catch (JSONException e) {
        }

        webSocket.setStringCallback(new WebSocket.StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                //Log.i(TAG, "recv String: " + s);
                try {
                    JSONObject jsonObject = new JSONObject(s);
                    jsonObject.put(JsonSSI.WEB_SOCKET_KEY, webSocket.toString());
                    serviceHandler.send2Handler(jsonObject.toString(), ServiceHandler.JSON_RESPONSE_BYTES);
                } catch (JSONException e) {
                    Log.e(TAG, e.toString());
                }
            }
        });

        webSocket.setDataCallback(new DataCallback(){
            @Override
            public void onDataAvailable(DataEmitter dataEmitter,
                                        ByteBufferList byteBufferList)
            {
                ByteBuffer[] data = byteBufferList.getAllArray();
                int payloadSize = 0;

                for(ByteBuffer bb : data){
                    payloadSize += bb.remaining();
                }
                Log.e(TAG, "Command server should not recv binary data: " + payloadSize + " bytes");
                byteBufferList.recycle();
            }
        });

        webSocket.setPongCallback(new WebSocket.PongCallback() {
            @Override
            public void onPongReceived(String s) {
                //Log.i(TAG, "onPongReceived(): " + s);
                try {
                    JSONObject jsonObject = new JSONObject(s);
                    int rounds = jsonObject.getInt(JsonSSI.ROUNDS);
                    long startTime = jsonObject.getLong(JsonSSI.TIMESTAMP);
                    rounds--;
                    if(rounds > 0){ // send another round of RTT message
                        JSONObject jsonForward = JsonSSI.makeRttQuery(startTime, rounds);
                        webSocket.ping(jsonForward.toString());
                    }else{
                        onReceiveLastRttReply(startTime, webSocket);
                    }

                } catch (JSONException e) {
                    Log.i(TAG, e.toString());
                }
            }
        });

        //Use this to clean up any references to your websocket
        webSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                StringBuilder sb = new StringBuilder();
                sb.append(TAG + "Connection with client " + SystemUtil.formatWebSocketStr(webSocket) + " ends ");
                serviceHandler.updateStatusTxt(sb.toString());
                try {
                    Log.e(TAG, ex.toString());
                    sb.append(ex.toString());
                } finally {
                    try {
                        serviceHandler.send2Handler(SystemUtil.makeJsonDisconnect(webSocket).toString(),ServiceHandler.JSON_SYSTEM_STATUS);
                    } catch (JSONException e) {
                    }
                    if(webSocket!=null){
                        _sockets.remove(webSocket.toString());
                        webSocket.close();
                    }

                }
            }
        });
    }

    private void notifyServiceHandlerNewConnection(WebSocket webSocket) throws JSONException {
        JSONObject jsonAccept = new JSONObject();
        jsonAccept.put(JsonSSI.COMMAND, JsonSSI.NEW_CONNECTION);
        jsonAccept.put(JsonSSI.WEB_SOCKET_KEY, webSocket.toString());
        jsonAccept.put(JsonSSI.DESC, "New Connection");
        Log.i(TAG, "jsonAccept: " + jsonAccept.toString());
        serviceHandler.send2Handler(jsonAccept.toString(), ServiceHandler.JSON_RESPONSE_BYTES);
    }

    /**
     * Event is fired when the RTT query sender receives enough RTT reply from a peer
     * @param startTimeMillis: requester local time, when RTT query was sent
     * @param webSocket: peer's webSocket
     */
    public void onReceiveLastRttReply(long startTimeMillis, WebSocket webSocket)
    {
        //Log.i(TAG, "startTimeMillis: " + startTimeMillis + "currentTime: " + System.currentTimeMillis());
        long rttMeasurement = (System.currentTimeMillis() - startTimeMillis) / Constants.RTT_ROUNDS;
        //String remoteSocketAddr = socketChannel.socket().getRemoteSocketAddress().toString();
        try {
            serviceHandler.getPeerList().get(webSocket.toString()).setRtt(rttMeasurement);
            serviceHandler.send2Handler(JsonSSI.makeLastRttReceived(webSocket).toString(), ServiceHandler.JSON_RESPONSE_BYTES);
        } catch (JSONException e) {
        }
        Log.i(TAG, "RTT between " + webSocket.toString() + " : " + rttMeasurement + " ms");
    }

    public WebSocket getSocket(String key){
        return _sockets.get(key);
    }

    public int getConnections(){
        return _sockets.size();
    }
}
