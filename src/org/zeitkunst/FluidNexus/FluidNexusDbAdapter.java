package org.zeitkunst.FluidNexus;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.io.FileNotFoundException;

public class FluidNexusDbAdapter {
    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

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
                add_new("00:02:EE:6B:86:09",
                    0,
                    "Mobilize to dine",
                    "CT delegation @ Maison (7th Ave. & 53rd).  Outdoor dining      area.  Try to get people there.",
                    "asdfasdfasdf",
                    "(123,123,123,123)");
                add_new("00:02:EE:6B:86:09",
                    0,
                    "Run",
                    "Run against Bush in progress (just went through times sq).     media march starts at 7, 52nd and broadway",
                    "asdfasdfasdf",
                    "(123,123,123,123)");
                add_new("00:02:EE:6B:86:09",
                    0,
                    "Federal agents",
                    "Video dispatch. Federal agents trailing activists at 6th Ave   and 9th St. Situation tense.",
                    "asdfasdfasdf",
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

    public long add_new(String source,
            int type,
            String title,
            String data,
            String hash,
            String cellID) {
        long now = System.currentTimeMillis();        

        ContentValues values = new ContentValues();
        values.put(KEY_SOURCE, source);
        values.put(KEY_TIME, now);
        values.put(KEY_TYPE, type);
        values.put(KEY_TITLE, title);
        values.put(KEY_DATA, data);
        values.put(KEY_HASH, hash);
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
}
