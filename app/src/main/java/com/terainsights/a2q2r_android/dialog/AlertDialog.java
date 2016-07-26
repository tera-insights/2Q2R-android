package com.terainsights.a2q2r_android.dialog;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.terainsights.a2q2r_android.R;

/**
 * A simple error dialog alerting the user that the QR they scanned is not 2Q2R-compliant.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 6/16/16
 */
public class AlertDialog extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.alert_dialog);

        Bundle b = getIntent().getExtras();

        if (b != null) {
            ((TextView) findViewById(R.id.dialog_text)).setText(b.getString("data"));
        }

        findViewById(R.id.okay_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

    }

}
