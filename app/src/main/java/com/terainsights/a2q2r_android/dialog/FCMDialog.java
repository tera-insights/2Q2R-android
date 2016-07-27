package com.terainsights.a2q2r_android.dialog;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.util.KeyManager;
import com.terainsights.a2q2r_android.util.U2F;

import java.io.File;
import java.io.IOException;

public class FCMDialog extends Activity implements View.OnClickListener {

    private String authData = null;
    private KeyManager km;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.fcm_auth);

        Bundle extras = getIntent().getExtras();

        try {

            File registrationsFile = new File(getFilesDir(), "registrations.json");
            km = new KeyManager(registrationsFile, registrationsFile.createNewFile());

        } catch (IOException e) {

            finish();

        }

        if (extras != null) {

            ((TextView) findViewById(R.id.server_name)).setText(extras.getString("serverName")
                .replace('_', ' '));
            ((TextView) findViewById(R.id.server_url)).setText(extras.getString("serverURL"));

            authData = extras.getString("authData");

        }

        findViewById(R.id.okay_button).setOnClickListener(this);
        findViewById(R.id.cancel_button).setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.okay_button:
                U2F.process(authData, km, getApplicationContext());
                finish();
                break;

            case R.id.cancel_button:
                finish();
                break;

        }

    }

}
