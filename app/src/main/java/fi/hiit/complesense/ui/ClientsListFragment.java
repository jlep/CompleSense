package fi.hiit.complesense.ui;

import android.app.Activity;
import android.app.ListFragment;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

import fi.hiit.complesense.R;

/**
 * Created by hxguo on 7/17/14.
 */
public class ClientsListFragment extends ListFragment
{
    private static final String TAG = "ClientsListFragment";
    View contentView;
    private Activity activity;
    private Button stopServer;
    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();

    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        //this.setListAdapter(new WiFiPeerListAdapter(getActivity(),
        //        R.layout.list_row_peers, peers));
        this.setListAdapter(new PeerListAdapter(getActivity(),
                R.layout.list_row_peers, peers));
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        Log.i(TAG, "onCreateView()");
        contentView = inflater.inflate(R.layout.list_frag_group_owner,container,false);
        stopServer = (Button)contentView.findViewById(R.id.server_stop);
        stopServer.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                getActivity().finish();
            }
        });


        return contentView;
    }

    public void updateList(WifiP2pGroup group)
    {
        Log.i(TAG,"updateList()");
        peers.clear();
        peers.addAll(group.getClientList());
        ((PeerListAdapter) getListAdapter()).notifyDataSetChanged();

        if (peers.size() == 0)
        {
            Log.d(TAG, "No devices found");
            return;
        }
    }

    public void clearPeers()
    {
        peers.clear();
        ((PeerListAdapter) getListAdapter()).notifyDataSetChanged();
    }

}
