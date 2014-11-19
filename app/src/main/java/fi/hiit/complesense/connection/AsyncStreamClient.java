package fi.hiit.complesense.connection;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import fi.hiit.complesense.audio.AudioStreamClient;
import fi.hiit.complesense.core.ClientServiceHandler;
import fi.hiit.complesense.core.SensorDataCollectionThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.util.SensorUtil;

/**
 * Created by hxguo on 28.10.2014.
 */
public class AsyncStreamClient extends AsyncClient
{
    public static final String TAG = AsyncStreamClient.class.getSimpleName();
    private final CountDownLatch countDownLatch;

    public AsyncStreamClient(ClientServiceHandler clientServiceHandler,
                             InetAddress ownerAddr, int port, CountDownLatch latch) throws IOException
    {
        super(clientServiceHandler, ownerAddr, port);
        this.countDownLatch = latch;
    }

    @Override
    public void run()
    {
        try{
            socketChannel = this.initiateConnection();
            countDownLatch.countDown();
            Log.i(TAG, "StreamClient running at thread: " + Thread.currentThread().getId());

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
            Log.v(TAG, e.toString());
        }catch(CancelledKeyException e){
            Log.v(TAG, e.toString());
        }
        catch (ClosedChannelException e) {
            Log.v(TAG, e.toString());
        } catch (IOException e) {
            Log.i(TAG, e.toString());
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
