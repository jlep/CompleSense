package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.img.ImageWebSocketServer;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SensorUtil;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 27.11.2014.
 */
public class ConnectorStreaming extends AbsSystemThread
{
    public static final String TAG = ConnectorStreaming.class.getSimpleName();

    private final URI jsonStreamUri, wavStreamUri;
    private final CountDownLatch latch;
    private WebSocket mJsonWebSocket = null, mWavWebSocket = null, mImgWebSocket = null;
    private WebSocket.StringCallback mStringCallback;
    private DataCallback mDataCallback;
    private int mJsonPacketsCounter = 0, mWavPacketsCounter = 0;

    public ConnectorStreaming(ServiceHandler serviceHandler, InetAddress ownerInetAddr, int streamPort, CountDownLatch latch) {
        super(TAG, serviceHandler);
        //uri = URI.create(Constants.WEB_PROTOCOL +"://"+ ownerInetAddr.getHostAddress()+":"+ streamPort + "/streaming_json");
        jsonStreamUri = URI.create(Constants.WEB_PROTOCOL +"://"+ ownerInetAddr.getHostAddress()+":"+ streamPort + "/streaming_json");
        wavStreamUri = URI.create(Constants.WEB_PROTOCOL +"://"+ ownerInetAddr.getHostAddress()+":"+ streamPort + "/streaming_wav");
        this.latch = latch;

        mStringCallback = new WebSocket.StringCallback(){
            @Override
            public void onStringAvailable(String s) {
                Log.e(TAG, "Streaming connect should not recv String: " + s);
            }
        };

        mDataCallback = new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                Log.i(TAG, "Streaming connect should not recv binary data: " + byteBufferList.getAll().array().length + " bytes");
            }
        };
    }

    @Override
    public void run() {
        String txt = "Starts ConnectorStreaming at thread id: " + Thread.currentThread().getId();
        Log.e(TAG, txt);
        serviceHandler.updateStatusTxt(txt);
        serviceHandler.workerThreads.put(TAG, this);
        latch.countDown();

        AsyncHttpClient.getDefaultInstance().websocket(jsonStreamUri.toString(),
                Constants.WEB_PROTOCOL, new AsyncHttpClient.WebSocketConnectCallback() {

                    @Override
                    public void onCompleted(Exception e, WebSocket webSocket) {
                        Log.i(TAG, "onCompleted(" + jsonStreamUri.toString() + ")");
                        if (e != null) {
                            Log.e(TAG, e.toString());
                            return;
                        }
                        String str = "Connection with " + jsonStreamUri.toString() + " is established";
                        serviceHandler.updateStatusTxt(str);
                        Log.i(TAG, str);
                        mJsonWebSocket = webSocket;

                        mJsonWebSocket.setStringCallback(mStringCallback);
                        mJsonWebSocket.setDataCallback(mDataCallback);
                        mJsonWebSocket.setClosedCallback(new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception e) {
                                StringBuilder sb = new StringBuilder();
                                sb.append(TAG + " json disconnects with master " + SystemUtil.formatWebSocketStr(mJsonWebSocket));
                                if (e != null){
                                    Log.e(TAG, e.toString());
                                    sb.append(e.toString());
                                }
                                serviceHandler.updateStatusTxt(sb.toString());
                                if(mJsonWebSocket!=null){
                                    mJsonWebSocket.close();
                                    try {
                                        serviceHandler.send2Handler(JsonSSI.makeJsonJsonStreamDisconnet(mJsonWebSocket).toString()
                                                ,ServiceHandler.JSON_SYSTEM_STATUS);
                                    } catch (JSONException e1) {
                                    }
                                }
                            }
                        });

                    }
                });
        AsyncHttpClient.getDefaultInstance().websocket(wavStreamUri.toString(),
                Constants.WEB_PROTOCOL, new AsyncHttpClient.WebSocketConnectCallback() {

                    @Override
                    public void onCompleted(Exception e, final WebSocket webSocket) {
                        Log.i(TAG, "onCompleted(" + wavStreamUri.toString() + ")");
                        if (e != null) {
                            Log.e(TAG, e.toString());
                            return;
                        }
                        String str = "Connection with " + wavStreamUri.toString() + " is established";
                        serviceHandler.updateStatusTxt(str);
                        Log.i(TAG, str);

                        mWavWebSocket = webSocket;

                        mWavWebSocket.setStringCallback(mStringCallback);
                        mWavWebSocket.setDataCallback(mDataCallback);
                        mWavWebSocket.setClosedCallback(new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception e) {
                                StringBuilder sb = new StringBuilder();
                                sb.append(TAG + " wav disconnects with master " + SystemUtil.formatWebSocketStr(mWavWebSocket));
                                if (e != null){
                                    Log.e(TAG, e.toString());
                                    sb.append(e.toString());
                                }
                                serviceHandler.updateStatusTxt(sb.toString());
                                if(mWavWebSocket!=null){
                                    mWavWebSocket.close();
                                    try {
                                        serviceHandler.send2Handler(JsonSSI.makeJsonWavStreamDisconnet(mWavWebSocket).toString()
                                                ,ServiceHandler.JSON_SYSTEM_STATUS);
                                    } catch (JSONException e1) {
                                    }
                                }

                            }
                        });
                    }
                });
    }

    public void sendJsonData(JSONObject jsonSensorData){
        mJsonPacketsCounter++;
        if(mJsonPacketsCounter % 1000 == 4){
            String str = String.format("sending %d th json packet", mJsonPacketsCounter );
            serviceHandler.updateStatusTxt(str);
        }

        ByteBuffer buffer = ByteBuffer.allocate(Constants.BYTES_SHORT + jsonSensorData.toString().getBytes().length);
        buffer.putShort((short)1);
        buffer.put(jsonSensorData.toString().getBytes());

        mJsonWebSocket.send(buffer.array());
    }

    public void sendWavData(byte[] data){
        mWavPacketsCounter++;
        if(mWavPacketsCounter % 100 == 4){
            String str = String.format("sending %d th wav packet", mWavPacketsCounter );
            serviceHandler.updateStatusTxt(str);
        }
        mWavWebSocket.send(data);
    }

    @Override
    public void stopThread() {
        //String txt = "Stopping ConnectorStreaming at thread id: " + Thread.currentThread().getId();
        //Log.e(TAG, txt);
        //serviceHandler.updateStatusTxt(txt);
        Log.i(TAG, "stopThread()");
        if(mJsonWebSocket!=null)
            mJsonWebSocket.close();
        if(mWavWebSocket!=null)
            mWavWebSocket.close();
    }
}
