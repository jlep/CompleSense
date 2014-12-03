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

import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
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
    private AsyncHttpServer mHttpServer;


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

        mHttpServer = createHttpServer();
    }

    private AsyncHttpServer createHttpServer(){

        AsyncHttpServer server = new AsyncHttpServer();
        server.get("/images", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                File localDir = new File(Constants.ROOT_DIR, Constants.LOCAL_SENSOR_DATA_DIR);
                String[] imgFiles = localDir.list(new FilenameFilter() {
                    public boolean accept(File directory, String fileName) {
                        return fileName.endsWith(".txt");
                    }
                });
                for(String s: imgFiles)
                    Log.i(TAG, "imgFile: " + s);
            }
        });

        // listen on port 5000
        server.listen(5000);
        return server;
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
                        serviceHandler.addNewConnection(mJsonWebSocket);
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
                                if(mJsonWebSocket!=null)
                                    mJsonWebSocket.close();
                                serviceHandler.removeFromPeerList(mJsonWebSocket);
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
                        serviceHandler.addNewConnection(mWavWebSocket);
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
                                if(mWavWebSocket!=null)
                                    mWavWebSocket.close();
                                serviceHandler.removeFromPeerList(mWavWebSocket);
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
