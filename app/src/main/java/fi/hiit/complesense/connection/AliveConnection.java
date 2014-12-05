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

    private long delay = 0;
    private long rtt = 0;
    private double batteryLevel = Double.MIN_VALUE;

    public void setLocal(boolean isLocal) {
        this.isLocal = isLocal;
    }

    public boolean isLocal() {

        return isLocal;
    }

    private boolean isLocal = false;

    public void setRtt(long rtt) {
        this.rtt = rtt;
    }

    public long getRtt() {
        return rtt;
    }

    public AliveConnection(final WebSocket webSocket)
    {
        this.webSocket = webSocket;
    }

    public void setBatteryLevel(double batteryLevel) {
        this.batteryLevel = batteryLevel;
    }

    public double getBatteryLevel() {
        return batteryLevel;
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
