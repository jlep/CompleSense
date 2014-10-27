package fi.hiit.complesense.connection;

import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 27.10.2014.
 */
public abstract class AbsAsyncIO extends Thread
{
    protected final ServiceHandler serviceHandler;
    protected volatile boolean keepRunning = true;

    protected AbsAsyncIO(ServiceHandler serviceHandler)
    {
        this.serviceHandler = serviceHandler;
    }
}
