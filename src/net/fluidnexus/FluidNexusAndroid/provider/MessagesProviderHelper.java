/*
 *  This file is part of Fluid Nexus.
 *
 *  Fluid Nexus is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Fluid Nexus is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Fluid Nexus.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package net.fluidnexus.FluidNexusAndroid.provider;

import java.lang.StringBuilder;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;

import info.guardianproject.database.sqlcipher.SQLiteDatabase;
import info.guardianproject.database.sqlcipher.SQLiteOpenHelper;
import info.guardianproject.database.sqlcipher.SQLiteQueryBuilder;

import net.fluidnexus.FluidNexusAndroid.Logger;

public class MessagesProviderHelper {

    private static Logger log = Logger.getLogger("FluidNexus"); 
    private final Context context;

    private static MessagesProviderHelper instance;
    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;

    /**
     * Keys for the database
     */
    public static final String KEY_ID = "_id";
    public static final String KEY_TYPE = "type";
    public static final String KEY_TITLE = "title";
    public static final String KEY_CONTENT = "content";
    public static final String KEY_MESSAGE_HASH= "message_hash";
    public static final String KEY_TIME = "time";
    public static final String KEY_RECEIVED_TIME = "received_time";
    public static final String KEY_ATTACHMENT_PATH = "attachment_path";
    public static final String KEY_ATTACHMENT_ORIGINAL_FILENAME = "attachment_original_filename";
    public static final String KEY_MINE = "mine";
    public static final String KEY_BLACKLIST = "blacklist";
    public static final String KEY_PUBLIC = "public";
    public static final String KEY_TTL = "ttl";
    public static final String KEY_UPLOADED = "uploaded";
    public static final String KEY_PRIORITY = "priority";
    public static final String[] ALL_PROJECTION = new String[] {KEY_ID, KEY_TYPE, KEY_TITLE, KEY_CONTENT, KEY_MESSAGE_HASH, KEY_TIME, KEY_RECEIVED_TIME, KEY_ATTACHMENT_PATH, KEY_ATTACHMENT_ORIGINAL_FILENAME, KEY_MINE, KEY_BLACKLIST, KEY_PUBLIC, KEY_TTL, KEY_UPLOADED, KEY_PRIORITY};
    public static final String[] HASHES_PROJECTION = new String[] {KEY_ID, KEY_MESSAGE_HASH};

    private static final String DATABASE_CREATE =
            "create table messages (_id integer primary key autoincrement, type integer, title text, content text, message_hash text, time float, received_time float, attachment_path text, attachment_original_filename text, mine bit, blacklist bit default 0, public bit default 0, ttl integer default 0, uploaded bit default 0, priority integer default 0);";
    private static final String DATABASE_NAME = "FluidNexusDatabase.db";
    private static final String DATABASE_TABLE = "messages";
    private static final int DATABASE_VERSION = 2;


    /**
     * Priority constants
     */
    public static final int NORMAL_PRIORITY = 0;
    public static final int HIGH_PRIORITY = 1;


    /**
     * Database helper class for creating and upgrading the database
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static Logger log = Logger.getLogger("FluidNexus"); 
    
    
        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
    
        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL(DATABASE_CREATE);
        }
    
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // This method of upgrading (and associated helper methods) is heavily indebted to http://stackoverflow.com/questions/3424156/upgrade-sqlite-database-from-one-version-to-another

            log.info("Upgrading database...");
            String dbCreateIfNotExists = 
            "create table if not exists messages (_id integer primary key autoincrement, type integer, title text, content text, message_hash text, time float, received_time float, attachment_path text, attachment_original_filename text, mine bit, blacklist bit default 0, public bit default 0, ttl integer default 0, uploaded bit default 0, priority integer default 0);";
            String tableName = "messages";

            // Start our upgrade transaction
            db.beginTransaction();
            db.execSQL(dbCreateIfNotExists);

            // Get list of columns in old table
            List<String> columns = getColumns(db, tableName);

            // Backup original table
            String alterQuery = "alter table " + tableName + " rename to 'temp_" + tableName + "'";
            db.execSQL(alterQuery);

            // Recreate table with new schema
            db.execSQL(dbCreateIfNotExists);

            // Get intersection with new columns taken from upgraded table
            columns.retainAll(getColumns(db, tableName));

            // Restore the data
            String cols = join(columns, ",");
            db.execSQL(String.format("insert into %s (%s) select %s from temp_%s", tableName, cols, cols, tableName));

            // Remove backup table
            db.execSQL("drop table 'temp_" + tableName + "'");

            // Finish transaction
            db.setTransactionSuccessful();
            db.endTransaction();
        }

        /**
         * Get a list of columns in the database
         */
        List<String> getColumns(SQLiteDatabase db, String tableName) {
            List<String> ar = null;
            Cursor c = null;

            try {
                c = db.rawQuery("select * from " + tableName + " limit 1", null);
                if (c != null) {
                    ar = new ArrayList<String>(Arrays.asList(c.getColumnNames()));
                }
            } catch (Exception e) {
                log.error("Error getting database columns: " + e.getMessage(), e);
                e.printStackTrace();
            } finally {
                if (c != null) {
                    c.close();
                }

            }

            return ar;
        }

        /**
         * Join an arraylist into a string
         */
        public static String join(List<String> list, String delim) {
            StringBuilder buf = new StringBuilder();
            int num = list.size();
            for (int i = 0; i < num; i++) {
                if (i != 0) {
                    buf.append(delim);
                }
                buf.append((String) list.get(i));
            }
            return buf.toString();
        }
    
    }

    private MessagesProviderHelper(Context applicationContext) {
        this.context = applicationContext;
    }

    /**
     * Return a singleton instance
     *
     * @return instance of MessagesProviderHelper
     */
    public static synchronized MessagesProviderHelper getInstance(Context ctx) {
        if (instance == null) {
            instance = new MessagesProviderHelper(ctx);
        }

        return instance;
    }

    /**
     * Open the messages database.  Try and create a new one if it doesn't exist; otherwise, throw an exception.
     * @param password Password for the encrypted database
     * @return this reference to an instance of this class
     * @throws SQLException if the database couldn't be opened nor created
     */
    public MessagesProviderHelper open(String password) throws SQLException {
        mDbHelper = new DatabaseHelper(context);
        mDb = mDbHelper.getWritableDatabase(password);
        System.gc();
        return this;
    }

    /**
     * Checks if the database is open
     */
    public boolean isOpen() {
        if (mDb != null) {
            return mDb.isOpen();
        } else {
            return false;
        }
    }

    /**
     * Rekey the database.  Force garbage collection to try and flush password from memory as quickly as possible.
     */
    public void rekey(String password) {
        mDb.execSQL("PRAGMA rekey = '" + password + "'");
        System.gc();
    }

    /**
     * Close the database
     */
    public void close() {
        mDbHelper.close();
    }

    public void initialPopulate() {
            float now = (float) (System.currentTimeMillis()/1000);        
            add_new(0,
                "Witness to the event",
                "[SAMPLE MESSAGE] I saw them being taken away in the car--just swooped up like that.  (This is an example of a message we have created that is just marked as 'outgoing'.  The system can be easily used for spreading personal testimonials like this one.)", false, 0, 0);
            add_received(0, now, now,
               "Schedule a meeting",
                "[SAMPLE MESSAGE] We need to schedule a meeting soon.  Send a message around with the title [S] and good times to meet.  (This is an example of using the system to surreptitiously spread information about covert meetings.)", false, 0, 0);
            add_received(0, now, now,
                "Building materials",
                "[SAMPLE MESSAGE] Some 2x4's and other sundry items seen around Walker Terrace.  (In the aftermath of a disaster, knowing where there might be temporary sources of material is very important.)", false, 0, 0);
            add_received(0, now, now,
                "Universal Declaration of Human Rights",
                "[SAMPLE MESSAGE] All human beings are born free and equal in dignity and rights.They are endowed with reason and conscience and should act towards one another in a spirit of brotherhood.  (In repressive regimes the system could be used to spread texts or other media that would be considered subversive.).  Everyone is entitled to all the rights and freedoms set forth in this Declaration, without distinction of any kind, such as race, colour, sex, language, religion, political or other opinion, national or social origin, property, birth or other status. Furthermore, no distinction shall be made on the basis of the political, jurisdictional or international status of the country or territory to which a person belongs, whether it be independent, trust, non-self-governing or under any other limitation of sovereignty....", false, 0, 0);

    }

    /**
     * Get all our messages
     */
    public Cursor all() {

        //Cursor c = cr.query(ALL_URI, ALL_PROJECTION, null, null, null);
        Cursor c = mDb.query(DATABASE_TABLE, ALL_PROJECTION, null, null, null, null, "received_time DESC");
        c.moveToFirst();
        return c;
    }

    /**
     * Get all our messages minuse those blacklisted
     */
    public Cursor allNoBlacklist() {
        //Cursor c = cr.query(ALL_NOBLACKLIST_URI, ALL_PROJECTION, null, null, null);
        Cursor c = mDb.query(DATABASE_TABLE, ALL_PROJECTION, KEY_BLACKLIST + " = 0", null, null, null, "received_time DESC");
        c.moveToFirst();
        return c;
    }

    /**
     * Get our public messages
     */
    public Cursor publicMessages() {
        //Cursor c = cr.query(PUBLIC_URI, ALL_PROJECTION, null, null, null);
        Cursor c = mDb.query(DATABASE_TABLE, ALL_PROJECTION, KEY_PUBLIC + " = 1", null, null, null, "received_time DESC");
        c.moveToFirst();
        return c;
    }


    /**
     * Get our outgoing messages
     */
    public Cursor outgoing() {
        //Cursor c = cr.query(OUTGOING_URI, ALL_PROJECTION, null, null, null);
        Cursor c = mDb.query(DATABASE_TABLE, ALL_PROJECTION, KEY_MINE + " = 1", null, null, null, "received_time DESC");
        c.moveToFirst();
        return c;
    }

    /**
     * Get our blacklsited messages
     */
    public Cursor blacklist() {
        //Cursor c = cr.query(BLACKLIST_URI, ALL_PROJECTION, null, null, null);
        Cursor c = mDb.query(DATABASE_TABLE, ALL_PROJECTION, KEY_BLACKLIST + " = 1", null, null, null, "received_time DESC");
        c.moveToFirst();
        return c;
    }

    /**
     * Get our high priority messages
     */
    public Cursor highPriority() {
        //Cursor c = cr.query(HIGH_PRIORITY_URI, ALL_PROJECTION, null, null, null);
        Cursor c = mDb.query(DATABASE_TABLE, ALL_PROJECTION, KEY_PRIORITY+ " = 1", null, null, null, "received_time DESC");
        c.moveToFirst();
        return c;
    }

    /**
     * Get our hashes 
     */
    public Cursor hashes() {

        //Cursor c = cr.query(ALL_URI, HASHES_PROJECTION, null, null, null);
        Cursor c = mDb.query(DATABASE_TABLE, HASHES_PROJECTION, null, null, null, null, "received_time DESC");
        c.moveToFirst();
        return c;

    }

    /**
     * Get our non-blacklist hashes 
     */
    public Cursor hashesNoBlacklist() {
        //Cursor c = cr.query(ALL_NOBLACKLIST_URI, HASHES_PROJECTION, null, null, null);
        Cursor c = mDb.query(DATABASE_TABLE, HASHES_PROJECTION, KEY_BLACKLIST + " = 0", null, null, null, "received_time DESC");
        c.moveToFirst();
        return c;

    }


    /**
     * Add a new message that doesn't have an attachment.
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     * @param publicMessage Whether or not the message is public (to be posted to the Nexus)
     * @param ttl TTL of the public message (defualt 0)
     */
    public long add_new(int type, String title, String content, boolean publicMessage, int ttl, int priority) {

        float now = (float) (System.currentTimeMillis()/1000);
        ContentValues values = new ContentValues();
        values.put(KEY_TYPE, 0);
        values.put(KEY_TITLE, title);
        values.put(KEY_CONTENT, content);
        values.put(KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(KEY_TIME, now);
        values.put(KEY_RECEIVED_TIME, now);
        values.put(KEY_ATTACHMENT_PATH, "");
        values.put(KEY_ATTACHMENT_ORIGINAL_FILENAME, "");
        values.put(KEY_MINE, 1);
        values.put(KEY_BLACKLIST, 0);
        values.put(KEY_PUBLIC, publicMessage);
        values.put(KEY_TTL, ttl);
        values.put(KEY_PRIORITY, priority);

        //return cr.insert(MESSAGES_URI, values);
        return mDb.insert(DATABASE_TABLE, null, values);
    }


    /**
     * Add a new message that has an attachment.
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     * @param attachment_path Path to the (local) attachment
     * @param attachment_original_filename Original filename of attachment
     * @param publicMessage Whether or not the message is public (to be posted to the Nexus)
     * @param ttl TTL of the public message (defualt 0)
     * @param priority Priority of the message (default 0)

     */
    public long add_new(int type,
            String title,
            String content, String attachment_path, String attachment_original_filename, boolean publicMessage, int ttl, int priority) {

        float now = (float) (System.currentTimeMillis()/1000);

        ContentValues values = new ContentValues();
        values.put(KEY_TYPE, type);
        values.put(KEY_TITLE, title);
        values.put(KEY_CONTENT, content);
        values.put(KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(KEY_TIME, now);
        values.put(KEY_RECEIVED_TIME, now);
        values.put(KEY_ATTACHMENT_PATH, attachment_path);
        values.put(KEY_ATTACHMENT_ORIGINAL_FILENAME, attachment_original_filename);
        values.put(KEY_MINE, 1);
        values.put(KEY_BLACKLIST, 0);
        values.put(KEY_PUBLIC, publicMessage);
        values.put(KEY_TTL, ttl);
        values.put(KEY_PRIORITY, priority);

        //return cr.insert(MESSAGES_URI, values);
        return mDb.insert(DATABASE_TABLE, null, values);
    }

    /**
     * Add a received message that doesn't have an attachment.
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     * @param publicMessage Whether or not the message is public (to be posted to the Nexus)
     * @param ttl TTL of the public message (defualt 0)
    */
    public long add_received(int type, float now, float received_time, String title, String content, boolean publicMessage, int ttl, int priority) {

        ContentValues values = new ContentValues();
        values.put(KEY_TYPE, 0);
        values.put(KEY_TITLE, title);
        values.put(KEY_CONTENT, content);
        values.put(KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(KEY_TIME, now);
        values.put(KEY_RECEIVED_TIME, received_time);
        values.put(KEY_ATTACHMENT_PATH, "");
        values.put(KEY_ATTACHMENT_ORIGINAL_FILENAME, "");
        values.put(KEY_MINE, 0);
        values.put(KEY_BLACKLIST, 0);
        values.put(KEY_PUBLIC, publicMessage);
        values.put(KEY_TTL, ttl);
        values.put(KEY_PRIORITY, priority);

        //return cr.insert(MESSAGES_URI, values);
        return mDb.insert(DATABASE_TABLE, null, values);

    }

    /**
     * Add a received message that has an attachment
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     * @param attachment_path Path to the (local) attachment
     * @param attachment_original_filename Original filename of attachment
     * @param publicMessage Whether or not the message is public (to be posted to the Nexus)
     * @param ttl TTL of the public message (defualt 0)
     */
    public long add_received(int type, float timestamp, float received_timestamp,
            String title,
            String content, String attachment_path, String attachment_original_filename, boolean publicMessage, int ttl, int priority) {
    
        ContentValues values = new ContentValues();
        values.put(KEY_TYPE, type);
        values.put(KEY_TITLE, title);
        values.put(KEY_CONTENT, content);
        values.put(KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(KEY_TIME, timestamp);
        values.put(KEY_RECEIVED_TIME, received_timestamp);
        values.put(KEY_ATTACHMENT_PATH, attachment_path);
        values.put(KEY_ATTACHMENT_ORIGINAL_FILENAME, attachment_original_filename);
        values.put(KEY_MINE, 0);
        values.put(KEY_BLACKLIST, 0);
        values.put(KEY_PUBLIC, publicMessage);
        values.put(KEY_TTL, ttl);
        values.put(KEY_PRIORITY, priority);

        //return cr.insert(MESSAGES_URI, values);
        return mDb.insert(DATABASE_TABLE, null, values);
    }

    /**
     * Return item based on its ID
     * @param id ID of the desired item
     */
    public Cursor returnItemByID(long id) throws SQLException {
        //Uri uri = ContentUris.withAppendedId(MESSAGES_URI_ID_BASE, id);
        //Cursor c = cr.query(uri, ALL_PROJECTION, null, null, null);
        Cursor c = mDb.query(true, DATABASE_TABLE, ALL_PROJECTION, KEY_ID + " = " + id, null, null, null, null, null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

   
    /**
     * Return an item based on a hash
     * @param hash Hash to search for
     */
    public Cursor returnItemBasedOnHash(String hash) throws SQLException {
        //Uri newUri = HASHES_URI_STRING_BASE.withAppendedPath(HASHES_URI_STRING_BASE, hash);
        //Cursor c = cr.query(newUri, ALL_PROJECTION, null, null, null);
        Cursor c = mDb.query(true, DATABASE_TABLE, ALL_PROJECTION, KEY_MESSAGE_HASH + " = '" + hash + "'", null, null, null, null, null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

    /**
     * Delete an item by the id 
     */
    public int deleteById(long id) {
        //Uri uri = ContentUris.withAppendedId(MESSAGES_URI_ID_BASE, id);
        return mDb.delete(DATABASE_TABLE, KEY_ID + " = " + id, null);
    }

    /**
     * Update an item by the ID
     */
    public int updateItemByID(long id, ContentValues cv) {
        //Uri uri = ContentUris.withAppendedId(MESSAGES_URI_ID_BASE, id);

        Cursor c = returnItemBasedOnHash(cv.getAsString(KEY_MESSAGE_HASH));
        if (c.getCount() > 0) {
            c.close();
            return 0;
        }
        c.close();

        return mDb.update(DATABASE_TABLE, cv, KEY_ID + " = " + id, null);

    }

    /**
     * Mark an item as public
     */
    public int setPublic(long id, ContentValues cv) {
        //Uri uri = ContentUris.withAppendedId(MESSAGES_URI_ID_BASE, id);

        Cursor c = returnItemBasedOnHash(cv.getAsString(KEY_MESSAGE_HASH));

        // Check to see if the message actually exists
        if (c.getCount() == 0) {
            c.close();
            return 0;
        }
        c.close();

        // Otherwise, update
        return mDb.update(DATABASE_TABLE, cv, KEY_ID + " = " + id, null);

    }


    /**
     * Make a SHA-256 hash of the input string
     * @param inputString Input string to create an MD5 hash of
     */
    public static String makeSHA256(String inputString) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] messageDigest = md.digest(inputString.getBytes());

            String sha256 = toHexString(messageDigest);
            return sha256;
        } catch(NoSuchAlgorithmException e) {
            log.error("SHA-256" + e.getMessage());
            return null;
        }
    }

    /**
     * Take a byte array and turn it into a hex string
     * @param bytes Array of bytes to convert
     * @note Taken from http://stackoverflow.com/questions/332079/in-java-how-do-i-convert-a-byte-array-to-a-string-of-hex-digits-while-keeping-le
     */
    public static String toHexString(byte[] bytes) {
        char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j*2] = hexArray[v/16];
            hexChars[j*2 + 1] = hexArray[v%16];
        }
        return new String(hexChars);
    }
}
