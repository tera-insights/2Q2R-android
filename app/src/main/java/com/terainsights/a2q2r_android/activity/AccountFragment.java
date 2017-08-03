package com.terainsights.a2q2r_android.activity;

import android.app.Fragment;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.dialog.KeyDescription;
import com.terainsights.a2q2r_android.util.KeyAdapter;
import com.terainsights.a2q2r_android.util.KeyDatabase;
import com.terainsights.a2q2r_android.util.U2F;

/**
 * Created by justin on 6/4/17.
 */

public class AccountFragment extends Fragment implements AdapterView.OnItemClickListener {
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View view = inflater.inflate(R.layout.accounts, container, false);
        ListView registrations = (ListView) view.findViewById(R.id.registrations_view);
        registrations.setOnItemClickListener(this);

        KeyDatabase.KEY_ADAPTER = new KeyAdapter(getActivity(), R.layout.registration_item);
        U2F.DATABASE.refreshKeyInfo();
        registrations.setAdapter(KeyDatabase.KEY_ADAPTER);
        return view;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {

        Intent intent = new Intent(getActivity(), KeyDescription.class);
        Cursor cursor = KeyDatabase.KEY_ADAPTER.getCursor();
        cursor.moveToPosition(pos);

        intent.putExtra("userID", cursor.getString(cursor.getColumnIndex("userID")));
        intent.putExtra("appName", cursor.getString(cursor.getColumnIndex("appName")));
        intent.putExtra("appURL", cursor.getString(cursor.getColumnIndex("appURL")));

        startActivity(intent);

    }
}
