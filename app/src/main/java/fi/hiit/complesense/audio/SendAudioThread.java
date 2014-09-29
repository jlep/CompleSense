package fi.hiit.complesense.audio;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ClientServiceHandler;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 29.9.2014.
 */
public class SendAudioThread extends AbstractSystemThread
    implements AsyncHttpClient.WebSocketConnectCallback, WebSocket.StringCallback, DataCallback
{
    public static final String TAG = "SendAudioThread";
    private final long ownerThreadId;
    private ExtRecorder extRecorder;
    private final DatagramSocket socket;
    private final boolean keepLocalFile;
    private final Object remoteSocketAddr;

    private static final String PROTOCOL = "ws";
    private URI uri = null;
    private WebSocket mWebSocket = null;

    public static SendAudioThread instance;


    private SendAudioThread(SocketAddress remoteSocketAddr,
                              ServiceHandler serviceHandler,
                              long threadId,
                              boolean keepLocalFile) throws SocketException {
        super(serviceHandler);

        socket = new DatagramSocket();
        uri = URI.create(PROTOCOL +"://"+ remoteSocketAddr+"/");

        this.remoteSocketAddr = remoteSocketAddr;
        this.keepLocalFile = keepLocalFile;
        extRecorder = null;
        this.ownerThreadId = threadId;

        if(this.keepLocalFile)
        {
            extRecorder = ExtRecorder.getInstanse(false, mWebSocket, remoteSocketAddr);
            extRecorder.setOutputFile(Constants.ROOT_DIR + Long.toString(ownerThreadId) +".wav");
            extRecorder.prepare();
        }

        connect();
    }

    private void connect()
    {
        Log.i(TAG, "connect("+ uri.toString() +")");
        serviceHandler.updateStatusTxt("connect("+ uri.toString() +")");
        AsyncHttpClient.getDefaultInstance().websocket(uri.toString(), PROTOCOL, this);
    }


    @Override
    public void run()
    {
        String str = "start SendAudioThread, thread id: " + Thread.currentThread().getId();
        Log.e(TAG, str);
        serviceHandler.updateStatusTxt(str);

        if(extRecorder!= null && keepLocalFile)
            extRecorder.start();
    } // end run

    @Override
    public void stopThread()
    {
        extRecorder.stop();
        extRecorder.reset();
        extRecorder.release();

        if(socket!=null)
            socket.close();
    }

    @Override
    public void pauseThread() {

    }

    public static SendAudioThread getInstancce(InetSocketAddress remoteSocketAddr,
                                               ServiceHandler serviceHandler,
                                               long threadId, boolean keepLocalFile)
    {
        instance = null;
        try
        {
            instance = new SendAudioThread(remoteSocketAddr, serviceHandler, threadId, keepLocalFile);
        }
        catch (SocketException e)
        {
            Log.i(TAG,e.toString() );
        }
        return instance;
    }

    @Override
    public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {

    }

    @Override
    public void onStringAvailable(String s) {

    }

    @Override
    public void onCompleted(Exception ex, WebSocket webSocket)
    {
        Log.i(TAG, "onCompleted("+ uri.toString() +")");
        if (ex != null)
        {
            Log.e(TAG, ex.toString());
            return;
        }
        serviceHandler.updateStatusTxt("Connection with " + uri.toString() + " is established");
        mWebSocket = webSocket;
        start();

    }
}
