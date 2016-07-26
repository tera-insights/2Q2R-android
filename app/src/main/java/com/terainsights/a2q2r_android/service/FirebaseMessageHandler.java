package com.terainsights.a2q2r_android.service;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

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

        Intent intent = new Intent("firebase-notification");
        intent.putExtra("notification", msg.getData().get("authData"));

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

    }

}
