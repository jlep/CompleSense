package fi.hiit.complesense.core;

import android.content.Context;
import android.hardware.SensorManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.audio.AudioStreamClient;
import fi.hiit.complesense.connection.ConnectorStreaming;
import fi.hiit.complesense.connection.ConnectorWebSocket;
import fi.hiit.complesense.connection.ImageSender;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.ui.TakePhotoActivity;
import fi.hiit.complesense.util.SensorUtil;
import fi.hiit.complesense.util.SystemUtil;

import static fi.hiit.complesense.json.JsonSSI.COMMAND;
import static fi.hiit.complesense.json.JsonSSI.WAV_STREAM_DISCONNECT;

/**
 * Created by rocsea0626 on 31.8.2014.
 */
public class ClientServiceHandler extends ServiceHandler
{
    private static final String TAG = "ClientServiceHandler";

    private final InetAddress ownerAddr;
    private final boolean mIsLocal;
    private WebSocket mServerWebSocket;
    private LocationListener mLocationDataListener = null;
    private Handler mHandler;
    private SensorDataListener mSensorDataListener = null;
    private ConnectorStreaming mConnector;
    private int mStreamPort;
    private long mTimeDiff;


    public ClientServiceHandler(Messenger serviceMessenger,
                                Context context,
                                InetAddress ownerAddr, int delay, boolean isLocal)

    {
        super(serviceMessenger, TAG, context, false, ownerAddr, delay);
        this.ownerAddr = ownerAddr;
        this.mIsLocal = isLocal;
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
            if(msg.what == JSON_RESPONSE_BYTES)
            {
                try{
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
                                mTimeDiff = jsonObject.getLong(JsonSSI.TIME_DIFF);
                                mStreamPort = jsonObject.getInt(JsonSSI.STREAM_PORT);
                                String txt = "Streaming port: "+ mStreamPort + ", timeDiff: " + mTimeDiff +" ms";
                                Log.i(TAG, txt);
                                updateStatusTxt(txt);

                                startStreamingConnector(sensorConfigJson, mStreamPort, mTimeDiff);
                                return true;
                            case JsonSSI.Q:
                                double battery = SystemUtil.getBatteryLevel(context);
                                JSONObject jsonBattery = JsonSSI.makeBatteryRep(battery, mIsLocal);
                                mServerWebSocket.send(jsonBattery.toString());
                                return true;
                            case JsonSSI.S:
                                String conf = jsonObject.getString(JsonSSI.CONF_CONTENT);
                                if(conf.equals(JsonSSI.SECONDARY_MASTER)){
                                    Message serviceMessage = Message.obtain();
                                    serviceMessage.what = Constants.SERVICE_MSG_SECONDARY_MASTER;
                                    serviceMessage.arg1 = (jsonObject.getBoolean(JsonSSI.CONF_VAL))?1:0;
                                    try {
                                        serviceMessenger.send(serviceMessage);
                                    } catch (RemoteException e) {
                                        Log.e(TAG, e.toString());
                                    }
                                }
                                return true;
                            case JsonSSI.SEND_DATA:
                                sendImages(jsonObject, mStreamPort);
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
                return true;
            }
        }

        if(msg.what == JSON_SYSTEM_STATUS)
        {
            try{
                JSONObject jsonObject = (JSONObject)msg.obj;
                int status = jsonObject.getInt(JsonSSI.SYSTEM_STATUS);
                switch (status){
                    case JsonSSI.JSON_STREAM_DISCONNECT:
                        unregisterSensors();
                        break;
                    case WAV_STREAM_DISCONNECT:
                        if(workerThreads.get(AudioStreamClient.TAG) != null)
                            workerThreads.get(AudioStreamClient.TAG).stopThread();
                        break;
                    default:
                        Log.i(TAG, context.getString(R.string.unknown_status));
                        return true;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return true;
        }


        return false;
    }

    private void startStreamingConnector(JSONArray sensorConfigJson, int streamPort, long timeDiff) throws IOException, JSONException
    {
        updateStatusTxt("Start Streaming client");
        Map<Integer, SystemConfig.SensorConfig> sensorConfigs = new HashMap<Integer, SystemConfig.SensorConfig>();
        Set<Integer> sensorTypes = new HashSet<Integer>();
        for(int i=0;i<sensorConfigJson.length();i++){
            SystemConfig.SensorConfig sc = new SystemConfig.SensorConfig(sensorConfigJson.getJSONObject(i));
            //Log.i(TAG, "sc: " + sc.toString());
            sensorConfigs.put(sc.getType(), sc);
            sensorTypes.add(sc.getType());
        }

        String txt = "Required sensors: "+ sensorTypes.toString();
        Log.i(TAG, txt);
        updateStatusTxt(txt);

        CountDownLatch latch = new CountDownLatch(2);

        mConnector = new ConnectorStreaming(this,ownerAddr ,streamPort, latch);
        mConnector.start();

        TextFileWritingThread fileWritingThread = new TextFileWritingThread(sensorTypes, latch);
        fileWritingThread.start();

        try
        {
            latch.await();
            SystemConfig.SensorConfig micConf = sensorConfigs.remove(SensorUtil.SENSOR_MIC);
            if(micConf != null){
                AudioStreamClient audioStreamClient = new AudioStreamClient(this, mConnector, timeDiff, false);
                audioStreamClient.start();
            }

            SystemConfig.SensorConfig camConf = sensorConfigs.remove(SensorUtil.SENSOR_CAMERA);
            if(camConf != null){
                startImageCapture(timeDiff);
            }

            SystemConfig.SensorConfig gpsConf = sensorConfigs.remove(SensorUtil.SENSOR_GPS);
            if(gpsConf != null){
                LocationManager locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
                mLocationDataListener = new LocationDataListener(this, mConnector, timeDiff, fileWritingThread);
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationDataListener);
            }

            if(sensorConfigs.size()>0){
                mSensorDataListener = new SensorDataListener(this, mConnector, timeDiff, sensorConfigs, fileWritingThread);
                SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
                Log.i(TAG, "registerSensors():" + sensorConfigs.keySet() );

                for(int type : sensorConfigs.keySet()) {
                    sensorManager.registerListener(mSensorDataListener,
                            sensorManager.getDefaultSensor(type), SensorManager.SENSOR_DELAY_GAME, mHandler);
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }

    }

    public void sendImages(JSONObject jsonObject, int streamPort) throws JSONException, IOException
    {
        String imageOrientationsFile = jsonObject.getString(JsonSSI.DATA_TO_SEND);
        Log.i(TAG, "sendImages(): " + imageOrientationsFile);
        File imgFile, localDir = new File(Constants.ROOT_DIR, Constants.LOCAL_SENSOR_DATA_DIR);

        List<JSONObject> orientations = new LinkedList<JSONObject>();

        FileReader fr = new FileReader(imageOrientationsFile);
        BufferedReader br = new BufferedReader(fr);

        String s;
        JSONObject orientationJson;
        while((s = br.readLine()) != null) {
            //Log.i(TAG, "s: " + s);
            orientationJson = new JSONObject(s);
            orientations.add(orientationJson);
        }
        br.close();

        ImageSender imageSender = new ImageSender(ownerAddr, streamPort);
        for(int i=0;i<orientations.size();i++){
            imageSender.sendImage(orientations.get(i));
        }
    }

    @Override
    public void stopServiceHandler() {
        unregisterSensors();
        super.stopServiceHandler();
    }

    private void unregisterSensors(){
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
    }
}
