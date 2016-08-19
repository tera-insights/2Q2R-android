package com.terainsights.a2q2r_android.dialog;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.util.U2F;

import java.nio.ByteBuffer;

/**
 * Controller for a simple authentication confirmation dialog which displays
 * the server's name and 2Q2R domain.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 8/19/16
 */
public class AuthDialog extends Activity implements View.OnClickListener {

    private String authData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.auth_dialog);

        Bundle extras = getIntent().getExtras();

        if (extras != null) {

            ((TextView) findViewById(R.id.server_name)).setText(extras.getString("serverName")
                .replace('_', ' '));
            ((TextView) findViewById(R.id.server_url)).setText(extras.getString("serverURL"));

            authData = extras.getString("authData");
            int missed = extras.getInt("missed");

            if (missed > 0) {

                TextView tv = (TextView) findViewById(R.id.counter_warning);
                tv.setText("You've missed " + missed + " authentication attempts " +
                           "from this server!");

                if (missed >= 5)
                    tv.setTextColor(Color.RED);

                tv.setVisibility(View.VISIBLE);

            }

        }

        findViewById(R.id.okay_button).setOnClickListener(this);
        findViewById(R.id.cancel_button).setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.okay_button:
                U2F.process(authData);
                finish();
                break;

            case R.id.cancel_button:
                finish();
                break;

        }

    }

}
