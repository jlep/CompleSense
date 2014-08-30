package fi.hiit.complesense.connection;

import fi.hiit.complesense.core.ServiceHandler;

/**
 * Created by hxguo on 20.8.2014.
 */
public abstract class AbstractSocketHandler extends Thread
{
    private static final String TAG = "AbstractSocketHandler";
    protected ServiceHandler serviceHandler;

    protected AbstractSocketHandler(ServiceHandler serviceHandler)
    {
        this.serviceHandler = serviceHandler;
    }

    @Override
    public abstract void run();
    public abstract void stopHandler();
}
