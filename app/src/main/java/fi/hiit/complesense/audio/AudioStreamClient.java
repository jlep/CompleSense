package fi.hiit.complesense.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.Callable;

import fi.hiit.complesense.Constants;

/**
 * Created by hxguo on 29.10.2014.
 */
public class AudioStreamClient implements Callable<Integer>
{
    private final long groupOwnerThreadId;
    private volatile boolean recording = false;
    private ByteBuffer buffer = ByteBuffer.allocate(Constants.BUF_SIZE);
    private WavFileWriter wavFileWriter;

    public AudioStreamClient(long groupOwnerThreadId)
    {
        this.groupOwnerThreadId = groupOwnerThreadId;
    }


    @Override
    public Integer call() throws Exception
    {
        wavFileWriter= WavFileWriter.getInstance(Constants.ROOT_DIR + groupOwnerThreadId + ".wav");
        if(wavFileWriter==null)
            throw new IOException("wavFileWrite is null");

        FileChannel fileChannel = wavFileWriter.getFileChannel();

        AudioRecord audio_recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, Constants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioRecord.getMinBufferSize(Constants.SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT) * 10);


        int bytes_read = 0;
        int bytes_count = 0;
        //byte[] buf = new byte[Constants.BUF_SIZE];

        recording = true;
        audio_recorder.startRecording();
        while(recording)
        {
            buffer.clear();
            bytes_read = audio_recorder.read(buffer, Constants.BUF_SIZE);
            buffer.flip();
            fileChannel.write(buffer);
            //getSampleEnergy(buf);
            bytes_count += bytes_read;

            Thread.sleep(Constants.SAMPLE_INTERVAL);
        }

        fileChannel.close();
        return bytes_count;
    }

    public void stopThread()
    {
        recording = false;
    }
}
