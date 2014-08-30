package fi.hiit.complesense.core;

/**
 * Created by hxguo on 21.8.2014.
 */
public abstract class AbstractSystemThread extends Thread
{
    protected final ServiceHandler serviceHandler;
    public enum STATE {RUNNING, STOPPED, PAUSED};

    private volatile STATE state = STATE.STOPPED;

    protected AbstractSystemThread(ServiceHandler serviceHandler)
    {
        this.serviceHandler = serviceHandler;
    }

    public abstract void stopThread();
    public abstract void pauseThread();

    public STATE state()
    {
        return state;
    }

    public void state(STATE state)
    {
        this.state = state;
    }

}
