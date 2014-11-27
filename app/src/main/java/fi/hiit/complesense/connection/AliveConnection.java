package fi.hiit.complesense.connection;

import android.os.CountDownTimer;
import android.util.Log;

import com.koushikdutta.async.http.WebSocket;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Created by rocsea0626 on 20.9.2014.
 */
public class AliveConnection
{
    private static final String TAG = "AliveConnection";
    public final WebSocket webSocket;
    public final long startTime;

    public static final long VALID_TIME = 1000000, COUNT_DOWN_INTERVAL = 1000;
    private long nextCheckTime;
    private long delay = 0;
    private long rtt = 0;

    public void setRtt(long rtt) {
        this.rtt = rtt;
    }

    public long getRtt() {
        return rtt;
    }

    public AliveConnection(final WebSocket webSocket)
    {

        this.webSocket = webSocket;
        startTime = System.currentTimeMillis();
        nextCheckTime = VALID_TIME;
    }

    public void setDelay(long delay)
    {
        this.delay = delay;
    }

    public long getDelay()
    {
        return this.delay;
    }

    public WebSocket getWebSocket() {
        return webSocket;
    }
}
