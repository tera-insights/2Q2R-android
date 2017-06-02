package com.terainsights.a2q2r_android.util;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Icon;
import android.media.Image;
import android.view.View;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.terainsights.a2q2r_android.R;

/**
 * Serves to translate key data from the internal U2F database into the
 * options activity's visual ListView.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 8/19/16
 */
public class KeyAdapter extends ResourceCursorAdapter {

    /**
     * Convenience constructor that doesn't auto refresh its cursor
     * in the U2F database, to save performance.
     * @param context The context of the ListView to populate.
     * @param layout The ID of the ListView's row template.
     */
    public KeyAdapter(Context context, int layout) {
        super(context, layout, null, 0);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {

        String userID = cursor.getString(cursor.getColumnIndex("userID"));
        String appName = cursor.getString(cursor.getColumnIndex("appName"));
        String dateTime = cursor.getString(cursor.getColumnIndex("lastUsed"));

        String date = dateTime.split(" ")[0];
        String time = dateTime.split(" ")[1];
        date = (date.startsWith("0")) ? date.substring(1) : date;
        time = (time.startsWith("0")) ? time.substring(1) : time;
        String lastUsed = context.getResources().getString(R.string.last_used, date, time);

        int icon;
//        TODO: Implement server icon; Leaving as default for now
//        if(server icon exists){
//          icon = server icon
//        else
            icon = R.mipmap.key_icon;

        ((ImageView) view.findViewById(R.id.server_icon)).setImageResource(icon);
        ((TextView) view.findViewById(R.id.user_id)).setText(userID);
        ((TextView) view.findViewById(R.id.app_name)).setText(appName);
        ((TextView) view.findViewById(R.id.last_used)).setText(lastUsed);

    }

}