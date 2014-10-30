package fi.hiit.complesense.connection;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import fi.hiit.complesense.audio.AudioStreamClient;
import fi.hiit.complesense.core.SensorDataCollectionThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 28.10.2014.
 */
public class AsyncStreamClient extends AsyncClient
{
    public static final String TAG = AsyncStreamClient.class.getSimpleName();

    public AsyncStreamClient(ServiceHandler serviceHandler,
                             InetAddress serverSocketAddr, int port) throws IOException, JSONException
    {
        super(serviceHandler, serverSocketAddr, port);
    }

    @Override
    public void run()
    {
        Log.i(TAG, "Stream Client running at thread: " + Thread.currentThread().getId());
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
                close();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

    }

}
