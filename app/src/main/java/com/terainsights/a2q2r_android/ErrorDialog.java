package com.terainsights.a2q2r_android;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * A simple error dialog alerting the user that the QR they scanned is not 2Q2R-compliant.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 6/16/16
 */
public class ErrorDialog extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog);

        Bundle b = getIntent().getExtras();

        if (b != null) {
            ((TextView) findViewById(R.id.invalid_qr)).setText(b.getString("info"));
        }

        findViewById(R.id.okay_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

    }

}
