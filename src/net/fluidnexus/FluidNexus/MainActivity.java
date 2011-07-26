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

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.SimpleCursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.TreeSet;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.HttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

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
import net.fluidnexus.FluidNexus.services.NetworkService;
/*
 * TODO
 * * deal with new binding to the service when clicking on the notification; this shouldn't happen
 */

public class MainActivity extends ListActivity {
    private Cursor c = null;
    private MessagesProviderHelper messagesProviderHelper = null;

    private Toast toast;
    
    private SharedPreferences prefs;
    private Editor prefsEditor;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    // just for testing
    private BroadcastReceiver iReceiver;
    private IntentFilter iFilter;

    private static Logger log = Logger.getLogger("FluidNexus"); 

    private static final int ACTIVITY_HOME = 0;
    private static final int ACTIVITY_VIEW_OUTGOING = 1;
    private static final int ACTIVITY_ADD_OUTGOING = 2;
    private static final int ACTIVITY_PREFERENCES= 3;
    private static final int ACTIVITY_VIEW_MESSAGE = 4;
    private static final int ACTIVITY_HELP = 5;
    private static final int REQUEST_ENABLE_BT = 6;
    private static final int ACTIVITY_EDIT_MESSAGE = 7;

    private static final int VIEW_ALL = 0;
    private static final int VIEW_PUBLIC = 1;
    private static final int VIEW_OUTGOING = 2;
    private static final int VIEW_BLACKLIST = 3;
    private static int VIEW_MODE = VIEW_ALL;

    private static final int MESSAGE_VIEW_LENGTH = 300;

    private long currentRowID = -1;

    private static final int DIALOG_REALLY_DELETE = 0;
    private static final int DIALOG_REALLY_BLACKLIST = 1;
    private static final int DIALOG_REALLY_UNBLACKLIST = 2;
    private static final int DIALOG_NO_KEY = 3;

    private static final int MENU_ADD_ID = Menu.FIRST;
    private static final int MENU_VIEW_ID = Menu.FIRST + 1;
    private static final int MENU_SETTINGS_ID = Menu.FIRST + 2;
    private static final int MENU_ALL_ID = Menu.FIRST + 3;
    private static final int MENU_DELETE_ID = Menu.FIRST + 4;
    private static final int MENU_HELP_ID = Menu.FIRST + 5;
    private static final int MENU_BLACKLIST_ID = Menu.FIRST + 5;

    // messages to/from bluetooth service
    Messenger networkService = null;
    final Messenger messenger = new Messenger(new IncomingHandler());
    private boolean bound = false;

    // Messages to the bluetooth service
    public static final int MSG_NEW_MESSAGE_CREATED = 0xF0;
    public static final int MSG_MESSAGE_DELETED = 0xF1;

    private boolean showMessages = true;

    private BluetoothAdapter bluetoothAdapter = null;
    private boolean enableBluetoothServicePref = true;

    private File attachmentsDir = null;

    // oauth constants
    private static final String API_BASE = "http://192.168.1.36:6543/api/01/";
    private static final String REQUEST_URL = API_BASE + "request_token/android";
    private static final String ACCESS_URL = API_BASE + "access_token";
    private static final String AUTH_URL = API_BASE + "authorize_token/android";
    private static final String CALLBACK_URL = "fluidnexus://access_token";
    private static CommonsHttpOAuthConsumer consumer = null;
    private static CommonsHttpOAuthProvider provider = new CommonsHttpOAuthProvider(REQUEST_URL, ACCESS_URL, AUTH_URL);

    /**
     * Our handler for incoming messages
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NetworkService.MSG_NEW_MESSAGE_RECEIVED:
                    Toast.makeText(getApplicationContext(), R.string.toast_new_message_received, Toast.LENGTH_LONG).show();
                    fillListView(VIEW_MODE);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private ServiceConnection networkServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            networkService = new Messenger(service);
            try {
                Message msg = Message.obtain(null, NetworkService.MSG_REGISTER_CLIENT);
                msg.replyTo = messenger;
                networkService.send(msg);
                log.debug("Connected to service");

                // Send bluetooth enabled bit on start
                msg = Message.obtain(null, NetworkService.MSG_BLUETOOTH_ENABLED);
                msg.arg1 = (prefs.getBoolean("enableBluetoothServicePref", false))? 1 : 0;
                msg.arg2 = Integer.parseInt(prefs.getString("bluetoothScanFrequency", "120"));
                msg.replyTo = messenger;
                networkService.send(msg);

                // Send zeroconf enabled bit on start
                msg = Message.obtain(null, NetworkService.MSG_ZEROCONF_ENABLED);
                msg.arg1 = (prefs.getBoolean("enableZeroconfServicePref", false)) ? 1 : 0;
                msg.arg2 = Integer.parseInt(prefs.getString("zeroconfScanFrequency", "120"));
                msg.replyTo = messenger;
                networkService.send(msg);

                // Send bit for starting nexus service
                msg = Message.obtain(null, NetworkService.MSG_NEXUS_START);
                msg.arg1 = (prefs.getBoolean("enableNexusServicePref", false)) ? 1 : 0;
                // TODO
                // Make this configurable?
                //msg.arg2 = Integer.parseInt(prefs.getString("zeroconfScanFrequency", "120"));
                msg.arg2 = 120;
                Bundle bundle = new Bundle();
                bundle.putString("key", prefs.getString("nexusKeyPref", ""));
                bundle.putString("secret", prefs.getString("nexusSecretPref", ""));
                bundle.putString("token", prefs.getString("nexusTokenPref", ""));
                bundle.putString("token_secret", prefs.getString("nexusTokenSecretPref", ""));
                msg.setData(bundle);
                msg.replyTo = messenger;
                networkService.send(msg);


            } catch (RemoteException e) {
                // Here, the service has crashed even before we were able to connect
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // Called when the connection to the service has been unexpectedly closed
            networkService = null;
            log.debug("Disconnected from service");
        }

    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        log.verbose("unfreezing...");

        if (messagesProviderHelper == null) {
            messagesProviderHelper = new MessagesProviderHelper(this);
        }

        setContentView(R.layout.message_list);
        registerForContextMenu(getListView());

        /*      
        // Regiser my receiver to NEW_MESSAGE action
        iFilter = new IntentFilter(getText(R.string.intent_new_message).toString());
        iReceiver = new NewMessageIntentReceiver();
        registerReceiver(iReceiver, iFilter);
        */

        // setup bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // if it's not available, let user know
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available; sending and receiving messages will not be possible", Toast.LENGTH_LONG).show();
        }

        // Create our attachments dir            
        // TODO
        // Make this configurable to SD card
        File dataDir = Environment.getExternalStorageDirectory();
        attachmentsDir = new File(dataDir.getAbsolutePath() + "/FluidNexusAttachments");
        attachmentsDir.mkdirs();
    }

    /**
     * Method of creating dialogs for this activity
     * @param id ID of the dialog to create
     */
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;

        switch (id) {
            case DIALOG_REALLY_DELETE:
                dialog = reallyDeleteDialog();
                break;
            case DIALOG_REALLY_BLACKLIST:
                dialog = reallyBlacklistDialog();
                break;
            case DIALOG_REALLY_UNBLACKLIST:
                dialog = reallyUnblacklistDialog();
                break;
            case DIALOG_NO_KEY:
                dialog = noKeyDialog();
                break;
            default:
                dialog = null;
        }

        return dialog;
    }
    
    /**
     * Method to create our really delete dialog
     */
    private AlertDialog reallyDeleteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure you want to delete this message?")
            .setCancelable(false)
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Cursor localCursor = messagesProviderHelper.returnItemByID(currentRowID);
                    String attachmentPath = localCursor.getString(localCursor.getColumnIndex(MessagesProvider.KEY_ATTACHMENT_PATH));
                    boolean mine = localCursor.getInt(localCursor.getColumnIndex(MessagesProvider.KEY_MINE)) > 0;
                    localCursor.close();
                    
                    if ((!(attachmentPath.equals(""))) && (!mine)) {
                        File f = new File(attachmentPath);
                        f.delete();
                    }

                    messagesProviderHelper.deleteById(currentRowID);

                    try {
                        // Send message to service to note that a new message has been created
                        Message msg = Message.obtain(null, MSG_MESSAGE_DELETED);
                        networkService.send(msg);
                    } catch (RemoteException e) {
                        // Here, the service has crashed even before we were able to connect
                    }

                    currentRowID = -1;
                    fillListView(VIEW_MODE);
                    toast = Toast.makeText(getApplicationContext(), R.string.toast_message_deleted, Toast.LENGTH_SHORT);
                    toast.show();
                }
            })
            .setNegativeButton("No", null);
        return builder.create();
    }

    /**
     * Method to create our really blacklist dialog
     */
    private AlertDialog reallyBlacklistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.really_blacklist_dialog)
            .setCancelable(false)
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    ContentValues values = new ContentValues();
                    values.put(MessagesProvider.KEY_BLACKLIST, 1);
                    messagesProviderHelper.updateItemByID(currentRowID, values);

                    currentRowID = -1;
                    fillListView(VIEW_MODE);
                    toast = Toast.makeText(getApplicationContext(), R.string.toast_message_blacklisted, Toast.LENGTH_SHORT);
                    toast.show();
                }
            })
            .setNegativeButton("No", null);
        return builder.create();
    }


    /**
     * Method to create our really blacklist dialog
     */
    private AlertDialog reallyUnblacklistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.really_unblacklist_dialog)
            .setCancelable(false)
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    ContentValues values = new ContentValues();
                    values.put(MessagesProvider.KEY_BLACKLIST, 0);
                    messagesProviderHelper.updateItemByID(currentRowID, values);

                    currentRowID = -1;
                    fillListView(VIEW_MODE);
                    toast = Toast.makeText(getApplicationContext(), R.string.toast_message_unblacklisted, Toast.LENGTH_SHORT);
                    toast.show();
                }
            })
            .setNegativeButton("No", null);
        return builder.create();
    }

    /**
     * Method to create our lack of nexus key or secret dialog
     */
    private AlertDialog noKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.no_key_dialog)
            .setCancelable(false)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
        return builder.create();
    }


    @Override 
    public void onStart() {
        super.onStart();

        fillListView(VIEW_MODE);

        setupPreferences();


        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                toast = Toast.makeText(this, "Restart application to use bluetooth.", Toast.LENGTH_LONG);
                toast.show();
            } else {
                /*
                if (networkService == null) {
                    setupFluidNexusBluetoothService();
                }
                */
            }
        }

        // Bind to the network service
        doBindService();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Uri uri = this.getIntent().getData();

        if (uri != null && uri.toString().startsWith(CALLBACK_URL)) {
            String token = uri.getQueryParameter("oauth_token");
            String token_secret = uri.getQueryParameter("oauth_token_secret");

            prefsEditor = prefs.edit();
            if ((token != null) && (token_secret != null)) {
                prefsEditor.putString("nexusTokenPref", token);
                prefsEditor.putString("nexusTokenSecretPref", token_secret);
                prefsEditor.commit();
                Toast.makeText(this, R.string.toast_tokens_updated, Toast.LENGTH_LONG).show();
            } else {
                log.error("Unable to parse token or token_secret from the uri: " + uri);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        c.close();

        try {
            if (bound) {
                doUnbindService();
            }
        } catch (Throwable t) {
            log.error("Failed to unbind from the service");
        }

    }

    /*
     * Context menu code from:
     * http://stackoverflow.com/questions/6205808/how-to-handle-long-tap-on-listview-item
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        currentRowID = info.id;
        
        Cursor localCursor = messagesProviderHelper.returnItemByID(currentRowID);
        menu.setHeaderTitle(localCursor.getString(localCursor.getColumnIndexOrThrow(MessagesProvider.KEY_TITLE)));
        int mine = localCursor.getInt(localCursor.getColumnIndexOrThrow(MessagesProvider.KEY_MINE));

        if (VIEW_MODE == VIEW_BLACKLIST) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.message_list_context_unblacklist, menu);
        } else {
            if (mine == 0) {
                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.message_list_context_noedit, menu);
            } else {
                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.message_list_context, menu);
            }
        }

        localCursor.close();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
            case R.id.delete_message:
                showDialog(DIALOG_REALLY_DELETE);
                return true;
            case R.id.blacklist_message:
                showDialog(DIALOG_REALLY_BLACKLIST);
                return true;
            case R.id.unblacklist_message:
                showDialog(DIALOG_REALLY_UNBLACKLIST);
                return true;
            case R.id.edit_message:
                editMessage();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * Bind to the service
     */
    private void doBindService() {
        if (bound == false) {
            log.info("Binding to Fluid Nexus Network Service");
            Intent i = new Intent(this, NetworkService.class);
            startService(i);
            bindService(i, networkServiceConnection, Context.BIND_AUTO_CREATE);
            bound = true;
        }
    }

    /**
     * Unbind to the service
     */
    private void doUnbindService() {
        if (networkService != null) {
            try {
                Message msg = Message.obtain(null, NetworkService.MSG_UNREGISTER_CLIENT);
                msg.replyTo = messenger;
                networkService.send(msg);
            } catch (RemoteException e) {
                // nothing special to do if the service has already stopped for some reason
            }

            unbindService(networkServiceConnection);
            log.info("Unbound to the Fluid Nexus Bluetooth Service");
        }
    }


    /**
     * Open up a new activity to edit the message
     */
    private void editMessage() {

        Cursor localCursor = messagesProviderHelper.returnItemByID(currentRowID);

        Intent i = new Intent(this, EditMessage.class);
        i.putExtra(MessagesProvider._ID, localCursor.getInt(localCursor.getColumnIndex(MessagesProvider._ID)));
        i.putExtra(MessagesProvider.KEY_TYPE, localCursor.getInt(localCursor.getColumnIndex(MessagesProvider.KEY_TYPE)));
        i.putExtra(MessagesProvider.KEY_TITLE, localCursor.getString(localCursor.getColumnIndex(MessagesProvider.KEY_TITLE)));
        i.putExtra(MessagesProvider.KEY_CONTENT, localCursor.getString(localCursor.getColumnIndex(MessagesProvider.KEY_CONTENT)));
        i.putExtra(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME, localCursor.getString(localCursor.getColumnIndex(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME)));
        i.putExtra(MessagesProvider.KEY_ATTACHMENT_PATH, localCursor.getString(localCursor.getColumnIndex(MessagesProvider.KEY_ATTACHMENT_PATH)));
        i.putExtra(MessagesProvider.KEY_PUBLIC, localCursor.getInt(localCursor.getColumnIndex(MessagesProvider.KEY_PUBLIC)) > 0);

        localCursor.close();
        startActivityForResult(i, ACTIVITY_EDIT_MESSAGE);

    }

    private void setupPreferences() {
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean firstRun = prefs.getBoolean("FirstRun", true);

        if (firstRun == true) {
            prefsEditor = prefs.edit();
            prefsEditor.putBoolean("FirstRun", false);
            prefsEditor.commit();

            messagesProviderHelper.initialPopulate();            
            fillListView(VIEW_MODE);
        }
        
        showMessages = prefs.getBoolean("ShowMessages", true);

        // Setup a listener for when preferences change
        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
                if (key.equals("enableBluetoothServicePref")) {
                    boolean tmp = prefs.getBoolean("enableBluetoothServicePref", true);
                    try {
                        // Send bluetooth enabled bit
                        Message msg = Message.obtain(null, NetworkService.MSG_BLUETOOTH_ENABLED);
                        msg.arg1 = (prefs.getBoolean("enableBluetoothServicePref", false))? 1 : 0;
                        msg.replyTo = messenger;
                        networkService.send(msg);
                        enableBluetoothServicePref = tmp;
                    } catch (RemoteException e) {
                        log.error("Unable to send MSG_BLUETOOTH_ENABLED");
                    }
                } else if (key.equals("bluetoothScanFrequency")) {
                    try {
                        Message msg = Message.obtain(null, NetworkService.MSG_BLUETOOTH_SCAN_FREQUENCY);
                        msg.arg1 = Integer.parseInt(prefs.getString("bluetoothScanFrequency", "5"));
                        msg.replyTo = messenger;
                        networkService.send(msg);

                    } catch (RemoteException e) {
                        log.error("Unable to send scan frequency message: " + e);
                    }
                } else if (key.equals("showMessages")) {
                    showMessages = prefs.getBoolean("showMessages", true);
                    fillListView(VIEW_MODE);
                }

            }

        };
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.message_list_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        TextView tv;

        switch (item.getItemId()) {
            case R.id.menu_add:
                addOutgoingMessage();
                return true;
            case R.id.menu_view_all:
                VIEW_MODE = VIEW_ALL;
                fillListView(VIEW_MODE);

                // Update our header text view
                tv = (TextView) findViewById(R.id.message_list_header_text);
                tv.setText(R.string.message_list_header_text_all);

                return true;
            case R.id.menu_view_public:
                VIEW_MODE = VIEW_PUBLIC;
                fillListView(VIEW_MODE);

                // Update our header text view
                tv = (TextView) findViewById(R.id.message_list_header_text);
                tv.setText(R.string.message_list_header_text_public);

                return true;

            case R.id.menu_view_outgoing:
                VIEW_MODE = VIEW_OUTGOING;
                fillListView(VIEW_MODE);

                // Update our header text view
                tv = (TextView) findViewById(R.id.message_list_header_text);
                tv.setText(R.string.message_list_header_text_outgoing);

                return true;
            case R.id.menu_view_blacklist:
                VIEW_MODE = VIEW_BLACKLIST;
                fillListView(VIEW_MODE);

                // Update our header text view
                tv = (TextView) findViewById(R.id.message_list_header_text);
                tv.setText(R.string.message_list_header_text_blacklist);

                return true;
            case R.id.menu_request_authorization:
                String key = prefs.getString("nexusKeyPref", "");
                String secret = prefs.getString("nexusSecretPref", "");

                if (key.equals("")) {
                    showDialog(DIALOG_NO_KEY);
                    return true;
                } else {

                    consumer = new CommonsHttpOAuthConsumer(key, secret);
                    HttpParameters p = new HttpParameters();
                    TreeSet<String> s = new TreeSet<String>();
                    s.add(CALLBACK_URL);
                    p.put("oauth_callback", s); 
                    //consumer.setAdditionalParameters(p);

                    try {
                        //HttpPost request = new HttpPost(REQUEST_URL);
                        //consumer.sign(request);
                        //HttpClient httpClient = new DefaultHttpClient();
                        //HttpResponse response = httpClient.execute(request);
                        //log.debug(EntityUtils.toString(response.getEntity()));
                        
                        provider.setOAuth10a(true);
                        String authURL = provider.retrieveRequestToken(consumer, CALLBACK_URL);
                        log.debug("URL: " + authURL);

                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authURL)));
                    } catch (OAuthMessageSignerException e) {
                        log.debug("OAuthMessageSignerException: " + e);
                    } catch (OAuthExpectationFailedException e) {
                        log.debug("OAuthExpectationFailedException: " + e);
                    } catch (OAuthCommunicationException e) {
                        log.debug("OAuthCommunicationException: " + e);
                    } catch (OAuthNotAuthorizedException e) {
                        log.debug("OAuthNotAuthorizedException: " + e);
                    } 
                    /*catch (IOException e) {
                        log.debug("Some sort of error trying to parse result" + e);
                    }*/

                    return true;
                }
            case R.id.menu_preferences:
                editPreferences();
                return true;
            case R.id.menu_help:
                Intent i = new Intent(this, Help.class);
                /*startSubActivity(i, ACTIVITY_HELP);*/
                startActivityForResult(i, ACTIVITY_HELP);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // We will need to be careful later here about the different uses of position and rowID
        super.onListItemClick(l, v, position, id);
        Cursor localCursor = messagesProviderHelper.returnItemByID(id);
        //localCursor.moveToPosition(position);

        Intent i = new Intent(this, ViewMessage.class);
        i.putExtra(MessagesProvider._ID, localCursor.getInt(localCursor.getColumnIndex(MessagesProvider._ID)));
        i.putExtra(MessagesProvider.KEY_TITLE, localCursor.getString(localCursor.getColumnIndex(MessagesProvider.KEY_TITLE)));
        i.putExtra(MessagesProvider.KEY_CONTENT, localCursor.getString(localCursor.getColumnIndex(MessagesProvider.KEY_CONTENT)));
        i.putExtra(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME, localCursor.getString(localCursor.getColumnIndex(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME)));
        i.putExtra(MessagesProvider.KEY_ATTACHMENT_PATH, localCursor.getString(localCursor.getColumnIndex(MessagesProvider.KEY_ATTACHMENT_PATH)));
        c.close();
        startActivityForResult(i, ACTIVITY_VIEW_MESSAGE);
        localCursor.close();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case(ACTIVITY_VIEW_MESSAGE):
                fillListView(VIEW_MODE);
                break;
            case(ACTIVITY_ADD_OUTGOING):
                try {
                    // Send message to service to note that a new message has been created
                    Message msg = Message.obtain(null, MSG_NEW_MESSAGE_CREATED);
                    networkService.send(msg);
                } catch (RemoteException e) {
                    // Here, the service has crashed even before we were able to connect
                }

                fillListView(VIEW_MODE);
                break;
            case(ACTIVITY_PREFERENCES):
                break;
            case(REQUEST_ENABLE_BT):
                if (resultCode == ListActivity.RESULT_OK) {
                    // setup services here
                } else {
                    log.warn("Bluetooth not enabled");
                    Toast.makeText(this, "Bluetooth was not enabled, leaving", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void editPreferences() {
        Intent intent = new Intent(this, Preferences.class);
        startActivityForResult(intent, ACTIVITY_PREFERENCES);
    }

    private void addOutgoingMessage() {
        Intent intent = new Intent(this, AddOutgoing.class);
        startActivityForResult(intent, ACTIVITY_ADD_OUTGOING);
    }


    private void fillListView(int viewType) {
        if (!(showMessages)) {
            log.debug("We shouldn't be showing messages...");
            return;
        }


        TextView tv;
        tv = (TextView) findViewById(R.id.message_list_item);
        
        String[] from = new String[] {MessagesProvider.KEY_TITLE, MessagesProvider.KEY_CONTENT, MessagesProvider.KEY_MINE, MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME};
        String[] projection = new String[] {MessagesProvider._ID, MessagesProvider.KEY_TITLE, MessagesProvider.KEY_CONTENT, MessagesProvider.KEY_MINE, MessagesProvider.KEY_ATTACHMENT_PATH, MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME, MessagesProvider.KEY_PUBLIC};
        int[] to = new int[] {R.id.message_list_item, R.id.message_list_data, R.id.message_list_item_icon, R.id.message_list_attachment};
        
        if (viewType == 0) {
            // Get the non-blacklisted messages
            c = messagesProviderHelper.allNoBlacklist();
        } else if (viewType == 1) {
            c = messagesProviderHelper.publicMessages();
        } else if (viewType == 2) {
            c = messagesProviderHelper.outgoing();
        } else if (viewType == 3) {
            c = messagesProviderHelper.blacklist();
        }

        SimpleCursorAdapter messagesAdapter = new SimpleCursorAdapter(this, R.layout.message_list_item, c, from, to);
        ListView lv;
        lv = (ListView) getListView();
        lv.setSelection(0);

        messagesAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int i) {
            
                if (i == cursor.getColumnIndex(MessagesProvider.KEY_CONTENT)) {
                    String fullMessage = cursor.getString(i);
                    TextView tv = (TextView) view;
                    int stringLen = fullMessage.length();
                    if (stringLen < MESSAGE_VIEW_LENGTH) {
                        tv.setText(fullMessage);
                    } else {
                        tv.setText(fullMessage.substring(0, MESSAGE_VIEW_LENGTH) + " ...");
                    }

                    return true;
                }

                if (i == cursor.getColumnIndex(MessagesProvider.KEY_MINE)) {
                    ImageView iv = (ImageView) view;
                    int mine = cursor.getInt(i);
                    boolean publicMessage = cursor.getInt(cursor.getColumnIndex(MessagesProvider.KEY_PUBLIC)) > 0;

                    if (mine == 0) {
                        if (publicMessage) {
                            iv.setImageResource(R.drawable.menu_public_other);
                        } else {
                            iv.setImageResource(R.drawable.menu_all);
                        }
                    } else if (mine == 1) {
                        if (publicMessage) {

                            iv.setImageResource(R.drawable.menu_public);
                        } else {
                            iv.setImageResource(R.drawable.menu_outgoing);
                        }
                    }

                    
                    
                    return true;
                }

                if (i == cursor.getColumnIndex(MessagesProvider.KEY_ATTACHMENT_ORIGINAL_FILENAME)) {

                    final String attachmentFilename = cursor.getString(i);
                    final String attachmentPath = cursor.getString(cursor.getColumnIndex(MessagesProvider.KEY_ATTACHMENT_PATH));
                    TextView viewAttachment = (TextView) view;
                   
                    if (attachmentFilename.equals("")) {
                        viewAttachment.setVisibility(View.GONE);
                    } else {
                        viewAttachment.setVisibility(View.VISIBLE);
                        viewAttachment.setText("Has attachment: " + attachmentFilename);
                    }

                    return true;

                }
                return false;
            }
        });

        setListAdapter(messagesAdapter);

    }
}
