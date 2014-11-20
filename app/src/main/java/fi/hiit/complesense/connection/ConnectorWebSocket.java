package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import java.net.URI;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 19.11.2014.
 */
public class ConnectorWebSocket extends AbsSystemThread
{
    public static final String TAG = ConnectorWebSocket.class.getSimpleName();
    private final URI uri;
    private WebSocket mWebSocket = null;
    private WebSocket.StringCallback mStringCallback;
    private DataCallback mDataCallback;

    public ConnectorWebSocket(final ServiceHandler serviceHandler) {
        super(TAG, serviceHandler);
        uri = URI.create(Constants.WEB_PROTOCOL +":/"+ remoteSocketAddr.toString()+"/send_rec");

        mStringCallback = new WebSocket.StringCallback(){
            @Override
            public void onStringAvailable(String s) {
                Log.i(TAG, "recv String: " + s);
                serviceHandler.send2Handler(s);
            }
        };
        mDataCallback = new DataCallback() {
            @Override
            public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                Log.i(TAG, "recv " + byteBufferList.getAll().array().length + " bytes" );
            }
        };
    }

    @Override
    public void run() {
        String txt = "ConnectorWebSocket running at thread id: " + Thread.currentThread().getId();
        Log.i(TAG, txt);
        serviceHandler.updateStatusTxt(txt);

        AsyncHttpClient.getDefaultInstance().websocket(uri.toString(), Constants.WEB_PROTOCOL, new AsyncHttpClient.WebSocketConnectCallback(){

            @Override
            public void onCompleted(Exception e, WebSocket webSocket) {
                Log.i(TAG, "onCompleted("+ uri.toString() +")");
                if (e != null)
                {
                    Log.e(TAG, e.toString());
                    return;
                }
                serviceHandler.updateStatusTxt("Connection with " + uri.toString() + " is established");

                mWebSocket = webSocket;
                mWebSocket.setStringCallback(mStringCallback);
                mWebSocket.setDataCallback(mDataCallback);
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
        mWebSocket.close();
    }

}
