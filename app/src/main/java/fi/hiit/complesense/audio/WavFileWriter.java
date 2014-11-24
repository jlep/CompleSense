package fi.hiit.complesense.audio;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 29.9.2014.
 */
public class WavFileWriter
{
    private static final String TAG = "WavFileWriter";
    private final File file;
    private final RandomAccessFile randomAccessWriter;
    public static WavFileWriter instance;
    private int payLoadSize = 0;

    private WavFileWriter(File file) throws FileNotFoundException
    {
        this.file = file;
        this.randomAccessWriter = new RandomAccessFile(file, "rw");
    }

    public static WavFileWriter getInstance(File file)
    {
        instance = null;
        try {
            instance = new WavFileWriter(file);
            instance.prepare();
        } catch (FileNotFoundException e) {
            Log.i(TAG, e.toString());
            return null;
        } catch (IOException e) {
            Log.i(TAG, e.toString());
            return null;
        }
        return instance;
    }

    private void prepare() throws IOException
    {
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
    }

    public void write(byte[] data)
    {
        try {
            randomAccessWriter.write(data);
            payLoadSize += data.length;
        } catch (IOException e) {
            Log.i(TAG,e.toString());
        }
    }

    public void write(byte[] data, int offset, int byteCount)
    {
        try {
            randomAccessWriter.write(data,offset, byteCount);
            payLoadSize += data.length;
        } catch (IOException e) {
            Log.i(TAG,e.toString());
        }
    }

    public int getPayLoadSize()
    {
        return payLoadSize;
    }

    public void close()
    {
        try {
            randomAccessWriter.seek(4); // Write size to RIFF header
            randomAccessWriter.writeInt(Integer.reverseBytes(36 + payLoadSize));

            randomAccessWriter.seek(40); // Write size to Subchunk2Size field
            randomAccessWriter.writeInt(Integer.reverseBytes(payLoadSize));
            randomAccessWriter.close();
            Log.i(TAG,"close(" + file.toString() + " size: " + payLoadSize +")");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public static void writeHeader(File outputFile)
            throws IOException
    {
        RandomAccessFile randomAccessWriter = new RandomAccessFile(outputFile, "rw");
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
        randomAccessWriter.close();
    }

    public static void close(File inputFile, int payloadSize, long recStartTime)
    {
        try
        {
            Log.i(TAG,"stop(inputFile: " + inputFile.toString() +")");
            if(inputFile.exists() && inputFile.isFile())
            {
                RandomAccessFile randomAccessWriter = new RandomAccessFile(inputFile, "rw");
                randomAccessWriter.seek(4); // Write size to RIFF header
                randomAccessWriter.writeInt(Integer.reverseBytes(36 + payloadSize));

                randomAccessWriter.seek(40); // Write size to Subchunk2Size field
                randomAccessWriter.writeInt(Integer.reverseBytes(payloadSize));
                randomAccessWriter.close();

                String outputFileStr = inputFile.toString();
                String threadId = outputFileStr.substring(outputFileStr.lastIndexOf("/")+1, outputFileStr.lastIndexOf(".raw"));

                String newFilename = threadId + "-" +Long.toString(recStartTime) + ".wav";

                //Log.i(TAG,"stop(oldFile.length(): " +oldFile.length() +")");
                File newFile = new File(inputFile.getParent(), newFilename);
                inputFile.renameTo(newFile);


                Log.i(TAG,"static close(" +newFilename + " size: " + payloadSize +")");
            }
        }
        catch(IOException e)
        {
            Log.e(TAG,"I/O exception occured while closing output file");
        }
    }

    public FileChannel getFileChannel()
    {
        return (randomAccessWriter==null)?null:randomAccessWriter.getChannel();
    }
}
