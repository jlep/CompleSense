package fi.hiit.complesense.img;

import android.content.Intent;
import android.hardware.Sensor;
import android.util.Log;

import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 23.10.2014.
 */
public class ImageWebSocketClient implements AsyncHttpClient.WebSocketConnectCallback
{
    private static final String TAG = ImageWebSocketClient.class.getSimpleName();

    private static ImageWebSocketClient instance;
    private final File imgFile;
    private final URI uri;
    private final ServiceHandler serviceHandler;
    private WebSocket mWebSocket;


    public ImageWebSocketClient(File imgFile, SocketAddress serverSocketAddr, ServiceHandler serviceHandler)
    {
        this.imgFile = imgFile;
        uri = URI.create(Constants.PROTOCOL + ":/" + serverSocketAddr.toString()+"/send_img");
        this.serviceHandler = serviceHandler;
    }

    public void connect()
    {
        Log.i(TAG, "connect(" + uri.toString() +" with id: " + Thread.currentThread().getId()+ ")");
        serviceHandler.updateStatusTxt("connect("+ uri.toString() +")");
        AsyncHttpClient.getDefaultInstance().websocket(uri.toString(), Constants.PROTOCOL, this);
    }

    public static ImageWebSocketClient getInstance(File imgFile,
                                                 SocketAddress serverSocketAddr,
                                                 ServiceHandler serviceHandler)
    {
        Log.i(TAG, "getInstance() with id: " + Thread.currentThread().getId());
        instance = new ImageWebSocketClient(imgFile, serverSocketAddr, serviceHandler);
        return instance;
    }


    @Override
    public void onCompleted(Exception e, WebSocket webSocket)
    {
        Log.i(TAG, "onCompleted(server uri: "+ uri.toString() +" with id: " + Thread.currentThread().getId()+ ")");
        if (e != null)
        {
            Log.e(TAG, e.toString());
            return;
        }
        serviceHandler.updateStatusTxt("Connection with " + uri.toString() + " is established");
        mWebSocket = webSocket;
        sendImg2Server();
    }

    private void sendImg2Server()
    {
        Log.i(TAG, "sendImg2Server() with id: " + Thread.currentThread().getId());
        if(imgFile!=null)
        {
            mWebSocket.send(ImageWebSocketServer.IMG_FILE_NAME + ":" +imgFile.getName());
            sendSensorValues();

            try {
                sendImage(imgFile);
            } catch (IOException e) {
                Log.i(TAG, e.toString() );
            }
            mWebSocket.send(ImageWebSocketServer.IMG_SEND_DONE);
            Log.i(TAG, "sendImg2Server() with Complete!!!");
            mWebSocket.close();
        }

    }

    private void sendImage(File sourceFile) throws IOException
    {
        FileInputStream fis = new FileInputStream(sourceFile);

        int bufferSize = 1024, bytesAvailable = 0;
        byte[] buffer = new byte[bufferSize];
        int bytesRead = fis.read(buffer, 0, bufferSize);

        while (bytesRead > 0)
        {
            mWebSocket.send(buffer);
            bytesAvailable = fis.available();
            bufferSize = Math.min(bytesAvailable, bufferSize);
            bytesRead = fis.read(buffer, 0, bufferSize);
        }
    }

    private void sendSensorValues()
    {
        Map<Integer, Float> sensorValues= new HashMap<Integer, Float>();
        sensorValues.put(Sensor.TYPE_ACCELEROMETER, -1f);
        sensorValues.put(Sensor.TYPE_GYROSCOPE, -1f);
        sensorValues.put(Sensor.TYPE_MAGNETIC_FIELD, -1f);
        mWebSocket.send(sensorValues.toString());
    }
}
