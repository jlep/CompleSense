package fi.hiit.complesense.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ClientServiceHandler;
import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 29.9.2014.
 */
public class SendAudioThread extends AbstractSystemThread
    implements AsyncHttpClient.WebSocketConnectCallback, WebSocket.StringCallback, DataCallback
{
    public static final String TAG = "SendAudioThread";
    private final long ownerThreadId;
    private final boolean keepLocalFile;
    private final SocketAddress remoteSocketAddr;
    private static final String PROTOCOL = "ws";
    private URI uri = null;
    private WebSocket mWebSocket = null;

    public static SendAudioThread instance;
    private volatile boolean recording;


    private SendAudioThread(SocketAddress remoteSocketAddr,
                              ServiceHandler serviceHandler,
                              long threadId,
                              boolean keepLocalFile) throws SocketException
    {
        super(serviceHandler);
        uri = URI.create(PROTOCOL +":/"+ remoteSocketAddr.toString()+"/send_rec");
        this.remoteSocketAddr = remoteSocketAddr;
        this.keepLocalFile = keepLocalFile;
        this.ownerThreadId = threadId;
        connect();
    }

    public static SendAudioThread getInstancce(InetSocketAddress remoteSocketAddr,
                                               ServiceHandler serviceHandler,
                                               long threadId, boolean keepLocalFile)
    {
        try
        {
            instance = new SendAudioThread(remoteSocketAddr, serviceHandler, threadId, keepLocalFile);
        }
        catch (SocketException e)
        {
            Log.i(TAG,e.toString() );
            instance = null;
        }
        return instance;
    }

    private void connect()
    {
        Log.i(TAG, "connect("+ uri.toString() +")");
        serviceHandler.updateStatusTxt("connect("+ uri.toString() +")");
        AsyncHttpClient.getDefaultInstance().websocket(uri.toString(), PROTOCOL, this);
    }


    @Override
    public void run()
    {
        String str = "start SendAudioThread, thread id: " + Thread.currentThread().getId();
        Log.e(TAG, str);
        serviceHandler.updateStatusTxt(str);

        sendMicAudio();

    } // end run

    @Override
    public void stopThread()
    {
        String str = "stop SendAudioThread, thread";
        Log.e(TAG, str);
        serviceHandler.updateStatusTxt(str);
        recording = false;

        //extRecorder.reset();
        //extRecorder.release();
    }

    @Override
    public void pauseThread() {

    }

    public void sendMicAudio()
    {
        Log.e(TAG, "start SendMicAudio thread, thread id: " + Thread.currentThread().getId());
        WavFileWriter wavFileWriter = null;

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
        try
        {
            wavFileWriter= WavFileWriter.getInstance(Constants.ROOT_DIR + ownerThreadId + ".wav");
            if(wavFileWriter==null)
                throw new IOException("wavFileWrite is null");

            long lastCheckMillis = System.currentTimeMillis(), interval = 2000;
            int preByteCount = 0;

            recording = true;
            audio_recorder.startRecording();
            while(recording)
            {
                bytes_read = audio_recorder.read(buf, 0, Constants.BUF_SIZE);
                mWebSocket.send(buf);
                wavFileWriter.write(buf);
                //getSampleEnergy(buf);
                bytes_count += bytes_read;

                Thread.sleep(Constants.SAMPLE_INTERVAL);
                if(System.currentTimeMillis() - lastCheckMillis > interval )
                {
                    serviceHandler.updateStatusTxt("send " + (bytes_count - preByteCount) + "Bytes to master");
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
            if(mWebSocket!=null)
                mWebSocket.close();
        }
    }

    @Override
    public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {

    }

    @Override
    public void onStringAvailable(String s) {

    }

    @Override
    public void onCompleted(Exception ex, WebSocket webSocket)
    {
        Log.i(TAG, "onCompleted("+ uri.toString() +")");
        if (ex != null)
        {
            Log.e(TAG, ex.toString());
            return;
        }
        serviceHandler.updateStatusTxt("Connection with " + uri.toString() + " is established");
        mWebSocket = webSocket;
        start();
    }
}
