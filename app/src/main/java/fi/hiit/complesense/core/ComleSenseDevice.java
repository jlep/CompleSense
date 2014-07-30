package fi.hiit.complesense.core;

import android.net.wifi.p2p.WifiP2pDevice;

import java.util.Map;

/**
 * Created by hxguo on 7/29/14.
 */
public class ComleSenseDevice
{
    private static final String TAG = "ComleSenseDevice";
    private final WifiP2pDevice device;
    private final Map txtRecord;

    public ComleSenseDevice(WifiP2pDevice device, Map txtRecord)
    {
        this.device = device;
        this.txtRecord = txtRecord;

    }


    public Map getTxtRecord() {
        return txtRecord;
    }

    public WifiP2pDevice getDevice() {
        return device;
    }
}
