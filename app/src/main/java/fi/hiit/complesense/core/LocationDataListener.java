package fi.hiit.complesense.core;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AsyncStreamClient;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 12.11.2014.
 */
public class LocationDataListener implements LocationListener
{
    public static final String TAG = LocationDataCollectionThread.class.getSimpleName();
    private static final int LATITUDE = 0;
    public static final int LONGITUDE = 1;
    private JSONObject jsonGeoCoords = new JSONObject();

    private final LocationManager locationManager;
    //private final AsyncStreamClient asyncStreamClient;
    private LocationListener mLocationListener = null;
    private ByteBuffer buffer;
    private int isJSON = 1;

    public LocationDataListener(ServiceHandler serviceHandler,
                            Context context) throws JSONException
    {
        locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        initBuffer();
    }

    private void initBuffer() throws JSONException
    {
        jsonGeoCoords.put(JsonSSI.TIMESTAMP, System.currentTimeMillis());
        jsonGeoCoords.put(JsonSSI.SENSOR_TYPE, SensorUtil.SENSOR_GPS);
        JSONArray jsonArray = new JSONArray();
        jsonArray.put(LATITUDE, 0d);
        jsonArray.put(LONGITUDE, 0d);
        jsonGeoCoords.put(JsonSSI.SENSOR_VALUES,jsonArray);
        buffer = ByteBuffer.allocate(Constants.BYTES_INT + jsonGeoCoords.toString().getBytes().length);
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

            buffer.clear();
            buffer.putInt(isJSON);
            buffer.put(jsonGeoCoords.toString().getBytes());

            //asyncStreamClient.send(buffer.array());
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
