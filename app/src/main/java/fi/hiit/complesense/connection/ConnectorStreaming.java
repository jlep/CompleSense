package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONObject;

import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 27.11.2014.
 */
public class ConnectorStreaming extends AbsSystemThread
{
    public static final String TAG = ConnectorStreaming.class.getSimpleName();

    private final URI jsonStreamUri, wavStreamUri;
    private final CountDownLatch latch;
    private WebSocket mJsonWebSocket = null, mWavWebSocket = null;
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

        AsyncHttpClient.getDefaultInstance().websocket(jsonStreamUri.toString(),
                Constants.WEB_PROTOCOL, new AsyncHttpClient.WebSocketConnectCallback() {

                    @Override
                    public void onCompleted(Exception e, WebSocket webSocket) {
                        Log.i(TAG, "onCompleted(" + jsonStreamUri.toString() + ")");
                        if (e != null) {
                            Log.e(TAG, e.toString());
                            return;
                        }
                        serviceHandler.updateStatusTxt("Connection with " + jsonStreamUri.toString() + " is established");

                        mJsonWebSocket = webSocket;
                        latch.countDown();

                        mJsonWebSocket.setStringCallback(mStringCallback);
                        mJsonWebSocket.setDataCallback(mDataCallback);
                        mJsonWebSocket.setClosedCallback(new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception e) {
                                StringBuilder sb = new StringBuilder();
                                sb.append("Connection with " + jsonStreamUri.toString() + " ends ");
                                if (e != null){
                                    Log.e(TAG, e.toString());
                                    sb.append(e.toString());
                                }
                                serviceHandler.updateStatusTxt(sb.toString());
                            }
                        });
                    }
                });
        AsyncHttpClient.getDefaultInstance().websocket(wavStreamUri.toString(),
                Constants.WEB_PROTOCOL, new AsyncHttpClient.WebSocketConnectCallback() {

                    @Override
                    public void onCompleted(Exception e, WebSocket webSocket) {
                        Log.i(TAG, "onCompleted(" + wavStreamUri.toString() + ")");
                        if (e != null) {
                            Log.e(TAG, e.toString());
                            return;
                        }
                        serviceHandler.updateStatusTxt("Connection with " + wavStreamUri.toString() + " is established");

                        mWavWebSocket = webSocket;
                        latch.countDown();

                        mWavWebSocket.setStringCallback(mStringCallback);
                        mWavWebSocket.setDataCallback(mDataCallback);
                        mWavWebSocket.setClosedCallback(new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception e) {
                                StringBuilder sb = new StringBuilder();
                                sb.append("Connection with " + mWavWebSocket.toString() + " ends ");
                                if (e != null){
                                    Log.e(TAG, e.toString());
                                    sb.append(e.toString());
                                }
                                serviceHandler.updateStatusTxt(sb.toString());
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

    public void sendBinaryData(byte[] data){
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
