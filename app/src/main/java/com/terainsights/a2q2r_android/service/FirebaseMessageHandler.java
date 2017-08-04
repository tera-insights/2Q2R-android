package com.terainsights.a2q2r_android.service;

import android.content.Intent;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.dialog.AuthDialog;
import com.terainsights.a2q2r_android.util.KeyDatabase;
import com.terainsights.a2q2r_android.util.Text;
import com.terainsights.a2q2r_android.util.U2F;

import java.io.File;

/**
 * This class is specified in the Firebase Cloud Messaging (FCM) service in the
 * manifest, and is thus notified statically whenever the device receives a
 * cloud notification from Firebase.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 8/31/16
 */
public class FirebaseMessageHandler extends FirebaseMessagingService {

    /**
     * Called whenever a notification hits the phone. Reads through the
     * message contents and displays info to the user, requesting permission
     * before beginning authentication.
     * @param msg The Firebase notification.
     */
    @Override
    public void onMessageReceived(RemoteMessage msg) {

        try {

            U2F.DATABASE = new KeyDatabase(new File(getFilesDir(), "registrations.database"));
            U2F.CTX = getApplicationContext();

            String qrContent = msg.getData().get("authData");
            KeyDatabase.ServerInfo serverInfo = U2F.DATABASE.getServerInfo(qrContent.split(" ")[1]);


            Log.i("FIREBASE", qrContent);

            String[] split = qrContent.split(" ");

            if (!U2F.DATABASE.hasKey(split[3]) || !U2F.DATABASE.hasServer(split[1])) {
                System.out.println(getString(R.string.unknown_firebase_error));
                return;
            }

            int serverCounter = Integer.parseInt(split[4]);
            int deviceCounter = U2F.DATABASE.getCounter(split[3]);
            int difference = serverCounter - deviceCounter;

            if (difference < 0) {

                Text.displayLong(getApplicationContext(), R.string.bad_counter_error);
                return;

            } else if (difference == 0) {

                System.out.println("This Firebase message was already handled.");
                return;

            }

            Intent intent = new Intent(getApplicationContext(), AuthDialog.class);
            intent.putExtra("serverName", serverInfo.appName);
            intent.putExtra("serverURL", serverInfo.appURL);
            intent.putExtra("authData", qrContent);
            intent.putExtra("missed", difference - 1);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);

        } catch (NullPointerException e) {

            System.out.println(getString(R.string.unknown_firebase_error));

        }

    }

}
