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
    private int packetCount;
    private long recStartTime;
    private WavFileWriter wavFileWriter = null;
    private AsyncHttpServer httpServer;
    private WebSocket clientWebSocket = null, cloudWebSocket = null;

    private static final String PROTOCOL = "ws";
    private URI uri = URI.create(PROTOCOL +"://"+ Constants.URL+":"+Constants.CLOUD_SERVER_PORT+"/");
    private final String tmpFilePath;
    private OutputStream outStream = null;
    private boolean firstPacket = true;
    private int payloadSize = 0;

    private RelayThread(ServiceHandler serviceHandler,
                       SocketAddress senderSocketAddr,
                       UdpConnectionRunnable localUdpRunnable,
                       int clientCounter) throws IOException {
        super(serviceHandler);
        this.senderSocketAddr = senderSocketAddr;
        this.localUdpRunnable = localUdpRunnable;
        this.clientCounter = clientCounter;
        tmpFilePath = Constants.ROOT_DIR + Long.toString(Thread.currentThread().getId())+".raw";
        WavFileWriter.writeHeader(tmpFilePath);
        outStream = new FileOutputStream(tmpFilePath, true);

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

        try {
            relayThread = new RelayThread(serviceHandler,
                    senderSocketAddr, localUdpRunnable, clientCounter);

        } catch (SocketException e) {
            Log.i(TAG, e.toString());
        } catch (FileNotFoundException e) {
            Log.i(TAG, e.toString());
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        }
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

        connect();
        AsyncServerSocket asyncServerSocket = httpServer.listen(AsyncServer.getDefault(),
            Constants.LOCAL_WEBSOCKET_PORT + clientCounter);

        localUdpRunnable.write(SystemMessage.makeAudioStreamingRequest(
                asyncServerSocket.getLocalPort(),
                Thread.currentThread().getId(), true), senderSocketAddr);
    } // end run



    @Override
    public void stopThread()
    {
        if(clientWebSocket!=null)
            clientWebSocket.close();
        if(cloudWebSocket!=null)
            cloudWebSocket.close();
        if(outStream!=null)
        {
            try {
                outStream.close();
                WavFileWriter.close(tmpFilePath, payloadSize);
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
        serviceHandler.updateStatusTxt("I got some bytes!");
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
                if(firstPacket)
                {
                    recStartTime = System.currentTimeMillis() -
                            serviceHandler.getPeerList().get(senderSocketAddr.toString()).getTimeDiff();
                    firstPacket = false;
                }
                try {
                    ByteBufferList.writeOutputStream(outStream, byteBufferList.getAll());
                    payloadSize += byteBufferList.getAll().array().length;
                } catch (IOException e) {
                    Log.i(TAG, e.toString());
                }
                byteBufferList.recycle();
            }
        });


    }
}
