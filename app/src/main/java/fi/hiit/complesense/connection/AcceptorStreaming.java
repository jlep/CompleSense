package fi.hiit.complesense.connection;

import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.WebSocket;
import com.koushikdutta.async.http.libcore.RequestHeaders;
import com.koushikdutta.async.http.server.AsyncHttpServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.DataProcessingThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.ui.TakePhotoActivity;
import fi.hiit.complesense.util.SensorUtil;
import fi.hiit.complesense.util.SystemUtil;

/**

 * Created by hxguo on 27.11.2014.
 */
public class AcceptorStreaming extends AbsSystemThread implements CompletedCallback {
    public static final String TAG = AcceptorStreaming.class.getSimpleName();

    private final int mStreamPort;
    private final CountDownLatch latch;
    private final WebSocket mWebSocket;
    private DataProcessingThread mWavProcessThread = null;
    private DataProcessingThread mJsonProcessThread = null;
    private AsyncHttpServer httpServer = new AsyncHttpServer();

    public AcceptorStreaming(ServiceHandler serviceHandler, Set<Integer> types, int serverIndex, WebSocket webSocket, CountDownLatch latch) throws IOException
    {
        super(TAG, serviceHandler);

        Set<Integer> sensorTypes = new HashSet<Integer>(types);
        this.latch = latch;
        mStreamPort = Constants.STREAM_SERVER_PORT + serverIndex;
        httpServer.setErrorCallback(this);
        mWebSocket = webSocket;

        if(sensorTypes.remove(SensorUtil.SENSOR_MIC)){
            Set<Integer> wav = new HashSet<Integer>();
            wav.add(SensorUtil.SENSOR_MIC);

            mWavProcessThread = new DataProcessingThread(serviceHandler, wav);
            StreamingCallback wavStreamingCallback = new StreamingCallback(mWavProcessThread, webSocket);
            httpServer.websocket("/streaming_wav", Constants.WEB_PROTOCOL, wavStreamingCallback);
        }

        if(sensorTypes.remove(SensorUtil.SENSOR_CAMERA)){
            Set<Integer> cam = new HashSet<Integer>();
            cam.add(SensorUtil.SENSOR_CAMERA);

            ImageCallback imageCallback = new ImageCallback(webSocket);
            httpServer.websocket("/streaming_images", Constants.WEB_PROTOCOL, imageCallback);
        }

        if(sensorTypes.size()>0){
            mJsonProcessThread = new DataProcessingThread(serviceHandler, sensorTypes);
            StreamingCallback jsonStreamingCallback = new StreamingCallback(mJsonProcessThread, webSocket);
            httpServer.websocket("/streaming_json", Constants.WEB_PROTOCOL, jsonStreamingCallback);
        }
    }

    @Override
    public void stopThread()
    {
        if(httpServer!=null)
            httpServer.stop();
        if(mWavProcessThread != null)
            mWavProcessThread.stopThread();
        if(mJsonProcessThread != null)
            mJsonProcessThread.stopThread();
    }

    @Override
    public void run()
    {
        String txt = "Start AcceptorStreaming at port "+ mStreamPort +" thread id: " + Thread.currentThread().getId();
        Log.e(TAG, txt);
        serviceHandler.updateStatusTxt(txt);
        serviceHandler.workerThreads.put(TAG + ":" +mWebSocket.toString(), this);

        httpServer.listen(mStreamPort);
        if(mWavProcessThread != null)
            mWavProcessThread.start();
        if(mJsonProcessThread != null)
            mJsonProcessThread.start();
        latch.countDown();
    }



    @Override
    public void onCompleted(Exception e) {
        Log.e(TAG, "AcceptorStreaming setup fails: " + e.toString());
        stopThread();
    }

    public int getmStreamPort() {
        return mStreamPort;
    }

    class StreamingCallback implements AsyncHttpServer.WebSocketRequestCallback{

        private final DataProcessingThread mDataProcessThread;
        private WebSocket mWebSocket;
        private final WebSocket cmdWebSocket;

        public StreamingCallback(DataProcessingThread dataProcessThread, WebSocket webSocket) {
            this.mDataProcessThread = dataProcessThread;
            this.cmdWebSocket = webSocket;
        }

        @Override
        public void onConnected(WebSocket webSocket, RequestHeaders requestHeaders) {
            String txt = "onConnected() webSocket: " + SystemUtil.formatWebSocketStr(webSocket);
            Log.i(TAG, txt);
            serviceHandler.updateStatusTxt(txt);

            mWebSocket = webSocket;
            //Use this to clean up any references to your websocket
            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    try {
                        if (ex != null)
                            Log.e(TAG, ex.toString());
                    } finally {
                        if(mWebSocket!=null)
                            mWebSocket.close();
                        if(mDataProcessThread!=null && mDataProcessThread.isAlive()){
                            mDataProcessThread.stopThread();
                        }

                    }
                }
            });

            mWebSocket.setStringCallback(new WebSocket.StringCallback() {
                @Override
                public void onStringAvailable(String s) {
                    Log.i(TAG, "Streaming server should not recv String: " + s);
                }
            });

            mWebSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter,
                                            ByteBufferList byteBufferList) {
                    ByteBuffer[] data = byteBufferList.getAllArray();
                    int payloadSize = 0;

                    for (ByteBuffer bb : data) {
                        payloadSize += bb.remaining();
                        mDataProcessThread.addDataToThreadBuffer(cmdWebSocket, bb.array(), payloadSize);
                    }
                    byteBufferList.recycle();
                }
            });
        }
    }

    class ImageCallback implements AsyncHttpServer.WebSocketRequestCallback
    {
        private final WebSocket cmdWebSocket;
        private Map<String, FileOutputStream> oss = new HashMap<String, FileOutputStream>();
        private int payloadSize = 0;
        private ExecutorService threadPools = Executors.newFixedThreadPool(5);


        public ImageCallback(WebSocket webSocket) {
            this.cmdWebSocket = webSocket;
        }

        @Override
        public void onConnected(final WebSocket webSocket, RequestHeaders requestHeaders) {
            String txt = "onConnected() webSocket: " + SystemUtil.formatWebSocketStr(webSocket);
            Log.i(TAG, txt);
            serviceHandler.updateStatusTxt(txt);

            //Use this to clean up any references to your websocket
            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception ex) {
                    try {
                        if (ex != null)
                            Log.e(TAG, ex.toString());
                    } finally {
                        if(webSocket!=null)
                            webSocket.close();
                    }
                }
            });

            webSocket.setStringCallback(new WebSocket.StringCallback() {
                @Override
                public void onStringAvailable(String s) {
                    try {
                        final JSONObject jsonObject = new JSONObject(s);
                        String imgCommand = jsonObject.getString(JsonSSI.IMAGE_COMMAND);
                        if(imgCommand.equals(JsonSSI.START_SEND_IMG)){
                            final File recvDir = new File(Constants.ROOT_DIR, SystemUtil.formatWebSocketStr(cmdWebSocket));
                            recvDir.mkdirs();

                            final String imageName = jsonObject.getString(JsonSSI.IMAGE_NAME);
                            String txt = "Receive imgName: " + imageName;
                            Log.i(TAG, txt);
                            serviceHandler.updateStatusTxt(txt);

                            JSONArray orientationsJson = jsonObject.getJSONArray(JsonSSI.IMAGE_ORIENTATIONS);
                            File orientationsFile = new File(recvDir, SensorUtil.SENSOR_CAMERA+".txt");
                            FileWriter fw = null;
                            FileOutputStream fos = null;

                            try {
                                fw = new FileWriter(orientationsFile, true);
                                fw.write(imageName + ",");
                                fw.write(orientationsJson.toString() + "\n");

                                File outputFile = new File(recvDir, imageName);
                                Log.i(TAG, "outputFile: " + outputFile.toString());

                                fos = oss.get(webSocket.toString());
                                if(fos == null){
                                    fos = new FileOutputStream(outputFile, true);
                                    oss.put(webSocket.toString(), fos);
                                }

                                sendOkToSend(webSocket, imageName);

                            } catch (IOException e) {
                                Log.i(TAG, e.toString());
                                oss.remove(webSocket.toString());
                            }finally {
                                if(fw !=null){
                                    try {
                                        fw.close();
                                    } catch (IOException e) {
                                    }
                                }
                            }
                        }

                        if(imgCommand.equals(JsonSSI.COMPLETE_SEND_IMG)){
                            Log.i(TAG, "Image send completes");
                            OutputStream os = oss.remove(webSocket.toString());
                            if(os!=null){
                                try {
                                    os.close();
                                } catch (IOException e) {
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Log.i(TAG, e.toString());
                    }

                }
            });

            webSocket.setDataCallback(new DataCallback() {
                @Override
                public void onDataAvailable(DataEmitter dataEmitter,
                                            ByteBufferList byteBufferList) {
                    payloadSize += byteBufferList.remaining();
                    try {
                        OutputStream os = oss.get(webSocket.toString());
                        if(os!=null)
                            ByteBufferList.writeOutputStream(os, byteBufferList.getAll());
                    } catch (IOException e) {
                        Log.i(TAG, e.toString());
                    }
                    byteBufferList.recycle();
                }
            });

            webSocket.setClosedCallback(new CompletedCallback() {
                @Override
                public void onCompleted(Exception e) {
                    OutputStream os = oss.remove(webSocket.toString());
                    if(os!=null){
                        try {
                            os.close();
                        } catch (IOException ex) {
                        }
                    }
                }
            });
        }

        private void sendOkToSend(WebSocket webSocket, String imageName) throws JSONException {
            JSONObject okSend = new JSONObject();
            okSend.put(JsonSSI.IMAGE_COMMAND, JsonSSI.OK_TO_SEND);
            okSend.put(JsonSSI.IMAGE_NAME, imageName);
            webSocket.send(okSend.toString());
        }
    }


}
