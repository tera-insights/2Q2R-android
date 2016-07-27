package com.terainsights.a2q2r_android.service;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.terainsights.a2q2r_android.dialog.FCMDialog;
import com.terainsights.a2q2r_android.util.KeyManager;
import com.terainsights.a2q2r_android.util.Text;
import com.terainsights.a2q2r_android.util.U2F;

import java.io.File;
import java.io.IOException;

/**
 * This class is specified in the Firebase Cloud Messaging (FCM) service in the
 * manifest, and is thus notified statically whenever the device receives a
 * cloud notification from Firebase.
 */
public class FirebaseMessageHandler extends FirebaseMessagingService {

    /**
     * Called whenever a notification hits the phone.
     * @param msg The Firebase notification.
     */
    @Override
    public void onMessageReceived(RemoteMessage msg) {

        try {

            File registrationsFile = new File(getFilesDir(), "registrations.json");
            KeyManager km = new KeyManager(registrationsFile, registrationsFile.createNewFile());
            String appID = msg.getData().get("authData").split(" ")[1];

            Intent intent = new Intent(getApplicationContext(), FCMDialog.class);
            intent.putExtra("serverName", km.getAppName(appID));
            intent.putExtra("serverURL", km.getBaseURL(appID));
            intent.putExtra("authData", msg.getData().get("authData"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);

        } catch (IOException e) {

            e.printStackTrace();

        }

    }

}
