package fi.hiit.complesense.core;

import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.Window;

import org.json.JSONException;
import org.webrtc.DataChannel;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoRenderer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.AbstractUdpConnectionRunnable;
import fi.hiit.complesense.connection.ConnectionRunnable;
import fi.hiit.complesense.connection.ConnectorCloud;
import fi.hiit.complesense.connection.UdpConnectionRunnable;

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

        private String callerId;

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
            serviceHandler.updateStatusTxt("SendMicAudioThread starts: " + Thread.currentThread().getId());

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



    public static RelayAudioThread getRelayAudioThread(final SocketAddress senderSocketAddr,
                                             final SocketAddress receiverSocketAddr,
                                             final AbstractUdpConnectionRunnable parentThread,
                                             final ServiceHandler serviceHandler)
    {
        RelayAudioThread thrd = null;
        try {
            thrd = new RelayAudioThread(senderSocketAddr,
                    receiverSocketAddr, parentThread, serviceHandler);
        } catch (SocketException e)
        {
            Log.i(TAG, e.toString());
        }
        return thrd;
    }

    public static class RelayAudioThread extends AbstractSystemThread
    {
        private final SocketAddress senderSocketAddr;
        private final SocketAddress receiverSocketAddr;
        private final AbstractUdpConnectionRunnable parentThread;
        DatagramSocket sendSocket, recvSocket;

        public RelayAudioThread(SocketAddress senderSocketAddr,
                                SocketAddress receiverSocketAddr,
                                AbstractUdpConnectionRunnable parentThread,
                                ServiceHandler serviceHandler) throws SocketException
        {
            super(serviceHandler);

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
              //  parentThread.write(SystemMessage.makeAudioStreamingRequest(recvSocket.getLocalPort()),
                //        senderSocketAddr);



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

        @Override
        public void pauseThread() {

        }
    }

    public static StreamRelayAudioThread getStreamRelayAudioThread(final SocketAddress senderSocketAddr,
                                                       final ServiceHandler serviceHandler,
                                                       final UdpConnectionRunnable parentThread)
    {
        StreamRelayAudioThread thrd = null;
        try {
            thrd = new StreamRelayAudioThread(senderSocketAddr,
                    parentThread, serviceHandler);
        } catch (SocketException e)
        {
            Log.i(TAG,e.toString());
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        }
        return thrd;
    }

    public static class StreamRelayAudioThread extends AbstractSystemThread
    {
        public static final String TAG = "StreamRelayAudioThread";
        private final SocketAddress senderSocketAddr;
        private final UdpConnectionRunnable parentThread;
        DatagramSocket recvSocket;
        private final OutputStream outputStream;
        private final Socket cloudSocket;



        public StreamRelayAudioThread(SocketAddress senderSocketAddr,
                                      UdpConnectionRunnable parentThread,
                                      ServiceHandler serviceHandler) throws IOException
        {
            super(serviceHandler);

            this.senderSocketAddr = senderSocketAddr;
            this.parentThread = parentThread;
            recvSocket = new DatagramSocket();
            cloudSocket = new Socket(Constants.URL, Constants.CLOUD_SERVER_PORT);
            outputStream = new BufferedOutputStream(cloudSocket.getOutputStream());

        }

        @Override
        public void run()
        {
            Log.e(TAG, "start getRelayAudioThread() thread, thread id: "
                    + Thread.currentThread().getId());

            serviceHandler.updateStatusTxt("StreamRelayAudioThread starts: "
                    + Thread.currentThread().getId());
            try
            {
                byte[] buf = new byte[BUF_SIZE];
                parentThread.write(SystemMessage.makeAudioStreamingRequest(
                        recvSocket.getLocalPort(),
                        cloudSocket.getLocalSocketAddress().toString()), senderSocketAddr);


                while(!Thread.currentThread().isInterrupted())
                {
                    DatagramPacket pack = new DatagramPacket(buf, BUF_SIZE);
                    recvSocket.receive(pack);
                    Log.i(TAG, "Relay recv pack: " + pack.getLength());

                    outputStream.write(pack.getData(), 0, pack.getLength());
                    outputStream.flush();
                }
                Log.e(TAG, "exits loop");
            }
            catch (SocketException se)
            {
                Log.e(TAG, se.toString());
            }
            catch (IOException ie)
            {
                Log.e(TAG, ie.toString());
            }
            finally{
                stopThread();
            }
        } // end run


        @Override
        public void stopThread()
        {
            if(recvSocket!=null)
                recvSocket.close();

            if(cloudSocket!=null){
                try {
                    cloudSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void pauseThread() {

        }

        public int getLocalForeignPort()
        {
            return cloudSocket.getLocalPort();
        }
    }


    public static RelayAudioHttpThread getHttpRelayAudioThread(final SocketAddress senderSocketAddr,
                                                       final ServiceHandler serviceHandler,
                                                       final UdpConnectionRunnable localConnectionRunnale)
    {
        RelayAudioHttpThread thrd = null;
        try {
            thrd = new RelayAudioHttpThread(senderSocketAddr, serviceHandler,localConnectionRunnale);
        } catch (IOException e) {
            Log.i(TAG,e.toString());
        }
        return thrd;
    }


    public static class RelayAudioHttpThread extends AbstractSystemThread
    {
        public static final String TAG = "RelayAudioHttpThread";

        public final SocketAddress senderSocketAddr;
        DatagramSocket recvSocket;
        byte[] buf = new byte[BUF_SIZE];
        ConnectionRunnable connectionRunnable;

        private BufferedOutputStream outputStream;
        private HttpURLConnection httpURLConnection;
        private final UdpConnectionRunnable localConnectionRunnale;

        public RelayAudioHttpThread(SocketAddress senderSocketAddr,
                                    ServiceHandler serviceHandler,
                                    UdpConnectionRunnable localConnectionRunnale) throws IOException
        {
            super(serviceHandler);
            this.senderSocketAddr = senderSocketAddr;

            recvSocket = new DatagramSocket();
            connectionRunnable = null;
            outputStream = null;
            this.localConnectionRunnale = localConnectionRunnale;
        }

        @Override
        public void run()
        {
            Log.e(TAG, "start RelayAudioHttpThread() thread, thread id: "
                    + Thread.currentThread().getId());
            URL url = null;
            try
            {
                url = new URL("http://"+ Constants.URL +":"
                        +Constants.CLOUD_SERVER_PORT + "/");
                httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setRequestMethod("POST");
                outputStream = new BufferedOutputStream(httpURLConnection.getOutputStream());

                // inform peer to start sending audio stream
                //localConnectionRunnale.write(SystemMessage.makeAudioStreamingRequest(
                //                recvSocket.getLocalPort()), senderSocketAddr);

                while(!Thread.currentThread().isInterrupted())
                {
                    DatagramPacket pack = new DatagramPacket(buf, BUF_SIZE);
                    recvSocket.receive(pack);
                    Log.i(TAG, "Relay recv pack: " + pack.getLength());
                    if(outputStream!=null)
                        outputStream.write(pack.getData(), 0, pack.getLength());
                }
                Log.e(TAG, "getHttpRelayAudioThread() exits loop");
            }
            catch (SocketException se)
            {
                Log.e(TAG, "getHttpRelayAudioThread(): " + se.toString());
            }
            catch (IOException ie)
            {
                Log.e(TAG, "getHttpRelayAudioThread(): " + ie.toString());
            }
            finally
            {
                stopThread();
            }
        } // end run

        public int getLocalPort()
        {
            return recvSocket.getLocalPort();
        }

        @Override
        public void stopThread()
        {
            if(recvSocket!=null)
                recvSocket.close();
        }

        @Override
        public void pauseThread() {

        }
    }


}
