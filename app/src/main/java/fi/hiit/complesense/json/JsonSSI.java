package fi.hiit.complesense.json;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by hxguo on 24.10.2014.
 */
public class JsonSSI
{
    public static final short R = 0x52; //Request sensor data
    public static final short V = 0x56; //Sensor data response
    public static final short C = 0x43; //Discover sensors
    public static final short N = 0x4E; //Discover reply

    public static final String SENSOR_DISCOVERY_REQ = "sensor_discovery_req";

    public static JSONObject makeSensorDiscvoeryReq() throws JSONException
    {
        JSONObject req = new JSONObject();
        req.put(SENSOR_DISCOVERY_REQ, C);
        return req;
    }

}
