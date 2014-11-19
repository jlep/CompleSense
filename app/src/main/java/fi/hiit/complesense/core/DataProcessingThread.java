package fi.hiit.complesense.core;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AsyncStreamServer;
import fi.hiit.complesense.util.MIME_FileWriter;

/**
 * Created by hxguo on 30.10.2014.
 */
public class DataProcessingThread extends AbsSystemThread {

    public static final String TAG = DataProcessingThread.class.getSimpleName();
    private final AsyncStreamServer asyncStreamServer;
    private final File recvDir;
    private ConcurrentMap<SocketChannel, List<ByteBuffer>> pendingData = new ConcurrentHashMap<SocketChannel, List<ByteBuffer>>();
    private ConcurrentMap<SocketChannel, FileWriter> fileWriters = new ConcurrentHashMap<SocketChannel, FileWriter>();
    private ConcurrentMap<SocketChannel, MIME_FileWriter> wavWriters = new ConcurrentHashMap<SocketChannel, MIME_FileWriter>();

    private ExecutorService executorService = Executors.newFixedThreadPool(2);


    public DataProcessingThread(AsyncStreamServer asyncStreamServer,
                                ServiceHandler serviceHandler) throws IOException
    {
        super(TAG, serviceHandler);
        this.asyncStreamServer = asyncStreamServer;
        this.recvDir = new File(Constants.ROOT_DIR, "recv");
        recvDir.mkdirs();

    }

    public void addDataToThreadBuffer(SocketChannel socketChannel, byte[] data, int count)
    {
        byte[] dataCopy = new byte[count];
        System.arraycopy(data, 0, dataCopy, 0, count);

        List queue = pendingData.get(socketChannel);
        if(queue==null)
        {
            queue = new LinkedList();
            queue.add(ByteBuffer.wrap(dataCopy));
            pendingData.put(socketChannel, queue);
            Log.i(TAG, "New Streaming client: " + socketChannel.socket().getRemoteSocketAddress());
            executorService.execute(new CreateNewFileRunnable(socketChannel));
        }
        queue.add(ByteBuffer.wrap(dataCopy));
    }

    @Override
    public void run()
    {
        Log.i(TAG, "Start DataProcessingThread at thread: " + Thread.currentThread().getId());

        FileOutputStream fos = null;
        ByteBuffer data;
        byte[] wavBuf = new byte[Constants.BUF_SIZE];
        int mediaDataType, payloadSize;
        try
        {
            while(keepRunning)
            {
                for(SocketChannel key : pendingData.keySet())
                {
                    if(!pendingData.get(key).isEmpty())
                    {
                        data = pendingData.get(key).remove(0);
                        if(data.hasRemaining()){
                            //Log.i(TAG, "remaining(): " + data.remaining());
                            int sz = data.getInt();
                            short isJson = data.getShort();

                            if(isJson == 0){ // Binary data
                                mediaDataType = data.getInt();
                                Log.i(TAG, "sz: " + sz + ", isJson: " + isJson + " remaining(): " + data.remaining() + " sent: " + (sz-Constants.BYTES_SHORT-Constants.BYTES_INT));

                                payloadSize = data.remaining();
                                data.get(wavBuf, 0, payloadSize);

                                if(wavWriters.get(key)!=null){
                                    wavWriters.get(key).write(wavBuf, 0, payloadSize);
                                }else{
                                    Log.w(TAG, "wav file is null");
                                }
                                continue;
                            }
                            if(isJson == 1){ // Data encapsulated in JSON
                                Log.i(TAG, "sz: " + sz + ", isJson: " + isJson + " remaining(): " + data.remaining() + " sent: " + (sz-Constants.BYTES_SHORT));
                                //JSONObject jsonObject = new JSONObject(new String(data.slice().array()));
                                byte[] strBuf = new byte[sz-Constants.BYTES_SHORT];
                                data.get(strBuf);

                                if(fileWriters.get(key)!=null){
                                    fileWriters.get(key).write(new String(strBuf));
                                    fileWriters.get(key).write('\n');
                                }else{
                                    Log.w(TAG, "data file is null");
                                }
                                //Log.i(TAG, "sensor data: " + new String(buf));
                                continue;
                            }
                            //Log.i(TAG, "SocketChannel: "+ key.socket().getRemoteSocketAddress() +" len: " + data.length);
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
                for(SocketChannel sc :fileWriters.keySet()){
                    FileWriter fw = fileWriters.remove(sc);
                    fw.close();
                }
                for(SocketChannel sc :wavWriters.keySet()){
                    MIME_FileWriter mfw = wavWriters.remove(sc);
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

        private final SocketChannel socketChannel;

        CreateNewFileRunnable(SocketChannel socketChannel){
            this.socketChannel = socketChannel;
        }

        @Override
        public void run() {
            try {
                String fileName = socketChannel.socket().getRemoteSocketAddress().toString();
                File txtFile = new File(recvDir, fileName+".txt");
                File mediaFile = new File(recvDir, fileName);


                FileWriter fw = new FileWriter(txtFile);
                MIME_FileWriter mfw = new MIME_FileWriter(mediaFile, MIME_FileWriter.Format.wav);
                fileWriters.put(socketChannel, fw);
                wavWriters.put(socketChannel, mfw);
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
