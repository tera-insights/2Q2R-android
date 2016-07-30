package com.terainsights.a2q2r_android.util;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * A wrapper for {@link SQLiteDatabase}, containing convenience methods for
 * querying a U2F-compliant database.
 *
 * @author Sam Claus, Tera Insights, LLC
 * @version 7/28/16
 */
public class Database {

    public static class KeyDetails {
        public String userID;
        public String appName;
        public String baseURL;
        public String date;
        public String time;
        public int    counter;
    }

    public static class ServerInfo {
        public String appName;
        public String baseURL;
    }

    private ArrayList<KeyRegistrationListener> listeners;

    private SQLiteDatabase database;

    /**
     * Opens a database from the given file or creates it, and then ensures that the
     * database has the necessary 2Q2R tables.
     * @param databaseFile The file to create or open the database from.
     */
    public Database(File databaseFile) {

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

        this.listeners = new ArrayList<>();

    }

    /**
     * Sets another listener to be notified whenever a key is added.
     * @param listener The listener to be notified.
     */
    public void addRegistrationListener(KeyRegistrationListener listener) {

        listeners.add(listener);

    }

    /**
     * Returns data for every key stored on the phone in order of the time and date
     * the keys were last used.
     * @return A list of KeyDetails containing information to display in a view.
     */
    public ArrayList<KeyDetails> getDisplayableKeyInformation() {

        ArrayList<KeyDetails> result = new ArrayList<>();

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

        cursor.moveToPosition(-1);

        while (cursor.moveToNext()) {

            KeyDetails kd = new KeyDetails();

            String isoDate = cursor.getString(lastLoginIndex).split(" ")[0];
            String isoTime = cursor.getString(lastLoginIndex).split(" ")[1];
            isoDate = (isoDate.startsWith("0")) ? isoDate.substring(1) : isoDate;
            isoTime = (isoTime.startsWith("0")) ? isoTime.substring(1) : isoTime;

            kd.userID  = cursor.getString(userIDIndex);
            kd.appName = cursor.getString(appNameIndex);
            kd.baseURL = cursor.getString(baseURLIndex);
            kd.date    = isoDate.substring(isoDate.indexOf('/') + 1);
            kd.time    = isoTime;
            kd.counter = cursor.getInt(counterIndex);

            result.add(kd);

        }

        cursor.close();
        return result;

    }

    /**
     * Retrieves the counter for the given registration key.
     * @param keyID The handle of the key in use.
     * @return The counter (pre-increment) for the given key.
     */
    public byte[] getCounter(String keyID) {

        Cursor cursor = database.rawQuery("SELECT counter " +
                                          "FROM keys " +
                                          "WHERE keyID = '" + keyID + "'", null);
        cursor.moveToFirst();

        int counter = cursor.getInt(cursor.getColumnIndex("counter"));
        cursor.close();

        return ByteBuffer.allocate(4).putInt(counter).array();

    }

    /**
     * Increments the counter for a given key. This should be called after
     * a successful authentication request to the target 2Q2R server.
     */
    public void incrementCounter(String keyID) {

        Cursor cursor = database.rawQuery("SELECT counter FROM keys WHERE keyID = '" +
                keyID + "'", null);
        cursor.moveToFirst();

        database.execSQL("UPDATE keys SET counter = " + (cursor.getInt(0) + 1) +
                " WHERE keyID = '" + keyID + "'");

        cursor.close();

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

        Cursor serverInfo = database.rawQuery("SELECT baseURL, appName " +
                                              "FROM servers " +
                                              "WHERE appID = '" + appID + "'", null);
        serverInfo.moveToFirst();

        for (String str: serverInfo.getColumnNames())
            System.out.println(str);

        KeyDetails kd = new KeyDetails();

        String date = dtTm.substring(0, dtTm.indexOf(" "));
        String time = dtTm.substring(dtTm.indexOf(" ") + 1);
        date = (date.startsWith("0")) ? date.substring(1) : date;
        time = (time.startsWith("0")) ? time.substring(1) : time;

        kd.userID  = userID;
        kd.appName = serverInfo.getString(serverInfo.getColumnIndex("appName"));
        kd.baseURL = serverInfo.getString(serverInfo.getColumnIndex("baseURL"));
        kd.counter = 0;
        kd.date    = date;
        kd.time    = time;

        for (int i = 0; i < listeners.size(); i++)
            listeners.get(i).notifyKeysUpdated(kd);

    }

    /**
     * Checks to see if the device already has information cached for the
     * given server.
     * @param appID The server ID to look for.
     * @return True if the server is already known, false otherwise.
     */
    public boolean containsServer(String appID) {

        Cursor cursor = database.rawQuery("SELECT appID FROM servers WHERE appID = '" +
                appID + "'", null);

        boolean result = cursor.moveToFirst();
        cursor.close();

        return result;

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
     * Indicates that a registration operation is faulty because the device is already
     * registered with the given account.
     */
    public static class UserAlreadyRegisteredException extends Exception {}

    /**
     * Notified whenever a new key is saved to the database.
     */
    public interface KeyRegistrationListener {

        /**
         * Called when a new is appended to the SQLite database.
         * @param newKeyDesc A description of the new key added.
         */
        void notifyKeysUpdated(KeyDetails newKeyDesc);

    }

}
