package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import fi.hiit.complesense.Constants;
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
    private List<WebSocket> _sockets = new ArrayList<WebSocket>();
    private WebSocket clientWebSocket = null;
    private static final String PROTOCOL = "ws";

    private OutputStream outputStream = null;

    protected AcceptorWebSocket(ServiceHandler serviceHandler)
    {
        super(serviceHandler);
        try {
            outputStream = new FileOutputStream(Constants.ROOT_DIR + Long.toString(Thread.currentThread().getId()) );
        } catch (FileNotFoundException e) {
            Log.i(TAG, e.toString() );
        }
        httpServer = new AsyncHttpServer();
        httpServer.setErrorCallback(this);
        httpServer.websocket("/send_rec", PROTOCOL, this);
    }

    @Override
    public void run()
    {
        super.run();
    }

    @Override
    public void stopThread()
    {
        if(outputStream != null)
        {
            try {
                outputStream.close();
            } catch (IOException e) {
                Log.i(TAG, e.toString());
            }
        }
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
                try {
                    ByteBufferList.writeOutputStream(outputStream, byteBufferList.getAll());
                } catch (IOException e) {
                    Log.i(TAG, e.toString());
                }
                byteBufferList.recycle();
            }
        });
    }
}
