package com.terainsights.a2q2r_android.activity;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.terainsights.a2q2r_android.R;

/**
 * Created by justin on 6/4/17.
 */

public class HistoryFragment extends Fragment{
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        return inflater.inflate(R.layout.history, container, false);
    }
}
