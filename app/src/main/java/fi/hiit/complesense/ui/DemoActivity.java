package fi.hiit.complesense.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.Serializable;
import java.net.SocketAddress;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.service.TestingService;
import fi.hiit.complesense.util.SystemUtil;


public class DemoActivity extends AbstractGroupActivity
{

    public static final String TAG = "DemoActivity";
    private Button stopButton;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.demo_activity_main);
        initUi((TextView) findViewById(R.id.status_text),
                (ScrollView) findViewById(R.id.scroll_text));

        uiMessenger = new Messenger(new IncomingHandler());

        stopButton = (Button)findViewById(R.id.stop_app);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mService!=null)
                {
                    try
                    {
                        Message msg = Message.obtain(null,
                                TestingService.STOP_TESTING);
                        msg.replyTo = uiMessenger;
                        mService.send(msg);
                    }
                    catch (RemoteException e)
                    {
                        // In this case the service has crashed before we could even
                        // do anything with it; we can count on soon being
                        // disconnected (and then reconnected if it can be restarted)
                        // so there is no need to do anything here.
                    }
                    finish();
                }
            }
        });

        /**
         * Class for interacting with the main interface of the service.
         */
        mConnection = new ServiceConnection()
        {
            public void onServiceConnected(ComponentName className,
                                           IBinder service)
            {
                mService = new Messenger(service);
                Log.i(TAG, "onServiceConnected()");
                try
                {
                    Message msg = Message.obtain(null,
                            TestingService.START_TESTING);
                    msg.replyTo = uiMessenger;
                    mService.send(msg);
                }
                catch (RemoteException e)
                {
                    // In this case the service has crashed before we could even
                    // do anything with it; we can count on soon being
                    // disconnected (and then reconnected if it can be restarted)
                    // so there is no need to do anything here.
                }
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

    @Override
    protected void onDestroy()
    {
        Log.v(TAG,"onDestroy()");
        super.onDestroy();
        stopService(new Intent(this, TestingService.class));
    }

    @Override
    protected void doBindService()
    {
        Log.v(TAG, "doBindService()");
        bindService(new Intent(getApplicationContext(),
                TestingService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        appendStatus("Binding to GroupOwnerService");
    }

    @Override
    public void onResume()
    {
        Log.v(TAG, "onResume()");
        doBindService();
        super.onResume();
    }

    @Override
    public void onPause()
    {
        Log.v(TAG, "onPause()");
        doUnbindService();
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.v(TAG,"onStop()");
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
                case Constants.MSG_TAKE_IMAGE:
                    SocketAddress socketAddress = (SocketAddress)msg.obj;
                    appendStatus("Receive image taking request from " + socketAddress.toString() );
                    Uri fileUri = SystemUtil.getOutputMediaFileUri(Constants.MEDIA_TYPE_IMAGE);
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri); // set the image file name

                    startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
