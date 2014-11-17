package fi.hiit.complesense.core;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private final AsyncStreamClient asyncStreamClient;
    private final CountDownLatch startSignal;
    private final HashMap<Integer, Integer> sampleCounters;
    private SensorEventListener mListener;
    private int isJSON = 1;
    private JSONObject jsonSensorData = new JSONObject();
    private ByteBuffer buffer;

    public SensorDataCollectionThread(ServiceHandler serviceHandler,
                                      Context context,
                                      Set<Integer> requiredSensors,
                                      AsyncStreamClient asyncStreamClient, CountDownLatch latch) throws JSONException {
        super(TAG, serviceHandler);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.asyncStreamClient = asyncStreamClient;
        this.sampleCounters = new HashMap<Integer, Integer>(requiredSensors.size());
        startSignal = latch;
        initBuffer();
    }

    private void initBuffer() throws JSONException
    {
        jsonSensorData.put(JsonSSI.TIMESTAMP, System.currentTimeMillis());
        jsonSensorData.put(JsonSSI.SENSOR_TYPE, -1);
        JSONArray jsonArray = new JSONArray();
        float placeholder = Float.MIN_VALUE;
        jsonArray.put(placeholder);
        jsonArray.put(placeholder);
        jsonArray.put(placeholder);
        jsonSensorData.put(JsonSSI.SENSOR_VALUES,jsonArray);
        //Log.i(TAG, "jsonSensorData:" + jsonSensorData.toString());
        //Log.i(TAG, "jsonSensorData.toString().getBytes().length:" + jsonSensorData.toString().getBytes().length);
        buffer = ByteBuffer.allocate(Constants.BYTES_INT + jsonSensorData.toString().getBytes().length);

    }

    @Override
    public void run()
    {
        Log.i(TAG, " starts running at thread: " + Thread.currentThread().getId());
        try {
            startSignal.await();
            final DecimalFormat df = new DecimalFormat("#.00");

            mListener = new SensorEventListener()
            {
                @Override
                public void onSensorChanged(SensorEvent sensorEvent)
                {
                    //String formatedStr =  formatSensorValues(sensorEvent);

                    try {
                        //jsonObject.put(JsonSSI.COMMAND, JsonSSI.V);
                        jsonSensorData.put(JsonSSI.TIMESTAMP, System.currentTimeMillis());
                        jsonSensorData.put(JsonSSI.SENSOR_TYPE, sensorEvent.sensor.getType());
                        JSONArray jsonArray = new JSONArray();
                        for(float value:sensorEvent.values)
                            jsonArray.put(value);
                        jsonSensorData.put(JsonSSI.SENSOR_VALUES, jsonArray);
                        //Log.i(TAG, "jsonSensorData:" + jsonSensorData.toString());
                        //Log.i(TAG, jsonObject.toString());
                        //bb.clear();
                        //ByteBuffer bb = ByteBuffer.allocate(Integer.SIZE/8);
                        //bb.putInt(jsonObject.toString().length());
                        //asyncStreamClient.send(bb.array());
                        buffer.clear();
                        buffer.putInt(isJSON);
                        //Log.i(TAG, "jsonSensorData.toString().getBytes().length:" + jsonSensorData.toString().getBytes().length);
                        buffer.put(jsonSensorData.toString().getBytes());

                        int count = sampleCounters.get(sensorEvent.sensor.getType());
                        count++;
                        sampleCounters.put(sensorEvent.sensor.getType(), count);
                        if(count%50==0)
                            serviceHandler.updateStatusTxt(jsonSensorData.toString());

                        asyncStreamClient.send(buffer.array());
                    } catch (JSONException e) {
                        Log.i(TAG, e.toString());
                    }
                }
                @Override
                public void onAccuracyChanged(Sensor sensor, int i) {

                }
            };

            registerSensors();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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
