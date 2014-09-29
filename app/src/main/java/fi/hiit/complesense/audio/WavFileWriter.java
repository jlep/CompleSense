package fi.hiit.complesense.audio;

import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 29.9.2014.
 */
public class WavFileWriter
{
    private static final String TAG = "ExtRecorder";
    private final String outputFile;
    private RandomAccessFile randomAccessWriter;
    private byte[] buffer;
    private int payloadSize;

    private WavFileWriter(ServiceHandler serviceHandler, String outputFile)
            throws FileNotFoundException
    {

        this.outputFile = outputFile;
        randomAccessWriter = new RandomAccessFile(this.outputFile, "rw");
    }

    public static WavFileWriter getWriter(ServiceHandler serviceHandler, String outputFile)
    {
        WavFileWriter wavFileWriter = null;
        try
        {
            wavFileWriter = new WavFileWriter(serviceHandler, outputFile);
            wavFileWriter.prepare();
        }
        catch (FileNotFoundException e)
        {
            Log.i(TAG, e.toString() );
        } catch (IOException e) {
            Log.i(TAG, e.toString() );
        }

        return wavFileWriter;
    }


    private void prepare()
            throws IOException
    {
        randomAccessWriter = new RandomAccessFile(outputFile, "rw");
        payloadSize = 0;
        randomAccessWriter.setLength(0); // Set file length to 0, to prevent unexpected behavior in case the file already existed
        randomAccessWriter.writeBytes("RIFF");
        randomAccessWriter.writeInt(0); // Final file size not known yet, write 0
        randomAccessWriter.writeBytes("WAVE");
        randomAccessWriter.writeBytes("fmt ");
        randomAccessWriter.writeInt(Integer.reverseBytes(16)); // Sub-chunk size, 16 for PCM
        randomAccessWriter.writeShort(Short.reverseBytes((short) 1)); // AudioFormat, 1 for PCM
        randomAccessWriter.writeShort(Short.reverseBytes(Constants.NUM_CHANNELS));// Number of channels, 1 for mono, 2 for stereo
        randomAccessWriter.writeInt(Integer.reverseBytes(Constants.SAMPLE_RATE)); // Sample rate
        randomAccessWriter.writeInt(Integer.reverseBytes(Constants.SAMPLE_RATE * Constants.BIT_SAMPLE * Constants.NUM_CHANNELS / 8)); // Byte rate, SampleRate*NumberOfChannels*BitsPerSample/8
        randomAccessWriter.writeShort(Short.reverseBytes((short) (Constants.NUM_CHANNELS * Constants.BIT_SAMPLE / 8))); // Block align, NumberOfChannels*BitsPerSample/8
        randomAccessWriter.writeShort(Short.reverseBytes(Constants.BIT_SAMPLE)); // Bits per sample
        randomAccessWriter.writeBytes("data");
        randomAccessWriter.writeInt(0); // Data chunk size not known yet, write 0

        //framePeriod = Constants.SAMPLE_RATE * TIMER_INTERVAL / 1000;
        //buffer = new byte[framePeriod*Constants.BIT_SAMPLE/8*Constants.NUM_CHANNELS];
        buffer = new byte[Constants.BUF_SIZE];
    }

    public String getOutputFile()
    {
        return this.outputFile;
    }



    public void write(byte[] data)
    {
        try {
            randomAccessWriter.write(data);
            payloadSize += buffer.length;
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        }
    }

    public void close()
    {
        try
        {
            randomAccessWriter.seek(4); // Write size to RIFF header
            randomAccessWriter.writeInt(Integer.reverseBytes(36 + payloadSize));

            randomAccessWriter.seek(40); // Write size to Subchunk2Size field
            randomAccessWriter.writeInt(Integer.reverseBytes(payloadSize));

            randomAccessWriter.close();

            Log.i(TAG,"stop(payloadSize = "+ payloadSize +")");

        }
        catch(IOException e)
        {
            Log.e(TAG,"I/O exception occured while closing output file");
        }
    }
}
