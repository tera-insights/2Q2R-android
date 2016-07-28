package com.terainsights.a2q2r_android.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener;
import com.karumi.dexter.listener.single.PermissionListener;
import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.dialog.AuthDialog;
import com.terainsights.a2q2r_android.dialog.ConfirmDialog;
import com.terainsights.a2q2r_android.util.Database;
import com.terainsights.a2q2r_android.util.Text;
import com.terainsights.a2q2r_android.util.U2F;

import java.io.File;

/**
 * The entry point for the application. Consists of a ListView displaying all of the user's
 * keys, as well as a FAB for scanning a QR, and a menu.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/28/16
 */
public class MainActivity extends Activity implements MenuItem.OnMenuItemClickListener {

    private static int SCAN_ACTION = 0;
    private static int CLEAR_ACTION = 1;
    private static int AUTH_ACTION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        U2F.DATABASE = new Database(new File(getFilesDir(), "registrations.database"));
        U2F.CTX      = getApplicationContext();

        ListView registrations = (ListView) findViewById(R.id.registrations_view);
        Database.KeyData data  = U2F.DATABASE.getDisplayableKeyInformation();
        CustomArrayAdapter adapter = new CustomArrayAdapter(data);
        registrations.setAdapter(adapter);

        Dexter.initialize(getApplicationContext());
        PermissionListener listener = DialogOnDeniedPermissionListener.Builder
                .withContext(getApplicationContext())
                .withTitle("Camera Permission")
                .withMessage(R.string.camera_required)
                .withButtonText(android.R.string.ok)
                .build();
        Dexter.checkPermission(listener, Manifest.permission.CAMERA);

        findViewById(R.id.action_scan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), SCAN_ACTION);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.action_about).setOnMenuItemClickListener(this);
        menu.findItem(R.id.action_clear_data).setOnMenuItemClickListener(this);
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.action_about:
                startActivity(new Intent(this, AboutActivity.class));
                break;

            case R.id.action_clear_data:
                Intent intent = new Intent(this, ConfirmDialog.class);
                intent.putExtra("data", "Are you sure you want to wipe your keys?");
                startActivityForResult(intent, CLEAR_ACTION);
                break;

        }

        return true;

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SCAN_ACTION) {

            if (resultCode == RESULT_OK && data != null) {

                Database.ServerInfo serverInfo = U2F.DATABASE
                        .getServerInfo(data.getStringExtra("qr_content").split(" ")[1]);

                Intent intent = new Intent(this, AuthDialog.class);
                intent.putExtra("serverName", serverInfo.appName);
                intent.putExtra("serverURL", serverInfo.baseURL);
                intent.putExtra("authData", data.getStringExtra("qr_content"));

                startActivity(intent);

            } else {

                Text.displayShort(this, R.string.camera_closed);

            }

        } else if (requestCode == CLEAR_ACTION && resultCode == RESULT_OK) {

            System.out.println("Keys have been cleared.");
            new File(getFilesDir(), "registrations.database").delete();

        } else if (requestCode == AUTH_ACTION && resultCode == RESULT_OK) {



        }

    }

    class CustomArrayAdapter extends ArrayAdapter<String> {

        private final Database.KeyData data;

        public CustomArrayAdapter(Database.KeyData data) {

            super(getApplicationContext(), -1, data.userIDs);
            this.data = data;

        }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {

            LayoutInflater inflater = getLayoutInflater();
            View singleRow = inflater.inflate(R.layout.registration_item, parent, false);

            ((TextView) singleRow.findViewById(R.id.user_id)).setText(data.userIDs.get(pos));
            ((TextView) singleRow.findViewById(R.id.server_name)).setText(data.appNames.get(pos));
            ((TextView) singleRow.findViewById(R.id.date_used)).setText(data.dates.get(pos));
            ((TextView) singleRow.findViewById(R.id.time_used)).setText(data.times.get(pos));

            return singleRow;

        }

    }

}
