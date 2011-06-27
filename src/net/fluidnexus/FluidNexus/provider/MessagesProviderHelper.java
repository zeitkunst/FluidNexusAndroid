package net.fluidnexus.FluidNexus.provider;

import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import net.fluidnexus.FluidNexus.Logger;

public class MessagesProviderHelper {

    private static Logger log = Logger.getLogger("FluidNexus"); 
    private final Context context;
    private ContentResolver cr;

    public MessagesProviderHelper(Context applicationContext) {
        context = applicationContext;
        cr = context.getContentResolver();
    }

    public void initialPopulate() {
            float now = (float) (System.currentTimeMillis()/1000);        
            add_new(0,
                "Witness to the event",
                "I saw them being taken away in the car--just swooped up like that.  (This is an example of a message we have created that is just marked as 'outgoing'.  The system can be easily used for spreading personal testimonials like this one.)");
            add_received(0, now,
               "Schedule a meeting",
                "We need to schedule a meeting soon.  Send a message around with the title [S] and good times to meet.  (This is an example of using the system to surreptitiously spread information about covert meetings.)");
            add_received(0, now,
                "Building materials",
                "Some 2x4's and other sundry items seen around Walker Terrace.  (In the aftermath of a disaster, knowing where there might be temporary sources of material is very important.)");
            add_received(0, now,
                "Universal Declaration of Human Rights",
                "All human beings are born free and equal in dignity and rights.They are endowed with reason and conscience and should act towards one another in a spirit of brotherhood.  (In repressive regimes the system could be used to spread texts or other media that would be considered subversive.).  Everyone is entitled to all the rights and freedoms set forth in this Declaration, without distinction of any kind, such as race, colour, sex, language, religion, political or other opinion, national or social origin, property, birth or other status. Furthermore, no distinction shall be made on the basis of the political, jurisdictional or international status of the country or territory to which a person belongs, whether it be independent, trust, non-self-governing or under any other limitation of sovereignty....");

    }

    /**
     * Get all our messages
     */
    public Cursor all() {

        Cursor c = cr.query(MessagesProvider.ALL_URI, MessagesProvider.ALL_PROJECTION, null, null, null);
        c.moveToFirst();
        return c;
    }

    /**
     * Get all our messages minuse those blacklisted
     */
    public Cursor allNoBlacklist() {
            Cursor c = cr.query(MessagesProvider.ALL_NOBLACKLIST_URI, MessagesProvider.ALL_PROJECTION, null, null, null);
            c.moveToFirst();
            if (c == null) {
                log.debug("cursor is null");
            } else {
                log.debug("cursor is " + c);
            }
            return c;
    }

    /**
     * Get our outgoing messages
     */
    public Cursor outgoing() {
            Cursor c = cr.query(MessagesProvider.OUTGOING_URI, MessagesProvider.ALL_PROJECTION, null, null, null);
            c.moveToFirst();
            return c;
    }

    /**
     * Get our blacklsited messages
     */
    public Cursor blacklist() {
            Cursor c = cr.query(MessagesProvider.BLACKLIST_URI, MessagesProvider.ALL_PROJECTION, null, null, null);
            c.moveToFirst();
            return c;
    }

    /**
     * Get our hashes 
     */
    public Cursor hashes() {

        Cursor c = cr.query(MessagesProvider.ALL_URI, MessagesProvider.HASHES_PROJECTION, null, null, null);
        c.moveToFirst();
        return c;

    }

    /**
     * Add a new message that doesn't have an attachment.
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     */
    public Uri add_new(int type, String title, String content) {

        float now = (float) (System.currentTimeMillis()/1000);
        ContentValues values = new ContentValues();
        values.put(MessagesProvider.KEY_TYPE, 0);
        values.put(MessagesProvider.KEY_TITLE, title);
        values.put(MessagesProvider.KEY_CONTENT, content);
        values.put(MessagesProvider.KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(MessagesProvider.KEY_TIME, now);
        values.put(MessagesProvider.KEY_ATTACHMENT_PATH, "");
        values.put(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME, "");
        values.put(MessagesProvider.KEY_MINE, 1);
        values.put(MessagesProvider.KEY_BLACKLIST, 0);

        return cr.insert(MessagesProvider.MESSAGES_URI, values);
    }


    /**
     * Add a new message that has an attachment.
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     * @param attachment_path Path to the (local) attachment
     * @param attachment_original_filename Original filename of attachment
     */
    public Uri add_new(int type,
            String title,
            String content, String attachment_path, String attachment_original_filename) {

        float now = (float) (System.currentTimeMillis()/1000);

        ContentValues values = new ContentValues();
        values.put(MessagesProvider.KEY_TYPE, type);
        values.put(MessagesProvider.KEY_TITLE, title);
        values.put(MessagesProvider.KEY_CONTENT, content);
        values.put(MessagesProvider.KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(MessagesProvider.KEY_TIME, now);
        values.put(MessagesProvider.KEY_ATTACHMENT_PATH, attachment_path);
        values.put(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME, attachment_original_filename);
        values.put(MessagesProvider.KEY_MINE, 1);
        values.put(MessagesProvider.KEY_BLACKLIST, 0);

        return cr.insert(MessagesProvider.MESSAGES_URI, values);
    }

    /**
     * Add a received message that doesn't have an attachment.
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     */
    public Uri add_received(int type, float now, String title, String content) {

        ContentValues values = new ContentValues();
        values.put(MessagesProvider.KEY_TYPE, 0);
        values.put(MessagesProvider.KEY_TITLE, title);
        values.put(MessagesProvider.KEY_CONTENT, content);
        values.put(MessagesProvider.KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(MessagesProvider.KEY_TIME, now);
        values.put(MessagesProvider.KEY_ATTACHMENT_PATH, "");
        values.put(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME, "");
        values.put(MessagesProvider.KEY_MINE, 0);
        values.put(MessagesProvider.KEY_BLACKLIST, 0);

        return cr.insert(MessagesProvider.MESSAGES_URI, values);

    }

    /**
     * Add a received message that has an attachment
     * @param type Type of the message
     * @param title Title of the message
     * @param content Content of the message
     * @param attachment_path Path to the (local) attachment
     * @param attachment_original_filename Original filename of attachment

     */
    public Uri add_received(int type, float timestamp,
            String title,
            String content, String attachment_path, String attachment_original_filename) {
    
        ContentValues values = new ContentValues();
        values.put(MessagesProvider.KEY_TYPE, type);
        values.put(MessagesProvider.KEY_TITLE, title);
        values.put(MessagesProvider.KEY_CONTENT, content);
        values.put(MessagesProvider.KEY_MESSAGE_HASH, makeSHA256(title + content));
        values.put(MessagesProvider.KEY_TIME, timestamp);
        values.put(MessagesProvider.KEY_ATTACHMENT_PATH, attachment_path);
        values.put(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME, attachment_original_filename);
        values.put(MessagesProvider.KEY_MINE, 0);
        values.put(MessagesProvider.KEY_BLACKLIST, 0);

        return cr.insert(MessagesProvider.MESSAGES_URI, values);
    }

    /**
     * Return item based on its ID
     * @param id ID of the desired item
     */
    public Cursor returnItemByID(long id) {
        Uri uri = ContentUris.withAppendedId(MessagesProvider.MESSAGES_URI_ID_BASE, id);
        Cursor c = cr.query(uri, MessagesProvider.ALL_PROJECTION, null, null, null);
        c.moveToFirst();
        return c;
    }

   
    /**
     * Return an item based on a hash
     * @param hash Hash to search for
     */
    public Cursor returnItemBasedOnHash(String hash) {
        Uri newUri = MessagesProvider.HASHES_URI_STRING_BASE.withAppendedPath(MessagesProvider.HASHES_URI_STRING_BASE, hash);
        Cursor c = cr.query(newUri, MessagesProvider.ALL_PROJECTION, null, null, null);
        c.moveToFirst();
        return c;
    }

    /**
     * Delete an item by the id 
     */
    public int deleteById(long id) {
        Uri uri = ContentUris.withAppendedId(MessagesProvider.MESSAGES_URI_ID_BASE, id);
        return cr.delete(uri, null, null);
    }

    /**
     * Update an item by the ID
     */
    public int updateItemByID(long id, ContentValues cv) {
        Uri uri = ContentUris.withAppendedId(MessagesProvider.MESSAGES_URI_ID_BASE, id);
        return cr.update(uri, cv, null, null);

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
