package fi.hiit.complesense.connection;

import android.os.Message;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 27.10.2014.
 */
public class AsyncServer extends AbsAsyncIO
{
    private static final String TAG = AsyncServer.class.getSimpleName();

    // The channel on which we'll accept connections
    private ServerSocketChannel serverChannel;

    private EchoWorker worker;


    protected AsyncServer(ServiceHandler serviceHandler ,EchoWorker worker) throws IOException
    {
        super(serviceHandler);
        selector = initSelector();
        this.worker = worker;
    }

    public static AsyncServer getInstance(ServiceHandler serviceHandler, EchoWorker echoWorker)
    {
        try {
            AsyncServer asyncServer = new AsyncServer(serviceHandler, echoWorker);
            return asyncServer;
        } catch (IOException e)
        {
            Log.e(TAG,e.toString());
            return null;
        }
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
        InetSocketAddress isa = new InetSocketAddress(Constants.SERVER_PORT);
        serverChannel.socket().bind(isa);
        serverChannel.socket().setReuseAddress(true);


        // Register the server socket channel, indicating an interest in
        // accepting new connections
        serverChannel.register(socketSelector, SelectionKey.OP_ACCEPT);

        return socketSelector;
    }

    @Override
    public SocketAddress getLocalSocketAddress()
    {
        if(serverChannel.socket()==null)
            return null;
        return serverChannel.socket().getLocalSocketAddress();
    }

    @Override
    public void run()
    {
        try
        {
            Log.i(TAG, "Server running at thread: " + Thread.currentThread().getId());
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
        }
        catch (IOException e)
        {
            Log.e(TAG, e.toString());
        }finally {
            Log.i(TAG, "exit main loop");
            try
            {
                closeConnections();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }


    }

    private void closeConnections() throws IOException
    {
        keepRunning = false;
        Log.i(TAG, "closeConnections()");
        selector.close();
        serverChannel.socket().close();
        serverChannel.close();
    }

    private void accept(SelectionKey key) throws IOException
    {
        // For an accept to be pending the channel must be a server socket channel.
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        // Accept the connection and make it non-blocking
        SocketChannel socketChannel = serverSocketChannel.accept();
        Socket socket = socketChannel.socket();
        serviceHandler.updateStatusTxt("Server receives connectin request from: " + socket.getRemoteSocketAddress());

        socketChannel.configureBlocking(false);

        // Register the new SocketChannel with our Selector, indicating
        // we'd like to be notified when there's data waiting to be read
        //socketChannel.register(this.selector, SelectionKey.OP_READ);
        socketChannel.register(this.selector, SelectionKey.OP_WRITE);
        try
        {
            JSONObject jsonAccept = new JSONObject();
            jsonAccept.put(JsonSSI.COMMAND, JsonSSI.NEW_CONNECTION);
            JsonSSI.send2ServiceHandler(serviceHandler.getHandler(), socketChannel, jsonAccept.toString().getBytes());

        } catch (JSONException e) {
            Log.i(TAG, e.toString());
        }
    }



}
