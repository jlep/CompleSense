package fi.hiit.complesense.connection;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;

import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 30.10.2014.
 */
public class DataProcessingThread extends AbsSystemThread {

    public static final String TAG = DataProcessingThread.class.getSimpleName();
    private Pipe.SourceChannel sourceChannel;

    public DataProcessingThread(ServiceHandler serviceHandler, Pipe.SourceChannel sourceChannel) throws IOException
    {
        super(TAG, serviceHandler);
        this.sourceChannel = sourceChannel;
    }

    @Override
    public void run()
    {
        ByteBuffer sizeb = ByteBuffer.allocate(Integer.SIZE/8);
        ByteBuffer bb = ByteBuffer.allocate(AbsAsyncIO.BUF_SIZE);
        byte[] data;
        try
        {
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
                    //Log.i(TAG, "len: " + len + " position: " + bb.position());
                    bb.clear();
                    while(bb.position() < len)
                        sourceChannel.read(bb);
                    bb.flip();
                    //Log.i(TAG, "position: " + bb.position() + " limit: " + bb.limit());

                    data = new byte[len];
                    bb.get(data,0, len);
                    Log.i(TAG, new String(data));

                }


            }

        }catch (IOException e) {
            Log.i(TAG, e.toString());
        } finally {
            try {
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
    }
}
