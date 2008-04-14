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

package org.zeitkunst.FluidNexus;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.security.MessageDigest;
import android.util.HexDump;
import android.os.SystemProperties;
import android.telephony.TelephonyProperties;

import java.io.FileNotFoundException;

public class FluidNexusDbAdapter {
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 
    private String IMSI_HASH;
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
    private static final String DATABASE_CREATE =
        "create table FluidNexusData (_id integer primary key autoincrement, source varchar(32), time bigint, type integer, title varchar(40), data long varchar, hash varchar(32), cellID varchar(20), mine bit);";
    private static final String DATABASE_NAME = "FluidNexusDatabase.db";
    private static final String DATABASE_TABLE = "FluidNexusData";
    private static final int DATABASE_VERSION = 1;
    
    private SQLiteDatabase db;
    private final Context ctx;

    /**
     * Constructor: create our db context
     */
    public FluidNexusDbAdapter(Context context) {
        SystemProperties prop = new SystemProperties();
        IMSI_HASH = makeMD5(prop.get(TelephonyProperties.PROPERTY_IMSI));
        this.ctx = context;
    }

    /**
     * Try to open the fluid nexus database
     */
    public FluidNexusDbAdapter open() throws SQLException {
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

        return this;
    }

    public void close() {
        db.close();
    }

    public static String makeMD5(String inputString) {
        try {
            HexDump dump = new HexDump();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(inputString.getBytes());
            String md5 = dump.dumpHexString(messageDigest);
            md5 = md5.replaceAll(" ", "");
            md5 = md5.substring(11);
            return md5;
        } catch(NoSuchAlgorithmException e) {
            log.error("MD5" + e.getMessage());
            return null;
        }
        /*
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(inputString.getBytes());
        return dump.dumpHexString(md.digest());    
        */
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
        return db.insert(DATABASE_TABLE, null, values);
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
        return db.insert(DATABASE_TABLE, null, values);
    }

    /**
     * Delete an item by the hash
     */
    public boolean deleteByHash(String hash) {
        return db.delete(DATABASE_TABLE, KEY_HASH + "=" + hash, null) > 0;
    }

    /**
     * Delete an item by the id 
     */
    public boolean deleteById(long id) {
        return db.delete(DATABASE_TABLE, KEY_ID + "=" + id, null) > 0;
    }

    /**
     * Return all of the items in the database
     */
    public Cursor all() {
        return db.query(DATABASE_TABLE, 
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
        return db.query(DATABASE_TABLE, 
                new String [] {KEY_ID, KEY_SOURCE, KEY_TIME, KEY_TYPE, KEY_TITLE, KEY_DATA, KEY_HASH, KEY_CELLID, KEY_MINE},
                KEY_MINE + "=1",
                null,
                null,
                null,
                KEY_TIME + " DESC");
    }

    /**
     * Return an item based on a hash
     */
    public Cursor returnItemBasedOnHash(String hash) {
        return db.query(DATABASE_TABLE, 
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
        return db.query(DATABASE_TABLE, 
                new String [] {KEY_ID, KEY_HASH},
                null,
                null,
                null,
                null,
                KEY_TIME + " DESC");
    }


}
