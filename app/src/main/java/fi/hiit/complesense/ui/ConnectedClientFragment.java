package fi.hiit.complesense.ui;

import android.app.Activity;
import android.app.ListFragment;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import fi.hiit.complesense.R;

/**
 * Created by hxguo on 7/17/14.
 */
public class ConnectedClientFragment extends ListFragment
{
    private static final String TAG = "ConnectedClientFragment";
    private View contentView;
    private List<WifiP2pDevice> connectedPeers = new ArrayList<WifiP2pDevice>();
    private WifiP2pDevice groupOwner;

    public PeerListAdapter listAdapter;
    private Button disconnectButton;
    private Activity activity;

    /**
     * An interface-callback for the activity to listen to fragment interaction
     * events.
     */
    public interface ClientActionListener
    {
        void disconnect();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        Log.i(TAG, "onCreateView()");

        contentView = inflater.inflate(R.layout.list_frag_client_connected,
                container,false);
        disconnectButton = (Button)contentView.findViewById(R.id.disconnect_client);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((ClientActionListener)activity).disconnect();
            }
        });

        groupOwner = null;

        return contentView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        listAdapter = new PeerListAdapter(getActivity(),
                R.layout.list_row_peers, connectedPeers);
        this.setListAdapter(listAdapter);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id)
    {
        super.onListItemClick(l, v, position, id);
    }

    public void updateServerUI(WifiP2pDevice server)
    {
        groupOwner = server;
        if(groupOwner!=null)
        {
            TextView text = (TextView)contentView.findViewById(R.id.connected_serv_status);
            text.setText(SelfInfoFragment.getSelfStatus(groupOwner.status));

            text = (TextView)contentView.findViewById(R.id.connected_serv_name);
            text.setText(groupOwner.deviceName);

            text = (TextView)contentView.findViewById(R.id.connected_serv_mac_addr);
            text.setText(groupOwner.deviceAddress);
        }
    }

    public void resetView()
    {

    }
}
