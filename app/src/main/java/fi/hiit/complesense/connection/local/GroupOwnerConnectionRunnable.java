package fi.hiit.complesense.connection.local;

import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.net.Socket;
import java.util.Date;
import java.util.List;

import fi.hiit.complesense.core.SystemMessage;
import fi.hiit.complesense.connection.AbstractConnectionRunnable;
import fi.hiit.complesense.core.GroupOwnerManager;
import fi.hiit.complesense.core.ScheduledQueryTask;

/**
 * Created by hxguo on 7/14/14.
 */
public class GroupOwnerConnectionRunnable extends AbstractConnectionRunnable
{
    private String TAG = "GroupOwnerConnectionRunnable";
    private GroupOwnerManager groupOwnerManager;
    private final String remoteSocketAddr;

    public GroupOwnerConnectionRunnable(Socket s, GroupOwnerManager groupOwnerManager,
                                        Messenger remoteHandler, String remoteSocketAddr)
            throws IOException
    {
        super(s, remoteHandler);
        this.groupOwnerManager = groupOwnerManager;
        this.remoteSocketAddr = remoteSocketAddr;
    }


    @Override
    public void run()
    {
        Log.i(TAG, "run()");
        //requestBarometerValues();

        Log.i(TAG,"Query available sensors on the connected client");
        write(SystemMessage.makeSensorsListQueryMessage() );

        while (!Thread.currentThread().isInterrupted())
        {
            try
            {
                Object o = iStream.readObject();
                if(o instanceof Date)
                    Log.i(TAG,"Date: " + ((Date)o).toString());

                if(o instanceof SystemMessage)
                {
                    //Log.i(TAG,"REC: " + o.toString() );
                    parseSystemMessage((SystemMessage) o);
                }

                if(Thread.currentThread().isInterrupted())
                    throw new InterruptedException();

            } catch (IOException e)
            {
                Log.e(TAG, "disconnected" + e.toString());
                break;
            } catch (InterruptedException e)
            {
                Log.e(TAG, e.toString());
                break;
            } catch (ClassNotFoundException e) {
                Log.i(TAG,e.toString());
            }
        }
        Log.w(TAG,"Connection with "+ remoteSocketAddr +" Terminates!!!");
    }

    @Override
    protected void parseSystemMessage(SystemMessage sm)
    {
        float[] values;
        int type;
        switch (sm.getCmd())
        {
            case SystemMessage.R:
                break;

            case SystemMessage.V:
                values = SystemMessage.parseSensorValues(sm);
                type = SystemMessage.parseSensorType(sm);
                groupOwnerManager.setSensorValues(values, type, remoteSocketAddr);
                try {
                    updateStatusTxt(remoteSocketAddr + "->: " + sm.toString());
                } catch (RemoteException e) {
                    Log.i(TAG,e.toString());
                }
                break;

            case SystemMessage.N:
                List<Integer> typeList = SystemMessage.parseSensorTypeList(sm);

                groupOwnerManager.registerSensors(remoteSocketAddr, typeList);
                Log.i(TAG,remoteSocketAddr + ":" +
                        groupOwnerManager.getSensorsList(remoteSocketAddr).toString());
                //Log.i(TAG,SystemMessage.parseSensorTypeList(sm).toString() );

                int sType = groupOwnerManager.randomlySelectSensor(typeList, remoteSocketAddr);

                ScheduledQueryTask sTask = new ScheduledQueryTask(this, groupOwnerManager);
                timer.schedule(sTask, 0, 2000);

                //SystemMessage queryMessage = SystemMessage.makeSensorDataQueryMessage(sType);
                //write(queryMessage);
                break;
            default:
                break;
        }

    }

    public String getRemoteSocketAddr() {
        return remoteSocketAddr;
    }


}
