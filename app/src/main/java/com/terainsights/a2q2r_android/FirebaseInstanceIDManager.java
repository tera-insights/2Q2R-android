package com.terainsights.a2q2r_android;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

/**
 * Creates and updates this device's unique Firebase Instance ID, which allows
 * the Firebase server to target this device specifically for notifications.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/20/16
 */
public class FirebaseInstanceIDManager extends FirebaseInstanceIdService {

    @Override
    public void onTokenRefresh() {

        System.out.println(FirebaseInstanceId.getInstance().getToken());

    }

}
