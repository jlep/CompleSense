package fi.hiit.complesense.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.Serializable;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.SystemUtil;
import fi.hiit.complesense.service.TestingService;


public class DemoActivity extends Activity
{

    public static final String TAG = "DemoActivity";

    private TextView statusTxtView;
    private Button stopButton;
    private final IntentFilter intentFilter = new IntentFilter();

    private boolean mIsBound;
    TestingService mService;

    IncomingHandler uiHandler = new IncomingHandler();
    Messenger uiMessenger = new Messenger(uiHandler);

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection()
    {
        public void onServiceConnected(ComponentName className,
                                       IBinder service)
        {
            TestingService.LocalBinder binder = (TestingService.LocalBinder)service;
            mService = binder.getService();
            mIsBound = true;
            Log.i(TAG, "onServiceConnected()");
            appendStatus("Attached to TestingService");
            //mService.startTesting(uiMessenger, TestingService.NUM_CLIENTS);
            //mService.testSensorListParsing();
        }

        public void onServiceDisconnected(ComponentName className)
        {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            mIsBound = false;
            appendStatus("Disconnected from GroupClientService");

            /**
             // As part of the sample, tell the user what happened.
             Toast.makeText(GroupClientActivity.this, R.string.remote_service_disconnected,
             Toast.LENGTH_SHORT).show();
             */
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.demo_activity_main);
        statusTxtView = (TextView) findViewById(R.id.status_text);
        statusTxtView.setMovementMethod(new ScrollingMovementMethod());
        statusTxtView.setText("");

        stopButton = (Button)findViewById(R.id.stop_app);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mService!=null)
                {
                    mService.stopTesting();
                    mService.stopSelf();
                    finish();
                }
            }
        });

        intentFilter.addAction(Constants.SELF_INFO_UPDATE_ACTION);
        intentFilter.addAction(Constants.STATUS_TEXT_UPDATE_ACTION);

        Intent intent = new Intent(this, TestingService.class);
        String serviceName = TestingService.class.getCanonicalName();
        if(!SystemUtil.isServiceRunning(serviceName, this))
        {
            Log.d(TAG,"service is not running");
            startService(intent);
        }
        else
        {
            Log.d(TAG,"service is running");
        }
    }

    void doBindService()
    {
        Log.i(TAG, "doBindService()");
        Intent intent =new Intent(getApplicationContext(),
                TestingService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        appendStatus("Binding to GroupOwnerService");
    }

    void doUnbindService()
    {
        Log.i(TAG,"doUnbindService()");
        if (mIsBound)
        {
            unbindService(mConnection);
            mIsBound = false;
            appendStatus("Unbinding from GroupOwnerService");
        }
    }

    @Override
    public void onResume()
    {
        Log.d(TAG, "onResume()");
        doBindService();
        super.onResume();
    }

    public void appendStatus(String status)
    {
        String current = statusTxtView.getText().toString();
        statusTxtView.setText(current + "\n" + status);
        statusTxtView.computeScroll();
    }

    @Override
    public void onPause()
    {
        Log.d(TAG, "onPause()");
        doUnbindService();
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.i(TAG,"onStop()");
        super.onStop();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    class IncomingHandler extends Handler implements Serializable
    {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what)
            {
                case Constants.MSG_UPDATE_STATUS_TXT:
                    appendStatus((String)msg.obj);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
