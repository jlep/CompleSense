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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.DataProcessingThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.SystemConfig;

/**

 * Created by hxguo on 27.11.2014.
 */
public class AcceptorStreaming extends AbsSystemThread implements AsyncHttpServer.WebSocketRequestCallback, CompletedCallback {
    private static final String TAG = AcceptorStreaming.class.getSimpleName();

    private final int mStreamPort;
    private final CountDownLatch latch;
    private AsyncHttpServer httpServer = new AsyncHttpServer();
    private WebSocket mClientSocket = null;
    private final DataProcessingThread mDataProcessingThread;

    public AcceptorStreaming(ServiceHandler serviceHandler, int clientCounter,
                             Set<Integer> types, CountDownLatch latch) throws IOException
    {
        super(TAG, serviceHandler);

        this.latch = latch;
        mStreamPort = clientCounter + Constants.STREAM_SERVER_PORT;
        httpServer.setErrorCallback(this);
        httpServer.websocket("/streaming", Constants.WEB_PROTOCOL, this);
        mDataProcessingThread = new DataProcessingThread(serviceHandler, types);
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
        serviceHandler.workerThreads.put(TAG, this);

        httpServer.listen(mStreamPort);
        mDataProcessingThread.start();
        latch.countDown();
    }

    @Override
    public void onConnected(WebSocket webSocket, RequestHeaders requestHeaders) {
        String txt = "onConnected() called@ thread id: " + Thread.currentThread().getId();
        Log.i(TAG, txt);
        serviceHandler.updateStatusTxt(txt);
        if(mClientSocket != null){
            Log.e(TAG, "Each Streaming server should only handle one connection");
            return;
        }

        mClientSocket = webSocket;
        mClientSocket.setEndCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception e) {
                Log.e(TAG,e.toString());
                if(mClientSocket!=null)
                    mClientSocket.close();
            }
        });

        //Use this to clean up any references to your websocket
        webSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception ex) {
                try {
                    if (ex != null)
                        Log.e(TAG, ex.toString());
                } finally {
                    if(mClientSocket!=null)
                        mClientSocket.close();
                    serviceHandler.removeFromPeerList(mClientSocket.toString());
                }
            }
        });

        mClientSocket.setStringCallback(new WebSocket.StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                Log.i(TAG, "Streaming server should not recv String: " + s);
            }
        });

        mClientSocket.setDataCallback(new DataCallback(){
            @Override
            public void onDataAvailable(DataEmitter dataEmitter,
                                        ByteBufferList byteBufferList)
            {
                ByteBuffer[] data = byteBufferList.getAllArray();
                int payloadSize = 0;

                for(ByteBuffer bb : data){
                    payloadSize += bb.remaining();
                    mDataProcessingThread.addDataToThreadBuffer(mClientSocket, bb.array(), payloadSize);
                }
                byteBufferList.recycle();
            }
        });
    }

    @Override
    public void onCompleted(Exception e) {

    }

    public WebSocket getmClientSocket() {
        return mClientSocket;
    }

    public int getmStreamPort() {
        return mStreamPort;
    }
}
