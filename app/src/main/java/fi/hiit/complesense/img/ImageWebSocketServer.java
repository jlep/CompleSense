package fi.hiit.complesense.img;

import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
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
import fi.hiit.complesense.core.AliveConnection;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 23.10.2014.
 */
public class ImageWebSocketServer implements
        AsyncHttpServer.WebSocketRequestCallback, CompletedCallback
{
    private static final String TAG = ImageWebSocketServer.class.getSimpleName();
    public static final String IMG_FILE_NAME = "img_file_name";
    public static final String IMG_SEND_DONE = "img_send_done";

    private static ImageWebSocketServer instance;
    private final ServiceHandler serviceHandler;
    private final AsyncHttpServer server;
    private final UdpConnectionRunnable localUdpRunnable;
    private Set<WebSocket> webSocketSet;
    private Map<String, OutputStream> outStreamMap;
    private final File recvDir;

    class MyStringCallBack implements WebSocket.StringCallback
    {
        private final WebSocket webSocket;
        private final int index;
        private File outFile;

        public MyStringCallBack(WebSocket webSocket, int index)
        {
            this.webSocket = webSocket;
            this.index = index;
        }

        @Override
        public void onStringAvailable(String s)
        {
            Log.v(TAG, "recv String: " + s);
            if(s.startsWith(IMG_FILE_NAME))
            {
                try
                {
                    outFile = new File(recvDir, "rec_" + index + "_" +s.substring(s.lastIndexOf(":")+1));
                    outStreamMap.put(webSocket.toString(), new FileOutputStream(outFile, true));
                    serviceHandler.updateStatusTxt("Save Recv file as: " + outFile.getAbsolutePath());
                }
                catch (FileNotFoundException e) {
                    Log.i(TAG, e.toString());
                }
            }
            if(s.startsWith(IMG_SEND_DONE))
            {
                try {
                    outStreamMap.get(webSocket.toString()).close();
                    serviceHandler.updateStatusTxt("Recv file: " + outFile.getAbsolutePath()+ " completes!!");
                } catch (IOException e) {
                    Log.i(TAG, e.toString());
                }
            }

        }
    }

    class MyDataCallBack implements DataCallback
    {
        private final WebSocket webSocket;

        public MyDataCallBack(WebSocket webSocket)
        {
            this.webSocket = webSocket;
        }

        @Override
        public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList)
        {
            try {
                ByteBufferList.writeOutputStream(outStreamMap.get(webSocket.toString()), byteBufferList.getAll());
            } catch (IOException e) {
                Log.i(TAG, e.toString());
            }
            byteBufferList.recycle();
        }
    }

    private ImageWebSocketServer(ServiceHandler serviceHandler,
                                 UdpConnectionRunnable localUdpRunnable)
    {
        this.serviceHandler = serviceHandler;
        this.localUdpRunnable = localUdpRunnable;
        server = new AsyncHttpServer();
        server.setErrorCallback(this);
        server.websocket("/send_img", Constants.PROTOCOL,this);

        recvDir = new File(Constants.ROOT_DIR, "recv_pic");
        recvDir.mkdir();
        webSocketSet = new HashSet<WebSocket>();
        outStreamMap = new HashMap<String, OutputStream>();
    }

    public static ImageWebSocketServer getInstance(ServiceHandler serviceHandler,
                                                   UdpConnectionRunnable localUdpRunnable)
    {
        instance = new ImageWebSocketServer(serviceHandler, localUdpRunnable);
        return instance;
    }

    public void startServer()
    {
        Log.e(TAG, "start ImageWebSocketServer, id: " + Thread.currentThread().getId());
        serviceHandler.updateStatusTxt("ImageWebSocketServer starts: " + Thread.currentThread().getId());

        AsyncServer asyncServer = new AsyncServer("AysncServer:" + Long.toString(Thread.currentThread().getId() ));
        AsyncServerSocket asyncServerSocket = server.listen(asyncServer,
                Constants.LOCAL_WEBSOCKET_PORT);

        for(AliveConnection ac : serviceHandler.getPeerList().values())
        {
            Log.i(TAG, "Send image capture request to client: " + ac.socketAddress);
            localUdpRunnable.write(SystemMessage.makeStereoImageReq(asyncServerSocket.getLocalPort() ), ac.socketAddress);
        }


    }

    @Override
    public void onConnected(WebSocket webSocket, RequestHeaders requestHeaders)
    {
        Log.i(TAG, "onConnected(threadId:"+ Thread.currentThread().getId()
                + "size of WebSocketSet: "+ webSocketSet.size() +")");
        webSocketSet.add(webSocket);

        webSocket.setStringCallback(new MyStringCallBack(webSocket,webSocketSet.size() ));
        webSocket.setDataCallback(new MyDataCallBack(webSocket));
    }


    @Override
    public void onCompleted(Exception e)
    {
        Log.e(TAG, "Local WebSocket Server setup fails: " + e.toString());
        stopServer();
    }

    public void stopServer()
    {
        if(server!=null)
            server.stop();

        for(WebSocket w : webSocketSet)
            w.close();

        for(OutputStream fos : outStreamMap.values())
        {
            try {
                fos.close();
            } catch (IOException e) {
                Log.i(TAG, e.toString());
            }
        }
    }
}
