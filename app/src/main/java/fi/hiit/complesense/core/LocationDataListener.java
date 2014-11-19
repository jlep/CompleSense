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
    public static final int LATITUDE = 0;
    public static final int LONGITUDE = 1;

    private final AsyncStreamClient asyncStreamClient;
    private JSONObject jsonGeoCoords = new JSONObject();

    private final LocationManager locationManager;
    //private final AsyncStreamClient asyncStreamClient;
    private LocationListener mLocationListener = null;
    private ByteBuffer buffer;
    private short isJSON = 1;

    public LocationDataListener(ServiceHandler serviceHandler, Context context,
                                AsyncStreamClient asyncStreamClient) throws JSONException
    {
        locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        this.asyncStreamClient = asyncStreamClient;
        initBuffer();
    }

    private void initBuffer() throws JSONException
    {
        jsonGeoCoords.put(JsonSSI.TIMESTAMP, System.currentTimeMillis());
        jsonGeoCoords.put(JsonSSI.SENSOR_TYPE, SensorUtil.SENSOR_GPS);
        JSONArray jsonArray = new JSONArray();
        double placeholder = Double.toString(Double.MIN_VALUE).length()>Double.toString(Double.MAX_VALUE).length()?Double.MIN_VALUE:Double.MAX_VALUE;
        jsonArray.put(placeholder);
        jsonArray.put(placeholder);
        jsonGeoCoords.put(JsonSSI.SENSOR_VALUES,jsonArray);

        int length = Constants.BYTES_INT + Constants.BYTES_SHORT + jsonGeoCoords.toString().getBytes().length;
        Log.i(TAG, "length:" + length);
        buffer = ByteBuffer.allocate(length);
        buffer.clear();
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

            int payloadSize = Constants.BYTES_SHORT + jsonGeoCoords.toString().getBytes().length;
            buffer.putInt(payloadSize);
            buffer.putShort(isJSON);
            Log.i(TAG, "Coords: " + jsonGeoCoords.toString());
            buffer.put(jsonGeoCoords.toString().getBytes());

            asyncStreamClient.send(buffer.array());
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
