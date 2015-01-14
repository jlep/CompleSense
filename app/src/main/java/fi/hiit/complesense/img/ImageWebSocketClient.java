package fi.hiit.complesense.img;

import android.content.Intent;
import android.hardware.Sensor;
import android.util.Log;

import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 23.10.2014.
 */
public class ImageWebSocketClient implements
        AsyncHttpClient.WebSocketConnectCallback
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
        uri = URI.create(Constants.WEB_PROTOCOL + ":/" + serverSocketAddr.toString()+"/send_img");
        this.serviceHandler = serviceHandler;
    }

    public void connect()
    {
        Log.i(TAG, "connect(" + uri.toString() +" with id: " + Thread.currentThread().getId()+ ")");
        serviceHandler.updateStatusTxt("connect("+ uri.toString() +")");
        AsyncHttpClient.getDefaultInstance().websocket(uri.toString(), Constants.WEB_PROTOCOL, this);
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

        mWebSocket.setStringCallback(new WebSocket.StringCallback()
        {
            @Override
            public void onStringAvailable(String s) {
                Log.v(TAG, "recv String: " + s);
                if(s.startsWith(ImageWebSocketServer.IMG_FILE_RECV))
                {
                    Log.i(TAG,"server has received whole file");
                    mWebSocket.close();
                }
            }
        });


        sendImg2Server();
    }

    private void sendImg2Server()
    {
        Log.i(TAG, "sendImg2Server() with id: " + Thread.currentThread().getId());
        if(imgFile!=null)
        {
            mWebSocket.send(ImageWebSocketServer.IMG_FILE_SIZE + ":" +imgFile.length());
            //sendSensorValues();

            try {
                sendImage(imgFile);
            } catch (IOException e) {
                Log.i(TAG, e.toString() );
            }
            mWebSocket.send(ImageWebSocketServer.IMG_SEND_DONE);

            //mWebSocket.close();
        }
    }

    private void sendImage(File sourceFile) throws IOException
    {
        FileInputStream fis = new FileInputStream(sourceFile);

        int bufferSize = 1024, bytesAvailable = 0;
        byte[] buffer = new byte[bufferSize];

        int bytesRead = fis.read(buffer, 0, bufferSize);
        long byteSend = 0;

        while (bytesRead > 0)
        {
            byteSend += bytesRead;
            mWebSocket.send(buffer);
            bytesAvailable = fis.available();
            bufferSize = Math.min(bytesAvailable, bufferSize);
            bytesRead = fis.read(buffer, 0, bufferSize);
        }


        Log.i(TAG, "sendImage(byteSend: " + byteSend +") Complete!!!");
    }

    /*
    private void sendSensorValues()
    {
        Map<Integer, String> sensorValues= new HashMap<Integer, String>();
        float[] values = serviceHandler.sensorUtil.getLocalSensorValue(Sensor.TYPE_ACCELEROMETER);
        sensorValues.put(Sensor.TYPE_ACCELEROMETER, SensorUtil.formatSensorValues(values));

        values = serviceHandler.sensorUtil.getLocalSensorValue(Sensor.TYPE_GYROSCOPE);
        sensorValues.put(Sensor.TYPE_GYROSCOPE, SensorUtil.formatSensorValues(values));

        values = serviceHandler.sensorUtil.getLocalSensorValue(Sensor.TYPE_MAGNETIC_FIELD);
        sensorValues.put(Sensor.TYPE_MAGNETIC_FIELD,SensorUtil.formatSensorValues(values));

        mWebSocket.send(sensorValues.toString());
    }
    */

}
