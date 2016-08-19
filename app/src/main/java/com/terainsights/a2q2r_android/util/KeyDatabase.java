package com.terainsights.a2q2r_android.util;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * A wrapper for {@link SQLiteDatabase}, containing convenience methods for
 * querying a U2F-compliant database.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 8/19/16
 */
public class KeyDatabase {

    /**
     * Just a simple Tuple.
     */
    public static class ServerInfo {
        public String appName;
        public String baseURL;
    }

    /**
     * Feeds key data into the main activity's ListView.
     */
    public static KeyAdapter KEY_ADAPTER;

    /**
     * The core database being manipulated with key data.
     */
    private SQLiteDatabase database;

    /**
     * Opens a database from the given file or creates it, and then ensures that the
     * database has the necessary 2Q2R tables.
     * @param databaseFile The file to create or open the database from.
     */
    public KeyDatabase(File databaseFile) {

        this.database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        this.database.execSQL("CREATE TABLE IF NOT EXISTS keys(" +
                              "keyID    TEXT PRIMARY KEY NOT NULL," +
                              "appID    TEXT NOT NULL," +
                              "counter  INT  NOT NULL," +
                              "userID   TEXT NOT NULL," +
                              "lastUsed TEXT NOT NULL)");
        this.database.execSQL("CREATE TABLE IF NOT EXISTS servers(" +
                              "appID   TEXT PRIMARY KEY NOT NULL," +
                              "baseURL TEXT NOT NULL," +
                              "appName TEXT NOT NULL)");

    }

    /**
     * Updates the static U2F KeyAdapter with the latest key data.
     */
    public void refreshKeyInfo() {

        Cursor c = database.rawQuery("SELECT keyID _id, userID, appName, baseURL, lastUsed, counter " +
                                 "FROM keys, servers " +
                                 "WHERE keys.appID = servers.appID " +
                                 "ORDER BY lastUsed DESC", null);

        KEY_ADAPTER.changeCursor(c);

    }

    /**
     * Retrieves the counter for the given registration key.
     * @param keyID The handle of the key in use.
     * @return The counter (pre-increment) for the given key, or -1 if no such
     *         counter was found.
     */
    public int getCounter(String keyID) {

        Cursor cursor = database.rawQuery("SELECT counter " +
                                          "FROM keys " +
                                          "WHERE keyID = '" + keyID + "'", null);

        if (!cursor.moveToFirst()) {
            cursor.close();
            return -1;
        }

        int counter = cursor.getInt(cursor.getColumnIndex("counter"));
        cursor.close();

        return counter;

    }

    /**
     * Increments the counter for a given key. This should be called after
     * a successful authentication request to the target 2Q2R server.
     *
     * @param keyID The key to
     */
    public void setCounter(String keyID, String counter) {

        database.execSQL("UPDATE keys SET counter = " + counter +
                " WHERE keyID = '" + keyID + "'");

        refreshKeyInfo();

    }

    /**
     * Retrieves cached info for the the given server.
     * @param appID The U2F ID of the server.
     * @return A {@link ServerInfo} object containing
     *         a 2Q2R domain and application name, or
     *         null if info could not be found.
     */
    public ServerInfo getServerInfo(String appID) {

        ServerInfo result = new ServerInfo();

        Cursor cursor = database.rawQuery("SELECT baseURL, appName " +
                                          "FROM servers " +
                                          "WHERE appID = '" + appID + "'", null);

        if (!cursor.moveToFirst())
            return null;

        result.baseURL = cursor.getString(cursor.getColumnIndex("baseURL"));
        result.appName = cursor.getString(cursor.getColumnIndex("appName"));

        cursor.close();

        return result;

    }

    /**
     * Appends key data for a new registration to the database, and
     * notifies all key registration listeners. Should NOT be called
     * unless the key's server has already been saved to the device
     * using {@code insertNewServer()}.
     * @param keyID  The registration key's U2F-compliant handle.
     * @param appID  The ID of the server the key is registered to.
     * @param userID The username of the account the key belongs to.
     */
    public void insertNewKey(String keyID, String appID, String userID) {

        DateFormat df = new SimpleDateFormat("yyyy/MM/dd' 'HH:mm");
        df.setTimeZone(TimeZone.getDefault());
        String dtTm = df.format(new Date());

        database.execSQL("INSERT INTO keys VALUES ('" +
                         keyID  + "','" +
                         appID  + "','" +
                         0      + "','" +
                         userID + "','" +
                         dtTm   + "')");

        refreshKeyInfo();

    }

    /**
     * Appends information for a new server to the database.
     * @param appID   The U2F server ID.
     * @param baseURL The 2Q2R server domain.
     * @param appName The human legible application name.
     */
    public void insertNewServer(String appID, String baseURL, String appName) {

        database.execSQL("INSERT INTO servers VALUES ('" +
                appID   + "','" +
                baseURL + "','" +
                appName + "')");

    }

    /**
     * Checks to see if the device has the given registration key.
     * @param keyID The handle for the key to check for.
     * @return True if the key is present, false otherwise.
     */
    public boolean hasKey(String keyID) {

        Cursor cursor = database.rawQuery("SELECT keyID FROM keys WHERE keyID = '" +
                keyID + "'", null);

        boolean result = cursor.moveToFirst();
        cursor.close();

        return result;

    }

    /**
     * Checks to see if the device already has information cached for the
     * given server.
     * @param appID The server ID to look for.
     * @return True if the server is already known, false otherwise.
     */
    public boolean hasServer(String appID) {

        Cursor cursor = database.rawQuery("SELECT appID FROM servers WHERE appID = '" +
                appID + "'", null);

        boolean result = cursor.moveToFirst();
        cursor.close();

        return result;

    }

    /**
     * Checks if the phone is already registered with the given account
     * @param userID The username of the account to check.
     * @param appID  The ID of the account's server.
     * @return True if the account is already registered, false otherwise.
     */
    public boolean checkUserAlreadyRegistered(String userID, String appID) {

        Cursor cursor = database.rawQuery("SELECT userID, appID " +
                                          "FROM keys " +
                                          "WHERE userID = '" + userID + "' " +
                                          "AND appID = '" + appID + "'",
                                          null);

        boolean result = cursor.getCount() > 0;
        cursor.close();

        return result;

    }

    /**
     * Completely wipes the 2Q2R database by clearing "servers" and "keys".
     */
    public void clear() {

        database.execSQL("DELETE FROM keys");
        database.execSQL("DELETE FROM servers");

        refreshKeyInfo();

    }

    /**
     * Indicates that a registration operation is faulty because the device is already
     * registered with the given account.
     */
    public static class UserAlreadyRegisteredException extends Exception {}

}
