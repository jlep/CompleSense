package fi.hiit.complesense.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by hxguo on 7/15/14.
 */
public class SensorUtil implements SensorEventListener
{
    public static final String TAG ="SensorUtil";
    private SensorManager mSensorManager;

    private Map<Integer, float[]> localSensorValues;


    public SensorUtil(Context context)
    {
        mSensorManager = (SensorManager)
                context.getSystemService(Context.SENSOR_SERVICE);
        localSensorValues = new ConcurrentHashMap<Integer, float[]>();
    }

    public void registerSensorListener(int sensorType)
    {
        Log.i(TAG,"registerSensorListener()");
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(sensorType),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void unregisterSensorListener()
    {
        mSensorManager.unregisterListener(this);
    }


    public float[] getLocalSensorValues(int sensorType)
    {
        return localSensorValues.get(sensorType);
    }

    private void setLocalSensorValues(int type, float[] values)
    {
        localSensorValues.put(type, values);
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        float[] values = new float[3];
        for(int i=0;i< values.length;i++)
            values[i] = event.values[i];

        setLocalSensorValues(event.sensor.getType(),values);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {

    }

    public static List<String> getLocalSensorNameList(Context context)
    {
        SensorManager sensorManager = (SensorManager)
                context.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> allSensorsList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        List<Sensor> sensorsList = new ArrayList<Sensor>();


        Log.i(TAG, "Total number of sensors = " + allSensorsList.size());

        for (int i = 0; i < allSensorsList.size(); i++) {
            Log.i(TAG, "all sensor type = " + i);
            if (sensorManager.getDefaultSensor(i) != null) {
                // Success! There's a such sensor
                List<Sensor> sensorListType = sensorManager.getSensorList(i);
                Log.i(TAG, "sensor type = " + i);
                Log.i(TAG,"sensorListType.size(): " + sensorListType.size());
                for (int j = 0; j < sensorListType.size(); j++) {
                    sensorsList.add(sensorListType.get(j));
                }
            }
        }

        List<String> sensorsNameList = new ArrayList<String>(sensorsList.size());
        for (Sensor sensor : sensorsList) {
            sensorsNameList.add(sensor.getName());
        }
        return sensorsNameList;
    }

    public static List<Integer> getLocalSensorTypeList(Context context)
    {
        SensorManager sensorManager = (SensorManager)
                context.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> allSensorsList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        List<Integer> sensorsTypeList = new ArrayList<Integer>();

        //Log.i(TAG, "Total number of sensors = " + allSensorsList.size());

        for (int i = 0; i < allSensorsList.size(); i++) {
            //Log.i(TAG, "all sensor type = " + i);
            if (sensorManager.getDefaultSensor(i) != null) {
                // Success! There's a such sensor
                sensorsTypeList.add(i);
            }
        }

        return sensorsTypeList;
    }



}
