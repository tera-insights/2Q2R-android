package com.terainsights.a2q2r_android.util;

import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Base64;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

/**
 * Contains utility methods for key generation and signing of data.
 * QR documentation can be found <a href="https://github.com/alinVD/2Q2R-lib/wiki/QR-code-protocol"
 * >here</a>.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/22/16
 */
public class Utils {

    /**
     * A convenience method to check whether a QR code complies with 2Q2R standards.
     * @param decodedQR The String parsed from a QR.
     * @return 'R' - registration QR
     *         'A' - authentication QR
     *          0  - invalid QR
     */
    public static char identifyQRType(String decodedQR) {

        String[] split = decodedQR.split(" ");

        if (split[0].equals("R")) {

            if (split.length != 4)
                return 0;

            if (Base64.decode(split[1], Base64.URL_SAFE).length != 32)
                return 0;

            if (!split[2].matches("[a-zA-Z0-9:/\\.]+"))
                return 0;

            return 'R';

        } else if (split[0].equals("A")) {

            if (split.length != 4)
                return 0;

            if (Base64.decode(split[1], Base64.URL_SAFE).length != 32)
                return 0;

            if (Base64.decode(split[2], Base64.URL_SAFE).length != 32)
                return 0;

            return 'A';

        } else { return 0; }

    }

    /**
     * Generates cryptographically secure random bytes for use as a U2F key handle.
     * @return A web-safe-Base64 representation of a 16 random bytes for use as a key handle.
     */
    public static String genKeyID() {

        byte[] handle = new byte[16];
        new SecureRandom().nextBytes(handle);

        return Base64.encodeToString(handle, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);

    }

    /**
     * Generates a new keypair for the device, storing the private key and
     * returning the public key.
     * @param base64KeyID The handle for the keys being generated, which will be stored by
     *                    the server to access the key pair during later authentication.
     * @param context The current application context, necessary because
     *                key stores are only accessible by the app that created.
     * @return The generated public key, or `null` if there was an issue
     *          with key generation or the device lacks the minimum required
     *          API level. If the device is already registered with the
     *          given server, returns the existing public key for the server.
     */
    public static PublicKey genKeys(String base64KeyID, Context context) {

        try {

            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);

            if (ks.containsAlias(base64KeyID))
                return ks.getCertificate(base64KeyID).getPublicKey();

            if (Build.VERSION.SDK_INT >= 23) {

                KeyPairGenerator gen = KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_EC,
                        "AndroidKeyStore"
                );

                gen.initialize(
                        new KeyGenParameterSpec.Builder(
                                base64KeyID,
                                KeyProperties.PURPOSE_SIGN)
                                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                                .setDigests(KeyProperties.DIGEST_SHA256)
                                .setUserAuthenticationRequired(true)
                                .setUserAuthenticationValidityDurationSeconds(5 * 60)
                                .build()
                );

                return gen.genKeyPair().getPublic();

            } else if (Build.VERSION.SDK_INT >= 19) {

                KeyPairGenerator gen = KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_RSA,
                        "AndroidKeyStore"
                );

                gen.initialize(
                        new KeyPairGeneratorSpec.Builder(context)
                            .setSubject(new X500Principal("CN=fake"))
                            .setSerialNumber(new BigInteger("1"))
                            .setStartDate(new Date(1970, 1, 1))
                            .setEndDate(new Date(2048, 1, 1))
                            .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                            .setEncryptionRequired()
                            .setAlias(base64KeyID)
                            .setKeyType(KeyProperties.KEY_ALGORITHM_EC)
                            .setKeySize(256)
                            .build()
                );

                return gen.genKeyPair().getPublic();

            } else { return null; }

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;

    }

    /**
     * Signs an array of bytes using a private key looked up in the KeyStore.
     * @param data The bytes to be signed.
     * @param index Should be an `appID` [Base64 of 32 bytes], used to look
     *              up the private key for a specific server in the KeyStore.
     * @return A signature over `data` using a key looked up in the KeyStore
     *          using `index`.
     */
    public static byte[] sign(byte[] data, String index) throws AuthExpiredException {

        try {

            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            PrivateKey privKey = (PrivateKey) ks.getKey(index, null);


            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initSign(privKey);
            sig.update(data);

            return sig.sign();

        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        } catch (UnrecoverableEntryException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new AuthExpiredException();
        }

        return null;

    }

    /**
     * Used in place of {@link UserNotAuthenticatedException} for backwards compatibility.
     */
    public static class AuthExpiredException extends Exception {}

}
