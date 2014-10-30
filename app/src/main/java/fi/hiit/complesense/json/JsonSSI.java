package fi.hiit.complesense.json;

import android.os.Handler;
import android.os.Message;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Set;

import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 24.10.2014.
 */
public class JsonSSI
{
    public static final short R = 0x52; //Request sensor data
    public static final short V = 0x56; //Sensor data response
    public static final short C = 0x43; //Discover sensors
    public static final short N = 0x4E; //Discover reply

    public static final short NEW_STREAM_CONNECTION = 0x08;
    public static final short NEW_CONNECTION = 0x09; // Accept new connection at server
    public static final short RTT_QUERY = 0x10; // rtt query
    public static final short NEW_STREAM_SERVER = 0x11;

    public static final String COMMAND = "command";
    public static final String DESC = "description";
    public static final String SENSOR_TYPES = "sensor_types";
    public static final String SOCKET_CHANNEL = "socket_channel";
    public static final String TIMESTAMP = "timestamp";
    public static final String ROUNDS = "rrt_rounds";
    public static final String ORIGIN_HOST = "origin_host";
    public static final String ORIGIN_PORT = "origin_port";
    private static final String TIME_DIFF = "time_diff";
    public static final String SAMPLES_PER_SECOND = "samples_per_second";
    public static final String STREAM_PORT = "stream_port";
    public static final String SENSOR_TYPE = "sensor_type";
    public static final String SENSOR_VALUES = "sensor_values";


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
        rep.put(SENSOR_TYPES, new JSONArray(sensorTypes));
        rep.put(DESC, "Discover sensors reply");
        return rep;
    }

    public static void send2ServiceHandler(Handler handler, SocketChannel socketChannel, byte[] data)
    {
        try
        {
            JSONObject jsonObject = new JSONObject(new String(data));
            jsonObject.put(JsonSSI.SOCKET_CHANNEL, socketChannel);
            Message msg = Message.obtain(handler, ServiceHandler.JSON_RESPONSE_BYTES, jsonObject);
            msg.sendToTarget();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static JSONObject makeRttQuery(long timeStamp,
                                          int rounds, String host, int port) throws JSONException
    {
        JSONObject query = new JSONObject();
        query.put(COMMAND, RTT_QUERY);
        query.put(TIMESTAMP, timeStamp);
        query.put(ROUNDS, rounds);
        query.put(ORIGIN_HOST, host);
        query.put(ORIGIN_PORT, port);

        return query;
    }

    public static JSONObject makeStartStreamReq(JSONArray sensorTypes, int samplesPerSeconds, long timeDiff, int port) throws JSONException
    {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(COMMAND, R);
        jsonObject.put(SENSOR_TYPES, sensorTypes);
        jsonObject.put(SAMPLES_PER_SECOND, samplesPerSeconds);
        jsonObject.put(TIME_DIFF, timeDiff);
        jsonObject.put(STREAM_PORT, port);
        jsonObject.put(DESC, "Start Streaming Request");
        return jsonObject;
    }
}
