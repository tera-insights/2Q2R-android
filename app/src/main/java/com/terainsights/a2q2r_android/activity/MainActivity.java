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
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener;
import com.karumi.dexter.listener.single.PermissionListener;
import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.dialog.ConfirmDialog;
import com.terainsights.a2q2r_android.util.KeyManager;
import com.terainsights.a2q2r_android.util.U2F;

import java.io.File;
import java.io.IOException;

/**
 * The entry point for the application. Consists of a ListView displaying all of the user's
 * keys, as well as a FAB for scanning a QR, and a menu.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/26/16
 */
public class MainActivity extends Activity implements MenuItem.OnMenuItemClickListener {

    private static int SCAN_ACTION = 0;
    private static int CLEAR_ACTION = 1;

    private KeyManager km;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        try {

            File registrationsFile = new File(getFilesDir(), "registrations.json");
            km = new KeyManager(registrationsFile, registrationsFile.createNewFile());

            String[] values = {"An account", "Another account", "A third account"};

            ListView registrations = (ListView) findViewById(R.id.registrations_view);
            CustomArrayAdapter adapter = new CustomArrayAdapter(this, values);
            registrations.setAdapter(adapter);

        } catch (IOException e) {

            System.out.println("Could not read registrations file.");

        }

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

            if (resultCode == RESULT_OK && data != null)
                U2F.process(data.getStringExtra("qr_content"), km, getApplicationContext());
            else
                Toast.makeText(this, "Camera closed to save battery.", Toast.LENGTH_SHORT);

        } else if (requestCode == CLEAR_ACTION) {

            switch (resultCode) {

                case RESULT_OK:
                    System.out.println("Keys have been cleared.");
                    km.clearRegistrations();
                    break;

                case RESULT_CANCELED:
                    System.out.println("Keys were not cleared.");
                    break;

            }

        }

    }

    class CustomArrayAdapter extends ArrayAdapter<String> {

        private final Context context;
        private final String[] values;

        public CustomArrayAdapter(Context context, String[] values) {

            super(context, -1, values);
            this.context = context;
            this.values = values;

        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            LayoutInflater inflater = getLayoutInflater();
            View singleRow = inflater.inflate(R.layout.registration_item, parent, false);

            TextView userID = (TextView) singleRow.findViewById(R.id.user_id);
            userID.setText(values[position]);

            return singleRow;

        }

    }

}
