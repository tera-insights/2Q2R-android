package com.terainsights.a2q2r_android;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

        if (firstRegistration) {

            serverRegs = new JSONObject();
            return;

        }

        try {

            FileReader reader = new FileReader(file);
            StringBuilder sb = new StringBuilder();

            while (reader.ready()) {

                int c = reader.read();

                if (c == -1)
                    break;

                sb.append(c);

            }

            if (sb.length() > 0)
                serverRegs = new JSONObject(sb.toString());

        } catch (FileNotFoundException e) {
            System.err.println("Registration file not found!");
        } catch (IOException e) {
            System.err.println("Failed to read registration file into a String!");
        } catch (JSONException e) {
            System.err.println(e.toString());
        }

        serverRegs = new JSONObject();
        System.out.println("The file was empty and is a " + (file.isFile() ? "file" : "directory") + ".");

    }

    /**
     * Appends a new server registration object to cache, or updates the `appName` and
     * `infoURL` if the server already exists. Does NOT actually register with the
     * server--registration and authentication operations are handled by the ScanActivity.
     * @param appID The application ID of the server; unique for each server. This
     *              is also used as an index to access the proper registration data
     *              whenever the user authenticates their identity.
     * @param appName User-legible server identifier, which may be used in GUI's.
     * @param infoURL URL containing standardized information on communication with
     *                the server.
     * @return True if a registration object was successfully added to cache, or an
     *          existing object was updated.
     */
    public boolean registerWithServer(String appID, String appName, String infoURL, String keyID) {

        try {

            if (serverRegs.has(appID)) {

                serverRegs.getJSONObject(appID)
                    .put("appName", appName)
                    .put("infoURL", infoURL);
                ((List<String>) serverRegs.getJSONObject(appID).get("keyID's")).add(keyID);

            }

            JSONObject obj = new JSONObject()
                .put("appName", appName)
                .put("infoURL", infoURL)
                .put("counter", 0)
                .put("keyID's", new ArrayList<String>());

            serverRegs.put(appID, obj);
            return true;

        } catch (JSONException e) { return false; }

    }

    /**
     * Retrieves the information needed for an authentication operation
     * with the given server.
     * @param base64AppID The application ID [Base64 of 32 bytes] of
     *                    the server sending the request. Used to
     *                    lookup the server's information.
     * @return A JSON object containing the `infoURL` [String] and
     *          `counter` [int], for authentication, or null if
     *          the server info wasn't found or an exception occurred.
     */
    public JSONObject getAuthenticationInfo(String base64AppID) {

        try {

            if (serverRegs.has(base64AppID)) {

                JSONObject obj = serverRegs.getJSONObject(base64AppID);
                int counter = obj.getInt("counter");
                obj.put("counter", counter + 1);

                String infoURL = obj.getString("infoURL");

                return new JSONObject()
                        .put("infoURL", infoURL)
                        .put("counter", counter);

            }

        } catch (JSONException e) {}

        return null;

    }

    /**
     * Saves all of the device's registrations in cache to internal storage.
     */
    public void saveRegistrations() {

        try {

            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(file, false));
            fileWriter.write(serverRegs.toString());
            System.out.println("Successfully saved registrations to file.");

        } catch (IOException e) {
            System.err.println("Failed to save registrations to file!");
        }

    }

}