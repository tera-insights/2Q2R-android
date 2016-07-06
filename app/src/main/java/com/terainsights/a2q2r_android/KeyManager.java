package com.terainsights.a2q2r_android;

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
    public KeyManager(File file, boolean firstRegistration) {

        this.file = file;
        loadServerRegs(firstRegistration);

    }

    /**
     * Populates `serverRegs` with JSON values from `file`.
     * @param firstRegistration True if the file does not contain any existing
     *                          server information, and the reading operation
     *                          can be skipped.
     */
    private void loadServerRegs(boolean firstRegistration) {

        try {

            if (true) {

                serverRegs = new JSONObject()
                        .put("servers", new JSONObject())
                        .put("keys", new JSONObject());

                return;

            }

            FileReader reader = new FileReader(file);
            StringBuilder sb = new StringBuilder();

            while (reader.ready()) {

                int c = reader.read();

                if (c == -1)
                    break;

                sb.append(c);

            }

            if (sb.length() > 0) {

                serverRegs = new JSONObject(sb.toString());
                return;

            }

            System.out.println("The file was " + sb.length() + " characters long!");
            System.out.println(file.isFile());

        } catch (FileNotFoundException e) {
            System.err.println("Registration file not found!");
        } catch (IOException e) {
            System.err.println("Failed to read registration file into a String!");
        } catch (JSONException e) {
            System.err.println("The registrations file was corrupted.");
        }

        serverRegs = new JSONObject();

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

        System.out.println(serverRegs.toString());
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

        int counter = serverRegs.getJSONObject("keys").getInt(keyID);
        serverRegs.getJSONObject("keys").put(keyID, counter + 1);
        return ByteBuffer.allocate(4).putInt(counter).array();

    }

    /**
     * Saves all of the device's registrations in cache to internal storage.
     * @throws IOException if there was an issue writing the file.
     */
    public void saveRegistrations() throws IOException {

        BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file, false));
        fileWriter.write(serverRegs.toString());

    }

    /**
     * Thrown if a user attempts to register twice on the same device.
     */
    class UserAlreadyRegisteredException extends Exception {}

}
