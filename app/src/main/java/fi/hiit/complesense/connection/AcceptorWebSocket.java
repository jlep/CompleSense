package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 29.9.2014.
 */
public class AcceptorWebSocket extends AbstractSystemThread
    implements CompletedCallback, AsyncHttpServer.WebSocketRequestCallback
{
    public static final String TAG = "AcceptorWebSocket";
    private AsyncHttpServer httpServer;
    private WebSocket clientWebSocket = null;


    protected AcceptorWebSocket(ServiceHandler serviceHandler)
    {
        super(serviceHandler);
        httpServer = new AsyncHttpServer();
        httpServer.setErrorCallback(this);
    }

    @Override
    public void stopThread() {

    }

    @Override
    public void pauseThread() {

    }

    @Override
    public void onCompleted(Exception e) {
        Log.e(TAG, e.toString() );
    }

    @Override
    public void onConnected(WebSocket webSocket, RequestHeaders requestHeaders)
    {
        clientWebSocket = webSocket;
        clientWebSocket.setStringCallback(new WebSocket.StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                Log.i(TAG, "recv String: " + s);
            }
        });

        clientWebSocket.setDataCallback(new DataCallback()
        {
            @Override
            public void onDataAvailable(DataEmitter dataEmitter,
                                        ByteBufferList byteBufferList)
            {
                // write to local file
                byteBufferList.getAll();
                byteBufferList.recycle();
            }
        });
    }
}
