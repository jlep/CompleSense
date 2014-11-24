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

    private final WebSocket mWebSocket;
    private JSONObject jsonGeoCoords = new JSONObject();

    //private final AsyncStreamClient asyncStreamClient;
    private final short isStringData = 1;

    public LocationDataListener(ServiceHandler serviceHandler, WebSocket webSocket) throws JSONException
    {
        this.mWebSocket = webSocket;
    }



    @Override
    public void onLocationChanged(Location location)
    {
        try
        {
            jsonGeoCoords.put(JsonSSI.TIMESTAMP, System.currentTimeMillis());
            jsonGeoCoords.put(JsonSSI.SENSOR_TYPE, SensorUtil.SENSOR_GPS);
            JSONArray jsonArray = new JSONArray();
            jsonArray.put(LATITUDE, location.getLatitude());
            jsonArray.put(LONGITUDE, location.getLongitude());
            jsonGeoCoords.put(JsonSSI.SENSOR_VALUES,jsonArray);

            ByteBuffer buffer = ByteBuffer.allocate(Constants.BYTES_SHORT + jsonGeoCoords.toString().getBytes().length);
            buffer.putShort(isStringData);
            buffer.put(jsonGeoCoords.toString().getBytes());
            //Log.i(TAG, "Coords: " + jsonGeoCoords.toString());
            mWebSocket.send(buffer.array());

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
