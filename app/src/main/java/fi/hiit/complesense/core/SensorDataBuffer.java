package fi.hiit.complesense.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 4.12.2014.
 */
public class SensorDataBuffer
{
    public final int packetSize;
    private ArrayList<JSONObject> buffer = new ArrayList<JSONObject>();

    public SensorDataBuffer(int maxSize){
        if(SystemUtil.calSensorDataSize() * maxSize > Constants.JSON_STR_BUF_SIZE){
            packetSize = Constants.JSON_STR_BUF_SIZE / SystemUtil.calSensorDataSize();
        }else{
            this.packetSize = maxSize;
        }
    }

    public void resetBuffer(){
        buffer.clear();
    }

    public boolean putBuffer(JSONObject jsonObject) throws JSONException {
        buffer.add(new JSONObject(jsonObject.toString()));
        return buffer.size() == packetSize;
    }

    public JSONObject getPackedBufferValues() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray(buffer);
        jsonObject.put(JsonSSI.SENSOR_PACKET, jsonArray);
        return jsonObject;
    }
}
