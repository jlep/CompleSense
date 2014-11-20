package fi.hiit.complesense.core;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AsyncStreamClient;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 29.10.2014.
 */
public class SensorDataCollectionThread extends AbsSystemThread
{
    public static final float[] dummies = new float[3];
    public static final String TAG = SensorDataCollectionThread.class.getSimpleName();

    private final SensorManager mSensorManager;
    private final HashMap<Integer, Integer> sampleCounters;
    private final WebSocket mWebSocket;
    private final short isStringData = 1;

    private SensorEventListener mListener;
    private JSONObject jsonSensorData = new JSONObject();


    public SensorDataCollectionThread(ServiceHandler serviceHandler,
                                      Context context,
                                      Set<Integer> requiredSensors,
                                      WebSocket webSocket) throws JSONException {
        super(TAG, serviceHandler);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.sampleCounters = new HashMap<Integer, Integer>(requiredSensors.size());
        this.mWebSocket = webSocket;
        for(int i:requiredSensors)
            sampleCounters.put(i, 0);

    }

    @Override
    public void run()
    {
        final File localDir = new File(Constants.ROOT_DIR, Constants.LOCAL_SENSOR_DATA_DIR);
        localDir.mkdirs();
        final File localFile = new File(localDir, mWebSocket.toString()+".txt");
        Log.i(TAG, " starts running at thread: " + Thread.currentThread().getId());

        mListener = new SensorEventListener()
        {
            FileWriter fw = null;


            @Override
            public void onSensorChanged(SensorEvent sensorEvent)
            {
                try
                {
                    fw = new FileWriter(localFile);

                    //jsonObject.put(JsonSSI.COMMAND, JsonSSI.V);
                    jsonSensorData.put(JsonSSI.IS_STRING_DATA, isStringData);
                    jsonSensorData.put(JsonSSI.TIMESTAMP, System.currentTimeMillis());
                    jsonSensorData.put(JsonSSI.SENSOR_TYPE, sensorEvent.sensor.getType());
                    JSONArray jsonArray = new JSONArray();
                    for(float value:sensorEvent.values)
                        jsonArray.put(value);
                    jsonSensorData.put(JsonSSI.SENSOR_VALUES, jsonArray);

                    fw.write(jsonSensorData.toString() );

                    int count = sampleCounters.get(sensorEvent.sensor.getType());
                    count++;
                    sampleCounters.put(sensorEvent.sensor.getType(), count);
                    //if(count%50==0)
                    //    serviceHandler.updateStatusTxt(jsonSensorData.toString());
                    ByteBuffer buffer = ByteBuffer.allocate(Constants.BYTES_SHORT + jsonSensorData.toString().getBytes().length);
                    buffer.putShort(isStringData);
                    buffer.put(jsonSensorData.toString().getBytes());
                    mWebSocket.send(buffer.array());
                } catch (JSONException e) {
                    Log.i(TAG, e.toString());
                }catch (IOException e) {
                    Log.i(TAG, e.toString());
                }finally {
                    if(fw!=null){
                        try {
                            fw.close();
                        } catch (IOException e) {}
                    }
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {

            }
        };

        registerSensors();
    }

    private String formatSensorValues(SensorEvent sensorEvent)
    {
        String formatted = Integer.toString(sensorEvent.sensor.getType()) + "\t"
                + String.valueOf(System.currentTimeMillis()) + "\t"
                + "\t" + String.valueOf(sensorEvent.timestamp)
                + "\t" + String.valueOf(sensorEvent.values[0])
                + "\t" + String.valueOf(sensorEvent.values[1])
                + "\t" + String.valueOf(sensorEvent.values[2])
                + "\r\n";
        return formatted;
    }

    private void registerSensors()
    {
        Log.i(TAG, "registerSensors():" + sampleCounters.keySet());
        for(int type : sampleCounters.keySet())
        {
            mSensorManager.registerListener(mListener,
                    mSensorManager.getDefaultSensor(type), SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void stopThread()
    {
        keepRunning = false;
        unRegisterSensors();
    }

    private void unRegisterSensors()
    {
        if(mSensorManager!=null){
            mSensorManager.unregisterListener(mListener);
        }
    }


}
