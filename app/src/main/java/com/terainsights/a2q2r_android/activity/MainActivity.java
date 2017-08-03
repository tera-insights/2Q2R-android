package com.terainsights.a2q2r_android.activity;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.MenuItem;
import android.support.design.widget.BottomNavigationView;

import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.dialog.AuthDialog;
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
public class MainActivity extends Activity implements ScanFragment.OnQRScanListener {

//    Putting these here for now, find some better way to transfer fragment states
    ScanFragment scanFragment;
    HistoryFragment historyFragment;
    AccountFragment accountFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        historyFragment = new HistoryFragment();
        accountFragment = new AccountFragment();
        scanFragment = new ScanFragment();

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().add(R.id.fragment_container, historyFragment).commit();

        }

        File f = new File(getFilesDir(), "registrations.database");

        U2F.DATABASE = new KeyDatabase(f);
        U2F.CTX      = getApplicationContext();

        BottomNavigationView bottomNavigation = (BottomNavigationView) findViewById(R.id.bottom_navigation);

        bottomNavigation.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                switch(item.getItemId()){
                    case R.id.history_label:
                        transaction.replace(R.id.fragment_container, historyFragment).commit();
                        break;
                    case R.id.accounts_label:
                        transaction.replace(R.id.fragment_container, accountFragment).commit();
                        break;
                    case R.id.scan_label:
                        transaction.replace(R.id.fragment_container, scanFragment).commit();
                        break;
                }
                return true;
            }
        });

    }

    @Override
    public void onQRScan(boolean success, Intent data){
        String qrContent = data.getStringExtra("qr_content");

        if (success == true && data != null) {

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

        } else if (success == false) {

            try {

                if (data.getBooleanExtra("canceled", false))
                    Text.displayShort(this, R.string.camera_closed);

            } catch (NullPointerException e) {}

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

    }

}
