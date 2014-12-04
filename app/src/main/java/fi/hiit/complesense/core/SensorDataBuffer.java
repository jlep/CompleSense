package fi.hiit.complesense.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 4.12.2014.
 */
public class SensorDataBuffer
{
    public final int maxSize;
    private ArrayList<JSONObject> buffer = new ArrayList<JSONObject>();

    public SensorDataBuffer(int maxSize){
        this.maxSize = maxSize;
    }

    public void resetBuffer(){
        buffer.clear();
    }

    public boolean putBuffer(int type, JSONObject jsonObject) throws JSONException {
        buffer.add(new JSONObject(jsonObject.toString()));
        return buffer.size()==maxSize;
    }

    public JSONObject getPackedBufferValues() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray(buffer);
        jsonObject.put(JsonSSI.SENSOR_PACKET, jsonArray);
        return jsonObject;
    }
}
