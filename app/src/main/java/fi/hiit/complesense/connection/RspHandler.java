package fi.hiit.complesense.connection;

import android.util.Log;

/**
 * Created by hxguo on 27.10.2014.
 */
public class RspHandler
{
    private static final String TAG = RspHandler.class.getSimpleName();
    private byte[] rsp = null;

    public synchronized boolean handleResponse(byte[] rsp)
    {
        this.rsp = rsp;
        this.notify();
        return true;
    }

    public synchronized void waitForResponse() {
        while(this.rsp == null) {
            try {
                this.wait();
            } catch (InterruptedException e) {
            }

        }
        Log.i(TAG, new String(this.rsp));

    }
}
