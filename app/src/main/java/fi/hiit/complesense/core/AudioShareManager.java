package fi.hiit.complesense.core;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.audio.ExtRecorder;
import fi.hiit.complesense.connection.AbstractUdpConnectionRunnable;
import fi.hiit.complesense.connection.ConnectionRunnable;
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
            final SocketAddress remoteSocketAddr, final ServiceHandler serviceHandler, boolean keepLocalFile)
    {
        SendMicAudioThread thrd = null;
        try {
            thrd = new SendMicAudioThread(remoteSocketAddr, serviceHandler, keepLocalFile);
        } catch (SocketException e) {
            Log.i(TAG,e.toString());
        }
        return thrd;
    }

    public static class SendMicAudioThread extends AbstractSystemThread
    {
        public static final String TAG = "SendMicAudioThread";
        private final ExtRecorder extRecorder;

        private String callerId;

        private final SocketAddress remoteSocketAddr;
        private final DatagramSocket socket;
        private final boolean keepLocalFile;

        public SendMicAudioThread(SocketAddress remoteSocketAddr,
                                  ServiceHandler serviceHandler,
                                  boolean keepLocalFile) throws SocketException
        {
            super(serviceHandler);
            socket = new DatagramSocket();
            this.remoteSocketAddr = remoteSocketAddr;
            this.keepLocalFile = keepLocalFile;
            extRecorder = ExtRecorder.getInstanse(false, socket, remoteSocketAddr);
            extRecorder.setOutputFile(Constants.ROOT_DIR + Long.toString(System.currentTimeMillis() ) +".wav");
            extRecorder.prepare();
        }

        @Override
        public void run()
        {
            Log.e(TAG, "start SendMicAudioThread, thread id: "
                    + Thread.currentThread().getId());
            serviceHandler.updateStatusTxt("SendMicAudioThread starts: " + Thread.currentThread().getId());
            extRecorder.start();
        } // end run

        @Override
        public void stopThread()
        {
            extRecorder.stop();
            extRecorder.reset();
            extRecorder.release();

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

        private int packetCount = 0;
        private long recStartTime = 0;

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
                    packetCount++;
                    Log.i(TAG, "Relay recv packetCount: " + packetCount);
                    if(packetCount == 1)
                    {
                        long timeDiff = serviceHandler.peerList.get(senderSocketAddr.toString()).getTimeDiff();
                        recStartTime = System.currentTimeMillis() - timeDiff;
                    }


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

        public long getRecStartTime()
        {
            return this.recStartTime;
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
            if(outputStream!=null)
            {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    Log.i(TAG,e.toString());
                }

            }

            if(httpURLConnection!=null)
                httpURLConnection.disconnect();
        }

        @Override
        public void pauseThread() {

        }
    }

    public static WebSocketConnection getWebSocketAudioRelayThread(final SocketAddress senderSocketAddr,
                                                                         final ServiceHandler serviceHandler,
                                                                         final UdpConnectionRunnable localUdpRunnable)
    {
        WebSocketConnection thrd = null;
        try {
            thrd = new WebSocketConnection(serviceHandler,
                    senderSocketAddr, localUdpRunnable);
        } catch (SocketException e)
        {
            Log.i(TAG,e.toString());
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        }
        return thrd;
    }


    public static class WebSocketConnection extends AbstractSystemThread
            implements AsyncHttpClient.WebSocketConnectCallback, WebSocket.StringCallback, DataCallback
    {
        public static final String TAG = "WebSocketConnection:" + Thread.currentThread().getId();
        public final SocketAddress senderSocketAddr;
        private final UdpConnectionRunnable localUdpRunnable;
        public final DatagramSocket recvSocket;
        private int packetCount;
        private long recStartTime;

        private static final String PROTOCOL = "ws";
        private URI uri = URI.create(PROTOCOL +"://"+ Constants.URL+":"+Constants.CLOUD_SERVER_PORT+"/");
        private WebSocket mWebSocket = null;

        public WebSocketConnection(ServiceHandler serviceHandler,
                                         SocketAddress senderSocketAddr,
                                         UdpConnectionRunnable localUdpRunnable) throws SocketException
        {
            super(serviceHandler);
            this.senderSocketAddr = senderSocketAddr;
            this.localUdpRunnable = localUdpRunnable;
            recvSocket = new DatagramSocket();

            connect();
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
            Log.e(TAG, "start WebSocketAudioRelayThread, id: "
                    + Thread.currentThread().getId());

            serviceHandler.updateStatusTxt("WebSocketAudioRelayThread starts: "
                    + Thread.currentThread().getId());
            try
            {
                byte[] buf = new byte[BUF_SIZE];
                localUdpRunnable.write(SystemMessage.makeAudioStreamingRequest(
                        recvSocket.getLocalPort(),
                        Thread.currentThread().getId(), true), senderSocketAddr);

                while(!Thread.currentThread().isInterrupted())
                {
                    DatagramPacket pack = new DatagramPacket(buf, BUF_SIZE);
                    recvSocket.receive(pack);
                    packetCount++;
                    Log.i(TAG, "Relay recv packetCount: " + packetCount);
                    if(packetCount == 1)
                    {
                        long timeDiff = serviceHandler.peerList.get(senderSocketAddr.toString()).getTimeDiff();

                        String str = "timeDiff with "+ senderSocketAddr.toString() +": " + timeDiff;
                        Log.i(TAG, str);
                        serviceHandler.updateStatusTxt(str);

                        recStartTime = System.currentTimeMillis() - timeDiff;
                        String audioName = "audio_name:" + Thread.currentThread().getId() +"_"+ Long.toString(recStartTime);
                        mWebSocket.send(audioName);
                        serviceHandler.updateStatusTxt(audioName);
                    }
                    mWebSocket.send(pack.getData());
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
            if(mWebSocket!=null)
                mWebSocket.close();
        }

        @Override
        public void pauseThread() {

        }

        @Override
        public void onDataAvailable(DataEmitter dataEmitter,
                                    ByteBufferList byteBufferList)
        {
            serviceHandler.updateStatusTxt("I got some bytes!");
            // note that this data has been read
            byteBufferList.recycle();
        }

        @Override
        public void onStringAvailable(String s) {
            serviceHandler.updateStatusTxt("str from Server: " + s);
        }

        @Override
        public void onCompleted(Exception ex, WebSocket webSocket)
        {
            Log.i(TAG, "onCompleted("+ uri.toString() +")");
            if (ex != null) {
                Log.e(TAG, ex.toString());
                return;
            }
            serviceHandler.updateStatusTxt("Connection with " + uri.toString() + " is established");
            mWebSocket = webSocket;
            start();
        }
    }
}
