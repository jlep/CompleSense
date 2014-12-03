package fi.hiit.complesense.core;

import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.MIME_FileWriter;
import fi.hiit.complesense.util.SensorUtil;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 30.10.2014.
 */
public class DataProcessingThread extends AbsSystemThread {

    public static final String TAG = DataProcessingThread.class.getSimpleName();

    private final Set<Integer> requiredSensors;
    private ConcurrentLinkedQueue<ByteBuffer> pendingData = new ConcurrentLinkedQueue<ByteBuffer>();
    //private ConcurrentMap<String, List<ByteBuffer>> pendingData = new ConcurrentHashMap<String, List<ByteBuffer>>();
    private Map<Integer, FileWriter> mSensorWriters = new HashMap<Integer, FileWriter>();
    private MIME_FileWriter mWavFileWriter = null;

    private ExecutorService executorService = Executors.newFixedThreadPool(2);
    private WebSocket mWebSocket = null;


    public DataProcessingThread(ServiceHandler serviceHandler, Set<Integer> requiredSensors) throws IOException
    {
        super(TAG, serviceHandler);
        this.requiredSensors = requiredSensors;
    }

    public void addDataToThreadBuffer(WebSocket webSocket, byte[] data, int count)
    {
        byte[] dataCopy = new byte[count];
        System.arraycopy(data, 0, dataCopy, 0, count);
        String webSocketStr = webSocket.toString();

        pendingData.offer(ByteBuffer.wrap(dataCopy));
        if(mWebSocket == null){
            mWebSocket = webSocket;
            Log.i(TAG, "New Streaming client: " + webSocketStr);
            executorService.execute(new CreateNewFileRunnable(
                    SystemUtil.formatWebSocketStr(webSocket), requiredSensors) );
        }
    }

    @Override
    public void run()
    {
        Log.i(TAG, "Start DataProcessingThread at thread: " + Thread.currentThread().getId());
        serviceHandler.workerThreads.put(DataProcessingThread.TAG +"-"+Long.toString(Thread.currentThread().getId()), this);

        FileOutputStream fos = null;
        ByteBuffer data;
        byte[] wavBuf = new byte[Constants.BUF_SIZE];
        byte[] strBuf = new byte[Constants.BUF_SIZE];
        int mediaDataType, payloadSize;
        try
        {
            while(keepRunning)
            {
                data = pendingData.poll();
                if(data !=null && data.hasRemaining()){
                    //Log.i(TAG, "data.remaining(): " + data.remaining());
                    //Log.i(TAG, "remaining(): " + data.remaining());
                    short isStringData = data.getShort();
                    //Log.i(TAG, "isStringData: " + isStringData);

                    if(isStringData == 0){ // Binary data
                        mediaDataType = data.getInt();
                        payloadSize = data.remaining();
                        data.get(wavBuf, 0, payloadSize);

                        if(mediaDataType == SensorUtil.SENSOR_MIC){
                            //Log.i(TAG, "recv mic data: " + payloadSize);
                            if(mWavFileWriter != null)
                                mWavFileWriter.write(wavBuf, 0, payloadSize);
                        }
                        continue;
                    }
                    if(isStringData == 1){ // Data encapsulated in JSON
                        //JSONObject jsonObject = new JSONObject(new String(data.slice().array()));
                        int byteCount = data.remaining();
                        data.get(strBuf,0,byteCount);

                        String sensorData =new String(strBuf, 0, byteCount);
                        try {
                            JSONObject jsonObject = new JSONObject(sensorData);
                            JSONArray jsonArray = jsonObject.getJSONArray(JsonSSI.SENSOR_PACKET);

                            for(int i=0;i<jsonArray.length();i++){
                                //Log.i(TAG, "item: " + jsonArray.getJSONObject(i).toString());
                                int type = (jsonArray.getJSONObject(i)).getInt(JsonSSI.SENSOR_TYPE);
                                if(mSensorWriters.get(type)!=null){
                                    String str = SystemUtil.parseJsonSensorData(jsonArray.getJSONObject(i));
                                    mSensorWriters.get(type).write(str);
                                }
                            }
                        } catch (JSONException e) {
                        }
                        //Log.i(TAG, "sensor data: " + new String(buf));
                        continue;
                    }
                }
            }

        }catch (IOException e) {
            Log.i(TAG, e.toString());
        }finally {
            Log.i(TAG, "Exit loop");
            try {
                if(fos!=null)
                    fos.close();
                for(FileWriter fw: mSensorWriters.values())
                    fw.close();
                if(mWavFileWriter!=null)
                    mWavFileWriter.close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }



    }

    /**
     * To create a new file to store the data from different clients
     */
    private class CreateNewFileRunnable implements Runnable{

        private final String mWebSocketStr;
        private Set<Integer> requiredSensors;

        CreateNewFileRunnable(String webSocketStr, Set<Integer> requiredSensors){
            this.mWebSocketStr = webSocketStr;
            this.requiredSensors = requiredSensors;
        }

        @Override
        public void run() {
            try {
                File recvDir = new File(Constants.ROOT_DIR, mWebSocketStr);
                if(recvDir.mkdirs())
                    Log.i(TAG, "Create dir: " + recvDir.toString());

                if(this.requiredSensors.remove(SensorUtil.SENSOR_MIC)){
                    File mediaFile = new File(recvDir, Integer.toString(SensorUtil.SENSOR_MIC));
                    mWavFileWriter = new MIME_FileWriter(mediaFile, MIME_FileWriter.Format.wav);
                    String txt = "Create wav file: " + mediaFile.toString();
                    Log.i(TAG, txt);
                    serviceHandler.updateStatusTxt(txt);
                }

                File txtFile;
                for(int type : this.requiredSensors){
                    txtFile= new File(recvDir, Integer.toString(type)+".txt");
                    FileWriter fw = new FileWriter(txtFile);
                    mSensorWriters.put(type, fw);
                }
                //Log.i(TAG, "Create data file: " + f.toString());
            } catch (IOException e) {
                Log.i(TAG, e.toString());
            }
        }
    }

    @Override
    public void stopThread()
    {
        keepRunning = false;
        executorService.shutdown();
    }
}
