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
import android.hardware.Camera;
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

import com.google.firebase.iid.FirebaseInstanceId;
import com.jaredrummler.android.device.DeviceName;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.listener.single.DialogOnDeniedPermissionListener;
import com.karumi.dexter.listener.single.PermissionListener;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.util.List;
import java.util.Scanner;

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
        System.out.println(FirebaseInstanceId.getInstance().getToken());

        Dexter.initialize(getApplicationContext());
        PermissionListener listener = DialogOnDeniedPermissionListener.Builder
                .withContext(getApplicationContext())
                .withTitle("Camera Permission")
                .withMessage(R.string.camera_required)
                .withButtonText(android.R.string.ok)
                .build();
        Dexter.checkPermission(listener, Manifest.permission.CAMERA);

        setContentView(R.layout.scan);
        serverRegistrations = new File(getFilesDir(), "registrations.json");

        printFile(serverRegistrations);

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
        restartScanner();
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

    /**
     * Convenience method to restart the scanning thread so another QR
     * can be scanned.
     */
    private void restartScanner() {
        mAsyncScanner = new ScanAsyncTask(this);
        mAsyncScanner.execute();
    }

    /**********************************************************************************************
     *                                                                                            *
     *                          U2F Registration and Authentication                               *
     *                                                                                            *
     **********************************************************************************************/

    File serverRegistrations;
    boolean firstRegistration;
    KeyManager keyManager;

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
            String infoURL   = splitQR[2];
            String userID    = splitQR[3];

            if (!infoURL.endsWith("/"))
                infoURL += "/";

            Retrofit retro = new Retrofit.Builder()
                    .baseUrl(infoURL)
                    .build();

            Bundle regData = new Bundle();
            regData.putString("challenge", challenge);
            regData.putString("infoURL", infoURL);
            regData.putString("userID", userID);
            regData.putString("type", "R");
            getIntent().putExtras(regData);

            U2F.ServerInfo info = retro.create(U2F.ServerInfo.class);
            Call<ResponseBody> infoCall = info.getInfo();
            infoCall.enqueue(new InformationCallback());

        } else if (Utils.identifyQRType(decodedQR) == 'A') {

            String[] splitQR   = decodedQR.split(" ");
            String base64AppID = splitQR[1];
            String challenge   = splitQR[2];
            String keyID       = splitQR[3];

            printFile(serverRegistrations);

            String infoURL;

            try {

                keyManager = new KeyManager(serverRegistrations, firstRegistration);
                infoURL    = keyManager.getInfoURL(base64AppID);

            } catch (FileNotFoundException e) {

                displayInPhoneDialog(getString(R.string.corrupted_registrations_error));
                return;

            }

            Retrofit retro = new Retrofit.Builder()
                    .baseUrl(infoURL)
                    .build();

            Bundle authData = new Bundle();
            authData.putString("challenge", challenge);
            authData.putString("keyID", keyID);
            authData.putString("type", "A");
            getIntent().putExtras(authData);

            U2F.ServerInfo info = retro.create(U2F.ServerInfo.class);
            Call<ResponseBody> infoCall = info.getInfo();
            infoCall.enqueue(new InformationCallback());

        } else {

            displayInPhoneDialog(getString(R.string.invalid_qr_error));

        }

    }

    /**
     * Provided with all the necessary data from a U2F registration request,
     * generates a registration response which conforms exactly to the
     * specifications outlined in the U2F standard, and sends it to the
     * "/register" directory of the baseURL obtained from the relying
     * party's `infoURL`.
     * @param challengeB64 A challenge provided by the server that the device is
     *                     being registered with [web-safe-Base64 of 32 bytes].
     * @param infoURL The URL embedded in the QR code, pointing to the server's
     *                information object, `serverInfo` (already obtained).
     * @param serverInfo Stringified JSON containing server details.
     * @param userID The username of the account registering the device, used to
     *               prevent multiple registrations of the same device to the
     *               same account.
     */
    private void register(String challengeB64, String infoURL, String serverInfo, String userID) {

        try {

            JSONObject info   = new JSONObject(serverInfo);
            String     keyID  = Utils.genKeyID();
            PublicKey  pubKey = Utils.genKeys(keyID, getApplicationContext());

            if (pubKey != null) {

                keyManager = new KeyManager(serverRegistrations, firstRegistration);

                Bundle extras = getIntent().getExtras();

                extras.putString("appID", info.getString("appID"));
                extras.putString("infoURL", infoURL);
                extras.putString("keyID", keyID);
                extras.putString("userID", userID);

                System.out.println("Server registrations:");
                System.out.println(keyManager.serverRegs.toString(4));

                JSONObject servers = keyManager.serverRegs.getJSONObject("servers");

                if (servers.has(info.getString("appID")) && servers
                        .getJSONObject(info.getString("appID")).getJSONArray("users")
                        .toString().contains("\"" + userID + "\"")) {

                    throw new KeyManager.UserAlreadyRegisteredException();

                }

                KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
                ks.load(null);

                Certificate x509 = ks.getCertificate(keyID);
                ECPoint     keyW = ((ECPublicKey) x509.getPublicKey()).getW();

                //////////////////////////////////////////////////////////////

                byte[] keyX = keyW.getAffineX().toByteArray();
                byte[] keyY = keyW.getAffineY().toByteArray();
                byte[] pref = {0x4};

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                bytes.write(pref);
                bytes.write(keyX, keyX.length - 32, 32);
                bytes.write(keyY, keyY.length - 32, 32);

                //////////////////////////////////////////////////////////////

                // TODO: Take hash and Base64 encode client data on server
                JSONObject clientData = new JSONObject()
                    .put("typ", "navigator.id.finishEnrollment")
                    .put("challenge", challengeB64)
                    .put("origin", info.getString("baseURL"));

                MessageDigest md = MessageDigest.getInstance("SHA-256");

                byte[] futureUse = {0x00};
                byte[] appParam  = md.digest(info.getString("appID").getBytes());
                byte[] challenge = md.digest(clientData.toString().replace("\\", "").getBytes());
                byte[] keyHandle = Base64.decode(keyID, Base64.URL_SAFE);
                byte[] publicKey = bytes.toByteArray();

                bytes.reset();

                bytes.write(futureUse);
                bytes.write(appParam);
                bytes.write(challenge);
                bytes.write(keyHandle);
                bytes.write(publicKey);

                byte[] signature = Utils.sign(bytes.toByteArray(), keyID);

                //////////////////////////////////////////////////////////////

                byte[] reserved     = {0x05};
                byte[] handleLength = {(byte) keyHandle.length};
                byte[] certificate  = x509.getEncoded();

                bytes.reset();

                bytes.write(reserved);
                bytes.write(publicKey);
                bytes.write(handleLength);
                bytes.write(keyHandle);
                bytes.write(certificate);
                bytes.write(signature);

                byte[] regRes = bytes.toByteArray();

                //////////////////////////////////////////////////////////////

                JSONObject registrationData = new JSONObject()
                    .put("deviceName", DeviceName.getDeviceName())
                    .put("fcmToken", FirebaseInstanceId.getInstance().getToken())
                    .put("clientData", clientData)
                    .put("registrationData", Base64.encodeToString(regRes, Base64.DEFAULT));

                MediaType media = MediaType.parse("application/json; charset=utf-8");

                RequestBody data = RequestBody.create(media, registrationData.toString());

                Retrofit retro = new Retrofit.Builder()
                        .baseUrl(info.getString("baseURL"))
                        .build();

                U2F.Registration reg = retro.create(U2F.Registration.class);
                Call<ResponseBody> regCall = reg.register(data);
                regCall.enqueue(new RegistrationCallback());

            } else if (Build.VERSION.SDK_INT < 19) {

                displayInPhoneDialog(getString(R.string.outdated_device_error));

            } else {

                displayInPhoneDialog(getString(R.string.key_gen_error));

            }

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
            displayInPhoneDialog(getString(R.string.key_gen_error));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            displayInPhoneDialog(getString(R.string.key_gen_error));
        } catch (IOException e) {
            e.printStackTrace();
            displayInPhoneDialog(getString(R.string.registration_gen_error));
        } catch (Utils.AuthExpiredException e) {
            e.printStackTrace();
            displayInPhoneDialog(getString(R.string.auth_timeout_error));
        } catch (KeyManager.UserAlreadyRegisteredException e) {
            e.printStackTrace();
            displayInPhoneDialog(getString(R.string.existing_registration_error));
        }

    }

    /**
     * Authenticates the user. First, the server's info is grabbed from
     * internal JSON storage using the `base64AppID` as an index. Then,
     * `challenge` is signed using the private key and the result
     * is sent back to the server.
     * @param challengeB64 A web-safe-Base64-encoded challenge of 32
     *                     bytes sent from the server.
     * @param keyID A web-safe-Base64-encoded handle of 16 bytes sent
     *              from the server to identify the key it expects to
     *              be used for authentication.
     * @param serverInfo Stringified JSON containing server details.
     */
    private void authenticate(String challengeB64, String keyID, String serverInfo) {

        try {

            KeyManager km = keyManager;
            JSONObject info = new JSONObject(serverInfo);

            JSONObject clientData = new JSONObject()
                .put("typ", "navigator.id.getAssertion")
                .put("challenge", challengeB64)
                .put("origin", info.getString("baseURL"));

            MessageDigest md = MessageDigest.getInstance("SHA-256");

            byte[] userPresence = {0b00000001};
            byte[] counter      = km.getCounter(keyID);
            byte[] appParam     = md.digest(info.getString("appID").getBytes());
            byte[] challenge    = md.digest(clientData.toString().replace("\\", "").getBytes());

            ByteArrayOutputStream os = new ByteArrayOutputStream();

            os.write(appParam);
            os.write(userPresence);
            os.write(counter);
            os.write(challenge);

            byte[] signature = Utils.sign(os.toByteArray(), keyID);

            os.reset();

            os.write(userPresence);
            os.write(counter);
            os.write(signature);

            byte[] signatureData = os.toByteArray();

            if (signatureData == null) {
                displayInPhoneDialog(getString(R.string.key_access_error));
                return;
            }

            JSONObject authData = new JSONObject()
                    .put("clientData", challengeB64)
                    .put("signatureData", Base64.encodeToString(signatureData, Base64.URL_SAFE));

            MediaType media = MediaType.parse("application/json; charset=utf-8");

            RequestBody data = RequestBody.create(media, authData.toString());

            Retrofit retro = new Retrofit.Builder()
                    .baseUrl(info.getString("baseURL"))
                    .build();

            U2F.Authentication auth = retro.create(U2F.Authentication.class);
            Call<ResponseBody> authCall = auth.authenticate(data);
            authCall.enqueue(new AuthenticationCallback());

        } catch (Exception e) {

            displayInPhoneDialog(getString(R.string.authentication_gen_error));

        }

    }

    /**
     * Convenience method for displaying a dialog.
     * @param text Text to display in the dialog.
     * param img The image to display underneath the text.
     */
    private void displayInPhoneDialog(String text/*, Image img*/) {

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

            if (body != null) {

                Bundle data = getIntent().getExtras();
                String challenge = data.getString("challenge");

                if (data.getString("type").equals("R")) {

                    String infoURL = data.getString("infoURL");
                    String userID  = data.getString("userID");
                    register(challenge, infoURL, body, userID);

                } else if (data.getString("type").equals("A")) {

                    String keyID = data.getString("keyID");
                    authenticate(challenge, keyID, body);

                }

            }

        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            t.printStackTrace();
            displayInPhoneDialog(getString(R.string.info_request_error));
        }

    }

    /**
     * Describes what should be done once the the server has responded to the
     * device's registration request.
     */
    private class RegistrationCallback implements Callback<ResponseBody> {

        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

            String body = "No response.";

            try {
                body = response.body().string();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            if (response.code() == 200) {

                try {

                    keyManager = new KeyManager(serverRegistrations, firstRegistration);
                    Bundle extras = getIntent().getExtras();

                    keyManager.registerWithServer(
                            extras.getString("appID"),
                            extras.getString("infoURL"),
                            extras.getString("keyID"),
                            extras.getString("userID")
                    );

                    keyManager.saveRegistrations();

                } catch (KeyManager.UserAlreadyRegisteredException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            displayInPhoneDialog(body);

        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            t.printStackTrace();
            displayInPhoneDialog(getString(R.string.registration_request_error));
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
            t.printStackTrace();
            displayInPhoneDialog(getString(R.string.authentication_request_error));
        }

    }

    public static void printFile(File f) {

        try {

            Scanner sc = new Scanner(f);

            while (sc.hasNextLine()) {

                System.out.println(sc.nextLine());

            }

        } catch (Exception e) {

            System.out.println("File didn't exist!");

        }

    }

}