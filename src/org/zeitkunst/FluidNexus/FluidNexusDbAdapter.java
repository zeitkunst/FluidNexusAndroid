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
                    "Mobilize to dine",
                    "CT delegation @ Maison (7th Ave. & 53rd).  Outdoor dining      area.  Try to get people there.",
                    "(123,123,123,123)");
                add_received(0,
                    "Run",
                    "Run against Bush in progress (just went through times sq).     media march starts at 7, 52nd and broadway",
                    "(123,123,123,123)");
                add_received(0,
                    "Federal agents",
                    "Video dispatch. Federal agents trailing activists at 6th Ave   and 9th St. Situation tense.  In sem. Vestibulum condimentum pulvinar quam. Donec dignissim eros non felis condimentum eleifend. Fusce vulputate orci quis diam. Mauris vel risus eget tortor congue elementum. Quisque lectus turpis, sagittis sit amet, auctor ac, euismod sit amet, tellus. Nam hendrerit tristique justo. Cras hendrerit, quam nec ornare dignissim, ipsum dui interdum velit, id condimentum turpis augue quis sem. Vestibulum ac libero at ligula pharetra accumsan. Mauris nulla odio, consequat blandit, blandit id, feugiat non, risus. Morbi purus tortor, pellentesque ut, posuere ut, dapibus gravida, arcu. Curabitur libero odio, semper vitae, imperdiet egestas, auctor non, sem. In hac habitasse platea dictumst.",
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

}
