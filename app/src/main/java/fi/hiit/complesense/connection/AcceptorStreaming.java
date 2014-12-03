package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.DataProcessingThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.util.SensorUtil;

/**

 * Created by hxguo on 27.11.2014.
 */
public class AcceptorStreaming extends AbsSystemThread implements CompletedCallback {
    private static final String TAG = AcceptorStreaming.class.getSimpleName();

    private final int mStreamPort;
    private final CountDownLatch latch;
    private DataProcessingThread mWavProcessThread = null;
    private DataProcessingThread mJsonProcessThread = null;
    private AsyncHttpServer httpServer = new AsyncHttpServer();

    public AcceptorStreaming(ServiceHandler serviceHandler, Set<Integer> types, int serverIndex, WebSocket webSocket, CountDownLatch latch) throws IOException
    {
        super(TAG, serviceHandler);

        Set<Integer> sensorTypes = new HashSet<Integer>(types);
        this.latch = latch;
        mStreamPort = Constants.STREAM_SERVER_PORT + serverIndex;
        httpServer.setErrorCallback(this);

        if(sensorTypes.remove(SensorUtil.SENSOR_MIC)){
            Set<Integer> wav = new HashSet<Integer>();
            wav.add(SensorUtil.SENSOR_MIC);

            mWavProcessThread = new DataProcessingThread(serviceHandler, wav);
            StreamingCallback wavStreamingCallback = new StreamingCallback(mWavProcessThread, webSocket);
            httpServer.websocket("/streaming_wav", Constants.WEB_PROTOCOL, wavStreamingCallback);
        }

        if(sensorTypes.size()>0){
            mJsonProcessThread = new DataProcessingThread(serviceHandler, sensorTypes);
            StreamingCallback jsonStreamingCallback = new StreamingCallback(mJsonProcessThread, webSocket);
            httpServer.websocket("/streaming_json", Constants.WEB_PROTOCOL, jsonStreamingCallback);
        }
    }

    @Override
    public void stopThread()
    {
        if(httpServer!=null)
            httpServer.stop();
    }

    @Override
    public void run()
    {
        String txt = "Start AcceptorStreaming at port "+ mStreamPort +" thread id: " + Thread.currentThread().getId();
        Log.e(TAG, txt);
        serviceHandler.updateStatusTxt(txt);
        serviceHandler.workerThreads.put(TAG + ":" +mStreamPort, this);

        httpServer.listen(mStreamPort);
        if(mWavProcessThread != null)
            mWavProcessThread.start();
        if(mJsonProcessThread != null)
            mJsonProcessThread.start();
        latch.countDown();
    }



    @Override
    public void onCompleted(Exception e) {

    }

    public int getmStreamPort() {
        return mStreamPort;
    }

    class StreamingCallback implements AsyncHttpServer.WebSocketRequestCallback{

        private final DataProcessingThread mDataProcessThread;
        private WebSocket mWebSocket;
        private final WebSocket cmdWebSocket;

        public StreamingCallback(DataProcessingThread dataProcessThread, WebSocket webSocket) {
            this.mDataProcessThread = dataProcessThread;
            this.cmdWebSocket = webSocket;
        }

        @Override
        public void onConnected(WebSocket webSocket, RequestHeaders requestHeaders) {
            String txt = "onConnected() called@ thread id: " + Thread.currentThread().getId();
            Log.i(TAG, txt);
            serviceHandler.updateStatusTxt(txt);

            mWebSocket = webSocket;
            //Use this to clean up any references to your websocket
            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    try {
                        if (ex != null)
                            Log.e(TAG, ex.toString());
                    } finally {
                        if(mWebSocket!=null)
                            mWebSocket.close();
                        serviceHandler.removeFromPeerList(mWebSocket);
                    }
                }
            });

            mWebSocket.setStringCallback(new WebSocket.StringCallback() {
                @Override
                public void onStringAvailable(String s) {
                    Log.i(TAG, "Streaming server should not recv String: " + s);
                }
            });

            mWebSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter,
                                            ByteBufferList byteBufferList) {
                    ByteBuffer[] data = byteBufferList.getAllArray();
                    int payloadSize = 0;

                    for (ByteBuffer bb : data) {
                        payloadSize += bb.remaining();
                        mDataProcessThread.addDataToThreadBuffer(cmdWebSocket, bb.array(), payloadSize);
                    }
                    byteBufferList.recycle();
                }
            });
        }
    }
}
