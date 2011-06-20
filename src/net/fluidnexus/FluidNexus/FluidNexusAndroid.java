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
import android.os.Bundle;
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
import android.widget.SimpleCursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

/*
 * TODO
 * * deal with the database somehow not being open when we receive the MSG_NEW_MESSAGE_RECEIVED message after an orientation change.  This is totally opaque to me.
 * * deal with new binding to the service when clicking on the notification; this shouldn't happen
 */

public class FluidNexusAndroid extends ListActivity {
    private FluidNexusDbAdapter dbAdapter;
    private Toast toast;
    
    private SharedPreferences prefs;
    private Editor prefsEditor;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    // just for testing
    private BroadcastReceiver iReceiver;
    private IntentFilter iFilter;

    private static FluidNexusLogger log = FluidNexusLogger.getLogger("FluidNexus"); 

    private static final int ACTIVITY_HOME = 0;
    private static final int ACTIVITY_VIEW_OUTGOING = 1;
    private static final int ACTIVITY_ADD_OUTGOING = 2;
    private static final int ACTIVITY_PREFERENCES= 3;
    private static final int ACTIVITY_VIEW_MESSAGE = 4;
    private static final int ACTIVITY_HELP = 5;
    private static final int REQUEST_ENABLE_BT = 6;
    private static final int ACTIVITY_EDIT_MESSAGE = 7;

    private static int VIEW_MODE = 0;

    private long currentRowID = -1;

    private static final int DIALOG_REALLY_DELETE = 0;

    private static final int MENU_ADD_ID = Menu.FIRST;
    private static final int MENU_VIEW_ID = Menu.FIRST + 1;
    private static final int MENU_SETTINGS_ID = Menu.FIRST + 2;
    private static final int MENU_ALL_ID = Menu.FIRST + 3;
    private static final int MENU_DELETE_ID = Menu.FIRST + 4;
    private static final int MENU_HELP_ID = Menu.FIRST + 5;

    // messages from bluetooth service
    Messenger bluetoothService = null;
    final Messenger messenger = new Messenger(new IncomingHandler());

    // Messages to the bluetooth service
    static final int MSG_NEW_MESSAGE_CREATED = 0xF0;
    static final int MSG_MESSAGE_DELETED = 0xF1;

    private boolean showMessages = true;

    private Cursor dbCursor;

    private BluetoothAdapter bluetoothAdapter = null;
    private boolean enableBluetoothServicePref = true;

    private class NewMessageIntentReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log.info(action);
            log.info("received new message intent.");
            fillListView(VIEW_MODE);
        }
    }

    /**
     * Our handler for incoming messages
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FluidNexusBluetoothServiceVer3.MSG_NEW_MESSAGE_RECEIVED:
                    log.debug("Received MSG_NEW_MESSAGE_RECEIVED");
                    fillListView(VIEW_MODE);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private ServiceConnection bluetoothServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            bluetoothService = new Messenger(service);
            log.debug("Connected to service");
            try {
                Message msg = Message.obtain(null, FluidNexusBluetoothServiceVer3.MSG_REGISTER_CLIENT);
                msg.replyTo = messenger;
                bluetoothService.send(msg);
            } catch (RemoteException e) {
                // Here, the service has crashed even before we were able to connect
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // Called when the connection to the service has been unexpectedly closed
            bluetoothService = null;
            log.debug("Disconnected from service");
        }

    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        log.verbose("unfreezing...");

        setContentView(R.layout.message_list);
        registerForContextMenu(getListView());

        log.verbose("starting up...");
      
        // Regiser my receiver to NEW_MESSAGE action
        iFilter = new IntentFilter(getText(R.string.intent_new_message).toString());
        iReceiver = new NewMessageIntentReceiver();
        registerReceiver(iReceiver, iFilter);

        // setup bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // if it's not available, let user know
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available; sending and receiving messages will not be possible", Toast.LENGTH_LONG).show();
        }
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
                    dbAdapter.deleteById(currentRowID);

                    // TODO
                    // move to instance method
                    try {
                        // Send message to service to note that a new message has been created
                        Message msg = Message.obtain(null, MSG_MESSAGE_DELETED);
                        bluetoothService.send(msg);
                    } catch (RemoteException e) {
                        // Here, the service has crashed even before we were able to connect
                    }

                    currentRowID = -1;
                    fillListView(VIEW_MODE);
                    toast = Toast.makeText(getApplicationContext(), "Message deleted.", Toast.LENGTH_LONG);
                    toast.show();
                }
            })
            .setNegativeButton("No", null);
        return builder.create();
    }

    @Override 
    public void onStart() {
        super.onStart();
        log.info("In onStart");

        dbAdapter = new FluidNexusDbAdapter(this);
        dbAdapter.open();
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
                if (bluetoothService == null) {
                    setupFluidNexusBluetoothService();
                }
                */
            }
        }

        enableBluetoothServicePref = prefs.getBoolean("enableBluetoothServicePref", true);
        if (enableBluetoothServicePref) {
            doBindService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(iReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(iReceiver, iFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        dbCursor.close();
        dbAdapter.close();
        try {
            doUnbindService();
        } catch (Throwable t) {
            log.debug("Failed to unbind from the service");
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
        Cursor c = dbAdapter.returnItemByID(currentRowID);
        menu.setHeaderTitle(c.getString(c.getColumnIndexOrThrow(FluidNexusDbAdapter.KEY_TITLE)));
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.message_list_context, menu);
        c.close();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
            case R.id.delete_message:
                showDialog(DIALOG_REALLY_DELETE);
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
        log.info("Binding to Fluid Nexus Bluetooth Service");
        Intent i = new Intent(this, FluidNexusBluetoothServiceVer3.class);
        startService(i);
        bindService(i, bluetoothServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Unbind to the service
     */
    private void doUnbindService() {
        if (bluetoothService != null) {
            try {
                Message msg = Message.obtain(null, FluidNexusBluetoothServiceVer3.MSG_UNREGISTER_CLIENT);
                msg.replyTo = messenger;
                bluetoothService.send(msg);
            } catch (RemoteException e) {
                // nothing special to do if the service has already stopped for some reason
            }

            unbindService(bluetoothServiceConnection);
            log.info("Unbound to the Fluid Nexus Bluetooth Service");
        }
    }


    /**
     * Open up a new activity to edit the message
     * TODO
     * Only edit messages that are outgoing
     */
    private void editMessage() {
        Cursor c = dbAdapter.returnItemByID(currentRowID);

        Intent i = new Intent(this, FluidNexusEditMessage.class);
        i.putExtra(FluidNexusDbAdapter.KEY_ID, c.getInt(c.getColumnIndex(FluidNexusDbAdapter.KEY_ID)));
        i.putExtra(FluidNexusDbAdapter.KEY_TITLE, c.getString(c.getColumnIndex(FluidNexusDbAdapter.KEY_TITLE)));
        i.putExtra(FluidNexusDbAdapter.KEY_DATA, c.getString(c.getColumnIndex(FluidNexusDbAdapter.KEY_DATA)));
        startActivityForResult(i, ACTIVITY_EDIT_MESSAGE);
        c.close();

    }

    private void setupPreferences() {
        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean firstRun = prefs.getBoolean("FirstRun", true);

        if (firstRun == true) {
            prefsEditor = prefs.edit();
            prefsEditor.putBoolean("FirstRun", false);
            prefsEditor.commit();

            dbAdapter.initialPopulate();
        }
        
        this.showMessages = prefs.getBoolean("ShowMessages", true);

        // Setup a listener for when preferences change
        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
                log.debug("changed key: " + key);
                if (key.equals("enableBluetoothServicePref")) {
                    boolean tmp = prefs.getBoolean("enableBluetoothServicePref", true);

                    if (tmp) {
                        startService(new Intent(FluidNexusBluetoothServiceVer3.class.getName()));
                    } else {
                        stopService(new Intent(FluidNexusBluetoothServiceVer3.class.getName()));
                    }
                    enableBluetoothServicePref = tmp;
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
                VIEW_MODE = 0;
                fillListView(VIEW_MODE);

                // Update our header text view
                tv = (TextView) findViewById(R.id.message_list_header_text);
                tv.setText(R.string.message_list_header_text_all);

                return true;
            case R.id.menu_view_outgoing:
                VIEW_MODE = 1;
                fillListView(VIEW_MODE);

                // Update our header text view
                tv = (TextView) findViewById(R.id.message_list_header_text);
                tv.setText(R.string.message_list_header_text_outgoing);

                return true;
            case R.id.menu_preferences:
                editPreferences();
                return true;
            case R.id.menu_help:
                Intent i = new Intent(this, FluidNexusHelp.class);
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
        Cursor localCursor = dbCursor;
        localCursor.moveToPosition(position);

        Intent i = new Intent(this, FluidNexusViewMessage.class);
        i.putExtra(FluidNexusDbAdapter.KEY_ID, localCursor.getInt(localCursor.getColumnIndex(FluidNexusDbAdapter.KEY_ID)));
        i.putExtra(FluidNexusDbAdapter.KEY_TITLE, localCursor.getString(localCursor.getColumnIndex(FluidNexusDbAdapter.KEY_TITLE)));
        i.putExtra(FluidNexusDbAdapter.KEY_DATA, localCursor.getString(localCursor.getColumnIndex(FluidNexusDbAdapter.KEY_DATA)));
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
                // TODO
                // move this to an instance method
                try {
                    // Send message to service to note that a new message has been created
                    Message msg = Message.obtain(null, MSG_NEW_MESSAGE_CREATED);
                    bluetoothService.send(msg);
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
        Intent intent = new Intent(this, FluidNexusPreferences.class);
        startActivityForResult(intent, ACTIVITY_PREFERENCES);
    }

    private void addOutgoingMessage() {
        Intent intent = new Intent(this, FluidNexusAddOutgoing.class);
        startActivityForResult(intent, ACTIVITY_ADD_OUTGOING);
    }


    public void setViewText(TextView v, String text) {
        log.debug(text);
    }

    private void fillListView(int viewType) {
        if (!this.showMessages) {
            return;
        }

        if (viewType == 0) {
            dbCursor = dbAdapter.all();
        } else if (viewType == 1) {
            dbCursor = dbAdapter.outgoing();
        }
        //startManagingCursor(dbCursor);

        TextView tv;
        tv = (TextView) findViewById(R.id.message_list_item);
        
        String[] from = new String[] {FluidNexusDbAdapter.KEY_TITLE, FluidNexusDbAdapter.KEY_DATA, FluidNexusDbAdapter.KEY_MINE};
        //String[] from = new String[] {FluidNexusDbAdapter.KEY_TITLE};
        int[] to = new int[] {R.id.message_list_item, R.id.message_list_data, R.id.message_list_item_icon};
        //int[] to = new int[] {R.id.message_list_item};
        SimpleCursorAdapter messagesAdapter = new SimpleCursorAdapter(this, R.layout.message_list_item, dbCursor, from, to);
        ListView lv;
        lv = (ListView) getListView();
        lv.setSelection(0);

        messagesAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            public boolean setViewValue(View view, Cursor cursor, int i) {
                if (i == cursor.getColumnIndex(FluidNexusDbAdapter.KEY_DATA)) {
                    String fullMessage = cursor.getString(i);
                    TextView tv = (TextView) view;
                    int stringLen = fullMessage.length();
                    if (stringLen < 20) {
                        tv.setText(fullMessage + " ...");
                    } else {
                        tv.setText(fullMessage.substring(0, 20) + " ...");
                    }

                    return true;
                }

                if (i == cursor.getColumnIndex(FluidNexusDbAdapter.KEY_MINE)) {
                    ImageView iv = (ImageView) view;
                    int mine = cursor.getInt(i);

                    if (mine == 0) {
                        iv.setImageResource(R.drawable.menu_all_list_icon);
                    } else if (mine == 1) {
                        iv.setImageResource(R.drawable.menu_view_list_icon);
                    }
                    
                    return true;
                }
                return false;
            }
        });

        setListAdapter(messagesAdapter);

    }
}
