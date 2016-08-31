package com.terainsights.a2q2r_android.dialog;

import android.app.Activity;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.util.Text;
import com.terainsights.a2q2r_android.util.U2F;

/**
 * Controller for a simple authentication confirmation dialog which displays
 * the server's name and 2Q2R domain.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 8/31/16
 */
public class AuthDialog extends Activity implements View.OnClickListener {

    private String authData = null;

    private AsyncTask<Void, Void, Void> timer;
    private static long TIMEOUT_MILLIS = 60_000;
    private static long POLL_INTERVAL = 1_000;

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

            String challenge = authData.split(" ")[authData.charAt(0) == 'R' ? 1 : 2];
            ((TextView) findViewById(R.id.challenge_excerpt)).setText(challenge.substring(0, 4) +
                    "   " + challenge.substring(4, 8));
            ((TextView) findViewById(R.id.challenge_excerpt_extended)).setText(
                    challenge.substring(8, 12) + "   " +
                    challenge.substring(12, 16) + "   " +
                    challenge.substring(16, 20) + "   " +
                    challenge.substring(20, 24) + "   " +
                    challenge.substring(24, 28) + "   "
            );

            if (missed > 0) {

                TextView tv = (TextView) findViewById(R.id.counter_warning);
                tv.setText("You've missed " + missed + " authentication attempts " +
                           "from this server!");

                if (missed >= 5)
                    tv.setTextColor(Color.RED);

                tv.setVisibility(View.VISIBLE);

            }

            if (authData.charAt(0) == 'R')
                ((TextView) findViewById(R.id.dialog_text)).setText(getString(R.string.reg_confirm));

        }

        findViewById(R.id.okay_button).setOnClickListener(this);
        findViewById(R.id.cancel_button).setOnClickListener(this);

        timer = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < TIMEOUT_MILLIS) {
                    try {
                        Thread.sleep(POLL_INTERVAL);
                    } catch (InterruptedException e) {
                        return null;
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                Text.displayShort(AuthDialog.this, "Timeout.");
                AuthDialog.this.finish();
            }

        }.execute();

    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.okay_button:
                timer.cancel(true);
                if (authData.charAt(0) == 'R') {

                    String challenge = U2F.TEMP.get("challenge");
                    String userID = U2F.TEMP.get("userID");
                    String body = U2F.TEMP.get("serverData");
                    U2F.register(challenge, body, userID);

                } else {

                    U2F.process(authData);

                }
                finish();
                break;

            case R.id.cancel_button:
                timer.cancel(true);
                if (authData.charAt(0) == 'A')
                    U2F.decline(authData);
                finish();
                break;

        }

    }

}
