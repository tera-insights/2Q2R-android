package com.terainsights.a2q2r_android;

import android.content.Intent;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

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

        System.out.println(msg.getData().get("authData"));

        Intent i = new Intent(getApplicationContext(), AboutActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(i);

    }

}
