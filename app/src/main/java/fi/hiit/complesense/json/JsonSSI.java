package fi.hiit.complesense.json;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Created by hxguo on 24.10.2014.
 */
public class JsonSSI
{
    public static final short R = 0x52; //Request sensor data
    public static final short V = 0x56; //Sensor data response
    public static final short C = 0x43; //Discover sensors
    public static final short N = 0x4E; //Discover reply

    public static final String COMMAND = "command";
    private static final String DESC = "description";
    private static final String SENSOR_TYPES = "sensor_types";

    public static JSONObject makeSensorDiscvoeryReq() throws JSONException
    {
        JSONObject req = new JSONObject();
        req.put(COMMAND, C);
        req.put(DESC, "Discover sensors request");
        return req;
    }

    public static JSONObject makeSensorDiscvoeryRep(List<Integer> sensorTypes) throws JSONException
    {
        JSONObject rep = new JSONObject();
        rep.put(COMMAND, N);
        rep.put(SENSOR_TYPES, sensorTypes);
        rep.put(DESC, "Discover sensors reply");
        return rep;
    }

}
