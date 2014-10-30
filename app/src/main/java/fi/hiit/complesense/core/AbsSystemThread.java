package fi.hiit.complesense.core;

import android.os.HandlerThread;

/**
 * Created by hxguo on 30.10.2014.
 */
public abstract class AbsSystemThread extends HandlerThread
{
    protected final ServiceHandler serviceHandler;
    protected volatile boolean keepRunning;

    protected AbsSystemThread(String name, ServiceHandler serviceHandler)
    {
        super(name);
        this.serviceHandler = serviceHandler;
        keepRunning = true;
    }

    public abstract void stopThread();
}
