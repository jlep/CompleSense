package fi.hiit.complesense.connection;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 27.10.2014.
 */
public class AsyncClient extends AbsAsyncIO
{

    public static final String TAG = AsyncClient.class.getSimpleName();

    private final InetAddress serverSocketAddr;
    private final int port;
    protected SocketChannel socketChannel;

    public AsyncClient(ServiceHandler serviceHandler, InetAddress serverSocketAddr, int port) throws IOException
    {
        super(TAG, serviceHandler);
        selector = initSelector();
        this.port = port;
        this.serverSocketAddr = serverSocketAddr;
    }

    @Override
    protected Selector initSelector() throws IOException {
        // Create a new selector
        return SelectorProvider.provider().openSelector();
    }

    @Override
    public void close() throws IOException {
        if(selector!=null)
            selector.close();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        if(socketChannel==null)
            return null;
        return socketChannel.socket().getLocalSocketAddress();
    }


    protected SocketChannel initiateConnection() throws IOException
    {
        // Create a non-blocking socket channel
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        // Kick off connection establishment
        socketChannel.connect(new InetSocketAddress(serverSocketAddr, port));

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
        Log.i(TAG, "starts running at thread: " + Thread.currentThread().getId());
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

        }catch (ClosedSelectorException e){
            Log.e(TAG, e.toString());
        }
        catch (ClosedChannelException e) {
            Log.e(TAG, e.toString());
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }finally {
            Log.i(TAG, "exit main loop");
            try {
                close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

    }

    protected void finishConnection(SelectionKey key) throws IOException
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

    public void send(byte[] data)
    {
        //Log.i(TAG, "send(): " + new String(data));
        synchronized (this.pendingChanges)
        {
            // Indicate we want the interest ops set changed
            this.pendingChanges.add(new ChangeRequest(socketChannel, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

            // And queue the data we want written
            synchronized (this.pendingData)
            {
                List queue = (List) this.pendingData.get(socketChannel);
                if (queue == null) {
                    queue = new ArrayList();
                    this.pendingData.put(socketChannel, queue);
                }
                queue.add(ByteBuffer.wrap(data));
            }
        }

        // Finally, wake up our selecting thread so it can make the required changes
        selector.wakeup();
    }

    @Override
    public void stopThread() {
        keepRunning = false;
        try {
            close();
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        }
    }
}
