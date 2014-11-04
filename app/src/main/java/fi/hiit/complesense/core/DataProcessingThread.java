package fi.hiit.complesense.core;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.Pipe;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.audio.WavFileWriter;
import fi.hiit.complesense.connection.AbsAsyncIO;

/**
 * Created by hxguo on 30.10.2014.
 */
public class DataProcessingThread extends AbsSystemThread {

    public static final String TAG = DataProcessingThread.class.getSimpleName();
    private Pipe.SourceChannel sourceChannel;
    private ConcurrentMap<SocketChannel, List<ByteBuffer>> pendingData = new ConcurrentHashMap<SocketChannel, List<ByteBuffer>>();

    public DataProcessingThread(ServiceHandler serviceHandler, Pipe.SourceChannel sourceChannel) throws IOException
    {
        super(TAG, serviceHandler);
        this.sourceChannel = sourceChannel;

    }

    public void processData(SocketChannel socketChannel, byte[] data, int count)
    {
        List queue = pendingData.get(socketChannel);
        if(queue==null)
        {
            queue = new ArrayList();
            queue.add(ByteBuffer.wrap(data));
            pendingData.put(socketChannel, queue);
        }
        queue.add(ByteBuffer.wrap(data));
    }

    @Override
    public void run()
    {
        Log.i(TAG, "Start DataProcessingThread at thread: " + Thread.currentThread().getId());
        ByteBuffer sizeb = ByteBuffer.allocate(Integer.SIZE/8);
        ByteBuffer bb = ByteBuffer.allocate(AbsAsyncIO.BUF_SIZE);
        byte[] data;
        FileOutputStream fos = null;
        File tempFile = null;
        int payloadSize = 0;
        try
        {
            File recvDir = new File(Constants.ROOT_DIR, "recv");
            recvDir.mkdirs();

            tempFile = new File(recvDir, Long.toString(System.currentTimeMillis())+ ".raw");
            WavFileWriter.writeHeader(tempFile);
            fos = new FileOutputStream(tempFile,true);
            int len;
            while(keepRunning)
            {
                sizeb.clear();
                while(sizeb.hasRemaining())
                    sourceChannel.read(sizeb);

                sizeb.flip();
                len = sizeb.getInt();
                if(len > 0)
                {
                    bb.clear();
                    //Log.i(TAG, "len: " + len + " position: " + bb.position());
                    while(bb.position() < len)
                        sourceChannel.read(bb);
                    bb.flip();
                    //Log.i(TAG, "position: " + bb.position() + " limit: " + bb.limit());

                    data = new byte[len];
                    bb.get(data,0, len);
                    fos.write(data);
                    payloadSize += len;
                    //Log.i(TAG, new String(data));
                }
            }

        }catch (IOException e) {
            Log.i(TAG, e.toString());
        } finally {
            Log.i(TAG, "Exit loop");
            try {
                if(fos!=null)
                    fos.close();
                WavFileWriter.close(tempFile, payloadSize, 0);
                sourceChannel.close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    @Override
    public void stopThread()
    {
        keepRunning = false;
        try {
            sourceChannel.close();
        } catch (IOException e) {
        }
    }
}
