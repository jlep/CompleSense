package fi.hiit.complesense.core;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.ConnectorStreaming;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 12.11.2014.
 */
public class LocationDataListener implements LocationListener
{
    public static final String TAG = LocationDataListener.class.getSimpleName();
    public static final int LATITUDE = 0;
    public static final int LONGITUDE = 1;
    public static final int ACCURACY = 2;
    public static final int SPEED = 3;
    public static final int PROVIDER = 4;
    private static final int DISTANCE = 5;


    private final ConnectorStreaming mConnector;
    private final long delay;
    private final TextFileWritingThread fileWritingThread;
    private final ServiceHandler serviceHandler;
    private Location prevLocation;
    private JSONObject jsonGeoCoords = new JSONObject();

    //private final AsyncStreamClient asyncStreamClient;
    private int packetsCounter = 0;

    public LocationDataListener(ServiceHandler serviceHandler,
                                ConnectorStreaming connectorStreaming, long delay, TextFileWritingThread fileWritingThread) throws JSONException
    {
        this.mConnector = connectorStreaming;
        this.prevLocation = null;
        this.delay = delay;
        this.fileWritingThread = fileWritingThread;
        this.serviceHandler = serviceHandler;
    }



    @Override
    public void onLocationChanged(Location location)
    {
        packetsCounter++;
        if(packetsCounter%100 == 3){
            String str = String.format("Get %d GPS packets", packetsCounter);
            serviceHandler.updateStatusTxt(str);
        }

        try
        {
            float distance;
            jsonGeoCoords.put(JsonSSI.TIMESTAMP, System.currentTimeMillis() + delay);
            jsonGeoCoords.put(JsonSSI.SENSOR_TYPE, SensorUtil.SENSOR_GPS);
            JSONArray jsonArray = new JSONArray();
            jsonArray.put(LATITUDE, location.getLatitude());
            jsonArray.put(LONGITUDE, location.getLongitude());
            jsonArray.put(ACCURACY, location.getAccuracy());
            jsonArray.put(SPEED, location.getSpeed());
            jsonArray.put(PROVIDER, location.getProvider());

            if(prevLocation!=null){
                distance = location.distanceTo(prevLocation);
            }else{
                distance = -1;
                prevLocation = location;
            }
            jsonArray.put(DISTANCE, distance);

            jsonGeoCoords.put(JsonSSI.SENSOR_VALUES,jsonArray);

            fileWritingThread.write(jsonGeoCoords.toString());
            mConnector.sendJsonData(jsonGeoCoords);
        } catch (JSONException e) {
            Log.i(TAG, e.toString());
        }
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
}
