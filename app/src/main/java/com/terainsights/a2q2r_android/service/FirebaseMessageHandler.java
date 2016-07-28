package com.terainsights.a2q2r_android.service;

import android.content.Intent;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.terainsights.a2q2r_android.dialog.AuthDialog;
import com.terainsights.a2q2r_android.util.Database;
import com.terainsights.a2q2r_android.util.U2F;

import java.io.File;

/**
 * This class is specified in the Firebase Cloud Messaging (FCM) service in the
 * manifest, and is thus notified statically whenever the device receives a
 * cloud notification from Firebase.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/28/16
 */
public class FirebaseMessageHandler extends FirebaseMessagingService {

    /**
     * Called whenever a notification hits the phone.
     * @param msg The Firebase notification.
     */
    @Override
    public void onMessageReceived(RemoteMessage msg) {

        U2F.DATABASE = new Database(new File(getFilesDir(), "registrations.database"));
        U2F.CTX = getApplicationContext();

        String qrContent = msg.getData().get("authData");
        Database.ServerInfo serverInfo = U2F.DATABASE.getServerInfo(qrContent.split(" ")[1]);

        Intent intent = new Intent(getApplicationContext(), AuthDialog.class);
        intent.putExtra("serverName", serverInfo.appName);
        intent.putExtra("serverURL", serverInfo.baseURL);
        intent.putExtra("authData", qrContent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(intent);

    }

}
