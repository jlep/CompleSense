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
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.Set;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.json.JsonSSI;

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
    private final TextFileWritingThread mFileWritingThread;

    private SensorEventListener mListener;
    private JSONObject jsonSensorData = new JSONObject();


    public SensorDataCollectionThread(ServiceHandler serviceHandler,
                                      Context context,
                                      Set<Integer> requiredSensors,
                                      WebSocket webSocket,
                                      TextFileWritingThread out) throws JSONException, IOException {
        super(TAG, serviceHandler);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.sampleCounters = new HashMap<Integer, Integer>(requiredSensors.size());
        this.mWebSocket = webSocket;
        for(int i:requiredSensors)
            sampleCounters.put(i, 0);
        this.mFileWritingThread = out;
    }

    @Override
    public void run()
    {
        Log.i(TAG, " starts SensorDataCollectionThread @thread: " + Thread.currentThread().getId());
        serviceHandler.workerThreads.put(SensorDataCollectionThread.TAG, this);

        mListener = new SensorEventListener()
        {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent)
            {
                try
                {
                    //jsonObject.put(JsonSSI.COMMAND, JsonSSI.V);
                    jsonSensorData.put(JsonSSI.TIMESTAMP, System.currentTimeMillis());
                    jsonSensorData.put(JsonSSI.SENSOR_TYPE, sensorEvent.sensor.getType());
                    JSONArray jsonArray = new JSONArray();
                    for(float value:sensorEvent.values)
                        jsonArray.put(value);
                    jsonSensorData.put(JsonSSI.SENSOR_VALUES, jsonArray);

                    mFileWritingThread.write(jsonSensorData.toString() );

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
    public void stopThread(){
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
