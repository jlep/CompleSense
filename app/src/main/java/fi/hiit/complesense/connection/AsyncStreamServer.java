package fi.hiit.complesense.connection;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.DataProcessingThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 28.10.2014.
 */
public class AsyncStreamServer extends AsyncServer
{
    public static final String TAG = AsyncStreamServer.class.getSimpleName();
    private final SocketChannel socketChannel;

    // The channel on which we'll accept connections
    private ServerSocketChannel serverChannel;
    private DataProcessingThread dataProcessingThread;


    public AsyncStreamServer(ServiceHandler serviceHandler,
                             SocketChannel socketChannel) throws IOException
    {
        super(serviceHandler);
        this.socketChannel = socketChannel;
        dataProcessingThread = new DataProcessingThread(this, serviceHandler);
    }

    @Override
    protected Selector initSelector() throws IOException
    {
        // Create a new selector
        Selector socketSelector = SelectorProvider.provider().openSelector();

        // Create a new non-blocking server socket channel
        this.serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);

        // Bind the server socket to the specified address and port
        InetSocketAddress isa = new InetSocketAddress(Constants.STREAM_SERVER_PORT);
        serverChannel.socket().bind(isa);
        serverChannel.socket().setReuseAddress(true);

        // Register the server socket channel, indicating an interest in
        // accepting new connections
        serverChannel.register(socketSelector, SelectionKey.OP_ACCEPT);
        return socketSelector;
    }

    @Override
    public void run()
    {
        try{
            Log.i(TAG, "StreamServer running at thread: " + Thread.currentThread().getId());
            selector = initSelector();
            dataProcessingThread.start();

            notifyServerRunning(serviceHandler.getHandler(), socketChannel);
            while(keepRunning)
            {
                synchronized(this.pendingChanges)
                {
                    Iterator changes = this.pendingChanges.iterator();
                    while (changes.hasNext())
                    {
                        ChangeRequest change = (ChangeRequest) changes.next();
                        switch(change.type)
                        {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(this.selector);
                                key.interestOps(change.ops);
                        }
                    }
                    this.pendingChanges.clear();
                }

                // Wait for an event one of the registered channels
                selector.select();

                // Iterate over the set of keys for which events are available
                Iterator selectedKeys = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext())
                {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    // Check what event is available and deal with it
                    if (key.isAcceptable()) {
                        this.accept(key);
                    } else if (key.isReadable()) {
                        this.read(key);
                    } else if (key.isWritable()) {
                        this.write(key);
                    }
                }

            }
        }catch (ClosedSelectorException e){
            Log.v(TAG, e.toString());
        }catch (IOException e)
        {
            Log.e(TAG, e.toString());
        }finally {
            Log.i(TAG, "exit main loop");
            try{
                close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    public void notifyServerRunning(Handler handler, SocketChannel socketChannel )
    {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(JsonSSI.COMMAND, JsonSSI.NEW_STREAM_SERVER);
            jsonObject.put(JsonSSI.SOCKET_CHANNEL, socketChannel);
            jsonObject.put(JsonSSI.STREAM_PORT, serverChannel.socket().getLocalPort());
            jsonObject.put(JsonSSI.DESC, "New Stream Server running at thread: "+ Thread.currentThread().getId());
            Message msg = Message.obtain(handler, ServiceHandler.JSON_RESPONSE_BYTES, jsonObject);
            msg.sendToTarget();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void handleNewConnection(Handler handler, SocketChannel socketChannel) throws JSONException
    {
        JSONObject jsonAccept = new JSONObject();
        jsonAccept.put(JsonSSI.COMMAND, JsonSSI.NEW_STREAM_CONNECTION);
        jsonAccept.put(JsonSSI.DESC, "New Stream Connection: " + socketChannel.socket().getRemoteSocketAddress());
       // JsonSSI.send2ServiceHandler(handler, socketChannel, jsonAccept.toString().getBytes());
    }

    @Override
    protected void read(SelectionKey key) throws IOException
    {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // Clear out our read buffer so it's ready for new data
        this.readBuffer.clear();

        // Attempt to read off the channel
        int numRead;
        try {
            numRead = socketChannel.read(this.readBuffer);
        } catch (IOException e) {
            // The remote forcibly closed the connection, cancel
            // the selection key and close the channel.
            key.cancel();
            socketChannel.close();
            return;
        }

        if (numRead == -1) {
            // Remote entity shut the socket down cleanly. Do the
            // same from our end and cancel the channel.
            key.channel().close();
            key.cancel();
            return;
        }
        //Log.i(TAG, "read(): " + new String(readBuffer.array()));
        // Hand the data off to our worker thread
        //this.worker.processData(this, socketChannel, this.readBuffer.array(), numRead);
        handleStreamData(socketChannel, numRead);
    }

    private void handleStreamData(SocketChannel socketChannel, int numRead) throws IOException
    {
        /*
        Log.i(TAG, "numRead: " + numRead + " pos: " + readBuffer.position() + " remaining(): " + readBuffer.remaining());
        if(numRead>Constants.BYTES_INT){
            readBuffer.flip();
            int payloadSize = readBuffer.getInt(0);
            Log.i(TAG, "payloadSize: " + payloadSize + " pos: " + readBuffer.position() + " remaining(): " + readBuffer.remaining());
            if((readBuffer.remaining()-Constants.BYTES_INT) >= payloadSize){
                payloadSize = readBuffer.getInt();
                Log.i(TAG, "payloadSize: " + payloadSize + " pos: " + readBuffer.position() + " remaining(): " + readBuffer.remaining());
                byte[] payload = new byte[payloadSize];
                readBuffer.get(payload);
                //dataProcessingThread.addDataToThreadBuffer(socketChannel, payload, payloadSize);

                Log.i(TAG, "pos: " + readBuffer.position() + " remaining(): " + readBuffer.remaining());

            }else{ // not enough data has been received, wait for more data
                Log.i(TAG, "need : " + (payloadSize-numRead+Constants.BYTES_INT) + " more bytes");
            }
        }
        */
        Log.i(TAG, "numRead: " + numRead + " from: " + socketChannel.socket().getRemoteSocketAddress().toString());
        byte[] bytes = new byte[numRead];
        readBuffer.flip();
        readBuffer.get(bytes);
        dataProcessingThread.addDataToThreadBuffer(socketChannel, bytes, numRead);
    }

    @Override
    public void stopThread() {
        dataProcessingThread.stopThread();
        super.stopThread();
    }
}
