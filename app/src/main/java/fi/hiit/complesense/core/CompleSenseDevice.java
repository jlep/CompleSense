package fi.hiit.complesense.core;

import android.net.wifi.p2p.WifiP2pDevice;

import java.util.Map;

/**
 * Created by rocsea0626 on 2.10.2014.
 */
public class CompleSenseDevice
{
    private static final String TAG = "CompleSenseDevice";
    private WifiP2pDevice device;
    private Map<String, String> txtRecord;

    public CompleSenseDevice(WifiP2pDevice device, Map txtRecord)
    {
        this.device = device;
        this.txtRecord = txtRecord;
    }


    public Map<String, String> getTxtRecord() {
        return txtRecord;
    }

    public WifiP2pDevice getDevice() {
        return device;
    }
}
