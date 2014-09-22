package fi.hiit.complesense.connection;

import android.util.Log;

import java.net.SocketAddress;

import fi.hiit.complesense.core.AbstractSystemThread;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.SystemMessage;

/**
 * Created by hxguo on 9/22/14.
 */
public class AbstractUdpSocketHandler extends AbstractSystemThread
{

    private static final String TAG = "AbstractUdpSocketHandler";
    protected UdpConnectionRunnable udpConnRunnable;

    protected AbstractUdpSocketHandler(ServiceHandler serviceHandler)
    {
        super(serviceHandler);
        udpConnRunnable = null;
    }

    @Override
    public void stopThread() 
    {
        Log.i(TAG, "stopThread()");
        if(udpConnRunnable != null)
            udpConnRunnable.stopRunnable();
    }

    @Override
    public void pauseThread() {

    }

    public void write(SystemMessage systemMessage, SocketAddress remoteSocketAddr)
    {
        if(getConnectionRunnable() != null)
            getConnectionRunnable().write(systemMessage, remoteSocketAddr);
    }

    public UdpConnectionRunnable getConnectionRunnable() {
        return udpConnRunnable;
    }

}
