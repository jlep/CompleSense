package fi.hiit.complesense.core;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import fi.hiit.complesense.connection.AbstractUdpConnectionRunnable;

/**
 * Created by hxguo on 8/4/14.
 */
public class AudioShareManager
{
    static final String TAG = "AudioShareManager";
    public static final int AUDIO_PORT = 20485;
    static final int SAMPLE_RATE = 8000;
    static final int SAMPLE_INTERVAL = 20; // milliseconds
    static final int SAMPLE_SIZE = 2; // bytes per sample
    static final int BUF_SIZE = SAMPLE_INTERVAL * SAMPLE_INTERVAL * SAMPLE_SIZE * 2;
    private static Thread receiveAudioStreamThread;

    public static Thread getReceiveAudioThread()
    {
        Thread thrd = new Thread(new Runnable() {

            @Override
            public void run()
            {
                Log.e(TAG, "start receiveAudio() thread, thread id: "
                        + Thread.currentThread().getId());
                AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, BUF_SIZE,
                        AudioTrack.MODE_STREAM);
                track.play();
                DatagramSocket sock = null;
                try
                {
                    sock = new DatagramSocket(AUDIO_PORT);
                    byte[] buf = new byte[BUF_SIZE];

                    while(!Thread.currentThread().isInterrupted())
                    {
                        DatagramPacket pack = new DatagramPacket(buf, BUF_SIZE);
                        sock.receive(pack);
                        Log.i(TAG, "recv pack: " + pack.getLength());
                        track.write(pack.getData(), 0, pack.getLength());
                    }
                    Log.e(TAG, "receiveAudio() exits loop");
                }
                catch (SocketException se)
                {
                    Log.e(TAG, "receiveAudio(): " + se.toString());
                }
                catch (IOException ie)
                {
                    Log.e(TAG, "receiveAudio(): " + ie.toString());
                }
                finally {
                    if(sock!=null)
                        sock.close();
                }
            } // end run
        });
//        thrd.start();
        return thrd;
    }

    public static Thread getSendAudioThread(String audioFilePath, final InetAddress remoteSocketAddr)
    {
        final File audio = new File(audioFilePath);

        Thread thrd = new Thread(new Runnable() {
            @Override
            public void run()
            {
                Log.e(TAG, "start sendAudioThread() thread, thread id: "
                        + Thread.currentThread().getId());
                long file_size = 0;
                int bytes_read = 0;
                int bytes_count = 0;

                FileInputStream audio_stream = null;
                file_size = audio.length();
                byte[] buf = new byte[BUF_SIZE];

                DatagramSocket sock = null;
                try
                {
                    //InetAddress addr = InetAddress.getLocalHost();
                    sock = new DatagramSocket();
                    audio_stream = new FileInputStream(audio);

                    while(bytes_count < file_size && !Thread.currentThread().isInterrupted())
                    {
                        bytes_read = audio_stream.read(buf, 0, BUF_SIZE);
                        DatagramPacket pack = new DatagramPacket(buf, bytes_read,
                                remoteSocketAddr, AUDIO_PORT);
                        sock.send(pack);
                        bytes_count += bytes_read;
                        //Log.i(TAG, "bytes_count : " + bytes_count);
                        Thread.sleep(SAMPLE_INTERVAL, 0);
                    }
                }
                catch (InterruptedException ie)
                {
                    Log.e(TAG, "sendAudioThread(): " + ie.toString());
                }
                catch (FileNotFoundException fnfe)
                {
                    Log.e(TAG, "sendAudioThread(): " + fnfe.toString() );
                }
                catch (SocketException se)
                {
                    Log.e(TAG, "sendAudioThread(): " + se.toString());
                }
                catch (UnknownHostException uhe)
                {
                    Log.e(TAG, "sendAudioThread(): " + uhe.toString());
                }
                catch (IOException ie)
                {
                    Log.e(TAG, "sendAudioThread(): " + ie.toString() );
                }
                finally
                {
                    if(sock!=null)
                        sock.close();
                }
            } // end run
        });
        //thrd.start();
        return thrd;
    }

    public static Thread getSendMicAudioThread(final SocketAddress remoteSocketAddr)
    {
        Thread thrd = new Thread(new Runnable() {
            @Override
            public void run()
            {
                Log.e(TAG, "start getSendMicAudioThread() thread, thread id: "
                        + Thread.currentThread().getId());
                AudioRecord audio_recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                        AudioFormat.CHANNEL_CONFIGURATION_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        AudioRecord.getMinBufferSize(SAMPLE_RATE,
                                AudioFormat.CHANNEL_CONFIGURATION_MONO,
                                AudioFormat.ENCODING_PCM_16BIT) * 10);
                int bytes_read = 0;
                int bytes_count = 0;
                byte[] buf = new byte[BUF_SIZE];

                DatagramSocket sock = null;
                try
                {
                    InetAddress addr = InetAddress.getLocalHost();
                    sock = new DatagramSocket();
                    audio_recorder.startRecording();

                    while(!Thread.currentThread().isInterrupted())
                    {
                        bytes_read = audio_recorder.read(buf, 0, BUF_SIZE);
                        DatagramPacket pack = new DatagramPacket(buf, bytes_read,
                                remoteSocketAddr);
                        sock.send(pack);
                        bytes_count += bytes_read;
                        Log.i(TAG, "send_bytes_count : " + bytes_count);
                        Thread.sleep(SAMPLE_INTERVAL, 0);
                    }
                }
                catch (InterruptedException ie)
                {
                    Log.e(TAG, "sendMicAudioThread(): " + ie.toString());
                }
                catch (SocketException se)
                {
                    Log.e(TAG, "sendMicAudioThread(): " + se.toString());
                }
                catch (UnknownHostException uhe)
                {
                    Log.e(TAG, "sendMicAudioThread(): " + uhe.toString());
                }
                catch (IOException ie)
                {
                    Log.e(TAG, "sendMicAudioThread(): " + ie.toString());
                }
                finally
                {
                    if(sock!=null)
                        sock.close();
                }
            } // end run
        });
        return thrd;
    }


    public static Thread getRelayAudioThread(final SocketAddress senderSocketAddr,
                                             final SocketAddress receiverSocketAddr,
                                             final AbstractUdpConnectionRunnable parentThread)
    {
        Thread thrd = new Thread(new Runnable() {

            @Override
            public void run()
            {
                Log.e(TAG, "start getRelayAudioThread() thread, thread id: "
                        + Thread.currentThread().getId());
                DatagramSocket sendSocket = null, recvSocket = null;

                try
                {
                    sendSocket = new DatagramSocket();
                    recvSocket = new DatagramSocket();
                    byte[] buf = new byte[BUF_SIZE];
                    parentThread.write(SystemMessage.makeAudioStreamingRequest(recvSocket.getLocalPort()),
                            senderSocketAddr);


                    while(!Thread.currentThread().isInterrupted())
                    {
                        DatagramPacket pack = new DatagramPacket(buf, BUF_SIZE);
                        recvSocket.receive(pack);
                        Log.i(TAG, "Relay recv pack: " + pack.getLength());

                        pack.setSocketAddress(receiverSocketAddr);
                        pack.setPort(AUDIO_PORT);
                        sendSocket.send(pack);
                    }
                    Log.e(TAG, "receiveAudio() exits loop");
                }
                catch (SocketException se)
                {
                    Log.e(TAG, "receiveAudio(): " + se.toString());
                }
                catch (IOException ie)
                {
                    Log.e(TAG, "receiveAudio(): " + ie.toString());
                }
                finally
                {
                    if(recvSocket!=null)
                        recvSocket.close();

                    if(sendSocket!=null)
                        sendSocket.close();
                }
            } // end run
        });
//        thrd.start();
        return thrd;
    }

}
