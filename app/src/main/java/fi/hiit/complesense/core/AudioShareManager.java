package fi.hiit.complesense.core;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import fi.hiit.complesense.connection.AbstractStoppableThread;
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

    public static ReceiveAudioThread getReceiveAudioThread(
            final ServiceHandler serviceHandler)
    {
        ReceiveAudioThread thrd = null;
        try
        {
            thrd = new ReceiveAudioThread(serviceHandler);
        } catch (SocketException e) {
            Log.i(TAG,e.toString());
        }
        return thrd;

    }

    public static class ReceiveAudioThread extends AbstractSystemThread
    {
        public static final String TAG = "ReceiveAudioThread";
        private final DatagramSocket socket;

        public ReceiveAudioThread(ServiceHandler serviceHandler) throws SocketException
        {
            super(serviceHandler);
            socket = new DatagramSocket();
        }

        public int getLocalPort()
        {
            return socket.getLocalPort();
        }

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
            try
            {
                byte[] buf = new byte[BUF_SIZE];

                while(!Thread.currentThread().isInterrupted())
                {
                    DatagramPacket pack = new DatagramPacket(buf, BUF_SIZE);
                    socket.receive(pack);
                    Log.i(TAG, "recv pack: " + pack.getLength());
                    track.write(pack.getData(), 0, pack.getLength());
                }
                Log.e(TAG, "getReceiveAudioThread() exits loop");
            }
            catch (SocketException se)
            {
                Log.e(TAG, "getReceiveAudioThread(): " + se.toString());
            }
            catch (IOException ie)
            {
                Log.e(TAG, "getReceiveAudioThread(): " + ie.toString());
            }
            finally
            {
                track.stop();
                if(socket!=null)
                    socket.close();
            }
        } // end run

        @Override
        public void stopThread()
        {
            if(socket!=null)
                socket.close();
        }

        @Override
        public void pauseThread() {

        }
    }


    public static SendMicAudioThread getSendMicAudioThread(
            final SocketAddress remoteSocketAddr, final ServiceHandler serviceHandler)
    {
        SendMicAudioThread thrd = null;
        try {
            thrd = new SendMicAudioThread(remoteSocketAddr, serviceHandler);
        } catch (SocketException e) {
            Log.i(TAG,e.toString());
        }
        return thrd;
    }

    public static class SendMicAudioThread extends AbstractSystemThread
    {
        public static final String TAG = "SendMicAudioThread";
        private final SocketAddress remoteSocketAddr;
        private final DatagramSocket socket;

        public SendMicAudioThread(SocketAddress remoteSocketAddr,
                                  ServiceHandler serviceHandler) throws SocketException
        {
            super(serviceHandler);
            socket = new DatagramSocket();
            this.remoteSocketAddr = remoteSocketAddr;
        }

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

            try
            {
                InetAddress addr = InetAddress.getLocalHost();
                audio_recorder.startRecording();

                while(!Thread.currentThread().isInterrupted())
                {
                    bytes_read = audio_recorder.read(buf, 0, BUF_SIZE);
                    DatagramPacket pack = new DatagramPacket(buf, bytes_read,
                            remoteSocketAddr);
                    socket.send(pack);
                    bytes_count += bytes_read;
                    Log.i(TAG, "send_bytes_count : " + bytes_count);
                    Thread.sleep(SAMPLE_INTERVAL, 0);
                }
            }
            catch (InterruptedException ie)
            {
                Log.e(TAG, "getSendMicAudioThread(): " + ie.toString());
            }
            catch (SocketException se)
            {
                Log.e(TAG, "getSendMicAudioThread(): " + se.toString());
            }
            catch (UnknownHostException uhe)
            {
                Log.e(TAG, "getSendMicAudioThread(): " + uhe.toString());
            }
            catch (IOException ie)
            {
                Log.e(TAG, "getSendMicAudioThread(): " + ie.toString());
            }
            finally
            {
                audio_recorder.stop();
                if(socket!=null)
                    socket.close();
            }
        } // end run

        @Override
        public void stopThread()
        {
            if(socket!=null)
                socket.close();
        }

        @Override
        public void pauseThread() {

        }
    }



    public static AbstractStoppableThread getRelayAudioThread(final SocketAddress senderSocketAddr,
                                             final SocketAddress receiverSocketAddr,
                                             final AbstractUdpConnectionRunnable parentThread)
    {
        AbstractStoppableThread thrd = null;
        try {
            thrd = new RelayAudioThread(senderSocketAddr,
                    receiverSocketAddr, parentThread);
        } catch (SocketException e)
        {
            Log.i(TAG,e.toString());
        }
        return thrd;
    }

    static class RelayAudioThread extends AbstractStoppableThread
    {
        private final SocketAddress senderSocketAddr;
        private final SocketAddress receiverSocketAddr;
        private final AbstractUdpConnectionRunnable parentThread;
        DatagramSocket sendSocket, recvSocket;

        public RelayAudioThread(SocketAddress senderSocketAddr,
                                SocketAddress receiverSocketAddr,
                                AbstractUdpConnectionRunnable parentThread) throws SocketException {
            this.senderSocketAddr = senderSocketAddr;
            this.receiverSocketAddr = receiverSocketAddr;
            this.parentThread = parentThread;
            sendSocket = new DatagramSocket();
            recvSocket = new DatagramSocket();

        }

        @Override
        public void run()
        {
            Log.e(TAG, "start getRelayAudioThread() thread, thread id: "
                    + Thread.currentThread().getId());
            try
            {
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
                Log.e(TAG, "getRelayAudioThread() exits loop");
            }
            catch (SocketException se)
            {
                Log.e(TAG, "getRelayAudioThread(): " + se.toString());
            }
            catch (IOException ie)
            {
                Log.e(TAG, "getRelayAudioThread(): " + ie.toString());
            }
            finally
            {
                if(recvSocket!=null)
                    recvSocket.close();

                if(sendSocket!=null)
                    sendSocket.close();
            }
        } // end run

        @Override
        public void stopThread()
        {
            if(recvSocket!=null)
                recvSocket.close();

            if(sendSocket!=null)
                sendSocket.close();
        }
    }


}
