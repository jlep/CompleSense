package fi.hiit.complesense.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import java.util.Map;
import java.util.TreeMap;

import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.core.ComleSenseDevice;
import fi.hiit.complesense.core.LocalManager;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.WifiConnectionManager;
import fi.hiit.complesense.ui.DemoActivity;
import fi.hiit.complesense.util.SystemUtil;

/**
 * Created by hxguo on 7/17/14.
 * Class to handle the DNS-SD connection me steps
 */
public abstract class AbstractGroupService extends Service
    implements WifiP2pManager.ConnectionInfoListener
{
    private static final String TAG = "AbstractGroupService";


    protected NotificationManager mNM;
    protected boolean isInitialized = false;
    protected WifiP2pManager manager;
    protected WifiP2pManager.Channel channel;

    protected GroupBroadcastReceiver receiver;
    protected WifiConnectionManager mWifiConnManager;
    //protected LocalManager localManager = null;
    protected ServiceHandler serviceHandler = null;

    protected WifiP2pDevice mDevice, groupOwner;

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    protected Messenger mMessenger, uiMessenger;
    protected final IntentFilter intentFilter = new IntentFilter();

    protected Map<String, ComleSenseDevice> nearbyDevices;

    @Override
    public void onCreate()
    {
        Log.i(TAG, "onCreate()");
        super.onCreate();
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        groupOwner = null;

        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        // Display a notification about us starting.
        showNotification();

        nearbyDevices = new TreeMap<String, ComleSenseDevice>();

        // add necessary intent values to be matched.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // Register BroadcastReceiver to track connection changes.
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);

    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.i(TAG, "onBInd()");
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG,"onDestroy()");
        super.onDestroy();
        stop();
        // Cancel the persistent notification.
        mNM.cancel(R.string.remote_service_started);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.remote_service_stopped, Toast.LENGTH_SHORT).show();
    }

    protected abstract void start();

    protected abstract void stop();

    protected void resetData()
    {
        Log.i(TAG,"resetData()");
        mDevice = null;
        mMessenger = null;
        uiMessenger = null;
        groupOwner = null;
        serviceHandler = null;
        isInitialized = false;
    }

    public WifiP2pDevice getDevice()
    {
        return mDevice;
    }

    public Map<String, ComleSenseDevice> getNearbyDevices()
    {
        return nearbyDevices;
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        mWifiConnManager.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    public void updateSelfInfo(WifiP2pDevice device)
    {
        Log.i(TAG,"updateSelfInfo()");
        mDevice = device;
        SystemUtil.sendSelfInfoUpdate(uiMessenger, mDevice);
    }

    protected void showNotification()
    {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.remote_service_started);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_launcher, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, DemoActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.remote_service_client),
                text, contentIntent);

        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNM.notify(R.string.remote_service_started, notification);

    }

    protected void sendServiceInitComplete()
    {
        Message msg = Message.obtain();
        msg.what = Constants.MSG_SERVICE_INIT_DONE;
        try {
            uiMessenger.send(msg);
        } catch (RemoteException e) {
            Log.i(TAG,e.toString());
        }
    }


}
