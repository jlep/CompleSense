package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.URI;
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

    private final URI uri;
    private final CountDownLatch latch;
    private WebSocket mWebSocket = null;
    private WebSocket.StringCallback mStringCallback;
    private DataCallback mDataCallback;


    public ConnectorStreaming(ServiceHandler serviceHandler, InetAddress ownerInetAddr, int streamPort, CountDownLatch latch) {
        super(TAG, serviceHandler);
        uri = URI.create(Constants.WEB_PROTOCOL +"://"+ ownerInetAddr.getHostAddress()+":"+ streamPort + "/streaming");
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

        AsyncHttpClient.getDefaultInstance().websocket(uri.toString(),
                Constants.WEB_PROTOCOL, new AsyncHttpClient.WebSocketConnectCallback() {

                    @Override
                    public void onCompleted(Exception e, WebSocket webSocket) {
                        Log.i(TAG, "onCompleted(" + uri.toString() + ")");
                        if (e != null) {
                            Log.e(TAG, e.toString());
                            return;
                        }
                        serviceHandler.updateStatusTxt("Connection with " + uri.toString() + " is established");

                        mWebSocket = webSocket;
                        latch.countDown();

                        mWebSocket.setStringCallback(mStringCallback);
                        mWebSocket.setDataCallback(mDataCallback);
                        mWebSocket.setEndCallback(new CompletedCallback() {
                            @Override
                            public void onCompleted(Exception e) {
                                Log.e(TAG, e.toString());
                                if (mWebSocket != null)
                                    mWebSocket.close();
                            }
                        });
                    }
                });
    }

    public WebSocket getWebSocket() {
        return mWebSocket;
    }


    @Override
    public void stopThread() {
        String txt = "Stopping ConnectorWebSocket at thread id: " + Thread.currentThread().getId();
        Log.e(TAG, txt);
        serviceHandler.updateStatusTxt(txt);
        if(mWebSocket!=null)
            mWebSocket.close();
    }
}
