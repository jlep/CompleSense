package fi.hiit.complesense.ui;

import android.app.Fragment;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import fi.hiit.complesense.R;


/**
 * Created by hxguo on 6/14/14.
 */
public class SelfInfoFragment extends Fragment
{
    private static final String TAG = "SelfInfoFragment";

    View contentView;

    public static final String SELF_INFO_FRAGMENT_TAG = "self_info_tag";
    private WifiP2pDevice device;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        Log.i(TAG, "onCreateView()");
        contentView = inflater.inflate(R.layout.frag_me_info, container, false);
        return contentView;
    }

    public void resetUI()
    {
        TextView text = (TextView) contentView.findViewById(R.id.me_name);
        text.setText("");

        text = (TextView) contentView.findViewById(R.id.me_status);
        text.setText("");

        text = (TextView) contentView.findViewById(R.id.me_group_owner);
        text.setText("");

        text = (TextView) contentView.findViewById(R.id.me_mac_addr);
        text.setText("");

        text = (TextView) contentView.findViewById(R.id.me_type);
        text.setText("");
    }

    /**
     * @return this device
     */
    public WifiP2pDevice getDevice() {
        return device;
    }

    public void updateUI(WifiP2pDevice device)
    {
        if(device!=null)
        {
            this.device = device;
            Log.i(TAG, "updateUI()");
            TextView text = (TextView) contentView.findViewById(R.id.me_name);
            text.setText(device.deviceName);

            text = (TextView) contentView.findViewById(R.id.me_status);
            text.setText(getSelfStatus(device.status));

            text = (TextView) contentView.findViewById(R.id.me_group_owner);
            if(device.isGroupOwner())
                text.setText("Yes");
            else
                text.setText("No");

            text = (TextView) contentView.findViewById(R.id.me_mac_addr);
            text.setText(device.deviceAddress);

            text = (TextView) contentView.findViewById(R.id.me_type);
            text.setText(device.primaryDeviceType);
        }
        else
            Log.e(TAG,"device is null for selfInfoFragment");
    }


    public static String getSelfStatus(int deviceStatus)
    {
        switch (deviceStatus)
        {
            case WifiP2pDevice.AVAILABLE:
                Log.d(TAG, "getSelfStatus : Available");
                return "Available";
            case WifiP2pDevice.INVITED:
                Log.d(TAG, "getSelfStatus : Invited");
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                Log.d(TAG, "getSelfStatus : Connected");
                return "Connected";
            case WifiP2pDevice.FAILED:
                Log.d(TAG, "getSelfStatus : Failed");
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                Log.d(TAG, "getSelfStatus : Unavailable");
                return "Unavailable";
            default:
                return "Unknown";
        }
    }
}
