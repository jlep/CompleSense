package fi.hiit.complesense.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.provider.Settings;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.json.JsonSSI;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 29.10.2014.
 */
public class AudioStreamClient extends AbsSystemThread
{
    public static final String TAG = AudioStreamClient.class.getSimpleName();

    private final WebSocket mWebSocket;
    private final short isStringData = 0;
    private final boolean readLocal;
    private final PipedOutputStream mPipedOut;
    private final PipedInputStream mPipedIn;
    private final long delay;

    public AudioStreamClient(ServiceHandler serviceHandler, WebSocket webSocket, long delay, boolean readFromLocal) throws IOException {
        super(TAG, serviceHandler);
        this.mWebSocket = webSocket;
        this.readLocal = readFromLocal;
        this.mPipedOut = new PipedOutputStream();
        this.mPipedIn = new PipedInputStream(mPipedOut);
        this.delay = delay;
    }

    @Override
    public void run()
    {
        long threadId = Thread.currentThread().getId();
        Log.i(TAG, "Starts AudioStreamClient @thread id: " + threadId);
        serviceHandler.workerThreads.put(AudioStreamClient.TAG, this);

        int bytes_read = 0, payloadSize = 0, bytes_count = 0;
        byte[] buf = new byte[Constants.BUF_SIZE];
        ByteBuffer bb = ByteBuffer.allocate(Constants.BYTES_SHORT + Constants.BYTES_INT +  Constants.BUF_SIZE);

        final File localDir = new File(Constants.ROOT_DIR, Constants.LOCAL_SENSOR_DATA_DIR);
        localDir.mkdirs();
        String fileName = String.format("%d-%d", (System.currentTimeMillis() + delay), threadId);
        final File localFile = new File(localDir, fileName);
        sendFileName2Master(fileName);

        AudioFileWritingThread audioFileWriter = new AudioFileWritingThread(serviceHandler, mPipedIn, localFile);
        audioFileWriter.start();

        keepRunning = true;
        if(!readLocal)
        {
            AudioRecord audio_recorder = null;
            try
            {
                audio_recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                        Constants.SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        AudioRecord.getMinBufferSize(
                                Constants.SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT) * 10);

                audio_recorder.startRecording();
                while(keepRunning){
                    bytes_read = audio_recorder.read(buf, 0, Constants.BUF_SIZE);
                    mPipedOut.write(buf, 0, bytes_read);

                    bb.clear();
                    bb.putShort(isStringData);
                    bb.putInt(SensorUtil.SENSOR_MIC);
                    bb.put(buf);
                    bytes_count += bytes_read;

                    mWebSocket.send(bb.array());
                    Thread.sleep(Constants.SAMPLE_INTERVAL);
                }

            } catch (InterruptedException e) {
                Log.i(TAG, e.toString());
            } catch (IOException e) {
                Log.i(TAG, e.toString());
            } finally{
                Log.i(TAG, "Recording thread stops");
                if(audio_recorder!=null)
                    audio_recorder.stop();
                if(audioFileWriter!=null)
                    audioFileWriter.stopThread();

            }
        }
        else
        {
            File testDir = new File(Constants.ROOT_DIR, "test_rec");
            File[] files= testDir.listFiles();
            FileInputStream fis = null;
            try {
                for(File f : files){
                    fis = new FileInputStream(f);
                    Log.i(TAG, "Reading file: " + f.toString());
                    while(keepRunning){
                        bb.clear();
                        if(fis.available()>0){
                            bytes_read = fis.read(buf);
                            mPipedOut.write(buf);
                            bytes_count += bytes_read;

                            bb.putShort(isStringData);
                            bb.putInt(SensorUtil.SENSOR_MIC);
                            bb.put(buf);

                            mWebSocket.send(bb.array());
                        }else{
                            fis.close();
                            break;
                        }
                        Thread.sleep(Constants.SAMPLE_INTERVAL);
                    }
                }
            } catch (FileNotFoundException e) {
                Log.e(TAG, e.toString());
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            } catch (InterruptedException e) {
                Log.i(TAG, e.toString());
            }finally{
                Log.i(TAG, "Recording thread stops");
                if(fis!=null){
                    try {
                        fis.close();
                    } catch (IOException e) { }
                }
            }
        }


    }

    private void sendFileName2Master(String fileName)
    {
        JSONObject jsonObject = new JSONObject();
    }

    @Override
    public void stopThread() {
        keepRunning = false;
    }
}
