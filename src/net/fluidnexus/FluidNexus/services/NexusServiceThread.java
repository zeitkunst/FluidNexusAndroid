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

package net.fluidnexus.FluidNexus.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.Vector;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import org.apache.http.NameValuePair;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import oauth.signpost.http.HttpParameters;

import net.fluidnexus.FluidNexus.provider.MessagesProvider;
import net.fluidnexus.FluidNexus.provider.MessagesProviderHelper;
import net.fluidnexus.FluidNexus.Base64;
import net.fluidnexus.FluidNexus.Logger;

/**
 * This thread runs all of the device/service discovery and starts threads for socket communication
 */
public class NexusServiceThread extends ServiceThread {
    // Logging
    private static Logger log = Logger.getLogger("FluidNexus"); 

    // State of the system
    private int state;

    // Potential states
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_RESOLVING = 1; // we're resolving things
    public static final int STATE_DISCOVERY_FINISHED = 2; // we're done discovering things and can now move on
    public static final int STATE_SERVICES = 3; // we're discovering services
    public static final int STATE_CONNECTING = 4; // we're connecting other devices
    public static final int STATE_CONNECTED = 5; // we're connected and sending data
    public static final int STATE_WAIT_FOR_CONNECTIONS = 6; // we wait for all of the connection threads to finish
    public static final int STATE_SERVICE_WAIT = 7; // we sleep for a bit before beginning the discovery process anew
    public static final int STATE_QUIT = 100; // we're done with everything

    // Connected devices
    private HashSet<String> connectedDevices = new HashSet<String>();

    // wifi infos
    private WifiManager wifiManager = null;

    // API infos

    private static final String API_BASE = "http://192.168.1.36:6543/api/01/";
    private static final String HASH_REQUEST_URL = API_BASE + "nexus/hashes/";
    private static final String NEXUS_NONCE_URL = API_BASE + "nexus/message/nonce.json";
    private static final String NEXUS_MESSAGE_URL = API_BASE + "nexus/message/update.json";
    private static final String REQUEST_URL = API_BASE + "request_token/android";
    private static final String ACCESS_URL = API_BASE + "access_token";
    private static final String AUTH_URL = API_BASE + "authorize_token/android";
    private static final String CALLBACK_URL = "fluidnexus://access_token";
    private static CommonsHttpOAuthConsumer consumer = null;
    private static CommonsHttpOAuthProvider provider = new CommonsHttpOAuthProvider(REQUEST_URL, ACCESS_URL, AUTH_URL);
    private String key = null;
    private String secret = null;
    private String token = null;
    private String token_secret = null;

    /**
     * Constructor for the thread that does nexus
     */
    public NexusServiceThread(Context ctx, ArrayList<Messenger> givenClients, String gkey, String gsecret, String gtoken, String gtoken_secret) {
        
        super(ctx, givenClients);
        key = gkey;
        secret = gsecret;
        token = gtoken;
        token_secret = gtoken_secret;
        
        // TODO
        // deal with what happens if wifi isn't enabled
        setName("NexusServiceThread");
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        setServiceState(STATE_NONE);
    }


    /**
     * Set the state of the bluetooth service
     * @param state Int that defines the current service state
     */
    @Override
    public synchronized void setServiceState(int newState) {
        //log.debug("Changing state from " + state + " to " + newState);
        state = newState;
    }

    /**
     * Get the current state value
     */
    @Override
    public synchronized int getServiceState() {
        return state;
    }

    /**
     * Start the listeners for zeroconf services
     */
    public void doStateNone() {
        if (wifiManager.getWifiState() == wifiManager.WIFI_STATE_ENABLED) {
            Cursor c = messagesProviderHelper.publicMessages();

            while (c.isAfterLast() == false) {
                String message_hash = c.getString(c.getColumnIndex(MessagesProvider.KEY_MESSAGE_HASH));
                boolean result = checkHash(message_hash);

                if (!result) {
                    try {
                        JSONObject message = new JSONObject();
                        message.put("message_title", c.getString(c.getColumnIndex(MessagesProvider.KEY_TITLE)));
                        message.put("message_content", c.getString(c.getColumnIndex(MessagesProvider.KEY_CONTENT)));
                        message.put("message_hash", c.getString(c.getColumnIndex(MessagesProvider.KEY_MESSAGE_HASH)));
                        message.put("message_type", c.getInt(c.getColumnIndex(MessagesProvider.KEY_TYPE)));
                        message.put("message_time", c.getFloat(c.getColumnIndex(MessagesProvider.KEY_TIME)));
                        message.put("message_received_time", c.getFloat(c.getColumnIndex(MessagesProvider.KEY_RECEIVED_TIME)));
                        
                        String attachment_path = c.getString(c.getColumnIndex(MessagesProvider.KEY_ATTACHMENT_PATH));
    
    
                        //String serializedMessage = message.toString();

                        // First, get our nonce
                        consumer = new CommonsHttpOAuthConsumer(key, secret);
                        consumer.setTokenWithSecret(token, token_secret);
                        HttpPost nonce_request = new HttpPost(NEXUS_NONCE_URL);
                        consumer.sign(nonce_request);
                        HttpClient client = new DefaultHttpClient();
                        String response = client.execute(nonce_request, new BasicResponseHandler());
                        JSONObject object = new JSONObject(response);
                        String nonce = object.getString("nonce");

                        // Then, take our nonce and key and put them in the message
                        message.put("message_nonce", nonce);
                        message.put("message_key", key);

                        // Setup our multipart entity
                        MultipartEntity entity = new MultipartEntity();

                        // Deal with file attachment
                        if (!attachment_path.equals("")) {
                            File file = new File(attachment_path);
                            ContentBody cbFile = new FileBody(file);
                            entity.addPart("message_attachment", cbFile);
                            // add the original filename to the message
                            message.put("message_attachment_original_filename", c.getString(c.getColumnIndex(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME)));

                            //FileInputStream fin = new FileInputStream(file);
                            //BufferedInputStream bin = new BufferedInputStream(fin);
                            //int length = (int) file.length();
    
                            // TODO
                            // Is there a better way of doing this, other than reading everything in at once?
                            //byte[] data = new byte[length];
                            //bin.read(data, 0, length);
                            //String dataBase64 = Base64.encodeBytes(data);
                        }

                        String serializedMessage = message.toString();
                        ContentBody messageBody = new StringBody(serializedMessage);
                        entity.addPart("message", messageBody);

                        HttpPost message_request = new HttpPost(NEXUS_MESSAGE_URL);
                        message_request.setEntity(entity);

                        //List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
                        //nameValuePairs.add(new BasicNameValuePair("message", serializedMessage));
                        //message_request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
                        client = new DefaultHttpClient();
                        client.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);

                        response = client.execute(message_request, new BasicResponseHandler());
                        object = new JSONObject(response);
                        boolean message_result = object.getBoolean("result");

                        
                        if (message_result) {
                            ContentValues values = new ContentValues();
                            values.put(MessagesProvider.KEY_MESSAGE_HASH, message_hash);
                            values.put(MessagesProvider.KEY_UPLOADED, 1);
                            int res = messagesProviderHelper.setPublic(c.getLong(c.getColumnIndex(MessagesProvider._ID)), values);
                            if (res == 0) {
                                log.debug("Message with hash " + message_hash + " not found; this should never happen!");
                            }
                        }

                    } catch (OAuthMessageSignerException e) {
                        log.debug("OAuthMessageSignerException: " + e);
                    } catch (OAuthExpectationFailedException e) {
                        log.debug("OAuthExpectationFailedException: " + e);
                    } catch (OAuthCommunicationException e) {
                        log.debug("OAuthCommunicationException: " + e);
                    } catch (JSONException e) {
                        log.debug("JSON Error: " + e);
                    } catch (IOException e) {
                        log.debug("IOException: " + e);
                    }
                }
                c.moveToNext();
            }

            c.close();

        }

        setServiceState(STATE_SERVICE_WAIT);
    }

    /**
     * Check the hash using the API
     */
    private boolean checkHash(String message_hash) {
        boolean result = true;

        try {
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet(HASH_REQUEST_URL + message_hash + ".json");
            String response = client.execute(request, new BasicResponseHandler());
            JSONObject object = new JSONObject(response);
            result = object.getBoolean("result");
        } catch (JSONException e) {
            log.debug("JSON Error: " + e);
        } catch (IOException e) {
            log.debug("IOException: " + e);
        }
        return result;

    }

    private void waitService() {
        try {
            log.debug("Service thread sleeping for " + getScanFrequency() + " seconds...");
            this.sleep(getScanFrequency() * 1000);
        } catch (InterruptedException e) {
            log.error("Thread sleeping interrupted: " + e);
        }

        setServiceState(STATE_NONE);
    }

    /**
     * Begin the thread, and thus the service main loop
     */
    @Override
    public void run() {
        
        while (getServiceState() != STATE_QUIT) {
            switch (getServiceState()) {
                case STATE_NONE:
                    doStateNone();
                    break;
                case STATE_SERVICE_WAIT:
                    waitService();
                    break;
                default:
                    break;
            }
        }
    }
}
