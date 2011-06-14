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

public class FluidNexusDbAdapter {
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
    private String IMSI_HASH;

    private SQLiteDatabase database;
    private FluidNexusDbHelper dbHelper;
    private static final String DATABASE_TABLE = "FluidNexusData";

    /**
     * Keys for the parts of the table
     */
    public static final String KEY_ID = "_id";
    public static final String KEY_SOURCE = "source";
    public static final String KEY_TIME = "time";
    public static final String KEY_TYPE = "type";
    public static final String KEY_TITLE = "title";
    public static final String KEY_DATA = "data";
    public static final String KEY_HASH = "hash";
    public static final String KEY_CELLID = "cellID";
    public static final String KEY_MINE = "mine";

    /**
     * Database creation statement
     */
    
    private SQLiteDatabase db;
    private final Context ctx;

    /**
     * Constructor: create our db context
     */
    public FluidNexusDbAdapter(Context context) {
        /*SystemProperties prop = new SystemProperties();*/
        /*IMSI_HASH = makeMD5(prop.get(TelephonyManager.getSubscriberId()));*/

        TelephonyManager tm = (TelephonyManager) context.getSystemService(context.TELEPHONY_SERVICE);

        IMSI_HASH = makeMD5(tm.getSubscriberId());
        this.ctx = context;
    }

    /**
     * Try to open the fluid nexus database
     */
    public FluidNexusDbAdapter open() throws SQLException {
        dbHelper = new FluidNexusDbHelper(ctx);
        database = dbHelper.getWritableDatabase();
        return this;
        /*
        try {
            // Open the database
            db = ctx.openDatabase(DATABASE_NAME, null);
        } catch (FileNotFoundException e) {
            try {
                // Doesn't exist?  Create it
                log.info("Creating new database");
                db = ctx.createDatabase(DATABASE_NAME, DATABASE_VERSION, 0, null);
                db.execSQL(DATABASE_CREATE); 
                add_received(0,
                   "Schedule a meeting",
                    "We need to schedule a meeting soon.  Send a message around with the title [S] and good times to meet.  (This is an example of using the system to surreptitiously spread information about covert meetings.)",
                    "(123,123,123,123)");
                add_received(0,
                    "Building materials",
                    "Some 2x4's and other sundry items seen around Walker Terrace.  (In the aftermath of a disaster, knowing where there might be temporary sources of material is very important.)",
                    "(123,123,123,123)");
                add_received(0,
                    "Universal Declaration of Human Rights",
                    "All human beings are born free and equal in dignity and rights.They are endowed with reason and conscience and should act towards one another in a spirit of brotherhood.  (In repressive regimes the system could be used to spread texts or other media that would be considered subversive.).  Everyone is entitled to all the rights and freedoms set forth in this Declaration, without distinction of any kind, such as race, colour, sex, language, religion, political or other opinion, national or social origin, property, birth or other status. Furthermore, no distinction shall be made on the basis of the political, jurisdictional or international status of the country or territory to which a person belongs, whether it be independent, trust, non-self-governing or under any other limitation of sovereignty....",
                    "(123,123,123,123)");
                add_new(0,
                    "Witness to the event",
                    "I saw them being taken away in the car--just swooped up like that.  (This is an example of a message we have created that is just marked as 'outgoing'.  The system can be easily used for spreading personal testimonials like this one.)",
                    "(123,123,123,123)");

            } catch (FileNotFoundException e1) {
                throw new SQLException("Could not create database" + e);
            }
        }
        */
    }

    public void close() {
        dbHelper.close();
    }

    public void initialPopulate() {
            add_received(0,
               "Schedule a meeting",
                "We need to schedule a meeting soon.  Send a message around with the title [S] and good times to meet.  (This is an example of using the system to surreptitiously spread information about covert meetings.)",
                "(123,123,123,123)");
            add_received(0,
                "Building materials",
                "Some 2x4's and other sundry items seen around Walker Terrace.  (In the aftermath of a disaster, knowing where there might be temporary sources of material is very important.)",
                "(123,123,123,123)");
            add_received(0,
                "Universal Declaration of Human Rights",
                "All human beings are born free and equal in dignity and rights.They are endowed with reason and conscience and should act towards one another in a spirit of brotherhood.  (In repressive regimes the system could be used to spread texts or other media that would be considered subversive.).  Everyone is entitled to all the rights and freedoms set forth in this Declaration, without distinction of any kind, such as race, colour, sex, language, religion, political or other opinion, national or social origin, property, birth or other status. Furthermore, no distinction shall be made on the basis of the political, jurisdictional or international status of the country or territory to which a person belongs, whether it be independent, trust, non-self-governing or under any other limitation of sovereignty....",
                "(123,123,123,123)");
            add_new(0,
                "Witness to the event",
                "I saw them being taken away in the car--just swooped up like that.  (This is an example of a message we have created that is just marked as 'outgoing'.  The system can be easily used for spreading personal testimonials like this one.)",
                "(123,123,123,123)");

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
     * Take a byte array and turn it into a hex string
     * @param bytes Array of bytes to convert
     * @note Taken from http://stackoverflow.com/questions/332079/in-java-how-do-i-convert-a-byte-array-to-a-string-of-hex-digits-while-keeping-le
     */
    public static String toHexString(byte[] bytes) {
        char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j*2] = hexArray[v/16];
            hexChars[j*2 + 1] = hexArray[v%16];
        }
        return new String(hexChars);
    }

    public long add_new(int type,
            String title,
            String data,
            String cellID) {
        long now = System.currentTimeMillis();        

        ContentValues values = new ContentValues();
        values.put(KEY_SOURCE, IMSI_HASH);
        values.put(KEY_TIME, now);
        values.put(KEY_TYPE, type);
        values.put(KEY_TITLE, title);
        values.put(KEY_DATA, data);
        values.put(KEY_HASH, makeMD5(title + data));
        values.put(KEY_CELLID, cellID);
        values.put(KEY_MINE, 1);
        return database.insert(DATABASE_TABLE, null, values);
    }

    public long add_received(int type,
            String title,
            String data,
            String cellID) {
        long now = System.currentTimeMillis();        

        ContentValues values = new ContentValues();
        values.put(KEY_SOURCE, IMSI_HASH);
        values.put(KEY_TIME, now);
        values.put(KEY_TYPE, type);
        values.put(KEY_TITLE, title);
        values.put(KEY_DATA, data);
        values.put(KEY_HASH, makeMD5(title + data));
        values.put(KEY_CELLID, cellID);
        values.put(KEY_MINE, 0);
        return database.insert(DATABASE_TABLE, null, values);
    }

    /**
     * Delete an item by the hash
     */
    public boolean deleteByHash(String hash) {
        return database.delete(DATABASE_TABLE, KEY_HASH + "=" + hash, null) > 0;
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
                new String [] {KEY_ID, KEY_SOURCE, KEY_TIME, KEY_TYPE, KEY_TITLE, KEY_DATA, KEY_HASH, KEY_CELLID, KEY_MINE},
                null,
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
                new String [] {KEY_ID, KEY_SOURCE, KEY_TIME, KEY_TYPE, KEY_TITLE, KEY_DATA, KEY_HASH, KEY_CELLID, KEY_MINE},
                KEY_MINE + "=1",
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
                new String [] {KEY_ID, KEY_SOURCE, KEY_TIME, KEY_TYPE, KEY_TITLE, KEY_DATA, KEY_HASH, KEY_CELLID, KEY_MINE},
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
        return database.query(DATABASE_TABLE, 
                new String [] {KEY_ID, KEY_SOURCE, KEY_TIME, KEY_TYPE, KEY_TITLE, KEY_DATA, KEY_HASH, KEY_CELLID, KEY_MINE},
                KEY_HASH + "='" + hash + "'",
                null,
                null,
                null,
                KEY_TIME + " DESC");
    }


    /**
     * Return id and hash for messages
     */
    public Cursor services() {
        return database.query(DATABASE_TABLE, 
                new String [] {KEY_ID, KEY_HASH},
                null,
                null,
                null,
                null,
                KEY_TIME + " DESC");
    }


}
