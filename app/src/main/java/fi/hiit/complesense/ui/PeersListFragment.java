package fi.hiit.complesense.ui;

import android.app.Activity;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.net.wifi.p2p.WifiP2pDevice;
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
 * Created by hxguo on 7/25/14.
 */
public class PeersListFragment extends ListFragment
{
    private static final String TAG = "PeersListFragment";
    View contentView;
    private Activity activity;
    private List<WifiP2pDevice> servsers = new ArrayList<WifiP2pDevice>();
    ProgressDialog progressDialog = null;
    PeerListAdapter listAdapter = null;
    private Button stopClient;

    public interface PeerOnClickListener
    {
        public void cancelConnect();
        public void stopServiceDiscovery();
    }

    @Override
    public void onAttach(Activity activity)
    {
        Log.i(TAG, "onAttach()");
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState)
    {
        Log.i(TAG,"oncreateView()");
        contentView = inflater.inflate(R.layout.list_frag_group_client,container,false);
        stopClient = (Button)contentView.findViewById(R.id.stop_service_discovery);
        stopClient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.finish();
            }
        });
        return contentView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        //listAdapter = new WiFiServerListAdapter(getActivity(),
        //        R.layout.list_row_peers, services);
        listAdapter = new PeerListAdapter(getActivity(),
                R.layout.list_row_peers, servsers);
        this.setListAdapter(listAdapter);
    }

    public void dimissDialog()
    {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

}
