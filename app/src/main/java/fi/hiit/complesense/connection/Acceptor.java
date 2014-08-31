package fi.hiit.complesense.connection;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 7/14/14.
 */
public class Acceptor extends AbstractSystemThread
{
    public static final String TAG = "Acceptor";

    // Sets the amount of time an idle thread will wait for a task before terminating
    private static final int KEEP_ALIVE_TIME = 1;

    // Sets the Time Unit to seconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;;

    // Sets the initial threadPool size to 8
    private static final int CORE_POOL_SIZE = 8;

    // Sets the maximum threadPool size to 8
    private static final int MAXIMUM_POOL_SIZE = 8;

    /**
     * NOTE: This is the number of total available cores. On current versions of
     * Android, with devices that use plug-and-play cores, this will return less
     * than the total number of cores. The total number of cores is not
     * available in current Android implementations.
     */
    private static int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();

    // A queue of Runnables for waiting connections
    private final BlockingQueue<Runnable> mPendingConnQueue;
    // A queue of Runnables for the TCP connection pool
    private final BlockingQueue<Runnable> mConnectionWorkQueue;

    // A managed pool of background TCP connection threads
    private final ThreadPoolExecutor pool;
    private final ServerSocket socket;
    private volatile boolean running = false;

    private final Messenger remoteMessenger;


    public Acceptor(Messenger remoteMessenger,
                    ServiceHandler serviceHandler) throws IOException
    {
        super(serviceHandler);

        socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(Constants.SERVER_PORT));

        mPendingConnQueue = new LinkedBlockingQueue<Runnable>();
        mConnectionWorkQueue = new LinkedBlockingQueue<Runnable>();

        /*
         * Creates a new pool of Thread objects for the tcp connection work queue
         */
        pool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, mPendingConnQueue);
        this.remoteMessenger = remoteMessenger;
        Log.i(TAG, "listening on port: " + socket.getLocalPort());

    }


    @Override
    public void run()
    {
        Log.i(TAG,"run()");
        running = true;

        while(running)
        {
            try
            {
                // A blocking operation. Initiate a CoSenseManager instance when
                // there is a new connection
                Socket s = socket.accept();

                String remoteSocketAddr = s.getRemoteSocketAddress().toString();
                ConnectionRunnable cr = new ConnectionRunnable(serviceHandler, s);


                pool.execute(cr);
                mConnectionWorkQueue.add(cr);
                cr.write(SystemMessage.makeSensorsListQueryMessage());
                //pool.execute(new ConnectionRunnable(socket.accept(), mInstance));
                Log.i(TAG,"Active threads: " + pool.getActiveCount() );
                //pool.execute(new ChatManager(socket.accept(), handler));
                Log.i(TAG, "Launching the I/O handler");

            }
            catch (IOException e)
            {
                Log.w(TAG,e.toString());
            }
        }
        Log.w(TAG,"Server Terminates!!!");
    }

    /**
     * Cancels all waiting Threads in the ThreadPool
     */
    public synchronized void cancelWaitingThreads()
    {
        Log.i(TAG,"cancelWaitingThreads()");

        /*
         * Creates an array of tasks that's the same size as the task work queue
         */

        ConnectionRunnable[] taskArray = new ConnectionRunnable[
                mPendingConnQueue.size()];

        // Populates the array with the task objects in the queue
        mPendingConnQueue.toArray(taskArray);

        // Stores the array length in order to iterate over the array
        int taskArraylen = taskArray.length;

        /*
         * Locks on the singleton to ensure that other processes aren't mutating Threads, then
         * iterates over the array of tasks and interrupts the task's current Thread.
         */

        synchronized (serviceHandler)
        {
            Log.i(TAG,"pendingArraylen: " + taskArraylen);
            // Iterates over the array of tasks
            for (int taskArrayIndex = 0; taskArrayIndex < taskArraylen; taskArrayIndex++)
            {
                // Gets the task's current thread
                ConnectionRunnable cRunnable = taskArray[taskArrayIndex];

                // if the Runnable exists, post an interrupt to it
                if (null != cRunnable) {
                    cRunnable.stopRunnable();
                }else
                    Log.e(TAG,"thread is null");
            }
        }
    }

    public synchronized void cancelBlockedThreads()
    {
        Log.i(TAG,"cancelBlockedThreads()");
        ConnectionRunnable[] taskArray = new ConnectionRunnable[
                mConnectionWorkQueue.size()];
        int taskArraylen = taskArray.length;

        Log.i(TAG,"blockedArraylen: " + taskArraylen);
        // Populates the array with the task objects in the queue
        mConnectionWorkQueue.toArray(taskArray);

        synchronized (serviceHandler)
        {
            for(int i=0;i<taskArraylen;i++)
            {
                ConnectionRunnable cr = taskArray[i];
                if(cr!=null)
                    cr.stopRunnable();
            }
        }

    }

    @Override
    public void stopThread()
    {
        Log.i(TAG, "stopThread()");
        running = false;
        cancelWaitingThreads();
        cancelBlockedThreads();
        pool.shutdownNow();
        try {
            socket.close();
        } catch (IOException e) {
            Log.i(TAG,e.toString());
        }
    }

    @Override
    public void pauseThread() {

    }
}
