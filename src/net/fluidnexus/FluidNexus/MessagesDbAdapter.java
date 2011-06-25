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

package net.fluidnexus.FluidNexus;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
/*import android.security.MessageDigest;*/
/*import android.util.HexDump;*/
/*import android.os.SystemProperties;*/
import android.telephony.TelephonyManager;

import java.io.FileNotFoundException;

public class MessagesDbAdapter {
    private static Logger log = Logger.getLogger("FluidNexus"); 
    private String IMSI_HASH;

    private SQLiteDatabase database;
    private MessagesDbHelper dbHelper;
    private static final String DATABASE_TABLE = "Messages";

    /**
     * Keys for the parts of the table
     */

        //"create table FluidNexusData (_id integer primary key autoincrement, type integer, title text, content text, message_hash text, time float, attachment_path text, attachment_original_filename text, mine bit);";
    public static final String KEY_ID = "_id";
    public static final String KEY_TYPE = "type";
    public static final String KEY_TITLE = "title";
    public static final String KEY_CONTENT = "content";
    public static final String KEY_MESSAGE_HASH= "message_hash";
    public static final String KEY_TIME = "time";
    public static final String KEY_ATTACHMENT_PATH = "attachment_path";
    public static final String KEY_ATTACHMENT_ORIGINAL_FILENAME = "attachment_original_filename";
    public static final String KEY_MINE = "mine";
    public static final String KEY_BLACKLIST = "blacklist";

    /**
     * Database creation statement
     */
    
    private SQLiteDatabase db;
    private final Context ctx;

    /**
     * Constructor: create our db context
     */
    public MessagesDbAdapter(Context context) {
        /*SystemProperties prop = new SystemProperties();*/
        /*IMSI_HASH = makeMD5(prop.get(TelephonyManager.getSubscriberId()));*/

        TelephonyManager tm = (TelephonyManager) context.getSystemService(context.TELEPHONY_SERVICE);

        IMSI_HASH = makeSHA256(tm.getSubscriberId());
        this.ctx = context;
    }

    /**
     * Try to open the fluid nexus database
     */
    public MessagesDbAdapter open() throws SQLException {
        dbHelper = new MessagesDbHelper(ctx);
        database = dbHelper.getWritableDatabase();
        return this;
    }

    public void close() {
        dbHelper.close();
    }

    public void initialPopulate() {
            float now = (float) (System.currentTimeMillis()/1000);        
            add_received(0, now,
               "Schedule a meeting",
                "We need to schedule a meeting soon.  Send a message around with the title [S] and good times to meet.  (This is an example of using the system to surreptitiously spread information about covert meetings.)");
            add_received(0, now,
                "Building materials",
                "Some 2x4's and other sundry items seen around Walker Terrace.  (In the aftermath of a disaster, knowing where there might be temporary sources of material is very important.)");
            add_received(0, now,
                "Universal Declaration of Human Rights",
                "All human beings are born free and equal in dignity and rights.They are endowed with reason and conscience and should act towards one another in a spirit of brotherhood.  (In repressive regimes the system could be used to spread texts or other media that would be considered subversive.).  Everyone is entitled to all the rights and freedoms set forth in this Declaration, without distinction of any kind, such as race, colour, sex, language, religion, political or other opinion, national or social origin, property, birth or other status. Furthermore, no distinction shall be made on the basis of the political, jurisdictional or international status of the country or territory to which a person belongs, whether it be independent, trust, non-self-governing or under any other limitation of sovereignty....");
            add_new(0,
                "Witness to the event",
                "I saw them being taken away in the car--just swooped up like that.  (This is an example of a message we have created that is just marked as 'outgoing'.  The system can be easily used for spreading personal testimonials like this one.)");

    }

    /**
     * Make a MD5 hash of the input string
     * @param inputString Input string to create an MD5 hash of
     */
    public static String makeMD5(String inputString) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(inputString.getBytes());

            String md5 = toHexString(messageDigest);
            return md5;
        } catch(NoSuchAlgorithmException e) {
            log.error("MD5" + e.getMessage());
            return null;
        }
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

    /**
     * Add a new message that doesn't have an attachment.
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     */
    public long add_new(int type,
            String title,
            String content) {
        float now = (float) (System.currentTimeMillis()/1000);

        ContentValues values = new ContentValues();
        values.put(KEY_TYPE, type);
        values.put(KEY_TITLE, title);
        values.put(KEY_CONTENT, content);
        values.put(KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(KEY_TIME, now);
        values.put(KEY_ATTACHMENT_PATH, "");
        values.put(KEY_ATTACHMENT_ORIGINAL_FILENAME, "");
        values.put(KEY_MINE, 1);
        values.put(KEY_BLACKLIST, 0);
        return database.insert(DATABASE_TABLE, null, values);
    }

    /**
     * Add a new message that has an attachment.
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     * @param attachment_path Path to the (local) attachment
     * @param attachment_original_filename Original filename of attachment
     */
    public long add_new(int type,
            String title,
            String content, String attachment_path, String attachment_original_filename) {
        float now = (float) (System.currentTimeMillis()/1000);

        ContentValues values = new ContentValues();
        values.put(KEY_TYPE, type);
        values.put(KEY_TITLE, title);
        values.put(KEY_CONTENT, content);
        values.put(KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(KEY_TIME, now);
        values.put(KEY_ATTACHMENT_PATH, attachment_path);
        values.put(KEY_ATTACHMENT_ORIGINAL_FILENAME, attachment_original_filename);
        values.put(KEY_MINE, 1);
        values.put(KEY_BLACKLIST, 0);
        return database.insert(DATABASE_TABLE, null, values);
    }

    /**
     * Add a received message that doesn't have an attachment.
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     */
    public long add_received(int type, float timestamp,
            String title,
            String content) {

        ContentValues values = new ContentValues();
        values.put(KEY_TYPE, type);
        values.put(KEY_TITLE, title);
        values.put(KEY_CONTENT, content);
        values.put(KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(KEY_TIME, timestamp);
        values.put(KEY_ATTACHMENT_PATH, "");
        values.put(KEY_ATTACHMENT_ORIGINAL_FILENAME, "");
        values.put(KEY_MINE, 0);
        values.put(KEY_BLACKLIST, 0);
        return database.insert(DATABASE_TABLE, null, values);
    }

    /**
     * Add a received message that has an attachment
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     * @param attachment_path Path to the (local) attachment
     * @param attachment_original_filename Original filename of attachment

     */
    public long add_received(int type, float timestamp,
            String title,
            String content, String attachment_path, String attachment_original_filename) {

        ContentValues values = new ContentValues();
        values.put(KEY_TYPE, type);
        values.put(KEY_TITLE, title);
        values.put(KEY_CONTENT, content);
        values.put(KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(KEY_TIME, timestamp);
        values.put(KEY_ATTACHMENT_PATH, attachment_path);
        values.put(KEY_ATTACHMENT_ORIGINAL_FILENAME, attachment_original_filename);
        values.put(KEY_MINE, 0);
        values.put(KEY_BLACKLIST, 0);
        return database.insert(DATABASE_TABLE, null, values);
    }


    /**
     * Delete an item by the hash
     */
    public boolean deleteByHash(String hash) {
        return database.delete(DATABASE_TABLE, KEY_MESSAGE_HASH + "=" + hash, null) > 0;
    }

    /**
     * Delete an item by the id 
     */
    public boolean deleteById(long id) {
        return database.delete(DATABASE_TABLE, KEY_ID + "=" + id, null) > 0;
    }

    /**
     * Return all of the items in the database
     */
    public Cursor all() {

        return database.query(DATABASE_TABLE, 
                new String [] {KEY_ID, KEY_TYPE, KEY_TITLE, KEY_CONTENT, KEY_MESSAGE_HASH, KEY_TIME, KEY_ATTACHMENT_PATH, KEY_ATTACHMENT_ORIGINAL_FILENAME, KEY_MINE, KEY_BLACKLIST},
                null,
                null,
                null,
                null,
                KEY_TIME + " DESC");
    }

    /**
     * Return all of the items in the database, with optional blacklist parameter
     */
    public Cursor allNoBlacklist() {

        return database.query(DATABASE_TABLE, 
                new String [] {KEY_ID, KEY_TYPE, KEY_TITLE, KEY_CONTENT, KEY_MESSAGE_HASH, KEY_TIME, KEY_ATTACHMENT_PATH, KEY_ATTACHMENT_ORIGINAL_FILENAME, KEY_MINE, KEY_BLACKLIST},
                KEY_BLACKLIST + "=0",
                null,
                null,
                null,
                KEY_TIME + " DESC");
    }


    /**
     * Return outgoing items in the database
     */
    public Cursor outgoing() {
        return database.query(DATABASE_TABLE, 
                new String [] {KEY_ID, KEY_TYPE, KEY_TITLE, KEY_CONTENT, KEY_MESSAGE_HASH, KEY_TIME, KEY_ATTACHMENT_PATH, KEY_ATTACHMENT_ORIGINAL_FILENAME, KEY_MINE, KEY_BLACKLIST},
                KEY_MINE + "=1",
                null,
                null,
                null,
                KEY_TIME + " DESC");
    }

    /**
     * Return blacklist items in the database
     */
    public Cursor blacklist() {
        return database.query(DATABASE_TABLE, 
                new String [] {KEY_ID, KEY_TYPE, KEY_TITLE, KEY_CONTENT, KEY_MESSAGE_HASH, KEY_TIME, KEY_ATTACHMENT_PATH, KEY_ATTACHMENT_ORIGINAL_FILENAME, KEY_MINE, KEY_BLACKLIST},
                KEY_BLACKLIST + "=1",
                null,
                null,
                null,
                KEY_TIME + " DESC");
    }


    /**
     * Return item based on its ID
     * @param id ID of the desired item
     */
    public Cursor returnItemByID(long id) {
        Cursor c = database.query(DATABASE_TABLE, 
                new String [] {KEY_ID, KEY_TYPE, KEY_TITLE, KEY_CONTENT, KEY_MESSAGE_HASH, KEY_TIME, KEY_ATTACHMENT_PATH, KEY_ATTACHMENT_ORIGINAL_FILENAME, KEY_MINE, KEY_BLACKLIST},
                KEY_ID + "=" + id,
                null,
                null,
                null,
                KEY_TIME + " DESC");
        c.moveToFirst();
        return c;
    }


    /**
     * Return an item based on a hash
     * @param hash Hash to search for
     */
    public Cursor returnItemBasedOnHash(String hash) {
        Cursor c = database.query(DATABASE_TABLE, 
                new String [] {KEY_ID, KEY_TYPE, KEY_TITLE, KEY_CONTENT, KEY_MESSAGE_HASH, KEY_TIME, KEY_ATTACHMENT_PATH, KEY_ATTACHMENT_ORIGINAL_FILENAME, KEY_MINE, KEY_BLACKLIST},
                KEY_MESSAGE_HASH + "='" + hash + "'",
                null,
                null,
                null,
                KEY_TIME + " DESC");

        c.moveToFirst();
        return c;
    }

    /**
     * Update an item based on an ID
     * @param id ID of entry to update
     * @param cv ContentValues to update for the given ID
     */
    public int updateItemByID(long id, ContentValues cv) {
        return database.update(DATABASE_TABLE, cv, KEY_ID + "=" + id, null);
    }

    /**
     * Return id and hash for messages
     */
    public Cursor services() {
        return database.query(DATABASE_TABLE, 
                new String [] {KEY_ID, KEY_MESSAGE_HASH},
                null,
                null,
                null,
                null,
                KEY_TIME + " DESC");
    }


}
