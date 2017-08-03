package com.terainsights.a2q2r_android.util;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.terainsights.a2q2r_android.R;

/**
 * Created by justin on 6/6/17.
 */

public class HistoryAdapter extends ResourceCursorAdapter {
    public HistoryAdapter(Context context, int layout){
        super(context, layout, null, 0);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        String userID = cursor.getString(cursor.getColumnIndex("userID"));
        String appName = cursor.getString(cursor.getColumnIndex("appName"));
        String dateTime = cursor.getString(cursor.getColumnIndex("dateTime"));

        String date = dateTime.split(" ")[0];
        String time = dateTime.split(" ")[1];
        date = (date.startsWith("0")) ? date.substring(1) : date;
        time = (time.startsWith("0")) ? time.substring(1) : time;
        String lastUsed = context.getResources().getString(R.string.history_used, date, time);

        int icon;
//        TODO: Implement server icon; Leaving as default for now
//        if(server icon exists){
//          icon = server icon
//        else
        icon = R.mipmap.key_icon;

        ((ImageView) view.findViewById(R.id.history_server_icon)).setImageResource(icon);
        ((TextView) view.findViewById(R.id.history_user_id)).setText(userID);
        ((TextView) view.findViewById(R.id.history_app_name)).setText(appName);
        ((TextView) view.findViewById(R.id.history_used)).setText(lastUsed);
    }
}
