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
    private CountDownTimer countDownTimer;
    private final AliveConnectionListener mListener;

    public static final long VALID_TIME = 1000000, COUNT_DOWN_INTERVAL = 1000;
    private long nextCheckTime;
    private long delay = 0;

    public interface AliveConnectionListener
    {
        public void onConnectionTimeout(WebSocket webSocket);
    }

    public AliveConnection(final WebSocket webSocket, AliveConnectionListener listener)
    {

        this.webSocket = webSocket;
        startTime = System.currentTimeMillis();
        mListener = listener;
        nextCheckTime = VALID_TIME;

        initCountDownTimer();
    }

    public void setDelay(long timeDiff)
    {
        this.delay = timeDiff;
    }

    public long getDelay()
    {
        return this.delay;
    }

    private void initCountDownTimer()
    {
        countDownTimer = new CountDownTimer(VALID_TIME, COUNT_DOWN_INTERVAL) {
            @Override
            public void onTick(long l) {
                //Log.i(TAG, "time until next check " + l);
            }

            @Override
            public void onFinish() {
                mListener.onConnectionTimeout(webSocket);
            }
        };
        countDownTimer.start();
    }

    public void resetCheckTime()
    {
        if(countDownTimer != null)
        {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        initCountDownTimer();
    }


    public WebSocket getWebSocket() {
        return webSocket;
    }
}
