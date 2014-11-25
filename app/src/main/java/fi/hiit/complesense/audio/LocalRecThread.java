package fi.hiit.complesense.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.util.MIME_FileWriter;

/**
 * Created by hxguo on 10.10.2014.
 */
public class LocalRecThread extends AbstractSystemThread
{
    public static final String TAG = "LocalRecThread";
    private volatile boolean recording;

    public LocalRecThread(ServiceHandler serviceHandler) {
        super(serviceHandler);
    }

    @Override
    public void run()
    {
        String str = "start LocalRecThread, thread id: " + Thread.currentThread().getId();
        Log.e(TAG, str);
        serviceHandler.updateStatusTxt(str);
        startRecording();

    } // end run

    private void startRecording()
    {
        Log.e(TAG, "start LocalRecThread thread, thread id: " + Thread.currentThread().getId());
        MIME_FileWriter wavFileWriter = null;

        AudioRecord audio_recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, Constants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioRecord.getMinBufferSize(Constants.SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT) * 10);
        int bytes_read = 0;
        int bytes_count = 0;
        byte[] buf = new byte[Constants.BUF_SIZE];
        //byte[] buf = new byte[Constants.NUM_CHANNELS * Constants.SAMPLE_SIZE * Constants.FRAME_SIZE * Constants.SAMPLE_RATE / 1000 ];
        File localFile = new File(Constants.ROOT_DIR, Thread.currentThread().getId() + "-"+ System.currentTimeMillis() +".wav");
        try
        {
            wavFileWriter= new MIME_FileWriter(localFile,MIME_FileWriter.Format.wav);
            if(wavFileWriter==null)
                throw new IOException("wavFileWrite is null");

            long lastCheckMillis = System.currentTimeMillis(), interval = 2000;
            int preByteCount = 0;

            recording = true;
            audio_recorder.startRecording();
            while(recording)
            {
                bytes_read = audio_recorder.read(buf, 0, Constants.BUF_SIZE);
                wavFileWriter.write(buf);
                //getSampleEnergy(buf);
                bytes_count += bytes_read;

                Thread.sleep(Constants.SAMPLE_INTERVAL);
                if(System.currentTimeMillis() - lastCheckMillis > interval )
                {
                    serviceHandler.updateStatusTxt("write " + (bytes_count - preByteCount) + "Bytes to local disk");
                    Log.i(TAG, "bytes_count : " + bytes_count);
                    lastCheckMillis = System.currentTimeMillis();
                    preByteCount = bytes_count;
                }
            }
        }
        catch (InterruptedException ie)
        {
            Log.e(TAG, "Recording thread interrupted");
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        } finally
        {
            Log.e(TAG, "Recording thread stops");
            if(audio_recorder!=null)
                audio_recorder.stop();
            if(wavFileWriter!=null)
                wavFileWriter.close();
        }

    }

    @Override
    public void stopThread()
    {
        String str = "stop LocalRecThread, thread";
        Log.e(TAG, str);
        serviceHandler.updateStatusTxt(str);
        recording = false;
    }

    @Override
    public void pauseThread() {

    }
}
