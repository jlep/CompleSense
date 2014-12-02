package fi.hiit.complesense.core;

import android.content.Context;
import android.hardware.SensorManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.audio.AudioStreamClient;
import fi.hiit.complesense.connection.ConnectorStreaming;
import fi.hiit.complesense.connection.ConnectorWebSocket;
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
    private LocationListener mLocationDataListener = null;
    private Handler mHandler;
    private SensorDataListener mSensorDataListener = null;


    public ClientServiceHandler(Messenger serviceMessenger,
                                Context context,
                                InetAddress ownerAddr, int delay)

    {
        super(serviceMessenger, TAG, context, false, ownerAddr, delay);
        this.ownerAddr = ownerAddr;
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();
        mHandler = new Handler(getLooper());
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
                            long timeDiff = jsonObject.getLong(JsonSSI.TIME_DIFF);
                            int streamPort = jsonObject.getInt(JsonSSI.STREAM_PORT);
                            String txt = "Streaming port: "+ streamPort + ", timeDiff: " + timeDiff +" ms";
                            Log.i(TAG, txt);
                            updateStatusTxt(txt);

                            startStreamingConnector(sensorConfigJson, streamPort, timeDiff);
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

    private void startStreamingConnector(JSONArray sensorConfigJson, int streamPort, long timeDiff) throws IOException, JSONException
    {
        updateStatusTxt("Start Streaming client");
        Set<Integer> requiredSensors = SystemConfig.getSensorTypesFromJson(sensorConfigJson);
        String txt = "Required sensors: "+ requiredSensors.toString();
        Log.i(TAG, txt);
        updateStatusTxt(txt);

        CountDownLatch latch = new CountDownLatch(3);

        ConnectorStreaming connector = new ConnectorStreaming(this,ownerAddr ,streamPort, latch);
        connector.start();

        TextFileWritingThread fileWritingThread = new TextFileWritingThread(requiredSensors, latch);
        fileWritingThread.start();

        try
        {
            latch.await();
            if(requiredSensors.remove(SensorUtil.SENSOR_MIC)){
                AudioStreamClient audioStreamClient = new AudioStreamClient(this, connector, timeDiff, false);
                audioStreamClient.start();
            }

            if(requiredSensors.remove(SensorUtil.SENSOR_CAMERA)){
                startImageCapture(timeDiff);
            }

            if(requiredSensors.remove(SensorUtil.SENSOR_GPS)){
                LocationManager locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
                mLocationDataListener = new LocationDataListener(this, connector, timeDiff, fileWritingThread);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationDataListener);
            }

            if(requiredSensors.size()>0){
                mSensorDataListener = new SensorDataListener(this, connector, timeDiff, fileWritingThread);
                SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
                Log.i(TAG, "registerSensors():" + requiredSensors);

                for(int type : requiredSensors)
                {
                    sensorManager.registerListener(mSensorDataListener,
                            sensorManager.getDefaultSensor(type), SensorManager.SENSOR_DELAY_GAME, mHandler);
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
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
        if(mLocationDataListener != null){
            Log.i(TAG, "locationManager.removeUpdates");
            LocationManager locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
            locationManager.removeUpdates(mLocationDataListener);
        }
        if(mSensorDataListener != null){
            Log.i(TAG, "SensorManager.unRegister");
            SensorManager sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
            sensorManager.unregisterListener(mSensorDataListener);
        }

        super.stopServiceHandler();
    }
}
