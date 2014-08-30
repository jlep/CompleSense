package fi.hiit.complesense.core;

import android.os.Message;
import android.os.Messenger;
import android.util.Log;

/**
 * Created by hxguo on 21.8.2014.
 */
public class GroupOwnerServiceHandler extends ServiceHandler
{
    private static final String TAG = "GroupOwnerServiceHandler";

    public GroupOwnerServiceHandler(Messenger serviceMessenger, String name) {
        super(serviceMessenger, name);
    }

    @Override
    public boolean handleMessage(Message msg)
    {
        if(msg.what == SystemMessage.ID_SYSTEM_MESSAGE)
        {
            // received a SystemMessage
            Log.i(TAG, "recv: " + ((SystemMessage) msg.obj).toString());
        }

        return false;
    }
}
