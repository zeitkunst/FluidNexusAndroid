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


package net.fluidnexus.FluidNexusAndroid;

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
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
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

import info.guardianproject.database.sqlcipher.SQLiteDatabase;
import net.fluidnexus.FluidNexusAndroid.provider.MessagesProviderHelper;
import net.fluidnexus.FluidNexusAndroid.services.NetworkService;

/*
 * TODO
 * * deal with new binding to the service when clicking on the notification; this shouldn't happen
 */

public class MainActivity extends ListActivity {
    private Cursor c = null;
    private MessagesProviderHelper messagesProviderHelper = null;

    // This has to be attended to carefully
    // We don't want to keep it around too long, but we need to send it to the service
    // So immediately after sending it to the service, so that it can be used to open a database there, we set the instance variable to null
    private String passphrase = null; 

    private Toast toast;
    
    private SharedPreferences prefs;
    private Editor prefsEditor;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    private static Logger log = Logger.getLogger("FluidNexus"); 

    private static final int ACTIVITY_HOME = 0;
    private static final int ACTIVITY_VIEW_OUTGOING = 1;
    private static final int ACTIVITY_ADD_OUTGOING = 2;
    private static final int ACTIVITY_PREFERENCES= 3;
    private static final int ACTIVITY_VIEW_MESSAGE = 4;
    private static final int ACTIVITY_HELP = 5;
    private static final int REQUEST_ENABLE_BT = 6;
    private static final int ACTIVITY_EDIT_MESSAGE = 7;
    private static final int ACTIVITY_ABOUT = 8;
    private static final int REQUEST_DISCOVERABLE_RESULT = 9;
    private static final int ACTIVITY_WELCOME_ACTIVITY = 10;

    private static final int VIEW_ALL = 0;
    private static final int VIEW_PUBLIC = 1;
    private static final int VIEW_OUTGOING = 2;
    private static final int VIEW_BLACKLIST = 3;
    private static final int VIEW_HIGH_PRIORITY = 4;
    private static int VIEW_MODE = VIEW_ALL;

    private long currentRowID = -1;

    private static final int DIALOG_REALLY_DELETE = 0;
    private static final int DIALOG_REALLY_BLACKLIST = 1;
    private static final int DIALOG_REALLY_UNBLACKLIST = 2;
    private static final int DIALOG_NO_KEY = 3;
    private static final int DIALOG_DISCLAIMER = 4;
    private static final int DIALOG_PASSPHRASE = 5;
    private static final int DIALOG_CHANGE_PASSPHRASE = 6;

    private static final int MENU_ADD_ID = Menu.FIRST;
    private static final int MENU_VIEW_ID = Menu.FIRST + 1;
    private static final int MENU_SETTINGS_ID = Menu.FIRST + 2;
    private static final int MENU_ALL_ID = Menu.FIRST + 3;
    private static final int MENU_DELETE_ID = Menu.FIRST + 4;
    private static final int MENU_HELP_ID = Menu.FIRST + 5;
    private static final int MENU_BLACKLIST_ID = Menu.FIRST + 5;
    private static final int MENU_REKEY_ID = Menu.FIRST + 6;

    // messages to/from bluetooth service
    Messenger networkService = null;
    final Messenger messenger = new Messenger(new IncomingHandler());
    private boolean bound = false;

    // Messages to network service
    public static final int MSG_NEW_MESSAGE_CREATED = 0xF0;
    public static final int MSG_MESSAGE_DELETED = 0xF1;
    public static final int STOP_SERVICE = 0xF2;

    private boolean showMessages = true;
    private boolean sendBlacklist = true;

    private BluetoothAdapter bluetoothAdapter = null;
    private boolean askedBluetooth = false;
    private boolean enableBluetoothServicePref = true;
    private boolean enableZeroconfServicePref = true;
    private boolean enableNexusServicePref = true;
    private boolean vibratePref = false;
    private boolean firstRun = false;

    private Vibrator vibrator = null;

    private File attachmentsDir = null;

    // oauth constants
    private static final String API_BASE = "http://fluidnexus.net/api/01/";
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

                    if (vibratePref) {
                        ((Vibrator) getSystemService(getApplicationContext().VIBRATOR_SERVICE)).vibrate(500);
                    }

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

                // Send database passphrase
                // CAREFUL
                // Be sure to take care that the passphrase is removed from memory as quickly as possible 
                msg = Message.obtain(null, NetworkService.MSG_DATABASE_OPEN);
                Bundle bundle = new Bundle();
                bundle.putString("passphrase", passphrase);
                msg.replyTo = messenger;
                msg.setData(bundle);
                networkService.send(msg);
                passphrase = null;

                // Send send_blacklist flag
                // This needs to be sent before all others
                msg = Message.obtain(null, NetworkService.MSG_SEND_BLACKLISTED);
                msg.arg1 = (prefs.getBoolean("sendBlacklistPref", false))? 1 : 0;
                msg.replyTo = messenger;
                networkService.send(msg);

                // Send bluetooth enabled bit on start
                msg = Message.obtain(null, NetworkService.MSG_BLUETOOTH_ENABLED);
                msg.arg1 = (prefs.getBoolean("enableBluetoothServicePref", false))? 1 : 0;
                msg.arg2 = Integer.parseInt(prefs.getString("bluetoothScanFrequency", "300"));
                msg.replyTo = messenger;
                networkService.send(msg);
                
                // Send bonded only flag
                msg = Message.obtain(null, NetworkService.MSG_BLUETOOTH_BONDED_ONLY_FLAG);
                msg.arg1 = (prefs.getBoolean("bluetoothBondedOnlyFlag", true))? 1 : 0;
                msg.replyTo = messenger;
                networkService.send(msg);

                // Send zeroconf enabled bit on start
                msg = Message.obtain(null, NetworkService.MSG_ZEROCONF_ENABLED);
                msg.arg1 = (prefs.getBoolean("enableZeroconfServicePref", false)) ? 1 : 0;
                msg.arg2 = Integer.parseInt(prefs.getString("zeroconfScanFrequency", "300"));
                msg.replyTo = messenger;
                networkService.send(msg);

                // Send bit for starting nexus service
                msg = Message.obtain(null, NetworkService.MSG_NEXUS_ENABLED);
                msg.arg1 = (prefs.getBoolean("enableNexusServicePref", false)) ? 1 : 0;
                msg.arg2 = Integer.parseInt(prefs.getString("nexusScanFrequency", "300"));
                bundle = new Bundle();
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

        // Load the libs necessary for sqlcipher
        SQLiteDatabase.loadLibs(this);

        /*
        if (messagesProviderHelper == null) {
            messagesProviderHelper = new MessagesProviderHelper(this);
        }
        */

        setContentView(R.layout.message_list);
        registerForContextMenu(getListView());


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

        setupPreferences();


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
            case DIALOG_DISCLAIMER:
                dialog = disclaimerDialog();
                break;
            case DIALOG_PASSPHRASE:
                dialog = passphraseDialog();
                break;
            case DIALOG_CHANGE_PASSPHRASE:
                dialog = changePassphraseDialog();
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
                    startManagingCursor(localCursor);
                    String attachmentPath = localCursor.getString(localCursor.getColumnIndex(MessagesProviderHelper.KEY_ATTACHMENT_PATH));
                    boolean mine = localCursor.getInt(localCursor.getColumnIndex(MessagesProviderHelper.KEY_MINE)) > 0;
                    
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
                    values.put(MessagesProviderHelper.KEY_BLACKLIST, 1);
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
                    values.put(MessagesProviderHelper.KEY_BLACKLIST, 0);
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

    /**
     * Method to create our lack of nexus key or secret dialog
     */
    private AlertDialog disclaimerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.disclaimer_dialog)
            .setCancelable(false)
            .setPositiveButton(R.string.understand_answer, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                }
            });
        return builder.create();
    }


    @Override 
    public void onStart() {
        super.onStart();

        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        if ((bluetoothAdapter != null) && (askedBluetooth == false)) {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            }
        }

        if (firstRun == true) {
            Intent i = new Intent(this, WelcomeActivity.class);
            startActivityForResult(i, ACTIVITY_WELCOME_ACTIVITY);
        } else {
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ListView lv;
        lv = (ListView) getListView();

        int scrollPos = lv.getFirstVisiblePosition();
        outState.putInt("position", scrollPos);
    }

    @Override
    public void onRestoreInstanceState(Bundle inState) {
        super.onRestoreInstanceState(inState);
        ListView lv;
        lv = (ListView) getListView();

        int scrollPos = inState.getInt("position");
        lv.setSelection(scrollPos);
    }

    /**
     * Show the ask for passphrase dialog box
     */
    private AlertDialog passphraseDialog() {
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.ask_for_passphrase, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setView(textEntryView)
            .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    EditText et = ((EditText) textEntryView.findViewById(R.id.ask_for_passphrase_edit));
                    passphrase = et.getText().toString();
                    unlockDatabase(passphrase);

                    et.setText("");
                    System.gc();
                }
            })
            .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {

                }
            });

        return builder.create();
    }

    /**
     * Show the change passphrase dialog box
     */
    private AlertDialog changePassphraseDialog() {
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.change_passphrase, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setView(textEntryView)
            .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    EditText et = ((EditText) textEntryView.findViewById(R.id.ask_for_passphrase_edit));
                    passphrase = et.getText().toString();

                    rekeyDatabase(passphrase);

                    et.setText("");
                    System.gc();
                }
            })
            .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {

                }
            });

        return builder.create();
    }


    /**
     * Unlock the database with the provided passphrase 
     */
    private void unlockDatabase(String passphrase) {
        if (messagesProviderHelper == null) {
            messagesProviderHelper = MessagesProviderHelper.getInstance(this);
        }

        try {
            messagesProviderHelper.open(passphrase);
            fillListView(VIEW_MODE);

            // Bind to the network service
            doBindService();
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_unable_to_unlock, Toast.LENGTH_LONG).show();
            log.error("Error opening database: " + e);
            passphraseDialog().show();
        }

    }

    /**
     * Rekey the database with the new passphrase
     */
    private void rekeyDatabase(String passphrase) {
        try {
            messagesProviderHelper.rekey(passphrase);
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_unable_to_rekey, Toast.LENGTH_LONG);
            log.error("Error rekeying database: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        /*
        SharedPreferences p = getSharedPreferences("SCROLL", 0);
        int scroll = p.getInt("ScrollValue", 0);
        log.debug("ON RESUME: " + scroll);
        ListView lv;
        lv = (ListView) getListView();
        lv.scrollTo(0, scroll);
        */

        messagesProviderHelper = MessagesProviderHelper.getInstance(this);
        if (!messagesProviderHelper.isOpen()) {
            if (firstRun != true) {
                showDialog(DIALOG_PASSPHRASE);
            }
        } else {
            fillListView(VIEW_MODE);


        }


        // Parse a URI result as sent from the browser on Nexus confirmation
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
        
        if (c != null) {
            c.close();
        }

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
        startManagingCursor(localCursor);
        menu.setHeaderTitle(localCursor.getString(localCursor.getColumnIndexOrThrow(MessagesProviderHelper.KEY_TITLE)));
        int mine = localCursor.getInt(localCursor.getColumnIndexOrThrow(MessagesProviderHelper.KEY_MINE));

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

        //localCursor.close();
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
        if ((networkService != null) && (bound == true)) {
            try {
                Message msg = Message.obtain(null, NetworkService.MSG_UNREGISTER_CLIENT);
                msg.replyTo = messenger;
                networkService.send(msg);

                msg = Message.obtain(null, STOP_SERVICE);
                msg.replyTo = messenger;
                networkService.send(msg);

            } catch (RemoteException e) {
                // nothing special to do if the service has already stopped for some reason
            }

            unbindService(networkServiceConnection);
            bound = false;
            log.info("Unbound to the Fluid Nexus Service");
        }
    }


    /**
     * Open up a new activity to edit the message
     */
    private void editMessage() {

        Intent i = new Intent(this, EditMessage.class);
        i.putExtra(MessagesProviderHelper.KEY_ID, currentRowID);
        startActivityForResult(i, ACTIVITY_EDIT_MESSAGE);

    }

    private void setupPreferences() {
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        firstRun = prefs.getBoolean("FirstRun", true);
        
        showMessages = prefs.getBoolean("showMessagesPref", true);
        sendBlacklist = prefs.getBoolean("sendBlacklistPref", false);
        vibratePref = prefs.getBoolean("vibratePref", false);

        // Setup a listener for when preferences change
        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
                if (key.equals("enableBluetoothServicePref")) {
                    boolean tmp = prefs.getBoolean("enableBluetoothServicePref", false);
                    try {
                        // Send bluetooth enabled bit
                        Message msg = Message.obtain(null, NetworkService.MSG_BLUETOOTH_ENABLED);
                        msg.arg1 = (prefs.getBoolean("enableBluetoothServicePref", false))? 1 : 0;
                        msg.arg2 = Integer.parseInt(prefs.getString("bluetoothScanFrequency", "300"));
                        msg.replyTo = messenger;
                        if (networkService != null) {
                            networkService.send(msg);
                            enableBluetoothServicePref = tmp;
                        }
                    } catch (RemoteException e) {
                        log.error("Unable to send MSG_BLUETOOTH_ENABLED");
                    }
                } else if (key.equals("enableZeroconfServicePref")) {
                    boolean tmp = prefs.getBoolean("enableZeroconfServicePref", false);
                    try {
                        // Send zeroconf enabled bit
                        Message msg = Message.obtain(null, NetworkService.MSG_ZEROCONF_ENABLED);
                        msg.arg1 = (prefs.getBoolean("enableZeroconfServicePref", false))? 1 : 0;
                        msg.arg2 = Integer.parseInt(prefs.getString("zeroconfScanFrequency", "300"));
                        msg.replyTo = messenger;
                        if (networkService != null) {
                            networkService.send(msg);
                            enableZeroconfServicePref = tmp;
                        }
                    } catch (RemoteException e) {
                        log.error("Unable to send MSG_ZEROCONF_ENABLED");
                    }
                } else if (key.equals("enableNexusServicePref")) {
                    boolean tmp = prefs.getBoolean("enableNexusServicePref", false);
                    try {
                        // Send nexus enabled bit
                        Message msg = Message.obtain(null, NetworkService.MSG_NEXUS_ENABLED);
                        msg.arg1 = (prefs.getBoolean("enableNexusServicePref", false))? 1 : 0;
                        // TODO
                        // Add preference for this
                        msg.arg2 = Integer.parseInt(prefs.getString("nexusScanFrequency", "300"));
                        msg.replyTo = messenger;
                        if (networkService != null) {
                            networkService.send(msg);
                            enableNexusServicePref = tmp;
                        }
                    } catch (RemoteException e) {
                        log.error("Unable to send MSG_NEXUS_ENABLED");
                    }
                } else if (key.equals("bluetoothScanFrequency")) {
                    try {
                        Message msg = Message.obtain(null, NetworkService.MSG_BLUETOOTH_SCAN_FREQUENCY);
                        msg.arg1 = Integer.parseInt(prefs.getString("bluetoothScanFrequency", "300"));
                        msg.replyTo = messenger;
                        if (networkService != null) {
                            networkService.send(msg);
                        }
                    } catch (RemoteException e) {
                        log.error("Unable to send scan frequency message: " + e);
                    }
                } else if (key.equals("bluetoothBondedOnlyFlag")) {
                    try {
                        Message msg = Message.obtain(null, NetworkService.MSG_BLUETOOTH_BONDED_ONLY_FLAG);
                        msg.arg1 = (prefs.getBoolean("bluetoothBondedOnlyFlag", true) ? 1:0);
                        msg.replyTo = messenger;
                        if (networkService != null) {
                            networkService.send(msg);
                        }
                    } catch (RemoteException e) {
                        log.error("Unable to send bonded only flag message: " + e);
                    }
                } else if (key.equals("zeroconfScanFrequency")) {
                    try {
                        Message msg = Message.obtain(null, NetworkService.MSG_ZEROCONF_SCAN_FREQUENCY);
                        msg.arg1 = Integer.parseInt(prefs.getString("zeroconfScanFrequency", "300"));
                        msg.replyTo = messenger;
                        if (networkService != null) {
                            networkService.send(msg);
                        }
                    } catch (RemoteException e) {
                        log.error("Unable to send scan frequency message: " + e);
                    }

                } else if (key.equals("vibratePref")) {
                    vibratePref = prefs.getBoolean("vibratePref", false);
                } else if (key.equals("showMessagesPref")) {
                    showMessages = prefs.getBoolean("showMessagesPref", true);
                    fillListView(VIEW_MODE);
                } else if (key.equals("sendBlacklistPref")) {
                    sendBlacklist = prefs.getBoolean("sendBlacklistPref", false);
                    try {
                        Message msg = Message.obtain(null, NetworkService.MSG_SEND_BLACKLISTED);
                        msg.arg1 = (prefs.getBoolean("sendBlacklistPref", false) ? 1:0);
                        msg.replyTo = messenger;
                        if (networkService != null) {
                            networkService.send(msg);
                        }
                    } catch (RemoteException e) {
                        log.error("Unable to send bonded only flag message: " + e);
                    }

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

        Intent i = null;

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
            case R.id.menu_view_high_priority:
                VIEW_MODE = VIEW_HIGH_PRIORITY;
                fillListView(VIEW_MODE);

                // Update our header text view
                tv = (TextView) findViewById(R.id.message_list_header_text);
                tv.setText(R.string.message_list_header_text_high_priority);

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
            case R.id.menu_change_passphrase:
                showDialog(DIALOG_CHANGE_PASSPHRASE);
                return true;
            case R.id.menu_discoverable:
                if (bluetoothAdapter != null) {
                    i = new Intent();
                    i.setAction(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                    i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                    startActivityForResult(i, REQUEST_DISCOVERABLE_RESULT);
                }
                return true;
            case R.id.menu_stop_service:
                doUnbindService();
                return true;
            case R.id.menu_help:
                i = new Intent(this, Help.class);
                /*startSubActivity(i, ACTIVITY_HELP);*/
                startActivityForResult(i, ACTIVITY_HELP);
                return true;
            case R.id.menu_about:
                i = new Intent(this, About.class);
                /*startSubActivity(i, ACTIVITY_HELP);*/
                startActivityForResult(i, ACTIVITY_ABOUT);
                return true;

        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        Intent i = new Intent(this, ViewMessage.class);
        i.putExtra(MessagesProviderHelper.KEY_ID, id);
        startActivityForResult(i, ACTIVITY_VIEW_MESSAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case(ACTIVITY_VIEW_MESSAGE):
                fillListView(VIEW_MODE);
                break;
            case(ACTIVITY_WELCOME_ACTIVITY):
                prefsEditor = prefs.edit();
                prefsEditor.putBoolean("FirstRun", false);
                prefsEditor.putBoolean("bluetoothBondedOnlyFlag", true);
                prefsEditor.putBoolean("sendBlacklistPref", false);
                prefsEditor.commit();

                firstRun = false;
                
                //AlertDialog passphraseDialog = passphraseDialog();
                //passphraseDialog.show();

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
            case(REQUEST_DISCOVERABLE_RESULT):
                // Do something on request discoverable result
                String s = "";
                if (resultCode < 0) {
                    s = getString(R.string.toast_discoverable_notok);
                } else {
                    s = getString(R.string.toast_discoverable_ok, resultCode);
                }
                Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
                break;
            case(ACTIVITY_PREFERENCES):
                break;
            case(REQUEST_ENABLE_BT):
                if (resultCode == ListActivity.RESULT_OK) {
                    toast = Toast.makeText(this, R.string.toast_bluetooth_request_ok, Toast.LENGTH_LONG);
                    toast.show();
                    // setup services here
                } else {
                    log.warn("Bluetooth not enabled");
                    Toast.makeText(this, R.string.toast_bluetooth_request_notok, Toast.LENGTH_SHORT).show();
                    askedBluetooth = true;
                    enableBluetoothServicePref = false;
                    if (prefsEditor != null) {
                        prefsEditor.putBoolean("enableBluetoothServicePref", false);
                    }
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


        String[] from = new String[] {MessagesProviderHelper.KEY_TITLE, MessagesProviderHelper.KEY_CONTENT, MessagesProviderHelper.KEY_MINE, MessagesProviderHelper.KEY_ATTACHMENT_ORIGINAL_FILENAME, MessagesProviderHelper.KEY_TIME, MessagesProviderHelper.KEY_RECEIVED_TIME, MessagesProviderHelper.KEY_PRIORITY};
        int[] to = new int[] {R.id.message_list_item, R.id.message_list_data, R.id.message_list_item_icon, R.id.message_list_attachment, R.id.message_list_created_time, R.id.message_list_received_time, R.id.message_list_item};
        
        if (viewType == VIEW_ALL) {
            c = messagesProviderHelper.allNoBlacklist();
            startManagingCursor(c);
        } else if (viewType == VIEW_PUBLIC) {
            c = messagesProviderHelper.publicMessages();
            startManagingCursor(c);
        } else if (viewType == VIEW_OUTGOING) {
            c = messagesProviderHelper.outgoing();
            startManagingCursor(c);
        } else if (viewType == VIEW_BLACKLIST) {
            c = messagesProviderHelper.blacklist();
            startManagingCursor(c);
        } else if (viewType == VIEW_HIGH_PRIORITY) {
            c = messagesProviderHelper.highPriority();
            startManagingCursor(c);
        }

        MessagesListAdapter messagesAdapter = new MessagesListAdapter(this, R.layout.message_list_item, c, from, to);

        ListView lv;
        lv = (ListView) getListView();
        
        setListAdapter(messagesAdapter);

    }

}
