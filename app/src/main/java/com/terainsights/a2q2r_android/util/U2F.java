package com.terainsights.a2q2r_android.util;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.iid.FirebaseInstanceId;
import com.jaredrummler.android.device.DeviceName;
import com.terainsights.a2q2r_android.R;
import com.terainsights.a2q2r_android.dialog.AuthDialog;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.json.JSONException;
import org.json.JSONObject;

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
import java.util.HashMap;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * Contains all of the static state needed to process a U2F challenge and talk to the
 * 2Q2R server. Should always be given the application context, rather than an individual
 * activity, because the {@code CTX} is used exclusively to open dialogs and access
 * resources.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 8/31/16
 */
public class U2F {

    /**
     * Soon to be removed in favor of a SQLite database.
     */
    public static Context CTX;

    /**
     * The application's internal database containing key/server information.
     */
    public static KeyDatabase DATABASE;

    /**
     * Static cache for use by U2F state until it is safe to insert a new registration
     * into the database.
     */
    public static HashMap<String, String> TEMP = new HashMap<>();

    /**
     * GET call for server info.
     */
    public interface ServerInfo {
        @GET("./")
        Call<ResponseBody> getInfo();
    }

    /**
     * POST call for sending U2F registration data.
     */
    public interface Registration {
        @POST("v1/register")
        Call<ResponseBody> register(@Body RequestBody body);
    }

    /**
     * POST call for sending U2F authentication data.
     */
    public interface Authentication {
        @POST("v1/auth")
        Call<ResponseBody> authenticate(@Body RequestBody body);
    }

    /**
     * This is the U2F entry point for the parsed QR code data, which is returned from
     * a successful ScanActivity.
     *
     * @param qrContent The String extracted from a scanned QR.
     */
    public static void process(String qrContent) {

        if (Utils.identifyQRType(qrContent) == 'R') {

            String[] splitQR = qrContent.split(" ");
            String challenge = splitQR[1];
            String infoURL = splitQR[2];
            String userID = splitQR[3];

            if (!infoURL.endsWith("/"))
                infoURL += "/";

            Retrofit retro = new Retrofit.Builder()
                    .baseUrl(infoURL)
                    .build();

            TEMP.put("challenge", challenge);
            TEMP.put("infoURL", infoURL);
            TEMP.put("userID", userID);
            TEMP.put("qrContent", qrContent);

            U2F.ServerInfo info = retro.create(U2F.ServerInfo.class);
            Call<ResponseBody> infoCall = info.getInfo();
            infoCall.enqueue(new InformationCallback());

        } else if (Utils.identifyQRType(qrContent) == 'A') {

            String[] splitQR = qrContent.split(" ");
            String base64AppID = splitQR[1];
            String challenge = splitQR[2];
            String keyID = splitQR[3];

            TEMP.put("serverCounter", splitQR[4]);

            String appURL = DATABASE.getServerInfo(base64AppID).appURL;

            if (appURL != null)
                authenticate(challenge, keyID, base64AppID, appURL);
            else
                Text.displayShort(CTX, R.string.registration_not_found_error);

        } else {

            Text.displayShort(CTX, R.string.invalid_qr_error);

        }

    }

    /**
     * Sends an error to the server's authentication route, indicating that
     * the user rejected the authentication attempt.
     * @param qrContent The full message read from an authentication QR.
     */
    public static void decline(String qrContent) {

        try {

            String[] splitQR = qrContent.split(" ");
            JSONObject authData = new JSONObject()
                    .put("successful", false)
                    .put("data", new JSONObject()
                        .put("challenge", splitQR[2])
                        .put("errorMessage", "Authentication declined.")
                        .put("errorStatus", 401));
            String appURL = U2F.DATABASE.getServerInfo(qrContent.split(" ")[1]).appURL;

            MediaType media = MediaType.parse("application/json; charset=utf-8");

            RequestBody data = RequestBody.create(media, authData.toString());

            Retrofit retro = new Retrofit.Builder()
                    .baseUrl(appURL)
                    .build();

            if (qrContent.charAt(0) == 'R') {
                U2F.Registration reg = retro.create(U2F.Registration.class);
                Call<ResponseBody> regCall = reg.register(data);
                regCall.enqueue(new RegistrationCallback());
            } else if (qrContent.charAt(0) == 'A') {
                U2F.Authentication auth = retro.create(U2F.Authentication.class);
                Call<ResponseBody> authCall = auth.authenticate(data);
                authCall.enqueue(new AuthenticationCallback());
            }

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        }

    }

    /**
     * Provided with all the necessary data from a U2F registration request,
     * generates a registration response which conforms exactly to the
     * specifications outlined in the U2F standard, and sends it to the
     * "/register" directory of the appURL obtained from the relying
     * party's {@code infoURL}.
     *
     * @param challengeB64 A challenge provided by the server that the device is
     *                     being registered with [web-safe-Base64 of 32 bytes].
     * @param serverInfo   Stringified JSON containing server details.
     * @param userID       The username of the account registering the device, used to
     *                     prevent multiple registrations of the same device to the
     *                     same account.
     */
    public static void register(String challengeB64, String serverInfo, String userID) {

        try {

            JSONObject info = new JSONObject(serverInfo);
            String keyID = Utils.genKeyID();
            PublicKey pubKey = Utils.genKeys(keyID, CTX);

            if (pubKey != null) {

                if (DATABASE.checkUserAlreadyRegistered(userID, info.getString("appID")))
                    throw new KeyDatabase.UserAlreadyRegisteredException();

                KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
                ks.load(null);

                Certificate x509 = ks.getCertificate(keyID);
                ECPoint keyW = ((ECPublicKey) x509.getPublicKey()).getW();

                //////////////////////////////////////////////////////////////

                byte[] keyX = keyW.getAffineX().toByteArray();
                byte[] keyY = keyW.getAffineY().toByteArray();
                byte[] pref = {0x4};

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                bytes.write(pref);
                bytes.write(keyX, keyX.length - 32, 32);
                bytes.write(keyY, keyY.length - 32, 32);

                //////////////////////////////////////////////////////////////

                String clientData = new JSONObject()
                        .put("typ", "navigator.id.finishEnrollment")
                        .put("challenge", challengeB64)
                        .put("origin", info.getString("appURL"))
                        .toString().replace("\\", "");

                MessageDigest md = MessageDigest.getInstance("SHA-256");

                byte[] futureUse = {0x00};
                byte[] appParam = md.digest(info.getString("appURL").getBytes());
                byte[] challenge = md.digest(clientData.getBytes());
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

                byte[] reserved = {0x05};
                byte[] handleLength = {(byte) keyHandle.length};
                byte[] certificate = x509.getEncoded();

                bytes.reset();

                bytes.write(reserved);
                bytes.write(publicKey);
                bytes.write(handleLength);
                bytes.write(keyHandle);
                bytes.write(certificate);
                bytes.write(signature);

                byte[] regRes = bytes.toByteArray();

                //////////////////////////////////////////////////////////////

                TEMP.put("appID", info.getString("appID"));
                TEMP.put("keyID", keyID);
                TEMP.put("appName", info.getString("appName"));
                TEMP.put("appURL", info.getString("appURL"));

                Log.i("MONITOR", "The keyID generated is: " + keyID);

                JSONObject registrationData = new JSONObject()
                        .put("successful", true)
                        .put("data", new JSONObject()
                            .put("type", "2q2r")
                            .put("deviceName", DeviceName.getDeviceName())
                            .put("fcmToken", FirebaseInstanceId.getInstance().getToken())
                            .put("clientData", Base64.encodeToString(clientData.getBytes(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP))
                            .put("registrationData", Base64.encodeToString(regRes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP)));

                MediaType media = MediaType.parse("application/json; charset=utf-8");

                RequestBody data = RequestBody.create(media, registrationData.toString());

                Retrofit retro = new Retrofit.Builder()
                        .baseUrl(info.getString("appURL"))
                        .build();

                U2F.Registration reg = retro.create(U2F.Registration.class);
                Call<ResponseBody> regCall = reg.register(data);
                regCall.enqueue(new RegistrationCallback());

            } else if (Build.VERSION.SDK_INT < 19) {

                Text.displayShort(CTX, R.string.outdated_device_error);

            } else {

                Text.displayShort(CTX, R.string.key_gen_error);

            }

        } catch (JSONException | KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            Text.displayShort(CTX, R.string.key_gen_error);
        } catch (IOException e) {
            e.printStackTrace();
            Text.displayShort(CTX, R.string.registration_gen_error);
        } catch (Utils.AuthExpiredException e) {
            e.printStackTrace();
            Text.displayShort(CTX, R.string.auth_timeout_error);
        } catch (KeyDatabase.UserAlreadyRegisteredException e) {
            e.printStackTrace();
            Text.displayShort(CTX, R.string.existing_registration_error);
        }

    }

    /**
     * Authenticates the user. First, the server's info is grabbed from
     * internal JSON storage using the `base64AppID` as an index. Then,
     * `challenge` is signed using the private key and the result
     * is sent back to the server.
     *
     * @param challengeB64 A web-safe-Base64-encoded challenge of 32
     *                     bytes sent from the server.
     * @param keyID        A web-safe-Base64-encoded handle of 16 bytes sent
     *                     from the server to identify the key it expects to
     *                     be used for authentication.
     * @param appID        A web-safe-Base64 encoded server ID.
     * @param appURL      The base URL for the U2F authentication route.
     */
    private static void authenticate(String challengeB64, String keyID, String appID,
                                     String appURL) {

        try {

            JSONObject clientData = new JSONObject()
                    .put("typ", "navigator.id.getAssertion")
                    .put("challenge", challengeB64)
                    .put("origin", appURL);

            String serializedClientData = clientData.toString();

            MessageDigest md = MessageDigest.getInstance("SHA-256");

            byte[] userPresence = {0b00000001};
            byte[] counter      = Utils.toByteArray(Integer.parseInt(TEMP.get("serverCounter")));
            byte[] appParam     = md.digest(appURL.getBytes());
            byte[] challenge    = md.digest(serializedClientData.getBytes());

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
                Toast.makeText(CTX, CTX.getString(R.string.key_access_error), Toast.LENGTH_LONG).show();
                return;
            }

            JSONObject authData = new JSONObject()
                    .put("successful", true)
                    .put("data", new JSONObject()
                        .put("clientData", Base64.encodeToString(serializedClientData.getBytes(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP))
                        .put("signatureData", Base64.encodeToString(signatureData, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP)));

            MediaType media = MediaType.parse("application/json; charset=utf-8");

            RequestBody data = RequestBody.create(media, authData.toString());

            Retrofit retro = new Retrofit.Builder()
                    .baseUrl(appURL)
                    .build();

            TEMP.put("keyID", keyID);

            U2F.Authentication auth = retro.create(U2F.Authentication.class);
            Call<ResponseBody> authCall = auth.authenticate(data);
            authCall.enqueue(new AuthenticationCallback());

        } catch (Exception e) {

            e.printStackTrace();
            Text.displayShort(CTX, R.string.authentication_gen_error);

        }

    }

    /**
     * Describes what the activity should do once information has either been
     * loaded from the server, or once the attempt failed.
     */
    private static class InformationCallback implements Callback<ResponseBody> {

        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

            try {

                String body = response.body().string();
                JSONObject serverData = new JSONObject(body);

                Intent intent = new Intent(CTX, AuthDialog.class);
                intent.putExtra("serverName", serverData.getString("appName"));
                intent.putExtra("serverURL", serverData.getString("appURL"));
                intent.putExtra("authData", TEMP.get("qrContent"));
                intent.putExtra("missed", 0);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                TEMP.put("serverData", body);

                CTX.startActivity(intent);

            } catch (IOException | JSONException e) {

                e.printStackTrace();

            }

        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            t.printStackTrace();
            Text.displayShort(CTX, R.string.info_request_error);
        }

    }

    /**
     * Describes what should be done once the the server has responded to the
     * device's registration request.
     */
    private static class RegistrationCallback implements Callback<ResponseBody> {

        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

            String body = "Sorry, the server didn't reply with a status message.";

            try {
                body = response.body().string();
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
            }

            if (response.code() == 200) {

                if (!DATABASE.hasServer(TEMP.get("appID")))
                    DATABASE.insertNewServer(TEMP.get("appID"),
                                             TEMP.get("appURL"),
                                             TEMP.get("appName"));

                DATABASE.insertNewKey(TEMP.get("keyID"),
                                      TEMP.get("appID"),
                                      TEMP.get("userID"));

            }

            Text.displayShort(CTX, body);

        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            t.printStackTrace();
            Text.displayShort(CTX, R.string.registration_request_error);
        }

    }

    /**
     * Describes what should be done once the server has responded to the
     * device's authentication request.
     */
    private static class AuthenticationCallback implements Callback<ResponseBody> {

        @Override
        public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

            try {

                if (response.code() == 200) {
                    DATABASE.setCounter(TEMP.get("keyID"), TEMP.get("serverCounter"));
                    Text.displayShort(CTX, response.body().string());
                } else {
                    System.out.println("Server says: \"" + response.body().string() + "\"");
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
                Text.displayShort(CTX, "Sorry, the server didn't reply with a status message.");
            }

        }

        @Override
        public void onFailure(Call<ResponseBody> call, Throwable t) {
            t.printStackTrace();
            Text.displayShort(CTX, R.string.authentication_request_error);
        }

    }

}
