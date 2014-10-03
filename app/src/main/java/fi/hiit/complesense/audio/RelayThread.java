package fi.hiit.complesense.audio;

import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.UdpConnectionRunnable;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 29.9.2014.
 */
public class RelayThread extends AbstractSystemThread
        implements AsyncHttpClient.WebSocketConnectCallback,
        WebSocket.StringCallback, DataCallback, CompletedCallback, AsyncHttpServer.WebSocketRequestCallback
{
    public static final String TAG = "RelayThread";
    public final SocketAddress senderSocketAddr;
    private final UdpConnectionRunnable localUdpRunnable;
    private final int clientCounter;
    private long recStartTime;
    private AsyncHttpServer httpServer;
    private WebSocket cloudWebSocket = null;

    private static final String PROTOCOL = "ws";
    private URI uri = URI.create(PROTOCOL +"://"+ Constants.URL+":"+Constants.CLOUD_SERVER_PORT+"/");
    private String tmpFilePath;
    private OutputStream outStream = null;
    private boolean firstPacket = true;
    private int payloadSize = 0;

    private RelayThread(ServiceHandler serviceHandler,
                       SocketAddress senderSocketAddr,
                       UdpConnectionRunnable localUdpRunnable,
                       int clientCounter){
        super(serviceHandler);
        this.senderSocketAddr = senderSocketAddr;
        this.localUdpRunnable = localUdpRunnable;
        this.clientCounter = clientCounter;

        httpServer = new AsyncHttpServer();
        httpServer.setErrorCallback(this);
        httpServer.websocket("/send_rec", PROTOCOL, this);

    }

    public static RelayThread getInstance(ServiceHandler serviceHandler,
                                   SocketAddress senderSocketAddr,
                                   UdpConnectionRunnable localUdpRunnable,
                                   int clientCounter)
    {
        RelayThread relayThread = null;
        relayThread = new RelayThread(serviceHandler,
                senderSocketAddr, localUdpRunnable, clientCounter);
        return relayThread;
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
        Log.e(TAG, "start RelayThread, id: " + Thread.currentThread().getId());
        serviceHandler.updateStatusTxt("RelayThread starts: " + Thread.currentThread().getId());

        try
        {
            tmpFilePath = Constants.ROOT_DIR + Thread.currentThread().getId() +".raw";
            WavFileWriter.writeHeader(tmpFilePath);
            outStream = new FileOutputStream(tmpFilePath, true);

            AsyncServer asyncServer = new AsyncServer("AysncServer:" + Long.toString(Thread.currentThread().getId() ));
            //connect();
            AsyncServerSocket asyncServerSocket = httpServer.listen(asyncServer,
                    Constants.LOCAL_WEBSOCKET_PORT + clientCounter);

            localUdpRunnable.write(SystemMessage.makeAudioStreamingRequest(
                    asyncServerSocket.getLocalPort(),
                    Thread.currentThread().getId(), true), senderSocketAddr);
        } catch (IOException e) {
            Log.i(TAG, e.toString() );
            stopThread();
        }



    } // end run



    @Override
    public void stopThread()
    {
        if(httpServer!=null)
            httpServer.stop();
        if(cloudWebSocket!=null)
            cloudWebSocket.close();
        if(outStream!=null)
        {
            try {
                outStream.close();
                WavFileWriter.close(tmpFilePath, payloadSize, recStartTime);
            } catch (IOException e) {
                Log.i(TAG, e.toString());
            }
        }

    }

    @Override
    public void pauseThread() {

    }

    /****Cloud WebSocket callbacks***/
    @Override
    public void onDataAvailable(DataEmitter dataEmitter,
                                ByteBufferList byteBufferList)
    {
        serviceHandler.updateStatusTxt("I got some bytes from cloud!");
        // note that this data has been read
        byteBufferList.recycle();
    }

    @Override
    public void onStringAvailable(String s) {
        serviceHandler.updateStatusTxt("str from Server: " + s);
    }

    @Override
    public void onCompleted(Exception ex, WebSocket webSocket)
    {
        Log.i(TAG, "onCompleted("+ uri.toString() +")");
        if (ex != null)
        {
            Log.e(TAG, ex.toString());
            serviceHandler.updateStatusTxt("Connection with " + uri.toString() + " failed");
            return;
        }
        serviceHandler.updateStatusTxt("Connection with " + uri.toString() + " is established");
        cloudWebSocket = webSocket;
        start();
    }


    /********* Local WebSocket server callbacks*******/
    @Override
    public void onCompleted(Exception e)
    {
        Log.e(TAG, "Local WebSocket Server setup fails: " + e.toString());
        stopThread();
    }

    @Override
    public void onConnected(WebSocket webSocket, RequestHeaders requestHeaders)
    {
        Log.i(TAG, "onConnected(threadId:"+ Thread.currentThread().getId() +")");
        webSocket.setStringCallback(new WebSocket.StringCallback() {
            @Override
            public void onStringAvailable(String s) {
                Log.i(TAG, "recv String: " + s);
            }
        });


        webSocket.setDataCallback(new DataCallback()
        {
            long lastCheckMillis = System.currentTimeMillis(), interval = 2000;
            int prePayLoadSize = 0;
            @Override
            public void onDataAvailable(DataEmitter dataEmitter,
                                        ByteBufferList byteBufferList)
            {
                // write to local file
                if(firstPacket)
                {
                    recStartTime = System.currentTimeMillis() -
                            serviceHandler.getPeerList().get(senderSocketAddr.toString()).getTimeDiff();
                    firstPacket = false;
                }
                try
                {
                    Log.d(TAG, "payloadSize: "+ payloadSize);
                    payloadSize +=  byteBufferList.remaining();
                    ByteBufferList.writeOutputStream(outStream, byteBufferList.getAll());
                    if(System.currentTimeMillis() - lastCheckMillis > interval)
                    {
                        serviceHandler.updateStatusTxt("recv: " + (payloadSize - prePayLoadSize)
                                +" Bytes from "+ senderSocketAddr.toString());
                        lastCheckMillis = System.currentTimeMillis();
                        prePayLoadSize = payloadSize;
                    }
                } catch (IOException e) {
                    Log.i(TAG, "cannot write date to file: " + e.toString());
                }
                byteBufferList.recycle();
            }
        });
    }
}
