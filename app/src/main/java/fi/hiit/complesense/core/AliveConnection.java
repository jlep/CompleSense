package fi.hiit.complesense.core;

import android.os.CountDownTimer;
import android.util.Log;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Created by rocsea0626 on 20.9.2014.
 */
public class AliveConnection
{
    private static final String TAG = "AliveConnection";
    public final SocketAddress socketAddress;
    public final long startTime;
    private CountDownTimer countDownTimer;
    private final AliveConnectionListener mListener;

    public static final long VALID_TIME = 10000, COUNT_DOWN_INTERVAL = 1000;
    private long nextCheckTime;
    private long timeDiff;

    public interface AliveConnectionListener
    {
        public void onValidTimeExpires(SocketAddress socketAddress);
    }

    public AliveConnection(final SocketAddress socketAddress, AliveConnectionListener listener)
    {
        this.socketAddress = socketAddress;
        startTime = System.currentTimeMillis();
        mListener = listener;
        nextCheckTime = VALID_TIME;

        initCountDownTimer();
    }

    public void setTimeDiff(long timeDiff)
    {
        this.timeDiff = timeDiff;
    }

    public long getTimeDiff()
    {
        return this.timeDiff;
    }

    private void initCountDownTimer()
    {
        countDownTimer = new CountDownTimer(VALID_TIME, COUNT_DOWN_INTERVAL) {
            @Override
            public void onTick(long l) {
                Log.i(TAG, "time until next check " + l);
            }

            @Override
            public void onFinish() {
                mListener.onValidTimeExpires(socketAddress);
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

}
