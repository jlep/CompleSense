package fi.hiit.complesense.connection;

import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import fi.hiit.complesense.core.AbsSystemThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.json.JsonSSI;

/**
 * Created by hxguo on 27.10.2014.
 */
public abstract class AbsAsyncIO extends AbsSystemThread
{
    public static final int BUF_SIZE = 8192;

    private static final String TAG = AbsAsyncIO.class.getSimpleName();

    protected Selector selector;

    // A list of PendingChange instances
    protected List<ChangeRequest> pendingChanges = new LinkedList<ChangeRequest>();

    // Maps a SocketChannel to a list of ByteBuffer instances
    protected Map<SocketChannel, List<ByteBuffer> > pendingData =
            new HashMap<SocketChannel, List<ByteBuffer> >();

    // The buffer into which we'll read data when it's available
    protected ByteBuffer readBuffer = ByteBuffer.allocate(BUF_SIZE);

    protected AbsAsyncIO(String name, ServiceHandler serviceHandler)
    {
        super(name, serviceHandler);
    }

    protected abstract Selector initSelector() throws IOException;
    public abstract void close() throws IOException;
    public abstract SocketAddress getLocalSocketAddress();

    public void send(SocketChannel socketChannel, byte[] data)
    {
        //Log.i(TAG, "send(): " + socketChannel.toString());
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

    public void send(SocketChannel socketChannel, JSONObject jsonObject)
    {
        send(socketChannel, jsonObject.toString().getBytes());
    }

    protected void write(SelectionKey key) throws IOException
    {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        synchronized (this.pendingData) {
            List queue = (List) this.pendingData.get(socketChannel);
            // Write until there's not more data ...
            while (!queue.isEmpty())
            {
                ByteBuffer buf = (ByteBuffer) queue.get(0);
                socketChannel.write(buf);
                if (buf.remaining() > 0) {
                    // ... or the socket's buffer fills up
                    break;
                }
                queue.remove(0);
            }

            if (queue.isEmpty()) {
                // We wrote away all data, so we're no longer interested
                // in writing on this socket. Switch back to waiting for
                // data.
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    protected void read(SelectionKey key) throws IOException {
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
        this.handleResponse(socketChannel, this.readBuffer.array(), numRead);
    }

    private void handleResponse(SocketChannel socketChannel, byte[] data, int numRead) throws IOException {
        // Make a correctly sized copy of the data before handing it
        // to the client
        byte[] rspData = new byte[numRead];
        System.arraycopy(data, 0, rspData, 0, numRead);
        JsonSSI.send2ServiceHandler(serviceHandler.getHandler(), socketChannel, data);

        /*
        // Look up the handler for this channel
        RspHandler handler = (RspHandler) this.rspHandlers.get(socketChannel);

        // And pass the response to it
        if (handler.handleResponse(rspData)) {
            // The handler has seen enough, close the connection
            socketChannel.close();
            socketChannel.keyFor(this.selector).cancel();
        }
        */
    }
}
