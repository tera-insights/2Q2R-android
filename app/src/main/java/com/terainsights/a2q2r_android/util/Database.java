package com.terainsights.a2q2r_android.util;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

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

        insertNewServerInfo("_T-wi0wzr7GCi4vsfsXsUuKOfmiWLiHBVbmJJPidvhA", "http://10.20.146.247:8081/", "2Q2R Server Demo");

        insertNewKey("afbabfjajbfjjajfa87bf28b", "_T-wi0wzr7GCi4vsfsXsUuKOfmiWLiHBVbmJJPidvhA", "sam@tera.com");
        insertNewKey("aufauajfnfaf89h3293f893g9fgf", "_T-wi0wzr7GCi4vsfsXsUuKOfmiWLiHBVbmJJPidvhA", "alin@tera.com");
        insertNewKey("x53j3x3j3j3jx3nhb3ub3uf", "_T-wi0wzr7GCi4vsfsXsUuKOfmiWLiHBVbmJJPidvhA", "josh@tera.com");
        insertNewKey("ffffffffffffffffff", "_T-wi0wzr7GCi4vsfsXsUuKOfmiWLiHBVbmJJPidvhA", "joost@tera.com");
        insertNewKey("qweqeqru33jfkekeekdke", "_T-wi0wzr7GCi4vsfsXsUuKOfmiWLiHBVbmJJPidvhA", "chris@tera.com");
        insertNewKey("xehfwheuf728f22f8f2", "_T-wi0wzr7GCi4vsfsXsUuKOfmiWLiHBVbmJJPidvhA", "jess@tera.com");
        insertNewKey("2df6g2f26db23d72d", "_T-wi0wzr7GCi4vsfsXsUuKOfmiWLiHBVbmJJPidvhA", "tiffany@tera.com");
        insertNewKey("782hfh827fh82fn82fn", "_T-wi0wzr7GCi4vsfsXsUuKOfmiWLiHBVbmJJPidvhA", "jacob@tera.com");
        insertNewKey("2fn928nf398gn348gn", "_T-wi0wzr7GCi4vsfsXsUuKOfmiWLiHBVbmJJPidvhA", "jon@tera.com");

    }

    /**
     * Returns data for every key stored on the phone in order of the time and date
     * the keys were last used.
     * @return A structure with five ordered lists of key data.
     */
    public KeyData getDisplayableKeyInformation() {

        KeyData result = new KeyData();
        Cursor cursor  = database.rawQuery("SELECT userID, appName, baseURL, lastUsed, counter " +
                                           "FROM keys, servers " +
                                           "WHERE keys.appID = servers.appID " +
                                           "ORDER BY lastUsed DESC", null);

        int userIDIndex    = cursor.getColumnIndex("userID");
        int appNameIndex   = cursor.getColumnIndex("appName");
        int baseURLIndex   = cursor.getColumnIndex("baseURL");
        int lastLoginIndex = cursor.getColumnIndex("lastUsed");
        int counterIndex   = cursor.getColumnIndex("counter");

        if (!cursor.moveToFirst())
            return result;

        String isoDate = cursor.getString(lastLoginIndex).split(" ")[0].replace('-', '/');
        String isoTime = cursor.getString(lastLoginIndex).split(" ")[1];

        isoDate = (isoDate.startsWith("0")) ? isoDate.substring(1) : isoDate;
        isoTime = (isoTime.startsWith("0")) ? isoTime.substring(1) : isoTime;

        result.userIDs.add(cursor.getString(userIDIndex));
        result.appNames.add(cursor.getString(appNameIndex));
        result.baseURLs.add(cursor.getString(baseURLIndex));
        result.dates.add(isoDate.substring(isoDate.indexOf('/') + 1));
        result.times.add(isoTime);
        result.counters.add(cursor.getInt(counterIndex));

        while (cursor.moveToNext()) {

            isoDate = cursor.getString(lastLoginIndex).split(" ")[0].replace('-', '/');
            isoTime = cursor.getString(lastLoginIndex).split(" ")[1];

            isoDate = (isoDate.startsWith("0")) ? isoDate.substring(1) : isoDate;
            isoTime = (isoTime.startsWith("0")) ? isoTime.substring(1) : isoTime;
            result.userIDs.add(cursor.getString(userIDIndex));
            result.appNames.add(cursor.getString(appNameIndex));
            result.baseURLs.add(cursor.getString(baseURLIndex));
            result.dates.add(isoDate.substring(isoDate.indexOf('/') + 1));
            result.times.add(isoTime);
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

        Cursor cursor = database.rawQuery("SELECT baseURL, appName " +
                                          "FROM servers " +
                                          "WHERE appID = '" + appID + "'", null);

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

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm");
        df.setTimeZone(TimeZone.getDefault());
        String dtTm = df.format(new Date());

        database.execSQL("INSERT INTO keys VALUES ('" +
                         keyID  + "','" +
                         appID  + "','" +
                         0      + "','" +
                         userID + "','" +
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
                         appID   + "','" +
                         baseURL + "','" +
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

    /**
     * Indicates that a registration operation is faulty because the device is already
     * registered with the given account.
     */
    public static class UserAlreadyRegisteredException extends Exception {}

}
