package fi.hiit.complesense.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 4.12.2014.
 */
public class SensorDataBuffer
{
    public final int numSensors;
    private Map<Integer, JSONObject> buffer = new HashMap<Integer, JSONObject>();

    public SensorDataBuffer(int numSensors){
        this.numSensors = numSensors;
    }

    public void resetBuffer(){
        buffer.clear();
    }

    public int putBuffer(int type, JSONObject jsonObject) throws JSONException {
        buffer.put(type, new JSONObject(jsonObject.toString()));
        return buffer.size();
    }

    public JSONObject getPackedBufferValues() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray(buffer.values());
        jsonObject.put(JsonSSI.SENSOR_PACKET, jsonArray);
        return jsonObject;
    }
}
