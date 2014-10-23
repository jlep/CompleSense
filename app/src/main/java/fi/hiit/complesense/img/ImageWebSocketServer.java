package fi.hiit.complesense.img;

import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.AsyncServerSocket;
import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.audio.WavFileWriter;
import fi.hiit.complesense.connection.UdpConnectionRunnable;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.AliveConnection;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 23.10.2014.
 */
public class ImageWebSocketServer extends AbstractSystemThread
        implements AsyncHttpServer.WebSocketRequestCallback, CompletedCallback
{
    public static final String TAG = ImageWebSocketServer.class.getSimpleName();
    public static final String IMG_FILE_RECV = "img_file_RECV";
    public static final String IMG_SEND_DONE = "img_send_done";
    public static final String IMG_FILE_SIZE = "img_file_size";

    private final AsyncHttpServer server;
    private final UdpConnectionRunnable localUdpRunnable;
    private final int index;
    private WebSocket clientWebSocket;
    private OutputStream outStream;
    private File outFile;
    private final File recvDir;
    private final SocketAddress clientSocketAddr;
    private long fileSize;
    private long payloadSize = 0;


    @Override
    public void stopThread()
    {
        if(outStream!=null)
        {
            try {
                outStream.close();
            } catch (IOException e) {
                Log.i(TAG,e.toString() );
            }
        }
        if(clientWebSocket!=null)
            clientWebSocket.close();

        if(server!=null)
            server.stop();
    }

    @Override
    public void pauseThread() {

    }

    public ImageWebSocketServer(ServiceHandler serviceHandler,
                                 UdpConnectionRunnable localUdpRunnable, SocketAddress clientSocketAddr, int index)
    {
        super(serviceHandler);
        this.localUdpRunnable = localUdpRunnable;
        server = new AsyncHttpServer();
        server.setErrorCallback(this);
        server.websocket("/send_img", Constants.PROTOCOL,this);
        this.index = index;

        recvDir = new File(Constants.ROOT_DIR, "recv_pic");
        recvDir.mkdir();
        this.clientSocketAddr = clientSocketAddr;
    }

    @Override
    public void run() {
        super.run();

        Log.e(TAG, "start ImageWebSocketServer, id: " + Thread.currentThread().getId() + " index: " +index);
        serviceHandler.updateStatusTxt("ImageWebSocketServer starts: " + Thread.currentThread().getId());

        AsyncServer asyncServer = new AsyncServer("AysncServer:" + Long.toString(Thread.currentThread().getId() ));
        AsyncServerSocket asyncServerSocket = server.listen(asyncServer,
                Constants.LOCAL_WEBSOCKET_PORT + index);

        Log.i(TAG, "Send image capture request to client: " + clientSocketAddr);
        localUdpRunnable.write(SystemMessage.makeStereoImageReq(asyncServerSocket.getLocalPort() ), clientSocketAddr);
    }

    @Override
    public void onConnected(WebSocket webSocket, RequestHeaders requestHeaders)
    {
        String str = "onConnected(threadId:" + Thread.currentThread().getId() + ")";
        Log.i(TAG, str);
        serviceHandler.updateStatusTxt(str);
        clientWebSocket = webSocket;

        try
        {
            outFile = new File(recvDir, "rec_" + index + "_" +Long.toString(System.currentTimeMillis())+".jpg" );
            outStream = new FileOutputStream(outFile, true);
            serviceHandler.updateStatusTxt("Save Recv file as: " + outFile.getAbsolutePath());
        }
        catch (FileNotFoundException e) {
            Log.i(TAG, e.toString());
        }

        clientWebSocket.setStringCallback(new WebSocket.StringCallback()
        {
            @Override
            public void onStringAvailable(String s) {

                if(s.startsWith(IMG_FILE_SIZE))
                {
                    fileSize = Long.parseLong(s.substring(s.lastIndexOf(":")+1));
                    serviceHandler.updateStatusTxt("fileSize: " + fileSize);
                    Log.i(TAG, "fileSize: " + fileSize);
                }
                else if(s.startsWith(IMG_SEND_DONE))
                {
                    String txt = "Client sending completes(payloadSize: " + payloadSize;
                    serviceHandler.updateStatusTxt(txt);
                    Log.i(TAG, txt);
                    stopThread();
                }
                else
                    serviceHandler.updateStatusTxt(s);
            }
        });

        clientWebSocket.setClosedCallback(new CompletedCallback() {
            @Override
            public void onCompleted(Exception e)
            {
                Log.i(TAG,"Client Socket Closed");
                stopThread();
            }
        });

        clientWebSocket.setDataCallback(new DataCallback()
        {
            @Override
            public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList)
            {
                try {
                    payloadSize +=  byteBufferList.remaining();
                    /*
                    if(payloadSize >= fileSize)
                    {
                        serviceHandler.updateStatusTxt("File: " + outFile.getName() + " has been received");
                        clientWebSocket.send(IMG_FILE_RECV);
                        return;
                    }
                    */
                    ByteBufferList.writeOutputStream(outStream, byteBufferList.getAll());
                } catch (IOException e) {
                    Log.i(TAG, e.toString());
                }
                byteBufferList.recycle();
            }
        });

    }


    @Override
    public void onCompleted(Exception e)
    {
        Log.e(TAG, "Local WebSocket Server setup fails: " + e.toString());
        stopThread();
    }

}
