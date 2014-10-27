package fi.hiit.complesense.connection;

import android.util.Log;

import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by hxguo on 27.10.2014.
 */
public class EchoWorker implements Runnable
{
    private static final String TAG = EchoWorker.class.getSimpleName();
    private List<ServerDataEvent> queue = new LinkedList<ServerDataEvent>();

    public void processData(AsyncServer server, SocketChannel socket, byte[] data, int count)
    {
        byte[] dataCopy = new byte[count];
        System.arraycopy(data, 0, dataCopy, 0, count);
        Log.i(TAG, new String(data));
        synchronized(queue)
        {
            queue.add(new ServerDataEvent(server, socket, dataCopy));
            queue.notify();
        }
    }

    @Override
    public void run()
    {
        Log.i(TAG, "starts running at thread: " + Thread.currentThread().getId());
        ServerDataEvent dataEvent;

        while(!Thread.currentThread().isInterrupted())
        {
            // Wait for data to become available
            synchronized(queue)
            {
                while(queue.isEmpty()) {
                    try {
                        queue.wait();
                    } catch (InterruptedException e) {
                    }
                }
                dataEvent = (ServerDataEvent) queue.remove(0);
                Log.i(TAG, new String(dataEvent.data));
            }

            // Return to sender
            dataEvent.server.send(dataEvent.socket, dataEvent.data);
        }
    }
}
