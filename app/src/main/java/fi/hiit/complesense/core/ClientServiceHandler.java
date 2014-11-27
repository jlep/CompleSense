package fi.hiit.complesense.core;

import android.content.Context;
import android.content.Intent;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Message;
import android.os.Messenger;
import android.provider.MediaStore;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Set;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.audio.AudioStreamClient;
import fi.hiit.complesense.connection.ConnectorWebSocket;
import fi.hiit.complesense.img.ImageWebSocketClient;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SensorUtil;

import static fi.hiit.complesense.json.JsonSSI.COMMAND;

/**
 * Created by rocsea0626 on 31.8.2014.
 */
public class ClientServiceHandler extends ServiceHandler
{
    private static final String TAG = "ClientServiceHandler";
    private final InetAddress ownerAddr;
    private WebSocket mServerWebSocket;
    private LocationManager locationManager = null;
    private LocationListener mLocationDataListener = null;


    public ClientServiceHandler(Messenger serviceMessenger,
                                Context context,
                                InetAddress ownerAddr, int delay)

    {
        super(serviceMessenger, TAG, context, false, ownerAddr, delay);
        this.ownerAddr = ownerAddr;
    }

    @Override
    public boolean handleMessage(Message msg)
    {
        if(super.handleMessage(msg))
        {
            try
            {
                JSONObject jsonObject = (JSONObject)msg.obj;
                mServerWebSocket = ((ConnectorWebSocket)workerThreads.get(ConnectorWebSocket.TAG)).getWebSocket();
                if(mServerWebSocket!=null)
                {
                    switch(jsonObject.getInt(COMMAND)){
                        case JsonSSI.C:
                            JSONObject sensorTypeList = JsonSSI.makeSensorDiscvoeryRep(SensorUtil.getLocalSensorTypeList(context));
                            Log.i(TAG, "sensorTypeList: " + sensorTypeList.toString());
                            mServerWebSocket.send(sensorTypeList.toString());
                            return true;
                        case JsonSSI.R:
                            JSONArray sensorConfigJson = jsonObject.getJSONArray(JsonSSI.SENSOR_TYPES);
                            long delay = jsonObject.getLong(JsonSSI.TIME_DIFF);
                            Log.i(TAG, "delay: " + delay +" ms, sensorConfigJson:" + sensorConfigJson.toString());
                            updateStatusTxt("delay between master is " + delay + " ms");
                            startStreaming(sensorConfigJson, delay, mServerWebSocket);
                            return true;
                        case JsonSSI.SEND_DATA:
                            JSONArray imagesNames = jsonObject.getJSONArray(JsonSSI.DATA_TO_SEND);
                            Log.i(TAG, "imageNames: " + imagesNames);
                            break;
                        default:
                            Log.i(TAG, "Unknown command...");
                            break;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                Log.i(TAG, e.toString());
            }
        }


        return false;
    }

    private void startStreaming(JSONArray sensorConfigJson, long delay, WebSocket webSocket) throws IOException, JSONException
    {
        updateStatusTxt("Start Streaming client");

        Set<Integer> requiredSensors = SystemConfig.getSensorTypesFromJson(sensorConfigJson);
        String txt = "Required sensors: "+ requiredSensors.toString();
        Log.i(TAG, txt);
        updateStatusTxt(txt);

        if(requiredSensors.remove(SensorUtil.SENSOR_MIC)){
            AudioStreamClient audioStreamClient = new AudioStreamClient(this, webSocket, delay, false);
            audioStreamClient.start();
        }

        if(requiredSensors.remove(SensorUtil.SENSOR_CAMERA)){ //start camera collecting activity
            startImageCapture(webSocket,delay);
        }

        if(requiredSensors.remove(SensorUtil.SENSOR_GPS)){
            locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            mLocationDataListener = new LocationDataListener(this, webSocket, delay);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationDataListener);
        }

        if(requiredSensors.size()>0){
            final File localDir = new File(Constants.ROOT_DIR, Constants.LOCAL_SENSOR_DATA_DIR);
            localDir.mkdirs();
            final File localFile = new File(localDir, webSocket.toString()+".txt");

            TextFileWritingThread fileWritingThread = new TextFileWritingThread(this, localFile);
            fileWritingThread.start();

            SensorDataCollectionThread sensorDataCollectionThread = new SensorDataCollectionThread(
                    this, context, requiredSensors, delay, webSocket, fileWritingThread);
            sensorDataCollectionThread.start();
        }
    }

    /*
    public void sendImg2Server(File imgFile)
    {
        Log.i(TAG, "sendImg2Server(imgFile: " + imgFile + ") @ thread id: " + Thread.currentThread().getId());
        SocketAddress serverSocketAddr = new InetSocketAddress(ownerAddr.getHostAddress(), serverWebSocketPort);
        ImageWebSocketClient socketClient = new ImageWebSocketClient(imgFile, serverSocketAddr, this);
        socketClient.connect();
    }
    */

    @Override
    public void stopServiceHandler() {
        if(mLocationDataListener!=null)
            locationManager.removeUpdates(mLocationDataListener);
        super.stopServiceHandler();
    }
}
