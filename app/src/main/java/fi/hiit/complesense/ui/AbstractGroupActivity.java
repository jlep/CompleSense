package fi.hiit.complesense.ui;

import android.app.Activity;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import fi.hiit.complesense.Constants;

/**
 * Created by hxguo on 7/17/14.
 */
public abstract class AbstractGroupActivity extends Activity
{
    private static final String TAG = "AbstractGroupActivity";

    protected TextView statusTxtView;
    protected ScrollView scrollView;
    protected boolean mIsBound;
    protected SelfInfoFragment selfInfoFragment = null;
    protected Messenger uiMessenger = null;

    /** Messenger for communicating with service. */
    protected Messenger mService = null;
    protected final String outputFile = Constants.ROOT_DIR +
            Long.toString(System.currentTimeMillis()) + ".textview";

    protected ServiceConnection mConnection = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

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

    @Override
    protected void onDestroy()
    {
        Log.d(TAG, "onDestroy()");
        dumpTextView2File(statusTxtView.getText().toString() );
        super.onDestroy();
    }

    protected abstract void doBindService();
    protected abstract void initUi(TextView statusTxtView, ScrollView statusTxtScroll);

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

    /**
    protected void appendStatus(String status)
    {
        String current = statusTxtView.getText().toString();
        statusTxtView.setText(current + "\n" + status);
    }
    */
    protected void appendStatus (String txt)
    {
        int maxLines = statusTxtView.getMaxLines();
        if(statusTxtView.getLineCount() >= maxLines)
        {
            StringBuffer sb = new StringBuffer(statusTxtView.getText());
            // delete lines
            int numLinesDel = (int)(maxLines*0.8);
            int offset = 0;
            for(int i=0;i<numLinesDel;++i)
            {
                //Log.i(TAG, "offset: " + offset);
                offset = sb.indexOf("\n",offset)+1;
            }
            dumpTextView2File(sb.subSequence(0, offset).toString() );

            //Log.i(TAG,"offset: " + sb.substring(0,offset));
            statusTxtView.setText(sb.subSequence(offset, sb.length()));
        }
        statusTxtView.append(txt + "\n");
        scrollView.fullScroll(View.FOCUS_DOWN);
    }

    private void dumpTextView2File(String str)
    {
        Log.i(TAG, "dumpTextView2File("+ outputFile +")");
        try {
            BufferedWriter bfw = new BufferedWriter(new FileWriter(outputFile, true) );
            bfw.write(str);
            bfw.flush();
            bfw.close();
        } catch (IOException e) {
            Log.i(TAG,e.toString() );
        }
    }

    protected void updateSelfInfoFragment(Message msg)
    {
        Log.i(TAG, "updateSelfInfoFragment()");
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
