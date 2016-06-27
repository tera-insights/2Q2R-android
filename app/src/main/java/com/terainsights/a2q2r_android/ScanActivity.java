/*
 * FreeOTP
 *
 * Authors: Nathaniel McCallum <npmccallum@redhat.com>
 *
 * Copyright (C) 2013  Nathaniel McCallum, Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terainsights.a2q2r_android;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * The main app activity. Maintains the camera view on screen while also asynchronously
 * handling QR codes via a ScanAsyncTask object and contacting detected servers through
 * the Retrofit OkHttp interface.
 *
 * @author Nathaniel McCallum, Red Hat
 * @version 2013
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 6/22/16
 * @// TODO: 6/24/16 add userID checks to prevent wasteful registrations
 */
public class ScanActivity extends Activity implements SurfaceHolder.Callback,
        MenuItem.OnMenuItemClickListener, ScanAsyncTask.QRScanHandler,
        ActivityCompat.OnRequestPermissionsResultCallback {

    /**********************************************************************************************
     *                                                                                            *
     *                                        QR Scanning                                         *
     *                                                                                            *
     **********************************************************************************************/

    private final CameraInfo mCameraInfo = new CameraInfo();
    private ScanAsyncTask mAsyncScanner;
    private Handler mHandler;
    private Camera mCamera;

    private static class AutoFocusHandler extends Handler implements Camera.AutoFocusCallback {

        private final Camera mCamera;

        public AutoFocusHandler(Camera camera) {
            mCamera = camera;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            mCamera.autoFocus(this);
        }

        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            sendEmptyMessageDelayed(0, 1000);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan);
        serverRegistrations = new File(getFilesDir(), "registrations.txt");

        try {
            firstRegistration = serverRegistrations.createNewFile();
        } catch (IOException e) {}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.action_about).setOnMenuItemClickListener(this);
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        startActivity(new Intent(this, AboutActivity.class));
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        mAsyncScanner = new ScanAsyncTask(this);
        mAsyncScanner.execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAsyncScanner.cancel(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ((SurfaceView) findViewById(R.id.surfaceview)).getHolder().addCallback(this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera == null)
            return;

        mCamera.setDisplayOrientation(90);
        mCamera.startPreview();

        if (mHandler != null)
            mHandler.sendEmptyMessageDelayed(0, 100);
    }

    @Override
    @TargetApi(14)
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceDestroyed(holder);

        // Android 6 and greater must explicitly ask for camera permission.
        if (Build.VERSION.SDK_INT >= 23)
            requestPermissions();

        try {

            // Open the camera.
            mCamera = Camera.open();
            mCamera.setPreviewDisplay(holder);
            mCamera.setPreviewCallback(mAsyncScanner);

        } catch (NullPointerException e) {

            // Didn't find a camera to open.
            e.printStackTrace();
            surfaceDestroyed(holder);
            cameraError(getString(R.string.error_no_camera));
            return;

        } catch (IOException e) {

            // Camera preview setup failed.
            e.printStackTrace();
            surfaceDestroyed(holder);
            cameraError(getString(R.string.error_attaching_camera));
            return;

        } catch (RuntimeException e) {

            // The app was denied permission to camera services.
            e.printStackTrace();
            surfaceDestroyed(holder);
            cameraError(getString(R.string.error_no_perms));
            return;

        }

        // Set auto-focus mode
        Parameters params = mCamera.getParameters();
        List<String> modes = params.getSupportedFocusModes();
        if (modes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
            params.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        else if (modes.contains(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
            params.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        else if (modes.contains(Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Parameters.FOCUS_MODE_AUTO);
            mHandler = new AutoFocusHandler(mCamera);
        }
        mCamera.setParameters(params);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera == null)
            return;

        if (mHandler != null) {
            mCamera.cancelAutoFocus();
            mHandler.removeMessages(0);
            mHandler = null;
        }

        mCamera.stopPreview();
        mCamera.setPreviewCallback(null);
        mCamera.release();
        mCamera = null;
    }

    /**
     * Beginning in Android 6, it is mandatory that applications ask the user
     * for camera permission at runtime.
     */
    @TargetApi(23)
    private void requestPermissions() {

        if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(Manifest.permission.CAMERA)) {

            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {

                // Opens a dialog explaining that the app needs access to the camera.

            } else {

                requestPermissions(new String[]{Manifest.permission.CAMERA}, 0);

            }

        }

    }

    /**
     * Called once the user has responded to the permissions request dialog for camera
     * use. Not actually used, because permission issues are already handled in the
     * try-catch when camera access is attempted.
     * @param requestCode The code given when requesting permissions as an index.
     * @param permissions Permissions that were requested.
     * @param results The response to each of the respective requests.
     */
    @Override
    @TargetApi(23)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {}

    /**
     * Convenience method for displaying an error message on an inactive
     * camera screen.
     * @param error The message to be displayed.
     */
    private void cameraError(String error) {

        findViewById(R.id.surfaceview).setVisibility(View.INVISIBLE);
        findViewById(R.id.progress).setVisibility(View.INVISIBLE);
        findViewById(R.id.window).setVisibility(View.INVISIBLE);
        ((TextView) findViewById(R.id.scan_instructions)).setText(error);

    }

    /**********************************************************************************************
     *                                                                                            *
     *                          U2F Registration and Authentication                               *
     *                                                                                            *
     **********************************************************************************************/

    File serverRegistrations;
    boolean firstRegistration;

    InformationCallback    informationCallback    = new InformationCallback();
    RegistrationCallback   registrationCallback   = new RegistrationCallback();
    AuthenticationCallback authenticationCallback = new AuthenticationCallback();
    UserIDCallback         userIDCallback         = new UserIDCallback();

    /**
     * This is the U2F entry point for the parsed QR code data, which is
     * passed in by the `mAsyncScanner` object.
     * @param decodedQR The String extracted from a scanned QR.
     */
    @Override
    public void handleQR(String decodedQR) {

        if (Utils.identifyQRType(decodedQR) == 'R') {

            String[] splitQR = decodedQR.split(" ");
            String challenge = splitQR[1];
            String infoURL = splitQR[2];
            System.out.println(infoURL);

            if (!infoURL.endsWith("/"))
                infoURL += "/";

            Retrofit retro = new Retrofit.Builder()
                    .baseUrl(infoURL)
                    .build();

            Bundle regData = new Bundle();
            regData.putString("challenge", challenge);
            regData.putString("infoURL", infoURL);
            getIntent().putExtras(regData);

            U2FServices.ServerInfo info = retro.create(U2FServices.ServerInfo.class);
            Call<ResponseBody> infoCall = info.getInfo();
            infoCall.enqueue(informationCallback);

        } else if (Utils.identifyQRType(decodedQR) == 'A') {

            String[] splitQR   = decodedQR.split(" ");
            String base64AppID = splitQR[1];
            String challenge   = splitQR[2];
            String keyID       = splitQR[3];

            authenticate(base64AppID, challenge, keyID);

        } else {

            displayInPhoneDialog(getString(R.string.invalid_qr));

        }

    }

    /**
     * Provided with all the necessary data from a U2F registration request,
     * generates a registration response which conforms exactly to the
     * specifications outlined in the U2F standard, and sends it to the
     * "/register" directory of the baseURL obtained from the relying
     * party's `infoURL`.
     * @param challengeB64 A challenge provided by the server that the device is
     *                     being registered with [Base64 of 32 bytes].
     * @param infoURL The URL embedded in the QR code, pointing to the server's
     *                information object, `serverInfo` (already obtained).
     * @param serverInfo Stringified JSON containing server details.
     */
    private void register(String challengeB64, String infoURL, String serverInfo) {

        try {

            JSONObject info   = new JSONObject(serverInfo);
            String     keyID  = Utils.genKeyID();
            PublicKey  pubKey = Utils.genKeys(keyID, getApplicationContext());

            if (pubKey != null) {

                KeyManager keyManager = new KeyManager(serverRegistrations, firstRegistration);

                keyManager.registerWithServer(
                        info.getString("appID"),
                        info.getString("appName"),
                        infoURL,
                        keyID
                );

                keyManager.saveRegistrations();

                KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
                ks.load(null);

                Certificate x509 = ks.getCertificate(keyID);
                ECPoint     keyW = ((ECPublicKey) x509.getPublicKey()).getW();

                //////////////////////////////////////////////////////////////

                byte[] keyX = keyW.getAffineX().toByteArray();
                byte[] keyY = keyW.getAffineY().toByteArray();
                byte[] pref = {0x4};

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                try {

                    bytes.write(pref);
                    bytes.write(keyX, keyX.length - 32, 32);
                    bytes.write(keyY, keyY.length - 32, 32);
                    System.out.println("Successfully parsed the public key.");

                } catch (IOException e) {
                } catch (IndexOutOfBoundsException e) {
                    displayInPhoneDialog("The public key X was of length: " + keyX.length);
                }

                //////////////////////////////////////////////////////////////

                JSONObject clientData = new JSONObject();

                try {

                    clientData.put("typ", "navigator.id.finishEnrollment");
                    clientData.put("challenge", challengeB64);
                    clientData.put("origin", info.getString("baseURL"));

                } catch (JSONException e) {}

                byte[] futureUse = {0x00};
                byte[] appParam  = Base64.decode(info.getString("appID"), Base64.DEFAULT);
                byte[] challenge = Base64.decode(clientData.toString(), Base64.URL_SAFE);
                byte[] keyHandle = Base64.decode(keyID, Base64.DEFAULT);
                byte[] publicKey = bytes.toByteArray();

                bytes.reset();

                try {

                    bytes.write(futureUse);
                    bytes.write(appParam);
                    bytes.write(challenge);
                    bytes.write(keyHandle);
                    bytes.write(publicKey);

                } catch (IOException e) {
                    displayInPhoneDialog(e.toString());
                }

                System.out.println(Base64.encodeToString(bytes.toByteArray(), Base64.URL_SAFE));
                byte[] signature = Utils.sign(bytes.toByteArray(), keyID);

                //////////////////////////////////////////////////////////////

                byte[] reserved     = {0x05};
                byte[] handleLength = {(byte) keyHandle.length};
                byte[] certificate  = x509.getEncoded();

                bytes.reset();

                try {

                    bytes.write(reserved);
                    bytes.write(publicKey);
                    bytes.write(handleLength);
                    bytes.write(keyHandle);
                    bytes.write(certificate);
                    bytes.write(signature);

                } catch (IOException e) {
                    displayInPhoneDialog(e.toString());
                }

                byte[] regRes = bytes.toByteArray();

                //////////////////////////////////////////////////////////////

                JSONObject registrationData = new JSONObject();

                try {

                    registrationData.put("clientData", clientData.toString());
                    registrationData.put("registrationData", Base64.encodeToString(
                            regRes, Base64.DEFAULT));

                } catch (JSONException e) {}

                MediaType media = MediaType.parse("application/json; charset=utf-8");

                RequestBody data = RequestBody.create(media, registrationData.toString());

                Retrofit retro = new Retrofit.Builder()
                        .baseUrl(info.getString("baseURL"))
                        .build();

                U2FServices.Registration reg = retro.create(U2FServices.Registration.class);
                Call<ResponseBody> regCall = reg.register(data);
                regCall.enqueue(registrationCallback);

            } else if (Build.VERSION.SDK_INT < 19) {

                displayInPhoneDialog("Your device is outdated. Please upgrade to Android 4.4 " +
                    "(KitKat) or newer.");

            } else {

                displayInPhoneDialog("You must have a lock on your device in order to securely " +
                    "generate keys. Additionally, if you turn off the security measure on your " +
                    "device, all existing keys may be deleted and cannot be recovered.");

            }

        } catch (JSONException e) {
        } catch (KeyStoreException e) {
        } catch (CertificateException e) {
        } catch (NoSuchAlgorithmException e) {
        } catch (IOException e) {
        }

    }

    /**
     * Authenticates the user. First, the server's info is grabbed from
     * internal JSON storage using the `base64AppID` as an index. Then,
     * `challenge` is signed using the private key and the result
     * is sent back to the server.
     * @param base64AppID A Base64-encoded copy of the server's unique
     *                    32-byte identity.
     * @param challenge A Base64-encoded challenge of 32 bytes sent
     *                  from the server.
     * @param keyID A Base64-encoded handle of 16 bytes sent from the
     *              server to identify the key it expects to be used
     *              for authentication.
     * @return True if an authentication response was successfully
     *          sent, or false if an error occurred.
     */
    private boolean authenticate(String base64AppID, String challenge, String keyID) {

        try {

            KeyManager keys = new KeyManager(serverRegistrations, false);
            JSONObject serverInfo = keys.getAuthenticationInfo(base64AppID);

            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.putInt(serverInfo.getInt("counter"));

            byte[] appParam     = Base64.decode(base64AppID, Base64.DEFAULT);
            byte[] userPresence = {0b00000001};
            byte[] counter      = buffer.array();
            byte[] challParam   = Base64.decode(challenge, Base64.DEFAULT);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            os.write(appParam);
            os.write(userPresence);
            os.write(counter);
            os.write(challParam);

            byte[] data = os.toByteArray();
            byte[] signed = Utils.sign(data, base64AppID);

            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            PublicKey pubKey = ks.getCertificate(base64AppID).getPublicKey();

            return true;

        } catch (IOException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;

    }

    /**
     * For debugging purposes only.
     * @param text Text to display on the phone.
     */
    private void displayInPhoneDialog(String text) {

        Intent intent = new Intent(this, ErrorDialog.class);
        Bundle b = new Bundle();
        b.putString("info", text);
        intent.putExtras(b);
        startActivity(intent);

    }

    /**
     * Describes what the activity should do once information has either been
     * loaded from the server, or once the attempt failed.
     */
    private class InformationCallback implements Callback<ResponseBody> {

        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

            String body = null;

            try {
                body = response.body().string();
            } catch (IOException e) {
                e.printStackTrace();
            }

            System.out.println(body);

            if (body != null) {

                Bundle regData = getIntent().getExtras();
                String challenge = regData.getString("challenge");
                String infoURL = regData.getString("infoURL");

                register(challenge, infoURL, body);

            }

        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            t.printStackTrace();
            displayInPhoneDialog("Failed to retrieve info from\n" + getIntent().getExtras()
                .getString("infoURL") + "!");
        }

    }

    /**
     * Describes what should be done once the server has responded to the
     * device's request for the userID corresponding to the current registration
     * challenge. Allows the device to block the user from registering it
     * multiple times with the same account.
     */
    private class UserIDCallback implements Callback<ResponseBody> {

        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {

        }
    }

    /**
     * Describes what should be done once the the server has responded to the
     * device's registration request.
     */
    private class RegistrationCallback implements Callback<ResponseBody> {

        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

            String body = null;

            try {
                body = response.body().string();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            displayInPhoneDialog(body);

        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            displayInPhoneDialog("Failed to register with server!" + "\n" + t.toString());
        }

    }

    /**
     * Describes what should be done once the server has responded to the
     * device's authentication request.
     */
    private class AuthenticationCallback implements Callback<ResponseBody> {

        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {

        }

    }

}