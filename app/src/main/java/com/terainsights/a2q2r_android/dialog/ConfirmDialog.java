package com.terainsights.a2q2r_android.dialog;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.terainsights.a2q2r_android.R;

/**
 * Controller for a confirmation dialog, which should always be started for a
 * result, and will return a status code describing the user's decision.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/24/16
 */
public class ConfirmDialog extends Activity implements View.OnClickListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.confirm_dialog);

        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            ((TextView) findViewById(R.id.dialog_text)).setText(extras.getString("data"));
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
