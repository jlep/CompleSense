package fi.hiit.complesense.core;

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

/**
 * Created by hxguo on 7/14/14.
 */
public class GroupOwnerSocketHandler extends AbstractSocketHandler
{
    private static final String TAG = "GroupOwnerSocketHandler";

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
    private final GroupOwnerManager groupOwnerManager;
    private volatile boolean running = false;

    private final Messenger remoteMessenger;


    public GroupOwnerSocketHandler(Messenger remoteMessenger,
                                   GroupOwnerManager groupOwnerMananger) throws IOException
    {
        super(remoteMessenger);
        this.groupOwnerManager = groupOwnerMananger;

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
                groupOwnerManager.setIsRunning(running);
                // A blocking operation. Initiate a CoSenseManager instance when
                // there is a new connection
                Socket s = socket.accept();
                String remoteSocketAddr = s.getRemoteSocketAddress().toString();
                //Log.i(TAG, "remote: " + s.getRemoteSocketAddress().toString());
                //Log.i(TAG,"local: " + socket.getLocalSocketAddress().toString() );
                GroupOwnerConnectionRunnable cr = new GroupOwnerConnectionRunnable(s,
                        groupOwnerManager, remoteMessenger, remoteSocketAddr);

                pool.execute(cr);
                mConnectionWorkQueue.add(cr);
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

    @Override
    public void stopHandler()
    {
        Log.i(TAG, "stopHandler()");
        running = false;
        cancelWaitingThreads();
        cancelBlockedThreads();
        pool.shutdownNow();
        AbstractSocketHandler.closeSocket(socket);
    }

    /**
     * Cancels all waiting Threads in the ThreadPool
     */
    public void cancelWaitingThreads()
    {
        Log.i(TAG,"cancelWaitingThreads()");

        /*
         * Creates an array of tasks that's the same size as the task work queue
         */

        AbstractConnectionRunnable[] taskArray = new AbstractConnectionRunnable[
                mPendingConnQueue.size()];

        // Populates the array with the task objects in the queue
        mPendingConnQueue.toArray(taskArray);

        // Stores the array length in order to iterate over the array
        int taskArraylen = taskArray.length;

        /*
         * Locks on the singleton to ensure that other processes aren't mutating Threads, then
         * iterates over the array of tasks and interrupts the task's current Thread.
         */

        synchronized (groupOwnerManager)
        {
            Log.i(TAG,"pendingArraylen: " + taskArraylen);
            // Iterates over the array of tasks
            for (int taskArrayIndex = 0; taskArrayIndex < taskArraylen; taskArrayIndex++)
            {
                // Gets the task's current thread
                AbstractConnectionRunnable cRunnable = taskArray[taskArrayIndex];

                // if the Runnable exists, post an interrupt to it
                if (null != cRunnable) {
                    cRunnable.signalStop();
                }else
                    Log.e(TAG,"thread is null");
            }
        }
    }

    public void cancelBlockedThreads()
    {
        Log.i(TAG,"cancelBlockedThreads()");
        AbstractConnectionRunnable[] taskArray = new AbstractConnectionRunnable[
                mConnectionWorkQueue.size()];
        int taskArraylen = taskArray.length;

        Log.i(TAG,"blockedArraylen: " + taskArraylen);
        // Populates the array with the task objects in the queue
        mConnectionWorkQueue.toArray(taskArray);

        synchronized (groupOwnerManager)
        {
            for(int i=0;i<taskArraylen;i++)
            {
                AbstractConnectionRunnable cr = taskArray[i];
                if(cr!=null)
                    cr.signalStop();
            }
        }

    }
}
