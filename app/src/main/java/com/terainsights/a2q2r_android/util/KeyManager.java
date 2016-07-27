package com.terainsights.a2q2r_android.util;

import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Scanner;

/**
 * Class for modification of a server registration file.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 6/13/16
 */
public class KeyManager {

    File file;
    JSONObject serverRegs;

    /**
     * Constructs a new KeyManager and attaches it to a file containing a
     * JSON array of server information objects.
     * @param file The file to store server information in.
     * @param firstRegistration True if the file does not contain any
     *                          existing registration information.
     */
    public KeyManager(File file, boolean firstRegistration) throws FileNotFoundException {

        this.file = file;
        loadServerRegs(firstRegistration);

    }

    /**
     * Populates `serverRegs` with JSON values from `file`.
     * @param firstRegistration True if the file does not contain any existing
     *                          server information, and the reading operation
     *                          can be skipped.
     */
    private void loadServerRegs(boolean firstRegistration) throws FileNotFoundException {

        try {

            if (!firstRegistration) {

                Scanner sc = new Scanner(file);
                StringBuilder sb = new StringBuilder();

                while (sc.hasNext())
                    sb.append(sc.next());

                if (sb.length() > 0) {

                    serverRegs = new JSONObject(sb.toString());
                    return;

                }

            }

            serverRegs = new JSONObject()
                    .put("servers", new JSONObject())
                    .put("keys", new JSONObject());

            return;

        } catch (JSONException e) {
            System.err.println("The registrations file was corrupted.");
        }

    }

    /**
     * Appends a new server registration object to cache, or updates existing data.
     * This does NOT perform any U2F operations--it only stores data.
     * @param appID The application ID of the server; unique for each server. This
     *              is also used as an index to access the proper registration data
     *              whenever the user authenticates their identity.
     * @param infoURL URL containing standardized information on communication with
     *                the server.
     * @param keyID The handle for the key pair generated for the user registration.
     * @param userID The username of the registering account. Storing usernames on the
     *               phone ensures that the app can prevent the user from wastefully
     *               registering the same account multiple times on the same device.
     * @throws UserAlreadyRegisteredException if the user already registered the device.
     * @throws JSONException if an error occurred while saving the registration.
     */
    public void registerWithServer(String appID, String infoURL, String keyID, String userID)
            throws UserAlreadyRegisteredException, JSONException {

        JSONObject servers = serverRegs.getJSONObject("servers");
        JSONObject keys    = serverRegs.getJSONObject("keys");

        if (servers.has(appID)) {

            servers.getJSONObject(appID).put("infoURL", infoURL);
            JSONArray users = servers.getJSONObject(appID).getJSONArray("users");

            if (users.toString().contains("\"" + userID + "\""))
                throw new UserAlreadyRegisteredException();
            else
                users.put(userID);

        } else {

            servers.put(appID, new JSONObject()
                    .put("infoURL", infoURL)
                    .put("users", new JSONArray()
                            .put(userID)));

        }

        keys.put(keyID, 0); // create a counter for the key
        System.out.println("Registrations:\n" + serverRegs.toString(4));

    }

    /**
     * Grabs the `infoURL` for a given server.
     * @param appID The Base64 server ID.
     * @return The `infoURL` for the given server, or null if it wasn't found.
     */
    public String getInfoURL(String appID) {

        try {

            return serverRegs.getJSONObject("servers").getJSONObject(appID).getString("infoURL");

        } catch (JSONException e) {

            return null;

        }

    }

    /**
     * Checks and updates the U2F counter for the given key handle.
     * @param keyID The handle for the key being used.
     * @return [4 bytes] The number of times the key has been used.
     * @throws JSONException if the counter couldn't be found for the
     *         given key handle.
     */
    public byte[] getCounter(String keyID) throws JSONException {

        System.out.println("Do we have the counter?");
        int counter = serverRegs.getJSONObject("keys").getInt(keyID);
        System.out.println("We do.");
        serverRegs.getJSONObject("keys").put(keyID, counter + 1);
        return ByteBuffer.allocate(4).putInt(counter).array();

    }

    /**
     * Saves all of the device's registrations in cache to internal storage.
     * @throws IOException if there was an issue writing the file.
     */
    public void saveRegistrations() throws IOException {

        System.out.println("File path: " + file.getAbsolutePath().toString());
        BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file.getAbsolutePath().toString()));
        fileWriter.write(serverRegs.toString());
        fileWriter.close();

        System.out.println("File saved as:");

        Scanner sc = new Scanner(file);

        while (sc.hasNextLine()) {
            System.out.println(sc.nextLine());
        }

        System.out.println("The scanner didn't encounter an error.");

    }

    /**
     * Wipes all registrations in cache. `saveRegistrations()` must be
     * called to preserve the changes.
     */
    public void clearRegistrations() {

        try {

            serverRegs.remove("servers");
            serverRegs.remove("keys");
            serverRegs.put("servers", new JSONObject());
            serverRegs.put("keys", new JSONObject());

        } catch (JSONException e) {

            e.printStackTrace();

        }

    }

    /**
     * Thrown if a user attempts to register twice on the same device.
     */
    static class UserAlreadyRegisteredException extends Exception {}

}
