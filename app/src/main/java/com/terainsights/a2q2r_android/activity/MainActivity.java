package com.terainsights.a2q2r_android.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener;
import com.karumi.dexter.listener.single.PermissionListener;
import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.dialog.AuthDialog;
import com.terainsights.a2q2r_android.dialog.ConfirmDialog;
import com.terainsights.a2q2r_android.dialog.KeyDescription;
import com.terainsights.a2q2r_android.util.Database;
import com.terainsights.a2q2r_android.util.Text;
import com.terainsights.a2q2r_android.util.U2F;

import java.io.File;
import java.util.ArrayList;

/**
 * The entry point for the application. Consists of a ListView displaying all of the user's
 * keys, as well as a FAB for scanning a QR, and a menu.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/28/16
 */
public class MainActivity extends Activity implements MenuItem.OnMenuItemClickListener,
        AdapterView.OnItemClickListener {

    private static int SCAN_ACTION = 0;
    private static int CLEAR_ACTION = 1;

    private KeyAdapter keyDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        File f = new File(getFilesDir(), "registrations.database");
        f.delete();

        U2F.DATABASE = new Database(f);
        U2F.CTX      = getApplicationContext();

        ListView registrations = (ListView) findViewById(R.id.registrations_view);
        registrations.setOnItemClickListener(this);

        keyDisplay = new KeyAdapter(U2F.DATABASE.getDisplayableKeyInformation());
        U2F.DATABASE.addRegistrationListener(keyDisplay);
        registrations.setAdapter(keyDisplay);

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

                String qrContent = data.getStringExtra("qr_content");
                Database.ServerInfo serverInfo = U2F.DATABASE
                        .getServerInfo(qrContent.split(" ")[1]);

                if (qrContent.startsWith("A")) {

                    if (serverInfo == null) {

                        Text.displayShort(this, R.string.unknown_server_error);
                        return;

                    }

                    Intent intent = new Intent(this, AuthDialog.class);
                    intent.putExtra("serverName", serverInfo.appName);
                    intent.putExtra("serverURL", serverInfo.baseURL);
                    intent.putExtra("authData", data.getStringExtra("qr_content"));

                    startActivity(intent);

                } else {

                    U2F.process(qrContent);

                }

            } else if (resultCode == RESULT_CANCELED) {

                try {

                    if (data.getBooleanExtra("canceled", false))
                        Text.displayShort(this, R.string.camera_closed);

                } catch (NullPointerException e) {}

            }

        } else if (requestCode == CLEAR_ACTION && resultCode == RESULT_OK) {

            System.out.println("Keys have been cleared.");
            new File(getFilesDir(), "registrations.database").delete();

        }

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {

        Intent intent = new Intent(this, KeyDescription.class);
        Database.KeyDetails key = keyDisplay.getItem(pos);

        intent.putExtra("userID", key.userID);
        intent.putExtra("appName", key.appName);
        intent.putExtra("baseURL", key.baseURL);

        startActivity(intent);

    }

    class KeyAdapter extends BaseAdapter implements Database.KeyRegistrationListener {

        private ArrayList<Database.KeyDetails> keyInfo;

        public KeyAdapter(ArrayList<Database.KeyDetails> keyInfo) {

            this.keyInfo = keyInfo;

        }

        @Override
        public int getCount() {

            return keyInfo.size();

        }

        @Override
        public Database.KeyDetails getItem(int position) {

            return keyInfo.get(position);

        }

        @Override
        public long getItemId(int position) {

            return position;

        }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {

            TextView userID;
            TextView appName;
            TextView date;
            TextView time;

            if (convertView == null) {

                convertView = getLayoutInflater().inflate(R.layout.registration_item, parent, false);

                userID =  (TextView) convertView.findViewById(R.id.user_id);
                appName = (TextView) convertView.findViewById(R.id.app_name);
                date =    (TextView) convertView.findViewById(R.id.date_used);
                time =    (TextView) convertView.findViewById(R.id.time_used);

                convertView.setTag(R.id.user_id, userID);
                convertView.setTag(R.id.app_name, appName);
                convertView.setTag(R.id.date_used, date);
                convertView.setTag(R.id.time_used, time);

            } else {

                userID =  (TextView) convertView.getTag(R.id.user_id);
                appName = (TextView) convertView.getTag(R.id.app_name);
                date =    (TextView) convertView.getTag(R.id.date_used);
                time =    (TextView) convertView.getTag(R.id.time_used);

            }

            Database.KeyDetails key = getItem(pos);

            userID.setText(key.userID);
            appName.setText(key.appName);
            date.setText(key.date);
            time.setText(key.time);

            return convertView;

        }

        @Override
        public void notifyKeysUpdated(Database.KeyDetails newKeyDesc) {

            keyInfo.add(newKeyDesc);
            notifyDataSetChanged();

        }

    }

}
