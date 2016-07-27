package com.terainsights.a2q2r_android.dialog;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.terainsights.a2q2r_android.R;

public class FCMDialog extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.fcm_auth);

        Bundle extras = getIntent().getExtras();

        if (extras != null) {

            ((TextView) findViewById(R.id.server_name)).setText(extras.getString("serverName"));
            ((TextView) findViewById(R.id.server_url)).setText(extras.getString("serverURL"));

        }

        findViewById(R.id.okay_button).setOnClickListener(this);
        findViewById(R.id.cancel_button).setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.okay_button:
                setResult(RESULT_OK);
                finish();
                break;

            case R.id.cancel_button:
                setResult(RESULT_CANCELED);
                finish();
                break;

        }

    }

}
