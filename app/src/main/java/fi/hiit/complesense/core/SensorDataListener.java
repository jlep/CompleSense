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
import java.util.Set;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 29.11.2014.
 */
public class SensorDataListener implements SensorEventListener
{
    private static final String TAG = SensorDataListener.class.getSimpleName();
    private final WebSocket mWebSocket;
    private final short isStringData = 1;
    private final TextFileWritingThread mFileWriter;
    private final long delay;

    private JSONObject jsonSensorData = new JSONObject();
    private Handler mHandler;

    public SensorDataListener(WebSocket webSocket, long timeDiff, TextFileWritingThread fileWriter){
        this.mWebSocket = webSocket;
        this.delay = timeDiff;
        this.mFileWriter = fileWriter;
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        //Log.i(TAG, "onSensorChanged() @thread: " + Thread.currentThread().getId());
        try
        {
            //jsonObject.put(JsonSSI.COMMAND, JsonSSI.V);
            jsonSensorData.put(JsonSSI.TIMESTAMP, sensorEvent.timestamp + delay);
            jsonSensorData.put(JsonSSI.SENSOR_TYPE, sensorEvent.sensor.getType());
            JSONArray jsonArray = new JSONArray();
            for(float value:sensorEvent.values)
                jsonArray.put(value);
            jsonSensorData.put(JsonSSI.SENSOR_VALUES, jsonArray);

            mFileWriter.write(jsonSensorData.toString() );

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
}
