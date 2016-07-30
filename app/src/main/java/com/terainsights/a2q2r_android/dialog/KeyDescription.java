package com.terainsights.a2q2r_android.dialog;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.terainsights.a2q2r_android.R;

/**
 * Controller for a key description dialog that pops up
 * when the user clicks on a key in MainActivity's ListView.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/29/16
 */
public class KeyDescription extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.key_dialog);

        Bundle data = getIntent().getExtras();

        ((TextView) findViewById(R.id.user_id)).setText(data.getString("userID"));
        ((TextView) findViewById(R.id.app_name)).setText(data.getString("appName"));
        ((TextView) findViewById(R.id.base_url)).setText(data.getString("baseURL"));

    }

}
