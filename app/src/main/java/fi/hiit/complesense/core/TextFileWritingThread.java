package fi.hiit.complesense.core;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SensorUtil;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 24.11.2014.
 */
public class TextFileWritingThread extends Thread
{
    private static final String TAG = TextFileWritingThread.class.getSimpleName();
    private final Map<Integer, FileWriter> mSensorWriters = new HashMap<Integer, FileWriter>();
    private final CountDownLatch latch;
    private ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<String>();
    private boolean keepRunning;

    public TextFileWritingThread(Set<Integer> requiredSensors, CountDownLatch latch) throws IOException {
        this.latch = latch;
        File localDir = new File(Constants.ROOT_DIR, Constants.LOCAL_SENSOR_DATA_DIR);
        localDir.mkdirs();
        for(int type: requiredSensors){
            if(type != SensorUtil.SENSOR_MIC && type != SensorUtil.SENSOR_CAMERA){
                FileWriter fw = new FileWriter(new File(localDir, Integer.toString(type)+".txt"));
                mSensorWriters.put(type, fw);
            }
        }
    }

    public void write(String str){
        //Log.i(TAG, "write(): " +str);
        buffer.offer(str);
    }


    @Override
    public void run()
    {
        Log.i(TAG, "Starts TextFileWritingThread @thread id: " + Thread.currentThread().getId());
        latch.countDown();
        try {
            keepRunning = true;
            while(keepRunning){
                String s = buffer.poll();
                if(s!=null){
                    try {
                        JSONObject jsonObject = new JSONObject(s);
                        int type = jsonObject.getInt(JsonSSI.SENSOR_TYPE);

                        if(mSensorWriters.get(type)!=null){
                            String str = SystemUtil.parseJsonSensorData(jsonObject);
                            mSensorWriters.get(type).write(str);
                        }
                        //Log.i(TAG, s);
                    } catch (JSONException e) {
                    }
                }
            }
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        } finally {
            Log.i(TAG, "TextFileWritingThread stops");
        }
    }

    public void stopTxtWriter() {
        Log.i(TAG, "stopTxtWriter()");
        keepRunning = false;
    }
}
