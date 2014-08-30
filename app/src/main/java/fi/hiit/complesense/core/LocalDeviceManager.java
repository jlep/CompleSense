package fi.hiit.complesense.core;

import android.os.Messenger;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.connection.Acceptor;
import fi.hiit.complesense.connection.Connector;

/**
 * Created by hxguo on 21.8.2014.
 */
public class LocalDeviceManager
{
    private static final String TAG = "LocalDeviceManager";
    private final Messenger serviceMessenger;
    private final boolean isGroupOwner;

    Map<String, AbstractSystemThread> managerThreads;
    ServiceHandler serviceHandler;

    public LocalDeviceManager(Messenger serviceMessenger,
                              ServiceHandler serviceHandler, InetAddress ownerAddr)
    {
        managerThreads = new TreeMap<String, AbstractSystemThread>();
        this.serviceMessenger = serviceMessenger;
        isGroupOwner = (ownerAddr==null) ? true:false;

        this.serviceHandler = serviceHandler;
        init(ownerAddr);
    }

    public LocalDeviceManager(Messenger serviceMessenger, ServiceHandler serviceHandler,
                              BatteryManager bm, AbstractSystemThread connectionManager, MySensorManager sm)
    {
        managerThreads = new TreeMap<String, AbstractSystemThread>();
        this.serviceMessenger = serviceMessenger;

        this.serviceHandler = serviceHandler;
        //managerThreads.put(bm.TAG, bm);
        //managerThreads.put(sm.TAG, sm);
        if(connectionManager instanceof Acceptor)
        {
            isGroupOwner = true;
            managerThreads.put(Acceptor.TAG, connectionManager);
        }
        else
        {
            isGroupOwner = false;
            managerThreads.put(Connector.TAG, connectionManager);
        }
    }



    private void init(InetAddress ownerAddr)
    {
        Log.i(TAG,"init()");

        //BatteryManager bm = new BatteryManager(serviceHandler);
        //managerThreads.put(bm.TAG, bm);

        try
        {
            if(isGroupOwner)
            {
                Acceptor cm = new Acceptor(serviceMessenger, serviceHandler);
                managerThreads.put(cm.TAG, cm);
            }
            else
            {
                Connector cm = new Connector(serviceMessenger, serviceHandler, ownerAddr, 500);
                managerThreads.put(cm.TAG, cm);
            }
        }
        catch (IOException e)
        {
            Log.e(TAG,e.toString());
            return;
        }


        //MySensorManager sm = new MySensorManager(serviceHandler);
        //managerThreads.put(sm.TAG, sm);
    }



    public void start()
    {
        serviceHandler.start();

        Iterator<Map.Entry<String, AbstractSystemThread>> iterator
                = managerThreads.entrySet().iterator();

        while(iterator.hasNext())
        {
            iterator.next().getValue().start();
        }
    }

    public void stop()
    {
        serviceHandler.interrupt();

        Iterator<Map.Entry<String, AbstractSystemThread>> iterator
                = managerThreads.entrySet().iterator();
        while(iterator.hasNext())
            iterator.next().getValue().stopThread();
    }


}
