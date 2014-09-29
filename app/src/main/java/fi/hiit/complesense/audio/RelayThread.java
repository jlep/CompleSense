package fi.hiit.complesense.audio;

import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import java.io.IOException;
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

    public RelayThread(ServiceHandler serviceHandler,
                       SocketAddress senderSocketAddr,
                       UdpConnectionRunnable localUdpRunnable,
                       int clientCounter) throws SocketException {
        super(serviceHandler);
        this.senderSocketAddr = senderSocketAddr;
        this.localUdpRunnable = localUdpRunnable;
        this.clientCounter = clientCounter;



        httpServer = new AsyncHttpServer();
        httpServer.setErrorCallback(this);
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
        Log.e(TAG, "start RelayThread, id: " + Thread.currentThread().getId());

        serviceHandler.updateStatusTxt("RelayThread starts: " + Thread.currentThread().getId());
        try
        {
            httpServer.listen(AsyncServer.getDefault(), Constants.LOCAL_WEBSOCKET_PORT + clientCounter);
            httpServer.websocket("/ws", this);



            byte[] buf = new byte[Constants.BUF_SIZE];
            localUdpRunnable.write(SystemMessage.makeAudioStreamingRequest(
                    recvSocket.getLocalPort(),
                    Thread.currentThread().getId(), true), senderSocketAddr);

            while(!Thread.currentThread().isInterrupted())
            {
                DatagramPacket pack = new DatagramPacket(buf, Constants.BUF_SIZE);
                recvSocket.receive(pack);
                packetCount++;
                Log.i(TAG, "Relay recv packetCount: " + packetCount);
                if(packetCount == 1)
                {
                    long timeDiff = serviceHandler.getPeerList().get(senderSocketAddr.toString()).getTimeDiff();

                    String str = "timeDiff with "+ senderSocketAddr.toString() +": " + timeDiff;
                    Log.i(TAG, str);
                    serviceHandler.updateStatusTxt(str);
                    recStartTime = System.currentTimeMillis() - timeDiff;

                    wavFileWriter = WavFileWriter.getWriter(serviceHandler,
                            Constants.ROOT_DIR + "relay-" +Long.toString(recStartTime) + ".wav");

                    String audioName = "audio_name:" + Thread.currentThread().getId() +"_"+ Long.toString(recStartTime);
                    cloudWebSocket.send(audioName);
                    serviceHandler.updateStatusTxt(audioName);
                }
                cloudWebSocket.send(pack.getData());
                if(wavFileWriter!=null)
                    wavFileWriter.write(pack.getData());
            }
            Log.e(TAG, "exits loop");
        }
        catch (SocketException se)
        {
            Log.e(TAG, se.toString());
        }
        catch (IOException ie)
        {
            Log.e(TAG, ie.toString());
        }
        finally{
            stopThread();
        }
    } // end run



    @Override
    public void stopThread()
    {
        if(clientWebSocket!=null)
            clientWebSocket.close();
        if(cloudWebSocket!=null)
            cloudWebSocket.close();
        if(wavFileWriter!=null)
            wavFileWriter.close();
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
        serviceHandler.updateStatusTxt("Connection with " + uri.toString() + " is established");
        if (ex != null) {
            Log.e(TAG, ex.toString());
            return;
        }
        cloudWebSocket = webSocket;
        start();
    }


    /********* Local WebSocket server callbacks*******/
    @Override
    public void onCompleted(Exception e)
    {
        Log.e(TAG, "Local WebSocket Server setup fails: " + e.toString());
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
