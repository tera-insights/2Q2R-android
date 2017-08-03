package com.terainsights.a2q2r_android.activity;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.util.HistoryAdapter;
import com.terainsights.a2q2r_android.util.KeyAdapter;
import com.terainsights.a2q2r_android.util.KeyDatabase;
import com.terainsights.a2q2r_android.util.U2F;

/**
 * Created by justin on 6/4/17.
 */

public class HistoryFragment extends Fragment{
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View view = inflater.inflate(R.layout.history, container, false);
        ListView registrations = (ListView) view.findViewById(R.id.history_view);

        KeyDatabase.HISTORY_ADAPTER = new HistoryAdapter(getActivity(), R.layout.history_item);
        U2F.DATABASE.refreshHistory();
        registrations.setAdapter(KeyDatabase.HISTORY_ADAPTER);
        return view;
    }
}
