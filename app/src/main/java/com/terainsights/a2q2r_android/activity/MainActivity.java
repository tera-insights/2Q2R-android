package com.terainsights.a2q2r_android.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.support.design.widget.BottomNavigationView;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener;
import com.karumi.dexter.listener.single.PermissionListener;
import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.dialog.AuthDialog;
import com.terainsights.a2q2r_android.dialog.ConfirmDialog;
import com.terainsights.a2q2r_android.dialog.KeyDescription;
import com.terainsights.a2q2r_android.util.KeyAdapter;
import com.terainsights.a2q2r_android.util.KeyDatabase;
import com.terainsights.a2q2r_android.util.Text;
import com.terainsights.a2q2r_android.util.U2F;
import com.terainsights.a2q2r_android.util.Utils;

import java.io.File;

/**
 * The entry point for the application. Consists of a ListView displaying all of the user's
 * keys, as well as a FAB for scanning a QR, and a menu.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 8/19/16
 */
public class MainActivity extends Activity implements MenuItem.OnMenuItemClickListener,
        AdapterView.OnItemClickListener {

    private static int SCAN_ACTION = 0;
    private static int CLEAR_ACTION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        File f = new File(getFilesDir(), "registrations.database");

        U2F.DATABASE = new KeyDatabase(f);
        U2F.CTX      = getApplicationContext();

        BottomNavigationView bottomNavigation = (BottomNavigationView) findViewById(R.id.bottom_navigation);

        bottomNavigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch(item.getItemId()){
                    case R.id.history_label:
                        findViewById(R.id.content).setLayout
                        findViewById(R.id.history).setVisibility(View.VISIBLE);
                        findViewById(R.id.accounts).setVisibility(View.GONE);
                        findViewById(R.id.scan).setVisibility(View.GONE);
                        break;
                    case R.id.accounts_label:
                        findViewById(R.id.history).setVisibility(View.GONE);
                        findViewById(R.id.accounts).setVisibility(View.VISIBLE);
                        findViewById(R.id.scan).setVisibility(View.GONE);
                        break;
                    case R.id.scan_label:
                        findViewById(R.id.history).setVisibility(View.GONE);
                        findViewById(R.id.accounts).setVisibility(View.GONE);
                        findViewById(R.id.scan).setVisibility(View.VISIBLE);
                        startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), SCAN_ACTION);
                        break;
                }
                return true;
            }
        });

        ListView registrations = (ListView) findViewById(R.id.registrations_view);
        registrations.setOnItemClickListener(this);

        KeyDatabase.KEY_ADAPTER = new KeyAdapter(this, R.layout.registration_item);
        U2F.DATABASE.refreshKeyInfo();
        registrations.setAdapter(KeyDatabase.KEY_ADAPTER);

        Dexter.initialize(getApplicationContext());
        PermissionListener listener = DialogOnDeniedPermissionListener.Builder
                .withContext(getApplicationContext())
                .withTitle("Camera Permission")
                .withMessage(R.string.camera_required)
                .withButtonText(android.R.string.ok)
                .build();
        Dexter.checkPermission(listener, Manifest.permission.CAMERA);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options, menu);
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

                if (Utils.identifyQRType(qrContent) == 'A') {

                    KeyDatabase.ServerInfo serverInfo = U2F.DATABASE
                            .getServerInfo(qrContent.split(" ")[1]);

                    if (serverInfo == null) {

                        Text.displayShort(this, R.string.unknown_server_error);
                        return;

                    }

                    String[] split = qrContent.split(" ");
                    int serverCounter = Integer.parseInt(split[4]);
                    int deviceCounter = U2F.DATABASE.getCounter(split[3]);
                    int difference = serverCounter - deviceCounter;

                    if (difference < 1) {

                        Text.displayLong(this, R.string.bad_counter_error);
                        return;

                    }

                    Intent intent = new Intent(this, AuthDialog.class);
                    intent.putExtra("serverName", serverInfo.appName);
                    intent.putExtra("serverURL", serverInfo.appURL);
                    intent.putExtra("authData", data.getStringExtra("qr_content"));
                    intent.putExtra("missed", difference - 1);

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
            U2F.DATABASE.clear();

        }

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {

        Intent intent = new Intent(this, KeyDescription.class);
        Cursor cursor = KeyDatabase.KEY_ADAPTER.getCursor();
        cursor.moveToPosition(pos);

        intent.putExtra("userID", cursor.getString(cursor.getColumnIndex("userID")));
        intent.putExtra("appName", cursor.getString(cursor.getColumnIndex("appName")));
        intent.putExtra("appURL", cursor.getString(cursor.getColumnIndex("appURL")));

        startActivity(intent);

    }

}
