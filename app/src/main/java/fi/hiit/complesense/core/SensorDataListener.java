package fi.hiit.complesense.core;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Handler;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.ConnectorStreaming;
import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 29.11.2014.
 */
public class SensorDataListener extends CompleSenseDataListener implements SensorEventListener
{
    private static final String TAG = SensorDataListener.class.getSimpleName();
    private final ConnectorStreaming mConnector;
    private final TextFileWritingThread mFileWriter;
    private final long mTimeDiff;
    private final ServiceHandler serviceHandler;
    private final HashMap<Integer, SystemConfig.SensorConfig> sensorConfigs;
    private SensorDataBuffer buffer;

    private JSONObject jsonSensorData = new JSONObject();
    private int packetsCounter = 0;

    public SensorDataListener(ServiceHandler serviceHandler,
                              ConnectorStreaming connectorStreaming, long timeDiff,
                              Map<Integer, SystemConfig.SensorConfig> sensorConfigs, TextFileWritingThread fileWriter){
        this.mConnector = connectorStreaming;
        this.mTimeDiff = timeDiff;
        this.mFileWriter = fileWriter;
        this.serviceHandler = serviceHandler;
        this.sensorConfigs = new HashMap<Integer, SystemConfig.SensorConfig>(sensorConfigs);
        this.buffer = new SensorDataBuffer(this.sensorConfigs.size());
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        packetsCounter++;
        if(packetsCounter%1000 == 3){
            String str = String.format("Get %d JSON packets", packetsCounter);
            serviceHandler.updateStatusTxt(str);
        }

        try
        {
            int type = sensorEvent.sensor.getType();

            jsonSensorData.put(JsonSSI.TIMESTAMP, System.currentTimeMillis() + mTimeDiff);
            jsonSensorData.put(JsonSSI.SENSOR_TYPE, type);
            JSONArray jsonArray = new JSONArray();
            for(float value:sensorEvent.values)
                jsonArray.put(value);
            jsonSensorData.put(JsonSSI.SENSOR_VALUES, jsonArray);

            if(buffer.putBuffer(type, jsonSensorData) == buffer.numSensors){ // enough data has filled the buffer
                JSONObject vals = buffer.getPackedBufferValues();
                //Log.i(TAG, "vals: " + vals.toString());
                mFileWriter.write(vals.toString());
                mConnector.sendJsonData(vals);
                buffer.resetBuffer();
            }
        } catch (JSONException e) {
            Log.i(TAG, e.toString());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
