package fi.hiit.complesense.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.SensorValues;

/**
 * Created by hxguo on 7/15/14.
 */
public class SensorUtil implements SensorEventListener
{
    public static final String TAG ="SensorUtil";
    public static final String KEY_LOCAL_SOCKET = "/0.0.0.0";
    public static final int SENSOR_CAMERA = 500;
    public static final int SENSOR_MIC = 505;
    public static final int SENSOR_GPS = 510;

    private final SensorManager mSensorManager;
    // All sensor values stored by local device, values can be retrieved from
    // server too
    protected Map<String, SensorValues> sensorValues; //Map<socketAddress:sensor_type, []>

    public SensorUtil(Context context)
    {
        mSensorManager = (SensorManager)
                context.getSystemService(Context.SENSOR_SERVICE);
        sensorValues = new ConcurrentHashMap<String, SensorValues>();

        List<Integer> localSensorTypeList = getLocalSensorTypeList();
        for(Integer type:localSensorTypeList)
            setLocalSensorValue(type, Constants.dummyValues);
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

    public void setSensorValue(float[] values, int sensorType, String srcSocketAddr)
    {
        String key = SensorValues.genKey(srcSocketAddr, sensorType);
        //Log.e(TAG, "key: " + key);
        SensorValues sv = sensorValues.get(key);
        if(sv==null)
        {
            sensorValues.put(key, new SensorValues(srcSocketAddr, sensorType,values));
        }
        else
            sv.setValues(values);
    }

    public void initSensorValues(Set<Integer> typeList, String remoteSocketAddr)
    {
        for(Integer type : typeList)
            setSensorValue(Constants.dummyValues, type, remoteSocketAddr);
    }

    public void initSensorValues(JSONArray jsonArray, String remoteSocketAddr) throws JSONException
    {
        for(int i=0;i<jsonArray.length();i++)
            setSensorValue(Constants.dummyValues, jsonArray.getInt(i), remoteSocketAddr);
    }

    public float[] getSensorValue(String srcSocketAddr, int sensorType)
    {
        String key = SensorValues.genKey(srcSocketAddr, sensorType);
        return ((SensorValues)sensorValues.get(key)).getValues();
    }


    public float[] getLocalSensorValue(int sensorType)
    {
        String key = KEY_LOCAL_SOCKET+"::"+ Integer.toString(sensorType);

        if (null==sensorValues.get(key))
        {
            // Sensor Listener not installed yet
            registerSensorListener(sensorType);
            return null;
        }
        return ((SensorValues)sensorValues.get(key)).getValues();
    }

    private void setLocalSensorValue(int type, float[] values)
    {
        SensorValues sv = new SensorValues(KEY_LOCAL_SOCKET, type, values);
        //Log.i(TAG, KEY_LOCAL_SOCKET+"::"+Integer.toString(type));
        sensorValues.put(KEY_LOCAL_SOCKET+"::"+Integer.toString(type), sv);
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        float[] values = new float[3];
        for(int i=0;i< values.length;i++)
            values[i] = event.values[i];

        setLocalSensorValue(event.sensor.getType(),values);
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

    /**
     * Randomly select one sensor from a connected client
     */
    public int randomlySelectSensor(String remoteSocketAddr)
    {
        Iterator<String> iter = sensorValues.keySet().iterator();
        List<String> typeList = new ArrayList<String>();
        while(iter.hasNext())
        {
            String key = iter.next();
            if(key.contains(remoteSocketAddr) )
                typeList.add(key);
        }

        String keySensorValues = typeList.get((int)(Math.random()*typeList.size()) );
        Log.i(TAG,"keySensorValues: " + keySensorValues);
        int sType = Integer.parseInt(keySensorValues.substring(keySensorValues.lastIndexOf(":")+1) );
        Log.i(TAG,"substring: " + keySensorValues.substring(keySensorValues.lastIndexOf(":")+1));
        return sType;
    }





    public List<Integer> getLocalSensorTypeList()
    {
        List<Sensor> allSensorsList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        List<Integer> sensorsTypeList = new ArrayList<Integer>();

        //Log.i(TAG, "Total number of sensors = " + allSensorsList.size());

        for (int i = 0; i < allSensorsList.size(); i++) {
            //Log.i(TAG, "all sensor type = " + i);
            if (mSensorManager.getDefaultSensor(i) != null) {
                // Success! There's a such sensor
                sensorsTypeList.add(i);
            }
        }

        return sensorsTypeList;
    }


    public static List<Integer> getLocalSensorTypeList(Context context)
    {
        SensorManager mSensorManager = (SensorManager)
                context.getSystemService(Context.SENSOR_SERVICE);

        List<Sensor> allSensorsList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        List<Integer> sensorsTypeList = new ArrayList<Integer>();

        // -- Check if GPS is enabled
        final LocationManager manager = (LocationManager) context.getSystemService( Context.LOCATION_SERVICE );
        if ( manager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
            sensorsTypeList.add(SENSOR_GPS);
        }

        sensorsTypeList.add(SENSOR_MIC);
        sensorsTypeList.add(SENSOR_CAMERA);

        for (int i = 0; i < allSensorsList.size(); i++) {
            //Log.i(TAG, "all sensor type = " + i);
            if (mSensorManager.getDefaultSensor(i) != null) {
                // Success! There's a such sensor
                sensorsTypeList.add(i);
            }
        }
        return sensorsTypeList;
    }

    public static String formatSensorValues(float[] values)
    {
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<values.length;i++)
        {
            sb.append(String.format("%3.1f", values[i]));
            if(i<values.length-1)
                sb.append(",");
        }
        return sb.toString();
    }

}
