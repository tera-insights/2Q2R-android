package com.terainsights.a2q2r_android.util;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * A wrapper for {@link SQLiteDatabase}, containing convenience methods for
 * querying a U2F-compliant database.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/28/16
 */
public class Database {

    public static class KeyData {
        public ArrayList<String>  userIDs  = new ArrayList<>();
        public ArrayList<String>  appNames = new ArrayList<>();
        public ArrayList<String>  baseURLs = new ArrayList<>();
        public ArrayList<String>  dates    = new ArrayList<>();
        public ArrayList<String>  times    = new ArrayList<>();
        public ArrayList<Integer> counters = new ArrayList<>();
    }

    public static class ServerInfo {
        public String appName;
        public String baseURL;
    }

    private SQLiteDatabase database;

    /**
     * Opens a database from the given file or creates it, and then ensures that the
     * database has the necessary 2Q2R tables.
     * @param databaseFile The file to create or open the database from.
     */
    public Database(File databaseFile) {

        this.database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        this.database.execSQL("CREATE TABLE IF NOT EXISTS keys(" +
                              "keyID    TEXT     PRIMARY KEY NOT NULL," +
                              "appID    TEXT     NOT NULL," +
                              "counter  INT      NOT NULL," +
                              "userID   TEXT     NOT NULL," +
                              "lastUsed DATETIME NOT NULL)");
        this.database.execSQL("CREATE TABLE IF NOT EXISTS servers(" +
                              "appID   TEXT PRIMARY KEY NOT NULL," +
                              "baseURL TEXT NOT NULL," +
                              "appName TEXT NOT NULL)");

    }

    /**
     * Returns data for every key stored on the phone in order of the time and date
     * the keys were last used.
     * @return A structure with five ordered lists of key data.
     */
    public KeyData getDisplayableKeyInformation() {

        KeyData result = new KeyData();
        Cursor cursor  = database.rawQuery("SELECT userID, appName, baseURL, lastLogin, counter" +
                                           "FROM keys, servers" +
                                           "WHERE keys.appID = servers.appID" +
                                           "ORDER BY lastLogin DEC", null);

        int userIDIndex    = cursor.getColumnIndex("userID");
        int appNameIndex   = cursor.getColumnIndex("appName");
        int baseURLIndex   = cursor.getColumnIndex("baseURL");
        int lastLoginIndex = cursor.getColumnIndex("lastLogin");
        int counterIndex   = cursor.getColumnIndex("counter");

        if (!cursor.moveToFirst())
            return null;

        result.userIDs.add(cursor.getString(userIDIndex));
        result.appNames.add(cursor.getString(appNameIndex));
        result.baseURLs.add(cursor.getString(baseURLIndex));
        result.dates.add(cursor.getString(lastLoginIndex).split(" ")[0].replace('-', '/'));
        result.times.add(cursor.getString(lastLoginIndex).split(" ")[1]);
        result.counters.add(cursor.getInt(counterIndex));

        while (cursor.moveToNext()) {

            result.userIDs.add(cursor.getString(userIDIndex));
            result.appNames.add(cursor.getString(appNameIndex));
            result.baseURLs.add(cursor.getString(baseURLIndex));
            result.dates.add(cursor.getString(lastLoginIndex).split(" ")[0].replace('-', '/'));
            result.times.add(cursor.getString(lastLoginIndex).split(" ")[1]);
            result.counters.add(cursor.getInt(counterIndex));

        }

        return result;

    }

    /**
     * Retrieves and increments the counter for the given registration key.
     * @param keyID The handle of the key in use.
     * @return The counter (pre-increment) for the given key.
     */
    public byte[] getCounter(String keyID) {

        Cursor cursor = database.rawQuery("SELECT counter" +
                                          "FROM keys" +
                                          "WHERE keyID = " + keyID, null);
        cursor.moveToFirst();

        // TODO: Increment the counter.

        return ByteBuffer.allocate(4).putInt(cursor.getInt(cursor.getColumnIndex("counter"))).array();

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

        Cursor cursor = database.rawQuery("SELECT baseURL, appName" +
                                          "FROM servers" +
                                          "WHERE appID = " + appID, null);

        if (!cursor.moveToFirst())
            return null;

        result.baseURL = cursor.getString(cursor.getColumnIndex("baseURL"));
        result.appName = cursor.getString(cursor.getColumnIndex("appName"));

        return result;

    }

    /**
     * Appends key data for a new registration to the database.
     * @param keyID  The registration key's U2F-compliant handle.
     * @param appID  The ID of the server the key is registered to.
     * @param userID The username of the account the key belongs to.
     */
    public void insertNewKey(String keyID, String appID, String userID) {

        Date now = new Date();
        String dtTm = now.getYear() + "-" + now.getMonth() + "-" + now.getDay() + " " +
                now.getHours() + ":" + now.getMinutes() + ":" + now.getSeconds();

        database.execSQL("INSERT INTO keys VALUES ('" +
                         keyID  + "'," +
                         appID  + "'," +
                         0      + "'," +
                         userID + "'," +
                         dtTm   + "')");

    }

    /**
     * Appends information for a new server to the database.
     * @param appID   The U2F server ID.
     * @param baseURL The 2Q2R server domain.
     * @param appName The human legible application name.
     */
    public void insertNewServerInfo(String appID, String baseURL, String appName) {

        database.execSQL("INSERT INTO servers VALUES ('" +
                         appID   + "'," +
                         baseURL + "'," +
                         appName + "')");

    }

    /**
     * Checks if the phone is already registered with the given account
     * @param userID The username of the account to check.
     * @param appID  The ID of the account's server.
     * @return True if the account is already registered, false otherwise.
     */
    public boolean checkUserAlreadyRegistered(String userID, String appID) {

        Cursor cursor = database.rawQuery("SELECT userID, appID" +
                                          "FROM keys" +
                                          "WHERE userID = '?' AND appID = '?'",
                                          new String[]{userID, appID});

        return cursor.getCount() > 0;

    }

}
