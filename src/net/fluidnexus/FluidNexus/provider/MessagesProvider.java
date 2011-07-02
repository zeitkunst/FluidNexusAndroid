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

package net.fluidnexus.FluidNexus.provider;

import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
//import info.guardianproject.database.sqlcipher.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
//import info.guardianproject.database.sqlcipher.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
//import info.guardianproject.database.sqlcipher.SQLiteQueryBuilder;
import android.net.Uri;

import net.fluidnexus.FluidNexus.Logger;

public class MessagesProvider extends ContentProvider {
    private static Logger log = Logger.getLogger("FluidNexus"); 
    private static final String TAG = "FluidNexusMessagesContentProvider";

    // Database constants
    private static final String DATABASE_NAME = "FluidNexusDatabase.db";
    private static final String DATABASE_TABLE = "Messages";
    private static final int DATABASE_VERSION = 1;

    private static final String AUTHORITY = "net.fluidnexus.FluidNexus.provider.MessagesProvider";

    // URIs for our provider
    public static final String SCHEME = "content://";
    public static final String PATH_ALL = "/all";
    public static final String PATH_ALL_NOBLACKLIST = "/allNoBlacklist";
    public static final String PATH_OUTGOING = "/outgoing";
    public static final String PATH_BLACKLIST = "/blacklist";
    public static final String PATH_MESSAGES = "/messages";
    public static final String PATH_MESSAGES_ID = "/messages/";
    public static final String PATH_HASHES_STRING = "/hashes/";
    public static final Uri ALL_URI = Uri.parse(SCHEME + AUTHORITY + PATH_ALL);
    public static final Uri ALL_NOBLACKLIST_URI = Uri.parse(SCHEME + AUTHORITY + PATH_ALL_NOBLACKLIST);
    public static final Uri OUTGOING_URI = Uri.parse(SCHEME + AUTHORITY + PATH_OUTGOING);
    public static final Uri BLACKLIST_URI = Uri.parse(SCHEME + AUTHORITY + PATH_BLACKLIST);
    public static final Uri MESSAGES_URI = Uri.parse(SCHEME + AUTHORITY + PATH_MESSAGES);
    public static final Uri MESSAGES_URI_ID_BASE = Uri.parse(SCHEME + AUTHORITY + PATH_MESSAGES_ID);
    public static final Uri MESSAGES_URI_ID_PATTERN = Uri.parse(SCHEME + AUTHORITY + PATH_MESSAGES_ID + "/#");

    public static final Uri HASHES_URI_STRING_BASE = Uri.parse(SCHEME + AUTHORITY + PATH_HASHES_STRING);
    public static final Uri HASHES_URI_STRING_PATTERN = Uri.parse(SCHEME + AUTHORITY + PATH_HASHES_STRING + "/*");


    // our content type
    public static final String CONTENT_TYPE = "vnd.fluidnexus/vnd.fluidnexus.message";

    /**
     * Constants for the Uri matcher
     */
    public static final int MESSAGES_ID_PATH_POSITION = 1;
    public static final int HASHES_STRING_PATH_POSITION = 1;
    private static final int ALL = 1;
    private static final int ALL_NOBLACKLIST = 2;
    private static final int OUTGOING = 3;
    private static final int BLACKLIST = 4;
    private static final int MESSAGES = 5;
    private static final int MESSAGES_ID = 6;
    private static final int HASHES = 7;
    private static final int HASHES_STRING = 8;

    /**
     * Keys for the database
     */
    public static final String _ID = "_id";
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
     * All keys for querying
     */

    public static final String[] ALL_PROJECTION = new String[] {_ID, KEY_TYPE, KEY_TITLE, KEY_CONTENT, KEY_MESSAGE_HASH, KEY_TIME, KEY_ATTACHMENT_PATH, KEY_ATTACHMENT_ORIGINAL_FILENAME, KEY_MINE, KEY_BLACKLIST};

    /**
     * Hashes keys
     */
    public static final String[] HASHES_PROJECTION = new String[] {_ID, KEY_MESSAGE_HASH};

    /**
     * Projection map used to select columns from the database
     */
    private static HashMap<String, String> messagesProjectionMap;

    /**
     * Matcher for our URIs
     */
    private static final UriMatcher uriMatcher;

    /**
     * Static objects for the provider
     */
    static {
        // new instance of urimatcher
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // add pattern for all messages
        uriMatcher.addURI(AUTHORITY, "all", ALL);

        // add pattern for all messages without blacklisted ones
        uriMatcher.addURI(AUTHORITY, "allNoBlacklist", ALL_NOBLACKLIST);

        // add pattern for outgoing messages
        uriMatcher.addURI(AUTHORITY, "outgoing", OUTGOING);

        // add pattern for blacklisted messages
        uriMatcher.addURI(AUTHORITY, "blacklist", BLACKLIST);

        // add pattern for a message
        uriMatcher.addURI(AUTHORITY, "messages", MESSAGES);

        // add a pattern for a particular message
        uriMatcher.addURI(AUTHORITY, "messages/#", MESSAGES_ID);

        // add a pattern for hashes
        uriMatcher.addURI(AUTHORITY, "hashes", HASHES);

        // add a pattern for a particular hash 
        uriMatcher.addURI(AUTHORITY, "hashes/*", HASHES_STRING);

        // Map each column to itself
        messagesProjectionMap = new HashMap<String, String>();
        messagesProjectionMap.put(_ID, _ID);
        messagesProjectionMap.put(KEY_TYPE, KEY_TYPE);
        messagesProjectionMap.put(KEY_TITLE, KEY_TITLE);
        messagesProjectionMap.put(KEY_CONTENT, KEY_CONTENT);
        messagesProjectionMap.put(KEY_MESSAGE_HASH, KEY_MESSAGE_HASH);
        messagesProjectionMap.put(KEY_TIME, KEY_TIME);
        messagesProjectionMap.put(KEY_ATTACHMENT_PATH, KEY_ATTACHMENT_PATH);
        messagesProjectionMap.put(KEY_ATTACHMENT_ORIGINAL_FILENAME, KEY_ATTACHMENT_ORIGINAL_FILENAME);
        messagesProjectionMap.put(KEY_MINE, KEY_MINE);
        messagesProjectionMap.put(KEY_BLACKLIST, KEY_BLACKLIST);
    }


    private MessagesDbHelper messagesDbHelper;
    
    /**
     * Database helper class for creating and upgrading the database
     */
    static class MessagesDbHelper extends SQLiteOpenHelper {
        private static Logger log = Logger.getLogger("FluidNexus"); 
    
        private static final String DATABASE_CREATE =
            "create table Messages (_id integer primary key autoincrement, type integer, title text, content text, message_hash text, time float, attachment_path text, attachment_original_filename text, mine bit, blacklist bit default 0);";
    
        public MessagesDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
    
        @Override
        public void onCreate(SQLiteDatabase database) {
            log.info("Creating database...");
            database.execSQL(DATABASE_CREATE);
        }
    
        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            log.info("Upgrading database...");
        }
    
    }

    /**
     * Initializes the content provider
     */
    @Override
    public boolean onCreate() {
        // Creates a new helper object
        messagesDbHelper = new MessagesDbHelper(getContext());
        return true;
    }

    /**
     * Query the underlying datastore
     * Called when a client calls {@link android.content.ContentResolver#query(Uri, String[], String, String[], String)}.
     *
     * @return a cursor containing the results of the query.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // Constructs a new query builder
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(DATABASE_TABLE);

        // Setup query based on URI
        switch (uriMatcher.match(uri)) {
            case ALL:
                qb.setProjectionMap(messagesProjectionMap);
                break;
            case ALL_NOBLACKLIST:
                qb.setProjectionMap(messagesProjectionMap);
                qb.appendWhere(KEY_BLACKLIST + "=0");
                break;
            case OUTGOING:
                qb.setProjectionMap(messagesProjectionMap);
                qb.appendWhere(
                        KEY_MINE + "=1"
                );
                break;
            case BLACKLIST:
                qb.setProjectionMap(messagesProjectionMap);
                qb.appendWhere(
                        KEY_BLACKLIST + "=1"
                );
                break;
            case MESSAGES_ID:
                qb.setProjectionMap(messagesProjectionMap);
                qb.appendWhere(
                        _ID + "=" + uri.getPathSegments().get(MESSAGES_ID_PATH_POSITION)
                );
                break;
            case HASHES_STRING:
                qb.setProjectionMap(messagesProjectionMap);
                qb.appendWhere(KEY_MESSAGE_HASH + "='" + uri.getPathSegments().get(HASHES_STRING_PATH_POSITION) + "'");
                break;

            default:
                // If the URI doesn't match any of the known patterns, throw an exception.
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (sortOrder == null) {
            sortOrder = "time DESC";
        }

        // Open in read mode since no changes are to be done
        SQLiteDatabase db = messagesDbHelper.getReadableDatabase();

        /*
         * Perform the query
         */
        Cursor c = qb.query(
                db,             // database to query
                projection,     // columns to return from the query
                selection,      // columns for the where clause
                selectionArgs,  // values for the where clause
                null,           // don't group the rows
                null,           // don't filter by row groups
                sortOrder       // sort order
        );

        // Tell the Cursor what URI to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);

        // Close the database when we're done with it
        //db.close();

        return c;
    }

    /**
     * Update the underlying datastore
     */
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = messagesDbHelper.getWritableDatabase();
        int count;
        String finalWhere;

        switch (uriMatcher.match(uri)) {
            case MESSAGES:
                count = db.update(
                    DATABASE_TABLE,
                    values,
                    where,
                    whereArgs
                );
                break;
            case MESSAGES_ID:
                // Create an introductory where clause
                finalWhere = _ID + " = " + uri.getPathSegments().get(MESSAGES_ID_PATH_POSITION);

                // If there were other wheres, add them in
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Perform the delete
                count = db.update(DATABASE_TABLE, values, finalWhere, whereArgs);
                break;
            case HASHES_STRING:
                // Create an introductory where clause
                finalWhere = _ID + " = " + uri.getPathSegments().get(HASHES_STRING_PATH_POSITION);

                // If there were other wheres, add them in
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Perform the delete
                count = db.update(DATABASE_TABLE, values, finalWhere, whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /*
         * Get a handle to the content resolver object to notify registered observers
         */
        getContext().getContentResolver().notifyChange(uri, null);


        //db.close();
        return 0;
    }

    /**
     * Delete an item from the underlying datastore
     */
     @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = messagesDbHelper.getWritableDatabase();
        String finalWhere;

        int count;

        switch (uriMatcher.match(uri)) {
            case MESSAGES:
                count = db.delete(
                    DATABASE_TABLE,
                    where,
                    whereArgs
                );
                break;
            case MESSAGES_ID:
                // Create an introductory where clause
                finalWhere = _ID + " = " + uri.getPathSegments().get(MESSAGES_ID_PATH_POSITION);

                // If there were other wheres, add them in
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Perform the delete
                count = db.delete(DATABASE_TABLE, finalWhere, whereArgs);
                break;
            case HASHES_STRING:
                // Create an introductory where clause
                finalWhere = _ID + " = " + uri.getPathSegments().get(HASHES_STRING_PATH_POSITION);

                // If there were other wheres, add them in
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Perform the delete
                count = db.delete(DATABASE_TABLE, finalWhere, whereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /*
         * Get a handle to the content resolver object to notify registered observers
         */
        getContext().getContentResolver().notifyChange(uri, null);
        
        //db.close();

        // return number of rows deleted
        return count;

    }


    /**
     * Insert an item into the underlying datastore
     * Called when a client calls {@link android.content.ContentResolver#insert(Uri, ContentValues)}.
     * @return The row ID of the inserted row
     * @throws SQLException if the insertion fails
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        // Check the incoming URI; only accept "messages"
        if (uriMatcher.match(uri) != MESSAGES) {
            throw new IllegalArgumentException("Unknown URI " + uri); 
        }

        // Map to hold new record's values
        ContentValues values;

        // If the incoming values are not null, use it for the new values
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            // Otherwise, create a new map
            values = new ContentValues();
        }

        // Open database in write mode
        SQLiteDatabase db = messagesDbHelper.getWritableDatabase();
        
        // Perform the actual insert and return the new ID
        long rowID = db.insert(
            DATABASE_TABLE,
            null,
            values
        );
        //db.close();

        // If the insert succeed, the rowID exists
        if (rowID > 0) {
            // Creates a new URI with the messages ID pattern and the new rowID appended to it
            Uri messagesURI = ContentUris.withAppendedId(MESSAGES_URI_ID_BASE, rowID);

            // Notifies registered observers that the data has changed
            getContext().getContentResolver().notifyChange(messagesURI, null);

            return messagesURI;
        }

        // If the insert didn't succeed, throw an exception
        throw new SQLException("Failed to insert row into " + uri);

    }

    /**
     * Get the type of the given URI
     */
    @Override
    public String getType(Uri uri) {

        // Return the general content type
        switch (uriMatcher.match(uri)) {
            case ALL:
            case ALL_NOBLACKLIST:
            case OUTGOING:
            case BLACKLIST:
            case MESSAGES:
            case MESSAGES_ID:
                return CONTENT_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);

        }

    }



}