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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Collections;
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
public class AsyncClient extends AbsAsyncIO
{

    private static final String TAG = AsyncClient.class.getSimpleName();

    private final InetAddress serverSocketAddr;
    private SocketChannel socketChannel;

    public AsyncClient(ServiceHandler serviceHandler, InetAddress serverSocketAddr) throws IOException
    {
        super(serviceHandler);
        selector = initSelector();
        this.serverSocketAddr = serverSocketAddr;
    }

    @Override
    protected Selector initSelector() throws IOException {
        // Create a new selector
        return SelectorProvider.provider().openSelector();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        if(socketChannel==null)
            return null;
        return socketChannel.socket().getLocalSocketAddress();
    }


    private SocketChannel initiateConnection() throws IOException
    {
        // Create a non-blocking socket channel
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        // Kick off connection establishment
        socketChannel.connect(new InetSocketAddress(serverSocketAddr, Constants.SERVER_PORT));

        // Queue a channel registration since the caller is not the
        // selecting thread. As part of the registration we'll register
        // an interest in connection events. These are raised when a channel
        // is ready to complete connection establishment.
        synchronized(this.pendingChanges)
        {
            this.pendingChanges.add(new ChangeRequest(socketChannel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
        }

        return socketChannel;
    }

    @Override
    public void run()
    {
        Log.i(TAG, "Client running at thread: " + Thread.currentThread().getId());

        try
        {
            socketChannel = this.initiateConnection();

            while(keepRunning)
            {
                // Process any pending changes
                synchronized (this.pendingChanges)
                {
                    Iterator changes = this.pendingChanges.iterator();
                    while (changes.hasNext()) {
                        ChangeRequest change = (ChangeRequest) changes.next();
                        switch (change.type) {
                            case ChangeRequest.CHANGEOPS:
                                SelectionKey key = change.socket.keyFor(this.selector);
                                key.interestOps(change.ops);
                                break;
                            case ChangeRequest.REGISTER:
                                change.socket.register(this.selector, change.ops);
                                break;
                        }
                    }
                    this.pendingChanges.clear();
                }

                // Wait for an event one of the registered channels
                this.selector.select();

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
                    if (key.isConnectable()) {
                        this.finishConnection(key);
                    } else if (key.isReadable()) {
                        this.read(key);
                    } else if (key.isWritable()) {
                        this.write(key);
                    }
                }
            }
        }
        catch (ClosedChannelException e) {
            Log.e(TAG, e.toString());
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }finally {
            Log.i(TAG, "exit main loop");
            try {
                closeConnection();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

    }

    private void closeConnection()  throws IOException
    {
        keepRunning = false;
        if(selector!=null)
            selector.close();
    }

    private void finishConnection(SelectionKey key) throws IOException
    {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // Finish the connection. If the connection operation failed
        // this will raise an IOException.
        try {
            socketChannel.finishConnect();
        } catch (IOException e) {
            // Cancel the channel's registration with our selector
            Log.e(TAG, e.toString());
            key.cancel();
            return;
        }
        serviceHandler.updateStatusTxt("Server Connection established");
        // Register an interest in writing on this channel
        //key.interestOps(SelectionKey.OP_WRITE);
        key.interestOps(SelectionKey.OP_READ);

    }



}
