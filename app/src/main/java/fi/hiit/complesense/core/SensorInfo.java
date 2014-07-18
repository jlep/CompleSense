package fi.hiit.complesense.core;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by hxguo on 7/7/14.
 */
public class SensorInfo
{
    public static final String TAG ="SensorsInfoFragment";
    private SensorManager mSensorManager;

    private float[] barometerReadings;


    public SensorInfo(Context context)
    {
        mSensorManager = (SensorManager)
                context.getSystemService(Context.SENSOR_SERVICE);

        barometerReadings = new float[3];
    }


    public float[] getSensorReadings(int sensorType)
    {
        switch (sensorType)
        {
            case Sensor.TYPE_PRESSURE:
                return barometerReadings;
            default:
                return null;

        }
    }

    public synchronized void setBarometerReadings(float[] readings)
    {
        barometerReadings[0] = readings[0];
    }


    public List<Sensor> getSensorsList(Context context)
    {
        List<Sensor> allSensorsList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        List<Sensor> sensorsList = new ArrayList<Sensor>();


        Log.i(TAG, "Total number of sensors = " + allSensorsList.size());

        for (int i = 0; i < allSensorsList.size(); i++)
        {
            if (mSensorManager.getDefaultSensor(i) != null) {
                // Success! There's a such sensor
                List<Sensor> sensorListType = mSensorManager.getSensorList(i);
                Log.i(TAG, "sensor type = " + i);
                for (int j = 0; j < sensorListType.size(); j++) {
                    sensorsList.add(sensorListType.get(j));
                }
            }
        }

        return sensorsList;
    }

    public List<String> getSensorNameList(Context context)
    {
        List<Sensor> allSensorsList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        List<Sensor> sensorsList = new ArrayList<Sensor>();


        Log.i(TAG, "Total number of sensors = " + allSensorsList.size());

        for (int i = 0; i < allSensorsList.size(); i++) {
            if (mSensorManager.getDefaultSensor(i) != null) {
                // Success! There's a such sensor
                List<Sensor> sensorListType = mSensorManager.getSensorList(i);
                Log.i(TAG, "sensor type = " + i);
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

    public static String readingsToString(int sensorType, double[] readings)
    {
        switch (sensorType)
        {
            case Sensor.TYPE_PRESSURE:
                return Double.toString(readings[0]);
            default:
                return null;

        }
    }

    public float[] getBarometerValues()
    {
        return getSensorReadings(Sensor.TYPE_PRESSURE);
    }
}
