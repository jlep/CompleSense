package fi.hiit.complesense.core;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.audio.WavFileWriter;
import fi.hiit.complesense.connection.AsyncStreamServer;

/**
 * Created by hxguo on 30.10.2014.
 */
public class DataProcessingThread extends AbsSystemThread {

    public static final String TAG = DataProcessingThread.class.getSimpleName();
    private final AsyncStreamServer asyncStreamServer;
    private ConcurrentMap<SocketChannel, List<ByteBuffer>> pendingData = new ConcurrentHashMap<SocketChannel, List<ByteBuffer>>();

    public DataProcessingThread(AsyncStreamServer asyncStreamServer,
                                ServiceHandler serviceHandler) throws IOException
    {
        super(TAG, serviceHandler);
        this.asyncStreamServer = asyncStreamServer;
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
            Log.i(TAG, "new Connection from: " + socketChannel.socket().getRemoteSocketAddress());
        }
        queue.add(ByteBuffer.wrap(dataCopy));
    }

    @Override
    public void run()
    {
        Log.i(TAG, "Start DataProcessingThread at thread: " + Thread.currentThread().getId());

        int payloadSize = 0, len = 0;
        FileOutputStream fos = null;
        File tempFile = null;
        ByteBuffer data;
        try
        {
            File recvDir = new File(Constants.ROOT_DIR, "recv");
            recvDir.mkdirs();

            tempFile = new File(recvDir, Long.toString(System.currentTimeMillis())+ ".raw");
            WavFileWriter.writeHeader(tempFile);
            fos = new FileOutputStream(tempFile,true);
            String jsonString = null;


            while(keepRunning)
            {
                for(SocketChannel key : pendingData.keySet())
                {
                    if(!pendingData.get(key).isEmpty())
                    {
                        data = pendingData.get(key).remove(0);
                        if(data.hasRemaining())
                        {
                            int isJSON = data.getInt();
                            if(data.getInt() == 0)
                            {
                                // Binary data
                                int type = data.getInt();
                                payloadSize += data.remaining();
                                fos.write(data.slice().array());
                                continue;
                            }
                            if(data.getInt() == 1)
                            {
                                // Data encapsulated in JSON
                                JSONObject jsonObject = new JSONObject(new String(data.slice().array()));
                                Log.i(TAG, "data: " + key.socket().toString() + jsonObject.toString());
                                continue;
                            }

                            //Log.i(TAG, "SocketChannel: "+ key.socket().getRemoteSocketAddress() +" len: " + data.length);
                        }
                    }
                }
            }

        }catch (IOException e) {
            Log.i(TAG, e.toString());
        } catch (JSONException e) {
            Log.i(TAG,e.toString());
        } finally {
            Log.i(TAG, "Exit loop");
            try {
                if(fos!=null)
                    fos.close();
                WavFileWriter.close(tempFile, payloadSize, 0);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }



    }

    @Override
    public void stopThread()
    {
        keepRunning = false;
    }
}
