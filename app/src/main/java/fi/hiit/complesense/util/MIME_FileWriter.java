package fi.hiit.complesense.util;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import fi.hiit.complesense.Constants;

/**
 * Created by hxguo on 18.11.2014.
 */
public class MIME_FileWriter
{
    private static final String TAG = MIME_FileWriter.class.getSimpleName();
    private final File file;
    private final Format format;
    private int payloadSize;

    public enum Format{
        wav, mpg4;
    }

    private final RandomAccessFile randomAccessWriter;

    public MIME_FileWriter(File file, Format format) throws IOException
    {
        this.file = file;
        this.format = format;
        this.randomAccessWriter = new RandomAccessFile(file, "rw");
        payloadSize = 0;
        prepare();
    }

    public void writeHeader() throws IOException {
        switch (format){
            case wav:
                prepare();
                break;
            case mpg4:
                Log.i(TAG, "Writing mpg4 file not implemented");
                break;
            default:
                break;
        }

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
            payloadSize += data.length;
        } catch (IOException e) {
            Log.i(TAG,e.toString());
        }
    }

    public void write(byte[] data, int offset, int byteCount)
    {
        try {
            randomAccessWriter.write(data,offset, byteCount);
            payloadSize += data.length;
        } catch (IOException e) {
            Log.i(TAG,e.toString());
        }
    }

    public void close()
    {
        Log.i(TAG, "close(inputFile: " + file.toString() + ")");
        try
        {
            if(format==Format.wav)
            {
                if(file.exists() && file.isFile()){
                    randomAccessWriter.seek(4); // Write size to RIFF header
                    randomAccessWriter.writeInt(Integer.reverseBytes(36 + payloadSize));

                    randomAccessWriter.seek(40); // Write size to Subchunk2Size field
                    randomAccessWriter.writeInt(Integer.reverseBytes(payloadSize));
                    randomAccessWriter.close();

                    String newFilename = file.toString().substring(file.toString().lastIndexOf('/')+1)+".wav";

                    //Log.i(TAG,"stop(oldFile.length(): " +oldFile.length() +")");
                    File newFile = new File(file.getParent(), newFilename);
                    file.renameTo(newFile);
                    Log.i(TAG,"close(" +newFilename + " size: " + payloadSize +")");
                }
            }else{
                randomAccessWriter.close();
            }
        }catch(IOException e){
            Log.e(TAG, "I/O exception occured while closing output file");
        }

    }


}
