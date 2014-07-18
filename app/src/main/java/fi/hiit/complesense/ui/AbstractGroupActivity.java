package fi.hiit.complesense.ui;

import android.app.Activity;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.TextView;

import fi.hiit.complesense.Constants;

/**
 * Created by hxguo on 7/17/14.
 */
public abstract class AbstractGroupActivity extends Activity
{
    private static final String TAG = "AbstractGroupActivity";

    protected TextView statusTxtView;
    protected boolean mIsBound;
    protected SelfInfoFragment selfInfoFragment;
    protected Messenger uiMessenger = null;

    /** Messenger for communicating with service. */
    protected Messenger mService = null;

    protected ServiceConnection mConnection = null;

    @Override
    public void onResume()
    {
        Log.d(TAG, "onResume()");
        super.onResume();
        doBindService();
    }

    @Override
    public void onPause()
    {
        Log.d(TAG, "onPause()");
        super.onPause();
        doUnbindService();
    }

    protected abstract void doBindService();

    protected void doUnbindService()
    {
        Log.i(TAG,"doUnbindService()");
        if (mIsBound)
        {
            unbindService(mConnection);
            mIsBound = false;
            appendStatus("Unbinding from GroupOwnerService");
        }
    }

    protected void appendStatus(String status)
    {
        String current = statusTxtView.getText().toString();
        statusTxtView.setText(current + "\n" + status);
    }

    protected void updateSelfInfoFragment(Message msg)
    {
        WifiP2pDevice device = (WifiP2pDevice)msg.obj;
        selfInfoFragment.updateUI(device);
    }

    protected void startServiceWork()
    {
        Log.i(TAG,"startServiceWork()");
        try
        {
            Message reply = Message.obtain();
            reply.what = Constants.SERVICE_MSG_START;
            reply.replyTo = uiMessenger;
            mService.send(reply);
        }
        catch (RemoteException e)
        {
        }
    }

}
