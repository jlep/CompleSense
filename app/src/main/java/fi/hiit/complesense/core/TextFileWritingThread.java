package fi.hiit.complesense.core;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import fi.hiit.complesense.Constants;

/**
 * Created by hxguo on 24.11.2014.
 */
public class TextFileWritingThread extends AbsSystemThread
{
    private static final String TAG = TextFileWritingThread.class.getSimpleName();
    private final FileWriter fw;
    private final File outputFile;
    private ConcurrentLinkedQueue<String> buffer = new ConcurrentLinkedQueue<String>();

    public TextFileWritingThread(ServiceHandler serviceHandler, File outputFile) throws IOException {
        super(TAG, serviceHandler);
        this.outputFile = outputFile;
        this.fw = new FileWriter(outputFile);
    }

    public void write(String str){
        //Log.i(TAG, "write(): " +str);
        buffer.offer(str);
    }


    @Override
    public void run() {
        Log.i(TAG, "Starts TextFileWritingThread @thread id: " + Thread.currentThread().getId());
        serviceHandler.workerThreads.put(TAG, this);
        OutputStream os = null;

        try {
            os = new FileOutputStream(outputFile);
            keepRunning = true;
            while(keepRunning){
                String s = buffer.poll();
                if(s!=null){
                    fw.write(s);
                    //Log.i(TAG, s);
                }
            }
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        } finally {
            Log.i(TAG, "TextFileWritingThread stops");
            if(os!=null){
                try {
                    os.close();
                } catch (IOException e) { }
            }
        }
    }

    @Override
    public void stopThread() {
        keepRunning = false;
    }
}
