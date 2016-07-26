package com.terainsights.a2q2r_android.util;

import android.content.Context;
import android.view.Gravity;
import android.widget.Toast;

/**
 * A convenience class for displaying quick Toast dialogs.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/26/16
 */
public class Text {

    public static void displayShort(Context ctx, String text) {

        Toast dialog = Toast.makeText(ctx, text, Toast.LENGTH_SHORT);
        dialog.setGravity(Gravity.CENTER, 0, 0);
        dialog.show();

    }

    public static void displayLong(Context ctx, String text) {

        Toast dialog = Toast.makeText(ctx, text, Toast.LENGTH_LONG);
        dialog.setGravity(Gravity.CENTER, 0, 0);
        dialog.show();

    }

    public static void displayShort(Context ctx, int resID) {

        Toast dialog = Toast.makeText(ctx, ctx.getString(resID), Toast.LENGTH_SHORT);
        dialog.setGravity(Gravity.CENTER, 0, 0);
        dialog.show();

    }

    public static void displayLong(Context ctx, int resID) {

        Toast dialog = Toast.makeText(ctx, ctx.getString(resID), Toast.LENGTH_LONG);
        dialog.setGravity(Gravity.CENTER, 0, 0);
        dialog.show();

    }

}
