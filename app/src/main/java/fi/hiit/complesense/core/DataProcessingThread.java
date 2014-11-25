package fi.hiit.complesense.core;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.util.MIME_FileWriter;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 30.10.2014.
 */
public class DataProcessingThread extends AbsSystemThread {

    public static final String TAG = DataProcessingThread.class.getSimpleName();
    private ConcurrentMap<String, List<ByteBuffer>> pendingData = new ConcurrentHashMap<String, List<ByteBuffer>>();
    private ConcurrentMap<String, FileWriter> fileWriters = new ConcurrentHashMap<String, FileWriter>();
    private ConcurrentMap<String, MIME_FileWriter> wavWriters = new ConcurrentHashMap<String, MIME_FileWriter>();

    private ExecutorService executorService = Executors.newFixedThreadPool(2);


    public DataProcessingThread(ServiceHandler serviceHandler) throws IOException
    {
        super(TAG, serviceHandler);
    }

    public void addDataToThreadBuffer(String webSocketStr, byte[] data, int count)
    {
        byte[] dataCopy = new byte[count];
        System.arraycopy(data, 0, dataCopy, 0, count);

        List queue = pendingData.get(webSocketStr);
        if(queue==null)
        {
            queue = new LinkedList();
            queue.add(ByteBuffer.wrap(dataCopy));
            pendingData.put(webSocketStr, queue);
            Log.i(TAG, "New Streaming client: " + webSocketStr);
            executorService.execute(new CreateNewFileRunnable(webSocketStr));
        }
        queue.add(ByteBuffer.wrap(dataCopy));
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
                for(String key : pendingData.keySet())
                {
                    if(!pendingData.get(key).isEmpty())
                    {
                        data = pendingData.get(key).remove(0);
                        if(data !=null && data.hasRemaining()){
                            //Log.i(TAG, "data.remaining(): " + data.remaining());
                            //Log.i(TAG, "remaining(): " + data.remaining());
                            short isStringData = data.getShort();
                            //Log.i(TAG, "isStringData: " + isStringData);

                            if(isStringData == 0){ // Binary data
                                mediaDataType = data.getInt();
                                payloadSize = data.remaining();
                                data.get(wavBuf, 0, payloadSize);

                                if(mediaDataType== SensorUtil.SENSOR_MIC){
                                    //Log.i(TAG, "recv mic data: " + payloadSize);
                                    if(wavWriters.get(key)!=null){
                                        wavWriters.get(key).write(wavBuf, 0, payloadSize);
                                    }else{
                                        Log.w(TAG, "wav file is null");
                                    }
                                }
                                continue;
                            }
                            if(isStringData == 1){ // Data encapsulated in JSON
                                //JSONObject jsonObject = new JSONObject(new String(data.slice().array()));
                                int byteCount = data.remaining();
                                data.get(strBuf,0,byteCount);

                                if(fileWriters.get(key)!=null){
                                    String sensorData =new String(strBuf, 0, byteCount);
                                    //Log.i(TAG, sensorData);
                                    fileWriters.get(key).write(sensorData);
                                    fileWriters.get(key).write('\n');
                                }else{
                                    Log.w(TAG, "data file is null");
                                }
                                //Log.i(TAG, "sensor data: " + new String(buf));
                                continue;
                            }
                        }
                    }
                }
            }

        }catch (IOException e) {
            Log.i(TAG, e.toString());
        } /*catch (JSONException e) {
            Log.i(TAG,e.toString());
        } */finally {
            Log.i(TAG, "Exit loop");
            try {
                if(fos!=null)
                    fos.close();
                for(String key :fileWriters.keySet()){
                    FileWriter fw = fileWriters.remove(key);
                    fw.close();
                }
                for(String key :wavWriters.keySet()){
                    MIME_FileWriter mfw = wavWriters.remove(key);
                    mfw.close();
                }
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

        CreateNewFileRunnable(String webSocketStr){
            this.mWebSocketStr = webSocketStr;
        }

        @Override
        public void run() {
            try {
                String fileName = mWebSocketStr;
                File recvDir = new File(Constants.ROOT_DIR, mWebSocketStr);
                recvDir.mkdirs();
                File txtFile = new File(recvDir, fileName+".txt");
                File mediaFile = new File(recvDir, fileName);


                FileWriter fw = new FileWriter(txtFile);
                MIME_FileWriter mfw = new MIME_FileWriter(mediaFile, MIME_FileWriter.Format.wav);
                fileWriters.put(mWebSocketStr, fw);
                wavWriters.put(mWebSocketStr, mfw);
                serviceHandler.updateStatusTxt("Create data file: " + txtFile.toString()+ " Create wav file: " + mediaFile.toString());
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
