package fi.hiit.complesense.ui;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import fi.hiit.complesense.R;

/**
 * Created by hxguo on 6/17/14.
 */
public class PeerListAdapter extends ArrayAdapter<WifiP2pDevice>
{
    private List<WifiP2pDevice> items;
    private Context context;
    /**
     * @param context
     * @param textViewResourceId
     * @param objects
     */
    public PeerListAdapter(Context context, int textViewResourceId,
                           List<WifiP2pDevice> objects)
    {
        super(context, textViewResourceId, objects);
        items = objects;
        this.context = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        View v = convertView;
        if (v == null)
        {
            LayoutInflater vi = (LayoutInflater) context.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(R.layout.list_row_peers, null);
        }
        WifiP2pDevice device = items.get(position);
        if (device != null)
        {
            TextView text = (TextView) v.findViewById(R.id.peer_name);
            text.setText(device.deviceName);

            text = (TextView) v.findViewById(R.id.peer_mac_addr);
            text.setText(device.deviceAddress);

            text = (TextView) v.findViewById(R.id.peer_status);
            text.setText(SelfInfoFragment.getSelfStatus(device.status) );
        }
        return v;
    }
}
