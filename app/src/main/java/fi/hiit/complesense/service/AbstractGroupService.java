package fi.hiit.complesense.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.TreeMap;
import fi.hiit.complesense.Constants;
import fi.hiit.complesense.R;
import fi.hiit.complesense.core.CompleSenseDevice;
import fi.hiit.complesense.core.GroupBroadcastReceiver;
import fi.hiit.complesense.core.ServiceHandler;
import fi.hiit.complesense.core.WifiConnectionManager;
import fi.hiit.complesense.json.JsonSSI;
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
    protected ServiceHandler serviceHandler = null;
    protected Context context;

    protected WifiP2pDevice mDevice, groupOwner;

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    protected Messenger mMessenger, uiMessenger;
    protected final IntentFilter intentFilter = new IntentFilter();

    //protected Map<String, CompleSenseDevice> nearbyDevices;
    protected Map<String, CompleSenseDevice> discoveredDevices;
    protected PowerManager.WakeLock wakeLock;

    /**
     *
     * This BroadcastReceiver intercepts the android.net.ConnectivityManager.CONNECTIVITY_ACTION,
     * which indicates a connection change. It checks whether the type is TYPE_WIFI.
     * If it is, it checks whether Wi-Fi is connected and sets the wifiConnected flag in the
     * main activity accordingly.
     *
     */
    class NetworkReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            Log.i("NetworkReceiver", "onReceive()");
            ConnectivityManager connMgr =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo[] networkInfos = connMgr.getAllNetworkInfo();
            for(NetworkInfo ni:networkInfos)
            {
                Log.i("NetworkReceiver", ni.getTypeName());
                //if(ni.isAvailable())
                //    availableConns.add(ni.getType() );
            }
        }
    }

    @Override
    public void onCreate()
    {
        Log.v(TAG, "onCreate()");
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "WAKELOCK_" + TAG);
        wakeLock.acquire();
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        groupOwner = null;
        context = getApplicationContext();

        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        // Display a notification about us starting.
        showNotification();

        discoveredDevices = new TreeMap<String, CompleSenseDevice>();

        // add necessary intent values to be matched.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // Register BroadcastReceiver to track connection changes.
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        SystemUtil.cleanRootDir();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand() id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.i(TAG, "onBind()");
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG,"onDestroy()");
        wakeLock.release();
        stop();
        // Cancel the persistent notification.
        mNM.cancel(R.string.remote_service_started);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.remote_service_stopped, Toast.LENGTH_SHORT).show();
        super.onDestroy();
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

    public Map<String, CompleSenseDevice> getDiscoveredDevices()
    {
        return discoveredDevices;
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        mWifiConnManager.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    public void updateSelfInfo(WifiP2pDevice device)
    {
        Log.i(TAG,"updateSelfInfo(groupOwner: "+ device.toString() +")");
        mDevice = device;
        Map<String, String> txtRecord = SystemUtil.generateTxtRecord(this);
        discoveredDevices.put(mDevice.deviceAddress, new CompleSenseDevice(mDevice, txtRecord));

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

    public float getBatteryLevel()
    {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, ifilter);

        //are we charging / charged?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        //how are we charging
        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        Log.i(TAG, "Batteray level: " + level);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        Log.i(TAG, "Batteray scale: " + scale);

        float batteryPct = level / (float)scale;
        //get battery temperatur
        int temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);

        //get battery voltage
        int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

        return batteryPct;
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
