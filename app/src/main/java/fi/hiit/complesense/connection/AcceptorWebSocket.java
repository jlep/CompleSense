package fi.hiit.complesense.connection;

import android.os.Handler;
import android.os.Message;
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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 29.9.2014.
 */
public class AcceptorWebSocket extends AbsSystemThread
    implements CompletedCallback, AsyncHttpServer.WebSocketRequestCallback
{
    public static final String TAG = "AcceptorWebSocket";
    private AsyncHttpServer httpServer;
    private List<WebSocket> _sockets = new ArrayList<WebSocket>();

    public AcceptorWebSocket(ServiceHandler serviceHandler)
    {
        super(TAG, serviceHandler);
        httpServer = new AsyncHttpServer();
        httpServer.setErrorCallback(this);
        httpServer.websocket("/send_rec", Constants.WEB_PROTOCOL, this);
    }

    @Override
    public void run()
    {
        super.run();
    }

    @Override
    public void stopThread()
    {

    }

    @Override
    public void onCompleted(Exception e) {
        Log.e(TAG, e.toString() );
    }

    @Override
    public void onConnected(final WebSocket webSocket, RequestHeaders requestHeaders)
    {
        _sockets.add(webSocket);
        serviceHandler.addNewConnection(webSocket);

        Log.i(TAG, "New connection url: " + requestHeaders.getUri());
        try {
            notifyServiceHandlerNewConnection(webSocket);
        } catch (JSONException e) {
        }


        webSocket.setStringCallback(new WebSocket.StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                Log.i(TAG, "recv String: " + s);
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
                for(ByteBuffer bb : data)
                    Log.i(TAG, "recv " + bb.array().length + " bytes");
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
    }

    private void notifyServiceHandlerNewConnection(WebSocket webSocket) throws JSONException {
        JSONObject jsonAccept = new JSONObject();
        jsonAccept.put(JsonSSI.COMMAND, JsonSSI.NEW_CONNECTION);
        jsonAccept.put(JsonSSI.DESC, "New Connection");
        serviceHandler.send2Handler(webSocket, jsonAccept.toString());
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
            JSONObject jsonLast = JsonSSI.makeLastRttReceived(webSocket);
            Handler handler = serviceHandler.getHandler();
            Message msg = Message.obtain(handler);
            msg.what = ServiceHandler.JSON_RESPONSE_BYTES;
            msg.obj =  jsonLast;

        } catch (JSONException e) {
        }
        Log.i(TAG,"RTT between "+ webSocket.toString() +" : " + rttMeasurement+ " ms");
    }
}
