package fi.hiit.complesense.connection;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.koushikdutta.async.*;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.DataProcessingThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 29.9.2014.
 */
public class AcceptorWebSocket extends AbsSystemThread
    implements CompletedCallback, AsyncHttpServer.WebSocketRequestCallback
{
    public static final String TAG = AcceptorWebSocket.class.getSimpleName();
    private AsyncHttpServer httpServer;
    private Map<String, WebSocket> _sockets = new HashMap<String, WebSocket>();
    private final DataProcessingThread mDataProcessingThread;

    public AcceptorWebSocket(ServiceHandler serviceHandler) throws IOException {
        super(TAG, serviceHandler);
        httpServer = new AsyncHttpServer();
        httpServer.setErrorCallback(this);
        httpServer.websocket("/test",Constants.WEB_PROTOCOL, this);
        mDataProcessingThread = new DataProcessingThread(serviceHandler);

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
        mDataProcessingThread.start();
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
        String txt = "onConnected(): New connection url: " + requestHeaders.getUri();
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
                JSONObject jsonObject = null;
                try {
                    jsonObject = new JSONObject(s);
                    jsonObject.put(JsonSSI.WEB_SOCKET_KEY, webSocket.toString());
                    serviceHandler.send2Handler(jsonObject.toString());
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
                /*
                try {
                    ByteBufferList.writeOutputStream(outputStream, byteBufferList.getAll());
                } catch (IOException e) {
                    Log.i(TAG, e.toString());
                }
                */
                ByteBuffer[] data = byteBufferList.getAllArray();
                //Log.i(TAG, "num of ByteBuffers: " + data.length);
                for(ByteBuffer bb : data){
                    int payloadSize = bb.remaining();
                    //Log.i(TAG, "recv " + payloadSize + " bytes");

                    mDataProcessingThread.addDataToThreadBuffer(webSocket.toString(), bb.array(), payloadSize);
                    /*
                    short isJsonData = bb.getShort();

                    if(isJsonData==1){
                        byte[] jsonBytes = new byte[bb.remaining()];
                        bb.get(jsonBytes);
                        Log.i(TAG, new String(jsonBytes));
                    }
                    if(isJsonData==0){
                        //byte[] jsonBytes = new byte[bb.remaining()];
                        //bb.get(jsonBytes);
                        Log.i(TAG, "receive " + bb.remaining() +" bytes");
                    }
                    */
                }
                byteBufferList.recycle();
            }
        });

        //Use this to clean up any references to your websocket
        webSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                try {
                    if (ex != null)
                        Log.e("WebSocket", "Error");
                } finally {
                    _sockets.remove(webSocket);
                }
            }
        });

        webSocket.setPongCallback(new WebSocket.PongCallback() {
            @Override
            public void onPongReceived(String s) {
                Log.i(TAG, "onPongReceived(): " + s);
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

        webSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception e) {
                Log.e(TAG,e.toString());
                if(webSocket!=null)
                    webSocket.close();
            }
        });
        webSocket.setEndCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception e) {
                Log.e(TAG,e.toString());
                if(webSocket!=null)
                    webSocket.close();
            }
        });
    }

    private void notifyServiceHandlerNewConnection(WebSocket webSocket) throws JSONException {
        JSONObject jsonAccept = new JSONObject();
        jsonAccept.put(JsonSSI.COMMAND, JsonSSI.NEW_CONNECTION);
        jsonAccept.put(JsonSSI.WEB_SOCKET_KEY, webSocket.toString());
        jsonAccept.put(JsonSSI.DESC, "New Connection");
        Log.i(TAG, "jsonAccept: " + jsonAccept.toString());
        serviceHandler.send2Handler(jsonAccept.toString());
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
            serviceHandler.send2Handler(JsonSSI.makeLastRttReceived(webSocket).toString());
        } catch (JSONException e) {
        }
        Log.i(TAG, "RTT between " + webSocket.toString() + " : " + rttMeasurement + " ms");
    }

    public WebSocket getSocket(String key){
        return _sockets.get(key);
    }
}
